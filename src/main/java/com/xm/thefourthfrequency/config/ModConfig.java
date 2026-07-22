package com.xm.thefourthfrequency.config;

public record ModConfig(
		Meta meta,
		Pacing pacing,
		ClientState clientState
) {
	public static ModConfig defaults() {
		return new ModConfig(Meta.defaults(), Pacing.defaults(), ClientState.defaults());
	}

	public ModConfig validated() {
		return new ModConfig(
				meta == null ? Meta.defaults() : meta.validated(),
				pacing == null ? Pacing.defaults() : pacing,
				clientState == null ? ClientState.defaults() : clientState
		);
	}

	public ModConfig withClientState(ClientState updatedClientState) {
		return new ModConfig(meta, pacing, updatedClientState).validated();
	}

	public record Meta(
			boolean enabled,
			double peakVolume
	) {
		private static Meta defaults() {
			return new Meta(true, 0.8D);
		}

		private Meta validated() {
			return new Meta(enabled, clamp(peakVolume, 0.0D, 1.0D));
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
