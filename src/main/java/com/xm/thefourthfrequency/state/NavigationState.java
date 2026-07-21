package com.xm.thefourthfrequency.state;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Typed resource-guidance state shared by scanning and the high-frequency navigation DTO. */
public record NavigationState(
		String kind,
		String itemId,
		boolean located,
		String blockId,
		long position,
		String dimension,
		long updatedGameTime
) {
	public NavigationState {
		kind = safe(kind, "unresolved");
		itemId = safe(itemId, "");
		blockId = safe(blockId, "");
		dimension = safe(dimension, "");
		updatedGameTime = Math.max(0L, updatedGameTime);
	}

	public static NavigationState read(CompoundTag tag) {
		return new NavigationState(
				tag.getStringOr(TerminalData.TARGET_KIND, "unresolved"),
				tag.getStringOr(TerminalData.TARGET_ITEM, ""),
				tag.getBooleanOr(TerminalData.TARGET_LOCATED, false),
				tag.getStringOr(TerminalData.TARGET_BLOCK, ""),
				tag.getLongOr(TerminalData.TARGET_POSITION, 0L),
				tag.getStringOr(TerminalData.TARGET_DIMENSION, ""),
				tag.getLongOr(TerminalData.GUIDANCE_UPDATED_GAME_TIME, 0L));
	}

	public void writeTo(CompoundTag tag) {
		tag.putString(TerminalData.TARGET_KIND, kind);
		tag.putString(TerminalData.TARGET_ITEM, itemId);
		tag.putBoolean(TerminalData.TARGET_LOCATED, located);
		tag.putString(TerminalData.TARGET_BLOCK, blockId);
		tag.putLong(TerminalData.TARGET_POSITION, position);
		tag.putString(TerminalData.TARGET_DIMENSION, dimension);
		tag.putLong(TerminalData.GUIDANCE_UPDATED_GAME_TIME, updatedGameTime);
	}

	public NavigationState clearLocation() {
		return new NavigationState(kind, itemId, false, "", 0L, "", updatedGameTime);
	}

	public NavigationState select(String selectedKind, String selectedItem) {
		return new NavigationState(selectedKind, selectedItem, false, "", 0L, "", updatedGameTime);
	}

	public NavigationState locate(String foundBlock, BlockPos foundPosition, String foundDimension, long now) {
		return new NavigationState(kind, itemId, true, foundBlock, foundPosition.asLong(), foundDimension, now);
	}

	private static String safe(String value, String fallback) {
		return value == null ? fallback : value;
	}
}
