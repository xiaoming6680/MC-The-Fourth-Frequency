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

	public static void unreadReminder(ServerPlayer player, int unreadCount) {
		send(player, Component.translatable("message.thefourthfrequency.terminal.unread_reminder", unreadCount),
				TerminalNoticePayload.TONE_UNREAD);
	}

	/**
	 * Completion and its reward are one moment, so they are one line. A separate "task complete"
	 * notice only ever preceded this one and carried nothing the reward text does not already imply.
	 */
	public static void rewardClaimed(ServerPlayer player, Component rewardName, int rewardCount,
			boolean completedNow) {
		send(player, Component.translatable(completedNow
				? "message.thefourthfrequency.task.completed_reward_claimed"
				: "message.thefourthfrequency.task.reward_claimed", rewardName, rewardCount),
				TerminalNoticePayload.TONE_TASK_COMPLETE);
	}

	/** Feedback for an action the mod refused; presented and sounded apart from progress notices. */
	public static void denied(ServerPlayer player, Component message) {
		send(player, message, TerminalNoticePayload.TONE_DENIED);
	}

	public static void denied(ServerPlayer player, String messageKey) {
		denied(player, Component.translatable(messageKey));
	}

	public static void pursuitWarning(ServerPlayer player) {
		pursuit(player, Component.translatable("message.thefourthfrequency.pursuit.warning"));
	}

	public static void pursuit(ServerPlayer player, Component message) {
		send(player, message, TerminalNoticePayload.TONE_PURSUIT_WARNING);
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
