package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.narrative.NarrativeFileCatalog;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.networking.DebugActionPayload;
import com.xm.thefourthfrequency.networking.DebugStatusPayload;
import com.xm.thefourthfrequency.terminal.AmbientAnomalyService;
import com.xm.thefourthfrequency.terminal.AnomalyCatalog;
import com.xm.thefourthfrequency.terminal.DebugNames;
import com.xm.thefourthfrequency.terminal.TerminalAnomalyLogService;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;


public final class DebugPanelService {
	private DebugPanelService() { }

	public static boolean setEnabled(ServerPlayer player, boolean enabled) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return false;
		data.updateTerminalRecord(player.getUUID(), tag -> tag.putBoolean(TerminalData.DEBUG_ENABLED, enabled));
		if (!enabled) ServerPlayNetworking.send(player, denied("调试面板已关闭"));
		return true;
	}

	public static void open(ServerPlayer player) {
		if (!enabled(player)) {
			ServerPlayNetworking.send(player, denied("请先使用 /tff debug true 开启个人调试面板"));
			return;
		}
		sendStatus(player, "状态已刷新");
	}

	public static void handle(ServerPlayer player, DebugActionPayload payload) {
		if (!enabled(player)) {
			ServerPlayNetworking.send(player, denied("调试权限已关闭"));
			return;
		}
		String message;
		try {
			message = apply(player, payload.action(), payload.target(), payload.value());
		} catch (IllegalArgumentException exception) {
			message = "操作被拒绝：" + exception.getMessage();
		}
		TerminalRuntimeService.synchronizeProjection(player);
		sendStatus(player, message);
	}

	private static String apply(ServerPlayer player, String action, String target, int value) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElseThrow(() -> new IllegalArgumentException("没有终端记录"));
		return switch (action) {
			case "refresh" -> "状态已刷新";
			case "prelude_ready" -> {
				data.updateTerminalRecord(player.getUUID(), tag -> {
					tag.putString(TerminalData.PROOF_ROUTE, "debug");
					tag.putBoolean(TerminalData.NIGHT_ENTERED, true);
					tag.putBoolean(TerminalData.NIGHT_WITNESSED, true);
					tag.putInt(TerminalData.PRELUDE_ANOMALY_MASK, 0b11);
					tag.putBoolean(TerminalData.WATCHER_WITNESSED, true);
					tag.putBoolean(TerminalData.BOUND, true);
					tag.putBoolean(TerminalData.SECOND_CACHE_UNLOCKED, true);
					tag.putInt(TerminalData.PLOT_STAGE, Math.max(2, tag.getIntOr(TerminalData.PLOT_STAGE, 1)));
					tag.putInt(TerminalData.BAND_STAGE, Math.max(1, tag.getIntOr(TerminalData.BAND_STAGE, 0)));
				});
				yield "前期准备已完成，异常信号层已经显现";
			}
			case "progress_next" -> {
				int next = Math.clamp(record.getIntOr(TerminalData.PLOT_STAGE, 1) + 1, 1, 5);
				data.updateTerminalRecord(player.getUUID(), tag -> tag.putInt(TerminalData.PLOT_STAGE, next));
				yield "主线阶段已推进到 " + next;
			}
			case "progress_reset" -> { resetProgress(player, data); yield "个人主线状态已重置"; }
			case "band" -> {
				int stage = Math.clamp(value, 0, 3);
				data.updateTerminalRecord(player.getUUID(), tag -> tag.putInt(TerminalData.BAND_STAGE, stage));
				yield "异常信号状态已设为“" + DebugNames.bandStage(stage) + "”";
			}
			case "milestone" -> {
				if (value < 0 || value >= SurvivalMilestone.values().length)
					throw new IllegalArgumentException("未知生存节点");
				SurvivalMilestone milestone = SurvivalMilestone.values()[value];
				data.updateTerminalRecord(player.getUUID(), tag -> tag.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
						tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0) | milestone.mask()));
				yield "已完成生存节点：" + milestone.name().toLowerCase(java.util.Locale.ROOT);
			}
			case "anomaly" -> {
				if (!AnomalyCatalog.contains(target)) throw new IllegalArgumentException("未知异象类型");
				AmbientAnomalyService.TriggerResult result = AmbientAnomalyService.triggerDetailed(player, target, value > 0);
				if (!result.started()) throw new IllegalArgumentException(anomalyFailureReason(player, target, result.failure()));
				yield "已触发异象：" + DebugNames.anomaly(target) + (value > 0 ? "（最强）" : "");
			}
			case "anomaly_stop" -> { AmbientAnomalyService.stop(player); yield "已停止当前异象并还原临时影响"; }
			case "anomaly_resume" -> { AmbientAnomalyService.resume(player); yield "已恢复自动触发异象"; }
			case "watcher_spawn" -> {
				if (!WatcherService.debugSpawn(player)) throw new IllegalArgumentException("附近没有合适的生成位置");
				yield "暗处人影已在玩家视野外生成";
			}
			case "file_unlock" -> {
				NarrativeFileCatalog.require(target);
				long now = player.level().getGameTime();
				data.updateTerminalRecord(player.getUUID(), tag -> TerminalFileState.setUnlocked(tag, target, true, now, player.level().getDayTime()));
				yield "已解锁文件：" + DebugNames.file(target);
			}
			case "files_lock" -> {
				data.updateTerminalRecord(player.getUUID(), tag -> {
					tag.put(TerminalData.FILE_STATES, new ListTag());
				});
				yield "文件进度已重置，等待对应阶段或建筑触发";
			}
			case "decay" -> {
				int stage = Math.clamp(value, 0, 5);
				data.updateNarrativeState(tag -> tag.putInt("decay_stage_override", stage));
				yield "异象效果等级已设为 " + stage;
			}
			case "decay_auto" -> {
				data.updateNarrativeState(tag -> tag.remove("decay_stage_override"));
				yield "异象效果已恢复为自动控制";
			}
			default -> throw new IllegalArgumentException("未知调试动作");
		};
	}

	private static void resetProgress(ServerPlayer player, FrequencyWorldData data) {
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putBoolean(TerminalData.BOUND, false); tag.putInt(TerminalData.BAND_STAGE, 0); tag.putInt(TerminalData.PLOT_STAGE, 1);
			tag.putBoolean(TerminalData.SECOND_CACHE_UNLOCKED, false);
			tag.putBoolean(TerminalData.LOCAL_FILE_UNLOCKED, false); tag.putBoolean(TerminalData.RIFT_OBSERVED, false);
			tag.putBoolean(TerminalData.CONTINUITY_LEARNED, false); tag.putBoolean(TerminalData.NETHER_RIFT_OBSERVED, false);
			tag.putInt(TerminalData.BODY_PROGRESS, 0); tag.putInt(TerminalData.BODY_STAGE, 0);
			tag.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, 0); tag.putInt(TerminalData.BREACH_MASK, 0);
			tag.putInt(TerminalData.SIGNATURE_SCENE_MASK, 0); tag.putLong(TerminalData.TOOLS_DISABLED_UNTIL, 0L);
			tag.putBoolean(TerminalData.TRUTH_READ, false); tag.putBoolean(TerminalData.PORTAL_ROOM_FOUND, false);
			tag.putLong(TerminalData.PORTAL_ROOM_POSITION, 0L); tag.putString(TerminalData.PORTAL_ROOM_DIMENSION, "");
			tag.putBoolean(TerminalData.NIGHT_ENTERED, false);
			tag.putBoolean(TerminalData.NIGHT_WITNESSED, false); tag.putInt(TerminalData.PRELUDE_ANOMALY_MASK, 0);
			 tag.putBoolean(TerminalData.WATCHER_WITNESSED, false); tag.putString(TerminalData.PROOF_ROUTE, "none");
			tag.putInt(TerminalData.ANOMALY_TIER, 0); tag.putInt(TerminalData.ANOMALY_STORY_CEILING, 0);
			tag.putLong(TerminalData.ANOMALY_TIER_ONLINE_TICKS, 0L); tag.putInt(TerminalData.ANOMALY_HEAT, 0);
			tag.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, 0L); tag.putBoolean(TerminalData.ANOMALIES_SUSPENDED, false);
			tag.put(TerminalData.FILE_STATES, new ListTag());
		});
	}

	private static String anomalyFailureReason(ServerPlayer player, String id,
			AmbientAnomalyService.TriggerFailure failure) {
		return switch (failure) {
			case ALREADY_ACTIVE -> {
				var active = com.xm.thefourthfrequency.terminal.AnomalyRuntimeService.active(player);
				yield active == null ? "另一个异象正在启动，请稍后再试"
						: "已有异象正在发生：" + DebugNames.anomaly(active.anomalyId());
			}
			case PRECONDITION_UNMET -> switch (id) {
				case "phantom_echo" -> "附近不是足够封闭、低天光的洞穴环境";
				case "light_dropout" -> "玩家周围 16 格内没有已加载的发光方块";
				case "surface_fracture" -> "玩家前方或侧前方没有可作用的实体方块";
				case "action_echo" -> "进入世界尚未满 3 秒，无法取得动作历史";
				default -> "当前玩家状态或环境不满足该异象的启动条件";
			};
			case EFFECT_UNAVAILABLE -> switch (id) {
				case "dark_watcher" -> "附近无法放置观察者，或已有观察者正在注视玩家";
				case "door_cascade" -> "玩家周围 20 格内不足 2 扇可作用的普通门";
				case "experience_gap" -> "附近找不到至少 12 格长的安全连续移动路径";
				default -> "该异象需要的临时实体或世界效果无法建立";
			};
			case RUNTIME_REJECTED -> "异象运行时拒绝了启动请求，请先停止当前异象后重试";
			case NONE -> "未知启动失败";
		};
	}
	private static boolean enabled(ServerPlayer player) {
		return FrequencyWorldData.get(player.level().getServer()).terminalRecord(player.getUUID())
				.map(tag -> tag.getBooleanOr(TerminalData.DEBUG_ENABLED, false)).orElse(false);
	}

	private static void sendStatus(ServerPlayer player, String message) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(new CompoundTag());
		var files = TerminalFileState.states(record);
		long now = player.level().getGameTime();
		ServerPlayNetworking.send(player, new DebugStatusPayload(DebugStatusPayload.CURRENT_PROTOCOL_VERSION, true,
				player.getGameProfile().name(), record.getIntOr(TerminalData.PLOT_STAGE, 1),
				record.getIntOr(TerminalData.BAND_STAGE, 0), record.getBooleanOr(TerminalData.BOUND, false),
				files.size(), (int) files.stream().filter(TerminalFileState.State::unlocked).count(),
				record.getIntOr(TerminalData.BODY_PROGRESS, 0), record.getIntOr(TerminalData.BODY_STAGE, 0),
				WorldDecayService.stage(data, record),
				!data.narrativeState().contains("decay_stage_override"),
				record.getIntOr(TerminalData.ANOMALY_TIER, 0), record.getIntOr(TerminalData.ANOMALY_STORY_CEILING, 0),
				record.getIntOr(TerminalData.ANOMALY_HEAT, 0), record.getStringOr(TerminalData.ACTIVE_ANOMALY_ID, "none"),
				seconds(record.getLongOr(TerminalData.ACTIVE_ANOMALY_UNTIL, 0L) - now),
				seconds(record.getLongOr(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, 0L) - now),
				seconds(record.getLongOr(TerminalData.NEXT_STRONG_ANOMALY_TICK, 0L) - now),
				seconds(record.getLongOr(TerminalData.NEXT_COMPOSITE_ANOMALY_TICK, 0L) - now),
				record.getBooleanOr(TerminalData.ANOMALIES_SUSPENDED, false), message));
	}

	private static DebugStatusPayload denied(String message) {
		return new DebugStatusPayload(DebugStatusPayload.CURRENT_PROTOCOL_VERSION, false, "", 0, 0, false,
				0, 0, 0, 0, 0, true,
				0, 0, 0, "none", 0, 0, 0, 0, false, message);
	}

	private static int seconds(long ticks) { return (int) Math.clamp((ticks + 19L) / 20L, 0L, Integer.MAX_VALUE); }
}
