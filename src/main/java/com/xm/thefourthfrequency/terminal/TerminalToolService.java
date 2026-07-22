package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.networking.TerminalToolSnapshotPayload;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.world.FragmentInvestigationService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.ResourceGuidanceService;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.StructureNavigationService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;

public final class TerminalToolService {
	public static final int NO_TOOL = 6;
	private static boolean initialized;

	private TerminalToolService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				TerminalToolService::recordPortalArrival);
	}

	private static void recordPortalArrival(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
		if (origin.dimension() == destination.dimension()) return;
		FrequencyWorldData data = FrequencyWorldData.get(destination.getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return;
		BlockPos arrival = player.blockPosition();
		String dimension = destination.dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putLong(TerminalData.LAST_PORTAL_POSITION, arrival.asLong());
			tag.putString(TerminalData.LAST_PORTAL_DIMENSION, dimension);
		});
		SurvivalProgressService.recordPortalTransition(player, origin, destination);
		TerminalRuntimeService.refresh(player);
	}

	public static void recordEyeSample(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return;
		BlockPos sample = player.blockPosition();
		BlockPos stronghold = player.level().findNearestMapStructure(
				StructureTags.EYE_OF_ENDER_LOCATED, sample, 100, false);
		if (stronghold == null) return;
		String dimension = player.level().dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putInt(TerminalData.EYE_SAMPLE_COUNT,
					Math.clamp(tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0) + 1, 0, 64));
			tag.putLong(TerminalData.STRONGHOLD_POSITION, stronghold.asLong());
			tag.putString(TerminalData.STRONGHOLD_DIMENSION, dimension);
		});
		SurvivalProgressService.mark(player, SurvivalMilestone.CRAFTED_EYE);
		SurvivalProgressService.mark(player, SurvivalMilestone.THREW_EYE);
		TerminalRuntimeService.refresh(player);
	}

	public static TerminalToolSnapshotPayload snapshot(ServerPlayer player, int selectedTool) {
		return snapshot(player, selectedTool, TerminalControlPolicy.DEFAULT_TUNING, 0);
	}

	public static TerminalToolSnapshotPayload snapshot(ServerPlayer player, int selectedTool,
			int receiverTuning, int receiverLockTicks) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag tag = data.terminalRecord(player.getUUID()).orElse(new CompoundTag());
		long now = player.level().getGameTime();
		int available = availableToolsMask(player, tag);
		int safeSelected = validToolWire(selectedTool) && (available & 1 << selectedTool) != 0
				? selectedTool : NO_TOOL;
		int guidance = guidanceTool(tag);
		if (!validToolWire(guidance) || (available & 1 << guidance) == 0) guidance = NO_TOOL;
		boolean disabled = toolsDisabled(tag, now);
		int disabledTicks = disabled ? (int) Math.clamp(
				tag.getLongOr(TerminalData.TOOLS_DISABLED_UNTIL, 0L) - now, 0L, 72_000L) : 0;
		long dayTime = Math.floorMod(player.level().getDayTime(), 24_000L);
		int untilLightChange = ticksUntilLightChange(dayTime);
		Location home = home(player, tag);
		Location portal = storedLocation(tag, TerminalData.LAST_PORTAL_POSITION, TerminalData.LAST_PORTAL_DIMENSION);
		StrongholdEstimate stronghold = strongholdEstimate(player, tag);
		int weather = player.level().isThundering() ? 2 : player.level().isRaining() ? 1 : 0;
		var nearby = FragmentInvestigationService.nearby(player).orElse(null);
		int nearbySignals = nearby == null ? 0 : 1;
		boolean receiverAvailable = nearby != null && !disabled
				&& tag.getIntOr(TerminalData.BAND_STAGE, 0) > 0;
		int receiverTarget = receiverAvailable ? nearby.tuning() : 0;
		int receiverStrength = receiverAvailable
				? TerminalControlPolicy.receiverStrength(receiverTuning, receiverTarget) : 0;
		int specialFiles = TerminalFileState.states(tag).size();
		int storySignals = (int) TerminalSignalLog.entries(tag).stream()
				.filter(entry -> entry.band() == SignalBand.UNKNOWN
						|| entry.type().startsWith("fragment_") || entry.type().startsWith("facility_"))
				.count();
		BlockPos playerPos = player.blockPosition();
		RelativeLocation homeRelative = relative(player, playerPos, home);
		RelativeLocation portalRelative = relative(player, playerPos, portal);
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		int hintTier = StoryProgressService.guidanceHintTier(tag);
		int resourceMask = TerminalGuidancePolicy.availableResourcesMask(milestones, hintTier);
		int navigationTargets = StructureNavigationService.availableTargetsMask(player, tag);
		TerminalStructureTarget selectedNavigation = StructureNavigationService.selectedTarget(tag);
		if (selectedNavigation != TerminalStructureTarget.NONE && NavigationState.read(tag).located())
			navigationTargets |= TerminalStructureTarget.bit(selectedNavigation);
		boolean unstableSignal = unstableSignalAvailable(player, tag);
		String objective = StoryProgressService.objective(tag, data).id();
		TerminalGuidancePolicy.Recommendations recommendations = TerminalGuidancePolicy.recommendations(
				objective, available, guidance, home.known(), dayTime, disabled, hintTier);
		return new TerminalToolSnapshotPayload(
				TerminalToolSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				available,
				safeSelected,
				guidance,
				recommendations.primary(),
				recommendations.secondary(),
				resourceMask,
				navigationTargets,
				selectedNavigation.wireId(),
				unstableSignal,
				disabled,
				disabledTicks,
				selectedResource(tag).wireId(),
				weather,
				dayTime,
				untilLightChange,
				playerPos.getY(),
				home.known(),
				home.bed(),
				homeRelative.sameDimension(),
				homeRelative.dx(),
				homeRelative.dz(),
				home.position().getY(),
				home.dimension(),
				portal.known(),
				portalRelative.sameDimension(),
				portalRelative.dx(),
				portalRelative.dz(),
				portal.position().getY(),
				portal.dimension(),
				nearbySignals,
				receiverAvailable,
				receiverTarget,
				receiverStrength,
				Math.clamp(receiverLockTicks, 0, 20),
				specialFiles,
				storySignals,
				Math.clamp(tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0), 0, 64),
				stronghold.known(),
				stronghold.sameDimension(),
				stronghold.dx(),
				stronghold.dz(),
				stronghold.dimension(),
				stronghold.minimumDistance(),
				stronghold.maximumDistance());
	}

	public static boolean selectTool(ServerPlayer player, int tool) {
		if (tool == NO_TOOL) return true;
		if (!validToolWire(tool)) return false;
		CompoundTag tag = record(player);
		return tag != null && (availableToolsMask(player, tag) & 1 << tool) != 0;
	}

	public static boolean selectResource(ServerPlayer player, int value) {
		if (!TerminalResource.isSelectableWire(value)) return false;
		CompoundTag tag = record(player);
		if (tag == null || blockedByCorrection(player, tag)) return false;
		TerminalResource resource = TerminalResource.fromWire(value);
		if (!resourceAvailable(tag, resource)) return false;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.SELECTED_RESOURCE, resource.wireId());
			NavigationState.read(record).select(resource.id(), resourceItem(resource)).writeTo(record);
		});
		ResourceGuidanceService.restartScan(player, false);
		return true;
	}

	public static boolean requestRescan(ServerPlayer player) {
		CompoundTag tag = record(player);
		if (tag == null || blockedByCorrection(player, tag)
				|| selectedResource(tag) == TerminalResource.NONE
				|| !resourceAvailable(tag, selectedResource(tag))) return false;
		ResourceGuidanceService.restartScan(player, true);
		return true;
	}

	public static boolean setHome(ServerPlayer player) {
		CompoundTag tag = record(player);
		if (tag == null || blockedByCorrection(player, tag)) return false;
		BlockPos position = player.blockPosition();
		String dimension = player.level().dimension().identifier().toString();
		FrequencyWorldData.get(player.level().getServer()).updateTerminalRecord(player.getUUID(), record -> {
			record.putLong(TerminalData.HOME_POSITION, position.asLong());
			record.putString(TerminalData.HOME_DIMENSION, dimension);
		});
		SurvivalProgressService.mark(player, SurvivalMilestone.HOME);
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.tool.home_saved"), true);
		return true;
	}

	public static boolean startGuidance(ServerPlayer player, int toolValue) {
		if (!validToolWire(toolValue) || toolValue == TerminalTool.WEATHER.slot()) return false;
		TerminalTool tool = TerminalTool.fromSlot(toolValue);
		CompoundTag tag = record(player);
		if (tool == null || tag == null || blockedByCorrection(player, tag)
				|| (availableToolsMask(player, tag) & 1 << toolValue) == 0 || !hasGuidanceTarget(player, tag, tool)) {
			return false;
		}
		int previous = guidanceTool(tag);
		FrequencyWorldData.get(player.level().getServer()).updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL, toolValue));
		guidanceFeedback(player, previous, tool);
		return true;
	}

	public static boolean stopGuidance(ServerPlayer player, int value) {
		if (value != 0 || record(player) == null) return false;
		FrequencyWorldData.get(player.level().getServer()).updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL, NO_TOOL));
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.guidance.stopped"), true);
		return true;
	}

	public static int guidanceTool(CompoundTag tag) {
		int value = tag.getIntOr(TerminalData.ACTIVE_GUIDANCE_TOOL, NO_TOOL);
		return validToolWire(value) || value == NO_TOOL ? value : NO_TOOL;
	}

	public static boolean toolsDisabled(CompoundTag tag, long now) {
		return tag.getLongOr(TerminalData.TOOLS_DISABLED_UNTIL, 0L) > now;
	}

	private static boolean blockedByCorrection(ServerPlayer player, CompoundTag tag) {
		if (!toolsDisabled(tag, player.level().getGameTime())) return false;
		FrequencyWorldData.get(player.level().getServer()).updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.BREACH_MASK, record.getIntOr(TerminalData.BREACH_MASK, 0) | 1));
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.tool.recovering"), true);
		return true;
	}

	public static Location guidanceLocation(ServerPlayer player, CompoundTag tag, TerminalTool tool) {
		return switch (tool) {
			case HOME -> home(player, tag);
			case PORTAL -> storedLocation(tag, TerminalData.LAST_PORTAL_POSITION, TerminalData.LAST_PORTAL_DIMENSION);
			default -> Location.unknown();
		};
	}

	public static StrongholdEstimate strongholdEstimate(ServerPlayer player, CompoundTag tag) {
		int samples = Math.clamp(tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0), 0, 64);
		String dimension = tag.getStringOr(TerminalData.STRONGHOLD_DIMENSION, "");
		if (samples < 2 || dimension.isBlank()) return StrongholdEstimate.unknown();
		BlockPos target = BlockPos.of(tag.getLongOr(TerminalData.STRONGHOLD_POSITION, 0L));
		boolean sameDimension = dimension.equals(player.level().dimension().identifier().toString());
		if (!sameDimension) return new StrongholdEstimate(true, false, 0, 0, dimension, 0, 0);
		BlockPos origin = player.blockPosition();
		int exactDx = target.getX() - origin.getX();
		int exactDz = target.getZ() - origin.getZ();
		double exactDistance = Math.sqrt((double) exactDx * exactDx + (double) exactDz * exactDz);
		int uncertainty = samples >= 3 ? 128 : 512;
		int minimum = Math.max(0, (int) Math.floor(exactDistance) - uncertainty);
		int maximum = Math.min(30_000_000, (int) Math.ceil(exactDistance) + uncertainty);
		double step = Math.toRadians(samples >= 3 ? 22.5D : 45.0D);
		double angle = Math.atan2(exactDz, exactDx);
		double estimatedAngle = Math.round(angle / step) * step;
		int dx = (int) Math.round(Math.cos(estimatedAngle) * 100.0D);
		int dz = (int) Math.round(Math.sin(estimatedAngle) * 100.0D);
		return new StrongholdEstimate(true, true, dx, dz, dimension, minimum, maximum);
	}

	public static int availableToolsMask(ServerPlayer player, CompoundTag tag) {
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		boolean portalKnown = !tag.getStringOr(TerminalData.LAST_PORTAL_DIMENSION, "").isBlank();
		int eyeSamples = tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0);
		return TerminalGuidancePolicy.availableToolsMask(milestones, portalKnown, eyeSamples);
	}

	public static int availableResourcesMask(CompoundTag tag) {
		return TerminalGuidancePolicy.availableResourcesMask(
				tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0),
				StoryProgressService.guidanceHintTier(tag));
	}

	public static boolean resourceAvailable(CompoundTag tag, TerminalResource resource) {
		return TerminalGuidancePolicy.resourceAvailable(availableResourcesMask(tag), resource);
	}

	public static boolean unstableSignalAvailable(ServerPlayer player, CompoundTag tag) {
		NavigationState navigation = NavigationState.read(tag);
		boolean alreadySelected = navigation.kind().equals("structure_fragment") && navigation.located();
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		boolean midgame = SurvivalMilestone.ENTERED_NETHER.present(milestones);
		return alreadySelected || midgame && StoryProgressService.guidanceHintTier(tag) >= 2
				&& FragmentInvestigationService.hasUndiscoveredCandidate(player);
	}

	private static boolean hasGuidanceTarget(ServerPlayer player, CompoundTag tag, TerminalTool tool) {
		return switch (tool) {
			case HOME -> home(player, tag).known();
			case MINERALS -> selectedResource(tag) != TerminalResource.NONE;
			case PORTAL -> !tag.getStringOr(TerminalData.LAST_PORTAL_DIMENSION, "").isBlank();
			case NAVIGATION -> {
				NavigationState navigation = NavigationState.read(tag);
				yield (navigation.kind().equals("structure_fragment")
						|| TerminalStructureTarget.fromId(navigation.kind()) != TerminalStructureTarget.NONE)
						&& navigation.located();
			}
			case STRONGHOLD -> tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0) >= 2
					&& !tag.getStringOr(TerminalData.STRONGHOLD_DIMENSION, "").isBlank();
			case WEATHER -> false;
		};
	}

	private static void guidanceFeedback(ServerPlayer player, int previous, TerminalTool next) {
		if (validToolWire(previous) && previous != next.slot()) {
			player.displayClientMessage(Component.translatable("message.thefourthfrequency.guidance.replaced",
					Component.translatable("terminal.thefourthfrequency.tool." + next.id())), true);
		} else {
			player.displayClientMessage(Component.translatable("message.thefourthfrequency.guidance.started",
					Component.translatable("terminal.thefourthfrequency.tool." + next.id())), true);
		}
	}

	private static Location home(ServerPlayer player, CompoundTag tag) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn != null) {
			return new Location(true, true, respawn.respawnData().pos(),
					respawn.respawnData().dimension().identifier().toString());
		}
		return storedLocation(tag, TerminalData.HOME_POSITION, TerminalData.HOME_DIMENSION);
	}

	private static Location storedLocation(CompoundTag tag, String positionKey, String dimensionKey) {
		String dimension = tag.getStringOr(dimensionKey, "");
		return dimension.isBlank() ? Location.unknown()
				: new Location(true, false, BlockPos.of(tag.getLongOr(positionKey, 0L)), dimension);
	}

	private static RelativeLocation relative(ServerPlayer player, BlockPos origin, Location location) {
		if (!location.known()) return RelativeLocation.unknown();
		boolean same = location.dimension().equals(player.level().dimension().identifier().toString());
		return new RelativeLocation(same,
				same ? boundedDelta(location.position().getX() - origin.getX()) : 0,
				same ? boundedDelta(location.position().getZ() - origin.getZ()) : 0);
	}

	private static TerminalResource selectedResource(CompoundTag tag) {
		TerminalResource explicit = TerminalResource.fromWire(tag.getIntOr(TerminalData.SELECTED_RESOURCE, 3));
		return explicit != TerminalResource.NONE ? explicit
				: TerminalResource.fromId(NavigationState.read(tag).kind());
	}

	private static CompoundTag record(ServerPlayer player) {
		return FrequencyWorldData.get(player.level().getServer()).terminalRecord(player.getUUID()).orElse(null);
	}

	private static String resourceItem(TerminalResource resource) {
		return switch (resource) {
			case IRON -> "minecraft:raw_iron";
			case REDSTONE -> "minecraft:redstone";
			case DIAMOND -> "minecraft:diamond";
			case NONE -> "";
		};
	}

	private static int ticksUntilLightChange(long dayTime) {
		if (dayTime < 13_000L) return (int) (13_000L - dayTime);
		if (dayTime < 23_000L) return (int) (23_000L - dayTime);
		return (int) (37_000L - dayTime);
	}

	private static int bit(TerminalTool tool) {
		return 1 << tool.slot();
	}

	private static boolean validToolWire(int value) {
		return value >= TerminalTool.HOME.slot() && value <= TerminalTool.STRONGHOLD.slot();
	}

	private static int boundedDelta(int value) {
		return Math.clamp(value, -30_000_000, 30_000_000);
	}

	public record Location(boolean known, boolean bed, BlockPos position, String dimension) {
		private static Location unknown() {
			return new Location(false, false, BlockPos.ZERO, "");
		}
	}

	private record RelativeLocation(boolean sameDimension, int dx, int dz) {
		private static RelativeLocation unknown() {
			return new RelativeLocation(false, 0, 0);
		}
	}

	public record StrongholdEstimate(boolean known, boolean sameDimension, int dx, int dz,
			String dimension, int minimumDistance, int maximumDistance) {
		private static StrongholdEstimate unknown() {
			return new StrongholdEstimate(false, false, 0, 0, "", 0, 0);
		}
	}
}
