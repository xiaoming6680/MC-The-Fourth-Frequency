package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.WorldDecayPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class WorldDecayClient {
	private static volatile int serverStage;
	private static int transientStage;
	private static int transientTicks;
	private WorldDecayClient() { }

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(WorldDecayPayload.TYPE, (payload, context) ->
				context.client().execute(() -> serverStage = Math.clamp(payload.stage(), 0, 5)));
		ClientTickEvents.END_CLIENT_TICK.register(WorldDecayClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	public static int stage() { return Math.max(serverStage, transientTicks > 0 ? transientStage : 0); }
	public static void pulse(int requestedStage, int durationTicks) {
		transientStage = Math.clamp(requestedStage, 1, 5);
		transientTicks = Math.max(transientTicks, Math.max(1, durationTicks));
	}

	public static boolean corruptTexture(net.minecraft.resources.Identifier id) {
		return WorldDecayTexturePolicy.shouldCorrupt(stage(), id.getNamespace(), id.getPath());
	}

	private static void tick(Minecraft client) {
		if (transientTicks > 0) transientTicks--;
	}

	private static void reset() {
		serverStage = 0;
		transientStage = 0;
		transientTicks = 0;
	}
}
