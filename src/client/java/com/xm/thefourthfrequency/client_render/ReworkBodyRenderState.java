package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class ReworkBodyRenderState extends LivingEntityRenderState {
	public int formStage = 1;
	public int morphTargetStage = 1;
	public int morphTicks;
}
