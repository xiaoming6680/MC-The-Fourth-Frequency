package com.xm.thefourthfrequency.client_ui;

/**
 * The sampling rule that keeps the encounter's distant beams visually stable.
 *
 * <p>Both live here rather than inside the renderer because both are pure arithmetic over numbers
 * the renderer happens to have, and both failed silently: a beam that blinks looks like a driver
 * problem rather than like a wrong branch, so neither had anything that would fail if it came back.
 *
 * <p>Nothing here touches Minecraft. {@code WorldInterfaceBeamBatchRenderer} is the only caller.
 */
public final class WorldInterfaceBeamPolicy {
	/**
	 * Radians of half-width a beam is never allowed to fall below, per block of distance.
	 *
	 * <p>The anchor tethers are authored at a tenth of a block across and run the length of the
	 * arena. At the far end that is under two pixels wide, and an additive line that thin has no
	 * stable pixel coverage: sub-pixel camera movement switches it on and off along its own length
	 * between one frame and the next, so the beam crawls instead of hanging. Depth makes it worse
	 * where a tether grazes a spike, because a line a pixel wide has no margin to win a depth
	 * comparison with.
	 *
	 * <p>Chosen for roughly two and a half pixels at 1080p and the default field of view, which is
	 * where the crawl stops. Deliberately not derived from the live window size: this is a lower
	 * bound in world units, and a beam that changed thickness when the player resized the window or
	 * moved a slider would be a worse artefact than the one being fixed. It is an angular bound, so
	 * a larger display spends its extra pixels on the same apparent thread rather than a fatter one.
	 *
	 * <p>Only meaningful together with {@link #stableAlpha}. Widening a beam without dimming it
	 * trades a thread that flickers for a cord that does not, which is a different wrong picture.
	 */
	public static final double MIN_ANGULAR_HALF_WIDTH = 0.0016D;

	private WorldInterfaceBeamPolicy() {
	}

	/**
	 * The half-width a beam is actually drawn at, which is the authored width or the distance floor,
	 * whichever is larger.
	 *
	 * <p>Close up the authored width always wins, so nothing about the laser, the sky lance or a
	 * tether the player is standing under changes.
	 */
	public static double stableHalfWidth(double authoredHalfWidth, double distance) {
		if (!Double.isFinite(authoredHalfWidth) || authoredHalfWidth < 0.0D) {
			throw new IllegalArgumentException("Authored half-width must be finite and non-negative");
		}
		if (!Double.isFinite(distance) || distance < 0.0D) {
			throw new IllegalArgumentException("Distance must be finite and non-negative");
		}
		return Math.max(authoredHalfWidth, distance * MIN_ANGULAR_HALF_WIDTH);
	}

	/**
	 * The alpha a widened beam is drawn at, so that making it wide enough to hold still does not
	 * also make it thicker.
	 *
	 * <p>The floor is a sampling fix, not an art change: the tether is supposed to read as a thread
	 * across the arena, and a thread three times its authored width is a cord. Scaling the alpha
	 * down by exactly the factor the width went up by keeps brightness times width constant, which
	 * is the quantity the eye integrates at this size - so the beam looks the same as before and
	 * simply has enough pixels under it to be sampled consistently from one frame to the next.
	 *
	 * <p>This is the same trade every thin-line renderer makes, and it is why the two constants have
	 * to move together.
	 */
	public static int stableAlpha(int authoredAlpha, double authoredHalfWidth, double drawnHalfWidth) {
		if (authoredAlpha < 0 || authoredAlpha > 255) {
			throw new IllegalArgumentException("Alpha must be within [0, 255]");
		}
		if (!Double.isFinite(authoredHalfWidth) || authoredHalfWidth < 0.0D
				|| !Double.isFinite(drawnHalfWidth) || drawnHalfWidth < 0.0D) {
			throw new IllegalArgumentException("Half-widths must be finite and non-negative");
		}
		if (drawnHalfWidth <= authoredHalfWidth) return authoredAlpha;
		return Math.clamp(Math.round(authoredAlpha * (authoredHalfWidth / drawnHalfWidth)), 0, 255);
	}

}
