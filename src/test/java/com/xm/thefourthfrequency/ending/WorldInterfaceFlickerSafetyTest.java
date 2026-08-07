package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No large-area effect in the encounter may strobe.
 *
 * <p>The boss fight draws several things that cover a lot of screen: a laser whose muzzle is metres
 * across, a detonation bloom, and two full-screen impact flashes. Modulating any of those on a fast
 * sine turns them into a strobe, and the frequency band that causes seizures in photosensitive
 * people - roughly 3 to 30 Hz, worst around 15 to 25 - is exactly the range a plausible-looking
 * per-tick coefficient lands in. {@code sin(age * 1.9)} reads as a small number in source and is
 * six hertz on screen.
 *
 * <p>This bounds the pulse rate of anything driven off the tick counter. It is a blunt check and it
 * is meant to be: the cost of a false positive is rewording one line, and the cost of a false
 * negative is hurting someone.
 */
final class WorldInterfaceFlickerSafetyTest {
	private static final Path CLIENT = Path.of("src/client/java/com/xm/thefourthfrequency");
	private static final double TICKS_PER_SECOND = 20.0D;
	/**
	 * Ceiling on radians per tick for a large-area pulse.
	 *
	 * <p>1.0 rad/tick is about 3.2 Hz, which is at the bottom edge of the risky band and reads as a
	 * throb rather than a flash. Anything faster than this on something the size of the laser needs
	 * a deliberate argument, not a default.
	 */
	private static final double MAX_RADIANS_PER_TICK = 1.0D;

	/** Files that draw something large enough for a fast pulse to matter. */
	private static final String[] LARGE_AREA_SOURCES = {
			"client_render/WorldInterfaceBeamBatchRenderer.java",
			"client_ui/WorldInterfacePresentationController.java",
			"client_ui/WorldInterfaceAtmosphereController.java",
	};

	@Test
	void noLargeAreaEffectPulsesFastEnoughToStrobe() throws Exception {
		Pattern pulse = Pattern.compile("Mth\\.(?:sin|cos)\\(\\s*age\\s*\\*\\s*([0-9.]+)F");
		List<String> offenders = new ArrayList<>();
		for (String relative : LARGE_AREA_SOURCES) {
			Path path = CLIENT.resolve(relative);
			if (!Files.isRegularFile(path)) continue;
			String source = Files.readString(path, StandardCharsets.UTF_8);
			Matcher matcher = pulse.matcher(source);
			while (matcher.find()) {
				double radiansPerTick = Double.parseDouble(matcher.group(1));
				if (radiansPerTick > MAX_RADIANS_PER_TICK) {
					double hertz = radiansPerTick * TICKS_PER_SECOND / (2.0D * Math.PI);
					offenders.add(String.format("%s: %.2f rad/tick = %.1f Hz",
							relative, radiansPerTick, hertz));
				}
			}
		}
		assertTrue(offenders.isEmpty(),
				"large-area effects must not strobe; these are inside the photosensitive band: "
						+ offenders);
	}

	/**
	 * The two effects that actually caused this: the laser's charge tell and the detonation bloom.
	 * Pinned by value so a revert is a test failure rather than a regression nobody notices until
	 * somebody is looking at it.
	 */
	@Test
	void theLaserAndDetonationKeepTheirSlowedPulses() throws Exception {
		String beams = Files.readString(
				CLIENT.resolve("client_render/WorldInterfaceBeamBatchRenderer.java"),
				StandardCharsets.UTF_8);
		assertFalse(beams.contains("0.72F + 0.28F * Mth.sin(age * 1.9F)"),
				"the laser charge flicker was a six-hertz strobe across the whole beam");
		assertFalse(beams.contains("0.72F + 0.28F * Mth.sin(age * 1.6F)"),
				"the detonation bloom was a five-hertz strobe");
		assertTrue(beams.contains("0.86F + 0.14F * Mth.sin(age * 0.8F)"));
		assertTrue(beams.contains("0.82F + 0.18F * Mth.sin(age * 0.9F)"));
	}

	/**
	 * A sawtooth may drive brightness on a small part, never position or a full-screen parameter.
	 *
	 * <p>This exact mistake was made twice in one pass, in two unrelated files, and produced the
	 * same symptom both times. A value that wraps from 1 back to 0 between two consecutive ticks is
	 * fine on something small and self-contained - the kernel lattice reads it as "charged, then
	 * discharged" - but the wrap is a discontinuity, and anything the eye can follow across that
	 * discontinuity jumps.
	 *
	 * <ul>
	 *   <li>The atmosphere fed {@code volleyRamp} into fog distance and sky tint. Both cover the
	 *       whole view, so every two seconds a large part of the frame changed at once.
	 *   <li>The debris storm used {@code fall}, a sawtooth, for each mote's position, and then
	 *       added a floor of 38 to the alpha that was supposed to hide the wrap. The motes were
	 *       therefore visible while they teleported - 120 of them, at 120 different phases.
	 * </ul>
	 */
	@Test
	void sawtoothsNeverDrivePositionOrFullScreenParameters() throws Exception {
		String atmosphere = Files.readString(
				CLIENT.resolve("client_ui/WorldInterfaceAtmosphereController.java"),
				StandardCharsets.UTF_8);
		assertTrue(atmosphere.contains("volleyBreath("),
				"fog and sky must ride the continuous curve, not the sawtooth");
		assertFalse(atmosphere.contains("volleyRamp("),
				"volleyRamp is a sawtooth and this drives full-screen fog and tint");

		String beams = Files.readString(
				CLIENT.resolve("client_render/WorldInterfaceBeamBatchRenderer.java"),
				StandardCharsets.UTF_8);
		// The storm's fade has to actually reach zero: its position wraps, and the fade is the only
		// thing hiding the wrap.
		assertFalse(beams.contains("Math.round(38.0F + alpha * 120.0F)"),
				"a fade with a floor cannot hide the position wrap it exists to hide");
		assertTrue(beams.contains("Math.round(alpha * 150.0F)"),
				"the debris fade must reach zero at both ends of a mote's fall");

		// And the continuous curve must genuinely be continuous at the wrap.
		String palette = Files.readString(
				CLIENT.resolve("client_render/WorldInterfacePalette.java"), StandardCharsets.UTF_8);
		assertTrue(palette.contains("0.5F - 0.5F * Mth.cos("),
				"volleyBreath must be a raised cosine, which is continuous where the ramp is not");
	}

	/**
	 * The anchor tethers stay wired to the rules that stop them blinking and crawling.
	 *
	 * <p>{@code WorldInterfaceBeamPolicyTest} owns whether those rules are right; this owns whether
	 * the renderer still asks. Both failures are invisible in a screenshot and only show up in
	 * motion, so a revert of either call site would otherwise reach a player before it reached a
	 * test.
	 *
	 * <ul>
	 *   <li>Tether brightness must come from the authoritative anchor mask. Deriving it from the
	 *       client's own entity sweep stepped all ten tethers together whenever one crystal fell
	 *       outside entity tracking.
	 *   <li>Tether endpoints must come from the ten indexed positions in the same authoritative
	 *       snapshot, so client entity tracking can no longer delete or reorder them.
	 *   <li>Beam width must be floored and the widening paid for in alpha, or a tether across the
	 *       arena is under two pixels wide and crawls.
	 * </ul>
	 */
	@Test
	void theAnchorTethersKeepTheirStabilityRules() throws Exception {
		String beams = Files.readString(
				CLIENT.resolve("client_render/WorldInterfaceBeamBatchRenderer.java"),
				StandardCharsets.UTF_8);
		assertFalse(beams.contains("1.0F - anchors.size() / (float) MAX_ANCHOR_TETHERS"),
				"tether pressure read the client's own sweep instead of the authoritative mask");
		assertTrue(beams.contains("standingAnchors(encounter)"),
				"tether pressure must come from the anchor mask the server sends");
		assertTrue(beams.contains("encounter.anchorPositions()")
				&& beams.contains("encounter.anchorAliveMask()"),
				"tether endpoints and liveness must come from the same authoritative snapshot");
		assertFalse(beams.contains("EndCrystal.class"),
				"client entity tracking must not be allowed to delete or reorder anchor tethers");
		assertTrue(beams.contains("WorldInterfaceBeamPolicy.stableAlpha("),
				"a beam held to a minimum width has to give the brightness back");
		assertFalse(beams.contains("double scale = beam.halfWidth() / rightLength;"),
				"the unfloored single-width beam quad is what crawled at range");
	}

	/**
	 * The full-screen impact flashes are single decaying pulses, not oscillators.
	 *
	 * <p>They cover the entire screen, so a repeating waveform there would be the worst offender of
	 * all. They must decay monotonically to nothing and stop.
	 */
	@Test
	void fullScreenFlashesDecayRatherThanOscillate() throws Exception {
		String presentation = Files.readString(
				CLIENT.resolve("client_ui/WorldInterfacePresentationController.java"),
				StandardCharsets.UTF_8);
		int start = presentation.indexOf("private static float decay(");
		assertTrue(start > 0, "the flash decay helper is missing");
		String decay = presentation.substring(start, presentation.indexOf('}', start));
		assertFalse(decay.contains("sin") || decay.contains("cos"),
				"an impact flash must fade out, never oscillate");
		assertTrue(decay.contains("Math.exp("), "the fade should be exponential");
		// And the peak alphas stay low enough that a flash is a hint, not a whiteout.
		Matcher peaks = Pattern.compile("(?:HIT|HURT)_FLASH_PEAK_ALPHA = 0x([0-9A-Fa-f]+)")
				.matcher(presentation);
		int found = 0;
		while (peaks.find()) {
			found++;
			int alpha = Integer.parseInt(peaks.group(1), 16);
			assertTrue(alpha <= 0x50,
					"a full-screen flash peaking at 0x" + Integer.toHexString(alpha)
							+ " is a whiteout, not feedback");
		}
		assertTrue(found >= 2, "both flash peaks must be declared as constants");
	}
}
