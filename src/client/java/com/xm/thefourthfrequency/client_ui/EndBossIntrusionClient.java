package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.EndBossIntrusionS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Client-only presentation mirror of a server-authoritative hotbar lease. */
public final class EndBossIntrusionClient {
	private static UUID encounterId;
	private static int lastSequence;
	private static int lockedSlotMask;
	private static long expiresAtTick;
	private static boolean initialized;

	private EndBossIntrusionClient() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(EndBossIntrusionS2C.TYPE, (payload, context) ->
				context.client().execute(() -> accept(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(EndBossIntrusionClient::tick);
	}

	private static void accept(EndBossIntrusionS2C payload) {
		if (encounterId != null && encounterId.equals(payload.encounterId()) && payload.sequence() <= lastSequence) return;
		if (encounterId == null || !encounterId.equals(payload.encounterId())) lastSequence = 0;
		encounterId = payload.encounterId();
		lastSequence = payload.sequence();
		lockedSlotMask = payload.lockedSlotMask() & 0x1FF;
		expiresAtTick = payload.expiresAtTick();
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.level.dimension() != Level.END) {
			clear();
			return;
		}
		if (lockedSlotMask != 0 && client.level.getGameTime() >= expiresAtTick) {
			lockedSlotMask = 0;
		}
	}

	public static boolean isLocked(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		if (stack.isEmpty() || client.player == null || client.level == null || lockedSlotMask == 0
				|| client.level.getGameTime() >= expiresAtTick) return false;
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if ((lockedSlotMask & 1 << slot) != 0 && client.player.getInventory().getItem(slot) == stack) return true;
		}
		return false;
	}

	public static int lockedSlotMask() {
		return lockedSlotMask;
	}

	private static void clear() {
		encounterId = null;
		lastSequence = 0;
		lockedSlotMask = 0;
		expiresAtTick = 0L;
	}
}
