package com.xm.thefourthfrequency.narrative;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

public final class HiddenFilePolicy {
	public static final String COMPLETE_FILE_ID = "encrypted_witness_file";
	public static final List<String> FILE_IDS = List.of(
			"surface_shelter_record",
			"field_observation_record",
			"underground_mine_record",
			"abandoned_warehouse_record");
	public static final int FILE_COUNT = FILE_IDS.size();

	private HiddenFilePolicy() {
	}

	public static boolean isHiddenFile(String id) {
		return FILE_IDS.contains(id);
	}

	public static int indexOf(String id) {
		return FILE_IDS.indexOf(id);
	}

	public static String fileId(int index) {
		return index >= 0 && index < FILE_COUNT ? FILE_IDS.get(index) : "";
	}

	public static int discoveredCount(CompoundTag record) {
		int count = 0;
		for (String id : FILE_IDS) if (TerminalFileState.discovered(record, id)) count++;
		return count;
	}

	public static int readCount(CompoundTag record) {
		int count = 0;
		for (String id : FILE_IDS) if (TerminalFileState.read(record, id)) count++;
		return count;
	}

	public static boolean allDiscovered(CompoundTag record) {
		return discoveredCount(record) == FILE_COUNT;
	}

	public static boolean allRead(CompoundTag record) {
		return readCount(record) == FILE_COUNT;
	}

	public static int readPercent(CompoundTag record) {
		return readCount(record) * 100 / FILE_COUNT;
	}

	public static int titleStage(CompoundTag record) {
		return Math.clamp(discoveredCount(record), 0, FILE_COUNT);
	}
}
