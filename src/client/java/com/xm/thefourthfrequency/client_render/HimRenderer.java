package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.entity.HimEntity;
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

/**
 * Draws the figure world-lit, with the eyes as the one thing that carries its own light.
 *
 * <p>The eyes are the whole tell. Everything else about the silhouette is ordinary enough to be
 * mistaken for a player at distance, and the two blank lit rectangles are what turn a glance into a
 * question. They are emissive at a constant strength rather than gated on being looked at the way
 * the watcher's are: this figure gets a fifth of a second, and a reveal that ramps up over that
 * window would never finish.
 */
public final class HimRenderer extends MobRenderer<HimEntity, HimRenderState, HimModel> {
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "him"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/him.png");
	private static final Identifier EMISSIVE_TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/him_emissive.png");

	public HimRenderer(EntityRendererProvider.Context context) {
		super(context, new HimModel(context.bakeLayer(MODEL_LAYER)), 0.28F);
		// A hard shadow says "solid object, definitely present". Kept faint for the same reason the
		// figure makes no sound on its way out.
		shadowStrength = 0.3F;
		addLayer(new EyeLayer(this));
	}

	@Override
	public HimRenderState createRenderState() {
		return new HimRenderState();
	}

	@Override
	public Identifier getTextureLocation(HimRenderState state) {
		return TEXTURE;
	}

	private static final class EyeLayer extends RenderLayer<HimRenderState, HimModel> {
		private EyeLayer(HimRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
				HimRenderState state, float yRot, float xRot) {
			if (state.isInvisible) return;
			collector.order(1).submitModel(getParentModel(), state, poseStack,
					RenderTypes.entityTranslucentEmissive(EMISSIVE_TEXTURE),
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					ARGB.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F), null, state.outlineColor, null);
		}
	}
}
