package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.TerminalNoticePayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Small LIFO notice stack: new feedback enters at the bottom and pushes prior entries upward.
 * The bottom entry leaves first; the remaining entries then fall one slot before the next timer starts.
 */
public final class TerminalNoticeHud {
	static final int MAX_VISIBLE = 4;
	static final long HOLD_MILLIS = 2_800L;
	static final long EXIT_MILLIS = 260L;
	private static final int SLOT_HEIGHT = 16;
	private static final int DEFAULT_BACKGROUND = 0x101A14;
	private static final int DEFAULT_BORDER = 0x6FA77A;
	private static final int TASK_BACKGROUND = 0x185C32;
	private static final int TASK_BORDER = 0x72E595;
	private static final List<NoticeEntry> ENTRIES = new ArrayList<>();
	private static long bottomActivatedAt;
	private static long lastRenderAt;
	private static NoticeEntry exiting;
	private static boolean initialized;

	private TerminalNoticeHud() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	public static void enqueue(Component message) {
		enqueue(message, TerminalNoticePayload.TONE_NONE);
	}

	public static void enqueue(Component message, int tone) {
		long now = Util.getMillis();
		finishExit(now);
		if (ENTRIES.size() >= MAX_VISIBLE) ENTRIES.removeFirst();
		for (NoticeEntry entry : ENTRIES) entry.targetSlot++;
		ENTRIES.add(new NoticeEntry(message, tone, -0.65D, 0));
		bottomActivatedAt = now;
		lastRenderAt = now;
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			clear();
			return;
		}
		if (client.options.hideGui || ENTRIES.isEmpty()) return;
		long now = Util.getMillis();
		if (exiting == null && now - bottomActivatedAt >= HOLD_MILLIS) {
			exiting = ENTRIES.getLast();
			exiting.exitStartedAt = now;
		}
		if (exiting != null && now - exiting.exitStartedAt >= EXIT_MILLIS) finishExit(now);
		double elapsed = Math.clamp(now - lastRenderAt, 0L, 100L);
		double movement = 1.0D - Math.exp(-elapsed / 70.0D);
		for (NoticeEntry entry : ENTRIES) {
			entry.currentSlot += (entry.targetSlot - entry.currentSlot) * movement;
		}
		lastRenderAt = now;

		int baseY = graphics.guiHeight() - 62;
		for (NoticeEntry entry : ENTRIES) renderEntry(graphics, client.font, entry, baseY, now);
	}

	private static void renderEntry(GuiGraphics graphics, Font font, NoticeEntry entry, int baseY, long now) {
		double exitProgress = entry == exiting
				? Math.clamp((now - entry.exitStartedAt) / (double) EXIT_MILLIS, 0.0D, 1.0D) : 0.0D;
		int alpha = (int) Math.round(230.0D * (1.0D - exitProgress));
		if (alpha <= 0) return;
		int textWidth = Math.max(1, font.width(entry.message));
		float scale = Math.min(1.0F, Math.max(0.62F,
				(graphics.guiWidth() - 36.0F) / (textWidth + 16.0F)));
		int panelWidth = Math.round((textWidth + 14) * scale);
		int x = graphics.guiWidth() / 2;
		int y = baseY - (int) Math.round(entry.currentSlot * SLOT_HEIGHT) + (int) Math.round(exitProgress * 8.0D);
		int left = x - panelWidth / 2;
		boolean taskComplete = entry.tone == TerminalNoticePayload.TONE_TASK_COMPLETE;
		int background = alpha << 24 | (taskComplete ? TASK_BACKGROUND : DEFAULT_BACKGROUND);
		int border = Math.min(255, alpha + 20) << 24 | (taskComplete ? TASK_BORDER : DEFAULT_BORDER);
		graphics.fill(left, y - 3, left + panelWidth, y + Math.round(12 * scale), background);
		graphics.renderOutline(left, y - 3, panelWidth, Math.max(8, Math.round(12 * scale) + 3), border);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, entry.message, -textWidth / 2, 0, alpha << 24 | 0xD8F4DD, true);
		graphics.pose().popMatrix();
	}

	private static void finishExit(long now) {
		if (exiting == null) return;
		ENTRIES.remove(exiting);
		exiting = null;
		for (NoticeEntry entry : ENTRIES) entry.targetSlot = Math.max(0, entry.targetSlot - 1);
		bottomActivatedAt = now;
	}

	private static void clear() {
		ENTRIES.clear();
		exiting = null;
		bottomActivatedAt = 0L;
		lastRenderAt = 0L;
	}

	static int queuedForTesting() {
		return ENTRIES.size();
	}

	static void clearForTesting() {
		clear();
	}

	private static final class NoticeEntry {
		private final Component message;
		private final int tone;
		private double currentSlot;
		private int targetSlot;
		private long exitStartedAt;

		private NoticeEntry(Component message, int tone, double currentSlot, int targetSlot) {
			this.message = message;
			this.tone = tone;
			this.currentSlot = currentSlot;
			this.targetSlot = targetSlot;
		}
	}
}
