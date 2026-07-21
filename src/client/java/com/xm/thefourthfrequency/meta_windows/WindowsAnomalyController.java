package com.xm.thefourthfrequency.meta_windows;

import com.mojang.blaze3d.platform.IconSet;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/** Windows-only, owned-process anomaly effects. It never sends input without PID/foreground verification. */
public final class WindowsAnomalyController {
	private static final String[] TITLES = { "测试", "窗口测试", "图标测试", "还没做完" };
	private static final int PULSE_ENTRANCE_TICKS = 12;
	private static final int PULSE_ACTIVE_UNTIL = 64;
	private static final int PULSE_TOTAL_TICKS = 80;
	private WindowSnapshot snapshot;
	private Mode mode = Mode.NONE;
	private int age;
	private Process notepad;
	private Process typer;
	private Path ownedNoteDirectory;
	private Path ownedNote;
	private Path ownedTyperScript;
	private WindowRect pulseExitStart;
	private boolean pulseTitleMutated;
	private boolean pulseIconMutated;
	private boolean pulseTitleRestored;
	private boolean pulseIconRestored;
	private boolean pulseEntranceAnimated;
	private boolean pulseExitAnimated;

	public boolean beginWindowPulse(Minecraft client) {
		if (mode != Mode.NONE || client.getWindow() == null) return false;
		snapshot = WindowSnapshot.capture(client);
		mode = Mode.WINDOW_PULSE;
		age = 0;
		pulseTitleMutated = false;
		pulseIconMutated = false;
		pulseTitleRestored = false;
		pulseIconRestored = false;
		pulseEntranceAnimated = false;
		pulseExitAnimated = false;
		pulseExitStart = null;
		prepareWindowed(client);
		pulseIconMutated = applyEyeIcon(client);
		return true;
	}

	public boolean beginDesktopPresence(Minecraft client) {
		if (mode != Mode.NONE || client.getWindow() == null || notepad != null && notepad.isAlive()) return false;
		snapshot = WindowSnapshot.capture(client);
		mode = Mode.DESKTOP_PRESENCE;
		age = 0;
		prepareWindowed(client);
		WorkArea area = WorkArea.primary();
		int width = Math.max(480, Math.round(area.width * 0.40F));
		int height = Math.max(270, Math.round(area.height * 0.40F));
		GLFW.glfwSetWindowSize(client.getWindow().handle(), width, height);
		GLFW.glfwSetWindowPos(client.getWindow().handle(), area.x + (area.width - width) / 2,
				area.y + (area.height - height) / 2);
		try {
			ownedNoteDirectory = Files.createTempDirectory("thefourthfrequency-desktop-").toAbsolutePath().normalize();
			ownedNote = ownedNoteDirectory.resolve("presence.txt").normalize();
			ownedTyperScript = ownedNoteDirectory.resolve("owned-unicode-typer.ps1").normalize();
			if (!ownedNote.getParent().equals(ownedNoteDirectory)) throw new java.io.IOException("Unsafe note path");
			if (!ownedTyperScript.getParent().equals(ownedNoteDirectory)) throw new java.io.IOException("Unsafe typer path");
			Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "meta/desktop_presence.txt");
			String noteText;
			try (InputStream input = client.getResourceManager().getResource(id).orElseThrow().open()) {
				noteText = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			}
			Files.createFile(ownedNote);
			Files.writeString(ownedTyperScript, ownedUnicodeTyperScript(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW);
			notepad = new ProcessBuilder("notepad.exe", ownedNote.toString()).start();
			startOwnedUnicodeTyper(notepad.pid(), noteText);
			return true;
		} catch (Exception failure) {
			TheFourthFrequency.LOGGER.warn("Desktop presence safely downgraded: owned Notepad was unavailable");
			closeOwnedNotepad();
			restoreWindow(client);
			mode = Mode.NONE;
			return false;
		}
	}

	public void tick(Minecraft client) {
		if (mode == Mode.NONE || snapshot == null || client.getWindow() == null) return;
		age++;
		if (mode == Mode.WINDOW_PULSE) {
			WorkArea area = WorkArea.primary();
			WindowRect original = snapshot.animationRect(area);
			if (age <= PULSE_ENTRANCE_TICKS) {
				WindowRect contracted = original.scaledAroundCenter(0.72D, area);
				applyWindowRect(client, WindowRect.lerp(original, contracted,
						smooth(age / (double) PULSE_ENTRANCE_TICKS)));
				pulseEntranceAnimated |= age >= 2;
			} else if (age <= PULSE_ACTIVE_UNTIL) {
				double wave = 0.5D + 0.5D * Math.sin(age * 0.42D);
				int width = Math.clamp((int) (area.width * (0.48D + wave * 0.30D)), 480, area.width);
				int height = Math.clamp((int) (area.height * (0.48D + (1.0D - wave) * 0.28D)), 270, area.height);
				int x = area.x + (int) ((area.width - width) * (0.5D + 0.45D * Math.sin(age * 0.26D)));
				int y = area.y + (int) ((area.height - height) * (0.5D + 0.45D * Math.cos(age * 0.34D)));
				applyWindowRect(client, new WindowRect(x, y, width, height));
			} else {
				if (pulseExitStart == null) pulseExitStart = WindowRect.capture(client);
				double progress = smooth((age - PULSE_ACTIVE_UNTIL)
						/ (double) (PULSE_TOTAL_TICKS - PULSE_ACTIVE_UNTIL));
				applyWindowRect(client, WindowRect.lerp(pulseExitStart, original, progress));
				pulseExitAnimated |= age >= PULSE_ACTIVE_UNTIL + 2;
			}
			if (age > PULSE_ENTRANCE_TICKS && age <= PULSE_ACTIVE_UNTIL && age % 10 == 1) {
				GLFW.glfwSetWindowTitle(client.getWindow().handle(), TITLES[(age / 10) % TITLES.length]);
				pulseTitleMutated = true;
			}
		}
		if (age >= PULSE_TOTAL_TICKS) {
			restoreWindow(client);
			mode = Mode.NONE;
		}
	}

	public void finishAnomaly(Minecraft client, boolean interrupted) {
		if (snapshot != null) restoreWindow(client);
		mode = Mode.NONE;
		if (interrupted) closeOwnedNotepad();
	}

	public void restoreAll(Minecraft client) {
		finishAnomaly(client, true);
		closeOwnedNotepad();
	}

	public long controlledNotepadPidForTesting() {
		return notepad == null ? -1L : notepad.pid();
	}

	public boolean unicodeTyperSucceededForTesting() {
		return typer != null && !typer.isAlive() && typer.exitValue() == 0;
	}

	public int unicodeTyperExitCodeForTesting() {
		return typer == null ? -1000 : typer.isAlive() ? -1001 : typer.exitValue();
	}

	public boolean unicodeTyperFinishedForTesting() { return typer != null && !typer.isAlive(); }

	public boolean windowPulseLifecycleVerifiedForTesting() {
		return pulseEntranceAnimated && pulseExitAnimated && pulseTitleMutated && pulseIconMutated
				&& pulseTitleRestored && pulseIconRestored;
	}

	private void prepareWindowed(Minecraft client) {
		if (client.getWindow().isFullscreen()) client.getWindow().toggleFullScreen();
		GLFW.glfwRestoreWindow(client.getWindow().handle());
	}

	private void restoreWindow(Minecraft client) {
		if (snapshot == null || client.getWindow() == null) return;
		boolean restoringPulse = mode == Mode.WINDOW_PULSE;
		snapshot.restore(client);
		client.updateTitle();
		if (restoringPulse) pulseTitleRestored = true;
		try {
			client.getWindow().setIcon(client.getVanillaPackResources(), IconSet.RELEASE);
			if (restoringPulse) pulseIconRestored = true;
		}
		catch (java.io.IOException ignored) { }
		snapshot = null;
	}

	private void closeOwnedNotepad() {
		if (typer != null && typer.isAlive()) typer.destroyForcibly();
		typer = null;
		if (notepad != null) {
			// Windows 11 Notepad may place the actual window in a direct child process.
			// Descendants are rooted at the launcher handle we own, so unrelated Notepad sessions are untouched.
			notepad.toHandle().descendants().forEach(handle -> {
				if (handle.isAlive()) handle.destroyForcibly();
			});
			if (notepad.isAlive()) notepad.destroyForcibly();
		}
		notepad = null;
		try {
			if (ownedNote != null && ownedNote.getParent().equals(ownedNoteDirectory)) Files.deleteIfExists(ownedNote);
			if (ownedTyperScript != null && ownedTyperScript.getParent().equals(ownedNoteDirectory))
				Files.deleteIfExists(ownedTyperScript);
			if (ownedNoteDirectory != null) Files.deleteIfExists(ownedNoteDirectory);
		} catch (java.io.IOException ignored) {
			// Best effort: both paths were created and owned by this controller.
		}
		ownedNote = null;
		ownedTyperScript = null;
		ownedNoteDirectory = null;
	}

	private static void applyWindowRect(Minecraft client, WindowRect rect) {
		long handle = client.getWindow().handle();
		GLFW.glfwSetWindowSize(handle, rect.width, rect.height);
		GLFW.glfwSetWindowPos(handle, rect.x, rect.y);
	}

	private static double smooth(double value) {
		double clamped = Math.clamp(value, 0.0D, 1.0D);
		return clamped * clamped * (3.0D - 2.0D * clamped);
	}

	private void startOwnedUnicodeTyper(long rootPid, String text) throws java.io.IOException {
		String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
		Path windows = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"))
				.toAbsolutePath().normalize();
		Path powershell = windows.resolve("System32/WindowsPowerShell/v1.0/powershell.exe").normalize();
		if (!powershell.startsWith(windows) || !Files.isRegularFile(powershell))
			throw new java.io.IOException("Trusted Windows PowerShell was unavailable");
		typer = new ProcessBuilder(powershell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
				"-ExecutionPolicy", "Bypass", "-File", ownedTyperScript.toString(),
				Long.toString(rootPid), encoded).start();
	}

	private boolean applyEyeIcon(Minecraft client) {
		Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
				"textures/gui/anomaly/eye_window.png");
		try (InputStream input = client.getResourceManager().getResource(id).orElseThrow().open()) {
			BufferedImage image = ImageIO.read(input);
			if (image == null) return false;
			ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
			for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				pixels.put((byte) (argb >> 16)); pixels.put((byte) (argb >> 8));
				pixels.put((byte) argb); pixels.put((byte) (argb >> 24));
			}
			pixels.flip();
			try (GLFWImage icon = GLFWImage.malloc(); GLFWImage.Buffer icons = GLFWImage.malloc(1)) {
				icon.set(image.getWidth(), image.getHeight(), pixels); icons.put(0, icon);
				GLFW.glfwSetWindowIcon(client.getWindow().handle(), icons);
			}
			return true;
		} catch (Exception ignored) { return false; }
	}

	private static String ownedUnicodeTyperScript() {
		return """
				param([int]$RootPid, [string]$TextBase64)
				$ErrorActionPreference = 'Stop'
				Add-Type -TypeDefinition @'
				using System;
				using System.Collections.Generic;
				using System.Runtime.InteropServices;
				using System.Threading;
				public static class OwnedUnicode {
				  const uint TH32CS_SNAPPROCESS = 0x00000002;
				  const uint INPUT_KEYBOARD = 1;
				  const uint KEYEVENTF_KEYUP = 0x0002;
				  const uint KEYEVENTF_UNICODE = 0x0004;
				  const ushort VK_CONTROL = 0x11;
				  const ushort VK_SHIFT = 0x10;
				  const ushort VK_OEM_PLUS = 0xBB;
				  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
				  struct PROCESSENTRY32 { public uint dwSize; public uint cntUsage; public uint th32ProcessID; public IntPtr th32DefaultHeapID; public uint th32ModuleID; public uint cntThreads; public uint th32ParentProcessID; public int pcPriClassBase; public uint dwFlags; [MarshalAs(UnmanagedType.ByValTStr, SizeConst=260)] public string szExeFile; }
				  [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx,dy; public uint mouseData,dwFlags,time; public UIntPtr dwExtraInfo; }
				  [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk,wScan; public uint dwFlags,time; public UIntPtr dwExtraInfo; }
				  [StructLayout(LayoutKind.Sequential)] struct HARDWAREINPUT { public uint uMsg; public ushort wParamL,wParamH; }
				  [StructLayout(LayoutKind.Explicit)] struct INPUTUNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; [FieldOffset(0)] public HARDWAREINPUT hi; }
				  [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public INPUTUNION U; }
				  delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
				  [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr CreateToolhelp32Snapshot(uint flags, uint pid);
				  [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool Process32FirstW(IntPtr snapshot, ref PROCESSENTRY32 entry);
				  [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool Process32NextW(IntPtr snapshot, ref PROCESSENTRY32 entry);
				  [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
				  [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
				  [DllImport("user32.dll")] static extern bool EnumWindows(EnumWindowsProc callback, IntPtr param);
				  [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hWnd);
				  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
				  [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
				  [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr hWnd);
				  [DllImport("user32.dll")] static extern bool BringWindowToTop(IntPtr hWnd);
				  [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr hWnd, int command);
				  [DllImport("user32.dll")] static extern bool AttachThreadInput(uint first, uint second, bool attach);
				  [DllImport("user32.dll", SetLastError=true)] static extern uint SendInput(uint count, INPUT[] inputs, int size);
				  static HashSet<uint> OwnedTree(uint root) {
				    var parents = new List<Tuple<uint,uint>>();
				    IntPtr snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
				    if (snapshot != new IntPtr(-1)) {
				      try {
				        var entry = new PROCESSENTRY32(); entry.dwSize = (uint)Marshal.SizeOf(typeof(PROCESSENTRY32));
				        if (Process32FirstW(snapshot, ref entry)) do { parents.Add(Tuple.Create(entry.th32ProcessID, entry.th32ParentProcessID)); entry.dwSize = (uint)Marshal.SizeOf(typeof(PROCESSENTRY32)); } while (Process32NextW(snapshot, ref entry));
				      } finally { CloseHandle(snapshot); }
				    }
				    var owned = new HashSet<uint>(); owned.Add(root);
				    bool changed; do { changed = false; foreach (var pair in parents) if (owned.Contains(pair.Item2) && owned.Add(pair.Item1)) changed = true; } while (changed);
				    return owned;
				  }
				  static IntPtr FindWindow(HashSet<uint> owned) {
				    IntPtr found = IntPtr.Zero;
				    EnumWindows(delegate(IntPtr h, IntPtr ignored) { uint pid; GetWindowThreadProcessId(h, out pid); if (IsWindowVisible(h) && owned.Contains(pid)) { found = h; return false; } return true; }, IntPtr.Zero);
				    return found;
				  }
				  static bool OwnedWindow(HashSet<uint> owned, IntPtr h) { uint pid; GetWindowThreadProcessId(h, out pid); return h != IntPtr.Zero && owned.Contains(pid); }
				  static bool FocusOwned(HashSet<uint> owned, IntPtr window) {
				    if (!OwnedWindow(owned, window)) return false;
				    if (GetForegroundWindow() == window) return true;
				    uint owner; uint targetThread = GetWindowThreadProcessId(window, out owner); uint currentThread = GetCurrentThreadId();
				    IntPtr previous = GetForegroundWindow(); uint ignored; uint foregroundThread = previous == IntPtr.Zero ? 0 : GetWindowThreadProcessId(previous, out ignored);
				    bool attachTarget = targetThread != 0 && targetThread != currentThread && AttachThreadInput(currentThread, targetThread, true);
				    bool attachForeground = foregroundThread != 0 && foregroundThread != currentThread && foregroundThread != targetThread && AttachThreadInput(currentThread, foregroundThread, true);
				    try { ShowWindow(window, 5); BringWindowToTop(window); SetForegroundWindow(window); Thread.Sleep(80); }
				    finally { if (attachForeground) AttachThreadInput(currentThread, foregroundThread, false); if (attachTarget) AttachThreadInput(currentThread, targetThread, false); }
				    return GetForegroundWindow() == window && OwnedWindow(owned, window);
				  }
				  static INPUT VirtualKey(ushort key, bool release) {
				    var input = new INPUT(); input.type = INPUT_KEYBOARD; input.U.ki.wVk = key;
				    input.U.ki.dwFlags = release ? KEYEVENTF_KEYUP : 0; return input;
				  }
				  static bool ZoomToMaximum(HashSet<uint> owned, IntPtr window) {
				    for (int step = 0; step < 64; step++) {
				      if (!FocusOwned(owned, window)) return false;
				      var keys = new INPUT[] { VirtualKey(VK_CONTROL, false), VirtualKey(VK_SHIFT, false),
				        VirtualKey(VK_OEM_PLUS, false), VirtualKey(VK_OEM_PLUS, true),
				        VirtualKey(VK_SHIFT, true), VirtualKey(VK_CONTROL, true) };
				      if (SendInput((uint)keys.Length, keys, Marshal.SizeOf(typeof(INPUT))) != (uint)keys.Length) return false;
				      Thread.Sleep(12);
				    }
				    return true;
				  }
				  public static int FocusAndType(int rootPid, string text) {
				    uint root = (uint)rootPid; IntPtr window = IntPtr.Zero; HashSet<uint> owned = null;
				    DateTime deadline = DateTime.UtcNow.AddSeconds(8);
				    while (DateTime.UtcNow < deadline && window == IntPtr.Zero) { owned = OwnedTree(root); window = FindWindow(owned); if (window == IntPtr.Zero) Thread.Sleep(100); }
				    if (window == IntPtr.Zero || owned == null || !OwnedWindow(owned, window)) return 1;
				    if (!FocusOwned(owned, window)) return 2;
				    if (!ZoomToMaximum(owned, window)) return 5;
				    foreach (char character in text) {
				      // The game may briefly reclaim focus; reacquire only this already-verified owned window.
				      if (!FocusOwned(owned, window)) return 4;
				      var down = new INPUT(); down.type = INPUT_KEYBOARD; down.U.ki.wScan = character; down.U.ki.dwFlags = KEYEVENTF_UNICODE;
				      var up = down; up.U.ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;
				      if (SendInput(2, new INPUT[] { down, up }, Marshal.SizeOf(typeof(INPUT))) != 2) return 3;
				      Thread.Sleep(character == '\\r' || character == '\\n' ? 110 : 34);
				    }
				    return 0;
				  }
				}
				'@
				$text = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($TextBase64))
				exit [OwnedUnicode]::FocusAndType($RootPid, $text)
				""";
	}

	private enum Mode { NONE, WINDOW_PULSE, DESKTOP_PRESENCE }

	private record WorkArea(int x, int y, int width, int height) {
		private static WorkArea primary() {
			long monitor = GLFW.glfwGetPrimaryMonitor();
			int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
			GLFW.glfwGetMonitorWorkarea(monitor, x, y, width, height);
			return new WorkArea(x[0], y[0], Math.max(640, width[0]), Math.max(360, height[0]));
		}
	}

	private record WindowRect(int x, int y, int width, int height) {
		private static WindowRect capture(Minecraft client) {
			long handle = client.getWindow().handle();
			int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
			GLFW.glfwGetWindowPos(handle, x, y); GLFW.glfwGetWindowSize(handle, width, height);
			return new WindowRect(x[0], y[0], width[0], height[0]);
		}

		private WindowRect scaledAroundCenter(double scale, WorkArea area) {
			int scaledWidth = Math.clamp((int) Math.round(width * scale), 480, area.width);
			int scaledHeight = Math.clamp((int) Math.round(height * scale), 270, area.height);
			return new WindowRect(x + (width - scaledWidth) / 2, y + (height - scaledHeight) / 2,
					scaledWidth, scaledHeight);
		}

		private static WindowRect lerp(WindowRect from, WindowRect to, double amount) {
			return new WindowRect(interpolate(from.x, to.x, amount), interpolate(from.y, to.y, amount),
					interpolate(from.width, to.width, amount), interpolate(from.height, to.height, amount));
		}

		private static int interpolate(int from, int to, double amount) {
			return (int) Math.round(from + (to - from) * amount);
		}
	}

	private record WindowSnapshot(boolean fullscreen, boolean maximized, int x, int y, int width, int height) {
		private static WindowSnapshot capture(Minecraft client) {
			long handle = client.getWindow().handle();
			int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
			GLFW.glfwGetWindowPos(handle, x, y); GLFW.glfwGetWindowSize(handle, width, height);
			return new WindowSnapshot(client.getWindow().isFullscreen(),
					GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
					x[0], y[0], width[0], height[0]);
		}
		private WindowRect animationRect(WorkArea area) {
			return fullscreen ? new WindowRect(area.x, area.y, area.width, area.height)
					: new WindowRect(x, y, width, height);
		}
		private void restore(Minecraft client) {
			long handle = client.getWindow().handle();
			if (fullscreen != client.getWindow().isFullscreen()) client.getWindow().toggleFullScreen();
			if (!fullscreen) {
				GLFW.glfwRestoreWindow(handle); GLFW.glfwSetWindowPos(handle, x, y);
				GLFW.glfwSetWindowSize(handle, width, height); if (maximized) GLFW.glfwMaximizeWindow(handle);
			}
		}
	}
}
