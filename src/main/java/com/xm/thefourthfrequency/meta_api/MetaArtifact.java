package com.xm.thefourthfrequency.meta_api;

public record MetaArtifact(String artifactId, Kind kind, String relativeName, boolean ownedByMod) {
	public MetaArtifact {
		if (artifactId == null || !artifactId.matches("[a-z0-9_]{1,40}")) {
			throw new IllegalArgumentException("Meta artifact ID must be a fixed safe identifier");
		}
		if (relativeName == null || !relativeName.matches("[A-Za-z0-9 _.-]{1,64}")
				|| relativeName.contains("..") || relativeName.contains("/") || relativeName.contains("\\")) {
			throw new IllegalArgumentException("Meta artifact must be a single safe relative name");
		}
	}

	public enum Kind { TEXT, PNG, DATA }
}
