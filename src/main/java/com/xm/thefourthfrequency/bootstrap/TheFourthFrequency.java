package com.xm.thefourthfrequency.bootstrap;

import com.xm.thefourthfrequency.config.ConfigManager;
import com.xm.thefourthfrequency.config.ModConfig;
import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.correction.CorrectionOrganService;
import com.xm.thefourthfrequency.correction.EmptySegmentService;
import com.xm.thefourthfrequency.body.BodyConstructionService;
import com.xm.thefourthfrequency.ending.FinalConfrontationService;
import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import com.xm.thefourthfrequency.world.PlayerPatternService;
import com.xm.thefourthfrequency.world.ZeroStationService;
import com.xm.thefourthfrequency.world.TerminalActivityTracker;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import com.xm.thefourthfrequency.world.ResourceGuidanceService;
import com.xm.thefourthfrequency.world.TerminalCommands;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import com.xm.thefourthfrequency.world.FragmentInvestigationService;
import com.xm.thefourthfrequency.world.WatcherService;
import com.xm.thefourthfrequency.world.WorldDecayService;
import com.xm.thefourthfrequency.facility.FacilityService;
import com.xm.thefourthfrequency.networking.M4Networking;
import com.xm.thefourthfrequency.networking.M5Networking;
import com.xm.thefourthfrequency.networking.M6Networking;
import com.xm.thefourthfrequency.networking.M8Networking;
import com.xm.thefourthfrequency.networking.TerminalNetworking;
import com.xm.thefourthfrequency.networking.DebugNetworking;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.terminal.AmbientAnomalyService;
import com.xm.thefourthfrequency.terminal.AnomalyRuntimeService;
import com.xm.thefourthfrequency.terminal.AnomalyServerEffects;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TheFourthFrequency implements ModInitializer {
	public static final String MOD_ID = "thefourthfrequency";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfig config = ConfigManager.load();
		RuntimeServices.initialize(config);
		ModSounds.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModEntities.initialize();
		M4Networking.initialize();
		M5Networking.initialize();
		M6Networking.initialize();
		M8Networking.initialize();
		TerminalNetworking.initialize();
		DebugNetworking.initialize();
		ZeroStationService.initialize();
		TerminalLifecycleService.initialize();
		FragmentInvestigationService.initialize();
		TerminalRuntimeService.initialize();
		TerminalToolService.initialize();
		TerminalSignalService.initialize();
		StoryProgressService.initialize();
		SurvivalProgressService.initialize();
		WatcherService.initialize();
		WorldDecayService.initialize();
		AnomalyRuntimeService.initialize();
		AnomalyServerEffects.initialize();
		AmbientAnomalyService.initialize();
		TerminalActivityTracker.initialize();
		ResourceGuidanceService.initialize();
		FacilityService.initialize();
		CorrectionOrganService.initialize();
		EmptySegmentService.initialize();
		BodyConstructionService.initialize();
		PlayerPatternService.initialize();
		FinalConfrontationService.initialize();
		EndBossEncounterService.initialize();
		TerminalCommands.initialize();
		LOGGER.info("The Fourth Frequency common bootstrap is ready (schema {}, accelerated={})",
				RuntimeServices.PERSISTENCE_SCHEMA_VERSION, config.pacing().developerAcceleration());
	}
}
