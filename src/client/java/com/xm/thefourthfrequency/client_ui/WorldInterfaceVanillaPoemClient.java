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
	private static PoemStartS2C pending;

	private WorldInterfaceVanillaPoemClient() {
	}

	public static synchronized void arm(PoemStartS2C poem) {
		pending = poem;
	}

	public static synchronized PoemStartS2C claim(boolean includesPoem) {
		if (!includesPoem || pending == null) return null;
		PoemStartS2C claimed = pending;
		pending = null;
		return claimed;
	}

	public static synchronized void clearPending() {
		pending = null;
	}

	public static Identifier poemResource(PoemStartS2C poem) {
		String language = Minecraft.getInstance().getLanguageManager().getSelected();
		boolean chinese = language.startsWith("zh_");
		return switch (poem.outcome()) {
			case SUCCESS -> chinese ? SUCCESS_ZH_CN : SUCCESS_EN_US;
			case FAILURE -> chinese ? FAILURE_ZH_CN : FAILURE_EN_US;
			case NONE -> throw new IllegalArgumentException("A vanilla End poem requires a resolved outcome");
		};
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
			if (sent && poem.outcome() == WorldInterfaceProtocol.Outcome.SUCCESS) {
				DimensionViewDistanceController.armUnlockAfterSuccessfulReturn();
			}
			// Packet ordering is intentional: the durable poem ACK reaches the server before PERFORM_RESPAWN.
			vanillaFinish.run();
		});
		if (!accepted) retry.run();
	}
}
