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

@Mixin(TitleScreen.class)
public abstract class TitleScreenErosionMixin {
	private static final int VANILLA_SPLASH_YELLOW = 0xFFFF00;
	private static final String REALMS_BUTTON_KEY = "menu.online";
	@Shadow private SplashRenderer splash;

	@Inject(method = "init", at = @At("TAIL"))
	private void thefourthfrequency$applyPersistentMenuIdentity(CallbackInfo callback) {
		// Every title-screen instance, including one recreated after leaving a world, keeps the same
		// session slogan until the server reports a new erosion stage.
		splash = new SplashRenderer(Component.translatable(MenuErosionState.sessionSplashKey())
				.withColor(VANILLA_SPLASH_YELLOW));
		for (var element : Screens.getButtons((TitleScreen) (Object) this)) {
			String key = thefourthfrequency$translationKey(element.getMessage());
			if (FailureMenuLockState.locked() && thefourthfrequency$isGameEntry(key)) {
				element.active = false;
				element.setTooltip(Tooltip.create(Component.translatable(
						FailureMenuLockState.outcome() == com.xm.thefourthfrequency.networking.WorldInterfaceProtocol.Outcome.SUCCESS
								? "screen.thefourthfrequency.ending_menu_lock.success"
								: "screen.thefourthfrequency.ending_menu_lock.failure")));
				continue;
			}
			// Keyed rather than label-matched, so Realms stays disabled in every language.
			if (REALMS_BUTTON_KEY.equals(key)) element.active = false;
		}
	}

	private static String thefourthfrequency$translationKey(Component message) {
		return message.getContents() instanceof TranslatableContents translated ? translated.getKey() : "";
	}

	private static boolean thefourthfrequency$isGameEntry(String translationKey) {
		return switch (translationKey) {
			case "menu.singleplayer", "menu.multiplayer", REALMS_BUTTON_KEY -> true;
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
