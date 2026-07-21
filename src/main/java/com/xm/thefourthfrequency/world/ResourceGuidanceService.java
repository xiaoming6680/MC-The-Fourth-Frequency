package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.state.PlayerPatternState;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ResourceGuidanceService {
	private static final int MAX_SCAN_RADIUS = 48;
	private static final int BLOCKS_PER_PLAYER_TICK = 1_024;
	private static final int MAX_PLAYERS_PER_SERVER_TICK = 4;
	private static final List<HorizontalOffset> SEARCH_OFFSETS = createSearchOffsets();
	private static final Map<MinecraftServer, Map<UUID, ScanState>> ACTIVE_SCANS = new IdentityHashMap<>();
	private static final Map<MinecraftServer, Integer> PLAYER_CURSORS = new IdentityHashMap<>();

	private ResourceGuidanceService() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ResourceGuidanceService::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE_SCANS.remove(server);
			PLAYER_CURSORS.remove(server);
		});
	}

	private static void onServerTick(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) return;
		int start = Math.floorMod(PLAYER_CURSORS.getOrDefault(server, 0), players.size());
		int count = Math.min(MAX_PLAYERS_PER_SERVER_TICK, players.size());
		for (int offset = 0; offset < count; offset++) {
			updatePlayer(players.get((start + offset) % players.size()));
		}
		PLAYER_CURSORS.put(server, (start + count) % players.size());
	}

	public static int maximumBlockChecksPerServerTick() {
		return BLOCKS_PER_PLAYER_TICK * MAX_PLAYERS_PER_SERVER_TICK;
	}

	public static void updatePlayer(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Map<UUID, ScanState> scans = ACTIVE_SCANS.computeIfAbsent(server, ignored -> new HashMap<>());
		FrequencyWorldData data = FrequencyWorldData.get(server);
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || TerminalToolService.toolsDisabled(record, player.level().getGameTime())) {
			scans.remove(player.getUUID());
			return;
		}
		ResourceNeed need = resolveNeed(player, record);
		if (need == null) {
			scans.remove(player.getUUID());
			return;
		}
		if (hasBindingResource(player, need)
				&& !containsEntry(PlayerPatternState.read(record).acceptedAdvice(), need.id)) {
			completeAdvice(player, data, need);
		}
		NavigationState navigation = NavigationState.read(record);
		if (navigation.located()) {
			boolean sameDimension = player.level().dimension().identifier().toString()
					.equals(navigation.dimension());
			if (!sameDimension || targetStillPresent(player.level(), navigation)) return;
			data.updateTerminalRecord(player.getUUID(), tag -> clearLocatedTarget(tag));
		}

		ScanState scan = scans.get(player.getUUID());
		if (scan == null || scan.need != need || !scan.dimension.equals(player.level().dimension().identifier().toString())
				|| scan.origin.distManhattan(player.blockPosition()) > 24) {
			scan = new ScanState(need, player.blockPosition(), player.level().dimension().identifier().toString());
			scans.put(player.getUUID(), scan);
		}
		if (scan(player, data, scan)) {
			scans.remove(player.getUUID());
		}
	}

	public static void requestRescan(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isPresent()) {
			data.updateTerminalRecord(player.getUUID(), record ->
					record.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL, TerminalTool.MINERALS.slot()));
		}
		restartScan(player, true);
	}

	public static void restartScan(ServerPlayer player, boolean clearTarget) {
		MinecraftServer server = player.level().getServer();
		ACTIVE_SCANS.computeIfAbsent(server, ignored -> new HashMap<>()).remove(player.getUUID());
		if (!clearTarget) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (data.terminalRecord(player.getUUID()).isPresent())
			data.updateTerminalRecord(player.getUUID(), ResourceGuidanceService::clearLocatedTarget);
	}

	private static boolean targetStillPresent(ServerLevel level, NavigationState navigation) {
		BlockPos target = BlockPos.of(navigation.position());
		if (!level.hasChunkAt(target)) return true;
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString()
				.equals(navigation.blockId());
	}

	private static void clearLocatedTarget(CompoundTag record) {
		NavigationState.read(record).clearLocation().writeTo(record);
	}

	private static ResourceNeed resolveNeed(ServerPlayer player, CompoundTag record) {
		NavigationState navigation = NavigationState.read(record);
		TerminalResource selected = TerminalResource.fromId(navigation.kind());
		if (selected == TerminalResource.NONE) selected = TerminalResource.fromWire(
				record.getIntOr(TerminalData.SELECTED_RESOURCE, TerminalResource.NONE.wireId()));
		return TerminalToolService.resourceAvailable(record, selected) ? ResourceNeed.byId(selected.id()) : null;
	}

	private static boolean scan(ServerPlayer player, FrequencyWorldData data, ScanState scan) {
		ServerLevel level = player.level();
		for (int checked = 0; checked < BLOCKS_PER_PLAYER_TICK; checked++) {
			if (scan.horizontalIndex >= SEARCH_OFFSETS.size()) {
				return true;
			}
			HorizontalOffset offset = SEARCH_OFFSETS.get(scan.horizontalIndex);
			BlockPos candidate = new BlockPos(scan.origin.getX() + offset.x, scan.y, scan.origin.getZ() + offset.z);
			scan.advance();
			if (!level.hasChunkAt(candidate)) {
				continue;
			}
			Block block = level.getBlockState(candidate).getBlock();
			if (!scan.need.blocks.contains(block)) {
				continue;
			}
			String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
			data.updateTerminalRecord(player.getUUID(), record -> {
				NavigationState.read(record).locate(blockId, candidate, scan.dimension, level.getGameTime()).writeTo(record);
			});
			player.displayClientMessage(Component.translatable("message.thefourthfrequency.guidance.ready"), true);
			com.xm.thefourthfrequency.terminal.TerminalSignalService.record(player,
					com.xm.thefourthfrequency.terminal.SignalBand.MINING, "resource_target_located",
					scan.need.ordinal(), 1, true);
			return true;
		}
		return false;
	}

	private static void completeAdvice(ServerPlayer player, FrequencyWorldData data, ResourceNeed need) {
		data.updateTerminalRecord(player.getUUID(), record -> {
			PlayerPatternState pattern = PlayerPatternState.read(record);
			pattern.withAcceptedAdvice(appendEntry(pattern.acceptedAdvice(), need.id)).writeTo(record);
		});
		TerminalLifecycleService.ensureCarried(player, false);
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.guidance.accepted"), true);
		com.xm.thefourthfrequency.terminal.TerminalSignalService.record(player,
				com.xm.thefourthfrequency.terminal.SignalBand.MINING, "resource_advice_accepted",
				need.ordinal(), 1, true);
	}

	private static boolean hasBindingResource(ServerPlayer player, ResourceNeed need) {
		for (Item item : need.bindingItems) {
			if (player.getInventory().contains(item.getDefaultInstance())) {
				return true;
			}
		}
		return false;
	}

	private static List<HorizontalOffset> createSearchOffsets() {
		List<HorizontalOffset> offsets = new ArrayList<>();
		for (int radius = 0; radius <= MAX_SCAN_RADIUS; radius++) {
			if (radius == 0) {
				offsets.add(new HorizontalOffset(0, 0));
				continue;
			}
			for (int x = -radius; x <= radius; x++) {
				offsets.add(new HorizontalOffset(x, -radius));
				offsets.add(new HorizontalOffset(x, radius));
			}
			for (int z = -radius + 1; z < radius; z++) {
				offsets.add(new HorizontalOffset(-radius, z));
				offsets.add(new HorizontalOffset(radius, z));
			}
		}
		return List.copyOf(offsets);
	}

	private static String appendEntry(String entries, String value) {
		if (entries.isBlank()) {
			return value;
		}
		for (String entry : entries.split(";")) {
			if (entry.equals(value)) {
				return entries;
			}
		}
		return entries + ";" + value;
	}

	private static boolean containsEntry(String entries, String value) {
		for (String entry : entries.split(";")) if (entry.equals(value)) return true;
		return false;
	}

	private enum ResourceNeed {
		IRON("iron", -64, 64, Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE), List.of(Items.RAW_IRON, Items.IRON_INGOT)),
		REDSTONE("redstone", -64, 16, Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE), List.of(Items.REDSTONE)),
		DIAMOND("diamond", -64, 16, Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE), List.of(Items.DIAMOND));

		private final String id;
		private final int minY;
		private final int maxY;
		private final Set<Block> blocks;
		private final List<Item> bindingItems;

		ResourceNeed(String id, int minY, int maxY, Set<Block> blocks, List<Item> bindingItems) {
			this.id = id;
			this.minY = minY;
			this.maxY = maxY;
			this.blocks = blocks;
			this.bindingItems = bindingItems;
		}

		private static ResourceNeed byId(String id) {
			for (ResourceNeed need : values()) {
				if (need.id.equals(id)) {
					return need;
				}
			}
			return null;
		}
	}

	private static final class ScanState {
		private final ResourceNeed need;
		private final BlockPos origin;
		private final String dimension;
		private int horizontalIndex;
		private int y;

		private ScanState(ResourceNeed need, BlockPos origin, String dimension) {
			this.need = need;
			this.origin = origin.immutable();
			this.dimension = dimension;
			this.y = need.minY;
		}

		private void advance() {
			y++;
			if (y > need.maxY) {
				y = need.minY;
				horizontalIndex++;
			}
		}
	}

	private record HorizontalOffset(int x, int z) {
	}
}
