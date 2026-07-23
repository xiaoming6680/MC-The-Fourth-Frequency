package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.persistence.PersistenceSchema;
import com.xm.thefourthfrequency.world.ResourceGuidanceService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;

public final class M0GameTests implements CustomTestMethodInvoker {
	@GameTest
	public void commonBootstrapUsesVersionedPersistence(GameTestHelper helper) {
		if (PersistenceSchema.CURRENT_VERSION < 1) {
			throw new IllegalStateException("Persistence schema version must be positive");
		}
		helper.assertBlockPresent(Blocks.AIR, 0, 0, 0);
		helper.succeed();
	}

	@GameTest
	public void narrativeAudioIsRegisteredAndBounded(GameTestHelper helper) {
		var sounds = java.util.List.of(ModSounds.EMPTY_VIEWPOINT, ModSounds.EMPTY_BASE,
				ModSounds.EMPTY_EXPERIENCE, ModSounds.FOURTH_BAND, ModSounds.REWORK_JOINT,
				ModSounds.ANOMALY_ECHO, ModSounds.WORLD_INTERFACE_SUMMON);
		if (sounds.stream().map(BuiltInRegistries.SOUND_EVENT::getKey).distinct().count() != 7
				|| sounds.stream().map(BuiltInRegistries.SOUND_EVENT::getKey)
				.anyMatch(id -> !id.getNamespace().equals("thefourthfrequency"))) {
			throw new AssertionError("M9 narrative sound events were not uniquely registered in the mod namespace");
		}
		if (RuntimeServices.config().meta().peakVolume() < 0.0D
				|| RuntimeServices.config().meta().peakVolume() > 1.0D) {
			throw new AssertionError("M9 peak sound volume escaped its safe range");
		}
		if (ResourceGuidanceService.maximumBlockChecksPerServerTick() != 4096) {
			throw new AssertionError("M9 resource scan lost its global four-player tick budget");
		}
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		helper.setBlock(0, 0, 0, Blocks.AIR);
		method.invoke(this, helper);
	}
}
