package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boot splashes the ending retires, and the ones it must not.
 *
 * <p>The withheld set is a list of raw indices into a translation catalogue. Nothing else in the
 * build would notice if it drifted past the end of that catalogue, emptied the pool, or started
 * handing back one of the entries it exists to suppress - the failure would be a title screen
 * asking a question the player has already answered, months later.
 */
class MenuErosionSplashTest {
	private static final MenuErosionState.Stage BOOT = MenuErosionState.Stage.BOOT;

	@Test
	void beforeTheEndingEverySplashIsStillReachable() {
		Set<Integer> seen = new HashSet<>();
		for (int seed = 0; seed < BOOT.splashCount() * 4; seed++) {
			int index = MenuErosionState.splashIndex(BOOT, false, seed);
			assertTrue(index >= 0 && index < BOOT.splashCount(), "index out of catalogue: " + index);
			seen.add(index);
		}
		assertEquals(BOOT.splashCount(), seen.size(),
				"an unfinished save must still be able to draw any of them");
	}

	@Test
	void afterTheEndingTheWithheldOnesNeverComeUp() {
		Set<Integer> seen = new HashSet<>();
		// Negative seeds included: the session seed is derived from nanoTime and can be negative,
		// and a modulo that forgets that is how a pool lookup goes out of bounds in the field.
		for (int seed = -400; seed < 400; seed++) {
			int index = MenuErosionState.splashIndex(BOOT, true, seed);
			assertTrue(index >= 0 && index < BOOT.splashCount(), "index out of catalogue: " + index);
			assertFalse(MenuErosionState.withheldAfterEnding(index),
					"a finished save drew a retired splash: " + index);
			seen.add(index);
		}
		// Everything that was not withheld is still reachable, so the ending narrows the catalogue
		// rather than collapsing it onto one line.
		for (int index = 0; index < BOOT.splashCount(); index++) {
			if (MenuErosionState.withheldAfterEnding(index)) continue;
			assertTrue(seen.contains(index), "a surviving splash became unreachable: " + index);
		}
	}

	/**
	 * The pool has to keep enough in it to still read as a rotation.
	 *
	 * <p>Just over half the catalogue is withheld, and that is the authored choice rather than an
	 * accident - the eleven that go are the ones that ask a question the ending has answered. What
	 * has to hold is that what remains is still a rotation and not one line repeating.
	 */
	@Test
	void theEndingLeavesAWorkableCatalogueBehind() {
		int surviving = 0;
		for (int index = 0; index < BOOT.splashCount(); index++) {
			if (!MenuErosionState.withheldAfterEnding(index)) surviving++;
		}
		assertEquals(10, surviving);
		assertTrue(surviving >= 6, "too few left for the title screen to still vary between sessions");
		assertFalse(MenuErosionState.withheldAfterEnding(BOOT.splashCount()),
				"the withheld set must not point past the catalogue");
	}

	/** Stages that have no catalogue of their own are unaffected either way. */
	@Test
	void onlyTheBootCatalogueIsNarrowed() {
		for (MenuErosionState.Stage stage : MenuErosionState.Stage.values()) {
			if (stage == BOOT || stage.splashCount() == 0) continue;
			for (int seed = 0; seed < 50; seed++) {
				assertEquals(MenuErosionState.splashIndex(stage, false, seed),
						MenuErosionState.splashIndex(stage, true, seed));
			}
		}
	}
}
