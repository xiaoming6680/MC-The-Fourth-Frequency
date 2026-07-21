package com.xm.thefourthfrequency.meta_windows;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.meta_api.MetaArtifact;
import com.xm.thefourthfrequency.meta_api.MetaCapability;
import com.xm.thefourthfrequency.meta_api.MetaContext;
import com.xm.thefourthfrequency.meta_api.MetaEvent;
import com.xm.thefourthfrequency.meta_api.MetaExecution;
import com.xm.thefourthfrequency.meta_api.MetaPlatformAdapter;
import com.xm.thefourthfrequency.meta_api.MetaProcessHandle;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WindowsTrustedMetaAdapter implements MetaPlatformAdapter {
	private final String sessionId = "session_" + UUID.randomUUID().toString().replace("-", "");
	private final Path desktopRoot = Path.of(System.getProperty("user.home"), "Desktop", "The Fourth Frequency")
			.toAbsolutePath().normalize();
	private final Map<String, Path> ownedArtifacts = new LinkedHashMap<>();
	private final Map<String, Process> ownedProcesses = new LinkedHashMap<>();
	private Path sessionRoot;
	private WindowSnapshot windowSnapshot;
	private String originalClipboardText;
	private boolean clipboardChanged;
	private TrayIcon trayIcon;

	@Override
	public Set<MetaCapability> capabilities() {
		return EnumSet.of(MetaCapability.WINDOW_CONTROL, MetaCapability.OWNED_ARTIFACTS,
				MetaCapability.WHITELISTED_VIEWER, MetaCapability.TEXT_CLIPBOARD,
				MetaCapability.LOCAL_NOTIFICATION);
	}

	@Override
	public synchronized MetaExecution execute(MetaEvent event, MetaContext context) throws Exception {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
			throw new IllegalStateException("Windows trusted Meta adapter used on another platform");
		}
		List<MetaArtifact> artifacts = new ArrayList<>();
		try {
			applyWindowEvent(event, context.windowHandle());
			ensureOwnedSession();
			applyArtifactEvent(event, context, artifacts);
			if (event == MetaEvent.FINAL_BODY_AWAKENED) {
				setTemporaryClipboard("THE FOURTH FREQUENCY // continuity is being measured");
				launchOwnedViewer("construction_note", "notepad.exe", ownedArtifacts.get("construction_note"));
			} else if (event == MetaEvent.FOURTH_BAND_TERMINATED
					|| event == MetaEvent.PREVENTION_FAILED || event == MetaEvent.UNDISCOVERED_BETRAYAL) {
				restoreClipboard();
			}
			displayNotification(event);
			TheFourthFrequency.LOGGER.info("Trusted Meta event {} applied with {} owned artifact changes",
					event.name(), artifacts.size());
			return new MetaExecution(event, true, false, artifacts, processHandles());
		} catch (Exception failure) {
			restore();
			throw failure;
		}
	}

	private void ensureOwnedSession() throws IOException {
		if (sessionRoot != null) return;
		Files.createDirectories(desktopRoot);
		sessionRoot = desktopRoot.resolve(sessionId).normalize();
		if (!sessionRoot.getParent().equals(desktopRoot)) throw new IOException("Unsafe Meta session path");
		Files.createDirectory(sessionRoot);
	}

	private void applyArtifactEvent(MetaEvent event, MetaContext context, List<MetaArtifact> changed) throws IOException {
		switch (event) {
			case FINAL_BODY_AWAKENED -> {
				writeOwned("construction_note", MetaArtifact.Kind.TEXT, "construction-note.txt",
						"LOCAL CONTINUITY RECORD\nWorld: " + context.worldName() + "\nTime: " + context.localTimeMillis()
								+ "\nOS: Windows\nWindow: present\n", changed);
				writePng(changed);
				writeOwned("continuity_state", MetaArtifact.Kind.DATA, "state.dat",
						"event=" + event.wireId() + "\n", changed);
			}
			case TERMINAL_CAPTURED -> appendOwnedNote("\nA bound terminal was integrated as an organ.\n", changed);
			case FOURTH_BAND_TERMINATED -> {
				renameOwnedNote(changed);
				deleteOwned("continuity_state");
			}
			case PREVENTION_FAILED -> appendOwnedNote("\nPrevention failed. External continuity remains active.\n", changed);
			case UNDISCOVERED_BETRAYAL -> appendOwnedNote("\nConsent was never part of the construction plan.\n", changed);
		}
	}

	private void writeOwned(String id, MetaArtifact.Kind kind, String name, String content,
			List<MetaArtifact> changed) throws IOException {
		MetaArtifact artifact = new MetaArtifact(id, kind, name, true);
		Path path = resolveNewOwned(name);
		Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		ownedArtifacts.put(id, path);
		changed.add(artifact);
	}

	private void writePng(List<MetaArtifact> changed) throws IOException {
		MetaArtifact artifact = new MetaArtifact("carrier_image", MetaArtifact.Kind.PNG, "carrier.png", true);
		Path path = resolveNewOwned(artifact.relativeName());
		BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(13, 8, 19));
		graphics.fillRect(0, 0, 320, 180);
		graphics.setColor(new Color(116, 66, 150));
		graphics.fillOval(86, 52, 148, 76);
		graphics.setColor(new Color(222, 198, 241));
		graphics.drawString("THE FOURTH FREQUENCY", 82, 150);
		graphics.dispose();
		if (!ImageIO.write(image, "PNG", path.toFile())) throw new IOException("PNG writer unavailable");
		ownedArtifacts.put(artifact.artifactId(), path);
		changed.add(artifact);
	}

	private void appendOwnedNote(String text, List<MetaArtifact> changed) throws IOException {
		Path note = ownedArtifacts.get("construction_note");
		if (note == null || !isOwnedPath(note) || !Files.exists(note)) return;
		Files.writeString(note, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		changed.add(new MetaArtifact("construction_note", MetaArtifact.Kind.TEXT, note.getFileName().toString(), true));
	}

	private void renameOwnedNote(List<MetaArtifact> changed) throws IOException {
		Path note = ownedArtifacts.get("construction_note");
		if (note == null || !isOwnedPath(note) || !Files.exists(note)) return;
		Path renamed = resolveNewOwned("weak-resonance.txt");
		Files.move(note, renamed, StandardCopyOption.ATOMIC_MOVE);
		ownedArtifacts.put("construction_note", renamed);
		changed.add(new MetaArtifact("construction_note", MetaArtifact.Kind.TEXT, renamed.getFileName().toString(), true));
	}

	private void deleteOwned(String id) throws IOException {
		Path path = ownedArtifacts.get(id);
		if (path != null && isOwnedPath(path)) Files.deleteIfExists(path);
		ownedArtifacts.remove(id);
	}

	private Path resolveNewOwned(String name) throws IOException {
		Path candidate = sessionRoot.resolve(name).normalize();
		if (!candidate.getParent().equals(sessionRoot) || Files.exists(candidate)) {
			throw new IOException("Refused unsafe or pre-existing Meta artifact");
		}
		return candidate;
	}

	private boolean isOwnedPath(Path path) {
		return sessionRoot != null && path.normalize().getParent().equals(sessionRoot)
				&& ownedArtifacts.containsValue(path);
	}

	private void launchOwnedViewer(String id, String program, Path path) throws IOException {
		if (path == null || !isOwnedPath(path) || ownedProcesses.containsKey(id)) return;
		if (!program.equals("notepad.exe") && !program.equals("mspaint.exe")) {
			throw new IOException("Viewer is not in the fixed whitelist");
		}
		ownedProcesses.put(id, new ProcessBuilder(program, path.toString()).start());
	}

	private List<MetaProcessHandle> processHandles() {
		return ownedProcesses.entrySet().stream().map(entry -> new MetaProcessHandle(entry.getValue().pid(),
				entry.getKey(), sessionId)).toList();
	}

	private void applyWindowEvent(MetaEvent event, long handle) {
		if (handle == 0L) return;
		if (windowSnapshot == null) {
			int[] x = new int[1];
			int[] y = new int[1];
			int[] width = new int[1];
			int[] height = new int[1];
			GLFW.glfwGetWindowPos(handle, x, y);
			GLFW.glfwGetWindowSize(handle, width, height);
			windowSnapshot = new WindowSnapshot(handle, x[0], y[0], width[0], height[0]);
		}
		switch (event) {
			case FINAL_BODY_AWAKENED -> {
				GLFW.glfwSetWindowTitle(handle, "The Fourth Frequency // carrier located");
				GLFW.glfwSetWindowPos(handle, windowSnapshot.x() + 32, windowSnapshot.y() + 20);
				GLFW.glfwSetWindowSize(handle, Math.max(640, windowSnapshot.width() - 64),
						Math.max(360, windowSnapshot.height() - 40));
			}
			case TERMINAL_CAPTURED -> GLFW.glfwIconifyWindow(handle);
			case FOURTH_BAND_TERMINATED, PREVENTION_FAILED, UNDISCOVERED_BETRAYAL -> restoreWindow();
		}
	}

	private void setTemporaryClipboard(String text) {
		try {
			var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return;
			originalClipboardText = (String) clipboard.getData(DataFlavor.stringFlavor);
			clipboard.setContents(new StringSelection(text), null);
			clipboardChanged = true;
		} catch (Exception ignored) {
			clipboardChanged = false;
		}
	}

	private void restoreClipboard() {
		if (!clipboardChanged) return;
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new StringSelection(originalClipboardText == null ? "" : originalClipboardText), null);
		} catch (Exception ignored) {
			// Best effort on a clipboard temporarily locked by another application.
		} finally {
			clipboardChanged = false;
			originalClipboardText = null;
		}
	}

	private void displayNotification(MetaEvent event) throws AWTException {
		if (!SystemTray.isSupported()) return;
		if (trayIcon == null) {
			Image image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = (Graphics2D) image.getGraphics();
			graphics.setColor(new Color(116, 66, 150));
			graphics.fillRect(0, 0, 16, 16);
			graphics.dispose();
			trayIcon = new TrayIcon(image, "The Fourth Frequency");
			SystemTray.getSystemTray().add(trayIcon);
		}
		trayIcon.displayMessage("The Fourth Frequency", event.name().replace('_', ' '), TrayIcon.MessageType.NONE);
	}

	@Override
	public synchronized void restore() {
		restoreWindow();
		restoreClipboard();
		if (trayIcon != null && SystemTray.isSupported()) {
			SystemTray.getSystemTray().remove(trayIcon);
			trayIcon = null;
		}
		for (Process process : ownedProcesses.values()) {
			if (process.isAlive()) process.destroy();
		}
		ownedProcesses.clear();
	}

	private void restoreWindow() {
		if (windowSnapshot == null) return;
		GLFW.glfwRestoreWindow(windowSnapshot.handle());
		Minecraft.getInstance().updateTitle();
		GLFW.glfwSetWindowPos(windowSnapshot.handle(), windowSnapshot.x(), windowSnapshot.y());
		GLFW.glfwSetWindowSize(windowSnapshot.handle(), windowSnapshot.width(), windowSnapshot.height());
		windowSnapshot = null;
	}

	private record WindowSnapshot(long handle, int x, int y, int width, int height) {
	}
}
