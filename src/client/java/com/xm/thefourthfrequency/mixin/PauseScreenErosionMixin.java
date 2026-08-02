package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.MenuErosionState;
import com.xm.thefourthfrequency.client_ui.PursuitPresentationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenErosionMixin {
	@Shadow private Button disconnectButton;
	@Unique private Component thefourthfrequency$originalDisconnectMessage;
	@Unique private boolean thefourthfrequency$originalDisconnectActive;

	@Inject(method = "init", at = @At("TAIL"))
	private void thefourthfrequency$erodeSingleplayerExit(CallbackInfo callback) {
		if (disconnectButton == null) return;
		thefourthfrequency$originalDisconnectMessage = disconnectButton.getMessage();
		thefourthfrequency$originalDisconnectActive = disconnectButton.active;
		thefourthfrequency$updateDisconnectButton();
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void thefourthfrequency$lockPursuitExit(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo callback) {
		thefourthfrequency$updateDisconnectButton();
	}

	@Unique
	private void thefourthfrequency$updateDisconnectButton() {
		if (disconnectButton == null || thefourthfrequency$originalDisconnectMessage == null) return;
		disconnectButton.setMessage(thefourthfrequency$originalDisconnectMessage);
		disconnectButton.active = thefourthfrequency$originalDisconnectActive;
		if (PursuitPresentationClient.locksPauseExit()) {
			disconnectButton.setMessage(Component.translatable(
					"message.thefourthfrequency.pursuit.exit_locked"));
			disconnectButton.active = false;
			return;
		}
		if (!Minecraft.getInstance().hasSingleplayerServer()) return;
		switch (MenuErosionState.stage()) {
			case MID -> disconnectButton.setMessage(Component.translatable(
					"message.thefourthfrequency.menu_erosion.escape_window"));
			case LATE -> disconnectButton.active = false;
			default -> { }
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void thefourthfrequency$lateNoise(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo callback) {
		if (!Minecraft.getInstance().hasSingleplayerServer()
				|| MenuErosionState.stage() != MenuErosionState.Stage.LATE) return;
		long phase = System.currentTimeMillis() / 90L;
		for (int row = 0; row < 9; row++) {
			int y = 34 + row * 18;
			int x = 10 + Math.floorMod((int) (phase * 31 + row * 67), Math.max(1, graphics.guiWidth() - 130));
			String noise = Integer.toHexString((int) (phase * 0x9E3779B9L + row * 0x45D9F3BL)).toUpperCase();
			graphics.drawString(Minecraft.getInstance().font, Component.literal(noise + "//" + noise),
					x, y, 0xAA8D2AB5, false);
		}
	}
}
