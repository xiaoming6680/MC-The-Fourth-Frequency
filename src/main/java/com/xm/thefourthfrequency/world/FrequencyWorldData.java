package com.xm.thefourthfrequency.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class FrequencyWorldData extends SavedData {
	private static final Codec<FrequencyWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("schema_version", 1).forGetter(data -> data.schemaVersion),
			Codec.STRING.optionalFieldOf("world_id", "").forGetter(data -> data.worldId),
			Codec.BOOL.optionalFieldOf("station_allocated", false).forGetter(data -> data.stationAllocated),
			Codec.LONG.optionalFieldOf("station_pos", 0L).forGetter(data -> data.stationPosition),
			Codec.BOOL.optionalFieldOf("station_complete", false).forGetter(data -> data.stationComplete),
			Codec.INT.optionalFieldOf("station_build_cursor", 0).forGetter(data -> data.stationBuildCursor),
			Codec.STRING.listOf().optionalFieldOf("issued_players", List.of())
					.forGetter(data -> data.issuedPlayers.stream().map(UUID::toString).toList()),
			CompoundTag.CODEC.listOf().optionalFieldOf("terminal_records", List.of())
					.forGetter(data -> data.terminalRecords.values().stream().map(CompoundTag::copy).toList()),
			CompoundTag.CODEC.optionalFieldOf("narrative_state", new CompoundTag())
					.forGetter(data -> data.narrativeState.copy())
	).apply(instance, FrequencyWorldData::new));

	public static final SavedDataType<FrequencyWorldData> TYPE = new SavedDataType<>(
			"thefourthfrequency_world",
			FrequencyWorldData::new,
			CODEC,
			null
	);

	private int schemaVersion;
	private final String worldId;
	private boolean stationAllocated;
	private long stationPosition;
	private boolean stationComplete;
	private int stationBuildCursor;
	private final Set<UUID> issuedPlayers;
	private final Map<UUID, CompoundTag> terminalRecords;
	private final CompoundTag narrativeState;

	public FrequencyWorldData() {
		this(RuntimeServices.PERSISTENCE_SCHEMA_VERSION, UUID.randomUUID().toString(),
				false, 0L, false, 0, List.of(), List.of(), new CompoundTag());
	}

	private FrequencyWorldData(int schemaVersion, String worldId, boolean stationAllocated, long stationPosition,
			boolean stationComplete, int stationBuildCursor, List<String> issuedPlayers,
			List<CompoundTag> terminalRecords, CompoundTag narrativeState) {
		if (schemaVersion > RuntimeServices.PERSISTENCE_SCHEMA_VERSION) {
			throw new IllegalStateException("World data schema " + schemaVersion + " is newer than supported schema "
					+ RuntimeServices.PERSISTENCE_SCHEMA_VERSION);
		}
		this.schemaVersion = RuntimeServices.PERSISTENCE_SCHEMA_VERSION;
		this.worldId = worldId.isBlank() ? UUID.randomUUID().toString() : worldId;
		this.stationAllocated = stationAllocated;
		this.stationPosition = stationPosition;
		this.stationComplete = stationComplete;
		this.stationBuildCursor = Math.max(0, stationBuildCursor);
		this.issuedPlayers = parsePlayers(issuedPlayers);
		this.terminalRecords = parseTerminalRecords(terminalRecords);
		this.narrativeState = narrativeState.copy();
		if (schemaVersion < RuntimeServices.PERSISTENCE_SCHEMA_VERSION || worldId.isBlank()) {
			setDirty();
		}
	}

	private static Map<UUID, CompoundTag> parseTerminalRecords(List<CompoundTag> values) {
		Map<UUID, CompoundTag> parsed = new LinkedHashMap<>();
		for (CompoundTag value : values) {
			int recordSchema = value.getIntOr(TerminalData.SCHEMA_VERSION, 1);
			if (recordSchema > RuntimeServices.PERSISTENCE_SCHEMA_VERSION) {
				throw new IllegalStateException("Terminal data schema " + recordSchema
						+ " is newer than supported schema " + RuntimeServices.PERSISTENCE_SCHEMA_VERSION);
			}
			try {
				UUID ownerId = UUID.fromString(value.getStringOr(TerminalData.OWNER_ID, ""));
				parsed.put(ownerId, TerminalData.migrateRecord(value));
			} catch (IllegalArgumentException ignored) {
				// A malformed legacy record cannot be authoritative, so retain no partial entry.
			}
		}
		return parsed;
	}

	private static Set<UUID> parsePlayers(Collection<String> values) {
		Set<UUID> parsed = new LinkedHashSet<>();
		for (String value : values) {
			try {
				parsed.add(UUID.fromString(value));
			} catch (IllegalArgumentException ignored) {
				// Ignore malformed legacy entries while retaining every valid grant record.
			}
		}
		return parsed;
	}

	public static FrequencyWorldData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	public String worldId() {
		return worldId;
	}

	public Optional<BlockPos> stationPosition() {
		return stationAllocated ? Optional.of(BlockPos.of(stationPosition)) : Optional.empty();
	}

	public void allocateStation(BlockPos position) {
		if (stationAllocated) {
			return;
		}
		stationAllocated = true;
		stationPosition = position.asLong();
		stationBuildCursor = 0;
		stationComplete = false;
		setDirty();
	}

	public boolean stationComplete() {
		return stationComplete;
	}

	public int stationBuildCursor() {
		return stationBuildCursor;
	}

	public void advanceStationBuildCursor(int cursor, int totalPlacements) {
		stationBuildCursor = Math.clamp(cursor, 0, totalPlacements);
		if (stationBuildCursor == totalPlacements) {
			stationComplete = true;
		}
		setDirty();
	}

	public boolean markTerminalIssued(UUID playerId) {
		if (!issuedPlayers.add(playerId)) {
			return false;
		}
		setDirty();
		return true;
	}

	public boolean hasTerminalIssued(UUID playerId) {
		return issuedPlayers.contains(playerId);
	}

	public int issuedPlayerCount() {
		return issuedPlayers.size();
	}

	public int cacheVariantFor(UUID playerId) {
		int index = 0;
		for (UUID issuedPlayer : issuedPlayers) {
			if (issuedPlayer.equals(playerId)) {
				return Math.floorMod(index, 4);
			}
			index++;
		}
		throw new IllegalStateException("Player has no terminal grant record: " + playerId);
	}

	public CompoundTag ensureTerminalRecord(ServerPlayer player) {
		CompoundTag existing = terminalRecords.get(player.getUUID());
		if (existing != null) {
			return existing.copy();
		}
		if (!issuedPlayers.contains(player.getUUID())) {
			throw new IllegalStateException("Cannot create terminal record before grant ledger entry");
		}
		CompoundTag created = TerminalData.createRecord(player, cacheVariantFor(player.getUUID()), worldId);
		terminalRecords.put(player.getUUID(), created.copy());
		setDirty();
		return created;
	}

	public Optional<CompoundTag> terminalRecord(UUID ownerId) {
		CompoundTag record = terminalRecords.get(ownerId);
		return record == null ? Optional.empty() : Optional.of(record.copy());
	}

	public Set<UUID> terminalOwnerIds() {
		return Set.copyOf(terminalRecords.keySet());
	}

	public void updateTerminalRecord(UUID ownerId, Consumer<CompoundTag> update) {
		CompoundTag record = terminalRecords.get(ownerId);
		if (record == null) {
			throw new IllegalStateException("No terminal record for player " + ownerId);
		}
		update.accept(record);
		setDirty();
	}

	public boolean isValidTerminal(ItemStack stack, UUID ownerId) {
		CompoundTag record = terminalRecords.get(ownerId);
		if (record == null || !TerminalData.belongsTo(stack, ownerId)) {
			return false;
		}
		CompoundTag candidate = TerminalData.copyTag(stack);
		return worldId.equals(candidate.getStringOr(TerminalData.WORLD_ID, ""))
				&& record.getStringOr(TerminalData.TERMINAL_ID, "")
						.equals(candidate.getStringOr(TerminalData.TERMINAL_ID, ""))
				&& record.getIntOr(TerminalData.COPY_GENERATION, 0)
						== candidate.getIntOr(TerminalData.COPY_GENERATION, -1);
	}

	public ItemStack recoverTerminal(UUID ownerId) {
		updateTerminalRecord(ownerId, record -> {
			record.putInt(TerminalData.COPY_GENERATION,
					record.getIntOr(TerminalData.COPY_GENERATION, 0) + 1);
			record.putInt(TerminalData.RECOVERY_COUNT,
					record.getIntOr(TerminalData.RECOVERY_COUNT, 0) + 1);
		});
		return TerminalData.stackFromRecord(terminalRecords.get(ownerId));
	}

	public CompoundTag narrativeState() {
		return narrativeState.copy();
	}

	public void updateNarrativeState(Consumer<CompoundTag> update) {
		update.accept(narrativeState);
		setDirty();
	}

}
