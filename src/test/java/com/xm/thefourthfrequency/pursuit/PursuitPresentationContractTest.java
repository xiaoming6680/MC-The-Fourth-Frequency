package com.xm.thefourthfrequency.pursuit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitPresentationContractTest {
	@Test
	void pursuitUsesOnePersistentRedStatusAndNoBossBar() throws Exception {
		String controller = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitFormController.java"),
				StandardCharsets.UTF_8);
		assertFalse(controller.contains("ServerBossEvent"));
		assertFalse(controller.contains("BossEvent."));
		assertFalse(controller.contains("displayClientMessage"));
		assertFalse(controller.contains("TerminalNoticeService.pursuit"));

		String noticeHud = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalNoticeHud.java"),
				StandardCharsets.UTF_8);
		assertTrue(noticeHud.contains("PURSUIT_BACKGROUND"));
		assertTrue(noticeHud.contains("TONE_PURSUIT_WARNING"));
		assertTrue(noticeHud.contains("PursuitPresentationClient.pursuitHudActive()"));
		assertTrue(noticeHud.contains("PursuitPresentationClient.holdsNoticeQueue()"));
		assertTrue(noticeHud.contains("message.thefourthfrequency.pursuit.try_escape"));
		assertTrue(noticeHud.contains("message.thefourthfrequency.pursuit.escaped_temporary"));
	}

	@Test
	void lowResolutionPostEffectAndClientMixinsArePackaged() throws Exception {
		Path effectPath = Path.of(
				"src/main/resources/assets/thefourthfrequency/post_effect/pursuit_low_res.json");
		var effect = JsonParser.parseString(Files.readString(effectPath, StandardCharsets.UTF_8))
				.getAsJsonObject();
		String serialized = effect.toString();
		assertTrue(serialized.contains("minecraft:post/color_convolve"));
		assertTrue(serialized.contains("minecraft:post/bits"));
		assertTrue(serialized.contains("\"MosaicSize\""));

		String mixins = Files.readString(Path.of(
				"src/main/resources/thefourthfrequency.mixins.json"), StandardCharsets.UTF_8);
		assertTrue(mixins.contains("GameRendererPursuitMixin"));
		assertTrue(mixins.contains("GameRendererPostEffectInvoker"));
		assertTrue(mixins.contains("PursuitLevelLoadingScreenMixin"));
	}

	@Test
	void proximityBandsDegradeMonotonicallyTowardContact() throws Exception {
		// One chain per ProximityGrade, ordered farthest to nearest. The picture must get coarser
		// (fewer colour steps, larger mosaic) with every step inward; getting this backwards would
		// hand the player *better* vision the closer the corrector gets, which is the whole point
		// inverted, and nothing else in the build would catch it.
		String[] bands = {"pursuit_low_res_distant", "pursuit_low_res",
				"pursuit_low_res_close", "pursuit_low_res_contact"};
		float previousResolution = Float.MAX_VALUE;
		float previousMosaic = 0.0F;
		for (String band : bands) {
			Path path = Path.of("src/main/resources/assets/thefourthfrequency/post_effect/"
					+ band + ".json");
			assertTrue(Files.exists(path), band + " must exist for its proximity grade");
			var chain = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
					.getAsJsonObject();
			float resolution = Float.NaN;
			float mosaic = Float.NaN;
			for (var pass : chain.getAsJsonArray("passes")) {
				var uniforms = pass.getAsJsonObject().getAsJsonObject("uniforms");
				if (uniforms == null || !uniforms.has("BitsConfig")) continue;
				for (var uniform : uniforms.getAsJsonArray("BitsConfig")) {
					var entry = uniform.getAsJsonObject();
					if ("Resolution".equals(entry.get("name").getAsString())) {
						resolution = entry.get("value").getAsFloat();
					} else if ("MosaicSize".equals(entry.get("name").getAsString())) {
						mosaic = entry.get("value").getAsFloat();
					}
				}
			}
			assertTrue(resolution < previousResolution,
					band + " must drop colour resolution relative to the band before it");
			assertTrue(mosaic > previousMosaic,
					band + " must enlarge the mosaic relative to the band before it");
			previousResolution = resolution;
			previousMosaic = mosaic;
		}
	}

	@Test
	void presentationProtocolIsRegisteredOnBothSides() throws Exception {
		String bootstrap = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/bootstrap/TheFourthFrequency.java"),
				StandardCharsets.UTF_8);
		String client = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TheFourthFrequencyClient.java"),
				StandardCharsets.UTF_8);
		assertTrue(bootstrap.contains("PursuitNetworking.initialize()"));
		assertTrue(client.contains("PursuitPresentationClient.initialize()"));
	}

	@Test
	void debugGuiStartsFormFiveWithoutAdvancingFormalProgress() throws Exception {
		String screen = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/DebugPanelScreen.java"),
				StandardCharsets.UTF_8);
		String service = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/world/DebugPanelService.java"),
				StandardCharsets.UTF_8);
		String director = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitDirector.java"),
				StandardCharsets.UTF_8);
		String controller = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitFormController.java"),
				StandardCharsets.UTF_8);
		String session = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitSessionService.java"),
				StandardCharsets.UTF_8);
		String slots = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitSlotManager.java"),
				StandardCharsets.UTF_8);
		assertTrue(screen.contains("测试第 5 形态追逐"));
		assertTrue(screen.contains("\"pursuit_test\", \"\", 5, true"));
		assertTrue(service.contains("case \"pursuit_test\""));
		assertTrue(director.contains("enterEmptyMirror(player, lease, requestedForm, true)"));
		assertTrue(session.contains("TerminalNoticeService.pursuitWarning(player)"));
		assertTrue(session.contains("TerminalSignalLog.append(record, SignalBand.UNKNOWN"));
		assertTrue(session.contains("PURSUIT_WARNING_RECORDS_REDIRECT, true"));
		assertTrue(controller.contains("if (!runtime.debugSession)"));
		assertTrue(session.contains("if (!debugSession && !\"success\".equals(resolution))"));
		assertTrue(slots.contains("if (!debugSession) record.putBoolean(TerminalData.PURSUIT_PENDING, true)"));
	}

	@Test
	void terminalPursuitWarningsUseMixedColorsAndCompleteTheirRecordLifecycle() throws Exception {
		var zh = JsonParser.parseString(Files.readString(Path.of(
				"src/main/resources/assets/thefourthfrequency/lang/zh_cn.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		String snapshot = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/TerminalSnapshot.java"),
				StandardCharsets.UTF_8);
		String session = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitSessionService.java"),
				StandardCharsets.UTF_8);
		String signalLog = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/terminal/TerminalSignalLog.java"),
				StandardCharsets.UTF_8);
		String warning = zh.get("message.thefourthfrequency.pursuit.warning").getAsString();
		assertTrue(warning.equals("终端传来剧烈震动"));
		String combined = "检测到异常信号波动正在接近.. 做好准备...";
		for (int form = 1; form <= 5; form++) {
			assertTrue(zh.get("terminal.thefourthfrequency.signal.event.pursuit_warning_" + form)
					.getAsString().equals(combined));
		}
		assertTrue(zh.get("terminal.thefourthfrequency.signal.event.pursuit_return_instability")
				.getAsString().equals("用户周围的磁场很不稳定..."));
		assertTrue(snapshot.contains("pursuit_warning.approaching"));
		assertTrue(snapshot.contains("withStyle(ChatFormatting.GREEN)"));
		assertTrue(snapshot.contains("pursuit_warning.prepare"));
		assertTrue(snapshot.contains("withStyle(ChatFormatting.RED)"));
		assertTrue(signalLog.contains("public static boolean removeTypesStartingWith"));
		assertTrue(session.contains("removeTypesStartingWith(record, \"pursuit_warning_\")"));
		assertTrue(session.contains("\"pursuit_return_instability\""));
		assertTrue(session.contains("successfulResolution(resolution)"));
		assertTrue(session.contains("completedResolution(resolution)"));
	}

	@Test
	void clientWaitsForServerBlackoutAndCoversEveryPursuitLoadingFrame() throws Exception {
		String client = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/PursuitPresentationClient.java"),
				StandardCharsets.UTF_8);
		String levelLoadingMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/LevelLoadingScreenCorruptionMixin.java"),
				StandardCharsets.UTF_8);
		String loadingOverlayMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/LoadingOverlaySuppressionMixin.java"),
				StandardCharsets.UTF_8);
		assertTrue(client.contains("return phase == Phase.BLACKOUT || clearRequested"));
		assertTrue(client.contains("client.getOverlay() != null"));
		assertTrue(client.contains("DESTINATION_FALLBACK_STABLE_TICKS = 12"));
		assertTrue(client.contains("transitionLoadingObserved"));
		assertTrue(client.contains("if (phase == Phase.WARNING) return;"));
		assertFalse(client.contains("PursuitPresentationTimeline.warningProgress(warningTicks)"));
		assertTrue(levelLoadingMixin.contains("PursuitPresentationClient.shouldCoverLoadingScreen()"));
		assertTrue(loadingOverlayMixin.contains("PursuitPresentationClient.shouldCoverLoadingScreen()"));
		assertFalse(client.contains(
				"warningTicks >= PursuitPresentationTimeline.PRELUDE_TICKS) {\n"
						+ "\t\t\t\tphase = Phase.BLACKOUT"));
	}

	@Test
	void pursuitCorrectorBreachesQuicklyCountersTowersAndSlowsInCaves() throws Exception {
		String entity = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/entity/ReworkEntity.java"),
				StandardCharsets.UTF_8);
		assertTrue(entity.contains("PURSUIT_BREACH_STUCK_TICKS = 3"));
		assertTrue(entity.contains("PURSUIT_BREACH_RETRY_TICKS = 1"));
		assertTrue(entity.contains("PURSUIT_MOVEMENT_SPEED = 0.31"));
		assertTrue(entity.contains("PURSUIT_NAVIGATION_SPEED = 1.32"));
		assertTrue(entity.contains("PURSUIT_CAVE_MOVEMENT_SPEED = 0.25"));
		assertTrue(entity.contains("PURSUIT_CAVE_NAVIGATION_SPEED = 1.04"));
		assertTrue(entity.contains("movementSpeed.setBaseValue(PURSUIT_MOVEMENT_SPEED)"));
		assertTrue(entity.contains("AnomalyConditions.caveLike(level, target.blockPosition())"));
		assertTrue(entity.contains("updatePursuitCaveSlowdown(level, hostile)"));
		assertTrue(entity.contains("pursuitCaveSlowdown ? PURSUIT_CAVE_NAVIGATION_SPEED"));
		assertTrue(entity.contains("tickPursuitHighGroundCounter(level, hostile)"));
		assertTrue(entity.contains("PURSUIT_TOWER_SCAN_DEPTH"));
		assertTrue(entity.contains("setDeltaMovement(horizontal.x, launch, horizontal.z)"));
	}

	@Test
	void captureAndEscapeUseThreeSecondResolutionStagesAndHeartLimitChanges() throws Exception {
		String controller = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitFormController.java"),
				StandardCharsets.UTF_8);
		String payload = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/networking/PursuitPresentationPayload.java"),
				StandardCharsets.UTF_8);
		String client = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/PursuitPresentationClient.java"),
				StandardCharsets.UTF_8);
		String policy = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitProgressPolicy.java"),
				StandardCharsets.UTF_8);
		assertTrue(controller.contains("RESOLUTION_TICKS = 60L"));
		assertTrue(policy.contains("HEART_HEALTH_POINTS = 2.0D"));
		assertTrue(policy.contains("CAPTURE_PENALTY_FLOOR_HEALTH = 12.0D"));
		assertTrue(controller.contains("PursuitProgressPolicy.resolutionMaxHealthDelta"));
		assertTrue(controller.contains("PursuitPresentationPayload.CAPTURE_FREEZE"));
		assertTrue(controller.contains("PursuitPresentationPayload.ESCAPE_RESOLUTION"));
		assertTrue(controller.contains("if (!\"caught\".equals(reason))"));
		assertTrue(controller.contains("maximumHealth.setBaseValue(adjusted)"));
		assertTrue(payload.contains("CAPTURE_FREEZE = 4"));
		assertTrue(payload.contains("ESCAPE_RESOLUTION = 5"));
		assertTrue(client.contains("phase == Phase.CAPTURE_FREEZE"));
		assertTrue(client.contains("resolutionTicks++ < 60"));
	}

	@Test
	void pursuitHeartbeatIsPositionalAndTheChaseLightsThePlayersOwnEyes() throws Exception {
		String client = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/PursuitPresentationClient.java"),
				StandardCharsets.UTF_8);
		// The beat has to arrive from where the corrector actually is. Behind a black-and-white
		// mosaic it is the only channel left that can say "not that way", and forUI() - which is
		// what this used to be - has no position at all, so its absence here is the real contract.
		assertTrue(client.contains(
				"playLocalSound(corrector.getX(), corrector.getEyeY(), corrector.getZ(),"));
		assertTrue(client.contains("SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE"));
		assertFalse(client.contains("play(client, SoundEvents.WARDEN_HEARTBEAT"));
		// Volume is what sets a positional sound's audible radius; drop it back under one and the
		// beat stops reaching across the distance band its own cadence is defined over.
		assertTrue(client.contains("HEARTBEAT_VOLUME = 1.75F"));

		String vision = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitVisionService.java"),
				StandardCharsets.UTF_8);
		String controller = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitFormController.java"),
				StandardCharsets.UTF_8);
		String session = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/pursuit/PursuitSessionService.java"),
				StandardCharsets.UTF_8);
		assertTrue(vision.contains("MobEffects.NIGHT_VISION"));
		// Bounded and topped up rather than infinite: a teardown path that ever gets missed has to
		// expire on its own instead of leaving the save permanently lit.
		assertTrue(vision.contains("DURATION_TICKS = 600"));
		assertTrue(vision.contains("REFRESH_BELOW_TICKS = 300"));
		assertFalse(vision.contains("INFINITE_DURATION"));
		assertTrue(controller.contains("PursuitVisionService.apply(player)"));
		assertTrue(controller.contains("PursuitVisionService.maintain(player)"));
		assertTrue(controller.contains("PursuitVisionService.clear(player)"));
		assertTrue(session.contains("PursuitVisionService.clear(player)"));
	}

	@Test
	void pursuitDisablesPauseExitAndReplacesItsLabel() throws Exception {
		String client = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/client_ui/PursuitPresentationClient.java"),
				StandardCharsets.UTF_8);
		String pauseMixin = Files.readString(Path.of(
				"src/client/java/com/xm/thefourthfrequency/mixin/PauseScreenErosionMixin.java"),
				StandardCharsets.UTF_8);
		var zh = JsonParser.parseString(Files.readString(Path.of(
				"src/main/resources/assets/thefourthfrequency/lang/zh_cn.json"),
				StandardCharsets.UTF_8)).getAsJsonObject();
		assertTrue(client.contains("public static boolean locksPauseExit()"));
		assertTrue(client.contains("return phase != Phase.IDLE || clearRequested"));
		assertTrue(pauseMixin.contains("PursuitPresentationClient.locksPauseExit()"));
		assertTrue(pauseMixin.contains("disconnectButton.active = false"));
		assertTrue(pauseMixin.contains("message.thefourthfrequency.pursuit.exit_locked"));
		assertTrue(pauseMixin.contains("method = \"render\", at = @At(\"HEAD\")"));
		assertTrue(zh.get("message.thefourthfrequency.pursuit.exit_locked").getAsString()
				.equals("你不能就这样逃走..."));
	}
}
