package com.xm.thefourthfrequency.facility;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.narrative.WitnessArchive;
import com.xm.thefourthfrequency.networking.ArchivePasswordResultPayload;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Optional;

public final class FacilityService {
	private static final String FACILITIES = "facilities";
	private static final String TEMPLATE_VARIANT = "template_variant";
	private static final String ARCHIVE_UNLOCKED = "archive_unlocked";
	private static final String RIFT_ENTRANCE = "rift_entrance";
	private static final String RIFT_CORE = "rift_core";
	private static final String RIFT_DEPTH = "rift_depth";
	private static final String RIFT_CURSOR = "rift_cursor";
	private static final String RIFT_COMPLETE = "rift_complete";
	private static final int BUILD_BUDGET_PER_TICK = 32;
	private static final List<FacilityDefinition> DEFINITIONS = FacilityDefinitionLoader.loadBuiltIn();

	private FacilityService() {
	}

	public static void initialize() {
		WitnessArchive.get();
		ServerTickEvents.END_SERVER_TICK.register(FacilityService::updateServer);
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			BlockPos position = hitResult.getBlockPos();
			var block = level.getBlockState(position).getBlock();
			if (ModBlocks.evidenceFor(block).isPresent()) {
				return recordEvidence(serverPlayer, position) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			if (block == ModBlocks.ARCHIVE_LOCK) {
				serverPlayer.displayClientMessage(
						Component.translatable("message.thefourthfrequency.archive.authentication_moved"), true);
				return InteractionResult.SUCCESS;
			}
			if (block == ModBlocks.RULE_FRACTURE_CORE) {
				return observeRift(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	public static void updateServer(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (data.stationPosition().isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		CompoundTag narrative = data.narrativeState();
		ListTag states = narrative.getListOrEmpty(FACILITIES);
		boolean changed = false;
		int budget = BUILD_BUDGET_PER_TICK;

		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			FacilityDefinition definition = definition(state.getStringOr("id", ""));
			int variant = state.contains(TEMPLATE_VARIANT)
					? Math.floorMod(state.getIntOr(TEMPLATE_VARIANT, 0), definition.templates().size())
					: FacilityLayout.stableVariant(data.worldId(), definition);
			if (!state.contains(TEMPLATE_VARIANT)) {
				state.putInt(TEMPLATE_VARIANT, variant);
				changed = true;
			}
			int x = state.getIntOr("x", 0);
			int z = state.getIntOr("z", 0);
			BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
			boolean playerNear = server.getPlayerList().getPlayers().stream()
					.anyMatch(player -> player.level() == level && horizontalDistanceSquared(player.blockPosition(), probe) <= 112 * 112);
			if (!state.getBooleanOr("y_resolved", false) && playerNear && level.hasChunkAt(probe)) {
				int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				int y = definition.yMode().equals("underground")
						? Math.max(level.getMinY() + 12, surface - 28) : surface;
				state.putInt("y", y);
				state.putBoolean("y_resolved", true);
				changed = true;
			}

			if (state.getBooleanOr("y_resolved", false) && !state.getBooleanOr("complete", false) && playerNear && budget > 0) {
				BlockPos origin = new BlockPos(x, state.getIntOr("y", level.getSeaLevel()), z);
				int clueDigit = clueDigit(data, definition.clueIndex());
				List<FacilityLayout.Placement> plan = FacilityLayout.create(server, definition, origin, clueDigit, variant);
				int cursor = Math.clamp(state.getIntOr("cursor", 0), 0, plan.size());
				while (cursor < plan.size() && budget > 0) {
					FacilityLayout.Placement placement = plan.get(cursor);
					if (!level.hasChunkAt(placement.position())) {
						break;
					}
					level.setBlock(placement.position(), placement.state(), 3);
					cursor++;
					budget--;
				}
				state.putInt("cursor", cursor);
				if (cursor == plan.size()) {
					state.putBoolean("complete", true);
				}
				changed = true;
			}

			if (state.getBooleanOr("complete", false)) {
				BlockPos origin = new BlockPos(x, state.getIntOr("y", level.getSeaLevel()), z);
				boolean inside = server.getPlayerList().getPlayers().stream()
						.anyMatch(player -> player.level() == level && player.distanceToSqr(origin.getCenter()) <= 14 * 14);
				boolean nearby = server.getPlayerList().getPlayers().stream()
						.anyMatch(player -> player.level() == level && player.distanceToSqr(origin.getCenter()) <= 48 * 48);
				if (inside && !state.getBooleanOr("discovered", false)) {
					state.putBoolean("discovered", true);
					changed = true;
				} else if (!nearby && state.getBooleanOr("discovered", false)
						&& !state.getBooleanOr("left_after_discovery", false)) {
					state.putBoolean("left_after_discovery", true);
					changed = true;
				} else if (inside && state.getBooleanOr("left_after_discovery", false)
						&& !state.getBooleanOr("revisited", false)) {
					state.putBoolean("revisited", true);
					state.putBoolean("changed_after_departure", true);
					level.setBlock(origin.offset(definition.width() / 2, 2, 0),
							net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					changed = true;
				}
			}
			states.set(index, state);
		}

		if (changed) {
			ListTag storedStates = (ListTag) states.copy();
			data.updateNarrativeState(tag -> tag.put(FACILITIES, storedStates));
		}
		if (data.narrativeState().getBooleanOr(ARCHIVE_UNLOCKED, false)) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				synchronizeArchiveAccess(player, data);
			}
			buildRift(level, data);
		}
	}

	public static boolean recordEvidence(ServerPlayer player, BlockPos markerPosition) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) {
			return false;
		}
		for (FacilityDefinition definition : DEFINITIONS) {
			if (definition.clueIndex() < 0) {
				continue;
			}
			Optional<CompoundTag> stateOptional = facilityState(data, definition.id());
			if (stateOptional.isEmpty()) {
				continue;
			}
			CompoundTag state = stateOptional.get();
			if (!state.getBooleanOr("complete", false)) {
				continue;
			}
			BlockPos origin = stateOrigin(state);
			int digit = clueDigit(data, definition.clueIndex());
			if (!FacilityLayout.markerPosition(definition, origin, digit).equals(markerPosition)) {
				continue;
			}
			String evidence = definition.category().equals("mine_station")
					? definition.id() + "=" + (digit + 1)
					: definition.id() + "=" + direction(digit);
			data.updateTerminalRecord(player.getUUID(), record -> {
				record.putString(TerminalData.FACILITY_EVIDENCE,
						appendEntry(record.getStringOr(TerminalData.FACILITY_EVIDENCE, ""), evidence));
				record.putInt(TerminalData.PLOT_STAGE, Math.max(3, record.getIntOr(TerminalData.PLOT_STAGE, 1)));
			});
			TerminalLifecycleService.ensureCarried(player, false);
			player.displayClientMessage(Component.translatable("message.thefourthfrequency.facility.evidence_recorded"), true);
			TerminalSignalService.record(player, SignalBand.PUBLIC, "facility_evidence_recorded",
					definition.clueIndex(), 1, true);
			return true;
		}
		return false;
	}

	public static boolean tryUnlockArchive(ServerPlayer player, String code) {
		return tryUnlockArchiveResult(player, code) == ArchivePasswordResultPayload.SUCCESS;
	}

	/** New mainline entry: four shared fragments replace the legacy facility password. */
	public static void unlockArchiveFromFragments(ServerPlayer anchorPlayer) {
		FrequencyWorldData data = FrequencyWorldData.get(anchorPlayer.level().getServer());
		CompoundTag narrative = data.narrativeState();
		if (!narrative.getBooleanOr(ARCHIVE_UNLOCKED, false)) {
			BlockPos entrance = anchorPlayer.blockPosition().offset(24, 0, 0);
			int availableDepth = entrance.getY() - anchorPlayer.level().getServer().overworld().getMinY() - 2;
			int depth = Math.clamp(availableDepth, 2, 10);
			BlockPos core = RiftLayout.corePosition(entrance, depth);
			data.updateNarrativeState(tag -> {
				tag.putBoolean(ARCHIVE_UNLOCKED, true);
				tag.putLong(RIFT_ENTRANCE, entrance.asLong());
				tag.putLong(RIFT_CORE, core.asLong());
				tag.putInt(RIFT_DEPTH, depth);
				tag.putInt(RIFT_CURSOR, 0);
				tag.putBoolean(RIFT_COMPLETE, false);
			});
		}
		for (ServerPlayer player : anchorPlayer.level().getServer().getPlayerList().getPlayers()) {
			synchronizeArchiveAccess(player, data);
			TerminalLifecycleService.ensureCarried(player, false);
		}
	}

	public static int tryUnlockArchiveResult(ServerPlayer player, String code) {
		if (code == null || !code.matches("[0-3]{4}")) {
			rejectUnlock(player, "message.thefourthfrequency.archive.invalid_format");
			return ArchivePasswordResultPayload.INVALID;
		}
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag terminal = data.terminalRecord(player.getUUID()).orElse(null);
		Optional<BlockPos> lock = facilityPosition(data, "transport_node");
		if (terminal == null || !terminal.getBooleanOr(TerminalData.BOUND, false)
				|| lock.isEmpty()) {
			rejectUnlock(player, "message.thefourthfrequency.archive.out_of_range");
			return ArchivePasswordResultPayload.UNAVAILABLE;
		}
		if (!hasAllEvidence(terminal.getStringOr(TerminalData.FACILITY_EVIDENCE, ""))) {
			rejectUnlock(player, "message.thefourthfrequency.archive.evidence_incomplete");
			return ArchivePasswordResultPayload.INCOMPLETE;
		}
		if (!passwordFor(data).equals(code)) {
			rejectUnlock(player, "message.thefourthfrequency.archive.wrong_code");
			return ArchivePasswordResultPayload.WRONG;
		}

		CompoundTag narrativeBeforeUnlock = data.narrativeState();
		BlockPos entrance = narrativeBeforeUnlock.contains(RIFT_ENTRANCE)
				? BlockPos.of(narrativeBeforeUnlock.getLongOr(RIFT_ENTRANCE, 0L))
				: lock.get().offset(0, 0, 7);
		int availableDepth = entrance.getY() - player.level().getServer().overworld().getMinY() - 2;
		int depth = narrativeBeforeUnlock.contains(RIFT_DEPTH)
				? Math.clamp(narrativeBeforeUnlock.getIntOr(RIFT_DEPTH, 2), 2, 10)
				: Math.clamp(availableDepth, 2, 10);
		BlockPos core = RiftLayout.corePosition(entrance, depth);
		boolean migrateIncompleteRift = !narrativeBeforeUnlock.contains(RIFT_CORE)
				|| !narrativeBeforeUnlock.contains(RIFT_DEPTH);
		data.updateNarrativeState(tag -> {
			tag.putBoolean(ARCHIVE_UNLOCKED, true);
			tag.putLong(RIFT_ENTRANCE, entrance.asLong());
			tag.putLong(RIFT_CORE, core.asLong());
			tag.putInt(RIFT_DEPTH, depth);
			if (!tag.contains(RIFT_CURSOR) || migrateIncompleteRift) {
				tag.putInt(RIFT_CURSOR, 0);
				tag.putBoolean(RIFT_COMPLETE, false);
			}
		});
		synchronizeArchiveAccess(player, data);
		TerminalLifecycleService.ensureCarried(player, false);
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.archive.unlocked"), true);
		return ArchivePasswordResultPayload.SUCCESS;
	}

	public static String passwordFor(FrequencyWorldData data) {
		int hash = data.worldId().hashCode();
		StringBuilder password = new StringBuilder(4);
		for (int index = 0; index < 4; index++) {
			password.append((hash >>> (index * 2)) & 3);
		}
		return password.toString();
	}

	public static List<FacilityDefinition> definitions() {
		return DEFINITIONS;
	}

	public static Optional<CompoundTag> facilityState(FrequencyWorldData data, String id) {
		ListTag states = data.narrativeState().getListOrEmpty(FACILITIES);
		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			if (id.equals(state.getStringOr("id", ""))) {
				return Optional.of(state.copy());
			}
		}
		return Optional.empty();
	}

	public static Optional<BlockPos> facilityPosition(FrequencyWorldData data, String id) {
		return facilityState(data, id).filter(state -> state.getBooleanOr("y_resolved", false))
				.map(FacilityService::stateOrigin);
	}

	public static Optional<BlockPos> completedFacilityPosition(FrequencyWorldData data, String id) {
		return facilityState(data, id)
				.filter(state -> state.getBooleanOr("y_resolved", false) && state.getBooleanOr("complete", false))
				.map(FacilityService::stateOrigin);
	}

	public static boolean debugRebuild(FrequencyWorldData data, String id) {
		if (!id.equals("all")) definition(id);
		ListTag states = data.narrativeState().getListOrEmpty(FACILITIES).copy();
		boolean changed = false;
		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			if (!id.equals("all") && !id.equals(state.getStringOr("id", ""))) continue;
			state.putInt("cursor", 0);
			state.putBoolean("complete", false);
			state.putBoolean("discovered", false);
			state.putBoolean("left_after_discovery", false);
			state.putBoolean("revisited", false);
			states.set(index, state);
			changed = true;
		}
		if (changed) data.updateNarrativeState(tag -> tag.put(FACILITIES, states));
		return changed;
	}

	private static void ensureAllocated(FrequencyWorldData data) {
		if (data.narrativeState().contains(FACILITIES)) {
			return;
		}
		BlockPos station = data.stationPosition().orElseThrow();
		ListTag states = new ListTag();
		for (FacilityDefinition definition : DEFINITIONS) {
			CompoundTag state = new CompoundTag();
			state.putString("id", definition.id());
			state.putInt("x", station.getX() + definition.offsetX());
			state.putInt("z", station.getZ() + definition.offsetZ());
			state.putBoolean("y_resolved", false);
			state.putInt("cursor", 0);
			state.putBoolean("complete", false);
			state.putBoolean("discovered", false);
			state.putBoolean("left_after_discovery", false);
			state.putBoolean("revisited", false);
			state.putInt(TEMPLATE_VARIANT, FacilityLayout.stableVariant(data.worldId(), definition));
			states.add(state);
		}
		data.updateNarrativeState(tag -> tag.put(FACILITIES, states));
	}

	private static void synchronizeArchiveAccess(ServerPlayer player, FrequencyWorldData data) {
		if (data.terminalRecord(player.getUUID()).isEmpty()) {
			return;
		}
		CompoundTag narrative = data.narrativeState();
		if (!narrative.getBooleanOr(ARCHIVE_UNLOCKED, false) || !narrative.contains(RIFT_CORE)) {
			return;
		}
		BlockPos core = BlockPos.of(narrative.getLongOr(RIFT_CORE, 0L));
		WitnessArchive archive = WitnessArchive.get();
		boolean firstUnlock = !data.terminalRecord(player.getUUID()).orElseThrow()
				.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false);
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
			TerminalFileState.discover(record, "encrypted_witness_file", now, dayTime, true);
		});
		if (firstUnlock) TerminalSignalService.record(player, SignalBand.UNKNOWN, "witness_file_unlocked", 0, 2, true);
	}

	private static void buildRift(ServerLevel level, FrequencyWorldData data) {
		CompoundTag narrative = data.narrativeState();
		if (!narrative.contains(RIFT_ENTRANCE) || narrative.getBooleanOr(RIFT_COMPLETE, false)) {
			return;
		}
		BlockPos entrance = BlockPos.of(narrative.getLongOr(RIFT_ENTRANCE, 0L));
		if (!level.hasChunkAt(entrance)) {
			return;
		}
		int depth = Math.clamp(narrative.getIntOr(RIFT_DEPTH, 2), 2, 10);
		List<FacilityLayout.Placement> plan = RiftLayout.create(entrance, depth);
		int cursor = Math.clamp(narrative.getIntOr(RIFT_CURSOR, 0), 0, plan.size());
		int end = Math.min(plan.size(), cursor + BUILD_BUDGET_PER_TICK);
		for (int index = cursor; index < end; index++) {
			FacilityLayout.Placement placement = plan.get(index);
			if (!level.isInWorldBounds(placement.position()) || !level.hasChunkAt(placement.position())) {
				break;
			}
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
		if (record == null || !record.getBooleanOr(TerminalData.RIFT_LOCATED, false)) {
			return false;
		}
		boolean firstObservation = !record.getBooleanOr(TerminalData.RIFT_OBSERVED, false);
		data.updateTerminalRecord(player.getUUID(), tag -> tag.putBoolean(TerminalData.RIFT_OBSERVED, true));
		if (firstObservation) TerminalSignalService.record(player, SignalBand.UNKNOWN,
				"overworld_fracture_observed", 0, 2, true);
		TerminalLifecycleService.ensureCarried(player, false);
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.rift.observed"), true);
		return true;
	}

	private static boolean rejectUnlock(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable(key), true);
		return false;
	}

	private static boolean hasAllEvidence(String evidence) {
		return evidence.contains("surface_shelter=")
				&& evidence.contains("abandoned_warehouse=")
				&& evidence.contains("underground_mine_station=")
				&& evidence.contains("field_observation=");
	}

	private static String appendEntry(String entries, String value) {
		for (String entry : entries.split(";")) {
			if (entry.equals(value)) {
				return entries;
			}
		}
		return entries.isBlank() ? value : entries + ";" + value;
	}

	private static int clueDigit(FrequencyWorldData data, int clueIndex) {
		return clueIndex < 0 ? 0 : passwordFor(data).charAt(clueIndex) - '0';
	}

	private static String direction(int digit) {
		return switch (digit) {
			case 0 -> "north";
			case 1 -> "east";
			case 2 -> "south";
			default -> "west";
		};
	}

	private static int horizontalDistanceSquared(BlockPos first, BlockPos second) {
		int x = first.getX() - second.getX();
		int z = first.getZ() - second.getZ();
		return x * x + z * z;
	}

	private static BlockPos stateOrigin(CompoundTag state) {
		return new BlockPos(state.getIntOr("x", 0), state.getIntOr("y", 0), state.getIntOr("z", 0));
	}

	private static FacilityDefinition definition(String id) {
		return DEFINITIONS.stream().filter(definition -> definition.id().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Unknown persisted facility id " + id));
	}
}
