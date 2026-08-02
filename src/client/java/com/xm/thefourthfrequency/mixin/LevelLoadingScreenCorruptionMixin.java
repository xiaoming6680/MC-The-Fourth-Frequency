package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.AlphaCorruptionAudio;
import com.xm.thefourthfrequency.client_ui.AlphaCorruptionRenderer;
import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import com.xm.thefourthfrequency.client_ui.AlphaLoadTimeline;
import com.xm.thefourthfrequency.client_ui.PersistentAlphaLoadingStyle;
import com.xm.thefourthfrequency.client_ui.PursuitPresentationClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenCorruptionMixin {
	@Shadow private LevelLoadTracker loadTracker;
	@Shadow private float smoothedProgress;
	@Unique private int thefourthfrequency$screenTicks;
	@Unique private boolean thefourthfrequency$worldEntryReason;
	@Unique private boolean thefourthfrequency$corruptionClaimed;
	@Unique private boolean thefourthfrequency$viewportFlooded;
	@Unique private boolean thefourthfrequency$testScreenshotRequested;
	@Unique private boolean thefourthfrequency$legacyFrameRecorded;
	@Unique private boolean thefourthfrequency$legacyScreenshotRequested;
	@Unique private float thefourthfrequency$legacyProgress = 0.04F;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void thefourthfrequency$rememberEntryReason(LevelLoadTracker tracker,
			LevelLoadingScreen.Reason reason, CallbackInfo callback) {
		thefourthfrequency$worldEntryReason = reason == LevelLoadingScreen.Reason.OTHER;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void thefourthfrequency$advanceFailure(CallbackInfo callback) {
		if (thefourthfrequency$worldEntryReason && !thefourthfrequency$corruptionClaimed
				&& AlphaLoadSessionController.claimInitialCorruptionScreen()) {
			thefourthfrequency$corruptionClaimed = true;
			thefourthfrequency$screenTicks = 0;
		}
		thefourthfrequency$screenTicks++;
		if (!thefourthfrequency$shouldCorrupt()) return;
		AlphaLoadSessionController.loadingScreenTick(thefourthfrequency$screenTicks);
		Minecraft client = Minecraft.getInstance();
		AlphaCorruptionAudio.tick(client, thefourthfrequency$screenTicks);
		if (thefourthfrequency$screenTicks == AlphaLoadTimeline.GLITCH_START_TICK) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(
					ModSounds.ALPHA_CORRUPTION_WARNING, 1.0F, 0.62F));
		} else if (thefourthfrequency$screenTicks == AlphaLoadTimeline.FLOOD_START_TICK) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(
					ModSounds.ALPHA_CORRUPTION_COLLAPSE, 1.0F, 0.96F));
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void thefourthfrequency$holdVanillaProgressAtHalf(CallbackInfo callback) {
		if (!thefourthfrequency$worldEntryReason) return;
		if (!thefourthfrequency$corruptionClaimed) {
			if (AlphaLoadSessionController.shouldPrepareInitialCorruptionScreen()) {
				smoothedProgress = Math.min(smoothedProgress, 0.5F);
			}
			return;
		}
		if (!AlphaLoadSessionController.shouldCorruptLoadingScreen()
				|| AlphaLoadTimeline.legacyRecoveryFrame(thefourthfrequency$screenTicks)) return;
		smoothedProgress = 0.5F;
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;isLevelReady()Z"))
	private boolean thefourthfrequency$holdForBoundedFailure(LevelLoadTracker tracker) {
		if (thefourthfrequency$worldEntryReason && !thefourthfrequency$corruptionClaimed
				&& AlphaLoadSessionController.shouldPrepareInitialCorruptionScreen()) return false;
		return tracker.isLevelReady()
				&& (!thefourthfrequency$shouldCorrupt()
				|| AlphaLoadSessionController.canCloseLoadingScreen(thefourthfrequency$screenTicks));
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$renderStableFirstEntryBackground(GuiGraphics graphics,
			int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
		boolean preparing = thefourthfrequency$worldEntryReason
				&& !thefourthfrequency$corruptionClaimed
				&& AlphaLoadSessionController.shouldPrepareInitialCorruptionScreen();
		if (!preparing && !thefourthfrequency$shouldCorrupt()) return;
		thefourthfrequency$drawWorldLoadingDirtBackground(graphics);
		callback.cancel();
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void thefourthfrequency$coverHalfProgressHandoffBeforeVanillaRender(
			GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo callback) {
		boolean preparing = thefourthfrequency$worldEntryReason
				&& !thefourthfrequency$corruptionClaimed
				&& AlphaLoadSessionController.shouldPrepareInitialCorruptionScreen();
		if (preparing || thefourthfrequency$shouldCorrupt()) {
			thefourthfrequency$drawWorldLoadingDirtBackground(graphics);
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void thefourthfrequency$hideVanillaLoadingText(GuiGraphics graphics, Font font,
			Component text, int centerX, int y, int color) {
		if (!thefourthfrequency$shouldCorrupt()
				|| AlphaLoadTimeline.initialNormalFrame(thefourthfrequency$screenTicks)) {
			graphics.drawCenteredString(font, text, centerX, y, color);
		}
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$renderLegacyLoadingScreen(GuiGraphics graphics, int mouseX,
			int mouseY, float partialTick, CallbackInfo callback) {
		if (PursuitPresentationClient.shouldCoverLoadingScreen()) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
			callback.cancel();
			return;
		}
		if (thefourthfrequency$corruptionClaimed
				&& !AlphaLoadSessionController.shouldCorruptLoadingScreen()) {
			// Keep the final loading frame covered until the world produces its first frame.
			thefourthfrequency$drawWorldLoadingDirtBackground(graphics);
			callback.cancel();
			return;
		}
		boolean initialNormalPrelude = thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& AlphaLoadTimeline.initialNormalFrame(thefourthfrequency$screenTicks);
		boolean recoveringFromCorruption = thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& AlphaLoadTimeline.legacyRecoveryFrame(thefourthfrequency$screenTicks);
		boolean blackout = thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& AlphaLoadTimeline.blackoutFrame(thefourthfrequency$screenTicks);
		boolean activeCorruption = thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& !initialNormalPrelude && !blackout && !recoveringFromCorruption;
		boolean subsequentLegacy = !thefourthfrequency$corruptionClaimed
				&& AlphaLoadSessionController.shouldRenderLegacyLoadingScreen();
		if (blackout) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
			AlphaCorruptionRenderer.drawDeadAir(graphics, thefourthfrequency$screenTicks);
			callback.cancel();
			return;
		}
		if (initialNormalPrelude || activeCorruption) return;
		if (!initialNormalPrelude && !activeCorruption && !recoveringFromCorruption
				&& !subsequentLegacy) return;
		Minecraft client = Minecraft.getInstance();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		thefourthfrequency$drawWorldLoadingDirtBackground(graphics);
		int centerX = width / 2;
		int centerY = height / 2;
		graphics.drawCenteredString(client.font, Component.translatable(
				"screen.thefourthfrequency.legacy_loading.generating_world"),
				centerX, centerY - 17, 0xFFFFFFFF);
		graphics.drawCenteredString(client.font, Component.translatable(
				"screen.thefourthfrequency.legacy_loading.generating_terrain"),
				centerX, centerY + 2, 0xFFFFFFFF);

		float target = loadTracker.hasProgress() ? loadTracker.serverProgress()
				: loadTracker.isLevelReady() ? 1.0F : 0.08F;
		thefourthfrequency$legacyProgress += (Math.clamp(target, 0.0F, 1.0F)
				- thefourthfrequency$legacyProgress) * 0.18F;
		int barWidth = Math.min(120, Math.max(40, width - 40));
		int barLeft = centerX - barWidth / 2;
		int barY = centerY + 16;
		graphics.fill(barLeft, barY, barLeft + barWidth, barY + 2, 0xFF808080);
		graphics.fill(barLeft, barY,
				barLeft + Math.round(barWidth * thefourthfrequency$legacyProgress), barY + 2,
				0xFF80FF80);

		if (recoveringFromCorruption) {
			AlphaCorruptionRenderer.drawRecoveryLock(graphics, thefourthfrequency$screenTicks);
		}

		if (!thefourthfrequency$legacyFrameRecorded) {
			thefourthfrequency$legacyFrameRecorded = true;
			AlphaLoadSessionController.recordLegacyLoadingScreenRendered();
		}
		if (!thefourthfrequency$legacyScreenshotRequested
				&& thefourthfrequency$isClientGameTest()
				&& (!initialNormalPrelude || thefourthfrequency$screenTicks
						>= AlphaLoadTimeline.NORMAL_PROGRESS_END_TICK)) {
			thefourthfrequency$legacyScreenshotRequested = true;
			AlphaLoadSessionController.requestRegressionScreenshot("legacy-loading-normal.png");
		}
		callback.cancel();
	}

	@Unique
	private static void thefourthfrequency$drawWorldLoadingDirtBackground(GuiGraphics graphics) {
		PersistentAlphaLoadingStyle.drawWorldLoadingBackground(graphics);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void thefourthfrequency$renderFailureOverVanillaPage(GuiGraphics graphics, int mouseX,
			int mouseY, float partialTick, CallbackInfo callback) {
		if (thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& !AlphaLoadTimeline.initialNormalFrame(thefourthfrequency$screenTicks)
				&& !AlphaLoadTimeline.blackoutFrame(thefourthfrequency$screenTicks)
				&& !AlphaLoadTimeline.legacyRecoveryFrame(thefourthfrequency$screenTicks)) {
			thefourthfrequency$renderTerrainFailureContents(graphics);
		}
	}

	@Unique
	private void thefourthfrequency$renderTerrainFailureContents(GuiGraphics graphics) {
		if (!thefourthfrequency$shouldCorrupt()
				|| thefourthfrequency$screenTicks < AlphaLoadTimeline.GLITCH_START_TICK
				|| AlphaLoadTimeline.legacyRecoveryFrame(thefourthfrequency$screenTicks)) return;
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		int motionTick = AlphaLoadTimeline.failureMotionTick(thefourthfrequency$screenTicks);
		int centerX = graphics.guiWidth() / 2;
		int labelY = thefourthfrequency$labelY(graphics);
		String prefix = Component.translatable("screen.thefourthfrequency.alpha_loading.prefix").getString();
		boolean failed = thefourthfrequency$screenTicks >= AlphaLoadTimeline.FAILURE_TICK;
		String suffix = Component.translatable(!failed
				? "screen.thefourthfrequency.alpha_loading.progress"
				: "screen.thefourthfrequency.alpha_loading.failed").getString();
		String failedLine = thefourthfrequency$failedLine();
		if (AlphaLoadTimeline.fullScreenFailureWall(thefourthfrequency$screenTicks)) {
			thefourthfrequency$renderFullScreenFailureWall(graphics, font, failedLine,
					AlphaLoadTimeline.floodWipeProgress(thefourthfrequency$screenTicks));
			AlphaCorruptionRenderer.drawMediumLayers(graphics, thefourthfrequency$screenTicks);
			thefourthfrequency$viewportFlooded = true;
			AlphaLoadSessionController.recordViewportFlooded(true);
			if (!thefourthfrequency$testScreenshotRequested
					&& thefourthfrequency$isClientGameTest()
					&& thefourthfrequency$screenTicks >= AlphaLoadTimeline.FREEZE_START_TICK + 5) {
				thefourthfrequency$testScreenshotRequested = true;
				AlphaLoadSessionController.requestRegressionScreenshot("alpha-loading-corruption.png");
			}
			return;
		}

		int startX = centerX - (font.width(prefix) + font.width(suffix)) / 2;
		int tremorSeed = thefourthfrequency$chaos(motionTick / 3 + 0x5F356495);
		int tremorX = Math.floorMod(tremorSeed, 19) == 0
				? Math.floorMod(tremorSeed >>> 5, 3) - 1 : 0;
		int tremorY = Math.floorMod(tremorSeed >>> 9, 23) == 0 ? 1 : 0;
		int suffixX = startX + font.width(prefix);
		if (Math.floorMod(motionTick, 13) == 2) {
			graphics.drawString(font, failedLine, startX + 1, labelY,
					failed ? 0x4C651E22 : 0x383C3531, false);
		}
		AlphaCorruptionRenderer.drawChromaString(graphics, font, prefix, startX + tremorX,
				labelY + tremorY, failed ? 0xFFD5CEC3 : 0xFFE8E3DA, thefourthfrequency$screenTicks);
		int suffixColor = !failed
				? (Math.floorMod(motionTick, 11) == 4 ? 0xFF766C65 : 0xFFE8E3DA)
				: 0xFF9B302C;
		AlphaCorruptionRenderer.drawChromaString(graphics, font, suffix, suffixX + tremorX,
				labelY + tremorY, suffixColor, thefourthfrequency$screenTicks);

		if (AlphaLoadTimeline.observerMessageVisible(thefourthfrequency$screenTicks)) {
			String observer = Component.translatable(
					"screen.thefourthfrequency.alpha_loading.observer_detected").getString();
			int observerX = centerX + (Math.floorMod(tremorSeed >>> 12, 3) - 1);
			graphics.drawCenteredString(font, observer, observerX + 1, labelY + 29,
					0x6A321416);
			AlphaCorruptionRenderer.drawChromaCenteredString(graphics, font, observer, observerX,
					labelY + 28, 0xC9D8D0C4, thefourthfrequency$screenTicks);
		}

		int copies = AlphaLoadTimeline.copiedFailureLines(thefourthfrequency$screenTicks);
		for (int copy = 0; copy < copies; copy++) {
			int y = labelY + 8 + copy * 6;
			if (y >= graphics.guiHeight() - 8) break;
			int seed = thefourthfrequency$chaos(copy * 31 + motionTick / 4);
			int jitter = Math.floorMod(seed, 5) - 2;
			int alpha = Math.max(34, 170 - copy * 10);
			int red = 112 + Math.floorMod(seed >>> 8, 37);
			int green = 22 + Math.floorMod(seed >>> 14, 16);
			int blue = 24 + Math.floorMod(seed >>> 19, 13);
			int color = alpha << 24 | red << 16 | green << 8 | blue;
			graphics.drawCenteredString(font, failedLine, centerX + jitter, y, color);
		}
		thefourthfrequency$renderSignalDropouts(graphics, motionTick, failed);
		AlphaCorruptionRenderer.drawMediumLayers(graphics, thefourthfrequency$screenTicks);
	}

	@Unique
	private static String thefourthfrequency$failedLine() {
		return Component.translatable("screen.thefourthfrequency.alpha_loading.prefix").getString()
				+ Component.translatable("screen.thefourthfrequency.alpha_loading.failed").getString();
	}

	/**
	 * @param wipeProgress 0 to 1; the wall opens outward from the middle of the screen rather
	 *                     than replacing the picture on a single frame. Six ticks is fast enough
	 *                     to still land as a shock and slow enough that the eye reads it as the
	 *                     picture being overtaken rather than as a cut to another image.
	 */
	@Unique
	private static void thefourthfrequency$renderFullScreenFailureWall(GuiGraphics graphics,
			Font font, String failedLine, float wipeProgress) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float opening = Math.clamp(wipeProgress, 0.0F, 1.0F);
		int halfOpening = Math.round(height * 0.5F * opening);
		int wipeTop = height / 2 - halfOpening;
		int wipeBottom = height / 2 + halfOpening;
		if (wipeBottom <= wipeTop) return;
		graphics.enableScissor(0, wipeTop, width, wipeBottom);
		graphics.fill(0, wipeTop, width, wipeBottom, 0xD00B0000);

		float scale = 2.85F;
		int logicalWidth = (int) Math.ceil(width / scale);
		int logicalHeight = (int) Math.ceil(height / scale);
		String word = failedLine + " ";
		int wordWidth = Math.max(1, font.width(word));
		int repetitions = Math.max(1, logicalWidth / wordWidth + 6);
		String wallLine = word.repeat(repetitions);

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		int row = -2;
		for (int y = -font.lineHeight * 2; y < logicalHeight + font.lineHeight * 2;
				y += font.lineHeight, row++) {
			int stagger = (row & 1) == 0 ? 0 : -(wordWidth / 2);
			int x = -wordWidth * 2 + stagger;
			int red = 206 + Math.floorMod(row * 17, 42);
			int green = 8 + Math.floorMod(row * 11, 17);
			int blue = 9 + Math.floorMod(row * 7, 13);
			graphics.drawString(font, wallLine, x + 1, y + 1,
					0xB8000000 | red << 16, false);
			graphics.drawString(font, wallLine, x, y,
					0xFF000000 | red << 16 | green << 8 | blue, false);
		}
		graphics.pose().popMatrix();
		graphics.disableScissor();
		// The leading edges of the wipe stay lit for as long as they are still travelling.
		if (opening < 1.0F) {
			graphics.fill(0, wipeTop, width, wipeTop + 1, 0xB3E8DCD4);
			graphics.fill(0, wipeBottom - 1, width, wipeBottom, 0xB3E8DCD4);
		}
	}

	@Unique
	private static void thefourthfrequency$renderSignalDropouts(GuiGraphics graphics,
			int motionTick, boolean failed) {
		int bandCount = failed
				? Math.min(4, 1 + Math.max(0, motionTick - AlphaLoadTimeline.FAILURE_TICK) / 28)
				: 1;
		for (int band = 0; band < bandCount; band++) {
			int seed = thefourthfrequency$chaos((motionTick / 3) * 0x1F123BB5
					+ band * 0x6D2B79F5);
			if (!failed && Math.floorMod(seed, 4) != 0) continue;
			int bandY = Math.floorMod(seed >>> 3, Math.max(1, graphics.guiHeight()));
			int bandHeight = 1 + Math.floorMod(seed >>> 13, failed ? 4 : 2);
			int alpha = failed ? 72 + Math.floorMod(seed >>> 20, 61) : 48;
			graphics.fill(0, bandY, graphics.guiWidth(),
					Math.min(graphics.guiHeight(), bandY + bandHeight), alpha << 24);
		}
	}

	/** Same seed source the medium layers use, so one tick shakes every layer the same way. */
	@Unique
	private static int thefourthfrequency$chaos(int value) {
		return AlphaLoadTimeline.noise(value);
	}

	@Unique
	private boolean thefourthfrequency$shouldCorrupt() {
		return thefourthfrequency$corruptionClaimed
				&& AlphaLoadSessionController.shouldCorruptLoadingScreen();
	}

	@Unique
	private int thefourthfrequency$labelY(GuiGraphics graphics) {
		ChunkLoadStatusView status = loadTracker.statusView();
		return status == null ? graphics.guiHeight() / 2 - 50
				: graphics.guiHeight() / 2 - status.radius() * 2 - 27;
	}

	@Unique
	private static boolean thefourthfrequency$isClientGameTest() {
		return FabricLoader.getInstance().isModLoaded("thefourthfrequency-test");
	}

	@Inject(method = "onClose", at = @At("TAIL"))
	private void thefourthfrequency$finishInitialFailure(CallbackInfo callback) {
		// Unconditional: a bed that outlives this screen would follow the player into the world.
		AlphaCorruptionAudio.stopAll();
		if (thefourthfrequency$corruptionClaimed) {
			AlphaLoadSessionController.loadingScreenClosed(thefourthfrequency$screenTicks,
					thefourthfrequency$viewportFlooded);
		}
	}
}
