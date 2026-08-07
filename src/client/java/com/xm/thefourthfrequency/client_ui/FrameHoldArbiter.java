package com.xm.thefourthfrequency.client_ui;

/**
 * The single decision point for whether this frame is held.
 *
 * <p>Two unrelated systems want to freeze the picture: the pursuit presentation, and the boss
 * fight's hit-stop. Both work the same way - skip the GPU clear so the previous frame survives in
 * the target and gets blitted again - which means both want to redirect the same call in
 * {@code Minecraft.runTick}. Mixin will not allow that: two {@code @Redirect}s on one instruction
 * is a hard failure at load time, not a subtle bug. So the redirect stays single and asks here.
 */
public final class FrameHoldArbiter {
	private FrameHoldArbiter() {
	}

	/**
	 * Whether the previous frame should be held instead of cleared.
	 *
	 * <p>The {@code |} is deliberate and must not be turned into {@code ||}. Both sides consume
	 * state - each advances its own hold clock and decides whether its window has expired - so
	 * short-circuiting would leave whichever source came second frozen in time on any frame the
	 * first one happened to claim, and it would only show up while both were active at once.
	 */
	public static boolean beginFrame(long nowNanos) {
		return PursuitPresentationClient.beginFrame(nowNanos) | WorldInterfaceHitStop.beginFrame(nowNanos);
	}

	/**
	 * Whether the world render should be cancelled for this frame.
	 *
	 * <p>Must be asked by the same set of sources as {@link #beginFrame}, and this is not optional.
	 * Holding a frame is two coordinated actions: skip the clear, <em>and</em> skip the render. Do
	 * only the first and the renderer draws into a depth buffer that still holds the previous
	 * frame's values, so the incoming geometry fails the depth test and the entire world vanishes
	 * for that frame - leaving the sky, the HUD, and nothing else.
	 *
	 * <p>That is exactly what happened when hit-stop was wired into the clear site alone: every
	 * freeze produced one or more frames with no world in them, which reads as the screen violently
	 * flickering rather than as an impact landing.
	 *
	 * <p>{@code |} rather than {@code ||} for the same reason as above - the pursuit side disarms
	 * its own flag when read, so it must be evaluated on every frame.
	 */
	public static boolean skipRenderFrame() {
		return PursuitPresentationClient.skipRenderFrame() | WorldInterfaceHitStop.skipRenderFrame();
	}
}
