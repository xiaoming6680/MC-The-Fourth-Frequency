package com.xm.thefourthfrequency.terminal;

public record AnomalyDefinition(String id, int tier, Scope scope, boolean strong, boolean destructive) {
	public enum Scope { PRIVATE, SHARED }
}
