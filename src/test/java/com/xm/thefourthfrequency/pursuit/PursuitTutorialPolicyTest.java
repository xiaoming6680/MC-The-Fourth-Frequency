package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitTutorialPolicyTest {
	@Test
	void requiresDemonstrationAndWarningBeforeAFormalPursuit() {
		int demo = PursuitTutorialPolicy.mark(0, 2);
		assertEquals(PursuitTutorialPolicy.Status.DEMONSTRATED,
				PursuitTutorialPolicy.status(demo, 0, 0, 2));
		assertFalse(PursuitTutorialPolicy.readyForFormalPursuit(demo, 0, 2));

		int warning = PursuitTutorialPolicy.mark(0, 2);
		assertTrue(PursuitTutorialPolicy.readyForFormalPursuit(demo, warning, 2));
		assertEquals(PursuitTutorialPolicy.Status.READY,
				PursuitTutorialPolicy.status(demo, warning, 0, 2));

		int archive = PursuitTutorialPolicy.mark(0, 2);
		assertEquals(PursuitTutorialPolicy.Status.ARCHIVED,
				PursuitTutorialPolicy.status(demo, warning, archive, 2));
	}

	@Test
	void ignoresInvalidFormsAndMasksUnknownBits() {
		assertEquals(0, PursuitTutorialPolicy.formMask(0));
		assertEquals(0, PursuitTutorialPolicy.formMask(6));
		assertEquals(PursuitTutorialPolicy.KNOWN_FORM_MASK,
				PursuitTutorialPolicy.mark(Integer.MAX_VALUE, 1));
	}
}
