package com.xm.thefourthfrequency.meta_api;

public record MetaContext(String worldName, long windowHandle, long localTimeMillis) {
	public MetaContext {
		worldName = worldName == null || worldName.isBlank() ? "world" : worldName.substring(0, Math.min(64, worldName.length()));
	}
}
