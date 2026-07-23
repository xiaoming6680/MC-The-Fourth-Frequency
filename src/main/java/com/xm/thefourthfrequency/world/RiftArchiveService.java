package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.narrative.HiddenFilePolicy;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.narrative.WitnessArchive;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.List;

/** Owns the current shared overworld fracture and per-player witness archive access. */
public final class RiftArchiveService {
	private static final String ARCHIVE_UNLOCKED = "archive_unlocked";
	private static final String RIFT_ENTRANCE = "rift_entrance";
	private static final String RIFT_CORE = "rift_core";
	private static final String RIFT_DEPTH = "rift_depth";
	private static final String RIFT_CURSOR = "rift_cursor";
	private static final String RIFT_COMPLETE = "rift_complete";
	private static final int BUILD_BUDGET_PER_TICK = 32;

	private RiftArchiveService() {
	}

	public static void initialize() {
		WitnessArchive.get();
		ServerTickEvents.END_SERVER_TICK.register(RiftArchiveService::updateServer);
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer)
					|| level.getBlockState(hitResult.getBlockPos()).getBlock() != ModBlocks.RULE_FRACTURE_CORE) {
				return InteractionResult.PASS;
			}
			return observeRift(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		});
	}

	private static void updateServer(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!data.narrativeState().getBooleanOr(ARCHIVE_UNLOCKED, false)) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) synchronizeArchiveAccess(player, data);
		buildRift(server.overworld(), data);
	}

	public static void updateForTesting(MinecraftServer server) {
		updateServer(server);
	}

	/** Unlocks this player's diary and creates the one shared rift for the first eligible reader. */
	public static boolean unlockArchiveFromHiddenFiles(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag terminal = data.terminalRecord(player.getUUID()).orElse(null);
		if (terminal == null || !HiddenFilePolicy.allDiscovered(terminal) || !HiddenFilePolicy.allRead(terminal)) {
			return false;
		}
		CompoundTag narrative = data.narrativeState();
		if (!narrative.getBooleanOr(ARCHIVE_UNLOCKED, false) || !narrative.contains(RIFT_CORE)) {
			BlockPos entrance = narrative.contains(RIFT_ENTRANCE)
					? BlockPos.of(narrative.getLongOr(RIFT_ENTRANCE, 0L))
					: player.blockPosition().offset(24, 0, 0);
			int availableDepth = entrance.getY() - player.level().getServer().overworld().getMinY() - 2;
			int depth = narrative.contains(RIFT_DEPTH)
					? Math.clamp(narrative.getIntOr(RIFT_DEPTH, 2), 2, 10)
					: Math.clamp(availableDepth, 2, 10);
			BlockPos core = narrative.contains(RIFT_CORE)
					? BlockPos.of(narrative.getLongOr(RIFT_CORE, 0L))
					: RiftLayout.corePosition(entrance, depth);
			data.updateNarrativeState(tag -> {
				tag.putBoolean(ARCHIVE_UNLOCKED, true);
				tag.putLong(RIFT_ENTRANCE, entrance.asLong());
				tag.putLong(RIFT_CORE, core.asLong());
				tag.putInt(RIFT_DEPTH, depth);
				if (!tag.contains(RIFT_CURSOR)) tag.putInt(RIFT_CURSOR, 0);
				if (!tag.contains(RIFT_COMPLETE)) tag.putBoolean(RIFT_COMPLETE, false);
			});
		}
		synchronizeArchiveAccess(player, data);
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.archive.unlocked"));
		return true;
	}

	private static void synchronizeArchiveAccess(ServerPlayer player, FrequencyWorldData data) {
		CompoundTag terminal = data.terminalRecord(player.getUUID()).orElse(null);
		if (terminal == null) return;
		CompoundTag narrative = data.narrativeState();
		if (!narrative.getBooleanOr(ARCHIVE_UNLOCKED, false) || !narrative.contains(RIFT_CORE)) return;
		boolean grandfathered = terminal.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false)
				|| TerminalFileState.unlocked(terminal, HiddenFilePolicy.COMPLETE_FILE_ID);
		if (!grandfathered
				&& (!HiddenFilePolicy.allDiscovered(terminal) || !HiddenFilePolicy.allRead(terminal))) return;
		BlockPos core = BlockPos.of(narrative.getLongOr(RIFT_CORE, 0L));
		WitnessArchive archive = WitnessArchive.get();
		boolean firstUnlock = !terminal.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false);
		long now = player.level().getGameTime();
		long dayTime = player.level().getDayTime();
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.LOCAL_FILE_UNLOCKED, true);
			record.putInt(TerminalData.LOCAL_FILE_VERSION, archive.version());
			record.putString(TerminalData.LOCAL_FILE_HASH, archive.contentHash());
			record.putBoolean(TerminalData.RIFT_LOCATED, true);
			record.putLong(TerminalData.RIFT_POSITION, core.asLong());
			record.putString(TerminalData.RIFT_DIMENSION, "minecraft:overworld");
			record.putInt(TerminalData.PLOT_STAGE, Math.max(4, record.getIntOr(TerminalData.PLOT_STAGE, 1)));
			TerminalFileState.discover(record, HiddenFilePolicy.COMPLETE_FILE_ID, now, dayTime, true);
		});
		if (firstUnlock) {
			TerminalSignalService.record(player, SignalBand.UNKNOWN, "witness_file_unlocked", 0, 2, true);
			TerminalLifecycleService.ensureCarried(player, false);
		}
	}

	private static void buildRift(ServerLevel level, FrequencyWorldData data) {
		CompoundTag narrative = data.narrativeState();
		if (!narrative.contains(RIFT_ENTRANCE) || narrative.getBooleanOr(RIFT_COMPLETE, false)) return;
		BlockPos entrance = BlockPos.of(narrative.getLongOr(RIFT_ENTRANCE, 0L));
		if (!level.hasChunkAt(entrance)) return;
		int depth = Math.clamp(narrative.getIntOr(RIFT_DEPTH, 2), 2, 10);
		List<RiftLayout.Placement> plan = RiftLayout.create(entrance, depth);
		int cursor = Math.clamp(narrative.getIntOr(RIFT_CURSOR, 0), 0, plan.size());
		int end = Math.min(plan.size(), cursor + BUILD_BUDGET_PER_TICK);
		for (int index = cursor; index < end; index++) {
			RiftLayout.Placement placement = plan.get(index);
			if (!level.isInWorldBounds(placement.position()) || !level.hasChunkAt(placement.position())) break;
			level.setBlock(placement.position(), placement.state(), 3);
			cursor = index + 1;
		}
		int storedCursor = cursor;
		data.updateNarrativeState(tag -> {
			tag.putInt(RIFT_CURSOR, storedCursor);
			tag.putBoolean(RIFT_COMPLETE, storedCursor == plan.size());
		});
	}

	private static boolean observeRift(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !record.getBooleanOr(TerminalData.RIFT_LOCATED, false)) return false;
		boolean firstObservation = !record.getBooleanOr(TerminalData.RIFT_OBSERVED, false);
		data.updateTerminalRecord(player.getUUID(), tag -> tag.putBoolean(TerminalData.RIFT_OBSERVED, true));
		if (firstObservation) {
			TerminalSignalService.record(player, SignalBand.UNKNOWN, "overworld_fracture_observed", 0, 2, true);
		}
		TerminalLifecycleService.ensureCarried(player, false);
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.rift.observed"));
		return true;
	}
}
