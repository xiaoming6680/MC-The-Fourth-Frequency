package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.terminal.AmbientAnomalyService;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalStructureTarget;
import com.xm.thefourthfrequency.terminal.TerminalTaskService;
import com.xm.thefourthfrequency.pursuit.PursuitDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Server-authoritative vanilla-survival milestones replacing the legacy timed body counter. */
public final class SurvivalProgressService {
	public static final int REQUIRED_WOOD = 12;
	/** Stable compatibility alias for older tests and saved objective wording. */
	public static final int REQUIRED_LOGS = REQUIRED_WOOD;
	public static final int REQUIRED_IRON = 6;
	public static final int REQUIRED_BLAZE_RODS = 8;
	public static final int REQUIRED_STRONGHOLD_UNLOCK_EYES = 3;
	public static final int REQUIRED_CRAFTED_EYES = 4;
	public static final int REQUIRED_EYE_SAMPLES = 3;
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
		if (iron >= REQUIRED_IRON) add |= SurvivalMilestone.IRON.mask();
		if (preparedForNether(player)) add |= SurvivalMilestone.PREPARED_NETHER.mask();
		if (blazeRods >= REQUIRED_BLAZE_RODS) {
			// Blazes spawn nowhere else, so rods in hand are proof of a fortress visited whether or not
			// the player was carrying the terminal when they walked through it. Without this the task
			// list deadlocks: it advances strictly in order, so a player who stumbled into a fortress
			// and left with eight rods before the terminal ever named the objective would be held at
			// "find a fortress" with the thing already behind them.
			add |= SurvivalMilestone.COLLECTED_BLAZE_RODS.mask() | SurvivalMilestone.FOUND_FORTRESS.mask();
		}
		if (craftedEyes >= REQUIRED_CRAFTED_EYES) add |= SurvivalMilestone.CRAFTED_EYE.mask();
		if (before.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0) > 0) {
			add |= SurvivalMilestone.THREW_EYE.mask();
		}
		if (insideNetherFortress(player)) add |= SurvivalMilestone.FOUND_FORTRESS.mask();
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
		// Progress changing is the moment a task can complete, so it is the moment its reward is owed.
		// While this only synchronized the attention projection, a finished task lit the terminal up
		// and then waited for the player to open a page or trip a signal before paying out - which is
		// the gap the old manual claim button existed to cover. Delivery is added to what this call
		// already did rather than replacing it: the attention projection has other inputs than tasks.
		TerminalTaskService.notifyIfCompleted(player);
		TerminalRuntimeService.synchronizeAttentionProjection(player, data);
	}

	public static boolean mark(ServerPlayer player, SurvivalMilestone milestone) {
		if (PursuitDimensions.isMirror(player.level())) return false;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || milestone.present(record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0))) return false;
		data.updateTerminalRecord(player.getUUID(), tag -> tag.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
				tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0) | milestone.mask()));
		// Milestones that are marked rather than counted (entering the Nether, finding the stronghold)
		// complete their task here and nowhere else, so they pay out here too - in addition to the
		// refresh this always owed, not instead of it.
		TerminalRuntimeService.refresh(player);
		TerminalTaskService.notifyIfCompleted(player);
		if (milestone == SurvivalMilestone.ENTERED_NETHER || milestone == SurvivalMilestone.THREW_EYE) {
			AmbientAnomalyService.scheduleSignature(player);
		}
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

	/**
	 * Whether the player is standing in a Nether fortress right now.
	 *
	 * <p>The same test the structure navigator calls arrival - one of the structure's own generated
	 * pieces is under the player's feet - rather than a radius around a located position. A fortress
	 * sprawls, so any single point plus a radius is either wide enough to fire from outside it or
	 * tight enough to miss most of the inside; the pieces are the building.
	 *
	 * <p>Reads only what generation has already placed. {@code getStructureWithPieceAt} does not force
	 * chunks, and the player standing there means the chunk is loaded anyway - so this stays a lookup
	 * rather than a search, which is why it can sit in the once-a-second poll with the rest.
	 *
	 * <p>Nether-only as a cheap guard: {@link TerminalStructureTarget#FORTRESS}'s tag holds the Nether
	 * fortress, and asking any other dimension for it is work that can only ever answer no.
	 */
	private static boolean insideNetherFortress(ServerPlayer player) {
		if (player.level().dimension() != Level.NETHER) return false;
		TagKey<Structure> fortress = TerminalStructureTarget.FORTRESS.structureTag();
		if (fortress == null) return false;
		// Never null: a miss comes back as StructureStart.INVALID_START, so isValid is the real test.
		return player.level().structureManager()
				.getStructureWithPieceAt(player.blockPosition(), fortress).isValid();
	}

	private static boolean nearRecordedStronghold(ServerPlayer player, CompoundTag tag) {
		if (tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0) < REQUIRED_EYE_SAMPLES) return false;
		if (!player.level().dimension().identifier().toString().equals(
				tag.getStringOr(TerminalData.STRONGHOLD_DIMENSION, ""))) return false;
		BlockPos target = BlockPos.of(tag.getLongOr(TerminalData.STRONGHOLD_POSITION, 0L));
		return player.blockPosition().distSqr(target) <= 128L * 128L;
	}

	public static int completedCount(CompoundTag tag) {
		return Integer.bitCount(tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
				& SurvivalMilestone.knownMask());
	}
}
