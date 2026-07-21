package com.xm.thefourthfrequency.client_ui;

import com.mojang.authlib.GameProfile;
import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.meta_api.MetaController;
import com.xm.thefourthfrequency.networking.AnomalyCompleteC2S;
import com.xm.thefourthfrequency.networking.AnomalyPhaseS2C;
import com.xm.thefourthfrequency.networking.AnomalyStartS2C;
import com.xm.thefourthfrequency.networking.TerminalAnomalyLoggedS2C;
import com.xm.thefourthfrequency.terminal.AnomalyCompletionStatus;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Single owner for camera, input, audio, overlay, distance, entities, light, item and trace leases. */
public final class AnomalyPresentationController {
	private static final Identifier HANDS = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
			"textures/gui/anomaly/peripheral_hand.png");
	private static final int HAND_TEXTURE_WIDTH = 512;
	private static final int HAND_TEXTURE_HEIGHT = 256;
	private static final int LIGHT_DROPOUT_SCAN_RADIUS = 16;
	private static final int LIGHT_DROPOUT_DARK_RADIUS = LIGHT_DROPOUT_SCAN_RADIUS + 15;
	private static final float PERIPHERAL_HAND_ENTER_FRACTION = 0.42F;
	private static final int HISTORY_TICKS = 60;
	private static final int ACTION_ECHO_ENTITY_ID = -0x4543484F;
	private static final int SECOND_PERSON_CAMERA_ID = -0x53454350;
	private static final int SECOND_PERSON_BODY_ID = -0x53454342;
	private static final int[][] INVENTORY_VISUAL_ROWS = {
			{9, 18}, {18, 27}, {27, 36}, {0, 9}
	};
	private static final List<EquipmentSlot> PLAYER_EQUIPMENT_SLOTS = List.of(
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET,
			EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
	private static final Deque<PlayerFrame> HISTORY = new ArrayDeque<>();
	private static final Set<Integer> MISREAD_SLOTS = new HashSet<>();
	private static final Set<BlockPos> HIDDEN_LIGHTS = ConcurrentHashMap.newKeySet();
	private static final Set<TracePosition> PURPLE_TRACES = new HashSet<>();
	private static final Set<BlockPos> RENDERED_TRACE_POSITIONS = ConcurrentHashMap.newKeySet();
	private static final Set<Integer> WATCHERS_HEARD = ConcurrentHashMap.newKeySet();

	private static UUID instanceId;
	private static String anomalyId = "none";
	private static long seed;
	private static int totalTicks;
	private static int remainingTicks;
	private static int lastPhaseSequence;
	private static boolean phaseBlackout;
	private static boolean nearBlindness;
	private static BlockPos fracturePos;
	private static volatile BlockPos lightDropoutCenter;
	private static ClientLevel activeLevel;
	private static ArmorStand cameraAnchor;
	private static RemotePlayer secondPersonBody;
	private static Entity previousCameraEntity;
	private static CameraType previousCameraType;
	private static float lockedPlayerYaw;
	private static float lockedPlayerPitch;
	private static double fixedCameraX;
	private static double fixedCameraY;
	private static double fixedCameraZ;
	private static RemotePlayer actionEcho;
	private static List<PlayerFrame> echoFrames = List.of();
	private static BlockPos echoCrack;
	private static int soundDelay;
	private static int phantomBurstRemaining;
	private static boolean phantomMiningBurst;
	private static double phantomSoundX;
	private static double phantomSoundY;
	private static double phantomSoundZ;
	private static double phantomWalkX;
	private static double phantomWalkZ;
	private static BlockState phantomSoundMaterial;
	private static int dedicatedSoundCount;
	private static int ambientSoundCount;
	private static int fractureStage = -1;
	private static int glitchImpactTicks;
	private static boolean glitchTriggered;
	private static boolean attackWasDown;
	private static String currentPhase = "idle";
	private static boolean simulatedWindow;
	private static boolean simulatedNotepad;
	private static boolean initialized;
	private static boolean restoring;

	private AnomalyPresentationController() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(AnomalyStartS2C.TYPE, (payload, context) ->
				context.client().execute(() -> start(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(AnomalyPhaseS2C.TYPE, (payload, context) ->
				context.client().execute(() -> phase(payload)));
		ClientPlayNetworking.registerGlobalReceiver(TerminalAnomalyLoggedS2C.TYPE, (payload, context) ->
				context.client().execute(TerminalClientAudio::anomaly));
		ClientTickEvents.END_CLIENT_TICK.register(AnomalyPresentationController::tick);
		HudRenderCallback.EVENT.register((graphics, delta) -> renderOverlay(graphics));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			restore(client, false, AnomalyCompletionStatus.INTERRUPTED);
			WATCHERS_HEARD.clear();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			restore(client, false, AnomalyCompletionStatus.INTERRUPTED);
			PURPLE_TRACES.clear();
			HISTORY.clear();
			WATCHERS_HEARD.clear();
		});
	}

	private static void start(Minecraft client, AnomalyStartS2C payload) {
		if (client.player == null || client.level == null) return;
		if (instanceId != null) restore(client, true, AnomalyCompletionStatus.INTERRUPTED);
		instanceId = payload.instanceId();
		anomalyId = payload.anomalyId();
		seed = payload.seed();
		totalTicks = Math.max(1, payload.expectedDurationTicks());
		remainingTicks = totalTicks;
		activeLevel = client.level;
		lastPhaseSequence = 0;
		currentPhase = "running";
		phaseBlackout = false;
		nearBlindness = false;
		soundDelay = 0;
		phantomBurstRemaining = 0;
		phantomSoundMaterial = null;
		dedicatedSoundCount = 0;
		ambientSoundCount = 0;
		fractureStage = -1;
		glitchImpactTicks = 0;
		glitchTriggered = false;
		attackWasDown = false;

		switch (anomalyId) {
			case "surface_fracture" -> {
				if (!payload.hasAnchor()) { fail(client); return; }
				fracturePos = BlockPos.of(payload.anchorPosition());
				activeLevel.destroyBlockProgress(fractureBreakerId(), fracturePos, 0);
				BlockState target = activeLevel.getBlockState(fracturePos);
				activeLevel.playLocalSound(Vec3.atCenterOf(fracturePos).x, Vec3.atCenterOf(fracturePos).y,
						Vec3.atCenterOf(fracturePos).z, target.getSoundType().getHitSound(),
						SoundSource.BLOCKS, 0.95F, 0.70F, false);
				ambientSoundCount++;
			}
			case "light_dropout" -> { scanLights(client); client.levelRenderer.allChanged(); }
			case "organ_misread" -> selectMisreadItems(client, seed);
			case "peripheral_residue" -> { }
			case "viewpoint_separation" -> beginFixedCamera(client);
			case "action_echo" -> beginActionEcho(client);
			case "local_rule_collapse" -> {
				addPurpleTraces(client, seed);
				// A full one-shot rebuild also works when the anomaly was requested while a GUI covered the world.
				client.levelRenderer.allChanged();
			}
			case "channel_override" -> client.setScreen(new ChannelOverrideScreen());
			case "window_pulse" -> simulatedWindow = !MetaController.startWindowPulse();
			case "desktop_presence" -> simulatedNotepad = !MetaController.startDesktopPresence();
			default -> { }
		}
	}

	private static void phase(AnomalyPhaseS2C payload) {
		if (instanceId == null || !instanceId.equals(payload.instanceId())
				|| payload.sequence() <= lastPhaseSequence) return;
		lastPhaseSequence = payload.sequence();
		currentPhase = payload.phase();
		remainingTicks = Math.max(remainingTicks, payload.remainingTicks());
		phaseBlackout = payload.blackout();
	}

	private static void tick(Minecraft client) {
		recordHistory(client);
		if (instanceId == null) return;
		if (client.level != activeLevel || client.player == null || !client.player.isAlive()) {
			restore(client, true, AnomalyCompletionStatus.INTERRUPTED);
			return;
		}
		int elapsed = totalTicks - remainingTicks;
		switch (anomalyId) {
			case "phantom_echo" -> tickPhantomEcho(client, elapsed);
			case "surface_fracture" -> tickSurfaceFracture(client);
			case "peripheral_residue" -> {
				int impactAt = Math.max(2, Math.round(totalTicks * 0.72F));
				if (!glitchTriggered && elapsed >= impactAt) triggerGlitchImpact(client);
			}
			case "light_dropout" -> {
				if ((elapsed % 10) == 0 && scanLights(client)) client.levelRenderer.allChanged();
			}
			case "action_echo" -> {
				try { tickActionEcho(client, elapsed); }
				catch (RuntimeException failure) {
					TheFourthFrequency.LOGGER.warn("Action echo presentation failed safely", failure);
					fail(client);
					return;
				}
			}
			case "viewpoint_separation" -> {
				maintainFixedCamera(client);
				syncSecondPersonBody(client);
			}
			case "experience_gap", "channel_override" -> releaseAllInput(client);
			default -> { }
		}
		if (glitchImpactTicks > 0) glitchImpactTicks--;
		if (anomalyId.equals("channel_override") && !(client.screen instanceof ChannelOverrideScreen))
			client.setScreen(new ChannelOverrideScreen());
		remainingTicks--;
		if (fracturePos != null) {
			int stage = Math.clamp(9 - Math.max(0, remainingTicks) / 10, 0, 9);
			fractureStage = stage;
			activeLevel.destroyBlockProgress(fractureBreakerId(), fracturePos, stage);
		}
		if (remainingTicks <= 0) restore(client, true, AnomalyCompletionStatus.COMPLETED);
	}

	private static void tickSurfaceFracture(Minecraft client) {
		boolean attackDown = client.options.keyAttack.isDown();
		if (attackDown && !attackWasDown && fracturePos != null
				&& client.hitResult instanceof BlockHitResult block && block.getBlockPos().equals(fracturePos))
			triggerGlitchImpact(client);
		attackWasDown = attackDown;
	}

	private static void triggerGlitchImpact(Minecraft client) {
		glitchTriggered = true;
		glitchImpactTicks = 18;
		if (client.player != null && client.level != null) {
			client.level.playLocalSound(client.player.getX(), client.player.getEyeY(), client.player.getZ(),
					ModSounds.WINDOW_GLITCH, SoundSource.MASTER, 1.35F, 0.58F, false);
			dedicatedSoundCount++;
		}
	}

	private static void recordHistory(Minecraft client) {
		if (client.player == null || client.level == null) { HISTORY.clear(); return; }
		List<ItemStack> equipment = new ArrayList<>(PLAYER_EQUIPMENT_SLOTS.size());
		for (EquipmentSlot slot : PLAYER_EQUIPMENT_SLOTS)
			equipment.add(client.player.getItemBySlot(slot).copy());
		BlockPos digging = client.options.keyAttack.isDown() && client.hitResult instanceof BlockHitResult block
				? block.getBlockPos().immutable() : null;
		HISTORY.addLast(new PlayerFrame(client.player.getX(), client.player.getY(), client.player.getZ(),
				client.player.getYRot(), client.player.getXRot(), client.player.getPose(),
				client.player.walkAnimation.speed(), client.player.swinging, client.player.swingTime,
				client.player.oAttackAnim, client.player.attackAnim, client.player.swingingArm,
				client.player.isSprinting(), client.player.isShiftKeyDown(), client.player.isSwimming(),
				client.player.isUsingItem() ? client.player.getUsedItemHand() : null, equipment, digging));
		while (HISTORY.size() > HISTORY_TICKS) HISTORY.removeFirst();
	}

	private static void beginActionEcho(Minecraft client) {
		if (HISTORY.size() < HISTORY_TICKS) { fail(client); return; }
		echoFrames = List.copyOf(HISTORY);
		PlayerFrame first = echoFrames.get(0);
		client.level.playLocalSound(first.x, first.y, first.z, ModSounds.ANOMALY_ECHO,
				SoundSource.AMBIENT, 0.82F, 0.78F, false);
	}

	private static void tickActionEcho(Minecraft client, int elapsed) {
		if (elapsed < 20 || echoFrames.isEmpty()) return;
		int frameIndex = elapsed - 20;
		if (frameIndex >= echoFrames.size()) return;
		if (actionEcho == null) {
			GameProfile copy = new GameProfile(UUID.randomUUID(), "echo");
			actionEcho = new ActionEchoPlayer(client.level, copy, client.player.getSkin());
			actionEcho.setId(ACTION_ECHO_ENTITY_ID);
			actionEcho.setInvulnerable(true);
			actionEcho.setCustomNameVisible(false);
			client.level.addEntity(actionEcho);
		}
		PlayerFrame frame = echoFrames.get(frameIndex);
		actionEcho.snapTo(frame.x, frame.y, frame.z, frame.yaw, frame.pitch);
		PlayerFrame previous = frameIndex > 0 ? echoFrames.get(frameIndex - 1) : frame;
		actionEcho.xo = previous.x; actionEcho.yo = previous.y; actionEcho.zo = previous.z;
		actionEcho.xOld = previous.x; actionEcho.yOld = previous.y; actionEcho.zOld = previous.z;
		actionEcho.yRotO = previous.yaw; actionEcho.xRotO = previous.pitch;
		actionEcho.setPose(frame.pose);
		actionEcho.setYBodyRot(frame.yaw);
		actionEcho.setYHeadRot(frame.yaw);
		actionEcho.setShiftKeyDown(frame.shiftKeyDown);
		actionEcho.setSprinting(frame.sprinting);
		actionEcho.setSwimming(frame.swimming);
		actionEcho.walkAnimation.update(frame.walkSpeed, 1.0F, 1.0F);
		actionEcho.swinging = frame.swinging;
		actionEcho.swingTime = frame.swingTime;
		actionEcho.oAttackAnim = frame.previousAttackAnim;
		actionEcho.attackAnim = frame.attackAnim;
		actionEcho.swingingArm = frame.swingingArm;
		for (int index = 0; index < PLAYER_EQUIPMENT_SLOTS.size(); index++)
			actionEcho.setItemSlot(PLAYER_EQUIPMENT_SLOTS.get(index), frame.equipment.get(index).copy());
		if (frame.usingHand != null) {
			if (!actionEcho.isUsingItem() || actionEcho.getUsedItemHand() != frame.usingHand)
				actionEcho.startUsingItem(frame.usingHand);
		} else if (actionEcho.isUsingItem()) actionEcho.stopUsingItem();
		if (echoCrack != null && !echoCrack.equals(frame.digging))
			client.level.destroyBlockProgress(echoBreakerId(), echoCrack, -1);
		echoCrack = frame.digging;
		if (echoCrack != null) client.level.destroyBlockProgress(echoBreakerId(), echoCrack,
				Math.clamp(frameIndex / 6, 0, 9));
	}

	private static void tickPhantomEcho(Minecraft client, int elapsed) {
		if (soundDelay-- > 0) return;
		RandomSource random = RandomSource.create(seed ^ elapsed * 0x9E3779B97F4A7C15L);
		if (phantomBurstRemaining <= 0 || phantomSoundMaterial == null) {
			phantomMiningBurst = random.nextBoolean();
			phantomBurstRemaining = phantomMiningBurst ? 3 + random.nextInt(3) : 2 + random.nextInt(5);
			double angle = Math.toRadians(client.player.getYRot() + 50.0D + random.nextDouble() * 260.0D);
			double distance = 1.7D + random.nextDouble() * 3.0D;
			phantomSoundX = client.player.getX() - Math.sin(angle) * distance;
			phantomSoundY = client.player.getY();
			phantomSoundZ = client.player.getZ() + Math.cos(angle) * distance;
			double walkAngle = angle + (random.nextBoolean() ? Math.PI / 2.0D : -Math.PI / 2.0D);
			phantomWalkX = Math.cos(walkAngle) * (0.28D + random.nextDouble() * 0.16D);
			phantomWalkZ = Math.sin(walkAngle) * (0.28D + random.nextDouble() * 0.16D);
			BlockPos materialPos = BlockPos.containing(phantomSoundX, phantomSoundY - 1.0D, phantomSoundZ);
			phantomSoundMaterial = client.level.getBlockState(materialPos);
			client.level.playLocalSound(phantomSoundX, phantomSoundY, phantomSoundZ, ModSounds.ANOMALY_ECHO,
					SoundSource.AMBIENT, 0.16F, 0.72F + random.nextFloat() * 0.08F, false);
			dedicatedSoundCount++;
		}
		SoundEvent humanSound = phantomMiningBurst ? phantomSoundMaterial.getSoundType().getHitSound()
				: phantomSoundMaterial.getSoundType().getStepSound();
		client.level.playLocalSound(phantomSoundX, phantomSoundY, phantomSoundZ, humanSound,
				SoundSource.AMBIENT, phantomMiningBurst ? 0.82F : 0.72F,
				(phantomMiningBurst ? 0.72F : 0.88F) + random.nextFloat() * 0.12F, false);
		ambientSoundCount++;
		phantomBurstRemaining--;
		if (!phantomMiningBurst) {
			phantomSoundX += phantomWalkX;
			phantomSoundZ += phantomWalkZ;
		}
		soundDelay = phantomMiningBurst ? 5 + random.nextInt(4) : 7 + random.nextInt(5);
		if (phantomBurstRemaining == 0)
			soundDelay += phantomMiningBurst ? 14 + random.nextInt(18) : 20 + random.nextInt(26);
	}

	private static void beginFixedCamera(Minecraft client) {
		previousCameraEntity = client.getCameraEntity();
		previousCameraType = client.options.getCameraType();
		var triggeringCamera = client.gameRenderer.getMainCamera();
		Vec3 triggeringView = triggeringCamera.position();
		fixedCameraX = triggeringView.x;
		fixedCameraY = triggeringView.y;
		fixedCameraZ = triggeringView.z;
		lockedPlayerYaw = triggeringCamera.yRot();
		lockedPlayerPitch = triggeringCamera.xRot();
		GameProfile bodyProfile = new GameProfile(UUID.randomUUID(), "\u200B");
		secondPersonBody = new ActionEchoPlayer(client.level, bodyProfile, client.player.getSkin());
		secondPersonBody.setId(SECOND_PERSON_BODY_ID);
		secondPersonBody.setInvulnerable(true);
		secondPersonBody.noPhysics = true;
		secondPersonBody.setCustomNameVisible(false);
		client.level.addEntity(secondPersonBody);
		syncSecondPersonBody(client);
		cameraAnchor = new ArmorStand(client.level, fixedCameraX, fixedCameraY, fixedCameraZ);
		cameraAnchor.setId(SECOND_PERSON_CAMERA_ID);
		cameraAnchor.setInvisible(true);
		cameraAnchor.setNoGravity(true);
		cameraAnchor.noPhysics = true;
		fixedCameraY -= cameraAnchor.getEyeHeight();
		maintainFixedCamera(client);
		client.level.addEntity(cameraAnchor);
		client.options.setCameraType(CameraType.FIRST_PERSON);
		client.setCameraEntity(cameraAnchor);
	}

	private static void maintainFixedCamera(Minecraft client) {
		if (cameraAnchor == null) return;
		cameraAnchor.snapTo(fixedCameraX, fixedCameraY, fixedCameraZ, lockedPlayerYaw, lockedPlayerPitch);
		cameraAnchor.setDeltaMovement(Vec3.ZERO);
	}

	private static void syncSecondPersonBody(Minecraft client) {
		if (client.player == null || secondPersonBody == null) return;
		secondPersonBody.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(),
				client.player.getYRot(), client.player.getXRot());
		secondPersonBody.setYHeadRot(client.player.getYHeadRot());
		secondPersonBody.setYBodyRot(client.player.yBodyRot);
		secondPersonBody.setPose(client.player.getPose());
		secondPersonBody.setShiftKeyDown(client.player.isShiftKeyDown());
		secondPersonBody.setSprinting(client.player.isSprinting());
		secondPersonBody.setSwimming(client.player.isSwimming());
		secondPersonBody.walkAnimation.update(client.player.walkAnimation.speed(), 1.0F, 1.0F);
		secondPersonBody.swinging = client.player.swinging;
		secondPersonBody.swingTime = client.player.swingTime;
		secondPersonBody.oAttackAnim = client.player.oAttackAnim;
		secondPersonBody.attackAnim = client.player.attackAnim;
		secondPersonBody.swingingArm = client.player.swingingArm;
		for (EquipmentSlot slot : PLAYER_EQUIPMENT_SLOTS)
			secondPersonBody.setItemSlot(slot, client.player.getItemBySlot(slot).copy());
	}

	private static void selectMisreadItems(Minecraft client, long stableSeed) {
		MISREAD_SLOTS.clear();
		int slots = Math.min(36, client.player.getInventory().getContainerSize());
		java.util.Random random = new java.util.Random(stableSeed);
		for (int[] row : INVENTORY_VISUAL_ROWS) {
			List<Integer> available = new ArrayList<>();
			for (int slot = row[0]; slot < Math.min(row[1], slots); slot++)
				if (!client.player.getInventory().getItem(slot).isEmpty()) available.add(slot);
			if (available.isEmpty()) continue;

			Collections.shuffle(available, random);
			int first = available.getFirst();
			MISREAD_SLOTS.add(first);
			if (available.size() == 1) continue;

			int second = available.get(1);
			int widestGap = Math.abs(second - first);
			for (int index = 2; index < available.size(); index++) {
				int candidate = available.get(index);
				int gap = Math.abs(candidate - first);
				if (gap > widestGap) {
					second = candidate;
					widestGap = gap;
				}
			}
			MISREAD_SLOTS.add(second);
		}
	}

	private static boolean scanLights(Minecraft client) {
		BlockPos origin = client.player.blockPosition().immutable();
		boolean changed = !origin.equals(lightDropoutCenter);
		lightDropoutCenter = origin;
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-LIGHT_DROPOUT_SCAN_RADIUS, -LIGHT_DROPOUT_SCAN_RADIUS,
						-LIGHT_DROPOUT_SCAN_RADIUS),
				origin.offset(LIGHT_DROPOUT_SCAN_RADIUS, LIGHT_DROPOUT_SCAN_RADIUS,
						LIGHT_DROPOUT_SCAN_RADIUS))) {
			if (client.level.hasChunkAt(pos) && client.level.getBlockState(pos).getLightEmission() > 0)
				changed |= HIDDEN_LIGHTS.add(pos.immutable());
		}
		return changed;
	}

	private static void addPurpleTraces(Minecraft client, long stableSeed) {
		RENDERED_TRACE_POSITIONS.clear();
		String dimension = client.level.dimension().identifier().toString();
		BlockPos origin = client.player.blockPosition();
		net.minecraft.world.phys.Vec3 eye = client.player.getEyePosition();
		net.minecraft.world.phys.Vec3 view = client.player.getViewVector(1.0F);
		List<BlockPos> exposed = new ArrayList<>();
		for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-12, -4, -12), origin.offset(12, 5, 12))) {
			BlockPos pos = cursor.immutable();
			if (!client.level.hasChunkAt(pos)) continue;
			BlockState state = client.level.getBlockState(pos);
			if (state.isAir() || state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) continue;
			boolean hasExposedFace = false;
			for (Direction direction : Direction.values()) if (client.level.getBlockState(pos.relative(direction)).isAir()) {
				hasExposedFace = true;
				break;
			}
			if (hasExposedFace) exposed.add(pos);
		}
		exposed.sort(java.util.Comparator
				.comparing((BlockPos pos) -> !isInViewCone(pos, eye, view))
				.thenComparingDouble(pos -> pos.distSqr(origin))
				.thenComparingLong(pos -> mixTraceOrder(pos.asLong(), stableSeed)));
		int added = 0;
		for (BlockPos pos : exposed) {
			if (added >= 48 || PURPLE_TRACES.size() >= 512) break;
			if (PURPLE_TRACES.add(new TracePosition(dimension, pos.immutable()))) added++;
		}
	}

	private static boolean isInViewCone(BlockPos pos, net.minecraft.world.phys.Vec3 eye,
			net.minecraft.world.phys.Vec3 view) {
		net.minecraft.world.phys.Vec3 delta = net.minecraft.world.phys.Vec3.atCenterOf(pos).subtract(eye);
		return delta.lengthSqr() > 0.01D && delta.normalize().dot(view) > 0.55D;
	}

	private static long mixTraceOrder(long position, long stableSeed) {
		long value = position ^ stableSeed;
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdl;
		value ^= value >>> 33;
		return value;
	}

	private static void renderOverlay(GuiGraphics graphics) {
		if (instanceId == null) return;
		Minecraft client = Minecraft.getInstance();
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		if (isFullBlackout()) graphics.fill(0, 0, width, height, 0xFF000000);
		else if (nearBlindness) {
			int clearW = Math.max(72, width / 5), clearH = Math.max(54, height / 5);
			int left = (width - clearW) / 2, top = (height - clearH) / 2;
			graphics.fill(0, 0, width, top, 0xF8000000);
			graphics.fill(0, top + clearH, width, height, 0xF8000000);
			graphics.fill(0, top, left, top + clearH, 0xF8000000);
			graphics.fill(left + clearW, top, width, top + clearH, 0xF8000000);
		}
		if (anomalyId.equals("light_dropout")) graphics.fill(0, 0, width, height, 0x24000000);
		if (anomalyId.equals("peripheral_residue")) {
			int elapsed = totalTicks - remainingTicks;
			float slide = peripheralHandSlide(elapsed, totalTicks);
			int drawWidth = Math.min(Math.max(280, Math.round(width * 0.58F)),
					Math.max(280, Math.round(height * 1.06F)));
			int drawHeight = drawWidth / 2;
			int visibleWidth = Math.min(Math.round(drawWidth * 0.70F), Math.round(width * 0.36F));
			int leftFinalX = visibleWidth - drawWidth;
			int rightFinalX = width - visibleWidth;
			int leftX = Math.round(Mth.lerp(slide, -drawWidth - 4, leftFinalX));
			int rightX = Math.round(Mth.lerp(slide, width + 4, rightFinalX));
			int handY = (height - drawHeight) / 2;
			drawPeripheralHand(graphics, leftX, handY, drawWidth, drawHeight, false);
			// One palm texture is reused; only X is mirrored so both palms keep facing the viewer.
			drawPeripheralHand(graphics, rightX, handY, drawWidth, drawHeight, true);
		}
		if (glitchImpactTicks > 0) renderGlitchImpact(graphics, client, width, height);
		if (simulatedWindow) {
			int elapsed = totalTicks - remainingTicks;
			int insetX = 12 + Math.floorMod(elapsed * 7, Math.max(13, width / 8));
			int insetY = 10 + Math.floorMod(elapsed * 5, Math.max(11, height / 8));
			graphics.fill(insetX, insetY, width - insetX, insetY + 3, 0xFFE8E8E8);
			graphics.fill(insetX, height - insetY - 3, width - insetX, height - insetY, 0xFFE8E8E8);
			graphics.fill(insetX, insetY, insetX + 3, height - insetY, 0xFFE8E8E8);
			graphics.fill(width - insetX - 3, insetY, width - insetX, height - insetY, 0xFFE8E8E8);
		}
		if (simulatedNotepad) {
			int left = width / 7, right = width - left, top = height / 8, bottom = height - top;
			graphics.fill(left, top, right, bottom, 0xFFF1F1ED);
			graphics.fill(left, top, right, top + 14, 0xFFD5D5D0);
			for (int row = 0; row < 10; row++)
				graphics.drawString(client.font, Component.literal("我无处不在... 我无处不在... 我无处不在... 我无处不在..."),
						left + 8, top + 22 + row * 11, 0xFF141414, false);
		}
	}

	private static float peripheralHandSlide(int elapsed, int duration) {
		float life = Math.max(0.0F, Math.min(1.0F, elapsed / (float) Math.max(1, duration)));
		if (life >= PERIPHERAL_HAND_ENTER_FRACTION) return 1.0F;
		float phase = life / PERIPHERAL_HAND_ENTER_FRACTION;
		phase = Math.max(0.0F, Math.min(1.0F, phase));
		return phase * phase * (3.0F - 2.0F * phase);
	}

	private static void renderGlitchImpact(GuiGraphics graphics, Minecraft client, int width, int height) {
		RandomSource random = RandomSource.create(seed ^ ((long) glitchImpactTicks << 32) ^ remainingTicks);
		int alpha = Math.clamp(42 + glitchImpactTicks * 9, 0, 210);
		if (glitchImpactTicks >= 15) graphics.fill(0, 0, width, height, alpha << 24 | 0x00F4F4F4);
		for (int index = 0; index < 14; index++) {
			int y = random.nextInt(Math.max(1, height));
			int barHeight = 1 + random.nextInt(Math.max(2, height / 34));
			int x = random.nextInt(Math.max(1, width / 4 + 1)) - width / 8;
			int barWidth = width / 3 + random.nextInt(Math.max(1, width * 3 / 4));
			int rgb = index % 3 == 0 ? 0x00F1F1F1 : index % 3 == 1 ? 0x00C000E8 : 0x0010D8D0;
			graphics.fill(x, y, Math.min(width, x + barWidth), Math.min(height, y + barHeight),
					Math.min(220, alpha + random.nextInt(35)) << 24 | rgb);
		}
		Component noise = Component.literal("XXXXXXXXXXXXXXXXXXXXXXXX")
				.withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.BOLD);
		for (int row = 0; row < 7; row++) {
			int x = random.nextInt(Math.max(1, width - 80));
			int y = random.nextInt(Math.max(1, height - 10));
			graphics.drawString(client.font, noise, x, y,
					0xFF000000 | (random.nextBoolean() ? 0x00FFFFFF : 0x00FF3A63), false);
		}
	}

	private static void drawPeripheralHand(GuiGraphics graphics, int x, int y, int width, int height,
			boolean mirrorHorizontally) {
		if (!mirrorHorizontally) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, HANDS, x, y, 0.0F, 0.0F,
					width, height, HAND_TEXTURE_WIDTH, HAND_TEXTURE_HEIGHT,
					HAND_TEXTURE_WIDTH, HAND_TEXTURE_HEIGHT);
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, HANDS, x, y, HAND_TEXTURE_WIDTH, 0.0F,
				width, height, -HAND_TEXTURE_WIDTH, HAND_TEXTURE_HEIGHT,
				HAND_TEXTURE_WIDTH, HAND_TEXTURE_HEIGHT);
	}

	private static void releaseAllInput(Minecraft client) {
		client.options.keyUp.setDown(false); client.options.keyDown.setDown(false);
		client.options.keyLeft.setDown(false); client.options.keyRight.setDown(false);
		client.options.keyJump.setDown(false); client.options.keyShift.setDown(false);
		client.options.keyAttack.setDown(false); client.options.keyUse.setDown(false);
		client.options.keyInventory.setDown(false); client.options.keyDrop.setDown(false);
		client.options.keySwapOffhand.setDown(false);
	}

	private static void fail(Minecraft client) {
		restore(client, true, AnomalyCompletionStatus.FAILED);
	}

	/** Idempotent recovery used by timeout, death, dimension change, disconnect, stop and F8. */
	public static void restore(Minecraft client, boolean report, AnomalyCompletionStatus status) {
		if (restoring) return;
		restoring = true;
		try {
			UUID completed = instanceId;
			if (fracturePos != null && activeLevel != null)
				activeLevel.destroyBlockProgress(fractureBreakerId(), fracturePos, -1);
			if (echoCrack != null && activeLevel != null)
				activeLevel.destroyBlockProgress(echoBreakerId(), echoCrack, -1);
			if (actionEcho != null && activeLevel != null)
				activeLevel.removeEntity(actionEcho.getId(), Entity.RemovalReason.DISCARDED);
			if (cameraAnchor != null && activeLevel != null)
				activeLevel.removeEntity(cameraAnchor.getId(), Entity.RemovalReason.DISCARDED);
			if (secondPersonBody != null && activeLevel != null)
				activeLevel.removeEntity(secondPersonBody.getId(), Entity.RemovalReason.DISCARDED);
			if (client.getCameraEntity() == cameraAnchor)
				client.setCameraEntity(previousCameraEntity != null ? previousCameraEntity : client.player);
			if (previousCameraType != null) client.options.setCameraType(previousCameraType);
			if (client.screen instanceof ChannelOverrideScreen) client.setScreen(null);
			if (anomalyId.equals("window_pulse") || anomalyId.equals("desktop_presence"))
				MetaController.finishAnomaly(status != AnomalyCompletionStatus.COMPLETED);
			boolean rebuild = lightDropoutCenter != null || !HIDDEN_LIGHTS.isEmpty();
			fracturePos = null; echoCrack = null; actionEcho = null; cameraAnchor = null; secondPersonBody = null;
			previousCameraEntity = null;
			previousCameraType = null;
			echoFrames = List.of(); MISREAD_SLOTS.clear(); lightDropoutCenter = null; HIDDEN_LIGHTS.clear();
			instanceId = null; anomalyId = "none"; seed = 0L; totalTicks = 0; remainingTicks = 0;
			lastPhaseSequence = 0; currentPhase = "idle"; phaseBlackout = false; nearBlindness = false;
			dedicatedSoundCount = 0; ambientSoundCount = 0; fractureStage = -1;
			glitchImpactTicks = 0; glitchTriggered = false; attackWasDown = false;
			phantomBurstRemaining = 0; phantomSoundMaterial = null;
			simulatedWindow = false; simulatedNotepad = false; activeLevel = null;
			if (rebuild && client.levelRenderer != null) client.levelRenderer.allChanged();
			if (report && completed != null && ClientPlayNetworking.canSend(AnomalyCompleteC2S.TYPE))
				ClientPlayNetworking.send(new AnomalyCompleteC2S(completed, status));
		} finally { restoring = false; }
	}

	public static void restoreForMetaToggle() {
		restore(Minecraft.getInstance(), true, AnomalyCompletionStatus.INTERRUPTED);
	}

	public static boolean isInputLocked() {
		return instanceId != null && (anomalyId.equals("experience_gap")
				|| anomalyId.equals("channel_override"));
	}
	public static boolean isFirstPersonHandHidden() {
		return instanceId != null && anomalyId.equals("viewpoint_separation");
	}
	public static boolean shouldControlSeparatedPlayer() {
		return instanceId != null && anomalyId.equals("viewpoint_separation");
	}
	public static boolean isAudioMuted() {
		return instanceId != null && anomalyId.equals("experience_gap");
	}
	public static int redSkyShaderColor(int original) {
		float strength = redSkyStrength();
		if (strength <= 0.0F) return original;
		return tintRed(original, strength);
	}
	public static float redSkyStrength() {
		if (instanceId == null || !anomalyId.equals("red_horizon")) return 0.0F;
		int fadeTicks = totalTicks == 800 ? 200 : Math.max(4, totalTicks / 4);
		float fade = remainingTicks > fadeTicks ? 1.0F : remainingTicks / (float) fadeTicks;
		return Math.clamp(fade, 0.0F, 1.0F);
	}
	private static int tintRed(int original, float strength) {
		int alpha = original >>> 24;
		if (alpha == 0) alpha = 255;
		int red = mix((original >> 16) & 255, 176, strength);
		int green = mix((original >> 8) & 255, 12, strength);
		int blue = mix(original & 255, 22, strength);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}
	public static boolean isLightSourceHidden(BlockPos pos) { return HIDDEN_LIGHTS.contains(pos); }
	public static boolean isBlockLightSuppressedAt(BlockPos pos) {
		BlockPos center = lightDropoutCenter;
		if (center == null) return false;
		int dx = center.getX() - pos.getX();
		int dy = center.getY() - pos.getY();
		int dz = center.getZ() - pos.getZ();
		return Math.abs(dx) <= LIGHT_DROPOUT_DARK_RADIUS
				&& Math.abs(dy) <= LIGHT_DROPOUT_DARK_RADIUS
				&& Math.abs(dz) <= LIGHT_DROPOUT_DARK_RADIUS
				&& dx * dx + dy * dy + dz * dz
				<= LIGHT_DROPOUT_DARK_RADIUS * LIGHT_DROPOUT_DARK_RADIUS;
	}
	public static boolean isMisread(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return false;
		for (int slot : MISREAD_SLOTS)
			if (slot < client.player.getInventory().getContainerSize()
					&& client.player.getInventory().getItem(slot) == stack) return true;
		return false;
	}
	public static Set<Integer> misreadSlotsForTesting() { return Set.copyOf(MISREAD_SLOTS); }
	public static BlockState visualReplacement(BlockPos pos, BlockState original) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return original;
		TracePosition trace = new TracePosition(client.level.dimension().identifier().toString(), pos);
		if (!PURPLE_TRACES.contains(trace)) return original;
		return ModBlocks.MISSING_TEXTURE_PROXY.defaultBlockState();
	}
	public static int purpleTraceCount() { return PURPLE_TRACES.size(); }
	public static void onWatcherVisible(int entityId, double x, double y, double z) {
		if (WATCHERS_HEARD.size() > 192) WATCHERS_HEARD.clear();
		if (!WATCHERS_HEARD.add(entityId)) return;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;
		client.level.playLocalSound(x, y, z, SoundEvents.AMBIENT_CAVE.value(),
				SoundSource.AMBIENT, 1.0F, 0.72F, false);
		if (instanceId != null && anomalyId.equals("dark_watcher")) ambientSoundCount++;
	}
	public static void markTraceRendered(BlockPos pos) { RENDERED_TRACE_POSITIONS.add(pos.immutable()); }
	public static boolean isAnonymousProxy(Entity entity) {
		return entity != null && (entity == actionEcho || entity == secondPersonBody);
	}
	public static String activeId() { return anomalyId; }
	public static int remainingTicks() { return remainingTicks; }
	public static AnomalyTestSnapshot testSnapshot() {
		Set<String> overlays = new java.util.LinkedHashSet<>();
		boolean echoRegistered = actionEcho != null && activeLevel != null
				&& activeLevel.getEntity(actionEcho.getId()) == actionEcho;
		boolean cameraRegistered = cameraAnchor != null && activeLevel != null
				&& activeLevel.getEntity(cameraAnchor.getId()) == cameraAnchor
				&& Minecraft.getInstance().getCameraEntity() == cameraAnchor;
		boolean secondPersonBodyRegistered = secondPersonBody != null && activeLevel != null
				&& activeLevel.getEntity(secondPersonBody.getId()) == secondPersonBody;
		if (isFullBlackout()) overlays.add("blackout");
		if (nearBlindness) overlays.add("near_blindness");
		if (echoRegistered) overlays.add("action_echo_replay");
		if (echoRegistered && (actionEcho.walkAnimation.isMoving() || actionEcho.swinging
				|| actionEcho.attackAnim > 0.0F || actionEcho.getPose() != Pose.STANDING))
			overlays.add("action_echo_animation");
		if (cameraRegistered) overlays.add("second_person_camera");
		if (cameraRegistered && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON
				&& cameraAnchor.distanceToSqr(fixedCameraX, fixedCameraY, fixedCameraZ) < 0.0001D)
			overlays.add("trigger_view_camera_fixed");
		if (secondPersonBodyRegistered) overlays.add("second_person_body_proxy");
		if (isFirstPersonHandHidden()) overlays.add("first_person_hands_hidden");
		if (instanceId != null && anomalyId.equals("local_rule_collapse") && !PURPLE_TRACES.isEmpty())
			overlays.add("missing_texture_proxies");
		if (instanceId != null && anomalyId.equals("local_rule_collapse") && !RENDERED_TRACE_POSITIONS.isEmpty())
			overlays.add("missing_texture_proxies_rendered");
		if (instanceId != null && anomalyId.equals("red_horizon")) {
			overlays.add("red_horizon");
			overlays.add("red_world_fog");
		}
		if (instanceId != null && anomalyId.equals("light_dropout")) overlays.add("light_dropout");
		if (instanceId != null && anomalyId.equals("peripheral_residue")) overlays.add("peripheral_hand_instances");
		if (glitchImpactTicks > 0) overlays.add("glitch_impact");
		if (fractureStage >= 0) overlays.add("surface_fracture");
		if (simulatedWindow) overlays.add("window_fallback");
		if (simulatedNotepad) overlays.add("notepad_fallback");
		if (instanceId != null && anomalyId.equals("channel_override")) overlays.add("channel_override");
		return new AnomalyTestSnapshot(instanceId, anomalyId, currentPhase, remainingTicks, overlays,
				dedicatedSoundCount, ambientSoundCount, HIDDEN_LIGHTS.size(), MISREAD_SLOTS.size(), fractureStage,
				cameraRegistered,
				isInputLocked(), isAudioMuted(), (actionEcho == null ? 0 : 1)
						+ (cameraRegistered ? 1 : 0) + (secondPersonBodyRegistered ? 1 : 0),
				PURPLE_TRACES.size(), simulatedWindow || simulatedNotepad);
	}
	private static int mix(int from, int to, float amount) {
		return Math.clamp(Math.round(from + (to - from) * amount), 0, 255);
	}
	private static boolean isFullBlackout() {
		return instanceId != null && anomalyId.equals("experience_gap");
	}
	private static int fractureBreakerId() { return -0x4F465246; }
	private static int echoBreakerId() { return -0x4543484F; }

	private record PlayerFrame(double x, double y, double z, float yaw, float pitch, Pose pose,
			float walkSpeed, boolean swinging, int swingTime, float previousAttackAnim, float attackAnim,
			InteractionHand swingingArm, boolean sprinting, boolean shiftKeyDown, boolean swimming,
			InteractionHand usingHand, List<ItemStack> equipment, BlockPos digging) { }
	private record TracePosition(String dimension, BlockPos position) { }

	private static final class ActionEchoPlayer extends RemotePlayer {
		private final PlayerSkin skin;
		private ActionEchoPlayer(ClientLevel level, GameProfile profile, PlayerSkin skin) {
			super(level, profile);
			this.skin = skin;
		}
		@Override public PlayerSkin getSkin() { return skin; }
		@Override public boolean isPushable() { return false; }
		@Override public boolean isPickable() { return false; }
	}
}
