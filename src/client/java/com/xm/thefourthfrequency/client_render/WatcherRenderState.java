package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Client-only presentation data; watcher behavior remains server authoritative. */
public final class WatcherRenderState extends LivingEntityRenderState {
	public int entityId;
	/** 0 while the eye is unobserved, 1 once the local player has held it in view. */
	public float gazeProgress;
}
