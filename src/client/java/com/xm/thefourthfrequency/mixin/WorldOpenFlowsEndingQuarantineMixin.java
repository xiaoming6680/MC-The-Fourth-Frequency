package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.ending.EndingWorldQuarantine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defense-in-depth guard for quick play and any path that bypasses the world-list buttons. */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsEndingQuarantineMixin {
	@Shadow @Final private Minecraft minecraft;

	@Inject(method = "openWorld", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$blockQuarantinedWorld(String levelId, Runnable returnAction,
			CallbackInfo ci) {
		if (!EndingWorldQuarantine.isQuarantined(levelId)) return;
		// Same success/failure wording split as the world list, so a save cannot be called sealed in
		// one place and damaged in the other depending on which route the player took to open it.
		String prefix = EndingWorldQuarantine.outcome(levelId).filter("SUCCESS"::equals).isPresent()
				? "selectWorld.thefourthfrequency.sealed"
				: "selectWorld.thefourthfrequency.corrupted";
		minecraft.setScreen(new AlertScreen(returnAction,
				Component.translatable(prefix),
				Component.translatable(prefix + ".details")));
		ci.cancel();
	}
}
