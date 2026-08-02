package com.xm.thefourthfrequency.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {
	@Test
	void defaultsContainOnlyLiveSettings() {
		ModConfig defaults = ModConfig.defaults();
		assertTrue(defaults.meta().enabled());
		assertEquals(0.8D, defaults.meta().peakVolume());
		assertEquals(1.0D, defaults.meta().bedVolume());
		assertEquals(0.8D, defaults.meta().effectiveBedVolume());
		assertFalse(defaults.pacing().developerAcceleration());
		assertFalse(defaults.clientState().alphaDowngradeComplete());
		assertFalse(defaults.clientState().viewDistanceUnlocked());
	}

	@Test
	void validationClampsUnsafeVolume() {
		ModConfig validated = new ModConfig(
				new ModConfig.Meta(true, Double.NaN, Double.NaN),
				new ModConfig.Pacing(true),
				new ModConfig.ClientState(false, false)
		).validated();
		assertEquals(1.0D, validated.meta().peakVolume());
		assertEquals(1.0D, validated.meta().bedVolume());
		assertTrue(validated.pacing().developerAcceleration());

		ModConfig muted = new ModConfig(
				new ModConfig.Meta(true, -2.0D, -2.0D),
				new ModConfig.Pacing(false),
				new ModConfig.ClientState(false, false)
		).validated();
		assertEquals(0.0D, muted.meta().peakVolume());
		assertEquals(0.0D, muted.meta().bedVolume());
	}

	/**
	 * The beds get their own trim, and an explicit zero has to survive validation - it is the
	 * only way a player can silence a permanent hiss without turning the rest of the mod down
	 * with it. That is also why the field is boxed: a config written before it existed must read
	 * back as "unset" and default to full, not as the 0.0 Gson would otherwise supply.
	 */
	@Test
	void bedVolumeDistinguishesUnsetFromDeliberateSilence() {
		Gson gson = new Gson();
		ModConfig legacy = gson.fromJson(
				"{\"meta\": {\"enabled\": true, \"peakVolume\": 0.5}}", ModConfig.class).validated();
		assertEquals(1.0D, legacy.meta().bedVolume());
		assertEquals(0.5D, legacy.meta().effectiveBedVolume());

		ModConfig silenced = gson.fromJson(
				"{\"meta\": {\"enabled\": true, \"peakVolume\": 0.5, \"bedVolume\": 0.0}}",
				ModConfig.class).validated();
		assertEquals(0.0D, silenced.meta().effectiveBedVolume());
		assertEquals(0.5D, silenced.meta().peakVolume(), "trimming the beds must not touch the rest");
	}

	@Test
	void jsonUsesMinimalGroupedConfigurationContractAndIgnoresRetiredFields() {
		Gson gson = new Gson();
		String encoded = gson.toJson(ModConfig.defaults());
		assertTrue(encoded.contains("\"meta\":{\"enabled\":true"));
		assertTrue(encoded.contains("\"pacing\":{"));
		assertTrue(encoded.contains("\"clientState\":{"));
		assertFalse(encoded.contains("subtitlesEnabled"));
		assertFalse(encoded.contains("productionHours"));
		assertFalse(encoded.contains("acceleratedMinutes"));
		assertFalse(encoded.contains("ambientAnomalyMinMinutes"));
		assertFalse(encoded.contains("ambientAnomalyMaxMinutes"));
		assertFalse(encoded.contains("correctionWorkBudgetPerTick"));
		assertFalse(encoded.contains("\"limits\""));
		assertFalse(encoded.contains("safetyNoticeAcknowledged"));

		ModConfig decoded = gson.fromJson("""
				{
				  "meta": {"enabled": false, "subtitlesEnabled": true, "peakVolume": 0.5},
				  "pacing": {
				    "developerAcceleration": true,
				    "productionHours": 9,
				    "acceleratedMinutes": 2,
				    "ambientAnomalyMinMinutes": 6,
				    "ambientAnomalyMaxMinutes": 12
				  },
				  "limits": {"correctionWorkBudgetPerTick": 96},
				  "clientState": {
				    "safetyNoticeAcknowledged": true,
				    "alphaDowngradeComplete": false
				  }
				}
				""", ModConfig.class).validated();
		assertFalse(decoded.meta().enabled());
		assertEquals(0.5D, decoded.meta().peakVolume());
		assertTrue(decoded.pacing().developerAcceleration());
		assertFalse(decoded.clientState().viewDistanceUnlocked());

		String normalized = gson.toJson(decoded);
		assertFalse(normalized.contains("subtitlesEnabled"));
		assertFalse(normalized.contains("productionHours"));
		assertFalse(normalized.contains("correctionWorkBudgetPerTick"));
		assertFalse(normalized.contains("\"limits\""));
		assertFalse(normalized.contains("safetyNoticeAcknowledged"));
	}

	@Test
	void clientStateTransitionsPreserveTheOtherFlag() {
		ModConfig.ClientState completed = ModConfig.defaults().clientState().completeAlphaDowngrade();
		assertTrue(completed.alphaDowngradeComplete());
		assertFalse(completed.viewDistanceUnlocked());

		ModConfig.ClientState unlocked = completed.unlockViewDistance();
		assertTrue(unlocked.alphaDowngradeComplete());
		assertTrue(unlocked.viewDistanceUnlocked());
	}
}
