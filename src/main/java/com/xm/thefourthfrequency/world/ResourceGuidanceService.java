package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.state.PlayerPatternState;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ResourceGuidanceService {
	private static final int MAX_SCAN_RADIUS = 24;
	private static final int BLOCKS_PER_PLAYER_TICK = 1_024;
	private static final int MAX_PLAYERS_PER_SERVER_TICK = 4;
	private static final List<SearchOffset> SEARCH_OFFSETS = createSearchOffsets();
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
		long now = player.level().getGameTime();
		long revealAt = record.getLongOr(TerminalData.MINERAL_SCAN_READY_GAME_TIME, 0L);
		NavigationState navigation = NavigationState.read(record);
		boolean targetValid = navigation.kind().equals(need.id) && navigation.located()
				&& player.level().dimension().identifier().toString().equals(navigation.dimension())
				&& targetStillPresent(player.level(), navigation);
		if (navigation.located() && !targetValid) {
			data.updateTerminalRecord(player.getUUID(), tag -> clearLocatedTarget(tag));
			scans.remove(player.getUUID());
		}

		ScanState scan = scans.get(player.getUUID());
		String dimension = player.level().dimension().identifier().toString();
		if (scan == null || scan.need != need || !scan.dimension.equals(dimension)
				|| scan.origin.distManhattan(player.blockPosition()) > 0) {
			scan = new ScanState(need, player.blockPosition(), dimension, !targetValid);
			scans.put(player.getUUID(), scan);
		}
		if (!scan.finished) scan(player, data, scan, revealAt);
	}

	public static void requestRescan(ServerPlayer player) {
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
		TerminalResource selected = TerminalResource.fromWire(
				record.getIntOr(TerminalData.SELECTED_RESOURCE, TerminalResource.NONE.wireId()));
		NavigationState navigation = NavigationState.read(record);
		TerminalResource navigationResource = TerminalResource.fromId(navigation.kind());
		if (navigationResource != selected) return null;
		return TerminalToolService.resourceAvailable(record, selected) ? ResourceNeed.byId(selected.id()) : null;
	}

	private static void scan(ServerPlayer player, FrequencyWorldData data, ScanState scan, long revealAt) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (scan.found != null) {
			if (now >= revealAt) commit(player, data, scan, now);
			return;
		}
		for (int checked = 0; checked < BLOCKS_PER_PLAYER_TICK; checked++) {
			if (scan.index >= SEARCH_OFFSETS.size()) {
				if (now >= revealAt) {
					scan.finished = true;
					com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
							Component.translatable("message.thefourthfrequency.guidance.not_found"));
					TerminalRuntimeService.refresh(player);
				}
				return;
			}
			SearchOffset offset = SEARCH_OFFSETS.get(scan.index++);
			BlockPos candidate = scan.mutable.setWithOffset(scan.origin, offset.x, offset.y, offset.z).immutable();
			if (level.isOutsideBuildHeight(candidate) || !level.hasChunkAt(candidate)) continue;
			Block block = level.getBlockState(candidate).getBlock();
			if (!scan.need.blocks.contains(block)) continue;
			scan.found = candidate;
			scan.blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
			if (now >= revealAt) commit(player, data, scan, now);
			return;
		}
	}

	private static void commit(ServerPlayer player, FrequencyWorldData data, ScanState scan, long now) {
		BlockPos found = scan.found;
		String blockId = scan.blockId;
		data.updateTerminalRecord(player.getUUID(), record -> {
			if (TerminalResource.fromWire(record.getIntOr(TerminalData.SELECTED_RESOURCE,
					TerminalResource.NONE.wireId())).id().equals(scan.need.id)) {
				NavigationState.read(record).locate(blockId, found, scan.dimension, now).writeTo(record);
			}
		});
		scan.finished = true;
		if (scan.announceReady)
			com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
					Component.translatable("message.thefourthfrequency.guidance.ready"));
		TerminalRuntimeService.refresh(player);
	}

	private static void completeAdvice(ServerPlayer player, FrequencyWorldData data, ResourceNeed need) {
		data.updateTerminalRecord(player.getUUID(), record -> {
			PlayerPatternState pattern = PlayerPatternState.read(record);
			pattern.withAcceptedAdvice(appendEntry(pattern.acceptedAdvice(), need.id)).writeTo(record);
		});
		TerminalLifecycleService.ensureCarried(player, false);
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.guidance.accepted"));
		TerminalRuntimeService.refresh(player);
	}

	private static boolean hasBindingResource(ServerPlayer player, ResourceNeed need) {
		for (Item item : need.bindingItems) {
			if (player.getInventory().contains(item.getDefaultInstance())) {
				return true;
			}
		}
		return false;
	}

	private static List<SearchOffset> createSearchOffsets() {
		List<SearchOffset> offsets = new ArrayList<>();
		int radiusSquared = MAX_SCAN_RADIUS * MAX_SCAN_RADIUS;
		for (int x = -MAX_SCAN_RADIUS; x <= MAX_SCAN_RADIUS; x++) {
			for (int y = -MAX_SCAN_RADIUS; y <= MAX_SCAN_RADIUS; y++) {
				for (int z = -MAX_SCAN_RADIUS; z <= MAX_SCAN_RADIUS; z++) {
					int distanceSquared = x * x + y * y + z * z;
					if (distanceSquared <= radiusSquared) offsets.add(new SearchOffset(x, y, z, distanceSquared));
				}
			}
		}
		offsets.sort(Comparator.comparingInt(SearchOffset::distanceSquared));
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
		IRON("iron", Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE), List.of(Items.RAW_IRON, Items.IRON_INGOT)),
		COAL("coal", Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE), List.of(Items.COAL)),
		GOLD("gold", Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
				List.of(Items.RAW_GOLD, Items.GOLD_INGOT)),
		DIAMOND("diamond", Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE), List.of(Items.DIAMOND));

		private final String id;
		private final Set<Block> blocks;
		private final List<Item> bindingItems;

		ResourceNeed(String id, Set<Block> blocks, List<Item> bindingItems) {
			this.id = id;
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
		private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		private int index;
		private BlockPos found;
		private String blockId = "";
		private boolean finished;
		private final boolean announceReady;

		private ScanState(ResourceNeed need, BlockPos origin, String dimension, boolean announceReady) {
			this.need = need;
			this.origin = origin.immutable();
			this.dimension = dimension;
			this.announceReady = announceReady;
		}
	}

	private record SearchOffset(int x, int y, int z, int distanceSquared) {
	}
}
