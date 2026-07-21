package com.xm.thefourthfrequency.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {
	@Test
	void defaultsKeepProductionAndDevelopmentPacingSeparate() {
		ModConfig defaults = ModConfig.defaults();
		assertTrue(defaults.pacing().productionHours() >= 6
				&& defaults.pacing().productionHours() <= 10);
		assertTrue(defaults.pacing().acceleratedMinutes()
				< defaults.pacing().productionHours() * 60);
		assertTrue(defaults.meta().subtitlesEnabled());
		assertEquals(0.8D, defaults.meta().peakVolume());
		assertFalse(defaults.clientState().safetyNoticeAcknowledged());
		assertFalse(defaults.clientState().alphaDowngradeComplete());
	}

	@Test
	void validationClampsUnsafeValues() {
		ModConfig validated = new ModConfig(
				new ModConfig.Meta(true, true, Double.NaN),
				new ModConfig.Pacing(true, 100, 0, -1, -1),
				new ModConfig.Limits(10_000),
				new ModConfig.ClientState(false, false)
		).validated();
		assertEquals(10, validated.pacing().productionHours());
		assertEquals(1, validated.pacing().acceleratedMinutes());
		assertEquals(1.0D, validated.meta().peakVolume());
		assertEquals(512, validated.limits().correctionWorkBudgetPerTick());
		assertEquals(5, validated.pacing().ambientAnomalyMinMinutes());
		assertEquals(10, validated.pacing().ambientAnomalyMaxMinutes());

		ModConfig muted = new ModConfig(
				new ModConfig.Meta(true, true, -2.0D),
				new ModConfig.Pacing(false, 8, 3, 5, 10),
				new ModConfig.Limits(64),
				new ModConfig.ClientState(false, false)
		).validated();
		assertEquals(0.0D, muted.meta().peakVolume());
	}

	@Test
	void jsonUsesOneGroupedConfigurationContract() {
		Gson gson = new Gson();
		String encoded = gson.toJson(ModConfig.defaults());
		assertTrue(encoded.contains("\"meta\":{\"enabled\":true"));
		assertTrue(encoded.contains("\"pacing\":{"));
		assertTrue(encoded.contains("\"limits\":{"));
		assertTrue(encoded.contains("\"clientState\":{"));
		assertFalse(encoded.contains("\"meta.enabled\""));

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
		assertEquals(9, decoded.pacing().productionHours());
		assertEquals(96, decoded.limits().correctionWorkBudgetPerTick());
		assertTrue(decoded.clientState().safetyNoticeAcknowledged());
	}

	@Test
	void clientStateTransitionsPreserveTheOtherFlag() {
		ModConfig.ClientState acknowledged = ModConfig.defaults().clientState()
				.acknowledgeSafetyNotice();
		assertTrue(acknowledged.safetyNoticeAcknowledged());
		assertFalse(acknowledged.alphaDowngradeComplete());

		ModConfig.ClientState completed = acknowledged.completeAlphaDowngrade();
		assertTrue(completed.safetyNoticeAcknowledged());
		assertTrue(completed.alphaDowngradeComplete());
	}
}
