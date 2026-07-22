package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.client_render.WorldInterfaceGatewayBatchRenderer;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Owns deterministic attack overlays, ambient transitions, and gateway presentation registration. */
public final class WorldInterfacePresentationController {
	private static final int AMBIENT_FADE_IN_TICKS = 20;
	private static final int AMBIENT_FADE_OUT_TICKS = 16;
	private static final long DAMAGE_FLASH_TICKS = 5L;
	private static boolean initialized;
	private static UUID trackedEncounterId;
	private static UUID trackedBossId;
	private static float lastObservedHealth = Float.NaN;
	private static long damageFlashUntil = Long.MIN_VALUE;
	private static WorldInterfaceProtocol.GatewayState audibleGatewayState =
			WorldInterfaceProtocol.GatewayState.DORMANT;
	private static WorldInterfaceProtocol.Stage ambientStage;
	private static int failureErosionBucket = -1;
	private static AmbientLoop ambientLoop;
	private static AmbientLoop retiringAmbientLoop;

	private WorldInterfacePresentationController() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		WorldInterfaceGatewayBatchRenderer.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(WorldInterfacePresentationController::tick);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> renderOverlay(graphics));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetPresentationState());
	}

	public static void resetForEnding() {
		resetPresentationState();
	}

	/** Lets the entity renderer turn authoritative virtual-health deltas into a short hit-material flash. */
	public static boolean isDamageFlashActive(UUID bossId, long gameTime) {
		return bossId != null && bossId.equals(trackedBossId) && gameTime < damageFlashUntil;
	}

	/** Visual-only progressive replacement for the End island; no world block is mutated. */
	public static BlockState failureBlockReplacement(BlockPos pos, BlockState original) {
		Minecraft client = Minecraft.getInstance();
		WorldInterfaceSnapshotS2C encounter = failureEncounter(client);
		if (encounter == null || client.level.dimension() != Level.END) return original;
		long dx = pos.getX() - encounter.center().getX();
		long dz = pos.getZ() - encounter.center().getZ();
		if (dx * dx + dz * dz > 160L * 160L) return original;
		return erosionThreshold(encounter.encounterId().getMostSignificantBits()
				^ encounter.encounterId().getLeastSignificantBits() ^ pos.asLong()) <= encounter.failureProgress()
				? ModBlocks.MISSING_TEXTURE_PROXY.defaultBlockState() : original;
	}

	/** Entity and player textures flip deterministically as the same failure erosion advances. */
	public static boolean corruptEntityTexture(Identifier id) {
		Minecraft client = Minecraft.getInstance();
		WorldInterfaceSnapshotS2C encounter = failureEncounter(client);
		if (encounter == null || client.level.dimension() != Level.END) return false;
		String path = id.getPath();
		if (!(path.startsWith("textures/entity/") || path.startsWith("skins/")
				|| path.contains("player_skin"))) return false;
		long textureSeed = ((long) id.getNamespace().hashCode() << 32) ^ path.hashCode()
				^ encounter.encounterId().getLeastSignificantBits();
		return erosionThreshold(textureSeed) <= encounter.failureProgress();
	}

	private static WorldInterfaceSnapshotS2C failureEncounter(Minecraft client) {
		if (client == null || client.level == null) return null;
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		return encounter != null
				&& encounter.stage() == WorldInterfaceProtocol.Stage.FAILURE_RESOLUTION
				&& encounter.outcome() == WorldInterfaceProtocol.Outcome.FAILURE
				&& encounter.failureProgress() > 0.0F ? encounter : null;
	}

	private static float erosionThreshold(long value) {
		long mixed = value;
		mixed ^= mixed >>> 33;
		mixed *= 0xff51afd7ed558ccdl;
		mixed ^= mixed >>> 33;
		mixed *= 0xc4ceb9fe1a85ec53l;
		mixed ^= mixed >>> 33;
		return (mixed >>> 40) / (float) 0xFFFFFF;
	}

	private static void resetPresentationState() {
		stopAmbientLoops();
		trackedEncounterId = null;
		trackedBossId = null;
		lastObservedHealth = Float.NaN;
		damageFlashUntil = Long.MIN_VALUE;
		audibleGatewayState = WorldInterfaceProtocol.GatewayState.DORMANT;
		ambientStage = null;
		failureErosionBucket = -1;
	}

	private static void tick(Minecraft client) {
		if (client.level == null || client.player == null || client.isPaused()) return;
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		if (encounter == null) {
			if (trackedEncounterId != null) resetPresentationState();
			return;
		}
		long now = client.level.getGameTime();
		observeEncounter(client, encounter, now);
	}

	private static void observeEncounter(Minecraft client, WorldInterfaceSnapshotS2C encounter, long now) {
		if (!encounter.encounterId().equals(trackedEncounterId)) {
			trackedEncounterId = encounter.encounterId();
			trackedBossId = encounter.bossId();
			lastObservedHealth = encounter.currentHealth();
			damageFlashUntil = Long.MIN_VALUE;
			audibleGatewayState = WorldInterfaceProtocol.GatewayState.DORMANT;
			ambientStage = null;
		} else {
			trackedBossId = encounter.bossId();
			if (Float.isFinite(lastObservedHealth)
					&& encounter.currentHealth() + 0.001F < lastObservedHealth) {
				damageFlashUntil = now + DAMAGE_FLASH_TICKS;
			}
			lastObservedHealth = encounter.currentHealth();
		}
		tickGatewayAudio(client, encounter);
		tickAmbientAudio(client, encounter);
		tickFailureErosion(client, encounter);
	}

	private static void tickFailureErosion(Minecraft client, WorldInterfaceSnapshotS2C encounter) {
		int bucket = encounter.stage() == WorldInterfaceProtocol.Stage.FAILURE_RESOLUTION
				&& encounter.outcome() == WorldInterfaceProtocol.Outcome.FAILURE
				? Math.clamp((int) Math.ceil(encounter.failureProgress() * 12.0F), 0, 12) : -1;
		if (bucket == failureErosionBucket) return;
		failureErosionBucket = bucket;
		if (client.levelRenderer != null) client.levelRenderer.allChanged();
	}

	private static void tickGatewayAudio(Minecraft client, WorldInterfaceSnapshotS2C encounter) {
		WorldInterfaceProtocol.GatewayState current = encounter.gatewayState();
		if (current == audibleGatewayState) return;
		audibleGatewayState = current;
		SoundEvent cue = switch (current) {
			case PURPLE -> ModSounds.WORLD_INTERFACE_GATEWAY_PURPLE;
			case GOLD -> ModSounds.WORLD_INTERFACE_GATEWAY_GOLD;
			case RED -> ModSounds.WORLD_INTERFACE_GATEWAY_RED;
			case DORMANT -> null;
		};
		if (cue == null) return;
		BlockPos origin = encounter.gatewayPositions().stream().min((left, right) -> Double.compare(
				client.player.distanceToSqr(left.getCenter()), client.player.distanceToSqr(right.getCenter())))
				.orElse(encounter.center());
		playBoundedLocal(client, cue, origin.getX() + 0.5D, origin.getY() + 1.0D,
				origin.getZ() + 0.5D, 0.62F, current == WorldInterfaceProtocol.GatewayState.RED ? 0.72F : 1.0F);
	}

	private static void tickAmbientAudio(Minecraft client, WorldInterfaceSnapshotS2C encounter) {
		WorldInterfaceProtocol.Stage currentStage = encounter.stage();
		SoundEvent cue = ambientCue(currentStage);
		if (cue == null) {
			if (ambientLoop != null || retiringAmbientLoop != null) stopAmbientLoops();
			ambientStage = currentStage;
			return;
		}
		float relativeVolume = switch (currentStage) {
			case PHASE_1 -> 0.42F;
			case PHASE_2 -> 0.46F;
			case PHASE_3 -> 0.50F;
			default -> 0.0F;
		};
		if (currentStage != ambientStage || ambientLoop == null || ambientLoop.isStopped()) {
			transitionAmbientLoop(client, cue, relativeVolume);
			ambientStage = currentStage;
		}
		if (retiringAmbientLoop != null && retiringAmbientLoop.isStopped()) retiringAmbientLoop = null;
	}

	private static SoundEvent ambientCue(WorldInterfaceProtocol.Stage stage) {
		return switch (stage) {
			case PHASE_1 -> ModSounds.WORLD_INTERFACE_AMBIENT_1;
			case PHASE_2 -> ModSounds.WORLD_INTERFACE_AMBIENT_2;
			case PHASE_3 -> ModSounds.WORLD_INTERFACE_AMBIENT_3;
			default -> null;
		};
	}

	private static void playBoundedLocal(Minecraft client, SoundEvent cue, double x, double y, double z,
			float relativeVolume, float pitch) {
		float volume = (float) Math.clamp(RuntimeServices.config().meta().peakVolume()
				* Math.clamp(relativeVolume, 0.0F, 1.0F), 0.0D, 1.0D);
		if (volume <= 0.0F) return;
		client.level.playLocalSound(x, y, z, cue, SoundSource.AMBIENT, volume,
				Math.clamp(pitch, 0.5F, 2.0F), false);
	}

	private static void transitionAmbientLoop(Minecraft client, SoundEvent cue, float relativeVolume) {
		if (retiringAmbientLoop != null) retiringAmbientLoop.forceStop();
		retiringAmbientLoop = ambientLoop;
		if (retiringAmbientLoop != null) retiringAmbientLoop.fadeOut();
		ambientLoop = new AmbientLoop(cue, relativeVolume);
		client.getSoundManager().play(ambientLoop);
	}

	private static void stopAmbientLoops() {
		if (ambientLoop != null) ambientLoop.forceStop();
		if (retiringAmbientLoop != null) retiringAmbientLoop.forceStop();
		ambientLoop = null;
		retiringAmbientLoop = null;
	}

	private static void renderOverlay(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || client.options.hideGui) return;
		WorldInterfaceClientState.Projection projection = WorldInterfaceClientState.snapshot();
		WorldInterfaceSnapshotS2C encounter = projection.encounter();
		if (encounter != null
				&& encounter.stage() == WorldInterfaceProtocol.Stage.FAILURE_RESOLUTION
				&& encounter.outcome() == WorldInterfaceProtocol.Outcome.FAILURE
				&& encounter.failureProgress() > 0.0F) {
			renderFailureErosion(graphics, encounter.failureProgress(), encounter.encounterId().getLeastSignificantBits());
		}
		if (!projection.actionActive(client.level.getGameTime())
				|| !projection.actionTargets(client.player.getUUID())) return;
		BossActionS2C action = projection.action();
		long elapsed = client.level.getGameTime() - action.startTick();
		switch (action.action()) {
			case MENTAL_ATTACK -> renderMentalAttack(graphics, action, elapsed);
			case FORCED_EXPULSION -> renderForcedExpulsion(graphics, action, elapsed);
			default -> {
				// Other actions are represented by entity animation and spatial warnings.
			}
		}
	}

	private static void renderMentalAttack(GuiGraphics graphics, BossActionS2C action, long elapsed) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (elapsed < 40L) {
			int radius = 14 + (int) elapsed / 3;
			int alpha = 80 + (int) elapsed * 3;
			int color = (Math.min(220, alpha) << 24) | 0x00B449D1;
			graphics.renderOutline(width / 2 - radius, height / 2 - radius, radius * 2, radius * 2, color);
			graphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable(
					"hud.thefourthfrequency.world_interface.action.mental_lock"),
					width / 2, height / 2 + radius + 6, 0xFFD59ADF);
			return;
		}
		int intensity = Math.clamp((int) (elapsed - 40L), 0, 80);
		graphics.fill(0, 0, width, height, ((45 + intensity) << 24) | 0x000B0010);
		String glyphs = "0101/空/见/频/我/你";
		for (int index = 0; index < 18; index++) {
			long mixed = action.seed() ^ action.sequence() * 0x9E3779B97F4A7C15L
					^ (elapsed / 2L) * 0xC2B2AE3D27D4EB4FL ^ index * 0x165667B19E3779F9L;
			int x = Math.floorMod(Long.hashCode(mixed), Math.max(1, width - 54));
			int y = Math.floorMod(Long.hashCode(mixed >>> 17), Math.max(1, height - 12));
			int start = Math.floorMod(Long.hashCode(mixed >>> 31), glyphs.length());
			String glyph = glyphs.substring(start, Math.min(glyphs.length(), start + 1));
			graphics.drawString(Minecraft.getInstance().font, Component.literal(glyph), x, y,
					0xBFCB64E6, false);
		}
	}

	private static void renderForcedExpulsion(GuiGraphics graphics, BossActionS2C action, long elapsed) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		double progress = Math.clamp(elapsed / Math.max(1.0D, action.duration()), 0.0D, 1.0D);
		int alpha = 50 + (int) Math.round(progress * 155.0D);
		graphics.fill(0, 0, width, height, (alpha << 24) | 0x00150008);
		int bands = 3 + (int) Math.round(progress * 14.0D);
		for (int index = 0; index < bands; index++) {
			long mixed = action.seed() ^ index * 0x9E3779B97F4A7C15L ^ elapsed / 3L;
			int y = Math.floorMod(Long.hashCode(mixed), Math.max(1, height));
			int bandHeight = 2 + Math.floorMod(Long.hashCode(mixed >>> 23), 9);
			graphics.fill(0, y, width, Math.min(height, y + bandHeight), 0xA04B0018);
		}
		if (elapsed >= Math.min(40L, action.duration() / 3L)) {
			graphics.drawCenteredString(Minecraft.getInstance().font,
					Component.translatable("hud.thefourthfrequency.world_interface.action.forced_expulsion"),
					width / 2, height / 2 - 4, 0xFFFF274D);
		}
	}

	private static void renderFailureErosion(GuiGraphics graphics, float progress, long seed) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float amount = Math.clamp(progress, 0.0F, 1.0F);
		int veilAlpha = 12 + Math.round(amount * 166.0F);
		graphics.fill(0, 0, width, height, (veilAlpha << 24) | 0x00090010);
		int missingAlpha = Math.round(amount * 84.0F);
		graphics.fill(0, 0, width, height, (missingAlpha << 24) | 0x00290035);

		int tears = Math.clamp((int) Math.ceil(amount * 30.0F), 1, 30);
		for (int index = 0; index < tears; index++) {
			long mixed = seed ^ index * 0xA24BAED4963EE407L;
			int x = Math.floorMod(Long.hashCode(mixed), Math.max(1, width));
			int y = Math.floorMod(Long.hashCode(mixed >>> 21), Math.max(1, height));
			int tearWidth = 8 + Math.floorMod(Long.hashCode(mixed >>> 37),
					Math.max(9, Math.round(18.0F + amount * 72.0F)));
			int tearHeight = 1 + Math.floorMod(Long.hashCode(mixed >>> 49),
					Math.max(2, Math.round(2.0F + amount * 9.0F)));
			int alpha = 30 + Math.round(amount * 120.0F);
			graphics.fill(x, y, Math.min(width, x + tearWidth), Math.min(height, y + tearHeight),
					(alpha << 24) | (index % 2 == 0 ? 0x005B0866 : 0x00190024));
		}

		int bands = Math.clamp((int) Math.ceil(amount * 26.0F), 1, 26);
		for (int index = 0; index < bands; index++) {
			long mixed = seed ^ index * 0xD6E8FEB86659FD93L;
			int y = Math.floorMod(Long.hashCode(mixed), Math.max(1, height));
			int bandHeight = 1 + Math.floorMod(Long.hashCode(mixed >>> 19),
					Math.max(2, (int) (amount * 18.0F) + 2));
			int alpha = 20 + (int) (amount * 125.0F);
			graphics.fill(0, y, width, Math.min(height, y + bandHeight),
					(alpha << 24) | (index % 2 == 0 ? 0x0016001D : 0x003A073E));
		}
	}

	private static final class AmbientLoop extends AbstractTickableSoundInstance {
		private final float relativeVolume;
		private int fadeInAge;
		private int fadeOutAge = -1;

		private AmbientLoop(SoundEvent cue, float relativeVolume) {
			super(cue, SoundSource.AMBIENT, RandomSource.create());
			this.relativeVolume = Math.clamp(relativeVolume, 0.0F, 1.0F);
			this.volume = 0.0F;
			this.pitch = 1.0F;
			this.looping = true;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
		}

		private void fadeOut() {
			if (fadeOutAge < 0) fadeOutAge = 0;
		}

		private void forceStop() {
			stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			float envelope;
			if (fadeOutAge >= 0) {
				fadeOutAge++;
				envelope = Math.clamp(1.0F - fadeOutAge / (float) AMBIENT_FADE_OUT_TICKS, 0.0F, 1.0F);
				if (fadeOutAge >= AMBIENT_FADE_OUT_TICKS) {
					stop();
					return;
				}
			} else {
				fadeInAge++;
				envelope = Math.clamp(fadeInAge / (float) AMBIENT_FADE_IN_TICKS, 0.0F, 1.0F);
			}
			volume = (float) Math.clamp(RuntimeServices.config().meta().peakVolume()
					* relativeVolume * envelope, 0.0D, 1.0D);
		}
	}
}
