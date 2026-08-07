package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.client_render.WorldInterfacePalette;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Vector4f;

/**
 * Turns the encounter stage into an atmospheric pressure the End itself reports.
 *
 * <p>Phase escalation used to exist only as a HUD label and an ambient loop swap. Pulling the fog in
 * and draining the sky toward the current palette band makes the same escalation something a player
 * feels while looking at the arena, and reuses the anomaly system's existing fog and sky seams
 * rather than adding new render hooks.</p>
 */
public final class WorldInterfaceAtmosphereController {
	/** Per-tick convergence. Deliberately slow: a phase change should creep in, not snap. */
	private static final float EASE_RATE = 0.045F;
	private static final float FOG_START_FLOOR = 12.0F;
	private static final float FOG_END_FLOOR = 54.0F;
	/** Cap so the arena never becomes literally unplayable at full pressure. */
	// Raised for the terminal phase. The old ceilings were set when the third form was
	// something the arena merely contained; it is supposed to be taking the sky by then.
	private static final float MAX_FOG_PULL = 0.90F;
	private static final float MAX_SKY_DRAIN = 0.84F;

	private static float pressure;

	private WorldInterfaceAtmosphereController() {
	}

	public static void tick(Minecraft client, WorldInterfaceClientState.Projection projection) {
		float target = targetPressure(client, projection);
		pressure += (target - pressure) * EASE_RATE;
		if (Math.abs(target - pressure) < 1.0E-4F) pressure = target;
	}

	public static void reset() {
		pressure = 0.0F;
	}

	public static float pressure() {
		return pressure;
	}

	/** Current escalation band, held at the last combat band through the resolutions. */
	public static int band() {
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		return WorldInterfacePalette.band(encounter == null ? null : encounter.stage());
	}

	public static float fogStart(float original) {
		if (pressure <= 0.0F) return original;
		return mix(original, Math.min(original, FOG_START_FLOOR), pressure * MAX_FOG_PULL);
	}

	public static float fogEnd(float original) {
		if (pressure <= 0.0F) return original;
		return mix(original, Math.min(original, FOG_END_FLOOR), pressure * MAX_FOG_PULL);
	}

	public static Vector4f tintFog(Vector4f original) {
		if (pressure <= 0.0F || original == null) return original;
		int band = band();
		float amount = pressure * MAX_SKY_DRAIN;
		return new Vector4f(
				mix(original.x, WorldInterfacePalette.red(band) * 0.30F, amount),
				mix(original.y, WorldInterfacePalette.green(band) * 0.24F, amount),
				mix(original.z, WorldInterfacePalette.blue(band) * 0.34F, amount),
				original.w);
	}

	public static int tintSky(int original) {
		if (pressure <= 0.0F) return original;
		int band = band();
		float amount = pressure * MAX_SKY_DRAIN;
		int alpha = original >>> 24;
		if (alpha == 0) alpha = 255;
		int red = channel((original >> 16) & 255, WorldInterfacePalette.red(band) * 0.26F, amount);
		int green = channel((original >> 8) & 255, WorldInterfacePalette.green(band) * 0.20F, amount);
		int blue = channel(original & 255, WorldInterfacePalette.blue(band) * 0.30F, amount);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	/** Stars are the first thing the interface takes; by phase three the sky is effectively empty. */
	public static float drainStarBrightness(float original) {
		return pressure <= 0.0F ? original : original * (1.0F - pressure * MAX_SKY_DRAIN);
	}

	private static float targetPressure(Minecraft client, WorldInterfaceClientState.Projection projection) {
		if (client == null || client.level == null || !Level.END.equals(client.level.dimension())) return 0.0F;
		WorldInterfaceSnapshotS2C encounter = projection.encounter();
		if (encounter == null) return 0.0F;
		if (encounter.outcome() == WorldInterfaceProtocol.Outcome.FAILURE) return 1.0F;
		float base = switch (encounter.stage()) {
			case SUMMONING -> 0.20F;
			case PHASE_1 -> 0.30F;
			case PHASE_2 -> 0.56F;
			case PHASE_3 -> 0.92F;
			case SUCCESS_RESOLUTION -> 0.34F;
			case FAILURE_RESOLUTION -> 1.0F;
			default -> 0.0F;
		};
		// The third phase breathes on its volley clock.
		//
		// That phase fires on a fixed interval and nothing in the world said so, which is why the
		// last salvos read as arbitrary rather than as fast. Pulsing the fog and the sky drain on
		// the same sawtooth the boss charges to means the arena itself tightens before each volley -
		// so the rhythm becomes something a player can anticipate instead of only survive.
		if (encounter.stage() == WorldInterfaceProtocol.Stage.PHASE_3 && client.level != null) {
			// volleyBreath, never volleyRamp: the ramp is a sawtooth and this drives fog distance
			// and sky tint, which cover the whole view. See WorldInterfacePalette.volleyBreath.
			float breath = WorldInterfacePalette.volleyBreath(client.level.getGameTime());
			base = Math.min(1.0F, base + breath * VOLLEY_PRESSURE_SWING);
		}
		return base;
	}

	/** How much extra pressure the third phase gathers between volleys. */
	private static final float VOLLEY_PRESSURE_SWING = 0.08F;

	private static int channel(int original, float targetUnit, float amount) {
		return Math.clamp(Math.round(mix(original, targetUnit * 255.0F, amount)), 0, 255);
	}

	private static float mix(float from, float to, float amount) {
		return from + (to - from) * Math.clamp(amount, 0.0F, 1.0F);
	}
}
