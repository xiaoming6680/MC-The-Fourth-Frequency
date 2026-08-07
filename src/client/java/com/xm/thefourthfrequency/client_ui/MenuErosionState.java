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
	/**
	 * Boot splashes withheld once the story has been finished.
	 *
	 * <p>The boot catalogue is written to a player who does not yet know what this place is: it asks
	 * where everyone went, warns that the way ahead is unknown, and says someone has not left. Every
	 * one of those is a question the ending answers. Read on the title screen of a save that has
	 * already been through it, they stop being unsettling and start being wrong - the menu insisting
	 * on a mystery the player closed.
	 *
	 * <p>The ten that remain are the ones that survive knowing: they describe the place rather than
	 * promise anything about it.
	 */
	private static final int[] ENDING_WITHHELD_BOOT_SPLASHES = {2, 4, 5, 6, 9, 13, 14, 15, 17, 18, 20};

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
		return source.splashKey(splashIndex(source,
				WorldInterfaceResourcePackLease.presentationRetired(), SESSION_SPLASH_SEED));
	}

	/**
	 * Which splash of {@code stage}'s catalogue a session draws.
	 *
	 * <p>Pure, and public for {@code MenuErosionSplashTest}: the withheld set is a list of indices
	 * into a translation catalogue, and nothing else in the build would notice if it drifted off the
	 * end of that catalogue or started returning one of the entries it exists to suppress.
	 *
	 * @param endingReached whether this client has finished the story at least once
	 */
	public static int splashIndex(Stage stage, boolean endingReached, int seed) {
		if (!endingReached || stage != Stage.BOOT) {
			return Math.floorMod(seed, stage.splashCount());
		}
		int[] pool = new int[stage.splashCount() - ENDING_WITHHELD_BOOT_SPLASHES.length];
		int cursor = 0;
		for (int index = 0; index < stage.splashCount(); index++) {
			if (!withheldAfterEnding(index)) pool[cursor++] = index;
		}
		return pool[Math.floorMod(seed, pool.length)];
	}

	/** Whether this boot-catalogue index is one the ending retires. */
	public static boolean withheldAfterEnding(int index) {
		for (int withheld : ENDING_WITHHELD_BOOT_SPLASHES) if (withheld == index) return true;
		return false;
	}
	public static void resetForReplay() { stage = Stage.BOOT; }
	public static void setForTesting(Stage value) { stage = value; }
}
