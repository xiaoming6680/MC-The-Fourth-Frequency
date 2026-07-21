package com.xm.thefourthfrequency.correction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CorrectionDefinitionLoader {
	private static final String RESOURCE = "/data/thefourthfrequency/correction/organs.json";

	private CorrectionDefinitionLoader() {
	}

	public static CorrectionParameters load() {
		try (var stream = CorrectionDefinitionLoader.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("Missing correction organ definition " + RESOURCE);
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
			Set<Block> excavation = parseBlocks(root.getAsJsonArray("excavation_blocks"));
			CorrectionParameters parameters = new CorrectionParameters(
					bounded(root, "trend_sample_interval_ticks", 5, 200),
					bounded(root, "trend_sample_budget", 4, 128),
					bounded(root, "index_refresh_budget", 1, 64),
					bounded(root, "trend_search_radius", 16, 96),
					bounded(root, "animal_stop_distance", 2, 20),
					bounded(root, "villager_stop_distance", 2, 24),
					bounded(root, "hostile_stop_distance", 2, 28),
					bounded(root, "rework_search_radius", 16, 128),
					bounded(root, "rework_contact_ticks", 20, 600),
					bounded(root, "rework_spawn_interval_ticks", 200, 72000),
					bounded(root, "empty_segment_min_interval_ticks", 1200, 720000),
					excavation);
			if (!(parameters.animalStopDistance() < parameters.villagerStopDistance()
					&& parameters.villagerStopDistance() < parameters.hostileStopDistance())) {
				throw new IllegalStateException("Trend stop distances must be strictly tiered");
			}
			return parameters;
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read correction organ definition", exception);
		}
	}

	private static int bounded(JsonObject object, String key, int minimum, int maximum) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			throw new IllegalStateException("Missing numeric correction field " + key);
		}
		int value = object.get(key).getAsInt();
		if (value < minimum || value > maximum) {
			throw new IllegalStateException("Correction field " + key + " outside " + minimum + ".." + maximum);
		}
		return value;
	}

	private static Set<Block> parseBlocks(JsonArray values) {
		if (values == null || values.isEmpty() || values.size() > 16) {
			throw new IllegalStateException("Correction excavation whitelist must contain 1..16 blocks");
		}
		Set<Block> blocks = new LinkedHashSet<>();
		for (JsonElement value : values) {
			Identifier id = Identifier.tryParse(value.getAsString());
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
			if (block == null) {
				throw new IllegalStateException("Unknown excavation block " + value);
			}
			blocks.add(block);
		}
		TheFourthFrequency.LOGGER.info("Validated correction organ definition with {} excavation blocks", blocks.size());
		return Set.copyOf(blocks);
	}
}
