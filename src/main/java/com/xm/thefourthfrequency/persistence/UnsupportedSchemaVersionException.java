package com.xm.thefourthfrequency.persistence;

public final class UnsupportedSchemaVersionException extends IllegalStateException {
	public UnsupportedSchemaVersionException(int found, int supported) {
		super("Save schema " + found + " is newer than supported schema " + supported);
	}
}

