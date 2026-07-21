package com.xm.thefourthfrequency.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Cheap, bounded preflight checks. A rejected candidate is never started or logged. */
public final class AnomalyConditions {
	private AnomalyConditions() { }

	public static Prepared prepare(ServerPlayer player, AnomalyDefinition definition, long seed) {
		if (!(player.level() instanceof ServerLevel level)) return null;
		return switch (definition.id()) {
			case "phantom_echo" -> caveLike(level, player.blockPosition()) ? Prepared.NONE : null;
			case "light_dropout" -> hasLoadedLightSource(level, player) ? Prepared.NONE : null;
			case "surface_fracture" -> {
				BlockPos target = surfaceTarget(level, player);
				yield target == null ? null : Prepared.at(level, target);
			}
			case "action_echo" -> player.tickCount >= 60 ? Prepared.NONE : null;
			default -> Prepared.NONE;
		};
	}

	public static boolean caveLike(ServerLevel level, BlockPos origin) {
		boolean directSky = level.canSeeSky(origin.above());
		int skyLight = level.getBrightness(LightLayer.SKY, origin);
		int enclosed = 0;
		for (Direction direction : Direction.values()) {
			for (int distance = 1; distance <= 2; distance++) {
				BlockPos pos = origin.relative(direction, distance);
				if (level.hasChunkAt(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
					enclosed++;
					break;
				}
			}
		}
		return AnomalySelectionRules.caveLike(directSky, skyLight, enclosed);
	}

	public static BlockPos surfaceTarget(ServerLevel level, ServerPlayer player) {
		Direction front = player.getDirection();
		Direction[] directions = { front, front.getCounterClockWise(), front.getClockWise() };
		BlockPos feet = player.blockPosition();
		BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		for (Direction direction : directions) {
			BlockPos footTarget = feet.relative(direction);
			if (collidable(level, footTarget)) return footTarget;
			BlockPos eyeTarget = eyes.relative(direction);
			if (collidable(level, eyeTarget)) return eyeTarget;
		}
		return null;
	}

	private static boolean collidable(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) return false;
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean hasLoadedLightSource(ServerLevel level, ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		Vec3 eye = player.getEyePosition();
		for (int radius = 1; radius <= 16; radius++) {
			for (int dx = -radius; dx <= radius; dx++) for (int dy = -8; dy <= 8; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
					BlockPos pos = origin.offset(dx, dy, dz);
					if (level.hasChunkAt(pos) && level.getBlockState(pos).getLightEmission() > 0
							&& visibleToPlayer(level, eye, pos)) return true;
				}
			}
		}
		return false;
	}

	private static boolean visibleToPlayer(ServerLevel level, Vec3 eye, BlockPos pos) {
		BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	public record Prepared(AnomalyRuntimeService.Anchor anchor) {
		public static final Prepared NONE = new Prepared(null);
		private static Prepared at(ServerLevel level, BlockPos pos) {
			return new Prepared(new AnomalyRuntimeService.Anchor(
					level.dimension().identifier().toString(), pos.immutable()));
		}
	}
}
