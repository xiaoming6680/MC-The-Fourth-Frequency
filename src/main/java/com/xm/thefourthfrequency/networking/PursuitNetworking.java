package com.xm.thefourthfrequency.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class PursuitNetworking {
	private static boolean initialized;

	private PursuitNetworking() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		PayloadTypeRegistry.playS2C().register(PursuitPresentationPayload.TYPE,
				PursuitPresentationPayload.CODEC);
	}
}
