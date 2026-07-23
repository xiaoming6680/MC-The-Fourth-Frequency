package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.persistence.PersistenceSchema;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalTaskServiceTest {
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
		assertEquals(12, SurvivalProgressService.REQUIRED_IRON);
		assertEquals(8, SurvivalProgressService.REQUIRED_BLAZE_RODS);
		assertEquals(4, SurvivalProgressService.REQUIRED_CRAFTED_EYES);
		assertEquals(3, SurvivalProgressService.REQUIRED_EYE_SAMPLES);
		assertEquals(11, TerminalTaskService.taskCount());
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
}
