package com.xm.thefourthfrequency.persistence;

import java.util.Map;

public final class PersistenceSchema {
	public static final int CURRENT_VERSION = 7;

	private PersistenceSchema() {
	}

	public static SchemaMigrator migrator() {
		return new SchemaMigrator(CURRENT_VERSION, Map.of(
				0, document -> {
					document.addProperty("schemaVersion", 1);
					return document;
				},
				1, document -> {
					document.addProperty("schemaVersion", 2);
					return document;
				},
				2, document -> {
					document.addProperty("schemaVersion", 3);
					return document;
				},
				3, document -> {
					document.addProperty("schemaVersion", 4);
					return document;
				},
				4, document -> {
					document.addProperty("schemaVersion", 5);
					return document;
				},
				5, document -> {
					document.addProperty("schemaVersion", 6);
					return document;
				},
				6, document -> {
					document.addProperty("schemaVersion", 7);
					return document;
				}));
	}
}
