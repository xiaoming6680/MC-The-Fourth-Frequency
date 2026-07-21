package com.xm.thefourthfrequency.correction;

import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class CorrectionState {
	private static final String ROOT = "correction_organs";
	private static final String ACTIVE = "active";
	private static final String ORGAN_POS = "nascent_organ_pos";
	private static final String ORGAN_DISMANTLED = "nascent_organ_dismantled";
	private static final String ANOMALY_TRACES = "anomaly_traces";
	private static final String TERMINAL_FACILITY_POS = "terminal_facility_pos";
	private static final String DISMANTLE_COUNT = "dismantle_count";
	private static final int MAX_ANOMALY_TRACES = 64;

	private CorrectionState() {
	}

	public static CompoundTag get(FrequencyWorldData data) {
		return data.narrativeState().getCompoundOrEmpty(ROOT).copy();
	}

	public static void update(FrequencyWorldData data, Consumer<CompoundTag> update) {
		data.updateNarrativeState(narrative -> {
			CompoundTag correction = narrative.getCompoundOrEmpty(ROOT).copy();
			update.accept(correction);
			narrative.put(ROOT, correction);
		});
	}

	public static boolean active(FrequencyWorldData data) {
		return get(data).getBooleanOr(ACTIVE, false);
	}

	public static void activate(FrequencyWorldData data) {
		if (active(data)) {
			return;
		}
		update(data, state -> state.putBoolean(ACTIVE, true));
	}

	public static Optional<BlockPos> organPosition(FrequencyWorldData data) {
		CompoundTag state = get(data);
		return state.contains(ORGAN_POS) ? Optional.of(BlockPos.of(state.getLongOr(ORGAN_POS, 0L))) : Optional.empty();
	}

	public static void setOrganPosition(FrequencyWorldData data, BlockPos position) {
		setOrganPosition(data, position, null);
	}

	public static void setOrganPosition(FrequencyWorldData data, BlockPos position, UUID owner) {
		update(data, state -> {
			state.putBoolean(ACTIVE, true);
			state.putLong(ORGAN_POS, position.asLong());
			state.putBoolean(ORGAN_DISMANTLED, false);
			ListTag traces = state.getListOrEmpty(ANOMALY_TRACES).copy();
			for (int index = 0; index < traces.size(); index++) {
				CompoundTag trace = traces.getCompoundOrEmpty(index);
				if (trace.getLongOr("position", Long.MIN_VALUE) != position.asLong()) continue;
				if (owner != null) trace.putString("owner", owner.toString());
				traces.set(index, trace);
				state.put(ANOMALY_TRACES, traces);
				return;
			}
			CompoundTag trace = new CompoundTag();
			trace.putLong("position", position.asLong());
			if (owner != null) trace.putString("owner", owner.toString());
			traces.add(trace);
			while (traces.size() > MAX_ANOMALY_TRACES) traces.remove(0);
			state.put(ANOMALY_TRACES, traces);
		});
	}

	public static List<BlockPos> anomalyTracePositions(FrequencyWorldData data) {
		CompoundTag state = get(data);
		List<BlockPos> positions = new ArrayList<>();
		ListTag traces = state.getListOrEmpty(ANOMALY_TRACES);
		for (int index = 0; index < traces.size(); index++) {
			CompoundTag trace = traces.getCompoundOrEmpty(index);
			if (!trace.contains("position")) continue;
			BlockPos position = BlockPos.of(trace.getLongOr("position", 0L));
			if (!positions.contains(position)) positions.add(position);
		}
		if (positions.isEmpty() && state.contains(ORGAN_POS)
				&& !state.getBooleanOr(ORGAN_DISMANTLED, false)) {
			positions.add(BlockPos.of(state.getLongOr(ORGAN_POS, 0L)));
		}
		return List.copyOf(positions);
	}

	public static Optional<UUID> anomalyTraceOwner(FrequencyWorldData data, BlockPos position) {
		ListTag traces = get(data).getListOrEmpty(ANOMALY_TRACES);
		for (int index = 0; index < traces.size(); index++) {
			CompoundTag trace = traces.getCompoundOrEmpty(index);
			if (trace.getLongOr("position", Long.MIN_VALUE) != position.asLong()) continue;
			try {
				String owner = trace.getStringOr("owner", "");
				return owner.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(owner));
			} catch (IllegalArgumentException ignored) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	public static boolean organDismantled(FrequencyWorldData data) {
		return get(data).getBooleanOr(ORGAN_DISMANTLED, false);
	}

	public static Optional<BlockPos> terminalFacilityPosition(FrequencyWorldData data) {
		CompoundTag state = get(data);
		return state.contains(TERMINAL_FACILITY_POS)
				? Optional.of(BlockPos.of(state.getLongOr(TERMINAL_FACILITY_POS, 0L))) : Optional.empty();
	}

	public static void setTerminalFacilityPosition(FrequencyWorldData data, BlockPos position) {
		update(data, state -> state.putLong(TERMINAL_FACILITY_POS, position.asLong()));
	}

	public static void recordDismantle(FrequencyWorldData data, CorrectionTarget.Kind kind, BlockPos position) {
		update(data, state -> {
			int current = Math.max(0, state.getIntOr(DISMANTLE_COUNT, 0));
			state.putInt(DISMANTLE_COUNT, current == Integer.MAX_VALUE ? current : current + 1);
			state.putString("last_dismantled_target", kind.name().toLowerCase());
			if (kind == CorrectionTarget.Kind.ANOMALY_TRACE) {
				ListTag traces = state.getListOrEmpty(ANOMALY_TRACES).copy();
				for (int index = traces.size() - 1; index >= 0; index--) {
					if (traces.getCompoundOrEmpty(index).getLongOr("position", Long.MIN_VALUE) == position.asLong())
						traces.remove(index);
				}
				state.put(ANOMALY_TRACES, traces);
				if (state.getLongOr(ORGAN_POS, Long.MIN_VALUE) == position.asLong())
					state.putBoolean(ORGAN_DISMANTLED, true);
			}
		});
	}

	public static int dismantleCount(FrequencyWorldData data) {
		return Math.max(0, get(data).getIntOr(DISMANTLE_COUNT, 0));
	}

	public static void recordBudget(FrequencyWorldData data, int work) {
		CompoundTag current = get(data);
		int maximum = Math.max(work, current.getIntOr("maximum_tick_work", 0));
		if (current.getIntOr("last_tick_work", -1) == work
				&& current.getIntOr("maximum_tick_work", 0) == maximum) {
			return;
		}
		update(data, state -> {
			state.putInt("last_tick_work", work);
			state.putInt("maximum_tick_work", maximum);
		});
	}
}
