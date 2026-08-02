package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldDecayResiduePolicyTest {
	@Test
	void residueClimbsOneStagePerFourAnomaliesAndStopsAtTheCeiling() {
		assertEquals(0, WorldDecayResiduePolicy.residueStage(0));
		assertEquals(0, WorldDecayResiduePolicy.residueStage(3));
		assertEquals(1, WorldDecayResiduePolicy.residueStage(4));
		assertEquals(2, WorldDecayResiduePolicy.residueStage(8));
		assertEquals(3, WorldDecayResiduePolicy.residueStage(12));
		assertEquals(4, WorldDecayResiduePolicy.residueStage(16));
		assertEquals(WorldDecayResiduePolicy.MAX_STAGE,
				WorldDecayResiduePolicy.residueStage(20));
		assertEquals(WorldDecayResiduePolicy.MAX_STAGE,
				WorldDecayResiduePolicy.residueStage(500));
		// Negative counts cannot appear from accumulate(), but a corrupt record must not sign-flip
		// into a negative stage that would then win a Math.max against a legitimate one.
		assertEquals(0, WorldDecayResiduePolicy.residueStage(-7));
	}

	@Test
	void residueOnlyEverGrows() {
		int count = 0;
		int previousStage = 0;
		for (int anomaly = 0; anomaly < 40; anomaly++) {
			count = WorldDecayResiduePolicy.accumulate(count);
			int stage = WorldDecayResiduePolicy.residueStage(count);
			assertTrue(stage >= previousStage, "decay must never recover on its own");
			previousStage = stage;
		}
		assertEquals(40, count);
		assertEquals(WorldDecayResiduePolicy.MAX_STAGE, previousStage);
	}

	@Test
	void accumulateClampsAndToleratesCorruptCounts() {
		assertEquals(1, WorldDecayResiduePolicy.accumulate(0));
		assertEquals(1, WorldDecayResiduePolicy.accumulate(-5));
		assertEquals(WorldDecayResiduePolicy.MAX_TRACKED,
				WorldDecayResiduePolicy.accumulate(WorldDecayResiduePolicy.MAX_TRACKED));
	}

	@Test
	void migrationSeedsResidueFromTheSeenMaskSoOldWorldsKeepTheirDecay() {
		// A long-running save from before this counter existed has no total to read, but each set
		// bit in the seen mask is an anomaly that provably completed. Losing that would visibly
		// reset an established world back to pristine textures on upgrade.
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(TerminalData.SCHEMA_VERSION, 9);
		legacy.putLong(TerminalData.ANOMALY_SEEN_MASK, 0b1111_1111_1111L);
		CompoundTag migrated = TerminalData.migrateRecord(legacy);
		assertEquals(12, migrated.getIntOr(TerminalData.ANOMALY_RESIDUE_COUNT, -1));
		assertEquals(3, WorldDecayResiduePolicy.residueStage(
				migrated.getIntOr(TerminalData.ANOMALY_RESIDUE_COUNT, 0)));

		CompoundTag fresh = new CompoundTag();
		fresh.putInt(TerminalData.SCHEMA_VERSION, 9);
		assertEquals(0, TerminalData.migrateRecord(fresh)
				.getIntOr(TerminalData.ANOMALY_RESIDUE_COUNT, -1));
	}
}
