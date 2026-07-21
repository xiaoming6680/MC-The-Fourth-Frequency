package com.xm.thefourthfrequency.audio;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent EMPTY_VIEWPOINT = register("empty_viewpoint");
	public static final SoundEvent EMPTY_BASE = register("empty_base");
	public static final SoundEvent EMPTY_EXPERIENCE = register("empty_experience");
	public static final SoundEvent FOURTH_BAND = register("fourth_band");
	public static final SoundEvent REWORK_JOINT = register("rework_joint");
	public static final SoundEvent MISREAD_BODY = register("misread_body");
	public static final SoundEvent MISREAD_ADAPTATION = register("misread_adaptation");
	public static final SoundEvent TERMINATION = register("termination");
	public static final SoundEvent ANOMALY_ECHO = register("anomaly_echo");
	public static final SoundEvent WINDOW_GLITCH = register("window_glitch");
	public static final SoundEvent DOOR_CASCADE = register("door_cascade");
	public static final SoundEvent RULE_COLLAPSE = register("rule_collapse");
	public static final SoundEvent TERMINAL_CLICK = register("terminal_click");
	public static final SoundEvent TERMINAL_TUNE = register("terminal_tune");
	public static final SoundEvent TERMINAL_LOCK = register("terminal_lock");
	public static final SoundEvent TERMINAL_FAULT = register("terminal_fault");
	public static final SoundEvent TERMINAL_PASSWORD = register("terminal_password");
	public static final SoundEvent TERMINAL_ANOMALY = register("terminal_anomaly");

	private ModSounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		TheFourthFrequency.LOGGER.info("Registered bounded narrative and terminal device sound cues");
	}
}
