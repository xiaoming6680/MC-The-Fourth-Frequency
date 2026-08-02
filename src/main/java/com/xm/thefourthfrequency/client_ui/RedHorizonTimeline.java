package com.xm.thefourthfrequency.client_ui;

/**
 * The shape of red_horizon.
 *
 * <p>What this replaces was a constant. The anomaly reached full strength on the frame it
 * started, held one unchanging value for six hundred ticks, and then fell away on a straight
 * line. Three things follow from that, and all three are what made a tier-four anomaly read as
 * a screen filter rather than as a place going wrong:</p>
 *
 * <ul>
 *   <li>Arriving on a single frame means the player never gets to doubt it. This whole mod is
 *       built on effects being deniable for a moment first, and an instant switch denies that
 *       moment outright.</li>
 *   <li>Thirty seconds of one value is not a phenomenon. It is a texture. Two slow modulators
 *       whose periods are not multiples of each other keep it alive without ever producing a
 *       beat the player can count - the same trick the signal beds use, for the same reason.</li>
 *   <li>The anomaly is called a <em>horizon</em>. It has to be worse at the horizon than
 *       overhead, which {@link #skyDomeShare()} handles: the fog colour that dominates the
 *       horizon band takes the full strength, and the sky dome above it takes far less. That
 *       produces a real vertical gradient out of two flat uniforms.</li>
 * </ul>
 *
 * <p>Pure arithmetic, no client types, so the whole envelope stays verifiable without a running
 * game - as with {@link AlphaLoadTimeline} and {@link GlitchImpactTimeline}.</p>
 */
public final class RedHorizonTimeline {
	/** The production duration. Anything shorter is an accelerated test run. */
	public static final int CANONICAL_DURATION_TICKS = 800;
	private static final int ENTER_TICKS = 70;
	private static final int CANONICAL_FADE_TICKS = 200;
	/** How much of the horizon's strength reaches the sky directly overhead. */
	private static final float SKY_DOME_SHARE = 0.42F;
	/** The world only starts closing in once the colour is already there. */
	private static final int FOG_LAG_TICKS = 60;
	private static final int FOG_ENTER_TICKS = 120;
	private static final float BREATH_AMOUNT = 0.08F;
	private static final float BREATH_PRIMARY_TICKS = 146.0F;
	private static final float BREATH_SECONDARY_TICKS = 234.0F;

	private RedHorizonTimeline() {
	}

	public static float skyDomeShare() {
		return SKY_DOME_SHARE;
	}

	/** Scaled down on accelerated runs, so a twenty-tick anomaly still gets a visible arrival. */
	public static int enterTicks(int durationTicks) {
		return Math.max(1, Math.min(ENTER_TICKS, durationTicks / 4));
	}

	public static int fadeTicks(int durationTicks) {
		return durationTicks == CANONICAL_DURATION_TICKS
				? CANONICAL_FADE_TICKS : Math.max(4, durationTicks / 4);
	}

	/**
	 * The master envelope: what fraction of full red is being applied at the horizon.
	 *
	 * <p>Both ends are smoothstepped rather than linear. A linear arrival has a corner in it at
	 * the moment it reaches full, and a corner is exactly the kind of edge that tells a player
	 * a piece of software just did something.</p>
	 */
	public static float horizonStrength(int elapsedTicks, int remainingTicks, int durationTicks) {
		if (durationTicks <= 0) return 0.0F;
		int enter = enterTicks(durationTicks);
		int fade = fadeTicks(durationTicks);
		float arrival = elapsedTicks >= enter ? 1.0F
				: smoothstep(Math.max(0, elapsedTicks) / (float) enter);
		float departure = remainingTicks >= fade ? 1.0F
				: smoothstep(Math.max(0, remainingTicks) / (float) fade);
		return Math.clamp(Math.min(arrival, departure) * breathing(elapsedTicks, remainingTicks, durationTicks),
				0.0F, 1.0F);
	}

	/** What reaches the sky directly overhead. See {@link #skyDomeShare()}. */
	public static float skyDomeStrength(int elapsedTicks, int remainingTicks, int durationTicks) {
		return horizonStrength(elapsedTicks, remainingTicks, durationTicks) * SKY_DOME_SHARE;
	}

	/**
	 * How far the world has closed in, which deliberately trails the colour.
	 *
	 * <p>Tinting the sky and collapsing the view distance off the same number spends both events
	 * at once. Staggered, the player gets told twice: the light is wrong, and only then does the
	 * distance start going.</p>
	 */
	public static float fogTightness(int elapsedTicks, int remainingTicks, int durationTicks) {
		if (durationTicks <= 0) return 0.0F;
		int lag = Math.min(FOG_LAG_TICKS, durationTicks / 5);
		int enter = Math.max(1, Math.min(FOG_ENTER_TICKS, durationTicks / 3));
		int fade = fadeTicks(durationTicks);
		float arrival = elapsedTicks <= lag ? 0.0F
				: smoothstep(Math.min(1.0F, (elapsedTicks - lag) / (float) enter));
		float departure = remainingTicks >= fade ? 1.0F
				: smoothstep(Math.max(0, remainingTicks) / (float) fade);
		return Math.clamp(Math.min(arrival, departure), 0.0F, 1.0F);
	}

	/**
	 * The slow swell that keeps the held minutes from being one flat value.
	 *
	 * <p>Always at or below 1, so it modulates the envelope downward and can never push the
	 * anomaly past full strength. It stops during the fade on purpose: something that has been
	 * breathing for thirty seconds going still is the clearest possible warning that it is
	 * about to leave, and it costs nothing to draw.</p>
	 *
	 * <p>Frozen at its last value rather than released to 1. Releasing it would step the
	 * strength <em>up</em> on the frame the fade begins, which is the one moment the anomaly
	 * must be visibly on its way out.</p>
	 */
	public static float breathing(int elapsedTicks, int remainingTicks, int durationTicks) {
		int fade = fadeTicks(durationTicks);
		int sampled = remainingTicks < fade
				? Math.max(0, durationTicks - fade) : Math.max(0, elapsedTicks);
		double primary = Math.sin(sampled * Math.PI * 2.0D / BREATH_PRIMARY_TICKS);
		double secondary = Math.sin(sampled * Math.PI * 2.0D / BREATH_SECONDARY_TICKS + 1.7D);
		return 1.0F - BREATH_AMOUNT * (float) ((2.0D - primary - secondary) / 4.0D);
	}

	private static float smoothstep(float phase) {
		float clamped = Math.clamp(phase, 0.0F, 1.0F);
		return clamped * clamped * (3.0F - 2.0F * clamped);
	}
}
