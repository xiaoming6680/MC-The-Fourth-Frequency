package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.FinaleRuntimePolicy;
import com.xm.thefourthfrequency.networking.WorldDecayPayload;
import com.xm.thefourthfrequency.networking.MenuErosionStageS2C;
import com.xm.thefourthfrequency.terminal.WorldDecayResiduePolicy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class WorldDecayService {
	private static boolean initialized;
	private WorldDecayService() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(WorldDecayService::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		int sharedStage = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
			if (record != null) sharedStage = Math.max(sharedStage, stage(data, record));
		}
		WorldDecayPayload payload = new WorldDecayPayload(sharedStage);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
			CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
			int ceiling = record == null ? 1 : record.getIntOr(TerminalData.ANOMALY_STORY_CEILING,
					record.getIntOr(TerminalData.ANOMALY_TIER, 1));
			int menuStage = MenuErosionStageS2C.stageFor(ceiling, FinaleRuntimePolicy.succeeded(data));
			ServerPlayNetworking.send(player, new MenuErosionStageS2C(menuStage));
		}
	}

	public static int stage(FrequencyWorldData data, CompoundTag record) {
		if (FinaleRuntimePolicy.succeeded(data)) return 0;
		if (data.narrativeState().contains("decay_stage_override"))
			return Math.clamp(data.narrativeState().getIntOr("decay_stage_override", 0), 0, 5);
		int anomaly = Math.clamp(record.getIntOr(TerminalData.ANOMALY_TIER, 0), 0, 5);
		int milestones = record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		int survival = SurvivalMilestone.FOUND_STRONGHOLD.present(milestones) ? 5
				: SurvivalMilestone.RETURNED_NETHER.present(milestones) ? 4
				: SurvivalMilestone.IRON.present(milestones) ? 3 : 0;
		if (FinaleRuntimePolicy.pressureActive(data)) return 5;
		// The tier and milestone terms both describe where the player currently is, and both can
		// sit still for a long time. The residue term describes what they have already been
		// through and only ever climbs, so surviving anomalies leaves the world permanently worse
		// even when nothing else has advanced.
		int residue = WorldDecayResiduePolicy.residueStage(
				record.getIntOr(TerminalData.ANOMALY_RESIDUE_COUNT, 0));
		return Math.max(Math.max(anomaly, survival), residue);
	}
}
