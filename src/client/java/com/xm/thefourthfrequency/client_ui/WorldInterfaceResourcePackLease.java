package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Persistent ownership lease for only the packs automatically selected by the Alpha presentation. */
public final class WorldInterfaceResourcePackLease {
	private static final String DIRECTORY_NAME = "thefourthfrequency-ending";
	private static final String MANIFEST_NAME = "resource-pack-lease.properties";
	private static final String RETIRED_MARKER_NAME = "alpha-presentation-retired.flag";
	private static final Set<String> GOLDEN_PACKS = Set.of(
			AlphaResourcePackPlan.GOLDEN_DAYS_BASE_PACK_ID,
			AlphaResourcePackPlan.GOLDEN_DAYS_ALPHA_PACK_ID);
	private static Lease lease;
	private static CompletableFuture<Boolean> restoreInFlight;
	private static boolean initialized;
	private static boolean presentationRetired;

	private WorldInterfaceResourcePackLease() {
	}

	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		presentationRetired = Files.isRegularFile(retiredMarker());
		lease = readLease();
	}

	public static synchronized boolean presentationRetired() {
		initialize();
		return presentationRetired;
	}

	public static synchronized void markPresentationRetired() {
		initialize();
		presentationRetired = true;
		try {
			Files.createDirectories(endingDirectory());
			Files.writeString(retiredMarker(), "retired\n", StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		} catch (IOException exception) {
			TheFourthFrequency.LOGGER.error("Could not persist retirement of the Alpha presentation", exception);
		}
	}

	public static synchronized void captureAutomaticSelection(List<String> selectedBefore,
			List<String> selectedAfter) {
		initialize();
		if (lease != null || presentationRetired || selectedBefore.equals(selectedAfter)) return;
		LinkedHashSet<String> added = new LinkedHashSet<>(selectedAfter);
		added.removeAll(selectedBefore);
		added.retainAll(AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH);
		if (added.isEmpty()) return;
		Lease candidate = new Lease(List.copyOf(selectedBefore), List.copyOf(added));
		if (writeLease(candidate)) lease = candidate;
	}

	/** Conservative adoption for pre-lease development installs: Golden Days is owned; Programmer Art is preserved. */
	public static synchronized void adoptExistingAutomaticSelection(List<String> currentSelection) {
		initialize();
		if (lease != null || !currentSelection.containsAll(GOLDEN_PACKS)) return;
		List<String> original = currentSelection.stream().filter(id -> !GOLDEN_PACKS.contains(id)).toList();
		Lease candidate = new Lease(original, GOLDEN_PACKS.stream().toList());
		if (writeLease(candidate)) lease = candidate;
	}

	public static synchronized CompletableFuture<Boolean> restoreAsync(Minecraft client) {
		initialize();
		markPresentationRetired();
		if (restoreInFlight != null && !restoreInFlight.isDone()) return restoreInFlight;
		if (lease == null) return CompletableFuture.completedFuture(true);
		Lease restoring = lease;
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		restoreInFlight = result;
		try {
			PackRepository repository = client.getResourcePackRepository();
			repository.reload();
			List<String> desired = restoredSelection(restoring.originalOrder(), restoring.autoAddedIds(),
					repository.getAvailableIds());
			repository.setSelected(desired);
			client.options.updateResourcePacks(repository);
			client.options.save();
			client.reloadResourcePacks().whenComplete((ignored, failure) -> client.execute(() -> {
				if (failure != null) {
					TheFourthFrequency.LOGGER.error("Could not restore the pre-presentation resource-pack order", failure);
					result.complete(false);
					return;
				}
				synchronized (WorldInterfaceResourcePackLease.class) {
					lease = null;
					try { Files.deleteIfExists(manifestPath()); }
					catch (IOException exception) {
						TheFourthFrequency.LOGGER.warn(
								"Resource packs restored but the completed lease remains on disk", exception);
					}
				}
				result.complete(true);
			}));
		} catch (RuntimeException exception) {
			TheFourthFrequency.LOGGER.error("Could not begin restoration of the pre-presentation pack order", exception);
			result.complete(false);
		}
		return result;
	}

	/** Re-arms the Alpha presentation only after its owned packs have been restored. */
	public static synchronized boolean resetPresentationForReplay() {
		initialize();
		if (lease != null || restoreInFlight != null && !restoreInFlight.isDone()) return false;
		try {
			Files.deleteIfExists(retiredMarker());
			presentationRetired = false;
			return true;
		} catch (IOException exception) {
			TheFourthFrequency.LOGGER.error("Could not re-arm the Alpha presentation for replay", exception);
			return false;
		}
	}

	public static List<String> restoredSelection(List<String> originalOrder, Collection<String> autoAddedIds,
			Collection<String> availableIds) {
		Set<String> available = Set.copyOf(availableIds);
		Set<String> owned = Set.copyOf(autoAddedIds);
		List<String> result = new ArrayList<>();
		for (String id : originalOrder) {
			if (available.contains(id) && !owned.contains(id) && !result.contains(id)) result.add(id);
		}
		// An originally selected pack is never considered owned merely because it shares a known pack id.
		for (String id : originalOrder) {
			if (available.contains(id) && !result.contains(id)) result.add(id);
		}
		return List.copyOf(result);
	}

	public static synchronized List<String> originalOrderForTesting() {
		initialize();
		return lease == null ? List.of() : lease.originalOrder();
	}

	private static Lease readLease() {
		Path path = manifestPath();
		if (!Files.isRegularFile(path)) return null;
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(path)) {
			properties.load(input);
			if (!"1".equals(properties.getProperty("version"))) throw new IOException("Unknown lease version");
			return new Lease(readList(properties, "original", 512), readList(properties, "added", 3));
		} catch (IOException | RuntimeException exception) {
			TheFourthFrequency.LOGGER.error("Could not read the resource-pack ownership lease", exception);
			return null;
		}
	}

	private static boolean writeLease(Lease value) {
		Properties properties = new Properties();
		properties.setProperty("version", "1");
		writeList(properties, "original", value.originalOrder());
		writeList(properties, "added", value.autoAddedIds());
		return writeAtomically(manifestPath(), properties);
	}

	private static List<String> readList(Properties properties, String key, int maximum) throws IOException {
		int count = Integer.parseInt(properties.getProperty(key + ".count", "-1"));
		if (count < 0 || count > maximum) throw new IOException("Invalid " + key + " count");
		List<String> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			String id = properties.getProperty(key + "." + index);
			if (id == null || id.length() > 256 || id.indexOf('\n') >= 0) throw new IOException("Invalid pack id");
			result.add(id);
		}
		return List.copyOf(result);
	}

	private static void writeList(Properties properties, String key, List<String> values) {
		properties.setProperty(key + ".count", Integer.toString(values.size()));
		for (int index = 0; index < values.size(); index++) properties.setProperty(key + "." + index, values.get(index));
	}

	private static boolean writeAtomically(Path target, Properties properties) {
		Path directory = endingDirectory();
		Path temporary = directory.resolve(MANIFEST_NAME + ".tmp").normalize();
		try {
			Files.createDirectories(directory);
			try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				properties.store(output, "The Fourth Frequency resource-pack ownership lease");
			}
			try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException exception) {
			TheFourthFrequency.LOGGER.error("Could not persist the resource-pack ownership lease", exception);
			try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
			return false;
		}
	}

	private static Path endingDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY_NAME).toAbsolutePath().normalize();
	}

	private static Path manifestPath() { return endingDirectory().resolve(MANIFEST_NAME).normalize(); }
	private static Path retiredMarker() { return endingDirectory().resolve(RETIRED_MARKER_NAME).normalize(); }

	private record Lease(List<String> originalOrder, List<String> autoAddedIds) {
		private Lease {
			originalOrder = List.copyOf(originalOrder);
			autoAddedIds = List.copyOf(autoAddedIds);
		}
	}
}
