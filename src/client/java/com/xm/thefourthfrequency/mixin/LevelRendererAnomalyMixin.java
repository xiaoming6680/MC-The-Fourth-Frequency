package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererAnomalyMixin {
	@Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
			at = @At("RETURN"), cancellable = true)
	private static void thefourthfrequency$removeHiddenBlockLight(BlockAndTintGetter level, BlockPos pos,
			CallbackInfoReturnable<Integer> callback) {
		thefourthfrequency$suppressHiddenBlockLight(pos, callback);
	}

	@Inject(method = "getLightColor(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;"
			+ "Lnet/minecraft/world/level/BlockAndTintGetter;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
			at = @At("RETURN"), cancellable = true)
	private static void thefourthfrequency$removeCompiledHiddenBlockLight(
			LevelRenderer.BrightnessGetter brightnessGetter, BlockAndTintGetter level, BlockState state,
			BlockPos pos, CallbackInfoReturnable<Integer> callback) {
		thefourthfrequency$suppressHiddenBlockLight(pos, callback);
	}

	private static void thefourthfrequency$suppressHiddenBlockLight(BlockPos pos,
			CallbackInfoReturnable<Integer> callback) {
		if (!AnomalyPresentationController.isBlockLightSuppressedAt(pos)) return;
		callback.setReturnValue(LightTexture.pack(0, LightTexture.sky(callback.getReturnValue())));
	}
}
