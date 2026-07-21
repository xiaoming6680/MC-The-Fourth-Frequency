package com.xm.thefourthfrequency.state;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;

/** Typed snapshot of the observations used to shape and animate the final body. */
public record PlayerPatternState(
		int mined,
		int placed,
		int crafted,
		int deviceInteractions,
		String preferredWeapon,
		int weaponSamples,
		String escapeAxis,
		long lastPosition,
		int foodUsePhase,
		String armorSignature,
		int armorChanges,
		String multiplayerRole,
		String acceptedAdvice
) {
	public PlayerPatternState {
		mined = Math.max(0, mined);
		placed = Math.max(0, placed);
		crafted = Math.max(0, crafted);
		deviceInteractions = Math.max(0, deviceInteractions);
		preferredWeapon = safe(preferredWeapon, "");
		weaponSamples = Math.clamp(weaponSamples, 0, 10_000);
		escapeAxis = safe(escapeAxis, "none");
		foodUsePhase = Math.clamp(foodUsePhase, -1, 99);
		armorSignature = safe(armorSignature, "");
		armorChanges = Math.max(0, armorChanges);
		multiplayerRole = safe(multiplayerRole, "unresolved");
		acceptedAdvice = safe(acceptedAdvice, "");
	}

	public static PlayerPatternState read(CompoundTag tag) {
		return new PlayerPatternState(
				tag.getIntOr(TerminalData.MINED_BLOCKS, 0), tag.getIntOr(TerminalData.PLACED_BLOCKS, 0),
				tag.getIntOr(TerminalData.CRAFTED_ITEMS, 0), tag.getIntOr(TerminalData.DEVICE_INTERACTIONS, 0),
				tag.getStringOr(TerminalData.PREFERRED_WEAPON, ""), tag.getIntOr(TerminalData.WEAPON_SAMPLE_COUNT, 0),
				tag.getStringOr(TerminalData.ESCAPE_AXIS, "none"), tag.getLongOr(TerminalData.LAST_PATTERN_POSITION, 0L),
				tag.getIntOr(TerminalData.FOOD_USE_PHASE, -1), tag.getStringOr(TerminalData.ARMOR_SIGNATURE, ""),
				tag.getIntOr(TerminalData.ARMOR_CHANGE_COUNT, 0),
				tag.getStringOr(TerminalData.MULTIPLAYER_ROLE, "unresolved"),
				tag.getStringOr(TerminalData.ACCEPTED_ADVICE, ""));
	}

	public void writeTo(CompoundTag tag) {
		tag.putInt(TerminalData.MINED_BLOCKS, mined);
		tag.putInt(TerminalData.PLACED_BLOCKS, placed);
		tag.putInt(TerminalData.CRAFTED_ITEMS, crafted);
		tag.putInt(TerminalData.DEVICE_INTERACTIONS, deviceInteractions);
		tag.putString(TerminalData.PREFERRED_WEAPON, preferredWeapon);
		tag.putInt(TerminalData.WEAPON_SAMPLE_COUNT, weaponSamples);
		tag.putString(TerminalData.ESCAPE_AXIS, escapeAxis);
		tag.putLong(TerminalData.LAST_PATTERN_POSITION, lastPosition);
		tag.putInt(TerminalData.FOOD_USE_PHASE, foodUsePhase);
		tag.putString(TerminalData.ARMOR_SIGNATURE, armorSignature);
		tag.putInt(TerminalData.ARMOR_CHANGE_COUNT, armorChanges);
		tag.putString(TerminalData.MULTIPLAYER_ROLE, multiplayerRole);
		tag.putString(TerminalData.ACCEPTED_ADVICE, acceptedAdvice);
	}

	public String inferredRole() {
		int devices = deviceInteractions + crafted;
		if (placed >= mined && placed >= devices) return "builder";
		if (mined >= devices) return "miner";
		return "operator";
	}

	public PlayerPatternState withAcceptedAdvice(String value) {
		return new PlayerPatternState(mined, placed, crafted, deviceInteractions, preferredWeapon, weaponSamples,
				escapeAxis, lastPosition, foodUsePhase, armorSignature, armorChanges, multiplayerRole, value);
	}

	private static String safe(String value, String fallback) {
		return value == null ? fallback : value;
	}
}
