package com.xm.thefourthfrequency.config;

public record ModConfig(
		Meta meta,
		Pacing pacing,
		ClientState clientState,
		Presentation presentation
) {
	public static ModConfig defaults() {
		return new ModConfig(Meta.defaults(), Pacing.defaults(), ClientState.defaults(),
				Presentation.defaults());
	}

	public ModConfig validated() {
		return new ModConfig(
				meta == null ? Meta.defaults() : meta.validated(),
				pacing == null ? Pacing.defaults() : pacing,
				clientState == null ? ClientState.defaults() : clientState,
				presentation == null ? Presentation.defaults() : presentation.validated()
		);
	}

	public ModConfig withClientState(ClientState updatedClientState) {
		return new ModConfig(meta, pacing, updatedClientState, presentation).validated();
	}

	public ModConfig withPresentation(Presentation updatedPresentation) {
		return new ModConfig(meta, pacing, clientState, updatedPresentation).validated();
	}

	/**
	 * The three impact effects, each switchable on its own.
	 *
	 * <p>Deliberately three independent settings rather than one "screen effects" toggle. They fail
	 * differently for different people: shake is the one that causes motion sickness, hit-stop is
	 * the one that feels like a stutter if you are not expecting it, and the flash is the one that
	 * matters if you are photosensitive. Bundling them would mean a player who needs one off loses
	 * the other two.
	 *
	 * <p>All three are boxed, for exactly the reason {@code Meta.bedVolume} is: Gson fills an absent
	 * primitive with 0/false, so shipping these unboxed would silently switch all three off for
	 * every player whose config file predates the field. Null means "not configured" and resolves to
	 * the default; an explicit 0 or false still means off.
	 */
	public record Presentation(
			Double cameraShake,
			Boolean hitStop,
			Boolean impactFlash
	) {
		private static final double DEFAULT_CAMERA_SHAKE = 1.0D;

		private static Presentation defaults() {
			return new Presentation(DEFAULT_CAMERA_SHAKE, true, true);
		}

		private Presentation validated() {
			return new Presentation(
					clamp(cameraShake == null ? DEFAULT_CAMERA_SHAKE : cameraShake, 0.0D, 1.0D),
					hitStop == null || hitStop,
					impactFlash == null || impactFlash);
		}

		/**
		 * Shake strength as a 0..1 scale rather than a boolean.
		 *
		 * <p>A player who finds the shake nauseating usually wants it toned down, not removed - the
		 * feedback is doing real work telling them a hit landed. 0.0 is still a full off switch.
		 */
		public double effectiveCameraShake() {
			return clamp(cameraShake == null ? DEFAULT_CAMERA_SHAKE : cameraShake, 0.0D, 1.0D);
		}

		public boolean hitStopEnabled() {
			return hitStop == null || hitStop;
		}

		public boolean impactFlashEnabled() {
			return impactFlash == null || impactFlash;
		}
	}

	public record Meta(
			boolean enabled,
			double peakVolume,
			/**
			 * Boxed on purpose. Gson fills an absent primitive with 0.0, so shipping this as a
			 * {@code double} would have silently muted the beds for everyone who already has a
			 * config file written before the field existed. Null means "not configured" and
			 * resolves to {@link #DEFAULT_BED_VOLUME}; an explicit 0 still means silence.
			 */
			Double bedVolume
	) {
		private static final double DEFAULT_BED_VOLUME = 1.0D;

		private static Meta defaults() {
			return new Meta(true, 0.8D, DEFAULT_BED_VOLUME);
		}

		private Meta validated() {
			return new Meta(enabled, clamp(peakVolume, 0.0D, 1.0D),
					clamp(bedVolume == null ? DEFAULT_BED_VOLUME : bedVolume, 0.0D, 1.0D));
		}

		/**
		 * A separate trim for the continuous signal beds, multiplied on top of {@link #peakVolume}.
		 *
		 * <p>The beds run on MASTER so the silent_world anomaly cannot mute them along with the
		 * world, which is the right call for the fiction but leaves a player with no way to turn
		 * down a permanent hiss: the game's own MASTER slider takes everything with it, and
		 * peakVolume trims every other cue this mod owns. This is the one knob that reaches the
		 * beds and nothing else.</p>
		 */
		public double effectiveBedVolume() {
			return clamp(peakVolume, 0.0D, 1.0D)
					* clamp(bedVolume == null ? DEFAULT_BED_VOLUME : bedVolume, 0.0D, 1.0D);
		}
	}

	public record Pacing(boolean developerAcceleration) {
		private static Pacing defaults() {
			return new Pacing(false);
		}
	}

	public record ClientState(
			boolean alphaDowngradeComplete,
			boolean viewDistanceUnlocked
	) {
		private static ClientState defaults() {
			return new ClientState(false, false);
		}

		public ClientState completeAlphaDowngrade() {
			return new ClientState(true, viewDistanceUnlocked);
		}

		public ClientState unlockViewDistance() {
			return new ClientState(alphaDowngradeComplete, true);
		}
	}

	private static double clamp(double value, double minimum, double maximum) {
		if (!Double.isFinite(value)) {
			return maximum;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}
}
