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
	/**
	 * Holds no state. It exists so the exit can be handed to vanilla's own end-portal renderer, which
	 * is what makes the block look like the End's exit rather than like a cube with a picture on it.
	 */
	public static final BlockEntityType<WorldInterfaceExitPortalBlockEntity> EXIT_PORTAL = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "world_interface_exit_portal"),
			FabricBlockEntityTypeBuilder.create(WorldInterfaceExitPortalBlockEntity::new,
					ModBlocks.WORLD_INTERFACE_EXIT_PORTAL).build());

	private WorldInterfaceBlockEntities() {
	}

	public static void initialize() {
		TheFourthFrequency.LOGGER.info("Registered the World Interface resonance-core and exit-portal block entities");
	}
}
