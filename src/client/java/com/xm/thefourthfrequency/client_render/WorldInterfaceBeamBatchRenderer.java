package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xm.thefourthfrequency.client_ui.WorldInterfaceClientState;
import com.xm.thefourthfrequency.entity.WorldInterfaceAnatomy;
import com.xm.thefourthfrequency.entity.WorldInterfaceEnergyOrbEntity;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Extracts every additive world-space primitive the encounter owns once per frame and submits them
 * through a single position-colour layer.
 *
 * <p>The laser, the anchor tethers and the energy-orb halos are all camera-facing quads over the
 * same segment primitive, so widening the encounter's visual vocabulary costs vertices rather than
 * draw calls: everything still resolves to one {@link RenderTypes#lightning()} buffer.</p>
 *
 * <p>The gateway ring used to contribute a vertical shaft per slot. The gate structures themselves
 * stopped being built, so those twenty shafts were the only thing left of them: a ring of lights
 * standing in empty air at radius 96, visible from anywhere on the island and attached to nothing.
 * They are gone; the slot positions survive only as snapshot addressing.</p>
 */
public final class WorldInterfaceBeamBatchRenderer {
	public static final int RENDER_LAYER_COUNT = 1;
	public static final int MAX_ANCHOR_TETHERS = Integer.bitCount(WorldInterfaceProtocol.ANCHOR_MASK);
	public static final int MAX_VERTICES_PER_TETHER = 16;
	/**
	 * Aim core while charging, then a white-hot core plus a coloured halo once it fires, and for
	 * the first three fired ticks an over-wide discharge flare on top of both. The ground contact
	 * carries a scorch ring of its own, which is most of the budget below.
	 */
	public static final int LASER_IMPACT_RING_SAMPLES = 16;
	public static final int MAX_VERTICES_PER_LASER = (LASER_IMPACT_RING_SAMPLES + 12) * 4;
	public static final int MAX_ORB_HALOS = 12;
	/** Two concentric halo quads plus the launch tether back to the core. */
	public static final int MAX_VERTICES_PER_ORB = 36;
	/** The falling column plus its ground mark; the mark alone is one quad per sample. */
	public static final int SKY_LANCE_MARK_SAMPLES = 24;
	public static final int SKY_LANCE_SHELLS = 3;
	public static final int MAX_VERTICES_PER_LANCE = (SKY_LANCE_MARK_SAMPLES + SKY_LANCE_SHELLS + 3) * 4;
	public static final double SKY_LANCE_MARK_RADIUS = 3.6D;
	public static final double SKY_LANCE_HEIGHT = 72.0D;
	/** How far down the gathering stub reaches from the top while the lance is still charging. */
	public static final double SKY_LANCE_GATHER_LENGTH = 14.0D;
	/** Blocks of streak drawn behind a bolt in flight. */
	public static final double ORB_TRAIL_LENGTH = 5.5D;
	/**
	 * Debris caught in the interface's own gravity. Not a particle system: the storm is deterministic
	 * per boss and per tick, so it costs one loop and no allocation churn, it cannot be thinned out by
	 * a client's particle setting, and it stays inside this batch like everything else.
	 */
	public static final int MAX_STORM_MOTES = 160;
	public static final int MAX_VERTICES_PER_MOTE = 4;
	public static final int MAX_BATCH_VERTICES = MAX_ANCHOR_TETHERS * MAX_VERTICES_PER_TETHER
			+ MAX_VERTICES_PER_LASER
			+ MAX_VERTICES_PER_LANCE
			+ MAX_ORB_HALOS * MAX_VERTICES_PER_ORB
			+ MAX_STORM_MOTES * MAX_VERTICES_PER_MOTE;
	public static final double FULL_DETAIL_DISTANCE = 96.0D;
	// Well past the arena: culling is now left to the render distance rather than a fixed cap.
	public static final double RENDER_CUTOFF_DISTANCE = 192.0D;
	/** Half-extent of the cube scanned around the arena centre for boss, anchors and orbs. */
	public static final double ARENA_SCAN_RADIUS = 224.0D;
	private static final double FULL_DETAIL_DISTANCE_SQR = FULL_DETAIL_DISTANCE * FULL_DETAIL_DISTANCE;
	private static final double RENDER_CUTOFF_DISTANCE_SQR = RENDER_CUTOFF_DISTANCE * RENDER_CUTOFF_DISTANCE;
	/** Anchors never move, so their positions only need re-reading twice a second. */
	private static final long ANCHOR_RESCAN_INTERVAL_TICKS = 10L;
	private static final long BOSS_RESCAN_INTERVAL_TICKS = 20L;
	private static final float LASER_HOLD_FRACTION = 0.42F;
	private static final float LASER_FLARE_TICKS = 3.0F;
	/** Nested scattering shells around the fired shaft; three is where the falloff stops reading. */
	private static final int LASER_SCATTER_SHELLS = 3;
	/**
	 * How long the laser is drawn for in total: the lock, the whole sweep, and the afterglow.
	 *
	 * <p>This was lock plus afterglow, which was correct back when the laser was a single instant
	 * shot. Against a forty-tick sweep it cut the beam off twenty-four ticks early - the shaft
	 * vanished while the sweep was still burning a path across the island, so the attack was
	 * visible for barely half of the time it was actually happening.</p>
	 */
	private static final float LASER_FIRE_TICKS = WorldInterfaceProtocol.LASER_WARNING_TICKS
			+ WorldInterfaceProtocol.LASER_SWEEP_TICKS
			+ WorldInterfaceProtocol.LASER_AFTERGLOW_TICKS;
	private static final RenderStateDataKey<BeamBatch> STATE_KEY = RenderStateDataKey.create(
			() -> "thefourthfrequency:world_interface_beams");
	private static final BeamBatch EMPTY = new BeamBatch(Vec3.ZERO, null, null, List.of(), List.of());
	private static boolean initialized;

	private static WorldInterfaceEntity cachedBoss;
	private static long lastBossScanTick = Long.MIN_VALUE;
	private static List<Vec3> cachedAnchors = List.of();
	private static long lastAnchorScanTick = Long.MIN_VALUE;
	private static UUID cachedScanEncounterId;
	/** Client-side mirror of the server's laser aim trail; see {@link #laserEnd}. */
	private static final Deque<Vec3> laserTrail = new ArrayDeque<>();
	private static long lastLaserSampleTick = Long.MIN_VALUE;
	private static Vec3 lanceImpact;

	private WorldInterfaceBeamBatchRenderer() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		WorldRenderEvents.END_EXTRACTION.register(WorldInterfaceBeamBatchRenderer::extract);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldInterfaceBeamBatchRenderer::draw);
	}

	/** Drops entity references so a disconnect cannot strand a stale level's boss or anchors. */
	public static void resetSession() {
		cachedBoss = null;
		cachedAnchors = List.of();
		lastBossScanTick = Long.MIN_VALUE;
		lastAnchorScanTick = Long.MIN_VALUE;
		cachedScanEncounterId = null;
		laserTrail.clear();
		lastLaserSampleTick = Long.MIN_VALUE;
		lanceImpact = null;
	}

	private static void extract(WorldExtractionContext context) {
		FabricRenderState state = (FabricRenderState) context.worldState();
		WorldInterfaceClientState.Projection projection = WorldInterfaceClientState.snapshot();
		WorldInterfaceSnapshotS2C encounter = projection.encounter();
		if (encounter == null) {
			resetSession();
			state.setData(STATE_KEY, EMPTY);
			return;
		}
		if (!encounter.encounterId().equals(cachedScanEncounterId)) {
			resetSession();
			cachedScanEncounterId = encounter.encounterId();
		}

		ClientLevel level = context.world();
		Vec3 camera = context.camera().position();
		long gameTime = level.getGameTime();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		int band = WorldInterfacePalette.band(encounter.stage());
		List<Beam> beams = new ArrayList<>();
		List<Halo> halos = new ArrayList<>();

		WorldInterfaceEntity boss = resolveBoss(level, encounter, gameTime);
		if (boss != null) {
			// The glowing core rather than the collision-box centre. At third form those are eleven
			// blocks apart, and every beam used to leave the body out of blank plating.
			Vec3 core = WorldInterfaceAnatomy.coreOrigin(boss.getPosition(partialTick), boss.form(),
					Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot));
			extractAnchorTethers(level, encounter, core, gameTime, band, beams);
			extractLaser(level, projection, boss, core, gameTime, partialTick, band, beams, halos);
			extractSkyLance(level, projection, gameTime, partialTick, band, beams, halos);
			extractOrbs(level, projection, encounter, core, gameTime, partialTick, band, beams, halos);
			extractDebrisStorm(boss, core, camera, gameTime, partialTick, band, halos);
		}

		state.setData(STATE_KEY, beams.isEmpty() && halos.isEmpty() ? EMPTY
				// The camera hands out its own live basis vectors; copy them so a re-setup between
				// extraction and the translucent pass cannot rotate the halos mid-frame.
				: new BeamBatch(camera, new Vector3f(context.camera().upVector()),
						new Vector3f(context.camera().leftVector()),
						List.copyOf(beams), List.copyOf(halos)));
	}

	/**
	 * Ties every surviving anchor back to the interface. The tethers are the only place the
	 * "the anchors are holding it up" rule is stated visually, so they brighten as anchors fall.
	 */
	private static void extractAnchorTethers(ClientLevel level, WorldInterfaceSnapshotS2C encounter,
			Vec3 eye, long gameTime, int band, List<Beam> beams) {
		if (!isCombatStage(encounter.stage())) return;
		List<Vec3> anchors = resolveAnchors(level, encounter, gameTime);
		if (anchors.isEmpty()) return;
		float pressure = 1.0F - anchors.size() / (float) MAX_ANCHOR_TETHERS;
		int red = WorldInterfacePalette.red255(band);
		int green = WorldInterfacePalette.green255(band);
		int blue = WorldInterfacePalette.blue255(band);
		for (int index = 0; index < anchors.size(); index++) {
			Vec3 anchor = anchors.get(index);
			// A travelling pulse reads as flow direction without needing a second primitive.
			float travel = Mth.sin(gameTime * 0.22F - index * 0.85F) * 0.5F + 0.5F;
			int alpha = Math.round(Mth.lerp(travel, 46.0F, 128.0F) * (0.72F + pressure * 0.52F));
			beams.add(new Beam(anchor.x, anchor.y, anchor.z, eye.x, eye.y, eye.z,
					0.09F + travel * 0.05F, red, green, blue, Math.clamp(alpha, 0, 255)));
		}
	}

	private static void extractLaser(ClientLevel level, WorldInterfaceClientState.Projection projection,
			WorldInterfaceEntity boss, Vec3 core, long gameTime, float partialTick, int band,
			List<Beam> beams, List<Halo> halos) {
		BossActionS2C action = projection.action();
		if (action == null || action.action() != WorldInterfaceProtocol.BossAction.LASER_SWEEP) return;
		// Deliberately not Projection#actionActive: the envelope closes one tick after the sweep, and
		// the afterglow is exactly the part a player looks at.
		float age = gameTime - action.startTick() + partialTick;
		if (age < 0.0F || age > LASER_FIRE_TICKS) return;

		Vec3 end = laserEnd(level, boss, action, gameTime, partialTick);
		int red = WorldInterfacePalette.red255(band);
		int green = WorldInterfacePalette.green255(band);
		int blue = WorldInterfacePalette.blue255(band);
		double muzzle = WorldInterfaceAnatomy.coreRadius(boss.form());
		if (age < WorldInterfaceProtocol.LASER_WARNING_TICKS) {
			float progress = age / WorldInterfaceProtocol.LASER_WARNING_TICKS;
			// Squared so the tell stays thin and calm early and snaps taut in the last half second.
			float charge = progress * progress;
			float flicker = progress < 0.78F ? 1.0F
					: 0.72F + 0.28F * Mth.sin(age * 1.9F);
			beams.add(new Beam(core.x, core.y, core.z, end.x, end.y, end.z,
					0.035F + charge * 0.16F, red, green, blue,
					Math.clamp(Math.round((54.0F + charge * 150.0F) * flicker), 0, 255)));
			// The core visibly gathers before it fires, so the aim line is not the only warning.
			halos.add(new Halo(core.x, core.y, core.z, (float) (muzzle * (0.28F + charge * 0.62F)),
					red, green, blue, Math.round(40.0F + charge * 120.0F)));
			return;
		}
		// The beam is drawn at full for the whole sweep, and only fades once the sweep is over.
		//
		// This used to fade over LASER_AFTERGLOW_TICKS measured from the shot, which was right when
		// the laser was a single instant shot. Against a forty-tick sweep it meant the shaft went
		// out two thirds of the way through while the beam was still burning people.
		float fired = age - WorldInterfaceProtocol.LASER_WARNING_TICKS;
		float sweep = WorldInterfaceProtocol.LASER_SWEEP_TICKS;
		float fade = fired <= sweep ? 1.0F
				: Math.clamp(1.0F - (fired - sweep)
						/ Math.max(1.0F, WorldInterfaceProtocol.LASER_AFTERGLOW_TICKS), 0.0F, 1.0F);
		float flare = fired <= LASER_FLARE_TICKS ? 1.0F - fired / LASER_FLARE_TICKS : 0.0F;
		// A slow boil along the shaft, so a beam held for two seconds is alive rather than a
		// static bar hanging in the air.
		float boil = 0.90F + 0.10F * Mth.sin(age * 0.85F);

		// Volumetric read: nested shells, each wider, dimmer and shorter-lived than the one inside
		// it. A single quad has no depth cue at all — several at falling alpha do, because the
		// overlap integrates toward the axis exactly the way scattered light does.
		for (int shell = LASER_SCATTER_SHELLS; shell >= 1; shell--) {
			float spread = shell / (float) LASER_SCATTER_SHELLS;
			// Squared rather than cubed: the outer haze now has to be visible from across the arena,
			// and the old falloff put almost everything into the innermost shell.
			float density = (1.0F - spread) * (1.0F - spread);
			float width = 0.95F + spread * (4.4F + flare * 7.0F);
			int alpha = Math.round((44.0F + density * 176.0F) * fade * boil);
			if (alpha <= 1) continue;
			beams.add(new Beam(core.x, core.y, core.z, end.x, end.y, end.z, width * fade,
					red, green, blue, Math.clamp(alpha, 0, 255)));
		}
		// White-hot axis last, so it reads as the source of the haze rather than a stripe on it.
		beams.add(new Beam(core.x, core.y, core.z, end.x, end.y, end.z, 0.86F * fade * boil,
				255, 255, 255, Math.round(255.0F * fade)));

		// Muzzle and impact blooms. Scattering is strongest where the beam meets something, and
		// these are what sell that the shaft has two ends rather than being a decal on the screen.
		float bloom = 0.72F + flare * 1.1F;
		halos.add(new Halo(core.x, core.y, core.z, (float) (muzzle * bloom * fade),
				255, 255, 255, Math.round(230.0F * fade)));
		halos.add(new Halo(core.x, core.y, core.z, (float) (muzzle * bloom * 2.1F * fade),
				red, green, blue, Math.round(140.0F * fade)));

		// The ground contact is the part the player actually has to track, so it gets a detonation
		// rather than a dot: a white core, a coloured shell, and a pulsing outer flash on the same
		// clock as the server's own explosion bursts.
		float detonation = 0.72F + 0.28F * Mth.sin(age * 1.6F);
		float impact = 2.4F + flare * 5.0F;
		halos.add(new Halo(end.x, end.y, end.z, impact * fade, 255, 255, 255,
				Math.round(230.0F * fade)));
		halos.add(new Halo(end.x, end.y, end.z, impact * 2.1F * fade * detonation, red, green, blue,
				Math.round(150.0F * fade)));
		halos.add(new Halo(end.x, end.y, end.z, impact * 3.4F * fade * detonation, red, green, blue,
				Math.round(70.0F * fade)));
		// A scorch ring lying on the floor around the contact, which is the only part of the shot
		// that reads at a glance from directly above.
		for (int index = 0; index < LASER_IMPACT_RING_SAMPLES; index++) {
			double angle = Math.PI * 2.0D * index / LASER_IMPACT_RING_SAMPLES + age * 0.12D;
			double ringRadius = impact * 1.5D * detonation;
			halos.add(new Halo(end.x + Math.cos(angle) * ringRadius, end.y + 0.12D,
					end.z + Math.sin(angle) * ringRadius, 0.42F * fade,
					255, 255, 255, Math.round(170.0F * fade)));
		}
	}

	/**
	 * A storm of torn world in orbit around the interface, drifting on its own axis and falling
	 * inward. Deterministic from the boss id and the tick, so every client sees the same storm and
	 * nothing has to be simulated or synchronised.
	 */
	private static void extractDebrisStorm(WorldInterfaceEntity boss, Vec3 core, Vec3 camera,
			long gameTime, float partialTick, int band, List<Halo> halos) {
		int form = Math.clamp(boss.form(), 0, 2);
		double distanceSqr = camera.distanceToSqr(core);
		if (distanceSqr > RENDER_CUTOFF_DISTANCE_SQR) return;
		// Thins with distance instead of vanishing at a ring: at range the storm is a haze, up close
		// it is individual blocks going past.
		float detail = distanceSqr <= FULL_DETAIL_DISTANCE_SQR ? 1.0F
				: (float) (1.0D - (Math.sqrt(distanceSqr) - FULL_DETAIL_DISTANCE)
						/ (RENDER_CUTOFF_DISTANCE - FULL_DETAIL_DISTANCE));
		detail = Math.clamp(detail, 0.0F, 1.0F);
		int count = Math.min(MAX_STORM_MOTES, Math.round((28 + form * 46) * detail));
		if (count <= 0) return;
		double radius = WorldInterfaceAnatomy.massRadius(form);
		float time = gameTime + partialTick;
		int red = WorldInterfacePalette.red255(band);
		int green = WorldInterfacePalette.green255(band);
		int blue = WorldInterfacePalette.blue255(band);
		int seed = boss.getId() * 31;
		for (int index = 0; index < count; index++) {
			float lane = hash(seed + index * 3);
			float phase = hash(seed + index * 3 + 1) * Mth.TWO_PI;
			float tilt = (hash(seed + index * 3 + 2) - 0.5F) * 1.7F;
			// Inner debris orbits faster, so the storm shears against itself instead of turning as a
			// rigid disc. Alternating direction keeps it from reading as a single spinning ring.
			float lap = (index % 2 == 0 ? 1.0F : -1.0F) * (0.020F + (1.0F - lane) * 0.034F);
			float angle = phase + time * lap;
			double orbit = radius * (0.75D + lane * 1.35D);
			// Slow inward fall, recycled, so material is always visibly being taken in.
			float fall = ((time * 0.006F + hash(seed + index * 7)) % 1.0F);
			double pull = 1.0D - fall * 0.34D;
			double x = core.x + Mth.cos(angle) * orbit * pull;
			double z = core.z + Mth.sin(angle) * orbit * pull;
			double y = core.y + Mth.sin(angle * 1.7F + tilt * 3.1F) * radius * 0.52D * tilt
					+ (fall - 0.5D) * radius * 0.30D;
			float size = (float) (radius * (0.020D + hash(seed + index * 11) * 0.055D));
			// Fades in and out at the ends of its fall so nothing pops into or out of existence.
			float alpha = Mth.sin(fall * Mth.PI) * detail;
			halos.add(new Halo(x, y, z, size, red, green, blue, Math.round(38.0F + alpha * 120.0F)));
		}
	}

	/** Stable per-index scatter; the storm must not shimmer between frames, nor drift off the body. */
	private static float hash(int seed) {
		return WorldInterfaceScatter.hash(seed);
	}

	/**
	 * Where the shaft is pointing this frame.
	 *
	 * <p>The sweep aims at where its target was {@link WorldInterfaceProtocol#LASER_TRACKING_LAG_TICKS}
	 * ticks ago, and that lag is the whole mechanic: the beam has to visibly trail a running player,
	 * or there is no way to learn that running works. The trail below is the client's own copy of
	 * the one the server keeps, sampled on the same clock, so the drawn shaft and the burning shaft
	 * agree without the aim point having to be put on the wire every tick.</p>
	 */
	private static Vec3 laserEnd(ClientLevel level, WorldInterfaceEntity boss, BossActionS2C action,
			long gameTime, float partialTick) {
		Player target = laserTarget(level, action);
		if (target == null) {
			laserTrail.clear();
			return boss.getEyePosition(partialTick).add(boss.getViewVector(partialTick).scale(48.0D));
		}
		if (gameTime != lastLaserSampleTick) {
			lastLaserSampleTick = gameTime;
			laserTrail.addLast(target.getEyePosition(1.0F));
			while (laserTrail.size() > WorldInterfaceProtocol.LASER_TRACKING_LAG_TICKS + 1) {
				laserTrail.removeFirst();
			}
		}
		Vec3 aim = laserTrail.size() > WorldInterfaceProtocol.LASER_TRACKING_LAG_TICKS
				? laserTrail.peekFirst() : target.getEyePosition(partialTick);
		if (aim == null) aim = target.getEyePosition(partialTick);
		float age = gameTime - action.startTick() + partialTick;
		// While it is still aiming the beam is drawn live, so the telegraph shows exactly who is
		// locked. Only the fired sweep trails, which is what makes running a real answer.
		return age < WorldInterfaceProtocol.LASER_WARNING_TICKS ? target.getEyePosition(partialTick)
				: groundUnder(level, aim);
	}

	private static Player laserTarget(ClientLevel level, BossActionS2C action) {
		if (action.targetIds().isEmpty()) return null;
		UUID targetId = action.targetIds().getFirst();
		for (Player player : level.players()) {
			if (player.getUUID().equals(targetId)) return player;
		}
		return null;
	}

	/** Mirrors the server's own projection so the shaft ends on the floor it is scarring. */
	private static Vec3 groundUnder(ClientLevel level, Vec3 position) {
		int x = Mth.floor(position.x);
		int z = Mth.floor(position.z);
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new Vec3(position.x, Math.max(level.getMinY() + 1, Math.min(position.y, surface)), position.z);
	}

	/**
	 * The sky lance: a column falling on a mark rather than a shaft leaving the body. The impact is
	 * the target's ground position at the moment the lock resolved, which both sides sample off the
	 * same tick, so nothing about it needs to be sent.
	 */
	private static void extractSkyLance(ClientLevel level, WorldInterfaceClientState.Projection projection,
			long gameTime, float partialTick, int band, List<Beam> beams, List<Halo> halos) {
		BossActionS2C action = projection.action();
		if (action == null || action.action() != WorldInterfaceProtocol.BossAction.SKY_LANCE) return;
		float age = gameTime - action.startTick() + partialTick;
		if (age < 0.0F) return;
		float lock = WorldInterfaceProtocol.SKY_LANCE_LOCK_TICKS;
		float strike = lock + WorldInterfaceProtocol.SKY_LANCE_CHARGE_TICKS;
		if (age > strike + WorldInterfaceProtocol.SKY_LANCE_STRIKE_TICKS) return;
		Player target = laserTarget(level, action);
		if (age < lock) {
			if (target == null) return;
			lanceImpact = groundUnder(level, target.position());
		}
		Vec3 impact = lanceImpact;
		if (impact == null) return;

		int red = WorldInterfacePalette.red255(band);
		int green = WorldInterfacePalette.green255(band);
		int blue = WorldInterfacePalette.blue255(band);
		float radius = (float) SKY_LANCE_MARK_RADIUS;
		// The mark is drawn for the whole action, so the place to not be standing is never ambiguous.
		float markPulse = age < strike ? 0.6F + 0.4F * Mth.sin(age * 0.9F) : 1.0F;
		for (int index = 0; index < SKY_LANCE_MARK_SAMPLES; index++) {
			double angle = Math.PI * 2.0D * index / SKY_LANCE_MARK_SAMPLES + age * 0.05D;
			halos.add(new Halo(impact.x + Math.cos(angle) * radius, impact.y + 0.15D,
					impact.z + Math.sin(angle) * radius, 0.34F * markPulse,
					red, green, blue, Math.round(150.0F * markPulse)));
		}
		if (age < lock) return;

		// It gathers overhead, then drops - it does not lower itself.
		//
		// The descent used to run the length of the charge, so seventy blocks took a second and a
		// half and the lance floated down while the player watched. The charge is now spent loading
		// the sky: a stub of column hangs at altitude, brightening. Only the last few ticks are the
		// fall, and they accelerate into the ground so the column arrives rather than settles.
		float charged = age - lock;
		float chargeProgress = Math.clamp(charged / WorldInterfaceProtocol.SKY_LANCE_CHARGE_TICKS,
				0.0F, 1.0F);
		float fallStart = WorldInterfaceProtocol.SKY_LANCE_CHARGE_TICKS
				- WorldInterfaceProtocol.SKY_LANCE_FALL_TICKS;
		float descent;
		if (age >= strike) {
			descent = 1.0F;
		} else if (charged <= fallStart) {
			descent = 0.0F;
		} else {
			float fall = Math.clamp((charged - fallStart)
					/ WorldInterfaceProtocol.SKY_LANCE_FALL_TICKS, 0.0F, 1.0F);
			// Squared: the column is quickest at the moment it lands.
			descent = fall * fall;
		}
		double top = impact.y + SKY_LANCE_HEIGHT;
		// Before the fall the column is only a growing stub near the top, so the sky reads as
		// loaded without the shaft having covered any of the distance yet.
		double reach = descent <= 0.0F
				? top - SKY_LANCE_GATHER_LENGTH * chargeProgress
				: impact.y + SKY_LANCE_HEIGHT * (1.0D - descent);
		float fade = age <= strike ? Math.max(0.30F, chargeProgress)
				: Math.clamp(1.0F - (age - strike) / WorldInterfaceProtocol.SKY_LANCE_STRIKE_TICKS,
						0.0F, 1.0F);
		if (fade <= 0.01F) return;
		for (int shell = SKY_LANCE_SHELLS; shell >= 1; shell--) {
			float spread = shell / (float) SKY_LANCE_SHELLS;
			float density = (1.0F - spread) * (1.0F - spread);
			beams.add(new Beam(impact.x, top, impact.z, impact.x, reach, impact.z,
					(0.5F + spread * 2.4F) * fade, red, green, blue,
					Math.round((26.0F + density * 150.0F) * fade)));
		}
		beams.add(new Beam(impact.x, top, impact.z, impact.x, reach, impact.z, 0.42F * fade,
				255, 255, 255, Math.round(255.0F * fade)));
		if (age >= strike) {
			halos.add(new Halo(impact.x, impact.y + 0.6D, impact.z, radius * 1.6F * fade,
					255, 255, 255, Math.round(200.0F * fade)));
			halos.add(new Halo(impact.x, impact.y + 0.6D, impact.z, radius * 3.0F * fade,
					red, green, blue, Math.round(110.0F * fade)));
		}
	}

	private static void extractOrbs(ClientLevel level, WorldInterfaceClientState.Projection projection,
			WorldInterfaceSnapshotS2C encounter, Vec3 eye, long gameTime, float partialTick, int band,
			List<Beam> beams, List<Halo> halos) {
		// Gated on the encounter being in combat rather than on the envelope naming ENERGY_ORB.
		//
		// The third form now opens volleys outside the scheduled lane, and the protocol carries one
		// envelope, so a bolt thrown by the volley arrives while the envelope names something else
		// entirely. Under the old gate those bolts flew, burned and landed with no halo and no trail
		// on them at all. The scan itself is what the gate was really protecting - one bounded query
		// for one entity class, and only while a fight is actually happening.
		if (!encounter.stage().isCombat()) return;
		// The orb's encounter binding is server-only state, so membership is established by the
		// arena bounds instead: nothing else spawns this entity type inside the ring.
		List<WorldInterfaceEnergyOrbEntity> orbs = level.getEntitiesOfClass(
				WorldInterfaceEnergyOrbEntity.class, arenaBounds(encounter), WorldInterfaceEnergyOrbEntity::isAlive);
		if (orbs.isEmpty()) return;
		int red = WorldInterfacePalette.red255(band);
		int green = WorldInterfacePalette.green255(band);
		int blue = WorldInterfacePalette.blue255(band);
		int count = Math.min(orbs.size(), MAX_ORB_HALOS);
		for (int index = 0; index < count; index++) {
			WorldInterfaceEnergyOrbEntity orb = orbs.get(index);
			Vec3 position = orb.getPosition(partialTick);
			float scale = Math.clamp(orb.orbScale(), WorldInterfaceEnergyOrbEntity.MIN_SCALE,
					WorldInterfaceEnergyOrbEntity.MAX_SCALE);
			float breath = 0.92F + Mth.sin(gameTime * 0.31F + index) * 0.08F;
			halos.add(new Halo(position.x, position.y, position.z, scale * 0.62F * breath,
					255, 255, 255, 160));
			halos.add(new Halo(position.x, position.y, position.z, scale * 1.18F * breath,
					red, green, blue, 110));
			// A short trail along its own travel rather than a tether back to the interface: the bolt
			// is loosed at launch and owes the body nothing after that, and at nearly two blocks a
			// tick the streak is what makes its direction readable in a single frame.
			Vec3 travel = orb.getDeltaMovement();
			if (travel.lengthSqr() < 1.0E-4D) continue;
			Vec3 tail = position.subtract(travel.normalize().scale(ORB_TRAIL_LENGTH));
			beams.add(new Beam(tail.x, tail.y, tail.z, position.x, position.y, position.z,
					scale * 0.30F, red, green, blue, 120));
		}
	}

	private static WorldInterfaceEntity resolveBoss(ClientLevel level, WorldInterfaceSnapshotS2C encounter,
			long gameTime) {
		WorldInterfaceEntity cached = cachedBoss;
		if (cached != null && !cached.isRemoved() && cached.level() == level
				&& cached.getUUID().equals(encounter.bossId())) {
			return cached;
		}
		if (lastBossScanTick != Long.MIN_VALUE && gameTime >= lastBossScanTick
				&& gameTime - lastBossScanTick < BOSS_RESCAN_INTERVAL_TICKS) {
			return null;
		}
		lastBossScanTick = gameTime;
		cachedBoss = level.getEntitiesOfClass(WorldInterfaceEntity.class, arenaBounds(encounter),
						candidate -> candidate.getUUID().equals(encounter.bossId()))
				.stream().findFirst().orElse(null);
		return cachedBoss;
	}

	private static List<Vec3> resolveAnchors(ClientLevel level, WorldInterfaceSnapshotS2C encounter,
			long gameTime) {
		if (lastAnchorScanTick != Long.MIN_VALUE && gameTime >= lastAnchorScanTick
				&& gameTime - lastAnchorScanTick < ANCHOR_RESCAN_INTERVAL_TICKS) {
			return cachedAnchors;
		}
		lastAnchorScanTick = gameTime;
		cachedAnchors = level.getEntitiesOfClass(EndCrystal.class, arenaBounds(encounter),
						EndCrystal::isAlive).stream()
				// Entity iteration order is not stable across frames; sorting keeps the travelling
				// pulse assigned to the same tether instead of shuffling every rescan.
				.sorted(Comparator.comparingInt(EndCrystal::getId))
				.limit(MAX_ANCHOR_TETHERS)
				.map(crystal -> crystal.position().add(0.0D, 1.0D, 0.0D))
				.toList();
		return cachedAnchors;
	}

	private static AABB arenaBounds(WorldInterfaceSnapshotS2C encounter) {
		return AABB.ofSize(encounter.center().getCenter(), ARENA_SCAN_RADIUS * 2.0D,
				ARENA_SCAN_RADIUS * 2.0D, ARENA_SCAN_RADIUS * 2.0D);
	}

	private static boolean isCombatStage(WorldInterfaceProtocol.Stage stage) {
		return stage == WorldInterfaceProtocol.Stage.SUMMONING
				|| stage == WorldInterfaceProtocol.Stage.PHASE_1
				|| stage == WorldInterfaceProtocol.Stage.PHASE_2
				|| stage == WorldInterfaceProtocol.Stage.PHASE_3;
	}

	private static void draw(WorldRenderContext context) {
		BeamBatch batch = ((FabricRenderState) context.worldState()).getData(STATE_KEY);
		if (batch == null || batch.beams().isEmpty() && batch.halos().isEmpty()) return;
		VertexConsumer vertices = context.consumers().getBuffer(RenderTypes.lightning());
		for (Beam beam : batch.beams()) drawBeam(vertices, batch.camera(), beam);
		for (Halo halo : batch.halos()) drawHalo(vertices, batch, halo);
	}

	/**
	 * Emits one camera-facing quad spanning the segment. Winding is chosen so the front face points
	 * at the camera, which keeps the beam correct even if the lightning pipeline ever starts culling.
	 */
	private static void drawBeam(VertexConsumer vertices, Vec3 camera, Beam beam) {
		double ax = beam.ax() - camera.x;
		double ay = beam.ay() - camera.y;
		double az = beam.az() - camera.z;
		double bx = beam.bx() - camera.x;
		double by = beam.by() - camera.y;
		double bz = beam.bz() - camera.z;
		double dx = bx - ax;
		double dy = by - ay;
		double dz = bz - az;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-5D) return;
		dx /= length;
		dy /= length;
		dz /= length;
		// View direction taken at the midpoint: a single orientation per beam is stable and avoids
		// the twist a per-vertex facing would introduce on long segments.
		double vx = -(ax + bx) * 0.5D;
		double vy = -(ay + by) * 0.5D;
		double vz = -(az + bz) * 0.5D;
		double viewLength = Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (viewLength < 1.0E-5D) {
			vx = 0.0D;
			vy = 0.0D;
			vz = 1.0D;
		} else {
			vx /= viewLength;
			vy /= viewLength;
			vz /= viewLength;
		}
		double rx = dy * vz - dz * vy;
		double ry = dz * vx - dx * vz;
		double rz = dx * vy - dy * vx;
		double rightLength = Math.sqrt(rx * rx + ry * ry + rz * rz);
		if (rightLength < 1.0E-4D) {
			// Camera is sighting straight down the beam; any perpendicular renders the same sliver.
			double px = Math.abs(dy) < 0.9D ? 0.0D : 1.0D;
			double py = Math.abs(dy) < 0.9D ? 1.0D : 0.0D;
			rx = dy * 0.0D - dz * py;
			ry = dz * px - dx * 0.0D;
			rz = dx * py - dy * px;
			rightLength = Math.sqrt(rx * rx + ry * ry + rz * rz);
			if (rightLength < 1.0E-5D) return;
		}
		double scale = beam.halfWidth() / rightLength;
		rx *= scale;
		ry *= scale;
		rz *= scale;
		vertex(vertices, ax + rx, ay + ry, az + rz, beam);
		vertex(vertices, bx + rx, by + ry, bz + rz, beam);
		vertex(vertices, bx - rx, by - ry, bz - rz, beam);
		vertex(vertices, ax - rx, ay - ry, az - rz, beam);
	}

	private static void drawHalo(VertexConsumer vertices, BeamBatch batch, Halo halo) {
		Vector3fc up = batch.cameraUp();
		Vector3fc left = batch.cameraLeft();
		if (up == null || left == null) return;
		double x = halo.x() - batch.camera().x;
		double y = halo.y() - batch.camera().y;
		double z = halo.z() - batch.camera().z;
		double ux = up.x() * halo.radius();
		double uy = up.y() * halo.radius();
		double uz = up.z() * halo.radius();
		double lx = left.x() * halo.radius();
		double ly = left.y() * halo.radius();
		double lz = left.z() * halo.radius();
		vertex(vertices, x + lx + ux, y + ly + uy, z + lz + uz, halo);
		vertex(vertices, x + lx - ux, y + ly - uy, z + lz - uz, halo);
		vertex(vertices, x - lx - ux, y - ly - uy, z - lz - uz, halo);
		vertex(vertices, x - lx + ux, y - ly + uy, z - lz + uz, halo);
	}

	private static void vertex(VertexConsumer vertices, double x, double y, double z, Beam beam) {
		vertices.addVertex((float) x, (float) y, (float) z)
				.setColor(beam.red(), beam.green(), beam.blue(), beam.alpha());
	}

	private static void vertex(VertexConsumer vertices, double x, double y, double z, Halo halo) {
		vertices.addVertex((float) x, (float) y, (float) z)
				.setColor(halo.red(), halo.green(), halo.blue(), halo.alpha());
	}

	private record BeamBatch(Vec3 camera, Vector3fc cameraUp, Vector3fc cameraLeft,
			List<Beam> beams, List<Halo> halos) {
	}

	private record Beam(double ax, double ay, double az, double bx, double by, double bz,
			float halfWidth, int red, int green, int blue, int alpha) {
	}

	private record Halo(double x, double y, double z, float radius,
			int red, int green, int blue, int alpha) {
	}
}
