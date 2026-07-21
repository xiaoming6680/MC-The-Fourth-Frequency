package com.xm.thefourthfrequency.persistence;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaMigratorTest {
	@Test
	void createsDocumentsAtCurrentVersion() {
		JsonObject document = PersistenceSchema.migrator().newDocument();
		assertEquals(PersistenceSchema.CURRENT_VERSION, document.get("schemaVersion").getAsInt());
	}

	@Test
	void appliesEveryMigrationExactlyOnce() {
		SchemaMigrator migrator = new SchemaMigrator(1, Map.of(0, document -> {
			document.addProperty("schemaVersion", 1);
			document.addProperty("migrated", true);
			return document;
		}));
		JsonObject migrated = migrator.migrate(new JsonObject());
		assertEquals(1, migrated.get("schemaVersion").getAsInt());
		assertEquals(true, migrated.get("migrated").getAsBoolean());
	}

	@Test
	void rejectsFutureSaveVersions() {
		JsonObject future = new JsonObject();
		future.addProperty("schemaVersion", PersistenceSchema.CURRENT_VERSION + 1);
		assertThrows(UnsupportedSchemaVersionException.class, () -> PersistenceSchema.migrator().migrate(future));
	}

	@Test
	void productionMigratorUpgradesUnversionedAlphaDataWithoutDroppingFields() {
		JsonObject legacy = new JsonObject();
		legacy.addProperty("worldId", "legacy-world");
		legacy.addProperty("stationComplete", true);
		JsonObject migrated = PersistenceSchema.migrator().migrate(legacy);
		assertEquals(PersistenceSchema.CURRENT_VERSION, migrated.get("schemaVersion").getAsInt());
		assertEquals("legacy-world", migrated.get("worldId").getAsString());
		assertEquals(true, migrated.get("stationComplete").getAsBoolean());
	}
}
