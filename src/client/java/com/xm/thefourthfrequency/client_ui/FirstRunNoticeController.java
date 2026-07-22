package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.IOException;

/** Client-local, one-time safety notice shown when the title screen first becomes available. */
public final class FirstRunNoticeController {
	private static final int CURRENT_NOTICE_VERSION = 3;
	private static final String VERSION_FILE = "thefourthfrequency-safety-notice.version";
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
		if (!pending || acknowledged || FailureMenuLockState.locked()
				|| !(client.screen instanceof TitleScreen titleScreen)) return;
		client.setScreen(new FirstRunNoticeScreen(titleScreen));
	}

	static void acknowledge(Minecraft client, Screen returnScreen) {
		acknowledged = true;
		pending = false;
		writeAcknowledged();
		client.setScreen(returnScreen);
	}

	private static boolean readAcknowledged() {
		Path path = noticeVersionPath();
		try {
			return Files.isRegularFile(path)
					&& Integer.parseInt(Files.readString(path, StandardCharsets.UTF_8).strip()) >= CURRENT_NOTICE_VERSION;
		} catch (IOException | NumberFormatException ignored) {
			return false;
		}
	}

	private static void writeAcknowledged() {
		Path path = noticeVersionPath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, Integer.toString(CURRENT_NOTICE_VERSION), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		} catch (IOException ignored) {
			// The upgraded notice safely appears again if its version marker cannot be persisted.
		}
	}

	public static synchronized boolean resetForReplay() {
		acknowledged = false;
		// The current process is closing; the next launch will arm the notice from disk.
		pending = false;
		try {
			Files.deleteIfExists(noticeVersionPath());
			return true;
		} catch (IOException exception) {
			return false;
		}
	}

	private static Path noticeVersionPath() {
		return ConfigManager.configPathForTesting().resolveSibling(VERSION_FILE).toAbsolutePath().normalize();
	}

	public static boolean acknowledgedForTesting() { return acknowledged; }
	public static boolean pendingForTesting() { return pending; }
	public static Path configPathForTesting() { return ConfigManager.configPathForTesting(); }
	public static Path noticeVersionPathForTesting() { return noticeVersionPath(); }
	public static void reloadFromDiskForTesting() {
		acknowledged = readAcknowledged();
		pending = false;
	}
}
