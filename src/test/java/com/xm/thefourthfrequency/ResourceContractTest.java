package com.xm.thefourthfrequency;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ResourceContractTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/thefourthfrequency");

	@Test
	void bootSplashCatalogRemainsPopulatedAndUsesVanillaYellow() throws Exception {
		String state = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/MenuErosionState.java"),
				StandardCharsets.UTF_8);
		String titleMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/TitleScreenErosionMixin.java"),
				StandardCharsets.UTF_8);
		int catalogStart = state.indexOf("BOOT_SPLASHES = List.of(");
		int catalogEnd = state.indexOf(");", catalogStart);
		assertTrue(catalogStart >= 0, "Boot splash catalog must remain declared");
		assertTrue(catalogEnd > catalogStart, "Boot splash catalog must remain well formed");
		String catalog = state.substring(catalogStart, catalogEnd);
		long entryCount = catalog.lines().map(String::strip)
				.filter(line -> line.startsWith("\"")).count();
		assertTrue(entryCount >= 4, "Boot splash catalog must retain at least four choices");
		assertFalse(catalog.contains("\"\""), "Boot splash catalog must not contain blank entries");
		assertTrue(titleMixin.contains("splash = new SplashRenderer"));
		assertTrue(titleMixin.contains("VANILLA_SPLASH_YELLOW = 0xFFFF00"));
		assertTrue(titleMixin.contains("withColor(VANILLA_SPLASH_YELLOW)"));
		assertTrue(titleMixin.contains("MenuErosionState.sessionSplash()"));
		assertTrue(titleMixin.contains("label.contains(\"realms\")"));
		assertFalse(titleMixin.contains("renderBackground"),
				"Returning to the title screen must keep the normal menu background");
	}

	@Test
	void sixItemIconsAndThreePanelsHaveExactPixelDimensions() throws Exception {
		for (int index = 0; index < 6; index++) {
			var image = ImageIO.read(ASSETS.resolve("textures/item/old_terminal_" + index + ".png").toFile());
			assertEquals(32, image.getWidth());
			assertEquals(32, image.getHeight());
			assertTrue(image.getColorModel().hasAlpha());
			for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
				int alpha = image.getRGB(x, y) >>> 24;
				assertTrue(alpha == 0 || alpha == 255, "non-binary icon alpha at " + index + ":" + x + "," + y);
			}
		}
		for (int stage = 0; stage < 3; stage++) {
			byte[] normal = Files.readAllBytes(ASSETS.resolve("textures/item/old_terminal_" + stage + ".png"));
			byte[] unread = Files.readAllBytes(ASSETS.resolve("textures/item/old_terminal_" + (stage + 3) + ".png"));
			assertNotEquals(java.util.Arrays.hashCode(normal), java.util.Arrays.hashCode(unread));
		}
		for (int stage = 0; stage < 3; stage++) {
			var image = ImageIO.read(ASSETS.resolve("textures/gui/terminal/panel_" + stage + ".png").toFile());
			assertEquals(512, image.getWidth());
			assertEquals(256, image.getHeight());
		}
	}

	@Test
	void reworkBodyUsesFiveDistinctOpaqueBasesAndTwoSparseEmissiveMasks() throws Exception {
		Path entityTextures = ASSETS.resolve("textures/entity");
		Set<Integer> baseHashes = new HashSet<>();
		for (int stage = 1; stage <= 5; stage++) {
			Path path = entityTextures.resolve("rework_body_stage_" + stage + ".png");
			assertTrue(Files.isRegularFile(path), path.toString());
			var image = ImageIO.read(path.toFile());
			assertEquals(256, image.getWidth());
			assertEquals(256, image.getHeight());
			for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
				assertEquals(255, image.getRGB(x, y) >>> 24,
						"base texture must be opaque at stage " + stage + ":" + x + "," + y);
			}
			assertTrue(baseHashes.add(java.util.Arrays.hashCode(Files.readAllBytes(path))),
					"duplicate base texture at stage " + stage);
		}
		assertEquals(5, baseHashes.size());

		Set<Integer> emissiveHashes = new HashSet<>();
		for (int stage = 4; stage <= 5; stage++) {
			Path path = entityTextures.resolve("rework_body_stage_" + stage + "_emissive.png");
			assertTrue(Files.isRegularFile(path), path.toString());
			var image = ImageIO.read(path.toFile());
			assertEquals(256, image.getWidth());
			assertEquals(256, image.getHeight());
			assertTrue(image.getColorModel().hasAlpha());
			int transparent = 0;
			int visible = 0;
			int maxAlpha = 0;
			for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
				int alpha = image.getRGB(x, y) >>> 24;
				if (alpha == 0) {
					transparent++;
					continue;
				}
				visible++;
				maxAlpha = Math.max(maxAlpha, alpha);
				boolean faceOrMouth = x >= 140 && x < 246 && y >= 0 && y < 66;
				boolean torsoCrack = stage == 5 && x >= 0 && x < 54 && y >= 0 && y < 48;
				boolean exposedSpine = stage == 5 && x >= 198 && x < 252 && y >= 128 && y < 202;
				assertTrue(faceOrMouth || torsoCrack || exposedSpine,
						"emissive escaped approved UV islands at stage " + stage + ":" + x + "," + y);
			}
			assertTrue(transparent > 64_000, "emissive background must remain overwhelmingly transparent");
			assertTrue(visible > 0 && visible < 1_500, "emissive coverage must remain sparse");
			assertTrue(maxAlpha >= 64 && maxAlpha <= 128, "emissive alpha must remain dim");
			assertTrue(emissiveHashes.add(java.util.Arrays.hashCode(Files.readAllBytes(path))),
					"duplicate emissive texture at stage " + stage);
		}
		assertEquals(2, emissiveHashes.size());
		assertFalse(Files.exists(entityTextures.resolve("rework_body.png")),
				"legacy single-form texture must not remain in the runtime pack");
	}

	@Test
	void customModelDispatchCoversEveryProjectionValue() throws Exception {
		JsonObject root = JsonParser.parseString(Files.readString(ASSETS.resolve("items/old_terminal.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		var entries = root.getAsJsonObject("model").getAsJsonArray("entries");
		assertEquals(5, entries.size());
		for (int index = 0; index < 5; index++) {
			assertEquals(index + 1.0F, entries.get(index).getAsJsonObject().get("threshold").getAsFloat());
			assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/old_terminal_" + (index + 1) + ".json")));
		}
	}

	@Test
	void everyTerminalSoundIsAProjectOwnedOgg() throws Exception {
		JsonObject sounds = JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		for (String event : new String[]{"terminal_click", "terminal_tune", "terminal_lock",
				"terminal_anomaly"}) {
			assertTrue(sounds.has(event), event);
		}
		for (Path path : Files.walk(ASSETS.resolve("sounds/device/terminal")).filter(Files::isRegularFile).toList()) {
			byte[] header = Files.readAllBytes(path);
			assertTrue(header.length > 4 && header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S', path.toString());
		}
		for (String event : new String[]{"terminal_click", "terminal_tune", "terminal_lock",
				"terminal_fault", "terminal_anomaly"}) {
			for (var sound : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
				String name = sound.isJsonPrimitive() ? sound.getAsString()
						: sound.getAsJsonObject().get("name").getAsString();
				String localName = name.substring(name.indexOf(':') + 1);
				assertTrue(Files.isRegularFile(ASSETS.resolve("sounds/" + localName + ".ogg")), name);
			}
		}
	}

	@Test
	void allJsonParsesAndLanguageKeySetsMatch() throws Exception {
		for (Path path : Files.walk(Path.of("src/main/resources")).filter(value -> value.toString().endsWith(".json")).toList()) {
			JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
		}
		JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject zh = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/zh_cn.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(new HashSet<>(en.keySet()), new HashSet<>(zh.keySet()));
		JsonObject terminologyCopy = zh.deepCopy();
		terminologyCopy.remove("screen.thefourthfrequency.first_run_notice.body.control");
		assertFalse(terminologyCopy.toString().contains("异常"),
				"Chinese gameplay terminology must consistently use 异象; the preserved computer-safety copy is ordinary prose");
		assertFalse(zh.toString().contains("缓存"), "The player-facing FILES system must not retain the old cache wording");
		assertEquals("接收到新文件：%s",
				zh.get("message.thefourthfrequency.file.discovered").getAsString());
		assertEquals("接收到来自【%s】共享的一份破损文件",
				zh.get("message.thefourthfrequency.fragment.received").getAsString());
		assertEquals("破损的文件",
				zh.get("terminal.thefourthfrequency.file.damaged.title").getAsString());
		assertFalse(zh.toString().contains("碎片1"));
		assertEquals(List.of("", "加", "加密", "加密日", "加密日记"),
				java.util.stream.IntStream.rangeClosed(0, 4)
						.mapToObj(stage -> zh.get("terminal.thefourthfrequency.file.encrypted_witness_file.revealed."
								+ stage).getAsString()).toList());
		assertEquals(List.of("", "Encrypted ", "Encrypted Witness ",
						"Encrypted Witness Journal ", "Encrypted Witness Journal File"),
				java.util.stream.IntStream.rangeClosed(0, 4)
						.mapToObj(stage -> en.get("terminal.thefourthfrequency.file.encrypted_witness_file.revealed."
								+ stage).getAsString()).toList());
		assertEquals("近场接收器",
				zh.get("terminal.thefourthfrequency.receiver.label").getAsString());
		assertEquals("待机",
				zh.get("terminal.thefourthfrequency.receiver.standby").getAsString());
		assertEquals("终端记下了你带回的资源。",
				zh.get("message.thefourthfrequency.guidance.accepted").getAsString());
		for (String retired : List.of("terminal.thefourthfrequency.band.weather",
				"terminal.thefourthfrequency.band.mining", "terminal.thefourthfrequency.band.public",
				"terminal.thefourthfrequency.band.unknown", "terminal.thefourthfrequency.objective.calibrate",
				"terminal.thefourthfrequency.tuning.auto", "terminal.thefourthfrequency.tuning.manual")) {
			assertFalse(zh.has(retired), "Retired fixed-band copy must stay absent: " + retired);
		}
		assertEquals("·· 未记录",
				zh.get("terminal.thefourthfrequency.signal.marker.unrecorded").getAsString());
		assertEquals("选取文件来查看",
				zh.get("terminal.thefourthfrequency.file.select_prompt").getAsString());
		assertEquals("文件", zh.get("terminal.thefourthfrequency.tab.files").getAsString());
		assertEquals("文件", zh.get("terminal.thefourthfrequency.tab.log").getAsString());
		assertEquals("在合适时机自动探测最近的结构",
				zh.get("terminal.thefourthfrequency.tool.navigation.summary").getAsString());
		assertEquals("自动记录你的重生点",
				zh.get("terminal.thefourthfrequency.tool.home.summary").getAsString());
		assertEquals("自动寻找附近合适的矿物",
				zh.get("terminal.thefourthfrequency.tool.minerals.summary").getAsString());
		assertEquals("正在探测矿物",
				zh.get("terminal.thefourthfrequency.tool.minerals.scanning").getAsString());
		assertEquals("探测失败",
				zh.get("terminal.thefourthfrequency.tool.minerals.not_found").getAsString());
		assertEquals("探测",
				zh.get("terminal.thefourthfrequency.tool.minerals.refresh").getAsString());
		assertEquals("探测失败",
				zh.get("message.thefourthfrequency.guidance.not_found").getAsString());
		assertTrue(zh.has("message.thefourthfrequency.terminal.unread"));
		assertTrue(zh.has("message.thefourthfrequency.task.completed"));
		assertEquals("目的地在您%s侧，本次导航结束",
				zh.get("terminal.thefourthfrequency.navigation.completed").getAsString());
		assertEquals("开始导航", zh.get("terminal.thefourthfrequency.tool.guide").getAsString());
		assertEquals("停止导航", zh.get("terminal.thefourthfrequency.tool.stop").getAsString());
		for (var entry : zh.entrySet()) {
			if (!entry.getKey().startsWith("terminal.thefourthfrequency.signal.card.")) continue;
			assertFalse(entry.getValue().getAsString().contains("碎片"),
					"SIGNAL card copy must not expose fragment labels: " + entry.getKey());
		}
		for (String abstractTerm : new String[]{"经历连续性", "身份连续性", "身体映射", "关系异常",
				"跨维度连续性", "关系证据", "关系层", "连续性样本", "关系触点",
				"身体生成", "环境连续性", "结构修订", "防线层数", "权限层级"}) {
			assertFalse(zh.entrySet().stream().anyMatch(entry -> entry.getValue().isJsonPrimitive()
					&& entry.getValue().getAsString().contains(abstractTerm)), abstractTerm);
		}
		for (var entry : zh.entrySet()) {
			if (!entry.getKey().startsWith("terminal.thefourthfrequency.file.")
					|| !entry.getKey().endsWith(".title")) continue;
			assertTrue(entry.getValue().getAsString().codePointCount(0, entry.getValue().getAsString().length()) <= 8,
					() -> "File title is too long: " + entry.getKey() + "=" + entry.getValue().getAsString());
		}
		assertEquals("我已了解",
				zh.get("button.thefourthfrequency.first_run_notice.acknowledge").getAsString());
		assertEquals("正在校验信号",
				zh.get("screen.thefourthfrequency.first_run_notice.status.checking").getAsString());
		assertEquals("信号已稳定",
				zh.get("screen.thefourthfrequency.first_run_notice.status.stable").getAsString());
		String noticeCopy = zh.get("screen.thefourthfrequency.first_run_notice.body.control").getAsString()
				+ zh.get("screen.thefourthfrequency.first_run_notice.body.safety").getAsString()
				+ zh.get("screen.thefourthfrequency.first_run_notice.body.f8").getAsString();
		assertTrue(noticeCopy.contains("不是病毒"));
		assertTrue(noticeCopy.contains("不会损坏你的电脑、系统或个人文件"));
		String saveNotice = zh.get("screen.thefourthfrequency.first_run_notice.body.safety_v2").getAsString();
		assertTrue(saveNotice.contains("不可逆") && saveNotice.contains("备份"));
		assertEquals("存档提示",
				zh.get("screen.thefourthfrequency.first_run_notice.section.effects").getAsString());
		assertFalse(zh.has("screen.thefourthfrequency.first_run_notice.recovery_hint"));
		assertFalse(en.has("screen.thefourthfrequency.first_run_notice.recovery_hint"));
		for (String undisclosedOperation : new String[]{"窗口", "记事本", "摄像头", "壁纸", "视频"})
			assertFalse(noticeCopy.contains(undisclosedOperation), undisclosedOperation);
		String displayedNotice = noticeCopy + saveNotice
				+ zh.get("screen.thefourthfrequency.first_run_notice.body.recovery_v3").getAsString();
		for (String spoiler : new String[]{"最终战", "成功或失败", "结局", "末地", "Notepad",
				"壁纸", "已损坏", "封锁", "mobGriefing"})
			assertFalse(displayedNotice.contains(spoiler), spoiler);
		assertTrue(zh.get("screen.thefourthfrequency.first_run_notice.body.f8").getAsString().contains("F8"));
		for (var entry : zh.entrySet()) {
			if (entry.getKey().startsWith("terminal.thefourthfrequency.file.")
					|| entry.getKey().startsWith("terminal.thefourthfrequency.cache.")
					|| entry.getKey().startsWith("text.thefourthfrequency.archive.line.")) {
				assertFalse(entry.getValue().getAsString().stripLeading().startsWith("|"),
						() -> "File prose retained a left-side pipe: " + entry.getKey());
			}
		}
	}

	@Test
	void terminalReworkKeepsFourClientPagesAndIndependentFileScrollState() throws Exception {
		String screen = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalScreen.java"), StandardCharsets.UTF_8);
		String page = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/TerminalPage.java"), StandardCharsets.UTF_8);
		String tool = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/TerminalTool.java"), StandardCharsets.UTF_8);
		String runtime = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/TerminalRuntimeService.java"),
				StandardCharsets.UTF_8);
		for (String name : new String[]{"HOME", "TOOLS", "RECORDS", "FILES"}) assertTrue(page.contains(name));
		for (String name : new String[]{"HOME", "MINERALS", "PORTAL", "WEATHER", "NAVIGATION", "STRONGHOLD"}) {
			assertTrue(tool.contains(name));
		}
		assertTrue(screen.contains("switch (page)"));
		assertTrue(screen.contains("fileListScroll"));
		assertTrue(screen.contains("fileContentScroll"));
		assertTrue(screen.contains("recordsScrollRow"));
		assertFalse(screen.contains("automaticTuning"));
		assertTrue(screen.contains("updateTools(TerminalToolSnapshotPayload"));
		assertFalse(screen.contains("TerminalControlPayload.SET_AUTO_TUNING"));
		assertTrue(screen.contains("receiverMechanicalInteractive()"));
		assertTrue(screen.contains("receiverGameplayActive()"));
		assertTrue(screen.contains("TerminalUiLayout.RECEIVER_SLIDER"));
		assertTrue(screen.contains("displayedObjectiveFraction"));
		assertTrue(screen.contains("drawTaskReward(graphics)"));
		assertTrue(screen.contains("graphics.renderItem(reward"));
		assertTrue(screen.contains("TerminalUiLayout.HOME_TASK.contains"));
		assertTrue(screen.contains("TerminalControlPayload.CLAIM_TASK_REWARD"));
		assertTrue(screen.contains("TerminalControlPayload.VISIT_PAGE"));
		assertTrue(screen.contains("recommendedPrimaryTool()"));
		assertTrue(screen.contains("HOME_TOOL_DETAIL"));
		assertTrue(screen.contains("HOME_TOOL_CLOSE"));
		assertTrue(screen.contains("returnHomeAfterToolActivation"));
		assertTrue(screen.contains("!toolVisible(tool) || !tools.available(tool)"),
				"Locked tools must be rejected by the shared detail-opening boundary");
		assertFalse(screen.contains("localLockedTool"),
				"Locked tools must not retain a local-only detail state");
		assertTrue(screen.contains("send(TerminalControlPayload.REQUEST_RESCAN"),
				"Mineral refresh must be an explicit server-authoritative button");
		assertFalse(screen.contains("send(TerminalControlPayload.SELECT_RESOURCE"),
				"The client must not offer manual mineral selection");
		assertFalse(screen.contains("send(TerminalControlPayload.SET_HOME"),
				"The client must not offer manual home storage");
		assertTrue(screen.contains("terminal.thefourthfrequency.tool.minerals.refresh"));
		assertFalse(screen.contains("terminal.thefourthfrequency.navigation.side_route"));
		assertFalse(screen.contains("targets.add(selected)"),
				"Selecting a destination must not move it into the first option slot");
		assertTrue(screen.contains("target.sideRoute()"));
		assertTrue(screen.contains("sideRouteGlitchActive(renderAge)"));
		assertTrue(screen.contains("navigationNeedleFlashStartedAt = renderAge"));
		assertTrue(screen.contains("mineralTargetLocated()"));
		assertTrue(screen.contains("\".\".repeat(dots)"));
		assertTrue(screen.contains("drawFittedLine(graphics, lineTwo"));
		assertTrue(screen.contains("navigationOptionBounds"));
		assertTrue(screen.contains("targets.size() >= 3"));
		assertTrue(screen.contains("index < 3"));
		assertTrue(screen.contains("TerminalControlPayload.MARK_RECORDS_READ"));
		assertTrue(screen.contains("TerminalUiLayout.unreadFlashOn"));
		assertTrue(screen.contains("Component.literal(\" [!]\")"));
		assertTrue(screen.contains("snapshot.recordEntries()"));
		assertFalse(screen.contains("advanceAutomaticTuning"));
		assertTrue(screen.contains("TerminalUiLayout.FILE_LIST.contains"));
		assertTrue(screen.contains("TerminalUiLayout.FILE_CONTENT.contains"));
		assertFalse(screen.contains("FILE_GRID_COLUMNS"));
		int markReadStart = runtime.indexOf("private static boolean markRecordsRead");
		int markReadEnd = runtime.indexOf("private static boolean markHiddenFileRead", markReadStart);
		assertTrue(markReadStart >= 0 && markReadEnd > markReadStart);
		assertFalse(runtime.substring(markReadStart, markReadEnd).contains("synchronizeProjection"),
				"Opening RECORDS must not rewrite the held terminal and restart its equip animation");
	}

	@Test
	void terminalFeedbackUsesABoundedBottomFirstNoticeStackAndClearAttentionTone() throws Exception {
		String hud = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalNoticeHud.java"),
				StandardCharsets.UTF_8);
		String networking = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalClientNetworking.java"),
				StandardCharsets.UTF_8);
		String commonNetworking = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/TerminalNetworking.java"),
				StandardCharsets.UTF_8);
		String audio = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalClientAudio.java"),
				StandardCharsets.UTF_8);
		String targets = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/TerminalStructureTarget.java"),
				StandardCharsets.UTF_8);
		String taskService = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/TerminalTaskService.java"),
				StandardCharsets.UTF_8);
		String survivalProgress = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/world/SurvivalProgressService.java"),
				StandardCharsets.UTF_8);
		String metaFallback = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/meta_api/InGameMetaPlatformAdapter.java"),
				StandardCharsets.UTF_8);
		assertTrue(hud.contains("MAX_VISIBLE = 4"));
		assertTrue(hud.contains("ENTRIES.add(new NoticeEntry"));
		assertTrue(hud.contains("entry.targetSlot++"),
				"A new bottom entry must push existing entries upward");
		assertTrue(hud.contains("exiting = ENTRIES.getLast()"),
				"The bottom entry must be the first one to leave");
		assertTrue(hud.contains("entry.targetSlot = Math.max(0, entry.targetSlot - 1)"),
				"Remaining entries must fall after the bottom entry leaves");
		assertTrue(hud.contains("ENTRIES.removeFirst()"),
				"The visible stack must stay bounded during notification bursts");
		assertTrue(networking.contains("TerminalNoticeHud.enqueue(payload.message(), payload.tone())"));
		assertTrue(metaFallback.contains("TerminalNoticeHud.enqueue("));
		assertFalse(metaFallback.contains("displayClientMessage("));
		assertTrue(commonNetworking.contains("TerminalNoticePayload.TYPE"));
		assertTrue(audio.contains("UI_TOAST_CHALLENGE_COMPLETE"));
		assertTrue(audio.contains("NOTE_BLOCK_CHIME"));
		assertTrue(hud.contains("taskComplete ? TASK_BACKGROUND : DEFAULT_BACKGROUND"),
				"Task completion notices must use the dedicated green background");
		assertTrue(taskService.contains("consumeCompletionAlert"));
		assertTrue(taskService.contains("TerminalNoticeService.taskComplete(player)"));
		assertTrue(survivalProgress.contains("public static final int REQUIRED_IRON = 12;"));
		assertTrue(taskService.contains(
				"new TaskDefinition(\"bring_iron\", SurvivalProgressService.REQUIRED_IRON, Items.TORCH, 24)"));
		assertTrue(targets.contains("MINESHAFT(2, \"mineshaft\", true"));
		assertTrue(targets.contains("TRIAL_CHAMBERS(3, \"trial_chambers\", true"));
		assertTrue(targets.contains("BASTION(5, \"bastion\", true"));
	}

	@Test
	void openingWoodTaskAcceptsEveryLogFamilyAndPlanks() throws Exception {
		String survival = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/world/SurvivalProgressService.java"),
				StandardCharsets.UTF_8);
		assertTrue(survival.contains("collectedWood(player)"));
		assertTrue(survival.contains("BlockTags.LOGS"));
		assertTrue(survival.contains("BlockTags.PLANKS"));
		assertTrue(survival.contains("instanceof BlockItem"));
	}

	@Test
	void toolProtocolIsIndependentAndAllToolControlsAreServerValidated() throws Exception {
		String snapshot = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/TerminalSnapshotPayload.java"), StandardCharsets.UTF_8);
		String navigation = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/TerminalNavigationPayload.java"), StandardCharsets.UTF_8);
		String toolSnapshot = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/TerminalToolSnapshotPayload.java"), StandardCharsets.UTF_8);
		String control = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/TerminalControlPayload.java"), StandardCharsets.UTF_8);
		String runtime = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/TerminalRuntimeService.java"), StandardCharsets.UTF_8);
		assertTrue(snapshot.contains("CURRENT_PROTOCOL_VERSION = 8"));
		assertTrue(navigation.contains("CURRENT_PROTOCOL_VERSION = 6"));
		assertTrue(toolSnapshot.contains("CURRENT_PROTOCOL_VERSION = 4"));
		for (String action : List.of("SELECT_TOOL", "START_GUIDANCE", "STOP_GUIDANCE",
				"REQUEST_RESCAN", "MARK_RECORDS_READ", "READ_HIDDEN_FILE", "SELECT_STRUCTURE_TARGET",
				"SELECT_NEAREST_UNSTABLE", "DISMISS_NAVIGATION_COMPLETION", "VISIT_PAGE",
				"CLAIM_TASK_REWARD")) {
			assertTrue(control.contains(action));
			assertTrue(runtime.contains("TerminalControlPayload." + action));
		}
		for (String retired : List.of("SELECT_RESOURCE", "SET_HOME")) {
			assertTrue(control.contains(retired));
			assertTrue(runtime.contains("case TerminalControlPayload." + retired + " -> { return; }"));
		}
		assertTrue(control.contains("SET_AUTO_TUNING"), "Wire id 11 remains reserved for old clients");
		assertFalse(runtime.contains("TerminalControlPayload.SET_AUTO_TUNING"));
		assertTrue(runtime.contains("TerminalControlPolicy.validMode(value)"));
		assertTrue(runtime.contains("TerminalControlPolicy.validTuning(value)"));
	}

	@Test
	void strongholdPortalHandsTheEndToThePersistedWorldInterfaceEncounter() throws Exception {
		String encounter = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/EndBossEncounterService.java"),
				StandardCharsets.UTF_8);
		String attackService = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/WorldInterfaceAttackService.java"),
				StandardCharsets.UTF_8);
		String state = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/WorldInterfaceState.java"),
				StandardCharsets.UTF_8);
		String ritual = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/WorldInterfaceRitualService.java"),
				StandardCharsets.UTF_8);
		String policy = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/WorldInterfacePolicy.java"),
				StandardCharsets.UTF_8);
		String stages = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/WorldInterfaceStage.java"),
				StandardCharsets.UTF_8);
		String actions = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/WorldInterfaceAction.java"),
				StandardCharsets.UTF_8);
		String arena = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/EndBossArenaService.java"),
				StandardCharsets.UTF_8);
		String protocol = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/WorldInterfaceProtocol.java"),
				StandardCharsets.UTF_8);
		String blocks = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/content/ModBlocks.java"),
				StandardCharsets.UTF_8);
		String mixins = Files.readString(Path.of("src/main/resources/thefourthfrequency.mixins.json"),
				StandardCharsets.UTF_8);
		JsonObject immunityTag = JsonParser.parseString(Files.readString(Path.of(
				"src/main/resources/data/thefourthfrequency/tags/block/world_interface_immune.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		assertTrue(state.contains("ROOT_KEY = \"world_interface\"")
				&& state.contains("FORMAT_VERSION = 1")
				&& state.contains("GATE_COUNT = 20")
				&& state.contains("ANCHOR_COUNT = 10")
				&& state.contains("MAX_ROSTER_SIZE = 8"));
		assertFalse(state.contains("ensureEndBossV3") || state.contains("ROOT_KEY = \"ending\""));
		for (String transactionState : List.of("PREPARED", "REMOVED", "RETURN_PENDING", "COMMITTED")) {
			assertTrue(state.contains(transactionState));
			assertTrue(ritual.contains("TerminalTransactionState." + transactionState));
		}
		assertTrue(ritual.contains("RitualResult deposit(") && ritual.contains("RitualResult withdraw(")
				&& ritual.contains("RitualResult cancel("));
		assertTrue(policy.contains("COLLAPSE_DURATION_TICKS = 12_000")
				&& policy.contains("MAX_PERMANENT_TERRAIN_EDITS = 2_048")
				&& policy.contains("MAX_TERRAIN_EDITS_PER_TICK = 8"));
		assertTrue(stages.contains("SUCCESS_RESOLUTION") && stages.contains("FAILURE_RESOLUTION")
				&& stages.contains("PHASE_1") && stages.contains("PHASE_2") && stages.contains("PHASE_3"));
		for (String action : List.of("LASER_SWEEP", "ENERGY_ORB", "GRAB_SLAM", "MENTAL_ASSAULT",
				"CHARGE_WEAPON_STEAL", "GRAB_THROW", "GAZE_HOTBAR_CLEAR", "ARROW_REFLECTION",
				"FORCED_EVICTION")) {
			assertTrue(actions.contains(action));
			assertTrue(attackService.contains("case " + action));
		}
		assertTrue(encounter.contains("WorldInterfaceAttackService.begin(")
				&& encounter.contains("WorldInterfaceAttackService.tick(")
				&& encounter.contains("SUCCESS_RESOLUTION")
				&& encounter.contains("FAILURE_RESOLUTION"));
		assertTrue(arena.contains("GATEWAY_COUNT = 20") && arena.contains("ANCHOR_COUNT = 10")
				&& arena.contains("MAX_PERMANENT_EDITS") && arena.contains("MAX_EDITS_PER_TICK"));
		assertTrue(blocks.contains("RESONANCE_CORE") && blocks.contains("WARP_GATE_CORE")
				&& blocks.contains("STABILITY_ANCHOR_CAGE"));
		assertTrue(protocol.contains("VERSION = 1") && protocol.contains("MAX_PARTICIPANTS = 8")
				&& protocol.contains("MAX_GATEWAYS = 20") && protocol.contains("ANCHOR_MASK = 0x03FF"));
		assertFalse(immunityTag.get("replace").getAsBoolean());
		String immuneValues = immunityTag.getAsJsonArray("values").toString();
		for (String criticalBlock : List.of("resonance_core", "warp_gate_core",
				"stability_anchor_cage", "world_interface_exit_portal")) {
			assertTrue(immuneValues.contains("thefourthfrequency:" + criticalBlock));
		}
		assertTrue(mixins.contains("EndCrystalMixin") && mixins.contains("EndPortalBlockMixin")
				&& mixins.contains("EnderEyeItemMixin") && mixins.contains("EnderDragonMixin")
				&& !mixins.contains("ServerPlayerDropMixin"));
	}

	@Test
	void worldInterfaceExitUsesTheRealVanillaEndPoemAndRespawnProtocol() throws Exception {
		String exit = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/content/WorldInterfaceExitPortalBlock.java"),
				StandardCharsets.UTF_8);
		String encounter = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/EndBossEncounterService.java"),
				StandardCharsets.UTF_8);
		String networking = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldInterfaceClientNetworking.java"),
				StandardCharsets.UTF_8);
		String binding = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldInterfaceVanillaPoemClient.java"),
				StandardCharsets.UTF_8);
		String winScreenMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/WinScreenPoemMixin.java"),
				StandardCharsets.UTF_8);
		String mixins = Files.readString(Path.of("src/main/resources/thefourthfrequency.mixins.json"),
				StandardCharsets.UTF_8);

		assertTrue(exit.contains("implements Portal") && exit.contains("player.showEndCredits()"));
		assertTrue(exit.contains("((Portal) Blocks.END_PORTAL).getPortalDestination"));
		assertFalse(exit.contains("setPortalCooldown") || exit.contains("restoreRespawnAndReturn"));
		assertTrue(networking.contains("acceptPoem(payload)") && networking.contains("VanillaPoemClient.arm"));
		assertFalse(networking.contains("new WorldInterfacePoemScreen"));
		assertFalse(Files.exists(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldInterfacePoemScreen.java")));
		assertTrue(mixins.contains("WinScreenPoemMixin"));
		assertTrue(winScreenMixin.contains("@Mixin(WinScreen.class)")
				&& winScreenMixin.contains("wrapCreditsIO")
				&& winScreenMixin.contains("PoemCompletion.SKIPPED"));
		int acknowledgement = binding.indexOf("sendPoemComplete(poem, completion)");
		int unlockArming = binding.indexOf("armUnlockAfterSuccessfulReturn()");
		int vanillaFinish = binding.indexOf("vanillaFinish.run()");
		assertTrue(acknowledgement >= 0 && unlockArming > acknowledgement
				&& vanillaFinish > unlockArming,
				"The durable success ACK must arm the return unlock before vanilla PERFORM_RESPAWN");
		assertTrue(encounter.contains("prepareVanillaEndReturn(player, result.snapshot())")
				&& encounter.contains("restoreRespawnAfterVanillaReturn(player, snapshot)"));

		for (String resource : List.of("end_success_zh_cn.txt", "end_failure_zh_cn.txt",
				"end_success_en_us.txt", "end_failure_en_us.txt")) {
			Path poem = ASSETS.resolve("texts").resolve(resource);
			assertTrue(Files.isRegularFile(poem), resource);
			long authoredLines = Files.readAllLines(poem, StandardCharsets.UTF_8).stream()
					.filter(line -> !line.isBlank()).count();
			assertEquals(15L, authoredLines, resource + " must retain all authored paragraphs");
		}
	}

	@Test
	void firstRunNoticeUsesClientLocalVersionMarkerAndCannotBeBypassed() throws Exception {
		String controller = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/FirstRunNoticeController.java"),
				StandardCharsets.UTF_8);
		assertFalse(controller.contains("ConfigManager.loadClientState()"));
		assertFalse(controller.contains("ConfigManager.updateClientState"));
		assertTrue(controller.contains("thefourthfrequency-safety-notice.version"));
		assertTrue(controller.contains("CURRENT_NOTICE_VERSION"));
		assertTrue(controller.contains("Files.writeString"));
		assertFalse(controller.contains("thefourthfrequency-client-state.json"));
		assertTrue(controller.contains("ClientTickEvents.END_CLIENT_TICK"));
		assertTrue(controller.contains("client.screen instanceof TitleScreen"));
		assertTrue(controller.contains("new FirstRunNoticeScreen(titleScreen)"));
		assertFalse(controller.contains("ClientPlayConnectionEvents.JOIN"));
		assertFalse(controller.contains("TerminalData"));
		String screen = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/FirstRunNoticeScreen.java"),
				StandardCharsets.UTF_8);
		assertTrue(screen.contains("shouldCloseOnEsc() { return false; }"));
		assertTrue(screen.contains("isPauseScreen() { return true; }"));
		assertTrue(screen.contains("button.thefourthfrequency.first_run_notice.acknowledge"));
		assertTrue(screen.contains("graphics.fill(0, 0, width, height, 0xFF080D0A)"));
		assertFalse(screen.contains("renderBlurredBackground"));
		assertFalse(screen.contains("drawBackdropWaveform"));
		assertFalse(screen.contains("drawRollingBeam"));
		assertFalse(screen.contains("drawEdgeGlitches"));
		assertFalse(screen.contains("drawDesyncedScanRows"));
		assertFalse(screen.contains("renderTransientTextGhosts"));
		assertTrue(screen.contains("enum EntrancePhase"));
		assertTrue(screen.contains("enum PresentationPhase"));
		assertTrue(screen.contains("LOCK_TICK = 64"));
		assertTrue(screen.contains("CALIBRATION_END_TICK = 72"));
		assertTrue(screen.contains("renderBandCalibration"));
		assertTrue(screen.contains("drawCalibrationWave"));
		assertTrue(screen.contains("drawCalibrationRail"));
		assertFalse(screen.contains("drawHeaderScope"));
		assertTrue(screen.contains("TerminalClientAudio.noticeOpening()"));
		assertTrue(screen.contains("TerminalClientAudio.noticeStable()"));
		assertTrue(screen.contains("acknowledgementButton.visible = ready"));
		assertTrue(screen.contains("transitionAge = 0"));
		assertTrue(screen.contains("TEXT_FADE_TICKS = 4"));
		assertTrue(screen.contains("ZOOM_TICKS = 24"));
		assertTrue(screen.contains("transitionAge >= TEXT_FADE_TICKS) return"));
		assertTrue(screen.contains("returnScreen.render(graphics"));
		assertTrue(screen.contains("graphics.enableScissor"));
		assertTrue(screen.contains("renderTransitionFrame"));
		assertTrue(screen.contains("zoomProgress * 2.0F"));
		assertTrue(screen.contains("255.0F * (1.0F - zoomProgress)"));
		assertTrue(screen.contains("renderTransitionFrame(graphics, zoomed, terminalAlpha)"));
		assertTrue(screen.contains("targetZoomScale"));
		assertTrue(screen.contains("(targetZoomScale(base) - 1.0F) * zoomProgress"));
		assertTrue(screen.contains("FirstRunNoticePalette"));
		assertTrue(screen.contains("LATIN_BASELINE_Y_OFFSET = 1"));
		assertTrue(screen.contains("drawBaselineAlignedString"));
		assertTrue(screen.contains("usesLatinPixelBaseline"));
		assertTrue(screen.contains("allTextInsideGlassForTesting"));
		assertTrue(screen.contains("GLASS_SAFE_BOTTOM_ASSET"));
		assertFalse(screen.contains("TerminalVisualTheme"));
		assertTrue(screen.contains("renderGeneratedNoticeUi"));
		assertTrue(screen.contains("textures/gui/notice/first_run_notice_terminal_shell.png"));
		assertFalse(screen.contains("first_run_notice_background.png"));
		assertFalse(screen.contains("panel_0.png"));
		assertFalse(screen.contains("panel_1.png"));
		assertFalse(screen.contains("panel_2.png"));
		assertFalse(screen.contains("SAFETY_FILL"));
		assertFalse(screen.contains("BUTTON_FILL"));
		assertFalse(screen.contains("SCOPE_FILL"));
		assertTrue(screen.contains("one continuous CRT glass grid"));
		assertTrue(screen.contains("class NoticeButton extends Button"));
		var noticeUi = ImageIO.read(ASSETS.resolve(
				"textures/gui/notice/first_run_notice_terminal_shell.png").toFile());
		assertEquals(1620, noticeUi.getWidth());
		assertEquals(971, noticeUi.getHeight());
		assertFalse(Files.exists(ASSETS.resolve("textures/gui/notice/first_run_notice_background.png")));
		String noticePalette = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/FirstRunNoticePalette.java"),
				StandardCharsets.UTF_8);
		assertTrue(noticePalette.contains("without importing terminal screen assets or constants"));

		String theme = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalVisualTheme.java"),
				StandardCharsets.UTF_8);
		String terminal = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalScreen.java"),
				StandardCharsets.UTF_8);
		for (String color : new String[]{"GREEN", "CYAN", "DIM", "AMBER", "HOT", "GLASS",
				"LCD_BACKGROUND", "LCD_BORDER", "DARK_BORDER"}) {
			assertTrue(theme.contains(" " + color + " ="), color);
			assertTrue(terminal.contains("TerminalVisualTheme." + color), color);
		}
	}

	@Test
	void currentFragmentMainlineUsesVanillaStructuresWithoutAllocatingFacilities() throws Exception {
		String fragments = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/world/FragmentInvestigationService.java"),
				StandardCharsets.UTF_8);
		for (String structure : new String[]{"MINESHAFT", "SHIPWRECK", "TRAIL_RUINS", "STRONGHOLD",
				"WOODLAND_MANSION", "DESERT_PYRAMID", "IGLOO", "TRIAL_CHAMBERS",
				"PILLAGER_OUTPOST", "JUNGLE_TEMPLE", "OCEAN_MONUMENT", "ANCIENT_CITY",
				"OCEAN_RUIN_COLD", "RUINED_PORTAL"})
			assertTrue(fragments.contains("BuiltinStructures." + structure), structure);
		assertTrue(fragments.contains("findNearestMapStructure"));
		assertTrue(fragments.contains("getStructureWithPieceAt"));
		String terminalScreen = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalScreen.java"),
				StandardCharsets.UTF_8);
		assertFalse(terminalScreen.contains("0x0700FF70"),
				"The terminal display must not restore the persistent green scanline overlay");
		assertFalse(terminalScreen.contains("log.top() + 19"),
				"The signal header must not restore a full-width horizontal divider");
		assertFalse(terminalScreen.contains("y + ROW_HEIGHT - 1, 0x551C3A25"),
				"Signal selection must use text color instead of a full-row horizontal band");
		assertFalse(terminalScreen.contains("footer.top() + 1, DARK_BORDER"),
				"The footer must not restore a full-width horizontal divider");
		assertFalse(terminalScreen.contains("drawFragmentPulse"),
				"Private fragment state must not restore moving horizontal glitch lines");
		assertFalse(terminalScreen.contains("signal.objective_prefix"),
				"Hidden story gates must not be rendered as a persistent task checklist");
		assertTrue(terminalScreen.contains("expanded ? \"  －\" : \"  ＋\""),
				"Folded signal cards use right-side plus/minus symbols");
		assertTrue(terminalScreen.contains("String key = \"candidates:\" + fragment"),
				"Anonymous candidate groups remain separately expandable instead of becoming one oversized card");
		assertTrue(terminalScreen.contains("SIGNAL_ROW_HEIGHT = 12"),
				"Expanded signal details retain readable vertical spacing");
		assertTrue(terminalScreen.contains("Component.literal(\"    · \")"),
				"Expanded signal details use a quiet indented list instead of dense tree branches");
		assertTrue(terminalScreen.contains("markerFragment(entry.type())"),
				"Position-free unrecorded markers remain distinct from expandable coordinate cards");

		String fragmentInvestigation = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/world/FragmentInvestigationService.java"),
				StandardCharsets.UTF_8);
		assertTrue(fragmentInvestigation.contains("SignalBand.WEATHER, SignalBand.MINING, SignalBand.PUBLIC, SignalBand.UNKNOWN"),
				"Legacy log wire ids remain stable while the client aggregates every fragment event");
		assertTrue(fragmentInvestigation.contains("if (record.getIntOr(TerminalData.BAND_STAGE, 0) == 0) return false;"),
				"Structure coordinates stay out of the signal log before the investigation gate");

		assertFalse(fragmentInvestigation.contains("unlockArchiveFromFragments"),
				"Discovering all hidden files must not unlock the complete diary before they are read");
	}

	@Test
	void everyStaticTranslatableKeyExistsAndCoreClientScreensContainNoHardcodedChinese() throws Exception {
		JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		Set<String> keys = new HashSet<>(en.keySet());
		Pattern translatable = Pattern.compile("Component\\.translatable\\(\\s*\"([^\"]+)\"\\s*(?:,|\\))");
		for (Path root : new Path[]{Path.of("src/main/java"), Path.of("src/client/java")}) {
			for (Path path : Files.walk(root).filter(value -> value.toString().endsWith(".java")).toList()) {
				Matcher matcher = translatable.matcher(Files.readString(path, StandardCharsets.UTF_8));
				while (matcher.find()) assertTrue(keys.contains(matcher.group(1)), path + " -> " + matcher.group(1));
			}
		}
		Pattern chineseLiteral = Pattern.compile("\"[^\"\\r\\n]*[\\x{3400}-\\x{9FFF}][^\"\\r\\n]*\"");
		for (String relative : new String[]{
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalScreen.java",
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldDecayClient.java"}) {
			String source = Files.readString(Path.of(relative), StandardCharsets.UTF_8);
			assertFalse(chineseLiteral.matcher(source).find(), relative);
		}
	}

	@Test
	void debugPanelUsesMAndContainsThreeCurrentSectionsAndScrollableAnomalies() throws Exception {
		String client = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/DebugPanelClient.java"), StandardCharsets.UTF_8);
		String screen = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/DebugPanelScreen.java"), StandardCharsets.UTF_8);
		assertTrue(client.contains("GLFW.GLFW_KEY_M"));
		assertFalse(client.contains("GLFW.GLFW_KEY_F7"));
		for (String section : new String[]{"总览", "主线", "异象"})
			assertTrue(screen.contains(section), section);
		assertFalse(screen.contains("ENDING(\"终局\")"));
		for (String removed : new String[]{"local_file_prev", "local_facility_prev", "local_anomaly_prev",
				"完成生存节点", "显示设施坐标", "解锁当前文件"})
			assertFalse(screen.contains(removed), removed);
		for (String internalTerm : new String[]{"停止/恢复租约", "满复合", "恢复异象导演", "BOSS：", "造身：",
				"最终实体", "肉身映射", "剧情上限"})
			assertFalse(screen.contains(internalTerm), internalTerm);
		assertTrue(screen.contains("AnomalyCatalog.definitions()"));
		assertTrue(screen.contains("sectionCountForTesting()"));
		assertTrue(screen.contains("mouseScrolled"));
	}

	@Test
	void semanticAnomalySoundsHaveMatchingSubtitles() throws Exception {
		JsonObject sounds = JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		for (String event : new String[]{"anomaly_echo", "window_glitch", "door_cascade", "rule_collapse"}) {
			assertTrue(sounds.has(event), event);
			assertTrue(sounds.getAsJsonObject(event).has("subtitle"), event);
		}
		assertFalse(sounds.has("hostile_echo"));
		assertFalse(sounds.has("composite_breach"));
		for (String event : new String[]{"rework_joint"}) {
			var definition = sounds.getAsJsonObject(event);
			assertTrue(definition.has("subtitle"), event);
			for (var sound : definition.getAsJsonArray("sounds")) {
				String name = sound.getAsString();
				assertTrue(name.startsWith("thefourthfrequency:entity/"), name);
				Path ogg = ASSETS.resolve("sounds/" + name.substring(name.indexOf(':') + 1) + ".ogg");
				assertTrue(Files.isRegularFile(ogg), ogg.toString());
				byte[] header = Files.readAllBytes(ogg);
				assertTrue(header.length > 4 && header[0] == 'O' && header[1] == 'g'
						&& header[2] == 'g' && header[3] == 'S', ogg.toString());
			}
		}
	}

	@Test
	void anomalyArtHasRequiredDimensionsAlphaAndSourceMasters() throws Exception {
		var hand = ImageIO.read(ASSETS.resolve("textures/gui/anomaly/peripheral_hand.png").toFile());
		assertEquals(512, hand.getWidth());
		assertEquals(256, hand.getHeight());
		assertTrue(hand.getColorModel().hasAlpha());
		boolean transparent = false;
		boolean opaque = false;
		for (int y = 0; y < hand.getHeight(); y += 8) for (int x = 0; x < hand.getWidth(); x += 8) {
			int alpha = hand.getRGB(x, y) >>> 24;
			transparent |= alpha == 0;
			opaque |= alpha > 220;
		}
		assertTrue(transparent && opaque, "Single hand texture must contain transparent background and visible hand");
		var eye = ImageIO.read(ASSETS.resolve("textures/gui/anomaly/eye_item.png").toFile());
		assertEquals(128, eye.getWidth());
		assertEquals(128, eye.getHeight());
		assertTrue(eye.getColorModel().hasAlpha());
		assertEquals(0, eye.getRGB(0, 0) >>> 24);
		assertEquals(0, eye.getRGB(eye.getWidth() - 1, eye.getHeight() - 1) >>> 24);
		var windowEye = ImageIO.read(ASSETS.resolve("textures/gui/anomaly/eye_window.png").toFile());
		assertEquals(64, windowEye.getWidth());
		assertTrue(windowEye.getColorModel().hasAlpha());
		assertEquals(0, windowEye.getRGB(0, 0) >>> 24);
		Path watcherPath = ASSETS.resolve("textures/entity/watcher.png");
		Path watcherEmissivePath = ASSETS.resolve("textures/entity/watcher_emissive.png");
		assertTrue(Files.isRegularFile(watcherPath));
		assertTrue(Files.isRegularFile(watcherEmissivePath));
		var watcher = ImageIO.read(watcherPath.toFile());
		var watcherEmissive = ImageIO.read(watcherEmissivePath.toFile());
		assertEquals(256, watcher.getWidth()); assertEquals(256, watcher.getHeight());
		assertEquals(256, watcherEmissive.getWidth()); assertEquals(256, watcherEmissive.getHeight());
		assertTrue(watcher.getColorModel().hasAlpha());
		assertTrue(watcherEmissive.getColorModel().hasAlpha());
		int nonTransparent = 0;
		int maximumAlpha = 0;
		for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
			assertEquals(255, watcher.getRGB(x, y) >>> 24, "base alpha at " + x + "," + y);
			int alpha = watcherEmissive.getRGB(x, y) >>> 24;
			if (alpha == 0) continue;
			nonTransparent++;
			maximumAlpha = Math.max(maximumAlpha, alpha);
			assertTrue(x >= 160 && x < 240 && y < 16,
					"emissive pixel escaped the eye UV at " + x + "," + y);
		}
		assertTrue(nonTransparent > 0 && nonTransparent <= 256 * 256 * 0.08,
				"emissive coverage=" + nonTransparent);
		assertTrue(maximumAlpha >= 112 && maximumAlpha <= 120, "emissive max alpha=" + maximumAlpha);
		assertNotEquals(java.util.Arrays.hashCode(Files.readAllBytes(watcherPath)),
				java.util.Arrays.hashCode(Files.readAllBytes(watcherEmissivePath)));
		assertFalse(Files.exists(ASSETS.resolve("textures/entity/watcher_eyes.png")));
		assertFalse(Files.exists(ASSETS.resolve("textures/entity/orbiter.png")));
		assertEquals(32, ImageIO.read(ASSETS.resolve("textures/block/missing_texture.png").toFile()).getWidth());
		assertTrue(Files.isRegularFile(Path.of("tools/assets/anomaly/eye_master.png")));
		assertTrue(Files.isRegularFile(Path.of("tools/assets/anomaly/hand_palm_long_master.png")));
		assertFalse(Files.exists(Path.of("tools/assets/anomaly/peripheral_hands.gif")));
	}

	@Test
	void anomalyPresentationUsesV3ControllerWithoutPotionOrPromptDependencies() throws Exception {
		String networking = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/TerminalNetworking.java"), StandardCharsets.UTF_8);
		assertTrue(networking.contains("AnomalyStartS2C.TYPE"));
		assertTrue(networking.contains("AnomalyPhaseS2C.TYPE"));
		assertTrue(networking.contains("AnomalyCompleteC2S.TYPE"));
		assertFalse(networking.contains("AmbientAnomalyPayload"));
		String controller = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/AnomalyPresentationController.java"),
				StandardCharsets.UTF_8);
		for (String forbidden : new String[]{"MobEffect", "MobEffects", "displayClientMessage",
				"setOverlayMessage", "setActionBarText"}) assertFalse(controller.contains(forbidden), forbidden);
		String runtime = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/AnomalyRuntimeService.java"), StandardCharsets.UTF_8);
		assertTrue(runtime.contains("ACTIVE.containsKey(player)"));
		assertTrue(runtime.contains("earliestCompletionTick"));
		assertTrue(runtime.contains("recordCompleted"));
	}

	@Test
	void desktopPresenceTypesIntoOwnedForegroundVerifiedNotepad() throws Exception {
		String source = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/meta_windows/WindowsAnomalyController.java"),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("meta/desktop_presence.txt"));
		assertTrue(source.contains("Files.createTempDirectory"));
		assertTrue(source.contains("Files.createFile"));
		assertFalse(source.contains("Files.copy"));
		assertTrue(source.contains("GetForegroundWindow"));
		assertTrue(source.contains("GetWindowThreadProcessId"));
		assertTrue(source.contains("AttachThreadInput"));
		assertTrue(source.contains("SendInput"));
		assertTrue(source.contains("MOUSEINPUT"));
		assertTrue(source.contains("powershell.exe"));
		assertTrue(source.contains("OwnedTree"));
		assertTrue(source.contains("ZoomToMaximum"));
		assertTrue(source.contains("VK_OEM_PLUS"));
		assertTrue(source.contains("step < 64"));
		assertTrue(source.contains("descendants()"));
		assertTrue(source.contains("notepad.exe"));
		assertTrue(Files.isRegularFile(ASSETS.resolve("meta/desktop_presence.txt")));
	}

	@Test
	void revisedAnomaliesUseTransparentWatcherDenseFogAndFixedTriggerView() throws Exception {
		String controller = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/AnomalyPresentationController.java"),
				StandardCharsets.UTF_8);
		String watcher = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_render/WatcherRenderer.java"),
				StandardCharsets.UTF_8);
		String watcherModel = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_render/WatcherModel.java"),
				StandardCharsets.UTF_8);
		String watcherState = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_render/WatcherRenderState.java"),
				StandardCharsets.UTF_8);
		String clientInitializer = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TheFourthFrequencyClient.java"),
				StandardCharsets.UTF_8);
		String skyMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/SkyRendererAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String fogMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/FogRendererAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String optionsMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/OptionsAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String renderDistanceOptionMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/OptionInstanceRenderDistanceMixin.java"),
				StandardCharsets.UTF_8);
		String viewDistancePolicy = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/client_ui/DimensionViewDistancePolicy.java"),
				StandardCharsets.UTF_8);
		String viewDistanceController = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/DimensionViewDistanceController.java"),
				StandardCharsets.UTF_8);
		String modConfig = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/config/ModConfig.java"),
				StandardCharsets.UTF_8);
		String inputMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/KeyboardInputAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String handMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/ItemInHandRendererAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String entityRendererMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/AvatarRendererAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String renderRegionMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/RenderSectionRegionAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String levelRendererMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/LevelRendererAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String itemNameMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/ItemStackAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String localPlayerMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/LocalPlayerAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		String mixinConfig = Files.readString(Path.of("src/main/resources/thefourthfrequency.mixins.json"),
				StandardCharsets.UTF_8);
		String channel = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/ChannelOverrideScreen.java"),
				StandardCharsets.UTF_8);
		assertFalse(controller.contains("ModEntities.ORBITER"));
		assertTrue(controller.contains("redSkyShaderColor"));
		assertTrue(controller.contains("-HAND_TEXTURE_WIDTH"));
		assertFalse(controller.contains("scale(-1.0F"));
		assertFalse(controller.contains("alpha << 24 | 0x00A01018"));
		assertTrue(controller.contains("CameraType.FIRST_PERSON"));
		assertTrue(controller.contains("trigger_view_camera_fixed"));
		assertTrue(controller.contains("SECOND_PERSON_BODY_ID"));
		assertTrue(controller.contains("second_person_body_proxy"));
		assertTrue(controller.contains("secondPersonBody.noPhysics = true"));
		assertTrue(controller.contains("cameraAnchor.noPhysics = true"));
		assertTrue(controller.contains("shouldControlSeparatedPlayer"));
		assertTrue(controller.contains("action_echo_animation"));
		assertTrue(controller.contains("levelRenderer.allChanged()"));
		assertTrue(controller.contains("PERIPHERAL_HAND_ENTER_FRACTION = 0.42F"));
		assertTrue(controller.contains("width * 0.58F"));
		assertTrue(controller.contains("width * 0.36F"));
		assertTrue(controller.contains("-LIGHT_DROPOUT_SCAN_RADIUS, -LIGHT_DROPOUT_SCAN_RADIUS"));
		assertTrue(controller.contains("LIGHT_DROPOUT_DARK_RADIUS = LIGHT_DROPOUT_SCAN_RADIUS + 15"));
		assertTrue(controller.contains("lightDropoutCenter"));
		assertFalse(controller.contains("lightVisibleFrom"));
		assertTrue(controller.contains("missing_texture_proxies_rendered"));
		assertTrue(controller.contains("isInViewCone"));
		String itemMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/GuiGraphicsAnomalyMixin.java"),
				StandardCharsets.UTF_8);
		assertTrue(itemMixin.contains("ItemStack;III)V"));
		assertTrue(itemMixin.contains("LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V"));
		assertTrue(watcher.contains("extends MobRenderer<WatcherEntity, WatcherRenderState, WatcherModel>"));
		assertFalse(watcher.contains("submitCustomGeometry"));
		assertFalse(watcher.contains("RenderTypes.eyes"));
		assertFalse(watcher.contains("textures/gui/anomaly/eye_item.png"));
		assertFalse(watcher.contains("getBlockLightLevel"));
		assertTrue(watcher.contains("textures/entity/watcher.png"));
		assertTrue(watcher.contains("textures/entity/watcher_emissive.png"));
		assertTrue(watcher.contains("entityTranslucentEmissive"));
		assertTrue(watcher.contains("0.22F"));
		assertTrue(watcher.contains("shadowStrength = 0.25F"));
		assertTrue(watcher.contains("physical.minX - 0.35D"));
		assertTrue(watcher.contains("physical.minY - 0.15D"));
		assertTrue(watcher.contains("onWatcherVisible"));
		assertTrue(watcherModel.contains("extends EntityModel<WatcherRenderState>"));
		for (String part : new String[]{"torso", "neck", "head", "left_arm", "right_arm", "left_leg",
				"right_leg", "hand", "spine", "left_scapula", "right_scapula", "eye", "iris", "pupil"}) {
			assertTrue(watcherModel.contains("\"" + part + "\""), part);
		}
		assertTrue(watcherModel.contains("LayerDefinition.create(mesh, 128, 128)"));
		assertTrue(watcherModel.contains("FULL_TURN / 120.0F"));
		assertTrue(watcherModel.contains("Mth.sin(irisPhase) * 0.03F"));
		assertTrue(watcherModel.contains("iris.xScale = irisScale"));
		assertFalse(watcherModel.toLowerCase(java.util.Locale.ROOT).contains("eyelid"));
		assertTrue(watcherState.contains("extends LivingEntityRenderState"));
		assertTrue(clientInitializer.contains("registerModelLayer(WatcherRenderer.MODEL_LAYER"));
		assertTrue(clientInitializer.contains("DimensionViewDistanceController.initialize()"));
		assertTrue(skyMixin.contains("state.skyColor"));
		assertTrue(skyMixin.contains("state.sunriseAndSunsetColor"));
		assertTrue(fogMixin.contains("setupFog"));
		assertTrue(fogMixin.contains("index = 5"));
		assertTrue(fogMixin.contains("index = 6"));
		assertTrue(fogMixin.contains("28.0F"));
		assertTrue(fogMixin.contains("AtmosphericFogProfile.sample"));
		assertTrue(fogMixin.contains("level.dimension().identifier().toString()"));
		assertTrue(fogMixin.contains("DimensionViewDistanceController.atmosphericChunks"));
		assertTrue(fogMixin.contains("clampRenderStart"));
		assertTrue(fogMixin.contains("clampRenderEnd"));
		assertTrue(viewDistancePolicy.contains("OVERWORLD_CHUNKS = 3")
				&& viewDistancePolicy.contains("NETHER_CHUNKS = 6")
				&& viewDistancePolicy.contains("END_CHUNKS = 12")
				&& viewDistancePolicy.contains("SUCCESS_RETURN_CHUNKS = 16"));
		assertTrue(viewDistanceController.contains("ClientTickEvents.END_CLIENT_TICK")
				&& viewDistanceController.contains("renderDistance().set(locked)")
				&& viewDistanceController.contains("successfulReturnPending")
				&& viewDistanceController.contains("Level.OVERWORLD.equals(client.level.dimension())")
				&& viewDistanceController.contains("ModConfig.ClientState::unlockViewDistance")
				&& viewDistanceController.contains("SUCCESS_RETURN_CHUNKS")
				&& viewDistanceController.contains("client.options.save()"));
		assertTrue(modConfig.contains("boolean viewDistanceUnlocked")
				&& modConfig.contains("unlockViewDistance()"));
		assertTrue(optionsMixin.contains("DimensionViewDistanceController.lockedChunks")
				&& optionsMixin.contains("DimensionViewDistanceController.isLocked()"));
		assertFalse(optionsMixin.contains("FIXED_RENDER_DISTANCE_CHUNKS"));
		assertFalse(optionsMixin.contains("setReturnValue(2)"));
		assertTrue(renderDistanceOptionMixin.contains("widget.active = false"));
		assertTrue(renderDistanceOptionMixin.contains("rejectRenderDistanceChanges"));
		assertTrue(renderDistanceOptionMixin.contains("@ModifyVariable"));
		assertTrue(renderDistanceOptionMixin.contains("DimensionViewDistanceController.lockedChunks")
				&& renderDistanceOptionMixin.contains("DimensionViewDistanceController.isLocked()"));
		assertTrue(renderDistanceOptionMixin.contains("render_distance_locked"));
		assertTrue(mixinConfig.contains("OptionInstanceRenderDistanceMixin"));
		assertTrue(inputMixin.contains("keyPresses = Input.EMPTY"));
		assertTrue(handMixin.contains("renderHandsWithItems"));
		assertTrue(entityRendererMixin.contains("isAnonymousProxy"));
		assertTrue(renderRegionMixin.contains("visualReplacement"));
		assertTrue(renderRegionMixin.contains("markTraceRendered"));
		assertTrue(renderRegionMixin.contains("isLightSourceHidden"));
		assertTrue(renderRegionMixin.contains("Blocks.AIR.defaultBlockState()"));
		assertTrue(levelRendererMixin.contains("LevelRenderer$BrightnessGetter"));
		assertTrue(levelRendererMixin.contains("removeCompiledHiddenBlockLight"));
		assertTrue(itemNameMixin.contains("getHoverName"));
		assertTrue(itemNameMixin.contains("I SEE YOU...."));
		assertTrue(localPlayerMixin.contains("isControlledCamera"));
		assertTrue(localPlayerMixin.contains("shouldControlSeparatedPlayer"));
		assertTrue(mixinConfig.contains("ItemStackAnomalyMixin"));
		assertTrue(mixinConfig.contains("LocalPlayerAnomalyMixin"));
		assertTrue(channel.contains("extends ChatScreen"));
		assertTrue(channel.contains("input.setEditable(false)"));
	}

	@Test
	void alphaSessionEmbedsAndHidesThreeOrderedBasePacks() throws Exception {
		Path packs = Path.of("src/main/resources/resourcepacks");
		Path alpha = packs.resolve("golden_days_alpha");
		Path base = packs.resolve("golden_days_base");
		for (Path pack : List.of(alpha, base)) {
			assertTrue(Files.isRegularFile(pack.resolve("pack.mcmeta")), pack.toString());
			assertTrue(Files.isRegularFile(pack.resolve("pack.png")), pack.toString());
			JsonObject metadata = JsonParser.parseString(Files.readString(pack.resolve("pack.mcmeta"),
					StandardCharsets.UTF_8)).getAsJsonObject();
			assertTrue(metadata.has("pack"));
			assertTrue(Files.isDirectory(pack.resolve("assets/minecraft")));
		}
		assertTrue(Files.isDirectory(base.resolve("patch_21_11")));
		assertTrue(Files.isRegularFile(base.resolve("credits.txt")));
		assertTrue(Files.walk(alpha).filter(Files::isRegularFile).count() > 500);
		assertTrue(Files.walk(base).filter(Files::isRegularFile).count() > 4_000);

		String controller = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/AlphaLoadSessionController.java"),
				StandardCharsets.UTF_8);
		String configManager = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/config/ConfigManager.java"),
				StandardCharsets.UTF_8);
		String config = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/config/ModConfig.java"),
				StandardCharsets.UTF_8);
		String plan = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/client_ui/AlphaResourcePackPlan.java"),
				StandardCharsets.UTF_8);
		String packMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/PackSelectionModelHiddenPacksMixin.java"),
				StandardCharsets.UTF_8);
		String loadingMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/LevelLoadingScreenCorruptionMixin.java"),
				StandardCharsets.UTF_8);
		String overlayMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/LoadingOverlaySuppressionMixin.java"),
				StandardCharsets.UTF_8);
		String persistentLoadingStyle = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/PersistentAlphaLoadingStyle.java"),
				StandardCharsets.UTF_8);
		String titleMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/TitleScreenErosionMixin.java"),
				StandardCharsets.UTF_8);
		String worldDecay = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/WorldDecayClient.java"),
				StandardCharsets.UTF_8);
		String mixinConfig = Files.readString(Path.of("src/main/resources/thefourthfrequency.mixins.json"),
				StandardCharsets.UTF_8);
		String startupMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/MinecraftAlphaStartupMixin.java"),
				StandardCharsets.UTF_8);
		JsonObject enLang = JsonParser.parseString(Files.readString(
				ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject zhLang = JsonParser.parseString(Files.readString(
				ASSETS.resolve("lang/zh_cn.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		assertTrue(controller.contains("PackActivationType.NORMAL"));
		assertTrue(controller.contains("ClientPlayConnectionEvents.INIT"));
		assertTrue(controller.contains("repository.setSelected(activePackOrder)"));
		assertFalse(controller.contains("repository.setSelected(restore)"));
		assertFalse(controller.contains("originalPackOrder"));
		assertTrue(controller.contains("setTitle("));
		assertFalse(controller.contains("updateTitle("));
		assertTrue(controller.contains("AlphaLoadTimeline.versionStage(screenTicks)"));
		assertTrue(controller.contains("claimInitialCorruptionScreen"));
		assertTrue(controller.contains("corruptionEverPlayed"));
		assertTrue(controller.contains("applyJavaIcon"));
		assertTrue(controller.contains("screenTicks >= AlphaLoadTimeline.GLITCH_START_TICK"));
		assertTrue(controller.contains("retainFinalWindowTitle"));
		assertTrue(controller.contains("MENU_VERSION_TEXT = \"Minecraft 1.0.0\""));
		assertTrue(controller.contains("MENU_WINDOW_TITLE = \"Minecraft 1.0.0\""));
		assertEquals("%s - Singleplayer World", enLang.get(
				"window.thefourthfrequency.alpha_load.singleplayer").getAsString());
		assertEquals("%s - Multiplayer", enLang.get(
				"window.thefourthfrequency.alpha_load.multiplayer").getAsString());
		assertEquals("%s - 单人世界", zhLang.get(
				"window.thefourthfrequency.alpha_load.singleplayer").getAsString());
		assertEquals("%s - 多人游戏", zhLang.get(
				"window.thefourthfrequency.alpha_load.multiplayer").getAsString());
		assertTrue(controller.contains("ConfigManager.loadClientState().alphaDowngradeComplete()"));
		assertTrue(controller.contains("ConfigManager.updateClientState"));
		assertTrue(controller.contains("client.screen instanceof TitleScreen"));
		assertTrue(controller.contains("client.getOverlay() != null"));
		assertTrue(controller.contains("ensureAlphaResourceStack(client, false)"));
		assertTrue(controller.contains("preparePersistentPackSelectionBeforeInitialReload"));
		assertTrue(controller.contains("primePersistentIdentity(client)"));
		assertTrue(controller.contains("client.options.resourcePacks.addAll"));
		assertFalse(controller.contains("client.options.save()"));
		assertTrue(controller.contains("containsOrderedAlphaBases"));
		assertTrue(controller.contains("shouldUsePersistentAlphaLoadingStyle"));
		assertTrue(controller.contains("AlphaLoadingPresentationPolicy.usePersistentLegacyPresentation"));
		assertTrue(controller.contains("recordPersistentAlphaLoadingOverlayCreated"));
		assertTrue(controller.contains("recordPersistentAlphaLoadingFirstFrame"));
		assertTrue(startupMixin.contains("Minecraft;options:Lnet/minecraft/client/Options;"));
		assertTrue(startupMixin.contains("shift = At.Shift.AFTER"));
		assertFalse(controller.contains("thefourthfrequency-alpha-state.json"));
		assertTrue(config.contains("alphaDowngradeComplete"));
		assertTrue(configManager.contains("StandardCopyOption.ATOMIC_MOVE"));
		assertFalse(configManager.contains("TerminalData"));
		assertTrue(titleMixin.contains("AlphaLoadSessionController.menuVersionText"));
		assertFalse(worldDecay.contains("setTitle("));
		assertFalse(worldDecay.contains("applyCorruptedIcon"));
		assertFalse(worldDecay.contains("setIcon("));
		assertTrue(mixinConfig.contains("MinecraftTitleRetentionMixin"));
		assertTrue(mixinConfig.contains("MinecraftAlphaStartupMixin"));
		var javaIcon = ImageIO.read(ASSETS.resolve("textures/gui/alpha_java_icon.png").toFile());
		assertEquals(32, javaIcon.getWidth());
		assertEquals(32, javaIcon.getHeight());
		assertFalse(controller.contains("Downloads"));
		assertTrue(plan.indexOf("PROGRAMMER_ART_PACK_ID") < plan.indexOf("GOLDEN_DAYS_BASE_PACK_ID"));
		assertTrue(plan.indexOf("GOLDEN_DAYS_BASE_PACK_ID") < plan.indexOf("GOLDEN_DAYS_ALPHA_PACK_ID"));
		assertTrue(packMixin.contains("selected.removeIf"));
		assertTrue(packMixin.contains("unselected.removeIf"));
		assertTrue(packMixin.contains("method = \"updateRepoSelectedList\""));
		assertTrue(packMixin.contains("repository.getSelectedPacks()"));
		assertTrue(loadingMixin.contains("SimpleSoundInstance.forUI(ModSounds.TERMINAL_FAULT"));
		assertTrue(loadingMixin.contains("AlphaLoadTimeline.copiedFailureLines"));
		assertTrue(loadingMixin.contains("reason == LevelLoadingScreen.Reason.OTHER"));
		assertTrue(loadingMixin.contains("AlphaLoadTimeline.smallFailureCopies"));
		assertTrue(loadingMixin.contains("AlphaLoadTimeline.largeFailureCopies"));
		assertTrue(loadingMixin.contains("AlphaLoadTimeline.failureMotionTick"));
		assertTrue(loadingMixin.contains("AlphaLoadTimeline.legacyRecoveryFrame"));
		assertTrue(loadingMixin.contains("AlphaLoadTimeline.initialNormalFrame"));
		assertTrue(loadingMixin.contains("AlphaLoadTimeline.initialNormalProgress"));
		assertTrue(loadingMixin.contains("holdVanillaProgressAtHalf"));
		assertTrue(loadingMixin.contains("renderFailureOverVanillaPage"));
		assertTrue(loadingMixin.contains("smoothedProgress = AlphaLoadTimeline.initialNormalFrame"));
		assertTrue(loadingMixin.contains("expose the world, not a legacy flash"));
		assertTrue(loadingMixin.contains("thefourthfrequency$chaos"));
		assertFalse(loadingMixin.contains("columnSpacing"));
		assertFalse(loadingMixin.contains("rowSpacing"));
		assertFalse(loadingMixin.contains("driftX"));
		assertFalse(loadingMixin.contains("originX"));
		assertTrue(loadingMixin.contains("translate(targetX, targetY)"));
		assertTrue(loadingMixin.contains("graphics.pose().scale(scale, scale)"));
		assertFalse(loadingMixin.contains("+ growth"));
		assertFalse(loadingMixin.contains("barLeft - 1"));
		assertFalse(loadingMixin.contains("int scanY ="),
				"The first-entry loading corruption must not render the red scanline");
		assertFalse(loadingMixin.contains("0x90FF1010"),
				"The first-entry loading corruption must not restore the red scanline color");
		assertTrue(loadingMixin.contains("isModLoaded(\"thefourthfrequency-test\")"));
		assertTrue(loadingMixin.contains("alpha-loading-corruption.png"));
		assertTrue(loadingMixin.contains("legacy-loading-normal.png"));
		assertTrue(loadingMixin.contains("Component.literal(\"生成世界中\")"));
		assertTrue(loadingMixin.contains("Component.literal(\"生成地形中\")"));
		assertTrue(loadingMixin.contains("textures/block/dirt.png"));
		assertTrue(loadingMixin.contains("shouldRenderLegacyLoadingScreen"));
		assertTrue(loadingMixin.contains("hideVanillaLoadingText"));
		assertFalse(loadingMixin.contains("0xE0080507"));
		assertTrue(overlayMixin.contains("consumeResourceReloadAnimationSuppression"));
		assertTrue(overlayMixin.contains("screen.render(graphics"));
		assertTrue(overlayMixin.contains("graphics.enableScissor(0, 0, 0, 0)"));
		assertTrue(overlayMixin.contains("graphics.disableScissor()"));
		assertTrue(overlayMixin.contains("keepUnderlyingScreen"));
		assertFalse(overlayMixin.contains("method = \"render\", at = @At(\"HEAD\"), cancellable = true"));
		assertTrue(overlayMixin.contains("registerPersistentAlphaLogo"));
		assertTrue(overlayMixin.contains("usePersistentAlphaBackground"));
		assertTrue(overlayMixin.contains("usePersistentAlphaLogo"));
		assertTrue(overlayMixin.contains("drawPersistentAlphaProgress"));
		assertTrue(overlayMixin.contains("persistentAlphaFirstFrameRecorded"));
		assertTrue(persistentLoadingStyle.contains("registerAndLoad"));
		assertTrue(persistentLoadingStyle.contains(
				"/resourcepacks/golden_days_base/assets/minecraft/textures/gui/title/mojangstudios.png"));
		assertTrue(persistentLoadingStyle.contains("BACKGROUND_COLOR = 0xFF373363"));
		assertTrue(persistentLoadingStyle.contains("PROGRESS_COLOR = 0xFF8E84FF"));
		assertTrue(Files.isRegularFile(base.resolve(
				"assets/minecraft/textures/gui/title/mojangstudios.png")));
		assertTrue(mixinConfig.contains("LevelLoadingScreenCorruptionMixin"));
		assertTrue(mixinConfig.contains("LoadingOverlaySuppressionMixin"));
		assertTrue(mixinConfig.contains("PackSelectionModelHiddenPacksMixin"));
	}

	@Test
	void experienceGapUsesContinuousCollisionMovementWithoutTeleport() throws Exception {
		String serverEffects = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/AnomalyServerEffects.java"),
				StandardCharsets.UTF_8);
		int start = serverEffects.indexOf("private static final class MovementTask");
		int end = serverEffects.indexOf("public static boolean protectedPosition", start);
		String movement = serverEffects.substring(start, end);
		assertTrue(movement.contains("player.setDeltaMovement"));
		assertTrue(movement.contains("player.hurtMarked = true"));
		assertFalse(movement.contains("teleportTo"));
		assertTrue(serverEffects.contains("distance <= 24"));
	}
}
