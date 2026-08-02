package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;

/**
 * Decides which authored score - if any - the current situation calls for.
 *
 * <p>The mod ships three playlists rather than one: the menu, ordinary play, and the single track
 * that carries the encounter's resolution through the End poem. Everything else the vanilla music
 * manager would have chosen is deliberately dropped, because a background score playing over a boss
 * encounter or a pursuit is the one thing this mod's audio design cannot afford: both sequences are
 * carried entirely by their own cues, and a song underneath them tells the player the situation is
 * scored, therefore authored, therefore safe.</p>
 *
 * <p>Silence is expressed two ways. {@link #musicVolume(float)} drives the vanilla fade, so music
 * retreats over a few seconds when a situation turns hostile and swells back in when it does not -
 * that is the normal path, and it also gives every track a fade-in on start. The hard stop in
 * {@link #tick} is reserved for the pursuit blackout, where the fiction is that the feed is cut.</p>
 */
public final class MusicDirector {
	/** Menu pacing, and the vanilla "take over from whatever is playing" flag, matching Musics.MENU. */
	private static final Music MENU = new Music(Holder.direct(ModSounds.MUSIC_MENU), 20, 600, true);
	/** Vanilla gameplay pacing: the player's music-frequency option still caps the gap. */
	private static final Music GAME = Musics.createGameMusic(Holder.direct(ModSounds.MUSIC_GAME));
	/** Zero delay, like Musics.CREDITS: it starts the moment it is asked for and repeats. */
	private static final Music ENDING = new Music(Holder.direct(ModSounds.MUSIC_ENDING), 0, 0, true);

	private static boolean cut;
	private static boolean initialized;

	private MusicDirector() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(MusicDirector::tick);
	}

	/**
	 * The score this situation calls for.
	 *
	 * @return the music to play, or null when this situation is one the player should hear
	 *         unaccompanied - the vanilla music manager treats null as "keep quiet"
	 */
	public static Music situationalMusic(Minecraft client) {
		if (PursuitPresentationClient.silencesMusic()) return null;
		WorldInterfaceProtocol.Stage stage = encounterStage();
		if (stage != null) {
			switch (stage) {
				// The encounter scores itself. Anything laid over it is noise.
				case SUMMONING, PHASE_1, PHASE_2, PHASE_3, FAILURE_RESOLUTION -> {
					return null;
				}
				// The interface is down. This is what the ending track was authored for, and it
				// carries straight through the portal and into the poem below.
				case SUCCESS_RESOLUTION, PORTAL_OPEN -> {
					return ENDING;
				}
				default -> {
				}
			}
		}
		if (client.screen instanceof WinScreen) return ENDING;
		if (client.player == null || client.level == null) return MENU;
		// Any other boss bar that asked for its own music - the vanilla dragon, a wither - is still
		// a fight, and the same reasoning applies.
		if (client.gui.getBossOverlay().shouldPlayMusic()) return null;
		return GAME;
	}

	/**
	 * Scales the vanilla music fade target.
	 *
	 * <p>The music manager eases its category gain towards this value every tick and stops the track
	 * outright once the gain reaches zero, so returning zero here is a fade-out rather than a cut,
	 * and the ramp back up is the fade-in for whatever plays next.</p>
	 */
	public static float musicVolume(Minecraft client, float vanilla) {
		return situationalMusic(client) == null ? 0.0F : vanilla;
	}

	private static void tick(Minecraft client) {
		boolean wanted = PursuitPresentationClient.cutsMusic();
		// Edge-triggered: stopPlaying pushes the next-song delay out every call, so holding it down
		// for the length of a pursuit would keep pushing it for no reason.
		if (wanted && !cut) client.getMusicManager().stopPlaying();
		cut = wanted;
	}

	private static WorldInterfaceProtocol.Stage encounterStage() {
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		return encounter == null ? null : encounter.stage();
	}
}
