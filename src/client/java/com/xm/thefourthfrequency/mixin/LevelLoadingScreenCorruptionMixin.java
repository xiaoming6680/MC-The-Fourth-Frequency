package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import com.xm.thefourthfrequency.client_ui.AlphaLoadTimeline;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
	@Unique private static final Identifier THEFOURTHFREQUENCY$DIRT =
			Identifier.withDefaultNamespace("textures/block/dirt.png");
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
		int failureAge = thefourthfrequency$screenTicks - AlphaLoadTimeline.FAILURE_TICK;
		if (failureAge >= 0 && failureAge <= 126 && failureAge % 9 == 0) {
			float pitch = 0.46F + Math.floorMod(failureAge / 9, 4) * 0.08F;
			Minecraft client = Minecraft.getInstance();
			client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TERMINAL_FAULT, pitch, 0.52F));
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void thefourthfrequency$holdVanillaProgressAtHalf(CallbackInfo callback) {
		if (!thefourthfrequency$corruptionClaimed
				|| !AlphaLoadSessionController.shouldCorruptLoadingScreen()
				|| AlphaLoadTimeline.legacyRecoveryFrame(thefourthfrequency$screenTicks)) return;
		smoothedProgress = AlphaLoadTimeline.initialNormalFrame(thefourthfrequency$screenTicks)
				? AlphaLoadTimeline.initialNormalProgress(thefourthfrequency$screenTicks) : 0.5F;
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;isLevelReady()Z"))
	private boolean thefourthfrequency$holdForBoundedFailure(LevelLoadTracker tracker) {
		return tracker.isLevelReady()
				&& (!thefourthfrequency$shouldCorrupt()
				|| AlphaLoadSessionController.canCloseLoadingScreen(thefourthfrequency$screenTicks));
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
		if (thefourthfrequency$corruptionClaimed
				&& !AlphaLoadSessionController.shouldCorruptLoadingScreen()) {
			// The claimed first screen may receive one last render after onClose; expose the world, not a legacy flash.
			callback.cancel();
			return;
		}
		boolean initialNormalPrelude = thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& AlphaLoadTimeline.initialNormalFrame(thefourthfrequency$screenTicks);
		boolean recoveringFromCorruption = thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& AlphaLoadTimeline.legacyRecoveryFrame(thefourthfrequency$screenTicks);
		boolean activeCorruption = thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& !initialNormalPrelude && !recoveringFromCorruption;
		boolean subsequentLegacy = !thefourthfrequency$corruptionClaimed
				&& AlphaLoadSessionController.shouldRenderLegacyLoadingScreen();
		if (initialNormalPrelude || activeCorruption) return;
		if (!initialNormalPrelude && !activeCorruption && !recoveringFromCorruption
				&& !subsequentLegacy) return;
		Minecraft client = Minecraft.getInstance();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		for (int y = 0; y < height; y += 32) {
			for (int x = 0; x < width; x += 32) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, THEFOURTHFREQUENCY$DIRT,
						x, y, 0.0F, 0.0F, 32, 32, 16, 16, 16, 16);
			}
		}
		graphics.fill(0, 0, width, height, 0x52000000);
		int centerX = width / 2;
		int centerY = height / 2;
		graphics.drawCenteredString(client.font, Component.literal("生成世界中"),
				centerX, centerY - 17, 0xFFFFFFFF);
		graphics.drawCenteredString(client.font, Component.literal("生成地形中"),
				centerX, centerY + 2, 0xFFFFFFFF);

		float target = loadTracker.hasProgress() ? loadTracker.serverProgress()
				: loadTracker.isLevelReady() ? 1.0F : 0.08F;
		thefourthfrequency$legacyProgress += (Math.clamp(target, 0.0F, 1.0F)
				- thefourthfrequency$legacyProgress) * 0.18F;
		int barWidth = Math.min(120, Math.max(40, width - 40));
		int barLeft = centerX - barWidth / 2;
		int barY = centerY + 16;
		graphics.fill(barLeft, barY, barLeft + barWidth, barY + 3, 0xFF808080);
		graphics.fill(barLeft, barY,
				barLeft + Math.round(barWidth * thefourthfrequency$legacyProgress), barY + 3,
				0xFF80FF80);

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

	@Inject(method = "render", at = @At("TAIL"))
	private void thefourthfrequency$renderFailureOverVanillaPage(GuiGraphics graphics, int mouseX,
			int mouseY, float partialTick, CallbackInfo callback) {
		if (thefourthfrequency$corruptionClaimed
				&& thefourthfrequency$shouldCorrupt()
				&& !AlphaLoadTimeline.initialNormalFrame(thefourthfrequency$screenTicks)
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
		String suffix = Component.translatable(thefourthfrequency$screenTicks < AlphaLoadTimeline.FAILURE_TICK
				? "screen.thefourthfrequency.alpha_loading.progress"
				: "screen.thefourthfrequency.alpha_loading.failed").getString();
		String failedLine = prefix + Component.translatable(
				"screen.thefourthfrequency.alpha_loading.failed").getString();
		int lineWidth = font.width(failedLine);

		int startX = centerX - (font.width(prefix) + font.width(suffix)) / 2;
		graphics.drawString(font, prefix, startX, labelY, 0xFFFFFFFF, false);
		int suffixColor = thefourthfrequency$screenTicks < AlphaLoadTimeline.FAILURE_TICK
				&& ((motionTick / 2) & 1) == 0 ? 0xFFF0F0F0 : 0xFFFF2424;
		graphics.drawString(font, suffix, startX + font.width(prefix), labelY, suffixColor, false);

		int copies = AlphaLoadTimeline.copiedFailureLines(thefourthfrequency$screenTicks);
		for (int copy = 0; copy < copies; copy++) {
			int y = labelY + 7 + copy * 7;
			if (y >= graphics.guiHeight() - 8) break;
			int jitter = Math.floorMod(copy * 31, 5) - 2;
			int alpha = Math.max(48, 224 - copy * 13);
			int color = alpha << 24 | 0x00FF1818;
			graphics.drawCenteredString(font, failedLine, centerX + jitter, y, color);
		}
		int smallCopies = AlphaLoadTimeline.smallFailureCopies(thefourthfrequency$screenTicks);
		if (smallCopies > 0) {
			int width = graphics.guiWidth();
			int height = graphics.guiHeight();
			for (int index = 0; index < smallCopies; index++) {
				int seed = thefourthfrequency$chaos(index * 0x45D9F3B + 0x27D4EB2D);
				int targetX = Math.floorMod(thefourthfrequency$chaos(seed ^ 0x68BC21EB),
						width + lineWidth * 2) - lineWidth;
				int targetY = Math.floorMod(thefourthfrequency$chaos(seed ^ 0x02E5BE93),
						height + font.lineHeight * 4) - font.lineHeight * 2;
				int alpha = 62 + Math.floorMod(seed >>> 7, 178);
				int red = 198 + Math.floorMod(seed >>> 13, 58);
				graphics.drawCenteredString(font, failedLine, targetX, targetY,
						alpha << 24 | red << 16 | 0x001010);
			}
			int largeCopies = AlphaLoadTimeline.largeFailureCopies(thefourthfrequency$screenTicks);
			for (int index = 0; index < largeCopies; index++) {
				int seed = thefourthfrequency$chaos(index * 0x119DE1F3 + 0x6D2B79F5);
				int targetX = Math.floorMod(thefourthfrequency$chaos(seed ^ 0x3C6EF372),
						width + lineWidth * 3) - lineWidth;
				int targetY = Math.floorMod(thefourthfrequency$chaos(seed ^ 0x1BF5A9D7),
						height + font.lineHeight * 6) - font.lineHeight * 3;
				float scale = 1.85F + Math.floorMod(seed >>> 9, 5) * 0.36F;
				int alpha = 92 + Math.floorMod(seed >>> 4, 148);
				int red = 210 + Math.floorMod(seed >>> 12, 46);
				graphics.pose().pushMatrix();
				graphics.pose().translate(targetX, targetY);
				graphics.pose().scale(scale, scale);
				graphics.drawCenteredString(font, failedLine, 0, 0,
						alpha << 24 | red << 16 | 0x001010);
				graphics.pose().popMatrix();
			}
			thefourthfrequency$viewportFlooded |= largeCopies
					>= AlphaLoadTimeline.MAX_LARGE_FAILURE_COPIES;
			AlphaLoadSessionController.recordViewportFlooded(thefourthfrequency$viewportFlooded);
			if (!thefourthfrequency$testScreenshotRequested
					&& thefourthfrequency$isClientGameTest()
					&& thefourthfrequency$screenTicks >= AlphaLoadTimeline.FREEZE_START_TICK + 10) {
				thefourthfrequency$testScreenshotRequested = true;
				AlphaLoadSessionController.requestRegressionScreenshot("alpha-loading-corruption.png");
			}
		}
	}

	@Unique
	private static int thefourthfrequency$chaos(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
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
		if (thefourthfrequency$corruptionClaimed) {
			AlphaLoadSessionController.loadingScreenClosed(thefourthfrequency$screenTicks,
					thefourthfrequency$viewportFlooded);
		}
	}
}
