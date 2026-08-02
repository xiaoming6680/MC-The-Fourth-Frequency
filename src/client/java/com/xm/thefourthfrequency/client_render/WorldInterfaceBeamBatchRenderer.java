package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xm.thefourthfrequency.client_ui.WorldInterfaceClientState;
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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Extracts every additive world-space primitive the encounter owns once per frame and submits them
 * through a single position-colour layer.
 *
 * <p>Gateways, the laser, the anchor tethers and the energy-orb halos are all camera-facing quads
 * over the same segment primitive, so widening the encounter's visual vocabulary costs vertices
 * rather than draw calls: everything still resolves to one {@link RenderTypes#lightning()} buffer.</p>
 */
public final class WorldInterfaceBeamBatchRenderer {
	public static final int MAX_GATEWAYS = WorldInterfaceProtocol.MAX_GATEWAYS;
	public static final int RENDER_LAYER_COUNT = 1;
	/** One camera-facing shaft plus one camera-facing flare; the old axis-aligned pair needed sixteen. */
	public static final int MAX_VERTICES_PER_GATE = 8;
	public static final int MAX_ANCHOR_TETHERS = Integer.bitCount(WorldInterfaceProtocol.ANCHOR_MASK);
	public static final int MAX_VERTICES_PER_TETHER = 4;
	/** Aim core while charging, then a white-hot core plus a coloured halo once it fires. */
	public static final int MAX_VERTICES_PER_LASER = 8;
	public static final int MAX_ORB_HALOS = 4;
	/** Two concentric halo quads plus the charge tether back to the eye. */
	public static final int MAX_VERTICES_PER_ORB = 12;
	public static final int MAX_BATCH_VERTICES = MAX_GATEWAYS * MAX_VERTICES_PER_GATE
			+ MAX_ANCHOR_TETHERS * MAX_VERTICES_PER_TETHER
			+ MAX_VERTICES_PER_LASER
			+ MAX_ORB_HALOS * MAX_VERTICES_PER_ORB;
	public static final double FULL_DETAIL_DISTANCE = 36.0D;
	// Gates sit on a nominal 96-block ring; retain headroom for block rounding and camera offset.
	public static final double RENDER_CUTOFF_DISTANCE = 112.0D;
	/** Half-extent of the cube scanned around the arena centre for boss, anchors and orbs. */
	public static final double ARENA_SCAN_RADIUS = 176.0D;
	private static final double FULL_DETAIL_DISTANCE_SQR = FULL_DETAIL_DISTANCE * FULL_DETAIL_DISTANCE;
	private static final double RENDER_CUTOFF_DISTANCE_SQR = RENDER_CUTOFF_DISTANCE * RENDER_CUTOFF_DISTANCE;
	/** Anchors never move, so their positions only need re-reading twice a second. */
	private static final long ANCHOR_RESCAN_INTERVAL_TICKS = 10L;
	private static final long BOSS_RESCAN_INTERVAL_TICKS = 20L;
	private static final float LASER_FIRE_TICKS = WorldInterfaceProtocol.LASER_WARNING_TICKS
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

		extractGateways(encounter, camera, gameTime, beams);
		WorldInterfaceEntity boss = resolveBoss(level, encounter, gameTime);
		if (boss != null) {
			Vec3 eye = boss.getEyePosition(partialTick);
			extractAnchorTethers(level, encounter, eye, gameTime, band, beams);
			extractLaser(level, projection, boss, eye, gameTime, partialTick, band, beams);
			extractOrbs(level, projection, encounter, eye, gameTime, partialTick, band, beams, halos);
		}

		state.setData(STATE_KEY, beams.isEmpty() && halos.isEmpty() ? EMPTY
				// The camera hands out its own live basis vectors; copy them so a re-setup between
				// extraction and the translucent pass cannot rotate the halos mid-frame.
				: new BeamBatch(camera, new Vector3f(context.camera().upVector()),
						new Vector3f(context.camera().leftVector()),
						List.copyOf(beams), List.copyOf(halos)));
	}

	private static void extractGateways(WorldInterfaceSnapshotS2C encounter, Vec3 camera,
			long gameTime, List<Beam> beams) {
		if (encounter.gatewayState() == WorldInterfaceProtocol.GatewayState.DORMANT
				|| encounter.gatewayPositions().isEmpty()) return;
		int red;
		int green;
		int blue;
		switch (encounter.gatewayState()) {
			case PURPLE -> {
				red = 190;
				green = 54;
				blue = 255;
			}
			case GOLD -> {
				red = 255;
				green = 204;
				blue = 72;
			}
			case RED -> {
				red = 255;
				green = 42;
				blue = 68;
			}
			default -> {
				return;
			}
		}
		int count = Math.min(encounter.gatewayPositions().size(), MAX_GATEWAYS);
		for (int index = 0; index < count; index++) {
			BlockPos position = encounter.gatewayPositions().get(index);
			double x = position.getX() + 0.5D;
			double bottom = position.getY() + 0.35D;
			double z = position.getZ() + 0.5D;
			double deltaX = camera.x - x;
			double deltaZ = camera.z - z;
			double distanceSqr = deltaX * deltaX + deltaZ * deltaZ;
			if (distanceSqr > RENDER_CUTOFF_DISTANCE_SQR) continue;
			// Smooth 1-to-0 detail ramp; the old boolean made both the shaft and its alpha pop
			// the instant a player crossed the 36-block ring.
			float detail = distanceSqr <= FULL_DETAIL_DISTANCE_SQR ? 1.0F
					: (float) (1.0D - (Math.sqrt(distanceSqr) - FULL_DETAIL_DISTANCE)
							/ (RENDER_CUTOFF_DISTANCE - FULL_DETAIL_DISTANCE));
			detail = Math.clamp(detail, 0.0F, 1.0F);
			float phase = (gameTime + index * 11L) * 0.085F;
			float pulse = 0.88F + Mth.sin(phase) * 0.12F;
			float height = Mth.lerp(detail, 9.0F, 13.5F) * pulse;
			float width = Mth.lerp(detail, 0.65F, 1.15F) * pulse;
			int alpha = Math.round(Mth.lerp(detail, 34.0F, 196.0F));
			beams.add(new Beam(x, bottom, z, x, bottom + height, z, width, red, green, blue, alpha));
			if (detail <= 0.35F) continue;
			beams.add(new Beam(x, bottom + height * 0.40F, z, x, bottom + height * 0.60F, z,
					width * 2.15F, red, green, blue, Math.round(132.0F * detail)));
		}
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
			WorldInterfaceEntity boss, Vec3 eye, long gameTime, float partialTick, int band,
			List<Beam> beams) {
		BossActionS2C action = projection.action();
		if (action == null || action.action() != WorldInterfaceProtocol.BossAction.LASER_SWEEP) return;
		// Deliberately not Projection#actionActive: the envelope closes one tick after the shot, and
		// the afterglow is exactly the part a player looks at.
		float age = gameTime - action.startTick() + partialTick;
		if (age < 0.0F || age > LASER_FIRE_TICKS) return;

		Vec3 end = laserEnd(level, boss, action, partialTick);
		int red = WorldInterfacePalette.red255(band);
		int green = WorldInterfacePalette.green255(band);
		int blue = WorldInterfacePalette.blue255(band);
		if (age < WorldInterfaceProtocol.LASER_WARNING_TICKS) {
			float progress = age / WorldInterfaceProtocol.LASER_WARNING_TICKS;
			// Squared so the tell stays thin and calm early and snaps taut in the last half second.
			float charge = progress * progress;
			float flicker = progress < 0.78F ? 1.0F
					: 0.72F + 0.28F * Mth.sin(age * 1.9F);
			beams.add(new Beam(eye.x, eye.y, eye.z, end.x, end.y, end.z,
					0.035F + charge * 0.16F, red, green, blue,
					Math.clamp(Math.round((54.0F + charge * 150.0F) * flicker), 0, 255)));
			return;
		}
		float fade = 1.0F - (age - WorldInterfaceProtocol.LASER_WARNING_TICKS)
				/ Math.max(1.0F, WorldInterfaceProtocol.LASER_AFTERGLOW_TICKS);
		fade = Math.clamp(fade, 0.0F, 1.0F);
		beams.add(new Beam(eye.x, eye.y, eye.z, end.x, end.y, end.z, 1.55F * fade,
				red, green, blue, Math.round(190.0F * fade)));
		beams.add(new Beam(eye.x, eye.y, eye.z, end.x, end.y, end.z, 0.52F * fade,
				255, 255, 255, Math.round(255.0F * fade)));
	}

	/** Mirrors the server's own fallback so the beam lands where the damage did. */
	private static Vec3 laserEnd(ClientLevel level, WorldInterfaceEntity boss, BossActionS2C action,
			float partialTick) {
		if (!action.targetIds().isEmpty()) {
			UUID targetId = action.targetIds().getFirst();
			for (Player player : level.players()) {
				if (player.getUUID().equals(targetId)) return player.getEyePosition(partialTick);
			}
		}
		return boss.getEyePosition(partialTick).add(boss.getViewVector(partialTick).scale(48.0D));
	}

	private static void extractOrbs(ClientLevel level, WorldInterfaceClientState.Projection projection,
			WorldInterfaceSnapshotS2C encounter, Vec3 eye, long gameTime, float partialTick, int band,
			List<Beam> beams, List<Halo> halos) {
		// Gate the scan on the action window rather than paying for an arena-wide entity query every
		// frame of the fight: the orb only ever exists inside its own envelope.
		if (!projection.actionActive(gameTime)
				|| projection.action().action() != WorldInterfaceProtocol.BossAction.ENERGY_ORB) return;
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
			float growth = (scale - WorldInterfaceEnergyOrbEntity.MIN_SCALE)
					/ (WorldInterfaceEnergyOrbEntity.MAX_SCALE - WorldInterfaceEnergyOrbEntity.MIN_SCALE);
			float breath = 0.92F + Mth.sin(gameTime * 0.31F + index) * 0.08F;
			halos.add(new Halo(position.x, position.y, position.z, scale * 0.62F * breath,
					255, 255, 255, 128));
			halos.add(new Halo(position.x, position.y, position.z, scale * 1.18F * breath,
					red, green, blue, 96));
			// While the orb is still swelling the interface is visibly feeding it.
			if (growth >= 0.72F) continue;
			beams.add(new Beam(eye.x, eye.y, eye.z, position.x, position.y, position.z,
					0.10F + growth * 0.14F, red, green, blue,
					Math.round(Mth.lerp(growth, 150.0F, 30.0F))));
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
