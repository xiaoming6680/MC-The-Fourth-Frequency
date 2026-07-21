package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.terminal.AnomalyCatalog;

import java.util.Optional;

/** Strict parser for the Gradle-to-client-GameTest selection contract. */
public record ClientGameTestSelection(Suite suite, Optional<String> anomalyId) {
	public enum Suite { ALL, MAINLINE, ALPHA_RELAUNCH, ANOMALIES, ANOMALY_META_SMOKE, REWORK_FORMS, WATCHER_MODEL }

	public ClientGameTestSelection {
		anomalyId = anomalyId == null ? Optional.empty() : anomalyId;
		if (anomalyId.isPresent() && suite != Suite.ANOMALIES)
			throw new IllegalArgumentException("tffAnomaly requires the anomalies suite");
		anomalyId.ifPresent(id -> AnomalyCatalog.require(id));
	}

	public static ClientGameTestSelection current() {
		return parse(System.getProperty("thefourthfrequency.clientTestSuite", "all"),
				System.getProperty("thefourthfrequency.anomaly", ""));
	}

	public static ClientGameTestSelection parse(String suiteValue, String anomalyValue) {
		Suite suite = switch (suiteValue == null ? "all" : suiteValue.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "all", "default" -> Suite.ALL;
			case "mainline" -> Suite.MAINLINE;
			case "alpha-relaunch" -> Suite.ALPHA_RELAUNCH;
			case "anomalies" -> Suite.ANOMALIES;
			case "anomaly-meta-smoke" -> Suite.ANOMALY_META_SMOKE;
			case "rework-forms" -> Suite.REWORK_FORMS;
			case "watcher-model" -> Suite.WATCHER_MODEL;
			default -> throw new IllegalArgumentException("Unknown client test suite: " + suiteValue);
		};
		String normalized = anomalyValue == null ? "" : anomalyValue.trim();
		return new ClientGameTestSelection(suite, normalized.isEmpty() ? Optional.empty() : Optional.of(normalized));
	}

	public boolean runsMainline() { return suite == Suite.ALL || suite == Suite.MAINLINE; }
	public boolean runsAlphaRelaunch() { return suite == Suite.ALPHA_RELAUNCH; }
	public boolean runsAnomalies() { return suite == Suite.ALL || suite == Suite.ANOMALIES; }
	public boolean runsMetaSmoke() { return suite == Suite.ANOMALY_META_SMOKE; }
	public boolean runsReworkForms() { return suite == Suite.REWORK_FORMS; }
	public boolean runsWatcherModel() { return suite == Suite.WATCHER_MODEL; }
}
