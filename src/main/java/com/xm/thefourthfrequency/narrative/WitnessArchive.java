package com.xm.thefourthfrequency.narrative;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record WitnessArchive(String id, int version, String contentHash, List<String> lineKeys) {
	private static final String RESOURCE = "/data/thefourthfrequency/archive/witness_file.json";
	private static final WitnessArchive INSTANCE = load();

	public WitnessArchive {
		if (!"witness_termination_note".equals(id) || version != 1
				|| !"TFF-WF-01-A91C".equals(contentHash)
				|| lineKeys == null || lineKeys.size() < 4 || lineKeys.size() > 8
				|| lineKeys.stream().anyMatch(key -> key == null
				|| !key.matches("text\\.thefourthfrequency\\.archive\\.[a-z0-9_.]+"))) {
			throw new IllegalArgumentException("Invalid immutable witness archive definition");
		}
		lineKeys = List.copyOf(lineKeys);
	}

	public static WitnessArchive get() {
		return INSTANCE;
	}

	private static WitnessArchive load() {
		try (InputStream stream = WitnessArchive.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("Missing immutable witness archive resource");
			}
			WitnessArchive archive = new Gson().fromJson(
					new InputStreamReader(stream, StandardCharsets.UTF_8), WitnessArchive.class);
			if (archive == null) {
				throw new IllegalStateException("Witness archive decoded to null");
			}
			return archive;
		} catch (IOException | JsonParseException exception) {
			throw new IllegalStateException("Unable to load immutable witness archive", exception);
		}
	}
}
