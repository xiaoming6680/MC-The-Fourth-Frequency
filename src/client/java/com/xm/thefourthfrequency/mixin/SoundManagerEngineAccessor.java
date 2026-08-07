package com.xm.thefourthfrequency.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the engine state which the public sound-manager debug string deliberately omits. */
@Mixin(SoundManager.class)
public interface SoundManagerEngineAccessor {
	@Accessor("soundEngine")
	SoundEngine thefourthfrequency$soundEngine();
}
