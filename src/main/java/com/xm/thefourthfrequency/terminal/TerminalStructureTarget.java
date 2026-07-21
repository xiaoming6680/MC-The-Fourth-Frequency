package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public enum TerminalStructureTarget {
	VILLAGE(0, "village", false, 96, StructureTags.VILLAGE),
	RUINED_PORTAL(1, "ruined_portal", false, 96, StructureTags.RUINED_PORTAL),
	MINESHAFT(2, "mineshaft", true, 72, StructureTags.MINESHAFT),
	TRIAL_CHAMBERS(3, "trial_chambers", true, 72, customTag("trial_chambers")),
	FORTRESS(4, "fortress", false, 96, customTag("fortress")),
	BASTION(5, "bastion", true, 96, customTag("bastion")),
	NONE(6, "none", false, 0, null);

	private final int wireId;
	private final String id;
	private final boolean sideRoute;
	private final int searchRadiusChunks;
	private final TagKey<Structure> structureTag;

	TerminalStructureTarget(int wireId, String id, boolean sideRoute, int searchRadiusChunks,
			TagKey<Structure> structureTag) {
		this.wireId = wireId;
		this.id = id;
		this.sideRoute = sideRoute;
		this.searchRadiusChunks = searchRadiusChunks;
		this.structureTag = structureTag;
	}

	public int wireId() {
		return wireId;
	}

	public String id() {
		return id;
	}

	public boolean sideRoute() {
		return sideRoute;
	}

	public int searchRadiusChunks() {
		return searchRadiusChunks;
	}

	public TagKey<Structure> structureTag() {
		return structureTag;
	}

	public static TerminalStructureTarget fromWire(int wire) {
		for (TerminalStructureTarget target : values()) if (target.wireId == wire) return target;
		return NONE;
	}

	public static TerminalStructureTarget fromId(String id) {
		for (TerminalStructureTarget target : values()) if (target.id.equals(id)) return target;
		return NONE;
	}

	public static int bit(TerminalStructureTarget target) {
		return target == NONE ? 0 : 1 << target.wireId;
	}

	private static TagKey<Structure> customTag(String path) {
		return TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(
				TheFourthFrequency.MOD_ID, "terminal_navigation/" + path));
	}
}
