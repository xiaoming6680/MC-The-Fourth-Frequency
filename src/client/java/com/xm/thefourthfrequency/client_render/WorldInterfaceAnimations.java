package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

import java.util.ArrayList;
import java.util.List;

/** Thirty-seven reusable native clips composed behind the thirteen live action wire ids. */
public final class WorldInterfaceAnimations {
	public static final int PROTOCOL_ACTION_COUNT = 13;
	/** Windup, release and - for the nine attacks - an authored recovery. */
	public static final int CLIPS_PER_ACTION = 3;
	public static final int AUTHORED_CLIP_COUNT = 37;
	/** Limb bones the model bakes; the clips below address every one of them individually. */
	public static final int TENDRIL_COUNT = 10;

	public static final AnimationDefinition IDLE_BODY = AnimationDefinition.Builder.withLength(4.0F).looping()
			.addAnimation("body", rotation(0.0F, 0, 0, -2, 2.0F, 0, 4, 2,
					4.0F, 0, 0, -2)).build();
	public static final AnimationDefinition IDLE_EYE = AnimationDefinition.Builder.withLength(4.0F).looping()
			.addAnimation("eye", scale(0.0F, 1.0, 1.0, 1.0, 1.8F, 1.08, 1.08, 1.08,
					2.2F, 0.94, 1.05, 1.0, 4.0F, 1.0, 1.0, 1.0)).build();
	public static final AnimationDefinition IDLE_RING = AnimationDefinition.Builder.withLength(4.0F).looping()
			.addAnimation("ring", rotation(0.0F, 0, 0, 0, 4.0F, 0, 360, 0)).build();

	// Every clip length below is the wire action's server duration in seconds. The laser sweeps for
	// two seconds after its lock, the grabs carry before they land, and the lance falls at 2.0s -
	// keeping the clips on those clocks is what makes the body read as driving the attack rather
	// than as playing over it.
	public static final AnimationDefinition LASER_AIM = eyeAim(6.5F, 58.0F);
	public static final AnimationDefinition LASER_APERTURE = eyeAperture(6.5F, 1.30);
	public static final AnimationDefinition ORB_CHARGE = coreCharge(3.0F, 0.5F, 24.0F);
	public static final AnimationDefinition ORB_RELEASE = eyeCharge(3.0F, 0.5F, 1.42);
	public static final AnimationDefinition LANCE_FOCUS = eyeHold(5.5F, 3.0F, -38.0F);
	public static final AnimationDefinition LANCE_DESCENT = lanceDescent(5.5F, 3.0F, 4.5F);
	public static final AnimationDefinition WEAPON_REACH = tendrilHold(10.8F, 2.75F, 74.0F);
	public static final AnimationDefinition WEAPON_HOLD = weaponHold(10.8F, 2.75F);
	public static final AnimationDefinition THROW_CAPTURE = grabCarry(4.5F, 2.5F, 3.2F, 3.7F, -38.0F);
	public static final AnimationDefinition THROW_RELEASE = tendrilCarryStrike(4.5F, 2.5F, 3.2F, 3.7F,
			-118.0F, true);
	public static final AnimationDefinition HOTBAR_GAZE = eyeHold(6.65F, 3.0F, 96.0F);
	public static final AnimationDefinition HOTBAR_PURGE = ringDistortion(6.65F, 3.0F, 1.46);
	public static final AnimationDefinition TENDRIL_REAR = tendrilRear(6.75F, 2.25F);
	public static final AnimationDefinition TENDRIL_LASH = tendrilFlurry(6.75F, 3.25F, 1.5F, 3);
	public static final AnimationDefinition EVICTION_CORRUPTION = eyeHold(6.0F, 2.0F, 180.0F);
	public static final AnimationDefinition FORCED_EXPULSION = expulsionPulse(6.0F, 1.72);
	public static final AnimationDefinition SUMMON_CORE = summonCore(5.0F, 3.8F);
	public static final AnimationDefinition SUMMON_LIMBS = limbMorph(5.0F, 3.8F, 1.08);
	public static final AnimationDefinition MORPH_SECOND_CORE = coreMorph(3.0F, -34.0F, 1.62, false);
	public static final AnimationDefinition MORPH_SECOND_LIMBS = limbMorph(3.0F, 2.04F, 1.14);
	public static final AnimationDefinition MORPH_THIRD_CORE = coreMorph(3.0F, 48.0F, 1.88, true);
	public static final AnimationDefinition MORPH_THIRD_LIMBS = limbMorph(3.0F, 2.04F, 1.24);
	// One authored recovery per attack. Each drives a bone its own pair leaves alone, so the settle
	// composes cleanly instead of fighting the release, and each overshoots past neutral before
	// damping - that overshoot is the whole reason a blow reads as having had mass behind it.
	public static final AnimationDefinition LASER_RECOVER = AnimationDefinition.Builder.withLength(6.5F)
			.addAnimation("body", rotation(0.0F, 0, 0, 0, 6.20F, 0, 0, 0,
					6.30F, 14, -9, 0, 6.41F, -5, 3, 0, 6.5F, 0, 0, 0)).build();
	public static final AnimationDefinition ORB_RECOVER = AnimationDefinition.Builder.withLength(3.0F)
			.addAnimation("jaw", rotation(0.0F, 0, 0, 0, 0.50F, -26, 0, 0,
					0.76F, 12, 0, 0, 1.20F, -6, 0, 0, 3.0F, 0, 0, 0)).build();
	public static final AnimationDefinition LANCE_RECOVER = AnimationDefinition.Builder.withLength(5.5F)
			.addAnimation("jaw", rotation(0.0F, 0, 0, 0, 4.44F, -34, 0, 0,
					4.60F, 15, 0, 0, 5.5F, 0, 0, 0)).build();
	public static final AnimationDefinition WEAPON_RECOVER = AnimationDefinition.Builder.withLength(10.8F)
			.addAnimation("body", rotation(0.0F, 0, 0, 0, 10.62F, 0, 0, 0,
					10.70F, 21, 0, -12, 10.76F, -8, 0, 5, 10.8F, 0, 0, 0)).build();
	public static final AnimationDefinition THROW_RECOVER = AnimationDefinition.Builder.withLength(4.5F)
			.addAnimation("body", rotation(0.0F, 0, 0, 0, 3.62F, 0, 0, 0,
					3.78F, -22, 46, 0, 4.02F, 6, -14, 0, 4.5F, 0, 0, 0)).build();
	public static final AnimationDefinition HOTBAR_RECOVER = AnimationDefinition.Builder.withLength(6.65F)
			.addAnimation("jaw", rotation(0.0F, 0, 0, 0, 6.50F, -20, 0, 0,
					6.56F, 9, 0, 0, 6.61F, -4, 0, 0, 6.65F, 0, 0, 0)).build();
	public static final AnimationDefinition TENDRIL_RECOVER = AnimationDefinition.Builder.withLength(6.75F)
			.addAnimation("jaw", rotation(0.0F, 0, 0, 0, 3.25F, -22, 0, 0,
					4.75F, -8, 0, 0, 6.25F, -22, 0, 0, 6.75F, 0, 0, 0)).build();
	public static final AnimationDefinition EXPULSION_RECOVER = AnimationDefinition.Builder.withLength(6.0F)
			.addAnimation("ring", rotation(0.0F, 0, 0, 0, 3.30F, 0, 0, 0,
					4.10F, 0, -190, 0, 5.10F, 0, 64, 0, 6.0F, 0, 0, 0)).build();

	public static final AnimationDefinition SUCCESS_COLLAPSE = successCollapse();
	public static final AnimationDefinition SUCCESS_FADE = successFade();
	public static final AnimationDefinition FAILURE_BLACKEN = failureBlacken();
	public static final AnimationDefinition FAILURE_ESCAPE = failureEscape();

	private static final AnimationDefinition[] NO_CLIPS = new AnimationDefinition[0];
	private static final AnimationDefinition[] IDLE_CLIPS = {IDLE_BODY, IDLE_EYE, IDLE_RING};
	private static final AnimationDefinition[][] ACTION_CLIPS = {
			NO_CLIPS,
			{LASER_AIM, LASER_APERTURE, LASER_RECOVER},
			{ORB_CHARGE, ORB_RELEASE, ORB_RECOVER},
			// 3: the grab-slam is retired; the id keeps its slot so the table stays wire-indexed.
			NO_CLIPS,
			{LANCE_FOCUS, LANCE_DESCENT, LANCE_RECOVER},
			{WEAPON_REACH, WEAPON_HOLD, WEAPON_RECOVER},
			{THROW_CAPTURE, THROW_RELEASE, THROW_RECOVER},
			{HOTBAR_GAZE, HOTBAR_PURGE, HOTBAR_RECOVER},
			{TENDRIL_REAR, TENDRIL_LASH, TENDRIL_RECOVER},
			{EVICTION_CORRUPTION, FORCED_EXPULSION, EXPULSION_RECOVER},
			{SUMMON_CORE, SUMMON_LIMBS},
			{MORPH_SECOND_CORE, MORPH_SECOND_LIMBS},
			{MORPH_THIRD_CORE, MORPH_THIRD_LIMBS},
			{SUCCESS_COLLAPSE, SUCCESS_FADE},
			{FAILURE_BLACKEN, FAILURE_ESCAPE}
	};

	private WorldInterfaceAnimations() {
	}

	public static AnimationDefinition[] idleClips() {
		return IDLE_CLIPS.clone();
	}

	public static AnimationDefinition[] clipsForAction(int actionId) {
		if (actionId <= 0 || actionId >= ACTION_CLIPS.length) return NO_CLIPS;
		return ACTION_CLIPS[actionId].clone();
	}

	/** Compatibility accessor for callers that need the primary motion for a wire action. */
	public static AnimationDefinition forAction(int actionId) {
		AnimationDefinition[] clips = clipsForAction(actionId);
		return clips.length == 0 ? null : clips[0];
	}

	private static AnimationDefinition eyeAim(float seconds, float yaw) {
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("eye", rotation(0.0F, 0, -yaw * 0.35F, 0,
						seconds * 0.55F, 0, yaw, 0, seconds, 0, 0, 0)).build();
	}

	private static AnimationDefinition eyeAperture(float seconds, double peakScale) {
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("eye", scale(0.0F, 1, 1, 1, seconds * 0.55F,
						peakScale, peakScale, peakScale, seconds, 1, 1, 1)).build();
	}

	private static AnimationDefinition coreCharge(float seconds, float chargeSeconds, float yaw) {
		float release = Math.max(chargeSeconds, seconds - 0.12F);
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("body", rotation(0.0F, 0, 0, 0, chargeSeconds, 0, yaw, 0,
						release, 0, yaw, 0, seconds, 0, 0, 0)).build();
	}

	private static AnimationDefinition eyeCharge(float seconds, float chargeSeconds, double peakScale) {
		float release = Math.max(chargeSeconds, seconds - 0.12F);
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("eye", scale(0.0F, 1, 1, 1, chargeSeconds,
						peakScale, peakScale, peakScale, release, peakScale, peakScale, peakScale,
						seconds, 1, 1, 1)).build();
	}

	private static AnimationDefinition eyeHold(float seconds, float warningSeconds, float yaw) {
		float release = seconds - 0.12F;
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("eye", rotation(0.0F, 0, 0, 0, warningSeconds, 0, yaw, 0,
						release, 0, yaw, 0, seconds, 0, 0, 0))
				.addAnimation("eye", scale(0.0F, 1, 1, 1, warningSeconds, 1.34, 1.34, 1.34,
						release, 1.34, 1.34, 1.34, seconds, 1, 1, 1)).build();
	}

	private static AnimationDefinition ringDistortion(float seconds, float warningSeconds, double peakScale) {
		float release = seconds - 0.12F;
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("ring", rotation(0.0F, 0, 0, 0, warningSeconds, 0, 240, 0,
						release, 0, 720, 0, seconds, 0, 760, 0))
				.addAnimation("ring", scale(0.0F, 1, 1, 1, warningSeconds,
						peakScale, 0.72, peakScale, release, peakScale, 0.72, peakScale,
						seconds, 1, 1, 1)).build();
	}

	private static AnimationDefinition tendrilHold(float seconds, float warningSeconds, float pitch) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds);
		float release = seconds - 0.12F;
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, side * 18,
					warningSeconds, pitch, side * 18, side * 48,
					release, pitch, side * 18, side * 48, seconds, 0, 0, side * 18));
		}
		return builder.build();
	}

	private static AnimationDefinition weaponHold(float seconds, float warningSeconds) {
		float release = seconds - 0.12F;
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("weapon", rotation(0.0F, 0, 0, 0,
						warningSeconds, -18, 0, 38, release, -18, 0, 38,
						seconds, 0, 0, 0))
				.addAnimation("weapon", scale(0.0F, 0.05, 0.05, 0.05,
						warningSeconds, 1, 1, 1, release, 1, 1, 1,
						seconds, 0.05, 0.05, 0.05)).build();
	}

	/**
	 * The body half of a grab: it leans in during the warning, hauls back as the victim is lifted,
	 * holds through the carry, and unwinds on release. The carry is now a real interval on the
	 * server rather than a teleport, so the body has something to be doing for the whole of it.
	 */
	private static AnimationDefinition grabCarry(float seconds, float warningSeconds,
			float liftSeconds, float releaseSeconds, float yaw) {
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("body", rotation(0.0F, 0, 0, 0,
						warningSeconds, -8, yaw, 6,
						liftSeconds, -19, yaw * 1.25F, 12,
						releaseSeconds, -14, yaw * 1.1F, 9,
						seconds, 0, 0, 0))
				.addAnimation("eye", scale(0.0F, 1, 1, 1,
						warningSeconds, 1.18, 1.18, 1.18,
						liftSeconds, 1.30, 1.30, 1.30,
						releaseSeconds, 1.22, 1.22, 1.22,
						seconds, 1, 1, 1)).build();
	}

	/**
	 * The limb half of the same grab. The tendrils close at the warning, snap taut on the lift so
	 * the victim visibly comes up on them, and then either drive down or fling wide at release.
	 */
	private static AnimationDefinition tendrilCarryStrike(float seconds, float warningSeconds,
			float liftSeconds, float releaseSeconds, float pitch, boolean throwAway) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds);
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			float releasePitch = throwAway ? pitch * -0.55F : pitch * 1.28F;
			float releaseYaw = throwAway ? side * -86.0F : side * -34.0F;
			float releaseRoll = throwAway ? side * -38.0F : side * 66.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, side * 18,
					warningSeconds, pitch * 0.45F, side * 30, side * 30,
					liftSeconds, pitch, side * 24, side * 42,
					releaseSeconds, pitch * 0.92F, side * 20, side * 46,
					seconds - 0.05F, releasePitch, releaseYaw, releaseRoll,
					seconds, 0, 0, side * 18));
		}
		return builder.build();
	}

	/**
	 * The lance: the ring flattens and drives downward through the charge, then slams past neutral
	 * at the strike. The column itself is drawn in the beam batch; this is the interface pushing it.
	 */
	private static AnimationDefinition lanceDescent(float seconds, float lockSeconds, float strikeSeconds) {
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("ring", rotation(0.0F, 0, 0, 0, lockSeconds, 0, 220, 0,
						strikeSeconds, 0, 520, 0, seconds, 0, 620, 0))
				.addAnimation("ring", scale(0.0F, 1, 1, 1,
						lockSeconds, 1.24, 0.62, 1.24,
						strikeSeconds, 1.62, 0.24, 1.62,
						seconds, 1, 1, 1))
				.addAnimation("body", rotation(0.0F, 0, 0, 0,
						strikeSeconds - 0.1F, -9, 0, 0,
						strikeSeconds + 0.08F, 17, 0, 0,
						seconds, 0, 0, 0)).build();
	}

	/**
	 * The rear-up, staggered limb by limb rather than all ten at once.
	 *
	 * <p>Ten identical bones driven by one identical curve is not ten tentacles, it is one tentacle
	 * drawn ten times - which is exactly what the flurry looked like. Each limb now has its own
	 * arrival time, its own height and its own splay, derived from its index, so the mass rears in a
	 * ripple around the body.</p>
	 */
	private static AnimationDefinition tendrilRear(float seconds, float warningSeconds) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds);
		for (int index = 0; index < TENDRIL_COUNT; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			// Phase runs around the ring, so the rear-up travels rather than snapping.
			float phase = index / (float) TENDRIL_COUNT;
			float arrival = Math.max(0.12F, warningSeconds * (0.45F + phase * 0.55F));
			float rise = -58.0F - phase * 34.0F;
			float splay = 8.0F + phase * 22.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, side * 18,
					arrival, rise, side * splay, side * (18.0F + phase * 16.0F),
					seconds - 0.12F, rise * 0.92F, side * (splay + 6.0F), side * 22,
					seconds, 0, 0, side * 18));
		}
		return builder.build();
	}

	/**
	 * The flurry, authored per limb.
	 *
	 * <p>Each strike is thrown by a distinct pair of tendrils - a lead limb that whips through and a
	 * partner that follows a beat behind - while the rest hold their reared pose and drift. The
	 * server picks its target per strike, so what the body owes the fight is that a viewer can see
	 * <em>which</em> limb committed to each one; ten bones moving in lockstep could not say that.</p>
	 */
	private static AnimationDefinition tendrilFlurry(float seconds, float firstStrikeSeconds,
			float intervalSeconds, int strikes) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds);
		for (int index = 0; index < TENDRIL_COUNT; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			float phase = index / (float) TENDRIL_COUNT;
			// Which strike, if any, this limb leads; its partner is the next one round the ring.
			int lead = index % strikes;
			boolean partner = (index + 1) % strikes == lead % strikes && index % strikes != lead;
			List<float[]> frames = new ArrayList<>();
			frames.add(new float[]{0.0F, -58.0F - phase * 34.0F, side * (8.0F + phase * 22.0F),
					side * (18.0F + phase * 16.0F)});
			for (int strike = 0; strike < strikes; strike++) {
				float at = firstStrikeSeconds + intervalSeconds * strike;
				boolean leads = strike == lead;
				// Every limb reacts to every strike; only the one that leads it actually commits.
				float windup = leads ? -78.0F : -62.0F - phase * 10.0F;
				float swingYaw = leads ? side * 34.0F : side * (12.0F + phase * 10.0F);
				float lash = leads ? 64.0F : 6.0F + phase * 10.0F;
				float lashYaw = leads ? side * -52.0F : side * (-8.0F - phase * 12.0F);
				float lashRoll = leads ? side * -58.0F : side * (14.0F + phase * 12.0F);
				// The partner trails the lead by a fraction of the interval, so a strike lands as a
				// pair of limbs arriving slightly apart rather than as a single flat swipe.
				float delay = partner ? intervalSeconds * 0.16F : 0.0F;
				frames.add(new float[]{Math.max(0.05F, at - 0.26F + delay), windup, swingYaw,
						side * (20.0F + phase * 14.0F)});
				frames.add(new float[]{at + delay, lash, lashYaw, lashRoll});
			}
			frames.add(new float[]{seconds, 0.0F, 0.0F, side * 18.0F});
			float[] flat = new float[frames.size() * 4];
			int cursor = 0;
			for (float[] frame : frames) {
				cursor = keyframe(flat, cursor, frame[0], frame[1], frame[2], frame[3]);
			}
			builder.addAnimation("tendril_" + index, rotation(flat));
		}
		return builder.build();
	}

	private static int keyframe(float[] frames, int cursor, float time, float x, float y, float z) {
		frames[cursor] = time;
		frames[cursor + 1] = x;
		frames[cursor + 2] = y;
		frames[cursor + 3] = z;
		return cursor + 4;
	}

	private static AnimationDefinition expulsionPulse(float seconds, double peakScale) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("body", rotation(0.0F, 0, 0, 0, seconds * 0.55F, 0, 180, 0,
						seconds, 0, 0, 0))
				.addAnimation("body", scale(0.0F, 1, 1, 1, seconds * 0.55F,
						peakScale, peakScale, peakScale, seconds, 1, 1, 1));
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, 0,
					seconds * 0.55F, side * 48, side * 90, side * 72,
					seconds, 0, 0, 0));
		}
		return builder.build();
	}

	private static AnimationDefinition summonCore(float seconds, float chargeSeconds) {
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("body", scale(0.0F, 0.05, 0.05, 0.05, chargeSeconds,
						1.08, 1.08, 1.08, seconds, 1, 1, 1))
				.addAnimation("eye", scale(0.0F, 0.01, 0.01, 0.01, chargeSeconds,
						1.35, 1.35, 1.35, seconds, 1, 1, 1))
				.addAnimation("ring", rotation(0.0F, 0, 0, -90, seconds, 0, 540, 0)).build();
	}

	private static AnimationDefinition coreMorph(float seconds, float yaw, double peakScale, boolean openJaw) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("body", rotation(0.0F, 0, 0, 0, seconds * 0.55F, 0, yaw, 0,
						seconds, 0, 0, 0))
				.addAnimation("eye", scale(0.0F, 0.12, 0.12, 0.12, seconds * 0.62F,
						peakScale, peakScale, peakScale, seconds, 1, 1, 1))
				.addAnimation("ring", scale(0.0F, 0.05, 0.05, 0.05, seconds * 0.68F,
						peakScale, peakScale, peakScale, seconds, 1, 1, 1))
				.addAnimation("ring", rotation(0.0F, 0, 0, -90, seconds, 0, 540, 0));
		if (openJaw) {
			builder.addAnimation("jaw", rotation(0.0F, 0, 0, 0,
					seconds * 0.7F, -42, 0, 0, seconds, 0, 0, 0));
		}
		return builder.build();
	}

	private static AnimationDefinition limbMorph(float seconds, float revealSeconds, double overshoot) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds);
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			builder.addAnimation("tendril_" + index, scale(0.0F, 0.06, 0.06, 0.06,
					revealSeconds, overshoot, overshoot, overshoot, seconds, 1, 1, 1));
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, side * 70,
					revealSeconds, side * -18, side * 28, side * 14, seconds, 0, 0, side * 18));
		}
		return builder.build();
	}

	private static AnimationDefinition successCollapse() {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(6.0F)
				.addAnimation("body", rotation(0.0F, 0, 0, 0, 3.0F, 25, 0, 20,
						5.8F, 82, 0, 36, 6.0F, 82, 0, 36));
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, 0,
					5.8F, 78, side * 16, side * 28, 6.0F, 82, side * 18, side * 31));
		}
		return builder.build();
	}

	private static AnimationDefinition successFade() {
		return AnimationDefinition.Builder.withLength(6.0F)
				.addAnimation("eye", scale(0.0F, 1, 1, 1, 4.5F, 0.05, 0.05, 0.05,
						6.0F, 0.01, 0.01, 0.01))
				.addAnimation("ring", scale(0.0F, 1, 1, 1, 5.5F, 0.02, 0.02, 0.02,
						6.0F, 0.01, 0.01, 0.01)).build();
	}

	private static AnimationDefinition failureBlacken() {
		return AnimationDefinition.Builder.withLength(6.0F)
				.addAnimation("eye", scale(0.0F, 1, 1, 1, 3.0F, 0.55, 0.08, 0.55,
						4.5F, 1.4, 0.12, 1.4, 6.0F, 0.05, 0.05, 0.05))
				.addAnimation("jaw", rotation(0.0F, 0, 0, 0, 3.0F, -18, 0, 0,
						4.5F, -52, 0, 0, 6.0F, -12, 0, 0)).build();
	}

	private static AnimationDefinition failureEscape() {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(6.0F)
				.addAnimation("body", rotation(0.0F, 0, 0, 0, 3.0F, -25, 180, -18,
						6.0F, 0, 360, 0))
				.addAnimation("body", scale(0.0F, 1, 1, 1, 4.5F, 1.4, 0.22, 1.4,
						6.0F, 0.04, 0.04, 0.04))
				.addAnimation("ring", rotation(0.0F, 0, 0, 0, 4.5F, 0, 720, 0,
						6.0F, 0, 1080, 0))
				.addAnimation("ring", scale(0.0F, 1, 1, 1, 4.5F, 1.8, 1.8, 1.8,
						6.0F, 0.04, 0.04, 0.04));
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, 0,
					3.0F, side * 48, side * 120, side * 72,
					6.0F, side * -80, side * 220, side * -110));
		}
		return builder.build();
	}

	private static AnimationChannel rotation(float... values) {
		Keyframe[] frames = new Keyframe[values.length / 4];
		for (int index = 0; index < frames.length; index++) {
			int offset = index * 4;
			frames[index] = new Keyframe(values[offset], KeyframeAnimations.degreeVec(
					values[offset + 1], values[offset + 2], values[offset + 3]),
					AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, frames);
	}

	private static AnimationChannel scale(double... values) {
		Keyframe[] frames = new Keyframe[values.length / 4];
		for (int index = 0; index < frames.length; index++) {
			int offset = index * 4;
			frames[index] = new Keyframe((float) values[offset], KeyframeAnimations.scaleVec(
					values[offset + 1], values[offset + 2], values[offset + 3]),
					AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(AnimationChannel.Targets.SCALE, frames);
	}
}
