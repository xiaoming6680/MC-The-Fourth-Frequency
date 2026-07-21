package com.xm.thefourthfrequency.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class M8Networking {
	private static boolean initialized;
	private M8Networking() { }
	public static void initialize() {
		if (initialized) return;
		initialized = true;
		PayloadTypeRegistry.playS2C().register(MetaEventPayload.TYPE, MetaEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WorldDecayPayload.TYPE, WorldDecayPayload.CODEC);
	}
}
