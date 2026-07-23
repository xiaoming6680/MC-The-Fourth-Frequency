package com.xm.thefourthfrequency.ending;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldInterfaceEndingClientContractTest {
	private static String source(String relativePath) throws Exception {
		return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
	}

	@Test
	void windowsEndingUsesFixedOwnedWriteAheadTransaction() throws Exception {
		String transaction = source(
				"src/client/java/com/xm/thefourthfrequency/meta_windows/WindowsEndingMetaTransaction.java");
		assertTrue(transaction.contains("PREPARED") && transaction.contains("APPLIED")
				&& transaction.contains("NOTEPAD_TYPED") && transaction.contains("LOCKED"));
		assertTrue(transaction.contains("我永远在盯着你......."));
		assertTrue(transaction.contains("ShowWindow(window,3)") && transaction.contains("IsZoomed(window)"));
		assertTrue(transaction.contains("GetForegroundWindow()==window"));
		assertTrue(transaction.contains("notepad.start") && transaction.contains("TffOwnedNotepad"));
		assertTrue(transaction.contains("wallpaper.path64") && transaction.contains("wallpaper.style")
				&& transaction.contains("wallpaper.tile"));
		assertFalse(transaction.contains("System.exit"));
		assertFalse(transaction.contains("taskkill"));
		assertFalse(transaction.contains("-Command"));
	}

	@Test
	void failureWallpaperHasTheRequiredOwnedDimensions() throws Exception {
		Path imagePath = Path.of(
				"src/main/resources/assets/thefourthfrequency/textures/gui/ending/world_interface_failure.png");
		var image = ImageIO.read(imagePath.toFile());
		assertEquals(2560, image.getWidth());
		assertEquals(1600, image.getHeight());
	}

	@Test
	void localRecoveryOwnsOnlyItsLockPacksAndNormalShutdown() throws Exception {
		String lock = source(
				"src/client/java/com/xm/thefourthfrequency/client_ui/FailureMenuLockState.java");
		String packs = source(
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldInterfaceResourcePackLease.java");
		String ending = source(
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldInterfaceEndingClient.java");
		assertTrue(lock.contains("StandardCopyOption.ATOMIC_MOVE"));
		assertTrue(packs.contains("originalOrder") && packs.contains("autoAddedIds"));
		assertTrue(packs.contains("client.options.updateResourcePacks(repository)")
				&& packs.contains("client.reloadResourcePacks()"));
		assertTrue(ending.contains("disconnectWithSavingScreen") && ending.contains("client.stop()"));
		assertTrue(ending.contains("thefourthfrequency.safeMode"));
		assertFalse(ending.contains("System.exit"));
	}

	@Test
	void upgradedNoticeDisclosesSafetyBoundariesWithoutSpoilingTheFinale() throws Exception {
		String controller = source(
				"src/client/java/com/xm/thefourthfrequency/client_ui/FirstRunNoticeController.java");
		String screen = source(
				"src/client/java/com/xm/thefourthfrequency/client_ui/FirstRunNoticeScreen.java");
		JsonObject chinese = JsonParser.parseString(source(
				"src/main/resources/assets/thefourthfrequency/lang/zh_cn.json")).getAsJsonObject();
		assertTrue(controller.contains("CURRENT_NOTICE_VERSION = 3"));
		assertTrue(screen.contains("screen.thefourthfrequency.first_run_notice.body.safety_v2"));
		assertTrue(screen.contains("screen.thefourthfrequency.first_run_notice.body.recovery_v3"));
		assertFalse(screen.contains("screen.thefourthfrequency.first_run_notice.recovery_hint"));
		assertFalse(chinese.has("screen.thefourthfrequency.first_run_notice.recovery_hint"));
		String disclosure = String.join(" ",
				chinese.get("screen.thefourthfrequency.first_run_notice.eyebrow").getAsString(),
				chinese.get("screen.thefourthfrequency.first_run_notice.body.control").getAsString(),
				chinese.get("screen.thefourthfrequency.first_run_notice.body.safety").getAsString(),
				chinese.get("screen.thefourthfrequency.first_run_notice.body.safety_v2").getAsString(),
				chinese.get("screen.thefourthfrequency.first_run_notice.body.recovery_v3").getAsString());
		assertTrue(disclosure.contains("安全边界") && disclosure.contains("不是病毒"));
		assertTrue(disclosure.contains("不可逆") && disclosure.contains("提前备份"));
		for (String spoiler : new String[]{"失败结局", "成功或失败", "最终战", "末地",
				"壁纸", "Notepad", "已损坏", "封锁"})
			assertFalse(disclosure.contains(spoiler), spoiler);
	}

	@Test
	void endingReplayIsConfirmedScopedAndLanHostSafe() throws Exception {
		String ending = source(
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldInterfaceEndingClient.java");
		String title = source(
				"src/client/java/com/xm/thefourthfrequency/mixin/TitleScreenErosionMixin.java");
		String quarantine = source(
				"src/main/java/com/xm/thefourthfrequency/ending/EndingWorldQuarantine.java");
		String summary = source(
				"src/client/java/com/xm/thefourthfrequency/mixin/LevelSummaryEndingQuarantineMixin.java");
		String worldOpen = source(
				"src/client/java/com/xm/thefourthfrequency/mixin/WorldOpenFlowsEndingQuarantineMixin.java");
		String lock = source(
				"src/client/java/com/xm/thefourthfrequency/client_ui/FailureMenuLockState.java");
		String fluids = source(
				"src/client/java/com/xm/thefourthfrequency/mixin/LiquidBlockRendererFailureMixin.java");
		assertTrue(ending.contains("getSingleplayerServer().isPublished()"));
		assertTrue(ending.contains("Mode.LAN_HOST_RETURNING"));
		assertTrue(ending.contains("new ConfirmScreen"));
		assertTrue(title.contains("menu.singleplayer") && title.contains("menu.multiplayer")
				&& title.contains("menu.online") && title.contains("setTooltip"));
		assertTrue(quarantine.contains("StandardCopyOption.ATOMIC_MOVE")
				&& quarantine.contains(".thefourthfrequency-corrupted")
				&& quarantine.contains("worldId"));
		assertTrue(lock.contains("LevelResource.ROOT") && lock.contains("stageReplayQuarantine()")
				&& lock.contains("properties.setProperty(\"version\", \"3\")"));
		assertTrue(summary.contains("selectWorld.thefourthfrequency.corrupted")
				&& summary.contains("primaryActionActive")
				&& summary.contains("canUpload") && summary.contains("canEdit")
				&& summary.contains("canRecreate"));
		assertTrue(worldOpen.contains("method = \"openWorld\"")
				&& worldOpen.contains("EndingWorldQuarantine.isQuarantined")
				&& worldOpen.contains("new AlertScreen"));
		assertFalse(Files.exists(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/EndingReplayResetService.java")));
		assertTrue(fluids.contains("lavaStill") && fluids.contains("lavaFlowing")
				&& fluids.contains("waterStill") && fluids.contains("waterFlowing")
				&& fluids.contains("waterOverlay"));
		String chinese = source("src/main/resources/assets/thefourthfrequency/lang/zh_cn.json");
		assertTrue(chinese.contains("\"存档已损坏\"") && chinese.contains("无法进入"));
	}
}
