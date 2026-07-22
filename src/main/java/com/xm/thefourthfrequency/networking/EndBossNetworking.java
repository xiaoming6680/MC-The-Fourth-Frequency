package com.xm.thefourthfrequency.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class EndBossNetworking {
	private static boolean initialized;

	private EndBossNetworking() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		PayloadTypeRegistry.playS2C().register(EndBossIntrusionS2C.TYPE, EndBossIntrusionS2C.CODEC);
	}
}
