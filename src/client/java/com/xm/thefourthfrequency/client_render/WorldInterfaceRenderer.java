package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.WorldInterfacePresentationController;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

public final class WorldInterfaceRenderer extends MobRenderer<WorldInterfaceEntity,
		WorldInterfaceRenderState, WorldInterfaceModel> {
	/** Base plus emissive; all form density remains inside these two model submissions. */
	public static final int MAX_RENDER_LAYERS = 2;
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "world_interface"), "main");
	private static final Identifier[] BASE = textures("");
	private static final Identifier[] EMISSIVE = textures("_emissive");
	private static final Identifier[] HIT = textures("_hit");
	private static final Identifier BLACK = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
			"textures/entity/world_interface_form_3_black.png");

	public WorldInterfaceRenderer(EntityRendererProvider.Context context) {
		super(context, new WorldInterfaceModel(context.bakeLayer(MODEL_LAYER)), 4.0F);
		addLayer(new EyeGlowLayer(this));
	}

	private static Identifier[] textures(String suffix) {
		Identifier[] result = new Identifier[3];
		for (int index = 0; index < result.length; index++) {
			result[index] = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
					"textures/entity/world_interface_form_" + (index + 1) + suffix + ".png");
		}
		return result;
	}

	@Override
	public WorldInterfaceRenderState createRenderState() {
		return new WorldInterfaceRenderState();
	}

	@Override
	public void extractRenderState(WorldInterfaceEntity entity, WorldInterfaceRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.form = Math.clamp(entity.form(), 0, 2);
		state.actionId = entity.actionId();
		long now = entity.level().getGameTime();
		state.actionAgeMillis = (long) (Math.max(0.0D,
				now - entity.actionStartTick() + partialTick) * 50.0D);
		state.blackened = state.actionId == 14;
		state.hasRedOverlay |= WorldInterfacePresentationController.isDamageFlashActive(entity.getUUID(), now);
	}

	@Override
	protected void scale(WorldInterfaceRenderState state, PoseStack poseStack) {
		float scale = switch (state.form) {
			case 1 -> 10.0F;
			case 2 -> 16.0F;
			default -> 5.8F;
		};
		poseStack.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTextureLocation(WorldInterfaceRenderState state) {
		return state.blackened ? BLACK : BASE[Math.clamp(state.form, 0, 2)];
	}

	@Override
	protected AABB getBoundingBoxForCulling(WorldInterfaceEntity entity) {
		return super.getBoundingBoxForCulling(entity).inflate(16.0 + entity.form() * 12.0, 12.0, 16.0);
	}

	private static final class EyeGlowLayer extends RenderLayer<WorldInterfaceRenderState, WorldInterfaceModel> {
		private EyeGlowLayer(WorldInterfaceRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
				WorldInterfaceRenderState state, float yRot, float xRot) {
			if (state.isInvisible || state.blackened) return;
			float wave = ((float) Math.sin(state.ageInTicks * 0.13F) + 1.0F) * 0.5F;
			int form = Math.clamp(state.form, 0, 2);
			Identifier overlay = state.hasRedOverlay ? HIT[form] : EMISSIVE[form];
			int color = state.hasRedOverlay ? ARGB.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F)
					: ARGB.colorFromFloat(0.78F + wave * 0.18F, 0.64F, 0.18F, 0.96F);
			collector.order(1).submitModel(getParentModel(), state, poseStack,
					RenderTypes.entityTranslucentEmissive(overlay),
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null,
					state.outlineColor, null);
		}
	}
}
