package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.content.TerminalData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allocates, sites and builds Relay Station Zero, and hands each player the one terminal they get.
 *
 * <p>Siting happens exactly once and is then persisted, because {@link ZeroStationLayout} derives
 * the whole plan from the centre alone. Everything terrain-dependent therefore has to be decided
 * before the first block is placed: once building starts, the world under the station is no longer
 * evidence about the world before it.</p>
 */
public final class ZeroStationService {
	/** Placements per tick. The full plan lands in roughly a second, well inside the join fade. */
	private static final int BLOCKS_PER_TICK = 64;
	/** How far the station may walk away from the vanilla spawn to find ground worth standing on. */
	private static final int SITE_SEARCH_RADIUS = 12;
	private static final int SITE_SEARCH_STEP = 4;
	/** Weight on flatness relative to distance: a level site is worth a few blocks of walking. */
	private static final int RELIEF_WEIGHT = 8;
	/** Clear of the mast tip, so anything solid at this height above the centre means "underground". */
	private static final int BURIAL_PROBE_HEIGHT = 12;

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
			BlockPos stationCenter = chooseSite(level, level.getRespawnData().pos());
			data.allocateStation(stationCenter);
			prepareSafeCenter(level, stationCenter);
			TheFourthFrequency.LOGGER.info("Allocated Relay Station Zero at {}", stationCenter);
		}

		BlockPos stationCenter = data.stationPosition().orElseThrow();
		// Nothing the station builds reaches this high, so solid ground above the centre means the
		// site is not a surface at all. An absolute Y bound cannot say this: a superflat world's
		// surface sits four blocks off the world floor. Re-siting would move a spawn a player may
		// already have built around, so this only says it out loud - such a save needs a new world.
		if (!level.getBlockState(stationCenter.above(BURIAL_PROBE_HEIGHT)).isAir()) {
			TheFourthFrequency.LOGGER.warn("Relay Station Zero at {} is buried: {} sits above it. "
							+ "This station is unreachable and the save needs to be recreated.",
					stationCenter, level.getBlockState(stationCenter.above(BURIAL_PROBE_HEIGHT)));
		}
		// Yaw 180 faces north, at the equipment wall. Waking up already looking at the empty rack is
		// the only introduction the station gets.
		level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, stationCenter, 180.0F, 0.0F));
		if (!data.stationComplete()) {
			List<ZeroStationLayout.Placement> plan = ZeroStationLayout.create(stationCenter);
			int cursor = Math.min(data.stationBuildCursor(), plan.size());
			ACTIVE_BUILDS.put(server, new BuildState(plan, cursor));
		}
	}

	/**
	 * Picks the station centre near the vanilla spawn: level ground, no liquid in the footprint,
	 * and as little walking from the original spawn as the first two conditions allow.
	 *
	 * <p>The search is deliberately short-ranged. Including the footprint it spans at most a 3x3
	 * block of chunks around spawn, all of which level preparation has already generated - but see
	 * {@link #surfaceHeight} for why "already generated" is not the same as "safe to ask".</p>
	 */
	private static BlockPos chooseSite(ServerLevel level, BlockPos spawn) {
		BlockPos best = null;
		long bestScore = Long.MAX_VALUE;
		for (int dx = -SITE_SEARCH_RADIUS; dx <= SITE_SEARCH_RADIUS; dx += SITE_SEARCH_STEP) {
			for (int dz = -SITE_SEARCH_RADIUS; dz <= SITE_SEARCH_RADIUS; dz += SITE_SEARCH_STEP) {
				int[] heights = sampleFootprint(level, spawn.getX() + dx, spawn.getZ() + dz);
				if (heights == null) {
					continue;
				}
				int relief = heights[heights.length - 1] - heights[0];
				long score = (long) relief * RELIEF_WEIGHT + Math.abs(dx) + Math.abs(dz);
				if (score < bestScore) {
					bestScore = score;
					best = new BlockPos(spawn.getX() + dx, heights[heights.length / 2], spawn.getZ() + dz);
				}
			}
		}
		if (best != null) {
			return best;
		}
		// Every candidate was rejected. A station on bad ground still beats no station, and the
		// foundation courses are deep enough to give the platform something to stand on regardless.
		return new BlockPos(spawn.getX(), surfaceHeight(level, spawn.getX(), spawn.getZ()), spawn.getZ());
	}

	/**
	 * Surface heights across the station footprint, sorted ascending, or {@code null} if any sample
	 * stands in liquid or could not be read. Corners and edge midpoints are enough: the layout has
	 * no interior column that can be supported while a corner is not.
	 */
	private static int[] sampleFootprint(ServerLevel level, int centerX, int centerZ) {
		int[] heights = new int[9];
		int index = 0;
		for (int dx = -ZeroStationLayout.HALF_WIDTH; dx <= ZeroStationLayout.HALF_WIDTH;
				dx += ZeroStationLayout.HALF_WIDTH) {
			for (int dz = -ZeroStationLayout.HALF_DEPTH; dz <= ZeroStationLayout.HALF_DEPTH;
					dz += ZeroStationLayout.HALF_DEPTH) {
				int x = centerX + dx;
				int z = centerZ + dz;
				int height = surfaceHeight(level, x, z);
				if (height <= level.getMinY()
						|| !level.getFluidState(new BlockPos(x, height - 1, z)).isEmpty()) {
					return null;
				}
				heights[index++] = height;
			}
		}
		Arrays.sort(heights);
		return heights;
	}

	/**
	 * The surface height of one column, with its chunk requested first.
	 *
	 * <p>{@link ServerLevel#getHeight} does not load anything: for a chunk that is not currently
	 * loaded it silently answers with the world floor instead of failing. Reading a whole footprint
	 * that way scores a column of bedrock as perfectly level ground, which is how the station once
	 * ended up assembled at Y = -64 inside solid rock.</p>
	 */
	private static int surfaceHeight(ServerLevel level, int x, int z) {
		level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/**
	 * The centre column, made safe before the first batched placement. A player can connect during
	 * the very tick the station is allocated, and they land here.
	 */
	private static void prepareSafeCenter(ServerLevel level, BlockPos center) {
		level.setBlockAndUpdate(center.below(), Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(center.above(), Blocks.AIR.defaultBlockState());
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
			// Most of the plan is the cleared envelope, and most of that is already air. Skipping the
			// write keeps the batch honest: the budget counts placements, not block updates.
			if (level.getBlockState(placement.position()).equals(placement.state())) {
				continue;
			}
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
		// One dispense is one moment; the empty rack was never a separate event worth its own line.
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.terminal.dispensed"));
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
