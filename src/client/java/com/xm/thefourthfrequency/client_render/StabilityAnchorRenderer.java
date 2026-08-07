package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.WorldInterfaceClientState;
import com.xm.thefourthfrequency.entity.StabilityAnchorEntity;
import com.xm.thefourthfrequency.entity.StabilityAnchorGeometry;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws one stability anchor: a world-lit structure plus a small emissive pass carrying its two
 * cores and four gold seams.
 *
 * <p>A plain {@code EntityRenderer} rather than a {@code MobRenderer}: the anchor is not a living
 * entity, has no head, no yaw to track and no shadow worth casting. The model is authored in the
 * usual Y-down entity space, so the only setup this does is the standard flip.
 */
public final class StabilityAnchorRenderer
		extends EntityRenderer<StabilityAnchorEntity, StabilityAnchorRenderState> {
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "stability_anchor"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/stability_anchor.png");
	private static final Identifier EMISSIVE_TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/stability_anchor_emissive.png");
	/** Blocks the drawn geometry reaches past the collision box; the claws hang below the origin. */
	private static final double CULLING_MARGIN = 1.0D;

	private final StabilityAnchorModel model;

	public StabilityAnchorRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new StabilityAnchorModel(context.bakeLayer(MODEL_LAYER));
		shadowRadius = 0.6F;
		shadowStrength = 0.35F;
	}

	@Override
	public StabilityAnchorRenderState createRenderState() {
		return new StabilityAnchorRenderState();
	}

	@Override
	public void extractRenderState(StabilityAnchorEntity entity, StabilityAnchorRenderState state,
			float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.anchorIndex = entity.anchorIndex();
		state.collapseAge = entity.collapseAge(partialTick);
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		state.paletteBand = WorldInterfacePalette.band(encounter == null ? null : encounter.stage());
	}

	@Override
	public void submit(StabilityAnchorRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (!state.isInvisible) {
			poseStack.pushPose();
			// Model space is Y-down, as everywhere else in this mod; y = 0 is the entity origin, which
			// is the top face of the bedrock cap the claws grip.
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			model.setupAnim(state);
			collector.submitModel(model, state, poseStack, RenderTypes.entityCutoutNoCull(TEXTURE),
					state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
			model.submitEmissive(poseStack, collector.order(1),
					RenderTypes.entityTranslucentEmissive(EMISSIVE_TEXTURE), coreColor(state),
					state.outlineColor);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	/**
	 * How bright the cores burn.
	 *
	 * <p>A slow breath at rest and a single hard flare over the first two ticks of a collapse that
	 * then falls away with the structure. The
	 * breath is deliberately under a hertz and the flare happens once, so nothing here approaches the
	 * flicker ceiling the rest of the encounter is held to.
	 */
	private static int coreColor(StabilityAnchorRenderState state) {
		float breath = 0.86F + 0.14F * Mth.sin(state.ageInTicks * 0.075F);
		float collapse = state.collapseAge;
		float flare = 0.0F;
		float fade = 1.0F;
		if (collapse >= 0.0F) {
			if (collapse < StabilityAnchorGeometry.COLLAPSE_FRACTURE_END) {
				flare = 1.0F - collapse / StabilityAnchorGeometry.COLLAPSE_FRACTURE_END;
			}
			fade = StabilityAnchorGeometry.collapsePresence(collapse);
		}
		float alpha = Math.clamp(breath * fade + flare * 0.9F, 0.0F, 1.0F);
		// Platinum, warmed toward the encounter's own escalation band as the fight goes on: the
		// anchors belong to the same colour language as the tethers they are holding.
		int band = Math.clamp(state.paletteBand, 0, WorldInterfacePalette.PHASE_BAND_COUNT - 1);
		float warmth = band * 0.10F;
		return ARGB.colorFromFloat(alpha, 1.0F,
				Math.clamp(0.94F - warmth * 0.5F, 0.0F, 1.0F),
				Math.clamp(0.80F - warmth, 0.0F, 1.0F));
	}

	@Override
	protected AABB getBoundingBoxForCulling(StabilityAnchorEntity entity) {
		// The claws reach a full block below the collision box to wrap the cap, and the relay core
		// stands above it; culling on the box alone popped the feet out at grazing angles.
		return super.getBoundingBoxForCulling(entity).inflate(CULLING_MARGIN);
	}
}
