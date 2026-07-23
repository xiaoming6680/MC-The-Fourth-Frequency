package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.networking.TerminalNoticePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Routes mod feedback into the client-side bounded notice stack. */
public final class TerminalNoticeService {
	private TerminalNoticeService() {
	}

	public static void send(ServerPlayer player, Component message) {
		send(player, message, TerminalNoticePayload.TONE_NONE);
	}

	public static void unread(ServerPlayer player) {
		send(player, Component.translatable("message.thefourthfrequency.terminal.unread"),
				TerminalNoticePayload.TONE_UNREAD);
	}

	public static void taskComplete(ServerPlayer player) {
		send(player, Component.translatable("message.thefourthfrequency.task.completed"),
				TerminalNoticePayload.TONE_TASK_COMPLETE);
	}

	private static void send(ServerPlayer player, Component message, int tone) {
		if (ServerPlayNetworking.canSend(player, TerminalNoticePayload.TYPE)) {
			ServerPlayNetworking.send(player, new TerminalNoticePayload(message, tone));
		} else {
			// Dedicated GameTests and non-modded diagnostic connections retain readable fallback feedback.
			player.displayClientMessage(message, true);
		}
	}
}
