package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.AltarSnapshotS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Shared, server-authoritative terminal sacrifice screen sized for 854x480 and larger. */
public final class ResonanceAltarScreen extends Screen {
	private static final int BACKGROUND = 0xEC07040D;
	private static final int PANEL = 0xF0120D1D;
	private static final int BORDER = 0xFF7C47A8;
	private static final int ACCENT = 0xFFD38BFF;
	private static final int GOLD = 0xFFFFD878;
	private static final int MUTED = 0xFF9D92AA;
	private static final int ROW = 0xCC1C132B;
	private static final int ROW_READY = 0xCC243221;

	private AltarSnapshotS2C snapshot;
	private Button terminalButton;
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
		int buttonY = layout.bottom() - 34;
		int primaryWidth = Math.max(116, Math.min(180, layout.width() / 3));
		terminalButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> sendTerminalAction())
				.bounds(layout.left() + 18, buttonY, primaryWidth, 20).build());
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
		if (terminalButton == null || cancelButton == null) return;
		int localIndex = localRosterIndex();
		boolean deposited = localIndex >= 0 && snapshot.deposited(localIndex);
		terminalButton.setMessage(Component.translatable(deposited
				? "button.thefourthfrequency.resonance_altar.withdraw"
				: "button.thefourthfrequency.resonance_altar.deposit"));
		boolean waiting = snapshot.stage() == WorldInterfaceProtocol.Stage.WAITING_TERMINALS;
		terminalButton.active = waiting && localIndex >= 0 && (snapshot.localEligible() || deposited);
		cancelButton.active = waiting && !snapshot.rosterIds().isEmpty();
	}

	private void sendTerminalAction() {
		int localIndex = localRosterIndex();
		if (localIndex < 0) return;
		WorldInterfaceProtocol.AltarAction action = snapshot.deposited(localIndex)
				? WorldInterfaceProtocol.AltarAction.WITHDRAW
				: WorldInterfaceProtocol.AltarAction.DEPOSIT;
		if (WorldInterfaceClientNetworking.sendAltarAction(snapshot, action)) {
			terminalButton.active = false;
			cancelButton.active = false;
		}
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
		graphics.drawCenteredString(font, title, width / 2, layout.top() + 14, ACCENT);
		graphics.drawCenteredString(font, Component.translatable(
				"screen.thefourthfrequency.resonance_altar.roster_locked"), width / 2,
				layout.top() + 30, MUTED);
		renderProgress(graphics, layout);
		renderRoster(graphics, layout);
		Component status = Component.translatable(snapshot.status().translationKey());
		graphics.drawCenteredString(font, status, width / 2, layout.bottom() - 49, GOLD);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderProgress(GuiGraphics graphics, Layout layout) {
		int ready = Integer.bitCount(snapshot.depositedMask());
		int total = snapshot.rosterIds().size();
		int left = layout.left() + 28;
		int right = layout.right() - 28;
		int top = layout.top() + 48;
		graphics.fill(left, top, right, top + 8, 0xFF241B2D);
		int filled = total == 0 ? 0 : (right - left) * ready / total;
		graphics.fill(left, top, left + filled, top + 8, 0xFF8E43C4);
		graphics.drawString(font, Component.literal(ready + " / " + total), left, top + 12, MUTED, false);
		String position = snapshot.altarPos().getX() + ", " + snapshot.altarPos().getY() + ", "
				+ snapshot.altarPos().getZ();
		graphics.drawString(font, Component.literal(position), right - font.width(position), top + 12, MUTED, false);
	}

	private void renderRoster(GuiGraphics graphics, Layout layout) {
		int count = snapshot.rosterIds().size();
		int columns = layout.width() >= 520 ? 2 : 1;
		int rows = Math.max(1, (count + columns - 1) / columns);
		int gap = 8;
		int areaLeft = layout.left() + 24;
		int areaRight = layout.right() - 24;
		int columnWidth = (areaRight - areaLeft - gap * (columns - 1)) / columns;
		int rowHeight = Math.min(28, Math.max(19, (layout.height() - 152) / rows));
		int startY = layout.top() + 78;
		int localIndex = localRosterIndex();
		for (int index = 0; index < count; index++) {
			int column = index / rows;
			int row = index % rows;
			int x = areaLeft + column * (columnWidth + gap);
			int y = startY + row * rowHeight;
			boolean deposited = snapshot.deposited(index);
			graphics.fill(x, y, x + columnWidth, y + rowHeight - 3, deposited ? ROW_READY : ROW);
			if (index == localIndex) graphics.renderOutline(x, y, columnWidth, rowHeight - 3, ACCENT);
			String marker = deposited ? "◆" : "◇";
			graphics.drawString(font, Component.literal(marker), x + 6, y + 5, deposited ? GOLD : MUTED, false);
			String name = snapshot.rosterNames().get(index);
			graphics.drawString(font, Component.literal(name), x + 22, y + 5, 0xFFE9E3EE, false);
			Component state = Component.translatable(deposited
					? "screen.thefourthfrequency.resonance_altar.roster.deposited"
					: "screen.thefourthfrequency.resonance_altar.roster.waiting");
			graphics.drawString(font, state, x + columnWidth - font.width(state) - 7,
					y + 5, deposited ? GOLD : MUTED, false);
		}
	}

	private Layout layout() {
		int panelWidth = Math.max(300, Math.min(640, width - 28));
		int panelHeight = Math.max(300, Math.min(370, height - 24));
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
