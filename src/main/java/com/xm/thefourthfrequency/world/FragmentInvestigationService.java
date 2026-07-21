package com.xm.thefourthfrequency.world;

import com.mojang.datafixers.util.Pair;
import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.facility.FacilityService;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.networking.PrivateAnomalyPayload;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalControlPolicy;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** World-shared vanilla-structure investigations. Blocks and loot never participate in completion. */
public final class FragmentInvestigationService {
	private static final String STATE = "fragment_investigation";
	private static final String CANDIDATES = "candidates";
	private static final String DISCOVERIES = "discoveries";
	private static final String ALLOCATION_CURSOR = "allocation_cursor";
	private static final String ALLOCATION_COMPLETE = "allocation_complete";
	private static final String NEAR_KEY = "fragment_near_candidate";
	private static final int STATE_VERSION = 1;
	private static final int FRAGMENT_COUNT = 4;
	private static final int GROUPS_PER_FRAGMENT = 4;
	private static final int MAX_CANDIDATES_PER_FRAGMENT = 3;
	private static final int LOCATE_RADIUS_CHUNKS = 256;
	private static final int ENTER_CHECK_INTERVAL = 10;
	private static final SignalBand[] SIGNAL_BANDS = {
			SignalBand.WEATHER, SignalBand.MINING, SignalBand.PUBLIC, SignalBand.UNKNOWN
	};
	private static final String[] FILE_IDS = {
			"surface_shelter_record", "field_observation_record",
			"underground_mine_record", "abandoned_warehouse_record"
	};
	private static final Group[][] POOLS = {
			{Group.MINESHAFT, Group.SHIPWRECK, Group.TRAIL_RUINS, Group.STRONGHOLD},
			{Group.MINESHAFT, Group.WOODLAND_MANSION, Group.DESERT_PYRAMID, Group.IGLOO},
			{Group.TRIAL_CHAMBERS, Group.PILLAGER_OUTPOST, Group.JUNGLE_TEMPLE, Group.OCEAN_MONUMENT},
			{Group.ANCIENT_CITY, Group.OCEAN_RUINS, Group.RUINED_PORTAL, Group.WOODLAND_MANSION}
	};
	private static final Map<UUID, Nearby> NEARBY = new LinkedHashMap<>();
	private static final Map<UUID, Nearby> FORCED_NEARBY_FOR_TESTS = new LinkedHashMap<>();
	private static boolean initialized;

	private FragmentInvestigationService() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(FragmentInvestigationService::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEARBY.clear();
			FORCED_NEARBY_FOR_TESTS.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 == 0) allocateNext(server);
		if (server.getTickCount() % ENTER_CHECK_INTERVAL != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) updateNearby(player, data);
	}

	private static void allocateNext(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!investigationsActive(data)) return;
		CompoundTag state = state(data);
		if (state.getBooleanOr(ALLOCATION_COMPLETE, false)) return;
		int cursor = Math.clamp(state.getIntOr(ALLOCATION_CURSOR, 0), 0, FRAGMENT_COUNT * GROUPS_PER_FRAGMENT);
		if (cursor >= FRAGMENT_COUNT * GROUPS_PER_FRAGMENT) {
			storeAllocationCursor(data, cursor, true);
			return;
		}
		int fragment = cursor / GROUPS_PER_FRAGMENT;
		List<Candidate> current = candidates(data);
		if (current.stream().filter(candidate -> candidate.fragment() == fragment).count()
				< MAX_CANDIDATES_PER_FRAGMENT) {
			int rotation = Math.floorMod(data.worldId().hashCode() + fragment * 31, GROUPS_PER_FRAGMENT);
			Group group = POOLS[fragment][Math.floorMod(cursor % GROUPS_PER_FRAGMENT + rotation, GROUPS_PER_FRAGMENT)];
			ServerLevel level = server.overworld();
			BlockPos origin = data.stationPosition().orElse(BlockPos.ZERO);
			Pair<BlockPos, Holder<Structure>> located = level.getChunkSource().getGenerator()
					.findNearestMapStructure(level, holders(level, group), origin, LOCATE_RADIUS_CHUNKS, false);
			if (located != null) {
				BlockPos position = located.getFirst().immutable();
				boolean duplicate = current.stream().anyMatch(candidate -> candidate.dimension().equals("minecraft:overworld")
						&& candidate.position().equals(position));
				if (!duplicate) {
					current = new ArrayList<>(current);
					current.add(new Candidate(fragment, group, position, "minecraft:overworld"));
					storeCandidates(data, current);
				}
			}
		}
		int next = cursor + 1;
		storeAllocationCursor(data, next, next >= FRAGMENT_COUNT * GROUPS_PER_FRAGMENT);
	}

	private static boolean investigationsActive(FrequencyWorldData data) {
		for (UUID owner : data.terminalOwnerIds()) {
			CompoundTag record = data.terminalRecord(owner).orElse(null);
			if (record != null && record.getIntOr(TerminalData.BAND_STAGE, 0) > 0) return true;
		}
		return false;
	}

	private static void updateNearby(ServerPlayer player, FrequencyWorldData data) {
		if (data.terminalRecord(player.getUUID()).isEmpty()) {
			NEARBY.remove(player.getUUID());
			return;
		}
		Nearby found = detect(player, data).orElse(null);
		Nearby previous = NEARBY.get(player.getUUID());
		if (found == null) NEARBY.remove(player.getUUID());
		else NEARBY.put(player.getUUID(), found);
		String nextKey = found == null ? "" : found.key();
		CompoundTag record = data.terminalRecord(player.getUUID()).orElseThrow();
		String storedKey = record.getStringOr(NEAR_KEY, "");
		if (storedKey.equals(nextKey)) return;
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putString(NEAR_KEY, nextKey);
			if (found != null) TerminalSignalLog.append(tag, bandForFragment(found.candidate().fragment()),
					"fragment_near_" + (found.candidate().fragment() + 1), player.level().getGameTime(),
					player.level().getDayTime(), found.candidate().dimension(), found.candidate().position().asLong(),
					found.candidate().group().code, 1, true);
		});
		if (found != null || previous != null) {
			TerminalLifecycleService.ensureCarried(player, false);
			TerminalRuntimeService.synchronizeProjection(player);
			TerminalRuntimeService.refresh(player);
		}
	}

	public static Optional<Nearby> nearby(ServerPlayer player) {
		Nearby cached = NEARBY.get(player.getUUID());
		if (cached != null) return Optional.of(cached);
		return detect(player, FrequencyWorldData.get(player.level().getServer()));
	}

	private static Optional<Nearby> detect(ServerPlayer player, FrequencyWorldData data) {
		Nearby forced = FORCED_NEARBY_FOR_TESTS.get(player.getUUID());
		if (forced != null) return Optional.of(forced);
		String dimension = player.level().dimension().identifier().toString();
		Set<Integer> discovered = discoveredFragments(data);
		for (Candidate candidate : candidates(data)) {
			if (discovered.contains(candidate.fragment()) || !candidate.dimension().equals(dimension)) continue;
			if (horizontalDistanceSquared(player.blockPosition(), candidate.position()) > 320L * 320L) continue;
			StructureStart start = player.level().structureManager().getStructureWithPieceAt(
					player.blockPosition(), holders(player.level(), candidate.group()));
			if (!start.isValid() || !matchesCandidate(start, candidate.position())) continue;
			return Optional.of(new Nearby(candidate, receiverTuning(candidate)));
		}
		return Optional.empty();
	}

	private static boolean matchesCandidate(StructureStart start, BlockPos located) {
		var box = start.getBoundingBox();
		return located.getX() >= box.minX() - 96 && located.getX() <= box.maxX() + 96
				&& located.getZ() >= box.minZ() - 96 && located.getZ() <= box.maxZ() + 96;
	}

	public static boolean insideSupportedStructure(ServerPlayer player) {
		for (Group group : Group.values()) {
			if (player.level().structureManager().getStructureWithPieceAt(
					player.blockPosition(), holders(player.level(), group)).isValid()) return true;
		}
		return false;
	}

	public static boolean appendCandidateLogs(CompoundTag record, ServerPlayer player, FrequencyWorldData data) {
		if (record.getIntOr(TerminalData.BAND_STAGE, 0) == 0) return false;
		boolean changed = false;
		int[] slots = new int[FRAGMENT_COUNT];
		for (Candidate candidate : candidates(data)) {
			int slot = slots[candidate.fragment()]++;
			String type = "fragment_candidate_" + (candidate.fragment() + 1) + "_" + slot;
			if (TerminalSignalLog.containsType(record, type)) continue;
			TerminalSignalLog.append(record, bandForFragment(candidate.fragment()), type, player.level().getGameTime(),
					player.level().getDayTime(), candidate.dimension(), candidate.position().asLong(),
					candidate.group().code, candidate.group().location.code, slot == 0);
			changed = true;
		}
		return changed;
	}

	public static boolean ensureSignalMarkers(CompoundTag record, ServerPlayer player) {
		boolean changed = normalizeSignalBands(record);
		for (int fragment = 0; fragment < FRAGMENT_COUNT; fragment++) {
			if (TerminalFileState.discovered(record, FILE_IDS[fragment])) continue;
			String type = "fragment_marker_" + (fragment + 1);
			if (TerminalSignalLog.containsType(record, type)) continue;
			TerminalSignalLog.append(record, bandForFragment(fragment), type, player.level().getGameTime(),
					player.level().getDayTime(), "", 0L, fragment, 0, false);
			changed = true;
		}
		return changed;
	}

	public static SignalBand bandForFragment(int fragment) {
		return SIGNAL_BANDS[Math.clamp(fragment, 0, SIGNAL_BANDS.length - 1)];
	}

	private static boolean normalizeSignalBands(CompoundTag record) {
		ListTag events = record.getListOrEmpty(TerminalData.SIGNAL_EVENTS).copy();
		boolean changed = false;
		for (int index = 0; index < events.size(); index++) {
			CompoundTag entry = events.getCompoundOrEmpty(index);
			int fragment = signalFragment(entry.getStringOr("type", ""));
			if (fragment < 0) continue;
			int expected = bandForFragment(fragment).wireId();
			if (entry.getIntOr("band", SignalBand.UNKNOWN.wireId()) == expected) continue;
			entry.putInt("band", expected);
			events.set(index, entry);
			changed = true;
		}
		if (changed) record.put(TerminalData.SIGNAL_EVENTS, events);
		return changed;
	}

	private static int signalFragment(String type) {
		String value = null;
		if (type.startsWith("fragment_candidate_")) {
			String remainder = type.substring("fragment_candidate_".length());
			int separator = remainder.indexOf('_');
			value = separator < 0 ? remainder : remainder.substring(0, separator);
		} else {
			for (String prefix : new String[]{"fragment_marker_", "fragment_near_", "fragment_shared_",
					"fragment_received_", "fragment_action_"}) {
				if (type.startsWith(prefix)) {
					value = type.substring(prefix.length());
					break;
				}
			}
		}
		if (value == null) return -1;
		try {
			int fragment = Integer.parseInt(value) - 1;
			return fragment >= 0 && fragment < FRAGMENT_COUNT ? fragment : -1;
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	public static boolean synchronizeSharedFiles(CompoundTag record, ServerPlayer player, FrequencyWorldData data,
			List<SharedReceipt> receipts) {
		boolean changed = false;
		ListTag discoveries = state(data).getListOrEmpty(DISCOVERIES);
		boolean allComplete = discoveredFragments(data).size() == FRAGMENT_COUNT;
		for (int index = 0; index < discoveries.size(); index++) {
			CompoundTag discovery = discoveries.getCompoundOrEmpty(index);
			int fragment = discovery.getIntOr("fragment", -1);
			if (fragment < 0 || fragment >= FRAGMENT_COUNT || TerminalFileState.discovered(record, FILE_IDS[fragment])) continue;
			long gameTime = discovery.getLongOr("game_time", player.level().getGameTime());
			long dayTime = discovery.getLongOr("day_time", player.level().getDayTime());
			String discovererName = discovery.getStringOr("discoverer_name", "?");
			String discovererId = discovery.getStringOr("discoverer_id", "");
			boolean own = player.getUUID().toString().equals(discovererId);
			TerminalFileState.discover(record, FILE_IDS[fragment], gameTime, dayTime, true);
			TerminalSignalLog.append(record, bandForFragment(fragment),
					(own ? "fragment_shared_" : "fragment_received_") + (fragment + 1), gameTime, dayTime,
					discovererName, discovery.getLongOr("position", 0L), fragment, 1, true);
			receipts.add(new SharedReceipt(fragment + 1, discovererName, own));
			changed = true;
		}
		if (!discoveries.isEmpty()) changed |= TerminalFileState.discover(record, "encrypted_witness_file",
				player.level().getGameTime(), player.level().getDayTime(), allComplete);
		return changed;
	}

	public static boolean selectCandidate(ServerPlayer player, int encoded) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !unstableNavigationUnlocked(record)) return false;
		int fragment = Math.floorDiv(encoded, MAX_CANDIDATES_PER_FRAGMENT);
		int slot = Math.floorMod(encoded, MAX_CANDIDATES_PER_FRAGMENT);
		if (fragment < 0 || fragment >= FRAGMENT_COUNT || discoveredFragments(data).contains(fragment)) return false;
		List<Candidate> values = candidates(data).stream()
				.filter(candidate -> candidate.fragment() == fragment).toList();
		if (slot >= values.size()) return false;
		Candidate selected = values.get(slot);
		data.updateTerminalRecord(player.getUUID(), tag -> new NavigationState(
				"structure_fragment", "fragment_" + (fragment + 1) + "_" + slot, true,
				selected.group().id, selected.position().asLong(), selected.dimension(), player.level().getGameTime()).writeTo(tag));
		TerminalRuntimeService.refresh(player);
		return true;
	}

	public static boolean hasUndiscoveredCandidate(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || record.getIntOr(TerminalData.BAND_STAGE, 0) == 0) return false;
		String dimension = player.level().dimension().identifier().toString();
		Set<Integer> discovered = discoveredFragments(data);
		return candidates(data).stream().anyMatch(candidate -> !discovered.contains(candidate.fragment())
				&& candidate.dimension().equals(dimension));
	}

	public static boolean selectNearestCandidate(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || record.getIntOr(TerminalData.BAND_STAGE, 0) == 0) return false;
		if (!unstableNavigationUnlocked(record)) return false;
		String dimension = player.level().dimension().identifier().toString();
		Set<Integer> discovered = discoveredFragments(data);
		Candidate nearest = candidates(data).stream()
				.filter(candidate -> !discovered.contains(candidate.fragment()) && candidate.dimension().equals(dimension))
				.min(Comparator.comparingLong(candidate -> horizontalDistanceSquared(
						player.blockPosition(), candidate.position()))).orElse(null);
		if (nearest == null) return false;
		List<Candidate> fragmentCandidates = candidates(data).stream()
				.filter(candidate -> candidate.fragment() == nearest.fragment()).toList();
		int slot = fragmentCandidates.indexOf(nearest);
		return slot >= 0 && selectCandidate(player, nearest.fragment() * MAX_CANDIDATES_PER_FRAGMENT + slot);
	}

	private static boolean unstableNavigationUnlocked(CompoundTag record) {
		if (record.getIntOr(TerminalData.BAND_STAGE, 0) == 0) return false;
		NavigationState navigation = NavigationState.read(record);
		if (navigation.kind().equals("structure_fragment") && navigation.located()) return true;
		int milestones = record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		return SurvivalMilestone.ENTERED_NETHER.present(milestones)
				&& StoryProgressService.guidanceHintTier(record) >= 2;
	}

	public static boolean completeNearby(ServerPlayer discoverer, int tuning) {
		FrequencyWorldData data = FrequencyWorldData.get(discoverer.level().getServer());
		Nearby nearby = detect(discoverer, data).orElse(null);
		if (nearby == null || !TerminalControlPolicy.receiverLocked(tuning, nearby.tuning())) return false;
		return completeCandidate(discoverer, nearby.candidate());
	}

	private static boolean completeCandidate(ServerPlayer discoverer, Candidate candidate) {
		FrequencyWorldData data = FrequencyWorldData.get(discoverer.level().getServer());
		if (discoveredFragments(data).contains(candidate.fragment())) return false;
		long now = discoverer.level().getGameTime();
		long dayTime = discoverer.level().getDayTime();
		String discovererName = discoverer.getGameProfile().name();
		CompoundTag discovery = new CompoundTag();
		discovery.putInt("fragment", candidate.fragment());
		discovery.putInt("group", candidate.group().code);
		discovery.putLong("position", candidate.position().asLong());
		discovery.putString("dimension", candidate.dimension());
		discovery.putString("discoverer_id", discoverer.getUUID().toString());
		discovery.putString("discoverer_name", discovererName);
		discovery.putLong("game_time", now);
		discovery.putLong("day_time", dayTime);
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(STATE).copy();
			ListTag discoveries = state.getListOrEmpty(DISCOVERIES).copy();
			discoveries.add(discovery);
			state.put(DISCOVERIES, discoveries);
			root.put(STATE, state);
		});
		NEARBY.entrySet().removeIf(entry -> entry.getValue().candidate().fragment() == candidate.fragment());
		FORCED_NEARBY_FOR_TESTS.entrySet().removeIf(entry -> entry.getValue().candidate().fragment() == candidate.fragment());
		boolean allComplete = discoveredFragments(data).size() == FRAGMENT_COUNT;
		for (UUID owner : data.terminalOwnerIds()) {
			boolean isDiscoverer = owner.equals(discoverer.getUUID());
			data.updateTerminalRecord(owner, tag -> {
				TerminalFileState.discover(tag, FILE_IDS[candidate.fragment()], now, dayTime, true);
				TerminalFileState.discover(tag, "encrypted_witness_file", now, dayTime, allComplete);
				TerminalSignalLog.append(tag, bandForFragment(candidate.fragment()),
						(isDiscoverer ? "fragment_shared_" : "fragment_received_") + (candidate.fragment() + 1),
						now, dayTime, discovererName, candidate.position().asLong(), candidate.fragment(), 1, true);
				if (isDiscoverer) TerminalSignalLog.append(tag, bandForFragment(candidate.fragment()),
						"fragment_action_" + (candidate.fragment() + 1), now, dayTime,
						candidate.dimension(), discoverer.blockPosition().asLong(), candidate.group().code, 2, true);
				NavigationState navigation = NavigationState.read(tag);
				if (navigation.kind().equals("structure_fragment")
						&& navigation.itemId().startsWith("fragment_" + (candidate.fragment() + 1) + "_")) {
					navigation.clearLocation().writeTo(tag);
				}
			});
		}
		ServerPlayNetworking.send(discoverer, new PrivateAnomalyPayload(
				"fragment_" + (candidate.fragment() + 1), candidate.group().code, 0, 0));
		AudioService.play(discoverer.level(), discoverer.blockPosition(), AudioService.Cue.FOURTH_BAND);
		for (ServerPlayer online : discoverer.level().getServer().getPlayerList().getPlayers()) {
			if (data.terminalRecord(online.getUUID()).isEmpty()) continue;
			Component message = online.getUUID().equals(discoverer.getUUID())
					? Component.translatable("message.thefourthfrequency.fragment.shared", candidate.fragment() + 1)
					: Component.translatable("message.thefourthfrequency.fragment.received", discovererName,
							candidate.fragment() + 1);
			online.displayClientMessage(message, true);
			TerminalLifecycleService.ensureCarried(online, false);
			TerminalRuntimeService.synchronizeProjection(online);
			TerminalRuntimeService.refresh(online);
		}
		if (allComplete) FacilityService.unlockArchiveFromFragments(discoverer);
		return true;
	}

	public static void setCandidatesForTesting(FrequencyWorldData data, List<Candidate> values) {
		storeCandidates(data, values);
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(STATE).copy();
			state.remove(DISCOVERIES);
			root.put(STATE, state);
		});
		NEARBY.clear();
		FORCED_NEARBY_FOR_TESTS.clear();
		storeAllocationCursor(data, FRAGMENT_COUNT * GROUPS_PER_FRAGMENT, true);
	}

	public static void setNearbyForTesting(ServerPlayer player, Candidate candidate) {
		Nearby nearby = new Nearby(candidate, receiverTuning(candidate));
		NEARBY.put(player.getUUID(), nearby);
		FORCED_NEARBY_FOR_TESTS.put(player.getUUID(), nearby);
	}

	public static boolean discoverForTesting(ServerPlayer player, Candidate candidate) {
		return completeCandidate(player, candidate);
	}

	public static int completedCount(FrequencyWorldData data) {
		return discoveredFragments(data).size();
	}

	public static boolean isFragmentFile(String id) {
		for (String file : FILE_IDS) if (file.equals(id)) return true;
		return false;
	}

	public static int fragmentForFile(String id) {
		for (int index = 0; index < FILE_IDS.length; index++) if (FILE_IDS[index].equals(id)) return index;
		return -1;
	}

	public static int receiverTuning(Candidate candidate) {
		int hash = 17;
		hash = 31 * hash + candidate.fragment();
		hash = 31 * hash + candidate.group().code;
		hash = 31 * hash + candidate.dimension().hashCode();
		hash = 31 * hash + Long.hashCode(candidate.position().asLong());
		return 12 + Math.floorMod(hash, 77);
	}

	private static Set<Integer> discoveredFragments(FrequencyWorldData data) {
		return state(data).getListOrEmpty(DISCOVERIES).stream()
				.map(tag -> ((CompoundTag) tag).getIntOr("fragment", -1))
				.filter(value -> value >= 0 && value < FRAGMENT_COUNT)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private static List<Candidate> candidates(FrequencyWorldData data) {
		List<Candidate> values = new ArrayList<>();
		ListTag list = state(data).getListOrEmpty(CANDIDATES);
		for (int index = 0; index < list.size(); index++) {
			CompoundTag tag = list.getCompoundOrEmpty(index);
			int fragment = tag.getIntOr("fragment", -1);
			Group group = Group.fromCode(tag.getIntOr("group", -1));
			if (fragment < 0 || fragment >= FRAGMENT_COUNT || group == null) continue;
			values.add(new Candidate(fragment, group, BlockPos.of(tag.getLongOr("position", 0L)),
					tag.getStringOr("dimension", "minecraft:overworld")));
		}
		values.sort(Comparator.comparingInt(Candidate::fragment));
		return List.copyOf(values);
	}

	private static CompoundTag state(FrequencyWorldData data) {
		CompoundTag state = data.narrativeState().getCompoundOrEmpty(STATE).copy();
		if (!state.contains("version")) state.putInt("version", STATE_VERSION);
		return state;
	}

	private static void storeCandidates(FrequencyWorldData data, List<Candidate> candidates) {
		ListTag encoded = new ListTag();
		for (Candidate candidate : candidates) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("fragment", candidate.fragment());
			tag.putInt("group", candidate.group().code);
			tag.putLong("position", candidate.position().asLong());
			tag.putString("dimension", candidate.dimension());
			encoded.add(tag);
		}
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(STATE).copy();
			state.putInt("version", STATE_VERSION);
			state.put(CANDIDATES, encoded);
			root.put(STATE, state);
		});
	}

	private static void storeAllocationCursor(FrequencyWorldData data, int cursor, boolean complete) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(STATE).copy();
			state.putInt("version", STATE_VERSION);
			state.putInt(ALLOCATION_CURSOR, cursor);
			state.putBoolean(ALLOCATION_COMPLETE, complete);
			root.put(STATE, state);
		});
	}

	private static HolderSet<Structure> holders(ServerLevel level, Group group) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> holders = group.keys.stream().map(registry::getOrThrow).map(value -> (Holder<Structure>) value).toList();
		return HolderSet.direct(holders);
	}

	private static long horizontalDistanceSquared(BlockPos first, BlockPos second) {
		long dx = first.getX() - (long) second.getX();
		long dz = first.getZ() - (long) second.getZ();
		return dx * dx + dz * dz;
	}

	public record Candidate(int fragment, Group group, BlockPos position, String dimension) { }
	public record Nearby(Candidate candidate, int tuning) {
		public String key() {
			return candidate.fragment() + ":" + candidate.group().code + ":" + candidate.position().asLong();
		}
	}
	public record SharedReceipt(int fragment, String discovererName, boolean own) { }

	public enum Location {
		UNDERGROUND(0), SURFACE(1), WATER(2);
		private final int code;
		Location(int code) { this.code = code; }
	}

	public enum Group {
		MINESHAFT(0, "mineshaft", Location.UNDERGROUND, BuiltinStructures.MINESHAFT, BuiltinStructures.MINESHAFT_MESA),
		SHIPWRECK(1, "shipwreck", Location.WATER, BuiltinStructures.SHIPWRECK, BuiltinStructures.SHIPWRECK_BEACHED),
		TRAIL_RUINS(2, "trail_ruins", Location.UNDERGROUND, BuiltinStructures.TRAIL_RUINS),
		STRONGHOLD(3, "stronghold", Location.UNDERGROUND, BuiltinStructures.STRONGHOLD),
		WOODLAND_MANSION(4, "woodland_mansion", Location.SURFACE, BuiltinStructures.WOODLAND_MANSION),
		DESERT_PYRAMID(5, "desert_pyramid", Location.SURFACE, BuiltinStructures.DESERT_PYRAMID),
		IGLOO(6, "igloo", Location.SURFACE, BuiltinStructures.IGLOO),
		TRIAL_CHAMBERS(7, "trial_chambers", Location.UNDERGROUND, BuiltinStructures.TRIAL_CHAMBERS),
		PILLAGER_OUTPOST(8, "pillager_outpost", Location.SURFACE, BuiltinStructures.PILLAGER_OUTPOST),
		JUNGLE_TEMPLE(9, "jungle_temple", Location.SURFACE, BuiltinStructures.JUNGLE_TEMPLE),
		OCEAN_MONUMENT(10, "ocean_monument", Location.WATER, BuiltinStructures.OCEAN_MONUMENT),
		ANCIENT_CITY(11, "ancient_city", Location.UNDERGROUND, BuiltinStructures.ANCIENT_CITY),
		OCEAN_RUINS(12, "ocean_ruins", Location.WATER, BuiltinStructures.OCEAN_RUIN_COLD, BuiltinStructures.OCEAN_RUIN_WARM),
		RUINED_PORTAL(13, "ruined_portal", Location.SURFACE, BuiltinStructures.RUINED_PORTAL_STANDARD,
				BuiltinStructures.RUINED_PORTAL_DESERT, BuiltinStructures.RUINED_PORTAL_JUNGLE,
				BuiltinStructures.RUINED_PORTAL_SWAMP, BuiltinStructures.RUINED_PORTAL_MOUNTAIN,
				BuiltinStructures.RUINED_PORTAL_OCEAN, BuiltinStructures.RUINED_PORTAL_NETHER);

		private final int code;
		private final String id;
		private final Location location;
		private final List<ResourceKey<Structure>> keys;

		@SafeVarargs
		Group(int code, String id, Location location, ResourceKey<Structure>... keys) {
			this.code = code;
			this.id = id;
			this.location = location;
			this.keys = List.of(keys);
		}

		private static Group fromCode(int code) {
			for (Group group : values()) if (group.code == code) return group;
			return null;
		}
	}
}
