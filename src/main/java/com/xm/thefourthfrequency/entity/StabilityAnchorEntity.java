package com.xm.thefourthfrequency.entity;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * One of the ten stability anchors: a fixed clamp standing on a spike's bedrock cap, holding the
 * World Interface's tether.
 *
 * <p>This entity is a body and a performance, never an authority. Whether an anchor is standing is
 * decided entirely by {@code WorldInterfaceState}; the entity is reconciled against that record on
 * load and after a restart, and its own synched fields carry nothing but which slot it fills and how
 * far through a short, bounded animation it is.
 *
 * <p><b>Why it outlives its own destruction.</b> A hit that lands is committed to the world state on
 * the same tick, and from that instant the anchor stops healing the interface, stops holding stable
 * ground and stops being hittable. What remains for sixteen ticks is geometry coming apart. The
 * distinction matters because the old {@code EndCrystal} was discarded the moment it fell, which
 * meant the one thing a player spends the fight trying to do had no visible result at all beyond a
 * puff of particles. A collapsing anchor loaded from disk is discarded on its first tick instead:
 * the performance is a live-session effect and is never a thing a save has to remember finishing.
 */
public final class StabilityAnchorEntity extends Entity {
	public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "stability_anchor");
	public static final int ANCHOR_COUNT = 10;
	/** Sentinel for "the collapse performance is not running"; the clock is server game time. */
	public static final long INACTIVE = Long.MIN_VALUE;

	private static final EntityDataAccessor<Integer> ANCHOR_INDEX = SynchedEntityData.defineId(
			StabilityAnchorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> COLLAPSE_START = SynchedEntityData.defineId(
			StabilityAnchorEntity.class, EntityDataSerializers.LONG);

	/** Gold, platinum white, charcoal and dim violet: the four materials the structure is made of. */
	private static final int[] COLLAPSE_MOTE_COLORS = {0xD8B25A, 0xEFE7D2, 0x24202B, 0x6A4C8C};

	/** Set only by {@link #readAdditionalSaveData}; a reloaded performance is cleaned up, not resumed. */
	private boolean loadedCollapsing;
	private int emittedCollapseParticles;

	public StabilityAnchorEntity(EntityType<? extends StabilityAnchorEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	/**
	 * Resolves the separately registered type without coupling this class to {@code ModEntities}
	 * initialization order, mirroring how the encounter's other bespoke entities are created.
	 */
	public static StabilityAnchorEntity create(ServerLevel level, int index, double x, double y, double z) {
		if (index < 0 || index >= ANCHOR_COUNT) throw new IllegalArgumentException("Anchor index must be 0..9");
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(TYPE_ID);
		if (type == null) throw new IllegalStateException("Stability anchor entity type is not registered");
		Entity created = type.create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof StabilityAnchorEntity anchor)) {
			throw new IllegalStateException("Registered stability anchor type has the wrong entity factory");
		}
		anchor.setAnchorIndex(index);
		anchor.setPos(x, y, z);
		return anchor;
	}

	public int anchorIndex() {
		return Math.clamp(entityData.get(ANCHOR_INDEX), 0, ANCHOR_COUNT - 1);
	}

	public void setAnchorIndex(int index) {
		if (index < 0 || index >= ANCHOR_COUNT) throw new IllegalArgumentException("Anchor index must be 0..9");
		entityData.set(ANCHOR_INDEX, index);
	}

	public boolean collapsing() {
		return entityData.get(COLLAPSE_START) != INACTIVE;
	}

	/**
	 * Age of the destruction performance in ticks, or a negative value when none is running.
	 * Partial ticks are the caller's business; this is the integral clock both sides share.
	 */
	public float collapseAge(float partialTick) {
		return performanceAge(entityData.get(COLLAPSE_START), partialTick);
	}

	private float performanceAge(long start, float partialTick) {
		if (start == INACTIVE) return -1.0F;
		return (float) (level().getGameTime() - start) + partialTick;
	}

	/**
	 * Starts the destruction performance. The caller has already committed the anchor as destroyed,
	 * so this only decides what the sixteen ticks after that look like.
	 */
	public void beginCollapse() {
		if (collapsing() || level().isClientSide()) return;
		entityData.set(COLLAPSE_START, level().getGameTime());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ANCHOR_INDEX, 0);
		builder.define(COLLAPSE_START, INACTIVE);
	}

	@Override
	public void tick() {
		super.tick();
		// Fixed by definition. Anything that pushed a velocity into this - a nearby detonation, a
		// restored save - would otherwise walk the anchor off the cap its claws are gripping.
		setDeltaMovement(Vec3.ZERO);
		if (level().isClientSide()) {
			tickCollapseParticles();
			return;
		}
		if (loadedCollapsing) {
			discard();
			return;
		}
		long start = entityData.get(COLLAPSE_START);
		if (start != INACTIVE && level().getGameTime() - start >= StabilityAnchorGeometry.COLLAPSE_TICKS) {
			discard();
			return;
		}
	}

	/**
	 * The implosion and the residue, drawn locally on every client that can see it.
	 *
	 * <p>Deliberately client-side: this is decoration on an outcome the server already committed, so
	 * paying for a broadcast per mote would be spending bandwidth on something no rule depends on.
	 * Both a per-tick and a whole-performance ceiling apply, which is what keeps ten anchors falling
	 * in quick succession from turning into an unbounded spray.
	 */
	private void tickCollapseParticles() {
		float age = collapseAge(0.0F);
		StabilityAnchorGeometry.CollapsePhase phase = StabilityAnchorGeometry.collapsePhase(age);
		if (phase == StabilityAnchorGeometry.CollapsePhase.NONE
				|| phase == StabilityAnchorGeometry.CollapsePhase.DONE) return;
		int budget = Math.min(StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES_PER_TICK,
				StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES - emittedCollapseParticles);
		if (budget <= 0) return;
		Vec3 relay = StabilityAnchorGeometry.relayCore(position());
		Vec3 chest = StabilityAnchorGeometry.chestCore(position());
		// The first beats throw material outward; the rest of the performance takes it back.
		//
		// The whole collapse used to be inward, on the reading that the structure is being reclaimed
		// rather than destroyed. That reading is still the ending of it - and the implosion below is
		// unchanged - but it made the moment of destruction itself, which is the single thing a player
		// spends this fight trying to cause, look like a light going out. Something has to come off it
		// first.
		if (phase == StabilityAnchorGeometry.CollapsePhase.FRACTURE
				|| phase == StabilityAnchorGeometry.CollapsePhase.TETHER_SNAP) {
			emitCollapseBurst(relay, chest, budget);
			return;
		}
		boolean residue = phase == StabilityAnchorGeometry.CollapsePhase.RESIDUE;
		int count = residue ? Math.min(budget, 3) : budget;
		for (int index = 0; index < count; index++) {
			Vec3 origin = index % 2 == 0 ? chest : relay;
			double spread = residue ? 0.30D : 0.55D;
			double x = origin.x + (random.nextDouble() - 0.5D) * spread * 2.0D;
			double y = origin.y + (random.nextDouble() - 0.5D) * spread * 2.0D;
			double z = origin.z + (random.nextDouble() - 0.5D) * spread * 2.0D;
			// Inward, not outward: the structure is being taken back, not blown apart.
			Vec3 pull = origin.subtract(x, y, z).scale(residue ? 0.04D : 0.16D);
			ParticleOptions options = residue
					? ParticleTypes.REVERSE_PORTAL
					: new DustParticleOptions(COLLAPSE_MOTE_COLORS[random.nextInt(COLLAPSE_MOTE_COLORS.length)],
							0.7F + random.nextFloat() * 0.5F);
			level().addParticle(options, x, y, z, pull.x, pull.y, pull.z);
			emittedCollapseParticles++;
		}
	}

	/** Plating thrown clear of the two cores, on the two beats before the structure folds. */
	private void emitCollapseBurst(Vec3 relay, Vec3 chest, int budget) {
		for (int index = 0; index < budget; index++) {
			Vec3 origin = index % 3 == 0 ? relay : chest;
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double speed = 0.28D + random.nextDouble() * 0.42D;
			level().addParticle(new DustParticleOptions(
							COLLAPSE_MOTE_COLORS[random.nextInt(COLLAPSE_MOTE_COLORS.length)],
							1.0F + random.nextFloat() * 0.8F),
					origin.x, origin.y, origin.z,
					Math.cos(angle) * speed, 0.10D + random.nextDouble() * 0.35D,
					Math.sin(angle) * speed);
			emittedCollapseParticles++;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isRemoved() || collapsing() || isInvulnerableToBase(source)) return false;
		return EndBossEncounterService.handleAnchorDamage(level, this, source, amount).orElse(false);
	}

	/** Stops being a target the instant it is destroyed, however long the geometry takes to leave. */
	@Override
	public boolean isPickable() {
		return !collapsing() && !isRemoved();
	}

	@Override
	public boolean canBeHitByProjectile() {
		return isPickable();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity entity) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return isPickable();
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256.0D * 256.0D;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("anchor_index", anchorIndex());
		// Saved so a save taken mid-performance is recognisable on load and cleaned up, rather than
		// resuming a sixteen-tick animation against a fresh game clock.
		output.putBoolean("collapsing", collapsing());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setAnchorIndex(Math.clamp(input.getIntOr("anchor_index", 0), 0, ANCHOR_COUNT - 1));
		loadedCollapsing = input.getBooleanOr("collapsing", false);
		entityData.set(COLLAPSE_START, INACTIVE);
		setNoGravity(true);
		noPhysics = true;
	}
}
