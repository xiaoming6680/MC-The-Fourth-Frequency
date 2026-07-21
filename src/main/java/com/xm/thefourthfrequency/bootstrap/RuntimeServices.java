package com.xm.thefourthfrequency.bootstrap;

import com.xm.thefourthfrequency.config.ModConfig;
import com.xm.thefourthfrequency.persistence.PersistenceSchema;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeServices {
	public static final int PERSISTENCE_SCHEMA_VERSION = PersistenceSchema.CURRENT_VERSION;
	private static final AtomicReference<ModConfig> CONFIG = new AtomicReference<>();

	private RuntimeServices() {
	}

	public static void initialize(ModConfig config) {
		Objects.requireNonNull(config, "config");
		if (!CONFIG.compareAndSet(null, config)) {
			throw new IllegalStateException("Runtime services were initialized more than once");
		}
	}

	public static ModConfig config() {
		ModConfig config = CONFIG.get();
		if (config == null) {
			throw new IllegalStateException("Runtime services are not initialized");
		}
		return config;
	}
}

