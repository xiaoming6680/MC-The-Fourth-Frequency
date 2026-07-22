package com.xm.thefourthfrequency.content;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class WorldInterfaceBlockEntities {
	public static final BlockEntityType<ResonanceCoreBlockEntity> RESONANCE_CORE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "resonance_core"),
			FabricBlockEntityTypeBuilder.create(ResonanceCoreBlockEntity::new, ModBlocks.RESONANCE_CORE).build());

	private WorldInterfaceBlockEntities() {
	}

	public static void initialize() {
		TheFourthFrequency.LOGGER.info("Registered the World Interface resonance-core block entity");
	}
}
