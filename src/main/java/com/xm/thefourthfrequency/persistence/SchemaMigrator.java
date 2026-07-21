package com.xm.thefourthfrequency.persistence;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class SchemaMigrator {
	private static final String VERSION_KEY = "schemaVersion";
	private final int currentVersion;
	private final Map<Integer, UnaryOperator<JsonObject>> migrations;

	public SchemaMigrator(int currentVersion, Map<Integer, UnaryOperator<JsonObject>> migrations) {
		if (currentVersion < 1) {
			throw new IllegalArgumentException("currentVersion must be positive");
		}
		this.currentVersion = currentVersion;
		this.migrations = Map.copyOf(migrations);
	}

	public JsonObject migrate(JsonObject source) {
		JsonObject working = Objects.requireNonNull(source, "source").deepCopy();
		int version = readVersion(working);
		if (version > currentVersion) {
			throw new UnsupportedSchemaVersionException(version, currentVersion);
		}

		while (version < currentVersion) {
			UnaryOperator<JsonObject> migration = migrations.get(version);
			if (migration == null) {
				throw new IllegalStateException("Missing schema migration from version " + version);
			}
			working = Objects.requireNonNull(migration.apply(working.deepCopy()), "migration result");
			int migratedVersion = readVersion(working);
			if (migratedVersion != version + 1) {
				throw new IllegalStateException("Schema migration " + version + " must advance exactly one version");
			}
			version = migratedVersion;
		}
		return working;
	}

	public JsonObject newDocument() {
		JsonObject document = new JsonObject();
		document.addProperty(VERSION_KEY, currentVersion);
		return document;
	}

	private static int readVersion(JsonObject document) {
		if (!document.has(VERSION_KEY) || !document.get(VERSION_KEY).isJsonPrimitive()) {
			return 0;
		}
		int version = document.get(VERSION_KEY).getAsInt();
		if (version < 0) {
			throw new IllegalArgumentException("schemaVersion cannot be negative");
		}
		return version;
	}
}

