package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.correction.CorrectionState;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.pursuit.PursuitDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Server-authoritative vanilla-survival milestones replacing the legacy timed body counter. */
public final class SurvivalProgressService {
	public static final int REQUIRED_WOOD = 12;
	/** Stable compatibility alias for older tests and saved objective wording. */
	public static final int REQUIRED_LOGS = REQUIRED_WOOD;
	public static final int REQUIRED_IRON = 12;
	public static final int REQUIRED_BLAZE_RODS = 8;
	public static final int REQUIRED_CRAFTED_EYES = 4;
	public static final int REQUIRED_EYE_SAMPLES = 3;
	private static final int SCENE_ANOMALY = 1;
	private static final int SCENE_CORRECTION = 1 << 1;
	private static final int SCENE_EXPLAINED = 1 << 2;
	private static boolean initialized;

	private SurvivalProgressService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(SurvivalProgressService::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) updatePlayer(player, data);
	}

	public static void updatePlayer(ServerPlayer player, FrequencyWorldData data) {
		if (PursuitDimensions.isMirror(player.level())) return;
		CompoundTag before = data.terminalRecord(player.getUUID()).orElse(null);
		if (before == null) return;
		int oldMask = before.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		int wood = Math.max(before.getIntOr(TerminalData.WOOD_MINED_COUNT, 0), collectedWood(player));
		int iron = Math.max(before.getIntOr(TerminalData.IRON_SAMPLE_COUNT, 0), ironSamples(player));
		int blazeRods = Math.max(before.getIntOr(TerminalData.BLAZE_ROD_SAMPLE_COUNT, 0), blazeRodSamples(player));
		int craftedEyes = Math.max(before.getIntOr(TerminalData.CRAFTED_EYE_COUNT, 0), craftedEyeSamples(player));
		int add = 0;
		if (wood >= REQUIRED_WOOD) add |= SurvivalMilestone.MINED_LOGS.mask();
		if (hasHome(player, before)) add |= SurvivalMilestone.HOME.mask();
		if (iron >= REQUIRED_IRON) add |= SurvivalMilestone.IRON.mask();
		if (preparedForNether(player)) add |= SurvivalMilestone.PREPARED_NETHER.mask();
		if (blazeRods >= REQUIRED_BLAZE_RODS) add |= SurvivalMilestone.COLLECTED_BLAZE_RODS.mask();
		if (craftedEyes >= REQUIRED_CRAFTED_EYES) add |= SurvivalMilestone.CRAFTED_EYE.mask();
		if (before.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0) > 0) {
			add |= SurvivalMilestone.THREW_EYE.mask();
		}
		if (nearRecordedStronghold(player, before)) add |= SurvivalMilestone.FOUND_STRONGHOLD.mask();
		if (player.level().dimension() == Level.END) {
			add |= SurvivalMilestone.ENTERED_END.mask();
		}
		int newMask = oldMask | add;
		if (newMask != oldMask || wood != before.getIntOr(TerminalData.WOOD_MINED_COUNT, 0)
				|| iron != before.getIntOr(TerminalData.IRON_SAMPLE_COUNT, 0)
				|| blazeRods != before.getIntOr(TerminalData.BLAZE_ROD_SAMPLE_COUNT, 0)
				|| craftedEyes != before.getIntOr(TerminalData.CRAFTED_EYE_COUNT, 0)) {
			data.updateTerminalRecord(player.getUUID(), tag -> {
				tag.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, newMask);
				tag.putInt(TerminalData.WOOD_MINED_COUNT, Math.clamp(wood, 0, REQUIRED_WOOD));
				tag.putInt(TerminalData.IRON_SAMPLE_COUNT, Math.clamp(iron, 0, REQUIRED_IRON));
				tag.putInt(TerminalData.BLAZE_ROD_SAMPLE_COUNT, Math.clamp(blazeRods, 0, REQUIRED_BLAZE_RODS));
				tag.putInt(TerminalData.CRAFTED_EYE_COUNT,
						Math.clamp(craftedEyes, 0, REQUIRED_CRAFTED_EYES));
			});
		}
		CompoundTag current = data.terminalRecord(player.getUUID()).orElse(before);
		maybeStartSignatureScene(player, data, current);
		maybeExplainRecoveredTools(player, data, current);
		TerminalRuntimeService.synchronizeAttentionProjection(player, data);
	}

	public static boolean mark(ServerPlayer player, SurvivalMilestone milestone) {
		if (PursuitDimensions.isMirror(player.level())) return false;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || milestone.present(record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0))) return false;
		data.updateTerminalRecord(player.getUUID(), tag -> tag.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
				tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0) | milestone.mask()));
		TerminalRuntimeService.refresh(player);
		return true;
	}

	public static void recordPortalTransition(ServerPlayer player, Level origin, Level destination) {
		if (PursuitDimensions.isMirror(origin) || PursuitDimensions.isMirror(destination)) return;
		if (origin.dimension() == Level.OVERWORLD && destination.dimension() == Level.NETHER) {
			mark(player, SurvivalMilestone.ENTERED_NETHER);
		} else if (origin.dimension() == Level.NETHER && destination.dimension() == Level.OVERWORLD) {
			mark(player, SurvivalMilestone.RETURNED_NETHER);
		} else if (destination.dimension() == Level.END) {
			mark(player, SurvivalMilestone.ENTERED_END);
		}
	}

	public static int collectedWood(ServerPlayer player) {
		int mined = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			if (isWoodMaterial(block)) {
				mined += player.getStats().getValue(Stats.BLOCK_MINED, block);
				if (mined >= REQUIRED_WOOD) return REQUIRED_WOOD;
			}
		}
		int carried = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof BlockItem blockItem && isWoodMaterial(blockItem.getBlock())) {
				carried += stack.getCount();
				if (carried >= REQUIRED_WOOD) return REQUIRED_WOOD;
			}
		}
		return Math.clamp(Math.max(mined, carried), 0, REQUIRED_WOOD);
	}

	/** Kept as a source-compatible probe; the opening task now accepts logs and planks of every wood family. */
	public static int minedLogs(ServerPlayer player) {
		return collectedWood(player);
	}

	private static boolean isWoodMaterial(Block block) {
		return block.defaultBlockState().is(BlockTags.LOGS)
				|| block.defaultBlockState().is(BlockTags.PLANKS);
	}

	public static int blazeRodSamples(ServerPlayer player) {
		int pickedUp = player.getStats().getValue(Stats.ITEM_PICKED_UP, Items.BLAZE_ROD);
		int craftedPowder = player.getStats().getValue(Stats.ITEM_CRAFTED, Items.BLAZE_POWDER);
		int craftedEyes = player.getStats().getValue(Stats.ITEM_CRAFTED, Items.ENDER_EYE);
		int inventoryEquivalent = count(player, Items.BLAZE_ROD)
				+ (count(player, Items.BLAZE_POWDER) + count(player, Items.ENDER_EYE) + 1) / 2;
		return Math.clamp(Math.max(Math.max(pickedUp, inventoryEquivalent),
				Math.max(craftedPowder / 2, (craftedEyes + 1) / 2)), 0, REQUIRED_BLAZE_RODS);
	}

	public static int ironSamples(ServerPlayer player) {
		int pickedUp = player.getStats().getValue(Stats.ITEM_PICKED_UP, Items.RAW_IRON)
				+ player.getStats().getValue(Stats.ITEM_PICKED_UP, Items.IRON_INGOT);
		int carried = count(player, Items.RAW_IRON) + count(player, Items.IRON_INGOT);
		return Math.clamp(Math.max(pickedUp, carried), 0, REQUIRED_IRON);
	}

	public static int craftedEyeSamples(ServerPlayer player) {
		int crafted = player.getStats().getValue(Stats.ITEM_CRAFTED, Items.ENDER_EYE);
		int accounted = count(player, Items.ENDER_EYE);
		return Math.clamp(Math.max(crafted, accounted), 0, REQUIRED_CRAFTED_EYES);
	}

	private static boolean hasHome(ServerPlayer player, CompoundTag tag) {
		return player.getRespawnConfig() != null;
	}

	private static boolean preparedForNether(ServerPlayer player) {
		if (hasAny(player, Items.FLINT_AND_STEEL) || count(player, Items.OBSIDIAN) >= 10) return true;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).isEnchanted()) return true;
		}
		return false;
	}

	private static boolean hasAny(ServerPlayer player, Item... items) {
		for (Item item : items) if (count(player, item) > 0) return true;
		return false;
	}

	private static int count(ServerPlayer player, Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) count += stack.getCount();
		}
		return count;
	}

	private static boolean nearRecordedStronghold(ServerPlayer player, CompoundTag tag) {
		if (tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0) < REQUIRED_EYE_SAMPLES) return false;
		if (!player.level().dimension().identifier().toString().equals(
				tag.getStringOr(TerminalData.STRONGHOLD_DIMENSION, ""))) return false;
		BlockPos target = BlockPos.of(tag.getLongOr(TerminalData.STRONGHOLD_POSITION, 0L));
		return player.blockPosition().distSqr(target) <= 128L * 128L;
	}

	private static void maybeStartSignatureScene(ServerPlayer player, FrequencyWorldData data, CompoundTag record) {
		int milestones = record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		int scenes = record.getIntOr(TerminalData.SIGNATURE_SCENE_MASK, 0);
		if (!SurvivalMilestone.IRON.present(milestones) || (scenes & SCENE_ANOMALY) != 0
				|| player.level().dimension() != Level.OVERWORLD) return;
		BlockPos trace = findTracePosition(player);
		if (trace == null) return;
		player.level().setBlockAndUpdate(trace, ModBlocks.NASCENT_BODY_ORGAN.defaultBlockState());
		CorrectionState.setOrganPosition(data, trace, player.getUUID());
		long now = player.level().getGameTime();
		long disabledUntil = now
				+ (RuntimeServices.config().pacing().developerAcceleration() ? 200L : 1_200L);
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putInt(TerminalData.SIGNATURE_SCENE_MASK,
					tag.getIntOr(TerminalData.SIGNATURE_SCENE_MASK, 0) | SCENE_ANOMALY | SCENE_CORRECTION);
			tag.putLong(TerminalData.TOOLS_DISABLED_UNTIL, disabledUntil);
			appendOnce(tag, player, "signature_anomaly", trace, 2);
			appendOnce(tag, player, "signature_correction", trace, 1);
		});
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.signature.started"));
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}

	private static BlockPos findTracePosition(ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		for (BlockPos position : List.of(origin.offset(6, 0, 0), origin.offset(-6, 0, 0),
				origin.offset(0, 0, 6), origin.offset(0, 0, -6), origin.offset(4, 1, 4))) {
			if (player.level().isLoaded(position) && player.level().getBlockState(position).isAir()
					&& player.level().getBlockState(position.above()).isAir()) return position;
		}
		return null;
	}

	private static void maybeExplainRecoveredTools(ServerPlayer player, FrequencyWorldData data, CompoundTag record) {
		int scenes = record.getIntOr(TerminalData.SIGNATURE_SCENE_MASK, 0);
		if ((scenes & (SCENE_ANOMALY | SCENE_EXPLAINED)) != SCENE_ANOMALY) return;
		if (record.getLongOr(TerminalData.TOOLS_DISABLED_UNTIL, 0L) > player.level().getGameTime()) return;
		completeSceneForPlayer(player, data, player.blockPosition());
	}

	public static void completeCorrectionScene(MinecraftServer server, BlockPos position) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		var owner = CorrectionState.anomalyTraceOwner(data, position);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (owner.isPresent() && !owner.get().equals(player.getUUID())) continue;
			if (data.terminalRecord(player.getUUID()).isPresent()) completeSceneForPlayer(player, data, position);
		}
	}

	private static void completeSceneForPlayer(ServerPlayer player, FrequencyWorldData data, BlockPos position) {
		CompoundTag before = data.terminalRecord(player.getUUID()).orElse(null);
		if (before == null || (before.getIntOr(TerminalData.SIGNATURE_SCENE_MASK, 0) & SCENE_EXPLAINED) != 0) return;
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putInt(TerminalData.SIGNATURE_SCENE_MASK,
					tag.getIntOr(TerminalData.SIGNATURE_SCENE_MASK, 0) | SCENE_EXPLAINED);
			tag.putLong(TerminalData.TOOLS_DISABLED_UNTIL, 0L);
			appendOnce(tag, player, "signature_explained", position, 1);
		});
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.signature.recovered"));
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}

	private static void appendOnce(CompoundTag tag, ServerPlayer player, String type, BlockPos position, int severity) {
		if (TerminalSignalLog.containsType(tag, type)) return;
		TerminalSignalLog.append(tag, SignalBand.UNKNOWN, type, player.level().getGameTime(), player.level().getDayTime(),
				player.level().dimension().identifier().toString(), position.asLong(), 0, severity, true);
	}

	public static int completedCount(CompoundTag tag) {
		return Integer.bitCount(tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
				& SurvivalMilestone.knownMask());
	}
}
