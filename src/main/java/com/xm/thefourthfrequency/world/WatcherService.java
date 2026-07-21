package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.entity.WatcherEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WatcherService {
	private static final Map<UUID, Long> NEXT_ATTEMPT = new HashMap<>();
	private static boolean initialized;
	private WatcherService() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(WatcherService::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) trySpawn(player, data, false);
	}

	public static boolean debugSpawn(ServerPlayer player) {
		return trySpawn(player, FrequencyWorldData.get(player.level().getServer()), true);
	}

	public static WatcherEntity spawnAnomaly(ServerPlayer player, int lifetimeTicks) {
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) return null;
		AABB search = player.getBoundingBox().inflate(32.0);
		if (!level.getEntitiesOfClass(WatcherEntity.class, search,
				watcher -> watcher.observes(player.getUUID())).isEmpty()) return null;
		BlockPos position = findPosition(level, player, true, 8.0, 14.0, true);
		if (position == null) return null;
		WatcherEntity watcher = ModEntities.WATCHER.create(level, EntitySpawnReason.EVENT);
		if (watcher == null) return null;
		watcher.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
				Mth.wrapDegrees(player.getYRot() + 180.0F), 0.0F);
		watcher.observe(player, Math.min(400, Math.max(20, lifetimeTicks)));
		return level.addFreshEntity(watcher) ? watcher : null;
	}

	private static boolean trySpawn(ServerPlayer player, FrequencyWorldData data, boolean forced) {
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) return false;
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || (!forced && record.getIntOr(TerminalData.BAND_STAGE, 0) > 0)) return false;
		long now = level.getGameTime();
		if (!forced) {
			if (now < NEXT_ATTEMPT.getOrDefault(player.getUUID(), record.getLongOr(TerminalData.ISSUED_GAME_TIME, now) + 2400L)) return false;
			NEXT_ATTEMPT.put(player.getUUID(), now + 2800L + level.getRandom().nextInt(3600));
			long day = Math.floorMod(level.getDayTime(), 24_000L);
			boolean darkTime = day >= 12_500L || day <= 1_000L;
			boolean underground = !level.canSeeSky(player.blockPosition()) && level.getMaxLocalRawBrightness(player.blockPosition()) <= 7;
			if (!darkTime && !underground) return false;
		}
		AABB search = player.getBoundingBox().inflate(64.0);
		if (level.getEntitiesOfClass(WatcherEntity.class, search, watcher -> watcher.observes(player.getUUID())).size() > 0) return false;
		BlockPos position = findPosition(level, player, forced,
				forced ? 12.0 : 18.0, forced ? 20.0 : 32.0, forced);
		if (position == null) return false;
		WatcherEntity watcher = ModEntities.WATCHER.create(level, EntitySpawnReason.EVENT);
		if (watcher == null) return false;
		watcher.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
				Mth.wrapDegrees(player.getYRot() + 180.0F), 0.0F);
		watcher.observe(player, forced ? 400 : 900);
		return level.addFreshEntity(watcher);
	}

	private static BlockPos findPosition(ServerLevel level, ServerPlayer player, boolean forced,
			double minimumDistance, double maximumDistance, boolean frontVisible) {
		for (int attempt = 0; attempt < 24; attempt++) {
			double offset;
			if (frontVisible) offset = -48.0 + level.getRandom().nextDouble() * 96.0;
			else {
				double side = 48.0 + level.getRandom().nextDouble() * 42.0;
				offset = level.getRandom().nextBoolean() ? side : -side;
			}
			double angle = Math.toRadians(player.getYRot() + offset);
			double distance = minimumDistance + level.getRandom().nextDouble() * (maximumDistance - minimumDistance);
			int x = Mth.floor(player.getX() - Math.sin(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.cos(angle) * distance);
			for (int y = player.blockPosition().getY() + 7; y >= player.blockPosition().getY() - 12; y--) {
				BlockPos candidate = new BlockPos(x, y, z);
				if (!level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), net.minecraft.core.Direction.UP)
						|| !level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir()
						|| !level.getBlockState(candidate.above(2)).isAir()) continue;
				if (forced || level.getMaxLocalRawBrightness(candidate) <= 5) return candidate;
			}
		}
		return null;
	}
}
