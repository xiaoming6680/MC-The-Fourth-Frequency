package com.xm.thefourthfrequency.data;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public final class TheFourthFrequencyDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		fabricDataGenerator.createPack();
		TheFourthFrequency.LOGGER.info("The Fourth Frequency data generation pack is registered");
	}
}

