package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.WatcherService;
import com.xm.thefourthfrequency.entity.WatcherEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded server-side implementations for reality-changing anomalies. */
public final class AnomalyServerEffects {
	private static final Map<ServerPlayer, AlignmentTask> ALIGNMENTS = new HashMap<>();
	private static final Map<ServerPlayer, DoorCascadeTask> DOORS = new HashMap<>();
	private static final Map<ServerPlayer, MovementTask> MOVEMENTS = new HashMap<>();
	private static boolean initialized;

	private AnomalyServerEffects() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(AnomalyServerEffects::tick);
	}

	public static EffectLease begin(ServerPlayer player, AnomalyDefinition definition, int durationTicks,
			long seed, AnomalyRuntimeService.Anchor anchor) {
		return switch (definition.id()) {
			case "watcher_alignment" -> alignment(player, durationTicks);
			case "dark_watcher" -> watcher(player, durationTicks);
			case "door_cascade" -> doors(player, seed);
			case "experience_gap" -> movement(player, durationTicks);
			default -> new EffectLease(() -> { });
		};
	}

	private static EffectLease alignment(ServerPlayer player, int durationTicks) {
		AlignmentTask task = new AlignmentTask(player, player.level().getGameTime() + durationTicks);
		ALIGNMENTS.put(player, task);
		return new EffectLease(() -> ALIGNMENTS.remove(player));
	}

	private static EffectLease watcher(ServerPlayer player, int durationTicks) {
		WatcherEntity watcher = WatcherService.spawnAnomaly(player, durationTicks);
		return watcher == null ? null : new EffectLease(() -> { if (watcher.isAlive()) watcher.discard(); });
	}

	private static EffectLease doors(ServerPlayer player, long seed) {
		DoorCascadeTask task = DoorCascadeTask.create(player, seed);
		if (task == null) return null;
		DOORS.put(player, task);
		return new EffectLease(() -> {
			DoorCascadeTask removed = DOORS.remove(player);
			if (removed != null) removed.clearProgress();
		});
	}

	private static EffectLease movement(ServerPlayer player, int durationTicks) {
		MovementTask task = MovementTask.create(player, durationTicks);
		if (task == null) return null;
		MOVEMENTS.put(player, task);
		return new EffectLease(() -> {
			MovementTask removed = MOVEMENTS.remove(player);
			if (removed != null) removed.stop();
		});
	}

	private static void tick(MinecraftServer server) {
		for (AlignmentTask task : List.copyOf(ALIGNMENTS.values())) task.tick(server);
		for (DoorCascadeTask task : List.copyOf(DOORS.values())) task.tick();
		for (MovementTask task : List.copyOf(MOVEMENTS.values())) task.tick(server);
	}

	private record AlignmentTask(ServerPlayer player, long endTick) {
		private void tick(MinecraftServer server) {
			if (player.isRemoved() || player.level().getGameTime() >= endTick) {
				ALIGNMENTS.remove(player);
				return;
			}
			AABB range = player.getBoundingBox().inflate(30.0D);
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, range, Mob::isAlive)) {
				mob.getLookControl().setLookAt(player, 360.0F, 360.0F);
				Vec3 delta = player.getEyePosition().subtract(mob.getEyePosition());
				float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
				mob.setYHeadRot(yaw);
			}
		}
	}

	private static final class DoorCascadeTask {
		private static final int SEARCH_RADIUS = 20;
		private static final int BREAK_TICKS = 10;
		private final ServerLevel level;
		private final ServerPlayer player;
		private final List<BlockPos> doors;
		private int age;
		private DoorCascadeTask(ServerLevel level, ServerPlayer player, List<BlockPos> doors) {
			this.level = level; this.player = player; this.doors = doors;
		}

		private static DoorCascadeTask create(ServerPlayer player, long seed) {
			if (!(player.level() instanceof ServerLevel level)) return null;
			FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
			List<BlockPos> candidates = new ArrayList<>();
			BlockPos origin = player.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -6, -SEARCH_RADIUS),
					origin.offset(SEARCH_RADIUS, 6, SEARCH_RADIUS))) {
				if (pos.distSqr(origin) > SEARCH_RADIUS * SEARCH_RADIUS || protectedPosition(level, data, pos)) continue;
				BlockState state = level.getBlockState(pos);
				if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
						|| state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) continue;
				BlockPos upper = pos.above();
				if (!(level.getBlockState(upper).getBlock() instanceof DoorBlock) || protectedPosition(level, data, upper)) continue;
				candidates.add(pos.immutable());
			}
			candidates.sort(Comparator.comparingDouble((BlockPos value) -> value.distSqr(origin)).reversed());
			int count = doorCount(candidates.size(), seed);
			if (count < 2) return null;
			return new DoorCascadeTask(level, player, List.copyOf(candidates.subList(0, count)));
		}

		private void tick() {
			int doorIndex = age / BREAK_TICKS;
			int stageAge = age % BREAK_TICKS;
			if (doorIndex < doors.size()) {
				BlockPos lower = doors.get(doorIndex);
				BlockState state = level.getBlockState(lower);
				int breaker = breakerId(player, doorIndex);
				if (state.getBlock() instanceof DoorBlock) {
					level.destroyBlockProgress(breaker, lower, stageAge);
					if (stageAge == BREAK_TICKS - 1) {
						level.destroyBlockProgress(breaker, lower, -1);
						level.levelEvent(2001, lower, Block.getId(state));
						level.playSound(null, lower, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
								SoundSource.BLOCKS, 1.15F, 0.82F);
						level.playSound(null, lower, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
						// Suppress paired-door neighbor drops; particles and material audio were sent above.
						level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), 2);
						level.setBlock(lower, Blocks.AIR.defaultBlockState(), 2);
						level.getEntitiesOfClass(ItemEntity.class, new AABB(lower).inflate(2.0D),
								item -> item.getItem().is(state.getBlock().asItem())).forEach(ItemEntity::discard);
					}
				}
			}
			age++;
			if (doorIndex >= doors.size()) DOORS.remove(player);
		}

		private void clearProgress() {
			for (int index = 0; index < doors.size(); index++)
				level.destroyBlockProgress(breakerId(player, index), doors.get(index), -1);
		}
	}

	public static int doorCount(int candidates, long seed) {
		return AnomalySelectionRules.doorCount(candidates, seed);
	}

	public static List<BlockPos> safeMovementPath(ServerLevel level, BlockPos origin) {
		List<BlockPos> best = List.of();
		for (int direction = 0; direction < 16; direction++) {
			double angle = direction * Math.PI * 2.0D / 16.0D;
			List<BlockPos> path = new ArrayList<>();
			Set<BlockPos> visited = new HashSet<>();
			BlockPos previous = origin;
			for (int distance = 1; distance <= 24; distance++) {
				int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
				int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
				BlockPos next = safeStep(level, x, previous.getY(), z);
				if (next == null || !visited.add(next)) break;
				path.add(next);
				previous = next;
			}
			if (path.size() == 24) return List.copyOf(path);
			if (path.size() > best.size()) best = List.copyOf(path);
		}
		return best.size() >= 12 ? best : List.of();
	}

	private static BlockPos safeStep(ServerLevel level, int x, int previousY, int z) {
		for (int offset : new int[] { 0, 1, -1 }) {
			BlockPos feet = new BlockPos(x, previousY + offset, z);
			BlockState floor = level.getBlockState(feet.below());
			if (!level.hasChunkAt(feet) || !floor.isFaceSturdy(level, feet.below(), Direction.UP)
					|| hazardous(floor) || hazardous(level.getBlockState(feet))
					|| hazardous(level.getBlockState(feet.above()))) continue;
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return feet;
		}
		return null;
	}

	private static boolean hazardous(BlockState state) {
		return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.POWDER_SNOW)
				|| state.getFluidState().is(Fluids.LAVA);
	}

	private static final class MovementTask {
		private static final double MAX_SPEED = 0.55D;
		private final ServerPlayer player;
		private final List<BlockPos> path;
		private final int duration;
		private int age;
		private int waypoint;
		private MovementTask(ServerPlayer player, List<BlockPos> path, int duration) {
			this.player = player; this.path = path; this.duration = duration;
		}
		private static MovementTask create(ServerPlayer player, int duration) {
			if (!(player.level() instanceof ServerLevel level)) return null;
			List<BlockPos> path = safeMovementPath(level, player.blockPosition());
			return path.isEmpty() ? null : new MovementTask(player, path, duration);
		}
		private void tick(MinecraftServer server) {
			if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) { MOVEMENTS.remove(player); return; }
			while (waypoint < path.size()
					&& horizontalDelta(Vec3.atBottomCenterOf(path.get(waypoint)), player.position()).lengthSqr() < 0.16D)
				waypoint++;
			if (waypoint < path.size()) {
				Vec3 direction = horizontalDelta(Vec3.atBottomCenterOf(path.get(waypoint)), player.position());
				double plannedSpeed = Math.min(MAX_SPEED, path.size() / Math.max(1.0D, duration) * 1.08D);
				Vec3 movement = direction.lengthSqr() <= plannedSpeed * plannedSpeed
						? direction : direction.normalize().scale(plannedSpeed);
				player.setDeltaMovement(movement.x, player.getDeltaMovement().y, movement.z);
			}
			player.hurtMarked = true;
			if (++age >= duration) {
				MOVEMENTS.remove(player);
				stop();
			}
		}
		private void stop() {
			player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
			player.hurtMarked = true;
		}
		private static Vec3 horizontalDelta(Vec3 target, Vec3 current) {
			return new Vec3(target.x - current.x, 0.0D, target.z - current.z);
		}
	}

	public static boolean protectedPosition(ServerLevel level, FrequencyWorldData data, BlockPos pos) {
		String namespace = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace();
		if (namespace.equals("thefourthfrequency")) return true;
		BlockPos station = data.stationPosition().orElse(null);
		if (station != null && within(pos, station, 6, 7, 5)) return true;
		var narrative = data.narrativeState();
		if (narrative.contains("rift_core")) {
			BlockPos core = BlockPos.of(narrative.getLongOr("rift_core", 0L));
			if (within(pos, core, 4, 12, 12)) return true;
		}
		return false;
	}

	static int activeLeaseCountForGameTest() {
		return ALIGNMENTS.size() + DOORS.size() + MOVEMENTS.size();
	}

	private static boolean within(BlockPos pos, BlockPos center, int x, int y, int z) {
		return Math.abs(pos.getX() - center.getX()) <= x && Math.abs(pos.getY() - center.getY()) <= y
				&& Math.abs(pos.getZ() - center.getZ()) <= z;
	}

	private static int breakerId(ServerPlayer player, int index) {
		return -Math.abs(System.identityHashCode(player) * 31 + index + 1);
	}

	public static final class EffectLease {
		private final Runnable cleanup;
		private boolean cleaned;
		public EffectLease(Runnable cleanup) { this.cleanup = cleanup == null ? () -> { } : cleanup; }
		public void cleanup() { if (!cleaned) { cleaned = true; cleanup.run(); } }
	}
}
