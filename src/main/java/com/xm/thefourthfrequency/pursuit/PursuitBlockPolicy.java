package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Mirror-only interaction rules: no extraction, no machinery, temporary simple placement. */
public final class PursuitBlockPolicy {
	private static final Map<MinecraftServer, List<PendingPlacement>> PENDING = new IdentityHashMap<>();
	private static boolean initialized;

	private PursuitBlockPolicy() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer) || !PursuitDimensions.isMirror(level)) return true;
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
					| Block.UPDATE_SUPPRESS_DROPS);
			return false;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
					|| !PursuitDimensions.isMirror(level)) return InteractionResult.PASS;
			if (!activeSession(serverPlayer)) return InteractionResult.FAIL;
			if (level.getBlockEntity(hit.getBlockPos()) != null) return InteractionResult.FAIL;
			ItemStack held = player.getItemInHand(hand);
			if (!(held.getItem() instanceof BlockItem blockItem)) return InteractionResult.PASS;
			if (!simplePlacement(blockItem.getBlock())) return InteractionResult.FAIL;
			BlockPos hitPos = hit.getBlockPos().immutable();
			BlockPos adjacent = hitPos.relative(hit.getDirection()).immutable();
			PENDING.computeIfAbsent(serverPlayer.level().getServer(), ignored -> new ArrayList<>())
					.add(new PendingPlacement(serverPlayer, blockItem.getBlock(), held.copyWithCount(1),
							hitPos, adjacent, level.getBlockState(hitPos), level.getBlockState(adjacent)));
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!PursuitDimensions.isMirror(level)) return InteractionResult.PASS;
			ItemStack held = player.getItemInHand(hand);
			return held.getItem() instanceof BucketItem || held.is(Items.FLINT_AND_STEEL)
					|| held.is(Items.FIRE_CHARGE) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(PursuitBlockPolicy::verifyPlacements);
		ServerLifecycleEvents.SERVER_STOPPED.register(PENDING::remove);
	}

	public static BlockState sanitizeSnapshotState(ServerLevel source, BlockPos position, BlockState state) {
		if (source.getBlockEntity(position) != null || state.hasBlockEntity()) {
			return state.getCollisionShape(source, position).isEmpty()
					? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
		}
		Block block = state.getBlock();
		if (!safeSnapshotBlock(block)) {
			return state.getCollisionShape(source, position).isEmpty()
					? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
		}
		return state;
	}

	public static boolean simplePlacement(Block block) {
		return safeSnapshotBlock(block) && !block.defaultBlockState().hasBlockEntity()
				&& block != Blocks.RESPAWN_ANCHOR;
	}

	private static boolean safeSnapshotBlock(Block block) {
		if (block == Blocks.TNT || block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL
				|| block == Blocks.END_GATEWAY || block == Blocks.FIRE || block == Blocks.SOUL_FIRE) return false;
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return !(path.contains("redstone") || path.contains("repeater") || path.contains("comparator")
				|| path.contains("piston") || path.contains("observer") || path.contains("dispenser")
				|| path.contains("dropper") || path.contains("hopper") || path.contains("rail")
				|| path.contains("sculk_sensor") || path.contains("tripwire") || path.contains("pressure_plate")
				|| path.contains("button") || path.contains("lever") || path.contains("portal")
				|| path.endsWith("_bed"));
	}

	private static boolean activeSession(ServerPlayer player) {
		return FrequencyWorldData.get(player.level().getServer()).terminalRecord(player.getUUID())
				.map(record -> record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false))
				.orElse(false);
	}

	private static void verifyPlacements(MinecraftServer server) {
		List<PendingPlacement> pending = PENDING.remove(server);
		if (pending == null) return;
		for (PendingPlacement placement : pending) {
			if (!placement.player.isAlive() || !PursuitDimensions.isMirror(placement.player.level())
					|| !activeSession(placement.player)) continue;
			BlockState hitNow = placement.player.level().getBlockState(placement.hitPos);
			BlockState adjacentNow = placement.player.level().getBlockState(placement.adjacentPos);
			boolean hitChanged = !hitNow.equals(placement.hitBefore) && hitNow.is(placement.block);
			boolean adjacentChanged = !adjacentNow.equals(placement.adjacentBefore)
					&& adjacentNow.is(placement.block);
			if (hitChanged || adjacentChanged) {
				PursuitRecoveryLedger.recordPlacement(placement.player, placement.refund);
			}
		}
	}

	private record PendingPlacement(ServerPlayer player, Block block, ItemStack refund,
			BlockPos hitPos, BlockPos adjacentPos, BlockState hitBefore, BlockState adjacentBefore) {
	}
}
