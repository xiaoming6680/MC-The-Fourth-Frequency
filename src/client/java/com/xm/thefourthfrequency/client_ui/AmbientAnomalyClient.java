package com.xm.thefourthfrequency.client_ui;

/** Legacy facade retained for terminal rendering and old client tests. */
public final class AmbientAnomalyClient {
	private AmbientAnomalyClient() { }
	public static void initialize() { }
	public static float pulse() {
		int ticks = AnomalyPresentationController.remainingTicks();
		return ticks <= 0 ? 0.0F : (float) (0.5D + 0.5D * Math.sin(ticks * 0.7D));
	}
	public static String activeId() { return AnomalyPresentationController.activeId(); }
}
