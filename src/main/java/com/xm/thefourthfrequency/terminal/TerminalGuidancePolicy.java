package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.world.SurvivalMilestone;

/** Pure unlock and recommendation policy shared by the authoritative tool service and tests. */
public final class TerminalGuidancePolicy {
	private TerminalGuidancePolicy() {
	}

	public static int availableToolsMask(int milestones, boolean portalKnown, int eyeSamples) {
		int mask = bit(TerminalTool.HOME) | bit(TerminalTool.WEATHER);
		if (SurvivalMilestone.MINED_LOGS.present(milestones)) {
			mask |= bit(TerminalTool.MINERALS) | bit(TerminalTool.NAVIGATION);
		}
		if (portalKnown || SurvivalMilestone.ENTERED_NETHER.present(milestones)) {
			mask |= bit(TerminalTool.PORTAL);
		}
		if (eyeSamples > 0 || SurvivalMilestone.CRAFTED_EYE.present(milestones)
				|| SurvivalMilestone.FOUND_STRONGHOLD.present(milestones)
				|| SurvivalMilestone.ENTERED_END.present(milestones)) {
			mask |= bit(TerminalTool.STRONGHOLD);
		}
		return mask;
	}

	public static int availableResourcesMask(int milestones, int hintTier) {
		if (!SurvivalMilestone.MINED_LOGS.present(milestones)) return 0;
		return bit(TerminalResource.IRON) | bit(TerminalResource.COAL)
				| bit(TerminalResource.GOLD) | bit(TerminalResource.DIAMOND);
	}

	public static Recommendations recommendations(String objectiveId, int availableToolsMask,
			int activeGuidanceTool, boolean homeKnown, long dayTime, boolean toolsDisabled, int hintTier) {
		if (toolsDisabled && available(availableToolsMask, TerminalTool.WEATHER)) {
			return fill(availableToolsMask, TerminalTool.WEATHER, TerminalTool.HOME, hintTier >= 1);
		}
		TerminalTool primary = validAvailable(activeGuidanceTool, availableToolsMask);
		TerminalTool secondary = null;
		TerminalTool[] desired = switch (objectiveId) {
			case "mine_logs" -> pair(TerminalTool.WEATHER, TerminalTool.HOME);
			case "bring_iron" -> pair(TerminalTool.MINERALS, TerminalTool.HOME);
			case "enter_nether" -> pair(TerminalTool.NAVIGATION, TerminalTool.MINERALS);
			case "collect_blaze_rods" -> pair(TerminalTool.NAVIGATION, TerminalTool.PORTAL);
			case "return_from_nether" -> pair(TerminalTool.PORTAL, TerminalTool.NAVIGATION);
			case "craft_eye" -> pair(TerminalTool.NAVIGATION, TerminalTool.WEATHER);
			case "record_eye", "find_stronghold", "enter_end" ->
					pair(TerminalTool.STRONGHOLD, TerminalTool.HOME);
			case "defeat_boss" -> pair(TerminalTool.STRONGHOLD, TerminalTool.WEATHER);
			default -> pair(TerminalTool.HOME, TerminalTool.WEATHER);
		};
		for (TerminalTool tool : desired) {
			if (!available(availableToolsMask, tool) || tool == primary) continue;
			if (primary == null) primary = tool;
			else if (secondary == null) secondary = tool;
		}
		long time = Math.floorMod(dayTime, 24_000L);
		boolean shelterSoonUseful = !homeKnown && time >= 10_000L && time < 23_000L;
		if (shelterSoonUseful && available(availableToolsMask, TerminalTool.HOME)
				&& primary != TerminalTool.HOME) secondary = TerminalTool.HOME;
		return fill(availableToolsMask, primary, secondary, hintTier >= 1 || shelterSoonUseful);
	}

	public static boolean available(int mask, TerminalTool tool) {
		return (mask & bit(tool)) != 0;
	}

	public static boolean resourceAvailable(int mask, TerminalResource resource) {
		return resource != TerminalResource.NONE && (mask & bit(resource)) != 0;
	}

	private static Recommendations fill(int mask, TerminalTool primary, TerminalTool secondary,
			boolean revealSecondary) {
		TerminalTool first = primary != null && available(mask, primary) ? primary : null;
		TerminalTool second = revealSecondary && secondary != null && secondary != first
				&& available(mask, secondary) ? secondary : null;
		for (TerminalTool fallback : new TerminalTool[]{TerminalTool.HOME, TerminalTool.WEATHER,
				TerminalTool.MINERALS, TerminalTool.NAVIGATION, TerminalTool.PORTAL, TerminalTool.STRONGHOLD}) {
			if (!available(mask, fallback) || fallback == first || fallback == second) continue;
			if (first == null) first = fallback;
			else if (revealSecondary && second == null) second = fallback;
			if (first != null && (!revealSecondary || second != null)) break;
		}
		return new Recommendations(wire(first), wire(second));
	}

	private static TerminalTool validAvailable(int wire, int mask) {
		TerminalTool tool = TerminalTool.fromSlot(wire);
		return tool != null && available(mask, tool) ? tool : null;
	}

	private static TerminalTool[] pair(TerminalTool first, TerminalTool second) {
		return new TerminalTool[]{first, second};
	}

	private static int wire(TerminalTool tool) {
		return tool == null ? TerminalToolService.NO_TOOL : tool.slot();
	}

	private static int bit(TerminalTool tool) {
		return 1 << tool.slot();
	}

	private static int bit(TerminalResource resource) {
		return 1 << resource.wireId();
	}

	public record Recommendations(int primary, int secondary) {
	}
}
