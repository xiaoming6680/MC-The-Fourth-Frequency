package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import com.xm.thefourthfrequency.meta_api.MetaEvent;
import com.xm.thefourthfrequency.networking.MetaEventPayload;
import com.xm.thefourthfrequency.state.PlayerPatternState;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative stronghold altar and final encounter. */
public final class FinalConfrontationService {
	private static final String ALTAR = "stronghold_altar";
	private static final String LEGACY_ENDING = "ending";
	private static final String RECOVERABLE_CORRUPTION = "recoverable_boss_corruption";
	private static final int ALTAR_PHASE_ACTIVE = 1;
	private static final int ALTAR_PHASE_COMPLETE = 2;
	private static final int ANCHOR_COUNT = 4;
	private static final int SUCCESS_ANCHORS = 2;
	private static final int ALTAR_RADIUS = 48;
	private static final int ANCHOR_ATTACK_INTERVAL = 600;
	private static final int[][] FRAME_OFFSETS = {
			{-1, -2}, {0, -2}, {1, -2},
			{-1, 2}, {0, 2}, {1, 2},
			{-2, -1}, {-2, 0}, {-2, 1},
			{2, -1}, {2, 0}, {2, 1}
	};
	private static final int[][] ANCHOR_OFFSETS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
	private static boolean initialized;

	private FinalConfrontationService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(FinalConfrontationService::updateServer);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (state.is(ModBlocks.ALTAR_ANCHOR)) synchronizeAnchors(level.getServer(), true);
		});
	}

	public static void updateServer(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (server.getTickCount() % 20 == 0) discoverPortalRooms(server, data);
		if (worldInterfaceOwnsFinale(data)) {
			if (legacyFinaleStatePresent(data)) retireLegacyAltar(server);
			return;
		}
		if (!altarActive(data)) return;
		CompoundTag altar = altarState(data);
		ServerLevel level = level(server, altar.getStringOr("dimension", "")).orElse(null);
		if (level == null) return;
		BlockPos center = BlockPos.of(altar.getLongOr("center", 0L));
		if (!level.hasChunkAt(center)) return;
		if (server.getTickCount() % 20 == 0) {
			clearEndPortal(level, center);
			synchronizeAnchors(server, false);
		}
		CompoundTag ending = EndingState.get(data);
		MisreadBodyEntity body = body(server, ending).orElse(null);
		if (body == null && server.getTickCount() % 100 == 0 && playerNear(level, center)) {
			body = spawnBodyAt(level, center);
			EndingState.updateBody(data, body.getUUID(), body.blockPosition().asLong());
			storeBody(data, body);
		} else if (body != null) {
			if (body.level() != level || body.blockPosition().distSqr(center) > ALTAR_RADIUS * ALTAR_RADIUS) {
				body.discard();
				body = spawnBodyAt(level, center);
				EndingState.updateBody(data, body.getUUID(), body.blockPosition().asLong());
				storeBody(data, body);
			}
			if (server.getTickCount() % 200 == 0) {
				EndingState.updateBody(data, body.getUUID(), body.blockPosition().asLong());
			}
		}
		long now = level.getGameTime();
		if (body != null && now >= altar.getLongOr("next_anchor_attack_tick", Long.MAX_VALUE)) {
			damageOneAnchor(level, data, altar);
		}
	}

	private static void discoverPortalRooms(MinecraftServer server, FrequencyWorldData data) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
			if (record == null || !record.getBooleanOr(TerminalData.BOUND, false)
					|| record.getBooleanOr(TerminalData.PORTAL_ROOM_FOUND, false)
					|| !(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) continue;
			findPortalRingNear(level, player.blockPosition(), 12)
					.filter(center -> insideRealStronghold(level, center))
					.ifPresent(center -> markPortalRoomFound(player, center));
		}
	}

	private static void markPortalRoomFound(ServerPlayer player, BlockPos center) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag before = data.terminalRecord(player.getUUID()).orElse(null);
		if (before == null || before.getBooleanOr(TerminalData.PORTAL_ROOM_FOUND, false)) return;
		String dimension = player.level().dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, true);
			record.putLong(TerminalData.PORTAL_ROOM_POSITION, center.asLong());
			record.putString(TerminalData.PORTAL_ROOM_DIMENSION, dimension);
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0) | 0xFF);
			appendOnce(record, "portal_room_found", player.level().getGameTime(), player.level().getDayTime(),
					dimension, center, 0, 1);
		});
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.altar.room_found"), true);
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}

	private static MisreadBodyEntity startAltar(ServerPlayer player, ServerLevel level, BlockPos center) {
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (worldInterfaceOwnsFinale(data) || altarActive(data)
				|| EndingState.started(data) && EndingState.outcome(data) != EndingOutcome.ACTIVE) return null;
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !anchorsCanBePlaced(level, center)) return null;
		clearEndPortal(level, center);
		List<BlockPos> anchors = anchorPositions(center);
		for (BlockPos anchor : anchors) level.setBlockAndUpdate(anchor, ModBlocks.ALTAR_ANCHOR.defaultBlockState());
		MisreadBodyEntity body = spawnBodyAt(level, center);
		boolean truthRead = record.getBooleanOr(TerminalData.TRUTH_READ, false);
		if (!EndingState.beginAltar(data, body.getUUID(), body.blockPosition().asLong(), activeTerminalCount(data),
				level.getGameTime(), level.dimension().identifier().toString(), center.asLong(), truthRead, anchors.size())) {
			body.discard();
			return null;
		}
		CompoundTag altar = new CompoundTag();
		altar.putInt("phase", ALTAR_PHASE_ACTIVE);
		altar.putString("dimension", level.dimension().identifier().toString());
		altar.putLong("center", center.asLong());
		altar.putString("owner", player.getUUID().toString());
		altar.putBoolean("truth_read", truthRead);
		altar.putInt("anchors_initial", anchors.size());
		altar.putInt("anchors_remaining", anchors.size());
		altar.putLong("started_tick", level.getGameTime());
		altar.putLong("next_anchor_attack_tick", level.getGameTime() + ANCHOR_ATTACK_INTERVAL);
		altar.putString("body_uuid", body.getUUID().toString());
		ListTag positions = new ListTag();
		for (BlockPos anchor : anchors) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("position", anchor.asLong());
			positions.add(entry);
		}
		altar.put("anchors", positions);
		data.updateNarrativeState(root -> root.put(ALTAR, altar));
		for (UUID owner : data.terminalOwnerIds()) data.updateTerminalRecord(owner, terminal -> {
			terminal.putBoolean(TerminalData.ALTAR_STARTED, true);
			terminal.putInt(TerminalData.GROUNDING_ANCHORS_REMAINING, anchors.size());
		});
		recordEvent(data, level, center, "altar_started", 0, 2);
		recordEvent(data, level, center, "altar_entity_awakened", 0, 2);
		broadcast(level.getServer(), truthRead ? "message.thefourthfrequency.altar.protect"
				: "message.thefourthfrequency.altar.destroy");
		sendMeta(level.getServer(), MetaEvent.FINAL_BODY_AWAKENED);
		AudioService.play(level, center, AudioService.Cue.MISREAD_BODY);
		synchronizeOnline(level.getServer());
		return body;
	}

	public static MisreadBodyEntity startAltarForTesting(ServerPlayer player, ServerLevel level,
			BlockPos center, boolean truthRead) {
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) {
			if (!data.hasTerminalIssued(player.getUUID())) data.markTerminalIssued(player.getUUID());
			data.ensureTerminalRecord(player);
		}
		body(level.getServer(), EndingState.get(data)).ifPresent(Entity::discard);
		data.updateNarrativeState(root -> {
			root.remove(ALTAR);
			root.remove("ending");
		});
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.BOUND, true);
			record.putBoolean(TerminalData.TRUTH_READ, truthRead);
			record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, true);
			record.putLong(TerminalData.PORTAL_ROOM_POSITION, center.asLong());
			record.putString(TerminalData.PORTAL_ROOM_DIMENSION, level.dimension().identifier().toString());
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, 0xFF);
			record.putInt(TerminalData.ENDING_VERSION, 0);
			record.putString(TerminalData.ENDING_OUTCOME, "unresolved");
		});
		return startAltar(player, level, center);
	}

	public static MisreadBodyEntity awaken(ServerLevel level, BlockPos near, FrequencyWorldData data) {
		MisreadBodyEntity body = spawnBodyAt(level, near);
		EndingState.begin(data, body.getUUID(), body.blockPosition().asLong(), activeTerminalCount(data), level.getGameTime());
		AudioService.play(level, body.blockPosition(), AudioService.Cue.MISREAD_BODY);
		return body;
	}

	public static MisreadBodyEntity spawnBody(ServerLevel level, BlockPos near) {
		return spawnBodyAt(level, near);
	}

	/** Retires persisted state owned by the replaced finale while retaining its prelude implementation. */
	public static void retireLegacyAltar(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		CompoundTag altar = altarState(data);
		ServerLevel end = server.getLevel(Level.END);
		if (end != null) {
			List<MisreadBodyEntity> legacyEndBodies = new ArrayList<>();
			for (Entity entity : end.getAllEntities()) {
				if (entity instanceof MisreadBodyEntity body) legacyEndBodies.add(body);
			}
			legacyEndBodies.forEach(Entity::discard);
		}
		if (!legacyFinaleStatePresent(data)) return;
		body(server, EndingState.get(data)).ifPresent(Entity::discard);
		level(server, altar.getStringOr("dimension", "")).ifPresent(level -> {
			for (BlockPos anchor : altarAnchors(altar)) {
				if (level.hasChunkAt(anchor) && level.getBlockState(anchor).is(ModBlocks.ALTAR_ANCHOR)) {
					level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
				}
			}
		});
		data.updateNarrativeState(root -> {
			root.remove(ALTAR);
			root.remove(LEGACY_ENDING);
		});
	}

	private static MisreadBodyEntity spawnBodyAt(ServerLevel level, BlockPos position) {
		MisreadBodyEntity body = ModEntities.MISREAD_BODY.create(level, EntitySpawnReason.EVENT);
		if (body == null) throw new IllegalStateException("Registered final entity factory returned null");
		body.snapTo(position.getX() + 0.5, position.getY() + 1.0, position.getZ() + 0.5, 0.0F, 0.0F);
		body.setPhenotype(phenotype(FrequencyWorldData.get(level.getServer())));
		body.setPersistenceRequired();
		if (!level.addFreshEntity(body)) throw new IllegalStateException("Unable to add final entity to loaded level");
		return body;
	}

	public static Optional<ServerPlayer> selectTarget(MisreadBodyEntity body) {
		FrequencyWorldData data = FrequencyWorldData.get(body.level().getServer());
		if (worldInterfaceOwnsFinale(data)) return Optional.empty();
		CompoundTag altar = altarState(data);
		if (altar.getIntOr("phase", 0) != ALTAR_PHASE_ACTIVE) return Optional.empty();
		BlockPos center = BlockPos.of(altar.getLongOr("center", 0L));
		String dimension = altar.getStringOr("dimension", "");
		if (!body.level().dimension().identifier().toString().equals(dimension)) return Optional.empty();
		return body.level().players().stream().filter(FinalConfrontationService::aliveServerPlayer)
				.map(ServerPlayer.class::cast)
				.filter(player -> player.blockPosition().distSqr(center) <= ALTAR_RADIUS * ALTAR_RADIUS)
				.min(Comparator.comparingDouble(body::distanceToSqr));
	}

	/** The final entity never follows players away from the stronghold altar. */
	public static MisreadBodyEntity followAcrossRules(MisreadBodyEntity body) {
		return null;
	}

	public static Vec3 predictedDestination(ServerPlayer player) {
		CompoundTag record = FrequencyWorldData.get(player.level().getServer())
				.terminalRecord(player.getUUID()).orElse(new CompoundTag());
		PlayerPatternState pattern = PlayerPatternState.read(record);
		String axis = pattern.escapeAxis();
		double x = player.getX();
		double z = player.getZ();
		switch (axis) {
			case "east" -> x += 6;
			case "west" -> x -= 6;
			case "south" -> z += 6;
			case "north" -> z -= 6;
			default -> { }
		}
		String preferred = pattern.preferredWeapon();
		if (!preferred.isBlank() && itemId(player.getMainHandItem()).equals(preferred)) {
			if (Math.abs(x - player.getX()) >= Math.abs(z - player.getZ())) z += 3; else x += 3;
		}
		return new Vec3(x, player.getY(), z);
	}

	public static double learnedTimingBonus(ServerPlayer player) {
		CompoundTag record = FrequencyWorldData.get(player.level().getServer())
				.terminalRecord(player.getUUID()).orElse(new CompoundTag());
		PlayerPatternState pattern = PlayerPatternState.read(record);
		int bonus = 0;
		int foodPhase = pattern.foodUsePhase();
		if (foodPhase >= 0) {
			int current = Math.floorMod((int) player.level().getGameTime(), 100);
			int distance = Math.min(Math.floorMod(current - foodPhase, 100),
					Math.floorMod(foodPhase - current, 100));
			if (distance <= 8) bonus += 14;
		}
		if (pattern.armorChanges() > 0 && Math.floorMod((int) player.level().getGameTime(), 80) < 10) bonus += 8;
		return bonus / 100.0;
	}

	/** Retained for source compatibility; the final entity no longer removes any carried item. */
	public static boolean tryCapture(MisreadBodyEntity body, ServerPlayer player) {
		return false;
	}

	public static boolean useTerminationSpike(ServerPlayer player, MisreadBodyEntity body, ItemStack spike) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		boolean endEncounter = EndBossEncounterService.isEncounterBoss(body)
				&& !EndingState.endBossDefeated(data)
				&& EndingState.get(data).getBooleanOr("body_active", false);
		boolean legacyEncounter = altarActive(data) && EndingState.outcome(data) == EndingOutcome.ACTIVE;
		if (record == null || !record.getBooleanOr(TerminalData.TRUTH_READ, false)
				|| !record.getBooleanOr(TerminalData.RIFT_OBSERVED, false)
				|| !spike.is(ModItems.TERMINATION_SPIKE) || !body.isAlive()
				|| !endEncounter && !legacyEncounter) {
			player.displayClientMessage(Component.translatable("message.thefourthfrequency.termination.rejected"), true);
			return false;
		}
		if (!player.getAbilities().instabuild) spike.shrink(1);
		body.disrupt(300);
		if (endEncounter) EndBossEncounterService.delayNextAttack(body, 300);
		body.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1));
		body.hurtServer((ServerLevel) player.level(), player.damageSources().playerAttack(player), 18.0F);
		AudioService.play((ServerLevel) player.level(), player.blockPosition(), AudioService.Cue.TERMINATION);
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.termination.disrupted"), true);
		return true;
	}

	public static boolean useTerminationSpike(ServerPlayer player, MisreadBodyEntity body) {
		return useTerminationSpike(player, body, player.getMainHandItem());
	}

	/** Legacy altar bodies remain non-destructive; the new End encounter edits terrain in its own service. */
	public static int absorbNearby(MisreadBodyEntity body) {
		return 0;
	}

	/** Legacy altar adaptation remains non-destructive; End braces are owned by EndBossArenaService. */
	public static int placeAdaptationBarrier(MisreadBodyEntity body, Vec3 predictedTarget) {
		return 0;
	}

	public static void onBodyDefeated(MisreadBodyEntity body, ServerPlayer victor) {
		if (!(body.level() instanceof ServerLevel level)) return;
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (worldInterfaceOwnsFinale(data)) return;
		CompoundTag altar = altarState(data);
		if (altar.getIntOr("phase", 0) != ALTAR_PHASE_ACTIVE) return;
		int remaining = countAnchors(level, altar);
		boolean truthRead = altar.getBooleanOr("truth_read", false);
		EndingOutcome outcome = !truthRead ? EndingOutcome.UNDISCOVERED
				: remaining >= SUCCESS_ANCHORS ? EndingOutcome.SUCCESS : EndingOutcome.FAILED;
		if (!EndingState.resolve(data, outcome, level.getGameTime())) return;
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(ALTAR).copy();
			state.putInt("phase", ALTAR_PHASE_COMPLETE);
			state.putInt("anchors_remaining", remaining);
			state.putLong("completed_tick", level.getGameTime());
			state.putString("outcome", outcome.id());
			root.put(ALTAR, state);
		});
		for (UUID owner : data.terminalOwnerIds()) data.updateTerminalRecord(owner, terminal ->
				terminal.putInt(TerminalData.GROUNDING_ANCHORS_REMAINING, remaining));
		BlockPos center = BlockPos.of(altar.getLongOr("center", body.blockPosition().asLong()));
		recordEvent(data, level, center, "altar_entity_defeated", remaining, 2);
		recordEvent(data, level, center, "ending_recorded", endingVariant(outcome), 2);
		restoreCorruption(level.getServer(), data);
		AudioService.play(level, body.blockPosition(), AudioService.Cue.TERMINATION);
		broadcast(level.getServer(), switch (outcome) {
			case UNDISCOVERED -> "message.thefourthfrequency.ending.undiscovered";
			case FAILED -> "message.thefourthfrequency.ending.failed";
			case SUCCESS -> "message.thefourthfrequency.ending.success";
			case ACTIVE -> "message.thefourthfrequency.final_body.awakened";
		});
		if (victor != null) victor.displayClientMessage(Component.translatable(
				"message.thefourthfrequency.boss.defeated", remaining), false);
		sendMeta(level.getServer(), switch (outcome) {
			case UNDISCOVERED -> MetaEvent.UNDISCOVERED_BETRAYAL;
			case FAILED -> MetaEvent.PREVENTION_FAILED;
			case SUCCESS, ACTIVE -> MetaEvent.FOURTH_BAND_TERMINATED;
		});
		synchronizeOnline(level.getServer());
	}

	private static void damageOneAnchor(ServerLevel level, FrequencyWorldData data, CompoundTag altar) {
		List<BlockPos> remaining = altarAnchors(altar).stream()
				.filter(pos -> level.getBlockState(pos).is(ModBlocks.ALTAR_ANCHOR)).toList();
		if (!remaining.isEmpty()) {
			BlockPos target = remaining.get(Math.floorMod(altar.getIntOr("anchor_attack_index", 0), remaining.size()));
			level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
			level.playSound(null, target, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
					SoundSource.BLOCKS, 1.0F, 0.7F);
		}
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(ALTAR).copy();
			state.putInt("anchor_attack_index", state.getIntOr("anchor_attack_index", 0) + 1);
			state.putLong("next_anchor_attack_tick", level.getGameTime() + ANCHOR_ATTACK_INTERVAL);
			root.put(ALTAR, state);
		});
		synchronizeAnchors(level.getServer(), true);
	}

	private static void synchronizeAnchors(MinecraftServer server, boolean notify) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		CompoundTag altar = altarState(data);
		if (altar.getIntOr("phase", 0) != ALTAR_PHASE_ACTIVE) return;
		ServerLevel level = level(server, altar.getStringOr("dimension", "")).orElse(null);
		if (level == null) return;
		int before = altar.getIntOr("anchors_remaining", ANCHOR_COUNT);
		int after = countAnchors(level, altar);
		if (after == before) return;
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(ALTAR).copy();
			state.putInt("anchors_remaining", after);
			root.put(ALTAR, state);
		});
		for (UUID owner : data.terminalOwnerIds()) data.updateTerminalRecord(owner, record ->
				record.putInt(TerminalData.GROUNDING_ANCHORS_REMAINING, after));
		BlockPos center = BlockPos.of(altar.getLongOr("center", 0L));
		recordEvent(data, level, center, "altar_anchor_" + after, after, 1);
		if (notify) broadcast(server, "message.thefourthfrequency.altar.anchor_changed", after);
		synchronizeOnline(server);
	}

	private static int countAnchors(ServerLevel level, CompoundTag altar) {
		return (int) altarAnchors(altar).stream()
				.filter(pos -> level.getBlockState(pos).is(ModBlocks.ALTAR_ANCHOR)).count();
	}

	private static List<BlockPos> altarAnchors(CompoundTag altar) {
		List<BlockPos> result = new ArrayList<>();
		ListTag entries = altar.getListOrEmpty("anchors");
		for (int index = 0; index < entries.size(); index++) {
			result.add(BlockPos.of(entries.getCompoundOrEmpty(index).getLongOr("position", 0L)));
		}
		return result;
	}

	private static boolean anchorsCanBePlaced(Level level, BlockPos center) {
		for (BlockPos anchor : anchorPositions(center)) {
			BlockState state = level.getBlockState(anchor);
			if (!state.isAir() && !state.is(Blocks.END_PORTAL) && !state.is(ModBlocks.ALTAR_ANCHOR)) return false;
		}
		return true;
	}

	private static List<BlockPos> anchorPositions(BlockPos center) {
		List<BlockPos> result = new ArrayList<>(ANCHOR_COUNT);
		for (int[] offset : ANCHOR_OFFSETS) result.add(center.offset(offset[0], 0, offset[1]));
		return result;
	}

	private static void clearEndPortal(ServerLevel level, BlockPos center) {
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
			BlockPos position = center.offset(x, 0, z);
			if (level.getBlockState(position).is(Blocks.END_PORTAL)) {
				level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
			}
		}
	}

	public static Optional<BlockPos> findPortalRingNear(Level level, BlockPos origin, int radius) {
		Set<Long> checked = new HashSet<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = origin.getY() - 4; y <= origin.getY() + 4; y++) {
			for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
				for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
					cursor.set(x, y, z);
					if (!(level.getBlockState(cursor).getBlock() instanceof EndPortalFrameBlock)) continue;
					Optional<BlockPos> center = portalCenterFromFrame(level, cursor.immutable());
					if (center.isPresent() && checked.add(center.get().asLong())) return center;
				}
			}
		}
		return Optional.empty();
	}

	public static boolean validPortalRing(Level level, BlockPos center) {
		for (int[] offset : FRAME_OFFSETS) {
			BlockState state = level.getBlockState(center.offset(offset[0], 0, offset[1]));
			if (!(state.getBlock() instanceof EndPortalFrameBlock)
					|| state.getValue(EndPortalFrameBlock.FACING) != expectedFacing(offset[0], offset[1])) return false;
		}
		return true;
	}

	private static Optional<BlockPos> portalCenterFromFrame(Level level, BlockPos frame) {
		for (int[] offset : FRAME_OFFSETS) {
			BlockPos center = frame.offset(-offset[0], 0, -offset[1]);
			if (validPortalRing(level, center)) return Optional.of(center);
		}
		return Optional.empty();
	}

	private static Direction expectedFacing(int x, int z) {
		if (x == -2) return Direction.EAST;
		if (x == 2) return Direction.WEST;
		if (z == -2) return Direction.SOUTH;
		return Direction.NORTH;
	}

	public static int eyeCount(Level level, BlockPos center) {
		int eyes = 0;
		for (int[] offset : FRAME_OFFSETS) {
			BlockState state = level.getBlockState(center.offset(offset[0], 0, offset[1]));
			if (state.getBlock() instanceof EndPortalFrameBlock
					&& state.getValue(EndPortalFrameBlock.HAS_EYE)) eyes++;
		}
		return eyes;
	}

	private static Optional<BlockPos> missingEyeFrame(Level level, BlockPos center) {
		for (int[] offset : FRAME_OFFSETS) {
			BlockPos frame = center.offset(offset[0], 0, offset[1]);
			BlockState state = level.getBlockState(frame);
			if (state.getBlock() instanceof EndPortalFrameBlock
					&& !state.getValue(EndPortalFrameBlock.HAS_EYE)) return Optional.of(frame);
		}
		return Optional.empty();
	}

	private static boolean insideRealStronghold(ServerLevel level, BlockPos center) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder<Structure> stronghold = registry.getOrThrow(BuiltinStructures.STRONGHOLD);
		return level.structureManager().getStructureWithPieceAt(center, HolderSet.direct(stronghold)).isValid();
	}

	private static boolean consumeInventoryEye(ServerPlayer player) {
		if (player.getAbilities().instabuild) return true;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(Items.ENDER_EYE)) continue;
			stack.shrink(1);
			player.getInventory().setChanged();
			return true;
		}
		return false;
	}

	private static boolean carriesValidTerminal(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (data.isValidTerminal(player.getInventory().getItem(slot), player.getUUID())) return true;
		}
		return data.isValidTerminal(player.getMainHandItem(), player.getUUID())
				|| data.isValidTerminal(player.getOffhandItem(), player.getUUID());
	}

	private static boolean playerNear(ServerLevel level, BlockPos center) {
		return level.players().stream().anyMatch(player -> player.isAlive()
				&& player.blockPosition().distSqr(center) <= ALTAR_RADIUS * ALTAR_RADIUS);
	}

	private static Optional<ServerLevel> level(MinecraftServer server, String dimension) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().identifier().toString().equals(dimension)) return Optional.of(level);
		}
		return Optional.empty();
	}

	private static CompoundTag altarState(FrequencyWorldData data) {
		return data.narrativeState().getCompoundOrEmpty(ALTAR).copy();
	}

	private static boolean worldInterfaceOwnsFinale(FrequencyWorldData data) {
		return WorldInterfaceState.snapshot(data).present();
	}

	private static boolean legacyFinaleStatePresent(FrequencyWorldData data) {
		return data.narrativeState().contains(ALTAR) || EndingState.started(data);
	}

	public static boolean altarActive(FrequencyWorldData data) {
		return !worldInterfaceOwnsFinale(data)
				&& altarState(data).getIntOr("phase", 0) == ALTAR_PHASE_ACTIVE;
	}

	public static int anchorsRemaining(FrequencyWorldData data) {
		if (worldInterfaceOwnsFinale(data)) return 0;
		return Math.clamp(altarState(data).getIntOr("anchors_remaining", 0), 0, ANCHOR_COUNT);
	}

	/** Loaded, real block targets used by the correction body's final altar obstruction. */
	public static List<BlockPos> activeAnchorPositions(FrequencyWorldData data, ServerLevel level) {
		if (worldInterfaceOwnsFinale(data)) return List.of();
		CompoundTag altar = altarState(data);
		if (altar.getIntOr("phase", 0) != ALTAR_PHASE_ACTIVE
				|| !level.dimension().identifier().toString().equals(altar.getStringOr("dimension", ""))) {
			return List.of();
		}
		return altarAnchors(altar).stream()
				.filter(level::hasChunkAt)
				.filter(position -> level.getBlockState(position).is(ModBlocks.ALTAR_ANCHOR))
				.toList();
	}

	public static int anchorsRequiredForSuccess() {
		return SUCCESS_ANCHORS;
	}

	private static void storeBody(FrequencyWorldData data, MisreadBodyEntity body) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(ALTAR).copy();
			state.putString("body_uuid", body.getUUID().toString());
			root.put(ALTAR, state);
		});
	}

	private static Optional<MisreadBodyEntity> body(MinecraftServer server, CompoundTag ending) {
		try {
			Entity entity = server.overworld().getEntityInAnyDimension(
					UUID.fromString(ending.getStringOr("body_uuid", "")));
			return entity instanceof MisreadBodyEntity body && body.isAlive() ? Optional.of(body) : Optional.empty();
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	private static void recordEvent(FrequencyWorldData data, ServerLevel level, BlockPos position,
			String type, int variant, int severity) {
		String dimension = level.dimension().identifier().toString();
		for (UUID owner : data.terminalOwnerIds()) data.updateTerminalRecord(owner, record ->
				appendOnce(record, type, level.getGameTime(), level.getDayTime(), dimension,
						position, variant, severity));
	}

	private static void appendOnce(CompoundTag record, String type, long gameTime, long dayTime,
			String dimension, BlockPos position, int variant, int severity) {
		if (TerminalSignalLog.containsType(record, type)) return;
		TerminalSignalLog.append(record, SignalBand.UNKNOWN, type, gameTime, dayTime,
				dimension, position.asLong(), variant, severity, true);
	}

	private static void synchronizeOnline(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			TerminalRuntimeService.synchronizeProjection(player);
			TerminalRuntimeService.refresh(player);
		}
	}

	private static void restoreCorruption(MinecraftServer server, FrequencyWorldData data) {
		ListTag entries = data.narrativeState().getListOrEmpty(RECOVERABLE_CORRUPTION).copy();
		for (int index = 0; index < entries.size(); index++) {
			CompoundTag entry = entries.getCompoundOrEmpty(index);
			String dimension = entry.getStringOr("dimension", "");
			BlockPos position = BlockPos.of(entry.getLongOr("position", 0L));
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.dimension().identifier().toString().equals(dimension) || !level.hasChunkAt(position)) continue;
				BlockState current = level.getBlockState(position);
				if (!current.is(ModBlocks.REWORK_SCAR) && !current.is(ModBlocks.REWORK_BRACE)) break;
				Identifier blockId = Identifier.tryParse(entry.getStringOr("block", "minecraft:stone"));
				Block original = blockId == null ? Blocks.STONE : BuiltInRegistries.BLOCK.getValue(blockId);
				level.setBlock(position, original.defaultBlockState(), 3);
				break;
			}
		}
		data.updateNarrativeState(tag -> tag.remove(RECOVERABLE_CORRUPTION));
	}

	private static int phenotype(FrequencyWorldData data) {
		int[] roles = new int[3];
		for (UUID owner : data.terminalOwnerIds()) data.terminalRecord(owner).ifPresent(record -> {
			switch (record.getStringOr(TerminalData.MULTIPLAYER_ROLE, "unresolved")) {
				case "operator" -> roles[0]++;
				case "builder" -> roles[1]++;
				case "miner" -> roles[2]++;
				default -> { }
			}
		});
		if (roles[0] == roles[1] && roles[1] == roles[2]) return 3;
		return roles[0] >= roles[1] && roles[0] >= roles[2] ? 0 : roles[1] >= roles[2] ? 1 : 2;
	}

	public static int capturedTerminalCount(FrequencyWorldData data) {
		return 0;
	}

	public static int activeTerminalCount(FrequencyWorldData data) {
		return Math.max(1, (int) data.terminalOwnerIds().stream().filter(id -> data.terminalRecord(id)
				.map(record -> record.getBooleanOr(TerminalData.BOUND, false)).orElse(false)).count());
	}

	private static boolean aliveServerPlayer(Entity entity) {
		return entity instanceof ServerPlayer player && player.isAlive();
	}

	private static String itemId(ItemStack stack) {
		return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static int endingVariant(EndingOutcome outcome) {
		return switch (outcome) {
			case ACTIVE -> 1;
			case UNDISCOVERED -> 2;
			case FAILED -> 3;
			case SUCCESS -> 4;
		};
	}

	private static void broadcast(MinecraftServer server, String key, Object... values) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.displayClientMessage(Component.translatable(key, values), false);
		}
	}

	private static void sendMeta(MinecraftServer server, MetaEvent event) {
		MetaEventPayload payload = new MetaEventPayload(event.wireId());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(player, payload);
	}

	public static boolean debugSetAltarState(ServerPlayer player, int value) {
		if (value < 0 || value > 3 || !(player.level() instanceof ServerLevel level)) return false;
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (value == 0) {
			CompoundTag altar = altarState(data);
			for (BlockPos anchor : altarAnchors(altar)) {
				if (level.getBlockState(anchor).is(ModBlocks.ALTAR_ANCHOR)) level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
			}
			body(level.getServer(), EndingState.get(data)).ifPresent(Entity::discard);
			data.updateNarrativeState(root -> { root.remove(ALTAR); root.remove("ending"); });
			for (UUID owner : data.terminalOwnerIds()) data.updateTerminalRecord(owner, record -> {
				record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, false);
				record.putBoolean(TerminalData.ALTAR_STARTED, false);
				record.putInt(TerminalData.GROUNDING_ANCHORS_REMAINING, 0);
				record.putInt(TerminalData.ENDING_VERSION, 0);
				record.putString(TerminalData.ENDING_OUTCOME, "unresolved");
			});
			return true;
		}
		BlockPos center = player.blockPosition().offset(0, 0, 6);
		if (value == 1) {
			data.updateTerminalRecord(player.getUUID(), record -> {
				record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, true);
				record.putLong(TerminalData.PORTAL_ROOM_POSITION, center.asLong());
				record.putString(TerminalData.PORTAL_ROOM_DIMENSION, level.dimension().identifier().toString());
				record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, 0xFF);
			});
			return true;
		}
		MisreadBodyEntity body = startAltarForTesting(player, level, center,
				data.terminalRecord(player.getUUID()).orElse(new CompoundTag())
						.getBooleanOr(TerminalData.TRUTH_READ, false));
		if (body == null) return false;
		if (value == 3) {
			List<BlockPos> anchors = altarAnchors(altarState(data));
			for (int index = 1; index < anchors.size(); index++) {
				if (level.getBlockState(anchors.get(index)).is(ModBlocks.ALTAR_ANCHOR)) {
					level.setBlockAndUpdate(anchors.get(index), Blocks.AIR.defaultBlockState());
				}
			}
			synchronizeAnchors(level.getServer(), false);
		}
		return true;
	}

	public static boolean debugSetEnding(ServerPlayer player, int value) {
		if (value < 0 || value > 3 || !(player.level() instanceof ServerLevel level)) return false;
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (!altarActive(data) && !debugSetAltarState(player, 2)) return false;
		if (value == 0) return true;
		EndingOutcome outcome = switch (value) {
			case 1 -> EndingOutcome.UNDISCOVERED;
			case 2 -> EndingOutcome.FAILED;
			case 3 -> EndingOutcome.SUCCESS;
			default -> throw new IllegalStateException();
		};
		if (!EndingState.resolve(data, outcome, level.getGameTime())) return false;
		body(level.getServer(), EndingState.get(data)).ifPresent(Entity::discard);
		data.updateNarrativeState(root -> {
			CompoundTag altar = root.getCompoundOrEmpty(ALTAR).copy();
			altar.putInt("phase", ALTAR_PHASE_COMPLETE);
			altar.putString("outcome", outcome.id());
			root.put(ALTAR, altar);
		});
		synchronizeOnline(level.getServer());
		return true;
	}
}
