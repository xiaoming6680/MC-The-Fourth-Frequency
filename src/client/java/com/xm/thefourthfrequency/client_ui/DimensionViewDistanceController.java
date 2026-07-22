package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.config.ConfigManager;
import com.xm.thefourthfrequency.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Locks view distance to 3/6/12 until a successful finale returns the player to the Overworld. */
public final class DimensionViewDistanceController {
	private static boolean initialized;
	private static boolean stateLoaded;
	private static boolean unlocked;
	private static boolean successfulReturnPending;

	private DimensionViewDistanceController() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		loadStateIfNeeded();
		ClientTickEvents.END_CLIENT_TICK.register(DimensionViewDistanceController::enforce);
	}

	public static synchronized boolean isLocked() {
		loadStateIfNeeded();
		return !unlocked;
	}

	public static int lockedChunks(Minecraft client) {
		if (client == null || client.level == null) {
			return DimensionViewDistancePolicy.OVERWORLD_CHUNKS;
		}
		return DimensionViewDistancePolicy.lockedChunks(
				client.level.dimension().identifier().toString());
	}

	public static int atmosphericChunks(String dimensionId, int vanillaChunks) {
		return isLocked() ? DimensionViewDistancePolicy.lockedChunks(dimensionId) : vanillaChunks;
	}

	public static synchronized void armUnlockAfterSuccessfulReturn() {
		loadStateIfNeeded();
		if (!unlocked) successfulReturnPending = true;
	}

	private static void enforce(Minecraft client) {
		if (client.options == null) return;
		if (successfulReturnPending && client.level != null
				&& Level.OVERWORLD.equals(client.level.dimension())) {
			completeSuccessfulReturn(client);
			return;
		}
		if (!isLocked()) return;
		int locked = lockedChunks(client);
		if (!client.options.renderDistance().get().equals(locked)) {
			client.options.renderDistance().set(locked);
		}
	}

	private static synchronized void completeSuccessfulReturn(Minecraft client) {
		if (!successfulReturnPending || client.level == null
				|| !Level.OVERWORLD.equals(client.level.dimension())) return;
		boolean persisted = ConfigManager.updateClientState(ModConfig.ClientState::unlockViewDistance);
		unlocked = true;
		successfulReturnPending = false;
		client.options.renderDistance().set(DimensionViewDistancePolicy.SUCCESS_RETURN_CHUNKS);
		client.options.save();
		if (!persisted) {
			TheFourthFrequency.LOGGER.error(
					"View distance was unlocked for this session but could not be persisted");
		}
	}

	private static synchronized void loadStateIfNeeded() {
		if (stateLoaded) return;
		unlocked = ConfigManager.loadClientState().viewDistanceUnlocked();
		stateLoaded = true;
	}

	public static synchronized void resetForTesting(Minecraft client) {
		ConfigManager.updateClientState(state -> new ModConfig.ClientState(
				state.alphaDowngradeComplete(), false));
		stateLoaded = true;
		unlocked = false;
		successfulReturnPending = false;
		if (client != null && client.options != null) enforce(client);
	}

	public static synchronized void resetForReplay(Minecraft client) {
		stateLoaded = true;
		unlocked = false;
		successfulReturnPending = false;
		if (client != null && client.options != null) enforce(client);
	}

	public static synchronized void reloadFromDiskForTesting() {
		stateLoaded = false;
		loadStateIfNeeded();
	}
}
