package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.WorldInterfaceVanillaPoemClient;
import com.xm.thefourthfrequency.networking.PoemStartS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses the real End poem screen while replacing only its encounter-authorized poem resource. */
@Mixin(WinScreen.class)
public abstract class WinScreenPoemMixin {
	@Shadow @Final @Mutable private Runnable onFinished;
	@Unique private PoemStartS2C thefourthfrequency$poem;
	@Unique private boolean thefourthfrequency$skipped;
	@Unique private boolean thefourthfrequency$completionStarted;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void thefourthfrequency$bindWorldInterfacePoem(boolean includesPoem, Runnable vanillaFinish,
			CallbackInfo callback) {
		thefourthfrequency$poem = WorldInterfaceVanillaPoemClient.claim(includesPoem);
		if (thefourthfrequency$poem == null) return;
		Runnable originalFinish = onFinished;
		onFinished = () -> {
			if (thefourthfrequency$completionStarted) return;
			thefourthfrequency$completionStarted = true;
			WorldInterfaceProtocol.PoemCompletion completion = thefourthfrequency$skipped
					? WorldInterfaceProtocol.PoemCompletion.SKIPPED
					: WorldInterfaceProtocol.PoemCompletion.READ;
			WorldInterfaceVanillaPoemClient.finish(thefourthfrequency$poem, completion, originalFinish,
					() -> thefourthfrequency$completionStarted = false);
		};
	}

	@ModifyArg(method = "init", at = @At(value = "INVOKE", ordinal = 0, target =
			"Lnet/minecraft/client/gui/screens/WinScreen;wrapCreditsIO(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/gui/screens/WinScreen$CreditsReader;)V"), index = 0)
	private Identifier thefourthfrequency$selectWorldInterfacePoem(Identifier vanillaPoem) {
		return thefourthfrequency$poem == null
				? vanillaPoem : WorldInterfaceVanillaPoemClient.poemResource(thefourthfrequency$poem);
	}

	@Inject(method = "onClose", at = @At("HEAD"))
	private void thefourthfrequency$rememberExplicitSkip(CallbackInfo callback) {
		if (thefourthfrequency$poem != null) thefourthfrequency$skipped = true;
	}
}
