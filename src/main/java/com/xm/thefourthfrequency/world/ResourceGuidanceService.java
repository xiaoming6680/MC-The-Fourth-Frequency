package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.pursuit.PursuitDimensions;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.state.PlayerPatternState;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ResourceGuidanceService {
	public static final String FAILED_SCAN_KIND = "mineral_scan_failed";
	private static final int INITIAL_SCAN_RADIUS = 24;
	private static final int BLOCKS_PER_PLAYER_TICK = 1_024;
	private static final int MAX_PLAYERS_PER_SERVER_TICK = 4;
	private static final long AUTO_SCAN_RESET_TICKS = 100L;
	private static final List<SearchOffset> INITIAL_SEARCH_OFFSETS = createSearchOffsets(INITIAL_SCAN_RADIUS);
	private static final List<SearchOffset> AUTO_SEARCH_OFFSETS = createSearchOffsets(MineralSurveyPolicy.RANGE);
	private static final Map<MinecraftServer, Map<UUID, ScanState>> ACTIVE_SCANS = new IdentityHashMap<>();
	private static final Map<MinecraftServer, Map<UUID, AutoScanState>> AUTO_SCANS = new IdentityHashMap<>();
	private static final Map<MinecraftServer, Integer> PLAYER_CURSORS = new IdentityHashMap<>();

	private ResourceGuidanceService() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ResourceGuidanceService::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE_SCANS.remove(server);
			AUTO_SCANS.remove(server);
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
		updatePlayer(player, null);
	}

	public static void updatePlayerForTesting(ServerPlayer player, int autoSurveyRoll) {
		updatePlayer(player, Math.clamp(autoSurveyRoll, 0, 99));
	}

	private static void updatePlayer(ServerPlayer player, Integer forcedAutoSurveyRoll) {
		MinecraftServer server = player.level().getServer();
		Map<UUID, ScanState> scans = ACTIVE_SCANS.computeIfAbsent(server, ignored -> new HashMap<>());
		Map<UUID, AutoScanState> autoScans = AUTO_SCANS.computeIfAbsent(server, ignored -> new HashMap<>());
		FrequencyWorldData data = FrequencyWorldData.get(server);
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) {
			scans.remove(player.getUUID());
			autoScans.remove(player.getUUID());
			return;
		}
		boolean autoEligible = autoSurveyEligible(player, record);
		if (!autoEligible) {
			autoScans.remove(player.getUUID());
			clearAutoSurvey(player, data, record);
		}
		if (TerminalToolService.toolsDisabled(record, player.level().getGameTime())) {
			scans.remove(player.getUUID());
			return;
		}
		if (handleFailedManualScan(player, data, record)) {
			scans.remove(player.getUUID());
			return;
		}

		ResourceNeed need = resolveNeed(player, record);
		if (need == null) {
			scans.remove(player.getUUID());
		} else {
			updateManualScan(player, data, record, scans, need);
			CompoundTag currentRecord = data.terminalRecord(player.getUUID()).orElse(record);
			if (completeMineralNavigationIfArrived(player, data, currentRecord, scans, autoScans)) return;
		}

		if (!autoEligible) return;
		boolean hadSurveyState = autoSurveyStatePresent(record);
		if (autoSurveyEpisodeValid(player, data, record)) {
			autoScans.remove(player.getUUID());
			return;
		}
		if (hadSurveyState) autoScans.remove(player.getUUID());
		if (TerminalRuntimeService.isOpen(player)) return;

		ScanState manualScan = scans.get(player.getUUID());
		if (manualScan != null && !manualScan.finished) return;
		updateAutoScan(player, data, autoScans, forcedAutoSurveyRoll);
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

	private static boolean handleFailedManualScan(ServerPlayer player, FrequencyWorldData data,
			CompoundTag record) {
		if (!NavigationState.read(record).kind().equals(FAILED_SCAN_KIND)) return false;
		long readyAt = record.getLongOr(TerminalData.MINERAL_SCAN_READY_GAME_TIME, 0L);
		if (readyAt == 0L) return false;
		if (player.level().getGameTime() < readyAt) return true;
		data.updateTerminalRecord(player.getUUID(), tag ->
				tag.putLong(TerminalData.MINERAL_SCAN_READY_GAME_TIME, 0L));
		TerminalRuntimeService.refresh(player);
		return true;
	}

	private static boolean completeMineralNavigationIfArrived(ServerPlayer player, FrequencyWorldData data,
			CompoundTag record, Map<UUID, ScanState> scans, Map<UUID, AutoScanState> autoScans) {
		if (TerminalToolService.guidanceTool(record) != TerminalTool.MINERALS.slot()) return false;
		NavigationState navigation = NavigationState.read(record);
		TerminalResource resource = TerminalResource.fromId(navigation.kind());
		if (resource == TerminalResource.NONE || !navigation.located()
				|| !navigation.dimension().equals(player.level().dimension().identifier().toString())) return false;
		BlockPos target = BlockPos.of(navigation.position());
		BlockPos origin = player.blockPosition();
		if (!MineralSurveyPolicy.arrived(target.getX() - origin.getX(),
				target.getY() - origin.getY(), target.getZ() - origin.getZ())) return false;
		long now = player.level().getGameTime();
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL, TerminalToolService.NO_TOOL);
			tag.putInt(TerminalData.SELECTED_RESOURCE, TerminalResource.NONE.wireId());
			tag.putLong(TerminalData.MINERAL_SCAN_READY_GAME_TIME, 0L);
			new NavigationState("unresolved", "", false, "", 0L, "", now).writeTo(tag);
			// Suppress immediate rediscovery of the ore that was just reached until the player
			// leaves its automatic-survey episode.
			tag.putBoolean(TerminalData.MINERAL_SURVEY_PROXIMITY, true);
			tag.putBoolean(TerminalData.MINERAL_SURVEY_NEARBY, false);
			tag.putLong(TerminalData.MINERAL_SURVEY_POSITION, target.asLong());
			tag.putString(TerminalData.MINERAL_SURVEY_DIMENSION, navigation.dimension());
		});
		scans.remove(player.getUUID());
		autoScans.remove(player.getUUID());
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.navigation.mineral_arrived"));
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
		return true;
	}

	private static void updateManualScan(ServerPlayer player, FrequencyWorldData data, CompoundTag record,
			Map<UUID, ScanState> scans, ResourceNeed need) {
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
			data.updateTerminalRecord(player.getUUID(), ResourceGuidanceService::clearLocatedTarget);
			scans.remove(player.getUUID());
		}

		ScanState scan = scans.get(player.getUUID());
		String dimension = player.level().dimension().identifier().toString();
		if (scan == null || scan.need != need || !scan.dimension.equals(dimension)
				|| scan.origin.distManhattan(player.blockPosition()) > 0) {
			scan = new ScanState(need, player.blockPosition(), dimension);
			scans.put(player.getUUID(), scan);
		}
		if (!scan.finished) scan(player, data, scan, revealAt);
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
			BlockPos candidate = nextManualCandidate(level, scan);
			if (candidate == null) continue;
			if (level.isOutsideBuildHeight(candidate) || !level.hasChunkAt(candidate)) continue;
			Block block = level.getBlockState(candidate).getBlock();
			if (!scan.need.blocks.contains(block)) continue;
			scan.found = candidate.immutable();
			scan.blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
			if (now >= revealAt) commit(player, data, scan, now);
			return;
		}
	}

	private static BlockPos nextManualCandidate(ServerLevel level, ScanState scan) {
		if (scan.initialIndex < INITIAL_SEARCH_OFFSETS.size()) {
			SearchOffset offset = INITIAL_SEARCH_OFFSETS.get(scan.initialIndex++);
			return scan.mutable.setWithOffset(scan.origin, offset.x, offset.y, offset.z);
		}

		int chunksInRing = scan.chunkRadius == 0 ? 1 : scan.chunkRadius * 8;
		if (scan.chunkIndex >= chunksInRing) {
			scan.chunkRadius++;
			scan.chunkIndex = 0;
			scan.chunkBlockIndex = 0;
		}
		ChunkOffset offset = chunkOffset(scan.chunkRadius, scan.chunkIndex);
		int chunkX = (scan.origin.getX() >> 4) + offset.x;
		int chunkZ = (scan.origin.getZ() >> 4) + offset.z;
		if (scan.chunkBlockIndex == 0) {
			int probeY = Math.clamp(scan.origin.getY(), level.getMinY(), level.getMaxY() - 1);
			scan.mutable.set(chunkX << 4, probeY, chunkZ << 4);
			if (!level.hasChunkAt(scan.mutable)) {
				advanceChunk(scan);
				return null;
			}
		}

		int height = level.getHeight();
		int blockIndex = scan.chunkBlockIndex++;
		int y = level.getMinY() + blockIndex % height;
		int column = blockIndex / height;
		int x = (chunkX << 4) + (column & 15);
		int z = (chunkZ << 4) + (column >>> 4);
		if (scan.chunkBlockIndex >= 16 * 16 * height) advanceChunk(scan);
		return scan.mutable.set(x, y, z);
	}

	private static void advanceChunk(ScanState scan) {
		scan.chunkIndex++;
		scan.chunkBlockIndex = 0;
	}

	private static ChunkOffset chunkOffset(int radius, int index) {
		if (radius == 0) return new ChunkOffset(0, 0);
		int side = radius * 2;
		int topLength = side + 1;
		if (index < topLength) return new ChunkOffset(-radius + index, -radius);
		index -= topLength;
		if (index < side) return new ChunkOffset(radius, -radius + 1 + index);
		index -= side;
		if (index < side) return new ChunkOffset(radius - 1 - index, radius);
		index -= side;
		return new ChunkOffset(-radius, radius - 1 - index);
	}

	private static boolean autoSurveyEligible(ServerPlayer player, CompoundTag record) {
		return player.isAlive() && !player.isSpectator() && !PursuitDimensions.isMirror(player.level())
				&& !TerminalToolService.toolsDisabled(record, player.level().getGameTime())
				&& TerminalToolService.guidanceTool(record) != TerminalTool.MINERALS.slot()
				&& (TerminalToolService.availableToolsMask(player, record) & 1 << TerminalTool.MINERALS.slot()) != 0;
	}

	private static boolean autoSurveyStatePresent(CompoundTag record) {
		return record.getBooleanOr(TerminalData.MINERAL_SURVEY_PROXIMITY, false)
				|| record.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false)
				|| record.getLongOr(TerminalData.MINERAL_SURVEY_POSITION, 0L) != 0L
				|| !record.getStringOr(TerminalData.MINERAL_SURVEY_DIMENSION, "").isBlank();
	}

	private static boolean autoSurveyEpisodeValid(ServerPlayer player, FrequencyWorldData data, CompoundTag record) {
		if (!record.getBooleanOr(TerminalData.MINERAL_SURVEY_PROXIMITY, false)) {
			if (autoSurveyStatePresent(record)) clearAutoSurvey(player, data, record);
			return false;
		}
		String currentDimension = player.level().dimension().identifier().toString();
		String surveyDimension = record.getStringOr(TerminalData.MINERAL_SURVEY_DIMENSION, "");
		BlockPos target = BlockPos.of(record.getLongOr(TerminalData.MINERAL_SURVEY_POSITION, 0L));
		BlockPos origin = player.blockPosition();
		boolean valid = currentDimension.equals(surveyDimension)
				&& MineralSurveyPolicy.withinRange(
						target.getX() - origin.getX(), target.getY() - origin.getY(), target.getZ() - origin.getZ())
				&& surveyTargetStillPresent(player.level(), target);
		if (!valid) clearAutoSurvey(player, data, record);
		return valid;
	}

	private static boolean surveyTargetStillPresent(ServerLevel level, BlockPos target) {
		if (!level.hasChunkAt(target)) return true;
		return isSurveyBlock(level.getBlockState(target).getBlock());
	}

	public static SurveyTarget automaticSurveyTarget(ServerPlayer player, CompoundTag record) {
		if (!record.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false)) return SurveyTarget.unavailable();
		String dimension = record.getStringOr(TerminalData.MINERAL_SURVEY_DIMENSION, "");
		if (!dimension.equals(player.level().dimension().identifier().toString())) return SurveyTarget.unavailable();
		BlockPos position = BlockPos.of(record.getLongOr(TerminalData.MINERAL_SURVEY_POSITION, 0L));
		BlockPos origin = player.blockPosition();
		if (!MineralSurveyPolicy.withinRange(position.getX() - origin.getX(),
				position.getY() - origin.getY(), position.getZ() - origin.getZ())
				|| !player.level().hasChunkAt(position)) return SurveyTarget.unavailable();
		Block block = player.level().getBlockState(position).getBlock();
		TerminalResource resource = surveyResource(block);
		if (resource == TerminalResource.NONE) return SurveyTarget.unavailable();
		return new SurveyTarget(true, resource, position,
				BuiltInRegistries.BLOCK.getKey(block).toString(), dimension);
	}

	private static void clearAutoSurvey(ServerPlayer player, FrequencyWorldData data, CompoundTag record) {
		if (!autoSurveyStatePresent(record)) return;
		boolean visible = record.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false);
		data.updateTerminalRecord(player.getUUID(), ResourceGuidanceService::clearAutomaticSurveyState);
		if (visible) TerminalRuntimeService.refresh(player);
	}

	public static void clearAutomaticSurveyState(CompoundTag record) {
		record.putBoolean(TerminalData.MINERAL_SURVEY_PROXIMITY, false);
		record.putBoolean(TerminalData.MINERAL_SURVEY_NEARBY, false);
		record.putLong(TerminalData.MINERAL_SURVEY_POSITION, 0L);
		record.putString(TerminalData.MINERAL_SURVEY_DIMENSION, "");
	}

	private static void updateAutoScan(ServerPlayer player, FrequencyWorldData data,
			Map<UUID, AutoScanState> autoScans, Integer forcedRoll) {
		long now = player.level().getGameTime();
		String dimension = player.level().dimension().identifier().toString();
		AutoScanState scan = autoScans.get(player.getUUID());
		if (scan == null || !scan.dimension.equals(dimension)
				|| scan.origin.distManhattan(player.blockPosition()) > 4
				|| scan.finished && now - scan.finishedAt >= AUTO_SCAN_RESET_TICKS) {
			scan = new AutoScanState(player.blockPosition(), dimension);
			autoScans.put(player.getUUID(), scan);
		}
		if (scan.finished) return;
		scanAuto(player, data, autoScans, scan, forcedRoll);
	}

	private static void scanAuto(ServerPlayer player, FrequencyWorldData data,
			Map<UUID, AutoScanState> autoScans, AutoScanState scan, Integer forcedRoll) {
		ServerLevel level = player.level();
		int checked = 0;
		while (checked++ < BLOCKS_PER_PLAYER_TICK) {
			if (scan.index >= AUTO_SEARCH_OFFSETS.size()) {
				scan.finished = true;
				scan.finishedAt = level.getGameTime();
				return;
			}
			SearchOffset offset = AUTO_SEARCH_OFFSETS.get(scan.index++);
			BlockPos candidate = scan.mutable.setWithOffset(scan.origin, offset.x, offset.y, offset.z).immutable();
			if (level.isOutsideBuildHeight(candidate) || !level.hasChunkAt(candidate)
					|| !MineralSurveyPolicy.withinRange(
							candidate.getX() - player.getBlockX(),
							candidate.getY() - player.getBlockY(),
							candidate.getZ() - player.getBlockZ())) continue;
			if (!isSurveyBlock(level.getBlockState(candidate).getBlock())) continue;
			int roll = forcedRoll != null ? forcedRoll : player.getRandom().nextInt(100);
			boolean nearby = MineralSurveyPolicy.shouldReveal(roll);
			data.updateTerminalRecord(player.getUUID(), record -> {
				record.putBoolean(TerminalData.MINERAL_SURVEY_PROXIMITY, true);
				record.putBoolean(TerminalData.MINERAL_SURVEY_NEARBY, nearby);
				record.putLong(TerminalData.MINERAL_SURVEY_POSITION, candidate.asLong());
				record.putString(TerminalData.MINERAL_SURVEY_DIMENSION, scan.dimension);
			});
			autoScans.remove(player.getUUID());
			if (nearby) {
				com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
						Component.translatable("message.thefourthfrequency.guidance.nearby"));
				TerminalRuntimeService.refresh(player);
			}
			return;
		}
	}

	private static boolean isSurveyBlock(Block block) {
		return surveyResource(block) != TerminalResource.NONE;
	}

	private static TerminalResource surveyResource(Block block) {
		for (ResourceNeed need : ResourceNeed.values()) {
			if (need.blocks.contains(block)) return TerminalResource.fromId(need.id);
		}
		return TerminalResource.NONE;
	}

	private static void commit(ServerPlayer player, FrequencyWorldData data, ScanState scan, long now) {
		BlockPos found = scan.found;
		String blockId = scan.blockId;
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putLong(TerminalData.MINERAL_SCAN_READY_GAME_TIME, 0L);
			if (TerminalResource.fromWire(record.getIntOr(TerminalData.SELECTED_RESOURCE,
					TerminalResource.NONE.wireId())).id().equals(scan.need.id)) {
				NavigationState.read(record).locate(blockId, found, scan.dimension, now).writeTo(record);
			}
		});
		scan.finished = true;
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

	private static List<SearchOffset> createSearchOffsets(int radius) {
		List<SearchOffset> offsets = new ArrayList<>();
		int radiusSquared = radius * radius;
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
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
		private int initialIndex;
		private int chunkRadius;
		private int chunkIndex;
		private int chunkBlockIndex;
		private BlockPos found;
		private String blockId = "";
		private boolean finished;

		private ScanState(ResourceNeed need, BlockPos origin, String dimension) {
			this.need = need;
			this.origin = origin.immutable();
			this.dimension = dimension;
		}
	}

	private static final class AutoScanState {
		private final BlockPos origin;
		private final String dimension;
		private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
		private int index;
		private boolean finished;
		private long finishedAt;

		private AutoScanState(BlockPos origin, String dimension) {
			this.origin = origin.immutable();
			this.dimension = dimension;
		}
	}

	private record SearchOffset(int x, int y, int z, int distanceSquared) {
	}

	private record ChunkOffset(int x, int z) {
	}

	public record SurveyTarget(boolean located, TerminalResource resource, BlockPos position,
			String blockId, String dimension) {
		private static SurveyTarget unavailable() {
			return new SurveyTarget(false, TerminalResource.NONE, BlockPos.ZERO, "", "");
		}
	}
}
