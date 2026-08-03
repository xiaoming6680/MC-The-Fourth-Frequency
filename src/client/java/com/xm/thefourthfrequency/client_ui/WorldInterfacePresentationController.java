package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.client_render.WorldInterfaceBeamBatchRenderer;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.ending.WorldInterfacePolicy;
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
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Owns deterministic attack overlays, ambient transitions, and gateway presentation registration. */
public final class WorldInterfacePresentationController {
	private static final int AMBIENT_FADE_IN_TICKS = 20;
	private static final int AMBIENT_FADE_OUT_TICKS = 16;
	private static final long DAMAGE_FLASH_TICKS = 5L;
	/**
	 * Horizontal reach of the erosion, and of the rebuild it needs.
	 *
	 * <p>Taken from the policy rather than chosen here. The server commits the same disc to the
	 * world when the fight ends, and a render that reached further simply un-drew itself the moment
	 * the encounter cleared - the island visibly healed the outer four fifths of its damage.</p>
	 */
	private static final int EROSION_RADIUS_BLOCKS = WorldInterfacePolicy.EROSION_RADIUS_BLOCKS;
	private static final int LOCK_ACCENT = 0x00C24BE0;
	private static final int LOCK_TEXT = 0x00F0D2FA;
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
		WorldInterfaceBeamBatchRenderer.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(WorldInterfacePresentationController::tick);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> renderOverlay(graphics));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hardReset());
	}

	public static void resetForEnding() {
		hardReset();
	}

	/** Teardown that also snaps the atmosphere, for paths where no further frames will ease it out. */
	private static void hardReset() {
		resetPresentationState();
		WorldInterfaceAtmosphereController.reset();
	}

	/** Lets the entity renderer turn authoritative virtual-health deltas into a short hit-material flash. */
	public static boolean isDamageFlashActive(UUID bossId, long gameTime) {
		return bossId != null && bossId.equals(trackedBossId) && gameTime < damageFlashUntil;
	}

	/** Visual-only progressive replacement for the End island; no world block is mutated. */
	public static BlockState failureBlockReplacement(BlockPos pos, BlockState original) {
		// Read client.level exactly once: the main thread can null it out (disconnect, dimension
		// change) between two separate reads, and this method may run on a chunk-build worker
		// thread via the render mixins, so a re-read here was a real, if narrow, NPE race.
		// Erosion re-skins what is already there; it must never conjure a solid face out of air.
		if (original.isAir()) return original;
		// End stone only, which is the same rule the server commits on.
		//
		// This used to re-skin every solid block inside the disc, so the ten obsidian pillars, their
		// bedrock caps and the altar all dissolved into missing-texture along with the ground - while
		// commitErosion, which decides what a lost encounter actually leaves behind, has always been
		// end-stone-only. The island therefore showed a corruption it was never going to keep, and
		// the landmarks a player navigates the arena by disappeared into it. The pillars keep their
		// own material and stand out against the corrupted ground, which is what they are for.
		if (!original.is(Blocks.END_STONE)) return original;
		var level = Minecraft.getInstance().level;
		WorldInterfaceSnapshotS2C encounter = failureEncounter(level);
		if (encounter == null || level.dimension() != Level.END) return original;
		long dx = pos.getX() - encounter.center().getX();
		long dz = pos.getZ() - encounter.center().getZ();
		if (dx * dx + dz * dz > (long) EROSION_RADIUS_BLOCKS * EROSION_RADIUS_BLOCKS) return original;
		return WorldInterfacePolicy.erodesAt(encounter.encounterId(), pos.asLong(), encounter.failureProgress())
				? ModBlocks.MISSING_TEXTURE_PROXY.defaultBlockState() : original;
	}

	/** Entity and player textures flip deterministically as the same failure erosion advances. */
	public static boolean corruptEntityTexture(Identifier id) {
		var level = Minecraft.getInstance().level;
		WorldInterfaceSnapshotS2C encounter = failureEncounter(level);
		if (encounter == null || level.dimension() != Level.END) return false;
		String path = id.getPath();
		if (!(path.startsWith("textures/entity/") || path.startsWith("skins/")
				|| path.contains("player_skin"))) return false;
		long textureSeed = ((long) id.getNamespace().hashCode() << 32) ^ path.hashCode()
				^ encounter.encounterId().getLeastSignificantBits();
		return WorldInterfacePolicy.erosionThreshold(textureSeed) <= encounter.failureProgress();
	}

	/**
	 * Any encounter currently reporting visible erosion, not just a lost one. The server now drives
	 * this progress from the collapse timer during combat as well, so gating on
	 * FAILURE_RESOLUTION here would throw away everything except the final six seconds.
	 */
	private static WorldInterfaceSnapshotS2C failureEncounter(net.minecraft.client.multiplayer.ClientLevel level) {
		if (level == null) return null;
		WorldInterfaceSnapshotS2C encounter = WorldInterfaceClientState.snapshot().encounter();
		return encounter != null && encounter.failureProgress() > 0.0F ? encounter : null;
	}

	private static void resetPresentationState() {
		stopAmbientLoops();
		WorldInterfaceBeamBatchRenderer.resetSession();
		WorldInterfacePostEffectController.clearOwned(Minecraft.getInstance());
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
		WorldInterfaceClientState.Projection projection = WorldInterfaceClientState.snapshot();
		// Runs before the early exit so the atmosphere eases back out after an encounter ends
		// instead of snapping the fog and sky back in a single frame.
		WorldInterfaceAtmosphereController.tick(client, projection);
		WorldInterfaceSnapshotS2C encounter = projection.encounter();
		if (encounter == null) {
			if (trackedEncounterId != null) resetPresentationState();
			return;
		}
		long now = client.level.getGameTime();
		observeEncounter(client, encounter, now);
		WorldInterfacePostEffectController.tick(client, projection);
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

	/**
	 * Erosion is baked into chunk meshes, so a changed progress value is invisible until the
	 * sections are rebuilt. This keys off the progress itself rather than the resolution stage:
	 * gating on FAILURE_RESOLUTION meant the collapse-driven erosion during combat was computed
	 * and then never actually shown, since nothing asked for a rebuild until the fight was over.
	 *
	 * <p>Twelve buckets keep the rebuild down to a handful of requests spread across the whole
	 * encounter instead of one per progress change.</p>
	 */
	private static void tickFailureErosion(Minecraft client, WorldInterfaceSnapshotS2C encounter) {
		int bucket = encounter.failureProgress() > 0.0F
				? Math.clamp((int) Math.ceil(encounter.failureProgress() * 12.0F), 0, 12) : -1;
		if (bucket == failureErosionBucket) return;
		failureErosionBucket = bucket;
		rebuildErodedSections(client, encounter);
	}

	/**
	 * Rebuilds only the cylinder {@link #failureBlockReplacement} can actually touch.
	 *
	 * <p>{@code allChanged()} does not merely re-mesh: it tears down and recreates the section render
	 * dispatcher and every render region. Calling it once per bucket meant twelve full renderer
	 * reloads during the exact seconds the frame budget matters most. Marking the affected section
	 * range dirty produces the same picture, and the meshes are rebuilt progressively.</p>
	 */
	private static void rebuildErodedSections(Minecraft client, WorldInterfaceSnapshotS2C encounter) {
		if (client.levelRenderer == null || client.level == null) return;
		BlockPos center = encounter.center();
		client.levelRenderer.setSectionRangeDirty(
				SectionPos.blockToSectionCoord(center.getX() - EROSION_RADIUS_BLOCKS),
				client.level.getMinSectionY(),
				SectionPos.blockToSectionCoord(center.getZ() - EROSION_RADIUS_BLOCKS),
				SectionPos.blockToSectionCoord(center.getX() + EROSION_RADIUS_BLOCKS),
				client.level.getMaxSectionY(),
				SectionPos.blockToSectionCoord(center.getZ() + EROSION_RADIUS_BLOCKS));
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
		client.level.playLocalSound(x, y, z, cue, SoundSource.HOSTILE, volume,
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
		if (action.action() == WorldInterfaceProtocol.BossAction.FORCED_EXPULSION) {
			renderForcedExpulsion(graphics, action, elapsed);
			return;
		}
		renderLockWarning(graphics, action, elapsed);
	}

	/**
	 * The screen half of a lock, for every action that has one.
	 *
	 * <p>These attacks are all dodgeable now - the laser trails its target, the lance falls on a
	 * fixed mark, the grab and the purge take a beat to close - and none of that is playable if
	 * being singled out is only legible from a particle on the ground behind you.</p>
	 *
	 * <p>Deliberately small. This started as a closing full-screen frame, which read as an
	 * interface failure rather than as a warning and covered the arena at the exact moment the
	 * player needs to see it. What is left is the smallest thing that answers the two questions
	 * being locked actually raises: <em>am I the one</em>, and <em>how long have I got</em>. Both
	 * live near the crosshair, where the player is already looking, and both run on the server's
	 * own warning clock so the screen never promises time the encounter will not give.</p>
	 */
	private static void renderLockWarning(GuiGraphics graphics, BossActionS2C action, long elapsed) {
		int warning = WorldInterfaceProtocol.lockWarningTicks(action.action());
		if (warning <= 0 || elapsed < 0L || elapsed >= warning) return;
		float progress = Math.clamp(elapsed / (float) warning, 0.0F, 1.0F);
		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;
		// Fades up over the first few ticks instead of snapping on, so the lock arrives rather
		// than flashing.
		int alpha = Math.round(Mth.lerp(Math.min(1.0F, elapsed / 6.0F), 0.0F,
				Mth.lerp(progress, 130.0F, 220.0F)));
		if (alpha <= 2) return;
		int accent = (alpha << 24) | LOCK_ACCENT;

		// Four short ticks converging on the crosshair. No frame, no ring, nothing across the
		// middle: the closing gap alone carries how much of the window is left.
		int reach = Math.round(Mth.lerp(progress, 34.0F, 13.0F));
		int arm = 6;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int cornerX = centerX + sx * reach;
				int cornerY = centerY + sy * reach;
				graphics.fill(Math.min(cornerX, cornerX - sx * arm), cornerY,
						Math.max(cornerX, cornerX - sx * arm), cornerY + 1, accent);
				graphics.fill(cornerX, Math.min(cornerY, cornerY - sy * arm),
						cornerX + 1, Math.max(cornerY, cornerY - sy * arm), accent);
			}
		}

		// A thin bar under the reticle, and the attack's name under that. The bar is the answer to
		// "how long"; the name is only there so the first lock of a given kind teaches what it is.
		int barWidth = 54;
		int barLeft = centerX - barWidth / 2;
		int barTop = centerY + reach + 10;
		graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 1, (alpha / 3) << 24);
		graphics.fill(barLeft, barTop, barLeft + Math.round(barWidth * (1.0F - progress)), barTop + 1,
				accent);
		graphics.drawCenteredString(Minecraft.getInstance().font,
				Component.translatable(lockLabelKey(action.action())),
				centerX, barTop + 5, (alpha << 24) | LOCK_TEXT);
	}

	private static String lockLabelKey(WorldInterfaceProtocol.BossAction action) {
		return "hud.thefourthfrequency.world_interface.lock." + switch (action) {
			case LASER_SWEEP -> "laser";
			case SKY_LANCE -> "sky_lance";
			case GRAB_THROW -> "grab_throw";
			case WEAPON_CHARGE -> "weapon";
			case HOTBAR_PURGE -> "hotbar";
			case TENDRIL_LASH -> "tendril";
			default -> "generic";
		};
	}

	private static void renderForcedExpulsion(GuiGraphics graphics, BossActionS2C action, long elapsed) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		double progress = Math.clamp(elapsed / Math.max(1.0D, action.duration()), 0.0D, 1.0D);
		// Shares the screen with the expulsion post-effect chain, so the veil only has to carry the
		// final blackout rather than the whole colour treatment.
		int alpha = 22 + (int) Math.round(progress * 120.0D);
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
		/**
		 * Slow detune and gain sway, on two periods that are not multiples of each other.
		 *
		 * <p>A phase can run for minutes, and the bed under it is a fixed recording however long
		 * that recording is. Drifting the playback the way a transmitter drifts pushes the point
		 * where a listener can pick out the wrap far past the length of the fight.</p>
		 */
		private static final float PITCH_DRIFT_TICKS = 743.0F;
		private static final float SWAY_TICKS = 509.0F;
		private static final float PITCH_DRIFT_AMOUNT = 0.008F;
		private static final float SWAY_AMOUNT = 0.06F;

		private final float relativeVolume;
		private final float phase;
		private int age;
		private int fadeInAge;
		private int fadeOutAge = -1;

		private AmbientLoop(SoundEvent cue, float relativeVolume) {
			// HOSTILE, matching the attack cues this bed plays under. On AMBIENT it shared a
			// slider with cave and weather ambience - the one category a horror-mod player is
			// most likely to have already turned off - and turning it off took the entire
			// atmosphere of the finale with it while leaving the attacks at full volume.
			super(cue, SoundSource.HOSTILE, RandomSource.create());
			this.relativeVolume = Math.clamp(relativeVolume, 0.0F, 1.0F);
			this.phase = this.random.nextFloat() * (float) (Math.PI * 2.0D);
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
			age++;
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
			pitch = 1.0F + PITCH_DRIFT_AMOUNT
					* (float) Math.sin(phase + age * (Math.PI * 2.0D) / PITCH_DRIFT_TICKS);
			float sway = 1.0F + SWAY_AMOUNT
					* (float) Math.sin(phase * 1.7D + age * (Math.PI * 2.0D) / SWAY_TICKS);
			volume = (float) Math.clamp(RuntimeServices.config().meta().peakVolume()
					* relativeVolume * envelope * sway, 0.0D, 1.0D);
		}
	}
}
