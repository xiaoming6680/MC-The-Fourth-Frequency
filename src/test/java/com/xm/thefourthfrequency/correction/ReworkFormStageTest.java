package com.xm.thefourthfrequency.correction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReworkFormStageTest {
	@Test
	void dismantleCountClampsToFiveMonotonicFormsWithoutOverflow() {
		assertEquals(1, ReworkFormStage.forDismantleCount(Integer.MIN_VALUE));
		assertEquals(1, ReworkFormStage.forDismantleCount(-1));
		assertEquals(1, ReworkFormStage.forDismantleCount(0));
		assertEquals(2, ReworkFormStage.forDismantleCount(1));
		assertEquals(3, ReworkFormStage.forDismantleCount(2));
		assertEquals(4, ReworkFormStage.forDismantleCount(3));
		assertEquals(5, ReworkFormStage.forDismantleCount(4));
		assertEquals(5, ReworkFormStage.forDismantleCount(5));
		assertEquals(5, ReworkFormStage.forDismantleCount(Integer.MAX_VALUE));
	}

	@Test
	void personalPursuitProgressUsesTheSameOneStepFormSequence() {
		assertEquals(1, ReworkFormStage.forResolvedChases(0));
		assertEquals(2, ReworkFormStage.forResolvedChases(1));
		assertEquals(3, ReworkFormStage.forResolvedChases(2));
		assertEquals(4, ReworkFormStage.forResolvedChases(3));
		assertEquals(5, ReworkFormStage.forResolvedChases(4));
		assertEquals(5, ReworkFormStage.forResolvedChases(99));
	}
}
