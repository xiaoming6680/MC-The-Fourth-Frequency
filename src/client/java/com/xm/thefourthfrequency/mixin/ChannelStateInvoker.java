package com.xm.thefourthfrequency.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads OpenAL's source state without exposing or retaining the native source id. */
@Mixin(Channel.class)
public interface ChannelStateInvoker {
	@Invoker("getState")
	int thefourthfrequency$getState();
}
