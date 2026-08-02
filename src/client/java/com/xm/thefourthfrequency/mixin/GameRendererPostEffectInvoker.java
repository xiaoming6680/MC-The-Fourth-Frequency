package com.xm.thefourthfrequency.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererPostEffectInvoker {
	@Invoker("setPostEffect")
	void thefourthfrequency$setPostEffect(Identifier id);
}
