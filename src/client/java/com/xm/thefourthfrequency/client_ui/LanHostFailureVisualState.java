package com.xm.thefourthfrequency.client_ui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Session-local renderer switch for the LAN host's non-closing failure epilogue. */
public final class LanHostFailureVisualState {
	private static volatile UUID encounterId;
	private static volatile boolean active;

	private LanHostFailureVisualState() {
	}

	public static void activate(Minecraft client, UUID encounter) {
		encounterId = encounter;
		active = true;
		if (client != null && client.levelRenderer != null) client.levelRenderer.allChanged();
	}

	public static void reset(Minecraft client) {
		boolean changed = active;
		active = false;
		encounterId = null;
		if (changed && client != null && client.levelRenderer != null) client.levelRenderer.allChanged();
	}

	public static boolean active() {
		return active;
	}

	public static UUID encounterId() {
		return encounterId;
	}

	/** TextureManager-backed entity materials; block models are replaced in their dispatcher Mixin. */
	public static boolean corruptEntityTexture(Identifier id) {
		if (!active || id == null) return false;
		String path = id.getPath();
		return path.startsWith("textures/entity/")
				|| path.startsWith("textures/models/armor/")
				|| path.startsWith("textures/painting/")
				|| path.startsWith("skins/")
				|| path.contains("player_skin");
	}
}
