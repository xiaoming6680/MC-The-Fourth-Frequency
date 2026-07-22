package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.MenuErosionStageS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** Client-session state: leaving a world preserves the last stage; only process restart returns to BOOT. */
public final class MenuErosionState {
	public enum Stage { BOOT, EARLY, MID, LATE, RESTORED }
	public static final List<String> BOOT_SPLASHES = List.of(
			"他们不在这了",
			"你回来了,但这里已经没有人了",
			"你来晚了",
			"门后什么也没有",
			"这不是你记得的地方",
			"你又回来了?",
			"它越来越怪了...",
			"也许它是好人呢？",
			"滋滋...信号不稳定...",
			"放我出去!!!",
			"这是一场实验...吗？",
			"他们忘了关闭终端",
			"下一个是谁?",
			"“必须永久封锁这个项目!”",
			"“怎么爆炸了!?”",
			"奇怪...",
			"似乎有很多人来过这里...",
			"前方是未知区域",
			"TA没有离开",
			"你没有走错路",
			"你回来了?");
	private static final String SESSION_SPLASH = BOOT_SPLASHES.get(Math.floorMod(
			(int) (System.nanoTime() >>> 8), BOOT_SPLASHES.size()));
	private static volatile Stage stage = Stage.BOOT;
	private static boolean initialized;
	private MenuErosionState() { }
	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(MenuErosionStageS2C.TYPE, (payload, context) ->
				context.client().execute(() -> stage = Stage.values()[Math.clamp(payload.stage(), 0,
						Stage.values().length - 1)]));
	}
	public static Stage stage() { return stage; }
	public static String sessionSplash() { return SESSION_SPLASH; }
	public static void resetForReplay() { stage = Stage.BOOT; }
	public static void setForTesting(Stage value) { stage = value; }
}
