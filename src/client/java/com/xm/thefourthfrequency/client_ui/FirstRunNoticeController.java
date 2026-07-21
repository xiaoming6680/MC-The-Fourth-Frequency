package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.config.ConfigManager;
import com.xm.thefourthfrequency.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Path;

/** Client-local, one-time safety notice shown when the title screen first becomes available. */
public final class FirstRunNoticeController {
	private static boolean initialized;
	private static boolean acknowledged;
	private static boolean pending;

	private FirstRunNoticeController() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		acknowledged = readAcknowledged();
		pending = !acknowledged;
		ClientTickEvents.END_CLIENT_TICK.register(FirstRunNoticeController::tick);
	}

	private static void tick(Minecraft client) {
		if (!pending || acknowledged || !(client.screen instanceof TitleScreen titleScreen)) return;
		client.setScreen(new FirstRunNoticeScreen(titleScreen));
	}

	static void acknowledge(Minecraft client, Screen returnScreen) {
		acknowledged = true;
		pending = false;
		writeAcknowledged();
		client.setScreen(returnScreen);
	}

	private static boolean readAcknowledged() {
		return ConfigManager.loadClientState().safetyNoticeAcknowledged();
	}

	private static void writeAcknowledged() {
		ConfigManager.updateClientState(ModConfig.ClientState::acknowledgeSafetyNotice);
	}

	public static boolean acknowledgedForTesting() { return acknowledged; }
	public static boolean pendingForTesting() { return pending; }
	public static Path configPathForTesting() { return ConfigManager.configPathForTesting(); }
	public static void reloadFromDiskForTesting() {
		acknowledged = readAcknowledged();
		pending = false;
	}
}
