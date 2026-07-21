package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Client-only presentation data; watcher behavior remains server authoritative. */
public final class WatcherRenderState extends LivingEntityRenderState {
	public int entityId;
}
