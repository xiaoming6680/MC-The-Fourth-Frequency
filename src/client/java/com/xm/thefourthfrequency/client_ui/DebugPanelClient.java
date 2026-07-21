package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.DebugOpenPayload;
import com.xm.thefourthfrequency.networking.DebugStatusPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class DebugPanelClient {
	private static KeyMapping openKey;
	private static String pendingAnomalyId;
	private static boolean initialized;
	private DebugPanelClient() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.thefourthfrequency.debug_panel", GLFW.GLFW_KEY_M, KeyMapping.Category.MISC));
		ClientPlayNetworking.registerGlobalReceiver(DebugStatusPayload.TYPE, (payload, context) ->
				context.client().execute(() -> accept(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.player != null && ClientPlayNetworking.canSend(DebugOpenPayload.TYPE))
					ClientPlayNetworking.send(new DebugOpenPayload());
			}
		});
	}

	private static void accept(DebugStatusPayload payload) {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (payload.protocolVersion() != DebugStatusPayload.CURRENT_PROTOCOL_VERSION) {
			if (client.player != null) client.player.displayClientMessage(Component.literal("调试协议版本不匹配"), false);
			return;
		}
		if (!payload.allowed()) {
			boolean anomalyResponse = pendingAnomalyId != null;
			pendingAnomalyId = null;
			if (client.player != null) client.player.displayClientMessage(Component.literal(
					(anomalyResponse ? "[异象触发失败] " : "") + payload.message()), false);
			if (client.screen instanceof DebugPanelScreen) client.setScreen(null);
			return;
		}
		String requestedAnomaly = pendingAnomalyId;
		boolean anomalyResponse = requestedAnomaly != null;
		boolean anomalyStarted = anomalyResponse
				&& requestedAnomaly.equals(payload.activeAnomaly())
				&& payload.message().startsWith("已触发异象：");
		pendingAnomalyId = null;
		if (anomalyResponse && client.player != null) client.player.displayClientMessage(Component.literal(
				(anomalyStarted ? "[异象触发成功] " : "[异象触发失败] ") + payload.message()), false);
		if (anomalyStarted) {
			if (client.screen instanceof DebugPanelScreen) client.setScreen(null);
			return;
		}
		if (client.screen instanceof DebugPanelScreen screen) screen.update(payload);
		else client.setScreen(new DebugPanelScreen(payload));
	}

	static void expectAnomalyResponse(String anomalyId) {
		pendingAnomalyId = anomalyId;
	}
}
