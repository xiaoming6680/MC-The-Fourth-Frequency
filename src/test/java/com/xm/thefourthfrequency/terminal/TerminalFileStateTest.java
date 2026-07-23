package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.narrative.NarrativeFileCatalog;
import com.xm.thefourthfrequency.narrative.HiddenFilePolicy;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalFileStateTest {
	@Test
	void catalogHasTheTwelveFixedFilesInStoryOrder() {
		assertEquals(12, NarrativeFileCatalog.definitions().size());
		assertEquals("maintenance_handoff", NarrativeFileCatalog.definitions().getFirst().id());
		assertEquals("encrypted_witness_file", NarrativeFileCatalog.definitions().get(6).id());
		assertEquals("permanent_aftermath_record", NarrativeFileCatalog.definitions().getLast().id());
	}

	@Test
	void hiddenFileReadIsIdempotentAndKeepsItsFirstReadTime() {
		CompoundTag record = emptyRecord();
		String id = HiddenFilePolicy.fileId(0);
		assertTrue(TerminalFileState.discover(record, id, 10L, 20L, true));
		assertTrue(TerminalFileState.markRead(record, id, 30L, 40L));
		assertFalse(TerminalFileState.markRead(record, id, 50L, 60L));
		TerminalFileState.State state = TerminalFileState.states(record).getFirst();
		assertTrue(state.read());
		assertEquals(30L, state.readGameTime());
		assertEquals(40L, state.readDayTime());
	}

	@Test
	void discoveryControlsTitleStageWhileReadsControlPercentage() {
		CompoundTag record = emptyRecord();
		assertEquals(0, HiddenFilePolicy.titleStage(record));
		assertEquals(0, HiddenFilePolicy.readPercent(record));
		for (int index = 0; index < HiddenFilePolicy.FILE_COUNT; index++) {
			String id = HiddenFilePolicy.fileId(index);
			TerminalFileState.discover(record, id, 10L + index, 20L + index, true);
			assertEquals(index + 1, HiddenFilePolicy.titleStage(record));
			assertEquals(index * 25, HiddenFilePolicy.readPercent(record));
			TerminalFileState.markRead(record, id, 30L + index, 40L + index);
			assertEquals((index + 1) * 25, HiddenFilePolicy.readPercent(record));
		}
		assertTrue(HiddenFilePolicy.allDiscovered(record));
		assertTrue(HiddenFilePolicy.allRead(record));
	}

	@Test
	void migrationLeavesPartialOldFilesUnreadButGrandfathersUnlockedDiaries() {
		CompoundTag partial = emptyRecord();
		TerminalFileState.discover(partial, HiddenFilePolicy.fileId(0), 10L, 20L, true);
		removeReadFields(partial);
		TerminalFileState.migrateReadState(partial, false);
		assertFalse(TerminalFileState.read(partial, HiddenFilePolicy.fileId(0)));

		CompoundTag unlocked = emptyRecord();
		TerminalFileState.discover(unlocked, HiddenFilePolicy.fileId(0), 10L, 20L, true);
		removeReadFields(unlocked);
		TerminalFileState.migrateReadState(unlocked, true);
		assertTrue(TerminalFileState.read(unlocked, HiddenFilePolicy.fileId(0)));
		assertEquals(10L, TerminalFileState.states(unlocked).getFirst().readGameTime());
	}

	private static CompoundTag emptyRecord() {
		CompoundTag record = new CompoundTag();
		record.put(TerminalData.FILE_STATES, new ListTag());
		return record;
	}

	private static void removeReadFields(CompoundTag record) {
		ListTag states = record.getListOrEmpty(TerminalData.FILE_STATES).copy();
		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			state.remove("read");
			state.remove("read_game_time");
			state.remove("read_day_time");
			states.set(index, state);
		}
		record.put(TerminalData.FILE_STATES, states);
	}
}
