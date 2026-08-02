package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class WorldInterfaceRenderState extends LivingEntityRenderState {
	public int form;
	public int actionId;
	public long actionAgeMillis;
	public boolean blackened;
	/** Escalation band from {@link WorldInterfacePalette}; drives the emissive colour and breath. */
	public int paletteBand;
}
