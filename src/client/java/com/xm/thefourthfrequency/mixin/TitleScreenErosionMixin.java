package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import com.xm.thefourthfrequency.client_ui.MenuErosionState;
import com.xm.thefourthfrequency.client_ui.FailureMenuLockState;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(TitleScreen.class)
public abstract class TitleScreenErosionMixin {
	private static final int VANILLA_SPLASH_YELLOW = 0xFFFF00;
	@Shadow private SplashRenderer splash;

	@Inject(method = "init", at = @At("TAIL"))
	private void thefourthfrequency$applyPersistentMenuIdentity(CallbackInfo callback) {
		// Every title-screen instance, including one recreated after leaving a world, keeps the same session slogan.
		splash = new SplashRenderer(Component.literal(MenuErosionState.sessionSplash())
				.withColor(VANILLA_SPLASH_YELLOW));
		for (var element : Screens.getButtons((TitleScreen) (Object) this)) {
			if (FailureMenuLockState.locked() && thefourthfrequency$isGameEntry(element.getMessage())) {
				element.active = false;
				element.setTooltip(Tooltip.create(Component.translatable(
						FailureMenuLockState.outcome() == com.xm.thefourthfrequency.networking.WorldInterfaceProtocol.Outcome.SUCCESS
								? "screen.thefourthfrequency.ending_menu_lock.success"
								: "screen.thefourthfrequency.ending_menu_lock.failure")));
				continue;
			}
			String label = element.getMessage().getString().toLowerCase(Locale.ROOT);
			if (label.contains("realms")) element.active = false;
		}
	}

	private static boolean thefourthfrequency$isGameEntry(Component message) {
		if (!(message.getContents() instanceof TranslatableContents translated)) return false;
		return switch (translated.getKey()) {
			case "menu.singleplayer", "menu.multiplayer", "menu.online" -> true;
			default -> false;
		};
	}

	@ModifyArg(method = "render", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
			index = 1)
	private String thefourthfrequency$alphaMenuVersion(String vanillaText) {
		return AlphaLoadSessionController.menuVersionText(vanillaText);
	}
}
