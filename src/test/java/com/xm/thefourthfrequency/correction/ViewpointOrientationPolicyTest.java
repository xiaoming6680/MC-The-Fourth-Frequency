package com.xm.thefourthfrequency.correction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ViewpointOrientationPolicyTest {
	@Test
	void separatedViewFacesThePlayersHorizontalForwardHeading() {
		var orientation = ViewpointOrientationPolicy.facePlayerForward(73.0F);

		assertEquals(73.0F, orientation.yaw());
		assertEquals(0.0F, orientation.pitch());
	}
}
