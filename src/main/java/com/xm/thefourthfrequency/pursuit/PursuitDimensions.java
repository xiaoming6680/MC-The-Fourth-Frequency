package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Single source of truth for real and private mirror dimensions. */
public final class PursuitDimensions {
	public static final ResourceKey<Level> OVERWORLD_0 = mirrorKey("correction_overworld_0");
	public static final ResourceKey<Level> OVERWORLD_1 = mirrorKey("correction_overworld_1");
	public static final ResourceKey<Level> NETHER_0 = mirrorKey("correction_nether_0");
	public static final ResourceKey<Level> NETHER_1 = mirrorKey("correction_nether_1");
	public static final ResourceKey<Level> END_0 = mirrorKey("correction_end_0");
	public static final ResourceKey<Level> END_1 = mirrorKey("correction_end_1");

	private PursuitDimensions() {
	}

	public static boolean isMirror(Level level) {
		return level != null && isMirror(level.dimension());
	}

	public static boolean isMirror(ResourceKey<Level> dimension) {
		return mirrors().contains(dimension);
	}

	public static boolean isMirror(String dimensionId) {
		return mirrors().stream().anyMatch(key -> key.identifier().toString().equals(dimensionId));
	}

	public static Optional<Family> sourceFamily(ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD) || dimension.equals(OVERWORLD_0) || dimension.equals(OVERWORLD_1)) {
			return Optional.of(Family.OVERWORLD);
		}
		if (dimension.equals(Level.NETHER) || dimension.equals(NETHER_0) || dimension.equals(NETHER_1)) {
			return Optional.of(Family.NETHER);
		}
		if (dimension.equals(Level.END) || dimension.equals(END_0) || dimension.equals(END_1)) {
			return Optional.of(Family.END);
		}
		return Optional.empty();
	}

	public static ResourceKey<Level> sourceKey(Family family) {
		return switch (family) {
			case OVERWORLD -> Level.OVERWORLD;
			case NETHER -> Level.NETHER;
			case END -> Level.END;
		};
	}

	public static Optional<ResourceKey<Level>> sourceForMirror(ResourceKey<Level> mirror) {
		if (!isMirror(mirror)) return Optional.empty();
		return sourceFamily(mirror).map(PursuitDimensions::sourceKey);
	}

	public static ResourceKey<Level> mirrorKey(Family family, int slot) {
		int normalized = Math.clamp(slot, 0, 1);
		return switch (family) {
			case OVERWORLD -> normalized == 0 ? OVERWORLD_0 : OVERWORLD_1;
			case NETHER -> normalized == 0 ? NETHER_0 : NETHER_1;
			case END -> normalized == 0 ? END_0 : END_1;
		};
	}

	public static Optional<ServerLevel> sourceLevel(MinecraftServer server, String sourceDimension) {
		try {
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(sourceDimension));
			if (isMirror(key)) key = sourceForMirror(key).orElse(Level.OVERWORLD);
			return Optional.ofNullable(server.getLevel(key));
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
	}

	public static List<ResourceKey<Level>> mirrors() {
		return List.of(OVERWORLD_0, OVERWORLD_1, NETHER_0, NETHER_1, END_0, END_1);
	}

	private static ResourceKey<Level> mirrorKey(String path) {
		return ResourceKey.create(Registries.DIMENSION,
				Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path));
	}

	public enum Family {
		OVERWORLD,
		NETHER,
		END
	}
}
