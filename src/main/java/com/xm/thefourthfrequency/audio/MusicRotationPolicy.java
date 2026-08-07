package com.xm.thefourthfrequency.audio;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops a playlist from playing the same track twice in a row.
 *
 * <p>Minecraft does not have playlists. An event with several {@code sounds} entries is a weighted
 * random pool that is drawn from independently every time the event is played, so with seven game
 * tracks roughly one handover in seven put the track that had just finished straight back on. That
 * is the one repetition a listener always notices, because the two halves are adjacent: it does not
 * read as a shuffle landing awkwardly, it reads as the music having got stuck.
 *
 * <p>The rule here is exactly "not the one that just played", and deliberately no more. A full
 * shuffle bag - every track once before any repeats - is the other reasonable design, but it also
 * makes the order predictable towards the end of each pass, and the complaint this answers is about
 * adjacency rather than about distribution.
 *
 * <h2>Why only some events</h2>
 *
 * <p>Pools are the right behaviour nearly everywhere else. The encounter's eight attack cues each
 * hold three variants precisely so that the same attack does not sound identical twice, and there
 * forbidding a repeat would be forbidding the pool from doing its job. Only the background score is
 * listened to as a sequence of whole pieces, so only the background score rotates.
 *
 * <p>Single-track events are excluded for free: the pursuit, the two encounter phases and the two
 * endings each hold exactly one sound, and "do not repeat" is meaningless for a pool of one. They
 * are not listed below, and {@code MusicRotationPolicyTest} checks that the listing and the actual
 * pool sizes in {@code sounds.json} agree in both directions - a new multi-track score event that
 * forgets to rotate is a test failure rather than something noticed months later by ear.
 */
public final class MusicRotationPolicy {
	/**
	 * Score events whose pool holds more than one track.
	 *
	 * <p>Paths rather than {@code SoundEvent} constants: this is read from a mixin that runs on the
	 * sound thread during resource resolution, well before anything should be touching registries.
	 */
	private static final Set<String> ROTATING = Set.of("music_game", "music_menu");

	/**
	 * Ceiling on re-draws before the pick is accepted as-is.
	 *
	 * <p>A pool of one, or a pool whose weights make one entry overwhelmingly likely, must not be
	 * able to spin here. Eight draws from a pool of four leaves under a tenth of a percent of runs
	 * ending on a repeat, and a repeat is a blemish rather than a fault - a hang would be the fault.
	 */
	public static final int MAX_REROLLS = 8;

	private static final Map<String, String> LAST_PLAYED = new ConcurrentHashMap<>();

	private MusicRotationPolicy() {
	}

	/** The score events this policy governs. */
	public static Set<String> rotatingEvents() {
		return ROTATING;
	}

	/** Whether {@code eventPath} is a score event that must not repeat back to back. */
	public static boolean rotates(String eventPath) {
		return eventPath != null && ROTATING.contains(eventPath);
	}

	/** Whether this draw would put the track that just played straight back on. */
	public static boolean repeats(String eventPath, String track) {
		return track != null && track.equals(LAST_PLAYED.get(eventPath));
	}

	/** Records what actually started, so the next draw for this event can avoid it. */
	public static void remember(String eventPath, String track) {
		if (eventPath == null || track == null) return;
		LAST_PLAYED.put(eventPath, track);
	}

	/** Forgets every event's history. Test seam; nothing in production needs to reset this. */
	public static void forgetAll() {
		LAST_PLAYED.clear();
	}
}
