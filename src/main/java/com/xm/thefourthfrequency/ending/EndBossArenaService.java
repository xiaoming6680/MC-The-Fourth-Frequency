package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.mixin.EndDragonFightAccessor;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Idempotent world-interface arena preparation and the bounded, no-drop terrain-scar queue.
 * Pending scar work is intentionally memory-only; callers restore only the committed edit count.
 */
public final class EndBossArenaService {
	/** Legacy animation timing retained so the prelude-only Misread body remains binary compatible. */
	public static final int WARNING_TICKS = 45;
	public static final int MAX_PERMANENT_EDITS = WorldInterfacePolicy.MAX_PERMANENT_TERRAIN_EDITS;
	public static final int MAX_EDITS_PER_TICK = WorldInterfacePolicy.MAX_TERRAIN_EDITS_PER_TICK;
	public static final int MAX_LASER_EDITS = 48;
	public static final int ARENA_RADIUS = 160;
	public static final int PORTAL_SAFE_RADIUS = 8;
	public static final int GATEWAY_COUNT = 20;
	public static final int GATEWAY_RADIUS = 96;
	public static final int ANCHOR_COUNT = 10;

	private static final int ALTAR_RADIUS = 5;
	private static final int EDIT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
			| Block.UPDATE_SUPPRESS_DROPS;
	private static final int MAX_PENDING_SCARS = 8_192;
	private static final String ANCHOR_TAG = "thefourthfrequency.world_interface_anchor";
	private static final String ANCHOR_INDEX_PREFIX = "thefourthfrequency.world_interface_anchor.";

	public static final TagKey<Block> WORLD_INTERFACE_IMMUNE = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "world_interface_immune"));
	/** Existing data-pack tag remains honored while the old boss assets are retired. */

	private static final Map<ServerLevel, ArenaRuntime> RUNTIMES =
			Collections.synchronizedMap(new WeakHashMap<>());
	/**
	 * Newly added entities can be accepted by the persistent entity manager before the visible UUID
	 * index exposes them. Retain only the ten bounded arena references so same-tick reconciliation
	 * cannot try to add a second entity with the same deterministic UUID.
	 */
	private static final Map<ServerLevel, Map<UUID, EndCrystal>> KNOWN_ANCHORS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static boolean initialized;

	private EndBossArenaService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(EndBossArenaService::tickServer);
	}

	/**
	 * Prepares the central altar, twenty inert gateways and ten authoritative crystals. The resonance
	 * core is written last and acts as the durable preparation marker.
	 */
	public static PreparedArena prepare(ServerLevel level) {
		Objects.requireNonNull(level, "level");
		if (level.dimension() != Level.END) {
			throw new IllegalArgumentException("The world-interface arena can only be prepared in the End");
		}
		synchronized (RUNTIMES) {
			ArenaRuntime cached = RUNTIMES.get(level);
			if (cached != null) {
				suppressVanillaFight(level);
				removeInitialHostileDragon(level);
				return cached.arena;
			}

			BlockPos existingCore = findBlockInColumn(level, 0, 0, ModBlocks.RESONANCE_CORE);
			boolean alreadyPrepared = existingCore != null;
			int altarFloorY = centralAltarFloorY(level);
			BlockPos center = new BlockPos(0, altarFloorY, 0);
			BlockPos altar = center.above();
			BlockPos safeSpawn = mainIslandSurfaceAir(level, 0, ALTAR_RADIUS + 2);

			buildAltar(level, center, false);
			List<BlockPos> gateways = buildGateways(level);
			List<AnchorSlot> anchors = ensureAnchorSlots(level, alreadyPrepared);
			removeInitialHostileDragon(level);
			suppressVanillaFight(level);
			level.setBlock(altar, ModBlocks.RESONANCE_CORE.defaultBlockState(), EDIT_FLAGS);

			PreparedArena arena = new PreparedArena(center, altar, safeSpawn, gateways, anchors);
			ArenaRuntime runtime = new ArenaRuntime(arena, computeProtectedPositions(arena), new TerrainScarQueue());
			RUNTIMES.put(level, runtime);
			return arena;
		}
	}

	public static Set<BlockPos> protectedPositions(ServerLevel level) {
		return runtime(level).protectedPositions;
	}

	public static Set<BlockPos> protectedPositions(PreparedArena arena) {
		return computeProtectedPositions(Objects.requireNonNull(arena, "arena"));
	}

	/** Queues deterministic scar candidates; only successfully changed blocks consume the total budget. */
	public static int queueTerrainScar(ServerLevel level, Collection<BlockPos> candidates,
			int requestedMaximum, long seed) {
		Objects.requireNonNull(candidates, "candidates");
		if (requestedMaximum < 0) throw new IllegalArgumentException("Requested maximum cannot be negative");
		ArenaRuntime runtime = runtime(level);
		return runtime.scars.enqueue(candidates, requestedMaximum, seed);
	}

	/** Laser paths have a hard per-attack permanent-edit cap of forty-eight. */
	public static int queueLaserScar(ServerLevel level, Collection<BlockPos> candidates, long seed) {
		return queueTerrainScar(level, candidates, MAX_LASER_EDITS, seed);
	}

	public static int queueExplosionScar(ServerLevel level, BlockPos center, int radius,
			int requestedMaximum, long seed) {
		Objects.requireNonNull(center, "center");
		if (radius < 0 || radius > 16) throw new IllegalArgumentException("Scar radius must be between 0 and 16");
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos position : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius))) {
			if (position.distSqr(center) <= (double) radius * radius) candidates.add(position.immutable());
		}
		return queueTerrainScar(level, candidates, requestedMaximum, seed);
	}

	/** Runs one bounded queue slice. Exposed for deterministic GameTests. */
	public static int tickTerrainScars(ServerLevel level) {
		ArenaRuntime runtime;
		synchronized (RUNTIMES) {
			runtime = RUNTIMES.get(level);
		}
		return runtime == null ? 0 : runtime.scars.process(level, runtime.protectedPositions);
	}

	public static int permanentTerrainEdits(ServerLevel level) {
		return runtime(level).scars.permanentEdits;
	}

	/** Restores the persisted committed count and cancels all transient work after a restart. */
	public static void restoreTerrainEditCount(ServerLevel level, int committedEdits) {
		if (committedEdits < 0 || committedEdits > MAX_PERMANENT_EDITS) {
			throw new IllegalArgumentException("Committed terrain edits must be between 0 and 2048");
		}
		TerrainScarQueue queue = runtime(level).scars;
		queue.clearPending();
		queue.permanentEdits = committedEdits;
	}

	public static void cancelQueuedScars(ServerLevel level) {
		ArenaRuntime runtime;
		synchronized (RUNTIMES) {
			runtime = RUNTIMES.get(level);
		}
		if (runtime != null) runtime.scars.clearPending();
	}

	public static boolean canDestroy(ServerLevel level, BlockPos pos, BlockState state) {
		if (!insideEditableArena(pos) || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos)
				|| state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return false;
		ArenaRuntime runtime;
		synchronized (RUNTIMES) {
			runtime = RUNTIMES.get(level);
		}
		if (runtime != null && runtime.protectedPositions.contains(pos)) return false;
		if (state.is(WORLD_INTERFACE_IMMUNE)
				|| state.is(ModBlocks.RESONANCE_CORE) || state.is(ModBlocks.WARP_GATE_CORE)
				|| state.is(ModBlocks.STABILITY_ANCHOR_CAGE)
				|| state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN)
				|| state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.END_PORTAL)
				|| state.is(Blocks.END_GATEWAY) || state.is(Blocks.END_PORTAL_FRAME)) return false;
		return state.getDestroySpeed(level, pos) >= 0.0F;
	}

	public static boolean insideEditableArena(BlockPos pos) {
		long distance = (long) pos.getX() * pos.getX() + (long) pos.getZ() * pos.getZ();
		return distance <= (long) ARENA_RADIUS * ARENA_RADIUS
				&& distance > (long) PORTAL_SAFE_RADIUS * PORTAL_SAFE_RADIUS;
	}

	public static void setAnchorsInvulnerable(ServerLevel level, PreparedArena arena, boolean invulnerable) {
		for (AnchorSlot slot : arena.anchors) {
			EndCrystal crystal = findAuthoritativeAnchor(level, slot.crystalUuid).orElse(null);
			if (crystal != null && isAuthoritativeAnchor(crystal, slot.index)) {
				crystal.setInvulnerable(invulnerable);
			}
		}
	}

	/** Resolves an authoritative anchor even before the vanilla visible UUID index catches up. */
	public static Optional<EndCrystal> findAuthoritativeAnchor(ServerLevel level, UUID crystalUuid) {
		if (level == null || crystalUuid == null || level.dimension() != Level.END) return Optional.empty();
		Entity entity = findLoadedEntity(level, crystalUuid);
		if (entity instanceof EndCrystal crystal && isAuthoritativeAnchor(crystal) && crystal.isAlive()) {
			rememberAnchor(level, crystal);
			return Optional.of(crystal);
		}
		return Optional.empty();
	}

	/**
	 * Reconciles the ten deterministic crystal entities from the persisted authority set.
	 * A missing live anchor is a load/restart concern and is recreated; a persisted destroyed
	 * anchor is never resurrected. This is intentionally separate from {@link #prepare} because
	 * the arena marker alone cannot distinguish those cases.
	 */
	public static void restoreAuthoritativeAnchors(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			boolean invulnerable) {
		if (!snapshot.valid() || !snapshot.present() || snapshot.anchors().size() != ANCHOR_COUNT) return;
		if (level.dimension() != Level.END) {
			throw new IllegalArgumentException("Authoritative anchors can only be restored in the End");
		}
		for (WorldInterfaceState.Anchor anchor : snapshot.anchors()) {
			level.getChunkAt(anchor.position());
			level.waitForEntities(new ChunkPos(anchor.position()), 0);
		}
		AABB bounds = new AABB(-ARENA_RADIUS, level.getMinY(), -ARENA_RADIUS,
				ARENA_RADIUS, level.getMaxY(), ARENA_RADIUS);
		List<EndCrystal> tagged = new ArrayList<>(level.getEntitiesOfClass(EndCrystal.class, bounds,
				EndBossArenaService::isAuthoritativeAnchor));
		for (WorldInterfaceState.Anchor anchor : snapshot.anchors()) {
			BlockPos position = anchor.position();
			UUID uuid = anchor.crystalUuid().orElse(null);
			for (EndCrystal candidate : tagged) {
				if (isAuthoritativeAnchor(candidate, anchor.index())
						&& (anchor.destroyed() || uuid == null || !uuid.equals(candidate.getUUID()))) {
					candidate.discard();
				}
			}
			if (uuid == null) {
				if (!anchor.destroyed()) {
					throw new IllegalStateException("Live anchor " + anchor.index() + " has no crystal UUID");
				}
				continue;
			}
			Entity loaded = findLoadedEntity(level, uuid);
			if (anchor.destroyed()) {
				if (loaded instanceof EndCrystal crystal) crystal.discard();
				continue;
			}
			EndCrystal crystal = loaded instanceof EndCrystal value ? value : null;
			if (loaded != null && crystal == null) {
				throw new IllegalStateException("Anchor UUID " + uuid + " belongs to " + loaded.getType());
			}
			if (crystal == null) {
				crystal = new EndCrystal(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
				crystal.setUUID(uuid);
				if (!level.addFreshEntity(crystal)) {
					throw new IllegalStateException("Unable to restore authoritative anchor " + anchor.index());
				}
				rememberAnchor(level, crystal);
			}
			crystal.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
			crystal.setShowBottom(false);
			crystal.setInvulnerable(invulnerable);
			for (int index = 0; index < ANCHOR_COUNT; index++) crystal.removeTag(ANCHOR_INDEX_PREFIX + index);
			crystal.addTag(ANCHOR_TAG);
			crystal.addTag(ANCHOR_INDEX_PREFIX + anchor.index());
			buildAnchorCage(level, position);
		}
	}

	public static boolean isAuthoritativeAnchor(Entity entity) {
		return entity instanceof EndCrystal && entity.getTags().contains(ANCHOR_TAG);
	}

	public static boolean isAuthoritativeAnchor(Entity entity, int index) {
		return isAuthoritativeAnchor(entity) && entity.getTags().contains(ANCHOR_INDEX_PREFIX + index);
	}

	private static ArenaRuntime runtime(ServerLevel level) {
		synchronized (RUNTIMES) {
			ArenaRuntime runtime = RUNTIMES.get(level);
			if (runtime != null) return runtime;
		}
		prepare(level);
		synchronized (RUNTIMES) {
			return RUNTIMES.get(level);
		}
	}

	private static void tickServer(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		synchronized (RUNTIMES) {
			if (!RUNTIMES.containsKey(level)) return;
		}
		suppressVanillaFight(level);
		tickTerrainScars(level);
	}

	private static int centralAltarFloorY(ServerLevel level) {
		for (int x : new int[]{-ALTAR_RADIUS, ALTAR_RADIUS}) {
			for (int z : new int[]{-ALTAR_RADIUS, ALTAR_RADIUS}) {
				level.getChunkAt(new BlockPos(x, 64, z));
			}
		}
		int lowestSurface = level.getMaxY() - 1;
		for (int dx = -ALTAR_RADIUS; dx <= ALTAR_RADIUS; dx++) {
			for (int dz = -ALTAR_RADIUS; dz <= ALTAR_RADIUS; dz++) {
				lowestSurface = Math.min(lowestSurface,
						level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz) - 1);
			}
		}
		int minimum = level.getMinY() + 1;
		for (int floorY = Math.max(minimum, lowestSurface); floorY > minimum; floorY--) {
			if (altarLayerHasNativeSupport(level, floorY)) return floorY;
		}
		return minimum;
	}

	private static boolean altarLayerHasNativeSupport(ServerLevel level, int floorY) {
		for (int dx = -ALTAR_RADIUS; dx <= ALTAR_RADIUS; dx++) {
			for (int dz = -ALTAR_RADIUS; dz <= ALTAR_RADIUS; dz++) {
				BlockState support = level.getBlockState(new BlockPos(dx, floorY - 1, dz));
				if (support.isAir() || !support.getFluidState().isEmpty()) return false;
			}
		}
		return true;
	}

	private static BlockPos mainIslandSurfaceAir(ServerLevel level, int x, int z) {
		level.getChunkAt(new BlockPos(x, 64, z));
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new BlockPos(x, Math.max(level.getMinY() + 2, y), z);
	}

	private static void buildAltar(ServerLevel level, BlockPos center, boolean includeCore) {
		for (int dx = -ALTAR_RADIUS; dx <= ALTAR_RADIUS; dx++) {
			for (int dz = -ALTAR_RADIUS; dz <= ALTAR_RADIUS; dz++) {
				BlockPos floor = center.offset(dx, 0, dz);
				int edge = Math.max(Math.abs(dx), Math.abs(dz));
				BlockState state;
				if (edge == ALTAR_RADIUS) state = Blocks.CRYING_OBSIDIAN.defaultBlockState();
				else if (dx == 0 || dz == 0) state = Blocks.PURPUR_BLOCK.defaultBlockState();
				else if ((Math.abs(dx) + Math.abs(dz)) % 3 == 0) state = Blocks.AMETHYST_BLOCK.defaultBlockState();
				else state = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				level.setBlock(floor, state, EDIT_FLAGS);
				for (int dy = 1; dy <= 4; dy++) {
					BlockPos clearance = floor.above(dy);
					BlockState existing = level.getBlockState(clearance);
					if (clearance.equals(center.above()) && existing.is(ModBlocks.RESONANCE_CORE)) continue;
					if (!existing.isAir() && !existing.is(Blocks.BEDROCK)) {
						level.setBlock(clearance, Blocks.AIR.defaultBlockState(), EDIT_FLAGS);
					}
				}
			}
		}
		if (includeCore) level.setBlock(center.above(), ModBlocks.RESONANCE_CORE.defaultBlockState(), EDIT_FLAGS);
	}

	private static List<BlockPos> buildGateways(ServerLevel level) {
		List<BlockPos> cores = new ArrayList<>(GATEWAY_COUNT);
		Set<BlockPos> unique = new HashSet<>();
		for (int index = 0; index < GATEWAY_COUNT; index++) {
			double angle = Math.PI * 2.0D * index / GATEWAY_COUNT;
			int x = (int) Math.round(Math.cos(angle) * GATEWAY_RADIUS);
			int z = (int) Math.round(Math.sin(angle) * GATEWAY_RADIUS);
			level.getChunkAt(new BlockPos(x, 64, z));
			BlockPos existing = findBlockInColumn(level, x, z, ModBlocks.WARP_GATE_CORE);
			int y = existing == null
					? Math.max(64, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 3)
					: existing.getY();
			BlockPos core = new BlockPos(x, y, z);
			if (!unique.add(core)) throw new IllegalStateException("Gateway position collision at " + core);
			buildVanillaGatewayOutline(level, core);
			cores.add(core);
		}
		return List.copyOf(cores);
	}

	private static void buildVanillaGatewayOutline(ServerLevel level, BlockPos core) {
		for (int dy = -2; dy <= 2; dy++) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				if (dy == 0 || Math.abs(dy) == 1) {
					level.setBlock(core.relative(direction, 2).offset(0, dy, 0),
							Blocks.BEDROCK.defaultBlockState(), EDIT_FLAGS);
				}
			}
			if (Math.abs(dy) == 2) {
				level.setBlock(core.offset(0, dy, 0), Blocks.BEDROCK.defaultBlockState(), EDIT_FLAGS);
			}
		}
		level.setBlock(core, ModBlocks.WARP_GATE_CORE.defaultBlockState(), EDIT_FLAGS);
	}

	private static List<AnchorSlot> ensureAnchorSlots(ServerLevel level, boolean alreadyPrepared) {
		List<SpikeFeature.EndSpike> spikes = new ArrayList<>(SpikeFeature.getSpikesForLevel(level));
		spikes.sort(Comparator
				.comparingDouble((SpikeFeature.EndSpike spike) -> normalizedAngle(spike.getCenterX(), spike.getCenterZ()))
				.thenComparingInt(SpikeFeature.EndSpike::getHeight));
		if (spikes.size() != ANCHOR_COUNT) {
			throw new IllegalStateException("Expected exactly ten End spikes, found " + spikes.size());
		}

		List<AnchorSlot> slots = new ArrayList<>(ANCHOR_COUNT);
		for (int index = 0; index < spikes.size(); index++) {
			SpikeFeature.EndSpike spike = spikes.get(index);
			BlockPos position = new BlockPos(spike.getCenterX(), spike.getHeight() + 1, spike.getCenterZ());
			level.getChunkAt(position);
			if (alreadyPrepared) level.waitForEntities(new ChunkPos(position), 0);
			UUID uuid = deterministicAnchorUuid(level, index);
			Entity loaded = findLoadedEntity(level, uuid);
			if (loaded != null && !(loaded instanceof EndCrystal)) {
				throw new IllegalStateException("Anchor UUID " + uuid + " belongs to " + loaded.getType());
			}
			EndCrystal existing = loaded instanceof EndCrystal crystal ? crystal : null;
			if (existing == null && !alreadyPrepared) {
				removeCrystalsAt(level, position);
				existing = new EndCrystal(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
				existing.setUUID(uuid);
				existing.setShowBottom(false);
				existing.setInvulnerable(true);
				existing.addTag(ANCHOR_TAG);
				existing.addTag(ANCHOR_INDEX_PREFIX + index);
				if (!level.addFreshEntity(existing)) {
					throw new IllegalStateException("Unable to create authoritative anchor " + index);
				}
				rememberAnchor(level, existing);
			} else if (existing != null) {
				rememberAnchor(level, existing);
				existing.setShowBottom(false);
				existing.addTag(ANCHOR_TAG);
				existing.addTag(ANCHOR_INDEX_PREFIX + index);
			}
			buildAnchorCage(level, position);
			slots.add(new AnchorSlot(index, position, uuid));
		}
		return List.copyOf(slots);
	}

	private static void buildAnchorCage(ServerLevel level, BlockPos crystalPosition) {
		level.setBlock(crystalPosition.below(), ModBlocks.STABILITY_ANCHOR_CAGE.defaultBlockState(), EDIT_FLAGS);
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			level.setBlock(crystalPosition.relative(direction),
					ModBlocks.STABILITY_ANCHOR_CAGE.defaultBlockState(), EDIT_FLAGS);
		}
	}

	private static void removeCrystalsAt(ServerLevel level, BlockPos position) {
		AABB bounds = new AABB(position).inflate(2.0D);
		for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, bounds, Entity::isAlive)) {
			crystal.discard();
		}
	}

	private static UUID deterministicAnchorUuid(ServerLevel level, int index) {
		String key = TheFourthFrequency.MOD_ID + ":world_interface_anchor:" + level.getSeed() + ':' + index;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
	}

	private static Entity findLoadedEntity(ServerLevel level, UUID uuid) {
		Entity direct = level.getEntity(uuid);
		if (direct != null) return direct;
		EndCrystal known = knownAnchor(level, uuid);
		if (known != null) return known;
		for (Entity entity : level.getAllEntities()) {
			if (!entity.isRemoved() && uuid.equals(entity.getUUID())) {
				if (entity instanceof EndCrystal crystal) rememberAnchor(level, crystal);
				return entity;
			}
		}
		return null;
	}

	private static void rememberAnchor(ServerLevel level, EndCrystal crystal) {
		synchronized (KNOWN_ANCHORS) {
			KNOWN_ANCHORS.computeIfAbsent(level, ignored -> new HashMap<>()).put(crystal.getUUID(), crystal);
		}
	}

	private static EndCrystal knownAnchor(ServerLevel level, UUID uuid) {
		synchronized (KNOWN_ANCHORS) {
			Map<UUID, EndCrystal> anchors = KNOWN_ANCHORS.get(level);
			if (anchors == null) return null;
			EndCrystal crystal = anchors.get(uuid);
			if (crystal != null && (crystal.isRemoved() || crystal.level() != level)) {
				anchors.remove(uuid);
				crystal = null;
			}
			if (anchors.isEmpty()) KNOWN_ANCHORS.remove(level);
			return crystal;
		}
	}

	private static double normalizedAngle(int x, int z) {
		double angle = Math.atan2(z, x);
		return angle < 0.0D ? angle + Math.PI * 2.0D : angle;
	}

	private static BlockPos findBlockInColumn(ServerLevel level, int x, int z, Block block) {
		level.getChunkAt(new BlockPos(x, 64, z));
		for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
			BlockPos position = new BlockPos(x, y, z);
			if (level.getBlockState(position).is(block)) return position;
		}
		return null;
	}

	private static Set<BlockPos> computeProtectedPositions(PreparedArena arena) {
		Set<BlockPos> positions = new HashSet<>();
		for (int dx = -ALTAR_RADIUS; dx <= ALTAR_RADIUS; dx++) {
			for (int dz = -ALTAR_RADIUS; dz <= ALTAR_RADIUS; dz++) {
				for (int dy = -2; dy <= 6; dy++) positions.add(arena.center.offset(dx, dy, dz));
			}
		}
		for (BlockPos core : arena.gatewayCorePositions) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dy = -3; dy <= 3; dy++) {
					for (int dz = -3; dz <= 3; dz++) positions.add(core.offset(dx, dy, dz));
				}
			}
		}
		for (AnchorSlot anchor : arena.anchors) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -2; dy <= 3; dy++) {
					for (int dz = -2; dz <= 2; dz++) positions.add(anchor.position.offset(dx, dy, dz));
				}
			}
		}
		for (int dx = -PORTAL_SAFE_RADIUS; dx <= PORTAL_SAFE_RADIUS; dx++) {
			for (int dz = -PORTAL_SAFE_RADIUS; dz <= PORTAL_SAFE_RADIUS; dz++) {
				if (dx * dx + dz * dz > PORTAL_SAFE_RADIUS * PORTAL_SAFE_RADIUS) continue;
				for (int dy = -8; dy <= 8; dy++) positions.add(arena.center.offset(dx, dy, dz));
			}
		}
		return Set.copyOf(positions);
	}

	private static void removeInitialHostileDragon(ServerLevel level) {
		for (EnderDragon dragon : List.copyOf(level.getDragons())) {
			if (!FriendlyDragonService.isFriendly(dragon)) dragon.discard();
		}
	}

	/** Suppresses vanilla fight bookkeeping without invoking EndDragonFight#setDragonKilled. */
	public static void suppressVanillaFight(ServerLevel level) {
		EndDragonFight fight = level.getDragonFight();
		if (fight == null) return;
		EndDragonFightAccessor accessor = (EndDragonFightAccessor) fight;
		accessor.thefourthfrequency$setDragonKilledSilently(true);
		accessor.thefourthfrequency$setNeedsStateScanning(false);
		accessor.thefourthfrequency$setDragonUuid(null);
		accessor.thefourthfrequency$setRespawnStage(null);
		accessor.thefourthfrequency$setRespawnCrystals(null);
		ServerBossEvent event = accessor.thefourthfrequency$dragonEvent();
		event.setVisible(false);
		event.removeAllPlayers();
	}

	private static long mix(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	public record PreparedArena(BlockPos center, BlockPos altar, BlockPos safeSpawn,
			List<BlockPos> gatewayCorePositions, List<AnchorSlot> anchors) {
		public PreparedArena {
			Objects.requireNonNull(center, "center");
			Objects.requireNonNull(altar, "altar");
			Objects.requireNonNull(safeSpawn, "safeSpawn");
			center = center.immutable();
			altar = altar.immutable();
			safeSpawn = safeSpawn.immutable();
			gatewayCorePositions = gatewayCorePositions.stream().map(BlockPos::immutable).toList();
			anchors = List.copyOf(anchors);
			if (gatewayCorePositions.size() != GATEWAY_COUNT) {
				throw new IllegalArgumentException("Prepared arena must contain exactly twenty gateways");
			}
			if (new HashSet<>(gatewayCorePositions).size() != GATEWAY_COUNT) {
				throw new IllegalArgumentException("Prepared gateway positions must be unique");
			}
			if (anchors.size() != ANCHOR_COUNT) {
				throw new IllegalArgumentException("Prepared arena must contain exactly ten anchors");
			}
			if (anchors.stream().map(AnchorSlot::index).distinct().count() != ANCHOR_COUNT
					|| anchors.stream().map(AnchorSlot::position).distinct().count() != ANCHOR_COUNT
					|| anchors.stream().map(AnchorSlot::crystalUuid).distinct().count() != ANCHOR_COUNT) {
				throw new IllegalArgumentException("Prepared anchors must have unique indices, positions and UUIDs");
			}
		}
	}

	public record AnchorSlot(int index, BlockPos position, UUID crystalUuid) {
		public AnchorSlot {
			if (index < 0 || index >= ANCHOR_COUNT) throw new IllegalArgumentException("Anchor index must be 0..9");
			Objects.requireNonNull(position, "position");
			Objects.requireNonNull(crystalUuid, "crystalUuid");
			position = position.immutable();
		}
	}

	private record ArenaRuntime(PreparedArena arena, Set<BlockPos> protectedPositions,
			TerrainScarQueue scars) {
	}

	private static final class TerrainScarQueue {
		private final Deque<BlockPos> pending = new ArrayDeque<>();
		private final Set<Long> pendingKeys = new LinkedHashSet<>();
		private int permanentEdits;

		private int enqueue(Collection<BlockPos> candidates, int requestedMaximum, long seed) {
			if (requestedMaximum == 0 || permanentEdits >= MAX_PERMANENT_EDITS) return 0;
			List<BlockPos> ordered = candidates.stream()
					.filter(Objects::nonNull)
					.map(BlockPos::immutable)
					.distinct()
					.sorted(Comparator.<BlockPos>comparingLong(position -> mix(seed ^ position.asLong()))
							.thenComparingLong(BlockPos::asLong))
					.toList();
			int available = Math.min(MAX_PENDING_SCARS - pending.size(),
					MAX_PERMANENT_EDITS - permanentEdits - pending.size());
			int limit = Math.min(requestedMaximum, Math.max(0, available));
			int added = 0;
			for (BlockPos position : ordered) {
				if (added >= limit) break;
				if (!pendingKeys.add(position.asLong())) continue;
				pending.addLast(position);
				added++;
			}
			return added;
		}

		private int process(ServerLevel level, Set<BlockPos> protectedPositions) {
			if (permanentEdits >= MAX_PERMANENT_EDITS) {
				clearPending();
				return 0;
			}
			int changed = 0;
			int examined = 0;
			while (!pending.isEmpty() && changed < MAX_EDITS_PER_TICK && examined < 64
					&& permanentEdits < MAX_PERMANENT_EDITS) {
				BlockPos position = pending.removeFirst();
				pendingKeys.remove(position.asLong());
				examined++;
				if (!insideEditableArena(position) || !level.isInWorldBounds(position)
						|| !level.hasChunkAt(position)) continue;
				BlockState state = level.getBlockState(position);
				if (protectedPositions.contains(position) || !canDestroy(level, position, state)) continue;
				if (level.setBlock(position, Blocks.AIR.defaultBlockState(), EDIT_FLAGS)) {
					changed++;
					permanentEdits++;
				}
			}
			return changed;
		}

		private void clearPending() {
			pending.clear();
			pendingKeys.clear();
		}
	}
}
