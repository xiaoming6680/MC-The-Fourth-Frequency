package com.xm.thefourthfrequency.config;

public record ModConfig(
		Meta meta,
		Pacing pacing,
		Limits limits,
		ClientState clientState
) {
	public static ModConfig defaults() {
		return new ModConfig(Meta.defaults(), Pacing.defaults(), Limits.defaults(), ClientState.defaults());
	}

	public ModConfig validated() {
		return new ModConfig(
				meta == null ? Meta.defaults() : meta.validated(),
				pacing == null ? Pacing.defaults() : pacing.validated(),
				limits == null ? Limits.defaults() : limits.validated(),
				clientState == null ? ClientState.defaults() : clientState
		);
	}

	public ModConfig withClientState(ClientState updatedClientState) {
		return new ModConfig(meta, pacing, limits, updatedClientState).validated();
	}

	public record Meta(
			boolean enabled,
			boolean subtitlesEnabled,
			double peakVolume
	) {
		private static Meta defaults() {
			return new Meta(true, true, 0.8D);
		}

		private Meta validated() {
			return new Meta(enabled, subtitlesEnabled, clamp(peakVolume, 0.0D, 1.0D));
		}
	}

	public record Pacing(
			boolean developerAcceleration,
			int productionHours,
			int acceleratedMinutes,
			int ambientAnomalyMinMinutes,
			int ambientAnomalyMaxMinutes
	) {
		private static Pacing defaults() {
			return new Pacing(false, 8, 3, 5, 10);
		}

		private Pacing validated() {
			int minimum = ambientAnomalyMinMinutes <= 0
					? 5 : clamp(ambientAnomalyMinMinutes, 1, 60);
			int maximum = ambientAnomalyMaxMinutes <= 0
					? 10 : clamp(ambientAnomalyMaxMinutes, minimum, 120);
			return new Pacing(
					developerAcceleration,
					clamp(productionHours, 6, 10),
					clamp(acceleratedMinutes, 1, 30),
					minimum,
					maximum
			);
		}
	}

	public record Limits(int correctionWorkBudgetPerTick) {
		private static Limits defaults() {
			return new Limits(64);
		}

		private Limits validated() {
			return new Limits(clamp(correctionWorkBudgetPerTick, 8, 512));
		}
	}

	public record ClientState(
			boolean safetyNoticeAcknowledged,
			boolean alphaDowngradeComplete
	) {
		private static ClientState defaults() {
			return new ClientState(false, false);
		}

		public ClientState acknowledgeSafetyNotice() {
			return new ClientState(true, alphaDowngradeComplete);
		}

		public ClientState completeAlphaDowngrade() {
			return new ClientState(safetyNoticeAcknowledged, true);
		}
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double clamp(double value, double minimum, double maximum) {
		if (!Double.isFinite(value)) {
			return maximum;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}
}
