package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.entity.ReworkEntity;
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
import net.minecraft.world.phys.AABB;

public final class ReworkBodyRenderer
		extends MobRenderer<ReworkEntity, ReworkBodyRenderState, ReworkBodyModel> {
	public static final ModelLayerLocation STAGE_1_LAYER = layer(1);
	public static final ModelLayerLocation STAGE_2_LAYER = layer(2);
	public static final ModelLayerLocation STAGE_3_LAYER = layer(3);
	public static final ModelLayerLocation STAGE_4_LAYER = layer(4);
	public static final ModelLayerLocation STAGE_5_LAYER = layer(5);
	private static final Identifier[] TEXTURES = new Identifier[5];
	private static final Identifier[] EMISSIVE_TEXTURES = new Identifier[2];
	private static final float[] SHADOWS = {0.28F, 0.34F, 0.40F, 0.47F, 0.54F};
	private static final double[] CULL_HORIZONTAL = {0.85, 1.05, 1.30, 1.58, 1.88};
	private static final double[] CULL_UP = {0.62, 0.78, 0.96, 1.18, 1.42};

	static {
		for (int stage = 1; stage <= 5; stage++) {
			TEXTURES[stage - 1] = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
					"textures/entity/rework_body_stage_" + stage + ".png");
		}
		EMISSIVE_TEXTURES[0] = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
				"textures/entity/rework_body_stage_4_emissive.png");
		EMISSIVE_TEXTURES[1] = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
				"textures/entity/rework_body_stage_5_emissive.png");
	}

	private final ReworkBodyModel[] models;

	public ReworkBodyRenderer(EntityRendererProvider.Context context) {
		super(context, new ReworkBodyModel(context.bakeLayer(STAGE_1_LAYER), 1), SHADOWS[0]);
		models = new ReworkBodyModel[] {
				model,
				new ReworkBodyModel(context.bakeLayer(STAGE_2_LAYER), 2),
				new ReworkBodyModel(context.bakeLayer(STAGE_3_LAYER), 3),
				new ReworkBodyModel(context.bakeLayer(STAGE_4_LAYER), 4),
				new ReworkBodyModel(context.bakeLayer(STAGE_5_LAYER), 5)
		};
		addLayer(new EmissiveLayer(this));
	}

	private static ModelLayerLocation layer(int stage) {
		return new ModelLayerLocation(Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
				"rework_body_stage_" + stage), "main");
	}

	@Override
	public ReworkBodyRenderState createRenderState() {
		return new ReworkBodyRenderState();
	}

	@Override
	public void extractRenderState(ReworkEntity entity, ReworkBodyRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.formStage = Math.clamp(entity.formStage(), 1, 5);
		state.morphTargetStage = Math.clamp(entity.morphTargetStage(), 1, 5);
		state.morphTicks = Math.clamp(entity.morphTicks(), 0, ReworkEntity.MORPH_DURATION_TICKS);
	}

	@Override
	public void submit(ReworkBodyRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		model = models[Math.clamp(state.formStage, 1, 5) - 1];
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(ReworkBodyRenderState state) {
		return TEXTURES[Math.clamp(state.formStage, 1, 5) - 1];
	}

	@Override
	protected float getShadowRadius(ReworkBodyRenderState state) {
		return SHADOWS[Math.clamp(state.formStage, 1, 5) - 1];
	}

	@Override
	protected AABB getBoundingBoxForCulling(ReworkEntity entity) {
		int stage = Math.clamp(Math.max(entity.formStage(), entity.morphTargetStage()), 1, 5);
		AABB physical = super.getBoundingBoxForCulling(entity);
		double horizontal = CULL_HORIZONTAL[stage - 1];
		return new AABB(physical.minX - horizontal, physical.minY - 0.12, physical.minZ - horizontal,
				physical.maxX + horizontal, physical.maxY + CULL_UP[stage - 1], physical.maxZ + horizontal);
	}

	private static final class EmissiveLayer extends RenderLayer<ReworkBodyRenderState, ReworkBodyModel> {
		private EmissiveLayer(ReworkBodyRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
				ReworkBodyRenderState state, float yRot, float xRot) {
			if (state.formStage < 4 || state.isInvisible) return;
			float wave = (MthBridge.sin(state.ageInTicks * ((float) Math.PI * 2.0F / 80.0F)) + 1.0F) * 0.5F;
			float alpha = (state.formStage == 4 ? 0.88F : 0.94F) + wave * 0.05F;
			float strength = (state.formStage == 4 ? 0.82F : 0.88F) + wave * 0.04F;
			int color = ARGB.colorFromFloat(alpha, strength, strength * 0.82F, strength * 0.76F);
			Identifier texture = EMISSIVE_TEXTURES[state.formStage - 4];
			collector.order(1).submitModel(getParentModel(), state, poseStack,
					RenderTypes.entityTranslucentEmissive(texture), LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
		}
	}

	/** Keeps the renderer's pulse expression isolated from model animation imports. */
	private static final class MthBridge {
		private static float sin(float value) { return (float) Math.sin(value); }
	}
}
