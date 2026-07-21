package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.terminal.TerminalStructureTarget;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** On-demand, cached structure location for the low-view-distance terminal navigator. */
public final class StructureNavigationService {
	private StructureNavigationService() {
	}

	public static int availableTargetsMask(ServerPlayer player, CompoundTag tag) {
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		if (!SurvivalMilestone.MINED_LOGS.present(milestones)) return 0;
		int hintTier = StoryProgressService.guidanceHintTier(tag);
		String objective = StoryProgressService.objective(tag,
				FrequencyWorldData.get(player.level().getServer())).id();
		if (player.level().dimension() == Level.NETHER) {
			if (!SurvivalMilestone.ENTERED_NETHER.present(milestones)) return 0;
			if (objective.equals("return_from_nether")) return progressiveMask(
					TerminalStructureTarget.RUINED_PORTAL, TerminalStructureTarget.FORTRESS,
					TerminalStructureTarget.BASTION, hintTier);
			return progressiveMask(TerminalStructureTarget.FORTRESS,
					TerminalStructureTarget.RUINED_PORTAL, TerminalStructureTarget.BASTION, hintTier);
		}
		if (player.level().dimension() != Level.OVERWORLD) return 0;
		return switch (objective) {
			case "bring_iron" -> progressiveMask(TerminalStructureTarget.VILLAGE,
					TerminalStructureTarget.MINESHAFT, TerminalStructureTarget.RUINED_PORTAL, hintTier);
			case "enter_nether", "collect_blaze_rods", "return_from_nether" -> progressiveMask(
					TerminalStructureTarget.RUINED_PORTAL, TerminalStructureTarget.VILLAGE,
					TerminalStructureTarget.MINESHAFT, hintTier);
			case "craft_eye" -> progressiveMask(TerminalStructureTarget.VILLAGE,
					TerminalStructureTarget.RUINED_PORTAL, TerminalStructureTarget.TRIAL_CHAMBERS, hintTier);
			case "record_eye", "find_stronghold", "enter_end", "defeat_boss", "complete" -> progressiveMask(
					TerminalStructureTarget.NONE, TerminalStructureTarget.VILLAGE,
					TerminalStructureTarget.TRIAL_CHAMBERS, hintTier);
			default -> progressiveMask(TerminalStructureTarget.VILLAGE,
					TerminalStructureTarget.MINESHAFT, TerminalStructureTarget.TRIAL_CHAMBERS, hintTier);
		};
	}

	public static boolean selectTarget(ServerPlayer player, int wireId) {
		TerminalStructureTarget target = TerminalStructureTarget.fromWire(wireId);
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag tag = data.terminalRecord(player.getUUID()).orElse(null);
		if (target == TerminalStructureTarget.NONE || tag == null
				|| TerminalToolService.toolsDisabled(tag, player.level().getGameTime())
				|| (availableTargetsMask(player, tag) & bit(target)) == 0) return false;
		ServerLevel level = player.level();
		BlockPos found = level.findNearestMapStructure(target.structureTag(), player.blockPosition(),
				target.searchRadiusChunks(), false);
		long now = level.getGameTime();
		String dimension = level.dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), record -> {
			NavigationState state = new NavigationState(target.id(), "", found != null, target.id(),
					found == null ? 0L : found.asLong(), found == null ? "" : dimension, now);
			state.writeTo(record);
			record.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL,
					found == null ? TerminalToolService.NO_TOOL : TerminalTool.NAVIGATION.slot());
		});
		player.displayClientMessage(Component.translatable(found == null
				? "message.thefourthfrequency.navigation.not_found"
				: "message.thefourthfrequency.navigation.ready",
				Component.translatable("terminal.thefourthfrequency.navigation.target." + target.id())), true);
		return true;
	}

	public static TerminalStructureTarget selectedTarget(CompoundTag tag) {
		return TerminalStructureTarget.fromId(NavigationState.read(tag).kind());
	}

	public static boolean hasLocatedTarget(CompoundTag tag) {
		return selectedTarget(tag) != TerminalStructureTarget.NONE && NavigationState.read(tag).located();
	}

	private static int bit(TerminalStructureTarget target) {
		return TerminalStructureTarget.bit(target);
	}

	private static int progressiveMask(TerminalStructureTarget primary, TerminalStructureTarget secondary,
			TerminalStructureTarget tertiary, int hintTier) {
		int mask = primary == TerminalStructureTarget.NONE ? 0 : bit(primary);
		if (hintTier >= 1 && secondary != TerminalStructureTarget.NONE) mask |= bit(secondary);
		if (hintTier >= 2 && tertiary != TerminalStructureTarget.NONE) mask |= bit(tertiary);
		return mask;
	}
}
