package com.xm.thefourthfrequency.ending;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldInterfaceStateSchemaTest {
	@Test
	void arenaLayoutRequiresExactlyTwentyUniqueGatesAndTenUniqueAnchors() {
		List<WorldInterfaceState.Gate> gates = gates();
		List<WorldInterfaceState.Anchor> anchors = anchors();
		Collections.reverse(gates);
		Collections.reverse(anchors);

		WorldInterfaceState.ArenaLayout layout = new WorldInterfaceState.ArenaLayout(1, "minecraft:the_end",
				BlockPos.ZERO, new BlockPos(0, 65, 0), new BlockPos(0, 65, 8), gates, anchors);
		assertEquals(0, layout.gates().getFirst().index());
		assertEquals(0, layout.anchors().getFirst().index());

		List<WorldInterfaceState.Gate> duplicate = gates();
		duplicate.set(19, new WorldInterfaceState.Gate(0, new BlockPos(99, 70, 99),
				WorldInterfaceGatewayState.DORMANT));
		assertThrows(IllegalArgumentException.class, () -> new WorldInterfaceState.ArenaLayout(1,
				"minecraft:the_end", BlockPos.ZERO, new BlockPos(0, 65, 0), new BlockPos(0, 65, 8),
				duplicate, anchors()));
	}

	@Test
	void terminalTransactionRejectsIdentityMismatchAndCopiesItsSnapshot() {
		UUID owner = UUID.randomUUID();
		CompoundTag terminal = terminal(owner, "terminal-a", 4);
		WorldInterfaceState.TerminalTransaction transaction = new WorldInterfaceState.TerminalTransaction(owner,
				"terminal-a", 4, WorldInterfaceState.TerminalTransactionState.PREPARED, 100L, terminal);

		terminal.putString("terminal_id", "mutated-after-journal");
		assertEquals("terminal-a", transaction.terminalSnapshot().getStringOr("terminal_id", ""));
		CompoundTag returned = transaction.terminalSnapshot();
		returned.putInt("copy_generation", 99);
		assertEquals(4, transaction.terminalSnapshot().getIntOr("copy_generation", -1));

		assertThrows(IllegalArgumentException.class, () -> new WorldInterfaceState.TerminalTransaction(owner,
				"terminal-b", 4, WorldInterfaceState.TerminalTransactionState.PREPARED, 100L,
				terminal(owner, "terminal-a", 4)));
	}

	@Test
	void anchorAndGateRecordsExposeOnlyTheFourBatchStates() {
		assertEquals(List.of("dormant", "purple", "gold", "red"),
				java.util.Arrays.stream(WorldInterfaceGatewayState.values())
						.map(WorldInterfaceGatewayState::serializedName).toList());
		WorldInterfaceState.Anchor alive = anchors().getFirst();
		assertFalse(alive.destroyed());
		assertEquals(Optional.of(alive.crystalUuid().orElseThrow()), alive.crystalUuid());
	}

	private static List<WorldInterfaceState.Gate> gates() {
		List<WorldInterfaceState.Gate> gates = new ArrayList<>();
		for (int index = 0; index < WorldInterfaceState.GATE_COUNT; index++) {
			gates.add(new WorldInterfaceState.Gate(index, new BlockPos(index * 2, 70, 100 + index),
					WorldInterfaceGatewayState.DORMANT));
		}
		return gates;
	}

	private static List<WorldInterfaceState.Anchor> anchors() {
		List<WorldInterfaceState.Anchor> anchors = new ArrayList<>();
		for (int index = 0; index < WorldInterfaceState.ANCHOR_COUNT; index++) {
			anchors.add(new WorldInterfaceState.Anchor(index, new BlockPos(index * 3, 80 + index, -100),
					Optional.of(UUID.nameUUIDFromBytes(("anchor-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
					false));
		}
		return anchors;
	}

	private static CompoundTag terminal(UUID owner, String terminalId, int generation) {
		CompoundTag tag = new CompoundTag();
		tag.putString("owner_id", owner.toString());
		tag.putString("terminal_id", terminalId);
		tag.putInt("copy_generation", generation);
		return tag;
	}
}
