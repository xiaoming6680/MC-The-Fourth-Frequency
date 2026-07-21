package com.xm.thefourthfrequency.state;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;

/** Typed scheduling boundary for the anomaly director; serialized names remain schema-v4 compatible. */
public record AnomalyState(
		int tier,
		int storyCeiling,
		long tierOnlineTicks,
		int heat,
		long nextAmbientTick,
		boolean suspended,
		String activeId,
		long activeUntil
) {
	public AnomalyState {
		tier = Math.clamp(tier, 0, 5);
		storyCeiling = Math.clamp(storyCeiling, 0, 5);
		tierOnlineTicks = Math.max(0L, tierOnlineTicks);
		heat = Math.max(0, heat);
		nextAmbientTick = Math.max(0L, nextAmbientTick);
		activeId = activeId == null || activeId.isBlank() ? "none" : activeId;
		activeUntil = Math.max(0L, activeUntil);
	}

	public static AnomalyState read(CompoundTag tag) {
		return new AnomalyState(
				tag.getIntOr(TerminalData.ANOMALY_TIER, 0),
				tag.getIntOr(TerminalData.ANOMALY_STORY_CEILING, 0),
				tag.getLongOr(TerminalData.ANOMALY_TIER_ONLINE_TICKS, 0L),
				tag.getIntOr(TerminalData.ANOMALY_HEAT, 0),
				tag.getLongOr(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, 0L),
				tag.getBooleanOr(TerminalData.ANOMALIES_SUSPENDED, false),
				tag.getStringOr(TerminalData.ACTIVE_ANOMALY_ID, "none"),
				tag.getLongOr(TerminalData.ACTIVE_ANOMALY_UNTIL, 0L));
	}

	public void writeTo(CompoundTag tag) {
		tag.putInt(TerminalData.ANOMALY_TIER, tier);
		tag.putInt(TerminalData.ANOMALY_STORY_CEILING, storyCeiling);
		tag.putLong(TerminalData.ANOMALY_TIER_ONLINE_TICKS, tierOnlineTicks);
		tag.putInt(TerminalData.ANOMALY_HEAT, heat);
		tag.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, nextAmbientTick);
		tag.putBoolean(TerminalData.ANOMALIES_SUSPENDED, suspended);
		tag.putString(TerminalData.ACTIVE_ANOMALY_ID, activeId);
		tag.putLong(TerminalData.ACTIVE_ANOMALY_UNTIL, activeUntil);
	}

	public AnomalyState scheduled(long tick) {
		return new AnomalyState(tier, storyCeiling, tierOnlineTicks, heat, tick, suspended, activeId, activeUntil);
	}

	public AnomalyState suspended(boolean value, long nextTick) {
		return new AnomalyState(tier, storyCeiling, tierOnlineTicks, heat, nextTick, value,
				value ? "none" : activeId, value ? 0L : activeUntil);
	}
}
