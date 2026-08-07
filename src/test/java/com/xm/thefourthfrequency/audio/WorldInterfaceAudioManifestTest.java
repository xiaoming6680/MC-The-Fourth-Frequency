package com.xm.thefourthfrequency.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ties the four places that have to agree about the world-interface sound library together.
 *
 * <p>They previously did not. {@code sounds/world_interface/} held 27 directories while the
 * generator's {@code GROUPS} table listed 22, so twelve files - {@code hurt}, {@code death},
 * {@code laser_fire}, {@code impact} and {@code form_shift}, which between them carry most of the
 * force in the fight - were orphan assets that no tool could reproduce. Nothing caught it, because
 * the generator asserted a hand-written total ({@code generated != 58}) rather than deriving one.
 *
 * <p>So no count is written down here either. The generator emits a manifest as it runs; this
 * asserts the manifest, {@code sounds.json}, the files on disk and the two lang files are the same
 * library seen four ways. A group added to only one of them fails rather than silently drifting.
 */
final class WorldInterfaceAudioManifestTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/thefourthfrequency");
	private static final Path SOUND_ROOT = ASSETS.resolve("sounds/world_interface");
	private static final Path MANIFEST = Path.of("docs/art/world_interface/audio_manifest.json");
	private static final Path GENERATOR = Path.of("tools/generate_world_interface_audio.py");
	private static final String EVENT_PREFIX = "world_interface_";
	private static final String SUBTITLE_PREFIX = "subtitles.thefourthfrequency.world_interface.";

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	/** Group name to variant count, parsed straight out of the generator's own GROUPS table. */
	private static Map<String, Integer> generatorGroups() throws Exception {
		String source = Files.readString(GENERATOR, StandardCharsets.UTF_8);
		int start = source.indexOf("GROUPS: dict[str, tuple[str, int]] = {");
		assertTrue(start > 0, "generator no longer declares a GROUPS table");
		int end = source.indexOf("\n}", start);
		assertTrue(end > start, "GROUPS table is unterminated");
		Matcher matcher = Pattern.compile("\"([a-z0-9_]+)\":\\s*\\(\"([a-z_]+)\",\\s*(\\d+)\\)")
				.matcher(source.substring(start, end));
		Map<String, Integer> groups = new LinkedHashMap<>();
		while (matcher.find()) {
			groups.put(matcher.group(1), Integer.parseInt(matcher.group(3)));
		}
		assertFalse(groups.isEmpty(), "parsed no groups out of the generator");
		return groups;
	}

	private static Map<String, Integer> manifestGroups() throws Exception {
		JsonObject groups = json(MANIFEST).getAsJsonObject("groups");
		Map<String, Integer> parsed = new LinkedHashMap<>();
		for (String name : groups.keySet()) {
			parsed.put(name, groups.getAsJsonObject(name).get("variants").getAsInt());
		}
		return parsed;
	}

	@Test
	void manifestIsGeneratedAndCountsAreDerivedRatherThanStated() throws Exception {
		JsonObject manifest = json(MANIFEST);
		assertTrue(manifest.get("note").getAsString().contains("GENERATED"),
				"the manifest must announce that it is generated, or someone will edit it");
		Map<String, Integer> groups = manifestGroups();
		assertEquals(groups.size(), manifest.get("groupCount").getAsInt());
		assertEquals(groups.values().stream().mapToInt(Integer::intValue).sum(),
				manifest.get("fileCount").getAsInt(),
				"fileCount must be the sum of the variant counts, never a written-down total");

		String generator = Files.readString(GENERATOR, StandardCharsets.UTF_8);
		assertFalse(generator.contains("generated != 58"),
				"the generator must not assert a hand-written file total again");
	}

	@Test
	void generatorCoversEveryGroupOnDiskWithMatchingVariantCounts() throws Exception {
		Map<String, Integer> generator = generatorGroups();
		Map<String, Integer> manifest = manifestGroups();
		assertEquals(generator, manifest,
				"the manifest is stale: re-run tools/generate_world_interface_audio.py");

		Map<String, Integer> disk = new LinkedHashMap<>();
		try (var directories = Files.list(SOUND_ROOT)) {
			for (Path directory : directories.filter(Files::isDirectory).toList()) {
				try (var files = Files.list(directory)) {
					disk.put(directory.getFileName().toString(),
							(int) files.filter(file -> file.toString().endsWith(".ogg")).count());
				}
			}
		}
		// Both directions. A directory with no generator entry is an unreproducible orphan; a
		// generator entry with no directory means the library was never regenerated.
		assertEquals(new LinkedHashSet<>(generator.keySet()), new LinkedHashSet<>(disk.keySet()),
				"every sound directory must be reproducible from the generator, and vice versa");
		for (Map.Entry<String, Integer> entry : generator.entrySet()) {
			assertEquals(entry.getValue(), disk.get(entry.getKey()),
					"variant count drifted for group " + entry.getKey());
		}
	}

	@Test
	void soundsJsonReferencesExactlyTheGeneratedFiles() throws Exception {
		JsonObject sounds = json(ASSETS.resolve("sounds.json"));
		Set<String> referenced = new LinkedHashSet<>();
		List<String> missing = new ArrayList<>();
		for (String event : sounds.keySet()) {
			if (!event.startsWith(EVENT_PREFIX)) continue;
			for (var element : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
				String name = element.isJsonObject()
						? element.getAsJsonObject().get("name").getAsString() : element.getAsString();
				if (!name.contains("world_interface/")) continue;
				String local = name.substring(name.indexOf(':') + 1);
				referenced.add(local);
				if (!Files.isRegularFile(ASSETS.resolve("sounds/" + local + ".ogg"))) missing.add(name);
			}
		}
		assertTrue(missing.isEmpty(), "sounds.json points at files that do not exist: " + missing);

		List<String> unreferenced = new ArrayList<>();
		try (var directories = Files.list(SOUND_ROOT)) {
			for (Path directory : directories.filter(Files::isDirectory).toList()) {
				try (var files = Files.list(directory)) {
					for (Path file : files.filter(path -> path.toString().endsWith(".ogg")).toList()) {
						String local = "world_interface/" + directory.getFileName() + "/"
								+ file.getFileName().toString().replace(".ogg", "");
						if (!referenced.contains(local)) unreferenced.add(local);
					}
				}
			}
		}
		// A generated file nothing plays is dead weight in the jar and, more to the point, means
		// a group was added to the generator and never wired up to an event.
		assertTrue(unreferenced.isEmpty(),
				"generated but never played: " + unreferenced);
	}

	@Test
	void everyWorldInterfaceEventIsSubtitledInBothLanguages() throws Exception {
		JsonObject sounds = json(ASSETS.resolve("sounds.json"));
		JsonObject english = json(ASSETS.resolve("lang/en_us.json"));
		JsonObject chinese = json(ASSETS.resolve("lang/zh_cn.json"));
		int checked = 0;
		for (String event : sounds.keySet()) {
			if (!event.startsWith(EVENT_PREFIX)) continue;
			JsonObject definition = sounds.getAsJsonObject(event);
			assertTrue(definition.has("subtitle"), event + " has no subtitle");
			String key = definition.get("subtitle").getAsString();
			assertTrue(key.startsWith(SUBTITLE_PREFIX), key);
			assertTrue(english.has(key) && !english.get(key).getAsString().isBlank(),
					"missing English subtitle " + key);
			assertTrue(chinese.has(key) && !chinese.get(key).getAsString().isBlank(),
					"missing Chinese subtitle " + key);
			checked++;
		}
		assertEquals(sounds.keySet().stream().filter(name -> name.startsWith(EVENT_PREFIX)).count(),
				checked);
	}

	/**
	 * The sky lance had been sharing the mental-interference sample. One attack drops a column of
	 * force on the player's head and the other only distorts what they see; announcing both with the
	 * same sound is most of why the telegraphs could not be told apart.
	 */
	@Test
	void distinctAttacksDoNotShareACue() throws Exception {
		String attacks = Files.readString(
				Path.of("src/main/java/com/xm/thefourthfrequency/ending/WorldInterfaceAttackService.java"),
				StandardCharsets.UTF_8);
		assertTrue(attacks.contains("case SKY_LANCE -> ModSounds.WORLD_INTERFACE_LANCE;"),
				"the lance must keep its own cue");
		assertFalse(attacks.contains("case SKY_LANCE -> ModSounds.WORLD_INTERFACE_MENTAL;"));
	}

	/**
	 * Six encounter cues were registered through the single-argument overload, which resolves to a
	 * flat 16 blocks whatever volume they are played at - far inside the radius this fight is
	 * actually fought from. The three phase beds legitimately stay on that overload: they are played
	 * non-positionally with attenuation disabled, so a radius would state a distance nothing reads.
	 */
	@Test
	void everyPositionalEncounterCueStatesItsRadius() throws Exception {
		String source = Files.readString(
				Path.of("src/main/java/com/xm/thefourthfrequency/audio/ModSounds.java"),
				StandardCharsets.UTF_8);
		Matcher matcher = Pattern.compile(
				"WORLD_INTERFACE_([A-Z0-9_]+)\\s*=\\s*\\n?\\s*register\\(\"([a-z0-9_]+)\"([^)]*)\\)")
				.matcher(source);
		List<String> unbounded = new ArrayList<>();
		int seen = 0;
		while (matcher.find()) {
			seen++;
			String constant = matcher.group(1);
			boolean hasRange = matcher.group(3).contains(",");
			boolean isBed = constant.startsWith("AMBIENT_");
			if (!hasRange && !isBed) unbounded.add(constant);
			if (hasRange && isBed) unbounded.add(constant + " (bed must not state a radius)");
		}
		assertTrue(seen >= 30, "parsed too few world-interface events: " + seen);
		assertTrue(unbounded.isEmpty(),
				"these cues are capped at 16 blocks and will be inaudible from the arena edge: "
						+ unbounded);
	}

	/**
	 * The radius a cue is registered with and the distance it actually fades over must be the same
	 * number, and both have to be stated.
	 *
	 * <p>They are two different mechanisms and only one of them was set. {@code SoundEvent}'s fixed
	 * range decides how far the <em>server</em> broadcasts the packet; the client's falloff comes
	 * from {@code attenuation_distance} in {@code sounds.json} and defaults to 16 blocks. So the
	 * whole encounter library was being delivered to the arena edge and then attenuated to nothing
	 * about a sixth of the way there.
	 *
	 * <p>It went unnoticed because it does not fail evenly. Cues are emitted at
	 * {@code WorldInterfaceAnatomy.coreOrigin}, which climbs with the form - roughly 9, 16 and 19
	 * blocks over the entity - so a telegraph that was audible in the first phase crossed the
	 * 16-block falloff at the second and sat outside it for the whole third. The attacks got louder
	 * in the fiction and silent in fact exactly as they started mattering.
	 */
	@Test
	void everyFixedRangeCueFadesOverTheRadiusItWasRegisteredWith() throws Exception {
		String source = Files.readString(
				Path.of("src/main/java/com/xm/thefourthfrequency/audio/ModSounds.java"),
				StandardCharsets.UTF_8);
		Map<String, Integer> named = new LinkedHashMap<>();
		Matcher constants = Pattern.compile(
				"private static final float (\\w+_RANGE) = ([0-9.]+)F;").matcher(source);
		while (constants.find()) {
			named.put(constants.group(1), (int) Float.parseFloat(constants.group(2)));
		}
		Matcher registered = Pattern.compile(
				"register\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*(\\w+_RANGE)\\s*\\)").matcher(source);
		Map<String, Integer> expected = new LinkedHashMap<>();
		while (registered.find()) {
			Integer range = named.get(registered.group(2));
			assertTrue(range != null, "unknown range constant " + registered.group(2));
			expected.put(registered.group(1), range);
		}
		assertTrue(expected.size() >= 30, "parsed too few fixed-range registrations: " + expected.size());

		JsonObject sounds = json(ASSETS.resolve("sounds.json"));
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : expected.entrySet()) {
			String event = entry.getKey();
			assertTrue(sounds.has(event), "registered but absent from sounds.json: " + event);
			for (var element : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
				if (!element.isJsonObject()) {
					wrong.add(event + " (bare string cannot carry attenuation_distance)");
					continue;
				}
				JsonObject sound = element.getAsJsonObject();
				if (!sound.has("attenuation_distance")) {
					wrong.add(event + " -> " + sound.get("name").getAsString() + " (missing)");
				} else if (sound.get("attenuation_distance").getAsInt() != entry.getValue()) {
					wrong.add(event + " -> " + sound.get("attenuation_distance").getAsInt()
							+ " but registered at " + entry.getValue());
				}
			}
		}
		assertTrue(wrong.isEmpty(),
				"these cues fade out long before the radius they are broadcast over: " + wrong);
	}

	/**
	 * The converse: a cue with no stated radius must not state a falloff either.
	 *
	 * <p>The phase beds are played non-positionally with attenuation disabled. Giving one an
	 * {@code attenuation_distance} would be a distance nothing reads, which is how the pair above
	 * drifted apart in the first place.
	 */
	@Test
	void variableRangeCuesDoNotStateAFalloff() throws Exception {
		String source = Files.readString(
				Path.of("src/main/java/com/xm/thefourthfrequency/audio/ModSounds.java"),
				StandardCharsets.UTF_8);
		Set<String> variable = new LinkedHashSet<>();
		Matcher matcher = Pattern.compile("register\\(\\s*\"([a-z0-9_]+)\"\\s*\\)").matcher(source);
		while (matcher.find()) variable.add(matcher.group(1));

		JsonObject sounds = json(ASSETS.resolve("sounds.json"));
		List<String> stated = new ArrayList<>();
		for (String event : variable) {
			if (!sounds.has(event)) continue;
			for (var element : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("attenuation_distance")) {
					stated.add(event);
				}
			}
		}
		assertTrue(stated.isEmpty(),
				"these are variable-range events and must not declare a falloff: " + stated);
	}
}
