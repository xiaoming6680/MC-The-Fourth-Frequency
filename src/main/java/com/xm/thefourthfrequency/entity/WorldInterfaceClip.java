package com.xm.thefourthfrequency.entity;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * One keyframe clip, in a form both sides of the mod can read.
 *
 * <p><b>Why this exists instead of {@code AnimationDefinition}.</b> Vanilla's clip classes live in
 * {@code net.minecraft.client.animation}, and this project splits its source sets, so the server
 * cannot see them. That was survivable while the boss's hit boxes stood on the model's <em>bind</em>
 * pose and ignored everything the clips did to it - survivable in the sense that it compiled, not in
 * the sense that it worked. A clip swings the centre head through ninety-six degrees of yaw, which at
 * third form carries the skull the better part of twenty blocks, and the box a player was swinging at
 * stayed where the head would have been if the storm were holding still.
 *
 * <p>So the clip data lives here, in common, and the client builds vanilla definitions out of it
 * rather than authoring its own. The arithmetic below is a deliberate reproduction of what
 * {@code KeyframeAnimation.apply} does - the same keyframe search, the same Catmull-Rom, the same
 * additive application onto the bind pose - so that "where the server says the bone is" and "where
 * the client draws it" are one computation over one set of numbers.
 *
 * <p>Frame values are stored in <em>engine</em> units, already converted the way
 * {@code KeyframeAnimations.degreeVec}, {@code posVec} and {@code scaleVec} convert them: radians for
 * rotation, model units with Y negated for position, and value-minus-one for scale. Storing authored
 * units instead would leave that conversion to be performed twice, which is the class of divergence
 * this file exists to end.
 */
public final class WorldInterfaceClip {
	/** Which property of a bone a channel drives. Matches {@code AnimationChannel.Targets}. */
	public enum Target {
		POSITION,
		ROTATION,
		SCALE
	}

	/**
	 * One property's worth of keyframes, not yet attached to a bone.
	 *
	 * @param frames flattened {@code [seconds, x, y, z]} quadruples, in engine units
	 */
	public record Channel(Target target, float[] frames) {
		public Channel {
			if (target == null) throw new IllegalArgumentException("Channel needs a target");
			if (frames == null || frames.length < 4 || frames.length % 4 != 0) {
				throw new IllegalArgumentException("Channel frames must be a non-empty run of quadruples");
			}
		}

		public int frameCount() {
			return frames.length / 4;
		}

		public float timestamp(int frame) {
			return frames[frame * 4];
		}

		/** Axis 0, 1 or 2 of a frame's value. */
		public float value(int frame, int axis) {
			return frames[frame * 4 + 1 + axis];
		}
	}

	/** A channel bound to the bone it drives. */
	public record Track(String bone, Channel channel) {
		public Track {
			if (bone == null || bone.isBlank()) throw new IllegalArgumentException("Track needs a bone");
			if (channel == null) throw new IllegalArgumentException("Track needs a channel");
		}
	}

	private final float lengthSeconds;
	private final boolean looping;
	private final List<Track> tracks;

	private WorldInterfaceClip(float lengthSeconds, boolean looping, List<Track> tracks) {
		this.lengthSeconds = lengthSeconds;
		this.looping = looping;
		this.tracks = List.copyOf(tracks);
	}

	public float lengthSeconds() {
		return lengthSeconds;
	}

	public boolean looping() {
		return looping;
	}

	public List<Track> tracks() {
		return tracks;
	}

	public static Builder builder(float lengthSeconds) {
		return new Builder(lengthSeconds);
	}

	/**
	 * Accumulates this clip onto {@code pose} at {@code ageMillis}.
	 *
	 * <p>Additive, exactly as vanilla is: every target offsets whatever the bind pose and any earlier
	 * clip already put there. Bones the pose does not track are skipped rather than rejected - the
	 * pose carries the skeleton the hit geometry stands on, and clips legitimately drive jaws, the
	 * kernel and the weapon, none of which anything can be hit on.
	 */
	public void apply(WorldInterfaceRig.Pose pose, long ageMillis) {
		float seconds = elapsedSeconds(ageMillis);
		for (Track track : tracks) {
			WorldInterfaceRig.Bone bone = pose.bone(track.bone());
			if (bone == null) continue;
			Channel channel = track.channel();
			int frames = channel.frameCount();
			// Mth.binarySearch returns the first frame at or past the clock; vanilla steps one back
			// from it and clamps, which is what holds the last frame once a one-shot clip is over.
			int from = Math.max(0, Mth.binarySearch(0, frames,
					index -> seconds <= channel.timestamp(index)) - 1);
			int to = Math.min(frames - 1, from + 1);
			float span = channel.timestamp(to) - channel.timestamp(from);
			float delta = to == from || span <= 0.0F ? 0.0F
					: Mth.clamp((seconds - channel.timestamp(from)) / span, 0.0F, 1.0F);
			int before = Math.max(0, from - 1);
			int after = Math.min(frames - 1, to + 1);
			float x = Mth.catmullrom(delta, channel.value(before, 0), channel.value(from, 0),
					channel.value(to, 0), channel.value(after, 0));
			float y = Mth.catmullrom(delta, channel.value(before, 1), channel.value(from, 1),
					channel.value(to, 1), channel.value(after, 1));
			float z = Mth.catmullrom(delta, channel.value(before, 2), channel.value(from, 2),
					channel.value(to, 2), channel.value(after, 2));
			switch (channel.target()) {
				case POSITION -> bone.offsetPosition(x, y, z);
				case ROTATION -> bone.offsetRotation(x, y, z);
				case SCALE -> bone.offsetScale(x, y, z);
			}
		}
	}

	/** A looping clip wraps; a one-shot clip runs past its end and is held at its last frame. */
	private float elapsedSeconds(long ageMillis) {
		float seconds = ageMillis / 1000.0F;
		return looping ? seconds % lengthSeconds : seconds;
	}

	/**
	 * Mirrors {@code AnimationDefinition.Builder} closely enough that the authored clips read the
	 * same as they did when they lived on the client, which is what makes the move reviewable.
	 */
	public static final class Builder {
		private final float lengthSeconds;
		private final List<Track> tracks = new ArrayList<>();
		private boolean looping;

		private Builder(float lengthSeconds) {
			if (!(lengthSeconds > 0.0F)) throw new IllegalArgumentException("Clip length must be positive");
			this.lengthSeconds = lengthSeconds;
		}

		public Builder looping() {
			looping = true;
			return this;
		}

		public Builder addAnimation(String bone, Channel channel) {
			tracks.add(new Track(bone, channel));
			return this;
		}

		public WorldInterfaceClip build() {
			return new WorldInterfaceClip(lengthSeconds, looping, tracks);
		}
	}

	// -------------------------------------------------------------------------------------------
	// Channel authoring. One helper per target, taking the units a designer thinks in and storing
	// the units the engine applies - the same conversion KeyframeAnimations performs.
	// -------------------------------------------------------------------------------------------

	/** Degrees in, radians out. {@code [seconds, xDeg, yDeg, zDeg]} per frame. */
	public static Channel rotation(float... values) {
		float[] frames = new float[values.length];
		for (int offset = 0; offset < values.length; offset += 4) {
			frames[offset] = values[offset];
			frames[offset + 1] = values[offset + 1] * Mth.DEG_TO_RAD;
			frames[offset + 2] = values[offset + 2] * Mth.DEG_TO_RAD;
			frames[offset + 3] = values[offset + 3] * Mth.DEG_TO_RAD;
		}
		return new Channel(Target.ROTATION, frames);
	}

	/**
	 * A position channel, in model units. Y is negated on the way in, exactly as {@code posVec} does,
	 * so a positive Y in an authored clip means up.
	 */
	public static Channel translation(float... values) {
		float[] frames = new float[values.length];
		for (int offset = 0; offset < values.length; offset += 4) {
			frames[offset] = values[offset];
			frames[offset + 1] = values[offset + 1];
			frames[offset + 2] = -values[offset + 2];
			frames[offset + 3] = values[offset + 3];
		}
		return new Channel(Target.POSITION, frames);
	}

	/** Absolute scale in, offset-from-one out, so the channel composes additively. */
	public static Channel scale(double... values) {
		float[] frames = new float[values.length];
		for (int offset = 0; offset < values.length; offset += 4) {
			frames[offset] = (float) values[offset];
			frames[offset + 1] = (float) (values[offset + 1] - 1.0D);
			frames[offset + 2] = (float) (values[offset + 2] - 1.0D);
			frames[offset + 3] = (float) (values[offset + 3] - 1.0D);
		}
		return new Channel(Target.SCALE, frames);
	}
}
