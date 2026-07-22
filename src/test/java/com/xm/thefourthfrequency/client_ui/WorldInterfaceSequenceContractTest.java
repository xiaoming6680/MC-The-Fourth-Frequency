package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldInterfaceSequenceContractTest {
	@Test
	void oneEncounterRemainsStrictlyMonotonicAcrossSignedIntMax() {
		UUID encounterId = UUID.fromString("8500f9f2-ff2b-49c0-8af8-dc016237c7c1");
		long atBoundary = Integer.MAX_VALUE;
		long afterBoundary = atBoundary + 1L;
		WorldInterfaceClientState.clearSession();
		try {
			assertTrue(WorldInterfaceClientState.accept(snapshot(encounterId, atBoundary)));
			assertTrue(WorldInterfaceClientState.accept(new BossActionS2C(encounterId, afterBoundary,
					WorldInterfaceProtocol.BossAction.LASER_SWEEP.wireId(), 0L, 40,
					List.of(), 1L, 0)));
			assertEquals(afterBoundary, WorldInterfaceClientState.snapshot().lastSequence());
			assertFalse(WorldInterfaceClientState.accept(snapshot(encounterId, atBoundary)));
			assertFalse(WorldInterfaceClientState.accept(new BossActionS2C(encounterId, afterBoundary,
					WorldInterfaceProtocol.BossAction.ENERGY_ORB.wireId(), 0L, 40,
					List.of(), 2L, 0)));
		} finally {
			WorldInterfaceClientState.clearSession();
		}
	}

	private static WorldInterfaceSnapshotS2C snapshot(UUID encounterId, long sequence) {
		return new WorldInterfaceSnapshotS2C(WorldInterfaceProtocol.VERSION, encounterId, sequence,
				WorldInterfaceProtocol.Stage.PHASE_1.wireId(),
				WorldInterfaceProtocol.Form.LISTENING_EMBRYO.wireId(), UUID.fromString(
						"de91b49b-a83f-47bc-8632-8f8dbe172254"), BlockPos.ZERO,
				600.0F, 600.0F, WorldInterfaceProtocol.ANCHOR_MASK, 0L, 0L, true, 0L,
				WorldInterfaceProtocol.GatewayState.PURPLE.wireId(), List.of(),
				WorldInterfaceProtocol.Outcome.NONE.wireId(), 0.0F);
	}
}
