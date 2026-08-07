package com.xm.thefourthfrequency.audio;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	/** The whole End arena hears it: summon and phase morph change the rules for everyone. */
	private static final float ARENA_RANGE = 96.0F;
	/** Wider still, because the encounter resolving is not something anyone should miss. */
	private static final float RESOLUTION_RANGE = 128.0F;
	/** Ritual structures - altar, bound terminals, stability anchors - read as places. */
	private static final float LANDMARK_RANGE = 64.0F;
	/** Spatial warnings emitted at a place on the ground: a mark at the player's own feet. */
	private static final float TELEGRAPH_RANGE = 48.0F;
	/**
	 * Anything the interface's own body emits.
	 *
	 * <p><b>The body is never near anybody, and that is the whole reason this exists.</b> Cues cast by
	 * the interface come out of {@code WorldInterfaceAnatomy.coreOrigin}, which sits {@code hover +
	 * coreLift} over the arena floor - about 16, 29 and 37 blocks at the three forms - and the storm
	 * additionally holds station one head-reach away horizontally. A player standing directly under it
	 * is therefore 18, 32 and 42 blocks from the source before they move anywhere.
	 *
	 * <p>These were registered at {@link #TELEGRAPH_RANGE}, which is the radius for a mark on the
	 * ground at the player's feet. Against a 48-block linear falloff those distances leave 62%, 33%
	 * and 12% of the cue - and the last of those, multiplied by any ordinary master-volume setting,
	 * is silence. The attacks went quiet exactly as the forms grew, which is the same shape as the
	 * bug {@code WorldInterfaceAudioManifestTest} was written for: it fails per form, so it reads as
	 * "the sound disappears after a while" rather than as a radius being wrong.
	 *
	 * <p>Ninety-six leaves 81%, 66% and 56%. The gateways keep the telegraph radius: they are fixed
	 * points on the ground, there are twenty of them, and they are the one cue that should stay local.
	 */
	private static final float BODY_RANGE = 96.0F;

	public static final SoundEvent EMPTY_VIEWPOINT = register("empty_viewpoint");
	public static final SoundEvent EMPTY_BASE = register("empty_base");
	public static final SoundEvent EMPTY_EXPERIENCE = register("empty_experience");
	public static final SoundEvent FOURTH_BAND = register("fourth_band");
	public static final SoundEvent REWORK_JOINT = register("rework_joint");
	public static final SoundEvent ANOMALY_ECHO = register("anomaly_echo");
	public static final SoundEvent WINDOW_GLITCH = register("window_glitch");
	public static final SoundEvent DOOR_CASCADE = register("door_cascade");
	public static final SoundEvent RULE_COLLAPSE = register("rule_collapse");
	// Subtitle-less second layers for the narrative cues. They redirect to vanilla events, but
	// they must be played through mod ids rather than the vanilla SoundEvent constants: playing
	// SoundEvents.STONE_STEP directly makes the client print vanilla's own "Footsteps" subtitle
	// underneath the authored one, which tells a captioned player the thing they just heard was
	// ordinary. Owning the id lets the layer stay silent in the subtitle list.
	public static final SoundEvent LAYER_STONE_STEP = register("layer_stone_step");
	public static final SoundEvent LAYER_WOODEN_DOOR_CLOSE = register("layer_wooden_door_close");
	public static final SoundEvent LAYER_CHEST_CLOSE = register("layer_chest_close");
	public static final SoundEvent LAYER_BEACON_DEACTIVATE = register("layer_beacon_deactivate");
	public static final SoundEvent LAYER_DEEPSLATE_BREAK = register("layer_deepslate_break");
	public static final SoundEvent LAYER_COMPARATOR_CLICK = register("layer_comparator_click");
	public static final SoundEvent TERMINAL_CLICK = register("terminal_click");
	public static final SoundEvent TERMINAL_TUNE = register("terminal_tune");
	public static final SoundEvent TERMINAL_LOCK = register("terminal_lock");
	public static final SoundEvent TERMINAL_FAULT = register("terminal_fault");
	public static final SoundEvent TERMINAL_ANOMALY = register("terminal_anomaly");
	/** The light contact, for navigation that commits nothing. Firmer clicks stay for decisions. */
	public static final SoundEvent TERMINAL_KEYPRESS = register("terminal_keypress");
	/** One notch of the tuning dial. The loop covers the sweep; this marks the discrete steps. */
	public static final SoundEvent TERMINAL_DETENT = register("terminal_detent");
	/**
	 * One line of the power-on self test landing.
	 *
	 * <p>The boot sequence is the first thing the mod ever shows a player and it played in complete
	 * silence, which made the one screen that is supposed to establish the terminal as a working
	 * machine read as a title card. Five of these and the completion below give it a machine's own
	 * cadence: something is being checked, and each check answers.
	 *
	 * <p>Drawn from the device's own recorded contacts rather than from a vanilla event. The first
	 * pass borrowed a comparator click and a beacon chime, and those are sounds a player has spent
	 * hundreds of hours learning to read as redstone and as a beacon - hearing them come out of the
	 * terminal places it in the wrong world. The pitch ramp in {@code TerminalClientAudio} is what
	 * keeps this from being indistinguishable from an ordinary click.
	 */
	public static final SoundEvent TERMINAL_BOOT_LINE = register("terminal_boot_line");
	/** The last line - the one that says it is ready. Deliberately not the same sound as the rest. */
	public static final SoundEvent TERMINAL_BOOT_COMPLETE = register("terminal_boot_complete");
	public static final SoundEvent ALPHA_CORRUPTION_WARNING = register("alpha_corruption_warning");
	public static final SoundEvent ALPHA_CORRUPTION_COLLAPSE = register("alpha_corruption_collapse");
	// The analog-horror signal palette. The four loops are beds meant to sit under everything
	// else at a level low enough to be doubted; the three cues are one-shots.
	public static final SoundEvent SIGNAL_CARRIER = register("signal_carrier");
	public static final SoundEvent SIGNAL_STATIC = register("signal_static");
	public static final SoundEvent SIGNAL_TAPE_HISS = register("signal_tape_hiss");
	public static final SoundEvent SIGNAL_DEAD_AIR = register("signal_dead_air");
	public static final SoundEvent SIGNAL_ALERT = register("signal_alert");
	public static final SoundEvent SIGNAL_CARRIER_LOST = register("signal_carrier_lost");
	public static final SoundEvent SIGNAL_TUNING_SWEEP = register("signal_tuning_sweep");
	// Everything below is an End-arena cue, and the arena is far wider than the 16 blocks a
	// variable-range event reaches at volume <= 1. See #register(String, float).
	public static final SoundEvent WORLD_INTERFACE_ALTAR = register("world_interface_altar", LANDMARK_RANGE);
	public static final SoundEvent WORLD_INTERFACE_TERMINAL = register("world_interface_terminal", LANDMARK_RANGE);
	public static final SoundEvent WORLD_INTERFACE_ANCHOR = register("world_interface_anchor", LANDMARK_RANGE);
	public static final SoundEvent WORLD_INTERFACE_GATEWAY_PURPLE =
			register("world_interface_gateway_purple", TELEGRAPH_RANGE);
	public static final SoundEvent WORLD_INTERFACE_GATEWAY_GOLD =
			register("world_interface_gateway_gold", TELEGRAPH_RANGE);
	public static final SoundEvent WORLD_INTERFACE_GATEWAY_RED =
			register("world_interface_gateway_red", TELEGRAPH_RANGE);
	public static final SoundEvent WORLD_INTERFACE_SUMMON = register("world_interface_summon", ARENA_RANGE);
	// Deliberately variable-range, and deliberately NOT "fixed" the way the cues below were. These
	// three are the phase beds: WorldInterfacePresentationController plays them through
	// playLocalSound with Attenuation.NONE, so they never travel through the positional path a range
	// would apply to. Giving them a radius would state a distance that nothing ever reads.
	public static final SoundEvent WORLD_INTERFACE_AMBIENT_1 = register("world_interface_ambient_1");
	public static final SoundEvent WORLD_INTERFACE_AMBIENT_2 = register("world_interface_ambient_2");
	public static final SoundEvent WORLD_INTERFACE_AMBIENT_3 = register("world_interface_ambient_3");
	public static final SoundEvent WORLD_INTERFACE_MORPH = register("world_interface_morph", ARENA_RANGE);
	// The boss was silent under fire, which read as hits not landing at all. Both cues carry to the
	// arena edge, because a fight this size is fought from further out than 16 blocks.
	public static final SoundEvent WORLD_INTERFACE_HURT = register("world_interface_hurt", ARENA_RANGE);
	/** Heard by whoever was hit, so it is deliberately near-field rather than arena-wide. */
	public static final SoundEvent WORLD_INTERFACE_IMPACT = register("world_interface_impact", BODY_RANGE);
	public static final SoundEvent WORLD_INTERFACE_FORM_SHIFT = register("world_interface_form_shift", ARENA_RANGE);
	public static final SoundEvent WORLD_INTERFACE_DEATH = register("world_interface_death", RESOLUTION_RANGE);
	public static final SoundEvent WORLD_INTERFACE_LASER = register("world_interface_laser", BODY_RANGE);
	/** The discharge, not the charge. Reusing the telegraph sample made firing sound like aiming. */
	public static final SoundEvent WORLD_INTERFACE_LASER_FIRE =
			register("world_interface_laser_fire", ARENA_RANGE);
	public static final SoundEvent WORLD_INTERFACE_ORB = register("world_interface_orb", BODY_RANGE);
	// These six were left on the variable-range default and so were inaudible past 16 blocks - far
	// inside the radius the fight is actually fought at. Each states the radius its role needs.
	public static final SoundEvent WORLD_INTERFACE_GRAB = register("world_interface_grab", BODY_RANGE);
	public static final SoundEvent WORLD_INTERFACE_MENTAL = register("world_interface_mental", BODY_RANGE);
	public static final SoundEvent WORLD_INTERFACE_WEAPON = register("world_interface_weapon", BODY_RANGE);
	/** Arena-wide: a teammate being slammed into the floor is not a private event. */
	public static final SoundEvent WORLD_INTERFACE_THROW = register("world_interface_throw", ARENA_RANGE);
	public static final SoundEvent WORLD_INTERFACE_HOTBAR = register("world_interface_hotbar", BODY_RANGE);
	public static final SoundEvent WORLD_INTERFACE_ARROW = register("world_interface_arrow", BODY_RANGE);
	/** Top-level event: someone is being removed from the world. Everyone hears it. */
	public static final SoundEvent WORLD_INTERFACE_EXPULSION =
			register("world_interface_expulsion", RESOLUTION_RANGE);
	// The encounter's remaining silent frames. Each of these moments previously played nothing at
	// all, which is why they read as the fight skipping rather than landing.
	/** The ring itself, emitted by {@code WorldInterfaceShockwaveService}. */
	public static final SoundEvent WORLD_INTERFACE_SHOCKWAVE =
			register("world_interface_shockwave", ARENA_RANGE);
	/** The downbeat the summon ceremony hands over to. See {@code WorldInterfaceSummonTimeline}. */
	public static final SoundEvent WORLD_INTERFACE_COMBAT_START =
			register("world_interface_combat_start", ARENA_RANGE);
	/** The eviction executing, distinct from the expulsion tear that follows it. */
	public static final SoundEvent WORLD_INTERFACE_EVICTION =
			register("world_interface_eviction", RESOLUTION_RANGE);
	/**
	 * The sky lance charge. It used to borrow the mental-interference sample, which meant a physical
	 * attack that can kill outright and a purely perceptual one announced themselves identically.
	 */
	public static final SoundEvent WORLD_INTERFACE_LANCE = register("world_interface_lance", BODY_RANGE);
	public static final SoundEvent WORLD_INTERFACE_LANCE_IMPACT =
			register("world_interface_lance_impact", ARENA_RANGE);
	/** Third-form hunting displacement: the storm relocating is audible from anywhere in the arena. */
	public static final SoundEvent WORLD_INTERFACE_FLIGHT = register("world_interface_flight", ARENA_RANGE);
	public static final SoundEvent WORLD_INTERFACE_SUCCESS = register("world_interface_success", RESOLUTION_RANGE);
	public static final SoundEvent WORLD_INTERFACE_FAILURE = register("world_interface_failure", RESOLUTION_RANGE);
	// The authored background score. One event per context, each holding that context's whole
	// playlist, so the music manager - which identifies a track by its *event* id - keeps treating
	// the playlist as a single continuous piece of music while the sound system shuffles the files.
	public static final SoundEvent MUSIC_MENU = register("music_menu");
	public static final SoundEvent MUSIC_GAME = register("music_game");
	public static final SoundEvent MUSIC_PURSUIT = register("music_pursuit");
	// The encounter is scored in two pieces rather than one, because the third body is a different
	// fight: separate events are what lets the music manager cut from one to the other on the morph
	// instead of waiting for the first to end.
	public static final SoundEvent MUSIC_ENCOUNTER = register("music_encounter");
	public static final SoundEvent MUSIC_ENCOUNTER_FINAL = register("music_encounter_final");
	public static final SoundEvent MUSIC_ENDING = register("music_ending");
	public static final SoundEvent MUSIC_ENDING_FAILURE = register("music_ending_failure");

	private ModSounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/**
	 * Registers a cue whose audible radius is stated outright instead of being inferred from
	 * whatever volume the caller happens to pass.
	 *
	 * <p>A variable-range event resolves to a flat 16 blocks for every volume at or below 1.0,
	 * and {@link AudioService#playBounded} clamps its relative volume into that range by
	 * construction. Encounter cues were therefore capped at 16 blocks no matter how loud they
	 * were mixed - the server simply never sent the packet to anyone further out - which left
	 * players fighting at range unable to hear the summon, the phase morph or the resolution
	 * that the whole encounter builds towards. Stating the radius here decouples "how far this
	 * carries" from "how loud this is", so the two can be tuned independently.</p>
	 */
	private static SoundEvent register(String path, float range) {
		Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
	}

	public static void initialize() {
		TheFourthFrequency.LOGGER.info("Registered bounded narrative and terminal device sound cues");
	}
}
