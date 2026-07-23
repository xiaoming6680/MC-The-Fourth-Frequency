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
	public static final SoundEvent TERMINAL_ANOMALY = register("terminal_anomaly");
	public static final SoundEvent WORLD_INTERFACE_ALTAR = register("world_interface_altar");
	public static final SoundEvent WORLD_INTERFACE_TERMINAL = register("world_interface_terminal");
	public static final SoundEvent WORLD_INTERFACE_ANCHOR = register("world_interface_anchor");
	public static final SoundEvent WORLD_INTERFACE_GATEWAY_PURPLE = register("world_interface_gateway_purple");
	public static final SoundEvent WORLD_INTERFACE_GATEWAY_GOLD = register("world_interface_gateway_gold");
	public static final SoundEvent WORLD_INTERFACE_GATEWAY_RED = register("world_interface_gateway_red");
	public static final SoundEvent WORLD_INTERFACE_SUMMON = register("world_interface_summon");
	public static final SoundEvent WORLD_INTERFACE_AMBIENT_1 = register("world_interface_ambient_1");
	public static final SoundEvent WORLD_INTERFACE_AMBIENT_2 = register("world_interface_ambient_2");
	public static final SoundEvent WORLD_INTERFACE_AMBIENT_3 = register("world_interface_ambient_3");
	public static final SoundEvent WORLD_INTERFACE_MORPH = register("world_interface_morph");
	public static final SoundEvent WORLD_INTERFACE_LASER = register("world_interface_laser");
	public static final SoundEvent WORLD_INTERFACE_ORB = register("world_interface_orb");
	public static final SoundEvent WORLD_INTERFACE_GRAB = register("world_interface_grab");
	public static final SoundEvent WORLD_INTERFACE_MENTAL = register("world_interface_mental");
	public static final SoundEvent WORLD_INTERFACE_WEAPON = register("world_interface_weapon");
	public static final SoundEvent WORLD_INTERFACE_THROW = register("world_interface_throw");
	public static final SoundEvent WORLD_INTERFACE_HOTBAR = register("world_interface_hotbar");
	public static final SoundEvent WORLD_INTERFACE_ARROW = register("world_interface_arrow");
	public static final SoundEvent WORLD_INTERFACE_EXPULSION = register("world_interface_expulsion");
	public static final SoundEvent WORLD_INTERFACE_SUCCESS = register("world_interface_success");
	public static final SoundEvent WORLD_INTERFACE_FAILURE = register("world_interface_failure");

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
