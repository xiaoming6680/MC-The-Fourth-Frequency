package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalNavigationMathTest {
	@Test
	void computesNorthAndTargetNeedlesInPlayerSpace() {
		assertEquals(0.0D, TerminalNavigationMath.northNeedleDegrees(180.0F), 0.0001D);
		assertEquals(-180.0D, TerminalNavigationMath.northNeedleDegrees(0.0F), 0.0001D);
		assertEquals(0.0D, TerminalNavigationMath.targetNeedleDegrees(0, 20, 0.0F), 0.0001D);
		assertEquals(-90.0D, TerminalNavigationMath.targetNeedleDegrees(20, 0, 0.0F), 0.0001D);
		assertEquals(0.0D, TerminalNavigationMath.targetNeedleDegrees(20, 0, -90.0F), 0.0001D);
	}

	@Test
	void interpolationTakesTheShortPathAcrossTheWrapBoundary() {
		assertEquals(-180.0D, TerminalNavigationMath.interpolateDegrees(170.0D, -170.0D, 0.5D), 0.0001D);
		assertEquals(170.0D, TerminalNavigationMath.interpolateDegrees(170.0D, -170.0D, 0.0D), 0.0001D);
		assertEquals(-170.0D, TerminalNavigationMath.interpolateDegrees(170.0D, -170.0D, 1.0D), 0.0001D);
	}

	@Test
	void navigationStopsForEveryInvalidTargetCondition() {
		assertTrue(TerminalNavigationMath.navigable(1, false, true, true));
		assertFalse(TerminalNavigationMath.navigable(0, false, true, true));
		assertFalse(TerminalNavigationMath.navigable(1, true, true, true));
		assertFalse(TerminalNavigationMath.navigable(4, true, true, true));
		assertTrue(TerminalNavigationMath.navigable(7, false, true, true));
		assertFalse(TerminalNavigationMath.navigable(1, false, false, true));
		assertFalse(TerminalNavigationMath.navigable(1, false, true, false));
	}

	@Test
	void producesEightDirectionsAndPlanarDistance() {
		assertEquals("north", TerminalNavigationMath.direction(0, -8));
		assertEquals("northeast", TerminalNavigationMath.direction(8, -8));
		assertEquals("east", TerminalNavigationMath.direction(8, 0));
		assertEquals("southeast", TerminalNavigationMath.direction(8, 8));
		assertEquals("south", TerminalNavigationMath.direction(0, 8));
		assertEquals("southwest", TerminalNavigationMath.direction(-8, 8));
		assertEquals("west", TerminalNavigationMath.direction(-8, 0));
		assertEquals("northwest", TerminalNavigationMath.direction(-8, -8));
		assertEquals(5, TerminalNavigationMath.distance(3, 4));
	}

	@Test
	void completionDirectionUsesThePlayersFourRelativeSides() {
		assertEquals("ahead", TerminalNavigationMath.relativeDirectionId(
				TerminalNavigationMath.relativeDirection(0, 10, 0.0F)));
		assertEquals("left", TerminalNavigationMath.relativeDirectionId(
				TerminalNavigationMath.relativeDirection(10, 0, 0.0F)));
		assertEquals("right", TerminalNavigationMath.relativeDirectionId(
				TerminalNavigationMath.relativeDirection(-10, 0, 0.0F)));
		assertEquals("behind", TerminalNavigationMath.relativeDirectionId(
				TerminalNavigationMath.relativeDirection(0, -10, 0.0F)));
	}

	@Test
	void structureArrivalUsesAFiftyBlockHorizontalRadius() {
		assertTrue(TerminalNavigationMath.withinHorizontalRadius(10, -10, 40, 30, 50));
		assertTrue(TerminalNavigationMath.withinHorizontalRadius(10, -10, 60, -10, 50));
		assertFalse(TerminalNavigationMath.withinHorizontalRadius(10, -10, 61, -10, 50));
		assertFalse(TerminalNavigationMath.withinHorizontalRadius(10, -10, 46, 26, 50));
	}
}
