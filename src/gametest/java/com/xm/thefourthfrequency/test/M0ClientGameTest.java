package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.config.ConfigManager;
import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.terminal.TerminalControlPolicy;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.ResourceGuidanceService;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import com.xm.thefourthfrequency.world.TerminalActivityTracker;
import com.xm.thefourthfrequency.world.FragmentInvestigationService;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.client_ui.TerminalScreen;
import com.xm.thefourthfrequency.client_ui.EmptySegmentClient;
import com.xm.thefourthfrequency.client_ui.EmptySegmentOverlayScreen;
import com.xm.thefourthfrequency.client_ui.PrivateAnomalyClient;
import com.xm.thefourthfrequency.client_ui.TerminalClientAudio;
import com.xm.thefourthfrequency.client_ui.DebugPanelScreen;
import com.xm.thefourthfrequency.client_ui.FirstRunNoticeController;
import com.xm.thefourthfrequency.client_ui.FirstRunNoticeScreen;
import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import com.xm.thefourthfrequency.client_ui.AlphaLoadTimeline;
import com.xm.thefourthfrequency.client_ui.AlphaResourcePackPlan;
import com.xm.thefourthfrequency.client_ui.DimensionViewDistanceController;
import com.xm.thefourthfrequency.client_ui.DimensionViewDistancePolicy;
import com.xm.thefourthfrequency.terminal.TerminalAnomalyLogService;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import com.xm.thefourthfrequency.narrative.NarrativeFileCatalog;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.body.BodyConstructionService;
import com.xm.thefourthfrequency.correction.CorrectionOrganService;
import com.xm.thefourthfrequency.correction.CorrectionState;
import com.xm.thefourthfrequency.correction.CorrectionTargetService;
import com.xm.thefourthfrequency.correction.EmptySegmentService;
import com.xm.thefourthfrequency.correction.TrendSwarmService;
import com.xm.thefourthfrequency.entity.ReworkEntity;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import com.xm.thefourthfrequency.ending.EndingOutcome;
import com.xm.thefourthfrequency.ending.EndingState;
import com.xm.thefourthfrequency.ending.FinalConfrontationService;
import com.xm.thefourthfrequency.world.PlayerPatternService;
import com.xm.thefourthfrequency.world.DebugPanelService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import com.xm.thefourthfrequency.meta_api.MetaController;
import com.xm.thefourthfrequency.meta_api.MetaEvent;
import com.xm.thefourthfrequency.meta_api.MockMetaPlatformAdapter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class M0ClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		ClientGameTestSelection selection = ClientGameTestSelection.current();
		if (selection.runsAlphaRelaunch()) {
			runAlphaRelaunch(context);
			return;
		}
		if (!selection.runsMainline()) {
			if (selection.runsNoticeEntry()) {
				assertAndAcknowledgeFirstRunNotice(context);
			} else {
				acknowledgeFirstRunNoticeForFocusedSuite(context);
			}
			context.waitForScreen(TitleScreen.class);
			if (selection.runsNoticeEntry()) {
				context.waitTicks(10);
				context.takeScreenshot("m1-first-run-title-after-entry");
			}
			return;
		}
		assertAndAcknowledgeFirstRunNotice(context);
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(client -> {
			if (!client.getLanguageManager().getSelected().equals("zh_cn")) {
				throw new AssertionError("Terminal UI screenshots must run with the Chinese language selected");
			}
		});
		context.runOnClient(DimensionViewDistanceController::resetForTesting);
		assertInitialOverworldRenderDistance(context);
		if (Boolean.getBoolean("thefourthfrequency.realMetaSmoke")) {
			context.runOnClient(client -> {
				var awakened = MetaController.dispatchForTesting(MetaEvent.FINAL_BODY_AWAKENED);
				if (!awakened.externalEffects() || awakened.degraded() || awakened.artifacts().size() != 3) {
					throw new AssertionError("M9 real Windows Meta awakening degraded or missed owned artifacts");
				}
			});
			context.waitTicks(10);
			context.runOnClient(client -> {
				var terminated = MetaController.dispatchForTesting(MetaEvent.FOURTH_BAND_TERMINATED);
				if (!terminated.externalEffects() || terminated.degraded() || terminated.artifacts().isEmpty()) {
					throw new AssertionError("M9 real Windows Meta termination degraded or missed its owned rename");
				}
				MetaController.restore();
			});
		}
		MockMetaPlatformAdapter metaAdapter = new MockMetaPlatformAdapter();
		MetaController.useAdapterForTesting(metaAdapter);
		assertAlphaBasePacksHidden(context);
		context.waitTicks(45);
		context.takeScreenshot("m0-title-screen");

		if (RuntimeServices.config() == null) {
			throw new AssertionError("Common runtime services must be initialized on the client");
		}

		TestWorldSave save;
		BlockPos stationPosition;
		BlockPos removedWall;
		TerminalPersistenceProof terminalProof;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.runOnClient(client -> {
				if (client.screen instanceof FirstRunNoticeScreen || FirstRunNoticeController.pendingForTesting())
					throw new AssertionError("Acknowledged title-screen notice appeared again during first world entry");
			});
			singleplayer.getClientWorld().waitForChunksRender();
			context.waitTicks(30);
			assertAlphaSessionLoaded(context);
			save = singleplayer.getWorldSave();
			stationPosition = singleplayer.getServer().computeOnServer(server -> {
				FrequencyWorldData data = FrequencyWorldData.get(server);
				if (!data.stationComplete()) {
					throw new AssertionError("Relay Station Zero did not finish its bounded build");
				}
				return data.stationPosition().orElseThrow();
			});

			assertSingleOwnedTerminal(context);
			assertPlayerInsideStation(context, stationPosition);
			context.takeScreenshot("m1-relay-station-zero");
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (!"HOME".equals(terminal.pageForTesting())) {
					throw new AssertionError("A terminal opened on a page other than HOME");
				}
				terminal.selectPageForTesting(1);
			});
			context.takeScreenshot("r-terminal-tools-grid");
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.selectPageForTesting(0);
				terminal.openToolForTesting(TerminalTool.HOME.slot());
				if (!"TOOLS".equals(terminal.pageForTesting()) || !terminal.toolReturnsHomeForTesting()) {
					throw new AssertionError("A HOME shortcut must open the full detail page with a HOME back target");
				}
			});
			context.waitTicks(2);
			context.takeScreenshot("r-terminal-tool-home-empty");
			context.runOnClient(client -> ((TerminalScreen) client.screen).setHomeForTesting());
			context.waitTicks(2);
			context.takeScreenshot("r-terminal-tool-home-saved");
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.backFromToolForTesting();
				if (!"HOME".equals(terminal.pageForTesting())) {
					throw new AssertionError("The detail back control did not return a HOME-opened tool to HOME");
				}
				terminal.selectPageForTesting(1);
				terminal.openToolForTesting(TerminalTool.WEATHER.slot());
			});
			context.waitTicks(2);
			context.takeScreenshot("r-terminal-tool-weather-current");
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.activateSelectedToolForTesting();
				if (!"HOME".equals(terminal.pageForTesting())
						|| terminal.homeLiveToolForTesting() != TerminalTool.WEATHER.slot()) {
					throw new AssertionError("Pinning an information tool must replace the HOME middle cards");
				}
			});
			context.waitTicks(2);
			context.takeScreenshot("r-terminal-home-weather-live-info");
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.closeHomeLiveToolForTesting();
				if (terminal.homeLiveToolForTesting() != TerminalToolService.NO_TOOL) {
					throw new AssertionError("The live information card close control did not restore HOME recommendations");
				}
			});
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.selectPageForTesting(1);
				terminal.openToolForTesting(TerminalTool.NAVIGATION.slot());
				if (terminal.selectedToolForTesting() != TerminalToolService.NO_TOOL) {
					throw new AssertionError("A locked tool opened its detail page");
				}
			});
			context.waitTicks(2);
			context.takeScreenshot("r-terminal-locked-tool-stays-closed");
			context.runOnClient(client -> ((TerminalScreen) client.screen).selectPageForTesting(2));
			context.waitTicks(2);
			context.takeScreenshot("r-terminal-records-page");
			context.runOnClient(client -> ((TerminalScreen) client.screen).selectPageForTesting(0));
			context.takeScreenshot("r-terminal-home-page");
			setTerminalView(context, TerminalControlPolicy.Mode.FILES.ordinal(),
					TerminalControlPolicy.DEFAULT_TUNING, 0);
			context.waitTicks(2);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openLogDirectoryForTesting();
				if (terminal.fileCountForTesting() != 1 || terminal.selectedFileForTesting() != -1
						|| !terminal.fileIdForTesting(0).equals("encrypted_witness_file")
						|| !"FILES".equals(terminal.pageForTesting())) {
					throw new AssertionError("A new terminal must expose only the locked complete diary in FILES");
				}
			});
			context.takeScreenshot("r86-file-directory-initial-locked-diary");
			setTerminalView(context, TerminalControlPolicy.Mode.SIGNAL.ordinal(),
					TerminalControlPolicy.DEFAULT_TUNING, 0);
			context.waitTicks(2);
			context.waitTicks(65);
			context.takeScreenshot("m2-terminal-home-receiver-standby");
			singleplayer.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				var data = FrequencyWorldData.get(server);
				player.getInventory().add(new ItemStack(Items.CRIMSON_PLANKS, SurvivalProgressService.REQUIRED_WOOD));
				SurvivalProgressService.updatePlayer(player, data);
				TerminalRuntimeService.refresh(player);
			});
			context.waitTicks(4);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.selectPageForTesting(1);
				terminal.openToolForTesting(TerminalTool.NAVIGATION.slot());
				terminal.setTuningForTesting(18);
				if (terminal.tuningForTesting() != 18) {
					throw new AssertionError("The receiver dial did not provide local mechanical feedback");
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("m2-signal-tool-no-nearby-receiver");
			context.runOnClient(client -> client.screen.onClose());
			context.waitTicks(4);
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (terminal.modeForTesting() != TerminalControlPolicy.Mode.SIGNAL.ordinal()
						|| terminal.tuningForTesting() != TerminalControlPolicy.DEFAULT_TUNING) {
					throw new AssertionError("Terminal did not reopen with the contextual receiver at rest");
				}
				terminal.onClose();
			});
			context.waitTicks(4);

			singleplayer.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				var data = FrequencyWorldData.get(server);
				BlockPos orePosition = player.blockPosition().below(2);
				server.overworld().setBlockAndUpdate(orePosition, Blocks.IRON_ORE.defaultBlockState());
				com.xm.thefourthfrequency.terminal.TerminalToolService.selectResource(
						player, TerminalResource.IRON.wireId());
				ResourceGuidanceService.updatePlayer(player);
				com.xm.thefourthfrequency.terminal.TerminalToolService.startGuidance(
						player, TerminalTool.MINERALS.slot());
				if (!data.terminalRecord(player.getUUID()).orElseThrow()
						.getBooleanOr(TerminalData.TARGET_LOCATED, false)) {
					throw new AssertionError("Client fixture did not locate the real iron ore");
				}
			});
			context.waitTicks(20);
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			setTerminalView(context, 0, TerminalControlPolicy.DEFAULT_TUNING, 0);
			context.waitTicks(6);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openToolForTesting(TerminalTool.MINERALS.slot());
				if (!terminal.navigationActiveForTesting()
						|| terminal.selectedResourceForTesting() != TerminalResource.IRON.wireId()
						|| terminal.guidanceToolForTesting() != TerminalTool.MINERALS.slot()) {
					throw new AssertionError("Located mining target did not activate the authoritative navigation DTO");
				}
			});
			context.takeScreenshot("r-terminal-tool-minerals-real-scan");
			context.runOnClient(client -> ((TerminalScreen) client.screen).activateSelectedToolForTesting());
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (terminal.guidanceToolForTesting() != TerminalToolService.NO_TOOL) {
					throw new AssertionError("The active navigation button did not stop mineral guidance");
				}
				terminal.activateSelectedToolForTesting();
				if (!"HOME".equals(terminal.pageForTesting())
						|| terminal.homeLiveToolForTesting() != TerminalTool.MINERALS.slot()) {
					throw new AssertionError("Restarting navigation did not create the HOME live information card");
				}
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				if (((TerminalScreen) client.screen).guidanceToolForTesting() != TerminalTool.MINERALS.slot()) {
					throw new AssertionError("The shared navigation button did not restart authoritative guidance");
				}
			});
			context.takeScreenshot("r-terminal-home-minerals-live-info");
			if (selection.runsToolsUi()) {
				singleplayer.getServer().runOnServer(server -> {
					var player = server.getPlayerList().getPlayers().getFirst();
					TerminalSignalService.record(player, com.xm.thefourthfrequency.terminal.SignalBand.UNKNOWN,
							"signature_anomaly", 0, 1, true);
				});
				context.waitTicks(2);
				context.runOnClient(client -> {
					TerminalScreen terminal = (TerminalScreen) client.screen;
					if (terminal.unreadCountForTesting() < 1 || !terminal.unreadFlashOnForTesting()) {
						throw new AssertionError("A new record did not activate the top-bar unread alert");
					}
				});
				context.takeScreenshot("r-terminal-unread-top-bar-alert");
				context.waitTicks(45);
				context.runOnClient(client -> {
					TerminalScreen terminal = (TerminalScreen) client.screen;
					if (terminal.unreadCountForTesting() < 1 || terminal.unreadFlashOnForTesting()) {
						throw new AssertionError("The unread marker must remain after its short flash has stopped");
					}
				});
				context.takeScreenshot("r-terminal-unread-top-bar-settled");
				closeTerminal(context);
				return;
			}
			closeTerminal(context);
			singleplayer.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				for (int count = 0; count < 32; count++)
					TerminalActivityTracker.record(player, TerminalData.MINED_BLOCKS, "mined");
				player.getInventory().add(new ItemStack(Items.RAW_IRON));
				ResourceGuidanceService.updatePlayer(player);
			});
			context.waitTicks(120);
			context.runOnClient(client -> client.getToastManager().clear());
			assertSignatureBoundTerminal(context);
			singleplayer.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				if (!DebugPanelService.setEnabled(player, true)) throw new AssertionError("Debug permission was not enabled");
				DebugPanelService.open(player);
			});
			context.waitForScreen(DebugPanelScreen.class);
			context.runOnClient(client -> {
				DebugPanelScreen debug = (DebugPanelScreen) client.screen;
				if (debug.sectionCountForTesting() != 4 || debug.anomalyCountForTesting() != 16)
					throw new AssertionError("M debug workbench must expose four sections and all sixteen anomalies");
				debug.triggerAnomalyForTesting("local_rule_collapse");
			});
			context.waitFor(client -> client.screen == null, 100);
			singleplayer.getServer().runOnServer(server -> DebugPanelService.open(server.getPlayerList().getPlayers().getFirst()));
			context.waitForScreen(DebugPanelScreen.class);
			context.runOnClient(client -> ((DebugPanelScreen) client.screen).triggerAnomalyForTesting("red_horizon"));
			context.waitTicks(4);
			context.runOnClient(client -> {
				if (!(client.screen instanceof DebugPanelScreen debug)
						|| !debug.statusMessageForTesting().contains("已有异象正在发生：附近材质丢失"))
					throw new AssertionError("Rejected anomaly request must keep the menu open and explain the active conflict");
			});
			context.takeScreenshot("r74-debug-panel-specific-anomaly-failure");
			context.runOnClient(client -> client.screen.onClose());
			context.waitTicks(4);
			singleplayer.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				com.xm.thefourthfrequency.terminal.AnomalyRuntimeService.interrupt(player, false);
				FrequencyWorldData.get(server).updateTerminalRecord(player.getUUID(), record ->
						record.putBoolean(TerminalData.ANOMALIES_SUSPENDED, true));
			});
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> ((TerminalScreen) client.screen).selectPageForTesting(0));
			context.takeScreenshot("r-terminal-survival-objective-prepare-nether");
			context.runOnClient(client -> ((TerminalScreen) client.screen).selectPageForTesting(2));
			context.waitTicks(2);
			context.takeScreenshot("r-terminal-records-signature-scene");
			setTerminalView(context, 0, TerminalControlPolicy.DEFAULT_TUNING, 0);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.selectPageForTesting(1);
				terminal.openToolForTesting(TerminalTool.NAVIGATION.slot());
			});
			context.waitTicks(5);
			context.takeScreenshot("m3-abnormal-signal-layer");
			context.waitTicks(20);
			context.takeScreenshot("m3-abnormal-signal-layer-confirm");
			context.waitTicks(20);
			context.runOnClient(client -> ((TerminalScreen) client.screen).openLogDirectoryForTesting());
			context.waitTicks(5);
			context.takeScreenshot("m3-file-directory-bound-normal");
			context.waitTicks(20);
			context.takeScreenshot("m3-file-directory-bound-normal-confirm");
			context.runOnClient(client -> ((TerminalScreen) client.screen).openLogEntryForTesting(1));
			context.waitTicks(5);
			context.takeScreenshot("m3-second-predecessor-detail");
			context.waitTicks(20);
			context.takeScreenshot("m3-second-predecessor-detail-confirm");
			context.waitTicks(20);
			closeTerminal(context);

			M4ClientFixture m4Fixture = singleplayer.getServer().computeOnServer(
					M0ClientGameTest::prepareM4Fixture);
			context.waitTicks(20);
			context.takeScreenshot("m4-vanilla-structure-source");
			context.takeScreenshot("m4-unread-red-light");
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.selectPageForTesting(1);
				terminal.openToolForTesting(TerminalTool.NAVIGATION.slot());
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (terminal.unreadCountForTesting() < 1) {
					throw new AssertionError("Expected unread vanilla-structure candidates");
				}
			});
			context.takeScreenshot("m4-candidate-cards-collapsed");
			context.runOnClient(client -> ((TerminalScreen) client.screen).expandFirstSignalCardForTesting());
			context.waitTicks(2);
			context.takeScreenshot("m4-candidate-card-coordinates-expanded");
			verifyReceiverAudioLifecycle(context, m4Fixture.tuning());
			context.takeScreenshot("m4-lcd-nearby-unrecorded-signal");
			context.waitTicks(25);
			context.runOnClient(client -> {
				if (!PrivateAnomalyClient.anomalyId().equals("fragment_2")) {
					throw new AssertionError("Nearby tuning did not preserve the discoverer's private fragment state");
				}
			});
			context.takeScreenshot("m4-private-fragment-signal-state");
			context.runOnClient(client -> ((TerminalScreen) client.screen).openLogDirectoryForTesting());
			context.waitTicks(5);
			context.takeScreenshot("m4-file-directory-fragment-parent");
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openWitnessFragmentsForTesting();
				if (!"LOCKED_DIARY".equals(terminal.logViewForTesting())
						|| terminal.hiddenFileReadPercentForTesting() != 0
						|| terminal.discoveredHiddenFileCountForTesting() != 2) {
					throw new AssertionError("Locked diary did not show 0% while its title recovered two authored segments");
				}
			});
			context.takeScreenshot("m4-diary-half-title-zero-read");
			singleplayer.getServer().runOnServer(M0ClientGameTest::completeM4Fragments);
			context.waitTicks(12);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (!terminal.selectedFileIdForTesting().equals("encrypted_witness_file")
						|| terminal.discoveredHiddenFileCountForTesting() != 4) {
					throw new AssertionError("New hidden files displaced the selected stable diary id");
				}
				for (int fragment = 0; fragment < 3; fragment++) terminal.openFragmentForTesting(fragment);
			});
			context.waitTicks(12);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openWitnessFragmentsForTesting();
				if (!"LOCKED_DIARY".equals(terminal.logViewForTesting())
						|| terminal.hiddenFileReadPercentForTesting() != 75) {
					throw new AssertionError("Complete recovered title must remain locked at three personal reads");
				}
			});
			context.takeScreenshot("m4-diary-complete-title-75-percent-locked");
			context.runOnClient(client -> ((TerminalScreen) client.screen).openFragmentForTesting(3));
			context.waitFor(client -> client.screen instanceof TerminalScreen terminal
					&& terminal.hiddenFileReadPercentForTesting() == 100, 80);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (!"DETAIL".equals(terminal.logViewForTesting())
						|| !terminal.selectedFileIdForTesting().equals("abandoned_warehouse_record")
						|| !terminal.diaryUnlockFadeActiveForTesting()) {
					throw new AssertionError("The fourth read did not preserve the open damaged file while fading the diary title");
				}
			});
			assertArchiveUnlocked(context);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openWitnessFragmentsForTesting();
				if (!"DETAIL".equals(terminal.logViewForTesting())) {
					throw new AssertionError("An unlocked witness file did not open its complete text in the right pane");
				}
			});
			context.takeScreenshot("m4-hidden-files-read-diary-unlocked");
			context.waitTicks(20);
			context.runOnClient(client -> ((TerminalScreen) client.screen).openCompleteFileForTesting());
			context.takeScreenshot("m4-complete-witness-file");
			closeTerminal(context);
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(2);
			setTerminalView(context, TerminalControlPolicy.Mode.SIGNAL.ordinal(),
					TerminalControlPolicy.DEFAULT_TUNING, 0);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.selectPageForTesting(1);
				terminal.openToolForTesting(TerminalTool.NAVIGATION.slot());
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (Math.abs(terminal.waveformMorphTargetForTesting() - 0.42D) > 0.0001D) {
					throw new AssertionError("Stage-two locked carrier did not mix the ECG waveform into the receiver trace");
				}
			});
			context.takeScreenshot("m4-formal-fourth-band-cyan");
			context.waitTicks(40);
			context.takeScreenshot("m4-formal-fourth-band-cyan-confirm");
			context.runOnClient(client -> ((TerminalScreen) client.screen).openLogDirectoryForTesting());
			context.waitTicks(5);
			context.takeScreenshot("m4-file-directory-witness-unlocked");
			context.waitTicks(20);
			context.takeScreenshot("m4-file-directory-witness-unlocked-confirm");
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openWitnessFragmentsForTesting();
				terminal.openCompleteFileForTesting();
			});
			context.waitTicks(2);
			context.takeScreenshot("m4-immutable-local-file");
			context.waitTicks(20);
			context.takeScreenshot("m4-immutable-local-file-confirm");
			context.waitTicks(20);
			context.runOnClient(client -> {
				if (client.screen == null) {
					throw new AssertionError("Archive screen closed before scroll verification");
				}
				for (int row = 0; row < 20; row++) {
					client.screen.mouseScrolled(0.0, 0.0, 0.0, -1.0);
				}
			});
			context.waitTicks(2);
			context.takeScreenshot("m4-immutable-local-file-end");
			context.waitTicks(20);
			context.takeScreenshot("m4-immutable-local-file-end-confirm");
			context.waitTicks(20);
			closeTerminal(context);

			singleplayer.getServer().runOnServer(M0ClientGameTest::prepareM5Fixture);
			context.waitTicks(30);
			context.takeScreenshot("m5-trend-swarm-rework-body");
			context.waitTicks(20);
			context.takeScreenshot("m5-trend-swarm-rework-body-confirm");
			singleplayer.getServer().runOnServer(server -> triggerEmptySegment(
					server, EmptySegmentService.EventType.VIEWPOINT_SEPARATION, 50));
			context.waitTicks(12);
			context.takeScreenshot("m5-empty-viewpoint-separation");
			context.waitTicks(50);
			singleplayer.getServer().runOnServer(server -> triggerEmptySegment(
					server, EmptySegmentService.EventType.EXPERIENCE_GAP, 50));
			context.waitForScreen(EmptySegmentOverlayScreen.class);
			context.waitTicks(3);
			context.takeScreenshot("m5-empty-experience-gap");
			context.waitTicks(55);
			context.runOnClient(client -> {
				if (!EmptySegmentClient.activeEvent().equals("none")
						|| client.screen instanceof EmptySegmentOverlayScreen) {
					throw new AssertionError("Empty-segment client presentation did not force recovery");
				}
			});

			singleplayer.getServer().runOnServer(M0ClientGameTest::beginM6NetherCrossing);
			waitForM6NetherRift(singleplayer, context);
			assertLockedRenderDistance(context, Level.NETHER,
					DimensionViewDistancePolicy.NETHER_CHUNKS);
			BlockPos netherRift = singleplayer.getServer().computeOnServer(M0ClientGameTest::observeM6NetherRift);
			context.waitTicks(8);
			context.runOnClient(client -> {
				client.getToastManager().clear();
				client.gui.getChat().clearMessages(true);
				if (!PrivateAnomalyClient.anomalyId().equals("fracture") || client.level == null
						|| client.level.dimension() != Level.NETHER
						|| !client.level.getBlockState(netherRift).is(ModBlocks.NETHER_RULE_FRACTURE_CORE)) {
					throw new AssertionError("M6 private fracture presentation or physical Nether core was not visible");
				}
			});
			context.takeScreenshot("m6-nether-fracture-private-anomaly");
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			setTerminalView(context, 0, TerminalControlPolicy.DEFAULT_TUNING, 0);
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (!terminal.toolAvailableForTesting(TerminalTool.PORTAL.slot()))
					throw new AssertionError("A real Nether arrival did not unlock the portal tool");
				terminal.openToolForTesting(TerminalTool.PORTAL.slot());
				terminal.activateSelectedToolForTesting();
				if (!"HOME".equals(terminal.pageForTesting())
						|| terminal.homeLiveToolForTesting() != TerminalTool.PORTAL.slot()) {
					throw new AssertionError("Starting portal navigation did not create the HOME live information card");
				}
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (!terminal.navigationActiveForTesting()
						|| terminal.guidanceToolForTesting() != TerminalTool.PORTAL.slot())
					throw new AssertionError("The portal tool did not guide to the real arrival point");
			});
			context.takeScreenshot("r-terminal-tool-portal-real-arrival");
			context.runOnClient(client -> ((TerminalScreen) client.screen).selectPageForTesting(0));
			context.takeScreenshot("m6-terminal-capability-model");
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openToolForTesting(TerminalTool.PORTAL.slot());
				terminal.activateSelectedToolForTesting();
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (terminal.guidanceToolForTesting() != TerminalToolService.NO_TOOL
						|| terminal.homeLiveToolForTesting() != TerminalToolService.NO_TOOL) {
					throw new AssertionError("The same portal navigation button did not stop active navigation");
				}
			});
			closeTerminal(context);
			singleplayer.getServer().runOnServer(M0ClientGameTest::finishM6ReturnCrossing);
			context.waitTicks(8);
			assertLockedRenderDistance(context, Level.OVERWORLD,
					DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
			context.runOnClient(client -> {
				client.getToastManager().clear();
				client.gui.getChat().clearMessages(true);
			});
			context.takeScreenshot("m6-private-return-continuity");
			singleplayer.getServer().runOnServer(M0ClientGameTest::prepareStrongholdEstimateFixture);
			context.waitTicks(4);
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (!terminal.toolAvailableForTesting(TerminalTool.STRONGHOLD.slot()))
					throw new AssertionError("The verified Eye of Ender sample fixture did not unlock the stronghold tool");
				terminal.openToolForTesting(TerminalTool.STRONGHOLD.slot());
				terminal.activateSelectedToolForTesting();
				if (!"HOME".equals(terminal.pageForTesting())
						|| terminal.homeLiveToolForTesting() != TerminalTool.STRONGHOLD.slot()) {
					throw new AssertionError("Starting stronghold navigation did not create the HOME live information card");
				}
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				if (!terminal.navigationActiveForTesting()
						|| terminal.guidanceToolForTesting() != TerminalTool.STRONGHOLD.slot())
					throw new AssertionError("The stronghold estimate did not control the compass");
			});
			context.takeScreenshot("r-terminal-tool-stronghold-estimate");
			closeTerminal(context);

			UUID finalBodyId = singleplayer.getServer().computeOnServer(M0ClientGameTest::prepareM7Fixture);
			context.waitTicks(12);
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(4);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.selectPageForTesting(0);
				if (!terminal.objectiveIdForTesting().equals("protect_anchors")) {
					throw new AssertionError("The active truth-read altar did not expose the protected-device final task");
				}
			});
			context.takeScreenshot("r-terminal-altar-protect-task");
			closeTerminal(context);
			singleplayer.getServer().runOnServer(M0ClientGameTest::equipM7TerminationSpike);
			context.waitTicks(89);
			context.runOnClient(client -> {
				client.getToastManager().clear();
				client.gui.getChat().clearMessages(true);
				client.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
				if (client.level == null || client.level.getEntitiesOfClass(MisreadBodyEntity.class,
						client.player.getBoundingBox().inflate(48.0)).stream()
						.noneMatch(body -> body.getUUID().equals(finalBodyId))) {
					throw new AssertionError("M7 final misread body was not rendered from the authoritative entity");
				}
			});
			context.takeScreenshot("r-stronghold-altar-active");
			context.takeScreenshot("m7-final-misread-body");
			singleplayer.getServer().runOnServer(M0ClientGameTest::moveIntoTerminationRange);
			context.waitTicks(4);
			context.runOnClient(client -> {
				if (client.player == null || client.level == null || client.gameMode == null) {
					throw new AssertionError("M7 client interaction context was not ready");
				}
				MisreadBodyEntity body = client.level.getEntitiesOfClass(MisreadBodyEntity.class,
						client.player.getBoundingBox().inflate(8.0)).stream()
						.filter(value -> value.getUUID().equals(finalBodyId)).findFirst().orElseThrow();
				client.gameMode.interactAt(client.player, body, new EntityHitResult(body), InteractionHand.MAIN_HAND);
			});
			context.waitTicks(12);
			singleplayer.getServer().runOnServer(M0ClientGameTest::assertAndProjectM7Success);
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (!metaAdapter.events().contains(MetaEvent.FINAL_BODY_AWAKENED)
						|| !metaAdapter.events().contains(MetaEvent.FOURTH_BAND_TERMINATED)
						|| MetaController.lastExecution() == null
						|| !MetaController.lastExecution().event().equals(MetaEvent.FOURTH_BAND_TERMINATED)) {
					throw new AssertionError("M8 fixed server event sequence did not reach the client Mock adapter");
				}
			});
			context.waitTicks(5);
			openTerminalThroughClientCallback(context);
			context.waitForScreen(TerminalScreen.class);
			setTerminalView(context, 0, TerminalControlPolicy.DEFAULT_TUNING, 0);
			context.waitTicks(5);
			context.runOnClient(client -> {
				if (!(client.screen instanceof TerminalScreen terminal)) {
					throw new AssertionError("M7 aftermath terminal page closed");
				}
				if (Math.abs(terminal.waveformMorphTargetForTesting() - 1.0D) > 0.0001D) {
					throw new AssertionError("Stage-three terminal did not replace the receiver trace with the ECG waveform");
				}
				terminal.scrollRowsForTesting(20);
			});
			context.waitTicks(2);
			context.takeScreenshot("m7-success-permanent-aftermath");
			context.waitTicks(20);
			context.takeScreenshot("m7-success-permanent-aftermath-confirm");
			singleplayer.getServer().runOnServer(M0ClientGameTest::discoverAllTerminalFiles);
			context.waitTicks(5);
			context.runOnClient(client -> {
				TerminalScreen terminal = (TerminalScreen) client.screen;
				terminal.openLogDirectoryForTesting();
				terminal.moveFileSelectionForTesting(1);
				terminal.moveFileSelectionForTesting(7);
				if (terminal.fileCountForTesting() != 12 || terminal.selectedFileForTesting() != 7
						|| terminal.fileScrollRowForTesting() != 2) {
					throw new AssertionError("The FILES list did not expose all 12 stable file records in one column");
				}
				int fragmentFiles = 0;
				for (int index = 0; index < terminal.fileCountForTesting(); index++) {
					String id = terminal.fileIdForTesting(index);
					if (id.equals("surface_shelter_record") || id.equals("field_observation_record")
							|| id.equals("underground_mine_record") || id.equals("abandoned_warehouse_record")) {
						fragmentFiles++;
					}
				}
				if (fragmentFiles != 4) throw new AssertionError("The FILES list did not include all four damaged files");
			});
			context.waitTicks(2);
			context.takeScreenshot("m7-file-directory-grid-current");
			closeTerminal(context);

			terminalProof = singleplayer.getServer().computeOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				var worldData = FrequencyWorldData.get(server);
				var record = worldData.terminalRecord(player.getUUID()).orElseThrow();
				var correction = CorrectionState.get(worldData);
				return new TerminalPersistenceProof(
						record.getStringOr(TerminalData.WORLD_ID, ""),
						record.getStringOr(TerminalData.TERMINAL_ID, ""),
						record.getStringOr(TerminalData.PERSONALITY_TEMPLATE, ""),
						record.getIntOr(TerminalData.MINED_BLOCKS, 0),
						record.getIntOr(TerminalData.PLACED_BLOCKS, 0),
						record.getIntOr(TerminalData.CRAFTED_ITEMS, 0),
						record.getStringOr(TerminalData.ACCEPTED_ADVICE, ""),
						record.getStringOr(TerminalData.FACILITY_EVIDENCE, ""),
						record.getStringOr(TerminalData.LOCAL_FILE_HASH, ""),
						record.getLongOr(TerminalData.RIFT_POSITION, 0L),
						record.getIntOr(TerminalData.EMPTY_SEGMENT_COUNT, 0),
						correction.getIntOr("dismantle_count", 0),
						correction.getStringOr("rework_entity_uuid", ""),
						record.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0),
						record.getIntOr(TerminalData.BODY_PROGRESS, 0),
						record.getStringOr(TerminalData.TERMINAL_CAPABILITIES, ""),
						BodyConstructionService.netherRiftState(worldData).getLongOr("origin", 0L),
						record.getIntOr(TerminalData.ENDING_VERSION, 0),
						record.getStringOr(TerminalData.ENDING_OUTCOME, ""));
			});

			removedWall = stationPosition.offset(-4, 0, -3);
			singleplayer.getServer().runOnServer(server -> {
				if (server.overworld().getBlockState(removedWall).isAir()) {
					throw new AssertionError("Expected station wall before persistence mutation");
				}
				server.overworld().setBlockAndUpdate(removedWall, Blocks.AIR.defaultBlockState());
			});
		}

		context.waitForScreen(TitleScreen.class);
		context.waitFor(client -> !AlphaLoadSessionController.activeForTesting(), 100);
		int[] legacyScreensBeforeReopen = {-1};
		context.runOnClient(client -> {
			if (AlphaLoadSessionController.activeForTesting()) {
				throw new AssertionError("Alpha resource controller stayed active on the main menu");
			}
			client.updateTitle();
			if (!AlphaLoadSessionController.appliedWindowTitleForTesting().contains("Alpha 1.0.0")) {
				throw new AssertionError("Vanilla title refresh replaced the final Alpha 1.0.0 window title");
			}
			if (!"Minecraft 1.0.0".equals(AlphaLoadSessionController
					.menuVersionText("Minecraft 1.21.11"))) {
				throw new AssertionError("Main-menu version text did not stay downgraded after leaving the world");
			}
			if (AlphaLoadSessionController.corruptionPlayCountForTesting() != 1
					|| !AlphaLoadSessionController.javaIconAppliedForTesting()) {
				throw new AssertionError("First world entry did not finish exactly one corruption and Java icon change");
			}
			List<String> selected = client.getResourcePackRepository().getSelectedPacks().stream()
					.map(net.minecraft.server.packs.repository.Pack::getId).toList();
			if (!selected.containsAll(AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH)) {
				throw new AssertionError("Alpha base packs were unloaded when returning to the main menu: "
						+ selected);
			}
			legacyScreensBeforeReopen[0] = AlphaLoadSessionController
					.legacyLoadingScreensRenderedForTesting();
		});
		context.takeScreenshot("alpha-main-menu-version");
		if (metaAdapter.restoreCount() < 1 || !metaAdapter.ownedProcesses().isEmpty()) {
			throw new AssertionError("M8 world leave did not restore Mock-owned external state");
		}
		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getClientWorld().waitForChunksDownload();
			context.waitTicks(5);
			context.runOnClient(client -> {
				if (client.screen instanceof FirstRunNoticeScreen || FirstRunNoticeController.pendingForTesting())
					throw new AssertionError("Acknowledged first-run notice appeared again after reopening a world");
				if (AlphaLoadSessionController.corruptionPlayCountForTesting() != 1
						|| AlphaLoadSessionController.legacyLoadingScreensRenderedForTesting()
						<= legacyScreensBeforeReopen[0]) {
					throw new AssertionError("Reopened world replayed corruption instead of the legacy loading screen");
				}
			});
			assertSingleOwnedTerminal(context);
			assertBoundTerminal(context);
			reopened.getServer().runOnServer(M0ClientGameTest::loadPersistentReworkChunks);
			context.waitTicks(10);
			BlockPos loadedStation = reopened.getServer().computeOnServer(server -> {
				FrequencyWorldData data = FrequencyWorldData.get(server);
				if (!data.stationComplete()) {
					throw new AssertionError("Station completion flag did not survive restart");
				}
				if (!server.overworld().getBlockState(removedWall).isAir()) {
					throw new AssertionError("Completed station regenerated after restart");
				}
				var player = server.getPlayerList().getPlayers().getFirst();
				var record = data.terminalRecord(player.getUUID()).orElseThrow();
				if (!record.getStringOr(TerminalData.WORLD_ID, "").equals(terminalProof.worldId())
						|| !record.getStringOr(TerminalData.TERMINAL_ID, "").equals(terminalProof.terminalId())
						|| !record.getStringOr(TerminalData.PERSONALITY_TEMPLATE, "").equals(terminalProof.personality())
						|| !record.getBooleanOr(TerminalData.BOUND, false)
						|| record.getIntOr(TerminalData.BAND_STAGE, 0) != 3
						|| !record.getBooleanOr(TerminalData.SECOND_CACHE_UNLOCKED, false)
						|| record.getIntOr(TerminalData.MINED_BLOCKS, 0) != terminalProof.mined()
						|| record.getIntOr(TerminalData.PLACED_BLOCKS, 0) != terminalProof.placed()
						|| record.getIntOr(TerminalData.CRAFTED_ITEMS, 0) != terminalProof.crafted()
						|| !record.getStringOr(TerminalData.ACCEPTED_ADVICE, "").equals(terminalProof.acceptedAdvice())
						|| !terminalProof.acceptedAdvice().contains("iron")
						|| !record.getStringOr(TerminalData.FACILITY_EVIDENCE, "").equals(terminalProof.facilityEvidence())
						|| !record.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false)
						|| !record.getStringOr(TerminalData.LOCAL_FILE_HASH, "").equals(terminalProof.localFileHash())
						|| record.getLongOr(TerminalData.RIFT_POSITION, 0L) != terminalProof.riftPosition()
						|| record.getIntOr(TerminalData.EMPTY_SEGMENT_COUNT, 0) != terminalProof.emptySegmentCount()
						|| record.getBooleanOr(TerminalData.EMPTY_SEGMENT_ACTIVE, false)
						|| record.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0) != terminalProof.portalTransitions()
						|| record.getIntOr(TerminalData.BODY_PROGRESS, 0) < terminalProof.bodyProgress()
						|| !record.getStringOr(TerminalData.TERMINAL_CAPABILITIES, "")
								.equals(terminalProof.terminalCapabilities())
						|| !record.getBooleanOr(TerminalData.CONTINUITY_LEARNED, false)
						|| !record.getBooleanOr(TerminalData.NETHER_RIFT_OBSERVED, false)
						|| record.getIntOr(TerminalData.ENDING_VERSION, 0) != terminalProof.endingVersion()
						|| !record.getStringOr(TerminalData.ENDING_OUTCOME, "").equals(terminalProof.endingOutcome())
						|| EndingState.outcome(data) != EndingOutcome.SUCCESS
						|| EndingState.get(data).getBooleanOr("active_anomalies", true)
						|| !server.overworld().getBlockState(BlockPos.of(terminalProof.riftPosition()))
								.is(ModBlocks.RULE_FRACTURE_CORE)) {
					throw new AssertionError("Terminal identity, personality, binding, or Fourth Frequency state changed after restart");
				}
				var nether = server.getLevel(Level.NETHER);
				var netherRiftState = BodyConstructionService.netherRiftState(data);
				if (nether == null || !netherRiftState.getBooleanOr("complete", false)
						|| netherRiftState.getLongOr("origin", 0L) != terminalProof.netherRiftOrigin()
						|| !nether.getBlockState(BlockPos.of(terminalProof.netherRiftOrigin()))
								.is(ModBlocks.NETHER_RULE_FRACTURE_CORE)) {
					throw new AssertionError("M6 Nether fracture state or physical core changed after restart");
				}
				var correction = CorrectionState.get(data);
				if (correction.getIntOr("dismantle_count", 0) < terminalProof.correctionDismantles()) {
					throw new AssertionError("Correction organ state changed after restart");
				}
				try {
					if (!correction.contains("rework_entity_pos")) {
						throw new AssertionError("Persistent rework body position was not recorded");
					}
					if (!(server.overworld().getEntityInAnyDimension(UUID.fromString(terminalProof.reworkEntityUuid()))
							instanceof ReworkEntity)) {
						throw new AssertionError("Persistent rework body entity was not restored after restart");
					}
				} catch (IllegalArgumentException exception) {
					throw new AssertionError("Persisted rework body UUID was malformed", exception);
				}
				return data.stationPosition().orElseThrow();
			});
		if (!stationPosition.equals(loadedStation)) {
				throw new AssertionError("Station position changed after restart");
			}
		}

		context.waitForScreen(TitleScreen.class);
	}

	/**
	 * Focused visual suites still clear the mandatory notice, but leave its full localization and
	 * layout audit to the mainline and notice-entry suites. This keeps an unrelated notice-layout
	 * change from preventing the selected renderer suite from reaching its own fixtures.
	 */
	private static void acknowledgeFirstRunNoticeForFocusedSuite(ClientGameTestContext context) {
		context.waitFor(client -> client.screen instanceof FirstRunNoticeScreen, 120);
		context.waitFor(client -> client.screen instanceof FirstRunNoticeScreen notice
				&& notice.acknowledgementAvailableForTesting(), 160);
		context.runOnClient(client -> ((FirstRunNoticeScreen) client.screen).acknowledgeForTesting());
	}

	private static void assertInitialOverworldRenderDistance(ClientGameTestContext context) {
		context.runOnClient(client -> {
			int fixed = DimensionViewDistancePolicy.OVERWORLD_CHUNKS;
			int stored = client.options.renderDistance().get();
			int effective = client.options.getEffectiveRenderDistance();
			if (stored != fixed || effective != fixed) {
				throw new AssertionError("Render distance was not initialized to the locked Overworld value"
						+ " (stored=" + stored + ", effective=" + effective + ")");
			}
			client.options.renderDistance().set(12);
			if (client.options.getEffectiveRenderDistance() != fixed) {
				throw new AssertionError("A runtime option change bypassed the effective three-chunk limit");
			}
			client.options.save();
			if (!client.options.renderDistance().get().equals(fixed)) {
				throw new AssertionError("Saving did not restore the stored three-chunk option");
			}
			client.setScreen(new VideoSettingsScreen(client.screen, client, client.options));
		});
		context.waitForScreen(VideoSettingsScreen.class);
		context.waitTicks(2);
		context.runOnClient(client -> {
			OptionsList optionsList = client.screen.children().stream()
					.filter(OptionsList.class::isInstance)
					.map(OptionsList.class::cast)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Video settings did not expose its option list"));
			AbstractWidget renderDistance = optionsList.findOption(client.options.renderDistance());
			if (renderDistance == null) {
				throw new AssertionError("Video settings did not contain the render-distance control");
			}
			String label = renderDistance.getMessage().getString();
			if (renderDistance.active || !label.equals("渲染距离：3 个区块（已锁定）")) {
				throw new AssertionError("Render-distance control was not visibly locked: active="
						+ renderDistance.active + ", label=" + label);
			}
			double centered = optionsList.scrollAmount() + renderDistance.getY()
					- (optionsList.getY() + optionsList.getHeight() / 2.0D - renderDistance.getHeight() / 2.0D);
			optionsList.setScrollAmount(Math.max(0.0D, Math.min(optionsList.maxScrollAmount(), centered)));
		});
		context.waitTicks(2);
		context.takeScreenshot("render-distance-locked-three-chunks");
		context.runOnClient(client -> client.screen.onClose());
		context.waitForScreen(TitleScreen.class);
	}

	private static void assertLockedRenderDistance(ClientGameTestContext context,
			net.minecraft.resources.ResourceKey<Level> dimension, int expected) {
		context.waitFor(client -> client.level != null && client.level.dimension() == dimension
				&& client.options.renderDistance().get().equals(expected)
				&& client.options.getEffectiveRenderDistance() == expected, 100);
		context.runOnClient(client -> {
			int rejected = expected == 12 ? 2 : 12;
			client.options.renderDistance().set(rejected);
			if (!client.options.renderDistance().get().equals(expected)
					|| client.options.getEffectiveRenderDistance() != expected) {
				throw new AssertionError("Render-distance setter bypassed the dimension lock for "
						+ dimension.identifier() + " (expected=" + expected + ")");
			}
		});
	}

	private static void assertAndAcknowledgeFirstRunNotice(ClientGameTestContext context) {
		context.waitFor(client -> client.screen instanceof FirstRunNoticeScreen, 120);
		context.runOnClient(client -> {
			if (!(client.screen instanceof FirstRunNoticeScreen notice))
				throw new AssertionError("Opening the game to the title page did not open the safety notice");
			if (client.level != null || client.player != null)
				throw new AssertionError("First-run notice waited until a world existed instead of opening on the title page");
			if (notice.openingSoundPlayCountForTesting() != 1 || TerminalClientAudio.lockPlaysForTesting() != 0)
				throw new AssertionError("First-run startup sound did not remain isolated from terminal lock statistics");
			if (notice.shouldCloseOnEsc() || !notice.isPauseScreen())
				throw new AssertionError("First-run notice title-page close contract changed");
			notice.onClose();
			if (client.screen != notice || FirstRunNoticeController.acknowledgedForTesting())
				throw new AssertionError("First-run notice was bypassed through its ordinary close path");
			if (notice.presentationPhaseForTesting() != FirstRunNoticeScreen.PresentationPhase.CALIBRATION
					|| notice.acknowledgementAvailableForTesting())
				throw new AssertionError("First-run notice did not begin with a non-bypassable calibration screen");
		});
		context.takeScreenshot("m1-first-run-band-calibration");
		switchFirstRunNoticeLanguage(context, "zh_cn");
		context.runOnClient(client -> {
			FirstRunNoticeScreen notice = (FirstRunNoticeScreen) client.screen;
			if (notice.openingSoundPlayCountForTesting() != 1)
				throw new AssertionError("First-run notice replayed startup audio when its widgets were rebuilt");
		});
		context.waitFor(client -> client.screen instanceof FirstRunNoticeScreen notice
				&& notice.presentationPhaseForTesting() == FirstRunNoticeScreen.PresentationPhase.NOTICE
				&& notice.acknowledgementAvailableForTesting()
				&& client.getLanguageManager().getSelected().equals("zh_cn")
				&& notice.getTitle().getString().equals("继续前，请阅读"), 160);
		context.takeScreenshot("m1-first-run-safety-notice-zh-cn");
		context.runOnClient(client -> {
			FirstRunNoticeScreen notice = (FirstRunNoticeScreen) client.screen;
			if (!client.getLanguageManager().getSelected().equals("zh_cn"))
				throw new AssertionError("First-run notice resource reload did not select Chinese");
			if (!notice.getTitle().getString().equals("继续前，请阅读"))
				throw new AssertionError("First-run notice title was not localized in Chinese");
			if (!notice.acknowledgementLabelForTesting().getString().equals("我已了解"))
				throw new AssertionError("First-run notice acknowledgement label changed");
			boolean bottomButton = notice.acknowledgementButtonIsAtBottomForTesting();
			boolean layoutFits = notice.dedicatedLayoutFitsForTesting();
			boolean aligned = notice.allElementsAlignedForTesting();
			boolean insideGlass = notice.allTextInsideGlassForTesting();
			boolean punctuation = notice.avoidsOrphanPunctuationForTesting();
			if (!bottomButton || !layoutFits || !aligned || !insideGlass || !punctuation) {
				throw new AssertionError("Localized first-run notice layout changed: bottomButton=" + bottomButton
						+ ", layoutFits=" + layoutFits + ", aligned=" + aligned
						+ ", insideGlass=" + insideGlass + ", punctuation=" + punctuation);
			}
			if (!notice.acknowledgementAvailableForTesting()
					|| notice.openingSoundPlayCountForTesting() != 1
					|| notice.stableSoundPlayCountForTesting() != 1
					|| TerminalClientAudio.lockPlaysForTesting() != 0)
				throw new AssertionError("First-run notice did not appear after one complete band calibration");
			notice.reinitializeForTesting();
			if (!notice.acknowledgementAvailableForTesting()
					|| notice.openingSoundPlayCountForTesting() != 1
					|| notice.stableSoundPlayCountForTesting() != 1)
				throw new AssertionError("First-run notice replayed audio or disabled itself after reinitialization");
		});
		switchFirstRunNoticeLanguage(context, "en_us");
		context.waitFor(client -> client.screen instanceof FirstRunNoticeScreen notice
				&& client.getLanguageManager().getSelected().equals("en_us")
				&& notice.getTitle().getString().equals("READ BEFORE CONTINUING")
				&& notice.acknowledgementLabelForTesting().getString().equals("I Understand"), 160);
		context.runOnClient(client -> {
			FirstRunNoticeScreen notice = (FirstRunNoticeScreen) client.screen;
			if (!notice.dedicatedLayoutFitsForTesting() || !notice.allElementsAlignedForTesting()
					|| !notice.allTextInsideGlassForTesting() || !notice.avoidsOrphanPunctuationForTesting())
				throw new AssertionError("English first-run notice layout does not fit the compact window");
		});
		context.takeScreenshot("m1-first-run-safety-notice-en-us");
		switchFirstRunNoticeLanguage(context, "zh_cn");
		context.waitFor(client -> client.screen instanceof FirstRunNoticeScreen notice
				&& client.getLanguageManager().getSelected().equals("zh_cn")
				&& notice.getTitle().getString().equals("继续前，请阅读"), 160);
		context.runOnClient(client -> ((FirstRunNoticeScreen) client.screen).acknowledgeForTesting());
		context.waitFor(client -> client.screen instanceof FirstRunNoticeScreen notice
				&& notice.presentationPhaseForTesting() == FirstRunNoticeScreen.PresentationPhase.TRANSITION
				&& notice.zoomProgressForTesting() >= 0.30F, 60);
		context.runOnClient(client -> {
			if (FirstRunNoticeController.acknowledgedForTesting())
				throw new AssertionError("First-run acknowledgement persisted before the entry transition finished");
		});
		context.takeScreenshot("m1-first-run-terminal-entry-transition");
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(client -> {
			if (client.level != null || client.player != null)
				throw new AssertionError("Acknowledging the notice did not return to the pre-world title page");
			if (!Files.isRegularFile(FirstRunNoticeController.configPathForTesting()))
				throw new AssertionError("First-run acknowledgement was not persisted to the unified config");
			FirstRunNoticeController.reloadFromDiskForTesting();
			if (!FirstRunNoticeController.acknowledgedForTesting())
				throw new AssertionError("Persisted first-run acknowledgement could not be reloaded");
		});
	}

	private static void switchFirstRunNoticeLanguage(ClientGameTestContext context, String languageCode) {
		java.util.concurrent.atomic.AtomicBoolean reloaded = new java.util.concurrent.atomic.AtomicBoolean();
		context.runOnClient(client -> {
			FirstRunNoticeScreen notice = (FirstRunNoticeScreen) client.screen;
			client.options.languageCode = languageCode;
			client.getLanguageManager().setSelected(languageCode);
			client.reloadResourcePacks().thenRun(() -> reloaded.set(true));
			notice.reinitializeForTesting();
		});
		context.waitFor(client -> reloaded.get(), 240);
		context.waitFor(client -> client.getOverlay() == null, 160);
		context.waitTicks(20);
	}

	private static void openTerminalThroughClientCallback(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.player == null || client.level == null) {
				throw new AssertionError("Client world is not ready");
			}
			InteractionResult result = UseItemCallback.EVENT.invoker()
					.interact(client.player, client.level, InteractionHand.MAIN_HAND);
			if (result != InteractionResult.SUCCESS) {
				throw new AssertionError("Using the held terminal did not send its private open request");
			}
		});
	}

	private static void closeTerminal(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.screen instanceof TerminalScreen terminal) terminal.onClose();
			else client.setScreen(null);
		});
		context.waitTicks(2);
	}

	private static void setTerminalView(ClientGameTestContext context, int mode, int tuning, int cache) {
		context.runOnClient(client -> {
			if (!(client.screen instanceof TerminalScreen terminal)) {
				throw new AssertionError("Terminal screen was not open for control input");
			}
			terminal.selectModeForTesting(mode);
			terminal.setTuningForTesting(tuning);
			if (mode == TerminalControlPolicy.Mode.FILES.ordinal()) terminal.openLogEntryForTesting(cache);
		});
	}

	private static void verifyReceiverAudioLifecycle(ClientGameTestContext context, int target) {
		final int[] baseline = new int[2];
		context.runOnClient(client -> {
			TerminalScreen terminal = (TerminalScreen) client.screen;
			TerminalClientAudio.resetTuningForTesting();
			baseline[0] = TerminalClientAudio.loopStartsForTesting();
			baseline[1] = TerminalClientAudio.lockPlaysForTesting();
			for (int value = Math.max(0, target - 4); value <= target; value++) {
				terminal.setTuningForTesting(value);
			}
			if (TerminalClientAudio.loopStartsForTesting() != baseline[0] + 1
					|| TerminalClientAudio.lockPlaysForTesting() != baseline[1] + 1) {
				throw new AssertionError("Contextual receiver audio mismatch: loops="
						+ (TerminalClientAudio.loopStartsForTesting() - baseline[0]) + ", locks="
						+ (TerminalClientAudio.lockPlaysForTesting() - baseline[1]));
			}
		});
		context.waitTicks(5);
		context.runOnClient(client -> {
			if (!TerminalClientAudio.loopActiveForTesting() || TerminalClientAudio.loopVolumeForTesting() <= 0.0F) {
				throw new AssertionError("Tuning loop did not hold for four ticks before fading");
			}
		});
		context.waitTicks(5);
		context.runOnClient(client -> {
			if (TerminalClientAudio.loopActiveForTesting()) {
				throw new AssertionError("Tuning loop did not fade and stop after input ceased");
			}
		});
	}

	private static void assertSingleOwnedTerminal(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.player == null) {
				throw new AssertionError("Client player was not present");
			}
			int owned = 0;
			for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
				var stack = client.player.getInventory().getItem(slot);
				if (stack.is(ModItems.OLD_TERMINAL)) {
					if (!TerminalData.belongsTo(stack, client.player.getUUID())) {
						throw new AssertionError("Terminal owner data did not match the player");
					}
					owned++;
				}
			}
			if (owned != 1) {
				throw new AssertionError("Expected exactly one personal terminal, got " + owned);
			}
		});
	}

	private static void assertBoundTerminal(ClientGameTestContext context) {
		assertBoundTerminalState(context);
	}

	private static void assertSignatureBoundTerminal(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.player == null) throw new AssertionError("Client player was not present");
			for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
				var stack = client.player.getInventory().getItem(slot);
				if (!stack.is(ModItems.OLD_TERMINAL)) continue;
				var tag = TerminalData.copyTag(stack);
				if (!TerminalData.isBound(stack) || !TerminalData.secondCacheUnlocked(stack))
					throw new AssertionError("Survival signature did not preserve the bound terminal state");
				if (TerminalData.bandStage(stack) != 1
						|| (tag.getIntOr(TerminalData.SIGNATURE_SCENE_MASK, 0) & 0b11) != 0b11)
					throw new AssertionError("Iron milestone did not synchronize the deterministic signature scene");
				return;
			}
			throw new AssertionError("Client had no terminal to verify");
		});
	}

	private static void assertBoundTerminalState(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.player == null) {
				throw new AssertionError("Client player was not present");
			}
			for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
				var stack = client.player.getInventory().getItem(slot);
				if (stack.is(ModItems.OLD_TERMINAL)) {
					if (!TerminalData.isBound(stack)
							|| !TerminalData.secondCacheUnlocked(stack)) {
						throw new AssertionError("Bound terminal state was not synchronized to the client item");
					}
					return;
				}
			}
			throw new AssertionError("Client had no terminal to verify");
		});
	}

	private static void assertArchiveUnlocked(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (client.player == null) {
				throw new AssertionError("Client player was not present");
			}
			for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
				var stack = client.player.getInventory().getItem(slot);
				if (stack.is(ModItems.OLD_TERMINAL)) {
					var tag = TerminalData.copyTag(stack);
					if (!tag.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false)
							|| !tag.getBooleanOr(TerminalData.RIFT_LOCATED, false)
							|| !"TFF-WF-01-A91C".equals(tag.getStringOr(TerminalData.LOCAL_FILE_HASH, ""))) {
						throw new AssertionError("Server-authoritative archive result did not synchronize to the client terminal");
					}
					return;
				}
			}
			throw new AssertionError("Client had no terminal for archive verification");
		});
	}

	private static M4ClientFixture prepareM4Fixture(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.BAND_STAGE, Math.max(2, record.getIntOr(TerminalData.BAND_STAGE, 0)));
			record.putInt(TerminalData.PLOT_STAGE, Math.max(3, record.getIntOr(TerminalData.PLOT_STAGE, 1)));
			record.putBoolean(TerminalData.BOUND, true);
		});
		BlockPos origin = player.blockPosition();
		List<FragmentInvestigationService.Candidate> candidates = List.of(
				m4Candidate(0, FragmentInvestigationService.Group.MINESHAFT, origin.offset(120, -20, 40)),
				m4Candidate(0, FragmentInvestigationService.Group.SHIPWRECK, origin.offset(-180, 0, 90)),
				m4Candidate(0, FragmentInvestigationService.Group.TRAIL_RUINS, origin.offset(75, -8, -210)),
				m4Candidate(1, FragmentInvestigationService.Group.WOODLAND_MANSION, origin.offset(300, 0, 130)),
				m4Candidate(1, FragmentInvestigationService.Group.DESERT_PYRAMID, origin.offset(-260, 0, -150)),
				m4Candidate(1, FragmentInvestigationService.Group.IGLOO, origin.offset(90, 0, 340)),
				m4Candidate(2, FragmentInvestigationService.Group.TRIAL_CHAMBERS, origin.offset(-330, -25, 70)),
				m4Candidate(2, FragmentInvestigationService.Group.PILLAGER_OUTPOST, origin.offset(410, 0, -80)),
				m4Candidate(2, FragmentInvestigationService.Group.OCEAN_MONUMENT, origin.offset(40, 0, -460)),
				m4Candidate(3, FragmentInvestigationService.Group.ANCIENT_CITY, origin.offset(-420, -40, -220)),
				m4Candidate(3, FragmentInvestigationService.Group.OCEAN_RUINS, origin.offset(480, 0, 210)),
				m4Candidate(3, FragmentInvestigationService.Group.RUINED_PORTAL, origin.offset(-110, 0, 520)));
		FragmentInvestigationService.setCandidatesForTesting(data, candidates);
		TerminalSignalService.updatePlayerForTesting(player);
		FragmentInvestigationService.discoverForTesting(player, candidates.getFirst());
		FragmentInvestigationService.setNearbyForTesting(player, candidates.get(3));
		TerminalAnomalyLogService.record(player, "phantom_echo", 0, 1, 20, false);
		TerminalAnomalyLogService.record(player, "light_dropout", 1, 1, 20, false);
		TerminalAnomalyLogService.record(player, "surface_fracture", 2, 1, 20, false);
		TerminalAnomalyLogService.record(player, "watcher_alignment", 3, 1, 20, false);
		return new M4ClientFixture(FragmentInvestigationService.receiverTuning(candidates.get(3)));
	}

	private static void completeM4Fragments(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		BlockPos origin = player.blockPosition();
		FragmentInvestigationService.discoverForTesting(player,
				m4Candidate(2, FragmentInvestigationService.Group.TRIAL_CHAMBERS, origin.offset(-330, -25, 70)));
		FragmentInvestigationService.discoverForTesting(player,
				m4Candidate(3, FragmentInvestigationService.Group.ANCIENT_CITY, origin.offset(-420, -40, -220)));
	}

	private static FragmentInvestigationService.Candidate m4Candidate(int fragment,
			FragmentInvestigationService.Group group, BlockPos position) {
		return new FragmentInvestigationService.Candidate(fragment, group, position, "minecraft:overworld");
	}

	private static void prepareM5Fixture(MinecraftServer server) {
		var level = server.overworld();
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		BlockPos center = data.stationPosition().orElse(player.blockPosition()).offset(0, 0, 64);
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				level.setBlock(center.offset(x, -1, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
				for (int y = 0; y <= 5; y++) {
					level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		BlockPos organ = center;
		BlockPos terminalFacility = center.offset(0, 0, 7);
		CorrectionTargetService.setOrganForTest(server, organ);
		CorrectionTargetService.setTerminalFacilityForTest(server, terminalFacility);
		var correction = CorrectionState.get(data);
		try {
			var existing = level.getEntityInAnyDimension(UUID.fromString(
					correction.getStringOr("rework_entity_uuid", "")));
			if (existing instanceof ReworkEntity) {
				existing.discard();
			}
		} catch (IllegalArgumentException ignored) {
			// No prior body is a normal first-run state.
		}
		ReworkEntity body = CorrectionOrganService.spawnReworkBody(level, organ);
		body.snapTo(organ.getX() + 2.5, organ.getY(), organ.getZ() + 0.5, 90.0F, 0.0F);
		Mob cow = spawnM5Mob(level, EntityType.COW, organ.offset(5, 0, -2));
		Mob villager = spawnM5Mob(level, EntityType.VILLAGER, organ.offset(-9, 0, 0));
		Mob zombie = spawnM5Mob(level, EntityType.ZOMBIE, organ.offset(0, 0, 13));
		TrendSwarmService.applyTrend(cow, organ);
		TrendSwarmService.applyTrend(villager, organ);
		TrendSwarmService.applyTrend(zombie, organ);
		BlockPos observation = organ.offset(-9, 2, -9);
		player.teleportTo(level, observation.getX() + 0.5, observation.getY(), observation.getZ() + 0.5,
				Set.of(), -45.0F, 12.0F, true);
	}

	private static <T extends Mob> T spawnM5Mob(net.minecraft.server.level.ServerLevel level, EntityType<T> type,
			BlockPos position) {
		T entity = type.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			throw new AssertionError("M5 client fixture entity factory returned null for " + type);
		}
		entity.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.0F, 0.0F);
		if (!level.addFreshEntity(entity)) {
			throw new AssertionError("M5 client fixture could not add " + type);
		}
		return entity;
	}

	private static void triggerEmptySegment(MinecraftServer server, EmptySegmentService.EventType type, int duration) {
		if (!EmptySegmentService.trigger(server.getPlayerList().getPlayers().getFirst(), type, duration)) {
			throw new AssertionError("Could not trigger client empty-segment fixture " + type);
		}
	}

	private static void beginM6NetherCrossing(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.RIFT_OBSERVED, true);
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
							| SurvivalMilestone.PREPARED_NETHER.mask());
		});
		var nether = server.getLevel(Level.NETHER);
		if (nether == null || !player.teleportTo(nether, 0.5, 64.0, 0.5,
				Set.of(), 0.0F, 0.0F, true)) {
			throw new AssertionError("M6 client fixture could not cross into the Nether");
		}
	}

	private static void waitForM6NetherRift(TestSingleplayerContext singleplayer,
			ClientGameTestContext context) {
		for (int tick = 0; tick < 160; tick++) {
			boolean complete = singleplayer.getServer().computeOnServer(server ->
					BodyConstructionService.netherRiftState(FrequencyWorldData.get(server))
							.getBooleanOr("complete", false));
			if (complete) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("M6 client fixture Nether fracture did not complete within 160 ticks");
	}

	private static BlockPos observeM6NetherRift(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		var state = BodyConstructionService.netherRiftState(data);
		if (!state.getBooleanOr("complete", false)) {
			throw new AssertionError("M6 client fixture Nether fracture did not complete");
		}
		BlockPos core = BlockPos.of(state.getLongOr("origin", 0L));
		player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(),
				core.getX() - 8.5, core.getY() + 2.0, core.getZ() + 0.5,
				Set.of(), -90.0F, 8.0F, true);
		if (!BodyConstructionService.observeNetherRift(player, core)) {
			throw new AssertionError("M6 client fixture could not observe the physical Nether fracture");
		}
		return core;
	}

	private static void finishM6ReturnCrossing(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		BlockPos gallery = CorrectionState.organPosition(data)
				.or(() -> data.stationPosition()).orElse(player.blockPosition());
		BlockPos landing = gallery.offset(-9, 0, -9);
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
			server.overworld().setBlock(landing.offset(x, -1, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
			for (int y = 0; y <= 3; y++)
				server.overworld().setBlock(landing.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
		}
		player.setHealth(player.getMaxHealth());
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		if (!player.teleportTo(server.overworld(), landing.getX() + 0.5, landing.getY() + 0.1,
				landing.getZ() + 0.5, Set.of(), -45.0F, 12.0F, true)) {
			throw new AssertionError("M6 client fixture could not return to the Overworld");
		}
		player.resetFallDistance();
	}

	private static void prepareStrongholdEstimateFixture(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		BlockPos estimatedStronghold = player.blockPosition().offset(1_600, -24, 800);
		FrequencyWorldData.get(server).updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.EYE_SAMPLE_COUNT, 2);
			record.putLong(TerminalData.STRONGHOLD_POSITION, estimatedStronghold.asLong());
			record.putString(TerminalData.STRONGHOLD_DIMENSION,
					player.level().dimension().identifier().toString());
		});
	}

	private static UUID prepareM7Fixture(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, 0xFF);
			record.putInt(TerminalData.BODY_PROGRESS, 1000);
			record.putInt(TerminalData.BODY_STAGE, 4);
			record.putBoolean(TerminalData.LOCAL_FILE_UNLOCKED, true);
			record.putBoolean(TerminalData.TRUTH_READ, true);
			record.putBoolean(TerminalData.RIFT_OBSERVED, true);
		});
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		PlayerPatternService.sample(player, data);
		PlayerPatternService.sample(player, data);
		BlockPos center = CorrectionState.organPosition(data).orElse(player.blockPosition()).offset(0, 0, 34);
		for (int x = -12; x <= 12; x++) {
			for (int z = -12; z <= 12; z++) {
				server.overworld().setBlock(center.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
				for (int y = 0; y <= 5; y++) server.overworld().setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
			}
		}
		MisreadBodyEntity body = FinalConfrontationService.startAltarForTesting(player, server.overworld(), center, true);
		if (body == null) throw new AssertionError("M7 altar fixture could not start the authoritative final encounter");
		body.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 180.0F, 0.0F);
		body.setNoAi(true);
		player.teleportTo(server.overworld(), center.getX() + 0.5, center.getY() + 1.5,
				center.getZ() - 5.5, Set.of(), 0.0F, 8.0F, true);
		player.setHealth(player.getMaxHealth());
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		player.setItemInHand(InteractionHand.MAIN_HAND,
				TerminalData.stackFromRecord(data.terminalRecord(player.getUUID()).orElseThrow()));
		player.displayClientMessage(net.minecraft.network.chat.Component.empty(), true);
		return body.getUUID();
	}

	private static void equipM7TerminationSpike(MinecraftServer server) {
		server.getPlayerList().getPlayers().getFirst().setItemInHand(
				InteractionHand.MAIN_HAND, new ItemStack(ModItems.TERMINATION_SPIKE));
	}

	private static void discoverAllTerminalFiles(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		long gameTime = server.overworld().getGameTime();
		long dayTime = server.overworld().getDayTime();
		data.updateTerminalRecord(player.getUUID(), record -> {
			for (NarrativeFileCatalog.Definition definition : NarrativeFileCatalog.definitions()) {
				TerminalFileState.discover(record, definition.id(), gameTime, dayTime, true);
			}
		});
		TerminalRuntimeService.refresh(player);
	}

	private static void moveIntoTerminationRange(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		var ending = EndingState.get(FrequencyWorldData.get(server));
		try {
			var entity = server.overworld().getEntityInAnyDimension(UUID.fromString(ending.getStringOr("body_uuid", "")));
			if (!(entity instanceof MisreadBodyEntity body)) throw new AssertionError("M7 body disappeared before termination");
			player.teleportTo(server.overworld(), body.getX(), body.getY(), body.getZ() - 2.0,
					Set.of(), 0.0F, 0.0F, true);
		} catch (IllegalArgumentException exception) {
			throw new AssertionError("M7 final body UUID was malformed", exception);
		}
	}

	private static void assertAndProjectM7Success(MinecraftServer server) {
		var player = server.getPlayerList().getPlayers().getFirst();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		var ending = EndingState.get(data);
		net.minecraft.world.entity.Entity entity;
		try {
			entity = server.overworld().getEntityInAnyDimension(UUID.fromString(ending.getStringOr("body_uuid", "")));
		} catch (IllegalArgumentException exception) {
			throw new AssertionError("M7 final body UUID was malformed after spike interaction", exception);
		}
		if (!(entity instanceof MisreadBodyEntity body) || !body.isAlive()
				|| EndingState.outcome(data) != EndingOutcome.ACTIVE) {
			throw new AssertionError("Termination spike should disrupt the boss without replacing normal combat");
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		body.setHealth(1.0F);
		body.hurtServer(server.overworld(), player.damageSources().playerAttack(player), 20.0F);
		if (EndingState.outcome(data) != EndingOutcome.SUCCESS
				|| EndingState.get(data).getBooleanOr("active_anomalies", true)) {
			throw new AssertionError("Normal combat did not permanently defeat the Fourth Frequency");
		}
		player.setItemInHand(InteractionHand.MAIN_HAND,
				TerminalData.stackFromRecord(data.terminalRecord(player.getUUID()).orElseThrow()));
		TerminalLifecycleService.ensureCarried(player, false);
	}

	private static void loadPersistentReworkChunks(MinecraftServer server) {
		var correction = CorrectionState.get(FrequencyWorldData.get(server));
		if (!correction.contains("rework_entity_pos")) {
			throw new AssertionError("Persistent rework body position was not recorded");
		}
		BlockPos reworkPosition = BlockPos.of(correction.getLongOr("rework_entity_pos", 0L));
		var player = server.getPlayerList().getPlayers().getFirst();
		player.teleportTo(server.overworld(), reworkPosition.getX() + 4.5,
				reworkPosition.getY() + 2.0, reworkPosition.getZ() + 4.5,
				Set.of(), 0.0F, 0.0F, true);
		for (int chunkX = (reworkPosition.getX() >> 4) - 1;
				chunkX <= (reworkPosition.getX() >> 4) + 1; chunkX++) {
			for (int chunkZ = (reworkPosition.getZ() >> 4) - 1;
					chunkZ <= (reworkPosition.getZ() >> 4) + 1; chunkZ++) {
				server.overworld().getChunk(chunkX, chunkZ);
			}
		}
	}

	private static void runAlphaRelaunch(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.waitFor(client -> AlphaLoadSessionController.persistentStartupAppliedForTesting()
				&& AlphaLoadSessionController.resourceReloadFinishedForTesting(), 1_000);
		assertAlphaBasePacksHidden(context);
		int[] legacyScreensBeforeWorld = {-1};
		context.runOnClient(client -> {
			if (!ConfigManager.loadClientState().alphaDowngradeComplete()
					|| !AlphaLoadSessionController.corruptionEverPlayedForTesting()) {
				throw new AssertionError("Relaunch did not restore the persisted Alpha downgrade marker");
			}
			if (AlphaLoadSessionController.persistentAlphaLoadingOverlaysForTesting() < 1
					|| AlphaLoadSessionController.persistentAlphaLoadingFirstFramesForTesting() < 1) {
				throw new AssertionError(
						"Relaunch did not use the persistent Alpha loading style from its first rendered frame");
			}
			if (!AlphaLoadSessionController.persistentInitialPackSelectionPreparedForTesting()
					|| AlphaLoadSessionController.suppressedResourceReloadAnimationsForTesting() != 0) {
				throw new AssertionError(
						"Relaunch did not prepare Alpha packs before the initial reload and risked a vanilla title frame");
			}
			if (AlphaLoadSessionController.activeForTesting()
					|| AlphaLoadSessionController.corruptionPlayCountForTesting() != 0) {
				throw new AssertionError("Relaunch replayed corruption before entering a world");
			}
			if (AlphaLoadSessionController.resourceReloadFailedForTesting()
					|| !AlphaLoadSessionController.javaIconAppliedForTesting()) {
				throw new AssertionError("Relaunch did not restore its hidden resource stack and Java icon");
			}
			if (!"Minecraft Alpha 1.0.0".equals(
					AlphaLoadSessionController.appliedWindowTitleForTesting())
					|| !"Minecraft 1.0.0".equals(AlphaLoadSessionController
					.menuVersionText("Minecraft 1.21.11"))) {
				throw new AssertionError("Relaunch title screen did not retain the Alpha 1.0.0 identity");
			}
			List<String> selected = client.getResourcePackRepository().getSelectedPacks().stream()
					.map(net.minecraft.server.packs.repository.Pack::getId).toList();
			int programmer = selected.indexOf(AlphaResourcePackPlan.PROGRAMMER_ART_PACK_ID);
			int base = selected.indexOf(AlphaResourcePackPlan.GOLDEN_DAYS_BASE_PACK_ID);
			int alpha = selected.indexOf(AlphaResourcePackPlan.GOLDEN_DAYS_ALPHA_PACK_ID);
			if (!(programmer >= 0 && programmer < base && base < alpha)) {
				throw new AssertionError("Relaunch resource priority is not Programmer Art < Base < Alpha: "
						+ selected);
			}
			String grassSource = client.getResourceManager().getResource(Identifier.fromNamespaceAndPath(
					"minecraft", "textures/block/grass_block_top.png")).orElseThrow().sourcePackId();
			String stoneSource = client.getResourceManager().getResource(Identifier.fromNamespaceAndPath(
					"minecraft", "textures/block/stone.png")).orElseThrow().sourcePackId();
			if (!AlphaResourcePackPlan.GOLDEN_DAYS_ALPHA_PACK_ID.equals(grassSource)
					|| !AlphaResourcePackPlan.GOLDEN_DAYS_BASE_PACK_ID.equals(stoneSource)) {
				throw new AssertionError("Relaunch resources did not resolve through Alpha then Base: grass="
						+ grassSource + ", stone=" + stoneSource);
			}
			legacyScreensBeforeWorld[0] = AlphaLoadSessionController
					.legacyLoadingScreensRenderedForTesting();
		});
		context.waitTicks(45);
		context.takeScreenshot("alpha-relaunch-title");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(client -> {
				if (!AlphaLoadSessionController.activeForTesting()
						|| AlphaLoadSessionController.corruptionPlayCountForTesting() != 0
						|| AlphaLoadSessionController.legacyLoadingScreensRenderedForTesting()
						<= legacyScreensBeforeWorld[0]) {
					throw new AssertionError(
							"First world after relaunch replayed corruption instead of normal legacy loading");
				}
			});
		}

		context.waitForScreen(TitleScreen.class);
		context.runOnClient(client -> {
			client.updateTitle();
			if (!"Minecraft Alpha 1.0.0".equals(
					AlphaLoadSessionController.appliedWindowTitleForTesting())
					|| AlphaLoadSessionController.corruptionPlayCountForTesting() != 0) {
				throw new AssertionError("Leaving the relaunch world lost the persistent Alpha identity");
			}
		});
	}

	private static void assertAlphaBasePacksHidden(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var repository = client.getResourcePackRepository();
			repository.reload();
			for (String packId : AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH) {
				if (!repository.isAvailable(packId)) {
					throw new AssertionError("Required hidden Alpha base pack is unavailable: " + packId);
				}
			}
			PackSelectionModel model = new PackSelectionModel(entry -> { },
					pack -> Identifier.fromNamespaceAndPath("minecraft", "textures/gui/resource_pack.png"),
					repository, ignored -> { });
			List<String> visible = Stream.concat(model.getSelected(), model.getUnselected())
					.map(PackSelectionModel.Entry::getId).toList();
			for (String packId : AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH) {
				if (visible.contains(packId)) {
					throw new AssertionError("Internal Alpha base leaked into resource pack list: " + packId);
				}
			}
		});
	}

	private static void assertAlphaSessionLoaded(ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (!AlphaLoadSessionController.activeForTesting()
					|| !AlphaLoadSessionController.resourceReloadFinishedForTesting()
					|| AlphaLoadSessionController.resourceReloadFailedForTesting()) {
				throw new AssertionError("Singleplayer Alpha resource session did not finish cleanly");
			}
			if (!"SINGLEPLAYER".equals(AlphaLoadSessionController.sessionKindForTesting())) {
				throw new AssertionError("Integrated world was not classified as singleplayer");
			}
			if (!AlphaLoadSessionController.corruptionEverPlayedForTesting()
					|| AlphaLoadSessionController.corruptionPlayCountForTesting() != 1
					|| !AlphaLoadSessionController.javaIconAppliedForTesting()) {
				throw new AssertionError("Initial world entry did not claim one client-lifetime corruption and Java icon");
			}
			if (!ConfigManager.loadClientState().alphaDowngradeComplete()
					|| !Files.isRegularFile(ConfigManager.configPathForTesting())) {
				throw new AssertionError("Initial corruption did not atomically persist its Alpha downgrade marker");
			}
			if (AlphaLoadSessionController.javaIconAppliedAtScreenTickForTesting()
					< AlphaLoadTimeline.GLITCH_START_TICK
					|| AlphaLoadSessionController.javaIconAppliedAtScreenTickForTesting()
					>= AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK) {
				throw new AssertionError("Java icon was not switched during the corruption phase: tick="
						+ AlphaLoadSessionController.javaIconAppliedAtScreenTickForTesting());
			}
			if (AlphaLoadSessionController.versionStageForTesting()
					!= AlphaLoadTimeline.finalVersionStage()
					|| !AlphaLoadSessionController.appliedWindowTitleForTesting().contains("Alpha 1.0.0")) {
				throw new AssertionError("Window title did not finish its visible downgrade at Alpha 1.0.0: "
						+ AlphaLoadSessionController.appliedWindowTitleForTesting());
			}
			if (AlphaLoadSessionController.lastLoadingScreenTicksForTesting()
					< AlphaLoadTimeline.MIN_LOADING_SCREEN_TICKS
					|| AlphaLoadSessionController.lastFailureCopiesForTesting()
					!= AlphaLoadTimeline.MAX_FAILURE_COPIES
					|| !AlphaLoadSessionController.lastViewportFloodedForTesting()) {
				throw new AssertionError("Real loading-terrain screen skipped the bounded failure cascade");
			}
			if (AlphaLoadSessionController.resourceReloadRequestedForTesting()
					&& AlphaLoadSessionController.suppressedResourceReloadAnimationsForTesting() < 1) {
				throw new AssertionError("Alpha resource reload exposed its LoadingOverlay animation");
			}
			List<String> requested = AlphaLoadSessionController.activePackOrderForTesting();
			int suffix = requested.size() - AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH.size();
			if (suffix < 0 || !requested.subList(suffix, requested.size())
					.equals(AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH)) {
				throw new AssertionError("Requested Alpha pack priority was not Programmer Art < Base < Alpha: "
						+ requested);
			}
			List<String> selected = client.getResourcePackRepository().getSelectedPacks().stream()
					.map(net.minecraft.server.packs.repository.Pack::getId).toList();
			int programmer = selected.indexOf(AlphaResourcePackPlan.PROGRAMMER_ART_PACK_ID);
			int base = selected.indexOf(AlphaResourcePackPlan.GOLDEN_DAYS_BASE_PACK_ID);
			int alpha = selected.indexOf(AlphaResourcePackPlan.GOLDEN_DAYS_ALPHA_PACK_ID);
			if (!(programmer >= 0 && programmer < base && base < alpha)) {
				throw new AssertionError("Applied resource stack order is wrong: " + selected);
			}
			String grassSource = client.getResourceManager().getResource(Identifier.fromNamespaceAndPath(
					"minecraft", "textures/block/grass_block_top.png")).orElseThrow().sourcePackId();
			String stoneSource = client.getResourceManager().getResource(Identifier.fromNamespaceAndPath(
					"minecraft", "textures/block/stone.png")).orElseThrow().sourcePackId();
			if (!AlphaResourcePackPlan.GOLDEN_DAYS_ALPHA_PACK_ID.equals(grassSource)
					|| !AlphaResourcePackPlan.GOLDEN_DAYS_BASE_PACK_ID.equals(stoneSource)) {
				throw new AssertionError("Live resources did not resolve through Alpha then Base: grass="
						+ grassSource + ", stone=" + stoneSource);
			}
		});
	}

	private static void assertPlayerInsideStation(ClientGameTestContext context, BlockPos station) {
		context.runOnClient(client -> {
			if (client.player == null
					|| Math.abs(client.player.getX() - (station.getX() + 0.5)) > 1.0
					|| Math.abs(client.player.getZ() - (station.getZ() + 0.5)) > 1.0) {
				throw new AssertionError("First join did not place the player inside Relay Station Zero");
			}
		});
	}

	private record TerminalPersistenceProof(
			String worldId,
			String terminalId,
			String personality,
			int mined,
			int placed,
			int crafted,
			String acceptedAdvice,
			String facilityEvidence,
			String localFileHash,
			long riftPosition,
			int emptySegmentCount,
			int correctionDismantles,
			String reworkEntityUuid,
			int portalTransitions,
			int bodyProgress,
			String terminalCapabilities,
			long netherRiftOrigin,
			int endingVersion,
			String endingOutcome) {
	}

	private record M4ClientFixture(int tuning) {
	}
}
