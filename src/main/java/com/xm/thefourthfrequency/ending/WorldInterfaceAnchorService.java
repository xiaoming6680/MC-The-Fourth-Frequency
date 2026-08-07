package com.xm.thefourthfrequency.ending;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Resolves the live, persisted anchor geometry shared by damage and terrain authority. */
public final class WorldInterfaceAnchorService {
	private WorldInterfaceAnchorService() {
	}

	/** Whether any surviving anchor protects this X/Z column. */
	public static boolean protects(ServerLevel level, BlockPos position) {
		if (level == null || position == null) return false;
		return protects(level, position.getX() + 0.5D, position.getZ() + 0.5D);
	}

	/** Whether any surviving anchor protects this exact horizontal point. */
	public static boolean protects(ServerLevel level, double x, double z) {
		if (level == null) return false;
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(level.getServer());
		if (!snapshot.valid() || !snapshot.present()) return false;
		for (WorldInterfaceState.Anchor anchor : snapshot.anchors()) {
			if (anchor.destroyed()) continue;
			BlockPos position = anchor.position();
			if (WorldInterfacePolicy.insideStabilityField(x, z,
					position.getX() + 0.5D, position.getZ() + 0.5D)) return true;
		}
		return false;
	}
}
