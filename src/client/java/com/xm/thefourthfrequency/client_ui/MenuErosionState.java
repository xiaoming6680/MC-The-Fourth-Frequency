package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.MenuErosionStageS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Locale;

/** Client-session state: leaving a world preserves the last stage; only process restart returns to BOOT. */
public final class MenuErosionState {
	/** Splash copy is translated, so every stage owns splash.thefourthfrequency.&lt;stage&gt;.&lt;index&gt; keys. */
	public enum Stage {
		BOOT(21), EARLY(0), MID(0), LATE(0), RESTORED(0);

		private final int splashCount;

		Stage(int splashCount) { this.splashCount = splashCount; }

		public int splashCount() { return splashCount; }

		public String splashKey(int index) {
			return "splash.thefourthfrequency." + name().toLowerCase(Locale.ROOT) + "." + index;
		}
	}
	private static final int SESSION_SPLASH_SEED = (int) (System.nanoTime() >>> 8);
	private static volatile Stage stage = Stage.BOOT;
	private static boolean initialized;
	private MenuErosionState() { }
	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(MenuErosionStageS2C.TYPE, (payload, context) ->
				context.client().execute(() -> stage = Stage.values()[Math.clamp(payload.stage(), 0,
						Stage.values().length - 1)]));
	}
	public static Stage stage() { return stage; }
	/** A stage without its own catalog keeps the boot copy, so the slogan only shifts once the menu erodes. */
	public static String sessionSplashKey() {
		Stage source = stage.splashCount() > 0 ? stage : Stage.BOOT;
		return source.splashKey(Math.floorMod(SESSION_SPLASH_SEED, source.splashCount()));
	}
	public static void resetForReplay() { stage = Stage.BOOT; }
	public static void setForTesting(Stage value) { stage = value; }
}
