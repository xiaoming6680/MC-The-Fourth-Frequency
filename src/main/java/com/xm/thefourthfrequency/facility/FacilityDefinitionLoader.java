package com.xm.thefourthfrequency.facility;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FacilityDefinitionLoader {
	private static final String RESOURCE = "/data/thefourthfrequency/facilities/facilities.json";
	private static final Gson GSON = new Gson();

	private FacilityDefinitionLoader() {
	}

	public static List<FacilityDefinition> loadBuiltIn() {
		try (InputStream stream = FacilityDefinitionLoader.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("Missing built-in facility data " + RESOURCE);
			}
			FacilityDefinition[] decoded = GSON.fromJson(
					new InputStreamReader(stream, StandardCharsets.UTF_8), FacilityDefinition[].class);
			if (decoded == null || decoded.length != 5) {
				throw new IllegalStateException("Expected exactly five M4 facility definitions");
			}
			List<FacilityDefinition> definitions = List.of(decoded);
			validate(definitions);
			return definitions;
		} catch (IOException | JsonParseException exception) {
			throw new IllegalStateException("Unable to load built-in facility data", exception);
		}
	}

	private static void validate(List<FacilityDefinition> definitions) {
		Set<String> ids = new HashSet<>();
		Set<Integer> clueIndices = new HashSet<>();
		for (FacilityDefinition definition : definitions) {
			if (!ids.add(definition.id())) {
				throw new IllegalStateException("Duplicate facility id " + definition.id());
			}
			if (definition.clueIndex() >= 0 && !clueIndices.add(definition.clueIndex())) {
				throw new IllegalStateException("Duplicate environmental clue index " + definition.clueIndex());
			}
			validateBlock(definition.floorBlock(), definition.id());
			validateBlock(definition.wallBlock(), definition.id());
			validateBlock(definition.roofBlock(), definition.id());
			validateBlock(definition.accentBlock(), definition.id());
			validateBlock(definition.markerBlock(), definition.id());
			for (String template : definition.templates()) {
				String resource = "/data/thefourthfrequency/structure/" + template + ".nbt";
				if (FacilityDefinitionLoader.class.getResource(resource) == null) {
					throw new IllegalStateException("Missing structure template " + resource);
				}
			}
		}
		if (!clueIndices.equals(Set.of(0, 1, 2, 3))) {
			throw new IllegalStateException("Facility data must define environmental clues 0 through 3");
		}
		TheFourthFrequency.LOGGER.info("Loaded and validated {} built-in predecessor facility definitions", definitions.size());
	}

	private static void validateBlock(String value, String facilityId) {
		Identifier id = Identifier.tryParse(value);
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			throw new IllegalStateException("Unknown block " + value + " in facility " + facilityId);
		}
	}
}
