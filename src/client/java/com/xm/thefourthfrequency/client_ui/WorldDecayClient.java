package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.WorldDecayPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class WorldDecayClient {
	private static volatile int serverStage;
	private static int transientStage;
	private static int transientTicks;
	private static int appliedStage = -1;
	private static WindowSnapshot snapshot;
	private WorldDecayClient() { }

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(WorldDecayPayload.TYPE, (payload, context) ->
				context.client().execute(() -> serverStage = Math.clamp(payload.stage(), 0, 5)));
		ClientTickEvents.END_CLIENT_TICK.register(WorldDecayClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(client));
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
		if (client.getWindow() == null) return;
		if (transientTicks > 0) transientTicks--;
		int stage = stage();
		if (stage != appliedStage) {
			applyStage(client, stage);
			appliedStage = stage;
		}
	}

	private static void applyStage(Minecraft client, int stage) {
		// Early story stages may erode in-game textures, but must leave the host window identity untouched.
		if (stage < 3) {
			if (snapshot != null) restore(client);
			return;
		}
		if (snapshot == null) snapshot = WindowSnapshot.capture(client);
		if (stage >= 3) {
			if (client.getWindow().isFullscreen()) client.getWindow().toggleFullScreen();
			GLFW.glfwMaximizeWindow(client.getWindow().handle());
		}
	}

	private static void restore(Minecraft client) {
		if (snapshot != null) { snapshot.restore(client); snapshot = null; }
	}

	private static void reset(Minecraft client) {
		serverStage = 0; transientStage = 0; transientTicks = 0; appliedStage = -1;
		if (client.getWindow() != null) restore(client);
	}

	private record WindowSnapshot(boolean fullscreen, boolean maximized, int x, int y, int width, int height) {
		static WindowSnapshot capture(Minecraft client) {
			long handle = client.getWindow().handle();
			int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
			GLFW.glfwGetWindowPos(handle, x, y); GLFW.glfwGetWindowSize(handle, width, height);
			return new WindowSnapshot(client.getWindow().isFullscreen(),
					GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
					x[0], y[0], width[0], height[0]);
		}
		void restore(Minecraft client) {
			long handle = client.getWindow().handle();
			if (fullscreen != client.getWindow().isFullscreen()) client.getWindow().toggleFullScreen();
			if (!fullscreen) {
				GLFW.glfwRestoreWindow(handle);
				GLFW.glfwSetWindowPos(handle, x, y); GLFW.glfwSetWindowSize(handle, width, height);
				if (maximized) GLFW.glfwMaximizeWindow(handle);
			}
		}
	}
}
