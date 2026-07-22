package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldInterfaceGatewayStateTest {
	@Test
	void gatewayRenderStatesHaveStableWireIds() {
		assertEquals(4, WorldInterfaceGatewayState.values().length);
		for (int wireId = 0; wireId < 4; wireId++) {
			WorldInterfaceGatewayState state = WorldInterfaceGatewayState.values()[wireId];
			assertEquals(wireId, state.wireId());
			assertEquals(state, WorldInterfaceGatewayState.fromWireId(wireId));
		}
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceGatewayState.fromWireId(-1));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceGatewayState.fromWireId(4));
	}
}
