package com.xm.thefourthfrequency.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import com.xm.thefourthfrequency.client_ui.PersistentAlphaLoadingStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlaySuppressionMixin {
	@Shadow private float currentProgress;
	@Unique private boolean thefourthfrequency$hiddenResourceReload;
	@Unique private boolean thefourthfrequency$persistentAlphaStyle;
	@Unique private boolean thefourthfrequency$persistentAlphaFirstFrameRecorded;
	@Unique private Minecraft thefourthfrequency$client;

	@Inject(method = "registerTextures", at = @At("TAIL"))
	private static void thefourthfrequency$registerPersistentAlphaLogo(TextureManager textureManager,
			CallbackInfo callback) {
		PersistentAlphaLoadingStyle.registerTexture(textureManager);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void thefourthfrequency$markSessionReload(Minecraft client, ReloadInstance reload,
			Consumer<Optional<Throwable>> onFinish, boolean fadeIn, CallbackInfo callback) {
		thefourthfrequency$client = client;
		thefourthfrequency$hiddenResourceReload =
				AlphaLoadSessionController.consumeResourceReloadAnimationSuppression();
		thefourthfrequency$persistentAlphaStyle =
				AlphaLoadSessionController.shouldUsePersistentAlphaLoadingStyle();
		if (thefourthfrequency$persistentAlphaStyle) {
			AlphaLoadSessionController.recordPersistentAlphaLoadingOverlayCreated();
		}
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void thefourthfrequency$hideSessionReloadAnimation(GuiGraphics graphics, int mouseX,
			int mouseY, float partialTick, CallbackInfo callback) {
		if (thefourthfrequency$persistentAlphaStyle
				&& !thefourthfrequency$persistentAlphaFirstFrameRecorded) {
			thefourthfrequency$persistentAlphaFirstFrameRecorded = true;
			AlphaLoadSessionController.recordPersistentAlphaLoadingFirstFrame();
		}
		if (!thefourthfrequency$hiddenResourceReload) return;
		Screen screen = thefourthfrequency$client.screen;
		if (screen != null) screen.render(graphics, mouseX, mouseY, partialTick);
		graphics.enableScissor(0, 0, 0, 0);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorTexture(Lcom/mojang/blaze3d/textures/GpuTexture;I)V"))
	private void thefourthfrequency$keepUnderlyingScreen(CommandEncoder encoder, GpuTexture texture,
			int color) {
		if (!thefourthfrequency$hiddenResourceReload) encoder.clearColorTexture(texture, color);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int thefourthfrequency$usePersistentAlphaBackground(IntSupplier supplier) {
		return thefourthfrequency$persistentAlphaStyle
				? PersistentAlphaLoadingStyle.BACKGROUND_COLOR : supplier.getAsInt();
	}

	@ModifyArg(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"),
			index = 1)
	private Identifier thefourthfrequency$usePersistentAlphaLogo(Identifier original) {
		return thefourthfrequency$persistentAlphaStyle
				? PersistentAlphaLoadingStyle.LOGO_TEXTURE : original;
	}

	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$drawPersistentAlphaProgress(GuiGraphics graphics, int left,
			int top, int right, int bottom, float alpha, CallbackInfo callback) {
		if (!thefourthfrequency$persistentAlphaStyle) return;
		PersistentAlphaLoadingStyle.drawProgressBar(graphics, left, top, right, bottom, alpha,
				currentProgress);
		callback.cancel();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void thefourthfrequency$restoreSessionReloadDrawing(GuiGraphics graphics, int mouseX,
			int mouseY, float partialTick, CallbackInfo callback) {
		if (thefourthfrequency$hiddenResourceReload) graphics.disableScissor();
	}
}
