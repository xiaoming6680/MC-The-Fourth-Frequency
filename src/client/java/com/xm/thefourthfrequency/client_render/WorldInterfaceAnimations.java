package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Thirty-one reusable native clips composed behind the fourteen stable action wire ids. */
public final class WorldInterfaceAnimations {
	public static final int PROTOCOL_ACTION_COUNT = 14;
	public static final int CLIPS_PER_ACTION = 2;
	public static final int AUTHORED_CLIP_COUNT = 31;

	public static final AnimationDefinition IDLE_BODY = AnimationDefinition.Builder.withLength(4.0F).looping()
			.addAnimation("body", rotation(0.0F, 0, 0, -2, 2.0F, 0, 4, 2,
					4.0F, 0, 0, -2)).build();
	public static final AnimationDefinition IDLE_EYE = AnimationDefinition.Builder.withLength(4.0F).looping()
			.addAnimation("eye", scale(0.0F, 1.0, 1.0, 1.0, 1.8F, 1.08, 1.08, 1.08,
					2.2F, 0.94, 1.05, 1.0, 4.0F, 1.0, 1.0, 1.0)).build();
	public static final AnimationDefinition IDLE_RING = AnimationDefinition.Builder.withLength(4.0F).looping()
			.addAnimation("ring", rotation(0.0F, 0, 0, 0, 4.0F, 0, 360, 0)).build();

	public static final AnimationDefinition LASER_AIM = eyeAim(2.3F, 58.0F);
	public static final AnimationDefinition LASER_APERTURE = eyeAperture(2.3F, 1.30);
	public static final AnimationDefinition ORB_CHARGE = coreCharge(5.0F, 3.8F, 24.0F);
	public static final AnimationDefinition ORB_RELEASE = eyeCharge(5.0F, 3.8F, 1.42);
	public static final AnimationDefinition GRAB_REACH = warningLean(3.55F, 1.5F, -24.0F);
	public static final AnimationDefinition GRAB_SLAM = tendrilStrike(3.55F, 1.5F, -82.0F, false);
	public static final AnimationDefinition MENTAL_FOCUS = eyeHold(6.0F, 2.0F, -38.0F);
	public static final AnimationDefinition MENTAL_DISTORTION = ringDistortion(6.0F, 2.0F, 1.34);
	public static final AnimationDefinition WEAPON_REACH = tendrilHold(9.8F, 1.75F, 74.0F);
	public static final AnimationDefinition WEAPON_HOLD = weaponHold(9.8F, 1.75F);
	public static final AnimationDefinition THROW_CAPTURE = warningLean(3.0F, 1.5F, -38.0F);
	public static final AnimationDefinition THROW_RELEASE = tendrilStrike(3.0F, 1.5F, -118.0F, true);
	public static final AnimationDefinition HOTBAR_GAZE = eyeHold(5.65F, 2.0F, 96.0F);
	public static final AnimationDefinition HOTBAR_PURGE = ringDistortion(5.65F, 2.0F, 1.46);
	public static final AnimationDefinition ARROW_CATCH = tendrilCatch(2.0F);
	public static final AnimationDefinition ARROW_REFLECTION = reflectionPulse(2.0F, 1.28);
	public static final AnimationDefinition EVICTION_CORRUPTION = eyeHold(6.0F, 2.0F, 180.0F);
	public static final AnimationDefinition FORCED_EXPULSION = expulsionPulse(6.0F, 1.72);
	public static final AnimationDefinition SUMMON_CORE = summonCore(5.0F, 3.8F);
	public static final AnimationDefinition SUMMON_LIMBS = limbMorph(5.0F, 3.8F, 1.08);
	public static final AnimationDefinition MORPH_SECOND_CORE = coreMorph(3.0F, -34.0F, 1.62, false);
	public static final AnimationDefinition MORPH_SECOND_LIMBS = limbMorph(3.0F, 2.04F, 1.14);
	public static final AnimationDefinition MORPH_THIRD_CORE = coreMorph(3.0F, 48.0F, 1.88, true);
	public static final AnimationDefinition MORPH_THIRD_LIMBS = limbMorph(3.0F, 2.04F, 1.24);
	public static final AnimationDefinition SUCCESS_COLLAPSE = successCollapse();
	public static final AnimationDefinition SUCCESS_FADE = successFade();
	public static final AnimationDefinition FAILURE_BLACKEN = failureBlacken();
	public static final AnimationDefinition FAILURE_ESCAPE = failureEscape();

	private static final AnimationDefinition[] NO_CLIPS = new AnimationDefinition[0];
	private static final AnimationDefinition[] IDLE_CLIPS = {IDLE_BODY, IDLE_EYE, IDLE_RING};
	private static final AnimationDefinition[][] ACTION_CLIPS = {
			NO_CLIPS,
			{LASER_AIM, LASER_APERTURE},
			{ORB_CHARGE, ORB_RELEASE},
			{GRAB_REACH, GRAB_SLAM},
			{MENTAL_FOCUS, MENTAL_DISTORTION},
			{WEAPON_REACH, WEAPON_HOLD},
			{THROW_CAPTURE, THROW_RELEASE},
			{HOTBAR_GAZE, HOTBAR_PURGE},
			{ARROW_CATCH, ARROW_REFLECTION},
			{EVICTION_CORRUPTION, FORCED_EXPULSION},
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

	private static AnimationDefinition warningLean(float seconds, float warningSeconds, float yaw) {
		float release = seconds - 0.12F;
		return AnimationDefinition.Builder.withLength(seconds)
				.addAnimation("body", rotation(0.0F, 0, 0, 0, warningSeconds, -8, yaw, 6,
						release, -8, yaw, 6, seconds, 0, 0, 0))
				.addAnimation("eye", scale(0.0F, 1, 1, 1, warningSeconds, 1.18, 1.18, 1.18,
						release, 1.18, 1.18, 1.18, seconds, 1, 1, 1)).build();
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

	private static AnimationDefinition tendrilStrike(float seconds, float warningSeconds,
			float pitch, boolean throwAway) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds);
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			float releasePitch = throwAway ? pitch * -0.55F : pitch * 1.28F;
			float releaseYaw = throwAway ? side * -86.0F : side * -34.0F;
			float releaseRoll = throwAway ? side * -38.0F : side * 66.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, 0, side * 18,
					warningSeconds, pitch, side * 24, side * 42,
					seconds - 0.18F, pitch, side * 24, side * 42,
					seconds - 0.05F, releasePitch, releaseYaw, releaseRoll,
					seconds, 0, 0, side * 18));
		}
		return builder.build();
	}

	private static AnimationDefinition tendrilCatch(float seconds) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(seconds).looping();
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			builder.addAnimation("tendril_" + index, rotation(0.0F, 0, side * -12, side * 16,
					seconds * 0.5F, -32, side * 38, side * 54,
					seconds, 0, side * -12, side * 16));
		}
		return builder.build();
	}

	private static AnimationDefinition reflectionPulse(float seconds, double peakScale) {
		return AnimationDefinition.Builder.withLength(seconds).looping()
				.addAnimation("body", rotation(0.0F, 0, 0, -4, seconds * 0.5F, 0, 24, 4,
						seconds, 0, 0, -4))
				.addAnimation("ring", scale(0.0F, 1, 1, 1, seconds * 0.5F,
						peakScale, peakScale, peakScale, seconds, 1, 1, 1)).build();
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
