package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.ending.EndingWorldQuarantine;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.UUID;

/** Durable client-local ending lock. It never changes the authoritative server outcome directly. */
public final class FailureMenuLockState {
	private static final String DIRECTORY_NAME = "thefourthfrequency-ending";
	private static final String LOCK_FILE_NAME = "failure-menu.lock";
	private static volatile boolean initialized;
	private static volatile boolean locked;
	private static volatile UUID encounterId;
	private static volatile String worldId = "";
	private static volatile String levelId = "";
	private static volatile WorldInterfaceProtocol.Outcome outcome = WorldInterfaceProtocol.Outcome.FAILURE;
	private static WindowSnapshot windowSnapshot;

	private FailureMenuLockState() {
	}

	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		readLock();
	}

	public static boolean locked() {
		if (!initialized) initialize();
		return locked;
	}

	public static UUID encounterId() {
		if (!initialized) initialize();
		return encounterId;
	}

	public static String worldId() {
		if (!initialized) initialize();
		return worldId;
	}

	public static WorldInterfaceProtocol.Outcome outcome() {
		if (!initialized) initialize();
		return outcome;
	}

	public static String levelId() {
		if (!initialized) initialize();
		return levelId;
	}

	public static synchronized boolean lock(UUID encounter) {
		return lock(encounter, WorldInterfaceProtocol.Outcome.FAILURE, "", null);
	}

	public static synchronized boolean lock(UUID encounter, Minecraft client) {
		return lock(encounter, WorldInterfaceProtocol.Outcome.FAILURE, "", client);
	}

	public static synchronized boolean lock(UUID encounter, WorldInterfaceProtocol.Outcome endingOutcome,
			String endingWorldId, Minecraft client) {
		initialize();
		if (endingOutcome == null || endingOutcome == WorldInterfaceProtocol.Outcome.NONE
				|| endingWorldId == null || endingWorldId.length() > 128) return false;
		String endingLevelId = captureLocalLevelId(client);
		if (locked && encounter.equals(encounterId) && endingOutcome == outcome
				&& endingWorldId.equals(worldId) && endingLevelId.equals(levelId)) return true;
		Properties properties = new Properties();
		properties.setProperty("version", "3");
		properties.setProperty("encounter", encounter.toString());
		properties.setProperty("worldId", endingWorldId);
		properties.setProperty("levelId", endingLevelId);
		properties.setProperty("outcome", endingOutcome.name());
		properties.setProperty("lockedAt", Long.toString(System.currentTimeMillis()));
		WindowSnapshot captured = client == null || client.getWindow() == null
				? null : WindowSnapshot.capture(client);
		if (captured != null) captured.write(properties);
		if (!writeAtomically(lockPath(), properties)) return false;
		encounterId = encounter;
		worldId = endingWorldId;
		levelId = endingLevelId;
		outcome = endingOutcome;
		windowSnapshot = captured;
		locked = true;
		return true;
	}

	public static synchronized boolean stageReplayQuarantine() {
		initialize();
		if (!locked || encounterId == null || worldId.isBlank()) {
			TheFourthFrequency.LOGGER.error("Ending replay quarantine has no valid world identity");
			return false;
		}
		if (levelId.isBlank()) {
			TheFourthFrequency.LOGGER.info("Ending came from a remote world; no local save was quarantined");
			return true;
		}
		return EndingWorldQuarantine.quarantine(levelId, worldId, encounterId, outcome.name());
	}

	public static synchronized boolean unlockAfterLocalRecovery() {
		initialize();
		try {
			Files.deleteIfExists(lockPath());
			locked = false;
			encounterId = null;
			worldId = "";
			levelId = "";
			outcome = WorldInterfaceProtocol.Outcome.FAILURE;
			windowSnapshot = null;
			return true;
		} catch (IOException exception) {
			TheFourthFrequency.LOGGER.error("Could not clear the local failure menu lock", exception);
			return false;
		}
	}

	public static Path lockPathForTesting() {
		return lockPath();
	}

	public static synchronized void restoreWindow(Minecraft client) {
		initialize();
		if (windowSnapshot != null && client.getWindow() != null) windowSnapshot.restore(client);
	}

	private static void readLock() {
		Path path = lockPath();
		if (!Files.isRegularFile(path)) {
			locked = false;
			encounterId = null;
			worldId = "";
			levelId = "";
			outcome = WorldInterfaceProtocol.Outcome.FAILURE;
			windowSnapshot = null;
			return;
		}
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(path)) {
			properties.load(input);
			String version = properties.getProperty("version");
			if (!"1".equals(version) && !"2".equals(version) && !"3".equals(version)) {
				throw new IOException("Unknown lock version");
			}
			encounterId = UUID.fromString(properties.getProperty("encounter", ""));
			worldId = !"1".equals(version) ? properties.getProperty("worldId", "") : "";
			if (worldId.length() > 128) throw new IOException("Invalid world id");
			levelId = "3".equals(version) ? properties.getProperty("levelId", "") : "";
			if (!levelId.isBlank() && EndingWorldQuarantine.markerPath(levelId).isEmpty()) {
				throw new IOException("Invalid local level id");
			}
			outcome = !"1".equals(version)
					? WorldInterfaceProtocol.Outcome.valueOf(properties.getProperty("outcome", "FAILURE"))
					: WorldInterfaceProtocol.Outcome.FAILURE;
			if (outcome == WorldInterfaceProtocol.Outcome.NONE) throw new IOException("Invalid ending outcome");
			windowSnapshot = WindowSnapshot.read(properties);
			locked = true;
		} catch (IOException | IllegalArgumentException exception) {
			// A malformed file must fail closed: recovery is still available through F8/safe mode.
			locked = true;
			encounterId = null;
			worldId = "";
			levelId = "";
			outcome = WorldInterfaceProtocol.Outcome.FAILURE;
			windowSnapshot = null;
			TheFourthFrequency.LOGGER.error("Failure menu lock is damaged; keeping the client locked", exception);
		}
	}

	private static String captureLocalLevelId(Minecraft client) {
		if (client == null || !client.hasSingleplayerServer() || client.getSingleplayerServer() == null) return "";
		Path savesDirectory = FabricLoader.getInstance().getGameDir().resolve("saves")
				.toAbsolutePath().normalize();
		Path worldDirectory = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
				.toAbsolutePath().normalize();
		if (!savesDirectory.equals(worldDirectory.getParent()) || worldDirectory.getFileName() == null) {
			TheFourthFrequency.LOGGER.warn("Could not safely identify the local ending save at {}", worldDirectory);
			return "";
		}
		return worldDirectory.getFileName().toString();
	}

	private static Path endingDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY_NAME).toAbsolutePath().normalize();
	}

	private static Path lockPath() {
		return endingDirectory().resolve(LOCK_FILE_NAME).normalize();
	}

	private static boolean writeAtomically(Path target, Properties properties) {
		Path directory = endingDirectory();
		Path temporary = directory.resolve(LOCK_FILE_NAME + ".tmp").normalize();
		if (!target.getParent().equals(directory) || !temporary.getParent().equals(directory)) return false;
		try {
			Files.createDirectories(directory);
			try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				properties.store(output, "The Fourth Frequency local failure lock");
			}
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException exception) {
			TheFourthFrequency.LOGGER.error("Could not persist the local failure menu lock", exception);
			try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
			return false;
		}
	}

	private record WindowSnapshot(boolean fullscreen, boolean maximized, int x, int y, int width, int height) {
		static WindowSnapshot capture(Minecraft client) {
			long handle = client.getWindow().handle();
			int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
			GLFW.glfwGetWindowPos(handle, x, y);
			GLFW.glfwGetWindowSize(handle, width, height);
			return new WindowSnapshot(client.getWindow().isFullscreen(),
					GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
					x[0], y[0], width[0], height[0]);
		}

		void write(Properties properties) {
			properties.setProperty("window.fullscreen", Boolean.toString(fullscreen));
			properties.setProperty("window.maximized", Boolean.toString(maximized));
			properties.setProperty("window.x", Integer.toString(x));
			properties.setProperty("window.y", Integer.toString(y));
			properties.setProperty("window.width", Integer.toString(width));
			properties.setProperty("window.height", Integer.toString(height));
		}

		static WindowSnapshot read(Properties properties) {
			if (!properties.containsKey("window.width")) return null;
			try {
				return new WindowSnapshot(Boolean.parseBoolean(properties.getProperty("window.fullscreen", "false")),
						Boolean.parseBoolean(properties.getProperty("window.maximized", "false")),
						Integer.parseInt(properties.getProperty("window.x", "80")),
						Integer.parseInt(properties.getProperty("window.y", "80")),
						Math.clamp(Integer.parseInt(properties.getProperty("window.width")), 320, 16_384),
						Math.clamp(Integer.parseInt(properties.getProperty("window.height")), 240, 16_384));
			} catch (RuntimeException ignored) {
				return null;
			}
		}

		void restore(Minecraft client) {
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
