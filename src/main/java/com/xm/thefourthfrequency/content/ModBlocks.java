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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ModBlocks {
	private static final Map<Block, String> EVIDENCE_MARKERS = new IdentityHashMap<>();

	public static final Block SHELTER_BEARING_MARKER = registerMarker("shelter_bearing_marker", "shelter");
	public static final Block MISSING_BAY_MARKER = registerMarker("missing_bay_marker", "warehouse");
	public static final Block DEPTH_ECHO_MARKER = registerMarker("depth_echo_marker", "mine");
	public static final Block OBSERVATION_VANE_MARKER = registerMarker("observation_vane_marker", "observation");
	public static final Block ARCHIVE_LOCK = register("archive_lock", Blocks.COPPER_BLOCK, 4.0F);
	public static final Block RULE_FRACTURE_CORE = register("rule_fracture_core", Blocks.OBSIDIAN, 18.0F);
	public static final Block NASCENT_BODY_ORGAN = register("nascent_body_organ", Blocks.SCULK_CATALYST, 8.0F);
	public static final Block REWORK_SCAR = register("rework_scar", Blocks.CRYING_OBSIDIAN, 12.0F);
	public static final Block REWORK_BRACE = register("rework_brace", Blocks.DEEPSLATE_TILES, 6.0F);
	public static final Block NETHER_RULE_FRACTURE_CORE = register("nether_rule_fracture_core", Blocks.CRYING_OBSIDIAN, 18.0F);
	public static final Block ALTAR_ANCHOR = register("altar_anchor", Blocks.OBSIDIAN, 5.0F);
	public static final ResonanceCoreBlock RESONANCE_CORE = registerCustom("resonance_core",
			BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN)
					.strength(Block.INDESTRUCTIBLE, 3_600_000.0F).noLootTable().lightLevel(state -> 12),
			ResonanceCoreBlock::new);
	public static final StabilityAnchorCageBlock STABILITY_ANCHOR_CAGE = registerCustom("stability_anchor_cage",
			BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS)
					.strength(Block.INDESTRUCTIBLE, 3_600_000.0F).noLootTable().lightLevel(state -> 7),
			StabilityAnchorCageBlock::new);
	public static final WarpGateCoreBlock WARP_GATE_CORE = registerCustom("warp_gate_core",
			BlockBehaviour.Properties.ofFullCopy(Blocks.END_GATEWAY)
					.strength(Block.INDESTRUCTIBLE, 3_600_000.0F).noLootTable(),
			WarpGateCoreBlock::new);
	public static final WorldInterfaceExitPortalBlock WORLD_INTERFACE_EXIT_PORTAL = registerCustom(
			"world_interface_exit_portal", BlockBehaviour.Properties.ofFullCopy(Blocks.END_PORTAL)
					.strength(Block.INDESTRUCTIBLE, 3_600_000.0F).noLootTable().lightLevel(state -> 15),
			WorldInterfaceExitPortalBlock::new);
	/** Client-side visual proxy for the missing-texture anomaly. Never placed in the real world. */
	public static final Block MISSING_TEXTURE_PROXY = register("missing_texture_proxy", Blocks.BLACK_CONCRETE, -1.0F);

	private ModBlocks() {
	}

	public static void initialize() {
		TheFourthFrequency.LOGGER.info("Registered facility evidence, correction targets, World Interface altar blocks, and anomaly proxy blocks");
	}

	public static Optional<String> evidenceFor(Block block) {
		return Optional.ofNullable(EVIDENCE_MARKERS.get(block));
	}

	private static Block registerMarker(String path, String evidence) {
		Block block = register(path, Blocks.COPPER_BLOCK, 3.0F);
		EVIDENCE_MARKERS.put(block, evidence);
		return block;
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
