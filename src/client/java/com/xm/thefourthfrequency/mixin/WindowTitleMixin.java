package com.xm.thefourthfrequency.mixin;

import com.mojang.blaze3d.platform.Window;
import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Holds the downgraded window title against every route that sets one.
 *
 * <p>{@code MinecraftTitleRetentionMixin} restores it after {@code updateTitle}, which is the only
 * place vanilla builds a title - but restoring <em>after</em> is a race with when that call happens.
 * Entering a world sets the title from inside the load, and any pass that lands while the corruption
 * is still marked in progress is left alone by that mixin and never revisited, so the window keeps
 * "Minecraft 1.21.11 - Singleplayer" for the rest of the session.
 *
 * <p>Rewriting the argument instead removes the timing question entirely: whatever asks for a title,
 * from wherever, gets the answer this client is supposed to give. The decision itself stays in
 * {@link AlphaLoadSessionController#overrideWindowTitle}, which passes requests straight through
 * while the downgrade sequence is still playing its own version stamps.
 */
@Mixin(Window.class)
public abstract class WindowTitleMixin {
	@ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
	private String thefourthfrequency$holdDowngradedTitle(String requested) {
		return AlphaLoadSessionController.overrideWindowTitle(requested);
	}
}
