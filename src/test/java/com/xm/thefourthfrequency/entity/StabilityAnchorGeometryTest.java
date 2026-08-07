package com.xm.thefourthfrequency.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anchor's shape and collapse performance, checked as rules rather than as numbers.
 *
 * <p>Everything here exists because three consumers have to agree about it - the model, the tether
 * renderer and the collapse effects - and the previous arrangement had each of them carrying its own
 * literal.
 */
final class StabilityAnchorGeometryTest {
	@Test
	void authoredExtentMatchesTheCollisionBoxAndStaysUnderTwoBlocksWide() {
		assertEquals(44.0F, StabilityAnchorGeometry.TOTAL_MODEL_HEIGHT, 0.001F,
				"The authored structure is 44 model units tall");
		assertEquals(StabilityAnchorGeometry.HEIGHT,
				StabilityAnchorGeometry.TOTAL_MODEL_HEIGHT / StabilityAnchorGeometry.UNITS_PER_BLOCK, 0.001F,
				"Collision height must be the authored height, not an independent guess");
		assertEquals(StabilityAnchorGeometry.WIDTH,
				StabilityAnchorGeometry.TOTAL_MODEL_WIDTH / StabilityAnchorGeometry.UNITS_PER_BLOCK, 0.001F,
				"Collision width must be the authored width");
		assertTrue(StabilityAnchorGeometry.TOTAL_MODEL_WIDTH <= 32.0F,
				"The drawn geometry must stay inside two blocks");
		assertTrue(StabilityAnchorGeometry.LOWEST_MODEL_Y > 0.0F,
				"The claws must reach below the entity origin to wrap the bedrock cap");
	}

	@Test
	void relayCoreIsTheSharedTetherEndpointAndSitsInsideTheEntity() {
		assertEquals(2.0D, StabilityAnchorGeometry.RELAY_CORE_HEIGHT, 1.0E-6D);
		assertTrue(StabilityAnchorGeometry.RELAY_CORE_HEIGHT < StabilityAnchorGeometry.HEIGHT,
				"The relay core must be inside the collision box, not floating above it");
		assertTrue(StabilityAnchorGeometry.CHEST_CORE_HEIGHT < StabilityAnchorGeometry.RELAY_CORE_HEIGHT,
				"The chest core is below the emitter it feeds");

		// The block form and the entity form have to name the same point: one is what the tether
		// renderer has (a snapshot position), the other is what the collapse effects have (a live
		// entity). The old +0.7 literal in the beam renderer was neither.
		BlockPos position = new BlockPos(41, 71, -13);
		Vec3 fromBlock = StabilityAnchorGeometry.relayCore(position);
		Vec3 fromEntity = StabilityAnchorGeometry.relayCore(
				new Vec3(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D));
		assertEquals(fromBlock, fromEntity);
		assertEquals(position.getY() + 2.0D, fromBlock.y, 1.0E-6D);
		assertEquals(position.getX() + 0.5D, fromBlock.x, 1.0E-6D);

		assertThrows(IllegalArgumentException.class,
				() -> StabilityAnchorGeometry.relayCore((BlockPos) null));
		assertThrows(IllegalArgumentException.class,
				() -> StabilityAnchorGeometry.chestCore((Vec3) null));
	}

	@Test
	void collapsePhasesCoverTheWholeSixteenTicksWithoutOverlapOrGap() {
		assertEquals(StabilityAnchorGeometry.CollapsePhase.NONE,
				StabilityAnchorGeometry.collapsePhase(-1.0F));
		assertEquals(StabilityAnchorGeometry.CollapsePhase.FRACTURE,
				StabilityAnchorGeometry.collapsePhase(0.0F));
		assertEquals(StabilityAnchorGeometry.CollapsePhase.TETHER_SNAP,
				StabilityAnchorGeometry.collapsePhase(StabilityAnchorGeometry.COLLAPSE_FRACTURE_END));
		assertEquals(StabilityAnchorGeometry.CollapsePhase.IMPLOSION,
				StabilityAnchorGeometry.collapsePhase(StabilityAnchorGeometry.COLLAPSE_TETHER_END));
		assertEquals(StabilityAnchorGeometry.CollapsePhase.RESIDUE,
				StabilityAnchorGeometry.collapsePhase(StabilityAnchorGeometry.COLLAPSE_IMPLOSION_END));
		assertEquals(StabilityAnchorGeometry.CollapsePhase.DONE,
				StabilityAnchorGeometry.collapsePhase(StabilityAnchorGeometry.COLLAPSE_TICKS));
		assertTrue(StabilityAnchorGeometry.COLLAPSE_FRACTURE_END < StabilityAnchorGeometry.COLLAPSE_TETHER_END
						&& StabilityAnchorGeometry.COLLAPSE_TETHER_END < StabilityAnchorGeometry.COLLAPSE_IMPLOSION_END
						&& StabilityAnchorGeometry.COLLAPSE_IMPLOSION_END < StabilityAnchorGeometry.COLLAPSE_TICKS,
				"The four beats must be ordered and none may be empty");
	}

	@Test
	void theStructureIsGoneBeforeTheResidueBeatAndTheFoldNeverReverses() {
		assertEquals(1.0F, StabilityAnchorGeometry.collapsePresence(0.0F), 1.0E-5F);
		assertEquals(1.0F,
				StabilityAnchorGeometry.collapsePresence(StabilityAnchorGeometry.COLLAPSE_TETHER_END), 1.0E-5F);
		assertEquals(0.0F,
				StabilityAnchorGeometry.collapsePresence(StabilityAnchorGeometry.COLLAPSE_IMPLOSION_END), 1.0E-5F);
		assertEquals(0.0F,
				StabilityAnchorGeometry.collapsePresence(StabilityAnchorGeometry.COLLAPSE_TICKS), 1.0E-5F);

		float previousPresence = Float.MAX_VALUE;
		float previousFold = -1.0F;
		for (int tick = 0; tick <= StabilityAnchorGeometry.COLLAPSE_TICKS; tick++) {
			float presence = StabilityAnchorGeometry.collapsePresence(tick);
			float fold = StabilityAnchorGeometry.collapseFold(tick);
			assertTrue(presence <= previousPresence + 1.0E-5F, "presence must never grow back at " + tick);
			assertTrue(fold >= previousFold - 1.0E-5F, "the fold must never unfold at " + tick);
			assertTrue(presence >= 0.0F && presence <= 1.0F);
			assertTrue(fold >= 0.0F && fold <= 1.0F);
			previousPresence = presence;
			previousFold = fold;
		}
		assertEquals(0.0F, StabilityAnchorGeometry.collapseFold(StabilityAnchorGeometry.COLLAPSE_FRACTURE_END),
				1.0E-5F, "The fracture beat is light only; nothing has moved yet");
		assertEquals(1.0F, StabilityAnchorGeometry.collapseFold(StabilityAnchorGeometry.COLLAPSE_IMPLOSION_END),
				1.0E-5F);
	}

	@Test
	void theSeveredTetherRetractsOnceAndIsGoneBeforeTheImplosion() {
		assertEquals(1.0F, StabilityAnchorGeometry.collapseTetherReach(0.0F), 1.0E-5F);
		assertEquals(1.0F,
				StabilityAnchorGeometry.collapseTetherReach(StabilityAnchorGeometry.COLLAPSE_FRACTURE_END),
				1.0E-5F);
		assertEquals(0.0F,
				StabilityAnchorGeometry.collapseTetherReach(StabilityAnchorGeometry.COLLAPSE_TETHER_END),
				1.0E-5F);
		float previous = Float.MAX_VALUE;
		for (int step = 0; step <= 64; step++) {
			float age = step * StabilityAnchorGeometry.COLLAPSE_TICKS / 64.0F;
			float reach = StabilityAnchorGeometry.collapseTetherReach(age);
			assertTrue(reach <= previous + 1.0E-5F, "the band must only ever be hauled in, at " + age);
			assertTrue(reach >= 0.0F && reach <= 1.0F);
			previous = reach;
		}
	}

	@Test
	void collapseParticlesCarryBothAWholePerformanceAndAPerTickCeiling() {
		assertTrue(StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES > 0);
		assertTrue(StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES_PER_TICK > 0);
		assertTrue(StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES_PER_TICK
						< StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES,
				"A per-tick ceiling that equals the total is not a per-tick ceiling");
		// Ten anchors can fall inside a couple of seconds. Whatever the budget is, the arena-wide
		// worst case has to stay a number somebody chose rather than an emergent one.
		assertTrue(StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES * StabilityAnchorEntity.ANCHOR_COUNT <= 1_024,
				"Ten simultaneous collapses must stay inside a stated arena-wide particle budget");
		assertFalse(StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES_PER_TICK
				* StabilityAnchorGeometry.COLLAPSE_TICKS < StabilityAnchorGeometry.MAX_COLLAPSE_PARTICLES
				/ 2, "The per-tick ceiling must not be so tight that the budget can never be spent");
	}
}
