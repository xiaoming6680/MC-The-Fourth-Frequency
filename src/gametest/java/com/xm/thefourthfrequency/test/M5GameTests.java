package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.correction.CorrectionOrganService;
import com.xm.thefourthfrequency.correction.CorrectionSpatialIndex;
import com.xm.thefourthfrequency.correction.CorrectionState;
import com.xm.thefourthfrequency.correction.CorrectionTargetService;
import com.xm.thefourthfrequency.correction.EmptySegmentService;
import com.xm.thefourthfrequency.correction.ReworkCollisionProfile;
import com.xm.thefourthfrequency.correction.TrendSwarmService;
import com.xm.thefourthfrequency.entity.ReworkEntity;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Method;

public final class M5GameTests implements CustomTestMethodInvoker {
	private static final int REWORK_TEST_Y = 70;

	@GameTest(maxTicks = 880)
	public void trendSwarmAndReworkBodyUseRealBoundedBehavior(GameTestHelper helper) {
		var level = helper.getLevel();
		var server = level.getServer();
		server.setDifficulty(Difficulty.NORMAL, true);
		FrequencyWorldData worldData = FrequencyWorldData.get(server);
		resetReworkFixture(worldData);
		buildReworkIsolationPlatform(level, helper);
		BlockPos organ = helper.absolutePos(new BlockPos(5, REWORK_TEST_Y, 5));
		BlockPos terminalFacility = helper.absolutePos(new BlockPos(9, REWORK_TEST_Y, 5));
		ServerPlayer terminalCarrier = helper.makeMockServerPlayerInLevel();
		terminalCarrier.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		terminalCarrier.setHealth(terminalCarrier.getMaxHealth());
		terminalCarrier.snapTo(organ.getX() + 14.5, organ.getY(), organ.getZ() + 6.5, 0.0F, 0.0F);
		CorrectionTargetService.setOrganForTest(server, organ);
		CorrectionTargetService.setTerminalFacilityForTest(server, terminalFacility);

		Cow cow = add(level, EntityType.COW, organ.offset(5, 0, 0));
		Villager villager = add(level, EntityType.VILLAGER, organ.offset(9, 0, 0));
		Zombie zombie = add(level, EntityType.ZOMBIE, organ.offset(13, 0, 0));
		TrendSwarmService.applyTrend(cow, organ);
		TrendSwarmService.applyTrend(villager, organ);
		TrendSwarmService.applyTrend(zombie, organ);
		helper.assertTrue(TrendSwarmService.stopDistance(cow) < TrendSwarmService.stopDistance(villager)
				&& TrendSwarmService.stopDistance(villager) < TrendSwarmService.stopDistance(zombie),
				"Cross-species trend bands must stop sequentially at distinct distances");
		for (Mob mob : new Mob[] {cow, villager, zombie}) {
			helper.assertTrue(mob.getTags().contains(TrendSwarmService.TREND_TAG),
					"Vanilla animal, villager, and hostile must be temporarily enlisted");
			helper.assertTrue(mob.getNavigation().isDone(),
					"An enlisted vanilla mob inside its tier distance must stop rather than frenzy");
		}
		helper.assertTrue(CorrectionSpatialIndex.indexedEntityCount() >= 3,
				"Entity load events must populate the explicit correction spatial index");
		helper.assertTrue(CorrectionSpatialIndex.sampleNear(level, organ, 48, 2).size() <= 2,
				"Spatial query must obey its hard sample limit");
		cow.discard();
		villager.discard();
		zombie.discard();

		ReworkEntity body = CorrectionOrganService.spawnReworkBody(level, organ);
		body.setInvulnerable(true);
		body.snapTo(organ.getX() + 2.5, organ.getY(), organ.getZ() + 0.5, 90.0F, 0.0F);
		ReworkEntity[] activeBody = {body};
		helper.assertValueEqual(body.formStage(), 1, "A zero-dismantle world must spawn form stage 1");
		helper.assertValueEqual(body.morphTicks(), 0, "Spawn restoration must not replay a morph");
		assertReworkCollision(helper, body, 1);
		BlockPos door = body.blockPosition().relative(net.minecraft.core.Direction.SOUTH);
		level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
		level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);

		helper.runAfterDelay(10, () -> helper.assertTrue(
				level.getBlockState(door).getValue(DoorBlock.OPEN),
				"Rework body must physically open a door by body contact"));
		helper.runAfterDelay(75, () -> {
			helper.assertValueEqual(CorrectionState.dismantleCount(worldData), 0,
					"Contact work must not record a dismantle before all 80 work ticks finish");
			helper.assertValueEqual(body.formStage(), 1, "The old model must remain before the first dismantle");
			helper.assertValueEqual(body.morphTicks(), 0, "No early morph may start");
		});
		float[] carrierHealth = new float[1];
		helper.runAfterDelay(870, () -> {
			BlockPos corridorTarget = helper.absolutePos(new BlockPos(12, REWORK_TEST_Y, 10));
			helper.assertTrue(false, "M5 stage-chain watchdog: count="
					+ CorrectionState.dismantleCount(worldData) + ", stage=" + activeBody[0].formStage()
					+ ", morph=" + activeBody[0].morphTicks() + ", pos=" + activeBody[0].blockPosition()
					+ ", terminal=" + level.getBlockState(terminalFacility).getBlock()
					+ ", corridor=" + level.getBlockState(corridorTarget).getBlock());
		});
		waitForMorphingDismantle(helper, level, organ, worldData, body, 1, 1, 2, () -> {
			helper.assertTrue(level.getBlockState(terminalFacility).is(ModBlocks.ARCHIVE_LOCK),
					"Lower-priority terminal facility must remain while organ work and morph complete");
			waitForMorphingDismantle(helper, level, terminalFacility, worldData, body, 2, 2, 3, () -> {
				boolean bracePresent = false;
				for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					bracePresent |= level.getBlockState(terminalFacility.relative(direction)).is(ModBlocks.REWORK_BRACE);
				}
				helper.assertTrue(bracePresent,
						"Dismantling must include an actual purposeful placement action");
				var state = CorrectionState.get(worldData);
				helper.assertTrue(state.getIntOr("maximum_tick_work", 0)
						<= RuntimeServices.config().limits().correctionWorkBudgetPerTick(),
						"Observed correction work must stay within the configured global budget");
				terminalCarrier.snapTo(body.getX() + 1.0, body.getY(), body.getZ(), 0.0F, 0.0F);
				body.snapTo(terminalCarrier.getX() + 1.0, terminalCarrier.getY(), terminalCarrier.getZ(),
						0.0F, 0.0F);
				carrierHealth[0] = terminalCarrier.getHealth();
				helper.runAfterDelay(25, () -> {
					helper.assertValueEqual(terminalCarrier.getHealth(), carrierHealth[0],
							"A correction entity with no work target must not hunt a terminal carrier");
					helper.assertTrue(body.isAlive(), "The stage fixture must keep its correction entity alive");
					CorrectionTargetService.setOrganForTest(server, terminalFacility);
					waitForMorphingDismantle(helper, level, terminalFacility, worldData, body,
							3, 3, 4, () -> {
						CorrectionTargetService.setOrganForTest(server, terminalFacility);
						waitForMorphingDismantle(helper, level, terminalFacility, worldData, body,
								4, 4, 5, () -> {
							helper.assertValueEqual(body.formStage(), 5,
									"The normal dismantle flow must reach stage 5");
							CorrectionTargetService.setOrganForTest(server, terminalFacility);
							waitForCappedDismantle(helper, level, terminalFacility, worldData, body, 5, () -> {
								body.setInvulnerable(false);
								body.kill(level);
								ReworkEntity reborn = CorrectionOrganService.spawnReworkBody(level, terminalFacility);
								reborn.setInvulnerable(true);
								activeBody[0] = reborn;
								helper.assertValueEqual(reborn.formStage(), 5,
										"A replacement body must inherit the world-unlocked final form");
								helper.assertValueEqual(reborn.morphTicks(), 0,
										"Death and respawn must not play a compensating morph");
								assertReworkCollision(helper, reborn, 5);
								BlockPos corridorStart = helper.absolutePos(new BlockPos(4, REWORK_TEST_Y, 10));
								BlockPos corridorTarget = helper.absolutePos(new BlockPos(12, REWORK_TEST_Y, 10));
								buildTwoBlockHighCorridor(level, helper);
								int ceilingBefore = countCorridorCeiling(level, helper);
								int sideWallsBefore = countCorridorSideWalls(level, helper);
								CorrectionTargetService.setOrganForTest(server, corridorTarget);
								reborn.snapTo(corridorStart.getX() + 0.5, corridorStart.getY(),
										corridorStart.getZ() + 0.5, 0.0F, 0.0F);
								waitForCappedDismantle(helper, level, corridorTarget, worldData, reborn, 6, () -> {
									BlockPos crossing = helper.absolutePos(new BlockPos(7, REWORK_TEST_Y, 10));
									helper.assertTrue(reborn.getX() > crossing.getX(),
											"The final-form body must cross after breaching the low corridor");
									helper.assertTrue(Math.abs(reborn.getZ()
											- (helper.absolutePos(new BlockPos(0, REWORK_TEST_Y, 10)).getZ() + 0.5)) < 0.72,
											"The path must stay in the one-block-wide corridor, not route around it");
									helper.assertTrue(countCorridorCeiling(level, helper) < ceilingBefore,
											"A 3.15-block form must directly remove the ceiling it cannot fit beneath");
									helper.assertValueEqual(countCorridorSideWalls(level, helper), sideWallsBefore,
											"The bounded forward probe must not tear down passable corridor side walls");
									terminalCarrier.snapTo(reborn.getX() + 1.0, reborn.getY(), reborn.getZ(),
											0.0F, 0.0F);
									carrierHealth[0] = terminalCarrier.getHealth();
									helper.assertTrue(terminalCarrier.isAlive(),
											"The isolated hostility fixture player must still be alive");
									helper.runAfterDelay(25, () -> {
										helper.assertValueEqual(terminalCarrier.getHealth(), carrierHealth[0],
												"A capped body with no work target must remain non-hostile");
										reborn.hurtServer(level,
												level.damageSources().playerAttack(terminalCarrier), 1.0F);
										waitForHostileAttack(helper, terminalCarrier, carrierHealth[0], 60);
									});
								});
							});
						});
					});
				});
			});
		});
	}

	@GameTest(maxTicks = 220)
	public void emptySegmentApexEventsRecoverWithoutItemOrChunkLoss(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		BlockPos origin = player.blockPosition();
		int itemCount = inventoryCount(player);

		helper.assertTrue(EmptySegmentService.trigger(player,
				EmptySegmentService.EventType.VIEWPOINT_SEPARATION, 30),
				"Viewpoint/body separation apex event must start under server control");
		helper.runAfterDelay(40, () -> {
			var record = data.terminalRecord(player.getUUID()).orElseThrow();
			helper.assertFalse(record.getBooleanOr(TerminalData.EMPTY_SEGMENT_ACTIVE, false),
					"Viewpoint separation must always self-recover");
			helper.assertValueEqual(inventoryCount(player), itemCount,
					"Viewpoint separation must not delete inventory content");
			helper.assertTrue(EmptySegmentService.trigger(player,
					EmptySegmentService.EventType.EXPERIENCE_GAP, 40),
					"Experience continuity gap apex event must start");
		});
		helper.runAfterDelay(95, () -> {
			var record = data.terminalRecord(player.getUUID()).orElseThrow();
			helper.assertFalse(record.getBooleanOr(TerminalData.EMPTY_SEGMENT_ACTIVE, false),
					"Experience gap must self-recover");
			helper.assertTrue(record.contains(TerminalData.EMPTY_SEGMENT_GAP_FROM)
					&& record.contains(TerminalData.EMPTY_SEGMENT_GAP_TO),
					"World-authoritative before/after positions must prove action during the missing interval");
			helper.assertValueEqual(inventoryCount(player), itemCount,
					"No empty-segment apex event may permanently delete an item");
			helper.succeed();
		});
	}

	private static <T extends Mob> T add(net.minecraft.server.level.ServerLevel level, EntityType<T> type,
			BlockPos position) {
		T entity = type.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			throw new AssertionError("Entity factory returned null for " + type);
		}
		entity.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.0F, 0.0F);
		if (!level.addFreshEntity(entity)) {
			throw new AssertionError("Could not add entity " + type);
		}
		return entity;
	}

	private static int inventoryCount(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			count += stack.getCount();
		}
		return count;
	}

	private static void waitForMorphingDismantle(GameTestHelper helper,
			net.minecraft.server.level.ServerLevel level, BlockPos target, FrequencyWorldData data,
			ReworkEntity body, int expectedCount, int oldStage, int newStage, Runnable afterMorph) {
		if (!level.getBlockState(target).is(ModBlocks.REWORK_SCAR)) {
			helper.runAfterDelay(1, () -> waitForMorphingDismantle(helper, level, target, data,
					body, expectedCount, oldStage, newStage, afterMorph));
			return;
		}
		helper.assertValueEqual(CorrectionState.dismantleCount(data), expectedCount,
				"Each completed target must increment the world count exactly once");
		helper.assertValueEqual(body.formStage(), oldStage,
				"The first morph half must keep the old model");
		helper.assertValueEqual(body.morphTargetStage(), newStage,
				"A dismantle may unlock only the next form stage");
		int contractionRemaining = body.morphTicks();
		helper.assertTrue(contractionRemaining >= ReworkEntity.MORPH_DURATION_TICKS - 1
				&& contractionRemaining <= ReworkEntity.MORPH_DURATION_TICKS,
				"The target transition must start one complete 40-tick morph");
		helper.runAfterDelay(contractionRemaining - ReworkEntity.MORPH_SWITCH_TICK + 1, () -> {
			helper.assertValueEqual(body.formStage(), newStage,
					"The renderer-facing form must switch at the 20-tick midpoint");
			assertReworkCollision(helper, body, newStage);
			helper.assertTrue(body.morphTicks() > 0
					&& body.morphTicks() <= ReworkEntity.MORPH_SWITCH_TICK,
					"The new model must unfold during the second morph half");
			int unfoldingRemaining = body.morphTicks();
			helper.runAfterDelay(unfoldingRemaining + 1, () -> {
				helper.assertValueEqual(body.formStage(), newStage,
						"The morph must settle on its target stage");
				helper.assertValueEqual(body.morphTicks(), 0,
						"The morph must release navigation, work, and attacks after 40 ticks");
				afterMorph.run();
			});
		});
	}

	private static void waitForCappedDismantle(GameTestHelper helper,
			net.minecraft.server.level.ServerLevel level, BlockPos target, FrequencyWorldData data,
			ReworkEntity body, int expectedCount, Runnable afterDismantle) {
		if (!level.getBlockState(target).is(ModBlocks.REWORK_SCAR)) {
			helper.runAfterDelay(1, () -> waitForCappedDismantle(helper, level, target, data,
					body, expectedCount, afterDismantle));
			return;
		}
		helper.assertValueEqual(CorrectionState.dismantleCount(data), expectedCount,
				"Capped-stage work must still be recorded exactly once");
		helper.assertValueEqual(body.formStage(), ReworkEntity.MAX_FORM_STAGE,
				"Form stage must remain capped at 5");
		helper.assertValueEqual(body.morphTicks(), 0,
				"A stage-5 dismantle must not replay a no-op morph");
		afterDismantle.run();
	}

	private static void waitForHostileAttack(GameTestHelper helper, ServerPlayer player,
			float healthBeforeAttack, int ticksRemaining) {
		if (player.getHealth() < healthBeforeAttack) {
			helper.succeed();
			return;
		}
		helper.assertTrue(ticksRemaining > 0,
				"A player attack must still make the body temporarily hostile");
		helper.runAfterDelay(1, () -> waitForHostileAttack(helper, player,
				healthBeforeAttack, ticksRemaining - 1));
	}

	private static void resetReworkFixture(FrequencyWorldData data) {
		CorrectionState.update(data, state -> {
			for (String key : new String[] {
					"active", "nascent_organ_pos", "nascent_organ_dismantled", "anomaly_traces",
					"terminal_facility_pos", "last_dismantled_target", "rework_entity_uuid",
					"rework_entity_pos", "last_rework_spawn_tick", "rework_spawn_count",
					"maximum_tick_work", "last_tick_work"
			}) state.remove(key);
			state.putInt("dismantle_count", 0);
		});
	}

	private static void buildTwoBlockHighCorridor(net.minecraft.server.level.ServerLevel level,
			GameTestHelper helper) {
		for (int x = 3; x <= 13; x++) {
			for (int y = REWORK_TEST_Y; y <= REWORK_TEST_Y + 1; y++) {
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 9)),
						Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 11)),
						Blocks.DEEPSLATE_BRICKS.defaultBlockState());
			}
			for (int z = 9; z <= 11; z++) {
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, REWORK_TEST_Y + 2, z)),
						Blocks.DEEPSLATE_BRICKS.defaultBlockState());
			}
		}
		for (int y = REWORK_TEST_Y; y <= REWORK_TEST_Y + 1; y++) {
			for (int z = 9; z <= 11; z++) {
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(3, y, z)),
						Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(13, y, z)),
						Blocks.DEEPSLATE_BRICKS.defaultBlockState());
			}
		}
		for (int x = 4; x <= 12; x++) for (int y = REWORK_TEST_Y; y <= REWORK_TEST_Y + 1; y++) {
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 10)), Blocks.AIR.defaultBlockState());
		}
	}

	private static int countCorridorCeiling(net.minecraft.server.level.ServerLevel level,
			GameTestHelper helper) {
		int count = 0;
		for (int x = 3; x <= 13; x++) {
			if (level.getBlockState(helper.absolutePos(new BlockPos(x, REWORK_TEST_Y + 2, 10)))
					.is(Blocks.DEEPSLATE_BRICKS)) count++;
		}
		return count;
	}

	private static int countCorridorSideWalls(net.minecraft.server.level.ServerLevel level,
			GameTestHelper helper) {
		int count = 0;
		for (int x = 3; x <= 13; x++) for (int y = REWORK_TEST_Y; y <= REWORK_TEST_Y + 1; y++) {
			for (int z : new int[] {9, 11}) {
				if (level.getBlockState(helper.absolutePos(new BlockPos(x, y, z)))
						.is(Blocks.DEEPSLATE_BRICKS)) count++;
			}
		}
		return count;
	}

	private static void assertReworkCollision(GameTestHelper helper, ReworkEntity body, int stage) {
		ReworkCollisionProfile profile = ReworkCollisionProfile.forStage(stage);
		helper.assertTrue(Math.abs(body.getBbWidth() - profile.width()) < 0.001F
				&& Math.abs(body.getBbHeight() - profile.height()) < 0.001F,
				"Rework collision must match the slender stage " + stage + " profile");
		helper.assertTrue(Math.abs(body.getEyeHeight() - profile.eyeHeight()) < 0.001F,
				"Rework eye height must follow the stage " + stage + " head position");
	}

	private static void buildReworkIsolationPlatform(net.minecraft.server.level.ServerLevel level,
			GameTestHelper helper) {
		for (int x = 2; x <= 20; x++) for (int z = 3; z <= 12; z++) {
			level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, REWORK_TEST_Y - 1, z)),
					Blocks.DEEPSLATE_BRICKS.defaultBlockState());
		}
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
