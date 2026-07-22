package com.xm.thefourthfrequency.client_ui;

import com.mojang.blaze3d.platform.IconSet;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.config.ConfigManager;
import com.xm.thefourthfrequency.meta_api.MetaController;
import com.xm.thefourthfrequency.meta_windows.WindowsEndingMetaTransaction;
import com.xm.thefourthfrequency.networking.PoemStartS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Coordinates local cleanup, pack restoration, durable failure locking and normal shutdown. */
public final class WorldInterfaceEndingClient {
	private static final Identifier FAILURE_IMAGE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/gui/ending/world_interface_failure.png");
	private static final int FAILURE_TEXTURE_WIDTH = 2560;
	private static final int FAILURE_TEXTURE_HEIGHT = 1600;
	private static final int FALLBACK_MINIMUM_TICKS = 160;
	private static boolean initialized;
	private static boolean recoveryRequested;
	private static boolean replayResetRequested;
	private static boolean recoveryStarted;
	private static Mode mode = Mode.IDLE;
	private static WindowLease presentationWindow;
	private static int modeTicks;
	private static boolean fallbackMayShutdown;
	private static boolean disconnectIssued;
	private static boolean failureLanHost;
	private static UUID failureEncounterId;

	private WorldInterfaceEndingClient() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		FailureMenuLockState.initialize();
		WorldInterfaceResourcePackLease.initialize();
		if (FailureMenuLockState.locked() || WindowsEndingMetaTransaction.hasPendingTransaction()
				|| WorldInterfaceResourcePackLease.presentationRetired()) {
			Minecraft client = Minecraft.getInstance();
			if (client.options != null) {
				WorldInterfaceResourcePackLease.adoptExistingAutomaticSelection(
						List.copyOf(client.options.resourcePacks));
			}
			AlphaLoadSessionController.retirePresentation();
		}
		recoveryRequested = Boolean.getBoolean("thefourthfrequency.safeMode");
		ClientTickEvents.END_CLIENT_TICK.register(WorldInterfaceEndingClient::tick);
	}

	public static void observeEncounter(Minecraft client, WorldInterfaceSnapshotS2C snapshot) {
		if (presentationWindow != null || client.getWindow() == null) return;
		switch (snapshot.stage()) {
			case SUMMONING, PHASE_1, PHASE_2, PHASE_3, SUCCESS_RESOLUTION,
					FAILURE_RESOLUTION, PORTAL_OPEN -> presentationWindow = WindowLease.capture(client);
			default -> { }
		}
	}

	public static boolean onPoemAcknowledged(PoemStartS2C poem,
			WorldInterfaceProtocol.PoemCompletion completion, Runnable completionAck) {
		Minecraft client = Minecraft.getInstance();
		if (mode != Mode.IDLE || client == null || completionAck == null) return false;
		AlphaLoadSessionController.retirePresentation();
		cleanupRuntime(client);
		if (poem.outcome() == WorldInterfaceProtocol.Outcome.SUCCESS) {
			mode = Mode.SUCCESS_RESTORING;
			WorldInterfaceResourcePackLease.restoreAsync(client).whenComplete((restored, failure) ->
					client.execute(() -> {
						if (failure != null || !Boolean.TRUE.equals(restored)) {
							TheFourthFrequency.LOGGER.error("Success cleanup could not restore resource packs", failure);
						}
						restorePresentationWindow(client);
						if (!FailureMenuLockState.lock(poem.encounterId(), poem.outcome(), poem.worldId(), client)) {
							TheFourthFrequency.LOGGER.error("Success ending could not persist its local replay lock");
						}
						completionAck.run();
						mode = Mode.IDLE;
					}));
			return true;
		}

		restorePresentationWindow(client);
		failureLanHost = isPublishedLanHost(client);
		failureEncounterId = poem.encounterId();
		boolean durableLock = FailureMenuLockState.lock(poem.encounterId(), poem.outcome(), poem.worldId(), client);
		if (!durableLock) {
			TheFourthFrequency.LOGGER.error("Failure ending could not persist its local menu lock");
		}
		mode = Mode.FAILURE_PRESENTING;
		modeTicks = 0;
		fallbackMayShutdown = false;
		client.setScreen(new FailureFallbackScreen(true));
		if (!durableLock) {
			if (client.screen instanceof FailureFallbackScreen fallback) fallback.systemFailed();
			fallbackMayShutdown = true;
			completionAck.run();
			return true;
		}
		if (WindowsEndingMetaTransaction.isWindows() && MetaController.enabled()) {
			WindowsEndingMetaTransaction.applyAsync(client, poem.encounterId()).whenComplete((applied, failure) ->
					client.execute(() -> {
						if (failure == null && Boolean.TRUE.equals(applied)) {
							completionAck.run();
							concludeFailure();
						} else {
							WindowsEndingMetaTransaction.restoreAsync(client).whenComplete((restored, restoreFailure) ->
									client.execute(() -> {
										fallbackMayShutdown = true;
										if (client.screen instanceof FailureFallbackScreen fallback) fallback.systemFailed();
										completionAck.run();
									}));
						}
					}));
		} else {
			fallbackMayShutdown = true;
			if (client.screen instanceof FailureFallbackScreen fallback) fallback.systemFailed();
			completionAck.run();
		}
		return true;
	}

	public static void requestRecovery() {
		replayResetRequested = false;
		recoveryRequested = true;
	}

	public static void requestRecoveryConfirmation(Minecraft client) {
		if (client == null || !FailureMenuLockState.locked() || recoveryStarted) return;
		Screen returnScreen = client.screen;
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (!confirmed) {
				client.setScreen(returnScreen);
				return;
			}
			client.setScreen(new RecoveryProgressScreen(returnScreen));
			replayResetRequested = true;
			recoveryRequested = true;
		}, Component.translatable("screen.thefourthfrequency.ending_reset.title"),
				Component.translatable("screen.thefourthfrequency.ending_reset.body"),
				Component.translatable("screen.thefourthfrequency.ending_reset.confirm"),
				CommonComponents.GUI_CANCEL));
	}

	public static boolean replayResetAvailable() {
		return FailureMenuLockState.locked() && switch (mode) {
			case IDLE, LAN_HOST_RETURNING -> true;
			case FAILURE_PRESENTING -> fallbackMayShutdown;
			default -> false;
		};
	}

	public static boolean recoveryRequired() {
		return FailureMenuLockState.locked() || WindowsEndingMetaTransaction.hasPendingTransaction();
	}

	private static void tick(Minecraft client) {
		if (recoveryRequested && !recoveryStarted) startRecovery(client);
		if (mode == Mode.FAILURE_PRESENTING) {
			modeTicks++;
			if (fallbackMayShutdown && modeTicks >= FALLBACK_MINIMUM_TICKS) concludeFailure();
		} else if (mode == Mode.LAN_HOST_RETURNING) {
			if (failureEncounterId != null && client.level != null && client.player != null
					&& Level.OVERWORLD.equals(client.level.dimension())) {
				LanHostFailureVisualState.activate(client, failureEncounterId);
				mode = Mode.IDLE;
			}
		} else if (mode == Mode.SHUTTING_DOWN) {
			modeTicks++;
			if (!disconnectIssued) {
				disconnectIssued = true;
				if (client.level != null) {
					if (client.hasSingleplayerServer()) client.disconnectWithSavingScreen();
					else client.disconnectFromWorld(Component.translatable(
							"disconnect.thefourthfrequency.world_interface.ending"));
				}
			}
			if (modeTicks >= 40) client.stop();
		}
	}

	private static void startRecovery(Minecraft client) {
		recoveryStarted = true;
		recoveryRequested = false;
		mode = Mode.RECOVERING;
		boolean replayReset = replayResetRequested && FailureMenuLockState.locked();
		replayResetRequested = false;
		MetaController.setEnabled(false);
		AlphaLoadSessionController.retirePresentation();
		cleanupRuntime(client);
		CompletableFuture<Boolean> packs = WorldInterfaceResourcePackLease.restoreAsync(client);
		CompletableFuture<Boolean> windows = WindowsEndingMetaTransaction.restoreAsync(client);
		packs.thenCombine(windows, (packsRestored, windowsRestored) -> packsRestored && windowsRestored)
				.whenComplete((restored, failure) -> client.execute(() -> {
					if (failure != null || !Boolean.TRUE.equals(restored)) {
						TheFourthFrequency.LOGGER.error("Safe ending recovery remains incomplete", failure);
						recoveryStarted = false;
						mode = Mode.IDLE;
						recoveryFailed(client);
						return;
					}
					FailureMenuLockState.restoreWindow(client);
					restorePresentationWindow(client);
					if (replayReset && !resetLocalProgress(client)) {
						recoveryStarted = false;
						mode = Mode.IDLE;
						recoveryFailed(client);
						return;
					}
					if (replayReset && !FailureMenuLockState.stageReplayQuarantine()) {
						recoveryStarted = false;
						mode = Mode.IDLE;
						recoveryFailed(client);
						return;
					}
					if (!FailureMenuLockState.unlockAfterLocalRecovery()) {
						recoveryStarted = false;
						mode = Mode.IDLE;
						recoveryFailed(client);
						return;
					}
					MetaController.setEnabled(false);
					recoveryStarted = false;
					if (replayReset) beginNormalShutdown();
					else {
						mode = Mode.IDLE;
						if (client.level == null) client.setScreen(new TitleScreen());
					}
				}));
	}

	private static boolean resetLocalProgress(Minecraft client) {
		boolean configReset = ConfigManager.resetClientState();
		boolean noticeReset = FirstRunNoticeController.resetForReplay();
		boolean presentationReset = WorldInterfaceResourcePackLease.resetPresentationForReplay();
		DimensionViewDistanceController.resetForReplay(client);
		MenuErosionState.resetForReplay();
		AlphaLoadSessionController.resetForReplay();
		LanHostFailureVisualState.reset(client);
		return configReset && noticeReset && presentationReset;
	}

	private static void recoveryFailed(Minecraft client) {
		if (client.screen instanceof RecoveryProgressScreen progress) progress.failed();
		else client.setScreen(new RecoveryProgressScreen(new TitleScreen(), true));
	}

	private static void cleanupRuntime(Minecraft client) {
		WorldInterfaceClientState.clearSession();
		WorldInterfacePresentationController.resetForEnding();
		AnomalyPresentationController.restoreForMetaToggle();
		MetaController.restore();
		if (client.options != null) {
			client.options.keyUp.setDown(false);
			client.options.keyDown.setDown(false);
			client.options.keyLeft.setDown(false);
			client.options.keyRight.setDown(false);
			client.options.keyJump.setDown(false);
			client.options.keyShift.setDown(false);
			client.options.keyAttack.setDown(false);
			client.options.keyUse.setDown(false);
			client.options.keyDrop.setDown(false);
		}
	}

	private static void beginNormalShutdown() {
		mode = Mode.SHUTTING_DOWN;
		modeTicks = 0;
		disconnectIssued = false;
	}

	private static void concludeFailure() {
		fallbackMayShutdown = false;
		if (failureLanHost) {
			mode = Mode.LAN_HOST_RETURNING;
			return;
		}
		beginNormalShutdown();
	}

	private static boolean isPublishedLanHost(Minecraft client) {
		return client != null && client.hasSingleplayerServer()
				&& client.getSingleplayerServer() != null
				&& client.getSingleplayerServer().isPublished();
	}

	private static void restorePresentationWindow(Minecraft client) {
		if (presentationWindow != null) {
			presentationWindow.restore(client);
			presentationWindow = null;
		}
		if (client.getWindow() == null) return;
		client.updateTitle();
		try { client.getWindow().setIcon(client.getVanillaPackResources(), IconSet.RELEASE); }
		catch (IOException exception) {
			TheFourthFrequency.LOGGER.warn("Could not restore the vanilla window icon", exception);
		}
	}

	private enum Mode {
		IDLE,
		SUCCESS_RESTORING,
		FAILURE_PRESENTING,
		LAN_HOST_RETURNING,
		SHUTTING_DOWN,
		RECOVERING
	}

	private static final class RecoveryProgressScreen extends Screen {
		private final Screen returnScreen;
		private boolean failed;

		private RecoveryProgressScreen(Screen returnScreen) {
			this(returnScreen, false);
		}

		private RecoveryProgressScreen(Screen returnScreen, boolean failed) {
			super(Component.translatable("screen.thefourthfrequency.ending_reset.title"));
			this.returnScreen = returnScreen;
			this.failed = failed;
		}

		private void failed() {
			failed = true;
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			super.render(graphics, mouseX, mouseY, partialTick);
			graphics.drawCenteredString(font, Component.translatable(failed
					? "screen.thefourthfrequency.ending_reset.failed"
					: "screen.thefourthfrequency.ending_reset.progress"), width / 2, height / 2, 0xFFFFFFFF);
		}

		@Override public boolean shouldCloseOnEsc() { return failed; }
		@Override public void onClose() {
			if (failed && minecraft != null) minecraft.setScreen(returnScreen == null ? new TitleScreen() : returnScreen);
		}
		@Override public boolean isPauseScreen() { return true; }
	}

	private static final class FailureFallbackScreen extends Screen {
		private boolean systemAttempt;

		private FailureFallbackScreen(boolean systemAttempt) {
			super(Component.translatable("screen.thefourthfrequency.world_interface.poem.failure.title"));
			this.systemAttempt = systemAttempt;
		}

		private void systemFailed() {
			systemAttempt = false;
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, FAILURE_IMAGE, 0, 0, 0.0F, 0.0F,
					width, height, FAILURE_TEXTURE_WIDTH, FAILURE_TEXTURE_HEIGHT,
					FAILURE_TEXTURE_WIDTH, FAILURE_TEXTURE_HEIGHT);
			graphics.fill(0, 0, width, height, 0x44000000);
			int panelWidth = Math.min(520, width - 48);
			int panelHeight = 92;
			int left = (width - panelWidth) / 2;
			int top = height - panelHeight - 26;
			graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE80A0710);
			graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFFC33DDA);
			graphics.drawString(font, Component.translatable(systemAttempt
					? "screen.thefourthfrequency.world_interface.failure.system_attempt"
					: "screen.thefourthfrequency.world_interface.failure.safe_fallback"), left + 14, top + 12,
					0xFFB98CC8, false);
			String finalLine = Component.translatable(
					"ending.thefourthfrequency.world_interface.poem.failure.15").getString();
			int visible = Math.min(finalLine.length(), Math.max(0, modeTicks / 5));
			String typed = finalLine.substring(0, visible);
			graphics.drawCenteredString(font, Component.literal(typed), width / 2, top + 42, 0xFFFFE6FF);
			graphics.drawCenteredString(font, Component.translatable(
					failureLanHost
							? "screen.thefourthfrequency.world_interface.failure.lan_host_return"
							: "screen.thefourthfrequency.world_interface.failure.normal_shutdown"),
					width / 2, top + 67, 0xFF8E8294);
		}

		@Override public boolean shouldCloseOnEsc() { return false; }
		@Override public void onClose() { }
		@Override public boolean isPauseScreen() { return false; }
	}

	private record WindowLease(boolean fullscreen, boolean maximized, int x, int y, int width, int height) {
		private static WindowLease capture(Minecraft client) {
			long handle = client.getWindow().handle();
			int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
			GLFW.glfwGetWindowPos(handle, x, y);
			GLFW.glfwGetWindowSize(handle, width, height);
			return new WindowLease(client.getWindow().isFullscreen(),
					GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
					x[0], y[0], width[0], height[0]);
		}

		private void restore(Minecraft client) {
			long handle = client.getWindow().handle();
			if (fullscreen != client.getWindow().isFullscreen()) client.getWindow().toggleFullScreen();
			if (!fullscreen) {
				GLFW.glfwRestoreWindow(handle);
				GLFW.glfwSetWindowPos(handle, x, y);
				GLFW.glfwSetWindowSize(handle, width, height);
				if (maximized) GLFW.glfwMaximizeWindow(handle);
			}
		}
	}
}
