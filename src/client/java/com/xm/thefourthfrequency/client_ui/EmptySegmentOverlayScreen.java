package com.xm.thefourthfrequency.client_ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EmptySegmentOverlayScreen extends Screen {
	public EmptySegmentOverlayScreen() {
		super(Component.translatable("screen.thefourthfrequency.empty_segment.experience_gap"));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xFF000000);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		// The server-authoritative event end is the only close path.
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
