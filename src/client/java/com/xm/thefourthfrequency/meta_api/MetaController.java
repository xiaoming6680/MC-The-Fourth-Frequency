package com.xm.thefourthfrequency.meta_api;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.meta_windows.WindowsTrustedMetaAdapter;
import com.xm.thefourthfrequency.networking.MetaEventPayload;
import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class MetaController {
	private static MetaDirector director;
	private static com.xm.thefourthfrequency.meta_windows.WindowsAnomalyController anomalyController;
	private static MetaExecution lastExecution;
	private static KeyMapping toggleKey;

	private MetaController() {
	}

	public static void initialize() {
		MetaPlatformAdapter fallback = new InGameMetaPlatformAdapter();
		MetaPlatformAdapter primary = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
				? new WindowsTrustedMetaAdapter() : fallback;
		anomalyController = primary instanceof WindowsTrustedMetaAdapter
				? new com.xm.thefourthfrequency.meta_windows.WindowsAnomalyController() : null;
		director = new MetaDirector(primary, fallback, RuntimeServices.config().meta().enabled());
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.thefourthfrequency.meta_toggle", GLFW.GLFW_KEY_F8, KeyMapping.Category.MISC));
		ClientPlayNetworking.registerGlobalReceiver(MetaEventPayload.TYPE, (payload, context) -> {
			try {
				lastExecution = director.dispatch(payload.event(), context(context.client()));
			} catch (IllegalArgumentException invalidEvent) {
				TheFourthFrequency.LOGGER.warn("Ignored unknown trusted Meta event ID");
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> restore());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> restore());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (anomalyController != null) anomalyController.tick(client);
			while (toggleKey.consumeClick()) {
				setEnabled(!enabled());
			}
		});
		TheFourthFrequency.LOGGER.info("Trusted Meta controller ready (enabled={}, adapter={})",
				director.enabled(), primary.getClass().getSimpleName());
	}

	private static MetaContext context(Minecraft client) {
		String world = client.level == null ? "menu" : client.level.dimension().identifier().toString();
		long window = client.getWindow() == null ? 0L : client.getWindow().handle();
		return new MetaContext(world, window, System.currentTimeMillis());
	}

	public static void setEnabled(boolean enabled) {
		if (director != null) director.setEnabled(enabled);
		if (!enabled) {
			AnomalyPresentationController.restoreForMetaToggle();
			if (anomalyController != null) anomalyController.restoreAll(Minecraft.getInstance());
		}
	}

	public static boolean enabled() {
		return director != null && director.enabled();
	}

	public static void restore() {
		if (director != null) director.restore();
		if (anomalyController != null) anomalyController.restoreAll(Minecraft.getInstance());
	}

	public static MetaExecution lastExecution() {
		return lastExecution;
	}

	public static MetaExecution dispatchForTesting(MetaEvent event) {
		if (director == null) throw new IllegalStateException("Trusted Meta controller is not initialized");
		lastExecution = director.dispatch(event, context(Minecraft.getInstance()));
		return lastExecution;
	}

	public static void useAdapterForTesting(MockMetaPlatformAdapter adapter) {
		// Client GameTest invokes this setup method from its dedicated test thread.
		// No window anomaly has started at this point, so restore only the pure director
		// and drop the real controller without touching Minecraft.getInstance().
		if (director != null) director.restore();
		director = new MetaDirector(adapter, new InGameMetaPlatformAdapter(), true);
		anomalyController = null;
		lastExecution = null;
	}

	public static boolean startWindowPulse() {
		return enabled() && anomalyController != null
				&& anomalyController.beginWindowPulse(Minecraft.getInstance());
	}

	public static boolean startDesktopPresence() {
		return enabled() && anomalyController != null
				&& anomalyController.beginDesktopPresence(Minecraft.getInstance());
	}

	public static long controlledNotepadPidForTesting() {
		return anomalyController == null ? -1L : anomalyController.controlledNotepadPidForTesting();
	}

	public static boolean unicodeTyperSucceededForTesting() {
		return anomalyController != null && anomalyController.unicodeTyperSucceededForTesting();
	}

	public static int unicodeTyperExitCodeForTesting() {
		return anomalyController == null ? -1002 : anomalyController.unicodeTyperExitCodeForTesting();
	}

	public static boolean unicodeTyperFinishedForTesting() {
		return anomalyController != null && anomalyController.unicodeTyperFinishedForTesting();
	}

	public static boolean windowPulseLifecycleVerifiedForTesting() {
		return anomalyController != null && anomalyController.windowPulseLifecycleVerifiedForTesting();
	}

	public static void finishAnomaly(boolean interrupted) {
		if (anomalyController != null) anomalyController.finishAnomaly(Minecraft.getInstance(), interrupted);
	}
}
