package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import com.xm.thefourthfrequency.entity.WatcherEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * World-lit native humanoid whose eye-only emissive pass is gated on being looked at.
 * Unobserved it is a faceless silhouette; the reveal and the server's despawn are the same beat.
 */
public final class WatcherRenderer extends MobRenderer<WatcherEntity, WatcherRenderState, WatcherModel> {
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "watcher"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/watcher.png");
	private static final Identifier EMISSIVE_TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/watcher_emissive.png");
	/** Matches the server's 40-tick gaze window, so the eye finishes arriving as it is taken away. */
	private static final float REVEAL_PER_SECOND = 0.5F;
	private static final float FADE_PER_SECOND = 2.6F;
	private static final long GAZE_STALE_MILLIS = 5_000L;
	private static final long MAX_FRAME_MILLIS = 200L;
	private static final Map<Integer, GazeTrack> GAZE = new HashMap<>();

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
		state.gazeProgress = trackGaze(entity);
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

	/**
	 * Purely local presentation: the server keeps owning the real gaze counter and the discard, so
	 * this needs no networking and cannot desync anything that matters.
	 */
	private static float trackGaze(WatcherEntity entity) {
		long now = Util.getMillis();
		GAZE.values().removeIf(track -> now - track.updatedAt > GAZE_STALE_MILLIS);
		GazeTrack track = GAZE.computeIfAbsent(entity.getId(), id -> new GazeTrack());
		float elapsed = track.updatedAt == 0L ? 0.0F
				: Math.min(MAX_FRAME_MILLIS, now - track.updatedAt) / 1000.0F;
		track.updatedAt = now;
		boolean observed = isObserved(entity);
		float step = elapsed * (observed ? REVEAL_PER_SECOND : FADE_PER_SECOND);
		float target = observed ? 1.0F : 0.0F;
		track.progress = track.progress < target
				? Math.min(target, track.progress + step)
				: Math.max(target, track.progress - step);
		return track.progress;
	}

	private static boolean isObserved(WatcherEntity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return false;
		Vec3 towardWatcher = entity.position()
				.add(0.0, entity.getBbHeight() * WatcherEntity.GAZE_TARGET_HEIGHT_FRACTION, 0.0)
				.subtract(player.getEyePosition()).normalize();
		double alignment = player.getViewVector(1.0F).dot(towardWatcher);
		return alignment > WatcherEntity.gazeAlignmentThreshold(entity.distanceToSqr(player));
	}

	private static final class GazeTrack {
		private float progress;
		private long updatedAt;
	}

	private static final class EyeEmissiveLayer extends RenderLayer<WatcherRenderState, WatcherModel> {
		private EyeEmissiveLayer(WatcherRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
				WatcherRenderState state, float yRot, float xRot) {
			if (state.isInvisible || state.gazeProgress <= 0.0F) return;
			float wave = (Mth.sin(state.ageInTicks * ((float) Math.PI * 2.0F / 120.0F)) + 1.0F) * 0.5F;
			// The mask itself stays sparse and low-alpha; the reveal ramp is what decides whether
			// the eye exists at all, so an unwatched Watcher has no face to give it away.
			float alpha = state.gazeProgress * (0.94F + wave * 0.06F);
			float strength = 0.96F + wave * 0.04F;
			int color = ARGB.colorFromFloat(alpha, strength, strength * 0.97F, strength * 0.88F);
			collector.order(1).submitModel(getParentModel(), state, poseStack,
					RenderTypes.entityTranslucentEmissive(EMISSIVE_TEXTURE), LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
		}
	}
}
