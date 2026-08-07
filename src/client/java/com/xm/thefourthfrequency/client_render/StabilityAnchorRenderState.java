package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Everything the anchor draws from, and nothing else: which slot it fills and how far through its
 * short collapse performance it is. The age is negative when the performance is not running, which
 * is the same sentinel the entity's synched clock uses.
 */
public final class StabilityAnchorRenderState extends EntityRenderState {
	public int anchorIndex;
	public float collapseAge = -1.0F;
	public int paletteBand;
}
