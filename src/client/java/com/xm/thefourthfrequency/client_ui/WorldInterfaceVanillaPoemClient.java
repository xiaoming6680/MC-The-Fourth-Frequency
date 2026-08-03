package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.networking.PoemStartS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Binds one server-authorized World Interface outcome to the next vanilla WinScreen. */
public final class WorldInterfaceVanillaPoemClient {
	private static final Identifier SUCCESS_ZH_CN = poem("end_success_zh_cn.txt");
	private static final Identifier FAILURE_ZH_CN = poem("end_failure_zh_cn.txt");
	private static final Identifier SUCCESS_EN_US = poem("end_success_en_us.txt");
	private static final Identifier FAILURE_EN_US = poem("end_failure_en_us.txt");
	private static final Identifier CREDITS_ZH_CN = poem("credits_zh_cn.json");
	private static final Identifier CREDITS_EN_US = poem("credits_en_us.json");
	private static PoemStartS2C pending;
	private static WorldInterfaceProtocol.Outcome scoredOutcome = WorldInterfaceProtocol.Outcome.NONE;

	private WorldInterfaceVanillaPoemClient() {
	}

	public static synchronized void arm(PoemStartS2C poem) {
		pending = poem;
		scoredOutcome = poem.outcome();
	}

	/**
	 * The outcome whose ending track the score should be carrying, or {@code NONE} while the run is
	 * still being played.
	 *
	 * <p>The server sends the poem the instant a player steps into the exit portal, which is the
	 * moment both endings were authored to start scoring. This is deliberately a latch rather than a
	 * read of the live projection: {@link #claim} hands the poem to the screen and the encounter
	 * session is torn down partway through the ending, so neither survives long enough to answer
	 * "which of the two endings is this" for as long as the music has to keep playing.</p>
	 */
	public static synchronized WorldInterfaceProtocol.Outcome scoredOutcome() {
		return scoredOutcome;
	}

	public static synchronized PoemStartS2C claim(boolean includesPoem) {
		if (!includesPoem || pending == null) return null;
		PoemStartS2C claimed = pending;
		pending = null;
		return claimed;
	}

	public static synchronized void clearPending() {
		pending = null;
		releaseScore();
	}

	private static synchronized void releaseScore() {
		scoredOutcome = WorldInterfaceProtocol.Outcome.NONE;
	}

	public static Identifier poemResource(PoemStartS2C poem) {
		boolean chinese = chineseSelected();
		return switch (poem.outcome()) {
			case SUCCESS -> chinese ? SUCCESS_ZH_CN : SUCCESS_EN_US;
			case FAILURE -> chinese ? FAILURE_ZH_CN : FAILURE_EN_US;
			case NONE -> throw new IllegalArgumentException("A vanilla End poem requires a resolved outcome");
		};
	}

	/**
	 * The credits roll that follows this mod's poem. Both outcomes credit the same production, so the
	 * only axis here is language.
	 */
	public static Identifier creditsResource() {
		return chineseSelected() ? CREDITS_ZH_CN : CREDITS_EN_US;
	}

	private static boolean chineseSelected() {
		return Minecraft.getInstance().getLanguageManager().getSelected().startsWith("zh_");
	}

	private static Identifier poem(String path) {
		return Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "texts/" + path);
	}

	public static void finish(PoemStartS2C poem, WorldInterfaceProtocol.PoemCompletion completion,
			Runnable vanillaFinish, Runnable retry) {
		boolean accepted = WorldInterfaceEndingClient.onPoemAcknowledged(poem, completion, () -> {
			boolean sent = WorldInterfaceClientNetworking.sendPoemComplete(poem, completion);
			if (!sent) {
				TheFourthFrequency.LOGGER.error("Could not acknowledge the World Interface vanilla End poem");
			}
			if (poem.outcome() == WorldInterfaceProtocol.Outcome.SUCCESS) {
				if (sent) DimensionViewDistanceController.armUnlockAfterSuccessfulReturn();
				// A won run hands the world back, so the ending track stops owning the score here and
				// the overworld playlist resumes behind the return. A lost one never gets that far:
				// the failure presentation is the remainder of the session, and it keeps its music.
				releaseScore();
			}
			// Packet ordering is intentional: the durable poem ACK reaches the server before PERFORM_RESPAWN.
			vanillaFinish.run();
		});
		if (!accepted) retry.run();
	}
}
