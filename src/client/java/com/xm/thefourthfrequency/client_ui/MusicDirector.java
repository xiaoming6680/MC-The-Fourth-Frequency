package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.mixin.MusicManagerGainAccessor;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Decides which authored score - if any - the current situation calls for.
 *
 * <p>The mod ships a playlist per context rather than one for the whole game: the menu, ordinary
 * play, the pursuit, the encounter - which is scored twice, because its third body is a different
 * fight - and one track for each of the two ways the story can end. Everything else the vanilla
 * music manager would have chosen is deliberately dropped.</p>
 *
 * <p>What is <em>not</em> scored matters as much as what is. Nothing plays across a world load: a
 * loading screen is the gap between two contexts rather than a context of its own, and letting the
 * menu playlist run underneath one means whichever track it picked is the one waiting on the other
 * side. Nothing plays over the aftermath of the encounter either - the two ending tracks are held
 * back until a player actually steps into the exit portal.</p>
 *
 * <p>Silence is expressed two ways. {@link #musicVolume} drives the vanilla fade, so music retreats
 * over a few seconds when a situation changes and swells back in when the next one starts - that is
 * the normal path, and it also gives every track a fade-in. The hard stop in {@link #tick} is for
 * the moments where a fade would be a loose end: the pursuit blackout, and the far side of a load.</p>
 *
 * <p>Two tracks replacing each other directly - the encounter taking over from ordinary play, and
 * the third body taking over from the first two - go through {@linkplain Handover a handover}
 * instead of either of those. See {@link #tickHandover}.</p>
 */
public final class MusicDirector {
	/** Menu pacing, and the vanilla "take over from whatever is playing" flag, matching Musics.MENU. */
	private static final Music MENU = new Music(Holder.direct(ModSounds.MUSIC_MENU), 20, 600, true);
	/**
	 * Four to six minutes between songs, against vanilla's ten to twenty.
	 *
	 * <p>Only the lower bound really decides the gap. The music manager re-rolls this range and takes
	 * the minimum on every single tick, including the thousands that pass while a song is playing, so
	 * the drawn value is pinned to the bottom of the range by the time it is used; the upper bound
	 * states the intent and little else.</p>
	 *
	 * <p>Vanilla's pacing was written for one playlist covering a whole game. Six tracks averaging
	 * just over two minutes, played on that schedule, leave the score audible less than a fifth of
	 * the time and take well over an hour to come round once - long enough that a player can finish a
	 * stretch of the main line without hearing half of them. At four minutes the score is present
	 * about a third of the time and the playlist turns over inside a normal sitting, which is as far
	 * as this can go before it starts working against the mod: silence is the default state here, and
	 * a track playing is what tells the player the moment is authored, therefore safe. The signal
	 * beds are what keep those gaps from reading as an empty channel.</p>
	 *
	 * <p>The player's music-frequency option still applies. "Frequent" caps the gap at 12000 ticks,
	 * which is above this and therefore changes nothing; "constant" is hard-coded to 100 ticks in the
	 * manager and overrides any pacing a track asks for.</p>
	 */
	private static final Music GAME = new Music(Holder.direct(ModSounds.MUSIC_GAME), 4_800, 7_200, false);
	private static final Music PURSUIT = immediate(ModSounds.MUSIC_PURSUIT);
	// The two encounter tracks are the only ones that ever replace a track that is still playing,
	// and they are handed over rather than cut - so they must NOT carry the vanilla replace flag.
	// It is evaluated inside the music manager's own tick, which runs before this class gets a look
	// at the frame, so leaving it set would cut the outgoing track before the handover ever started.
	private static final Music ENCOUNTER = handedOver(ModSounds.MUSIC_ENCOUNTER);
	private static final Music ENCOUNTER_FINAL = handedOver(ModSounds.MUSIC_ENCOUNTER_FINAL);
	private static final Music ENDING = immediate(ModSounds.MUSIC_ENDING);
	private static final Music ENDING_FAILURE = immediate(ModSounds.MUSIC_ENDING_FAILURE);

	/**
	 * Per-tick multiplier applied to the fade's gain on top of vanilla's own 0.97 while a handover
	 * is emptying the channel. Together they clear a full gain in about a second and a half - long
	 * enough to read as the previous track leaving, short enough that the morph it belongs to has
	 * not finished playing out by the time the next one arrives.
	 */
	private static final float STEEP_FADE_OUT = 0.80F;
	/**
	 * Extra vanilla-shaped fade-in steps to run per tick while a handover fills the channel back up.
	 * One step a tick is a ten-second swell, which is right for a track that drifts in over quiet
	 * play and far too slow for one that answers a phase change.
	 */
	private static final int HANDOVER_FADE_IN_STEPS = 7;
	/** The vanilla fade-in step, reproduced so the extra steps ramp on the same curve it does. */
	private static final float VANILLA_FADE_IN_MIN_STEP = 0.000_5F;
	private static final float VANILLA_FADE_IN_MAX_STEP = 0.005F;
	/** Ceiling on either half, so a handover can never strand itself if the gain stops moving. */
	private static final int HANDOVER_TIMEOUT_TICKS = 60;
	/**
	 * How long the menu theme gets to leave once the player commits to a world.
	 *
	 * <p>Half a second, and linear rather than eased, because this is the one fade that races
	 * something. Entering a world destroys the playing sound twice over - {@code updateLevelInEngines}
	 * stops every sound outright, and the Alpha pack swap reloads the sound engine - and neither is
	 * a fade this class can lengthen. Worse, the window before them is not a fixed length and is not
	 * even continuous: reading the level data blocks the main thread, so no ticks pass at all until
	 * the server is being waited on. A fade measured in seconds simply loses the race and gets cut
	 * off wherever it happened to be, which is exactly the abrupt stop it was added to avoid.</p>
	 *
	 * <p>Linear, because an eased fade spends most of its drop in the first few ticks and then
	 * crawls; over ten ticks that reads as a clip rather than a fade. A straight ramp over the same
	 * ten ticks is short but unmistakably a fade.</p>
	 */
	private static final int LOAD_FADE_TICKS = 10;

	private static boolean cut;
	private static boolean loadingScreen;
	private static boolean scored;
	private static boolean initialized;
	private static Handover handover = Handover.NONE;
	private static int handoverTicks;
	private static float fadeTarget = 1.0F;
	/** Negative while no world-entry fade is running; the ramp needs its own start point. */
	private static int loadFadeTicks = -1;
	private static float loadFadeFrom;

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
		// A handover empties the channel before it fills it again, and "nothing" is how that is
		// asked for: it drops the fade target to zero, which is what carries the outgoing track out.
		return handover == Handover.FADING_OUT ? null : wantedMusic(client);
	}

	private static Music wantedMusic(Minecraft client) {
		if (PursuitPresentationClient.scoresMusic()) return PURSUIT;
		if (PursuitPresentationClient.silencesMusic()) return null;
		// Answered ahead of the encounter stage on purpose. The poem is armed the instant a player
		// steps into the exit portal, but the stage the client still holds at that point is
		// PORTAL_OPEN, which is deliberately one of the silent ones.
		if (endingScored() || client.screen instanceof WinScreen) return endingMusic();
		WorldInterfaceProtocol.Stage stage = encounterStage();
		if (stage != null) {
			switch (stage) {
				// The fight is scored from the summon, and re-scored when the interface takes its
				// third body: that morph is the point the encounter stops being survivable and
				// starts being a race, and it should not be reached to the same music.
				case SUMMONING, PHASE_1, PHASE_2 -> {
					return ENCOUNTER;
				}
				case PHASE_3 -> {
					return ENCOUNTER_FINAL;
				}
				// Both resolutions and the open portal stay silent: the aftermath is the quietest
				// the End ever gets, and starting an ending track the instant the fight is decided
				// spends it on an empty arena. It is held back for the portal.
				case SUCCESS_RESOLUTION, FAILURE_RESOLUTION, PORTAL_OPEN -> {
					return null;
				}
				default -> {
				}
			}
		}
		if (loading(client)) return null;
		// The menu theme waits for the safety notice to be dismissed. Until then the title screen is
		// still carrying a page the player has to read, and scoring it would work against that.
		if (client.player == null || client.level == null) {
			return FirstRunNoticeController.released() ? MENU : null;
		}
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
		// Remembered so a handover knows where its fade-in has to stop. The manager asks for this
		// once at the top of every one of its own ticks, so the cached value is never stale.
		fadeTarget = vanilla;
		return situationalMusic(client) == null ? 0.0F : vanilla;
	}

	private static void tick(Minecraft client) {
		boolean wanted = PursuitPresentationClient.cutsMusic();
		// Edge-triggered: stopPlaying pushes the next-song delay out every call, so holding it down
		// for the length of a pursuit would keep pushing it for no reason.
		if (wanted && !cut) {
			client.getMusicManager().stopPlaying();
			// A cut leaves the gain wherever the fade had reached; zeroing it is what makes the
			// pursuit theme rise out of the blackout instead of arriving at whatever level the
			// overworld track happened to have left behind.
			silenceGain(client);
		}
		cut = wanted;
		// Backstop for a load short enough that even the half-second ramp below could not finish
		// inside it: whatever is left has to go, or the menu playlist swells back up in the world it
		// faded out for. Normally the ramp has already handed the track back before this fires.
		//
		// A pursuit is the one load this must keep its hands off: the blackout covers a dimension
		// transfer, and the pursuit theme is already fading in underneath it by the time the
		// loading screen goes away.
		boolean nowLoading = loading(client);
		if (!nowLoading && loadingScreen && !PursuitPresentationClient.scoresMusic()) {
			client.getMusicManager().stopPlaying();
			silenceGain(client);
		}
		loadingScreen = nowLoading;
		tickHandover(client);
		// The manager starts life at full gain and only ever eases it while a track is already
		// playing, so the first song of a session would otherwise begin at full volume. Zeroing on
		// the edge into "there is a score again" buys that first entrance a fade-in too. A handover
		// leans on the same edge: its fade-out reports "no score", so the tick that ends it is the
		// tick that drops the gain the incoming track then climbs out of.
		Music scoring = situationalMusic(client);
		if (scoring != null || !nowLoading) loadFadeTicks = -1;
		if (scoring != null) {
			if (!scored) {
				silenceGain(client);
				// The menu theme's own pacing leaves up to a second before a song starts. That gap
				// belongs between two of them; in front of the one that answers the acknowledgement
				// it is just the theme arriving later than the decision that asked for it.
				if (scoring == MENU) {
					((MusicManagerGainAccessor) client.getMusicManager())
							.thefourthfrequency$setNextSongDelay(0);
				}
			}
		} else if (nowLoading) {
			fadeOutForWorldEntry(client);
		}
		scored = scoring != null;
	}

	/**
	 * Walks the menu theme down a straight ramp once the player has committed to a world.
	 *
	 * <p>See {@link #LOAD_FADE_TICKS} for why this is its own fade rather than a steeper version of
	 * the vanilla one: it is racing two hard stops it cannot influence, across a window that is not
	 * a fixed length. Ten ticks is short enough to finish inside the shortest window that carries
	 * any ticks at all, and the ramp is recomputed from a remembered start rather than accumulated,
	 * so vanilla easing the same value in the same tick cannot bend it.</p>
	 */
	private static void fadeOutForWorldEntry(Minecraft client) {
		MusicManagerGainAccessor manager = (MusicManagerGainAccessor) client.getMusicManager();
		if (manager.thefourthfrequency$currentMusic() == null) {
			loadFadeTicks = -1;
			return;
		}
		if (loadFadeTicks < 0) {
			loadFadeTicks = 0;
			loadFadeFrom = manager.thefourthfrequency$currentGain();
		}
		float remaining = 1.0F - ++loadFadeTicks / (float) LOAD_FADE_TICKS;
		if (remaining > 0.0F) {
			manager.thefourthfrequency$setCurrentGain(loadFadeFrom * remaining);
			return;
		}
		// Inaudible by now, so this stop cannot be heard - but it does hand the track back. Left
		// alone, a track sitting at zero gain holds the channel until the world change tears it
		// down, and the manager would treat it as still playing in the meantime.
		client.getMusicManager().stopPlaying();
		silenceGain(client);
		loadFadeTicks = -1;
	}

	/**
	 * Carries one track out and the next one in when the two would otherwise cut.
	 *
	 * <p>Everywhere else the score changes, it changes through a stretch of deliberate silence, and
	 * vanilla's own curve covers that: the fade target drops to zero, the outgoing track eases out
	 * over about fifteen seconds, and whatever plays next climbs back over about ten. The encounter
	 * is the exception. Its two tracks replace each other directly - ordinary play gives way to the
	 * summon, and the first two bodies give way to the third - and at vanilla's pace the swap would
	 * either be a hard cut or a twenty-five second hole in the middle of a boss fight.</p>
	 *
	 * <p>So the handover runs the same two curves, steeply. It empties the channel by reporting no
	 * music at all, then multiplies the gain down on top of vanilla's own easing until the manager
	 * stops the track; then it clears the next-song delay so the incoming track starts on the spot,
	 * and runs the vanilla fade-in step several times a tick until the gain is back at target. The
	 * whole exchange is a little under three seconds - long enough to hear one track leave and the
	 * other arrive, short enough to fit inside the morph it belongs to.</p>
	 */
	private static void tickHandover(Minecraft client) {
		MusicManagerGainAccessor manager = (MusicManagerGainAccessor) client.getMusicManager();
		SoundInstance playing = manager.thefourthfrequency$currentMusic();
		switch (handover) {
			case NONE -> {
				Music wanted = wantedMusic(client);
				if (playing == null || wanted == null || !handsOver(wanted) || plays(playing, wanted)) {
					return;
				}
				handover = Handover.FADING_OUT;
				handoverTicks = 0;
			}
			case FADING_OUT -> {
				if (playing != null && ++handoverTicks < HANDOVER_TIMEOUT_TICKS) {
					deepenFadeOut(client);
					return;
				}
				// The manager stops a track of its own accord once the fade reaches zero, so an
				// empty channel is how the fade-out reports that it is finished.
				if (playing != null) client.getMusicManager().stopPlaying();
				handover = Handover.FADING_IN;
				handoverTicks = 0;
				// Every stop pushes the next-song delay out by five seconds, and the "constant"
				// music-frequency option floors it at five seconds regardless of what the incoming
				// track's own pacing asks for. Neither is a gap this handover wanted.
				manager.thefourthfrequency$setNextSongDelay(0);
			}
			case FADING_IN -> {
				// A situation that changed again mid-handover has its own answer, and the vanilla
				// fade is the right one for it. The tick ceiling is the backstop for a track that
				// never starts at all.
				if (wantedMusic(client) == null || ++handoverTicks >= HANDOVER_TIMEOUT_TICKS) {
					handover = Handover.NONE;
					return;
				}
				if (playing == null) return;
				float gain = manager.thefourthfrequency$currentGain();
				for (int step = 0; step < HANDOVER_FADE_IN_STEPS; step++) {
					gain += Math.clamp(gain, VANILLA_FADE_IN_MIN_STEP, VANILLA_FADE_IN_MAX_STEP);
				}
				if (gain >= fadeTarget) {
					gain = fadeTarget;
					handover = Handover.NONE;
				}
				manager.thefourthfrequency$setCurrentGain(gain);
			}
		}
	}

	/**
	 * Only the encounter's own tracks are handed over. Every other change of score already passes
	 * through a stretch of silence that belongs to the fiction, and shortening those would cost
	 * more than it bought.
	 */
	private static boolean handsOver(Music wanted) {
		return wanted == ENCOUNTER || wanted == ENCOUNTER_FINAL;
	}

	private static boolean plays(SoundInstance instance, Music music) {
		return music.sound().value().location().equals(instance.getIdentifier());
	}

	/**
	 * True while the client is between places rather than in one: chunk loading, a world handoff,
	 * a connection attempt, or the resource reload behind the startup overlay.
	 *
	 * <p>These screens are not a context that gets scored, they are the gap between two that do.
	 * Letting the menu playlist run underneath a progress bar also means whichever track it picked
	 * is the one waiting on the other side, which is the wrong track for wherever the player just
	 * went.</p>
	 */
	private static boolean loading(Minecraft client) {
		return client.getOverlay() != null
				|| client.screen instanceof LevelLoadingScreen
				|| client.screen instanceof ProgressScreen
				|| client.screen instanceof GenericMessageScreen
				|| client.screen instanceof ConnectScreen;
	}

	/**
	 * Arms the next track to fade in, by emptying the music channel while nothing is playing.
	 *
	 * <p>Guarded on there being no current track, because a situation that flickers - a boss bar
	 * that blinks out for a tick, a stage that lands a frame early - would otherwise reach this on
	 * an edge and cut a track that was only part-way through an honest fade.</p>
	 *
	 * <p>The engine's own category volume has to come down with the fade's gain, not just the gain.
	 * The manager pushes one into the other only from inside its fade, and the fade only runs once a
	 * track is already playing - so a track starting while the engine still carries the previous
	 * one's volume plays its whole first tick at that volume and is yanked down on the next. Fifty
	 * milliseconds at full level followed by near-silence is a click, arriving exactly where the
	 * fade-in was supposed to be. Zeroing both is what makes the entrance actually start from
	 * nothing.</p>
	 */
	/**
	 * Steepens the fade the manager is already running, so a track that has to be gone by a deadline
	 * gets there. A no-op when nothing is playing: the fade target is what asks for the fade, and
	 * this only decides how fast it is taken.
	 */
	private static void deepenFadeOut(Minecraft client) {
		MusicManagerGainAccessor manager = (MusicManagerGainAccessor) client.getMusicManager();
		if (manager.thefourthfrequency$currentMusic() == null) return;
		manager.thefourthfrequency$setCurrentGain(
				manager.thefourthfrequency$currentGain() * STEEP_FADE_OUT);
	}

	private static void silenceGain(Minecraft client) {
		MusicManagerGainAccessor manager = (MusicManagerGainAccessor) client.getMusicManager();
		if (manager.thefourthfrequency$currentMusic() != null) return;
		manager.thefourthfrequency$setCurrentGain(0.0F);
		client.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 0.0F);
	}

	private static WorldInterfaceProtocol.Stage encounterStage() {
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		return encounter == null ? null : encounter.stage();
	}

	private static boolean endingScored() {
		return WorldInterfaceVanillaPoemClient.scoredOutcome() != WorldInterfaceProtocol.Outcome.NONE;
	}

	/**
	 * The two endings are scored separately, and a vanilla dragon kill - which reaches the same
	 * screen without arming a poem - keeps the one this mod always played there.
	 */
	private static Music endingMusic() {
		return WorldInterfaceVanillaPoemClient.scoredOutcome() == WorldInterfaceProtocol.Outcome.FAILURE
				? ENDING_FAILURE : ENDING;
	}

	/**
	 * A track that starts the moment it is asked for and repeats, like Musics.CREDITS. Zero delays
	 * are also what lets one of these replace another on the spot: the manager derives the gap
	 * before the next song from the incoming track's own pacing.
	 */
	private static Music immediate(SoundEvent sound) {
		return new Music(Holder.direct(sound), 0, 0, true);
	}

	/**
	 * Same zero pacing, but without the vanilla replace flag: {@link #tickHandover} owns the moment
	 * one of these takes over, and the flag would let the manager cut the outgoing track first.
	 */
	private static Music handedOver(SoundEvent sound) {
		return new Music(Holder.direct(sound), 0, 0, false);
	}

	/** Which half of a two-track exchange the score is in, if it is in one at all. */
	private enum Handover {
		NONE,
		FADING_OUT,
		FADING_IN
	}
}
