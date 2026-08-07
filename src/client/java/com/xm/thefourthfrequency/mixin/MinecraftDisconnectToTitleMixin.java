package com.xm.thefourthfrequency.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Leaving a world always lands on the title screen, however the world was joined.
 *
 * <p>Vanilla routes {@code Minecraft#disconnectFromWorld} three ways: a local server goes to the
 * title screen, a realm goes to the realms list, and everything else - which includes every client
 * of a LAN world, since only the host counts as local - goes to the server list. So in singleplayer
 * "quit" reached the menu and in multiplayer the same button dropped players onto a server browser
 * instead, with the mod's own title presentation skipped entirely.</p>
 *
 * <p>The redirect is deliberately on {@code setScreen} rather than an injection after the method
 * returns: the discarded screens are still constructed either way, but never being shown means
 * {@code JoinMultiplayerScreen} never runs {@code init()}, which is what would otherwise build the
 * server list and start its LAN pinger for a screen about to be thrown away.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDisconnectToTitleMixin {
	@Redirect(method = "disconnectFromWorld",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void thefourthfrequency$alwaysReturnToTitle(Minecraft client, Screen screen) {
		client.setScreen(screen instanceof TitleScreen ? screen : new TitleScreen());
	}
}
