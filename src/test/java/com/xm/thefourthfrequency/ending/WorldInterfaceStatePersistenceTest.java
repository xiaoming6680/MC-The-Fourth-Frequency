package com.xm.thefourthfrequency.ending;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldInterfaceStatePersistenceTest {
	private static final UUID ENCOUNTER_ID = named("encounter");
	private static final UUID FIRST_PLAYER = named("player-one");
	private static final UUID SECOND_PLAYER = named("player-two");
	private static final UUID FRIENDLY_DRAGON_ID = named("friendly-dragon");

	@Test
	void resolvedStateRoundTripsEveryDurableLedgerWithoutLoss() {
		WorldInterfaceState.Snapshot before = resolvedSnapshot(WorldInterfaceStage.PORTAL_OPEN, Optional.empty());
		CompoundTag encoded = encode(before);
		WorldInterfaceState.Snapshot after = decode(encoded);

		assertEquals(before, after);
		assertEquals(WorldInterfaceState.FORMAT_VERSION, encoded.getIntOr("format_version", -1));
		assertEquals(Optional.of(FRIENDLY_DRAGON_ID), after.friendlyDragonUuid());
		assertTrue(after.poemLedger().values().stream().allMatch(WorldInterfaceState.PoemLedgerEntry::acked));
		assertTrue(after.respawnLedger().values().stream().allMatch(WorldInterfaceState.RespawnLedgerEntry::restored));
	}

	@Test
	void oldVersionAndCorruptedKnownVersionAreRejectedAsWholePayloads() {
		CompoundTag encoded = encode(resolvedSnapshot(WorldInterfaceStage.PORTAL_OPEN, Optional.empty()));
		CompoundTag oldVersion = encoded.copy();
		oldVersion.putInt("format_version", WorldInterfaceState.FORMAT_VERSION - 1);
		assertEquals("unsupported_version",
				assertThrows(IllegalArgumentException.class, () -> decode(oldVersion)).getMessage());

		CompoundTag corrupted = encoded.copy();
		corrupted.putInt("anchor_penalty_ticks", 0);
		assertEquals("anchor_penalty_mismatch",
				assertThrows(IllegalArgumentException.class, () -> decode(corrupted)).getMessage());
	}

	@Test
	void completeStateAllowsPersistedFriendlyDragonButForbidsBossIdentity() {
		WorldInterfaceState.Snapshot complete = resolvedSnapshot(WorldInterfaceStage.COMPLETE, Optional.empty());
		assertEquals(Optional.of(FRIENDLY_DRAGON_ID), decode(encode(complete)).friendlyDragonUuid());

		WorldInterfaceState.Snapshot contaminated = resolvedSnapshot(WorldInterfaceStage.COMPLETE,
				Optional.of(named("stale-boss")));
		assertEquals("finished_boss_identity",
				assertThrows(IllegalArgumentException.class, () -> encode(contaminated)).getMessage());
	}

	@Test
	void completionGuardRequiresPoemAckAndRestoredRespawnForEveryFrozenPlayer() throws Exception {
		String source = Files.readString(projectRoot().resolve(
				"src/main/java/com/xm/thefourthfrequency/ending/EndBossEncounterService.java"),
				StandardCharsets.UTF_8);
		int start = source.indexOf("private static void completeIfAllPoemsAcknowledged");
		int end = source.indexOf("private static void ensureExitOpen", start);
		assertTrue(start >= 0 && end > start, "The authoritative completion guard must remain identifiable");
		String guard = source.substring(start, end).replaceAll("\\s+", "");
		assertTrue(guard.contains(".map(WorldInterfaceState.PoemLedgerEntry::acked).orElse(false)"
				+ "&&Optional.ofNullable(snapshot.respawnLedger().get(id))"
				+ ".map(WorldInterfaceState.RespawnLedgerEntry::restored).orElse(false)"),
				"Completion must conjunctively require both the poem ACK and restored respawn ledger");
		assertTrue(guard.indexOf("PoemLedgerEntry::acked") < guard.indexOf("WorldInterfaceState.transition"));
		assertTrue(guard.indexOf("RespawnLedgerEntry::restored") < guard.indexOf("WorldInterfaceState.transition"));
	}

	private static WorldInterfaceState.Snapshot resolvedSnapshot(WorldInterfaceStage stage,
			Optional<UUID> bossUuid) {
		Set<UUID> roster = new LinkedHashSet<>(List.of(FIRST_PLAYER, SECOND_PLAYER));
		Map<UUID, WorldInterfaceState.TerminalTransaction> transactions = new LinkedHashMap<>();
		transactions.put(FIRST_PLAYER, transaction(FIRST_PLAYER, "terminal-one"));
		transactions.put(SECOND_PLAYER, transaction(SECOND_PLAYER, "terminal-two"));
		Map<UUID, WorldInterfaceState.RespawnLedgerEntry> respawns = Map.of(
				FIRST_PLAYER, new WorldInterfaceState.RespawnLedgerEntry(FIRST_PLAYER, true,
						"minecraft:overworld", new BlockPos(12, 72, -8), 90.0F, 0.0F, false, true),
				SECOND_PLAYER, new WorldInterfaceState.RespawnLedgerEntry(SECOND_PLAYER, false,
						"", BlockPos.ZERO, 0.0F, 0.0F, false, true));
		Map<UUID, WorldInterfaceState.PoemLedgerEntry> poems = Map.of(
				FIRST_PLAYER, new WorldInterfaceState.PoemLedgerEntry(FIRST_PLAYER, 2_147_483_648L,
						true, true, true),
				SECOND_PLAYER, new WorldInterfaceState.PoemLedgerEntry(SECOND_PLAYER, 2_147_483_649L,
						true, true, true));
		CompoundTag recoveryPayload = new CompoundTag();
		recoveryPayload.putString("weapon_kind", "sword");
		WorldInterfaceState.RecoveryEntry recovery = new WorldInterfaceState.RecoveryEntry(named("recovery"),
				FIRST_PLAYER, "weapon", recoveryPayload, true);

		return new WorldInterfaceState.Snapshot(true, true, Optional.of(ENCOUNTER_ID), 42L,
				stage, WorldInterfaceState.Outcome.SUCCESS, 1, "minecraft:the_end",
				BlockPos.ZERO, new BlockPos(0, 65, 0), new BlockPos(0, 65, 8), 20,
				gates(), anchors(), roster, transactions, true, bossUuid,
				1_200.0D, 0.0D, 4_800L, -1L, 600, 0x574F524C44494E54L,
				Optional.empty(), 4_700L, 9L, 9, 5_000L, 4_000L,
				Map.of(FIRST_PLAYER, 5_200L), 0, 321, respawns, poems, List.of(recovery),
				Optional.of(FRIENDLY_DRAGON_ID), new BlockPos(0, 65, 0), true, 3, 9_000L);
	}

	private static WorldInterfaceState.TerminalTransaction transaction(UUID playerId, String terminalId) {
		CompoundTag terminal = new CompoundTag();
		terminal.putString("owner_id", playerId.toString());
		terminal.putString("terminal_id", terminalId);
		terminal.putInt("copy_generation", 0);
		return new WorldInterfaceState.TerminalTransaction(playerId, terminalId, 0,
				WorldInterfaceState.TerminalTransactionState.COMMITTED, 100L, terminal);
	}

	private static List<WorldInterfaceState.Gate> gates() {
		List<WorldInterfaceState.Gate> result = new ArrayList<>();
		for (int index = 0; index < WorldInterfaceState.GATE_COUNT; index++) {
			result.add(new WorldInterfaceState.Gate(index, new BlockPos(index * 2, 70, 100 + index),
					WorldInterfaceGatewayState.GOLD));
		}
		return List.copyOf(result);
	}

	private static List<WorldInterfaceState.Anchor> anchors() {
		List<WorldInterfaceState.Anchor> result = new ArrayList<>();
		for (int index = 0; index < WorldInterfaceState.ANCHOR_COUNT; index++) {
			result.add(new WorldInterfaceState.Anchor(index, new BlockPos(index * 3, 80 + index, -100),
					Optional.of(named("anchor-" + index)), index == 0));
		}
		return List.copyOf(result);
	}

	private static CompoundTag encode(WorldInterfaceState.Snapshot snapshot) {
		return (CompoundTag) invoke("encode", new Class<?>[]{WorldInterfaceState.Snapshot.class}, snapshot);
	}

	private static WorldInterfaceState.Snapshot decode(CompoundTag tag) {
		return (WorldInterfaceState.Snapshot) invoke("decode", new Class<?>[]{CompoundTag.class}, tag);
	}

	private static Object invoke(String name, Class<?>[] parameterTypes, Object argument) {
		try {
			Method method = WorldInterfaceState.class.getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method.invoke(null, argument);
		} catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
			throw new AssertionError("World-interface persistence invocation failed", exception.getCause());
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("World-interface persistence method is unavailable", exception);
		}
	}

	private static UUID named(String value) {
		return UUID.nameUUIDFromBytes(("world-interface-test:" + value).getBytes(StandardCharsets.UTF_8));
	}

	private static Path projectRoot() {
		return Path.of(System.getProperty("thefourthfrequency.projectDir", System.getProperty("user.dir")))
				.toAbsolutePath().normalize();
	}
}
