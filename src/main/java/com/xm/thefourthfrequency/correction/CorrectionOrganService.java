package com.xm.thefourthfrequency.correction;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.entity.ReworkEntity;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.ending.FinaleRuntimePolicy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.List;
import java.util.UUID;

public final class CorrectionOrganService {
	private static CorrectionParameters parameters;
	private static boolean initialized;

	private CorrectionOrganService() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		parameters = CorrectionDefinitionLoader.load();
		TrendSwarmService.initialize(parameters);
		ServerTickEvents.END_SERVER_TICK.register(CorrectionOrganService::updateServer);
	}

	public static void updateServer(MinecraftServer server) {
		if (!FinaleRuntimePolicy.backgroundSystemsAllowed(FrequencyWorldData.get(server))) {
			return;
		}
		CorrectionTargetService.ensureActivated(server);
		if (!CorrectionState.active(FrequencyWorldData.get(server))) {
			return;
		}
		TrendSwarmService.updateServer(server);
		ensureReworkBody(server);
	}

	private static void ensureReworkBody(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!CorrectionState.active(data)) {
			return;
		}
		CompoundTag state = CorrectionState.get(data);
		if (state.contains("rework_entity_uuid")) {
			try {
				Entity existing = server.overworld().getEntityInAnyDimension(
						UUID.fromString(state.getStringOr("rework_entity_uuid", "")));
				if (existing instanceof ReworkEntity body && existing.isAlive()) {
					BlockPos recorded = BlockPos.of(state.getLongOr("rework_entity_pos", body.blockPosition().asLong()));
					if (server.getTickCount() % 200 == 0
							&& ((recorded.getX() >> 4) != (body.blockPosition().getX() >> 4)
							|| (recorded.getZ() >> 4) != (body.blockPosition().getZ() >> 4))) {
						CorrectionState.update(data, value ->
								value.putLong("rework_entity_pos", body.blockPosition().asLong()));
					}
					return;
				}
			} catch (IllegalArgumentException ignored) {
				// Invalid legacy UUID is replaced by a real spawned body below.
			}
		}
		long lastSpawn = state.getLongOr("last_rework_spawn_tick", Long.MIN_VALUE / 2);
		int interval = RuntimeServices.config().pacing().developerAcceleration()
				? Math.min(100, parameters.reworkSpawnIntervalTicks()) : parameters.reworkSpawnIntervalTicks();
		if (server.overworld().getGameTime() - lastSpawn < interval) {
			return;
		}
		List<CorrectionTarget> targets = CorrectionTargetService.blockTargets(server.overworld());
		if (targets.isEmpty()) {
			return;
		}
		BlockPos target = targets.getFirst().position();
		if (server.overworld().players().stream().noneMatch(player -> player.distanceToSqr(target.getCenter()) <= 64 * 64)) {
			return;
		}
		spawnReworkBody(server.overworld(), target);
	}

	public static ReworkEntity spawnReworkBody(ServerLevel level, BlockPos target) {
		BlockPos spawn = findSpawn(level, target);
		if (spawn == null) {
			throw new IllegalStateException("No safe loaded position for a rework body near " + target);
		}
		ReworkEntity body = ModEntities.REWORK_BODY.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("Registered rework body factory returned null");
		}
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		body.initializeFormStageFromWorld(data);
		body.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
		body.setPersistenceRequired();
		if (!level.addFreshEntity(body)) {
			throw new IllegalStateException("Server rejected the rework body spawn");
		}
		CorrectionState.update(data, state -> {
			state.putString("rework_entity_uuid", body.getUUID().toString());
			state.putLong("rework_entity_pos", body.blockPosition().asLong());
			state.putLong("last_rework_spawn_tick", level.getGameTime());
			state.putInt("rework_spawn_count", state.getIntOr("rework_spawn_count", 0) + 1);
		});
		return body;
	}

	private static BlockPos findSpawn(ServerLevel level, BlockPos target) {
		for (BlockPos offset : List.of(
				target.offset(8, 0, 0), target.offset(-8, 0, 0), target.offset(0, 0, 8), target.offset(0, 0, -8),
				target.offset(5, 1, 5), target.offset(-5, 1, -5))) {
			if (level.isLoaded(offset) && level.getBlockState(offset).isAir()
					&& level.getBlockState(offset.above()).isAir()) {
				return offset;
			}
		}
		return null;
	}

	public static CorrectionParameters parameters() {
		return parameters;
	}
}
