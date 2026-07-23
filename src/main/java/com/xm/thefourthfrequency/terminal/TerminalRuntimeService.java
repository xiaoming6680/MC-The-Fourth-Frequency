package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.networking.TerminalClosedPayload;
import com.xm.thefourthfrequency.networking.TerminalControlPayload;
import com.xm.thefourthfrequency.networking.TerminalNavigationPayload;
import com.xm.thefourthfrequency.networking.TerminalSnapshotPayload;
import com.xm.thefourthfrequency.networking.TerminalToolSnapshotPayload;
import com.xm.thefourthfrequency.networking.TerminalLogEntryPayload;
import com.xm.thefourthfrequency.networking.TerminalFilePayload;
import com.xm.thefourthfrequency.facility.FacilityService;
import com.xm.thefourthfrequency.narrative.HiddenFilePolicy;
import com.xm.thefourthfrequency.narrative.NarrativeFileCatalog;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.FragmentInvestigationService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TerminalRuntimeService {
	private static final int LIVE_SYNC_TICKS = 20;
	private static final int NAVIGATION_SYNC_TICKS = 4;
	private static final Map<UUID, ViewState> OPEN_VIEWS = new LinkedHashMap<>();
	private static final Map<UUID, Integer> REMEMBERED_MODES = new LinkedHashMap<>();
	private static final Map<UUID, Integer> REMEMBERED_TUNING = new LinkedHashMap<>();
	private static boolean initialized;

	private TerminalRuntimeService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(TerminalRuntimeService::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OPEN_VIEWS.clear();
			REMEMBERED_MODES.clear();
			REMEMBERED_TUNING.clear();
		});
	}

	public static void open(ServerPlayer player, int handId) {
		InteractionHand hand = decodeHand(handId);
		if (hand == null || !validHeldTerminal(player, hand)) {
			ServerPlayNetworking.send(player, new TerminalClosedPayload(TerminalClosedPayload.INVALID_ITEM));
			return;
		}
		int mode = REMEMBERED_MODES.getOrDefault(player.getUUID(), TerminalControlPolicy.Mode.SIGNAL.ordinal());
		int tuning = REMEMBERED_TUNING.getOrDefault(player.getUUID(), TerminalControlPolicy.DEFAULT_TUNING);
		int serverTick = player.level().getServer().getTickCount();
		ViewState view = new ViewState(hand, TerminalControlPolicy.mode(mode), TerminalControlPolicy.tuning(tuning),
				0L, 0L, "", serverTick, TerminalToolService.NO_TOOL);
		OPEN_VIEWS.put(player.getUUID(), view);
		sendSnapshot(player, view);
		sendNavigation(player);
	}

	public static void control(ServerPlayer player, int action, int value) {
		ViewState view = OPEN_VIEWS.get(player.getUUID());
		if (view == null || !validHeldTerminal(player, view.hand)) return;
		switch (action) {
			case TerminalControlPayload.MODE -> {
				if (!TerminalControlPolicy.validMode(value)) return;
				view.mode = value;
				REMEMBERED_MODES.put(player.getUUID(), view.mode);
			}
			case TerminalControlPayload.TUNE -> {
				if (!TerminalControlPolicy.validTuning(value)
						|| view.selectedTool != TerminalTool.NAVIGATION.slot()
						|| !receiverAvailable(player)) return;
				applyTuning(player, view, value);
			}
			case TerminalControlPayload.REFRESH -> { if (value != 0) return; }
			case TerminalControlPayload.SELECT_FRAGMENT_TARGET -> {
				if (value < 0 || value >= 12) return;
				if (!FragmentInvestigationService.selectCandidate(player, value)) return;
			}
			case TerminalControlPayload.SELECT_TOOL -> {
				if (!TerminalToolService.selectTool(player, value)) return;
				view.selectedTool = value;
				if (value != TerminalTool.NAVIGATION.slot()) resetReceiver(view,
						player.level().getServer().getTickCount());
			}
			case TerminalControlPayload.SELECT_STRUCTURE_TARGET -> {
				if (!com.xm.thefourthfrequency.world.StructureNavigationService.selectTarget(player, value)) return;
			}
			case TerminalControlPayload.SELECT_NEAREST_UNSTABLE -> {
				if (value != 0 || !FragmentInvestigationService.selectNearestCandidate(player)) return;
			}
			case TerminalControlPayload.START_GUIDANCE -> {
				if (!TerminalToolService.startGuidance(player, value)) {
					sendSnapshot(player, view);
					return;
				}
			}
			case TerminalControlPayload.STOP_GUIDANCE -> {
				if (!TerminalToolService.stopGuidance(player, value)) return;
			}
			case TerminalControlPayload.SELECT_RESOURCE -> {
				if (!TerminalToolService.selectResource(player, value)) return;
			}
			case TerminalControlPayload.REQUEST_RESCAN -> {
				if (value != 0 || !TerminalToolService.requestRescan(player)) return;
			}
			case TerminalControlPayload.SET_HOME -> {
				if (value != 0 || !TerminalToolService.setHome(player)) return;
			}
			case TerminalControlPayload.READ_TRUTH_FILE -> {
				if (value != 0 || !markTruthRead(player)) return;
			}
			case TerminalControlPayload.MARK_RECORDS_READ -> {
				if (value != 0 || !markRecordsRead(player)) return;
			}
			case TerminalControlPayload.READ_HIDDEN_FILE -> {
				if (!markHiddenFileRead(player, value)) return;
			}
			case TerminalControlPayload.CLOSE -> {
				if (value != 0) return;
				remember(player.getUUID(), view);
				OPEN_VIEWS.remove(player.getUUID());
				return;
			}
			default -> { return; }
		}
		sendSnapshot(player, view);
	}

	private static boolean markTruthRead(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return false;
		boolean readable = TerminalFileState.states(record).stream().anyMatch(file ->
				file.id().equals("encrypted_witness_file") && file.unlocked());
		if (!readable) return false;
		if (!record.getBooleanOr(TerminalData.TRUTH_READ, false)) {
			data.updateTerminalRecord(player.getUUID(), tag -> tag.putBoolean(TerminalData.TRUTH_READ, true));
			synchronizeProjection(player, data);
		}
		return true;
	}

	private static boolean markRecordsRead(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return false;
		data.updateTerminalRecord(player.getUUID(), TerminalSignalLog::markAllRead);
		synchronizeProjection(player, data);
		return true;
	}

	private static boolean markHiddenFileRead(ServerPlayer player, int index) {
		String id = HiddenFilePolicy.fileId(index);
		if (id.isEmpty()) return false;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !TerminalFileState.discovered(record, id)
				|| !TerminalFileState.unlocked(record, id)) return false;
		long now = player.level().getGameTime();
		long dayTime = player.level().getDayTime();
		boolean[] completed = {false};
		data.updateTerminalRecord(player.getUUID(), tag -> {
			TerminalFileState.markRead(tag, id, now, dayTime);
			completed[0] = HiddenFilePolicy.allDiscovered(tag) && HiddenFilePolicy.allRead(tag)
					&& !TerminalFileState.unlocked(tag, HiddenFilePolicy.COMPLETE_FILE_ID);
		});
		if (completed[0]) FacilityService.unlockArchiveFromHiddenFiles(player);
		synchronizeProjection(player, data);
		return true;
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		var iterator = OPEN_VIEWS.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			ViewState view = entry.getValue();
			if (player == null) {
				remember(entry.getKey(), view);
				iterator.remove();
				continue;
			}
			if (!player.isAlive() || !validHeldTerminal(player, view.hand)) {
				remember(entry.getKey(), view);
				iterator.remove();
				ServerPlayNetworking.send(player, new TerminalClosedPayload(TerminalClosedPayload.UNAVAILABLE));
				continue;
			}
			advanceNearbyReceiver(player, view, now);
			if (now >= view.nextSyncTick) {
				view.nextSyncTick = now + LIVE_SYNC_TICKS;
				sendSnapshot(player, view);
			}
			if (now >= view.nextNavigationSyncTick) {
				view.nextNavigationSyncTick = now + NAVIGATION_SYNC_TICKS;
				sendNavigation(player);
			}
		}
	}

	private static boolean validHeldTerminal(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(ModItems.OLD_TERMINAL)) return false;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		return record != null
				&& !record.getBooleanOr(TerminalData.TERMINAL_CAPTURED, false)
				&& data.isValidTerminal(stack, player.getUUID());
	}

	private static void sendSnapshot(ServerPlayer player, ViewState view) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag tag = data.terminalRecord(player.getUUID()).orElse(null);
		if (tag == null) return;
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		BlockPos rift = BlockPos.of(tag.getLongOr(TerminalData.RIFT_POSITION, 0L));
		int bodyStage = tag.getIntOr(TerminalData.BODY_STAGE, 0);
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		int survivalVisualStage = SurvivalMilestone.FOUND_STRONGHOLD.present(milestones) ? 3
				: SurvivalMilestone.RETURNED_NETHER.present(milestones) ? 2
				: SurvivalMilestone.IRON.present(milestones) ? 1 : 0;
		boolean bound = tag.getBooleanOr(TerminalData.BOUND, false);
		String evidence = tag.getStringOr(TerminalData.FACILITY_EVIDENCE, "");
		java.util.List<TerminalLogEntryPayload> logs = TerminalSignalLog.entries(tag).stream()
				.map(entry -> new TerminalLogEntryPayload(entry.sequence(), entry.band().wireId(), entry.type(), entry.gameTime(),
						entry.dayTime(), entry.dimension(), entry.position(), entry.variant(), entry.severity(), entry.unread()))
				.toList();
		java.util.List<TerminalFilePayload> files = visibleFiles(tag);
		StoryProgressService.Objective objective = StoryProgressService.objective(tag, data);
		long now = level.getGameTime();
		TerminalSnapshotPayload payload = new TerminalSnapshotPayload(
				TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				0,
				view.mode,
				view.tuning,
				TerminalControlPolicy.visualStage(tag.getIntOr(TerminalData.PLOT_STAGE, 1), survivalVisualStage),
				Math.clamp(tag.getIntOr(TerminalData.BAND_STAGE, 0), 0, 3),
				Math.floorMod(tag.getIntOr(TerminalData.CACHE_VARIANT, 0), 4),
				tag.getBooleanOr(TerminalData.SECOND_CACHE_UNLOCKED, false),
				Math.floorMod(tag.getIntOr(TerminalData.SECOND_CACHE_VARIANT, 1), 4),
				personality(tag.getStringOr(TerminalData.PERSONALITY_TEMPLATE, "clinical")),
				tag.getBooleanOr(TerminalData.CONTINUITY_LEARNED, false),
				Math.clamp(tag.getIntOr(TerminalData.CONTINUITY_CONFIDENCE, 0), 0, 100),
				nonNegative(tag.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0)),
				Math.clamp(tag.getIntOr(TerminalData.BODY_PROGRESS, 0), 0, 1000),
				Math.clamp(bodyStage, 0, 4),
				capabilityMask(tag.getStringOr(TerminalData.TERMINAL_CAPABILITIES, "")),
				evidenceValue(evidence, "surface_shelter", false),
				evidenceValue(evidence, "abandoned_warehouse", false),
				evidenceValue(evidence, "underground_mine_station", true),
				evidenceValue(evidence, "field_observation", false),
				tag.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false),
				tag.getBooleanOr(TerminalData.RIFT_LOCATED, false),
				boundedDelta(rift.getX() - pos.getX()),
				boundedDelta(rift.getZ() - pos.getZ()),
				rift.getY(),
				nonNegative(tag.getIntOr(TerminalData.ENDING_VERSION, 0)),
				ending(tag.getStringOr(TerminalData.ENDING_OUTCOME, "unresolved")),
				tag.getBooleanOr(TerminalData.TERMINAL_CAPTURED, false),
				now,
				TerminalSignalLog.unreadCount(tag),
				logs,
				tag.getStringOr(TerminalData.ACTIVE_ANOMALY_ID, "none"),
				(int) Math.clamp(tag.getLongOr(TerminalData.ACTIVE_ANOMALY_UNTIL, 0L) - now, 0L, 1200L),
				files, -1, objective.id(), objective.progress(), objective.target());
		ServerPlayNetworking.send(player, payload);
		ServerPlayNetworking.send(player, TerminalToolService.snapshot(player, view.selectedTool,
				view.tuning, receiverLockTicks(player, view, now)));
	}

	public static java.util.List<TerminalFilePayload> visibleFiles(CompoundTag tag) {
		Map<String, TerminalFileState.State> knownFiles = TerminalFileState.states(tag).stream()
				.collect(java.util.stream.Collectors.toMap(TerminalFileState.State::id, state -> state));
		return NarrativeFileCatalog.definitions().stream().filter(definition ->
				knownFiles.containsKey(definition.id())).map(definition -> {
			TerminalFileState.State file = knownFiles.get(definition.id());
			return new TerminalFilePayload(definition.id(), true, file.unlocked(),
					file.discoveredGameTime(), file.discoveredDayTime(),
					file.unlockedGameTime(), file.unlockedDayTime(),
					file.read(), file.readGameTime(), file.readDayTime(),
					definition.id().equals("permanent_aftermath_record")
							? ending(tag.getStringOr(TerminalData.ENDING_OUTCOME, "unresolved")) : 0);
		}).toList();
	}

	private static void sendNavigation(ServerPlayer player) {
		ServerPlayNetworking.send(player, navigationSnapshot(player));
	}

	public static TerminalNavigationPayload navigationSnapshot(ServerPlayer player) {
		CompoundTag tag = FrequencyWorldData.get(player.level().getServer()).terminalRecord(player.getUUID()).orElse(null);
		if (tag == null) return new TerminalNavigationPayload(TerminalNavigationPayload.CURRENT_PROTOCOL_VERSION,
				0, false, false, 0, 0, 0, player.getYRot());
		NavigationState navigation = NavigationState.read(tag);
		BlockPos playerPos = player.blockPosition();
		int guidance = TerminalToolService.guidanceTool(tag);
		int kind = TerminalNavigationPayload.NONE;
		boolean located = false;
		boolean sameDimension = false;
		int dx = 0;
		int dz = 0;
		int targetY = 0;
		TerminalTool tool = TerminalTool.fromSlot(guidance);
		if (tool != null) {
			switch (tool) {
				case MINERALS -> {
					kind = targetKind(navigation.kind());
					located = kind >= TerminalNavigationPayload.IRON
							&& kind <= TerminalNavigationPayload.DIAMOND && navigation.located();
					sameDimension = player.level().dimension().identifier().toString().equals(navigation.dimension());
					BlockPos target = BlockPos.of(navigation.position());
					dx = boundedDelta(target.getX() - playerPos.getX());
					dz = boundedDelta(target.getZ() - playerPos.getZ());
					targetY = target.getY();
				}
				case NAVIGATION -> {
					TerminalStructureTarget structure = TerminalStructureTarget.fromId(navigation.kind());
					kind = navigation.kind().equals("structure_fragment")
							? TerminalNavigationPayload.UNSTABLE_SIGNAL : structureNavigationKind(structure);
					located = (navigation.kind().equals("structure_fragment")
							|| structure != TerminalStructureTarget.NONE) && navigation.located();
					sameDimension = player.level().dimension().identifier().toString().equals(navigation.dimension());
					BlockPos target = BlockPos.of(navigation.position());
					dx = boundedDelta(target.getX() - playerPos.getX());
					dz = boundedDelta(target.getZ() - playerPos.getZ());
					targetY = target.getY();
				}
				case HOME, PORTAL -> {
					kind = tool == TerminalTool.HOME ? TerminalNavigationPayload.HOME : TerminalNavigationPayload.PORTAL;
					TerminalToolService.Location target = TerminalToolService.guidanceLocation(player, tag, tool);
					located = target.known();
					sameDimension = target.dimension().equals(player.level().dimension().identifier().toString());
					dx = boundedDelta(target.position().getX() - playerPos.getX());
					dz = boundedDelta(target.position().getZ() - playerPos.getZ());
					targetY = target.position().getY();
				}
				case STRONGHOLD -> {
					kind = TerminalNavigationPayload.STRONGHOLD;
					TerminalToolService.StrongholdEstimate estimate = TerminalToolService.strongholdEstimate(player, tag);
					located = estimate.known();
					sameDimension = estimate.sameDimension();
					dx = estimate.dx();
					dz = estimate.dz();
				}
				case WEATHER -> { }
			}
		}
		boolean disabled = TerminalToolService.toolsDisabled(tag, player.level().getGameTime());
		boolean navigable = TerminalNavigationMath.navigable(kind, disabled, located, sameDimension);
		return new TerminalNavigationPayload(
				TerminalNavigationPayload.CURRENT_PROTOCOL_VERSION,
				kind,
				located,
				navigable,
				navigable ? dx : 0,
				navigable ? dz : 0,
				located ? targetY : 0,
				player.getYRot());
	}

	public static int navigationSyncTicks() {
		return NAVIGATION_SYNC_TICKS;
	}

	private static InteractionHand decodeHand(int hand) {
		return switch (hand) {
			case 0 -> InteractionHand.MAIN_HAND;
			case 1 -> InteractionHand.OFF_HAND;
			default -> null;
		};
	}

	private static void remember(UUID id, ViewState view) {
		REMEMBERED_MODES.put(id, view.mode);
		REMEMBERED_TUNING.put(id, view.tuning);
	}

	private static int nonNegative(int value) {
		return Math.max(0, value);
	}

	private static int boundedDelta(int value) {
		return Math.clamp(value, -30_000_000, 30_000_000);
	}

	private static int targetKind(String value) {
		return switch (value) {
			case "iron" -> TerminalNavigationPayload.IRON;
			case "redstone" -> TerminalNavigationPayload.REDSTONE;
			case "diamond" -> TerminalNavigationPayload.DIAMOND;
			case "structure_fragment" -> TerminalNavigationPayload.UNSTABLE_SIGNAL;
			default -> TerminalNavigationPayload.NONE;
		};
	}

	private static int structureNavigationKind(TerminalStructureTarget target) {
		return switch (target) {
			case VILLAGE -> TerminalNavigationPayload.VILLAGE;
			case RUINED_PORTAL -> TerminalNavigationPayload.RUINED_PORTAL;
			case MINESHAFT -> TerminalNavigationPayload.MINESHAFT;
			case TRIAL_CHAMBERS -> TerminalNavigationPayload.TRIAL_CHAMBERS;
			case FORTRESS -> TerminalNavigationPayload.FORTRESS;
			case BASTION -> TerminalNavigationPayload.BASTION;
			case NONE -> TerminalNavigationPayload.NONE;
		};
	}

	private static int personality(String value) {
		return switch (value) {
			case "cautious" -> 0;
			case "direct" -> 1;
			case "wry" -> 2;
			default -> 3;
		};
	}

	private static int capabilityMask(String value) {
		int mask = 0;
		if (value.contains("identity_continuity")) mask |= 1;
		if (value.contains("fracture_resonance")) mask |= 2;
		if (value.contains("private_differentiation")) mask |= 4;
		if (value.contains("body_mapping")) mask |= 8;
		if (value.contains("behavior_prediction")) mask |= 16;
		return mask;
	}

	private static int ending(String value) {
		return switch (value) {
			case "active" -> 1;
			case "undiscovered_truth" -> 2;
			case "prevention_failed" -> 3;
			case "prevention_succeeded" -> 4;
			default -> 0;
		};
	}

	private static int evidenceValue(String evidence, String id, boolean numeric) {
		for (String entry : evidence.split(";")) {
			String prefix = id + "=";
			if (!entry.startsWith(prefix)) continue;
			String value = entry.substring(prefix.length());
			if (numeric) {
				try {
					return Math.clamp(Integer.parseInt(value) - 1, 0, 3);
				} catch (NumberFormatException ignored) {
					return -1;
				}
			}
			return switch (value) {
				case "north" -> 0;
				case "east" -> 1;
				case "south" -> 2;
				case "west" -> 3;
				default -> -1;
			};
		}
		return -1;
	}

	public static boolean isOpen(ServerPlayer player) {
		return OPEN_VIEWS.containsKey(player.getUUID());
	}

	public static void refresh(ServerPlayer player) {
		ViewState view = OPEN_VIEWS.get(player.getUUID());
		if (view != null) sendSnapshot(player, view);
	}

	public static void synchronizeProjection(ServerPlayer player) {
		synchronizeProjection(player, FrequencyWorldData.get(player.level().getServer()));
	}

	private static void synchronizeProjection(ServerPlayer player, FrequencyWorldData data) {
		CompoundTag authoritative = data.terminalRecord(player.getUUID()).orElse(null);
		if (authoritative != null) {
			boolean changed = false;
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (data.isValidTerminal(stack, player.getUUID())) changed |= TerminalData.applyProjection(stack, authoritative);
			}
			if (changed) player.getInventory().setChanged();
		}
	}

	public static int rememberedMode(UUID playerId) {
		return REMEMBERED_MODES.getOrDefault(playerId, TerminalControlPolicy.Mode.SIGNAL.ordinal());
	}

	public static int rememberedTuning(UUID playerId) {
		return REMEMBERED_TUNING.getOrDefault(playerId, TerminalControlPolicy.DEFAULT_TUNING);
	}

	private static void applyTuning(ServerPlayer player, ViewState view, int value) {
		view.tuning = value;
		REMEMBERED_TUNING.put(player.getUUID(), view.tuning);
	}

	private static boolean receiverAvailable(ServerPlayer player) {
		CompoundTag record = FrequencyWorldData.get(player.level().getServer())
				.terminalRecord(player.getUUID()).orElse(null);
		return record != null && record.getIntOr(TerminalData.BAND_STAGE, 0) > 0
				&& !TerminalToolService.toolsDisabled(record, player.level().getGameTime())
				&& FragmentInvestigationService.nearby(player).isPresent();
	}

	private static void advanceNearbyReceiver(ServerPlayer player, ViewState view, long now) {
		var nearby = FragmentInvestigationService.nearby(player).orElse(null);
		if (view.selectedTool != TerminalTool.NAVIGATION.slot() || nearby == null || !receiverAvailable(player)
				|| !TerminalControlPolicy.receiverLocked(view.tuning, nearby.tuning())) {
			resetReceiver(view, now);
			return;
		}
		if (!nearby.key().equals(view.fragmentCandidateKey)) {
			view.fragmentCandidateKey = nearby.key();
			view.fragmentLockedSinceTick = now;
			return;
		}
		if (now - view.fragmentLockedSinceTick >= 20L
				&& FragmentInvestigationService.completeNearby(player, view.tuning)) resetReceiver(view, now);
	}

	private static int receiverLockTicks(ServerPlayer player, ViewState view, long now) {
		var nearby = FragmentInvestigationService.nearby(player).orElse(null);
		if (view.selectedTool != TerminalTool.NAVIGATION.slot() || nearby == null
				|| !nearby.key().equals(view.fragmentCandidateKey)
				|| !TerminalControlPolicy.receiverLocked(view.tuning, nearby.tuning())) return 0;
		return (int) Math.clamp(now - view.fragmentLockedSinceTick, 0L, 20L);
	}

	private static void resetReceiver(ViewState view, long now) {
		view.fragmentCandidateKey = "";
		view.fragmentLockedSinceTick = now;
	}

	private static final class ViewState {
		private final InteractionHand hand;
		private int mode;
		private int tuning;
		private long nextSyncTick;
		private long nextNavigationSyncTick;
		private String fragmentCandidateKey;
		private long fragmentLockedSinceTick;
		private int selectedTool;

		private ViewState(InteractionHand hand, int mode, int tuning, long nextSyncTick, long nextNavigationSyncTick,
				String fragmentCandidateKey, long fragmentLockedSinceTick, int selectedTool) {
			this.hand = hand;
			this.mode = mode;
			this.tuning = tuning;
			this.nextSyncTick = nextSyncTick;
			this.nextNavigationSyncTick = nextNavigationSyncTick;
			this.fragmentCandidateKey = fragmentCandidateKey;
			this.fragmentLockedSinceTick = fragmentLockedSinceTick;
			this.selectedTool = selectedTool;
		}
	}
}
