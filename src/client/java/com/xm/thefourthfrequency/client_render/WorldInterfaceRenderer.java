package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.WorldInterfaceClientState;
import com.xm.thefourthfrequency.client_ui.WorldInterfacePresentationController;
import com.xm.thefourthfrequency.entity.WorldInterfaceAnatomy;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
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
	/** Base, emissive and headroom for additional passes; density is no longer capped at two. */
	public static final int MAX_RENDER_LAYERS = 6;
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "world_interface"), "main");
	/** Shared with the server so beam origins and damage geometry cannot drift apart. */
	private static final float[] FORM_SCALE = WorldInterfaceAnatomy.FORM_SCALE;
	/** WorldInterfaceProtocol.BossAction MORPH_TO_SECOND / MORPH_TO_THIRD, and their 60-tick window. */
	private static final int MORPH_TO_SECOND_ACTION = 11;
	private static final int MORPH_TO_THIRD_ACTION = 12;
	private static final long MORPH_ACTION_MILLIS = 3_000L;
	private static final float MORPH_PINCH = 0.72F;
	/** Every action clip telegraphs inside its first two seconds; the glow tracks that window. */
	private static final long ACTION_CHARGE_MILLIS = 2_000L;
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
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		state.paletteBand = WorldInterfacePalette.band(encounter == null ? null : encounter.stage());
		state.healthFraction = encounter == null || encounter.maxHealth() <= 0.0F ? 1.0F
				: Math.clamp(encounter.currentHealth() / encounter.maxHealth(), 0.0F, 1.0F);
		state.actionCharge = state.actionId > 0 && state.actionAgeMillis <= ACTION_CHARGE_MILLIS
				? Math.clamp(state.actionAgeMillis / (float) ACTION_CHARGE_MILLIS, 0.0F, 1.0F)
				: -1.0F;
	}

	@Override
	protected void scale(WorldInterfaceRenderState state, PoseStack poseStack) {
		float scale = FORM_SCALE[Math.clamp(state.form, 0, FORM_SCALE.length - 1)];
		float morph = morphProgress(state);
		if (morph >= 0.0F) {
			// The server flips the form on one tick, so the silhouette used to jump between two
			// frames. Pinching the body shut at the midpoint and drawing the new one back out of
			// it means the swap happens where there is almost nothing on screen to see swap.
			float previous = FORM_SCALE[Math.clamp(state.form - 1, 0, FORM_SCALE.length - 1)];
			float pinch = 1.0F - MORPH_PINCH * (float) Math.sin(morph * Math.PI);
			float eased = morph * morph * (3.0F - 2.0F * morph);
			scale = (previous + (scale - previous) * eased) * pinch;
		}
		poseStack.scale(scale, scale, scale);
	}

	/** Progress through a morph action, or -1 when the boss is not currently changing form. */
	private static float morphProgress(WorldInterfaceRenderState state) {
		if (state.actionId != MORPH_TO_SECOND_ACTION && state.actionId != MORPH_TO_THIRD_ACTION) {
			return -1.0F;
		}
		float progress = state.actionAgeMillis / (float) MORPH_ACTION_MILLIS;
		return progress < 0.0F || progress > 1.0F ? -1.0F : progress;
	}

	@Override
	public Identifier getTextureLocation(WorldInterfaceRenderState state) {
		return state.blackened ? BLACK : BASE[Math.clamp(state.form, 0, 2)];
	}

	@Override
	protected AABB getBoundingBoxForCulling(WorldInterfaceEntity entity) {
		// Sized off the built silhouette rather than the hitbox: mass, halo and tentacles all reach
		// far outside the entity box. The vertical term was a flat twelve against a third form that
		// stands some forty blocks tall, so framing the tentacles used to cull the whole storm.
		int form = Math.clamp(entity.form(), 0, 2);
		return super.getBoundingBoxForCulling(entity)
				.inflate(16.0 + form * 14.0, 14.0 + form * 12.0, 16.0 + form * 8.0);
	}

	private static final class EyeGlowLayer extends RenderLayer<WorldInterfaceRenderState, WorldInterfaceModel> {
		private EyeGlowLayer(WorldInterfaceRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
				WorldInterfaceRenderState state, float yRot, float xRot) {
			if (state.isInvisible || state.blackened) return;
			int band = Math.clamp(state.paletteBand, 0, WorldInterfacePalette.PHASE_BAND_COUNT - 1);
			// Both the hue and the breath rate escalate with the band, so a player who never looks
			// at the HUD still sees the interface turn from patient purple to agitated red.
			float wave = ((float) Math.sin(state.ageInTicks * WorldInterfacePalette.breathSpeed(band))
					+ 1.0F) * 0.5F;
			int form = Math.clamp(state.form, 0, 2);
			// The glow used to breathe at a constant depth, which said nothing about what the
			// interface was doing. Winding an action up now visibly overdrives it, so the tell is
			// on the body itself rather than only in the HUD label.
			float charge = state.actionCharge < 0.0F ? 0.0F
					: state.actionCharge * state.actionCharge;
			if (state.hasRedOverlay) {
				// The damage flash has to reach plating and debris the glow never touches, so this
				// one still costs a whole second model submission. It lasts a few ticks.
				collector.order(1).submitModel(getParentModel(), state, poseStack,
						RenderTypes.entityTranslucentEmissive(HIT[form]),
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
						ARGB.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F), null, state.outlineColor, null);
				return;
			}
			int color = ARGB.colorFromFloat(Math.min(1.0F, 0.78F + wave * 0.18F + charge * 0.34F),
					WorldInterfacePalette.red(band),
					WorldInterfacePalette.green(band), WorldInterfacePalette.blue(band));
			// Only the bones that carry glow. The emissive sheet is under half a percent painted,
			// and the third form has several hundred parts to walk past to reach them.
			getParentModel().submitEmissive(poseStack, collector.order(1),
					RenderTypes.entityTranslucentEmissive(EMISSIVE[form]), color,
					state.outlineColor, form);
		}
	}
}
