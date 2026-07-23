package com.xm.thefourthfrequency.pursuit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tick-distributed mirror stream. The opening 5x5 window must finish before
 * transfer; later player movement appends only newly encountered chunk columns.
 */
public final class PursuitSnapshotBuilder {
	public static final int CHUNK_RADIUS = PursuitStreamWindow.RADIUS;
	public static final int VERTICAL_RADIUS = 48;
	public static final int BLOCK_BUDGET_PER_TICK = 8_192;
	private static final int COLUMN_WIDTH = 16;
	private static final int COLUMN_AREA = COLUMN_WIDTH * COLUMN_WIDTH;
	private static final Map<UUID, StreamSession> SESSIONS = new HashMap<>();
	private static boolean initialized;

	private PursuitSnapshotBuilder() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(PursuitSnapshotBuilder::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	public static boolean start(MinecraftServer server, UUID playerId, ResourceKey<Level> sourceKey,
			ResourceKey<Level> mirrorKey, BlockPos origin, Consumer<Boolean> completion) {
		if (SESSIONS.containsKey(playerId)) return false;
		ServerLevel source = server.getLevel(sourceKey);
		ServerLevel mirror = server.getLevel(mirrorKey);
		if (source == null || mirror == null || source == mirror || PursuitDimensions.isMirror(source)) return false;
		int minimumY = Math.max(source.getMinY(), origin.getY() - VERTICAL_RADIUS);
		int maximumY = Math.min(source.getMaxY() - 1, origin.getY() + VERTICAL_RADIUS);
		StreamSession session = new StreamSession(playerId, sourceKey, mirrorKey, minimumY,
				maximumY - minimumY + 1, completion == null ? ignored -> { } : completion);
		for (PursuitStreamWindow.Column column : PursuitStreamWindow.centeredAt(
				origin.getX() >> 4, origin.getZ() >> 4)) {
			session.enqueue(column, true);
		}
		SESSIONS.put(playerId, session);
		return true;
	}

	/** Keeps two complete chunk columns ahead of ordinary player movement. */
	public static boolean requestWindow(UUID playerId, BlockPos center) {
		StreamSession session = SESSIONS.get(playerId);
		if (session == null || !session.initialDelivered) return false;
		for (PursuitStreamWindow.Column column : PursuitStreamWindow.centeredAt(
				center.getX() >> 4, center.getZ() >> 4)) {
			session.enqueue(column, false);
		}
		return true;
	}

	public static boolean isChunkReady(UUID playerId, BlockPos position) {
		StreamSession session = SESSIONS.get(playerId);
		if (session == null) return false;
		return session.copied.contains(PursuitStreamWindow.key(position.getX() >> 4, position.getZ() >> 4));
	}

	public static void cancel(UUID playerId) {
		StreamSession removed = SESSIONS.remove(playerId);
		if (removed != null && !removed.initialDelivered) {
			removed.initialDelivered = true;
			removed.completion.accept(false);
		}
	}

	public static boolean active(UUID playerId) {
		return SESSIONS.containsKey(playerId);
	}

	private static void tick(MinecraftServer server) {
		for (StreamSession session : Map.copyOf(SESSIONS).values()) {
			ServerLevel source = server.getLevel(session.source);
			ServerLevel mirror = server.getLevel(session.mirror);
			if (source == null || mirror == null) {
				fail(session);
				continue;
			}
			int remainingBudget = BLOCK_BUDGET_PER_TICK;
			BlockPos.MutableBlockPos sourcePos = new BlockPos.MutableBlockPos();
			while (remainingBudget > 0) {
				ColumnTask task = session.current;
				if (task == null) {
					task = session.pending.pollFirst();
					session.current = task;
					if (task == null) break;
					prepareColumn(source, mirror, task, session);
				}
				int total = COLUMN_AREA * session.height;
				while (task.cursor < total && remainingBudget-- > 0) {
					int horizontalIndex = task.cursor % COLUMN_AREA;
					int dy = task.cursor / COLUMN_AREA;
					int dx = horizontalIndex % COLUMN_WIDTH;
					int dz = horizontalIndex / COLUMN_WIDTH;
					sourcePos.set((task.column.chunkX() << 4) + dx, session.minimumY + dy,
							(task.column.chunkZ() << 4) + dz);
					BlockState copied = PursuitBlockPolicy.sanitizeSnapshotState(source, sourcePos,
							source.getBlockState(sourcePos));
					if (!mirror.getBlockState(sourcePos).equals(copied)) {
						mirror.setBlock(sourcePos, copied, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
								| Block.UPDATE_SUPPRESS_DROPS);
					}
					if (mirror.getBlockEntity(sourcePos) != null) mirror.removeBlockEntity(sourcePos);
					task.cursor++;
				}
				if (task.cursor < total) break;
				session.copied.add(task.column.key());
				session.current = null;
				if (task.initial && --session.initialRemaining == 0 && !session.initialDelivered) {
					session.initialDelivered = true;
					session.completion.accept(true);
				}
			}
		}
	}

	private static void prepareColumn(ServerLevel source, ServerLevel mirror, ColumnTask task,
			StreamSession session) {
		source.getChunk(task.column.chunkX(), task.column.chunkZ());
		mirror.getChunk(task.column.chunkX(), task.column.chunkZ());
		AABB area = new AABB(task.column.chunkX() << 4, session.minimumY, task.column.chunkZ() << 4,
				(task.column.chunkX() + 1) << 4, session.minimumY + session.height,
				(task.column.chunkZ() + 1) << 4);
		mirror.getEntities((Entity) null, area, entity -> !(entity instanceof ServerPlayer)
				&& (task.initial || !entity.getTags().contains("thefourthfrequency:pursuit")))
				.forEach(Entity::discard);
	}

	private static void fail(StreamSession session) {
		if (SESSIONS.remove(session.playerId) == null || session.initialDelivered) return;
		session.initialDelivered = true;
		session.completion.accept(false);
	}

	private static final class StreamSession {
		private final UUID playerId;
		private final ResourceKey<Level> source;
		private final ResourceKey<Level> mirror;
		private final int minimumY;
		private final int height;
		private final Consumer<Boolean> completion;
		private final ArrayDeque<ColumnTask> pending = new ArrayDeque<>();
		private final Set<Long> scheduled = new HashSet<>();
		private final Set<Long> copied = new HashSet<>();
		private ColumnTask current;
		private int initialRemaining;
		private boolean initialDelivered;

		private StreamSession(UUID playerId, ResourceKey<Level> source, ResourceKey<Level> mirror,
				int minimumY, int height, Consumer<Boolean> completion) {
			this.playerId = playerId;
			this.source = source;
			this.mirror = mirror;
			this.minimumY = minimumY;
			this.height = height;
			this.completion = completion;
		}

		private void enqueue(PursuitStreamWindow.Column column, boolean initial) {
			if (!scheduled.add(column.key())) return;
			pending.addLast(new ColumnTask(column, initial));
			if (initial) initialRemaining++;
		}
	}

	private static final class ColumnTask {
		private final PursuitStreamWindow.Column column;
		private final boolean initial;
		private int cursor;

		private ColumnTask(PursuitStreamWindow.Column column, boolean initial) {
			this.column = column;
			this.initial = initial;
		}
	}
}
