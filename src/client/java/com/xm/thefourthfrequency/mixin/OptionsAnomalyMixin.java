package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import com.xm.thefourthfrequency.client_ui.DimensionViewDistanceController;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsAnomalyMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void thefourthfrequency$lockInitialRenderDistance(Minecraft client, File optionsFile,
			CallbackInfo callback) {
		thefourthfrequency$forceLockedRenderDistance(client);
	}

	@Inject(method = "load", at = @At("RETURN"))
	private void thefourthfrequency$lockLoadedRenderDistance(CallbackInfo callback) {
		thefourthfrequency$forceLockedRenderDistance(Minecraft.getInstance());
	}

	@Inject(method = "save", at = @At("HEAD"))
	private void thefourthfrequency$lockSavedRenderDistance(CallbackInfo callback) {
		thefourthfrequency$forceLockedRenderDistance(Minecraft.getInstance());
	}

	@Inject(method = "getFinalSoundSourceVolume", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$muteAnomalyOutput(SoundSource source,
			CallbackInfoReturnable<Float> callback) {
		if (AnomalyPresentationController.isAudioMuted()) callback.setReturnValue(0.0F);
	}

	@Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
	private void thefourthfrequency$fixedRenderDistance(CallbackInfoReturnable<Integer> callback) {
		if (DimensionViewDistanceController.isLocked()) {
			callback.setReturnValue(DimensionViewDistanceController.lockedChunks(Minecraft.getInstance()));
		}
	}

	@Unique
	private void thefourthfrequency$forceLockedRenderDistance(Minecraft client) {
		if (DimensionViewDistanceController.isLocked()) {
			((Options) (Object) this).renderDistance().set(DimensionViewDistanceController.lockedChunks(client));
		}
	}
}
