package com.xm.thefourthfrequency.client_ui;

/**
 * Carries the score across the one exit that is not a change of situation: the run has ended, and
 * the player is on their way out to the title screen.
 *
 * <p>Every other transition this mod scores passes through a stretch of deliberate silence, and the
 * menu theme on the far side of it is correct - the player is between runs. Leaving a finished run
 * is not that. The story is over, the player is walking away from it, and cutting the track they
 * are walking away to in order to start the title-screen playlist ends the game on a jingle.</p>
 *
 * <p>Pure and on the common side so the whole handoff can be asserted without a client. It decides
 * only <em>when</em> the hold is in force; what that costs the rest of the music director - a
 * skipped fade, a spared sound category, an ordinary-play track under the title screen - lives in
 * {@code MusicDirector}.</p>
 */
public final class EndingScoreHandoff {
	public enum State {
		/** Ordinary scoring. The menu theme owns the title screen and a load is silent. */
		OFF,
		/**
		 * The run is over and the player is still in the world. The hold is in force from here, so
		 * that whenever they do leave, the exit is already covered - the disconnect gives no warning
		 * a tick beforehand, and the sound engine is torn down inside it.
		 */
		ARMED,
		/** Out at the title screen, with the track that was playing still playing. */
		HOLDING
	}

	private EndingScoreHandoff() {
	}

	/**
	 * The state one client tick later.
	 *
	 * @param runEnded whether an ending has been acknowledged and not yet spent. The caller clears
	 *                 it on the tick this returns {@link State#ARMED} from {@link State#OFF}, so a
	 *                 later return to the menu is scored normally again
	 * @param inWorld  a level and a player both present
	 * @param loading  a loading, connecting or progress screen is up - the gap between two places
	 * @param scorePlaying whether the carried track is still sounding. Consulted in
	 *                     {@link State#HOLDING} only - see that case
	 */
	public static State next(State state, boolean runEnded, boolean inWorld, boolean loading,
			boolean scorePlaying) {
		return switch (state) {
			// Waits for the world rather than arming the moment the ending resolves: the success
			// cleanup restores the resource packs, and that reload puts a loading overlay up. Arming
			// underneath it would score the reload with ordinary play, which is exactly the "menu
			// music leaking into a gap" this class exists to prevent, only pointed the other way.
			case OFF -> runEnded && inWorld && !loading ? State.ARMED : State.OFF;
			// The disconnect passes through a saving screen with no level, which is still the exit.
			// Only a settled screen with nothing loading behind it counts as having arrived.
			case ARMED -> !inWorld && !loading ? State.HOLDING : State.ARMED;
			// Joining anything at all ends the hold, including the world they just left. From that
			// point the ordinary rules apply again, fade-out on world entry included.
			//
			// And so does the carried track simply running out. The hold exists to let the player walk
			// away to the track they finished on; once that track has played, there is nothing left to
			// protect and the title screen is an ordinary menu again. Without this the state was
			// terminal - a player who sat on the title screen after winning stayed on the ordinary-play
			// playlist indefinitely, which paces itself for a world to be happening underneath it and
			// leaves four to six minutes between tracks. From the player's side the music simply
			// stopped after one song and the menu theme never came back.
			case HOLDING -> loading || inWorld || !scorePlaying ? State.OFF : State.HOLDING;
		};
	}

	/** Whether the ordinary-play score is being carried rather than handed back to the menu. */
	public static boolean holdsScore(State state) {
		return state != State.OFF;
	}

	/**
	 * Whether a disconnect happening right now must leave the music channel alone.
	 *
	 * <p>{@link State#ARMED} only. By {@link State#HOLDING} the exit is behind us, and a client that
	 * tore its level down again from there is doing something the hold has no claim on.</p>
	 */
	public static boolean keepsSoundsAcrossDisconnect(State state) {
		return state == State.ARMED;
	}
}
