package com.xm.thefourthfrequency.client_render;

import com.xm.thefourthfrequency.entity.WorldInterfaceClip;
import com.xm.thefourthfrequency.entity.WorldInterfaceClips;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import org.joml.Vector3f;

/**
 * Vanilla clip definitions, built from the shared authored data in {@link WorldInterfaceClips}.
 *
 * <p><b>This file used to be where the clips lived.</b> That was the right place for exactly as long
 * as the animation was only a picture. It stopped being right when the hit boxes had to stand on the
 * animated skeleton rather than on the bind pose: the server has to know that the laser clip is
 * currently carrying the centre skull twenty blocks off its rest position, and the server cannot see
 * {@code net.minecraft.client.animation} at all.
 *
 * <p>So the thirty-seven clips moved to common and this became the adapter that hands them to
 * vanilla. Nothing about the drawn result changed: the frame values are the same numbers in the same
 * units, {@code WorldInterfaceClip} performs the same {@code degreeVec}/{@code posVec}/{@code
 * scaleVec} conversion the builders used to, and every channel is still CATMULLROM. What changed is
 * that there is now one copy of them, and the copy the player is hit by is the copy the player sees.
 */
public final class WorldInterfaceAnimations {
	public static final int PROTOCOL_ACTION_COUNT = WorldInterfaceClips.PROTOCOL_ACTION_COUNT;
	public static final int CLIPS_PER_ACTION = WorldInterfaceClips.CLIPS_PER_ACTION;
	public static final int AUTHORED_CLIP_COUNT = WorldInterfaceClips.AUTHORED_CLIP_COUNT;
	public static final int TENDRIL_COUNT = WorldInterfaceClips.TENDRIL_COUNT;
	public static final String[] HEADS = WorldInterfaceClips.HEADS.clone();

	private static final AnimationDefinition[] NO_CLIPS = new AnimationDefinition[0];

	private WorldInterfaceAnimations() {
	}

	public static AnimationDefinition[] idleClips() {
		return convert(WorldInterfaceClips.idleClips());
	}

	public static AnimationDefinition[] clipsForAction(int actionId) {
		return convert(WorldInterfaceClips.clipsForAction(actionId));
	}

	/** Compatibility accessor for callers that need the primary motion for a wire action. */
	public static AnimationDefinition forAction(int actionId) {
		AnimationDefinition[] clips = clipsForAction(actionId);
		return clips.length == 0 ? null : clips[0];
	}

	private static AnimationDefinition[] convert(WorldInterfaceClip[] clips) {
		if (clips.length == 0) return NO_CLIPS;
		AnimationDefinition[] converted = new AnimationDefinition[clips.length];
		for (int index = 0; index < clips.length; index++) converted[index] = convert(clips[index]);
		return converted;
	}

	private static AnimationDefinition convert(WorldInterfaceClip clip) {
		AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(clip.lengthSeconds());
		if (clip.looping()) builder.looping();
		for (WorldInterfaceClip.Track track : clip.tracks()) {
			builder.addAnimation(track.bone(), channel(track.channel()));
		}
		return builder.build();
	}

	private static AnimationChannel channel(WorldInterfaceClip.Channel channel) {
		Keyframe[] frames = new Keyframe[channel.frameCount()];
		for (int index = 0; index < frames.length; index++) {
			// Already in engine units - the shared clip performs the conversion KeyframeAnimations
			// would otherwise perform here, precisely so the server can perform it too.
			frames[index] = new Keyframe(channel.timestamp(index),
					new Vector3f(channel.value(index, 0), channel.value(index, 1),
							channel.value(index, 2)),
					AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(target(channel.target()), frames);
	}

	private static AnimationChannel.Target target(WorldInterfaceClip.Target target) {
		return switch (target) {
			case POSITION -> AnimationChannel.Targets.POSITION;
			case ROTATION -> AnimationChannel.Targets.ROTATION;
			case SCALE -> AnimationChannel.Targets.SCALE;
		};
	}
}
