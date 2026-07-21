package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import com.xm.thefourthfrequency.entity.WatcherEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** World-lit native humanoid with a sparse, low-alpha eye-only emissive pass. */
public final class WatcherRenderer extends MobRenderer<WatcherEntity, WatcherRenderState, WatcherModel> {
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "watcher"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/watcher.png");
	private static final Identifier EMISSIVE_TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/watcher_emissive.png");

	public WatcherRenderer(EntityRendererProvider.Context context) {
		super(context, new WatcherModel(context.bakeLayer(MODEL_LAYER)), 0.22F);
		shadowStrength = 0.25F;
		addLayer(new EyeEmissiveLayer(this));
	}

	@Override
	public WatcherRenderState createRenderState() {
		return new WatcherRenderState();
	}

	@Override
	public void extractRenderState(WatcherEntity entity, WatcherRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.entityId = entity.getId();
	}

	@Override
	public void submit(WatcherRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		AnomalyPresentationController.onWatcherVisible(state.entityId, state.x, state.y, state.z);
	}

	@Override
	public Identifier getTextureLocation(WatcherRenderState state) {
		return TEXTURE;
	}

	@Override
	protected AABB getBoundingBoxForCulling(WatcherEntity entity) {
		AABB physical = super.getBoundingBoxForCulling(entity);
		return new AABB(physical.minX - 0.35D, physical.minY - 0.15D, physical.minZ - 0.35D,
				physical.maxX + 0.35D, physical.maxY + 0.15D, physical.maxZ + 0.35D);
	}

	private static final class EyeEmissiveLayer extends RenderLayer<WatcherRenderState, WatcherModel> {
		private EyeEmissiveLayer(WatcherRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
				WatcherRenderState state, float yRot, float xRot) {
			if (state.isInvisible) return;
			float wave = (Mth.sin(state.ageInTicks * ((float) Math.PI * 2.0F / 120.0F)) + 1.0F) * 0.5F;
			// The texture itself stays sparse and low-alpha; a near-opaque vertex tint keeps those
			// thin sclera edges and iris fibers legible against a truly black night sky.
			float alpha = 0.94F + wave * 0.06F;
			float strength = 0.96F + wave * 0.04F;
			int color = ARGB.colorFromFloat(alpha, strength, strength * 0.97F, strength * 0.88F);
			collector.order(1).submitModel(getParentModel(), state, poseStack,
					RenderTypes.entityTranslucentEmissive(EMISSIVE_TEXTURE), LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
		}
	}
}
