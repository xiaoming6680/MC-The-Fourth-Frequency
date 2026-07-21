package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.AMBER;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.CYAN;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.DIM;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.DISABLED_RAIL;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.GREEN;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.HOT;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.withAlpha;

/** Mandatory first-launch disclosure mounted inside a complete metal terminal shell. */
public final class FirstRunNoticeScreen extends Screen {
	public enum EntrancePhase {
		NOISE_STARTUP,
		SCAN_DESYNC,
		SIGNAL_CONVERGENCE,
		LOCKED
	}

	private static final int MAX_PANEL_WIDTH = 360;
	private static final int MAX_PANEL_HEIGHT = 216;
	private static final int BUTTON_HEIGHT = 18;
	private static final int NORMAL_LINE_HEIGHT = 9;
	private static final int COMPACT_LINE_HEIGHT = 8;
	private static final int LOCK_TICK = 24;
	private static final String STATUS_PREFIX = "RX-04 //";
	private static final int STATUS_GAP = 4;
	private static final int LATIN_BASELINE_Y_OFFSET = 1;
	private static final int UI_TEXTURE_WIDTH = 1620;
	private static final int UI_TEXTURE_HEIGHT = 971;
	private static final int GLASS_SAFE_LEFT_ASSET = 132;
	private static final int GLASS_SAFE_RIGHT_ASSET = 1488;
	private static final int GLASS_SAFE_TOP_ASSET = 120;
	private static final int GLASS_SAFE_BOTTOM_ASSET = 870;
	private static final Identifier NOTICE_UI = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/gui/notice/first_run_notice_terminal_shell.png");

	private int age;
	private boolean openingSoundPlayed;
	private boolean stableSoundPlayed;
	private final Screen returnScreen;
	private NoticeButton acknowledgementButton;

	public FirstRunNoticeScreen(Screen returnScreen) {
		super(Component.translatable("screen.thefourthfrequency.first_run_notice.title"));
		this.returnScreen = returnScreen;
	}

	@Override
	protected void init() {
		NoticeLayout layout = layout();
		NoticeGrid grid = NoticeGrid.from(layout);
		acknowledgementButton = addRenderableWidget(new NoticeButton(
				grid.buttonLeft(), grid.buttonY(), grid.buttonWidth(), BUTTON_HEIGHT,
				Component.translatable("button.thefourthfrequency.first_run_notice.acknowledge")));
		updateButtonState();
		if (!openingSoundPlayed) {
			openingSoundPlayed = true;
			TerminalClientAudio.noticeOpening();
		}
	}

	@Override
	public void tick() {
		age++;
		if (age >= LOCK_TICK && !stableSoundPlayed) {
			stableSoundPlayed = true;
			TerminalClientAudio.noticeStable();
		}
		updateButtonState();
	}

	private void updateButtonState() {
		if (acknowledgementButton != null) acknowledgementButton.active = isSignalLocked();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float renderAge = age + partialTick;
		NoticeLayout layout = layout();
		renderGeneratedNoticeUi(graphics, layout);
		renderHeader(graphics, layout, renderAge);
		renderContent(graphics, layout);
		renderFooter(graphics, layout);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xFF080D0A);
	}

	private void renderGeneratedNoticeUi(GuiGraphics graphics, NoticeLayout layout) {
		graphics.fill(layout.left() + 4, layout.top() + 5, layout.right() + 4, layout.bottom() + 5, 0x78000000);
		graphics.blit(RenderPipelines.GUI_TEXTURED, NOTICE_UI,
				layout.left(), layout.top(), 0.0F, 0.0F, layout.width(), layout.height(),
				UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT, UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT);
	}

	private void renderHeader(GuiGraphics graphics, NoticeLayout layout, float renderAge) {
		NoticeGrid grid = NoticeGrid.from(layout);
		drawBaselineAlignedString(graphics, STATUS_PREFIX, grid.textLeft(), grid.statusY(), DIM, false);
		int statusTextLeft = grid.textLeft() + font.width(STATUS_PREFIX) + STATUS_GAP;
		drawBaselineAlignedString(graphics, statusText(), statusTextLeft, grid.statusY(),
				isSignalLocked() ? GREEN : faultAccent(), false);
		drawHeaderScope(graphics, grid.scopeLeft(), grid.scopeTop(), grid.scopeWidth(),
				grid.scopeHeight(), renderAge);

		drawBaselineAlignedString(graphics, title, grid.textLeft(), grid.titleY(), AMBER, false);
		drawLeftScaled(graphics,
				Component.translatable("screen.thefourthfrequency.first_run_notice.eyebrow"),
				grid.textLeft(), grid.eyebrowY(), grid.textWidth(), DIM);
		graphics.fill(grid.slotLeft(), grid.headerRuleY(), grid.slotRight(), grid.headerRuleY() + 1,
				withAlpha(GREEN, 52));
		graphics.fill(grid.textLeft(), grid.headerRuleY(), grid.textLeft() + 46, grid.headerRuleY() + 1,
				withAlpha(AMBER, 128));
	}

	private void drawHeaderScope(GuiGraphics graphics, int x, int y, int scopeWidth, int scopeHeight,
			float renderAge) {
		int baseline = y + scopeHeight / 2;
		int previousY = baseline;
		for (int localX = 2; localX < scopeWidth - 1; localX += 3) {
			double ordinarySignal = Math.sin((localX + renderAge * 1.8F) * 0.31D) * 1.6D;
			double noise = deterministicNoise(localX + 97, age) * noiseStrength() * 0.55D;
			int nextY = Math.max(y + 2, Math.min(y + scopeHeight - 2,
					baseline + (int) Math.round(ordinarySignal + noise)));
			graphics.fill(x + localX, Math.min(previousY, nextY), x + localX + 1,
					Math.max(previousY, nextY) + 1, isSignalLocked() ? GREEN : faultAccent());
			previousY = nextY;
		}
	}

	private void renderContent(GuiGraphics graphics, NoticeLayout layout) {
		NoticeGrid grid = NoticeGrid.from(layout);
		int x = grid.textLeft();
		int width = grid.textWidth();
		int y = grid.contentTop();
		int lineHeight = contentLineHeight(layout);

		Component control = Component.translatable("screen.thefourthfrequency.first_run_notice.body.control");
		List<String> controlLines = wrapText(control, width);
		drawBaselineAlignedString(graphics, ">", grid.slotLeft(), y, DIM, false);
		drawBaselineAlignedString(graphics,
				Component.translatable("screen.thefourthfrequency.first_run_notice.section.control"),
				x, y, DIM, false);
		drawWrappedText(graphics, controlLines, x, y + 10, GREEN, lineHeight);
		y += controlHeight(controlLines.size(), lineHeight) + 2;

		Component safety = Component.translatable("screen.thefourthfrequency.first_run_notice.body.safety");
		List<String> safetyLines = wrapText(safety, width);
		int safetyHeight = safetyHeight(safetyLines.size(), lineHeight);
		graphics.fill(grid.slotLeft(), y + 1, grid.slotLeft() + 2, y + safetyHeight - 1,
				withAlpha(GREEN, 180));
		drawBaselineAlignedString(graphics,
				Component.translatable("screen.thefourthfrequency.first_run_notice.section.safety"),
				x, y + 2, GREEN, false);
		drawWrappedText(graphics, safetyLines, x, y + 11, GREEN, lineHeight);
		Component f8 = Component.translatable("screen.thefourthfrequency.first_run_notice.body.f8");
		List<String> f8Lines = wrapText(f8, grid.f8TextWidth());
		drawF8Bay(graphics, grid, f8Lines);
	}

	private void drawF8Bay(GuiGraphics graphics, NoticeGrid grid, List<String> lines) {
		graphics.fill(grid.slotLeft(), grid.f8RuleY(), grid.slotRight(), grid.f8RuleY() + 1,
				withAlpha(AMBER, 72));
		drawBaselineAlignedCenteredString(graphics, "F8", grid.f8BadgeCenterX(), grid.f8BadgeY(), AMBER);
		drawWrappedText(graphics, lines, grid.f8TextLeft(), grid.f8TextY(), AMBER, COMPACT_LINE_HEIGHT);
	}

	private void renderFooter(GuiGraphics graphics, NoticeLayout layout) {
		NoticeGrid grid = NoticeGrid.from(layout);
		drawLeftScaled(graphics,
				Component.translatable("screen.thefourthfrequency.first_run_notice.footer"),
				grid.textLeft(), grid.footerY(), grid.textWidth(), DIM);
	}

	private void drawWrappedText(GuiGraphics graphics, List<String> lines, int x, int y,
			int color, int lineHeight) {
		for (String line : lines) {
			drawBaselineAlignedString(graphics, line, x, y, color, false);
			y += lineHeight;
		}
	}

	private void drawBaselineAlignedCenteredString(GuiGraphics graphics, Component text,
			int centerX, int y, int color) {
		drawBaselineAlignedString(graphics, text, centerX - font.width(text) / 2, y, color, false);
	}

	private void drawBaselineAlignedCenteredString(GuiGraphics graphics, String text,
			int centerX, int y, int color) {
		drawBaselineAlignedString(graphics, text, centerX - font.width(text) / 2, y, color, false);
	}

	private void drawBaselineAlignedString(GuiGraphics graphics, Component text,
			int x, int y, int color, boolean shadow) {
		drawBaselineAlignedString(graphics, text.getString(), x, y, color, shadow);
	}

	/** Minecraft's Latin pixel face is one GUI pixel shorter than its CJK fallback. */
	private void drawBaselineAlignedString(GuiGraphics graphics, String text,
			int x, int y, int color, boolean shadow) {
		if (text.isEmpty()) return;
		int cursorX = x;
		int runStart = 0;
		int index = 0;
		boolean latinBaseline = usesLatinPixelBaseline(text.codePointAt(0));
		while (index < text.length()) {
			int codePoint = text.codePointAt(index);
			boolean nextLatinBaseline = usesLatinPixelBaseline(codePoint);
			if (nextLatinBaseline != latinBaseline) {
				String run = text.substring(runStart, index);
				graphics.drawString(font, run, cursorX,
						y + (latinBaseline ? LATIN_BASELINE_Y_OFFSET : 0), color, shadow);
				cursorX += font.width(run);
				runStart = index;
				latinBaseline = nextLatinBaseline;
			}
			index += Character.charCount(codePoint);
		}
		String run = text.substring(runStart);
		graphics.drawString(font, run, cursorX,
				y + (latinBaseline ? LATIN_BASELINE_Y_OFFSET : 0), color, shadow);
	}

	private static boolean usesLatinPixelBaseline(int codePoint) {
		return codePoint <= 0x024F;
	}

	private void drawLeftScaled(GuiGraphics graphics, Component text, int x, int y,
			int maxWidth, int color) {
		int textWidth = Math.max(1, font.width(text));
		float scale = Math.min(1.0F, maxWidth / (float) textWidth);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		drawBaselineAlignedString(graphics, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	private List<String> wrapText(Component text, int maxWidth) {
		String value = text.getString();
		List<String> lines = new ArrayList<>();
		int start = 0;
		while (start < value.length()) {
			while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
			if (start >= value.length()) break;
			int fit = start;
			while (fit < value.length() && font.width(value.substring(start, fit + 1)) <= maxWidth) fit++;
			if (fit == start) fit = Math.min(value.length(), start + 1);
			int end = fit;
			if (fit < value.length()) {
				int searchStart = Math.max(start + 1, fit - 10);
				for (int index = fit - 1; index >= searchStart; index--) {
					if (isPreferredBreak(value.charAt(index))) {
						end = index + 1;
						break;
					}
				}
			}
			while (end < value.length() && isClosingPunctuation(value.charAt(end))) end++;
			lines.add(value.substring(start, end).strip());
			start = end;
		}
		if (lines.isEmpty()) lines.add("");
		return lines;
	}

	private int contentLineHeight(NoticeLayout layout) {
		return contentHeight(layout, NORMAL_LINE_HEIGHT) <= NoticeGrid.from(layout).contentHeight()
				? NORMAL_LINE_HEIGHT : COMPACT_LINE_HEIGHT;
	}

	private int contentHeight(NoticeLayout layout, int lineHeight) {
		int width = NoticeGrid.from(layout).textWidth();
		int controlLines = wrapText(Component.translatable(
				"screen.thefourthfrequency.first_run_notice.body.control"), width).size();
		int safetyLines = wrapText(Component.translatable(
				"screen.thefourthfrequency.first_run_notice.body.safety"), width).size();
		return controlHeight(controlLines, lineHeight) + 2
				+ safetyHeight(safetyLines, lineHeight);
	}

	private static int controlHeight(int lines, int lineHeight) { return 10 + lines * lineHeight; }
	private static int safetyHeight(int lines, int lineHeight) { return 12 + lines * lineHeight; }

	private static boolean isPreferredBreak(char character) {
		return Character.isWhitespace(character) || "，。；：、！？,.;:!?".indexOf(character) >= 0;
	}

	private static boolean isClosingPunctuation(char character) {
		return "，。；：、！？）】》”’,.;:!?)]}".indexOf(character) >= 0;
	}

	private NoticeLayout layout() {
		int panelWidth = Math.max(300, Math.min(MAX_PANEL_WIDTH, width - 24));
		int panelHeight = Math.max(196, Math.min(MAX_PANEL_HEIGHT, height - 16));
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		return new NoticeLayout(left, top, panelWidth, panelHeight);
	}

	private EntrancePhase entrancePhase() {
		if (age <= 4) return EntrancePhase.NOISE_STARTUP;
		if (age <= 12) return EntrancePhase.SCAN_DESYNC;
		if (age < LOCK_TICK) return EntrancePhase.SIGNAL_CONVERGENCE;
		return EntrancePhase.LOCKED;
	}

	private boolean isSignalLocked() {
		return age >= LOCK_TICK;
	}

	private Component statusText() {
		return Component.translatable(isSignalLocked()
				? "screen.thefourthfrequency.first_run_notice.status.stable"
				: "screen.thefourthfrequency.first_run_notice.status.checking");
	}

	private int faultAccent() {
		return (age / 2 & 1) == 0 ? CYAN : HOT;
	}

	private double noiseStrength() {
		return switch (entrancePhase()) {
			case NOISE_STARTUP -> 6.5D;
			case SCAN_DESYNC -> 4.5D;
			case SIGNAL_CONVERGENCE -> 4.0D * (LOCK_TICK - age) / 11.0D;
			case LOCKED -> 0.0D;
		};
	}

	private static double deterministicNoise(int x, int tick) {
		long value = scramble(0x4E4F544943455258L ^ (long) x * 0x9E3779B97F4A7C15L ^ tick * 0x632BE59BD9B4E019L);
		return ((value >>> 20) & 0xFF) / 127.5D - 1.0D;
	}

	private static long scramble(long value) {
		value ^= value << 13;
		value ^= value >>> 7;
		value ^= value << 17;
		return value;
	}

	@Override public boolean shouldCloseOnEsc() { return false; }
	@Override public void onClose() { }
	@Override public boolean isPauseScreen() { return true; }

	public Component acknowledgementLabelForTesting() { return acknowledgementButton.getMessage(); }
	public EntrancePhase entrancePhaseForTesting() { return entrancePhase(); }
	public boolean acknowledgementAvailableForTesting() {
		return acknowledgementButton != null && acknowledgementButton.active;
	}
	public int openingSoundPlayCountForTesting() { return TerminalClientAudio.noticeOpeningPlaysForTesting(); }
	public int stableSoundPlayCountForTesting() { return TerminalClientAudio.noticeStablePlaysForTesting(); }
	public boolean acknowledgementButtonIsAtBottomForTesting() {
		NoticeLayout layout = layout();
		NoticeGrid grid = NoticeGrid.from(layout);
		return acknowledgementButton != null
				&& acknowledgementButton.getX() == grid.buttonLeft()
				&& acknowledgementButton.getY() == grid.buttonY()
				&& acknowledgementButton.getWidth() == grid.buttonWidth()
				&& acknowledgementButton.getBottom() < layout.bottom();
	}
	public boolean dedicatedLayoutFitsForTesting() {
		NoticeLayout layout = layout();
		NoticeGrid grid = NoticeGrid.from(layout);
		int f8Lines = wrapText(Component.translatable(
				"screen.thefourthfrequency.first_run_notice.body.f8"), grid.f8TextWidth()).size();
		return layout.width() >= 300 && layout.height() >= 196
				&& layout.left() >= 0 && layout.top() >= 0
				&& layout.right() <= width && layout.bottom() <= height
				&& contentHeight(layout, contentLineHeight(layout)) <= grid.contentHeight()
				&& grid.f8TextY() + f8Lines * COMPACT_LINE_HEIGHT <= grid.f8Bottom();
	}
	public boolean allElementsAlignedForTesting() {
		NoticeGrid grid = NoticeGrid.from(layout());
		return grid.statusLeft() == grid.titleLeft()
				&& grid.titleLeft() == grid.bodyLeft()
				&& grid.bodyLeft() == grid.safetyLeft()
				&& grid.slotLeft() < grid.textLeft()
				&& grid.textRight() < grid.slotRight()
				&& grid.centerX() == (grid.slotLeft() + grid.slotRight()) / 2
				&& grid.buttonLeft() + grid.buttonWidth() / 2 == grid.centerX();
	}
	public boolean allTextInsideGlassForTesting() {
		NoticeLayout layout = layout();
		NoticeGrid grid = NoticeGrid.from(layout);
		int safeLeft = NoticeGrid.x(layout, GLASS_SAFE_LEFT_ASSET);
		int safeRight = NoticeGrid.x(layout, GLASS_SAFE_RIGHT_ASSET);
		int safeTop = NoticeGrid.y(layout, GLASS_SAFE_TOP_ASSET);
		int safeBottom = NoticeGrid.y(layout, GLASS_SAFE_BOTTOM_ASSET);
		return grid.slotLeft() >= safeLeft && grid.slotRight() <= safeRight
				&& grid.textLeft() >= safeLeft && grid.textRight() <= safeRight
				&& grid.slotLeft() + font.width(">") < grid.textLeft()
				&& grid.statusY() >= safeTop
				&& grid.scopeTop() >= safeTop
				&& grid.footerY() + NORMAL_LINE_HEIGHT < grid.buttonY()
				&& grid.buttonY() + BUTTON_HEIGHT <= safeBottom;
	}
	public boolean avoidsOrphanPunctuationForTesting() {
		NoticeLayout layout = layout();
		NoticeGrid grid = NoticeGrid.from(layout);
		int contentWidth = grid.textWidth();
		return avoidsLeadingPunctuation(Component.translatable(
				"screen.thefourthfrequency.first_run_notice.body.control"), contentWidth)
				&& avoidsLeadingPunctuation(Component.translatable(
				"screen.thefourthfrequency.first_run_notice.body.safety"), contentWidth)
				&& avoidsLeadingPunctuation(Component.translatable(
				"screen.thefourthfrequency.first_run_notice.body.f8"), grid.f8TextWidth());
	}
	private boolean avoidsLeadingPunctuation(Component text, int maxWidth) {
		for (String line : wrapText(text, maxWidth))
			if (!line.isEmpty() && isClosingPunctuation(line.charAt(0))) return false;
		return true;
	}
	public void reinitializeForTesting() { rebuildWidgets(); }
	public void acknowledgeForTesting() { acknowledge(); }

	private void acknowledge() {
		if (!isSignalLocked()) return;
		FirstRunNoticeController.acknowledge(minecraft, returnScreen);
	}

	private final class NoticeButton extends Button {
		private NoticeButton(int x, int y, int width, int height, Component message) {
			super(x, y, width, height, message, button -> acknowledge(), DEFAULT_NARRATION);
		}

		@Override
		protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int railInset = Math.max(18, getWidth() / 6);
			graphics.fill(getX() + railInset, getBottom() - 2, getRight() - railInset, getBottom() - 1,
					active ? AMBER : DISABLED_RAIL);
			drawBaselineAlignedCenteredString(graphics, getMessage(), getX() + getWidth() / 2,
					getY() + (getHeight() - 8) / 2, active ? (isHovered() ? AMBER : GREEN) : DIM);
		}
	}

	private record NoticeLayout(int left, int top, int width, int height) {
		private int right() { return left + width; }
		private int bottom() { return top + height; }
	}

	/** Asset-space anchors keep every overlay on one continuous CRT glass grid. */
	private record NoticeGrid(
			int slotLeft, int slotRight, int textLeft, int textRight,
			int statusY,
			int scopeLeft, int scopeTop, int scopeWidth, int scopeHeight,
			int titleY, int eyebrowY, int headerRuleY,
			int contentTop, int contentBottom, int safetyGhostY,
			int f8RuleY, int f8BadgeCenterX, int f8BadgeY, int f8TextLeft, int f8TextY, int f8Bottom,
			int footerY, int buttonLeft, int buttonRight, int buttonY) {
		private static NoticeGrid from(NoticeLayout layout) {
			return new NoticeGrid(
					x(layout, GLASS_SAFE_LEFT_ASSET), x(layout, GLASS_SAFE_RIGHT_ASSET),
					x(layout, 170), x(layout, 1450),
					y(layout, 126),
					x(layout, 1131), y(layout, 126), spanX(layout, 286), spanY(layout, 48),
					y(layout, 190), y(layout, 235), y(layout, 280),
					y(layout, 305), y(layout, 590), y(layout, 445),
					y(layout, 620), x(layout, 220), y(layout, 652), x(layout, 380), y(layout, 638), y(layout, 722),
					y(layout, 730), x(layout, 520), x(layout, 1100), y(layout, 790));
		}

		private static int x(NoticeLayout layout, int assetX) {
			return layout.left() + Math.round(assetX * layout.width() / (float) UI_TEXTURE_WIDTH);
		}

		private static int y(NoticeLayout layout, int assetY) {
			return layout.top() + Math.round(assetY * layout.height() / (float) UI_TEXTURE_HEIGHT);
		}

		private static int spanX(NoticeLayout layout, int assetWidth) {
			return Math.max(1, Math.round(assetWidth * layout.width() / (float) UI_TEXTURE_WIDTH));
		}

		private static int spanY(NoticeLayout layout, int assetHeight) {
			return Math.max(1, Math.round(assetHeight * layout.height() / (float) UI_TEXTURE_HEIGHT));
		}

		private int centerX() { return (slotLeft + slotRight) / 2; }
		private int textWidth() { return textRight - textLeft; }
		private int contentHeight() { return contentBottom - contentTop; }
		private int f8TextWidth() { return textRight - f8TextLeft; }
		private int buttonWidth() { return buttonRight - buttonLeft; }
		private int statusLeft() { return textLeft; }
		private int titleLeft() { return textLeft; }
		private int bodyLeft() { return textLeft; }
		private int safetyLeft() { return textLeft; }
	}
}
