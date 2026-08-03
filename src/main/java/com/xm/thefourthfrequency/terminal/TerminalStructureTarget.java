package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public enum TerminalStructureTarget {
	VILLAGE(0, "village", false, 96, 48, StructureTags.VILLAGE),
	RUINED_PORTAL(1, "ruined_portal", false, 96, 20, StructureTags.RUINED_PORTAL),
	MINESHAFT(2, "mineshaft", true, 72, 28, StructureTags.MINESHAFT),
	TRIAL_CHAMBERS(3, "trial_chambers", true, 72, 28, customTag("trial_chambers")),
	FORTRESS(4, "fortress", false, 96, 40, customTag("fortress")),
	BASTION(5, "bastion", true, 96, 40, customTag("bastion")),
	NONE(6, "none", false, 0, 0, null);

	private final int wireId;
	private final String id;
	private final boolean sideRoute;
	private final int searchRadiusChunks;
	private final int arrivalRadius;
	private final TagKey<Structure> structureTag;

	TerminalStructureTarget(int wireId, String id, boolean sideRoute, int searchRadiusChunks,
			int arrivalRadius, TagKey<Structure> structureTag) {
		this.wireId = wireId;
		this.id = id;
		this.sideRoute = sideRoute;
		this.searchRadiusChunks = searchRadiusChunks;
		this.arrivalRadius = arrivalRadius;
		this.structureTag = structureTag;
	}

	/**
	 * Horizontal distance at which the navigator calls this structure reached.
	 *
	 * <p>Scaled to how big the thing actually is and how far off you can spot it. One shared
	 * fifty-block radius meant a ruined portal - which is a handful of blocks - was declared
	 * reached while it was still well out of sight, and because arrival also strikes the target
	 * off the candidate list for good, that mistake was not repeatable.</p>
	 *
	 * <p>There is deliberately no vertical component: {@code StructurePlacement.getLocatePos}
	 * reports structures at y=0, so the located height carries no information to compare against.
	 * A deep mineshaft is therefore still reachable from the surface directly above it, and the
	 * tighter horizontal radius is what keeps that from happening a chunk too early.</p>
	 */
	public int arrivalRadius() {
		return arrivalRadius;
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
