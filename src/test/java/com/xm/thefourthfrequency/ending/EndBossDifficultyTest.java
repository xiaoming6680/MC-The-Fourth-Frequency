package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndBossDifficultyTest {
	@Test
	void everyLostStabilityAnchorTierMakesTheBossHarder() {
		var contained = EndBossDifficulty.forEncounter(10, 1);
		var strained = EndBossDifficulty.forEncounter(6, 1);
		var critical = EndBossDifficulty.forEncounter(3, 1);
		var failing = EndBossDifficulty.forEncounter(1, 1);
		var unbound = EndBossDifficulty.forEncounter(0, 1);
		var profiles = java.util.List.of(contained, strained, critical, failing, unbound);
		for (int index = 1; index < profiles.size(); index++) {
			var easier = profiles.get(index - 1);
			var harder = profiles.get(index);
			assertTrue(harder.movementSpeed() > easier.movementSpeed());
			assertTrue(harder.attackDamage() > easier.attackDamage());
			assertTrue(harder.damageTakenMultiplier() < easier.damageTakenMultiplier());
			assertTrue(harder.attackInterval() < easier.attackInterval());
			assertTrue(harder.adaptationCooldown() < easier.adaptationCooldown());
		}
		assertEquals(0.0F, unbound.healingPerSecond());
		assertTrue(unbound.damageTakenMultiplier() <= 0.035F);
	}

	@Test
	void participantScalingOnlyRaisesMaximumHealth() {
		var solo = EndBossDifficulty.forEncounter(10, 1);
		var group = EndBossDifficulty.forEncounter(10, 4);
		assertTrue(group.maxHealth() > solo.maxHealth());
		assertEquals(solo.attackDamage(), group.attackDamage());
	}
}
