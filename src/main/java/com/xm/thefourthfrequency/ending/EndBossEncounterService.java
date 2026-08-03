package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.content.ResonanceCoreBlockEntity;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.entity.WorldInterfaceAnatomy;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import com.xm.thefourthfrequency.entity.WorldInterfacePartEntity;
import com.xm.thefourthfrequency.networking.AltarActionC2S;
import com.xm.thefourthfrequency.networking.AltarSnapshotS2C;
import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.PoemCompleteC2S;
import com.xm.thefourthfrequency.networking.PoemStartS2C;
import com.xm.thefourthfrequency.networking.TerminalNoticePayload;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalNoticeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;

/**
 * Authoritative orchestrator for the dedicated final boss, its ritual and both endings.
 *
 * <p>This class is the only owner of encounter stage transitions, the virtual health pool and the collapse clock;
 * entities, blocks and packets are projections or narrowly validated inputs.</p>
 */
public final class EndBossEncounterService {
	private static final UUID NIL_UUID = new UUID(0L, 0L);
	private static final int SUMMON_DURATION_TICKS = 100;
	/**
	 * The successful ending, in order: the body falls, the body goes, the dragon arrives, the dragon
	 * opens the way out.
	 *
	 * <p>These used to overlap. The dragon was spawned at tick eighty and spoke at a hundred and
	 * seventy, while the interface itself was not taken off the field until two hundred and sixty -
	 * so the thing the players had just killed was still lying in the sky, mid-collapse, when its
	 * replacement flew in and thanked them for killing it. The exit then arrived ninety ticks after
	 * the last line, from the altar, unattached to anything.</p>
	 *
	 * <p>Nothing overlaps now. Each beat waits for the one before it to be visibly finished.</p>
	 */
	private static final int ANCHORS_SKYWARD_TICKS = 20;
	/** The collapse and fade clips run 6.0s; the body is only discarded once they have played out. */
	private static final int BOSS_REMOVAL_TICKS = 120;
	/** A second of empty sky, so the body going is its own beat and not a cut. */
	private static final int DRAGON_SPAWN_TICKS = 140;
	/** The five seconds the dragon spends prising the altar open, from the tick it arrives. */
	private static final int DRAGON_PORTAL_WORK_TICKS = 100;
	private static final int SUCCESS_PORTAL_TICKS = DRAGON_SPAWN_TICKS + DRAGON_PORTAL_WORK_TICKS;
	/** First line while it is still working; the second lands on the tick the exit exists. */
	private static final int DRAGON_FIRST_LINE_TICKS = 200;
	private static final int RESOLUTION_DURATION_TICKS = 260;
	private static final int SNAPSHOT_INTERVAL_TICKS = 10;
	private static final int HEAL_INTERVAL_TICKS = 20;
	private static final int MAX_MUTATION_RETRIES = 5;
	private static final double ENCOUNTER_VISIBILITY_RADIUS_SQR = 256.0D * 256.0D;
	/** Before the fight starts nothing has to be reachable, so the prelude stays purely a silhouette. */
	private static final double PRELUDE_HOVER_HEIGHT = 18.0D;
	/** Slow enough that the core's telegraph shapes stay readable while it tracks a strafing player. */
	private static final float BODY_TURN_DEGREES_PER_TICK = 2.2F;
	/**
	 * The morph is flown rather than played in place.
	 *
	 * <p>A body twenty-five blocks across cannot credibly turn into a different body in front of
	 * you; the renderer's pinch hid the swap frame but the thing still visibly deflated and
	 * reinflated on the spot. So it leaves: straight up and out of sight, changes where nobody is
	 * looking, and comes back down as the next form. The apex is half the window, which is also
	 * where the pinch bottoms out, so on the one angle where a player can still follow it -- looking
	 * straight up -- the swap still happens behind nothing.
	 */
	private static final int MORPH_FLIGHT_TICKS = 60;
	private static final int MORPH_APEX_TICKS = MORPH_FLIGHT_TICKS / 2;
	/** Blocks per tick. Thirty ticks of this puts the whole silhouette well past useful sight. */
	private static final double MORPH_CLIMB_SPEED = 3.6D;
	private static final double MORPH_DIVE_SPEED = 3.2D;
	/**
	 * The interface's roar: the vanilla dragon growl, pitched down per form.
	 *
	 * <p>Borrowed rather than generated on purpose. The growl is the sound the End already means,
	 * and a player who has fought the dragon reads it before they have identified anything on
	 * screen; dropping it an octave and a bit says "that, but the thing it was guarding against".
	 * The pitch falls as the body grows, so the same cue reports which form is out there from
	 * across the island.
	 */
	private static final float[] ROAR_PITCH_BY_FORM = {0.82F, 0.68F, 0.56F};
	/** Long enough to stay atmosphere rather than turning into a metronome. */
	private static final int ROAR_INTERVAL_TICKS = 260;
	/** Beat the interface holds over the ruin before it starts climbing away from a lost encounter. */
	private static final int FAILURE_ESCAPE_HOLD_TICKS = 30;
	/** Ticks the ascent takes to reach full speed. */
	private static final int FAILURE_ESCAPE_RAMP_TICKS = 55;
	/** Blocks per tick at the top of the climb: gone from the sky inside the resolution window. */
	private static final double FAILURE_ESCAPE_TOP_SPEED = 3.4D;
	/** Vanilla melee reach, used to place impact feedback where the player actually swung. */
	private static final double MELEE_CONTACT_REACH = 3.0D;
	private static final String PART_TAG_PREFIX = "thefourthfrequency.world_interface_part.";

	private static final Map<MinecraftServer, Map<UUID, Long>> CLIENT_SEQUENCES =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<MinecraftServer, Set<UUID>> ALTAR_VIEWERS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<MinecraftServer, Map<UUID, Long>> PROJECTILE_HITS =
			Collections.synchronizedMap(new WeakHashMap<>());
	/** Encounters whose one-time anchor-cost warning has already absorbed a first strike. */
	private static final Map<MinecraftServer, Set<UUID>> ANCHOR_WARNINGS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<MinecraftServer, Map<UUID, Long>> MELEE_HITS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Set<MinecraftServer> RECOVERED_SERVERS =
			Collections.newSetFromMap(new WeakHashMap<>());
	private static boolean initialized;

	private EndBossEncounterService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		WorldInterfaceRitualService.registerAltarOpenHandler((player, position) -> openAltar(player));
		ServerTickEvents.START_SERVER_TICK.register(EndBossEncounterService::tickStart);
		ServerLifecycleEvents.SERVER_STARTED.register(EndBossEncounterService::recoverAfterRestart);
		ServerLifecycleEvents.SERVER_STOPPING.register(EndBossEncounterService::pauseForStop);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLIENT_SEQUENCES.remove(server);
			ALTAR_VIEWERS.remove(server);
			PROJECTILE_HITS.remove(server);
			MELEE_HITS.remove(server);
			RECOVERED_SERVERS.remove(server);
		});
		ServerPlayerEvents.JOIN.register(EndBossEncounterService::reconcilePlayer);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			WorldInterfaceState.snapshot(newPlayer.level().getServer()).encounterId()
					.ifPresent(id -> WorldInterfaceAttackService.onDeath(newPlayer, id));
			reconcilePlayer(newPlayer);
		});
	}

	/** Called only after vanilla successfully places the twelfth eye. */
	public static void prepareFromActivatedPortal(ServerLevel sourceLevel, BlockPos portalCenter,
			ServerPlayer activator) {
		if (sourceLevel.dimension() == Level.END) return;
		MinecraftServer server = sourceLevel.getServer();
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return;

		WorldInterfaceState.Snapshot existing = WorldInterfaceState.snapshot(server);
		if (!existing.valid()) {
			WorldInterfaceState.clearInvalid(server);
			existing = WorldInterfaceState.snapshot(server);
		}
		if (!existing.present()) {
			EndBossArenaService.PreparedArena prepared = EndBossArenaService.prepare(end);
			WorldInterfaceState.ArenaLayout layout = new WorldInterfaceState.ArenaLayout(1,
					Level.END.identifier().toString(), prepared.center(), prepared.altar(), prepared.safeSpawn(),
					indexedGates(prepared), indexedAnchors(prepared));
			UUID encounterId = UUID.randomUUID();
			long seed = end.getSeed() ^ encounterId.getMostSignificantBits()
					^ encounterId.getLeastSignificantBits() ^ end.getGameTime();
			WorldInterfaceState.MutationResult initializedState =
					WorldInterfaceState.initialize(server, encounterId, layout, seed);
			if (!initializedState.applied()) return;
			existing = initializedState.snapshot();
		}
		if (existing.stage() == WorldInterfaceStage.ARENA_READY) {
			WorldInterfaceState.transition(server, existing.encounterId().orElseThrow(), existing.revision(),
					WorldInterfaceStage.ARENA_READY, WorldInterfaceStage.WAITING_TERMINALS);
		}
		bindCore(end, WorldInterfaceState.snapshot(server));
		recordAltarOpening(activator, sourceLevel, portalCenter);
		AudioService.playBounded(sourceLevel, portalCenter, ModSounds.WORLD_INTERFACE_ALTAR,
				SoundSource.AMBIENT, 0.82F, 1.0F);
	}

	/**
	 * Debug-only jump straight into the encounter, skipping the twelve eyes and the walk to the
	 * altar. Everything after the arrival is the real path: the arena is built the way the portal
	 * builds it, and the terminal is really sacrificed, because the committed sacrifice is what the
	 * stage invariants are written against and faking it would test a state the game cannot reach.
	 */
	public static DebugBossResult debugStartEncounter(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return DebugBossResult.END_MISSING;

		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid()) {
			WorldInterfaceState.clearInvalid(server);
			snapshot = WorldInterfaceState.snapshot(server);
		}
		if (snapshot.present() && snapshot.stage().wireId() >= WorldInterfaceStage.SUMMONING.wireId()) {
			return DebugBossResult.ALREADY_RUNNING;
		}
		if (!snapshot.present()) {
			EndBossArenaService.PreparedArena prepared = EndBossArenaService.prepare(end);
			WorldInterfaceState.ArenaLayout layout = new WorldInterfaceState.ArenaLayout(1,
					Level.END.identifier().toString(), prepared.center(), prepared.altar(), prepared.safeSpawn(),
					indexedGates(prepared), indexedAnchors(prepared));
			UUID encounterId = UUID.randomUUID();
			long seed = end.getSeed() ^ encounterId.getMostSignificantBits()
					^ encounterId.getLeastSignificantBits() ^ end.getGameTime();
			WorldInterfaceState.MutationResult initialized =
					WorldInterfaceState.initialize(server, encounterId, layout, seed);
			if (!initialized.applied()) return DebugBossResult.ARENA_FAILED;
			snapshot = initialized.snapshot();
		}
		if (snapshot.stage() == WorldInterfaceStage.ARENA_READY) {
			WorldInterfaceState.MutationResult opened = WorldInterfaceState.transition(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(),
					WorldInterfaceStage.ARENA_READY, WorldInterfaceStage.WAITING_TERMINALS);
			if (!opened.applied()) return DebugBossResult.ARENA_FAILED;
			snapshot = opened.snapshot();
		}
		bindCore(end, snapshot);

		// The sacrifice needs a real bound terminal in hand; a tester who jumped the mainline will
		// not have one, and issuing it here is the same recovery the mod already performs on join.
		TerminalLifecycleService.ensureCarried(player, true);
		if (!rememberAndOverrideRespawn(player, snapshot)) return DebugBossResult.ARENA_FAILED;
		snapshot = WorldInterfaceState.snapshot(server);
		BlockPos altar = snapshot.altarCenter();
		if (!player.teleportTo(end, altar.getX() + 0.5D, altar.getY() + 1.0D, altar.getZ() + 2.5D,
				Set.of(), 180.0F, 0.0F, true)) return DebugBossResult.ARENA_FAILED;
		SurvivalProgressService.mark(player, SurvivalMilestone.ENTERED_END);

		WorldInterfaceRitualService.RitualResult deposited = WorldInterfaceRitualService.deposit(player,
				snapshot.encounterId().orElseThrow(), snapshot.revision());
		sendAltarSnapshots(server, deposited.snapshot(), altarStatus(deposited.reason()));
		if (deposited.snapshot().stage() == WorldInterfaceStage.SUMMONING) {
			sendEncounterSnapshots(server, true);
			return DebugBossResult.STARTED;
		}
		// The roster is every non-spectator online, so on a shared server the remaining players
		// still have to deposit; the arena and the altar are ready and waiting for them.
		return deposited.applied() ? DebugBossResult.WAITING_FOR_OTHERS : DebugBossResult.DEPOSIT_REJECTED;
	}

	public enum DebugBossResult {
		STARTED,
		WAITING_FOR_OTHERS,
		ALREADY_RUNNING,
		END_MISSING,
		ARENA_FAILED,
		DEPOSIT_REJECTED
	}

	/** Overrides only End-portal travel while a prepared world-interface encounter exists. */
	public static Optional<TeleportTransition> createPortalTransition(ServerLevel sourceLevel, Entity entity,
			BlockPos portalPosition) {
		if (sourceLevel.dimension() == Level.END || !(entity instanceof ServerPlayer player)
				|| player.isSpectator()) return Optional.empty();
		MinecraftServer server = sourceLevel.getServer();
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present() || snapshot.stage() == WorldInterfaceStage.COMPLETE) {
			return Optional.empty();
		}
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return Optional.empty();
		if (!rememberAndOverrideRespawn(player, snapshot)) return Optional.empty();
		SurvivalProgressService.mark(player, SurvivalMilestone.ENTERED_END);
		Vec3 arrival = Vec3.atBottomCenterOf(snapshot.safeSpawn());
		return Optional.of(new TeleportTransition(end, arrival, Vec3.ZERO, 180.0F, 0.0F,
				TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
	}

	/** Entity AI callback: movement and rendering projection only; attacks remain server-tick owned. */
	public static void tickBossEntity(ServerLevel level, WorldInterfaceEntity boss) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(level.getServer());
		if (!snapshot.valid() || !snapshot.present()
				|| snapshot.encounterId().filter(id -> id.equals(boss.encounterId())).isEmpty()
				|| snapshot.bossUuid().filter(id -> id.equals(boss.getUUID())).isEmpty()) {
			boss.discard();
			return;
		}
		if (snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			boss.discard();
			return;
		}
		int form = formForStage(snapshot.stage());
		// Until it reaches the top of the morph climb it is still the old body, so the form is not
		// pushed here: driveMorphFlight owns it for the length of the flight.
		if (driveMorphFlight(level, boss, snapshot, form)) return;
		boss.setForm(form);
		if (snapshot.stage() == WorldInterfaceStage.FAILURE_RESOLUTION) {
			driveFailureEscape(level, boss, snapshot);
			return;
		}
		if (snapshot.stage().isResolution() || snapshot.stage() == WorldInterfaceStage.PORTAL_OPEN) {
			boss.setDeltaMovement(Vec3.ZERO);
			return;
		}
		if (!snapshot.stage().isCombat()) {
			hoverAt(boss, snapshot.arenaCenter(), PRELUDE_HOVER_HEIGHT, level.getGameTime());
			return;
		}
		// Third form used to be excluded from the chase and left orbiting the arena centre thirty-four
		// blocks up, which put it out of reach of everything except a bow for the whole final phase.
		double hover = WorldInterfaceAnatomy.combatHoverHeight(form);
		ServerPlayer target = nearestArenaParticipant(level, snapshot, boss.position()).orElse(null);
		if (target == null) {
			hoverAt(boss, snapshot.arenaCenter(), hover, level.getGameTime());
			return;
		}
		double movement = 0.11D * WorldInterfacePolicy.movementMultiplier(snapshot.destroyedAnchorCount());
		// Stand off rather than sit on top of them. The chase used to aim at the player's own
		// column, so the interface parked directly overhead and a body twenty-five blocks across
		// simply contained them: nothing to face, nothing to back away from, and the core -- which
		// hangs off the front of the body -- pointing somewhere they were not. Held one body radius
		// plus a swing away, the near face lands at melee reach and the thing is in front of them.
		Vec3 flat = boss.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
		Vec3 approach = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, -1.0D) : flat.normalize();
		double standoff = WorldInterfaceAnatomy.massRadius(form) + 3.0D;
		Vec3 desired = target.position().add(approach.scale(standoff)).add(0.0D, hover, 0.0D);
		Vec3 delta = desired.subtract(boss.position());
		boss.setDeltaMovement(delta.lengthSqr() < 1.0D ? Vec3.ZERO : delta.normalize().scale(movement));
		faceTarget(boss, target.position());
	}

	/**
	 * Roar, from the core rather than from the entity position.
	 *
	 * <p>The position matters as much as the sound: the interface's origin is a bookkeeping point
	 * under the arena floor, and at third form that is some nineteen blocks below the body. Emitted
	 * there, a roar from the thing overhead came out of the ground.
	 */
	private static void roar(ServerLevel level, WorldInterfaceEntity boss, float volume) {
		int form = Math.clamp(boss.form(), 0, ROAR_PITCH_BY_FORM.length - 1);
		AudioService.playBounded(level, BlockPos.containing(WorldInterfaceAnatomy.coreOrigin(boss)),
				SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, volume,
				ROAR_PITCH_BY_FORM[form]);
	}

	/** Whether the interface is currently away on its morph flight, and so owed no other orders. */
	private static boolean isMorphing(ServerLevel level, WorldInterfaceEntity boss) {
		int action = boss.actionId();
		if (action != WorldInterfaceProtocol.BossAction.MORPH_TO_SECOND.wireId()
				&& action != WorldInterfaceProtocol.BossAction.MORPH_TO_THIRD.wireId()) return false;
		long elapsed = level.getGameTime() - boss.actionStartTick();
		return elapsed >= 0L && elapsed < MORPH_FLIGHT_TICKS;
	}

	/**
	 * Climb out of sight, change up there, and come back down as the next form.
	 *
	 * <p>Returns whether the flight is currently steering the body. The form deliberately does not
	 * change until the apex: swapping it on the tick the stage advanced meant the new silhouette
	 * appeared at ground level and the climb was the new body leaving rather than the old one.
	 */
	private static boolean driveMorphFlight(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, int targetForm) {
		if (!isMorphing(level, boss)) return false;
		long elapsed = level.getGameTime() - boss.actionStartTick();
		boss.setNoGravity(true);
		if (elapsed < MORPH_APEX_TICKS) {
			// Straight up, no steering. It is leaving, not manoeuvring.
			boss.setDeltaMovement(0.0D, MORPH_CLIMB_SPEED, 0.0D);
			return true;
		}
		boss.setForm(targetForm);
		Vec3 desired = morphReturnPoint(level, boss, snapshot, targetForm);
		Vec3 delta = desired.subtract(boss.position());
		boss.setDeltaMovement(delta.lengthSqr() < 1.0D ? Vec3.ZERO
				: delta.normalize().scale(MORPH_DIVE_SPEED));
		// Arrives already looking at whoever it is coming down on.
		nearestArenaParticipant(level, snapshot, boss.position())
				.ifPresent(player -> faceTarget(boss, player.position()));
		return true;
	}

	/** Where the new body is headed on the way down: its ordinary combat station, or the centre. */
	private static Vec3 morphReturnPoint(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, int form) {
		double hover = WorldInterfaceAnatomy.combatHoverHeight(form);
		ServerPlayer target = nearestArenaParticipant(level, snapshot, boss.position()).orElse(null);
		if (target == null) return snapshot.arenaCenter().getCenter().add(0.0D, hover, 0.0D);
		Vec3 flat = boss.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
		Vec3 approach = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, -1.0D) : flat.normalize();
		return target.position()
				.add(approach.scale(WorldInterfaceAnatomy.massRadius(form) + 3.0D))
				.add(0.0D, hover, 0.0D);
	}

	/**
	 * Turn the body toward whoever it is hunting.
	 *
	 * <p>Nothing ever set the interface's yaw. It kept whatever facing it span up with for the whole
	 * encounter, which meant it never looked at anybody, and {@link WorldInterfaceAnatomy#coreOrigin}
	 * -- which places the core off the front of the body by that yaw -- aimed the core, the laser
	 * origin and the orb feed at a fixed compass direction instead of at the fight.
	 *
	 * <p>Turned slowly on purpose: the telegraph shapes are read off the core, and a body that
	 * snapped to face a strafing player would swing them across the screen faster than they can be
	 * read. Slow enough to circle, fast enough that it is plainly tracking you.
	 */
	private static void faceTarget(WorldInterfaceEntity boss, Vec3 target) {
		Vec3 delta = target.subtract(boss.position());
		if (delta.x * delta.x + delta.z * delta.z < 1.0E-4D) return;
		float wanted = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
		float turned = Mth.approachDegrees(boss.getYRot(), wanted, BODY_TURN_DEGREES_PER_TICK);
		boss.setYRot(turned);
		boss.yBodyRot = turned;
		boss.yHeadRot = turned;
		boss.setYHeadRot(turned);
	}

	/**
	 * The interface's exit on a loss: it climbs out of the world rather than standing still.
	 *
	 * <p>A won encounter has a death to play - the body comes apart over the altar and the dragon
	 * arrives. A lost one had nothing. The thing that had just beaten a table simply froze where it
	 * was for eight and a half seconds and then blinked out of existence, which reads as the fight
	 * being switched off rather than as the interface being finished with them.</p>
	 *
	 * <p>So it leaves. It holds for a beat over the ruin it made, then rises - slowly at first, then
	 * faster than anything on the island can follow - straight up, until it is a point of light and
	 * then nothing. Nobody drove it off and it is not hurt; it is done looking, which is the worse
	 * reading and the correct one. The removal at the end of the resolution window still happens on
	 * exactly the same tick it always did, hundreds of blocks above anyone's head by then.</p>
	 */
	private static void driveFailureEscape(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot) {
		long started = snapshot.resolutionTick();
		long age = started < 0L ? 0L : level.getGameTime() - started;
		long climb = age - FAILURE_ESCAPE_HOLD_TICKS;
		if (climb <= 0L) {
			boss.setDeltaMovement(Vec3.ZERO);
			return;
		}
		// Squared ramp: the first second is a body detaching itself, the last is an ascent.
		double ramp = Math.min(1.0D, climb / (double) FAILURE_ESCAPE_RAMP_TICKS);
		boss.setDeltaMovement(0.0D, FAILURE_ESCAPE_TOP_SPEED * ramp * ramp, 0.0D);
		Vec3 core = WorldInterfaceAnatomy.coreOrigin(boss);
		double spread = WorldInterfaceAnatomy.massRadius(boss.form()) * 0.5D;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z,
				40, spread, 1.4D, spread, 0.18D);
		level.sendParticles(ParticleTypes.END_ROD, core.x, core.y - spread, core.z,
				24, spread * 0.7D, 0.8D, spread * 0.7D, 0.22D);
		if (climb % 10L == 0L) {
			// Fades with the climb, so the last thing anyone hears of it is already distant.
			AudioService.playBounded(level, BlockPos.containing(core), ModSounds.WORLD_INTERFACE_MORPH,
					SoundSource.HOSTILE, (float) (0.9D - ramp * 0.65D), (float) (0.6D + ramp * 0.55D));
		}
	}

	/** Routes an accepted player-authored hit into the persisted virtual health pool. */
	public static boolean applyVirtualDamage(WorldInterfaceEntity boss, DamageSource source, float rawAmount) {
		if (!(boss.level() instanceof ServerLevel level) || rawAmount <= 0.0F || !Float.isFinite(rawAmount)
				|| !(source.getEntity() instanceof ServerPlayer attacker)) return false;
		MinecraftServer server = level.getServer();
		Entity direct = source.getDirectEntity();
		if (direct != null && direct != attacker && duplicateProjectileHit(server, direct.getUUID(), level.getGameTime())) {
			return false;
		}
		boolean directMelee = direct == null || direct == attacker;
		boolean meleeClaimed = false;

		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
			if (!bossMatches(before, boss) || !before.stage().isCombat()
					|| attacker.isSpectator()) return false;
			long elapsed = effectiveActiveTicks(before, level.getGameTime());
			if (WorldInterfacePolicy.resolveTick(elapsed, false)
					== WorldInterfacePolicy.TickVerdict.FAILURE) {
				lockFailure(level, before);
				return false;
			}
			if (directMelee && !meleeClaimed) {
				if (duplicateMeleeHit(server, attacker.getUUID(), level.getGameTime())) return false;
				meleeClaimed = true;
			}
			double adjusted = WorldInterfacePolicy.adjustedIncomingDamage(rawAmount,
					before.destroyedAnchorCount());
			double remaining = Math.max(0.0D, before.virtualHealth() - adjusted);
			boolean lethal = remaining <= 0.0D;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					before.encounterId().orElseThrow(), before.revision(), state -> {
						long nowElapsed = effectiveActiveTicks(before, level.getGameTime());
						state.setClock(nowElapsed, before.runningSinceGameTime() >= 0L
								? level.getGameTime() : -1L);
						state.setVirtualHealth(before.maxVirtualHealth(), remaining);
						advanceToHealthStage(state, remaining / Math.max(1.0D, before.maxVirtualHealth()));
						if (lethal) {
							state.setClock(nowElapsed, -1L);
							advanceToPhaseThree(state);
							state.clearCurrentAttack();
							state.clearControlCooldowns();
							state.setGateState(WorldInterfaceGatewayState.GOLD);
							state.transitionTo(WorldInterfaceStage.SUCCESS_RESOLUTION);
							state.setResolution(0, level.getGameTime());
						}
					});
			if (!result.applied()) {
				if ("revision_mismatch".equals(result.reason())) continue;
				return false;
			}
			WorldInterfaceState.Snapshot after = result.snapshot();
			emitImpactBurst(level, boss, attacker, adjusted, directMelee);
			if (lethal) beginResolution(level, after, true);
			else if (after.stage() != before.stage()) phaseChanged(level, before, after);
			return true;
		}
		return false;
	}

	/** Returns empty for ordinary crystals so vanilla remains untouched. */
	public static Optional<Boolean> handleAnchorDamage(ServerLevel level, EndCrystal crystal,
			DamageSource source, float amount) {
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(level.getServer());
		if (!before.valid() || !before.present()) return Optional.empty();
		WorldInterfaceState.Anchor anchor = before.anchorForCrystal(crystal.getUUID()).orElse(null);
		if (anchor == null) return Optional.empty();
		if (amount <= 0.0F || !Float.isFinite(amount) || !before.stage().isCombat()
				|| !(source.getEntity() instanceof ServerPlayer player)
				|| player.isSpectator()) return Optional.of(false);
		if (anchor.destroyed()) {
			crystal.discard();
			return Optional.of(true);
		}
		// Vanilla trained everyone to "break the towers first"; the trade-off here is intentional
		// and must be argued about, not discovered post mortem. The very first anchor strike of an
		// encounter is therefore absorbed and answered with the cost, and only repeated strikes commit.
		UUID encounterId = before.encounterId().orElse(null);
		if (encounterId != null && ANCHOR_WARNINGS.computeIfAbsent(level.getServer(),
				ignored -> Collections.synchronizedSet(new HashSet<>())).add(encounterId)) {
			AudioService.playBounded(level, anchor.position(), ModSounds.WORLD_INTERFACE_ANCHOR,
					SoundSource.HOSTILE, 0.55F, 1.25F);
			// The absorbed strike has to look absorbed.
			//
			// Returning false here makes vanilla play the "no damage" attack sound and skip the
			// hit particles, which for someone swinging a sword is indistinguishable from having
			// missed - so the one deliberate free strike in the fight read as the anchor taking two
			// hits for no reason. A shield of particles on the crystal carries that on its own.
			BlockPos anchorPos = anchor.position();
			level.sendParticles(ParticleTypes.END_ROD, anchorPos.getX() + 0.5D,
					anchorPos.getY() + 1.2D, anchorPos.getZ() + 0.5D, 60, 1.1D, 1.1D, 1.1D, 0.22D);
			level.sendParticles(ParticleTypes.CRIT, anchorPos.getX() + 0.5D,
					anchorPos.getY() + 1.2D, anchorPos.getZ() + 0.5D, 24, 0.9D, 0.9D, 0.9D, 0.30D);
			// One line, not two. The absorbed swing and the trade-off it exists to state were split
			// across a private notice and a broadcast, so the player who swung read the same event
			// twice in two registers while everyone else read half of it.
			broadcast(level.getServer(), TerminalNoticePayload.TONE_ANCHOR,
					"message.thefourthfrequency.world_interface.anchor_warning");
			return Optional.of(false);
		}

		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			before = WorldInterfaceState.snapshot(level.getServer());
			anchor = before.anchorForCrystal(crystal.getUUID()).orElse(null);
			if (anchor == null) return Optional.empty();
			if (!before.stage().isCombat()) return Optional.of(false);
			if (anchor.destroyed()) {
				crystal.discard();
				return Optional.of(true);
			}
			int index = anchor.index();
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(level.getServer(),
					before.encounterId().orElseThrow(), before.revision(), state -> state.markAnchorDestroyed(index));
			if (!result.applied()) {
				if ("revision_mismatch".equals(result.reason())) continue;
				// Anything else silently ate a player's swing. Whatever invariant rejected it, the
				// player just watched a hit do nothing for a reason nobody can see, so it is worth
				// a line in the log rather than another mystery about how many hits an anchor takes.
				TheFourthFrequency.LOGGER.warn("Anchor {} refused destruction: {}",
						anchor.index(), result.reason());
				return Optional.of(false);
			}
			crystal.discard();
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, anchor.position().getX() + 0.5D,
					anchor.position().getY() + 0.5D, anchor.position().getZ() + 0.5D,
					72, 1.7D, 2.8D, 1.7D, 0.16D);
			AudioService.playBounded(level, anchor.position(), ModSounds.WORLD_INTERFACE_ANCHOR,
					SoundSource.HOSTILE, 0.9F, 0.72F);
			broadcast(level.getServer(), TerminalNoticePayload.TONE_ANCHOR,
					"message.thefourthfrequency.world_interface.anchor_destroyed",
					result.snapshot().aliveAnchorCount());
			if (WorldInterfacePolicy.hasTimedOut(effectiveActiveTicks(result.snapshot(),
					level.getGameTime()))) lockFailure(level, result.snapshot());
			sendEncounterSnapshots(level.getServer(), true);
			return Optional.of(true);
		}
		return Optional.of(false);
	}

	public static void handleAltarAction(ServerPlayer player, AltarActionC2S payload) {
		WorldInterfaceRitualService.RitualResult result = switch (payload.action()) {
			case DEPOSIT -> WorldInterfaceRitualService.deposit(player, payload.encounterId(), payload.expectedRevision());
			case WITHDRAW -> WorldInterfaceRitualService.withdraw(player, payload.encounterId(), payload.expectedRevision());
			case CANCEL -> WorldInterfaceRitualService.cancel(player, payload.encounterId(), payload.expectedRevision());
		};
		if (result.applied() && payload.action() == WorldInterfaceProtocol.AltarAction.DEPOSIT) {
			ServerLevel end = player.level().getServer().getLevel(Level.END);
			if (end != null) {
				for (BlockPos gate : result.snapshot().gates().stream().map(WorldInterfaceState.Gate::position).toList()) {
					end.sendParticles(ParticleTypes.PORTAL, gate.getX() + 0.5D, gate.getY() + 0.5D,
							gate.getZ() + 0.5D, 12, 0.7D, 2.0D, 0.7D, 0.05D);
				}
				AudioService.playBounded(end, result.snapshot().altarCenter(), ModSounds.WORLD_INTERFACE_TERMINAL,
						SoundSource.AMBIENT, 0.85F, result.snapshot().sacrificeCommitted() ? 0.72F : 1.0F);
			}
		}
		sendAltarSnapshots(player.level().getServer(), result.snapshot(), altarStatus(result.reason()));
		if (result.snapshot().stage() == WorldInterfaceStage.SUMMONING) {
			sendEncounterSnapshots(player.level().getServer(), true);
		}
	}

	public static void handlePoemComplete(ServerPlayer player, PoemCompleteC2S payload) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
		if (!before.valid() || !before.present()
				|| before.encounterId().filter(payload.encounterId()::equals).isEmpty()
				|| before.stage() != WorldInterfaceStage.PORTAL_OPEN) return;
		WorldInterfaceState.PoemLedgerEntry poem = before.poemLedger().get(player.getUUID());
		if (poem == null || !poem.started() || poem.acked() || poem.sequence() != payload.sequence()) return;
		WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
				payload.encounterId(), before.revision(), state -> state.putPoem(new WorldInterfaceState.PoemLedgerEntry(
						player.getUUID(), poem.sequence(), true, true, true)));
		if (!result.applied()) return;
		prepareVanillaEndReturn(player, result.snapshot());
		completeIfAllPoemsAcknowledged(server);
	}

	/** Opens the dedicated altar UI through a complete server snapshot. */
	public static boolean openAltar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()
				|| snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS) return false;
		ALTAR_VIEWERS.computeIfAbsent(server, ignored -> new HashSet<>()).add(player.getUUID());
		sendAltarSnapshot(player, snapshot, WorldInterfaceProtocol.AltarStatus.READY);
		return true;
	}

	/** Arms one authored poem immediately before the exit invokes vanilla End credits. */
	public static void startPoem(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
			if (!before.valid() || !before.present()
					|| (before.stage() != WorldInterfaceStage.PORTAL_OPEN
							&& before.stage() != WorldInterfaceStage.COMPLETE)
					|| before.outcome() == WorldInterfaceState.Outcome.NONE) return;
			WorldInterfaceState.PoemLedgerEntry existing = before.poemLedger().get(player.getUUID());
			prepareVanillaEndReturn(player, before);
			if (before.stage() == WorldInterfaceStage.COMPLETE
					|| !before.frozenRoster().contains(player.getUUID())
					|| existing != null && existing.acked()) {
				if (before.stage() == WorldInterfaceStage.PORTAL_OPEN) {
					completeIfAllPoemsAcknowledged(server);
				}
				return;
			}
			long sequence;
			if (existing != null && existing.started()) {
				sequence = existing.sequence();
			} else {
				sequence = nextClientSequence(player);
				WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
						before.encounterId().orElseThrow(), before.revision(), state -> state.putPoem(
								new WorldInterfaceState.PoemLedgerEntry(player.getUUID(), sequence, true, false, false)));
				if (!result.applied()) {
					if ("revision_mismatch".equals(result.reason())) continue;
					return;
				}
				before = result.snapshot();
			}
			rememberClientSequence(player, sequence);
			ServerPlayNetworking.send(player, new PoemStartS2C(before.encounterId().orElseThrow(), sequence,
					outcomeWire(before.outcome()), FrequencyWorldData.get(server).worldId()));
			return;
		}
	}

	private static void tickStart(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return;
		if (!RECOVERED_SERVERS.contains(server)) {
			recoverAfterRestart(server);
			snapshot = WorldInterfaceState.snapshot(server);
		}
		EndBossArenaService.suppressVanillaFight(level);
		WorldInterfaceShockwaveService.tick(level);
		if (snapshot.friendlyDragonUuid().isPresent()) {
			UUID dragonId = snapshot.friendlyDragonUuid().orElseThrow();
			// While the exit is being opened the dragon flies a low, fast circle over the altar; once
			// the way out exists it climbs back to the resting orbit and the island is theirs again.
			long resolutionAge = snapshot.resolutionTick() < 0L
					? 0L : level.getGameTime() - snapshot.resolutionTick();
			double approach = dragonApproach(snapshot, resolutionAge);
			if (!FriendlyDragonService.tick(level, dragonId, snapshot.arenaCenter(), approach)
					&& snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()
					&& snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS) {
				FriendlyDragonService.spawn(level, snapshot.arenaCenter(), dragonId);
			}
		}
		if (snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			removeBossProjection(level, snapshot);
			snapshot = clearFinishedBossIdentity(server, snapshot);
		}

		switch (snapshot.stage()) {
			case SUMMONING -> tickSummoning(level, snapshot);
			case PHASE_1, PHASE_2, PHASE_3 -> {
				tickCombat(level, snapshot);
				// Written as the fight happens, not afterwards: what the collapse timer has already
				// taken is real ground by the time anyone looks at it.
				commitErosion(level, snapshot);
			}
			case SUCCESS_RESOLUTION, FAILURE_RESOLUTION -> tickResolution(level, snapshot);
			case PORTAL_OPEN -> ensureExitOpen(level, snapshot);
			default -> { }
		}
		if (server.getTickCount() % SNAPSHOT_INTERVAL_TICKS == 0) {
			sendEncounterSnapshots(server, false);
			pruneAltarViewers(server);
		}
	}

	private static void tickSummoning(ServerLevel level, WorldInterfaceState.Snapshot before) {
		WorldInterfaceEntity boss = ensureBoss(level, before);
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(level.getServer());
		if (snapshot.resolutionTick() < 0L) {
			WorldInterfaceState.MutationResult started = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state ->
						state.setResolution(0, level.getGameTime()));
			if (started.applied()) snapshot = started.snapshot();
			showAction(level, snapshot, WorldInterfaceProtocol.BossAction.SUMMONING, SUMMON_DURATION_TICKS,
					List.of(), snapshot.deterministicSeed());
			AudioService.playBounded(level, snapshot.arenaCenter(), ModSounds.WORLD_INTERFACE_SUMMON,
					SoundSource.HOSTILE, 0.95F, 0.8F);
			WorldInterfaceShockwaveService.emit(level, snapshot.arenaCenter().getCenter().add(0.0D, 18.0D, 0.0D),
					WorldInterfaceShockwaveService.MORPH_DURATION_TICKS,
					WorldInterfaceShockwaveService.MORPH_MAX_RADIUS);
		}
		if (boss != null) {
			boss.setForm(WorldInterfaceEntity.FORM_LISTENING);
		}
		if (snapshot.resolutionTick() >= 0L
				&& level.getGameTime() - snapshot.resolutionTick() >= SUMMON_DURATION_TICKS) {
			boolean running = onlineFrozenCount(level.getServer(), snapshot) > 0;
			WorldInterfaceState.MutationResult transitioned = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> {
						state.transitionTo(WorldInterfaceStage.PHASE_1);
						state.setClock(0L, running ? level.getGameTime() : -1L);
						state.setActionSchedule(0L, 0, 40L);
						state.setResolution(0, -1L);
					});
			if (transitioned.applied()) {
				EndBossArenaService.setAnchorsInvulnerable(level, EndBossArenaService.prepare(level), false);
				if (boss != null) boss.clearAction();
				broadcast(level.getServer(), TerminalNoticePayload.TONE_ENCOUNTER,
						"message.thefourthfrequency.world_interface.combat_started");
				sendEncounterSnapshots(level.getServer(), true);
			}
		}
	}

	private static void tickCombat(ServerLevel level, WorldInterfaceState.Snapshot initial) {
		WorldInterfaceState.Snapshot snapshot = reconcileClock(level, initial);
		long elapsed = effectiveActiveTicks(snapshot, level.getGameTime());
		if (WorldInterfacePolicy.resolveTick(elapsed, false)
				== WorldInterfacePolicy.TickVerdict.FAILURE) {
			lockFailure(level, snapshot);
			return;
		}
		WorldInterfaceEntity boss = ensureBoss(level, snapshot);
		if (boss == null) return;
		boolean running = snapshot.runningSinceGameTime() >= 0L;
		if (running && snapshot.recoveryGraceTicks() > 0) {
			int graceBefore = snapshot.recoveryGraceTicks();
			WorldInterfaceState.MutationResult grace = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state ->
						state.setRecoveryGraceTicks(graceBefore - 1));
			if (grace.applied()) snapshot = grace.snapshot();
		}
		if (running && level.getServer().getTickCount() % HEAL_INTERVAL_TICKS == 0
				&& snapshot.virtualHealth() > 0.0D && snapshot.aliveAnchorCount() > 0) {
			healBoss(level, snapshot);
			snapshot = WorldInterfaceState.snapshot(level.getServer());
		}
		if (level.getServer().getTickCount() % HEAL_INTERVAL_TICKS == 0) {
			persistTerrainBudget(level, snapshot);
		}
		// The concrete nine-action executor is integrated below this invariant gate.
		if (running && snapshot.recoveryGraceTicks() == 0) tickAttacks(level, boss, snapshot, elapsed);
	}

	/** Half-width of the exit terrace; its rim is the stair ring. */
	private static final int EXIT_STAIR_EDGE = 3;
	private static final int FAILURE_EROSION_RADIUS = WorldInterfacePolicy.EROSION_RADIUS_BLOCKS;
	private static final int FAILURE_EROSION_DEPTH = WorldInterfacePolicy.EROSION_DEPTH;
	/**
	 * Sweep rate and ceiling.
	 *
	 * <p>A ninety-six block disc is about twenty-nine thousand columns, so the sweep has to move an
	 * order of magnitude faster than it did over forty or it will not finish inside the resolution
	 * at all. The block ceiling rises with it: at four thousand the commit ran out partway through
	 * and left a small patch of damage in the middle of a clean island, which is the exact failure
	 * this path exists to avoid.</p>
	 */
	/**
	 * Columns examined per tick, and the ceiling on committed blocks.
	 *
	 * <p>The commit runs across the whole fight now rather than being crammed into the resolution,
	 * which is the only way a hundred-and-sixty block disc is affordable at all: the same quarter of
	 * a million blocks that would need sixteen hundred writes a tick over the resolution needs about
	 * forty across six minutes of combat. The sweep loops - each pass re-examines columns it has
	 * already walked, because the threshold rises with the collapse timer and ground that survived
	 * one pass is eligible on the next.</p>
	 */
	private static final int FAILURE_EROSION_COLUMNS_PER_TICK = 160;
	private static final int FAILURE_EROSION_MAX_BLOCKS = 300_000;
	/** Columns healed per tick when a win reverses the erosion; deliberately far faster. */
	private static final int EROSION_HEAL_COLUMNS_PER_TICK = 900;
	/** Sweep cursor and spend, per encounter. Transient: a restart simply resumes from the start. */
	private static final Map<UUID, int[]> FAILURE_EROSION_PROGRESS = new ConcurrentHashMap<>();
	private static final Map<UUID, int[]> EROSION_HEAL_PROGRESS = new ConcurrentHashMap<>();

	/**
	 * Writes the failure erosion into the world for real.
	 *
	 * <p>The erosion was render-only: every missing-texture block a losing table watched spread
	 * across the island existed solely in that client's chunk meshes, and the moment the encounter
	 * cleared, the End snapped back to being pristine. Losing left no mark, which is the opposite of
	 * what losing to this thing is supposed to mean.</p>
	 *
	 * <p>Committed on the same threshold the client renders from, so the blocks that turn permanent
	 * are exactly the blocks that were already shown as gone - the world does not rearrange itself
	 * at the moment of failure. Bounded three ways: a forty-block radius, four surface layers per
	 * column, and a hard ceiling on total blocks; and it never touches anything
	 * {@link EndBossArenaService#canDestroy} protects, so bedrock, the altar, the exit and the
	 * anchors survive intact.</p>
	 */
	private static void commitErosion(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		UUID encounterId = snapshot.encounterId().orElse(null);
		if (encounterId == null) return;
		long elapsed = effectiveActiveTicks(snapshot, level.getGameTime());
		// What the collapse timer alone had already eaten by the time the fight ended.
		//
		// A won encounter returns its presentation erosion to zero on the spot, so reading the
		// live progress here would commit nothing at all on a win - and the damage six minutes of
		// collapse did to the island would evaporate the instant it was survived. The island wears
		// what the clock did to it either way; only a loss goes on past that to the full wash.
		float failureProgress = WorldInterfacePolicy.presentationErosionProgress(snapshot.stage(),
				elapsed, snapshot.resolutionTick(), level.getGameTime(), RESOLUTION_DURATION_TICKS);
		if (failureProgress <= 0.0F) return;
		int[] progress = FAILURE_EROSION_PROGRESS.computeIfAbsent(encounterId, ignored -> new int[2]);
		int span = FAILURE_EROSION_RADIUS * 2 + 1;
		if (progress[1] >= FAILURE_EROSION_MAX_BLOCKS) return;
		// The sweep wraps instead of stopping: the threshold climbs with the collapse timer, so a
		// column that was still intact on the last pass may not be on the next one.
		if (progress[0] >= span * span) progress[0] = 0;
		BlockPos center = snapshot.arenaCenter();
		BlockState proxy = ModBlocks.MISSING_TEXTURE_PROXY.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
		int examined = 0;
		while (examined < FAILURE_EROSION_COLUMNS_PER_TICK && progress[0] < span * span
				&& progress[1] < FAILURE_EROSION_MAX_BLOCKS) {
			int index = progress[0]++;
			examined++;
			int dx = index % span - FAILURE_EROSION_RADIUS;
			int dz = index / span - FAILURE_EROSION_RADIUS;
			if (dx * dx + dz * dz > FAILURE_EROSION_RADIUS * FAILURE_EROSION_RADIUS) continue;
			int x = center.getX() + dx;
			int z = center.getZ() + dz;
			if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) continue;
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			for (int depth = 0; depth < FAILURE_EROSION_DEPTH; depth++) {
				BlockPos position = new BlockPos(x, surface - depth, z);
				if (position.getY() <= level.getMinY()) break;
				// End stone only, and that is what makes a win able to undo this.
				//
				// Recording the original state of a quarter of a million positions so they could be
				// put back costs megabytes and does not survive a restart. Restricting the erosion
				// to the one block the island is actually made of means the reversal needs no record
				// at all: every proxy inside the disc was end stone, so healing is a single
				// substitution. The obsidian pillars keep their own material and stand out against
				// the corrupted ground rather than dissolving into it.
				BlockState state = level.getBlockState(position);
				if (!state.is(Blocks.END_STONE)) continue;
				if (!WorldInterfacePolicy.erodesAt(encounterId, position.asLong(),
						failureProgress)) continue;
				if (!EndBossArenaService.canDestroy(level, position, state)) continue;
				level.setBlock(position, proxy, flags);
				progress[1]++;
			}
		}
	}

	/**
	 * Runs the erosion backwards after a win.
	 *
	 * <p>A losing table keeps the island the countdown left them. A winning one gets it back - but
	 * not by having it blink: the heal sweeps outward from the altar as a front, so cutting the
	 * interface visibly restores the world's materials, several times faster than the six minutes
	 * it took to lose them.</p>
	 *
	 * <p>Every proxy inside the disc was end stone before the erosion touched it, so this needs no
	 * record of what it is undoing.</p>
	 */
	private static void healErosion(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		UUID encounterId = snapshot.encounterId().orElse(null);
		if (encounterId == null) return;
		int[] progress = EROSION_HEAL_PROGRESS.computeIfAbsent(encounterId, ignored -> new int[1]);
		int span = FAILURE_EROSION_RADIUS * 2 + 1;
		if (progress[0] >= span * span) return;
		BlockPos center = snapshot.arenaCenter();
		BlockState endStone = Blocks.END_STONE.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
		int examined = 0;
		while (examined < EROSION_HEAL_COLUMNS_PER_TICK && progress[0] < span * span) {
			int index = progress[0]++;
			examined++;
			int dx = index % span - FAILURE_EROSION_RADIUS;
			int dz = index / span - FAILURE_EROSION_RADIUS;
			if (dx * dx + dz * dz > FAILURE_EROSION_RADIUS * FAILURE_EROSION_RADIUS) continue;
			int x = center.getX() + dx;
			int z = center.getZ() + dz;
			if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) continue;
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			for (int depth = 0; depth < FAILURE_EROSION_DEPTH; depth++) {
				BlockPos position = new BlockPos(x, surface - depth, z);
				if (position.getY() <= level.getMinY()) break;
				if (!level.getBlockState(position).is(ModBlocks.MISSING_TEXTURE_PROXY)) continue;
				level.setBlock(position, endStone, flags);
			}
		}
	}

	private static void tickResolution(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		if (snapshot.stage() == WorldInterfaceStage.FAILURE_RESOLUTION) commitErosion(level, snapshot);
		else healErosion(level, snapshot);
		WorldInterfaceEntity boss = ensureBoss(level, snapshot);
		if (boss != null) {
			boss.setForm(WorldInterfaceEntity.FORM_INTERFACE);
			// A loss is the one resolution the body still moves through - it climbs out of the world.
			// driveFailureEscape owns its motion for the whole window; pinning it here would fight
			// that from a second tick source and the result would depend on registration order.
			if (snapshot.stage() != WorldInterfaceStage.FAILURE_RESOLUTION) {
				boss.setDeltaMovement(Vec3.ZERO);
			}
		}
		long started = snapshot.resolutionTick() < 0L ? level.getGameTime() : snapshot.resolutionTick();
		if (snapshot.resolutionTick() < 0L) {
			WorldInterfaceState.MutationResult repaired = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.setResolution(0, started));
			if (!repaired.applied()) return;
			snapshot = repaired.snapshot();
		}
		synchronizeResolutionAction(level, snapshot, boss, false);
		long age = level.getGameTime() - started;
		boolean success = snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS;
		if (age >= ANCHORS_SKYWARD_TICKS && snapshot.resolutionStep() < 1) {
			if (success) pointLivingAnchorsSkyward(level, snapshot);
			WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.setResolution(1, started));
			if (step.applied()) snapshot = step.snapshot();
		}
		// The body goes before anything replaces it. Discarding the projection here rather than at
		// the end of the window is the whole reordering: the collapse and fade clips have finished
		// by now, so what the players watch is the thing they killed stopping, and then an empty sky.
		if (success && age >= BOSS_REMOVAL_TICKS && snapshot.resolutionStep() < 2) {
			WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> {
						state.clearBossUuid();
						state.setResolution(2, started);
					});
			if (step.applied()) {
				snapshot = step.snapshot();
				removeBossProjection(level, snapshot);
				sendEncounterSnapshots(level.getServer(), true);
			}
		}
		if (!success && age >= 80L && snapshot.resolutionStep() < 2) {
			WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.setResolution(2, started));
			if (step.applied()) snapshot = step.snapshot();
		}
		if (success && age >= DRAGON_SPAWN_TICKS && snapshot.resolutionStep() < 3) {
			UUID dragonId = snapshot.friendlyDragonUuid().orElse(deterministicEntityUuid(
					"friendly_dragon", snapshot.encounterId().orElseThrow()));
			WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> {
						state.setFriendlyDragonUuid(dragonId);
						state.setResolution(3, started);
					});
			if (step.applied()) {
				snapshot = step.snapshot();
				// Spawn only. Speaking on the same tick puts the words on screen before the dragon
				// has been sent to a single client, so the line reads as coming from nothing.
				FriendlyDragonService.spawn(level, snapshot.arenaCenter(), dragonId);
			}
		}
		if (success && age >= DRAGON_FIRST_LINE_TICKS && snapshot.resolutionStep() < 4) {
			WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(),
					state -> state.setResolution(4, started));
			if (step.applied()) {
				snapshot = step.snapshot();
				broadcast(level.getServer(), TerminalNoticePayload.TONE_DRAGON,
						"message.thefourthfrequency.world_interface.dragon.thanks");
			}
		}
		// The exit is opened, not switched on - and it is opened by the dragon, not by the altar.
		// The particles and the sound are emitted along the line between the two, so the way out is
		// visibly something the thing they spared is doing for them.
		if (success && age >= DRAGON_SPAWN_TICKS && age < SUCCESS_PORTAL_TICKS) {
			emitPortalOpening(level, snapshot, age);
		}
		int finalTick = success ? SUCCESS_PORTAL_TICKS : RESOLUTION_DURATION_TICKS;
		if (age >= finalTick && snapshot.resolutionStep() < 6) {
			removeBossProjection(level, snapshot);
			BlockPos exitPosition = snapshot.altarCenter();
			placeExit(level, exitPosition);
			if (success) emitPortalBurst(level, exitPosition);
			WorldInterfaceState.MutationResult opened = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> {
						state.clearBossUuid();
						state.setExit(exitPosition, true);
						state.setResolution(6, started);
						state.transitionTo(WorldInterfaceStage.PORTAL_OPEN);
					});
			if (opened.applied()) {
				// The second line lands on the tick the exit exists, so "go back" is said to people
				// who can already see the way back.
				if (success) {
					broadcast(level.getServer(), TerminalNoticePayload.TONE_DRAGON,
							"message.thefourthfrequency.world_interface.dragon.return");
				}
				sendEncounterSnapshots(level.getServer(), true);
			}
		}
	}

	/** How far the dragon has come down onto the altar, in [0, 1]. */
	private static double dragonApproach(WorldInterfaceState.Snapshot snapshot, long age) {
		if (snapshot.outcome() != WorldInterfaceState.Outcome.SUCCESS) return 0.0D;
		if (snapshot.stage() != WorldInterfaceStage.SUCCESS_RESOLUTION) return 0.0D;
		if (age < DRAGON_SPAWN_TICKS) return 0.0D;
		return Math.clamp((age - DRAGON_SPAWN_TICKS) / (double) DRAGON_PORTAL_WORK_TICKS, 0.0D, 1.0D);
	}

	/**
	 * The dragon prising the exit open: a thread drawn from wherever it currently is down to the
	 * altar, and a ring that tightens on the altar as it works.
	 *
	 * <p>The ring alone used to be the whole effect, anchored on the altar and running on its own
	 * clock. That is a portal switching itself on next to a dragon. Sourcing the particles at the
	 * dragon's actual position each tick is what makes it the dragon's doing - and because the
	 * dragon is descending across the same window, the thread shortens as the ring closes.</p>
	 */
	private static void emitPortalOpening(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			long age) {
		BlockPos altar = snapshot.altarCenter();
		float progress = Math.clamp((age - DRAGON_SPAWN_TICKS)
				/ (float) DRAGON_PORTAL_WORK_TICKS, 0.0F, 1.0F);
		double radius = 0.7D + 6.5D * (1.0D - progress);
		double height = altar.getY() + 1.2D + 3.4D * (1.0D - progress);
		for (int point = 0; point < 14; point++) {
			double angle = point / 14.0D * Math.PI * 2.0D + age * 0.13D;
			level.sendParticles(ParticleTypes.PORTAL,
					altar.getX() + 0.5D + Math.cos(angle) * radius, height,
					altar.getZ() + 0.5D + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
		Vec3 source = snapshot.friendlyDragonUuid()
				.flatMap(id -> FriendlyDragonService.recover(level, id))
				.map(dragon -> dragon.position().add(0.0D, -1.0D, 0.0D))
				.orElse(null);
		if (source != null) {
			Vec3 target = new Vec3(altar.getX() + 0.5D, altar.getY() + 1.2D, altar.getZ() + 0.5D);
			Vec3 span = target.subtract(source);
			for (int step = 0; step < 10; step++) {
				double along = (step + (age % 4L) * 0.25D) / 10.0D;
				Vec3 point = source.add(span.scale(along));
				level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z,
						1, 0.10D, 0.10D, 0.10D, 0.0D);
			}
		}
		if (age % 12L == 0L) {
			AudioService.playBounded(level, altar, ModSounds.WORLD_INTERFACE_GATEWAY_PURPLE,
					SoundSource.AMBIENT, 0.5F, 0.7F + 0.5F * progress);
		}
	}

	private static void emitPortalBurst(ServerLevel level, BlockPos exit) {
		level.sendParticles(ParticleTypes.END_ROD, exit.getX() + 0.5D, exit.getY() + 1.0D,
				exit.getZ() + 0.5D, 120, 1.6D, 0.6D, 1.6D, 0.32D);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, exit.getX() + 0.5D, exit.getY() + 0.6D,
				exit.getZ() + 0.5D, 160, 2.2D, 1.4D, 2.2D, 0.24D);
		AudioService.playBounded(level, exit, ModSounds.WORLD_INTERFACE_GATEWAY_GOLD,
				SoundSource.AMBIENT, 0.95F, 1.0F);
	}

	private static void tickAttacks(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, long elapsed) {
		MinecraftServer server = level.getServer();
		List<ServerPlayer> participants = arenaParticipants(level, snapshot);
		// Nothing is thrown while it is away changing. The phase change already cancels whatever was
		// running; without this the scheduler would simply open the next attack on the following tick
		// and fire it out of an empty sky from a hundred blocks up.
		if (isMorphing(level, boss)) return;
		// Idle roar. Off the encounter clock rather than a timer of its own so it stays in step with
		// everything else the fight schedules, and skipped above while it is away changing.
		if (elapsed > 0L && elapsed % ROAR_INTERVAL_TICKS == 0L) roar(level, boss, 0.85F);
		// The third form's second lane, driven whether or not a scheduled attack is running: it is
		// what makes the last phase continuous instead of a turn order. Ticked before the scheduled
		// lane so a volley opened this tick gets its first frame on this tick.
		if (snapshot.stage() == WorldInterfaceStage.PHASE_3
				&& elapsed % WorldInterfaceActionScheduler.VOLLEY_INTERVAL_TICKS == 0L) {
			WorldInterfaceAttackService.beginVolley(level, boss, snapshot, participants, elapsed,
					snapshot.actionSequence());
		}
		WorldInterfaceAttackService.tickVolley(level, boss, snapshot, elapsed);
		if (snapshot.currentAttack().isPresent()) {
			WorldInterfaceState.AttackEnvelope active = snapshot.currentAttack().orElseThrow();
			WorldInterfaceAttackService.AttackTick tick =
					WorldInterfaceAttackService.tick(level, boss, snapshot, elapsed);
			WorldInterfaceState.Snapshot latest = WorldInterfaceState.snapshot(server);
			if (!latest.stage().isCombat() || latest.encounterId().isEmpty()) return;
			for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
				latest = WorldInterfaceState.snapshot(server);
				WorldInterfaceState.AttackEnvelope stored = latest.currentAttack().orElse(null);
				if (stored == null || stored.sequence() != active.sequence()) return;
				WorldInterfaceState.Snapshot captured = latest;
				WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
						latest.encounterId().orElseThrow(), latest.revision(), state -> {
							switch (tick.status()) {
								case CONTINUE -> state.setCurrentAttack(tick.replacementEnvelope().orElseThrow());
								case COMPLETE -> state.clearCurrentAttack();
								case CANCELLED_RESTART -> {
									state.clearCurrentAttack();
									state.setRecoveryGraceTicks(WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS);
									state.setActionSchedule(captured.actionSequence(), captured.lastActionWireId(),
											Math.max(captured.nextActionActiveTick(),
													elapsed + WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS));
								}
							}
						});
				if (result.applied()) {
					if (tick.status() == WorldInterfaceAttackService.AttackStatus.CANCELLED_RESTART) {
						WorldInterfaceAttackService.cancelAndRestore(server, latest.encounterId().orElseThrow());
					}
					return;
				}
				if (!"revision_mismatch".equals(result.reason())) return;
			}
			return;
		}
		if (elapsed < snapshot.nextActionActiveTick()) return;
		if (participants.isEmpty()) return;

		WorldInterfaceAction previous = WorldInterfaceAction
				.fromWireIdOrEmpty(snapshot.lastActionWireId()).orElse(null);
		long choiceSequence = snapshot.actionSequence();
		WorldInterfaceAction selected = null;
		for (int scan = 0; scan < WorldInterfaceAction.values().length * 2; scan++, choiceSequence++) {
			WorldInterfaceAction candidate = WorldInterfaceActionScheduler.nextAction(snapshot.stage(),
					snapshot.deterministicSeed(), choiceSequence, previous);
			if (candidate != WorldInterfaceAction.FORCED_EVICTION
					|| WorldInterfaceActionScheduler.isForcedEvictionReady(elapsed,
							snapshot.lastForcedEvictionTick(), participants.size())) {
				selected = candidate;
				break;
			}
		}
		if (selected == null) return;

		List<ServerPlayer> targets = attackTargets(server, snapshot, participants, selected,
				choiceSequence, elapsed);
		if (selected != WorldInterfaceAction.TENDRIL_LASH && targets.isEmpty()) return;
		WorldInterfaceAttackService.AttackStart started;
		try {
			started = WorldInterfaceAttackService.begin(level, boss, snapshot, selected, targets,
					elapsed, choiceSequence);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return;
		}

		WorldInterfaceState.Snapshot latest = WorldInterfaceState.snapshot(server);
		long nextTick = elapsed + WorldInterfaceActionScheduler.scaledIntervalTicks(latest.stage(),
				latest.deterministicSeed(), choiceSequence, latest.destroyedAnchorCount());
		WorldInterfaceAction selectedAction = selected;
		long persistedSequence = choiceSequence;
		WorldInterfaceState.Snapshot captured = latest;
		WorldInterfaceState.MutationResult stored = WorldInterfaceState.mutate(server,
				latest.encounterId().orElseThrow(), latest.revision(), state -> {
					if (state.currentAttack().isPresent()) throw new IllegalStateException("attack_already_stored");
					state.setCurrentAttack(started.envelope());
					state.setActionSchedule(persistedSequence + 1L, selectedAction.wireId(), nextTick);
					if (selectedAction.requiresExclusiveControl()) {
						for (Map.Entry<UUID, Long> cooldown : captured.controlCooldowns().entrySet()) {
							if (cooldown.getValue() <= elapsed) {
								state.removeControlCooldown(cooldown.getKey());
							}
						}
						for (UUID target : started.targets()) {
							state.putControlCooldown(target,
									elapsed + WorldInterfaceActionScheduler.STRONG_CONTROL_IMMUNITY_TICKS);
						}
					}
					if (selectedAction == WorldInterfaceAction.FORCED_EVICTION) {
						state.setLastForcedEvictionTick(elapsed);
					}
				});
		if (!stored.applied()) {
			WorldInterfaceAttackService.cancelAndRestore(server, latest.encounterId().orElseThrow());
			return;
		}
		showAction(level, stored.snapshot(), bossActionWire(selectedAction), started.durationTicks(),
				started.targets().stream().sorted(Comparator.comparing(UUID::toString)).toList(), started.seed());
	}

	/** Everyone inside the arena an attack may be aimed at, in a stable order. */
	private static List<ServerPlayer> arenaParticipants(ServerLevel level,
			WorldInterfaceState.Snapshot snapshot) {
		return level.players().stream()
				.filter(player -> player.isAlive() && !player.isSpectator())
				.filter(player -> player.distanceToSqr(snapshot.arenaCenter().getCenter())
						<= ENCOUNTER_VISIBILITY_RADIUS_SQR)
				.sorted(Comparator.comparing(player -> player.getUUID().toString()))
				.limit(WorldInterfacePolicy.MAX_ROSTER_SIZE).toList();
	}

	private static List<ServerPlayer> attackTargets(MinecraftServer server,
			WorldInterfaceState.Snapshot snapshot, List<ServerPlayer> participants,
			WorldInterfaceAction action, long sequence, long elapsed) {
		if (action == WorldInterfaceAction.TENDRIL_LASH) return participants;
		if (action == WorldInterfaceAction.FORCED_EVICTION) {
			UUID host = participants.stream().filter(player -> server.isSingleplayerOwner(player.nameAndId()))
					.map(ServerPlayer::getUUID).findFirst().orElse(null);
			List<UUID> selected = WorldInterfaceActionScheduler.selectForcedEvictionTargets(
					participants.stream().map(ServerPlayer::getUUID).toList(), host,
					snapshot.deterministicSeed(), sequence);
			return selected.stream().map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull).toList();
		}
		List<ServerPlayer> eligible = participants;
		if (action.requiresExclusiveControl()) {
			eligible = participants.stream().filter(player ->
					elapsed >= snapshot.controlCooldowns().getOrDefault(player.getUUID(), 0L)).toList();
		}
		if (eligible.isEmpty()) return List.of();
		int first = Math.floorMod((int) (snapshot.deterministicSeed() ^ sequence), eligible.size());
		if (action == WorldInterfaceAction.GRAB_THROW && eligible.size() > 1) {
			return List.of(eligible.get(first), eligible.get((first + 1) % eligible.size()));
		}
		return List.of(eligible.get(first));
	}

	private static WorldInterfaceProtocol.BossAction bossActionWire(WorldInterfaceAction action) {
		return WorldInterfaceProtocol.BossAction.fromWireId(action.wireId());
	}

	private static void healBoss(ServerLevel level, WorldInterfaceState.Snapshot before) {
		double healing = WorldInterfacePolicy.healingPerTick(before.maxVirtualHealth(), before.aliveAnchorCount())
				* HEAL_INTERVAL_TICKS;
		double healed = Math.min(before.maxVirtualHealth(), before.virtualHealth() + healing);
		if (healed <= before.virtualHealth()) return;
		WorldInterfaceState.mutate(level.getServer(), before.encounterId().orElseThrow(), before.revision(),
				state -> state.setVirtualHealth(before.maxVirtualHealth(), healed));
	}

	private static WorldInterfaceState.Snapshot reconcileClock(ServerLevel level,
			WorldInterfaceState.Snapshot before) {
		boolean shouldRun = onlineFrozenCount(level.getServer(), before) > 0;
		if (shouldRun == (before.runningSinceGameTime() >= 0L)) return before;
		long elapsed = effectiveActiveTicks(before, level.getGameTime());
		WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(level.getServer(),
				before.encounterId().orElseThrow(), before.revision(), state ->
						state.setClock(elapsed, shouldRun ? level.getGameTime() : -1L));
		return result.applied() ? result.snapshot() : WorldInterfaceState.snapshot(level.getServer());
	}

	private static void lockFailure(ServerLevel level, WorldInterfaceState.Snapshot before) {
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			before = WorldInterfaceState.snapshot(level.getServer());
			if (!before.stage().isCombat()) return;
			long elapsed = effectiveActiveTicks(before, level.getGameTime());
			if (!WorldInterfacePolicy.hasTimedOut(elapsed)) return;
			WorldInterfaceState.Snapshot captured = before;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(level.getServer(),
					before.encounterId().orElseThrow(), before.revision(), state -> {
						state.setClock(elapsed, -1L);
						advanceToPhaseThree(state);
						state.clearCurrentAttack();
						state.clearControlCooldowns();
						state.setGateState(WorldInterfaceGatewayState.RED);
						state.transitionTo(WorldInterfaceStage.FAILURE_RESOLUTION);
						state.setResolution(0, level.getGameTime());
					});
			if (!result.applied()) {
				if ("revision_mismatch".equals(result.reason())) continue;
				return;
			}
			beginResolution(level, result.snapshot(), false);
			return;
		}
	}

	private static void beginResolution(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			boolean success) {
		// Only a win stops the world being torn. Cancelling the queue on a loss threw away the
		// damage the interface was in the middle of doing, so the island a player was evicted from
		// ended up tidier than the one they were still fighting on. On failure the pending scars
		// keep draining through the ordinary tick until the whole budget has landed, and every
		// committed edit is permanent either way.
		if (success) EndBossArenaService.cancelQueuedScars(level);
		WorldInterfaceAttackService.cancelAndRestore(level.getServer(), snapshot.encounterId().orElseThrow());
		WorldInterfaceEntity boss = findBoss(level, snapshot).orElse(null);
		if (boss != null) {
			boss.setForm(WorldInterfaceEntity.FORM_INTERFACE);
			boss.showAction(success ? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH.wireId()
					: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE.wireId(),
					level.getGameTime(), RESOLUTION_DURATION_TICKS);
			// The death cue was declared, registered, shipped - and never played. The boss dies by
			// its virtual pool reaching zero, which never routes through LivingEntity#die, so the
			// getDeathSound() this entity returns was unreachable. Fired here, at the body, which
			// is the moment it actually dies as far as the encounter is concerned.
			if (success) {
				AudioService.playBounded(level, BlockPos.containing(WorldInterfaceAnatomy.coreOrigin(boss)),
						ModSounds.WORLD_INTERFACE_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
			}
			boss.setDeltaMovement(Vec3.ZERO);
		}
		showAction(level, snapshot, success ? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH
				: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE, RESOLUTION_DURATION_TICKS,
				List.of(), snapshot.deterministicSeed() ^ snapshot.actionSequence());
		AudioService.playBounded(level, snapshot.arenaCenter(), success
				? ModSounds.WORLD_INTERFACE_SUCCESS : ModSounds.WORLD_INTERFACE_FAILURE,
				SoundSource.HOSTILE, 1.0F, success ? 1.0F : 0.62F);
		broadcast(level.getServer(), TerminalNoticePayload.TONE_ENCOUNTER,
				success ? "message.thefourthfrequency.world_interface.success_locked"
						: "message.thefourthfrequency.world_interface.failure_locked");
		if (success) markDefeatedMilestone(level.getServer(), snapshot.frozenRoster());
		sendEncounterSnapshots(level.getServer(), true);
	}

	private static void synchronizeResolutionAction(ServerLevel level,
			WorldInterfaceState.Snapshot snapshot, WorldInterfaceEntity boss, boolean broadcast) {
		if (snapshot.stage() != WorldInterfaceStage.SUCCESS_RESOLUTION
				&& snapshot.stage() != WorldInterfaceStage.FAILURE_RESOLUTION) return;
		WorldInterfaceProtocol.BossAction action = snapshot.stage() == WorldInterfaceStage.SUCCESS_RESOLUTION
				? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH
				: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE;
		long started = snapshot.resolutionTick() < 0L ? level.getGameTime() : snapshot.resolutionTick();
		if (boss != null && (boss.actionId() != action.wireId()
				|| boss.actionStartTick() != started || boss.actionDuration() != RESOLUTION_DURATION_TICKS)) {
			boss.showAction(action.wireId(), started, RESOLUTION_DURATION_TICKS);
		}
		if (!broadcast) return;
		for (ServerPlayer player : encounterRecipients(level.getServer(), snapshot)) {
			sendResolutionAction(player, snapshot, action, started);
		}
	}

	private static void sendResolutionAction(ServerPlayer player, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.BossAction action, long started) {
		ServerPlayNetworking.send(player, new BossActionS2C(snapshot.encounterId().orElseThrow(),
				nextClientSequence(player), action.wireId(), started, RESOLUTION_DURATION_TICKS,
				List.of(), snapshot.deterministicSeed() ^ snapshot.actionSequence(), 0));
	}

	/**
	 * Server-authored contact spray. The virtual-health model means a landed hit produces no vanilla
	 * knockback or hurt animation, so without this the only feedback was a five-tick texture swap
	 * driven off a snapshot diff - which a low snapshot rate could drop entirely.
	 */
	private static void emitImpactBurst(ServerLevel level, WorldInterfaceEntity boss,
			ServerPlayer attacker, double adjusted, boolean melee) {
		// The box no longer stands on the entity position - it is lifted to hug the drawn mass - so
		// the centre of the body is the centre of the box, not half a box-height off the origin.
		Vec3 centre = boss.getBoundingBox().getCenter();
		Vec3 eye = attacker.getEyePosition();
		// A melee hit lands on a limb at head height, not on the mass overhead. Spraying at the body
		// centre put the only confirmation a player gets tens of blocks above where they swung.
		Vec3 contact;
		if (melee) {
			contact = eye.add(attacker.getLookAngle().scale(MELEE_CONTACT_REACH));
		} else {
			Vec3 toward = eye.subtract(centre);
			double length = toward.length();
			contact = length < 1.0E-3D ? centre
					: centre.add(toward.scale(Math.min(1.0D, boss.getBbWidth() * 0.5D / length)));
		}
		int count = Math.clamp(6 + (int) Math.round(adjusted * 1.5D), 6, 28);
		level.sendParticles(ParticleTypes.CRIT, contact.x, contact.y, contact.z,
				count, 0.55D, 0.55D, 0.55D, 0.22D);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, contact.x, contact.y, contact.z,
				count / 2, 0.45D, 0.45D, 0.45D, 0.08D);

		// The boss had a hurt sound declared and never once played it.
		//
		// WorldInterfaceEntity#hurtServer is overridden to route everything into the virtual health
		// pool and never calls super, so LivingEntity's own "play getHurtSound()" branch is dead
		// code for this entity. A thousand points of damage landed in silence, and at any range
		// where the health bar is the only other feedback a landed hit was indistinguishable from
		// a missed one. Played at the contact rather than at the entity position, which for the
		// third form is twenty-five blocks below the mass.
		//
		// Pitch falls as the hit gets heavier, so a charged blow is audibly worth more than a poke.
		AudioService.playBounded(level, BlockPos.containing(contact), ModSounds.WORLD_INTERFACE_HURT,
				SoundSource.HOSTILE, 0.85F, 1.12F - (float) Math.min(0.30D, adjusted * 0.02D));
	}

	private static void phaseChanged(ServerLevel level, WorldInterfaceState.Snapshot before,
			WorldInterfaceState.Snapshot after) {
		WorldInterfaceAttackService.cancelAndRestore(level.getServer(), after.encounterId().orElseThrow());
		WorldInterfaceEntity boss = findBoss(level, after).orElse(null);
		WorldInterfaceProtocol.BossAction action = after.stage() == WorldInterfaceStage.PHASE_2
				? WorldInterfaceProtocol.BossAction.MORPH_TO_SECOND
				: WorldInterfaceProtocol.BossAction.MORPH_TO_THIRD;
		if (boss != null) {
			// The form itself is left alone here. driveMorphFlight changes it at the apex of the
			// climb, so the body that leaves the ground is the one the players were just fighting.
			boss.showAction(action.wireId(), level.getGameTime(), MORPH_FLIGHT_TICKS);
		}
		showAction(level, after, action, MORPH_FLIGHT_TICKS, List.of(),
				after.deterministicSeed() ^ after.stage().wireId());
		// Played at the interface, not at the arena centre. The third form hunts, so by the time it
		// morphs it can be a hundred blocks from the middle of the island - and both of these cues
		// were being emitted at a point nobody was standing near, which is why a transformation
		// that visibly tears the body apart happened in silence.
		BlockPos morphAt = boss == null ? after.arenaCenter()
				: BlockPos.containing(WorldInterfaceAnatomy.coreOrigin(boss));
		AudioService.playBounded(level, morphAt, ModSounds.WORLD_INTERFACE_MORPH,
				SoundSource.HOSTILE, 0.92F, after.stage() == WorldInterfaceStage.PHASE_2 ? 0.86F : 0.68F);
		// Layered under the morph cue: the shell coming apart and closing again, which is the part
		// the renderer's pinch is actually showing. Particles fill the gap the body vacates.
		AudioService.playBounded(level, morphAt, ModSounds.WORLD_INTERFACE_FORM_SHIFT,
				SoundSource.HOSTILE, 0.88F, after.stage() == WorldInterfaceStage.PHASE_2 ? 1.0F : 0.82F);
		// Roared at full volume as it goes up, still in the old body's register: the last thing
		// heard from the form that just lost, before it comes back down as something else.
		if (boss != null) roar(level, boss, 1.0F);
		if (boss != null) {
			Vec3 body = boss.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, body.x, body.y, body.z,
					220, boss.getBbWidth() * 0.6D, boss.getBbHeight() * 0.35D,
					boss.getBbWidth() * 0.6D, 0.22D);
		}
		// A morph is the only moment the encounter changes its own rules; give it a world event
		// rather than leaving the phase change legible only through the HUD label.
		WorldInterfaceShockwaveService.emit(level, boss == null ? after.arenaCenter().getCenter()
						: boss.getBoundingBox().getCenter(),
				WorldInterfaceShockwaveService.MORPH_DURATION_TICKS,
				WorldInterfaceShockwaveService.MORPH_MAX_RADIUS);
		broadcast(level.getServer(), TerminalNoticePayload.TONE_ENCOUNTER,
				"message.thefourthfrequency.world_interface.phase." + after.stage().serializedName());
		sendEncounterSnapshots(level.getServer(), true);
	}

	private static WorldInterfaceEntity ensureBoss(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceEntity found = findBoss(level, snapshot).orElse(null);
		if (found != null) {
			found.bindEncounter(snapshot.encounterId().orElseThrow());
			if (snapshot.bossUuid().filter(found.getUUID()::equals).isEmpty()) {
				WorldInterfaceState.mutate(level.getServer(), snapshot.encounterId().orElseThrow(), snapshot.revision(),
						state -> state.setBossUuid(found.getUUID()));
			}
			ensureParts(level, found);
			return found;
		}
		if (snapshot.stage().wireId() < WorldInterfaceStage.SUMMONING.wireId()
				|| snapshot.stage().wireId() > WorldInterfaceStage.FAILURE_RESOLUTION.wireId()) return null;
		level.getChunkAt(snapshot.arenaCenter());
		WorldInterfaceEntity boss = ModEntities.WORLD_INTERFACE.create(level, EntitySpawnReason.EVENT);
		if (boss == null) return null;
		UUID requested = snapshot.bossUuid().orElseGet(() -> deterministicEntityUuid(
				"boss", snapshot.encounterId().orElseThrow()));
		Entity collision = level.getEntity(requested);
		if (collision != null) return null;
		boss.setUUID(requested);
		boss.bindEncounter(snapshot.encounterId().orElseThrow());
		boss.setForm(formForStage(snapshot.stage()));
		boss.snapTo(snapshot.arenaCenter().getX() + 0.5D, snapshot.arenaCenter().getY() + 18.0D,
				snapshot.arenaCenter().getZ() + 0.5D, 0.0F, 0.0F);
		if (!level.addFreshEntity(boss)) return null;
		if (snapshot.bossUuid().filter(boss.getUUID()::equals).isEmpty()) {
			WorldInterfaceState.mutate(level.getServer(), snapshot.encounterId().orElseThrow(), snapshot.revision(),
					state -> state.setBossUuid(boss.getUUID()));
		}
		ensureParts(level, boss);
		return boss;
	}

	private static Optional<WorldInterfaceEntity> findBoss(ServerLevel level,
			WorldInterfaceState.Snapshot snapshot) {
		if (snapshot.bossUuid().isPresent()) {
			Entity direct = level.getEntity(snapshot.bossUuid().orElseThrow());
			if (direct instanceof WorldInterfaceEntity boss && boss.isAlive()) return Optional.of(boss);
		}
		AABB bounds = new AABB(snapshot.arenaCenter()).inflate(192.0D, 128.0D, 192.0D);
		return level.getEntitiesOfClass(WorldInterfaceEntity.class, bounds, Entity::isAlive).stream()
				.filter(entity -> snapshot.encounterId().filter(id -> id.equals(entity.encounterId())).isPresent())
				.findFirst();
	}

	private static void ensureParts(ServerLevel level, WorldInterfaceEntity boss) {
		String tag = PART_TAG_PREFIX + boss.getUUID();
		// The limb proxies hang below the box rather than sitting inside it, so the search has to
		// clear the full drop or reconciliation keeps re-creating parts it simply failed to see.
		double reach = WorldInterfaceAnatomy.tentacleDrop(boss.form()) + 24.0D;
		AABB bounds = boss.getBoundingBox().inflate(reach, reach, reach);
		Set<Integer> present = new HashSet<>();
		for (WorldInterfacePartEntity part : level.getEntitiesOfClass(WorldInterfacePartEntity.class, bounds,
				entity -> entity.getTags().contains(tag))) present.add(part.partIndex());
		for (int index = 0; index < WorldInterfacePartEntity.PART_COUNT; index++) {
			if (present.contains(index)) continue;
			WorldInterfacePartEntity part = ModEntities.WORLD_INTERFACE_PART.create(level, EntitySpawnReason.EVENT);
			if (part == null) continue;
			part.addTag(tag);
			part.attach(boss, index);
			level.addFreshEntity(part);
		}
	}

	private static void hoverAt(WorldInterfaceEntity boss, BlockPos center, double height, long tick) {
		double angle = tick * 0.006D;
		Vec3 desired = new Vec3(center.getX() + 0.5D + Math.cos(angle) * 7.0D,
				center.getY() + height + Math.sin(angle * 1.7D) * 1.5D,
				center.getZ() + 0.5D + Math.sin(angle) * 7.0D);
		boss.setDeltaMovement(desired.subtract(boss.position()).scale(0.08D));
	}

	private static void showAction(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.BossAction action, int duration, List<UUID> targets, long seed) {
		WorldInterfaceEntity boss = findBoss(level, snapshot).orElse(null);
		if (boss != null) boss.showAction(action.wireId(), level.getGameTime(), duration);
		for (ServerPlayer player : encounterRecipients(level.getServer(), snapshot)) {
			ServerPlayNetworking.send(player, new BossActionS2C(snapshot.encounterId().orElseThrow(),
					nextClientSequence(player), action.wireId(), level.getGameTime(), duration,
					targets, seed, 0));
		}
	}

	private static void sendEncounterSnapshots(MinecraftServer server, boolean immediate) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return;
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		WorldInterfaceEntity boss = findBoss(level, snapshot).orElse(null);
		int form = boss == null ? formForStage(snapshot.stage()) + 1 : boss.form() + 1;
		if (snapshot.stage().wireId() < WorldInterfaceStage.SUMMONING.wireId()
				|| snapshot.stage() == WorldInterfaceStage.COMPLETE) form = WorldInterfaceProtocol.Form.NONE.wireId();
		long elapsed = effectiveActiveTicks(snapshot, level.getGameTime());
		int anchors = 0;
		for (WorldInterfaceState.Anchor anchor : snapshot.anchors()) if (!anchor.destroyed()) anchors |= 1 << anchor.index();
		int gatewayState = snapshot.gates().isEmpty() ? WorldInterfaceGatewayState.DORMANT.wireId()
				: snapshot.gates().getFirst().state().wireId();
		float progress = WorldInterfacePolicy.presentationErosionProgress(snapshot.stage(),
				elapsed, snapshot.resolutionTick(), level.getGameTime(), RESOLUTION_DURATION_TICKS);
		for (ServerPlayer player : encounterRecipients(server, snapshot)) {
			ServerPlayNetworking.send(player, new WorldInterfaceSnapshotS2C(WorldInterfaceProtocol.VERSION,
					snapshot.encounterId().orElseThrow(), nextClientSequence(player), snapshot.stage().wireId(), form,
					boss == null ? NIL_UUID : boss.getUUID(), snapshot.arenaCenter(),
					(float) snapshot.maxVirtualHealth(), (float) snapshot.virtualHealth(), anchors,
					elapsed, snapshot.runningSinceGameTime() < 0L,
					Math.max(0L, level.getGameTime()), gatewayState,
					snapshot.gates().stream().map(WorldInterfaceState.Gate::position).toList(),
					outcomeWire(snapshot.outcome()), progress));
		}
	}

	private static void sendAltarSnapshots(MinecraftServer server, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.AltarStatus status) {
		Set<UUID> viewers = ALTAR_VIEWERS.getOrDefault(server, Set.of());
		for (UUID viewerId : List.copyOf(viewers)) {
			ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
			if (viewer != null) sendAltarSnapshot(viewer, snapshot, status);
		}
	}

	private static void sendAltarSnapshot(ServerPlayer viewer, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.AltarStatus status) {
		MinecraftServer server = viewer.level().getServer();
		List<UUID> roster = altarRoster(server, snapshot);
		List<String> names = roster.stream().map(id -> {
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			return online == null ? id.toString().substring(0, 8) : online.getGameProfile().name();
		}).toList();
		int mask = 0;
		for (int index = 0; index < roster.size(); index++) {
			WorldInterfaceState.TerminalTransaction transaction = snapshot.terminalTransactions().get(roster.get(index));
			if (transaction != null && (transaction.state() == WorldInterfaceState.TerminalTransactionState.REMOVED
					|| transaction.state() == WorldInterfaceState.TerminalTransactionState.COMMITTED)) mask |= 1 << index;
		}
		ServerPlayNetworking.send(viewer, new AltarSnapshotS2C(WorldInterfaceProtocol.VERSION,
				snapshot.encounterId().orElseThrow(), nextClientSequence(viewer), snapshot.revision(),
				snapshot.stage().wireId(), snapshot.altarCenter(), roster, names, mask,
				roster.contains(viewer.getUUID()) && !viewer.isSpectator(), status.wireId()));
	}

	private static List<UUID> altarRoster(MinecraftServer server, WorldInterfaceState.Snapshot snapshot) {
		if (!snapshot.frozenRoster().isEmpty()) return snapshot.frozenRoster().stream()
				.sorted(Comparator.comparing(UUID::toString)).toList();
		return server.getPlayerList().getPlayers().stream().filter(player -> !player.isSpectator())
				.map(ServerPlayer::getUUID).sorted(Comparator.comparing(UUID::toString)).limit(8).toList();
	}

	private static List<ServerPlayer> encounterRecipients(MinecraftServer server,
			WorldInterfaceState.Snapshot snapshot) {
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (snapshot.frozenRoster().contains(player.getUUID()) || player.level().dimension() == Level.END) {
				recipients.add(player);
			}
		}
		return List.copyOf(recipients);
	}

	private static long nextClientSequence(ServerPlayer player) {
		Map<UUID, Long> sequences = CLIENT_SEQUENCES.computeIfAbsent(player.level().getServer(),
				ignored -> new HashMap<>());
		long next = Math.incrementExact(sequences.getOrDefault(player.getUUID(), -1L));
		sequences.put(player.getUUID(), next);
		return next;
	}

	private static void rememberClientSequence(ServerPlayer player, long sequence) {
		if (sequence < 0L) throw new IllegalArgumentException("Sequence must be non-negative");
		CLIENT_SEQUENCES.computeIfAbsent(player.level().getServer(), ignored -> new HashMap<>())
				.merge(player.getUUID(), sequence, Math::max);
	}

	private static void pruneAltarViewers(MinecraftServer server) {
		Set<UUID> viewers = ALTAR_VIEWERS.get(server);
		if (viewers == null) return;
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		viewers.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			return player == null || snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS
					|| player.level().dimension() != Level.END
					|| player.distanceToSqr(snapshot.altarCenter().getCenter()) > 10.0D * 10.0D;
		});
	}

	private static void recoverAfterRestart(MinecraftServer server) {
		if (!RECOVERED_SERVERS.add(server)) return;
		ServerLevel level = server.getLevel(Level.END);
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
		if (level == null || !before.valid() || !before.present()) return;
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(level);
		WorldInterfaceShockwaveService.clear(level);
		EndBossArenaService.restoreAuthoritativeAnchors(level, before, !before.stage().isCombat());
		EndBossArenaService.restoreTerrainEditCount(level, before.terrainEditsUsed());
		bindCore(level, before);
		ensureExitOpen(level, before);
		if (before.stage().isCombat()) {
			WorldInterfaceAttackService.onRestart(server, before.encounterId().orElseThrow());
			before = WorldInterfaceState.snapshot(server);
			long recoveredElapsed = effectiveActiveTicks(before, level.getGameTime());
			WorldInterfaceState.Snapshot captured = before;
			WorldInterfaceState.MutationResult recovered = WorldInterfaceState.mutate(server,
					before.encounterId().orElseThrow(), before.revision(), state -> {
						state.setClock(recoveredElapsed, -1L);
						state.clearCurrentAttack();
						state.setRecoveryGraceTicks(WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS);
						state.setActionSchedule(captured.actionSequence(), captured.lastActionWireId(),
								Math.max(captured.nextActionActiveTick(), recoveredElapsed
										+ WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS));
					});
			if (recovered.applied()) before = recovered.snapshot();
		}
		if (before.stage().wireId() >= WorldInterfaceStage.SUMMONING.wireId()
				&& before.stage().wireId() <= WorldInterfaceStage.FAILURE_RESOLUTION.wireId()) {
			WorldInterfaceEntity recoveredBoss = ensureBoss(level, before);
			synchronizeResolutionAction(level, before, recoveredBoss, true);
		}
		if (before.friendlyDragonUuid().isPresent()
				&& FriendlyDragonService.recover(level, before.friendlyDragonUuid().orElseThrow()).isEmpty()) {
			FriendlyDragonService.spawn(level, before.arenaCenter(), before.friendlyDragonUuid().orElseThrow());
		}
	}

	private static void pauseForStop(MinecraftServer server) {
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
		if (!before.valid() || !before.present() || !before.stage().isCombat()
				|| before.runningSinceGameTime() < 0L) return;
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		long elapsed = effectiveActiveTicks(before, level.getGameTime());
		WorldInterfaceState.mutate(server, before.encounterId().orElseThrow(), before.revision(),
				state -> state.setClock(elapsed, -1L));
	}

	private static void reconcilePlayer(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return;
		snapshot.encounterId().ifPresent(id -> WorldInterfaceAttackService.restorePendingFor(player, id));
		if (snapshot.stage() == WorldInterfaceStage.SUCCESS_RESOLUTION
				|| snapshot.stage() == WorldInterfaceStage.FAILURE_RESOLUTION) {
			WorldInterfaceProtocol.BossAction action = snapshot.stage() == WorldInterfaceStage.SUCCESS_RESOLUTION
					? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH
					: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE;
			long started = snapshot.resolutionTick() < 0L
					? player.level().getGameTime() : snapshot.resolutionTick();
			sendResolutionAction(player, snapshot, action, started);
		}
		WorldInterfaceState.PoemLedgerEntry poem = snapshot.poemLedger().get(player.getUUID());
		WorldInterfaceState.RespawnLedgerEntry respawn = snapshot.respawnLedger().get(player.getUUID());
		if (respawn != null && !respawn.restored() && player.level().dimension() != Level.END
				&& snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			restoreRespawnAfterVanillaReturn(player, snapshot);
			snapshot = WorldInterfaceState.snapshot(server);
			poem = snapshot.poemLedger().get(player.getUUID());
			respawn = snapshot.respawnLedger().get(player.getUUID());
		}
		if (snapshot.stage() == WorldInterfaceStage.COMPLETE) {
			if (respawn != null && !respawn.restored()) restoreRespawnAndReturn(player, snapshot);
			return;
		}
		if (snapshot.stage() == WorldInterfaceStage.PORTAL_OPEN && poem != null && poem.acked()) {
			if (respawn != null && !respawn.restored()) restoreRespawnAndReturn(player, snapshot);
			completeIfAllPoemsAcknowledged(server);
			return;
		}
		if (snapshot.stage() == WorldInterfaceStage.PORTAL_OPEN && poem != null && poem.started() && !poem.acked()) {
			rememberClientSequence(player, poem.sequence());
			ServerPlayNetworking.send(player, new PoemStartS2C(snapshot.encounterId().orElseThrow(),
					poem.sequence(), outcomeWire(snapshot.outcome()), FrequencyWorldData.get(server).worldId()));
			prepareVanillaEndReturn(player, snapshot);
			if (player.level().dimension() == Level.END && !player.wonGame) {
				// Reopen the real credits after a disconnect that interrupted an unacknowledged poem.
				player.showEndCredits();
			}
			return;
		}
		if (respawn != null && !respawn.restored()) overrideRespawn(player, snapshot.safeSpawn());
	}

	private static boolean rememberAndOverrideRespawn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.RespawnLedgerEntry existing = snapshot.respawnLedger().get(player.getUUID());
		if (existing == null) {
			if (snapshot.respawnLedger().size() >= WorldInterfaceState.MAX_ROSTER_SIZE) {
				TerminalNoticeService.denied(player,
						"message.thefourthfrequency.world_interface.roster_full");
				return false;
			}
			ServerPlayer.RespawnConfig original = player.getRespawnConfig();
			WorldInterfaceState.RespawnLedgerEntry entry = original == null
					? new WorldInterfaceState.RespawnLedgerEntry(player.getUUID(), false, "", BlockPos.ZERO,
							0.0F, 0.0F, false, false)
					: new WorldInterfaceState.RespawnLedgerEntry(player.getUUID(), true,
							original.respawnData().dimension().identifier().toString(),
							original.respawnData().pos(), original.respawnData().yaw(), original.respawnData().pitch(),
							original.forced(), false);
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.putRespawn(entry));
			if (!result.applied()) return false;
		} else if (existing.restored()) {
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.putRespawn(
						new WorldInterfaceState.RespawnLedgerEntry(existing.playerId(), existing.hasOriginal(),
								existing.dimension(), existing.position(), existing.yaw(), existing.pitch(),
								existing.forced(), false)));
			if (!result.applied()) return false;
		}
		overrideRespawn(player, snapshot.safeSpawn());
		return true;
	}

	private static void overrideRespawn(ServerPlayer player, BlockPos safeSpawn) {
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(Level.END, safeSpawn, 180.0F, 0.0F), true), false);
	}

	private static void prepareVanillaEndReturn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceState.RespawnLedgerEntry entry = snapshot.respawnLedger().get(player.getUUID());
		if (entry != null && !entry.restored()) {
			// Vanilla PERFORM_RESPAWN now resolves to the Overworld default instead of the temporary End altar.
			player.setRespawnPosition(null, false);
		}
	}

	private static void restoreRespawnAfterVanillaReturn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceState.RespawnLedgerEntry entry = snapshot.respawnLedger().get(player.getUUID());
		if (entry == null || entry.restored()) return;
		restoreRespawnConfiguration(player, entry);
		markRespawnRestored(player.level().getServer(), entry.playerId());
	}

	private static void restoreRespawnAndReturn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceState.RespawnLedgerEntry entry = snapshot.respawnLedger().get(player.getUUID());
		if (entry != null) restoreRespawnConfiguration(player, entry);
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		BlockPos spawn = overworld.getRespawnData().pos();
		Entity teleported = player.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(spawn), Vec3.ZERO,
				player.getYRot(), player.getXRot(),
				TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
		if (teleported != null && entry != null && !entry.restored()) markRespawnRestored(server, entry.playerId());
	}

	private static void restoreRespawnConfiguration(ServerPlayer player,
			WorldInterfaceState.RespawnLedgerEntry entry) {
		if (!entry.hasOriginal()) {
			player.setRespawnPosition(null, false);
			return;
		}
		try {
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
					Identifier.parse(entry.dimension()));
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(dimension,
					entry.position(), entry.yaw(), entry.pitch()), entry.forced()), false);
		} catch (RuntimeException exception) {
			player.setRespawnPosition(null, false);
		}
	}

	private static void markRespawnRestored(MinecraftServer server, UUID playerId) {
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot latest = WorldInterfaceState.snapshot(server);
			if (!latest.valid() || !latest.present() || latest.encounterId().isEmpty()) return;
			WorldInterfaceState.RespawnLedgerEntry current = latest.respawnLedger().get(playerId);
			if (current == null || current.restored()) return;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					latest.encounterId().orElseThrow(), latest.revision(), state -> state.putRespawn(
							new WorldInterfaceState.RespawnLedgerEntry(current.playerId(), current.hasOriginal(),
									current.dimension(), current.position(), current.yaw(), current.pitch(),
									current.forced(), true)));
			if (result.applied() || !"revision_mismatch".equals(result.reason())) return;
		}
	}

	private static void completeIfAllPoemsAcknowledged(MinecraftServer server) {
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
			if (snapshot.stage() != WorldInterfaceStage.PORTAL_OPEN) return;
			boolean complete = !snapshot.frozenRoster().isEmpty() && snapshot.frozenRoster().stream()
					.allMatch(id -> Optional.ofNullable(snapshot.poemLedger().get(id))
							.map(WorldInterfaceState.PoemLedgerEntry::acked).orElse(false)
							&& Optional.ofNullable(snapshot.respawnLedger().get(id))
							.map(WorldInterfaceState.RespawnLedgerEntry::restored).orElse(false));
			if (!complete) return;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.transition(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), WorldInterfaceStage.PORTAL_OPEN,
					WorldInterfaceStage.COMPLETE);
			if (result.applied()) {
				sendEncounterSnapshots(server, true);
				return;
			}
			if (!"revision_mismatch".equals(result.reason())) return;
		}
	}

	private static void ensureExitOpen(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		if (snapshot.exitOpen() && snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			// Recomputed rather than read back from the ledger: a save written before the exit moved
			// onto the terrace still names the old sunken position, and re-placing it there would
			// keep rebuilding the wrong portal every tick for the rest of that world's life.
			placeExit(level, snapshot.altarCenter());
		}
	}

	/**
	 * Opens the way out by taking the altar down.
	 *
	 * <p>The exit used to be written into the terrace and left the terrace standing around it, which
	 * put a three-by-three portal on a raised platform with the resonance core in the middle of it -
	 * a doorway you had to climb onto, with a block in the doorway. The altar has done its job by
	 * this point: it exists to start the encounter, and the encounter is over.</p>
	 *
	 * <p>So the terrace, its pillars and the core are cleared away entirely, and the portal is laid
	 * into the base course where it sits flush with the island. Nothing about the way out hangs in
	 * the air, and nothing stands in it.</p>
	 */
	private static void placeExit(ServerLevel level, BlockPos center) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
		BlockPos floor = AltarShape.centerFromCore(AltarShape.corePosition(center));
		// Everything the altar ever wrote, including the pillar shafts and the core above the middle.
		for (int x = -AltarShape.RADIUS; x <= AltarShape.RADIUS; x++) {
			for (int z = -AltarShape.RADIUS; z <= AltarShape.RADIUS; z++) {
				for (int y = 1; y <= AltarShape.MAX_OFFSET; y++) {
					BlockPos position = floor.offset(x, y, z);
					if (!level.getBlockState(position).isAir()) {
						level.setBlock(position, Blocks.AIR.defaultBlockState(), flags);
					}
				}
			}
		}
		BlockState brick = Blocks.END_STONE_BRICKS.defaultBlockState();
		// Ground course: the footing the terrace stands on, and the ring the stairs step down to.
		for (int x = -AltarShape.RADIUS; x <= AltarShape.RADIUS; x++) {
			for (int z = -AltarShape.RADIUS; z <= AltarShape.RADIUS; z++) {
				level.setBlock(floor.offset(x, 0, z), brick, flags);
			}
		}
		// One course up: a seven-wide terrace with the portal set into the middle of it, ringed by
		// stairs so the way out is something you walk up to rather than a hole in the floor.
		for (int x = -EXIT_STAIR_EDGE; x <= EXIT_STAIR_EDGE; x++) {
			for (int z = -EXIT_STAIR_EDGE; z <= EXIT_STAIR_EDGE; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				BlockPos position = floor.offset(x, 1, z);
				if (edge <= 1) {
					level.setBlock(position, ModBlocks.WORLD_INTERFACE_EXIT_PORTAL.defaultBlockState(), flags);
				} else if (edge < EXIT_STAIR_EDGE) {
					level.setBlock(position, brick, flags);
				} else {
					level.setBlock(position, exitStair(x, z), flags);
				}
			}
		}
	}

	/**
	 * A stair on the terrace rim, facing outward.
	 *
	 * <p>The corners take a full block rather than a stair: a stair there would have to pick one of
	 * two equally wrong facings, and the seam reads worse than a plain step does.</p>
	 */
	private static BlockState exitStair(int dx, int dz) {
		if (Math.abs(dx) == EXIT_STAIR_EDGE && Math.abs(dz) == EXIT_STAIR_EDGE) {
			return Blocks.END_STONE_BRICK_SLAB.defaultBlockState();
		}
		Direction facing = Math.abs(dx) > Math.abs(dz)
				? (dx > 0 ? Direction.EAST : Direction.WEST)
				: (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		return Blocks.END_STONE_BRICK_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, facing);
	}

	private static void removeBossProjection(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		AABB bounds = new AABB(snapshot.arenaCenter()).inflate(256.0D, 192.0D, 256.0D);
		for (WorldInterfaceEntity entity : level.getEntitiesOfClass(WorldInterfaceEntity.class, bounds,
				candidate -> snapshot.encounterId().filter(id -> id.equals(candidate.encounterId())).isPresent())) {
			entity.discard();
		}
		for (WorldInterfacePartEntity part : level.getEntitiesOfClass(WorldInterfacePartEntity.class, bounds,
				Entity::isAlive)) {
			part.discard();
		}
	}

	private static WorldInterfaceState.Snapshot clearFinishedBossIdentity(MinecraftServer server,
			WorldInterfaceState.Snapshot initial) {
		WorldInterfaceState.Snapshot snapshot = initial;
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES && snapshot.bossUuid().isPresent(); attempt++) {
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), WorldInterfaceState.MutableState::clearBossUuid);
			if (result.applied()) return result.snapshot();
			if (!"revision_mismatch".equals(result.reason())) return snapshot;
			snapshot = result.snapshot();
		}
		return snapshot;
	}

	private static void pointLivingAnchorsSkyward(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		for (WorldInterfaceState.Anchor anchor : snapshot.anchors()) {
			if (anchor.destroyed() || anchor.crystalUuid().isEmpty()) continue;
			Entity entity = level.getEntity(anchor.crystalUuid().orElseThrow());
			if (entity instanceof EndCrystal crystal) {
				crystal.setInvulnerable(true);
				crystal.setBeamTarget(anchor.position().above(192));
			}
		}
	}

	private static void persistTerrainBudget(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		int actual = EndBossArenaService.permanentTerrainEdits(level);
		if (actual == snapshot.terrainEditsUsed()) return;
		WorldInterfaceState.mutate(level.getServer(), snapshot.encounterId().orElseThrow(), snapshot.revision(),
				state -> state.setTerrainEditsUsed(actual));
	}

	private static Optional<ServerPlayer> nearestArenaParticipant(ServerLevel level,
			WorldInterfaceState.Snapshot snapshot, Vec3 origin) {
		return level.players().stream().filter(player -> player.isAlive() && !player.isSpectator())
				.filter(player -> player.distanceToSqr(snapshot.arenaCenter().getCenter())
						<= ENCOUNTER_VISIBILITY_RADIUS_SQR)
				.min(Comparator.comparingDouble(player -> player.distanceToSqr(origin)));
	}

	private static int onlineFrozenCount(MinecraftServer server, WorldInterfaceState.Snapshot snapshot) {
		int online = 0;
		for (UUID playerId : snapshot.frozenRoster()) {
			if (server.getPlayerList().getPlayer(playerId) != null) online++;
		}
		return online;
	}

	private static long effectiveActiveTicks(WorldInterfaceState.Snapshot snapshot, long gameTime) {
		if (snapshot.runningSinceGameTime() < 0L) return snapshot.activeTicks();
		long delta = Math.max(0L, gameTime - snapshot.runningSinceGameTime());
		return snapshot.activeTicks() > Long.MAX_VALUE - delta ? Long.MAX_VALUE
				: snapshot.activeTicks() + delta;
	}

	private static void advanceToHealthStage(WorldInterfaceState.MutableState state, double healthRatio) {
		WorldInterfaceStage startingStage = state.stage();
		WorldInterfaceStage desired = WorldInterfaceStage.advanceCombatStage(state.stage(), healthRatio);
		while (state.stage().wireId() < desired.wireId()) state.transitionTo(nextCombatStage(state.stage()));
		if (desired != state.stage()) throw new IllegalStateException("phase_advance_failed");
		if (state.stage() != startingStage) {
			state.clearCurrentAttack();
			state.setRecoveryGraceTicks(40);
			state.setActionSchedule(state.actionSequence(), state.lastActionWireId(), state.activeTicks() + 40L);
		}
	}

	private static void advanceToPhaseThree(WorldInterfaceState.MutableState state) {
		while (state.stage().wireId() < WorldInterfaceStage.PHASE_3.wireId()) {
			state.transitionTo(nextCombatStage(state.stage()));
		}
	}

	private static WorldInterfaceStage nextCombatStage(WorldInterfaceStage stage) {
		return switch (stage) {
			case PHASE_1 -> WorldInterfaceStage.PHASE_2;
			case PHASE_2 -> WorldInterfaceStage.PHASE_3;
			default -> throw new IllegalStateException("not_advancing_combat");
		};
	}

	private static boolean bossMatches(WorldInterfaceState.Snapshot snapshot, WorldInterfaceEntity boss) {
		return snapshot.valid() && snapshot.present()
				&& snapshot.encounterId().filter(id -> id.equals(boss.encounterId())).isPresent()
				&& snapshot.bossUuid().filter(id -> id.equals(boss.getUUID())).isPresent();
	}

	private static boolean duplicateProjectileHit(MinecraftServer server, UUID projectileId, long tick) {
		Map<UUID, Long> hits = PROJECTILE_HITS.computeIfAbsent(server, ignored -> new HashMap<>());
		Long previous = hits.get(projectileId);
		if (previous != null && previous + 40L >= tick) return true;
		hits.put(projectileId, tick);
		if (hits.size() > 256) hits.entrySet().removeIf(entry -> entry.getValue() + 40L < tick);
		return false;
	}

	private static boolean duplicateMeleeHit(MinecraftServer server, UUID attackerId, long tick) {
		Map<UUID, Long> hits = MELEE_HITS.computeIfAbsent(server, ignored -> new HashMap<>());
		Long previous = hits.put(attackerId, tick);
		if (hits.size() > 256) hits.entrySet().removeIf(entry -> entry.getValue() < tick);
		return previous != null && previous == tick;
	}

	private static UUID deterministicEntityUuid(String role, UUID encounterId) {
		return UUID.nameUUIDFromBytes(("thefourthfrequency:world_interface:" + role + ":" + encounterId)
				.getBytes(StandardCharsets.UTF_8));
	}

	private static int formForStage(WorldInterfaceStage stage) {
		return switch (stage) {
			case PHASE_2 -> WorldInterfaceEntity.FORM_CONSUMING;
			case PHASE_3, SUCCESS_RESOLUTION, FAILURE_RESOLUTION, PORTAL_OPEN, COMPLETE ->
					WorldInterfaceEntity.FORM_INTERFACE;
			default -> WorldInterfaceEntity.FORM_LISTENING;
		};
	}

	private static int outcomeWire(WorldInterfaceState.Outcome outcome) {
		return switch (outcome) {
			case NONE -> WorldInterfaceProtocol.Outcome.NONE.wireId();
			case SUCCESS -> WorldInterfaceProtocol.Outcome.SUCCESS.wireId();
			case FAILURE -> WorldInterfaceProtocol.Outcome.FAILURE.wireId();
		};
	}

	private static List<WorldInterfaceState.Gate> indexedGates(EndBossArenaService.PreparedArena arena) {
		List<WorldInterfaceState.Gate> gates = new ArrayList<>(EndBossArenaService.GATEWAY_COUNT);
		for (int index = 0; index < arena.gatewayCorePositions().size(); index++) {
			gates.add(new WorldInterfaceState.Gate(index, arena.gatewayCorePositions().get(index),
					WorldInterfaceGatewayState.DORMANT));
		}
		return List.copyOf(gates);
	}

	private static List<WorldInterfaceState.Anchor> indexedAnchors(EndBossArenaService.PreparedArena arena) {
		return arena.anchors().stream().sorted(Comparator.comparingInt(EndBossArenaService.AnchorSlot::index))
				.map(anchor -> new WorldInterfaceState.Anchor(anchor.index(), anchor.position(),
						Optional.of(anchor.crystalUuid()), false)).toList();
	}

	private static void bindCore(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		if (snapshot.encounterId().isPresent()
				&& level.getBlockEntity(snapshot.altarCenter()) instanceof ResonanceCoreBlockEntity core) {
			core.bind(snapshot.encounterId().orElseThrow(), snapshot.revision());
		}
	}

	private static WorldInterfaceProtocol.AltarStatus altarStatus(String reason) {
		return WorldInterfaceProtocol.AltarStatus.fromReason(reason);
	}

	private static void recordAltarOpening(ServerPlayer player, ServerLevel level, BlockPos center) {
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (data.terminalRecord(player.getUUID()).isPresent()) {
			data.updateTerminalRecord(player.getUUID(), record -> {
				record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, true);
				record.putLong(TerminalData.PORTAL_ROOM_POSITION, center.asLong());
				record.putString(TerminalData.PORTAL_ROOM_DIMENSION, level.dimension().identifier().toString());
				record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
						record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
								| SurvivalMilestone.FOUND_STRONGHOLD.mask());
			});
		}
		TerminalSignalService.record(player, SignalBand.UNKNOWN, "world_interface_altar", 0, 2, true);
		TerminalNoticeService.encounter(player, Component.translatable(
				"message.thefourthfrequency.world_interface.altar_prepared"));
	}

	private static void markDefeatedMilestone(MinecraftServer server, Set<UUID> roster) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (UUID playerId : roster) {
			if (data.terminalRecord(playerId).isEmpty()) continue;
			data.updateTerminalRecord(playerId, record -> record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
							| SurvivalMilestone.DEFEATED_BOSS.mask()));
		}
	}

	/**
	 * The finale's narration goes to the mod's own notice stack rather than to chat.
	 *
	 * <p>In the chat log every one of these lines - the fight starting, an anchor falling, the
	 * dragon speaking, the phase turning over - was the same white text in the same scroll as death
	 * messages and player conversation, at the exact moment the player has the least attention to
	 * spend parsing it. The stack gives each channel its own colour and holds it above the hotbar
	 * where the rest of the encounter's feedback already lives.</p>
	 */
	private static void broadcast(MinecraftServer server, int tone, String key, Object... arguments) {
		Component message = Component.translatable(key, arguments);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			switch (tone) {
				case TerminalNoticePayload.TONE_ANCHOR -> TerminalNoticeService.anchor(player, message);
				case TerminalNoticePayload.TONE_DRAGON -> TerminalNoticeService.dragon(player, message);
				default -> TerminalNoticeService.encounter(player, message);
			}
		}
	}

}
