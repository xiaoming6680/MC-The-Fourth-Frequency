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
	@Unique private String thefourthfrequency$label;

	@Inject(method = "getInfo", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$replaceInfo(CallbackInfoReturnable<Component> cir) {
		if (thefourthfrequency$isQuarantined()) {
			cir.setReturnValue(Component.translatable(thefourthfrequency$label()));
		}
	}

	@Inject(method = "primaryActionMessage", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$replacePrimaryAction(CallbackInfoReturnable<Component> cir) {
		if (thefourthfrequency$isQuarantined()) {
			cir.setReturnValue(Component.translatable(thefourthfrequency$label()));
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

	/**
	 * A lost run leaves a damaged save; a won run leaves a sealed one. Both are equally unenterable,
	 * and the difference is only ever wording - the disabling above does not consult this at all, so
	 * an unreadable or pre-existing marker degrades to the damaged label rather than to an open save.
	 */
	@Unique
	private String thefourthfrequency$label() {
		if (thefourthfrequency$label == null) {
			thefourthfrequency$label = EndingWorldQuarantine.outcome(getLevelId())
					.filter("SUCCESS"::equals)
					.map(ignored -> "selectWorld.thefourthfrequency.sealed")
					.orElse("selectWorld.thefourthfrequency.corrupted");
		}
		return thefourthfrequency$label;
	}
}
