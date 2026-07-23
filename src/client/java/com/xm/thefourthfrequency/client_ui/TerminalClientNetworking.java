package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.TerminalClosedPayload;
import com.xm.thefourthfrequency.networking.TerminalNavigationPayload;
import com.xm.thefourthfrequency.networking.TerminalSnapshotPayload;
import com.xm.thefourthfrequency.networking.TerminalToolSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class TerminalClientNetworking {
	private static boolean initialized;

	private TerminalClientNetworking() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(TerminalSnapshotPayload.TYPE, (payload, context) ->
				context.client().execute(() -> openOrUpdate(payload)));
		ClientPlayNetworking.registerGlobalReceiver(TerminalNavigationPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (context.client().screen instanceof TerminalScreen terminal) terminal.updateNavigation(payload);
				}));
		ClientPlayNetworking.registerGlobalReceiver(TerminalToolSnapshotPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (context.client().screen instanceof TerminalScreen terminal) terminal.updateTools(payload);
				}));
		ClientPlayNetworking.registerGlobalReceiver(TerminalClosedPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (context.client().screen instanceof TerminalScreen terminal) terminal.closeFromServer();
				}));
	}

	private static void openOrUpdate(TerminalSnapshotPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof TerminalScreen terminal) terminal.update(payload);
		else client.setScreen(new TerminalScreen(payload));
	}
}
