package com.xm.thefourthfrequency.terminal;

public final class DebugNames {
	private DebugNames() { }

	public static String anomaly(String id) {
		return switch (id) {
			case "none" -> "无";
			case "phantom_echo" -> "近处连续脚步／挖掘声";
			case "light_dropout" -> "附近突然变暗";
			case "peripheral_residue" -> "双手遮住视线";
			case "window_pulse" -> "游戏窗口闪动";
			case "surface_fracture" -> "墙面出现假裂纹";
			case "watcher_alignment" -> "动物同时转头";
			case "dark_watcher" -> "暗处出现发光眼睛";
			case "action_echo" -> "刚才的动作重复出现";
			case "viewpoint_separation" -> "视角离开身体";
			case "door_cascade" -> "门由远到近破碎";
			case "organ_misread" -> "物品显示成眼睛";
			case "rework_probe" -> "施工怪物靠近";
			case "experience_gap" -> "记忆空白";
			case "disconnected_base" -> "暂时认不出基地";
			case "local_rule_collapse" -> "附近材质丢失";
			case "hostile_echo" -> "听见敌对生物靠近";
			case "red_horizon" -> "红色视界";
			case "watcher_orbit" -> "围绕者环";
			case "channel_override" -> "频道接管";
			case "desktop_presence" -> "桌面在场";
			default -> "未知异象";
		};
	}

	public static String file(String id) {
		return switch (id) {
			case "maintenance_handoff" -> "维修日记";
			case "recovered_fragment" -> "维修日记补记";
			case "surface_shelter_record" -> "避难所日记";
			case "field_observation_record" -> "观测点日记";
			case "underground_mine_record" -> "矿站日记";
			case "abandoned_warehouse_record" -> "仓库日记";
			case "encrypted_witness_file" -> "前任留下的日记";
			case "correction_response_record" -> "修复装置日记";
			case "overworld_fracture_record" -> "主世界裂缝日记";
			case "continuity_report" -> "下界往返日记";
			case "body_mapping_warning" -> "入口线索日记";
			case "world_interface_entry_record" -> "世界接口入口记录";
			default -> "未知文件";
		};
	}

	public static String bandStage(int stage) {
		return switch (stage) {
			case 0 -> "尚未显现";
			case 1 -> "异常信号已侵入";
			case 2 -> "终端已经绑定";
			case 3 -> "异常信号已经终止";
			default -> "未知状态";
		};
	}
}
