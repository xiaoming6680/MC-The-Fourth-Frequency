package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Compact virtual-health, collapse-clock and authoritative-anchor HUD. */
public final class WorldInterfaceHud {
	private static final int ANCHOR_COUNT = Integer.bitCount(WorldInterfaceProtocol.ANCHOR_MASK);
	private static boolean initialized;

	private WorldInterfaceHud() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		WorldInterfaceClientState.Projection projection = WorldInterfaceClientState.snapshot();
		if (client.player == null || client.level == null || client.options.hideGui || !projection.combatVisible()) return;
		WorldInterfaceSnapshotS2C encounter = projection.encounter();
		if (encounter == null) return;
		int width = graphics.guiWidth();
		int barWidth = Math.clamp(width - 80, 180, 360);
		int left = (width - barWidth) / 2;
		int top = 12;
		int border = encounter.outcome() == WorldInterfaceProtocol.Outcome.FAILURE ? 0xFFD33B54 : 0xFF8E57B8;
		graphics.fill(left - 5, top - 5, left + barWidth + 5, top + 56, 0xB80B0710);
		graphics.renderOutline(left - 5, top - 5, barWidth + 10, 61, border);
		graphics.drawCenteredString(client.font, stageLabel(encounter), width / 2, top, 0xFFE8D7F2);
		int healthTop = top + 13;
		graphics.fill(left, healthTop, left + barWidth, healthTop + 8, 0xFF2B172D);
		int healthWidth = Math.round(barWidth * projection.healthRatio());
		graphics.fill(left, healthTop, left + healthWidth, healthTop + 8, 0xFF9B3BC3);
		String health = Math.round(encounter.currentHealth()) + " / " + Math.round(encounter.maxHealth());
		graphics.drawCenteredString(client.font, Component.literal(health), width / 2, healthTop, 0xFFFFFFFF);

		double collapse = projection.collapseProgress(client.level.getGameTime());
		int collapseTop = healthTop + 13;
		graphics.fill(left, collapseTop, left + barWidth, collapseTop + 5, 0xFF241822);
		graphics.fill(left, collapseTop, left + (int) Math.round(barWidth * collapse), collapseTop + 5,
				collapse >= 0.85D ? 0xFFE02C38 : 0xFFCE6B36);
		long remainingTicks = Math.max(0L, WorldInterfaceProtocol.COLLAPSE_DURATION_TICKS
				- Math.round(collapse * WorldInterfaceProtocol.COLLAPSE_DURATION_TICKS));
		String clock = String.format(Locale.ROOT, "%d:%02d", remainingTicks / 1_200L,
				(remainingTicks / 20L) % 60L);
		Component collapseLabel = Component.translatable(encounter.timerPaused()
				? "hud.thefourthfrequency.world_interface.collapse_paused"
				: "hud.thefourthfrequency.world_interface.collapse", clock);
		graphics.drawString(client.font, collapseLabel, left, collapseTop + 9,
				collapse >= 0.85D ? 0xFFFF8C91 : 0xFFD5A991, false);
		renderAnchors(graphics, client, encounter, left, barWidth, collapseTop + 9);
	}

	private static void renderAnchors(GuiGraphics graphics, Minecraft client,
			WorldInterfaceSnapshotS2C encounter, int left, int barWidth, int y) {
		int x = left + barWidth - 82;
		int alive = Integer.bitCount(encounter.anchorAliveMask() & WorldInterfaceProtocol.ANCHOR_MASK);
		Component anchors = Component.translatable("hud.thefourthfrequency.world_interface.anchors",
				alive, ANCHOR_COUNT);
		graphics.drawString(client.font, anchors, x - client.font.width(anchors) - 6, y, 0xFFCAB7CF, false);
		for (int index = 0; index < ANCHOR_COUNT; index++) {
			int cellX = x + index * 8;
			boolean present = (encounter.anchorAliveMask() & (1 << index)) != 0;
			graphics.fill(cellX, y + 1, cellX + 5, y + 7, present ? 0xFFB98BE0 : 0xFF3D303F);
		}
	}

	private static Component stageLabel(WorldInterfaceSnapshotS2C encounter) {
		return switch (encounter.stage()) {
			case SUMMONING -> Component.translatable("hud.thefourthfrequency.world_interface.stage.summoning");
			case PHASE_1 -> Component.translatable("hud.thefourthfrequency.world_interface.stage.phase_1");
			case PHASE_2 -> Component.translatable("hud.thefourthfrequency.world_interface.stage.phase_2");
			case PHASE_3 -> Component.translatable("hud.thefourthfrequency.world_interface.stage.phase_3");
			case SUCCESS_RESOLUTION -> Component.translatable(
					"hud.thefourthfrequency.world_interface.stage.success_resolution");
			case FAILURE_RESOLUTION -> Component.translatable(
					"hud.thefourthfrequency.world_interface.stage.failure_resolution");
			case PORTAL_OPEN -> encounter.outcome() == WorldInterfaceProtocol.Outcome.SUCCESS
					? Component.translatable("hud.thefourthfrequency.world_interface.stage.portal_success")
					: Component.translatable("hud.thefourthfrequency.world_interface.stage.portal_failure");
			default -> Component.translatable("hud.thefourthfrequency.world_interface.stage.phase_3");
		};
	}
}
