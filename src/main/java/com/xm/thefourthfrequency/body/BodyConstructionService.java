package com.xm.thefourthfrequency.body;

import com.xm.thefourthfrequency.audio.AudioService;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.networking.PrivateAnomalyPayload;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import com.xm.thefourthfrequency.ending.FinaleRuntimePolicy;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BodyConstructionService {
	private static final String ARCHIVE_UNLOCKED = "archive_unlocked";
	private static final String NETHER_RIFT = "nether_rift";
	private static final int BUILD_BUDGET_PER_TICK = 24;
	private static boolean initialized;

	private BodyConstructionService() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				BodyConstructionService::afterPlayerChangeWorld);
		ServerTickEvents.END_SERVER_TICK.register(BodyConstructionService::updateServer);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer)
					|| level.getBlockState(hit.getBlockPos()).getBlock() != ModBlocks.NETHER_RULE_FRACTURE_CORE) {
				return InteractionResult.PASS;
			}
			return observeNetherRift(serverPlayer, hit.getBlockPos())
					? InteractionResult.SUCCESS : InteractionResult.FAIL;
		});
	}

	private static void afterPlayerChangeWorld(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
		boolean validPair = origin.dimension() == Level.OVERWORLD && destination.dimension() == Level.NETHER
				|| origin.dimension() == Level.NETHER && destination.dimension() == Level.OVERWORLD;
		if (!validPair) {
			return;
		}
		FrequencyWorldData data = FrequencyWorldData.get(destination.getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (!eligibleForNether(record)) {
			return;
		}
		String originId = origin.dimension().identifier().toString();
		String destinationId = destination.dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), tag -> {
			int transitions = tag.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0) + 1;
			tag.putInt(TerminalData.PORTAL_TRANSITIONS, transitions);
			tag.putBoolean(TerminalData.CONTINUITY_LEARNED, true);
			tag.putInt(TerminalData.CONTINUITY_CONFIDENCE, Math.min(100, transitions * 25));
			tag.putString(TerminalData.LAST_PORTAL_ORIGIN, originId);
			tag.putString(TerminalData.LAST_PORTAL_DESTINATION, destinationId);
			addCapability(tag, "identity_continuity");
		});
		TerminalLifecycleService.recordCurrentDimension(player);
		TerminalLifecycleService.ensureCarried(player, false);
		sendPrivateAnomaly(player, destination.dimension() == Level.NETHER ? "continuity" : "return");
	}

	private static void updateServer(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!FinaleRuntimePolicy.backgroundSystemsAllowed(data)) {
			return;
		}
		if (!data.narrativeState().getBooleanOr(ARCHIVE_UNLOCKED, false)) {
			return;
		}
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether != null) {
			ensureNetherRift(nether, data);
			buildNetherRift(nether, data);
		}
	}

	private static void ensureNetherRift(ServerLevel nether, FrequencyWorldData data) {
		if (data.narrativeState().contains(NETHER_RIFT)) {
			return;
		}
		ServerPlayer anchor = nether.players().stream().filter(player ->
				eligibleForNether(data.terminalRecord(player.getUUID()).orElse(null))).findFirst().orElse(null);
		if (anchor == null) {
			return;
		}
		int y = Math.clamp(anchor.blockPosition().getY(), nether.getMinY() + 8,
				nether.getMinY() + nether.getHeight() - 8);
		BlockPos origin = new BlockPos(anchor.blockPosition().getX() + 12, y, anchor.blockPosition().getZ());
		CompoundTag state = new CompoundTag();
		state.putLong("origin", origin.asLong());
		state.putInt("cursor", 0);
		state.putBoolean("complete", false);
		state.putInt("observation_count", 0);
		data.updateNarrativeState(narrative -> narrative.put(NETHER_RIFT, state));
	}

	private static void buildNetherRift(ServerLevel level, FrequencyWorldData data) {
		CompoundTag state = netherRiftState(data);
		if (state.isEmpty() || state.getBooleanOr("complete", false)) {
			return;
		}
		BlockPos origin = BlockPos.of(state.getLongOr("origin", 0L));
		List<NetherRiftLayout.Placement> plan = NetherRiftLayout.create(origin);
		int cursor = Math.clamp(state.getIntOr("cursor", 0), 0, plan.size());
		int placed = 0;
		while (cursor < plan.size() && placed < BUILD_BUDGET_PER_TICK) {
			NetherRiftLayout.Placement placement = plan.get(cursor);
			if (!level.hasChunkAt(placement.position())) {
				break;
			}
			level.setBlock(placement.position(), placement.state(), 3);
			cursor++;
			placed++;
		}
		if (cursor != state.getIntOr("cursor", 0)) {
			int storedCursor = cursor;
			int storedWork = placed;
			data.updateNarrativeState(narrative -> {
				CompoundTag updated = narrative.getCompoundOrEmpty(NETHER_RIFT).copy();
				updated.putInt("cursor", storedCursor);
				updated.putBoolean("complete", storedCursor == plan.size());
				updated.putInt("last_tick_work", storedWork);
				updated.putInt("maximum_tick_work", Math.max(storedWork,
						updated.getIntOr("maximum_tick_work", 0)));
				narrative.put(NETHER_RIFT, updated);
			});
		}
	}

	public static boolean observeNetherRift(ServerPlayer player, BlockPos position) {
		if (player.level().dimension() != Level.NETHER) {
			return false;
		}
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag state = netherRiftState(data);
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (!state.getBooleanOr("complete", false) || !eligibleForNether(record)
				|| !BlockPos.of(state.getLongOr("origin", 0L)).equals(position)) {
			return false;
		}
		boolean first = !record.getBooleanOr(TerminalData.NETHER_RIFT_OBSERVED, false);
		if (first) {
			data.updateTerminalRecord(player.getUUID(), tag -> {
				tag.putBoolean(TerminalData.NETHER_RIFT_OBSERVED, true);
				tag.putInt(TerminalData.PLOT_STAGE, Math.max(5, tag.getIntOr(TerminalData.PLOT_STAGE, 1)));
				addCapability(tag, "fracture_resonance");
			});
			data.updateNarrativeState(narrative -> {
				CompoundTag updated = narrative.getCompoundOrEmpty(NETHER_RIFT).copy();
				updated.putInt("observation_count", updated.getIntOr("observation_count", 0) + 1);
				narrative.put(NETHER_RIFT, updated);
			});
		}
		TerminalLifecycleService.ensureCarried(player, false);
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.nether_rift.observed"));
		sendPrivateAnomaly(player, "fracture");
		return true;
	}

	private static void ensureModelDefaults(ServerPlayer player, FrequencyWorldData data) {
		CompoundTag record = data.terminalRecord(player.getUUID()).orElseThrow();
		if (record.contains(TerminalData.TERMINAL_CAPABILITIES)) {
			return;
		}
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putString(TerminalData.TERMINAL_CAPABILITIES,
					"environment;life;activity;resource_guidance;relation_inference");
			tag.putLong(TerminalData.PRIVATE_ANOMALY_SEED,
					tag.getLongOr(TerminalData.PERSONALITY_SEED, 0L) ^ 0x5446464D364C4F4EL);
			tag.putInt(TerminalData.PRIVATE_ANOMALY_VARIANT,
					Math.floorMod(tag.getIntOr(TerminalData.CACHE_VARIANT, 0), 4));
		});
	}

	private static void sendPrivateAnomaly(ServerPlayer player, String anomalyId) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag before = data.terminalRecord(player.getUUID()).orElseThrow();
		int count = before.getIntOr(TerminalData.PRIVATE_ANOMALY_COUNT, 0) + 1;
		int variant = Math.floorMod(before.getIntOr(TerminalData.CACHE_VARIANT, 0) + count - 1, 4);
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putInt(TerminalData.PRIVATE_ANOMALY_COUNT, count);
			tag.putInt(TerminalData.PRIVATE_ANOMALY_VARIANT, variant);
		});
		CompoundTag record = data.terminalRecord(player.getUUID()).orElseThrow();
		ServerPlayNetworking.send(player, new PrivateAnomalyPayload(anomalyId, variant,
				Math.min(1_000, SurvivalProgressService.completedCount(record) * 125), capabilityMask(record)));
		if (player.level() instanceof ServerLevel level) {
			AudioService.play(level, player.blockPosition(), AudioService.Cue.FOURTH_BAND);
		}
		TerminalLifecycleService.ensureCarried(player, false);
	}

	private static boolean eligibleForNether(CompoundTag record) {
		return record != null && record.getBooleanOr(TerminalData.BOUND, false)
				&& SurvivalMilestone.PREPARED_NETHER.present(
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0));
	}

	private static void addCapability(CompoundTag tag, String capability) {
		String current = tag.getStringOr(TerminalData.TERMINAL_CAPABILITIES, "");
		for (String entry : current.split(";")) {
			if (entry.equals(capability)) {
				return;
			}
		}
		tag.putString(TerminalData.TERMINAL_CAPABILITIES,
				current.isBlank() ? capability : current + ";" + capability);
	}

	private static int capabilityMask(CompoundTag record) {
		String capabilities = record.getStringOr(TerminalData.TERMINAL_CAPABILITIES, "");
		int mask = 0;
		if (capabilities.contains("identity_continuity")) mask |= 1;
		if (capabilities.contains("fracture_resonance")) mask |= 2;
		if (capabilities.contains("private_differentiation")) mask |= 4;
		if (capabilities.contains("body_mapping")) mask |= 8;
		if (capabilities.contains("behavior_prediction")) mask |= 16;
		return mask;
	}

	public static CompoundTag netherRiftState(FrequencyWorldData data) {
		return data.narrativeState().getCompoundOrEmpty(NETHER_RIFT).copy();
	}

	public static int buildBudgetPerTick() {
		return BUILD_BUDGET_PER_TICK;
	}
}
