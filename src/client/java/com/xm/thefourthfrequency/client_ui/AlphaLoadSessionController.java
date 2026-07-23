package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.config.ConfigManager;
import com.xm.thefourthfrequency.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

public final class AlphaLoadSessionController {
	private static final String JAVA_ICON_RESOURCE =
			"/assets/thefourthfrequency/textures/gui/alpha_java_icon.png";
	private static final String MENU_VERSION_TEXT = "Minecraft 1.0.0";
	private static final String MENU_WINDOW_TITLE = "Minecraft 1.0.0";
	private static boolean initialized;
	private static boolean active;
	private static boolean corruptionEverPlayed;
	private static boolean corruptionInProgress;
	private static boolean persistentStartupPending;
	private static boolean persistentStartupApplied;
	private static boolean persistentInitialPackSelectionPrepared;
	private static boolean persistentIdentityPrimed;
	private static boolean presentationRetired;
	private static boolean javaIconApplied;
	private static boolean resourceReloadFinished = true;
	private static boolean resourceReloadInProgress;
	private static boolean resourceReloadFailed;
	private static boolean resourceReloadRequestedThisSession;
	private static boolean suppressNextResourceReloadAnimation;
	private static boolean currentViewportFlooded;
	private static int reloadGeneration;
	private static int corruptionPlayCount;
	private static int javaIconAppliedAtScreenTick = -1;
	private static int appliedVersionStage = -1;
	private static int legacyLoadingScreensRendered;
	private static int lastLoadingScreenTicks;
	private static int lastFailureCopies;
	private static int suppressedResourceReloadAnimations;
	private static int persistentAlphaLoadingOverlays;
	private static int persistentAlphaLoadingFirstFrames;
	private static String pendingRegressionScreenshot;
	private static boolean corruptionScreenshotRequested;
	private static boolean legacyScreenshotRequested;
	private static boolean lastViewportFlooded;
	private static SessionKind sessionKind = SessionKind.MULTIPLAYER;
	private static String launchedVersion = "1.21.11";
	private static String appliedWindowTitle = "";
	private static List<String> activePackOrder = List.of();

	private AlphaLoadSessionController() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		WorldInterfaceResourcePackLease.initialize();
		presentationRetired = WorldInterfaceResourcePackLease.presentationRetired();
		ModContainer container = FabricLoader.getInstance().getModContainer(TheFourthFrequency.MOD_ID)
				.orElseThrow(() -> new IllegalStateException("Missing own Fabric mod container"));
		registerPack(container, "golden_days_base", "Golden Days Base");
		registerPack(container, "golden_days_alpha", "Golden Days Alpha");
		corruptionEverPlayed = ConfigManager.loadClientState().alphaDowngradeComplete();
		persistentStartupPending = corruptionEverPlayed && !presentationRetired;

		ClientPlayConnectionEvents.INIT.register((handler, client) -> client.execute(() -> begin(client)));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> begin(client)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> end(client)));
		ClientTickEvents.END_CLIENT_TICK.register(AlphaLoadSessionController::clientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(AlphaLoadSessionController::end);
	}

	private static void registerPack(ModContainer container, String path, String displayName) {
		Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
		if (!ResourceLoader.registerBuiltinPack(id, container, Component.literal(displayName),
				PackActivationType.NORMAL)) {
			throw new IllegalStateException("Missing embedded resource pack resourcepacks/" + path);
		}
	}

	private static void begin(Minecraft client) {
		if (presentationRetired || active || client.getWindow() == null) return;
		active = true;
		corruptionInProgress = false;
		resourceReloadFailed = false;
		suppressNextResourceReloadAnimation = false;
		currentViewportFlooded = false;
		sessionKind = client.hasSingleplayerServer() ? SessionKind.SINGLEPLAYER : SessionKind.MULTIPLAYER;
		launchedVersion = client.getLaunchedVersion() == null || client.getLaunchedVersion().isBlank()
				? "1.21.11" : client.getLaunchedVersion();

		ensureAlphaResourceStack(client, true);
		if (javaIconApplied) applyJavaIcon(client);
		applyVersionTitle(client, corruptionEverPlayed
				? AlphaLoadTimeline.finalVersionStage() : 0);
		TheFourthFrequency.LOGGER.info("Started {} Alpha resource session with order {}; first-entry corruption={}",
				sessionKind.name().toLowerCase(java.util.Locale.ROOT), activePackOrder,
				!corruptionEverPlayed);
	}

	private static void end(Minecraft client) {
		active = false;
		corruptionInProgress = false;
		resourceReloadFinished = true;
		resourceReloadInProgress = false;
		++reloadGeneration;
		currentViewportFlooded = false;
		suppressNextResourceReloadAnimation = false;
		if (presentationRetired) return;
		// The Alpha bases intentionally stay selected on the main menu and between later world entries.
		if (javaIconApplied && client.getWindow() != null) applyJavaIcon(client);
		if (corruptionEverPlayed && client.getWindow() != null) {
			applyPersistentFinalTitle(client);
		}
	}

	public static boolean claimInitialCorruptionScreen() {
		if (presentationRetired || !active || corruptionEverPlayed || corruptionInProgress) return false;
		corruptionEverPlayed = true;
		corruptionInProgress = true;
		corruptionPlayCount++;
		currentViewportFlooded = false;
		ConfigManager.updateClientState(ModConfig.ClientState::completeAlphaDowngrade);
		return true;
	}

	public static boolean shouldCorruptLoadingScreen() {
		return active && corruptionInProgress;
	}

	public static boolean shouldRenderLegacyLoadingScreen() {
		return shouldUsePersistentAlphaLoadingStyle();
	}

	public static boolean shouldUsePersistentAlphaLoadingStyle() {
		return !presentationRetired && AlphaLoadingPresentationPolicy.usePersistentLegacyPresentation(
				corruptionEverPlayed, corruptionInProgress);
	}

	public static boolean canCloseLoadingScreen(int screenTicks) {
		if (shouldCorruptLoadingScreen()) {
			return AlphaLoadTimeline.mayCloseLoadingScreen(screenTicks, resourceReloadFinished,
					currentViewportFlooded);
		}
		return !shouldRenderLegacyLoadingScreen() || resourceReloadFinished;
	}

	public static void loadingScreenTick(int screenTicks) {
		if (!shouldCorruptLoadingScreen()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() != null) {
			applyVersionTitle(client, AlphaLoadTimeline.versionStage(screenTicks));
			if (screenTicks >= AlphaLoadTimeline.GLITCH_START_TICK && !javaIconApplied
					&& applyJavaIcon(client)) {
				javaIconApplied = true;
				javaIconAppliedAtScreenTick = screenTicks;
			}
		}
	}

	public static void recordViewportFlooded(boolean flooded) {
		if (shouldCorruptLoadingScreen() && flooded) currentViewportFlooded = true;
	}

	public static void loadingScreenClosed(int screenTicks, boolean viewportFlooded) {
		if (!shouldCorruptLoadingScreen()) return;
		lastLoadingScreenTicks = screenTicks;
		lastFailureCopies = AlphaLoadTimeline.copiedFailureLines(screenTicks);
		lastViewportFlooded = currentViewportFlooded || viewportFlooded;
		corruptionInProgress = false;
		applyVersionTitle(Minecraft.getInstance(), AlphaLoadTimeline.finalVersionStage());
	}

	public static void retainFinalWindowTitle(Minecraft client) {
		if (!presentationRetired && corruptionEverPlayed && !corruptionInProgress && client.getWindow() != null) {
			applyPersistentFinalTitle(client);
		}
	}

	public static String menuVersionText(String vanillaText) {
		return corruptionEverPlayed && !presentationRetired ? MENU_VERSION_TEXT : vanillaText;
	}

	public static void recordLegacyLoadingScreenRendered() {
		legacyLoadingScreensRendered++;
	}

	public static void requestRegressionScreenshot(String fileName) {
		if ("alpha-loading-corruption.png".equals(fileName)) {
			if (corruptionScreenshotRequested) return;
			corruptionScreenshotRequested = true;
		} else if ("legacy-loading-normal.png".equals(fileName)) {
			if (legacyScreenshotRequested) return;
			legacyScreenshotRequested = true;
		}
		pendingRegressionScreenshot = fileName;
	}

	private static void clientTick(Minecraft client) {
		if (presentationRetired) return;
		primePersistentIdentity(client);
		applyPersistentStartupIfReady(client);
		capturePendingScreenshot(client);
	}

	public static void preparePersistentPackSelectionBeforeInitialReload(Minecraft client) {
		if (presentationRetired || !corruptionEverPlayed
				|| persistentInitialPackSelectionPrepared || client.options == null) return;
		try {
			selectAlphaResourceStack(client);
			client.options.resourcePacks.removeIf(AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH::contains);
			client.options.resourcePacks.addAll(AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH);
			persistentInitialPackSelectionPrepared = true;
			TheFourthFrequency.LOGGER.info(
					"Prepared persistent Alpha resource order before Minecraft's initial resource reload: {}",
					activePackOrder);
		} catch (RuntimeException exception) {
			TheFourthFrequency.LOGGER.error(
					"Could not prepare the persistent Alpha packs before initial reload; title-screen recovery will retry",
					exception);
		}
	}

	private static void primePersistentIdentity(Minecraft client) {
		if (presentationRetired || !corruptionEverPlayed
				|| active || persistentIdentityPrimed || client.getWindow() == null) return;
		if (applyJavaIcon(client)) javaIconApplied = true;
		applyPersistentFinalTitle(client);
		persistentIdentityPrimed = true;
	}

	private static void applyPersistentStartupIfReady(Minecraft client) {
		if (presentationRetired || !persistentStartupPending || active || client.getWindow() == null
				|| !(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
		persistentStartupPending = false;
		launchedVersion = client.getLaunchedVersion() == null || client.getLaunchedVersion().isBlank()
				? "1.21.11" : client.getLaunchedVersion();
		ensureAlphaResourceStack(client, false);
		primePersistentIdentity(client);
		applyPersistentFinalTitle(client);
		persistentStartupApplied = true;
		TheFourthFrequency.LOGGER.info(
				"Restored persistent Alpha 1.0.0 client identity with resource order {}", activePackOrder);
	}

	private static void capturePendingScreenshot(Minecraft client) {
		if (pendingRegressionScreenshot != null) {
			String fileName = pendingRegressionScreenshot;
			pendingRegressionScreenshot = null;
			Screenshot.grab(client.gameDirectory, fileName, client.getMainRenderTarget(), 1,
					message -> TheFourthFrequency.LOGGER.info("Captured loading regression frame {}: {}",
							fileName, message.getString()));
		}
	}

	private static void ensureAlphaResourceStack(Minecraft client, boolean recordSessionRequest) {
		if (presentationRetired) return;
		boolean selectionChanged = selectAlphaResourceStack(client);
		if (recordSessionRequest) resourceReloadRequestedThisSession = selectionChanged;
		if (!selectionChanged) {
			if (!resourceReloadInProgress) resourceReloadFinished = true;
			return;
		}

		resourceReloadFinished = false;
		resourceReloadInProgress = true;
		resourceReloadFailed = false;
		int generation = ++reloadGeneration;
		boolean restorePersistentIdentity = !recordSessionRequest && corruptionEverPlayed;
		armResourceReloadAnimationSuppression();
		client.reloadResourcePacks().whenComplete((ignored, failure) -> client.execute(() -> {
			if (generation != reloadGeneration) return;
			suppressNextResourceReloadAnimation = false;
			resourceReloadInProgress = false;
			resourceReloadFinished = true;
			resourceReloadFailed = failure != null;
			if (failure != null) {
				TheFourthFrequency.LOGGER.error(
						"Alpha base resource reload failed; world loading may continue with recovered resources",
						failure);
			}
			if (restorePersistentIdentity && client.getWindow() != null) {
				if (applyJavaIcon(client)) javaIconApplied = true;
				applyPersistentFinalTitle(client);
			}
		}));
	}

	private static boolean selectAlphaResourceStack(Minecraft client) {
		if (presentationRetired) return false;
		PackRepository repository = client.getResourcePackRepository();
		repository.reload();
		List<String> selectedBefore = repository.getSelectedPacks().stream().map(Pack::getId).toList();
		if (containsOrderedAlphaBases(selectedBefore)) {
			WorldInterfaceResourcePackLease.adoptExistingAutomaticSelection(selectedBefore);
			activePackOrder = List.copyOf(selectedBefore);
			return false;
		}
		activePackOrder = AlphaResourcePackPlan.selectionForSession(selectedBefore,
				repository.getAvailableIds());
		WorldInterfaceResourcePackLease.captureAutomaticSelection(selectedBefore, activePackOrder);
		for (String packId : AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH) {
			if (!repository.isAvailable(packId)) {
				TheFourthFrequency.LOGGER.error("Required Alpha base resource pack is unavailable: {}", packId);
			}
		}
		repository.setSelected(activePackOrder);
		return !selectedBefore.equals(activePackOrder);
	}

	private static boolean containsOrderedAlphaBases(List<String> packIds) {
		int programmer = packIds.indexOf(AlphaResourcePackPlan.PROGRAMMER_ART_PACK_ID);
		int base = packIds.indexOf(AlphaResourcePackPlan.GOLDEN_DAYS_BASE_PACK_ID);
		int alpha = packIds.indexOf(AlphaResourcePackPlan.GOLDEN_DAYS_ALPHA_PACK_ID);
		return programmer >= 0 && programmer < base && base < alpha;
	}

	private static boolean applyJavaIcon(Minecraft client) {
		if (client.getWindow() == null) return false;
		try (InputStream input = AlphaLoadSessionController.class.getResourceAsStream(JAVA_ICON_RESOURCE)) {
			if (input == null) throw new IllegalStateException("Missing " + JAVA_ICON_RESOURCE);
			BufferedImage image = ImageIO.read(input);
			if (image == null) throw new IllegalStateException("Invalid " + JAVA_ICON_RESOURCE);
			ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getRGB(x, y);
					pixels.put((byte) (argb >> 16));
					pixels.put((byte) (argb >> 8));
					pixels.put((byte) argb);
					pixels.put((byte) (argb >> 24));
				}
			}
			pixels.flip();
			try (GLFWImage icon = GLFWImage.malloc(); GLFWImage.Buffer icons = GLFWImage.malloc(1)) {
				icon.set(image.getWidth(), image.getHeight(), pixels);
				icons.put(0, icon);
				GLFW.glfwSetWindowIcon(client.getWindow().handle(), icons);
			}
			return true;
		} catch (Exception exception) {
			TheFourthFrequency.LOGGER.error("Could not apply the Alpha-era Java window icon", exception);
			return false;
		}
	}

	private static void armResourceReloadAnimationSuppression() {
		suppressNextResourceReloadAnimation = true;
	}

	public static boolean consumeResourceReloadAnimationSuppression() {
		if (!suppressNextResourceReloadAnimation) return false;
		suppressNextResourceReloadAnimation = false;
		suppressedResourceReloadAnimations++;
		return true;
	}

	public static boolean activeForTesting() {
		return active;
	}

	public static boolean resourceReloadFinishedForTesting() {
		return resourceReloadFinished;
	}

	public static boolean resourceReloadFailedForTesting() {
		return resourceReloadFailed;
	}

	public static boolean resourceReloadRequestedForTesting() {
		return resourceReloadRequestedThisSession;
	}

	public static boolean corruptionEverPlayedForTesting() {
		return corruptionEverPlayed;
	}

	public static boolean persistentStartupAppliedForTesting() {
		return persistentStartupApplied;
	}

	public static boolean persistentInitialPackSelectionPreparedForTesting() {
		return persistentInitialPackSelectionPrepared;
	}

	public static int corruptionPlayCountForTesting() {
		return corruptionPlayCount;
	}

	public static int versionStageForTesting() {
		return appliedVersionStage;
	}

	public static String appliedWindowTitleForTesting() {
		return appliedWindowTitle;
	}

	public static boolean javaIconAppliedForTesting() {
		return javaIconApplied;
	}

	public static int javaIconAppliedAtScreenTickForTesting() {
		return javaIconAppliedAtScreenTick;
	}

	public static int legacyLoadingScreensRenderedForTesting() {
		return legacyLoadingScreensRendered;
	}

	public static int lastLoadingScreenTicksForTesting() {
		return lastLoadingScreenTicks;
	}

	public static int lastFailureCopiesForTesting() {
		return lastFailureCopies;
	}

	public static boolean lastViewportFloodedForTesting() {
		return lastViewportFlooded;
	}

	public static int suppressedResourceReloadAnimationsForTesting() {
		return suppressedResourceReloadAnimations;
	}

	public static void recordPersistentAlphaLoadingOverlayCreated() {
		persistentAlphaLoadingOverlays++;
	}

	public static void recordPersistentAlphaLoadingFirstFrame() {
		persistentAlphaLoadingFirstFrames++;
	}

	public static int persistentAlphaLoadingOverlaysForTesting() {
		return persistentAlphaLoadingOverlays;
	}

	public static int persistentAlphaLoadingFirstFramesForTesting() {
		return persistentAlphaLoadingFirstFrames;
	}

	public static List<String> activePackOrderForTesting() {
		return activePackOrder;
	}

	public static String sessionKindForTesting() {
		return sessionKind.name();
	}

	/** Permanently ends the optional Alpha presentation without changing core mod resources. */
	public static void retirePresentation() {
		presentationRetired = true;
		active = false;
		persistentStartupPending = false;
		persistentIdentityPrimed = false;
		resourceReloadInProgress = false;
		resourceReloadFinished = true;
		++reloadGeneration;
		WorldInterfaceResourcePackLease.markPresentationRetired();
	}

	public static void resetForReplay() {
		active = false;
		corruptionEverPlayed = false;
		corruptionInProgress = false;
		persistentStartupPending = false;
		persistentStartupApplied = false;
		persistentIdentityPrimed = false;
		presentationRetired = false;
		resourceReloadInProgress = false;
		resourceReloadFinished = true;
	}

	public static boolean presentationRetiredForTesting() {
		return presentationRetired;
	}

	private static void applyVersionTitle(Minecraft client, int stage) {
		appliedVersionStage = Math.clamp(stage, 0, AlphaLoadTimeline.finalVersionStage());
		String version = AlphaLoadTimeline.versionAt(appliedVersionStage, launchedVersion);
		String contextKey = AlphaLoadTimeline.windowContextKey(sessionKind == SessionKind.SINGLEPLAYER);
		appliedWindowTitle = Component.translatable(contextKey, "Minecraft " + version).getString();
		client.getWindow().setTitle(appliedWindowTitle);
	}

	private static void applyPersistentFinalTitle(Minecraft client) {
		if (active) {
			applyVersionTitle(client, AlphaLoadTimeline.finalVersionStage());
			return;
		}
		appliedVersionStage = AlphaLoadTimeline.finalVersionStage();
		appliedWindowTitle = MENU_WINDOW_TITLE;
		client.getWindow().setTitle(appliedWindowTitle);
	}

	private enum SessionKind {
		SINGLEPLAYER,
		MULTIPLAYER
	}
}
