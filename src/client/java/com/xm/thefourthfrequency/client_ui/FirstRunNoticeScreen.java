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
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.DIM;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.DISABLED_RAIL;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.GLASS_BLACKOUT;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.GREEN;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.PHOSPHOR_FLASH;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.SHELL_BACKDROP;
import static com.xm.thefourthfrequency.client_ui.FirstRunNoticePalette.withAlpha;

/** Mandatory first-launch disclosure mounted inside a complete metal terminal shell. */
public final class FirstRunNoticeScreen extends Screen {
	/** The three strokes of a cathode tube coming up: strike, unfold, settle. */
	public enum EntrancePhase {
		IGNITION,
		UNFOLD,
		BLOOM,
		LIT
	}

	public enum PresentationPhase {
		POWER_ON,
		NOTICE,
		TRANSITION
	}

	private static final int MAX_PANEL_WIDTH = 394;
	private static final int MAX_PANEL_HEIGHT = 236;
	private static final int MIN_PANEL_WIDTH = 300;
	private static final int MIN_PANEL_HEIGHT = 196;
	private static final int PANEL_MARGIN_X = 16;
	private static final int PANEL_MARGIN_Y = 4;
	private static final int BUTTON_HEIGHT = 18;
	private static final int NORMAL_LINE_HEIGHT = 9;
	private static final int SECTION_GAP = 4;
	private static final float[] BODY_SCALE_STEPS = {0.82F, 0.78F, 0.74F, 0.70F, 0.66F};
	private static final int IGNITION_TICKS = 4;
	private static final int UNFOLD_TICKS = 6;
	private static final int BLOOM_TICKS = 6;
	private static final int TUBE_LIT_TICK = IGNITION_TICKS + UNFOLD_TICKS;
	private static final int POWER_ON_END_TICK = TUBE_LIT_TICK + BLOOM_TICKS;
	private static final int NOTICE_REVEAL_TICKS = 10;
	private static final int NOTICE_READY_TICK = POWER_ON_END_TICK + NOTICE_REVEAL_TICKS;
	/** The tube is already lit when the copy arrives, so it emerges from a haze, not from black. */
	private static final int NOTICE_REVEAL_VEIL = 170;
	private static final int PHOSPHOR_FLOOR_ALPHA = 34;
	private static final int PHOSPHOR_STRIKE_ALPHA = 210;
	private static final int TEXT_FADE_TICKS = 4;
	private static final int ZOOM_TICKS = 24;
	private static final int TRANSITION_TICKS = TEXT_FADE_TICKS + ZOOM_TICKS;
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
	private int transitionAge = -1;
	private boolean transitionFinished;
	private boolean openingSoundPlayed;
	private boolean stableSoundPlayed;
	private boolean acknowledgementFocused;
	private final Screen returnScreen;
	private NoticeButton acknowledgementButton;

	public FirstRunNoticeScreen(Screen returnScreen) {
		super(Component.translatable("screen.thefourthfrequency.first_run_notice.title"));
		this.returnScreen = returnScreen;
	}

	@Override
	protected void init() {
		acknowledgementFocused = false;
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
		if (transitionAge >= 0) {
			transitionAge++;
			if (transitionAge >= TRANSITION_TICKS && !transitionFinished) {
				transitionFinished = true;
				FirstRunNoticeController.acknowledge(minecraft, returnScreen);
			}
			return;
		}
		age++;
		if (age >= TUBE_LIT_TICK && !stableSoundPlayed) {
			stableSoundPlayed = true;
			TerminalClientAudio.noticeStable();
		}
		updateButtonState();
	}

	private void updateButtonState() {
		if (acknowledgementButton == null) return;
		boolean ready = presentationPhase() == PresentationPhase.NOTICE && age >= NOTICE_READY_TICK;
		acknowledgementButton.visible = ready;
		acknowledgementButton.active = ready;
		// Escape and every other exit is blocked here, so the only way out must be reachable by keyboard.
		if (ready && !acknowledgementFocused) {
			acknowledgementFocused = true;
			setInitialFocus(acknowledgementButton);
		}
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		// The zoom transition renders the title screen directly, but Minecraft only resizes the
		// screen it currently owns, so an unforwarded resize would expose a stale menu layout.
		returnScreen.resize(width, height);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float renderAge = age + partialTick;
		switch (presentationPhase()) {
			case POWER_ON -> renderPowerOn(graphics, renderAge);
			case NOTICE -> renderNotice(graphics, renderAge, mouseX, mouseY, partialTick);
			case TRANSITION -> renderTransition(graphics, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (presentationPhase() == PresentationPhase.TRANSITION
				&& transitionAge >= TEXT_FADE_TICKS) return;
		graphics.fill(0, 0, width, height, SHELL_BACKDROP);
	}

	/**
	 * A cathode tube coming up: the beam strikes as a point and snaps out to a full-width line, the
	 * raster unfolds vertically around it, then the over-bright phosphor decays to its resting floor.
	 * No copy and no data — the screen is hardware waking up, which needs no translation.
	 */
	private void renderPowerOn(GuiGraphics graphics, float renderAge) {
		NoticeLayout layout = layout();
		renderGeneratedNoticeUi(graphics, layout);
		GlassBounds glass = glassBounds(layout, 5);
		graphics.enableScissor(glass.left(), glass.top(), glass.right(), glass.bottom());
		graphics.fill(glass.left(), glass.top(), glass.right(), glass.bottom(), GLASS_BLACKOUT);

		int centerX = (glass.left() + glass.right()) / 2;
		int centerY = (glass.top() + glass.bottom()) / 2;
		float ignition = easeOut(Math.clamp(renderAge / IGNITION_TICKS, 0.0F, 1.0F));
		float unfold = easeOut(Math.clamp((renderAge - IGNITION_TICKS) / UNFOLD_TICKS, 0.0F, 1.0F));
		float bloom = Math.clamp((renderAge - TUBE_LIT_TICK) / BLOOM_TICKS, 0.0F, 1.0F);

		int halfHeight = Math.round(glass.height() / 2.0F * unfold);
		if (halfHeight > 0) {
			int rasterTop = Math.max(glass.top(), centerY - halfHeight);
			int rasterBottom = Math.min(glass.bottom(), centerY + halfHeight);
			int interior = withAlpha(GREEN, Math.round(PHOSPHOR_STRIKE_ALPHA
					+ (PHOSPHOR_FLOOR_ALPHA - PHOSPHOR_STRIKE_ALPHA) * bloom));
			graphics.fill(glass.left(), rasterTop, glass.right(), rasterBottom, interior);
			// The opening edges stay hottest until the raster has finished sweeping outwards.
			int edge = withAlpha(PHOSPHOR_FLASH, Math.round(255.0F * (1.0F - bloom)));
			graphics.fill(glass.left(), rasterTop, glass.right(), rasterTop + 1, edge);
			graphics.fill(glass.left(), rasterBottom - 1, glass.right(), rasterBottom, edge);
		}

		int halfWidth = Math.round(glass.width() / 2.0F * ignition);
		int strikeAlpha = Math.round(255.0F * (1.0F - bloom) * (1.0F - 0.45F * unfold));
		if (halfWidth > 0 && strikeAlpha > 0) {
			graphics.fill(Math.max(glass.left(), centerX - halfWidth), centerY - 1,
					Math.min(glass.right(), centerX + halfWidth), centerY + 1,
					withAlpha(PHOSPHOR_FLASH, strikeAlpha));
		}
		graphics.disableScissor();
	}

	private static float easeOut(float progress) {
		float inverted = 1.0F - progress;
		return 1.0F - inverted * inverted;
	}

	private void renderNotice(GuiGraphics graphics, float renderAge, int mouseX, int mouseY,
			float partialTick) {
		NoticeLayout layout = layout();
		renderGeneratedNoticeUi(graphics, layout);
		renderNoticeText(graphics, layout);
		float reveal = Math.clamp((renderAge - POWER_ON_END_TICK) / NOTICE_REVEAL_TICKS,
				0.0F, 1.0F);
		if (reveal < 1.0F) fillGlass(graphics, glassBounds(layout, 5),
				withAlpha(GLASS_BLACKOUT, Math.round(NOTICE_REVEAL_VEIL * (1.0F - reveal))));
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderTransition(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float transitionTime = Math.min(TRANSITION_TICKS, transitionAge + partialTick);
		float textFade = Math.clamp(transitionTime / TEXT_FADE_TICKS, 0.0F, 1.0F);
		float zoomProgress = Math.clamp((transitionTime - TEXT_FADE_TICKS) / ZOOM_TICKS, 0.0F, 1.0F);
		NoticeLayout base = layout();
		float scale = 1.0F + (targetZoomScale(base) - 1.0F) * zoomProgress;
		NoticeLayout zoomed = zoomedLayout(base, scale);

		if (zoomProgress <= 0.0F) {
			renderGeneratedNoticeUi(graphics, base);
			renderNoticeText(graphics, base);
			fillGlass(graphics, glassBounds(base, 5),
					withAlpha(GLASS_BLACKOUT, Math.round(255.0F * textFade)));
			return;
		}

		GlassBounds mask = glassBounds(zoomed, 3);
		renderVanillaAnimationMask(graphics, mask, mouseX, mouseY, partialTick);
		float maskReveal = Math.clamp(zoomProgress * 2.0F, 0.0F, 1.0F);
		int veilAlpha = Math.round(255.0F * (1.0F - maskReveal));
		if (veilAlpha > 0) fillGlass(graphics, mask, withAlpha(GLASS_BLACKOUT, veilAlpha));
		int terminalAlpha = Math.round(255.0F * (1.0F - zoomProgress));
		if (terminalAlpha > 0) renderTransitionFrame(graphics, zoomed, terminalAlpha);
	}

	private void renderNoticeText(GuiGraphics graphics, NoticeLayout layout) {
		renderHeader(graphics, layout);
		renderContent(graphics, layout);
		renderFooter(graphics, layout);
	}

	private void renderVanillaAnimationMask(GuiGraphics graphics, GlassBounds mask,
			int mouseX, int mouseY, float partialTick) {
		int left = Math.clamp(mask.left(), 0, width);
		int top = Math.clamp(mask.top(), 0, height);
		int right = Math.clamp(mask.right(), 0, width);
		int bottom = Math.clamp(mask.bottom(), 0, height);
		if (left >= right || top >= bottom) return;
		returnScreen.render(graphics, mouseX, mouseY, partialTick);
		if (top > 0) graphics.fill(0, 0, width, top, SHELL_BACKDROP);
		if (bottom < height) graphics.fill(0, bottom, width, height, SHELL_BACKDROP);
		if (left > 0) graphics.fill(0, top, left, bottom, SHELL_BACKDROP);
		if (right < width) graphics.fill(right, top, width, bottom, SHELL_BACKDROP);
	}

	private void renderTransitionFrame(GuiGraphics graphics, NoticeLayout layout, int alpha) {
		int innerLeft = NoticeGrid.x(layout, GLASS_SAFE_LEFT_ASSET);
		int innerRight = NoticeGrid.x(layout, GLASS_SAFE_RIGHT_ASSET);
		int innerTop = NoticeGrid.y(layout, GLASS_SAFE_TOP_ASSET);
		int innerBottom = NoticeGrid.y(layout, GLASS_SAFE_BOTTOM_ASSET);
		int topHeight = innerTop - layout.top();
		int bottomHeight = layout.bottom() - innerBottom;
		int leftWidth = innerLeft - layout.left();
		int rightWidth = layout.right() - innerRight;
		int middleHeight = innerBottom - innerTop;

		graphics.blit(RenderPipelines.GUI_TEXTURED, NOTICE_UI,
				layout.left(), layout.top(), 0.0F, 0.0F, layout.width(), topHeight,
				UI_TEXTURE_WIDTH, GLASS_SAFE_TOP_ASSET, UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT,
				withAlpha(0xFFFFFFFF, alpha));
		graphics.blit(RenderPipelines.GUI_TEXTURED, NOTICE_UI,
				layout.left(), innerTop, 0.0F, GLASS_SAFE_TOP_ASSET, leftWidth, middleHeight,
				GLASS_SAFE_LEFT_ASSET, GLASS_SAFE_BOTTOM_ASSET - GLASS_SAFE_TOP_ASSET,
				UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT, withAlpha(0xFFFFFFFF, alpha));
		graphics.blit(RenderPipelines.GUI_TEXTURED, NOTICE_UI,
				innerRight, innerTop, GLASS_SAFE_RIGHT_ASSET, GLASS_SAFE_TOP_ASSET,
				rightWidth, middleHeight, UI_TEXTURE_WIDTH - GLASS_SAFE_RIGHT_ASSET,
				GLASS_SAFE_BOTTOM_ASSET - GLASS_SAFE_TOP_ASSET, UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT,
				withAlpha(0xFFFFFFFF, alpha));
		graphics.blit(RenderPipelines.GUI_TEXTURED, NOTICE_UI,
				layout.left(), innerBottom, 0.0F, GLASS_SAFE_BOTTOM_ASSET,
				layout.width(), bottomHeight, UI_TEXTURE_WIDTH,
				UI_TEXTURE_HEIGHT - GLASS_SAFE_BOTTOM_ASSET, UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT,
				withAlpha(0xFFFFFFFF, alpha));
	}

	private static void fillGlass(GuiGraphics graphics, GlassBounds glass, int color) {
		graphics.fill(glass.left(), glass.top(), glass.right(), glass.bottom(), color);
	}

	private void renderGeneratedNoticeUi(GuiGraphics graphics, NoticeLayout layout) {
		int shadowX = Math.max(4, Math.round(layout.width() / (float) MAX_PANEL_WIDTH * 4.0F));
		int shadowY = Math.max(5, Math.round(layout.height() / (float) MAX_PANEL_HEIGHT * 5.0F));
		graphics.fill(layout.left() + shadowX, layout.top() + shadowY,
				layout.right() + shadowX, layout.bottom() + shadowY, 0x78000000);
		graphics.blit(RenderPipelines.GUI_TEXTURED, NOTICE_UI,
				layout.left(), layout.top(), 0.0F, 0.0F, layout.width(), layout.height(),
				UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT, UI_TEXTURE_WIDTH, UI_TEXTURE_HEIGHT);
	}

	private void renderHeader(GuiGraphics graphics, NoticeLayout layout) {
		NoticeGrid grid = NoticeGrid.from(layout);
		drawBaselineAlignedString(graphics, STATUS_PREFIX, grid.textLeft(), grid.statusY(), DIM, false);
		int statusTextLeft = grid.textLeft() + font.width(STATUS_PREFIX) + STATUS_GAP;
		drawBaselineAlignedString(graphics, statusText(), statusTextLeft, grid.statusY(),
				GREEN, false);

		drawBaselineAlignedString(graphics, title, grid.textLeft(), grid.titleY(), AMBER, false);
		drawLeftScaled(graphics,
				Component.translatable("screen.thefourthfrequency.first_run_notice.eyebrow"),
				grid.textLeft(), grid.eyebrowY(), grid.textWidth(), DIM);
		graphics.fill(grid.slotLeft(), grid.headerRuleY(), grid.slotRight(), grid.headerRuleY() + 1,
				withAlpha(GREEN, 52));
		graphics.fill(grid.textLeft(), grid.headerRuleY(), grid.textLeft() + 46, grid.headerRuleY() + 1,
				withAlpha(AMBER, 128));
	}

	private void renderContent(GuiGraphics graphics, NoticeLayout layout) {
		NoticeGrid grid = NoticeGrid.from(layout);
		NoticeCopyLayout copy = noticeCopyLayout(grid);

		drawNoticeColumn(graphics, copy.left(), grid.leftColumnLeft(), grid.contentTop(),
				grid.columnWidth(), copy.bodyScale(), DIM);
		graphics.fill(grid.columnDividerX(), grid.contentTop(), grid.columnDividerX() + 1,
				grid.contentBottom(), withAlpha(GREEN, 44));
		drawNoticeColumn(graphics, copy.right(), grid.rightColumnLeft(), grid.contentTop(),
				grid.columnWidth(), copy.bodyScale(), AMBER);
	}

	private void drawNoticeColumn(GuiGraphics graphics, List<NoticeSection> sections,
			int x, int y, int width, float bodyScale, int accent) {
		int lineHeight = scaledBodyLineHeight(bodyScale);
		for (int index = 0; index < sections.size(); index++) {
			NoticeSection section = sections.get(index);
			drawBaselineAlignedString(graphics, section.heading(), x, y, accent, false);
			graphics.fill(x, y + 9, x + Math.min(width, 34), y + 10, withAlpha(accent, 92));
			y += 12;
			drawWrappedScaledText(graphics, section.lines(), x, y, GREEN, lineHeight, bodyScale);
			y += section.lines().size() * lineHeight;
			if (index + 1 < sections.size()) y += SECTION_GAP;
		}
	}

	private void renderFooter(GuiGraphics graphics, NoticeLayout layout) {
		NoticeGrid grid = NoticeGrid.from(layout);
		drawLeftScaled(graphics,
				Component.translatable("screen.thefourthfrequency.first_run_notice.footer"),
				grid.textLeft(), grid.footerY(), grid.textWidth(), DIM);
	}

	private void drawWrappedScaledText(GuiGraphics graphics, List<String> lines, int x, int y,
			int color, int lineHeight, float scale) {
		for (String line : lines) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(scale, scale);
			drawBaselineAlignedString(graphics, line, 0, 0, color, false);
			graphics.pose().popMatrix();
			y += lineHeight;
		}
	}

	private void drawBaselineAlignedCenteredString(GuiGraphics graphics, Component text,
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
		drawLeftScaled(graphics, text, x, y, maxWidth, color, 1.0F);
	}

	private void drawLeftScaled(GuiGraphics graphics, Component text, int x, int y,
			int maxWidth, int color, float maxScale) {
		int textWidth = Math.max(1, font.width(text));
		float scale = Math.min(maxScale, maxWidth / (float) textWidth);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		drawBaselineAlignedString(graphics, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	private List<String> wrapText(Component text, int maxWidth, float scale) {
		int unscaledWidth = Math.max(1, (int) Math.floor(maxWidth / scale));
		String value = text.getString();
		List<String> lines = new ArrayList<>();
		int start = 0;
		while (start < value.length()) {
			while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
			if (start >= value.length()) break;
			int fit = start;
			while (fit < value.length() && font.width(value.substring(start, fit + 1)) <= unscaledWidth) fit++;
			if (fit == start) fit = Math.min(value.length(), start + 1);
			int end = fit;
			if (fit < value.length()) {
				int wordSearchStart = Math.max(start + 1, fit - 24);
				for (int index = fit - 1; index >= wordSearchStart; index--) {
					if (Character.isWhitespace(value.charAt(index))) {
						end = index + 1;
						break;
					}
				}
				int searchStart = Math.max(start + 1, fit - 10);
				for (int index = fit - 1; index >= searchStart; index--) {
					if (end == fit && isPreferredBreak(value.charAt(index))) {
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

	private NoticeCopyLayout noticeCopyLayout(NoticeGrid grid) {
		for (float scale : BODY_SCALE_STEPS) {
			NoticeCopyLayout copy = noticeCopyLayout(grid, scale);
			if (copy.height() <= grid.contentHeight()) return copy;
		}
		return noticeCopyLayout(grid, BODY_SCALE_STEPS[BODY_SCALE_STEPS.length - 1]);
	}

	private NoticeCopyLayout noticeCopyLayout(NoticeGrid grid, float scale) {
		List<NoticeSection> left = List.of(
				noticeSection("screen.thefourthfrequency.first_run_notice.section.control",
						"screen.thefourthfrequency.first_run_notice.body.control", grid.columnWidth(), scale),
				noticeSection("screen.thefourthfrequency.first_run_notice.section.safety",
						"screen.thefourthfrequency.first_run_notice.body.safety", grid.columnWidth(), scale));
		List<NoticeSection> right = List.of(
				noticeSection("screen.thefourthfrequency.first_run_notice.section.effects",
						"screen.thefourthfrequency.first_run_notice.body.safety_v2", grid.columnWidth(), scale),
				noticeSection("screen.thefourthfrequency.first_run_notice.section.f8",
						"screen.thefourthfrequency.first_run_notice.body.recovery_v3", grid.columnWidth(), scale));
		return new NoticeCopyLayout(left, right, scale,
				Math.max(columnHeight(left, scale), columnHeight(right, scale)));
	}

	private NoticeSection noticeSection(String headingKey, String bodyKey,
			int width, float scale) {
		return new NoticeSection(Component.translatable(headingKey),
				wrapText(Component.translatable(bodyKey), width, scale));
	}

	private static int columnHeight(List<NoticeSection> sections, float scale) {
		int height = 0;
		int lineHeight = scaledBodyLineHeight(scale);
		for (NoticeSection section : sections) height += 12 + section.lines().size() * lineHeight;
		return height + SECTION_GAP * Math.max(0, sections.size() - 1);
	}

	private static int scaledBodyLineHeight(float scale) {
		return Math.max(6, Math.round(NORMAL_LINE_HEIGHT * scale));
	}

	private static boolean isPreferredBreak(char character) {
		return Character.isWhitespace(character) || "，。；：、！？,.;:!?".indexOf(character) >= 0;
	}

	private static boolean isClosingPunctuation(char character) {
		return "，。；：、！？）】》”’,.;:!?)]}".indexOf(character) >= 0;
	}

	private NoticeLayout layout() {
		int panelWidth = Math.max(MIN_PANEL_WIDTH,
				Math.min(MAX_PANEL_WIDTH, Math.max(1, width - PANEL_MARGIN_X)));
		int panelHeight = Math.max(MIN_PANEL_HEIGHT,
				Math.min(MAX_PANEL_HEIGHT, Math.max(1, height - PANEL_MARGIN_Y)));
		// The margins above are a preference, but the viewport is a hard limit: rather than let the
		// minimum panel hang off screen and clip a disclosure the player must read, shrink the shell.
		float shortfall = Math.min(width / (float) panelWidth, height / (float) panelHeight);
		if (shortfall < 1.0F) {
			panelWidth = Math.max(1, Math.round(panelWidth * shortfall));
			panelHeight = Math.max(1, Math.round(panelHeight * shortfall));
		}
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		return new NoticeLayout(left, top, panelWidth, panelHeight);
	}

	private PresentationPhase presentationPhase() {
		if (transitionAge >= 0) return PresentationPhase.TRANSITION;
		return age < POWER_ON_END_TICK ? PresentationPhase.POWER_ON : PresentationPhase.NOTICE;
	}

	private NoticeLayout zoomedLayout(NoticeLayout base, float scale) {
		int scaledWidth = Math.max(1, Math.round(base.width() * scale));
		int scaledHeight = Math.max(1, Math.round(base.height() * scale));
		return new NoticeLayout((width - scaledWidth) / 2, (height - scaledHeight) / 2,
				scaledWidth, scaledHeight);
	}

	private float targetZoomScale(NoticeLayout base) {
		GlassBounds glass = glassBounds(base, 3);
		float centerX = width / 2.0F;
		float centerY = height / 2.0F;
		float scaleLeft = centerX / Math.max(1.0F, centerX - glass.left());
		float scaleRight = (width - centerX) / Math.max(1.0F, glass.right() - centerX);
		float scaleTop = centerY / Math.max(1.0F, centerY - glass.top());
		float scaleBottom = (height - centerY) / Math.max(1.0F, glass.bottom() - centerY);
		return Math.max(Math.max(scaleLeft, scaleRight), Math.max(scaleTop, scaleBottom)) * 1.04F;
	}

	private static GlassBounds glassBounds(NoticeLayout layout, int inset) {
		return new GlassBounds(
				NoticeGrid.x(layout, GLASS_SAFE_LEFT_ASSET) + inset,
				NoticeGrid.y(layout, GLASS_SAFE_TOP_ASSET) + inset,
				NoticeGrid.x(layout, GLASS_SAFE_RIGHT_ASSET) - inset,
				NoticeGrid.y(layout, GLASS_SAFE_BOTTOM_ASSET) - inset);
	}

	private EntrancePhase entrancePhase() {
		if (age < IGNITION_TICKS) return EntrancePhase.IGNITION;
		if (age < TUBE_LIT_TICK) return EntrancePhase.UNFOLD;
		if (age < POWER_ON_END_TICK) return EntrancePhase.BLOOM;
		return EntrancePhase.LIT;
	}

	private boolean isSignalLocked() {
		return age >= TUBE_LIT_TICK;
	}

	private Component statusText() {
		return Component.translatable(isSignalLocked()
				? "screen.thefourthfrequency.first_run_notice.status.stable"
				: "screen.thefourthfrequency.first_run_notice.status.checking");
	}

	@Override public boolean shouldCloseOnEsc() { return false; }
	@Override public void onClose() { }
	@Override public boolean isPauseScreen() { return true; }

	public Component acknowledgementLabelForTesting() { return acknowledgementButton.getMessage(); }
	public EntrancePhase entrancePhaseForTesting() { return entrancePhase(); }
	public PresentationPhase presentationPhaseForTesting() { return presentationPhase(); }
	public float transitionProgressForTesting() {
		return transitionAge < 0 ? 0.0F : Math.clamp(transitionAge / (float) TRANSITION_TICKS, 0.0F, 1.0F);
	}
	public float zoomProgressForTesting() {
		return transitionAge < 0 ? 0.0F : Math.clamp(
				(transitionAge - TEXT_FADE_TICKS) / (float) ZOOM_TICKS, 0.0F, 1.0F);
	}
	public boolean acknowledgementAvailableForTesting() {
		return acknowledgementButton != null && acknowledgementButton.visible && acknowledgementButton.active;
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
		NoticeCopyLayout copy = noticeCopyLayout(grid);
		return layout.width() >= Math.min(MIN_PANEL_WIDTH, width)
				&& layout.height() >= Math.min(MIN_PANEL_HEIGHT, height)
				&& layout.left() >= 0 && layout.top() >= 0
				&& layout.right() <= width && layout.bottom() <= height
				&& copy.height() <= grid.contentHeight()
				&& grid.contentBottom() + NORMAL_LINE_HEIGHT <= grid.footerY();
	}
	public boolean allElementsAlignedForTesting() {
		NoticeGrid grid = NoticeGrid.from(layout());
		return grid.statusLeft() == grid.titleLeft()
				&& grid.titleLeft() == grid.bodyLeft()
				&& grid.bodyLeft() == grid.safetyLeft()
				&& grid.slotLeft() < grid.textLeft()
				&& grid.textRight() < grid.slotRight()
				&& grid.leftColumnLeft() == grid.textLeft()
				&& grid.rightColumnLeft() > grid.leftColumnLeft() + grid.columnWidth()
				&& grid.rightColumnLeft() + grid.columnWidth() == grid.textRight()
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
				&& grid.statusY() >= safeTop
				&& grid.contentTop() > grid.headerRuleY()
				&& grid.contentBottom() < grid.footerY()
				&& grid.footerY() + NORMAL_LINE_HEIGHT < grid.buttonY()
				&& grid.buttonY() + BUTTON_HEIGHT <= safeBottom;
	}
	public boolean avoidsOrphanPunctuationForTesting() {
		NoticeLayout layout = layout();
		NoticeGrid grid = NoticeGrid.from(layout);
		NoticeCopyLayout copy = noticeCopyLayout(grid);
		for (NoticeSection section : copy.sections())
			for (String line : section.lines())
				if (!line.isEmpty() && isClosingPunctuation(line.charAt(0))) return false;
		return true;
	}
	public void reinitializeForTesting() { rebuildWidgets(); }
	public void acknowledgeForTesting() { acknowledge(); }

	private void acknowledge() {
		if (!acknowledgementAvailableForTesting() || transitionAge >= 0) return;
		transitionAge = 0;
		updateButtonState();
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

	private record GlassBounds(int left, int top, int right, int bottom) {
		private int width() { return right - left; }
		private int height() { return bottom - top; }
	}

	private record NoticeSection(Component heading, List<String> lines) { }

	private record NoticeCopyLayout(List<NoticeSection> left, List<NoticeSection> right,
			float bodyScale, int height) {
		private List<NoticeSection> sections() {
			List<NoticeSection> sections = new ArrayList<>(left);
			sections.addAll(right);
			return sections;
		}
	}

	/** Asset-space anchors keep every overlay on one continuous CRT glass grid. */
	private record NoticeGrid(
			int slotLeft, int slotRight, int textLeft, int textRight,
			int statusY,
			int titleY, int eyebrowY, int headerRuleY,
			int contentTop, int contentBottom,
			int footerY, int buttonLeft, int buttonRight, int buttonY) {
		private static NoticeGrid from(NoticeLayout layout) {
			return new NoticeGrid(
					x(layout, GLASS_SAFE_LEFT_ASSET), x(layout, GLASS_SAFE_RIGHT_ASSET),
					x(layout, 170), x(layout, 1450),
					y(layout, 126),
					y(layout, 185), y(layout, 230), y(layout, 274),
					y(layout, 300), y(layout, 688),
					y(layout, 742), x(layout, 520), x(layout, 1100), y(layout, 788));
		}

		private static int x(NoticeLayout layout, int assetX) {
			return layout.left() + Math.round(assetX * layout.width() / (float) UI_TEXTURE_WIDTH);
		}

		private static int y(NoticeLayout layout, int assetY) {
			return layout.top() + Math.round(assetY * layout.height() / (float) UI_TEXTURE_HEIGHT);
		}

		private int centerX() { return (slotLeft + slotRight) / 2; }
		private int textWidth() { return textRight - textLeft; }
		private int contentHeight() { return contentBottom - contentTop; }
		private int columnGap() { return Math.max(8, spanBetween(textLeft, textRight) / 26); }
		private int columnWidth() { return (textWidth() - columnGap()) / 2; }
		private int leftColumnLeft() { return textLeft; }
		private int rightColumnLeft() { return textRight - columnWidth(); }
		private int columnDividerX() { return (textLeft + textRight) / 2; }
		private int buttonWidth() { return buttonRight - buttonLeft; }
		private int statusLeft() { return textLeft; }
		private int titleLeft() { return textLeft; }
		private int bodyLeft() { return textLeft; }
		private int safetyLeft() { return textLeft; }

		private static int spanBetween(int left, int right) { return right - left; }
	}
}
