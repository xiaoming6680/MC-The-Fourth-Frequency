package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.TerminalSnapshotPayload;
import com.xm.thefourthfrequency.terminal.TerminalHandheldPose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * State-machine transitions for the held terminal's performance.
 *
 * <p>Only the transitions - {@code clientTick} needs a running client and belongs to the client
 * GameTest suite. Everything asserted here is reachable without one, and each case corresponds to
 * a way the real client can enter or leave the animation.</p>
 */
final class TerminalHandheldAnimatorTest {
	@BeforeEach
	void reset() {
		TerminalHandheldAnimator.resetForTesting();
	}

	private static TerminalSnapshotPayload snapshot() {
		return new TerminalSnapshotPayload(
				TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				0, 0, 0, 50, 0, 0, 0, false, 0, 0, false, 0,
				0, false, false, 100L, 0, 0, List.of(), "none", 0,
				List.of(), -1, "mine_logs", 3, 12,
				1, false, "minecraft:stone_axe", 1, false, false);
	}

	@Test
	void aSnapshotStartsTheDeviceComingUpFromRest() {
		assertEquals(TerminalHandheldAnimator.State.IDLE, TerminalHandheldAnimator.state());
		assertEquals(0.0D, TerminalHandheldAnimator.openness(), 1.0E-9D);
		assertEquals(1.0F, TerminalHandheldAnimator.fovScale(), 1.0E-6F);
		assertRestingInHand();

		TerminalHandheldAnimator.requestOpen(snapshot());
		assertEquals(TerminalHandheldAnimator.State.OPENING, TerminalHandheldAnimator.state());
		assertTrue(TerminalHandheldAnimator.isAnimating());
	}

	/**
	 * The server pushes a snapshot roughly once a second while the terminal is open - far more
	 * often while the tuning dial is settling. Restarting the phase on each would hold the device
	 * mid-travel forever and the screen would never arrive.
	 */
	@Test
	void repeatedSnapshotsRefreshWithoutRestartingTheTravel() {
		TerminalHandheldAnimator.requestOpen(snapshot());
		long started = TerminalHandheldAnimator.phaseStartedAtMillisForTesting();
		for (int repeat = 0; repeat < 5; repeat++) TerminalHandheldAnimator.requestOpen(snapshot());
		assertEquals(started, TerminalHandheldAnimator.phaseStartedAtMillisForTesting());
		assertEquals(TerminalHandheldAnimator.State.OPENING, TerminalHandheldAnimator.state());
	}

	@Test
	void closingFromRestIsANoOpSoAStrayCloseCannotAnimateAnIdleItem() {
		TerminalHandheldAnimator.requestClose();
		assertEquals(TerminalHandheldAnimator.State.IDLE, TerminalHandheldAnimator.state());
		assertFalse(TerminalHandheldAnimator.isAnimating());
		assertEquals(0.0D, TerminalHandheldAnimator.openness(), 1.0E-9D);
	}

	/**
	 * Death, a dimension change, a disconnect and a server-side refusal all land here. Each of them
	 * can arrive at any point in the performance, and each has to leave the item back in the hand
	 * with nothing pending.
	 */
	@Test
	void abortReturnsToRestFromEveryPhase() {
		TerminalHandheldAnimator.requestOpen(snapshot());
		TerminalHandheldAnimator.abort();
		assertEquals(TerminalHandheldAnimator.State.IDLE, TerminalHandheldAnimator.state());
		assertEquals(1.0F, TerminalHandheldAnimator.fovScale(), 1.0E-6F,
				"an aborted performance must give the camera its field of view back");
		assertRestingInHand();

		TerminalHandheldAnimator.requestOpen(snapshot());
		TerminalHandheldAnimator.requestClose();
		assertEquals(TerminalHandheldAnimator.State.CLOSING, TerminalHandheldAnimator.state());
		TerminalHandheldAnimator.abort();
		assertEquals(TerminalHandheldAnimator.State.IDLE, TerminalHandheldAnimator.state());
		assertRestingInHand();
	}

	/**
	 * The item is back where a carried terminal belongs.
	 *
	 * <p>Not "no transform at all": a resting terminal still carries its hand-held tilt and its
	 * idle breath. What has to be gone is everything the performance added - the travel toward the
	 * centre of the frame, the magnification, and the camera's lean.</p>
	 */
	private static void assertRestingInHand() {
		var pose = TerminalHandheldAnimator.presentation();
		var open = TerminalHandheldPose.presentation(1.0D, 0L);
		assertEquals(1.0F, TerminalHandheldAnimator.fovScale(), 1.0E-6F);
		assertTrue(pose.scale() < open.scale(), "the device must not stay magnified");
		// Compared as screen angles: the raised and resting poses sit at different depths, so the
		// raw heights understate how far it actually came down.
		assertTrue(pose.y() / Math.abs(pose.z()) < open.y() / Math.abs(open.z()) - 0.25D,
				"the device must have come back down");
		assertTrue(pose.pitch() < -15.0F, "the device must be laid back again, not still presented");
	}

	/**
	 * A reversal resumes from where the device actually is.
	 *
	 * <p>Reopening immediately after a close - a misclick, or a second right-click landing before
	 * the first close finished - must not snap the terminal back down to the hand before starting
	 * up again. The phase start is backdated instead, so openness is continuous across the turn.</p>
	 */
	@Test
	void reversingMidTravelIsContinuousRatherThanRestarting() {
		TerminalHandheldAnimator.requestOpen(snapshot());
		double whileOpening = TerminalHandheldAnimator.openness();
		TerminalHandheldAnimator.requestClose();
		assertEquals(whileOpening, TerminalHandheldAnimator.openness(), 0.05D,
				"closing must pick up from the openness the device had");

		TerminalHandheldAnimator.requestOpen(snapshot());
		assertEquals(TerminalHandheldAnimator.State.OPENING, TerminalHandheldAnimator.state());
		assertEquals(whileOpening, TerminalHandheldAnimator.openness(), 0.05D,
				"reopening must pick up from the openness the device had");
	}

	/**
	 * With something in the off hand there is no two-handed performance to play.
	 *
	 * <p>Vanilla draws the item on one arm and the mixin only tilts it, so the raise, the scale-up
	 * and the lens closing in have nothing to animate - playing their timings anyway bought a wait
	 * in front of the page with nothing to look at during it, and pulled the field of view in around
	 * an object that had not moved. The device stays at rest and the page is the whole event.
	 */
	@Test
	void aOneHandedCarryOpensStraightToThePageWithNoTravel() {
		TerminalHandheldAnimator.requestOpen(snapshot(), false);
		assertEquals(TerminalHandheldAnimator.State.OPEN, TerminalHandheldAnimator.state(),
				"there is no travel to wait through");
		assertFalse(TerminalHandheldAnimator.isAnimating());
		assertEquals(0.0D, TerminalHandheldAnimator.openness(), 1.0E-9D,
				"the device never leaves rest, so the pose must not report it raised");
		assertEquals(1.0F, TerminalHandheldAnimator.fovScale(), 1.0E-6F,
				"a lens that closes in around an object that did not move is the jump this avoids");

		// And it comes back down without a performance either.
		TerminalHandheldAnimator.requestClose();
		assertEquals(TerminalHandheldAnimator.State.IDLE, TerminalHandheldAnimator.state());
		assertEquals(0.0D, TerminalHandheldAnimator.openness(), 1.0E-9D);

		// The two-handed carry still gets its full travel.
		TerminalHandheldAnimator.requestOpen(snapshot(), true);
		assertEquals(TerminalHandheldAnimator.State.OPENING, TerminalHandheldAnimator.state());
	}
}
