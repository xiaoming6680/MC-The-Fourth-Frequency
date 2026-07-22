package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.entity.WorldInterfaceEnergyOrbEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;

/** Full-bright item renderer whose model follows the authoritative 1-to-6 block growth. */
public final class WorldInterfaceEnergyOrbRenderer
		extends ThrownItemRenderer<WorldInterfaceEnergyOrbEntity> {
	public WorldInterfaceEnergyOrbRenderer(EntityRendererProvider.Context context) {
		super(context, 1.0F, true);
	}

	@Override
	public WorldInterfaceEnergyOrbRenderState createRenderState() {
		return new WorldInterfaceEnergyOrbRenderState();
	}

	@Override
	public void extractRenderState(WorldInterfaceEnergyOrbEntity entity, ThrownItemRenderState state,
			float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		((WorldInterfaceEnergyOrbRenderState) state).scale =
				Math.clamp(entity.orbScale(), WorldInterfaceEnergyOrbEntity.MIN_SCALE,
						WorldInterfaceEnergyOrbEntity.MAX_SCALE);
	}

	@Override
	public void submit(ThrownItemRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		float scale = ((WorldInterfaceEnergyOrbRenderState) state).scale;
		poseStack.pushPose();
		poseStack.scale(scale, scale, scale);
		super.submit(state, poseStack, collector, camera);
		poseStack.popPose();
	}
}
