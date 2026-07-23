package com.xm.thefourthfrequency.correction;

import net.minecraft.core.BlockPos;

public record CorrectionTarget(Kind kind, BlockPos position, int priority) {
	public enum Kind {
		ANOMALY_TRACE
	}
}
