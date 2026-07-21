package com.xm.thefourthfrequency.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class M6Networking {
	private static boolean initialized;

	private M6Networking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		PayloadTypeRegistry.playS2C().register(PrivateAnomalyPayload.TYPE, PrivateAnomalyPayload.CODEC);
	}
}
