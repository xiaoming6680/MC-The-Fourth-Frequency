package com.xm.thefourthfrequency.mixin;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Reads the second, engine-owned multiplier applied after the player's category slider. */
@Mixin(SoundEngine.class)
public interface SoundEngineStateAccessor {
	@Accessor("gainBySource")
	Object2FloatMap<SoundSource> thefourthfrequency$gainBySource();

	@Accessor("instanceToChannel")
	Map<SoundInstance, ChannelAccess.ChannelHandle> thefourthfrequency$instanceToChannel();
}
