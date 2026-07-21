package com.xm.thefourthfrequency.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class M5Networking {
	private static boolean initialized;

	private M5Networking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		PayloadTypeRegistry.playS2C().register(EmptySegmentPayload.TYPE, EmptySegmentPayload.CODEC);
	}
}
