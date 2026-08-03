package com.xm.thefourthfrequency.ending;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Short-lived expanding particle rings for encounter beats that deserve a world-space event rather
 * than a HUD line: the two morphs and the summon.
 *
 * <p>Each wave is pure presentation. It owns no authoritative state, is never persisted, and is
 * dropped wholesale on restart, so a lost wave can never desynchronise the encounter.</p>
 */
public final class WorldInterfaceShockwaveService {
	public static final int MORPH_DURATION_TICKS = 30;
	public static final double MORPH_MAX_RADIUS = 42.0D;
	/** Samples per ring. Emitting on alternating ticks keeps the packet cost near vanilla's. */
	public static final int RING_SAMPLES = 32;
	public static final int EMIT_INTERVAL_TICKS = 2;
	public static final int MAX_CONCURRENT_WAVES = 16;

	private static final List<Wave> ACTIVE = new CopyOnWriteArrayList<>();
	private static boolean initialized;

	private WorldInterfaceShockwaveService() {
	}

	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
	}

	/** Queues one ring. Silently drops the request once the concurrency cap is reached. */
	public static boolean emit(ServerLevel level, Vec3 origin, int durationTicks, double maxRadius) {
		initialize();
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(origin, "origin");
		if (durationTicks <= 0 || !(maxRadius > 0.0D) || !Double.isFinite(maxRadius)) {
			throw new IllegalArgumentException("Shockwave needs a positive duration and radius");
		}
		if (ACTIVE.size() >= MAX_CONCURRENT_WAVES) return false;
		ACTIVE.add(new Wave(level, origin, level.getGameTime(), durationTicks, maxRadius));
		return true;
	}

	/** Advances every wave bound to this level. Returns how many are still alive afterwards. */
	public static int tick(ServerLevel level) {
		if (ACTIVE.isEmpty()) return 0;
		long gameTime = level.getGameTime();
		int remaining = 0;
		List<Wave> finished = new ArrayList<>();
		for (Wave wave : ACTIVE) {
			if (wave.level() != level) continue;
			long age = gameTime - wave.startTick();
			if (age < 0L || age >= wave.durationTicks()) {
				finished.add(wave);
				continue;
			}
			remaining++;
			if (age % EMIT_INTERVAL_TICKS != 0L) continue;
			emitRing(wave, age / (float) wave.durationTicks());
		}
		ACTIVE.removeAll(finished);
		return remaining;
	}

	public static void clear(ServerLevel level) {
		for (Iterator<Wave> waves = ACTIVE.iterator(); waves.hasNext(); ) {
			Wave wave = waves.next();
			if (wave.level() == level) ACTIVE.remove(wave);
		}
	}

	public static int activeWaveCount() {
		return ACTIVE.size();
	}

	private static void emitRing(Wave wave, float progress) {
		// Ease-out so the wave leaves fast and settles, which reads as a release of pressure.
		double eased = 1.0D - Math.pow(1.0D - progress, 2.6D);
		double radius = wave.maxRadius() * eased;
		// The trailing body lags behind the crisp leading edge so the ring reads as having depth.
		double bodyRadius = wave.maxRadius() * (1.0D - Math.pow(1.0D - Math.max(0.0F, progress - 0.12F), 2.6D));
		int alive = Math.max(4, Math.round(RING_SAMPLES * (0.45F + 0.55F * (1.0F - progress))));
		Vec3 origin = wave.origin();
		for (int index = 0; index < alive; index++) {
			double angle = index * (Math.PI * 2.0D) / alive;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			wave.level().sendParticles(ParticleTypes.END_ROD,
					origin.x + cos * radius, origin.y, origin.z + sin * radius,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
			if ((index & 1) != 0) continue;
			wave.level().sendParticles(ParticleTypes.REVERSE_PORTAL,
					origin.x + cos * bodyRadius, origin.y - 1.0D, origin.z + sin * bodyRadius,
					2, 0.35D, 0.9D, 0.35D, 0.02D);
		}
	}

	private record Wave(ServerLevel level, Vec3 origin, long startTick, int durationTicks, double maxRadius) {
	}
}
