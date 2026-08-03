package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class WorldInterfaceRenderState extends LivingEntityRenderState {
	public int form;
	public int actionId;
	public long actionAgeMillis;
	public boolean blackened;
	/** Escalation band from {@link WorldInterfacePalette}; drives the emissive colour and breath. */
	public int paletteBand;
	/** 1.0 at full virtual health, 0.0 at none; drives how far the shell has come apart. */
	public float healthFraction = 1.0F;
	/** 0..1 through the current action clip, or -1 when the interface is idle. */
	public float actionCharge = -1.0F;
}
