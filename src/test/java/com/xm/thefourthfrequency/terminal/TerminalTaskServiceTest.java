package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.persistence.PersistenceSchema;
import com.xm.thefourthfrequency.pursuit.PursuitProgressPolicy;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalTaskServiceTest {
	@BeforeAll
	static void bootstrapRegistries() {
		// Items.BREAD / Items.STONE_AXE below trigger BuiltInRegistries's <clinit>, which asserts
		// that the vanilla bootstrap has run, and that in turn needs the game version detected
		// (DataFixers reads it). Outside a running game or GameTest environment nothing does this
		// for us, so the plain unit-test JVM needs it done explicitly once.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void fourthTabVisitLatchesTheWalkthroughClosed() {
		CompoundTag record = new CompoundTag();
		TerminalTaskService.latchOnboarding(record, 0b0111);
		assertFalse(record.getBooleanOr(TerminalData.ONBOARDING_DONE, false),
				"three tabs is not the end of the walkthrough");

		TerminalTaskService.latchOnboarding(record, TerminalTaskService.ALL_PAGES_MASK);
		assertTrue(record.getBooleanOr(TerminalData.ONBOARDING_DONE, false));

		// One-way: a mask that somehow narrows again must not reopen it. The walkthrough is a thing
		// that happened to this player, not a view of the current task progress.
		TerminalTaskService.latchOnboarding(record, 0b0001);
		assertTrue(record.getBooleanOr(TerminalData.ONBOARDING_DONE, false));
	}

	@Test
	void savesThatAlreadyUsedTheTerminalDoNotReplayTheWalkthrough() {
		// migrateRecord copies rather than mutating, so each case reads the value it returns.
		CompoundTag untouched = TerminalData.migrateRecord(new CompoundTag());
		assertFalse(untouched.getBooleanOr(TerminalData.ONBOARDING_DONE, true),
				"a record with no history at all still owes the walkthrough");

		CompoundTag visitedSource = new CompoundTag();
		visitedSource.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, 0b0011);
		assertTrue(TerminalData.migrateRecord(visitedSource)
						.getBooleanOr(TerminalData.ONBOARDING_DONE, false),
				"a player who already opened tabs must not be walked through them again");

		CompoundTag claimedSource = new CompoundTag();
		claimedSource.putInt(TerminalData.TASK_REWARD_CLAIMED_MASK, 0b1);
		assertTrue(TerminalData.migrateRecord(claimedSource)
						.getBooleanOr(TerminalData.ONBOARDING_DONE, false),
				"a player who already earned a task reward is well past the walkthrough");
	}

	@Test
	void firstTaskRequiresAllFourExplicitTabVisits() {
		CompoundTag record = new CompoundTag();
		assertEquals("learn_terminal", TerminalTaskService.current(record).id());
		assertEquals(0, TerminalTaskService.current(record).progress());
		assertFalse(TerminalTaskService.current(record).claimable());

		record.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, 0b0111);
		assertEquals(3, TerminalTaskService.current(record).progress());
		assertFalse(TerminalTaskService.current(record).claimable());

		record.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, TerminalTaskService.ALL_PAGES_MASK);
		assertEquals(4, TerminalTaskService.current(record).progress());
		assertTrue(TerminalTaskService.current(record).claimable());
		assertTrue(TerminalTaskService.hasClaimableReward(record));
		assertTrue(TerminalTaskService.rewardStack(0).is(Items.BREAD));
		assertEquals(6, TerminalTaskService.rewardStack(0).getCount());
	}

	@Test
	void claimingMaskAdvancesToRaisedWoodThreshold() {
		CompoundTag record = new CompoundTag();
		record.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, TerminalTaskService.ALL_PAGES_MASK);
		record.putInt(TerminalData.TASK_REWARD_CLAIMED_MASK, 1);
		record.putInt(TerminalData.WOOD_MINED_COUNT, SurvivalProgressService.REQUIRED_WOOD - 1);

		var wood = TerminalTaskService.current(record);
		assertEquals("mine_logs", wood.id());
		assertEquals(12, wood.target());
		assertFalse(wood.claimable());

		record.putInt(TerminalData.WOOD_MINED_COUNT, SurvivalProgressService.REQUIRED_WOOD);
		assertTrue(TerminalTaskService.current(record).claimable());
		assertTrue(TerminalTaskService.rewardStack(1).is(Items.STONE_AXE));
	}

	@Test
	void quantitativeTargetsUseTheRaisedCompletionRequirements() {
		assertEquals(12, SurvivalProgressService.REQUIRED_WOOD);
		assertEquals(6, SurvivalProgressService.REQUIRED_IRON);
		assertEquals(8, SurvivalProgressService.REQUIRED_BLAZE_RODS);
		assertEquals(3, SurvivalProgressService.REQUIRED_STRONGHOLD_UNLOCK_EYES);
		assertEquals(4, SurvivalProgressService.REQUIRED_CRAFTED_EYES);
		assertEquals(3, SurvivalProgressService.REQUIRED_EYE_SAMPLES);
		assertEquals(12, TerminalTaskService.taskCount());
	}

	/**
	 * The claim mask stores one bit per task <em>index</em>, so inserting {@code find_fortress} in the
	 * middle re-points every bit above it. A save left mid-Nether has to come forward pointing at the
	 * same tasks it went in pointing at, or the terminal pays rewards out a second time.
	 */
	@Test
	void insertingTheFortressTaskRepointsClaimedBitsInsteadOfShiftingThem() {
		int fortress = 1 << TerminalTaskService.FORTRESS_TASK_INDEX;

		// Nothing claimed stays nothing claimed, and the new task is genuinely outstanding.
		assertEquals(0, TerminalTaskService.migrateMaskForFortressInsert(0));

		// Claimed through enter_nether (indices 0-3): untouched, and the fortress is what comes next.
		assertEquals(0b1111, TerminalTaskService.migrateMaskForFortressInsert(0b1111));
		assertEquals(0, TerminalTaskService.migrateMaskForFortressInsert(0b1111) & fortress,
				"a player who has only just arrived in the Nether has not been in a fortress");

		// Claimed through collect_blaze_rods (old index 4): the rods move up to 5, and the fortress
		// bit is filled in behind them, because blazes only spawn inside one.
		int throughRods = TerminalTaskService.migrateMaskForFortressInsert(0b11111);
		assertEquals(0b111111, throughRods);

		// Every later task keeps its identity: old defeat_boss at 10 becomes 11, and nothing is lost.
		int everything = (1 << 11) - 1;
		assertEquals((1 << 12) - 1, TerminalTaskService.migrateMaskForFortressInsert(everything),
				"a finished save must still read as finished");
	}

	/** The same shift, applied to a real record by the migrator that ships it. */
	@Test
	void aMidNetherSaveIsNotAskedToCollectTheBlazeRodsTwice() {
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(TerminalData.SCHEMA_VERSION, 10);
		// Claimed through the old collect_blaze_rods, which lived at index 4.
		legacy.putInt(TerminalData.TASK_REWARD_CLAIMED_MASK, 0b11111);
		legacy.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
				SurvivalMilestone.ENTERED_NETHER.mask() | SurvivalMilestone.COLLECTED_BLAZE_RODS.mask());

		CompoundTag migrated = TerminalData.migrateRecord(legacy);

		assertEquals(0b111111, migrated.getIntOr(TerminalData.TASK_REWARD_CLAIMED_MASK, 0));
		assertTrue(SurvivalMilestone.FOUND_FORTRESS.present(
						migrated.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)),
				"rods in hand are proof of a fortress visited");
		// The next thing asked of them is what it was before the insert: come back from the Nether.
		assertEquals("return_from_nether", TerminalTaskService.current(migrated).id());
	}

	@Test
	void legacyRecordMigrationAddsTaskStateAndPreservesCompletedSamples() {
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(TerminalData.SCHEMA_VERSION, 7);
		legacy.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
				SurvivalMilestone.IRON.mask() | SurvivalMilestone.CRAFTED_EYE.mask());

		CompoundTag migrated = TerminalData.migrateRecord(legacy);
		assertEquals(PersistenceSchema.CURRENT_VERSION,
				migrated.getIntOr(TerminalData.SCHEMA_VERSION, 0));
		assertEquals(SurvivalProgressService.REQUIRED_IRON,
				migrated.getIntOr(TerminalData.IRON_SAMPLE_COUNT, 0));
		assertEquals(SurvivalProgressService.REQUIRED_CRAFTED_EYES,
				migrated.getIntOr(TerminalData.CRAFTED_EYE_COUNT, 0));
		assertEquals(0, migrated.getIntOr(TerminalData.TERMINAL_PAGE_VISIT_MASK, -1));
		assertEquals(0, migrated.getIntOr(TerminalData.TASK_REWARD_CLAIMED_MASK, -1));
		assertEquals(0, migrated.getIntOr(TerminalData.TASK_COMPLETION_NOTIFIED_MASK, -1));
		assertFalse(migrated.getBooleanOr(TerminalData.UNREAD_ALERT_ACTIVE, true));
	}

	@Test
	void migrationSeedsEncounteredChasesFromResolvedSoOldSavesKeepFinalEyeAccess() {
		// Saves written before the encountered counter existed only tracked successes. A player who
		// had already escaped a chase must not be sent back behind the final-Eye gate by the
		// upgrade, so the new counter starts at least at the old resolved count.
		CompoundTag escaped = new CompoundTag();
		escaped.putInt(TerminalData.SCHEMA_VERSION, 9);
		escaped.putInt(TerminalData.PURSUIT_RESOLVED_CHASES, 2);
		CompoundTag migratedEscaped = TerminalData.migrateRecord(escaped);
		assertEquals(2, migratedEscaped.getIntOr(TerminalData.PURSUIT_ENCOUNTERED_CHASES, -1));
		assertTrue(PursuitProgressPolicy.finalEyeReady(
				migratedEscaped.getIntOr(TerminalData.PURSUIT_ENCOUNTERED_CHASES, 0)));

		CompoundTag untouched = new CompoundTag();
		untouched.putInt(TerminalData.SCHEMA_VERSION, 9);
		CompoundTag migratedUntouched = TerminalData.migrateRecord(untouched);
		assertEquals(0, migratedUntouched.getIntOr(TerminalData.PURSUIT_ENCOUNTERED_CHASES, -1));
		assertFalse(PursuitProgressPolicy.finalEyeReady(
				migratedUntouched.getIntOr(TerminalData.PURSUIT_ENCOUNTERED_CHASES, 0)));
	}
}
