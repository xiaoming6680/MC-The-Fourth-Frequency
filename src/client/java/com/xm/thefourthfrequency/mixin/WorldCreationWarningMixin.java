package com.xm.thefourthfrequency.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives the mod's experimental world-generation lifecycle warning an explicit description of the
 * irreversible save changes while preserving the vanilla title key and confirmation flow.
 *
 * <p>Fabric Client GameTest identifies this screen from the vanilla title translation key before
 * automatically confirming it. Replacing that key leaves every world-building client test blocked
 * on the warning screen.</p>
 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldCreationWarningMixin {
	private static final String VANILLA_EXPERIMENTAL_QUESTION = "selectWorld.warning.experimental.question";

	@Redirect(method = "confirmWorldCreation", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
	private static MutableComponent thefourthfrequency$replaceExperimentalWarning(String translationKey) {
		return Component.translatable(VANILLA_EXPERIMENTAL_QUESTION.equals(translationKey)
				? "screen.thefourthfrequency.world_creation_warning.question"
				: translationKey);
	}
}
