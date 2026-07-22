package com.xm.thefourthfrequency.meta_windows;

import com.mojang.blaze3d.platform.IconSet;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Windows-only write-ahead transaction for the fixed failure wallpaper and one owned Notepad file.
 * No path, text, executable or script is accepted from the network.
 */
public final class WindowsEndingMetaTransaction {
	private static final String FIXED_TEXT = "我永远在盯着你.......";
	private static final String WALLPAPER_RESOURCE =
			"/assets/thefourthfrequency/textures/gui/ending/world_interface_failure.png";
	private static final String DIRECTORY_NAME = "thefourthfrequency-ending";
	private static final String OWNED_DIRECTORY_NAME = "windows-owned";
	private static final String MANIFEST_NAME = "windows-ending.properties";
	private static final String FAILURE_WALLPAPER_NAME = "failure-wallpaper.png";
	private static final String NOTE_NAME = "ending-note.txt";
	private static final String WALLPAPER_SCRIPT_NAME = "wallpaper-transaction.ps1";
	private static final String NOTEPAD_SCRIPT_NAME = "owned-notepad.ps1";
	private static final List<String> BACKUP_NAMES = List.of(
			"original-wallpaper.png", "original-wallpaper.jpg", "original-wallpaper.jpeg",
			"original-wallpaper.bmp", "original-wallpaper.gif", "original-wallpaper.img");
	private static CompletableFuture<Boolean> applyInFlight;
	private static CompletableFuture<Boolean> restoreInFlight;

	private WindowsEndingMetaTransaction() {
	}

	public enum State {
		PREPARED,
		APPLIED,
		NOTEPAD_TYPED,
		LOCKED
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	public static boolean hasPendingTransaction() {
		return Files.isRegularFile(manifestPath());
	}

	public static synchronized CompletableFuture<Boolean> applyAsync(Minecraft client, UUID encounterId) {
		if (!isWindows() || client.getWindow() == null || hasPendingTransaction()) {
			return CompletableFuture.completedFuture(false);
		}
		if (applyInFlight != null && !applyInFlight.isDone()) return applyInFlight;
		WindowState window = WindowState.capture(client);
		applyInFlight = CompletableFuture.supplyAsync(() -> applyWorker(encounterId, window));
		return applyInFlight;
	}

	public static synchronized CompletableFuture<Boolean> restoreAsync(Minecraft client) {
		if (!hasPendingTransaction()) return CompletableFuture.completedFuture(true);
		if (restoreInFlight != null && !restoreInFlight.isDone()) return restoreInFlight;
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		restoreInFlight = result;
		CompletableFuture.supplyAsync(WindowsEndingMetaTransaction::restoreExternalWorker)
				.whenComplete((recovery, failure) -> client.execute(() -> {
					if (failure != null || recovery == null || !recovery.externalRestored()) {
						TheFourthFrequency.LOGGER.error("Windows ending recovery did not complete", failure);
						result.complete(false);
						return;
					}
					try {
						recovery.window().restore(client);
						client.updateTitle();
						client.getWindow().setIcon(client.getVanillaPackResources(), IconSet.RELEASE);
						cleanupOwnedFiles();
						result.complete(true);
					} catch (Exception exception) {
						TheFourthFrequency.LOGGER.error("Could not restore the Minecraft window after failure", exception);
						result.complete(false);
					}
				}));
		return result;
	}

	public static State stateForTesting() {
		Properties properties = readManifest();
		if (properties == null) return null;
		try { return State.valueOf(properties.getProperty("state", "")); }
		catch (IllegalArgumentException ignored) { return null; }
	}

	public static Path manifestPathForTesting() {
		return manifestPath();
	}

	private static boolean applyWorker(UUID encounterId, WindowState window) {
		Process ownedNotepad = null;
		try {
			Path root = endingDirectory();
			Path owned = ownedDirectory();
			Files.createDirectories(owned);
			Path wallpaperScript = owned.resolve(WALLPAPER_SCRIPT_NAME).normalize();
			Path notepadScript = owned.resolve(NOTEPAD_SCRIPT_NAME).normalize();
			Path failureWallpaper = owned.resolve(FAILURE_WALLPAPER_NAME).normalize();
			Path note = owned.resolve(NOTE_NAME).normalize();
			requireOwned(wallpaperScript);
			requireOwned(notepadScript);
			requireOwned(failureWallpaper);
			requireOwned(note);
			Files.writeString(wallpaperScript, wallpaperScript(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			Files.writeString(notepadScript, notepadScript(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			try (InputStream input = WindowsEndingMetaTransaction.class.getResourceAsStream(WALLPAPER_RESOURCE)) {
				if (input == null) throw new IOException("Missing fixed ending wallpaper resource");
				Files.copy(input, failureWallpaper, StandardCopyOption.REPLACE_EXISTING);
			}
			WallpaperState wallpaper = captureWallpaper(wallpaperScript);
			String backupName = backupOriginalWallpaper(wallpaper.path());
			Properties manifest = new Properties();
			manifest.setProperty("version", "1");
			manifest.setProperty("state", State.PREPARED.name());
			manifest.setProperty("encounter", encounterId.toString());
			manifest.setProperty("createdAt", Long.toString(System.currentTimeMillis()));
			manifest.setProperty("wallpaper.path64", encode(wallpaper.path()));
			manifest.setProperty("wallpaper.style", wallpaper.style());
			manifest.setProperty("wallpaper.tile", wallpaper.tile());
			manifest.setProperty("wallpaper.backup", backupName);
			window.write(manifest);
			writeManifest(manifest);

			runPowerShell(wallpaperScript, "SET", failureWallpaper.toString(), "10", "0");
			advance(manifest, State.APPLIED);
			Files.writeString(note, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
			ownedNotepad = new ProcessBuilder("notepad.exe", note.toString()).start();
			long launcherPid = ownedNotepad.pid();
			long launcherStart = ownedNotepad.toHandle().info().startInstant()
					.map(Instant::toEpochMilli).orElse(System.currentTimeMillis());
			manifest.setProperty("notepad.pid", Long.toString(launcherPid));
			manifest.setProperty("notepad.start", Long.toString(launcherStart));
			manifest.setProperty("notepad.note", NOTE_NAME);
			writeManifest(manifest);
			List<String> output = runPowerShell(notepadScript, Long.toString(launcherPid),
					encode(FIXED_TEXT), note.toString());
			long verifiedPid = parseLong(output, "PID=");
			long verifiedStart = parseLong(output, "START=");
			if (verifiedPid <= 0L || verifiedStart <= 0L
					|| !FIXED_TEXT.equals(Files.readString(note, StandardCharsets.UTF_8))) {
				throw new IOException("Owned Notepad verification did not produce the fixed text");
			}
			manifest.setProperty("notepad.pid", Long.toString(verifiedPid));
			manifest.setProperty("notepad.start", Long.toString(verifiedStart));
			advance(manifest, State.NOTEPAD_TYPED);
			advance(manifest, State.LOCKED);
			return true;
		} catch (Exception exception) {
			if (ownedNotepad != null) {
				Process failedNotepad = ownedNotepad;
				failedNotepad.toHandle().descendants().forEach(handle -> {
					if (handle.isAlive()) handle.destroy();
				});
				if (failedNotepad.isAlive()) failedNotepad.destroy();
			}
			TheFourthFrequency.LOGGER.warn("Windows failure presentation safely downgraded to the in-game fallback",
					exception);
			return false;
		}
	}

	private static Recovery restoreExternalWorker() {
		Properties manifest = readManifest();
		if (manifest == null) return new Recovery(WindowState.defaults(), !Files.exists(manifestPath()));
		try {
			if (!"1".equals(manifest.getProperty("version"))) throw new IOException("Unknown transaction version");
			State state = State.valueOf(manifest.getProperty("state", ""));
			WindowState window = WindowState.read(manifest);
			stopVerifiedOwnedNotepad(manifest);
			// PREPARED is write-ahead: wallpaper application may have happened before APPLIED reached disk.
			if (state.ordinal() >= State.PREPARED.ordinal()) {
				Path wallpaperScript = ownedDirectory().resolve(WALLPAPER_SCRIPT_NAME).normalize();
				requireOwned(wallpaperScript);
				String original = decode(manifest.getProperty("wallpaper.path64", ""));
				String restoreValue = "";
				Path restorePath = original.isBlank() ? null : Path.of(original).toAbsolutePath().normalize();
				if (restorePath != null && Files.isRegularFile(restorePath)) {
					restoreValue = restorePath.toString();
				} else if (!original.isBlank()) {
					String backupName = manifest.getProperty("wallpaper.backup", "");
					if (!BACKUP_NAMES.contains(backupName)) throw new IOException("Unsafe wallpaper backup name");
					restorePath = ownedDirectory().resolve(backupName).normalize();
					requireOwned(restorePath);
					restoreValue = restorePath.toString();
				}
				if (!restoreValue.isBlank() && !Files.isRegularFile(Path.of(restoreValue))) {
					throw new IOException("Original wallpaper is unavailable");
				}
				String style = validatedDesktopValue(manifest.getProperty("wallpaper.style"), "0");
				String tile = validatedDesktopValue(manifest.getProperty("wallpaper.tile"), "0");
				runPowerShell(wallpaperScript, "SET", restoreValue, style, tile);
			}
			return new Recovery(window, true);
		} catch (Exception exception) {
			TheFourthFrequency.LOGGER.error("Could not roll back the Windows ending transaction", exception);
			return new Recovery(WindowState.defaults(), false);
		}
	}

	private static WallpaperState captureWallpaper(Path script) throws IOException, InterruptedException {
		List<String> output = runPowerShell(script, "GET", "", "", "");
		String path = decode(parseString(output, "PATH64="));
		return new WallpaperState(path,
				validatedDesktopValue(parseString(output, "STYLE="), "0"),
				validatedDesktopValue(parseString(output, "TILE="), "0"));
	}

	private static String backupOriginalWallpaper(String original) throws IOException {
		if (original.isBlank()) return "";
		Path source;
		try { source = Path.of(original).toAbsolutePath().normalize(); }
		catch (RuntimeException invalid) { return ""; }
		if (!Files.isRegularFile(source)) return "";
		String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);
		String suffix = lower.endsWith(".png") ? ".png" : lower.endsWith(".jpeg") ? ".jpeg"
				: lower.endsWith(".jpg") ? ".jpg" : lower.endsWith(".bmp") ? ".bmp"
				: lower.endsWith(".gif") ? ".gif" : ".img";
		String name = "original-wallpaper" + suffix;
		Path backup = ownedDirectory().resolve(name).normalize();
		requireOwned(backup);
		Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
		return name;
	}

	private static void stopVerifiedOwnedNotepad(Properties manifest) {
		try {
			long pid = Long.parseLong(manifest.getProperty("notepad.pid", "-1"));
			long expectedStart = Long.parseLong(manifest.getProperty("notepad.start", "-1"));
			if (pid <= 0L || expectedStart <= 0L) return;
			ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
			if (handle == null || !handle.isAlive()) return;
			ProcessHandle.Info info = handle.info();
			long actualStart = info.startInstant().map(Instant::toEpochMilli).orElse(-1L);
			String command = info.command().orElse("").toLowerCase(Locale.ROOT);
			if (Math.abs(actualStart - expectedStart) > 2_000L || !command.endsWith("notepad.exe")) {
				TheFourthFrequency.LOGGER.warn("Refused to stop PID {} because owned Notepad identity was not provable", pid);
				return;
			}
			handle.destroy();
		} catch (RuntimeException exception) {
			TheFourthFrequency.LOGGER.warn("Could not verify the owned Notepad process during recovery", exception);
		}
	}

	private static void cleanupOwnedFiles() throws IOException {
		Path owned = ownedDirectory();
		Files.deleteIfExists(owned.resolve(NOTE_NAME));
		Files.deleteIfExists(owned.resolve(FAILURE_WALLPAPER_NAME));
		Files.deleteIfExists(owned.resolve(WALLPAPER_SCRIPT_NAME));
		Files.deleteIfExists(owned.resolve(NOTEPAD_SCRIPT_NAME));
		for (String backup : BACKUP_NAMES) Files.deleteIfExists(owned.resolve(backup));
		Files.deleteIfExists(owned);
		Files.deleteIfExists(manifestPath());
	}

	private static List<String> runPowerShell(Path script, String... arguments)
			throws IOException, InterruptedException {
		Path powershell = trustedPowerShell();
		List<String> command = new ArrayList<>();
		command.add(powershell.toString());
		command.add("-NoLogo");
		command.add("-NoProfile");
		command.add("-NonInteractive");
		command.add("-ExecutionPolicy");
		command.add("Bypass");
		command.add("-File");
		command.add(script.toString());
		command.addAll(List.of(arguments));
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		if (!process.waitFor(30L, TimeUnit.SECONDS)) {
			process.destroy();
			throw new IOException("Timed out waiting for an owned Windows helper");
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		process.getInputStream().transferTo(output);
		String text = output.toString(StandardCharsets.UTF_8);
		if (process.exitValue() != 0) throw new IOException("Owned Windows helper failed: " + text.strip());
		return text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
	}

	private static Path trustedPowerShell() throws IOException {
		Path windows = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"))
				.toAbsolutePath().normalize();
		Path powershell = windows.resolve("System32/WindowsPowerShell/v1.0/powershell.exe").normalize();
		if (!powershell.startsWith(windows) || !Files.isRegularFile(powershell)) {
			throw new IOException("Trusted Windows PowerShell is unavailable");
		}
		return powershell;
	}

	private static void advance(Properties manifest, State state) throws IOException {
		manifest.setProperty("state", state.name());
		writeManifest(manifest);
	}

	private static Properties readManifest() {
		if (!Files.isRegularFile(manifestPath())) return null;
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(manifestPath())) {
			properties.load(input);
			return properties;
		} catch (IOException exception) {
			TheFourthFrequency.LOGGER.error("Could not read the Windows ending manifest", exception);
			return null;
		}
	}

	private static void writeManifest(Properties manifest) throws IOException {
		Path directory = endingDirectory();
		Path temporary = directory.resolve(MANIFEST_NAME + ".tmp").normalize();
		Files.createDirectories(directory);
		try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			manifest.store(output, "The Fourth Frequency Windows ending transaction");
		}
		try { Files.move(temporary, manifestPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
		catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, manifestPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static long parseLong(List<String> lines, String prefix) throws IOException {
		try { return Long.parseLong(parseString(lines, prefix)); }
		catch (NumberFormatException exception) { throw new IOException("Invalid helper number " + prefix, exception); }
	}

	private static String parseString(List<String> lines, String prefix) throws IOException {
		return lines.stream().filter(line -> line.startsWith(prefix)).map(line -> line.substring(prefix.length()))
				.findFirst().orElseThrow(() -> new IOException("Missing helper output " + prefix));
	}

	private static String encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String value) throws IOException {
		try { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
		catch (IllegalArgumentException exception) { throw new IOException("Invalid manifest base64", exception); }
	}

	private static String validatedDesktopValue(String value, String fallback) throws IOException {
		String candidate = value == null || value.isBlank() ? fallback : value;
		if (!candidate.matches("[0-9]{1,2}")) throw new IOException("Invalid wallpaper style value");
		return candidate;
	}

	private static void requireOwned(Path path) throws IOException {
		if (!path.toAbsolutePath().normalize().getParent().equals(ownedDirectory())) {
			throw new IOException("Refused a path outside the fixed ending transaction directory");
		}
	}

	private static Path endingDirectory() {
		return FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY_NAME).toAbsolutePath().normalize();
	}

	private static Path ownedDirectory() { return endingDirectory().resolve(OWNED_DIRECTORY_NAME).normalize(); }
	private static Path manifestPath() { return endingDirectory().resolve(MANIFEST_NAME).normalize(); }

	private static String wallpaperScript() {
		return """
				param([ValidateSet('GET','SET')][string]$Mode, [string]$Path, [string]$Style, [string]$Tile)
				$ErrorActionPreference = 'Stop'
				Add-Type -TypeDefinition @'
				using System;
				using System.Text;
				using System.Runtime.InteropServices;
				public static class TffWallpaper {
				  [DllImport("user32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
				  static extern bool SystemParametersInfo(int action, int param, StringBuilder value, int flags);
				  [DllImport("user32.dll", EntryPoint="SystemParametersInfoW", CharSet=CharSet.Unicode, SetLastError=true)]
				  static extern bool SetSystemParametersInfo(int action, int param, string value, int flags);
				  public static string Get() { var value = new StringBuilder(32768); if (!SystemParametersInfo(0x73, value.Capacity, value, 0)) throw new System.ComponentModel.Win32Exception(); return value.ToString(); }
				  public static void Set(string path) { if (!SetSystemParametersInfo(20, 0, path, 3)) throw new System.ComponentModel.Win32Exception(); }
				}
				'@
				$key = 'HKCU:\\Control Panel\\Desktop'
				if ($Mode -eq 'GET') {
				  $desktop = Get-ItemProperty -LiteralPath $key
				  $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([TffWallpaper]::Get()))
				  Write-Output ('PATH64=' + $encoded)
				  Write-Output ('STYLE=' + [string]$desktop.WallpaperStyle)
				  Write-Output ('TILE=' + [string]$desktop.TileWallpaper)
				  exit 0
				}
				if ($Path -ne '' -and -not (Test-Path -LiteralPath $Path -PathType Leaf)) { exit 2 }
				Set-ItemProperty -LiteralPath $key -Name WallpaperStyle -Value $Style
				Set-ItemProperty -LiteralPath $key -Name TileWallpaper -Value $Tile
				[TffWallpaper]::Set($Path)
				""";
	}

	private static String notepadScript() {
		return """
				param([int]$RootPid, [string]$TextBase64, [string]$NotePath)
				$ErrorActionPreference = 'Stop'
				Add-Type -TypeDefinition @'
				using System;
				using System.Collections.Generic;
				using System.Runtime.InteropServices;
				using System.Threading;
				public static class TffOwnedNotepad {
				  const uint SNAP = 2, INPUT_KEYBOARD = 1, KEYUP = 2, UNICODE = 4;
				  const ushort VK_CONTROL = 0x11, VK_S = 0x53;
				  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct PE { public uint size,usage,pid; public IntPtr heap; public uint module,threads,parent; public int priority; public uint flags; [MarshalAs(UnmanagedType.ByValTStr,SizeConst=260)] public string exe; }
				  [StructLayout(LayoutKind.Sequential)] struct MI { public int dx,dy; public uint data,flags,time; public UIntPtr extra; }
				  [StructLayout(LayoutKind.Sequential)] struct KI { public ushort key,scan; public uint flags,time; public UIntPtr extra; }
				  [StructLayout(LayoutKind.Sequential)] struct HI { public uint msg; public ushort low,high; }
				  [StructLayout(LayoutKind.Explicit)] struct IU { [FieldOffset(0)] public MI mi; [FieldOffset(0)] public KI ki; [FieldOffset(0)] public HI hi; }
				  [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public IU data; }
				  delegate bool EnumProc(IntPtr window, IntPtr value);
				  [DllImport("kernel32.dll")] static extern IntPtr CreateToolhelp32Snapshot(uint flags,uint pid);
				  [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] static extern bool Process32FirstW(IntPtr snap,ref PE entry);
				  [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] static extern bool Process32NextW(IntPtr snap,ref PE entry);
				  [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
				  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback,IntPtr value);
				  [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr window);
				  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window,out uint pid);
				  [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
				  [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr window);
				  [DllImport("user32.dll")] static extern bool BringWindowToTop(IntPtr window);
				  [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr window,int command);
				  [DllImport("user32.dll")] static extern bool IsZoomed(IntPtr window);
				  [DllImport("user32.dll")] static extern uint SendInput(uint count,INPUT[] input,int size);
				  static HashSet<uint> Tree(uint root) { var pairs=new List<Tuple<uint,uint>>(); var snap=CreateToolhelp32Snapshot(SNAP,0); if(snap!=new IntPtr(-1)){try{var e=new PE();e.size=(uint)Marshal.SizeOf(typeof(PE));if(Process32FirstW(snap,ref e))do{pairs.Add(Tuple.Create(e.pid,e.parent));e.size=(uint)Marshal.SizeOf(typeof(PE));}while(Process32NextW(snap,ref e));}finally{CloseHandle(snap);}} var owned=new HashSet<uint>();owned.Add(root);bool changed;do{changed=false;foreach(var p in pairs)if(owned.Contains(p.Item2)&&owned.Add(p.Item1))changed=true;}while(changed);return owned; }
				  static IntPtr Find(HashSet<uint> owned,out uint pid) { IntPtr found=IntPtr.Zero;uint actual=0;EnumWindows(delegate(IntPtr w,IntPtr v){uint p;GetWindowThreadProcessId(w,out p);if(IsWindowVisible(w)&&owned.Contains(p)){found=w;actual=p;return false;}return true;},IntPtr.Zero);pid=actual;return found; }
				  static bool Focus(HashSet<uint> owned,IntPtr window) { uint pid;GetWindowThreadProcessId(window,out pid);if(!owned.Contains(pid))return false;ShowWindow(window,3);BringWindowToTop(window);SetForegroundWindow(window);Thread.Sleep(120);return GetForegroundWindow()==window&&IsZoomed(window); }
				  static INPUT Key(ushort key,bool up){var i=new INPUT();i.type=INPUT_KEYBOARD;i.data.ki.key=key;i.data.ki.flags=up?KEYUP:0;return i;}
				  public static uint Type(int root,string text) { var owned=Tree((uint)root);IntPtr window=IntPtr.Zero;uint pid=0;DateTime until=DateTime.UtcNow.AddSeconds(8);while(DateTime.UtcNow<until&&window==IntPtr.Zero){owned=Tree((uint)root);window=Find(owned,out pid);if(window==IntPtr.Zero)Thread.Sleep(100);}if(window==IntPtr.Zero||!Focus(owned,window))throw new Exception("No verified maximized owned window");foreach(char c in text){if(GetForegroundWindow()!=window&&!Focus(owned,window))throw new Exception("Lost owned foreground");var down=new INPUT();down.type=INPUT_KEYBOARD;down.data.ki.scan=c;down.data.ki.flags=UNICODE;var up=down;up.data.ki.flags=UNICODE|KEYUP;if(SendInput(2,new INPUT[]{down,up},Marshal.SizeOf(typeof(INPUT)))!=2)throw new Exception("SendInput failed");Thread.Sleep(34);}var save=new INPUT[]{Key(VK_CONTROL,false),Key(VK_S,false),Key(VK_S,true),Key(VK_CONTROL,true)};if(SendInput(4,save,Marshal.SizeOf(typeof(INPUT)))!=4)throw new Exception("Save failed");Thread.Sleep(500);return pid; }
				}
				'@
				$text = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($TextBase64))
				$actualPid = [TffOwnedNotepad]::Type($RootPid, $text)
				if ([IO.File]::ReadAllText($NotePath, [Text.Encoding]::UTF8) -ne $text) { exit 7 }
				$process = [Diagnostics.Process]::GetProcessById([int]$actualPid)
				$start = [DateTimeOffset]::new($process.StartTime.ToUniversalTime()).ToUnixTimeMilliseconds()
				Write-Output ('PID=' + $actualPid)
				Write-Output ('START=' + $start)
				""";
	}

	private record WallpaperState(String path, String style, String tile) { }
	private record Recovery(WindowState window, boolean externalRestored) { }

	private record WindowState(boolean fullscreen, boolean maximized, int x, int y, int width, int height) {
		static WindowState capture(Minecraft client) {
			long handle = client.getWindow().handle();
			int[] x = new int[1], y = new int[1], width = new int[1], height = new int[1];
			GLFW.glfwGetWindowPos(handle, x, y);
			GLFW.glfwGetWindowSize(handle, width, height);
			return new WindowState(client.getWindow().isFullscreen(),
					GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
					x[0], y[0], width[0], height[0]);
		}

		static WindowState defaults() { return new WindowState(false, false, 80, 80, 1280, 720); }

		void write(Properties properties) {
			properties.setProperty("window.fullscreen", Boolean.toString(fullscreen));
			properties.setProperty("window.maximized", Boolean.toString(maximized));
			properties.setProperty("window.x", Integer.toString(x));
			properties.setProperty("window.y", Integer.toString(y));
			properties.setProperty("window.width", Integer.toString(width));
			properties.setProperty("window.height", Integer.toString(height));
		}

		static WindowState read(Properties properties) throws IOException {
			try {
				int width = Math.clamp(Integer.parseInt(properties.getProperty("window.width")), 320, 16_384);
				int height = Math.clamp(Integer.parseInt(properties.getProperty("window.height")), 240, 16_384);
				return new WindowState(Boolean.parseBoolean(properties.getProperty("window.fullscreen", "false")),
						Boolean.parseBoolean(properties.getProperty("window.maximized", "false")),
						Integer.parseInt(properties.getProperty("window.x")),
						Integer.parseInt(properties.getProperty("window.y")), width, height);
			} catch (RuntimeException exception) {
				throw new IOException("Invalid window snapshot", exception);
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
