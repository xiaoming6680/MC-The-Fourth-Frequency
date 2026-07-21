package com.xm.thefourthfrequency.narrative;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TerminalFileState {
	private TerminalFileState() { }

	public static boolean discover(CompoundTag record, String id, long gameTime, long dayTime, boolean unlocked) {
		NarrativeFileCatalog.require(id);
		ListTag states = record.getListOrEmpty(TerminalData.FILE_STATES).copy();
		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			if (!id.equals(state.getStringOr("id", ""))) continue;
			if (unlocked && !state.getBooleanOr("unlocked", false)) {
				state.putBoolean("unlocked", true);
				state.putLong("unlocked_game_time", Math.max(0L, gameTime));
				state.putLong("unlocked_day_time", Math.max(0L, dayTime));
				states.set(index, state);
				record.put(TerminalData.FILE_STATES, states);
				return true;
			}
			return false;
		}
		CompoundTag state = new CompoundTag();
		state.putString("id", id);
		state.putBoolean("unlocked", unlocked);
		state.putLong("discovered_game_time", Math.max(0L, gameTime));
		state.putLong("discovered_day_time", Math.max(0L, dayTime));
		state.putLong("unlocked_game_time", unlocked ? Math.max(0L, gameTime) : 0L);
		state.putLong("unlocked_day_time", unlocked ? Math.max(0L, dayTime) : 0L);
		states.add(state);
		record.put(TerminalData.FILE_STATES, states);
		return true;
	}

	public static List<State> states(CompoundTag record) {
		List<State> result = new ArrayList<>();
		ListTag states = record.getListOrEmpty(TerminalData.FILE_STATES);
		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			String id = state.getStringOr("id", "");
			try {
				NarrativeFileCatalog.require(id);
				result.add(new State(id, state.getBooleanOr("unlocked", false),
						state.getLongOr("discovered_game_time", 0L), state.getLongOr("discovered_day_time", 0L),
						state.getLongOr("unlocked_game_time", 0L), state.getLongOr("unlocked_day_time", 0L)));
			} catch (IllegalArgumentException ignored) { }
		}
		result.sort(Comparator.comparingInt(state -> order(state.id())));
		return List.copyOf(result);
	}

	public static boolean discovered(CompoundTag record, String id) {
		return states(record).stream().anyMatch(state -> state.id().equals(id));
	}

	public static boolean unlocked(CompoundTag record, String id) {
		return states(record).stream().anyMatch(state -> state.id().equals(id) && state.unlocked());
	}

	public static boolean setUnlocked(CompoundTag record, String id, boolean unlocked, long gameTime, long dayTime) {
		NarrativeFileCatalog.require(id);
		if (unlocked && !discovered(record, id)) return discover(record, id, gameTime, dayTime, true);
		ListTag states = record.getListOrEmpty(TerminalData.FILE_STATES).copy();
		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			if (!id.equals(state.getStringOr("id", ""))) continue;
			state.putBoolean("unlocked", unlocked);
			state.putLong("unlocked_game_time", unlocked ? Math.max(0L, gameTime) : 0L);
			state.putLong("unlocked_day_time", unlocked ? Math.max(0L, dayTime) : 0L);
			states.set(index, state);
			record.put(TerminalData.FILE_STATES, states);
			return true;
		}
		return false;
	}

	public static boolean remove(CompoundTag record, String id) {
		NarrativeFileCatalog.require(id);
		ListTag states = record.getListOrEmpty(TerminalData.FILE_STATES).copy();
		for (int index = 0; index < states.size(); index++) {
			CompoundTag state = states.getCompoundOrEmpty(index);
			if (!id.equals(state.getStringOr("id", ""))) continue;
			states.remove(index);
			record.put(TerminalData.FILE_STATES, states);
			return true;
		}
		return false;
	}

	private static int order(String id) {
		List<NarrativeFileCatalog.Definition> definitions = NarrativeFileCatalog.definitions();
		for (int index = 0; index < definitions.size(); index++) if (definitions.get(index).id().equals(id)) return index;
		return Integer.MAX_VALUE;
	}

	public record State(String id, boolean unlocked, long discoveredGameTime, long discoveredDayTime,
			long unlockedGameTime, long unlockedDayTime) { }
}
