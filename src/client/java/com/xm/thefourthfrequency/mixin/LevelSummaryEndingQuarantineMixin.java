package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.ending.EndingWorldQuarantine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes an ending save look damaged in the vanilla world list without touching its world data. */
@Mixin(LevelSummary.class)
public abstract class LevelSummaryEndingQuarantineMixin {
	@Shadow public abstract String getLevelId();
	@Unique private Boolean thefourthfrequency$quarantined;

	@Inject(method = "getInfo", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$replaceInfo(CallbackInfoReturnable<Component> cir) {
		if (thefourthfrequency$isQuarantined()) {
			cir.setReturnValue(Component.translatable("selectWorld.thefourthfrequency.corrupted"));
		}
	}

	@Inject(method = "primaryActionMessage", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$replacePrimaryAction(CallbackInfoReturnable<Component> cir) {
		if (thefourthfrequency$isQuarantined()) {
			cir.setReturnValue(Component.translatable("selectWorld.thefourthfrequency.corrupted"));
		}
	}

	@Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$markDisabled(CallbackInfoReturnable<Boolean> cir) {
		if (thefourthfrequency$isQuarantined()) cir.setReturnValue(true);
	}

	@Inject(method = "primaryActionActive", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$disablePrimaryAction(CallbackInfoReturnable<Boolean> cir) {
		if (thefourthfrequency$isQuarantined()) cir.setReturnValue(false);
	}

	@Inject(method = {"canUpload", "canEdit", "canRecreate"}, at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$disableAlternativeEntry(CallbackInfoReturnable<Boolean> cir) {
		if (thefourthfrequency$isQuarantined()) cir.setReturnValue(false);
	}

	@Unique
	private boolean thefourthfrequency$isQuarantined() {
		if (thefourthfrequency$quarantined == null) {
			thefourthfrequency$quarantined = EndingWorldQuarantine.isQuarantined(getLevelId());
		}
		return thefourthfrequency$quarantined;
	}
}
