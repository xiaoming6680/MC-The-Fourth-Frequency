package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.AltarSnapshotS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

/**
 * Shared, server-authoritative terminal sacrifice screen sized for 854x480 and larger.
 *
 * <p>This screen stopped being the place a terminal is handed over. Insertion is a held-item
 * interaction at the core now, so the deposit button is gone and the screen's job narrowed to the
 * two things a shared ritual actually needs: telling you what to do, and showing you who else has
 * done it. What is left is a standing instruction, a per-player progress ledger, and the one
 * action - withdrawal - that is still yours to take from here.</p>
 */
public final class ResonanceAltarScreen extends Screen {
	private static final int BACKGROUND = 0xEC07040D;
	private static final int PANEL = 0xF0120D1D;
	private static final int BORDER = 0xFF7C47A8;
	private static final int ACCENT = 0xFFD38BFF;
	private static final int GOLD = 0xFFFFD878;
	private static final int MUTED = 0xFF9D92AA;
	private static final int TEXT = 0xFFE9E3EE;
	private static final int ROW = 0xCC1C132B;
	private static final int ROW_READY = 0xCC243221;
	private static final int TRACK = 0xFF241B2D;
	private static final int FILL = 0xFF8E43C4;
	private static final int HINT_PANEL = 0x99241338;
	/** Rows the list shows before it stops growing; eight is the roster ceiling anyway. */
	private static final int MAX_ROSTER = WorldInterfaceProtocol.MAX_PARTICIPANTS;

	private AltarSnapshotS2C snapshot;
	private Button withdrawButton;
	private Button cancelButton;

	public ResonanceAltarScreen(AltarSnapshotS2C snapshot) {
		super(Component.translatable("screen.thefourthfrequency.resonance_altar.title"));
		this.snapshot = snapshot;
	}

	public boolean matches(UUID encounterId) {
		return snapshot.encounterId().equals(encounterId);
	}

	public void update(AltarSnapshotS2C update) {
		if (!matches(update.encounterId()) || update.revision() < snapshot.revision()) return;
		snapshot = update;
		if (minecraft != null) rebuildWidgets();
	}

	public void closeFromServer() {
		if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
	}

	@Override
	protected void init() {
		Layout layout = layout();
		int buttonY = layout.bottom() - 30;
		withdrawButton = addRenderableWidget(Button.builder(Component.translatable(
				"button.thefourthfrequency.resonance_altar.withdraw"), ignored -> sendWithdraw())
				.bounds(layout.left() + 18, buttonY, 110, 20).build());
		cancelButton = addRenderableWidget(Button.builder(Component.translatable(
				"button.thefourthfrequency.resonance_altar.cancel"), ignored ->
				WorldInterfaceClientNetworking.sendAltarAction(snapshot,
						WorldInterfaceProtocol.AltarAction.CANCEL))
				.bounds(layout.left() + (layout.width() - 100) / 2, buttonY, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(
				"button.thefourthfrequency.resonance_altar.close"), ignored -> onClose())
				.bounds(layout.right() - 78, buttonY, 60, 20).build());
		refreshButtons();
	}

	private void refreshButtons() {
		if (withdrawButton == null || cancelButton == null) return;
		boolean waiting = snapshot.stage() == WorldInterfaceProtocol.Stage.WAITING_TERMINALS;
		// Withdrawal is the only transfer this screen still performs, and only for someone who has
		// actually inserted something. Showing it otherwise implies an exchange that is not on offer.
		withdrawButton.visible = localDeposited();
		withdrawButton.active = waiting && withdrawButton.visible;
		cancelButton.active = waiting && !snapshot.rosterIds().isEmpty();
	}

	private void sendWithdraw() {
		if (!localDeposited()) return;
		if (WorldInterfaceClientNetworking.sendAltarAction(snapshot,
				WorldInterfaceProtocol.AltarAction.WITHDRAW)) {
			withdrawButton.active = false;
			cancelButton.active = false;
		}
	}

	private boolean localDeposited() {
		int localIndex = localRosterIndex();
		return localIndex >= 0 && snapshot.deposited(localIndex);
	}

	private int localRosterIndex() {
		if (minecraft == null || minecraft.player == null) return -1;
		return snapshot.rosterIds().indexOf(minecraft.player.getUUID());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, BACKGROUND);
		Layout layout = layout();
		graphics.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), PANEL);
		graphics.renderOutline(layout.left(), layout.top(), layout.width(), layout.height(), BORDER);
		graphics.fill(layout.left() + 1, layout.top() + 1, layout.right() - 1, layout.top() + 4, ACCENT);

		graphics.drawCenteredString(font, title, width / 2, layout.top() + 13, ACCENT);
		graphics.drawCenteredString(font, Component.translatable(
				"screen.thefourthfrequency.resonance_altar.roster_locked"), width / 2,
				layout.top() + 26, MUTED);

		int cursor = renderProgress(graphics, layout, layout.top() + 44);
		cursor = renderRoster(graphics, layout, cursor + 6);
		cursor = renderInstruction(graphics, layout, cursor + 6);
		renderStatus(graphics, layout, cursor + 5);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/**
	 * One segment per player rather than a continuous bar. A shared ritual's progress is a count of
	 * people, and a smooth fill makes three of eight look like a percentage instead of five players
	 * still standing there holding their terminals.
	 */
	private int renderProgress(GuiGraphics graphics, Layout layout, int top) {
		int ready = Integer.bitCount(snapshot.depositedMask());
		int total = snapshot.rosterIds().size();
		int left = layout.left() + 24;
		int right = layout.right() - 24;
		int height = 7;
		graphics.fill(left, top, right, top + height, TRACK);
		if (total > 0) {
			int gap = 2;
			int span = right - left;
			for (int index = 0; index < total; index++) {
				int segmentLeft = left + span * index / total;
				int segmentRight = left + span * (index + 1) / total - (index == total - 1 ? 0 : gap);
				graphics.fill(segmentLeft, top, segmentRight, top + height,
						snapshot.deposited(index) ? FILL : TRACK);
			}
		}
		Component counted = Component.translatable(
				"screen.thefourthfrequency.resonance_altar.progress", ready, total);
		graphics.drawString(font, counted, left, top + height + 4, ready == total && total > 0 ? GOLD : MUTED,
				false);
		String position = snapshot.altarPos().getX() + ", " + snapshot.altarPos().getY() + ", "
				+ snapshot.altarPos().getZ();
		graphics.drawString(font, Component.literal(position), right - font.width(position),
				top + height + 4, MUTED, false);
		return top + height + 4 + font.lineHeight;
	}

	private int renderRoster(GuiGraphics graphics, Layout layout, int top) {
		int count = snapshot.rosterIds().size();
		int columns = layout.width() >= 520 && count > 4 ? 2 : 1;
		int rows = Math.max(1, (Math.min(count, MAX_ROSTER) + columns - 1) / columns);
		int gap = 8;
		int areaLeft = layout.left() + 24;
		int areaRight = layout.right() - 24;
		int columnWidth = (areaRight - areaLeft - gap * (columns - 1)) / columns;
		int rowHeight = 20;
		int localIndex = localRosterIndex();
		for (int index = 0; index < Math.min(count, MAX_ROSTER); index++) {
			int column = index / rows;
			int row = index % rows;
			int x = areaLeft + column * (columnWidth + gap);
			int y = top + row * rowHeight;
			boolean deposited = snapshot.deposited(index);
			graphics.fill(x, y, x + columnWidth, y + rowHeight - 3, deposited ? ROW_READY : ROW);
			// A solid edge on your own row instead of an outline around it: the outline read as a
			// selection you could act on, which is exactly what this list is not.
			if (index == localIndex) graphics.fill(x, y, x + 2, y + rowHeight - 3, ACCENT);
			graphics.drawString(font, Component.literal(deposited ? "◆" : "◇"), x + 8, y + 4,
					deposited ? GOLD : MUTED, false);
			int nameX = x + 22;
			graphics.drawString(font, Component.literal(snapshot.rosterNames().get(index)), nameX, y + 4,
					TEXT, false);
			if (index == localIndex) {
				int nameWidth = font.width(snapshot.rosterNames().get(index));
				graphics.drawString(font, Component.translatable(
						"screen.thefourthfrequency.resonance_altar.roster.you"),
						nameX + nameWidth + 4, y + 4, ACCENT, false);
			}
			Component state = Component.translatable(deposited
					? "screen.thefourthfrequency.resonance_altar.roster.deposited"
					: "screen.thefourthfrequency.resonance_altar.roster.waiting");
			graphics.drawString(font, state, x + columnWidth - font.width(state) - 8, y + 4,
					deposited ? GOLD : MUTED, false);
		}
		return top + rows * rowHeight;
	}

	/**
	 * The standing instruction. Insertion happens at the block, not here, so the screen has to say
	 * so plainly and keep saying it — an empty panel with a disabled button was the previous
	 * answer, and it read as the ritual being broken rather than as it waiting on the player.
	 */
	private int renderInstruction(GuiGraphics graphics, Layout layout, int top) {
		if (snapshot.stage() != WorldInterfaceProtocol.Stage.WAITING_TERMINALS) return top;
		boolean deposited = localDeposited();
		Component hint = Component.translatable(deposited
				? "screen.thefourthfrequency.resonance_altar.inserted_hint"
				: "screen.thefourthfrequency.resonance_altar.insert_hint");
		int left = layout.left() + 24;
		int right = layout.right() - 24;
		List<FormattedCharSequence> lines = font.split(hint, right - left - 16);
		int panelHeight = lines.size() * font.lineHeight + 8;
		graphics.fill(left, top, right, top + panelHeight, HINT_PANEL);
		// Pulses only while it is still asking for something, so a satisfied panel goes quiet.
		int marker = deposited ? GOLD
				: 0xFF000000 | (Mth.hsvToRgb(0.78F, 0.55F,
						0.75F + 0.25F * Mth.sin(System.currentTimeMillis() / 260.0F)) & 0xFFFFFF);
		graphics.fill(left, top, left + 2, top + panelHeight, marker);
		int lineY = top + 4;
		for (FormattedCharSequence line : lines) {
			graphics.drawString(font, line, left + 10, lineY, deposited ? GOLD : TEXT, false);
			lineY += font.lineHeight;
		}
		return top + panelHeight;
	}

	/** Wrapped rather than one clipped line: several of these statuses are full sentences. */
	private void renderStatus(GuiGraphics graphics, Layout layout, int top) {
		Component status = Component.translatable(snapshot.status().translationKey());
		int available = layout.width() - 48;
		int ceiling = layout.bottom() - 36;
		for (FormattedCharSequence line : font.split(status, available)) {
			if (top + font.lineHeight > ceiling) return;
			graphics.drawString(font, line, layout.left() + (layout.width() - font.width(line)) / 2, top,
					GOLD, false);
			top += font.lineHeight;
		}
	}

	private Layout layout() {
		int panelWidth = Math.max(300, Math.min(640, width - 28));
		int panelHeight = Math.max(300, Math.min(390, height - 24));
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		return new Layout(left, top, panelWidth, panelHeight);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record Layout(int left, int top, int width, int height) {
		int right() { return left + width; }
		int bottom() { return top + height; }
	}
}
