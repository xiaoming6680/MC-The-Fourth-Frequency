package com.xm.thefourthfrequency.facility;

import com.xm.thefourthfrequency.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class FacilityLayout {
	private static final int MAX_PLACEMENTS = 1_200;
	private static final Map<ResourceManager, Map<String, TemplateData>> CACHE = new WeakHashMap<>();

	private FacilityLayout() {
	}

	public static List<Placement> create(MinecraftServer server, FacilityDefinition definition, BlockPos origin,
			int clueDigit, int variant) {
		TemplateData template = template(server, definition.template(variant));
		List<Placement> plan = new ArrayList<>(template.blocks().size() + 24);
		for (TemplateBlock block : template.blocks()) {
			plan.add(new Placement(origin.offset(
					block.position().getX() - template.anchorX(),
					block.position().getY(),
					block.position().getZ() - template.anchorZ()), block.state()));
		}
		addClueProjection(plan, definition, origin, clueDigit);
		plan.add(new Placement(markerPosition(definition, origin, clueDigit),
				block(definition.markerBlock()).defaultBlockState()));
		if (plan.size() >= MAX_PLACEMENTS) {
			throw new IllegalStateException("Facility plan exceeds bounded size: " + definition.id());
		}
		return List.copyOf(plan);
	}

	public static BlockPos markerPosition(FacilityDefinition definition, BlockPos origin, int clueDigit) {
		return switch (definition.category()) {
			case "shelter" -> sideOffset(origin, clueDigit, definition.width() / 2 - 1, 1);
			case "observation", "mine_station", "warehouse", "transport" -> origin.offset(0,
					definition.category().equals("observation") ? 2 : 1, 0);
			default -> throw new IllegalStateException("Unknown facility category " + definition.category());
		};
	}

	public static int stableVariant(String worldId, FacilityDefinition definition) {
		return Math.floorMod((worldId + ":" + definition.id()).hashCode(), definition.templates().size());
	}

	private static void addClueProjection(List<Placement> plan, FacilityDefinition definition, BlockPos origin,
			int clueDigit) {
		switch (definition.category()) {
			case "shelter" -> {
				int distance = definition.width() / 2;
				plan.add(new Placement(sideOffset(origin, clueDigit, distance, 1), Blocks.AIR.defaultBlockState()));
				plan.add(new Placement(sideOffset(origin, clueDigit, distance, 2), Blocks.AIR.defaultBlockState()));
			}
			case "observation" -> {
				BlockState accent = block(definition.accentBlock()).defaultBlockState();
				for (int distance = 1; distance <= 3; distance++) {
					plan.add(new Placement(sideOffset(origin, clueDigit, distance, 1), accent));
				}
			}
			case "mine_station" -> {
				BlockState accent = block(definition.accentBlock()).defaultBlockState();
				for (int index = 0; index <= clueDigit; index++) {
					plan.add(new Placement(origin.offset(-2 + index, 1, -2), accent));
				}
			}
			case "warehouse" -> {
				int halfX = definition.width() / 2;
				int halfZ = definition.length() / 2;
				for (int y = 1; y < definition.height(); y++) {
					if (clueDigit == 0 || clueDigit == 2) {
						int z = clueDigit == 0 ? -halfZ : halfZ;
						for (int x = -1; x <= 1; x++)
							plan.add(new Placement(origin.offset(x, y, z), Blocks.AIR.defaultBlockState()));
					} else {
						int x = clueDigit == 1 ? halfX : -halfX;
						for (int z = -1; z <= 1; z++)
							plan.add(new Placement(origin.offset(x, y, z), Blocks.AIR.defaultBlockState()));
					}
				}
			}
			case "transport" -> {
				// The archive lock itself is the only clue projection for this facility.
			}
			default -> throw new IllegalStateException("Unknown facility category " + definition.category());
		}
	}

	private static TemplateData template(MinecraftServer server, String name) {
		ResourceManager resources = server.getResourceManager();
		synchronized (CACHE) {
			Map<String, TemplateData> templates = CACHE.computeIfAbsent(resources, ignored -> new HashMap<>());
			return templates.computeIfAbsent(name, ignored -> loadTemplate(server, resources, name));
		}
	}

	private static TemplateData loadTemplate(MinecraftServer server, ResourceManager resources, String name) {
		Identifier resourceId = Identifier.parse("thefourthfrequency:structure/" + name + ".nbt");
		try (InputStream stream = resources.getResourceOrThrow(resourceId).open()) {
			CompoundTag root = NbtIo.readCompressed(stream, NbtAccounter.create(2L * 1024L * 1024L));
			ListTag size = root.getListOrEmpty("size");
			if (size.size() != 3) throw new IllegalStateException("Invalid structure size in " + resourceId);
			int sizeX = size.getIntOr(0, 0);
			int sizeY = size.getIntOr(1, 0);
			int sizeZ = size.getIntOr(2, 0);
			if (sizeX < 1 || sizeX > 31 || sizeY < 1 || sizeY > 16 || sizeZ < 1 || sizeZ > 31) {
				throw new IllegalStateException("Unsafe structure dimensions in " + resourceId);
			}

			ListTag paletteTag = root.getListOrEmpty("palette");
			if (paletteTag.isEmpty() || paletteTag.size() > 64) {
				throw new IllegalStateException("Unsafe structure palette in " + resourceId);
			}
			List<BlockState> palette = new ArrayList<>(paletteTag.size());
			var blockLookup = server.registryAccess().lookupOrThrow(Registries.BLOCK);
			for (int index = 0; index < paletteTag.size(); index++) {
				palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompoundOrEmpty(index)));
			}

			ListTag blocks = root.getListOrEmpty("blocks");
			if (blocks.isEmpty() || blocks.size() >= MAX_PLACEMENTS) {
				throw new IllegalStateException("Unsafe structure placement count in " + resourceId);
			}
			List<TemplateBlock> decoded = new ArrayList<>(blocks.size());
			for (int index = 0; index < blocks.size(); index++) {
				CompoundTag entry = blocks.getCompoundOrEmpty(index);
				ListTag position = entry.getListOrEmpty("pos");
				int stateIndex = entry.getIntOr("state", -1);
				if (position.size() != 3 || stateIndex < 0 || stateIndex >= palette.size()) {
					throw new IllegalStateException("Invalid structure block in " + resourceId);
				}
				BlockPos relative = new BlockPos(position.getIntOr(0, -1), position.getIntOr(1, -1),
						position.getIntOr(2, -1));
				if (relative.getX() < 0 || relative.getX() >= sizeX || relative.getY() < 0 || relative.getY() >= sizeY
						|| relative.getZ() < 0 || relative.getZ() >= sizeZ) {
					throw new IllegalStateException("Structure block outside declared bounds in " + resourceId);
				}
				decoded.add(new TemplateBlock(relative, palette.get(stateIndex)));
			}
			return new TemplateData(sizeX / 2, sizeZ / 2, List.copyOf(decoded));
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load structure template " + resourceId, exception);
		}
	}

	private static BlockPos sideOffset(BlockPos origin, int side, int distance, int y) {
		return switch (Math.floorMod(side, 4)) {
			case 0 -> origin.offset(0, y, -distance);
			case 1 -> origin.offset(distance, y, 0);
			case 2 -> origin.offset(0, y, distance);
			default -> origin.offset(-distance, y, 0);
		};
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
	}

	private record TemplateData(int anchorX, int anchorZ, List<TemplateBlock> blocks) {
	}

	private record TemplateBlock(BlockPos position, BlockState state) {
	}

	public record Placement(BlockPos position, BlockState state) {
	}
}
