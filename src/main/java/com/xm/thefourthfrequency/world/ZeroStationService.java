package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.content.TerminalData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ZeroStationService {
	private static final int BLOCKS_PER_TICK = 32;
	private static final Map<MinecraftServer, BuildState> ACTIVE_BUILDS = new IdentityHashMap<>();

	private ZeroStationService() {
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(ZeroStationService::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(ACTIVE_BUILDS::remove);
		ServerTickEvents.END_SERVER_TICK.register(ZeroStationService::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player));
	}

	private static void onServerStarted(MinecraftServer server) {
		ServerLevel level = server.overworld();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		boolean newlyAllocated = data.stationPosition().isEmpty();

		if (newlyAllocated) {
			BlockPos originalSpawn = level.getRespawnData().pos();
			int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					originalSpawn.getX(), originalSpawn.getZ());
			BlockPos stationCenter = new BlockPos(originalSpawn.getX(), surfaceY, originalSpawn.getZ());
			data.allocateStation(stationCenter);
			prepareSafeCenter(level, stationCenter);
			TheFourthFrequency.LOGGER.info("Allocated Relay Station Zero at {}", stationCenter);
		}

		BlockPos stationCenter = data.stationPosition().orElseThrow();
		level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, stationCenter, 180.0F, 0.0F));
		if (!data.stationComplete()) {
			List<ZeroStationLayout.Placement> plan = ZeroStationLayout.create(stationCenter);
			int cursor = Math.min(data.stationBuildCursor(), plan.size());
			ACTIVE_BUILDS.put(server, new BuildState(plan, cursor));
		}
	}

	private static void prepareSafeCenter(ServerLevel level, BlockPos center) {
		level.setBlockAndUpdate(center.below(), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(center, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(center.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
	}

	private static void onServerTick(MinecraftServer server) {
		BuildState build = ACTIVE_BUILDS.get(server);
		if (build == null) {
			return;
		}

		ServerLevel level = server.overworld();
		int end = Math.min(build.cursor + BLOCKS_PER_TICK, build.plan.size());
		for (int index = build.cursor; index < end; index++) {
			ZeroStationLayout.Placement placement = build.plan.get(index);
			level.setBlock(placement.position(), placement.state(), 3);
		}
		build.cursor = end;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		data.advanceStationBuildCursor(build.cursor, build.plan.size());

		if (build.cursor == build.plan.size()) {
			ACTIVE_BUILDS.remove(server);
			TheFourthFrequency.LOGGER.info("Relay Station Zero initialization completed in {} bounded placements",
					build.plan.size());
		}
	}

	public static boolean issueTerminalIfNeeded(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (!data.markTerminalIssued(player.getUUID())) {
			return false;
		}

		ItemStack terminal = TerminalData.stackFromRecord(data.ensureTerminalRecord(player));
		if (!player.addItem(terminal)) {
			player.drop(terminal, false);
		}
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.terminal.dispensed"), true);
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.terminal.stock_zero"), true);
		return true;
	}

	private static void onPlayerJoin(ServerPlayer player) {
		if (!issueTerminalIfNeeded(player)) {
			return;
		}
		FrequencyWorldData.get(player.level().getServer()).stationPosition().ifPresent(position ->
				player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5));
	}

	private static final class BuildState {
		private final List<ZeroStationLayout.Placement> plan;
		private int cursor;

		private BuildState(List<ZeroStationLayout.Placement> plan, int cursor) {
			this.plan = plan;
			this.cursor = cursor;
		}
	}
}
