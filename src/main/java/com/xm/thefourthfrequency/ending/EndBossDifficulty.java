package com.xm.thefourthfrequency.ending;

/**
 * Pure difficulty policy for the End encounter. More surviving stability anchors must always make
 * the target easier to control, even though they provide a deliberately small amount of healing.
 */
public final class EndBossDifficulty {
	private static final double BASE_HEALTH = 360.0;

	private EndBossDifficulty() {
	}

	public static Profile forEncounter(int anchors, int participantScale) {
		int safeAnchors = Math.max(0, anchors);
		int players = Math.max(1, participantScale);
		double maxHealth = Math.min(4_096.0, BASE_HEALTH * (1.0 + (players - 1) * 0.72));
		if (safeAnchors >= 8) {
			return new Profile("contained", safeAnchors, players, maxHealth,
					0.22, 0.68, 8.0, 1.0F, safeAnchors * 0.08F, 22, 220, 7.0F);
		}
		if (safeAnchors >= 5) {
			return new Profile("strained", safeAnchors, players, maxHealth,
					0.29, 0.84, 12.0, 0.72F, safeAnchors * 0.07F, 16, 160, 10.0F);
		}
		if (safeAnchors >= 2) {
			return new Profile("critical", safeAnchors, players, maxHealth,
					0.37, 1.02, 18.0, 0.38F, safeAnchors * 0.05F, 12, 105, 15.0F);
		}
		if (safeAnchors == 1) {
			return new Profile("failing", 1, players, maxHealth,
					0.46, 1.18, 25.0, 0.16F, 0.03F, 9, 70, 22.0F);
		}
		// Zero anchors is intentionally only barely winnable: 96.5% damage reduction plus an
		// aggressive movement/attack profile. It is not absolute invulnerability, so a save cannot
		// be permanently soft-locked.
		return new Profile("unbound", 0, players, maxHealth,
				0.60, 1.38, 36.0, 0.035F, 0.0F, 6, 35, 32.0F);
	}

	public record Profile(String tier, int anchors, int participantScale, double maxHealth,
			double movementSpeed, double navigationSpeed, double attackDamage, float damageTakenMultiplier,
			float healingPerSecond, int attackInterval, int adaptationCooldown, float adaptationDamage) {
		public Profile {
			if (anchors < 0 || participantScale < 1 || maxHealth <= 0.0 || movementSpeed <= 0.0
					|| navigationSpeed <= 0.0 || attackDamage <= 0.0 || damageTakenMultiplier <= 0.0F
					|| healingPerSecond < 0.0F || attackInterval < 1 || adaptationCooldown < 1
					|| adaptationDamage <= 0.0F) {
				throw new IllegalArgumentException("Invalid End boss profile");
			}
		}
	}
}
