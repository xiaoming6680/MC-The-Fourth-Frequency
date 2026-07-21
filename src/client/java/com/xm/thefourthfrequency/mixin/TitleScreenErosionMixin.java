package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import com.xm.thefourthfrequency.client_ui.MenuErosionState;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
	private static final Identifier CORRUPTED_BACKGROUND = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/gui/anomaly/title_corruption.png");
	@Shadow private SplashRenderer splash;

	@Inject(method = "init", at = @At("TAIL"))
	private void thefourthfrequency$applyBootMenu(CallbackInfo callback) {
		if (MenuErosionState.stage() != MenuErosionState.Stage.BOOT) return;
		int index = Math.floorMod((int) (System.nanoTime() >>> 8), MenuErosionState.BOOT_SPLASHES.size());
		// Preserve vanilla rotation/pulse while restoring Minecraft's classic splash yellow.
		splash = new SplashRenderer(Component.literal(MenuErosionState.BOOT_SPLASHES.get(index))
				.withColor(VANILLA_SPLASH_YELLOW));
		for (var element : Screens.getButtons((TitleScreen) (Object) this)) {
			String label = element.getMessage().getString().toLowerCase(Locale.ROOT);
			if (label.contains("realms")) element.active = false;
		}
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$purplePanorama(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo callback) {
		MenuErosionState.Stage stage = MenuErosionState.stage();
		if (stage != MenuErosionState.Stage.MID && stage != MenuErosionState.Stage.LATE) return;
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		graphics.blit(RenderPipelines.GUI_TEXTURED, CORRUPTED_BACKGROUND, 0, 0, 0.0F, 0.0F,
				width, height, 256, 128, 256, 128);
		callback.cancel();
	}

	@ModifyArg(method = "render", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
			index = 1)
	private String thefourthfrequency$alphaMenuVersion(String vanillaText) {
		return AlphaLoadSessionController.menuVersionText(vanillaText);
	}
}
