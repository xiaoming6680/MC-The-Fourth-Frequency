package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.networking.WorldInterfaceBlastS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells everyone near a detonation that it happened, so their camera can answer to it.
 *
 * <p>Presentation only, and deliberately so: nothing here is persisted, nothing is acknowledged, and
 * a dropped packet costs one shake. What it is <em>not</em> is optional - see
 * {@link WorldInterfaceBlastS2C} for why the action envelope cannot carry this.
 *
 * <p><b>Throttled per source, and that is load-bearing.</b> The laser's contact point detonates every
 * other tick for two solid seconds; the third phase can have four attacks in the air at once. Without
 * a floor between two blasts from the same source, a single sweep is twenty packets, twenty shake
 * impulses fighting over four slots and - because every one of these sites plays a sound as well -
 * twenty overlapping explosion samples in the client's mixer. That is already unreadable as a mix
 * and wastes a finite channel pool, even though the long-phase silence was later measured at a peak
 * of only fifteen channels and traced to the server scheduler rather than to pool exhaustion.
 * Rate-limiting remains load-bearing for the camera and the mix, so both live here.
 */
public final class WorldInterfaceBlastService {
	/**
	 * Ticks between two blasts from one source.
	 *
	 * <p>Six is a third of a second: fast enough that a walking detonation still reads as continuous,
	 * slow enough that one sweep is seven events instead of twenty.
	 */
	public static final int MIN_GAP_TICKS = 6;
	/** Sources whose events are one-offs and must never be swallowed by another source's cooldown. */
	public static final String SOURCE_LANCE = "lance";
	public static final String SOURCE_LASER = "laser";
	public static final String SOURCE_ORB = "orb";
	public static final String SOURCE_TENDRIL = "tendril";
	public static final String SOURCE_ANCHOR = "anchor";
	public static final String SOURCE_SHOCKWAVE = "shockwave";

	private static final Map<MinecraftServer, Map<String, Long>> LAST_EMIT =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static boolean initialized;

	private WorldInterfaceBlastService() {
	}

	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		ServerLifecycleEvents.SERVER_STOPPED.register(LAST_EMIT::remove);
	}

	/**
	 * Whether {@code source} is allowed to speak again on this tick.
	 *
	 * <p>Public because the audio at each of these sites is gated on the same answer: a blast the
	 * camera is not told about must not play its own explosion either, or the throttle moves the
	 * problem into the mixer instead of solving it.
	 */
	public static boolean allows(ServerLevel level, String source, int minGapTicks) {
		initialize();
		if (level == null || source == null) return false;
		Map<String, Long> clocks = LAST_EMIT.computeIfAbsent(level.getServer(),
				ignored -> new ConcurrentHashMap<>());
		long now = level.getGameTime();
		Long previous = clocks.get(source);
		if (!permits(previous == null ? Long.MIN_VALUE : previous, now, minGapTicks)) return false;
		clocks.put(source, now);
		return true;
	}

	public static boolean allows(ServerLevel level, String source) {
		return allows(level, source, MIN_GAP_TICKS);
	}

	/**
	 * The cooldown rule itself, with no server attached so it can be tested.
	 *
	 * <p>{@code lastTick} is {@link Long#MIN_VALUE} for a source that has never spoken. A clock that
	 * has gone <em>backwards</em> - a restored save, an encounter recovered after a restart - counts
	 * as permitted rather than as an enormous remaining cooldown, which is the difference between a
	 * source that resumes and a source that is silent for the rest of the session.
	 */
	public static boolean permits(long lastTick, long now, int minGapTicks) {
		if (lastTick == Long.MIN_VALUE || lastTick > now) return true;
		return now - lastTick >= Math.max(1, minGapTicks);
	}

	/** Announces a detonation to every encounter participant inside its radius. */
	public static void emit(ServerLevel level, UUID encounterId, Vec3 origin, double radius,
			WorldInterfaceProtocol.BlastGrade grade) {
		initialize();
		Objects.requireNonNull(grade, "grade");
		if (level == null || encounterId == null || origin == null) return;
		if (level.dimension() != Level.END) return;
		if (!Double.isFinite(radius) || radius <= 0.0D) return;
		float bounded = (float) Math.min(radius, WorldInterfaceBlastS2C.MAX_RADIUS);
		WorldInterfaceBlastS2C payload = new WorldInterfaceBlastS2C(encounterId,
				origin.x, origin.y, origin.z, bounded, grade.wireId());
		double reachSqr = bounded * bounded;
		for (ServerPlayer player : level.players()) {
			// Outside the radius the falloff is zero anyway, so the packet would be pure cost.
			if (player.position().distanceToSqr(origin) > reachSqr) continue;
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Emits only if {@code source} is off cooldown. Returns whether anything was sent. */
	public static boolean emitThrottled(ServerLevel level, UUID encounterId, Vec3 origin, double radius,
			WorldInterfaceProtocol.BlastGrade grade, String source) {
		if (!allows(level, source)) return false;
		emit(level, encounterId, origin, radius, grade);
		return true;
	}
}
