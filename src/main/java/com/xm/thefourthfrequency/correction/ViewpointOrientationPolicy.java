package com.xm.thefourthfrequency.correction;

/** Shared trigger-time orientation for every viewpoint/body separation presentation. */
public final class ViewpointOrientationPolicy {
	private ViewpointOrientationPolicy() {
	}

	public static Orientation facePlayerForward(float playerYaw) {
		return new Orientation(playerYaw, 0.0F);
	}

	public record Orientation(float yaw, float pitch) {
	}
}
