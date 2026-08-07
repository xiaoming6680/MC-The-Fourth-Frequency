package com.xm.thefourthfrequency.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The melee contract, as arithmetic.
 *
 * <p>"Can a player with a sword hit this thing" was previously a question you could only answer by
 * launching the game, and the answer had been no for the whole third form: the body hung out of
 * reach, the heads had no hit boxes at all, and the limbs were served by invisible columns that
 * spanned an entire tentacle whether or not the drawn limb was anywhere near you. All three of
 * those are geometry, so all three are checkable here.
 *
 * <p>The numbers this pins down are the ones an innocent-looking tweak breaks silently. Raising the
 * body clearance or shortening a neck does not fail to compile and does not look wrong in a
 * screenshot; it just quietly ends melee for that form.
 */
final class WorldInterfaceAnatomyTest {
	/**
	 * How high a standing player can land a hit, in blocks above their feet.
	 *
	 * <p>Eye height 1.62 plus vanilla's three-block reach, rounded down. A part whose hit box
	 * extends below this is swingable at.
	 */
	private static final double MELEE_REACH = 4.5D;

	@Test
	void eachFormExposesFourteenSixteenAndTwentyDamageableParts() {
		// The mass, three heads, two proxies on each of their necks, and one per drawn limb.
		assertEquals(14, WorldInterfaceAnatomy.hitPartCount(0));
		assertEquals(16, WorldInterfaceAnatomy.hitPartCount(1));
		assertEquals(20, WorldInterfaceAnatomy.hitPartCount(2));
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			assertEquals(1 + WorldInterfaceAnatomy.HEAD_COUNT
							+ WorldInterfaceAnatomy.HEAD_COUNT * WorldInterfaceAnatomy.NECK_SEGMENTS_PER_HEAD
							+ WorldInterfaceAnatomy.tentacleCount(form),
					WorldInterfaceAnatomy.hitPartCount(form), "form " + form);
		}
		// The proxy pool has to cover the widest form exactly: a spare proxy is a hittable volume
		// standing where nothing is drawn.
		assertEquals(WorldInterfaceAnatomy.HEAD_COUNT
						+ WorldInterfaceAnatomy.HEAD_COUNT * WorldInterfaceAnatomy.NECK_SEGMENTS_PER_HEAD + 10,
				WorldInterfacePartEntity.PART_COUNT);
		assertEquals(WorldInterfaceAnatomy.tentacleCount(2), WorldInterfacePartEntity.LIMB_PARTS);
		assertEquals(WorldInterfaceAnatomy.HEAD_COUNT * WorldInterfaceAnatomy.NECK_SEGMENTS_PER_HEAD,
				WorldInterfacePartEntity.NECK_PARTS);
	}

	/**
	 * The body flies, and it must clear the arena floor by the designed amount at every form.
	 *
	 * <p>This is the regression that put it underground. The clearance is authored against the
	 * <em>drawn</em> underside of the shell, and the entity is placed at {@code clearance -
	 * massBottomLift}; when that second term was derived from the kernel height and the mass's
	 * half-width instead of from the geometry, it overstated the gap by five to six and a half
	 * blocks and every one of them went into the floor.
	 */
	@Test
	void theBodyHangsClearOfTheFloorAtEveryForm() {
		double previous = 0.0D;
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			double underside = WorldInterfaceAnatomy.combatHoverHeight(form)
					+ WorldInterfaceAnatomy.massBottomLift(form);
			assertEquals(new double[]{8.0D, 14.0D, 18.0D}[form], underside, 1.0E-6D,
					"form " + form + ": the drawn underside must sit at the authored clearance");
			assertTrue(underside > MELEE_REACH,
					"form " + form + ": the body is supposed to fly, not to be swingable at");
			assertTrue(underside > previous, "the storm must climb with each form");
			previous = underside;
			// The bookkeeping origin itself has to stay above the floor, or the summon descent -
			// which lands on this very number - drives the body into the ground on arrival.
			assertTrue(WorldInterfaceAnatomy.combatHoverHeight(form) > 0.0D,
					"form " + form + ": the entity origin is below the arena floor");
		}
	}

	/**
	 * At every form the lowest head can be hit from the ground.
	 *
	 * <p>This is the promise that replaced "the boss grows legs". The body is allowed to float - it
	 * is a storm - but something real and visible has to stay inside a swing, and the centre skull is
	 * the most legible candidate because it is the largest lit thing on the model.
	 */
	@Test
	void theCentreHeadStaysInsideAMeleeSwingAtEveryForm() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			double centre = WorldInterfaceAnatomy.combatHoverHeight(form)
					+ WorldInterfaceAnatomy.headOffset(form, 0).y;
			// The hit box, not the drawn cube. WorldInterfacePartEntity gives every head a quarter
			// more than the skull it stands on, and a promise about what a sword reaches has to be
			// stated against the volume the sword actually tests.
			double bottom = centre - WorldInterfaceAnatomy.headHitRadius(form, 0);
			assertTrue(bottom <= MELEE_REACH,
					"form " + form + ": the centre head's hit box starts " + bottom
							+ " blocks up, past a melee swing at " + MELEE_REACH);
			// And the drawn box must clear the floor, not merely its centre.
			//
			// The centre being above zero was the wrong assertion: the skull is 3.3, 5.8 and 6.9
			// blocks across at the three forms, so a centre two blocks up put a third of the head
			// inside the island. That is what the body clearance was raised to fix, and this is the
			// property that keeps it fixed - the head hangs in the air, and its lowest point is still
			// the thing a player on the floor swings at.
			assertTrue(bottom > 0.0D, "form " + form + ": the centre head's drawn box reaches "
					+ (-bottom) + " blocks into the arena floor");
		}
	}

	/**
	 * The centre head clears the arena floor while it is <em>animating</em>, not merely at rest.
	 *
	 * <p>The rest pose was never the problem. Three live terms push the skull further down than the
	 * shape it is specified against - the structural sag, which grows as the pool drains and is worth
	 * a third of a radian on the lower neck; the tracking drift; and the action clips - and at the
	 * second and third forms that is several blocks on a chain that is already hanging. The drawn head
	 * spent the back half of the fight with its lower plating inside the island.
	 *
	 * <p>Swept over the whole wear range, a couple of full drift cycles, and the ends of the gaze
	 * range, because the worst case is a combination of all of them rather than the end of any one.
	 *
	 * <p>The margin is deliberately thin. It was three to four blocks when the body was first raised,
	 * and then two of those were spent bringing the whole storm back down so its heads land inside a
	 * swing. What is left is the promise itself - the drawn head stays out of the floor - rather than
	 * comfortable headroom, so any further lowering has to be measured rather than eyeballed.
	 */
	@Test
	void theCentreHeadClearsTheFloorThroughTheWholeAnimation() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			double lowest = Double.MAX_VALUE;
			for (int step = 0; step <= 20; step++) {
				float health = step / 20.0F;
				for (float gaze : new float[]{-1.2F, 0.0F, 0.6F, 1.2F}) {
					for (int tick = 0; tick <= 240; tick++) {
						var pose = WorldInterfaceRig.pose(form, tick, health, 0, 0L, gaze, gaze);
						// The underside of the jaw, which is the lowest thing the model draws on a
						// head and therefore the part that was going through the island. Measuring
						// the skull box instead hid it: the jaw hangs another unit and a half below.
						lowest = Math.min(lowest, WorldInterfaceAnatomy.combatHoverHeight(form)
								+ pose.offset(WorldInterfaceRig.HEAD_PREFIX[0] + "_jaw",
										0.0D, JAW_DEPTH_UNITS, 0.0D).y);
					}
				}
			}
			assertTrue(lowest > 0.35D, "form " + form + ": the animated centre head is drawn down to "
					+ lowest + " blocks - its jaw is inside the arena floor");
		}
	}

	/** Height of the jaw cube in model units, off {@code WorldInterfaceModel#buildHeadChain}. */
	private static final double JAW_DEPTH_UNITS = 2.8D;

	/** The centre head hangs lowest; the flanks are carried higher and are smaller. */
	@Test
	void theCentreHeadIsTheLowestAndLargestOfTheThree() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			double centre = WorldInterfaceAnatomy.headOffset(form, 0).y;
			for (int head = 1; head < WorldInterfaceAnatomy.HEAD_COUNT; head++) {
				assertTrue(WorldInterfaceAnatomy.headOffset(form, head).y > centre,
						"form " + form + " head " + head + " must sit above the centre head");
				assertTrue(WorldInterfaceAnatomy.headRadius(form, head)
								< WorldInterfaceAnatomy.headRadius(form, 0),
						"the flanking heads must be smaller than the centre head");
			}
		}
		// The flanks mirror each other exactly.
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			assertEquals(WorldInterfaceAnatomy.headOffset(form, 1).x,
					-WorldInterfaceAnatomy.headOffset(form, 2).x, 1.0E-6D);
			assertEquals(WorldInterfaceAnatomy.headOffset(form, 1).y,
					WorldInterfaceAnatomy.headOffset(form, 2).y, 1.0E-6D);
		}
	}

	/** The necks lengthen and splay with each morph: that is what the growth is. */
	@Test
	void necksExtendAndSplayWithEachForm() {
		double previousReach = 0.0D;
		double previousSplay = 0.0D;
		float previousScale = 0.0F;
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			// Reach measured in model units so form scale does not mask a neck that never grew.
			double reach = WorldInterfaceAnatomy.headLocalUnits(form, 0)[1]
					- WorldInterfaceAnatomy.headLocalUnits(0, 0)[1];
			double splay = Math.abs(WorldInterfaceAnatomy.headLocalUnits(form, 1)[0]);
			if (form > 0) {
				assertTrue(reach > previousReach, "form " + form + ": the necks must extend");
				assertTrue(splay > previousSplay, "form " + form + ": the heads must separate");
			}
			float scale = WorldInterfaceAnatomy.neckLengthScale(form);
			assertTrue(scale > 0.0F && scale < 3.0F,
					"form " + form + ": a neck length ratio must stay positive and bounded: " + scale);
			assertTrue(scale > previousScale, "form " + form + ": the neck length scale must grow");
			previousReach = reach;
			previousSplay = splay;
			previousScale = scale;
		}
		// The stretch is authored against the melee contract rather than derived from the reach
		// array, and it is never 1.0: an unstretched chain is twelve model units and the skull has
		// to travel roughly twice that to come down to a player.
		assertTrue(WorldInterfaceAnatomy.neckLengthScale(0) > 1.5F,
				"the first form's neck must already be stretched, or its head never reaches anyone");
	}

	/**
	 * Every neck carries hit boxes along its length, and they lie on the chain.
	 *
	 * <p>The necks are the longest thing the storm lowers into reach and they used to be scenery.
	 * The proxies are anchored by interpolating the drawn root and the drawn skull, so "on the neck"
	 * is true by construction; what is checked here is that they are spread along it, sized to it,
	 * and that at least one of them is swingable at from the floor.
	 */
	@Test
	void neckProxiesLieAlongTheChainAndAtLeastOneIsReachable() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			for (int head = 0; head < WorldInterfaceAnatomy.HEAD_COUNT; head++) {
				var skull = WorldInterfaceAnatomy.headOffset(form, head);
				var first = WorldInterfaceAnatomy.neckSegmentOffset(form, head, 0);
				var second = WorldInterfaceAnatomy.neckSegmentOffset(form, head, 1);
				assertTrue(first.distanceTo(skull) > second.distanceTo(skull),
						"form " + form + " head " + head + ": neck proxies must run root to skull");
				assertTrue(second.distanceTo(skull) > 0.0D,
						"the upper neck proxy must not collapse onto the skull box");
				assertTrue(WorldInterfaceAnatomy.neckSegmentHeight(form, head) > 1.0D
								&& WorldInterfaceAnatomy.neckSegmentRadius(form, head) > 0.2D,
						"a neck hit box must have a volume worth swinging at");
			}
			// Head box and neck boxes have to form one continuous surface. A gap between them is a
			// stretch of a limb that is plainly in front of the player and answers to nothing, which
			// is the failure the proxies exist to prevent - just moved further up the chain.
			for (int head = 0; head < WorldInterfaceAnatomy.HEAD_COUNT; head++) {
				double skullTop = WorldInterfaceAnatomy.headOffset(form, head).y
						+ WorldInterfaceAnatomy.headRadius(form, head) * 1.25D;
				double lowerNeckBottom = WorldInterfaceAnatomy.neckSegmentOffset(form, head, 1).y
						- WorldInterfaceAnatomy.neckSegmentHeight(form, head) * 0.5D;
				assertTrue(lowerNeckBottom <= skullTop,
						"form " + form + " head " + head + ": gap of "
								+ (lowerNeckBottom - skullTop) + " blocks between skull and neck");
				double lowerNeckTop = WorldInterfaceAnatomy.neckSegmentOffset(form, head, 1).y
						+ WorldInterfaceAnatomy.neckSegmentHeight(form, head) * 0.5D;
				double upperNeckBottom = WorldInterfaceAnatomy.neckSegmentOffset(form, head, 0).y
						- WorldInterfaceAnatomy.neckSegmentHeight(form, head) * 0.5D;
				assertTrue(upperNeckBottom <= lowerNeckTop,
						"form " + form + " head " + head + ": gap between the two neck proxies");
			}
			// And the bottom of that surface - the centre skull - stays inside a swing.
			double lowest = WorldInterfaceAnatomy.combatHoverHeight(form)
					+ WorldInterfaceAnatomy.headOffset(form, 0).y
					- WorldInterfaceAnatomy.headRadius(form, 0) * 1.25D;
			assertTrue(lowest <= MELEE_REACH,
					"form " + form + ": the lowest hittable point is " + lowest + " blocks up");
		}
	}

	/**
	 * The hit box for a head sits on the head the renderer draws.
	 *
	 * <p>The two used to be computed by different arithmetic - the proxy from the authored
	 * coordinate, the skull from a two-link chain whose scale carries both link offsets - and they
	 * disagreed by six blocks at the first form and sixteen at the third, which is the whole reason
	 * the heads could not be hit. They now come out of one function; this pins the property that
	 * function exists for, by checking the head ends up somewhere a player could plausibly reach
	 * rather than tens of blocks away from the body.
	 */
	@Test
	void theHeadSitsBelowTheBodyItHangsFrom() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			double head = WorldInterfaceAnatomy.combatHoverHeight(form)
					+ WorldInterfaceAnatomy.headOffset(form, 0).y;
			double underside = WorldInterfaceAnatomy.combatHoverHeight(form)
					+ WorldInterfaceAnatomy.massBottomLift(form);
			assertTrue(head < underside,
					"form " + form + ": the centre head must hang below the mass, not inside it");
		}
	}

	/**
	 * A limb's hit box covers the limb's last link, and stands where that link is drawn.
	 *
	 * <p>This used to assert something else, and the something else was fiction. The box was a column
	 * whose bottom was clamped to the arena floor on the strength of a comment claiming the tentacles
	 * "reach the arena floor and carry on through it" - and the drawn ones do not. The bone chain the
	 * model builds ends some four to seven blocks under the body, which at third form leaves the tips
	 * a good ten blocks over a player's head. So the old box stood in air where nothing was drawn,
	 * for the same reason and in the same direction as every other proxy: it was placed from a
	 * description of the limb rather than from the limb.
	 *
	 * <p><b>The consequence is a real change to the fight and is stated here on purpose:</b> the limbs
	 * are no longer swingable at from the ground at any form. What a player on the floor melees is the
	 * centre head, which {@link #theCentreHeadStaysInsideAMeleeSwingAtEveryForm} still pins inside a
	 * swing; the limbs answer to anything ranged, and to anyone who gets above them. If they are meant
	 * to be a melee target the model has to grow them, and that is a modelling decision rather than
	 * something the hit boxes may keep pretending.
	 */
	@Test
	void limbHitBoxesCoverTheDrawnTipLinkAndNotTheWholeLimb() {
		for (int form = 0; form < WorldInterfaceAnatomy.FORM_COUNT; form++) {
			var pose = WorldInterfaceRig.restPose(form);
			for (int limb = 0; limb < WorldInterfaceAnatomy.tentacleCount(form); limb++) {
				var near = pose.tendrilTipOffset(limb, false);
				var far = pose.tendrilTipOffset(limb, true);
				double covered = near.distanceTo(far);
				assertTrue(covered > 1.0D,
						"form " + form + " limb " + limb + ": the tip link has no length to hit");
				// The whole limb is three links; covering one of them is the point.
				double fullLimb = WorldInterfaceAnatomy.tentacleRootLift(form) + Math.abs(far.y);
				assertTrue(covered < fullLimb * 0.6D,
						"form " + form + " limb " + limb + ": a box covering " + covered + " of a "
								+ fullLimb + " block limb is the invisible column again");
				// And it hangs below the body it comes off rather than inside it.
				assertTrue(far.y < 0.0D,
						"form " + form + " limb " + limb + ": the tip is above the entity origin");
			}
		}
	}

	/** Head and limb positions must turn with the body, or they detach when the boss faces away. */
	@Test
	void partGeometryFollowsTheBodyFacing() {
		var offset = WorldInterfaceAnatomy.headOffset(2, 0);
		var straight = WorldInterfaceAnatomy.rotate(offset, 0.0F);
		var turned = WorldInterfaceAnatomy.rotate(offset, 180.0F);
		assertEquals(straight.y, turned.y, 1.0E-6D, "a yaw must not move a part vertically");
		assertEquals(-straight.z, turned.z, 1.0E-4D, "half a turn must mirror the forward offset");
		assertEquals(-straight.x, turned.x, 1.0E-4D, "half a turn must mirror the lateral offset");

		// Limbs are spread around the body rather than stacked on one flank, so the storm is anchored
		// on every side. Measured on the drawn chain: they no longer orbit on a timer of their own.
		var pose = WorldInterfaceRig.restPose(2);
		double leftmost = Double.MAX_VALUE;
		double rightmost = -Double.MAX_VALUE;
		for (int limb = 0; limb < WorldInterfaceAnatomy.tentacleCount(2); limb++) {
			double x = pose.tendrilTipOffset(limb, true).x;
			leftmost = Math.min(leftmost, x);
			rightmost = Math.max(rightmost, x);
		}
		assertTrue(leftmost < 0.0D && rightmost > 0.0D,
				"limbs must hang on both flanks, not cluster on one");
	}
}
