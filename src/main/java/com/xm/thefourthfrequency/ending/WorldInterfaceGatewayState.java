package com.xm.thefourthfrequency.ending;

import java.util.Arrays;

/** Shared render state for the twenty inert End gateways. */
public enum WorldInterfaceGatewayState {
	DORMANT(0, "dormant"),
	PURPLE(1, "purple"),
	GOLD(2, "gold"),
	RED(3, "red");

	private final int wireId;
	private final String serializedName;

	WorldInterfaceGatewayState(int wireId, String serializedName) {
		this.wireId = wireId;
		this.serializedName = serializedName;
	}

	public int wireId() {
		return wireId;
	}

	public String serializedName() {
		return serializedName;
	}

	public static WorldInterfaceGatewayState fromWireId(int wireId) {
		return Arrays.stream(values())
				.filter(value -> value.wireId == wireId)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown gateway state ID: " + wireId));
	}
}
