package com.xm.thefourthfrequency.test;

import com.mojang.authlib.GameProfile;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.EndBossArenaService;
import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import com.xm.thefourthfrequency.ending.FriendlyDragonService;
import com.xm.thefourthfrequency.ending.WorldInterfaceAction;
import com.xm.thefourthfrequency.ending.WorldInterfaceAttackService;
import com.xm.thefourthfrequency.ending.WorldInterfaceGatewayState;
import com.xm.thefourthfrequency.ending.WorldInterfaceRitualService;
import com.xm.thefourthfrequency.ending.WorldInterfaceStage;
import com.xm.thefourthfrequency.ending.WorldInterfaceState;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import com.xm.thefourthfrequency.entity.WorldInterfaceEnergyOrbEntity;
import com.xm.thefourthfrequency.entity.WorldInterfacePartEntity;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server integration contracts for the persisted world-interface encounter. */
public final class EndBossGameTests implements CustomTestMethodInvoker {
	@GameTest(setupTicks = 60, maxTicks = 200)
	public void arenaPreparationBuildsExactProtectedTopologyAndBoundedNoDropScars(GameTestHelper helper) {
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		EndBossArenaService.PreparedArena repeated = EndBossArenaService.prepare(end);

		helper.assertValueEqual(arena.gatewayCorePositions().size(), 20,
				"The prepared arena must contain exactly twenty gateways");
		helper.assertValueEqual(new HashSet<>(arena.gatewayCorePositions()).size(), 20,
				"Every gateway core position must be unique");
		helper.assertValueEqual(arena.anchors().size(), 10,
				"The prepared arena must contain exactly ten anchor slots");
		helper.assertTrue(arena.equals(repeated), "Arena preparation must be idempotent in one server run");
		helper.assertTrue(end.getBlockState(arena.altar()).is(ModBlocks.RESONANCE_CORE),
				"The durable preparation marker must be the resonance core");
		for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
			BlockPos floor = arena.center().offset(dx, 0, dz);
			helper.assertTrue(!end.getBlockState(floor.below()).isAir(),
					"The altar must be embedded in the original main-island surface, never floated on a platform: "
							+ floor.below() + " below center " + arena.center());
		}
		for (int dx = -2; dx <= 2; dx++) for (int dz = 6; dz <= 8; dz++) {
			helper.assertTrue(!end.getBlockState(arena.center().offset(dx, 0, dz))
					.is(Blocks.POLISHED_BLACKSTONE_BRICKS),
					"Arena preparation must not add the former artificial south platform");
		}

		Set<BlockPos> protectedPositions = EndBossArenaService.protectedPositions(arena);
		for (BlockPos gateway : arena.gatewayCorePositions()) {
			helper.assertTrue(end.getBlockState(gateway).is(ModBlocks.WARP_GATE_CORE),
					"Every inert gateway must use the custom warp core");
			helper.assertTrue(protectedPositions.contains(gateway)
					&& !EndBossArenaService.canDestroy(end, gateway, end.getBlockState(gateway)),
					"Gateway cores must be protected from encounter terrain edits");
		}
		for (EndBossArenaService.AnchorSlot anchor : arena.anchors()) {
			helper.assertTrue(end.getBlockState(anchor.position().below()).is(ModBlocks.STABILITY_ANCHOR_CAGE),
					"Every anchor slot must have a stability cage");
			EndCrystal crystal = EndBossArenaService.findAuthoritativeAnchor(end, anchor.crystalUuid()).orElse(null);
			helper.assertTrue(crystal != null
						&& EndBossArenaService.isAuthoritativeAnchor(crystal, anchor.index())
						&& crystal.isInvulnerable(),
					"Every prepared anchor UUID must resolve to its authoritative crystal");
			helper.assertTrue(protectedPositions.contains(anchor.position()),
					"Every authoritative anchor position must be protected");
		}

		EndBossArenaService.restoreTerrainEditCount(end, 0);
		BlockPos firstBase = arena.center().offset(24, 0, 0);
		Set<UUID> existingItems = end.getEntitiesOfClass(ItemEntity.class,
				new AABB(firstBase).inflate(24.0D)).stream().map(Entity::getUUID).collect(
						java.util.stream.Collectors.toUnmodifiableSet());
		List<BlockPos> firstBatch = placeEditableLine(end, firstBase, 16);
		helper.assertValueEqual(EndBossArenaService.queueTerrainScar(end, firstBatch, 16, 0x51A2L), 16,
				"All eligible scar candidates should enter the bounded queue");
		helper.assertValueEqual(EndBossArenaService.tickTerrainScars(end), 8,
				"A server tick may commit at most eight permanent terrain edits");
		helper.assertValueEqual(EndBossArenaService.permanentTerrainEdits(end), 8,
				"Only successfully committed changes consume the permanent budget");
		helper.assertTrue(end.getEntitiesOfClass(ItemEntity.class, new AABB(firstBase).inflate(24.0D)).stream()
				.allMatch(item -> existingItems.contains(item.getUUID())),
				"World-interface terrain scars must not create item drops");

		EndBossArenaService.restoreTerrainEditCount(end, 2_045);
		List<BlockPos> finalBatch = placeEditableLine(end, firstBase.offset(0, 0, 4), 8);
		EndBossArenaService.queueTerrainScar(end, finalBatch, 8, 0x51A3L);
		helper.assertValueEqual(EndBossArenaService.tickTerrainScars(end), 3,
				"The final tick must stop exactly at the 2048-edit lifetime cap");
		helper.assertValueEqual(EndBossArenaService.permanentTerrainEdits(end), 2_048,
				"The lifetime terrain budget must be exactly 2048");
		helper.assertValueEqual(EndBossArenaService.tickTerrainScars(end), 0,
				"No queued scar may settle after the lifetime cap is exhausted");
		EndBossArenaService.restoreTerrainEditCount(end, 0);
		helper.succeed();
	}

	@GameTest(setupTicks = 62, maxTicks = 60)
	public void portalTransitionArrivesAtAltarAndDeathRespawnsAtSafePoint(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		initializeWaiting(server, encounterId, stateLayout(arena));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerPlayer respawned = null;
		try {
			var transition = EndBossEncounterService.createPortalTransition(server.overworld(), player, BlockPos.ZERO);
			helper.assertTrue(transition.isPresent(), "A prepared encounter must replace vanilla End travel");
			player.teleport(transition.orElseThrow());
			helper.assertTrue(player.level().dimension() == Level.END
					&& player.blockPosition().distManhattan(arena.safeSpawn()) <= 3,
					"Portal travel must arrive directly at the altar safe point");
			ServerPlayer.RespawnConfig config = player.getRespawnConfig();
			helper.assertTrue(config != null && config.respawnData().dimension() == Level.END
					&& config.respawnData().pos().equals(arena.safeSpawn()),
					"Entering the encounter must install the authoritative altar respawn");
			respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
			helper.assertTrue(respawned.level().dimension() == Level.END
					&& respawned.blockPosition().distManhattan(arena.safeSpawn()) <= 4,
					"Death before the ending must respawn beside the altar");
		} finally {
			if (respawned != null) respawned.setRespawnPosition(null, false);
			else player.setRespawnPosition(null, false);
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 64, maxTicks = 40)
	public void persistedStateCommitsOneFrozenRosterAndLocksBothEndingBranches(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		EndBossArenaService.PreparedArena prepared = EndBossArenaService.prepare(requireEnd(helper));
		WorldInterfaceState.ArenaLayout layout = stateLayout(prepared);
		FrequencyWorldData data = FrequencyWorldData.get(server);

		try {
			clearWorldInterface(data);
			exerciseEnding(server, layout, WorldInterfaceStage.SUCCESS_RESOLUTION,
					WorldInterfaceState.Outcome.SUCCESS, helper);
			clearWorldInterface(data);
			exerciseEnding(server, layout, WorldInterfaceStage.FAILURE_RESOLUTION,
					WorldInterfaceState.Outcome.FAILURE, helper);
		} finally {
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 66, maxTicks = 40)
	public void persistedSacrificeScalesAtomicallyForOneThreeAndEightPlayers(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		WorldInterfaceState.ArenaLayout layout = stateLayout(EndBossArenaService.prepare(requireEnd(helper)));
		FrequencyWorldData data = FrequencyWorldData.get(server);
		try {
			for (int rosterSize : List.of(1, 3, 8)) {
				clearWorldInterface(data);
				UUID encounterId = UUID.randomUUID();
				WorldInterfaceState.Snapshot snapshot = initializeWaiting(server, encounterId, layout);
				Set<UUID> roster = new LinkedHashSet<>();
				List<WorldInterfaceState.TerminalTransaction> transactions = new ArrayList<>();
				for (int index = 0; index < rosterSize; index++) {
					UUID playerId = UUID.randomUUID();
					roster.add(playerId);
					transactions.add(terminalTransaction(playerId,
							WorldInterfaceState.TerminalTransactionState.REMOVED));
				}
				snapshot = requireApplied(WorldInterfaceState.mutate(server, encounterId, snapshot.revision(), state -> {
					state.freezeRoster(roster);
					for (WorldInterfaceState.TerminalTransaction transaction : transactions) {
						state.putTerminalTransaction(transaction);
					}
					state.commitSacrifice(600.0D * rosterSize);
				}), "commit " + rosterSize + "-player sacrifice");
				helper.assertValueEqual(snapshot.frozenRoster().size(), rosterSize,
						"The committed roster size must remain frozen");
				helper.assertTrue(Math.abs(snapshot.maxVirtualHealth() - 600.0D * rosterSize) < 0.000_001D,
						"Maximum health must be exactly 600 times the frozen roster");
				helper.assertTrue(snapshot.terminalTransactions().values().stream().allMatch(value ->
						value.state() == WorldInterfaceState.TerminalTransactionState.COMMITTED),
						"Every removed terminal must commit in the same state write");
			}
		} finally {
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 68, maxTicks = 60)
	public void threePlayersMayDepositFromOneSharedSnapshotRevision(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		WorldInterfaceState.Snapshot waiting = initializeWaiting(server, encounterId, stateLayout(arena));
		List<ServerPlayer> players = new ArrayList<>();
		Map<UUID, GameType> originalGameModes = spectateExistingPlayers(server);
		try {
			for (int index = 0; index < 3; index++) {
				ServerPlayer player = preparedMockParticipant(helper, end, arena.altar(), data);
				players.add(player);
			}
			long sharedRevision = waiting.revision();
			Set<UUID> expectedRoster = players.stream().map(ServerPlayer::getUUID)
					.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
			for (int index = 0; index < players.size(); index++) {
				ServerPlayer player = players.get(index);
				WorldInterfaceRitualService.RitualResult result = WorldInterfaceRitualService.deposit(
						player, encounterId, sharedRevision);
				String diagnosis = ritualDiagnosis(server, data, player, sharedRevision, result);
				helper.assertTrue(result.applied(),
						"Stable-roster deposit " + index
								+ " from one shared UI snapshot must rebase server-side; " + diagnosis);
				helper.assertTrue(result.snapshot().frozenRoster().equals(expectedRoster),
						"Every rebased deposit must retain the exact initial online roster; " + diagnosis);
				helper.assertFalse(hasValidBoundTerminal(player, data),
						"A successful deposit must move that player's bound terminal into custody; " + diagnosis);
			}
			WorldInterfaceState.Snapshot committed = WorldInterfaceState.snapshot(server);
			helper.assertTrue(committed.stage() == WorldInterfaceStage.SUMMONING
					&& committed.sacrificeCommitted(), "The third durable removal must atomically begin summoning");
			helper.assertValueEqual(committed.frozenRoster().size(), 3,
					"Exactly the three simultaneous participants must be frozen");
			helper.assertTrue(Math.abs(committed.maxVirtualHealth() - 1_800.0D) < 0.000_001D,
					"Three simultaneous deposits must create exactly 1800 virtual health");
		} finally {
			restoreGameModes(server, originalGameModes);
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 70, maxTicks = 60)
	public void joiningPlayerRollsBackPartialRitualAndReturnsHostedTerminal(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		WorldInterfaceState.Snapshot waiting = initializeWaiting(server, encounterId, stateLayout(arena));
		Map<UUID, GameType> originalGameModes = spectateExistingPlayers(server);
		try {
			ServerPlayer first = preparedMockParticipant(helper, end, arena.altar(), data);
			preparedMockParticipant(helper, end, arena.altar(), data);
			WorldInterfaceRitualService.RitualResult deposited = WorldInterfaceRitualService.deposit(
					first, encounterId, waiting.revision());
			helper.assertTrue(deposited.applied() && !deposited.snapshot().sacrificeCommitted(),
					"The first of two terminals must remain a recoverable partial ritual; "
							+ ritualDiagnosis(server, data, first, waiting.revision(), deposited));

			preparedMockParticipant(helper, end, arena.altar(), data);
			long rollbackRevision = deposited.snapshot().revision();
			WorldInterfaceRitualService.RitualResult rollback = WorldInterfaceRitualService.deposit(
					first, encounterId, rollbackRevision);
			String rollbackDiagnosis = ritualDiagnosis(server, data, first, rollbackRevision, rollback);
			helper.assertTrue(rollback.applied() && "roster_changed".equals(rollback.reason()),
					"A newly joined online player must trigger the explicit roster-change rollback; "
							+ rollbackDiagnosis);
			WorldInterfaceState.Snapshot rolledBack = WorldInterfaceState.snapshot(server);
			helper.assertTrue(rolledBack.frozenRoster().isEmpty()
					&& rolledBack.terminalTransactions().isEmpty(),
					"A pre-summoning roster join must abort and fully unwind the partial journal; "
							+ rollbackDiagnosis);
			helper.assertTrue(hasValidBoundTerminal(first, data),
					"The hosted terminal must be returned exactly through the recovery path; "
							+ rollbackDiagnosis);
		} finally {
			restoreGameModes(server, originalGameModes);
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 72, maxTicks = 40)
	public void offlineReturnEntitlementRemainsDurable(GameTestHelper helper) throws ReflectiveOperationException {
		MinecraftServer server = helper.getLevel().getServer();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		UUID offlinePlayer = UUID.randomUUID();
		WorldInterfaceState.Snapshot waiting = initializeWaiting(server, encounterId,
				stateLayout(EndBossArenaService.prepare(requireEnd(helper))));
		try {
			requireApplied(WorldInterfaceState.mutate(server, encounterId, waiting.revision(), state -> {
				state.freezeRoster(Set.of(offlinePlayer));
				state.putTerminalTransaction(terminalTransaction(offlinePlayer,
						WorldInterfaceState.TerminalTransactionState.RETURN_PENDING));
			}), "persist offline return entitlement");
			Method processReturns = WorldInterfaceRitualService.class.getDeclaredMethod(
					"processReturns", MinecraftServer.class);
			processReturns.setAccessible(true);
			processReturns.invoke(null, server);
			WorldInterfaceState.Snapshot after = WorldInterfaceState.snapshot(server);
			helper.assertTrue(after.terminalTransactions().get(offlinePlayer).state()
					== WorldInterfaceState.TerminalTransactionState.RETURN_PENDING,
					"An offline player's return entitlement must survive every recovery pass");
		} finally {
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 74, maxTicks = 60)
	public void lateJoinDamageDeduplicatesRootAndPartPerAttackerTick(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		WorldInterfaceEntity boss = null;
		WorldInterfacePartEntity part = null;
		try {
			WorldInterfaceState.Snapshot combat = committedCombat(server, encounterId, stateLayout(arena));
			boss = ModEntities.WORLD_INTERFACE.create(end, EntitySpawnReason.EVENT);
			if (boss == null) throw new AssertionError("Unable to create world-interface fixture");
			boss.bindEncounter(encounterId);
			boss.snapTo(arena.center().getX() + 0.5D, arena.center().getY() + 18.0D,
					arena.center().getZ() + 0.5D, 0.0F, 0.0F);
			helper.assertTrue(end.addFreshEntity(boss), "Boss fixture must enter the End");
			WorldInterfaceEntity storedBoss = boss;
			combat = requireApplied(WorldInterfaceState.mutate(server, encounterId, combat.revision(),
					state -> state.setBossUuid(storedBoss.getUUID())), "bind boss fixture");

			part = ModEntities.WORLD_INTERFACE_PART.create(end, EntitySpawnReason.EVENT);
			if (part == null) throw new AssertionError("Unable to create world-interface part fixture");
			part.attach(boss, 0);
			helper.assertTrue(end.addFreshEntity(part), "Part fixture must enter the End");
			ServerPlayer first = helper.makeMockServerPlayerInLevel();
			ServerPlayer second = helper.makeMockServerPlayerInLevel();
			for (ServerPlayer player : List.of(first, second)) {
				player.teleportTo(end, arena.center().getX() + 4.5D, arena.center().getY() + 2.0D,
						arena.center().getZ() + 0.5D, Set.of(), 0.0F, 0.0F, true);
			}
			helper.assertFalse(combat.frozenRoster().contains(first.getUUID())
					|| combat.frozenRoster().contains(second.getUUID()),
					"Damage fixtures must be genuine post-freeze participants");
			helper.assertTrue(boss.hurtServer(end, end.damageSources().playerAttack(first), 10.0F),
					"A late joiner may damage the boss root");
			helper.assertFalse(part.hurtServer(end, end.damageSources().playerAttack(first), 10.0F),
					"The same attacker's same-tick part hit must be deduplicated");
			helper.assertTrue(part.hurtServer(end, end.damageSources().playerAttack(second), 10.0F),
					"A different late joiner in the same tick must still deal damage");
			WorldInterfaceState.Snapshot after = WorldInterfaceState.snapshot(server);
			helper.assertTrue(Math.abs(after.virtualHealth() - 580.0D) < 0.000_001D,
					"Only one ten-point hit per attacker may reach the virtual health pool");
		} finally {
			if (part != null) part.discard();
			if (boss != null) boss.discard();
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 76, maxTicks = 40)
	public void zeroDamageCannotDestroyAuthoritativeAnchor(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		try {
			WorldInterfaceState.Snapshot combat = committedCombat(server, encounterId, stateLayout(arena));
			EndBossArenaService.restoreAuthoritativeAnchors(end, combat, false);
			EndBossArenaService.AnchorSlot first = arena.anchors().getFirst();
			EndCrystal crystal = EndBossArenaService.findAuthoritativeAnchor(end, first.crystalUuid()).orElse(null);
			if (crystal == null) throw new AssertionError("Authoritative anchor fixture is missing");
			ServerPlayer lateJoiner = helper.makeMockServerPlayerInLevel();
			lateJoiner.teleportTo(end, first.position().getX() + 2.5D, first.position().getY(),
					first.position().getZ() + 0.5D, Set.of(), 0.0F, 0.0F, true);
			helper.assertFalse(crystal.hurtServer(end, end.damageSources().playerAttack(lateJoiner), 0.0F),
					"A zero-damage player source must not consume an authoritative anchor");
			WorldInterfaceState.Snapshot after = WorldInterfaceState.snapshot(server);
			helper.assertFalse(after.anchors().get(first.index()).destroyed() || crystal.isRemoved(),
					"Zero damage must leave both the durable anchor bit and entity intact");
		} finally {
			EndBossArenaService.setAnchorsInvulnerable(end, arena, true);
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 78, maxTicks = 60)
	public void restartRecoveryCancelsTransientAttackButPreservesCooldowns(GameTestHelper helper)
			throws ReflectiveOperationException {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		UUID controlledPlayer = UUID.randomUUID();
		WorldInterfaceState.Snapshot combat = committedCombat(server, encounterId, stateLayout(arena));
		try {
			combat = requireApplied(WorldInterfaceState.mutate(server, encounterId, combat.revision(), state -> {
				state.setClock(100L, end.getGameTime(), 0);
				state.setActionSchedule(5L, 1, 500L);
				state.putControlCooldown(controlledPlayer, 700L);
				state.setCurrentAttack(new WorldInterfaceState.AttackEnvelope(1, 4L, 100L, 145L,
						0xA771L, arena.center(), Set.of(), 0, false));
			}), "persist restart fixture");
			var recoveredField = EndBossEncounterService.class.getDeclaredField("RECOVERED_SERVERS");
			recoveredField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Set<MinecraftServer> recoveredServers = (Set<MinecraftServer>) recoveredField.get(null);
			recoveredServers.remove(server);
			Method recover = EndBossEncounterService.class.getDeclaredMethod("recoverAfterRestart", MinecraftServer.class);
			recover.setAccessible(true);
			recover.invoke(null, server);

			WorldInterfaceState.Snapshot after = WorldInterfaceState.snapshot(server);
			helper.assertTrue(after.currentAttack().isEmpty() && after.runningSinceGameTime() == -1L,
					"Restart recovery must cancel transient attacks and pause the active clock");
			helper.assertValueEqual(after.recoveryGraceTicks(), 40,
					"Restart recovery must grant exactly forty safe ticks");
			helper.assertTrue(after.nextActionActiveTick() >= 500L,
					"Restart recovery must never shorten the stored global attack cooldown");
			helper.assertValueEqual(after.controlCooldowns().get(controlledPlayer), 700L,
					"The per-player 600-tick strong-control immunity must survive restart");
		} finally {
			for (WorldInterfaceEntity entity : end.getEntitiesOfClass(WorldInterfaceEntity.class,
					new AABB(arena.center()).inflate(256.0D), candidate -> true)) entity.discard();
			for (WorldInterfacePartEntity entity : end.getEntitiesOfClass(WorldInterfacePartEntity.class,
					new AABB(arena.center()).inflate(256.0D), candidate -> true)) entity.discard();
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 80, maxTicks = 40)
	public void friendlyDragonUsesPersistedIdentityAndNonHostileContract(GameTestHelper helper) {
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		UUID dragonId = UUID.randomUUID();
		EnderDragon dragon = FriendlyDragonService.spawn(end, arena.center(), dragonId);
		try {
			helper.assertTrue(dragon.getUUID().equals(dragonId) && FriendlyDragonService.isFriendly(dragon),
					"The successful ending dragon must use its persisted identity tag");
			helper.assertTrue(dragon.isInvulnerable() && dragon.isNoAi() && dragon.getTarget() == null,
					"The successful ending dragon must remain non-hostile and player-immune");
			helper.assertTrue(FriendlyDragonService.recover(end, dragonId).orElse(null) == dragon,
					"Recovery must resolve the same persistent friendly dragon instead of duplicating it");
		} finally {
			dragon.discard();
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 82, maxTicks = 80)
	public void allNineWorldInterfaceActionsExposeTheirServerContracts(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		WorldInterfaceEntity boss = null;
		try {
			WorldInterfaceState.Snapshot snapshot = phaseThreeCombat(server, encounterId, stateLayout(arena));
			boss = spawnAttackBoss(end, encounterId, arena.center());
			WorldInterfaceEntity activeBoss = boss;
			ServerPlayer target = attackTarget(helper, end, arena.safeSpawn());

			// 1: laser -- exact direct damage after the 45-tick warning.
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.LASER_SWEEP,
					List.of(target), 1L);
			float before = target.getHealth();
			WorldInterfaceAttackService.AttackTick laserTick = WorldInterfaceAttackService.tick(
					end, boss, snapshot, 45L);
			float observedLaserDamage = before - target.getHealth();
			helper.assertTrue(Math.abs(observedLaserDamage - 12.0F) < 0.001F,
					"Laser direct damage must be exactly twelve points after forty-five ticks; observed="
							+ observedLaserDamage + ", health=" + before + "->" + target.getHealth()
							+ ", mode=" + target.gameMode.getGameModeForPlayer()
							+ ", abilityInvulnerable=" + target.getAbilities().invulnerable
							+ ", entityInvulnerable=" + target.isInvulnerable()
							+ ", alive=" + target.isAlive() + ", dimension=" + target.level().dimension().identifier()
							+ ", attackStatus=" + laserTick.status()
							+ ", damageApplied=" + laserTick.replacementEnvelope()
									.map(WorldInterfaceState.AttackEnvelope::damageApplied).orElse(false));
			snapshot = cancelAndClearAttack(server, encounterId);

			// 2: energy orb -- a dedicated transient entity is spawned and cancellation removes it.
			resetAttackTarget(target, end, arena.safeSpawn());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.ENERGY_ORB,
					List.of(target), 2L);
			helper.assertValueEqual(end.getEntitiesOfClass(WorldInterfaceEnergyOrbEntity.class,
					new AABB(arena.center()).inflate(96.0D), Entity::isAlive).size(), 1,
					"Energy-orb action must spawn exactly one dedicated orb");
			WorldInterfaceEnergyOrbEntity orb = end.getEntitiesOfClass(WorldInterfaceEnergyOrbEntity.class,
					new AABB(arena.center()).inflate(96.0D), Entity::isAlive).getFirst();
			before = target.getHealth();
			orb.setPos(target.position());
			orb.detonate(end, true);
			helper.assertTrue(Math.abs((before - target.getHealth()) - 14.0F) < 0.001F,
					"Energy-orb impact must deal exactly fourteen damage");
			WorldInterfaceAttackService.tick(end, boss, snapshot, 0L);
			snapshot = cancelAndClearAttack(server, encounterId);
			helper.assertTrue(end.getEntitiesOfClass(WorldInterfaceEnergyOrbEntity.class,
					new AABB(arena.center()).inflate(96.0D), Entity::isAlive).isEmpty(),
					"Cancelling the orb action must remove its transient entity");

			// 3: grab slam -- control begins at 30 and is released with twelve damage at 70.
			resetAttackTarget(target, end, arena.safeSpawn());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.GRAB_SLAM,
					List.of(target), 3L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 30L);
			helper.assertTrue(target.isNoGravity(), "Grab slam must take authoritative control at tick thirty");
			before = target.getHealth();
			WorldInterfaceAttackService.tick(end, boss, snapshot, 70L);
			helper.assertFalse(target.isNoGravity(), "Grab slam must restore the original gravity state");
			helper.assertTrue(Math.abs((before - target.getHealth()) - 12.0F) < 0.001F,
					"Grab slam must deal exactly twelve direct damage");
			snapshot = cancelAndClearAttack(server, encounterId);

			// 4: mental assault -- damage begins after the 40-tick gaze warning.
			resetAttackTarget(target, end, arena.safeSpawn());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.MENTAL_ASSAULT,
					List.of(target), 4L);
			before = target.getHealth();
			WorldInterfaceAttackService.tick(end, boss, snapshot, 40L);
			helper.assertTrue(Math.abs((before - target.getHealth()) - 4.0F) < 0.001F,
					"Mental assault must deal exactly four damage");
			snapshot = cancelAndClearAttack(server, encounterId);

			// 5: weapon theft -- the exact selected weapon enters custody and cancellation restores it.
			resetAttackTarget(target, end, arena.safeSpawn());
			target.getInventory().clearContent();
			target.getInventory().setSelectedSlot(0);
			target.getInventory().setItem(0, Items.DIAMOND_SWORD.getDefaultInstance());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.CHARGE_WEAPON_STEAL,
					List.of(target), 5L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 35L);
			helper.assertTrue(target.getInventory().getItem(0).isEmpty(),
					"Weapon theft must remove the selected valid weapon after thirty-five ticks");
			snapshot = cancelAndClearAttack(server, encounterId);
			helper.assertValueEqual(countInventoryItem(target, Items.DIAMOND_SWORD), 1,
					"Cancelling weapon custody must restore the exact sword once");

			// 6: grab throw -- a bounded ten-tick arc releases control and deals at most ten damage.
			resetAttackTarget(target, end, arena.safeSpawn());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.GRAB_THROW,
					List.of(target), 6L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 30L);
			helper.assertTrue(target.isNoGravity(), "Grab throw must take control after its warning");
			WorldInterfaceAttackService.tick(end, boss, snapshot, 50L);
			before = target.getHealth();
			WorldInterfaceAttackService.tick(end, boss, snapshot, 60L);
			helper.assertFalse(target.isNoGravity(), "Grab throw must release control at the safe landing");
			helper.assertTrue(Math.abs((before - target.getHealth()) - 10.0F) < 0.001F,
					"Grab throw must deal exactly ten direct collision damage");
			helper.assertTrue(horizontalDistanceSquared(target.position(), arena.center().getCenter())
					<= 130.0D * 130.0D, "Grab throw landing must remain inside the safe combat radius");
			snapshot = clearCurrentAttack(server, encounterId);

			// 7: gaze hotbar clear -- one whole slot becomes a protected world item at tick 48.
			resetAttackTarget(target, end, arena.safeSpawn());
			target.getInventory().clearContent();
			target.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.GAZE_HOTBAR_CLEAR,
					List.of(target), 7L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 48L);
			helper.assertTrue(target.getInventory().getItem(0).isEmpty(),
					"Hotbar clear must remove the first complete slot at tick forty-eight");
			helper.assertTrue(end.getEntitiesOfClass(ItemEntity.class,
					new AABB(target.blockPosition()).inflate(8.0D), item -> item.getItem().is(Items.COBBLESTONE))
					.stream().anyMatch(ItemEntity::isInvulnerable),
					"The removed hotbar stack must exist as an invulnerable world item");
			snapshot = cancelAndClearAttack(server, encounterId);

			// 8: arrow reflection -- a captured player arrow becomes one twenty-arrow boss volley.
			resetAttackTarget(target, end, arena.safeSpawn());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.ARROW_REFLECTION,
					List.of(target), 8L);
			Arrow captured = EntityType.ARROW.create(end, EntitySpawnReason.EVENT);
			if (captured == null) throw new AssertionError("Unable to create captured-arrow fixture");
			captured.setOwner(target);
			captured.setPos(boss.getX(), boss.getEyeY(), boss.getZ());
			helper.assertTrue(end.addFreshEntity(captured), "Captured-arrow fixture must enter the End");
			helper.assertTrue(WorldInterfaceAttackService.captureArrow(end, boss, captured)
					&& captured.noPhysics && captured.isNoGravity(),
					"Player arrows touching a boss part must be frozen into custody");
			WorldInterfaceAttackService.tick(end, boss, snapshot, 40L);
			helper.assertValueEqual(end.getEntitiesOfClass(AbstractArrow.class,
					new AABB(boss.blockPosition()).inflate(96.0D), arrow -> arrow.getOwner() == activeBoss).size(), 20,
					"One reflection volley must contain exactly twenty boss-owned arrows");
			snapshot = cancelAndClearAttack(server, encounterId);
			helper.assertTrue(end.getEntitiesOfClass(AbstractArrow.class,
					new AABB(boss.blockPosition()).inflate(96.0D), arrow -> arrow.getOwner() == activeBoss).isEmpty(),
					"Cancelling reflection must remove every leased reflected arrow");

			// 9: forced eviction -- warning completion closes the selected non-host connection.
			ServerPlayer evicted = attackTarget(helper, end, arena.safeSpawn());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.FORCED_EVICTION,
					List.of(evicted), 9L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 120L);
			helper.assertFalse(evicted.connection.isAcceptingMessages(),
					"Forced eviction must disconnect its selected non-host after 120 warning ticks");
			WorldInterfaceAttackService.tick(end, boss, snapshot, 121L);
			clearCurrentAttack(server, encounterId);
		} finally {
			WorldInterfaceAttackService.cancelAndRestore(server, encounterId);
			EndBossArenaService.cancelQueuedScars(end);
			if (boss != null) boss.discard();
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 84, maxTicks = 80)
	public void attackRecoveryRestoresCustodyDropsAndControlAcrossCancellationPaths(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		WorldInterfaceEntity boss = null;
		try {
			WorldInterfaceState.Snapshot snapshot = phaseThreeCombat(server, encounterId, stateLayout(arena));
			boss = spawnAttackBoss(end, encounterId, arena.center());
			ServerPlayer target = attackTarget(helper, end, arena.safeSpawn());

			// Simulated disconnect returns an entrusted weapon exactly once.
			target.getInventory().clearContent();
			target.getInventory().setSelectedSlot(0);
			target.getInventory().setItem(0, Items.DIAMOND_AXE.getDefaultInstance());
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.CHARGE_WEAPON_STEAL,
					List.of(target), 101L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 35L);
			helper.assertTrue(target.getInventory().getItem(0).isEmpty(), "Weapon fixture must enter custody");
			WorldInterfaceAttackService.onDisconnect(target, encounterId);
			helper.assertValueEqual(countInventoryItem(target, Items.DIAMOND_AXE), 1,
					"Disconnect recovery must return the entrusted weapon exactly once");
			snapshot = cancelAndClearAttack(server, encounterId);

			// Restart cancellation discards the protected drop and restores its exact stack.
			resetAttackTarget(target, end, arena.safeSpawn());
			target.getInventory().clearContent();
			target.getInventory().setItem(0, new ItemStack(Items.AMETHYST_SHARD, 7));
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.GAZE_HOTBAR_CLEAR,
					List.of(target), 102L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 48L);
			helper.assertTrue(target.getInventory().getItem(0).isEmpty(), "Hotbar fixture must become a world drop");
			WorldInterfaceAttackService.onRestart(server, encounterId);
			helper.assertValueEqual(countInventoryItem(target, Items.AMETHYST_SHARD), 7,
					"Restart recovery must restore the complete hotbar stack without duplication");
			snapshot = clearCurrentAttack(server, encounterId);

			// Disconnect releases a grab and restores its pre-control position.
			resetAttackTarget(target, end, arena.safeSpawn());
			Vec3 original = target.position();
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.GRAB_SLAM,
					List.of(target), 103L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 30L);
			helper.assertTrue(target.isNoGravity(), "Disconnect grab fixture must be controlled");
			WorldInterfaceAttackService.onDisconnect(target, encounterId);
			helper.assertFalse(target.isNoGravity(), "Disconnect must restore the original gravity flag");
			helper.assertTrue(target.position().distanceToSqr(original) < 0.01D,
					"Disconnect must restore the pre-grab position");
			snapshot = cancelAndClearAttack(server, encounterId);

			// Restart cancellation performs the same control restoration without a real process restart.
			resetAttackTarget(target, end, arena.safeSpawn());
			original = target.position();
			snapshot = beginAttack(end, boss, snapshot, WorldInterfaceAction.GRAB_THROW,
					List.of(target), 104L);
			WorldInterfaceAttackService.tick(end, boss, snapshot, 30L);
			helper.assertTrue(target.isNoGravity(), "Restart grab fixture must be controlled");
			WorldInterfaceAttackService.onRestart(server, encounterId);
			helper.assertFalse(target.isNoGravity(), "Restart cancellation must restore gravity");
			helper.assertTrue(target.position().distanceToSqr(original) < 0.01D,
					"Restart cancellation must restore the pre-grab position");
			clearCurrentAttack(server, encounterId);
		} finally {
			WorldInterfaceAttackService.cancelAndRestore(server, encounterId);
			if (boss != null) boss.discard();
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 86, maxTicks = 40)
	public void authoritativeAnchorsRejectNonPlayerDamageThroughTheMixin(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		try {
			phaseThreeCombat(server, encounterId, stateLayout(arena));
			EndBossArenaService.setAnchorsInvulnerable(end, arena, false);
			EndCrystal anchor = EndBossArenaService.findAuthoritativeAnchor(end,
					arena.anchors().getFirst().crystalUuid()).orElse(null);
			if (anchor == null) throw new AssertionError("Authoritative anchor fixture is missing");
			helper.assertFalse(anchor.hurtServer(end, end.damageSources().magic(), 5.0F),
					"A non-player damage source must be rejected by the anchor mixin");
			helper.assertTrue(anchor.isAlive() && WorldInterfaceState.snapshot(server).aliveAnchorCount() == 10,
					"Rejected non-player damage must preserve the entity and all ten authoritative anchors");
		} finally {
			EndBossArenaService.setAnchorsInvulnerable(end, arena, true);
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	@GameTest(setupTicks = 88, maxTicks = 40)
	public void completeStageRemovesStrayRootAndParts(GameTestHelper helper) throws ReflectiveOperationException {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = requireEnd(helper);
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(end);
		FrequencyWorldData data = FrequencyWorldData.get(server);
		clearWorldInterface(data);
		UUID encounterId = UUID.randomUUID();
		WorldInterfaceState.Snapshot snapshot = committedCombat(server, encounterId, stateLayout(arena));
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_1, WorldInterfaceStage.PHASE_2), "phase two");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_2, WorldInterfaceStage.PHASE_3), "phase three");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_3, WorldInterfaceStage.SUCCESS_RESOLUTION), "success resolution");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.SUCCESS_RESOLUTION, WorldInterfaceStage.PORTAL_OPEN), "portal open");
		requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PORTAL_OPEN, WorldInterfaceStage.COMPLETE), "complete");

		WorldInterfaceEntity boss = ModEntities.WORLD_INTERFACE.create(end, EntitySpawnReason.EVENT);
		WorldInterfacePartEntity part = ModEntities.WORLD_INTERFACE_PART.create(end, EntitySpawnReason.EVENT);
		if (boss == null || part == null) throw new AssertionError("Unable to create cleanup fixtures");
		boss.bindEncounter(encounterId);
		boss.snapTo(arena.center().getX() + 0.5D, arena.center().getY() + 18.0D,
				arena.center().getZ() + 0.5D, 0.0F, 0.0F);
		end.addFreshEntity(boss);
		part.attach(boss, 0);
		end.addFreshEntity(part);
		try {
			Method tickStart = EndBossEncounterService.class.getDeclaredMethod("tickStart", MinecraftServer.class);
			tickStart.setAccessible(true);
			tickStart.invoke(null, server);
			helper.assertTrue(boss.isRemoved(), "A completed encounter must remove every stray root entity");
			helper.assertTrue(part.isRemoved(), "A completed encounter must immediately remove collision proxies");
		} finally {
			clearWorldInterface(data);
		}
		helper.succeed();
	}

	private static WorldInterfaceState.Snapshot phaseThreeCombat(MinecraftServer server, UUID encounterId,
			WorldInterfaceState.ArenaLayout layout) {
		WorldInterfaceState.Snapshot snapshot = committedCombat(server, encounterId, layout);
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_1, WorldInterfaceStage.PHASE_2), "enter phase-two attack fixture");
		return requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_2, WorldInterfaceStage.PHASE_3), "enter phase-three attack fixture");
	}

	private static WorldInterfaceEntity spawnAttackBoss(ServerLevel level, UUID encounterId, BlockPos center) {
		WorldInterfaceEntity boss = ModEntities.WORLD_INTERFACE.create(level, EntitySpawnReason.EVENT);
		if (boss == null) throw new AssertionError("Unable to create world-interface attack fixture");
		boss.bindEncounter(encounterId);
		boss.snapTo(center.getX() + 0.5D, center.getY() + 18.0D,
				center.getZ() + 0.5D, 0.0F, 0.0F);
		if (!level.addFreshEntity(boss)) throw new AssertionError("World-interface attack fixture could not spawn");
		return boss;
	}

	private static ServerPlayer attackTarget(GameTestHelper helper, ServerLevel level, BlockPos safeSpawn) {
		ServerPlayer player = makeCombatServerPlayer(helper, level);
		player.setGameMode(GameType.SURVIVAL);
		if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
			throw new AssertionError("Attack target could not enter survival mode");
		}
		resetAttackTarget(player, level, safeSpawn);
		player.getInventory().clearContent();
		return player;
	}

	private static ServerPlayer makeCombatServerPlayer(GameTestHelper helper, ServerLevel level) {
		MinecraftServer server = helper.getLevel().getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "world-interface-test");
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	private static void resetAttackTarget(ServerPlayer player, ServerLevel level, BlockPos safeSpawn) {
		if (!player.teleportTo(level, safeSpawn.getX() + 0.5D, safeSpawn.getY() + 1.0D,
				safeSpawn.getZ() + 0.5D, Set.of(), 180.0F, 0.0F, true)) {
			throw new AssertionError("Attack target could not enter the End arena");
		}
		player.setNoGravity(false);
		player.setDeltaMovement(Vec3.ZERO);
		player.setHealth(player.getMaxHealth());
		player.invulnerableTime = 0;
	}

	private static WorldInterfaceState.Snapshot beginAttack(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, WorldInterfaceAction action,
			List<ServerPlayer> targets, long sequence) {
		WorldInterfaceAttackService.AttackStart started = WorldInterfaceAttackService.begin(
				level, boss, snapshot, action, targets, 0L, sequence);
		if (started.action() != action || started.envelope().actionWireId() != action.wireId()) {
			throw new AssertionError("Attack start envelope did not preserve " + action.serializedName());
		}
		return requireApplied(WorldInterfaceState.mutate(level.getServer(),
				snapshot.encounterId().orElseThrow(), snapshot.revision(),
				state -> state.setCurrentAttack(started.envelope())), "persist " + action.serializedName());
	}

	private static WorldInterfaceState.Snapshot cancelAndClearAttack(MinecraftServer server, UUID encounterId) {
		WorldInterfaceAttackService.cancelAndRestore(server, encounterId);
		return clearCurrentAttack(server, encounterId);
	}

	private static WorldInterfaceState.Snapshot clearCurrentAttack(MinecraftServer server, UUID encounterId) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (snapshot.currentAttack().isEmpty()) return snapshot;
		return requireApplied(WorldInterfaceState.mutate(server, encounterId, snapshot.revision(),
				state -> state.clearCurrentAttack()), "clear attack fixture");
	}

	private static int countInventoryItem(ServerPlayer player, Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) count += stack.getCount();
		}
		return count;
	}

	private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}

	private static void exerciseEnding(net.minecraft.server.MinecraftServer server,
			WorldInterfaceState.ArenaLayout layout, WorldInterfaceStage resolutionStage,
			WorldInterfaceState.Outcome expectedOutcome, GameTestHelper helper) {
		UUID encounterId = UUID.randomUUID();
		UUID participantId = UUID.randomUUID();
		WorldInterfaceState.Snapshot snapshot = requireApplied(
				WorldInterfaceState.initialize(server, encounterId, layout, 0x574F524C444CL), "initialize");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.ARENA_READY, WorldInterfaceStage.WAITING_TERMINALS), "wait for terminals");

		String terminalId = UUID.randomUUID().toString();
		CompoundTag terminal = new CompoundTag();
		terminal.putString(TerminalData.OWNER_ID, participantId.toString());
		terminal.putString(TerminalData.TERMINAL_ID, terminalId);
		terminal.putInt(TerminalData.COPY_GENERATION, 0);
		WorldInterfaceState.TerminalTransaction transaction = new WorldInterfaceState.TerminalTransaction(
				participantId, terminalId, 0, WorldInterfaceState.TerminalTransactionState.REMOVED, 1L, terminal);
		snapshot = requireApplied(WorldInterfaceState.mutate(server, encounterId, snapshot.revision(), state -> {
			state.freezeRoster(Set.of(participantId));
			state.putTerminalTransaction(transaction);
			state.commitSacrifice(600.0D);
		}), "commit terminal sacrifice");
		helper.assertTrue(snapshot.sacrificeCommitted() && snapshot.frozenRoster().equals(Set.of(participantId)),
				"The roster must freeze atomically with the committed terminal transaction");
		helper.assertTrue(Math.abs(snapshot.maxVirtualHealth() - 600.0D) < 0.000_001D,
				"One frozen participant must create exactly 600 virtual health");
		helper.assertTrue(snapshot.terminalTransactions().get(participantId).state()
				== WorldInterfaceState.TerminalTransactionState.COMMITTED,
				"The removed terminal journal must become committed before combat");

		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.SUMMONING, WorldInterfaceStage.PHASE_1), "enter phase one");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_1, WorldInterfaceStage.PHASE_2), "enter phase two");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_2, WorldInterfaceStage.PHASE_3), "enter phase three");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PHASE_3, resolutionStage), "lock ending");
		helper.assertTrue(snapshot.outcome() == expectedOutcome,
				"The resolution stage must lock its matching ending outcome");

		WorldInterfaceState.Outcome opposite = expectedOutcome == WorldInterfaceState.Outcome.SUCCESS
				? WorldInterfaceState.Outcome.FAILURE : WorldInterfaceState.Outcome.SUCCESS;
		WorldInterfaceState.MutationResult rewrite = WorldInterfaceState.mutate(server, encounterId,
				snapshot.revision(), state -> state.setOutcome(opposite));
		helper.assertFalse(rewrite.applied(), "A locked ending outcome must never be rewritten");
		helper.assertTrue(rewrite.snapshot().outcome() == expectedOutcome,
				"A rejected rewrite must leave the persisted outcome unchanged");

		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				resolutionStage, WorldInterfaceStage.PORTAL_OPEN), "open exit portal");
		snapshot = requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.PORTAL_OPEN, WorldInterfaceStage.COMPLETE), "complete encounter");
		helper.assertTrue(snapshot.stage() == WorldInterfaceStage.COMPLETE
				&& snapshot.outcome() == expectedOutcome,
				"Both ending branches must converge on complete without losing their identity");
	}

	private static WorldInterfaceState.Snapshot initializeWaiting(MinecraftServer server, UUID encounterId,
			WorldInterfaceState.ArenaLayout layout) {
		WorldInterfaceState.Snapshot snapshot = requireApplied(
				WorldInterfaceState.initialize(server, encounterId, layout, 0x574F524C444CL), "initialize");
		return requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.ARENA_READY, WorldInterfaceStage.WAITING_TERMINALS), "wait for terminals");
	}

	private static WorldInterfaceState.Snapshot committedCombat(MinecraftServer server, UUID encounterId,
			WorldInterfaceState.ArenaLayout layout) {
		WorldInterfaceState.Snapshot snapshot = initializeWaiting(server, encounterId, layout);
		UUID frozenPlayer = UUID.randomUUID();
		WorldInterfaceState.TerminalTransaction transaction = terminalTransaction(frozenPlayer,
				WorldInterfaceState.TerminalTransactionState.REMOVED);
		snapshot = requireApplied(WorldInterfaceState.mutate(server, encounterId, snapshot.revision(), state -> {
			state.freezeRoster(Set.of(frozenPlayer));
			state.putTerminalTransaction(transaction);
			state.commitSacrifice(600.0D);
		}), "commit combat fixture");
		return requireApplied(WorldInterfaceState.transition(server, encounterId, snapshot.revision(),
				WorldInterfaceStage.SUMMONING, WorldInterfaceStage.PHASE_1), "enter combat fixture");
	}

	private static WorldInterfaceState.TerminalTransaction terminalTransaction(UUID playerId,
			WorldInterfaceState.TerminalTransactionState state) {
		String terminalId = UUID.randomUUID().toString();
		CompoundTag terminal = new CompoundTag();
		terminal.putString(TerminalData.OWNER_ID, playerId.toString());
		terminal.putString(TerminalData.TERMINAL_ID, terminalId);
		terminal.putInt(TerminalData.COPY_GENERATION, 0);
		return new WorldInterfaceState.TerminalTransaction(playerId, terminalId, 0, state, 1L, terminal);
	}

	private static ServerPlayer preparedMockParticipant(GameTestHelper helper, ServerLevel end, BlockPos altar,
			FrequencyWorldData data) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
			throw new AssertionError("Mock participant could not enter survival mode");
		}
		data.updateTerminalRecord(player.getUUID(), record -> record.putBoolean(TerminalData.BOUND, true));
		player.getInventory().clearContent();
		player.getInventory().setItem(0, TerminalData.stackFromRecord(
				data.terminalRecord(player.getUUID()).orElseThrow()));
		player.getInventory().setChanged();
		if (!hasValidBoundTerminal(player, data)) {
			throw new AssertionError("Prepared participant does not carry its valid bound terminal");
		}
		if (!player.teleportTo(end, altar.getX() + 0.5D, altar.getY() + 1.0D, altar.getZ() + 0.5D,
				Set.of(), 0.0F, 0.0F, true)) throw new AssertionError("Mock participant could not enter the End altar");
		return player;
	}

	private static Map<UUID, GameType> spectateExistingPlayers(MinecraftServer server) {
		Map<UUID, GameType> original = new java.util.LinkedHashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			original.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
			player.setGameMode(GameType.SPECTATOR);
			if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
				throw new AssertionError("Unable to isolate the ritual roster from another GameTest player");
			}
		}
		return Map.copyOf(original);
	}

	private static void restoreGameModes(MinecraftServer server, Map<UUID, GameType> original) {
		for (Map.Entry<UUID, GameType> entry : original.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) player.setGameMode(entry.getValue());
		}
	}

	private static String ritualDiagnosis(MinecraftServer server, FrequencyWorldData data, ServerPlayer actor,
			long requestedRevision, WorldInterfaceRitualService.RitualResult result) {
		WorldInterfaceState.Snapshot live = WorldInterfaceState.snapshot(server);
		String eligible = server.getPlayerList().getPlayers().stream()
				.filter(player -> player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
				.sorted(java.util.Comparator.comparing(player -> player.getUUID().toString()))
				.map(player -> player.getUUID() + "@" + player.level().dimension().identifier()
						+ ":bound=" + hasValidBoundTerminal(player, data))
				.toList().toString();
		String transactions = live.terminalTransactions().entrySet().stream()
				.sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(UUID::toString)))
				.map(entry -> entry.getKey() + "=" + entry.getValue().state())
				.toList().toString();
		return "reason=" + result.reason() + ", applied=" + result.applied()
				+ ", actor=" + actor.getUUID() + ", actorBound=" + hasValidBoundTerminal(actor, data)
				+ ", requestedRevision=" + requestedRevision + ", resultRevision=" + result.snapshot().revision()
				+ ", liveRevision=" + live.revision() + ", stage=" + live.stage()
				+ ", frozenRoster=" + live.frozenRoster() + ", transactions=" + transactions
				+ ", eligible=" + eligible;
	}

	private static boolean hasValidBoundTerminal(ServerPlayer player, FrequencyWorldData data) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			var stack = player.getInventory().getItem(slot);
			if (data.isValidTerminal(stack, player.getUUID()) && TerminalData.isBound(stack)) return true;
		}
		return false;
	}

	private static WorldInterfaceState.Snapshot requireApplied(WorldInterfaceState.MutationResult result,
			String operation) {
		if (!result.applied()) {
			throw new AssertionError("World-interface state operation failed (" + operation + "): " + result.reason());
		}
		return result.snapshot();
	}

	private static WorldInterfaceState.ArenaLayout stateLayout(EndBossArenaService.PreparedArena arena) {
		List<WorldInterfaceState.Gate> gates = new ArrayList<>(20);
		for (int index = 0; index < arena.gatewayCorePositions().size(); index++) {
			gates.add(new WorldInterfaceState.Gate(index, arena.gatewayCorePositions().get(index),
					WorldInterfaceGatewayState.DORMANT));
		}
		List<WorldInterfaceState.Anchor> anchors = arena.anchors().stream()
				.map(anchor -> new WorldInterfaceState.Anchor(anchor.index(), anchor.position(),
						Optional.of(anchor.crystalUuid()), false)).toList();
		return new WorldInterfaceState.ArenaLayout(1, "minecraft:the_end", arena.center(), arena.altar(),
				arena.safeSpawn(), gates, anchors);
	}

	private static List<BlockPos> placeEditableLine(ServerLevel level, BlockPos base, int count) {
		List<BlockPos> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			BlockPos position = base.offset(index, 0, 0);
			level.setBlockAndUpdate(position, Blocks.END_STONE.defaultBlockState());
			result.add(position);
		}
		return List.copyOf(result);
	}

	private static ServerLevel requireEnd(GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		if (end == null) throw new AssertionError("The End dimension is unavailable to the GameTest");
		return end;
	}

	private static void clearWorldInterface(FrequencyWorldData data) {
		data.updateNarrativeState(root -> root.remove(WorldInterfaceState.ROOT_KEY));
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
