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
 * Throttled priority notice stack. Incoming feedback waits in a bounded queue, then enters from the
 * bottom at a readable cadence. Important notices retain priority without bypassing that cadence.
 */
public final class TerminalNoticeHud {
	static final int MAX_VISIBLE = 3;
	static final int MAX_PENDING = 12;
	static final long HOLD_MILLIS = 2_800L;
	static final long EXIT_MILLIS = 260L;
	static final long MIN_APPEAR_INTERVAL_MILLIS = 900L;
	static final long DUPLICATE_WINDOW_MILLIS = 5_000L;
	private static final int SLOT_HEIGHT = 16;
	private static final int MAX_RECENT = 64;
	private static final int DEFAULT_BACKGROUND = 0x101A14;
	private static final int DEFAULT_BORDER = 0x6FA77A;
	private static final int TASK_BACKGROUND = 0x185C32;
	private static final int TASK_BORDER = 0x72E595;
	private static final int PURSUIT_BACKGROUND = 0x59151B;
	private static final int PURSUIT_BORDER = 0xF05B65;
	private static final int PURSUIT_TEXT = 0xFFE2E4;
	private static final NoticeEntry PURSUIT_STATUS = new NoticeEntry(
			Component.translatable("message.thefourthfrequency.pursuit.try_escape"),
			TerminalNoticePayload.TONE_PURSUIT_WARNING, 0.0D, 0);
	private static final NoticeEntry ESCAPE_STATUS = new NoticeEntry(
			Component.translatable("message.thefourthfrequency.pursuit.escaped_temporary"),
			TerminalNoticePayload.TONE_TASK_COMPLETE, 0.0D, 0);
	private static final List<NoticeEntry> ENTRIES = new ArrayList<>();
	private static final List<NoticeEntry> PENDING = new ArrayList<>();
	private static final List<RecentNotice> RECENT = new ArrayList<>();
	private static long bottomActivatedAt;
	private static long lastActivatedAt;
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
		if (duplicateRecently(message, tone, now)) return;
		remember(message, tone, now);
		NoticeEntry entry = new NoticeEntry(message, tone, -0.65D, 0);
		int priority = priority(tone);
		int insertion = PENDING.size();
		for (int index = 0; index < PENDING.size(); index++) {
			if (priority > priority(PENDING.get(index).tone)) {
				insertion = index;
				break;
			}
		}
		PENDING.add(insertion, entry);
		trimPending();
	}

	private static void promotePending(long now) {
		if (PENDING.isEmpty() || exiting != null || ENTRIES.size() >= MAX_VISIBLE) return;
		if (!ENTRIES.isEmpty() && now - lastActivatedAt < MIN_APPEAR_INTERVAL_MILLIS) return;
		NoticeEntry pending = PENDING.removeFirst();
		for (NoticeEntry entry : ENTRIES) entry.targetSlot++;
		ENTRIES.add(new NoticeEntry(pending.message, pending.tone, -0.65D, 0));
		bottomActivatedAt = now;
		lastActivatedAt = now;
		lastRenderAt = now;
		if (pending.tone != TerminalNoticePayload.TONE_NONE) TerminalClientAudio.attention(pending.tone);
	}

	private static void trimPending() {
		if (PENDING.size() <= MAX_PENDING) return;
		int lowestPriority = priority(PENDING.getLast().tone);
		for (int index = 0; index < PENDING.size(); index++) {
			if (priority(PENDING.get(index).tone) == lowestPriority) {
				PENDING.remove(index);
				return;
			}
		}
	}

	private static boolean duplicateRecently(Component message, int tone, long now) {
		String key = noticeKey(message, tone);
		RECENT.removeIf(entry -> now - entry.createdAt >= DUPLICATE_WINDOW_MILLIS);
		for (RecentNotice entry : RECENT) if (entry.key.equals(key)) return true;
		return false;
	}

	private static void remember(Component message, int tone, long now) {
		if (RECENT.size() >= MAX_RECENT) RECENT.removeFirst();
		RECENT.add(new RecentNotice(noticeKey(message, tone), now));
	}

	private static String noticeKey(Component message, int tone) {
		return tone + "\u0000" + message.getString();
	}

	private static int priority(int tone) {
		return switch (tone) {
			case TerminalNoticePayload.TONE_PURSUIT_WARNING -> 3;
			case TerminalNoticePayload.TONE_TASK_COMPLETE -> 2;
			case TerminalNoticePayload.TONE_UNREAD -> 1;
			default -> 0;
		};
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			clear();
			return;
		}
		if (client.options.hideGui) return;
		long now = Util.getMillis();
		int baseY = graphics.guiHeight() - 62;
		if (PursuitPresentationClient.pursuitHudActive()) {
			renderEntry(graphics, client.font, PURSUIT_STATUS, baseY, now);
			return;
		}
		if (PursuitPresentationClient.escapeHudActive()) {
			renderEntry(graphics, client.font, ESCAPE_STATUS, baseY, now);
			return;
		}
		if (PursuitPresentationClient.holdsNoticeQueue()) return;
		if (exiting != null && now - exiting.exitStartedAt >= EXIT_MILLIS) finishExit(now);
		promotePending(now);
		if (ENTRIES.isEmpty()) return;
		if (exiting == null && now - bottomActivatedAt >= HOLD_MILLIS) {
			exiting = ENTRIES.getLast();
			exiting.exitStartedAt = now;
		}
		double elapsed = Math.clamp(now - lastRenderAt, 0L, 100L);
		double movement = 1.0D - Math.exp(-elapsed / 70.0D);
		for (NoticeEntry entry : ENTRIES) {
			entry.currentSlot += (entry.targetSlot - entry.currentSlot) * movement;
		}
		lastRenderAt = now;

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
		boolean pursuitWarning = entry.tone == TerminalNoticePayload.TONE_PURSUIT_WARNING;
		boolean taskComplete = entry.tone == TerminalNoticePayload.TONE_TASK_COMPLETE;
		int backgroundColor = pursuitWarning ? PURSUIT_BACKGROUND
				: taskComplete ? TASK_BACKGROUND : DEFAULT_BACKGROUND;
		int borderColor = pursuitWarning ? PURSUIT_BORDER : taskComplete ? TASK_BORDER : DEFAULT_BORDER;
		int textColor = pursuitWarning ? PURSUIT_TEXT : 0xD8F4DD;
		int background = alpha << 24 | backgroundColor;
		int border = Math.min(255, alpha + 20) << 24 | borderColor;
		graphics.fill(left, y - 3, left + panelWidth, y + Math.round(12 * scale), background);
		graphics.renderOutline(left, y - 3, panelWidth, Math.max(8, Math.round(12 * scale) + 3), border);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, entry.message, -textWidth / 2, 0, alpha << 24 | textColor, true);
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
		PENDING.clear();
		RECENT.clear();
		exiting = null;
		bottomActivatedAt = 0L;
		lastActivatedAt = 0L;
		lastRenderAt = 0L;
	}

	static int queuedForTesting() {
		return ENTRIES.size() + PENDING.size();
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

	private record RecentNotice(String key, long createdAt) {
	}
}
