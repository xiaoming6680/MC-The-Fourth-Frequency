package com.xm.thefourthfrequency.correction;

import com.xm.thefourthfrequency.entity.ReworkEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CorrectionSpatialIndex {
	private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
	private static final Map<Bucket, LinkedHashSet<UUID>> BUCKETS = new HashMap<>();
	private static final List<UUID> REFRESH_ORDER = new ArrayList<>();
	private static int refreshCursor;
	private static boolean initialized;

	private CorrectionSpatialIndex() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerEntityEvents.ENTITY_LOAD.register(CorrectionSpatialIndex::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> remove(entity.getUUID()));
	}

	private static void onLoad(Entity entity, ServerLevel level) {
		if (entity instanceof Mob mob && eligible(mob)) {
			put(mob, level);
		}
	}

	private static boolean eligible(Mob mob) {
		return !(mob instanceof ReworkEntity)
				&& (mob instanceof Animal || mob instanceof AbstractVillager || mob instanceof Monster);
	}

	private static void put(Mob mob, ServerLevel level) {
		remove(mob.getUUID());
		Bucket bucket = bucket(level, mob.chunkPosition());
		ENTRIES.put(mob.getUUID(), new Entry(mob, bucket));
		BUCKETS.computeIfAbsent(bucket, ignored -> new LinkedHashSet<>()).add(mob.getUUID());
		REFRESH_ORDER.add(mob.getUUID());
	}

	private static void remove(UUID id) {
		Entry previous = ENTRIES.remove(id);
		if (previous != null) {
			Set<UUID> values = BUCKETS.get(previous.bucket());
			if (values != null) {
				values.remove(id);
				if (values.isEmpty()) {
					BUCKETS.remove(previous.bucket());
				}
			}
		}
		REFRESH_ORDER.remove(id);
		if (refreshCursor >= REFRESH_ORDER.size()) {
			refreshCursor = 0;
		}
	}

	public static void refresh(int budget) {
		int refreshed = 0;
		while (refreshed < budget && !REFRESH_ORDER.isEmpty()) {
			if (refreshCursor >= REFRESH_ORDER.size()) {
				refreshCursor = 0;
			}
			UUID id = REFRESH_ORDER.get(refreshCursor++);
			Entry entry = ENTRIES.get(id);
			if (entry == null || entry.mob().isRemoved()) {
				remove(id);
				continue;
			}
			if (!(entry.mob().level() instanceof ServerLevel level)) {
				remove(id);
				continue;
			}
			Bucket current = bucket(level, entry.mob().chunkPosition());
			if (!current.equals(entry.bucket())) {
				Set<UUID> oldValues = BUCKETS.get(entry.bucket());
				if (oldValues != null) {
					oldValues.remove(id);
				}
				BUCKETS.computeIfAbsent(current, ignored -> new LinkedHashSet<>()).add(id);
				ENTRIES.put(id, new Entry(entry.mob(), current));
			}
			refreshed++;
		}
	}

	public static List<Mob> sampleNear(ServerLevel level, net.minecraft.core.BlockPos origin, int radius, int limit) {
		List<Mob> result = new ArrayList<>(limit);
		int minimumChunkX = (origin.getX() - radius) >> 4;
		int maximumChunkX = (origin.getX() + radius) >> 4;
		int minimumChunkZ = (origin.getZ() - radius) >> 4;
		int maximumChunkZ = (origin.getZ() + radius) >> 4;
		double radiusSquared = (double) radius * radius;
		for (int chunkX = minimumChunkX; chunkX <= maximumChunkX && result.size() < limit; chunkX++) {
			for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ && result.size() < limit; chunkZ++) {
				Set<UUID> values = BUCKETS.get(new Bucket(level.dimension().identifier().toString(), ChunkPos.asLong(chunkX, chunkZ)));
				if (values == null) {
					continue;
				}
				for (UUID id : List.copyOf(values)) {
					Entry entry = ENTRIES.get(id);
					if (entry != null && entry.mob().isAlive()
							&& entry.mob().distanceToSqr(origin.getCenter()) <= radiusSquared) {
						result.add(entry.mob());
						if (result.size() == limit) {
							break;
						}
					}
				}
			}
		}
		return List.copyOf(result);
	}

	public static int indexedEntityCount() {
		return ENTRIES.size();
	}

	private static Bucket bucket(ServerLevel level, ChunkPos chunk) {
		return new Bucket(level.dimension().identifier().toString(), chunk.toLong());
	}

	private record Entry(Mob mob, Bucket bucket) {
	}

	private record Bucket(String dimension, long chunk) {
	}
}
