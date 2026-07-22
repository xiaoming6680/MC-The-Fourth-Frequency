package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Places a harmless, mod-owned quarantine marker inside the exact local save that produced an ending.
 * The marker never modifies level.dat, region files, entities, or player data.
 */
public final class EndingWorldQuarantine {
	private static final String MARKER_FILE_NAME = ".thefourthfrequency-corrupted";
	private static final String TEMPORARY_FILE_NAME = MARKER_FILE_NAME + ".tmp";

	private EndingWorldQuarantine() {
	}

	public static synchronized boolean quarantine(String levelId, String worldId, UUID encounterId, String outcome) {
		Optional<Path> worldDirectory = worldDirectory(levelId);
		if (worldDirectory.isEmpty() || worldId == null || worldId.isBlank()
				|| encounterId == null || outcome == null || outcome.isBlank()) return false;
		if (!Files.isDirectory(worldDirectory.get())) {
			TheFourthFrequency.LOGGER.warn("Ending save {} no longer exists; no quarantine marker was needed", levelId);
			return true;
		}

		Properties properties = new Properties();
		properties.setProperty("version", "1");
		properties.setProperty("worldId", worldId);
		properties.setProperty("encounter", encounterId.toString());
		properties.setProperty("outcome", outcome);
		properties.setProperty("quarantinedAt", Long.toString(System.currentTimeMillis()));
		return writeAtomically(worldDirectory.get(), properties);
	}

	public static boolean isQuarantined(String levelId) {
		return markerPath(levelId).map(Files::isRegularFile).orElse(false);
	}

	public static Optional<Path> markerPath(String levelId) {
		return worldDirectory(levelId).map(path -> path.resolve(MARKER_FILE_NAME).normalize());
	}

	private static boolean writeAtomically(Path worldDirectory, Properties properties) {
		Path target = worldDirectory.resolve(MARKER_FILE_NAME).normalize();
		Path temporary = worldDirectory.resolve(TEMPORARY_FILE_NAME).normalize();
		if (!worldDirectory.equals(target.getParent()) || !worldDirectory.equals(temporary.getParent())) return false;
		try {
			try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				properties.store(output, "The Fourth Frequency ending save quarantine");
			}
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException exception) {
			TheFourthFrequency.LOGGER.error("Could not quarantine ending save {}", worldDirectory, exception);
			try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
			return false;
		}
	}

	private static Optional<Path> worldDirectory(String levelId) {
		if (levelId == null || levelId.isBlank()) return Optional.empty();
		try {
			Path relative = Path.of(levelId);
			if (relative.isAbsolute() || relative.getNameCount() != 1
					|| !relative.getFileName().toString().equals(levelId)
					|| ".".equals(levelId) || "..".equals(levelId)) return Optional.empty();
			Path savesDirectory = FabricLoader.getInstance().getGameDir().resolve("saves")
					.toAbsolutePath().normalize();
			Path worldDirectory = savesDirectory.resolve(relative).normalize();
			return savesDirectory.equals(worldDirectory.getParent())
					? Optional.of(worldDirectory) : Optional.empty();
		} catch (InvalidPathException exception) {
			return Optional.empty();
		}
	}
}
