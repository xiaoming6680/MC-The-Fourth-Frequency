package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.terminal.TerminalStructureTarget;
import com.xm.thefourthfrequency.terminal.TerminalNavigationMath;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.xm.thefourthfrequency.pursuit.PursuitDimensions;

/** On-demand, cached structure location for the low-view-distance terminal navigator. */
public final class StructureNavigationService {
	public static final int ARRIVAL_RADIUS = 50;
	private static boolean initialized;

	private StructureNavigationService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(StructureNavigationService::onServerTick);
	}

	public static int availableTargetsMask(ServerPlayer player, CompoundTag tag) {
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		if (!SurvivalMilestone.MINED_LOGS.present(milestones)) return 0;
		int hintTier = StoryProgressService.guidanceHintTier(tag);
		String objective = StoryProgressService.objective(tag,
				FrequencyWorldData.get(player.level().getServer())).id();
		int mask;
		if (player.level().dimension() == Level.NETHER) {
			mask = progressiveMask(TerminalStructureTarget.FORTRESS,
					TerminalStructureTarget.RUINED_PORTAL, TerminalStructureTarget.BASTION, hintTier);
			return withoutCompleted(mask, tag);
		}
		if (player.level().dimension() != Level.OVERWORLD) return 0;
		mask = switch (objective) {
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
		return withoutCompleted(mask, tag);
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
			clearCompletion(record);
			NavigationState state = new NavigationState(target.id(), "", found != null, target.id(),
					found == null ? 0L : found.asLong(), found == null ? "" : dimension, now);
			state.writeTo(record);
		});
		com.xm.thefourthfrequency.terminal.TerminalNoticeService.send(player, Component.translatable(found == null
				? "message.thefourthfrequency.navigation.not_found"
				: "message.thefourthfrequency.navigation.ready",
				Component.translatable("terminal.thefourthfrequency.navigation.target." + target.id())));
		return true;
	}

	public static TerminalStructureTarget selectedTarget(CompoundTag tag) {
		return TerminalStructureTarget.fromId(NavigationState.read(tag).kind());
	}

	public static boolean hasLocatedTarget(CompoundTag tag) {
		return selectedTarget(tag) != TerminalStructureTarget.NONE && NavigationState.read(tag).located();
	}

	public static boolean navigationCompletionAvailable(CompoundTag tag) {
		return tag.getBooleanOr(TerminalData.NAVIGATION_COMPLETION_ACTIVE, false);
	}

	public static void clearStructureTargetOutsideDimension(CompoundTag tag, String dimension, long now) {
		NavigationState navigation = NavigationState.read(tag);
		if (TerminalStructureTarget.fromId(navigation.kind()) == TerminalStructureTarget.NONE
				|| navigation.dimension().equals(dimension)) return;
		if (TerminalToolService.guidanceTool(tag) == TerminalTool.NAVIGATION.slot())
			tag.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL, TerminalToolService.NO_TOOL);
		new NavigationState("unresolved", "", false, "", 0L, "", now).writeTo(tag);
	}

	public static int navigationCompletionDirection(ServerPlayer player, CompoundTag tag) {
		int fallback = Math.clamp(tag.getIntOr(TerminalData.NAVIGATION_COMPLETION_DIRECTION, 0), 0, 3);
		String dimension = tag.getStringOr(TerminalData.NAVIGATION_COMPLETION_DIMENSION, "");
		if (!dimension.equals(player.level().dimension().identifier().toString())) return fallback;
		BlockPos target = BlockPos.of(tag.getLongOr(TerminalData.NAVIGATION_COMPLETION_POSITION, 0L));
		BlockPos origin = player.blockPosition();
		return TerminalNavigationMath.relativeDirection(target.getX() - origin.getX(),
				target.getZ() - origin.getZ(), player.getYRot());
	}

	public static boolean acknowledgeCompletion(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag tag = data.terminalRecord(player.getUUID()).orElse(null);
		if (tag == null || !tag.getBooleanOr(TerminalData.NAVIGATION_COMPLETION_UNREAD, false)) return false;
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.NAVIGATION_COMPLETION_UNREAD, false);
			record.putBoolean(TerminalData.UNREAD_ALERT_ACTIVE,
					com.xm.thefourthfrequency.terminal.TerminalSignalLog.unreadCount(record) > 0);
		});
		return true;
	}

	public static boolean dismissCompletion(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag tag = data.terminalRecord(player.getUUID()).orElse(null);
		if (tag == null || !navigationCompletionAvailable(tag)) return false;
		data.updateTerminalRecord(player.getUUID(), StructureNavigationService::clearCompletion);
		TerminalRuntimeService.synchronizeProjection(player);
		return true;
	}

	public static void clearCompletion(CompoundTag tag) {
		tag.putBoolean(TerminalData.NAVIGATION_COMPLETION_ACTIVE, false);
		tag.putBoolean(TerminalData.NAVIGATION_COMPLETION_UNREAD, false);
		tag.putLong(TerminalData.NAVIGATION_COMPLETION_POSITION, 0L);
		tag.putString(TerminalData.NAVIGATION_COMPLETION_DIMENSION, "");
		tag.putInt(TerminalData.NAVIGATION_COMPLETION_DIRECTION, 0);
	}

	public static boolean arrived(BlockPos player, BlockPos target) {
		return TerminalNavigationMath.withinHorizontalRadius(
				player.getX(), player.getZ(), target.getX(), target.getZ(), ARRIVAL_RADIUS);
	}

	private static void onServerTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) updatePlayer(player);
	}

	public static void updatePlayer(ServerPlayer player) {
		if (PursuitDimensions.isMirror(player.level())) return;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag tag = data.terminalRecord(player.getUUID()).orElse(null);
		if (tag == null || TerminalToolService.guidanceTool(tag) != TerminalTool.NAVIGATION.slot()) return;
		NavigationState navigation = NavigationState.read(tag);
		TerminalStructureTarget target = TerminalStructureTarget.fromId(navigation.kind());
		if (target == TerminalStructureTarget.NONE || !navigation.located()
				|| !navigation.dimension().equals(player.level().dimension().identifier().toString())) return;
		BlockPos destination = BlockPos.of(navigation.position());
		if (!arrived(player.blockPosition(), destination)) return;
		int direction = TerminalNavigationMath.relativeDirection(
				destination.getX() - player.getBlockX(), destination.getZ() - player.getBlockZ(), player.getYRot());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL, TerminalToolService.NO_TOOL);
			record.putInt(TerminalData.COMPLETED_STRUCTURE_TARGETS_MASK,
					record.getIntOr(TerminalData.COMPLETED_STRUCTURE_TARGETS_MASK, 0)
							| TerminalStructureTarget.bit(target));
			record.putBoolean(TerminalData.NAVIGATION_COMPLETION_ACTIVE, true);
			record.putBoolean(TerminalData.NAVIGATION_COMPLETION_UNREAD, true);
			record.putLong(TerminalData.NAVIGATION_COMPLETION_POSITION, destination.asLong());
			record.putString(TerminalData.NAVIGATION_COMPLETION_DIMENSION, navigation.dimension());
			record.putInt(TerminalData.NAVIGATION_COMPLETION_DIRECTION, direction);
			new NavigationState("unresolved", "", false, "", 0L, "", player.level().getGameTime()).writeTo(record);
		});
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}

	private static int bit(TerminalStructureTarget target) {
		return TerminalStructureTarget.bit(target);
	}

	private static int withoutCompleted(int mask, CompoundTag tag) {
		return mask & ~tag.getIntOr(TerminalData.COMPLETED_STRUCTURE_TARGETS_MASK, 0);
	}

	private static int progressiveMask(TerminalStructureTarget primary, TerminalStructureTarget secondary,
			TerminalStructureTarget tertiary, int hintTier) {
		int mask = primary == TerminalStructureTarget.NONE ? 0 : bit(primary);
		if (hintTier >= 1 && secondary != TerminalStructureTarget.NONE) mask |= bit(secondary);
		if (hintTier >= 2 && tertiary != TerminalStructureTarget.NONE) mask |= bit(tertiary);
		return mask;
	}
}
