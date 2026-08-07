package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entrance's clock, and the one place the audio and the timeline are checked against each other.
 */
final class WorldInterfaceSummonTimelineTest {
	private static final Path GENERATOR = Path.of("tools/generate_world_interface_audio.py");
	private static final double TICKS_PER_SECOND = 20.0D;

	@Test
	void everyBeatIsStrictlyOrderedAndLandsInsideTheCeremony() {
		int[] beats = WorldInterfaceSummonTimeline.beats();
		for (int index = 1; index < beats.length; index++) {
			assertTrue(beats[index] > beats[index - 1],
					"beat " + index + " must come after the one before it");
		}
		assertEquals(0, beats[0], "the ceremony starts at zero");
		assertEquals(WorldInterfaceSummonTimeline.COMBAT, beats[beats.length - 1]);
		assertEquals(WorldInterfaceSummonTimeline.TOTAL_TICKS, WorldInterfaceSummonTimeline.COMBAT);
		// Thirteen seconds. The entrance it replaces was five, and silent.
		assertEquals(13.0D, WorldInterfaceSummonTimeline.TOTAL_TICKS / TICKS_PER_SECOND, 1.0E-9D);
		// The roar and the music handover are the same instant by construction: the score arrives
		// underneath the roar rather than after it.
		assertEquals(WorldInterfaceSummonTimeline.ROAR, WorldInterfaceSummonTimeline.MUSIC_HANDOVER);
		assertTrue(WorldInterfaceSummonTimeline.ROAR < WorldInterfaceSummonTimeline.COMBAT,
				"the roar must land before combat, not on it");
	}

	@Test
	void theAnchorChainFinishesBeforeTheGroundBreaks() {
		assertEquals(10, WorldInterfaceSummonTimeline.ANCHOR_CHAIN_COUNT);
		assertEquals(WorldInterfaceSummonTimeline.ANCHOR_CHAIN_START,
				WorldInterfaceSummonTimeline.anchorBeat(0));
		int previous = -1;
		for (int index = 0; index < WorldInterfaceSummonTimeline.ANCHOR_CHAIN_COUNT; index++) {
			int beat = WorldInterfaceSummonTimeline.anchorBeat(index);
			assertTrue(beat > previous, "anchor " + index + " must fire after the one before it");
			previous = beat;
		}
		// The chain has to be finished by the time the ground opens, or the two read as one event.
		assertTrue(WorldInterfaceSummonTimeline.anchorChainEnd()
						< WorldInterfaceSummonTimeline.GROUND_BREAK,
				"the anchor chain must complete before GROUND_BREAK");
	}

	/**
	 * The alignment the whole entrance is cut to.
	 *
	 * <p>The rise cue is 6.5 seconds long with a downbeat 5.5 seconds in, and the ceremony breaks
	 * the ground at tick 110 - which is 5.5 seconds. Those are two numbers in two languages in two
	 * files, and nothing but this test connects them. If either moves alone the entrance still runs,
	 * still sounds fine in isolation, and is simply no longer scored: the biggest hit in the cue
	 * lands on nothing in particular. That is precisely the kind of failure nobody files a bug for.
	 */
	@Test
	void theRiseCueDownbeatLandsExactlyOnTheGroundBreak() throws Exception {
		String generator = Files.readString(GENERATOR, StandardCharsets.UTF_8);
		double cueSeconds = constant(generator, "SUMMON_SECONDS");
		double downbeatSeconds = constant(generator, "SUMMON_DOWNBEAT_SECONDS");

		assertEquals(WorldInterfaceSummonTimeline.GROUND_BREAK / TICKS_PER_SECOND, downbeatSeconds,
				1.0E-9D, "the rise cue's downbeat must land on GROUND_BREAK");
		assertEquals(WorldInterfaceSummonTimeline.RISE_CUE_DOWNBEAT_TICKS,
				Math.round(downbeatSeconds * TICKS_PER_SECOND));
		assertEquals(WorldInterfaceSummonTimeline.RISE_CUE_TICKS,
				Math.round(cueSeconds * TICKS_PER_SECOND));
		// The downbeat has to be inside the cue, and the cue has to end before the roar - otherwise
		// its tail is still running when the next scheduled sound arrives.
		assertTrue(downbeatSeconds < cueSeconds);
		assertTrue(WorldInterfaceSummonTimeline.RISE_CUE_TICKS < WorldInterfaceSummonTimeline.ROAR,
				"the rise cue must finish before the roar rather than fighting it");
	}

	/**
	 * Every variant of the summon group has to be the same length.
	 *
	 * <p>Minecraft picks one at random, so the server cannot know which variant it started. If they
	 * differed in duration the downbeat would land somewhere different every time the entrance ran,
	 * and roughly two runs in three would be out of sync with no way to reproduce it.
	 */
	@Test
	void everySummonVariantSharesOneDurationSoTheBeatCannotMove() throws Exception {
		String generator = Files.readString(GENERATOR, StandardCharsets.UTF_8);
		assertTrue(generator.contains("\"summon\": SUMMON_SECONDS")
						|| generator.contains("\"summon\": (\"rise\"")
						&& generator.contains("GROUP_SECONDS")
						&& generator.contains("\"summon\": SUMMON_SECONDS"),
				"the summon group must take its length from the shared constant, not per variant");
		// The manifest reports what was actually rendered; all summon variants must agree.
		String manifest = Files.readString(Path.of("docs/art/world_interface/audio_manifest.json"),
				StandardCharsets.UTF_8);
		Matcher summon = Pattern.compile("\"summon\"\\s*:\\s*\\{.*?\"files\"\\s*:\\s*\\[(.*?)]",
				Pattern.DOTALL).matcher(manifest);
		assertTrue(summon.find(), "the manifest has no summon group; re-run the audio generator");
		Matcher seconds = Pattern.compile("\"seconds\"\\s*:\\s*([0-9.]+)").matcher(summon.group(1));
		Double first = null;
		int variants = 0;
		while (seconds.find()) {
			double value = Double.parseDouble(seconds.group(1));
			if (first == null) first = value;
			// Vorbis rounds the reported duration slightly; a tenth of a second is far tighter than
			// any drift that would actually move the beat.
			assertEquals(first, value, 0.1D, "summon variants must all be the same length");
			variants++;
		}
		assertTrue(variants >= 2, "expected several summon variants, found " + variants);
		assertEquals(WorldInterfaceSummonTimeline.RISE_CUE_TICKS,
				Math.round(first * TICKS_PER_SECOND), 1L);
	}

	@Test
	void descentProgressIsBoundedAndMonotonic() {
		assertEquals(0.0F, WorldInterfaceSummonTimeline.descentProgress(0L));
		assertEquals(1.0F, WorldInterfaceSummonTimeline.descentProgress(
				WorldInterfaceSummonTimeline.BODY_REVEAL));
		assertEquals(1.0F, WorldInterfaceSummonTimeline.descentProgress(
				WorldInterfaceSummonTimeline.COMBAT));
		float previous = -1.0F;
		for (long age = 0; age <= WorldInterfaceSummonTimeline.BODY_REVEAL; age += 5L) {
			float value = WorldInterfaceSummonTimeline.descentProgress(age);
			assertTrue(value >= previous, "descent must not go backwards at " + age);
			assertTrue(value >= 0.0F && value <= 1.0F);
			previous = value;
		}
	}

	@Test
	void musicIsSilentUntilTheHandover() {
		assertTrue(!WorldInterfaceSummonTimeline.musicAllowed(0L));
		assertTrue(!WorldInterfaceSummonTimeline.musicAllowed(
				WorldInterfaceSummonTimeline.MUSIC_HANDOVER - 1L));
		assertTrue(WorldInterfaceSummonTimeline.musicAllowed(
				WorldInterfaceSummonTimeline.MUSIC_HANDOVER));
	}

	private static double constant(String source, String name) {
		Matcher matcher = Pattern.compile("^" + name + "\\s*=\\s*([0-9.]+)", Pattern.MULTILINE)
				.matcher(source);
		assertTrue(matcher.find(), "the generator no longer declares " + name);
		return Double.parseDouble(matcher.group(1));
	}
}
