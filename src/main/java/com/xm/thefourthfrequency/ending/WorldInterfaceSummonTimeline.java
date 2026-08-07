package com.xm.thefourthfrequency.ending;

/**
 * The thirteen-second arrival, as one table both sides read.
 *
 * <p>The summon used to be five silent seconds: {@code SUMMONING} held for a hundred ticks, nothing
 * played, nothing happened, and then the fight started. This is the ceremony that replaces it -
 * descent, anchor chain, ground break, body reveal, eyes, roar, and the handover into combat - with
 * every beat stated once here so the server's step table and the client's presentation cannot
 * disagree about when anything happens.
 *
 * <p>Lives in {@code src/main} rather than on either side, for the same reason
 * {@code PursuitPresentationTimeline} does: both the server and the client need it, and a unit test
 * needs to be able to reach it.
 *
 * <h2>The audio alignment</h2>
 *
 * <p>The rise cue is 6.5 seconds long and its fifth layer is a single downbeat. Whether the entrance
 * reads as scored or as two unrelated things happening near each other depends entirely on where
 * that downbeat falls, and this is the one place that decides.
 *
 * <p>It falls on {@link #GROUND_BREAK}. The cue starts at tick 0, its downbeat is 5.5 seconds in,
 * and 5.5 seconds is 110 ticks - so the beat lands on exactly the tick the first shockwave goes out
 * and the ground opens. {@code WorldInterfaceSummonTimelineTest} parses the generator and asserts
 * the two numbers still agree; changing either alone is a test failure rather than a silent drift
 * that nobody would notice until they listened closely.
 *
 * <p><b>Every variant of the summon cue shares that duration and that downbeat.</b> This is a hard
 * requirement, not a convenience: Minecraft picks a variant at random from {@code sounds.json}, so
 * the server has no way to know which one it started. Variants may differ in timbre; they may not
 * differ in where the beat lands. The cue is therefore played exactly once, at tick 0, and the
 * later beats get their own distinct sounds rather than a second roll of the same group - a
 * re-triggered rise cue would put its downbeat somewhere the timeline has nothing scheduled.
 */
public final class WorldInterfaceSummonTimeline {
	/** The whole ceremony, in ticks. {@code SUMMON_DURATION_TICKS} must equal this. */
	public static final int TOTAL_TICKS = 260;

	/** The body appears far overhead and begins to fall. The rise cue starts here. */
	public static final int DESCENT_START = 0;
	/** The ten stability anchors fire skyward in sequence, one every {@link #ANCHOR_CHAIN_STEP}. */
	public static final int ANCHOR_CHAIN_START = 40;
	public static final int ANCHOR_CHAIN_STEP = 6;
	public static final int ANCHOR_CHAIN_COUNT = 10;
	/** First shockwave, and where the rise cue's downbeat lands. See the class note. */
	public static final int GROUND_BREAK = 110;
	/** The descent hands over to the drive: the mass resolves out of the sky. */
	public static final int BODY_REVEAL = 140;
	/** The three heads open their apertures. Second shockwave. */
	public static final int EYE_OPEN = 190;
	/** The roar, and the tick the music is allowed in behind it. */
	public static final int ROAR = 235;
	public static final int MUSIC_HANDOVER = 235;
	/** Combat begins: anchors become vulnerable and the first attack can be scheduled. */
	public static final int COMBAT = 260;

	/** The rise cue's length in ticks, and where its downbeat sits inside it. */
	public static final int RISE_CUE_TICKS = 130;
	public static final int RISE_CUE_DOWNBEAT_TICKS = 110;

	private WorldInterfaceSummonTimeline() {
	}

	/** Every beat, in order. Used by the tests and by the server's step table. */
	public static int[] beats() {
		return new int[]{DESCENT_START, ANCHOR_CHAIN_START, GROUND_BREAK, BODY_REVEAL,
				EYE_OPEN, ROAR, COMBAT};
	}

	/** When anchor {@code index} fires, in ticks from the start of the ceremony. */
	public static int anchorBeat(int index) {
		return ANCHOR_CHAIN_START + Math.clamp(index, 0, ANCHOR_CHAIN_COUNT - 1) * ANCHOR_CHAIN_STEP;
	}

	/** The last anchor's beat. The chain must finish before the ground breaks. */
	public static int anchorChainEnd() {
		return anchorBeat(ANCHOR_CHAIN_COUNT - 1);
	}

	/**
	 * Progress of the descent in [0, 1], for the client's atmosphere ramp.
	 *
	 * <p>Eased rather than linear: the body slows as it arrives, so the last few blocks of the fall
	 * take as long as the first thirty and the arrival has somewhere to settle into.
	 */
	public static float descentProgress(long age) {
		if (age <= DESCENT_START) return 0.0F;
		if (age >= BODY_REVEAL) return 1.0F;
		float linear = (age - DESCENT_START) / (float) (BODY_REVEAL - DESCENT_START);
		return 1.0F - (1.0F - linear) * (1.0F - linear);
	}

	/** Whether the music is allowed to be playing yet. Silence is the point until the roar. */
	public static boolean musicAllowed(long age) {
		return age >= MUSIC_HANDOVER;
	}
}
