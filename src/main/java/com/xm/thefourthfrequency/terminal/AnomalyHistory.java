package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.pursuit.PursuitProgressPolicy;
import com.xm.thefourthfrequency.pursuit.PursuitTutorialPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Updates the small personal history used for anti-repetition, stage pacing and pursuit tutorials. */
public final class AnomalyHistory {
	public static final int RECENT_LIMIT = 3;

	private AnomalyHistory() {
	}

	public static void recordSuccess(CompoundTag record, String anomalyId) {
		int index = AnomalyCatalog.indexOf(anomalyId);
		if (index < 0) return;
		record.putLong(TerminalData.ANOMALY_SEEN_MASK,
				record.getLongOr(TerminalData.ANOMALY_SEEN_MASK, 0L) | 1L << index);
		record.putInt(TerminalData.ANOMALY_STAGE_SUCCESSES,
				Math.max(0, record.getIntOr(TerminalData.ANOMALY_STAGE_SUCCESSES, 0)) + 1);

		ListTag previous = record.getListOrEmpty(TerminalData.ANOMALY_RECENT_IDS);
		ListTag recent = new ListTag();
		recent.add(StringTag.valueOf(anomalyId));
		for (int oldIndex = 0; oldIndex < previous.size() && recent.size() < RECENT_LIMIT; oldIndex++) {
			String previousId = previous.getString(oldIndex).orElse("");
			if (!previousId.equals(anomalyId) && AnomalyCatalog.contains(previousId)) {
				recent.add(StringTag.valueOf(previousId));
			}
		}
		record.put(TerminalData.ANOMALY_RECENT_IDS, recent);

		int form = PursuitProgressPolicy.actualForm(
				record.getIntOr(TerminalData.PURSUIT_RESOLVED_CHASES, 0));
		record.putInt(TerminalData.PURSUIT_TUTORIAL_DEMO_MASK, PursuitTutorialPolicy.mark(
				record.getIntOr(TerminalData.PURSUIT_TUTORIAL_DEMO_MASK, 0), form));
	}
}
