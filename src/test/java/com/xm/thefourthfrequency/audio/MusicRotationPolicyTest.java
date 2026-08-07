package com.xm.thefourthfrequency.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The background score may not play the same track twice in a row. */
final class MusicRotationPolicyTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/thefourthfrequency");

	@BeforeEach
	void forget() {
		MusicRotationPolicy.forgetAll();
	}

	@Test
	void onlyTheMultiTrackScoreEventsRotate() {
		assertTrue(MusicRotationPolicy.rotates("music_game"));
		assertTrue(MusicRotationPolicy.rotates("music_menu"));
		// One-track events: "not the last one" cannot be honoured and must not be attempted.
		assertFalse(MusicRotationPolicy.rotates("music_pursuit"));
		assertFalse(MusicRotationPolicy.rotates("music_encounter"));
		assertFalse(MusicRotationPolicy.rotates("music_encounter_final"));
		// The attack cues hold three variants precisely so they can repeat freely.
		assertFalse(MusicRotationPolicy.rotates("world_interface_laser"));
		assertFalse(MusicRotationPolicy.rotates(null));
	}

	@Test
	void aTrackRepeatsOnlyItselfAndOnlyForItsOwnEvent() {
		MusicRotationPolicy.remember("music_game", "thefourthfrequency:music/game/hi");
		assertTrue(MusicRotationPolicy.repeats("music_game", "thefourthfrequency:music/game/hi"));
		assertFalse(MusicRotationPolicy.repeats("music_game", "thefourthfrequency:music/game/tenshi"));
		// The two playlists keep separate histories, or the menu theme would constrain the game one.
		assertFalse(MusicRotationPolicy.repeats("music_menu", "thefourthfrequency:music/game/hi"));
		assertFalse(MusicRotationPolicy.repeats("music_game", null));
	}

	/**
	 * The re-draw loop the mixin runs, against a source that would otherwise stutter badly.
	 *
	 * <p>The draw is deliberately hostile: it returns each track twice before moving on, which is
	 * the pattern that produces an audible back-to-back repeat every single time. What is asserted
	 * is the property the player hears - no two consecutive tracks are the same - rather than any
	 * particular ordering, because the real draw is vanilla's weighted pick and the ordering is its
	 * business.
	 */
	@Test
	void reDrawingRemovesEveryBackToBackRepeat() {
		List<String> pool = List.of("a", "b", "c", "d", "e", "f", "g");
		int[] cursor = {0};
		List<String> played = new ArrayList<>();
		for (int round = 0; round < 200; round++) {
			String pick = pool.get(cursor[0]++ / 2 % pool.size());
			for (int attempt = 0; attempt < MusicRotationPolicy.MAX_REROLLS
					&& MusicRotationPolicy.repeats("music_game", pick); attempt++) {
				pick = pool.get(cursor[0]++ / 2 % pool.size());
			}
			MusicRotationPolicy.remember("music_game", pick);
			played.add(pick);
		}
		for (int index = 1; index < played.size(); index++) {
			assertDiffersFromPredecessor(played.get(index - 1), played.get(index), index, played);
		}
		// And it must still be drawing from the whole pool rather than settling into a pair.
		assertEquals(new LinkedHashSet<>(pool), new LinkedHashSet<>(played),
				"re-drawing must not narrow the playlist");
	}

	private static void assertDiffersFromPredecessor(String previous, String current, int index,
			List<String> played) {
		assertFalse(previous.equals(current),
				"track " + index + " repeats the one before it in " + played);
	}

	/**
	 * The declared list and the actual pools have to agree, in both directions.
	 *
	 * <p>This is the half that will still be true in a year. Adding an eighth game track is a one
	 * line change in {@code sounds.json} and nothing about it suggests that a policy elsewhere needs
	 * looking at; adding a whole new score event even less so. Deriving the expectation from the
	 * resource means the omission fails here instead of being noticed by ear.
	 */
	@Test
	void everyMultiTrackScoreEventIsDeclaredAndEveryDeclaredOneExists() throws Exception {
		JsonObject sounds = JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		Set<String> multiTrack = new LinkedHashSet<>();
		Set<String> singleTrack = new LinkedHashSet<>();
		for (String event : sounds.keySet()) {
			if (!event.startsWith("music_")) continue;
			JsonArray pool = sounds.getAsJsonObject(event).getAsJsonArray("sounds");
			(pool.size() > 1 ? multiTrack : singleTrack).add(event);
		}
		assertFalse(multiTrack.isEmpty(), "parsed no score events out of sounds.json");
		assertEquals(multiTrack, new LinkedHashSet<>(MusicRotationPolicy.rotatingEvents()),
				"the rotating list and the multi-track score events in sounds.json disagree");
		for (String event : singleTrack) {
			assertFalse(MusicRotationPolicy.rotates(event),
					event + " holds one track; rotating it would only cost re-draws");
		}
	}
}
