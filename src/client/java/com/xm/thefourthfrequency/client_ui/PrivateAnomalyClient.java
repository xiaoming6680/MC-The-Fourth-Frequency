package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.PrivateAnomalyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class PrivateAnomalyClient {
	private static String anomalyId = "none";
	private static int variant;
	private static int bodyProgress;
	private static int capabilityMask;
	private static int remainingTicks;

	private PrivateAnomalyClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(PrivateAnomalyPayload.TYPE, (payload, context) -> {
			anomalyId = payload.anomalyId();
			variant = Math.floorMod(payload.variant(), 4);
			bodyProgress = Math.clamp(payload.bodyProgress(), 0, 1000);
			capabilityMask = payload.capabilityMask() & 31;
			remainingTicks = 100;
		});
		ClientTickEvents.END_CLIENT_TICK.register(PrivateAnomalyClient::tick);
	}

	private static void tick(Minecraft client) {
		if (remainingTicks <= 0 || client.player == null) {
			return;
		}
		remainingTicks--;
		if (remainingTicks == 0) {
			anomalyId = "none";
		}
	}

	public static String anomalyId() {
		return anomalyId;
	}

	public static int variant() {
		return variant;
	}

	public static int remainingTicks() { return remainingTicks; }
}
