package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.ArchivePasswordPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ArchivePasswordScreen extends Screen {
	private static final int PANEL_WIDTH = 300;
	private static final int PANEL_HEIGHT = 180;
	private final StringBuilder code = new StringBuilder(4);

	public ArchivePasswordScreen() {
		super(Component.translatable("screen.thefourthfrequency.archive_lock"));
	}

	@Override
	protected void init() {
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		for (int digit = 0; digit < 4; digit++) {
			int value = digit;
			addRenderableWidget(Button.builder(Component.translatable("button.thefourthfrequency.archive.digit." + digit),
					button -> appendDigit(value)).bounds(left + 34 + digit * 58, top + 92, 48, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("button.thefourthfrequency.archive.clear"),
				button -> code.setLength(0)).bounds(left + 34, top + 122, 106, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("button.thefourthfrequency.archive.submit"),
				button -> submit()).bounds(left + 160, top + 122, 106, 20).build());
	}

	private void appendDigit(int digit) {
		if (code.length() < 4) {
			code.append(digit);
		}
	}

	private void submit() {
		if (code.length() == 4 && ClientPlayNetworking.canSend(ArchivePasswordPayload.TYPE)) {
			ClientPlayNetworking.send(new ArchivePasswordPayload(code.toString()));
			onClose();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xC8080D10);
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111A1D);
		graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8A7048);
		graphics.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 16, 0xFFD8B56A);
		graphics.drawWordWrap(font, Component.translatable("screen.thefourthfrequency.archive_lock.hint"),
				left + 24, top + 38, PANEL_WIDTH - 48, 0xFF9EB6B8, false);
		String shown = code + "_".repeat(4 - code.length());
		graphics.drawCenteredString(font, Component.literal(shown), left + PANEL_WIDTH / 2, top + 73, 0xFF64D8E8);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
