package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.entity.WorldInterfaceAnatomy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The storm's combat station must stay with the island rather than with the player's feet.
 *
 * <p>The anchors sit on top of the ten obsidian spikes, so breaking one means climbing. While the
 * station was written straight off the player's Y, the body was a ceiling pinned a fixed distance
 * above their head and the climb took it along - a player arrived at the anchor already underneath
 * thirty-three blocks of storm. These assertions are about that endpoint: how much higher than the
 * body a player gets by climbing, not how the number is arrived at.
 */
class WorldInterfaceStationHeightTest {
	private static final double EPSILON = 0.000_001D;
	/** The island itself; the real value is whatever the arena centre reports. */
	private static final double FLOOR = 64.0D;
	/** Vanilla generates the end spikes between roughly 12 and 36 blocks above the island. */
	private static final double SHORTEST_SPIKE = 12.0D;
	private static final double TALLEST_SPIKE = 36.0D;

	@Test
	void aPlayerOnTheGroundIsUnaffected() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			double hover = WorldInterfaceAnatomy.combatHoverHeight(form);
			assertEquals(FLOOR + hover,
					WorldInterfacePolicy.combatStationY(FLOOR, FLOOR, hover), EPSILON,
					"the station over flat ground is the form's own clearance and nothing else");
		}
	}

	/** Small rises still register, or the storm would ignore a player standing on the altar. */
	@Test
	void shortClimbsAreStillFollowed() {
		double hover = WorldInterfaceAnatomy.combatHoverHeight(0);
		double step = WorldInterfacePolicy.MAX_VERTICAL_FOLLOW / 2.0D;
		assertEquals(FLOOR + step + hover,
				WorldInterfacePolicy.combatStationY(FLOOR, FLOOR + step, hover), EPSILON);
	}

	/**
	 * The endpoint that matters: climbing has to actually buy the player something.
	 *
	 * <p>Under the old rule the underside of the body sat a fixed {@code clearance} above the
	 * player's feet however high they climbed, so the answer to "it is on top of me" was the same at
	 * the top of a spike as at the bottom. Now the underside stops rising once the cap is reached,
	 * and every block above that is a block of daylight.
	 */
	@Test
	void climbingASpikeBuysClearanceItPreviouslyDidNot() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			double hover = WorldInterfaceAnatomy.combatHoverHeight(form);
			double clearance = hover + WorldInterfaceAnatomy.massBottomLift(form);
			double previous = Double.NEGATIVE_INFINITY;
			for (double spike = SHORTEST_SPIKE; spike <= TALLEST_SPIKE; spike += 1.0D) {
				double playerY = FLOOR + spike;
				double underside = WorldInterfacePolicy.combatStationY(FLOOR, playerY, hover)
						+ WorldInterfaceAnatomy.massBottomLift(form);
				double headroom = playerY - underside;

				// The old rule's answer, which never varied with the climb.
				assertTrue(headroom > -clearance,
						"climbing must beat standing on the ground, form " + form + " spike " + spike);
				assertTrue(headroom > previous,
						"every block climbed must be a block gained, form " + form + " spike " + spike);
				previous = headroom;

				// However high the player goes, the body stops at the cap over the island.
				assertTrue(underside <= FLOOR + WorldInterfacePolicy.MAX_VERTICAL_FOLLOW + clearance
								+ EPSILON,
						"the body must stay with the island, form " + form + " spike " + spike);
			}
			// On a full-height spike the player is out from under it outright, in every form.
			double tallUnderside = WorldInterfacePolicy.combatStationY(
					FLOOR, FLOOR + TALLEST_SPIKE, hover) + WorldInterfaceAnatomy.massBottomLift(form);
			assertTrue(FLOOR + TALLEST_SPIKE > tallUnderside,
					"a full-height spike must clear the body outright, form " + form);
		}
	}

	/** A player below the island is not somewhere to descend to; that would sink the body. */
	@Test
	void theStationNeverFollowsAPlayerDownwards() {
		double hover = WorldInterfaceAnatomy.combatHoverHeight(0);
		for (double drop = 1.0D; drop <= 40.0D; drop += 1.0D) {
			assertEquals(FLOOR + hover,
					WorldInterfacePolicy.combatStationY(FLOOR, FLOOR - drop, hover), EPSILON,
					"a player who fell off the island must not pull the station into the ground");
		}
	}

	/** The skyhold lift rides on top of the follow rather than being capped with it. */
	@Test
	void theSkyholdClimbIsUnaffectedByTheFollowCap() {
		double hover = WorldInterfaceAnatomy.combatHoverHeight(1)
				+ WorldInterfaceSkyholdPolicy.CEILING_LIFT;
		assertEquals(FLOOR + hover,
				WorldInterfacePolicy.combatStationY(FLOOR, FLOOR, hover), EPSILON);
		assertTrue(WorldInterfacePolicy.combatStationY(FLOOR, FLOOR + TALLEST_SPIKE, hover)
						> FLOOR + WorldInterfaceSkyholdPolicy.CEILING_LIFT,
				"the whole point of the window is that it is out of reach of anything to stand on");
	}
}
