package com.xm.thefourthfrequency.content;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

/**
 * The encounter owns three blocks and no decoration.
 *
 * <p>It used to own two more: a cage ringing every stability anchor and a core for each of the
 * twenty warp gates. The gate structures stopped being built long before this, and the cage was a
 * bright band of custom texture wrapped around the one thing in the arena a player is supposed to
 * look at. Both are gone rather than retextured, and the blocks that remain borrow vanilla
 * surfaces instead of shipping their own.</p>
 */
public final class ModBlocks {
	public static final ResonanceCoreBlock RESONANCE_CORE = registerCustom("resonance_core",
			BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN)
					.strength(Block.INDESTRUCTIBLE, 3_600_000.0F).noLootTable().lightLevel(state -> 12),
			ResonanceCoreBlock::new);
	public static final WorldInterfaceExitPortalBlock WORLD_INTERFACE_EXIT_PORTAL = registerCustom(
			"world_interface_exit_portal", BlockBehaviour.Properties.ofFullCopy(Blocks.END_PORTAL)
					.strength(Block.INDESTRUCTIBLE, 3_600_000.0F).noLootTable().lightLevel(state -> 15),
			WorldInterfaceExitPortalBlock::new);
	/** Client-side visual proxy for the missing-texture anomaly. Never placed in the real world. */
	public static final Block MISSING_TEXTURE_PROXY = register("missing_texture_proxy", Blocks.BLACK_CONCRETE, -1.0F);

	private ModBlocks() {
	}

	public static void initialize() {
		TheFourthFrequency.LOGGER.info("Registered World Interface and anomaly proxy blocks");
	}

	private static Block register(String path, Block copy, float strength) {
		Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = new Block(BlockBehaviour.Properties.ofFullCopy(copy).strength(strength).setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static <T extends Block> T registerCustom(String path, BlockBehaviour.Properties properties,
			Function<BlockBehaviour.Properties, T> factory) {
		Identifier id = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		T block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}
}
