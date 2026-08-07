package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.ending.ScreenShakePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Camera shake, sampled per frame and applied by {@code CameraShakeMixin}.
 *
 * <p>All of the arithmetic lives in {@link ScreenShakePolicy} in {@code src/main} so it can be unit
 * tested; this holds the live impulses, the config cache and the mute rules, none of which a test
 * can reach anyway.
 *
 * <p><b>This only moves the rendering camera.</b> It never touches {@code LocalPlayer}'s yRot or
 * xRot, so where the player is aiming, what their crosshair is over, and every ray trace and attack
 * result are completely unaffected. The view shakes; the game does not.
 */
public final class ScreenShakeController {
	public enum Grade {
		LIGHT(ScreenShakePolicy.Grade.LIGHT),
		MEDIUM(ScreenShakePolicy.Grade.MEDIUM),
		HEAVY(ScreenShakePolicy.Grade.HEAVY),
		CATACLYSM(ScreenShakePolicy.Grade.CATACLYSM);

		private final ScreenShakePolicy.Grade policy;

		Grade(ScreenShakePolicy.Grade policy) {
			this.policy = policy;
		}
	}

	/** Pitch, yaw, up, right. Each gets its own phase so the knock is not a slide. */
	private static final int AXIS_PITCH = 0;
	private static final int AXIS_YAW = 1;
	private static final int AXIS_UP = 2;
	private static final int AXIS_RIGHT = 3;
	/** Translation is a fraction of a block at full strength; more reads as the camera detaching. */
	private static final double TRANSLATION_SCALE = 0.035D;

	private static final Impulse[] ACTIVE = new Impulse[ScreenShakePolicy.MAX_CONCURRENT];
	private static volatile double configuredScale = 1.0D;
	private static long nextSeed = 1L;
	/**
	 * Last computed offset, held for the rest of the frame.
	 *
	 * <p>{@code Camera.setup} runs more than once per frame, and the shake has to give the same
	 * answer every time it does. It is driven by {@code nanoTime}, so without this it does not:
	 * each call lands at a different point on the waveform and leaves the camera in a slightly
	 * different place. That is invisible on its own, but {@code WorldInterfaceBeamBatchRenderer}
	 * reads the camera's position and its basis vectors at two different moments and builds every
	 * beam and halo quad from the pair. Getting them from two different shake states expands those
	 * quads along the wrong axes - which reads as large blocks of colour flickering around the boss.
	 *
	 * <p>A short window rather than a frame counter because this has no reliable frame signal to
	 * hook; 4 ms is longer than any real frame at 250 fps and far shorter than the 13-27 Hz
	 * waveforms being sampled, so it cannot flatten the shake itself.
	 */
	private static final long FRAME_CACHE_NANOS = 4_000_000L;
	private static Sample cachedSample;
	private static long cachedSampleNanos = Long.MIN_VALUE;
	private static boolean cachedSampleValid;

	private ScreenShakeController() {
	}

	private record Impulse(long startNanos, double durationSeconds, double peakDegrees,
			double scale, long seed) {
	}

	public record Sample(float pitch, float yaw, float up, float right) {
	}

	/** Cached once per client tick: reading an AtomicReference every frame is not free. */
	public static void tick() {
		configuredScale = RuntimeServices.config().presentation().effectiveCameraShake();
	}

	/** An event that happened to the local player. No distance term: they are at the centre of it. */
	public static void impulse(Grade grade) {
		add(grade, 1.0D);
	}

	/** A world event. Falls off quadratically, so distance is legible rather than binary. */
	public static void impulseAt(Vec3 origin, double radius, Grade grade) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || origin == null) return;
		double falloff = ScreenShakePolicy.falloff(client.player.position().distanceTo(origin), radius);
		if (falloff <= 0.0D) return;
		add(grade, falloff);
	}

	private static synchronized void add(Grade grade, double strength) {
		if (grade == null || strength <= 0.0D || configuredScale <= 0.0D) return;
		long now = System.nanoTime();
		int slot = -1;
		double[] amplitudes = new double[ACTIVE.length];
		for (int index = 0; index < ACTIVE.length; index++) {
			Impulse impulse = ACTIVE[index];
			if (impulse == null || elapsed(impulse, now) >= impulse.durationSeconds()) {
				slot = index;
				break;
			}
			amplitudes[index] = impulse.peakDegrees() * impulse.scale()
					* ScreenShakePolicy.envelope(elapsed(impulse, now), impulse.durationSeconds());
		}
		// Every slot busy: drop the one contributing least, never the strongest in flight.
		if (slot < 0) slot = ScreenShakePolicy.weakestSlot(amplitudes);
		ACTIVE[slot] = new Impulse(now, grade.policy.seconds(), grade.policy.peakDegrees(),
				strength, nextSeed++);
	}

	public static synchronized void reset() {
		java.util.Arrays.fill(ACTIVE, null);
		cachedSample = null;
		cachedSampleValid = false;
		cachedSampleNanos = Long.MIN_VALUE;
	}

	private static double elapsed(Impulse impulse, long nowNanos) {
		return (nowNanos - impulse.startNanos()) / 1.0E9D;
	}

	/**
	 * The current offset, or null when nothing is shaking.
	 *
	 * <p>Driven by {@code System.nanoTime()} rather than by the tick counter: these are 13 to 27 Hz
	 * waveforms and a 20 Hz sample would alias them into a slow wobble or a strobe.
	 */
	public static synchronized Sample sample() {
		long frameNow = System.nanoTime();
		// Same answer for every call inside one frame. See FRAME_CACHE_NANOS.
		if (cachedSampleValid && frameNow - cachedSampleNanos < FRAME_CACHE_NANOS) return cachedSample;
		Sample computed = computeSample(frameNow);
		cachedSample = computed;
		cachedSampleNanos = frameNow;
		cachedSampleValid = true;
		return computed;
	}

	private static Sample computeSample(long now) {
		double scale = configuredScale;
		if (scale <= 0.0D || isMuted()) return null;
		double pitch = 0.0D;
		double yaw = 0.0D;
		double up = 0.0D;
		double right = 0.0D;
		boolean any = false;
		for (int index = 0; index < ACTIVE.length; index++) {
			Impulse impulse = ACTIVE[index];
			if (impulse == null) continue;
			double elapsed = elapsed(impulse, now);
			if (elapsed >= impulse.durationSeconds()) {
				ACTIVE[index] = null;
				continue;
			}
			double amplitude = impulse.scale() * scale;
			pitch += ScreenShakePolicy.sample(elapsed, impulse.durationSeconds(),
					impulse.peakDegrees(), amplitude, impulse.seed(), AXIS_PITCH);
			yaw += ScreenShakePolicy.sample(elapsed, impulse.durationSeconds(),
					impulse.peakDegrees(), amplitude, impulse.seed(), AXIS_YAW);
			up += ScreenShakePolicy.sample(elapsed, impulse.durationSeconds(),
					impulse.peakDegrees(), amplitude, impulse.seed(), AXIS_UP);
			right += ScreenShakePolicy.sample(elapsed, impulse.durationSeconds(),
					impulse.peakDegrees(), amplitude, impulse.seed(), AXIS_RIGHT);
			any = true;
		}
		if (!any) return null;
		return new Sample((float) pitch, (float) yaw,
				(float) (up * TRANSLATION_SCALE), (float) (right * TRANSLATION_SCALE));
	}

	/**
	 * Situations where the camera must be still.
	 *
	 * <p>The frame-hold case is the important one: a held frame is a still image by definition, so
	 * shaking the camera underneath it does nothing but desynchronise the two the moment the hold
	 * ends.
	 */
	private static boolean isMuted() {
		Minecraft client = Minecraft.getInstance();
		return client.isPaused() || client.player == null || client.level == null
				|| PursuitPresentationClient.isHoldingFrame()
				|| WorldInterfaceHitStop.isHolding();
	}
}
