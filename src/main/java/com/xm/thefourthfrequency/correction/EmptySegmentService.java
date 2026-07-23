package com.xm.thefourthfrequency.correction;

import com.xm.thefourthfrequency.audio.AudioService;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.networking.EmptySegmentPayload;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.ending.FinaleRuntimePolicy;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EmptySegmentService {
	private static final Map<UUID, ActiveEvent> ACTIVE_EVENTS = new HashMap<>();
	private static boolean initialized;

	private EmptySegmentService() {
	}

	public static boolean isActive(ServerPlayer player) {
		return ACTIVE_EVENTS.containsKey(player.getUUID());
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(EmptySegmentService::updateServer);
		ServerPlayerEvents.JOIN.register(EmptySegmentService::recoverOnJoin);
	}

	private static void updateServer(MinecraftServer server) {
		if (!FinaleRuntimePolicy.backgroundSystemsAllowed(FrequencyWorldData.get(server))) {
			for (ActiveEvent event : Map.copyOf(ACTIVE_EVENTS).values()) {
				finish(server, event, server.getPlayerList().getPlayer(event.playerId));
			}
			ACTIVE_EVENTS.clear();
			return;
		}
		for (ActiveEvent event : Map.copyOf(ACTIVE_EVENTS).values()) {
			ServerPlayer player = server.getPlayerList().getPlayer(event.playerId);
			if (event.type == EventType.EXPERIENCE_GAP && !event.midpointApplied
					&& server.getTickCount() >= event.startTick + (event.endTick - event.startTick) / 2) {
				event.midpointApplied = true;
				if (player != null) {
					event.gapEnd = moveDuringGap(player);
				}
			}
			if (server.getTickCount() >= event.endTick) {
				finish(server, event, player);
				ACTIVE_EVENTS.remove(event.playerId);
			}
		}
		// Empty-segment variants are scheduled exclusively by AmbientAnomalyService v3.
	}

	private static void scheduleRareEvent(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!CorrectionState.active(data) || server.getPlayerList().getPlayers().isEmpty()) {
			return;
		}
		var correction = CorrectionState.get(data);
		long last = correction.getLongOr("last_empty_segment_tick", 0L);
		int interval = RuntimeServices.config().pacing().developerAcceleration()
				? 200 : CorrectionOrganService.parameters().emptySegmentMinIntervalTicks();
		if (server.overworld().getGameTime() - last < interval) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayers().stream()
				.filter(value -> value.level() == server.overworld())
				.filter(value -> FrequencyWorldData.get(server).terminalRecord(value.getUUID())
						.map(record -> record.getIntOr(TerminalData.PLOT_STAGE, 0) >= 4).orElse(false))
				.findFirst().orElse(null);
		if (player == null || ACTIVE_EVENTS.containsKey(player.getUUID())) {
			return;
		}
		int next = Math.floorMod(correction.getIntOr("next_empty_segment_event", 0), EventType.values().length);
		EventType type = EventType.values()[next];
		if (trigger(player, type, switch (type) {
			case VIEWPOINT_SEPARATION -> 100;
			case EXPERIENCE_GAP -> 80;
		})) {
			CorrectionState.update(data, state -> {
				state.putLong("last_empty_segment_tick", server.overworld().getGameTime());
				state.putInt("next_empty_segment_event", (next + 1) % EventType.values().length);
			});
		}
	}

	public static boolean trigger(ServerPlayer player, EventType type, int durationTicks) {
		if (durationTicks < 20 || durationTicks > 600 || ACTIVE_EVENTS.containsKey(player.getUUID())
				|| !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) {
			return false;
		}
		BlockPos origin = player.blockPosition();
		ArmorStand camera = null;
		if (type == EventType.VIEWPOINT_SEPARATION) {
			camera = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.EVENT);
			if (camera == null) {
				return false;
			}
			camera.snapTo(player.getX() + 4.0, player.getY() + 2.0, player.getZ() + 4.0,
					player.getYRot() + 180.0F, 15.0F);
			camera.setInvisible(true);
			camera.setNoGravity(true);
			camera.addTag("thefourthfrequency:empty_viewpoint");
			if (!level.addFreshEntity(camera)) {
				return false;
			}
		}
		int cameraId = camera == null ? -1 : camera.getId();
		ActiveEvent event = new ActiveEvent(player.getUUID(), type, level.getServer().getTickCount(),
				level.getServer().getTickCount() + durationTicks, origin,
				camera == null ? null : camera.getUUID());
		ACTIVE_EVENTS.put(player.getUUID(), event);
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.EMPTY_SEGMENT_ACTIVE, true);
			record.putString(TerminalData.EMPTY_SEGMENT_EVENT, type.id);
			record.putLong(TerminalData.EMPTY_SEGMENT_RETURN_POS, origin.asLong());
			record.putInt(TerminalData.EMPTY_SEGMENT_COUNT,
					record.getIntOr(TerminalData.EMPTY_SEGMENT_COUNT, 0) + 1);
			if (type == EventType.EXPERIENCE_GAP) {
				record.putLong(TerminalData.EMPTY_SEGMENT_GAP_FROM, origin.asLong());
			}
		});
		ServerPlayNetworking.send(player, new EmptySegmentPayload(type.id, durationTicks, cameraId));
		AudioService.play(level, player.blockPosition(), switch (type) {
			case VIEWPOINT_SEPARATION -> AudioService.Cue.EMPTY_VIEWPOINT;
			case EXPERIENCE_GAP -> AudioService.Cue.EMPTY_EXPERIENCE;
		});
		return true;
	}

	private static void finish(MinecraftServer server, ActiveEvent event, ServerPlayer player) {
		ServerLevel level = server.overworld();
		if (event.cameraId != null) {
			var camera = level.getEntityInAnyDimension(event.cameraId);
			if (camera != null) {
				camera.discard();
			}
		}
		if (player == null) {
			return;
		}
		FrequencyWorldData data = FrequencyWorldData.get(server);
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.EMPTY_SEGMENT_ACTIVE, false);
			record.putString(TerminalData.EMPTY_SEGMENT_EVENT, "none");
			if (event.gapEnd != null) {
				record.putLong(TerminalData.EMPTY_SEGMENT_GAP_TO, event.gapEnd.asLong());
			}
		});
		ServerPlayNetworking.send(player, new EmptySegmentPayload("end", 0, -1));
	}

	private static BlockPos moveDuringGap(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = player.blockPosition().relative(direction, 8);
			if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()
					&& !level.getBlockState(candidate.below()).isAir()) {
				Vec3 displacement = Vec3.atBottomCenterOf(candidate).subtract(player.position());
				player.move(MoverType.SELF, displacement);
				player.setDeltaMovement(Vec3.ZERO);
				player.hurtMarked = true;
				return player.blockPosition();
			}
		}
		return player.blockPosition();
	}

	private static void recoverOnJoin(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		var record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !record.getBooleanOr(TerminalData.EMPTY_SEGMENT_ACTIVE, false)) {
			return;
		}
		BlockPos returnPosition = BlockPos.of(record.getLongOr(TerminalData.EMPTY_SEGMENT_RETURN_POS,
				player.blockPosition().asLong()));
		ServerLevel level = player.level().getServer().overworld();
		player.teleportTo(level, returnPosition.getX() + 0.5, returnPosition.getY(), returnPosition.getZ() + 0.5,
				Set.of(), player.getYRot(), player.getXRot(), true);
		data.updateTerminalRecord(player.getUUID(), value -> {
			value.putBoolean(TerminalData.EMPTY_SEGMENT_ACTIVE, false);
			value.putString(TerminalData.EMPTY_SEGMENT_EVENT, "recovered_on_join");
		});
		ACTIVE_EVENTS.remove(player.getUUID());
		ServerPlayNetworking.send(player, new EmptySegmentPayload("end", 0, -1));
	}

	public enum EventType {
		VIEWPOINT_SEPARATION("viewpoint_separation"),
		EXPERIENCE_GAP("experience_gap");

		private final String id;

		EventType(String id) {
			this.id = id;
		}
	}

	private static final class ActiveEvent {
		private final UUID playerId;
		private final EventType type;
		private final int startTick;
		private final int endTick;
		private final BlockPos returnPosition;
		private final UUID cameraId;
		private boolean midpointApplied;
		private BlockPos gapEnd;

		private ActiveEvent(UUID playerId, EventType type, int startTick, int endTick, BlockPos returnPosition,
				UUID cameraId) {
			this.playerId = playerId;
			this.type = type;
			this.startTick = startTick;
			this.endTick = endTick;
			this.returnPosition = returnPosition;
			this.cameraId = cameraId;
		}
	}
}
