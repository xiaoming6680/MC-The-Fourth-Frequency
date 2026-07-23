package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.entity.ReworkEntity;
import com.xm.thefourthfrequency.terminal.AnomalyIntensity;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side five-form chase mechanics and authoritative resolution. */
public final class PursuitFormController {
	private static final Map<UUID, Runtime> ACTIVE = new HashMap<>();
	private static boolean initialized;

	private PursuitFormController() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(PursuitFormController::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
	}

	public static boolean begin(ServerPlayer player, String sessionId, int form) {
		if (!(player.level() instanceof ServerLevel level) || !PursuitDimensions.isMirror(level)
				|| ACTIVE.containsKey(player.getUUID())) return false;
		ReworkEntity entity = spawn(level, player, sessionId, form);
		if (entity == null) return false;
		long now = level.getGameTime();
		int normalizedForm = Math.clamp(form, 1, 5);
		ServerBossEvent waveform = new ServerBossEvent(
				Component.translatable("message.thefourthfrequency.pursuit.waveform"),
				BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
		waveform.addPlayer(player);
		ACTIVE.put(player.getUUID(), new Runtime(player.getUUID(), sessionId, Math.clamp(form, 1, 5),
				entity.getUUID(), now, now + PursuitFormPolicy.forForm(normalizedForm).durationTicks(),
				player.position(), waveform));
		PursuitVisibilityService.isolate(player);
		player.displayClientMessage(Component.translatable(
				"message.thefourthfrequency.pursuit.begin." + form), true);
		return true;
	}

	public static void interrupt(ServerPlayer player) {
		Runtime runtime = ACTIVE.remove(player.getUUID());
		if (runtime == null) return;
		runtime.waveform.removeAllPlayers();
		if (player.level() instanceof ServerLevel level) {
			Entity entity = level.getEntity(runtime.entityId);
			if (entity != null) entity.discard();
		}
	}

	private static void tick(MinecraftServer server) {
		for (Runtime runtime : Map.copyOf(ACTIVE).values()) {
			ServerPlayer player = server.getPlayerList().getPlayer(runtime.playerId);
			if (player == null) {
				ACTIVE.remove(runtime.playerId);
				continue;
			}
			if (!(player.level() instanceof ServerLevel level) || !PursuitDimensions.isMirror(level)) {
				interrupt(player);
				continue;
			}
			if (!PursuitSnapshotBuilder.active(player.getUUID())) {
				capture(player, runtime, "snapshot_stream_lost");
				continue;
			}
			Entity raw = level.getEntity(runtime.entityId);
			ReworkEntity rework = raw instanceof ReworkEntity body && body.isAlive() ? body : null;
			if (rework == null) {
				rework = spawn(level, player, runtime.sessionId, runtime.form);
				if (rework == null) {
					capture(player, runtime, "entity_lost");
					continue;
				}
				runtime.entityId = rework.getUUID();
				runtime.captureGrace = 30;
			}
			if (runtime.captureGrace > 0) runtime.captureGrace--;
			if (!maintainStreamingWindow(level, player, rework, runtime)) {
				if (server.getTickCount() % 40 == 0) removeAmbientHostiles(level, player, rework);
				continue;
			}
			tickForm(level, player, rework, runtime);
			if (server.getTickCount() % 40 == 0) removeAmbientHostiles(level, player, rework);
			if (player.getHealth() <= 2.0F
					|| runtime.captureGrace <= 0 && rework.distanceToSqr(player) <= 2.25D) {
				capture(player, runtime, "caught");
				continue;
			}
			long now = level.getGameTime();
			runtime.waveform.setProgress(Math.clamp(
					(float) (runtime.deadline - now) / Math.max(1.0F, runtime.deadline - runtime.startTick),
					0.0F, 1.0F));
			if (now >= runtime.deadline) {
				succeed(player, runtime);
			} else if (server.getTickCount() % 200 == 0) {
				player.displayClientMessage(Component.translatable(
						"message.thefourthfrequency.pursuit.remaining",
						Math.max(1L, (runtime.deadline - now) / 20L)), true);
			}
		}
	}

	private static boolean maintainStreamingWindow(ServerLevel level, ServerPlayer player, ReworkEntity rework,
			Runtime runtime) {
		PursuitSnapshotBuilder.requestWindow(player.getUUID(), player.blockPosition());
		if (PursuitSnapshotBuilder.isChunkReady(player.getUUID(), player.blockPosition())) {
			runtime.lastSafePosition = player.position();
			return true;
		}
		Vec3 safe = runtime.lastSafePosition;
		level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1.0D, player.getZ(),
				32, 1.0D, 1.0D, 1.0D, 0.02D);
		player.teleportTo(level, safe.x, safe.y, safe.z, java.util.Set.of(),
				player.getYRot(), player.getXRot(), true);
		rework.setPursuitTracking(false);
		runtime.deadline++;
		runtime.captureGrace = Math.max(runtime.captureGrace, 10);
		runtime.lastPlayerPosition = player.position();
		if (level.getGameTime() >= runtime.nextStreamNoticeTick) {
			player.displayClientMessage(Component.translatable(
					"message.thefourthfrequency.pursuit.stream_wait"), true);
			runtime.nextStreamNoticeTick = level.getGameTime() + 40L;
		}
		return false;
	}

	private static void tickForm(ServerLevel level, ServerPlayer player, ReworkEntity rework, Runtime runtime) {
		Vec3 movement = player.position().subtract(runtime.lastPlayerPosition);
		runtime.lastPlayerPosition = player.position();
		switch (runtime.form) {
			case 1 -> {
				boolean noisy = !player.isCrouching() && movement.horizontalDistanceSqr() > 0.0025D;
				rework.setPursuitTracking(noisy || rework.distanceToSqr(player) < 25.0D);
				if (level.getGameTime() % 100L == 0L) player.displayClientMessage(
						Component.translatable("message.thefourthfrequency.pursuit.hint.1"), true);
			}
			case 2 -> {
				rework.setPursuitTracking(true);
				if (level.getGameTime() % 140L == 0L) player.displayClientMessage(
						Component.translatable("message.thefourthfrequency.pursuit.hint.2"), true);
			}
			case 3 -> {
				rework.setPursuitTracking(true);
				if (level.getGameTime() % 100L == 0L && movement.horizontalDistanceSqr() > 0.01D) {
					Vec3 direction = movement.multiply(1.0D, 0.0D, 1.0D).normalize();
					reposition(level, rework, player.position().add(direction.scale(10.0D)), runtime);
					player.displayClientMessage(
							Component.translatable("message.thefourthfrequency.pursuit.hint.3"), true);
				}
			}
			case 4 -> {
				rework.setPursuitTracking(true);
				long cycle = level.getGameTime() % 140L;
				if (cycle == 110L) player.displayClientMessage(
						Component.translatable("message.thefourthfrequency.pursuit.lunge"), true);
				if (cycle == 130L) {
					Vec3 behind = player.getLookAngle().multiply(-4.0D, 0.0D, -4.0D)
							.add(player.position()).add(0.0D, 2.5D, 0.0D);
					reposition(level, rework, behind, runtime);
				}
			}
			case 5 -> {
				rework.setPursuitTracking(true);
				if (level.getGameTime() % 100L == 0L) {
					int falseX = player.blockPosition().getX() + player.getRandom().nextIntBetweenInclusive(-96, 96);
					int falseZ = player.blockPosition().getZ() + player.getRandom().nextIntBetweenInclusive(-96, 96);
					player.displayClientMessage(Component.translatable(
							"message.thefourthfrequency.pursuit.false_coordinate", falseX, falseZ), true);
				}
			}
			default -> rework.setPursuitTracking(true);
		}
	}

	private static void reposition(ServerLevel level, ReworkEntity entity, Vec3 desired, Runtime runtime) {
		BlockPos origin = BlockPos.containing(desired);
		for (int dy = 3; dy >= -3; dy--) {
			BlockPos candidate = origin.offset(0, dy, 0);
			if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
					&& level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
					&& !level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
				entity.teleportTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
				runtime.captureGrace = 20;
				return;
			}
		}
	}

	private static ReworkEntity spawn(ServerLevel level, ServerPlayer player, String sessionId, int form) {
		BlockPos spawn = findSpawn(level, player.blockPosition());
		if (spawn == null) return null;
		ReworkEntity body = ModEntities.REWORK_BODY.create(level, EntitySpawnReason.EVENT);
		if (body == null) return null;
		body.configurePursuit(player.getUUID(), sessionId, form);
		body.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
				player.getYRot(), 0.0F);
		body.setPersistenceRequired();
		body.addTag("thefourthfrequency:pursuit");
		return level.addFreshEntity(body) ? body : null;
	}

	private static BlockPos findSpawn(ServerLevel level, BlockPos target) {
		int[][] offsets = {{18, 0}, {-18, 0}, {0, 18}, {0, -18}, {12, 12}, {-12, -12}};
		for (int[] offset : offsets) {
			BlockPos origin = target.offset(offset[0], 0, offset[1]);
			for (int dy = 5; dy >= -5; dy--) {
				BlockPos candidate = origin.offset(0, dy, 0);
				if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
						&& level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
						&& !level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
					return candidate;
				}
			}
		}
		return null;
	}

	private static void removeAmbientHostiles(ServerLevel level, ServerPlayer player, ReworkEntity rework) {
		for (Monster monster : level.getEntitiesOfClass(Monster.class,
				player.getBoundingBox().inflate(64.0D), value -> value != rework)) {
			monster.discard();
		}
	}

	private static void capture(ServerPlayer player, Runtime runtime, String reason) {
		ACTIVE.remove(player.getUUID());
		runtime.waveform.removeAllPlayers();
		if (player.level() instanceof ServerLevel level) {
			Entity entity = level.getEntity(runtime.entityId);
			if (entity != null) entity.discard();
		}
		if (player.getHealth() < 6.0F) player.setHealth(6.0F);
		player.displayClientMessage(Component.translatable(
				"message.thefourthfrequency.pursuit.caught." + runtime.form), true);
		PursuitSessionService.returnToSource(player, reason);
	}

	private static void succeed(ServerPlayer player, Runtime runtime) {
		ACTIVE.remove(player.getUUID());
		runtime.waveform.removeAllPlayers();
		if (player.level() instanceof ServerLevel level) {
			Entity entity = level.getEntity(runtime.entityId);
			if (entity != null) entity.discard();
		}
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		long now = player.level().getGameTime();
		long cooldown = PursuitProgressPolicy.MIN_CHASE_GAP_TICKS
				+ Math.floorMod(player.getRandom().nextLong(),
						PursuitProgressPolicy.MAX_CHASE_GAP_TICKS - PursuitProgressPolicy.MIN_CHASE_GAP_TICKS + 1);
		data.updateTerminalRecord(player.getUUID(), record -> {
			int resolved = PursuitProgressPolicy.resolvedAfterSuccess(
					record.getIntOr(TerminalData.PURSUIT_RESOLVED_CHASES, 0));
			record.putInt(TerminalData.PURSUIT_RESOLVED_CHASES, resolved);
			record.putBoolean(TerminalData.PURSUIT_PENDING,
					PursuitProgressPolicy.pendingAfterSuccess(resolved,
							record.getIntOr(TerminalData.PURSUIT_ALLOWED_FORM, 0)));
			record.putLong(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK, now + cooldown);
			record.putInt(TerminalData.PURSUIT_TUTORIAL_ARCHIVE_MASK, PursuitTutorialPolicy.mark(
					record.getIntOr(TerminalData.PURSUIT_TUTORIAL_ARCHIVE_MASK, 0), runtime.form));
			record.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK,
					now + AnomalyIntensity.DIMENSION_GRACE_TICKS + 5L * 60L * 20L);
		});
		player.displayClientMessage(Component.translatable(
				"message.thefourthfrequency.pursuit.success." + runtime.form), true);
		PursuitSessionService.returnToSource(player, "success");
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}

	private static final class Runtime {
		private final UUID playerId;
		private final String sessionId;
		private final int form;
		private UUID entityId;
		private final long startTick;
		private long deadline;
		private Vec3 lastPlayerPosition;
		private Vec3 lastSafePosition;
		private final ServerBossEvent waveform;
		private int captureGrace = 40;
		private long nextStreamNoticeTick;

		private Runtime(UUID playerId, String sessionId, int form, UUID entityId,
				long startTick, long deadline, Vec3 lastPlayerPosition, ServerBossEvent waveform) {
			this.playerId = playerId;
			this.sessionId = sessionId;
			this.form = form;
			this.entityId = entityId;
			this.startTick = startTick;
			this.deadline = deadline;
			this.lastPlayerPosition = lastPlayerPosition;
			this.lastSafePosition = lastPlayerPosition;
			this.waveform = waveform;
		}
	}
}
