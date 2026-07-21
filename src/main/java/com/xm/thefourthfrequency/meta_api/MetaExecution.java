package com.xm.thefourthfrequency.meta_api;

import java.util.List;

public record MetaExecution(MetaEvent event, boolean externalEffects, boolean degraded,
		List<MetaArtifact> artifacts, List<MetaProcessHandle> ownedProcesses) {
	public MetaExecution {
		artifacts = List.copyOf(artifacts);
		ownedProcesses = List.copyOf(ownedProcesses);
	}

	public static MetaExecution disabled(MetaEvent event) {
		return new MetaExecution(event, false, true, List.of(), List.of());
	}
}
