package com.xm.thefourthfrequency.world;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape contract for Relay Station Zero. This is the first building the player ever stands in,
 * and it is assembled from a flat placement list rather than a structure file, so the properties
 * that make it habitable have to be asserted rather than eyeballed.
 */
final class ZeroStationLayoutTest {
	private static final BlockPos CENTER = new BlockPos(64, 71, -32);

	private static List<ZeroStationLayout.Placement> plan;
	/** Final state per position: the plan overwrites, so the last write is what the player sees. */
	private static Map<BlockPos, BlockState> settled;

	@BeforeAll
	static void bootstrapRegistries() {
		// Blocks touches BuiltInRegistries, which asserts the vanilla bootstrap has run, and that in
		// turn needs the game version detected. Nothing does this for a plain unit-test JVM.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		plan = ZeroStationLayout.create(CENTER);
		settled = new HashMap<>();
		for (ZeroStationLayout.Placement placement : plan) {
			settled.put(placement.position(), placement.state());
		}
	}

	@Test
	void planIsAPureFunctionOfTheCentre() {
		// A build spans many ticks and a shutdown can interrupt it. Resuming replays the plan from a
		// persisted index, so a second generation that differed anywhere would corrupt the station.
		assertEquals(plan, ZeroStationLayout.create(CENTER));
		List<ZeroStationLayout.Placement> elsewhere = ZeroStationLayout.create(CENTER.offset(1000, 3, -700));
		assertEquals(plan.size(), elsewhere.size());
		for (int index = 0; index < plan.size(); index++) {
			assertEquals(plan.get(index).state(), elsewhere.get(index).state(),
					"Weathering must not depend on where in the world the station lands");
		}
	}

	@Test
	void planStaysInsideTheTickBudget() {
		assertTrue(plan.size() > 512, "Station must require multiple bounded tick batches");
		assertTrue(plan.size() < 4_096, "Station plan must remain compact: " + plan.size());
	}

	@Test
	void floorCourseIsWrittenBeforeAnythingStandsOnIt() {
		// A player can connect during the build and is teleported onto the centre column. Every
		// floor block therefore lands in the opening batches, before clearing or masonry begins.
		int floorY = CENTER.getY() + ZeroStationLayout.FLOOR;
		int lastFloorIndex = -1;
		int firstNonFloorIndex = Integer.MAX_VALUE;
		for (int index = 0; index < plan.size(); index++) {
			if (plan.get(index).position().getY() == floorY) {
				lastFloorIndex = index;
			} else {
				firstNonFloorIndex = Math.min(firstNonFloorIndex, index);
			}
		}
		assertTrue(lastFloorIndex < firstNonFloorIndex,
				"The floor course must be one leading run of placements");
	}

	@Test
	void wakeUpColumnEndsBreathableAndSupported() {
		assertTrue(settled.get(CENTER).isAir(), "The player wakes standing in air");
		assertTrue(settled.get(CENTER.above()).isAir(), "The player wakes with head room");
		assertFalse(settled.get(CENTER.below()).isAir(), "The player wakes standing on something");
		assertTrue(settled.get(CENTER.below()).getLightEmission() > 0
						|| settled.get(CENTER.below(2)).getLightEmission() > 0,
				"The wake-up tile is lit from underneath; that is the station's only opening claim");
	}

	@Test
	void everyInteriorColumnIsEnclosedAndClear() {
		for (int x = -ZeroStationLayout.HALF_WIDTH + 1; x <= ZeroStationLayout.HALF_WIDTH - 1; x++) {
			for (int z = -ZeroStationLayout.HALF_DEPTH + 1; z <= ZeroStationLayout.HALF_DEPTH - 1; z++) {
				BlockPos floor = CENTER.offset(x, ZeroStationLayout.FLOOR, z);
				assertNotNull(settled.get(floor), "Interior floor hole at " + x + "," + z);
				assertFalse(settled.get(floor).isAir(), "Interior floor hole at " + x + "," + z);
				for (int y = ZeroStationLayout.FOUNDATION_BOTTOM; y < ZeroStationLayout.FLOOR; y++) {
					assertFalse(settled.get(CENTER.offset(x, y, z)).isAir(),
							"Foundation hole under " + x + "," + z);
				}
			}
		}
	}

	@Test
	void theStationIsLitByItsOwnFixtures() {
		// Regression guard: the station used to place a single copper bulb in its default, unpowered
		// state. Its one light source emitted nothing, so the interior bred hostiles on night one.
		long emitters = settled.values().stream().filter(state -> state.getLightEmission() > 0).count();
		assertTrue(emitters >= 8, "Station must light itself, got " + emitters + " emitters");
		assertTrue(settled.values().stream()
						.filter(state -> state.is(Blocks.WAXED_COPPER_BULB)
								|| state.is(Blocks.WAXED_EXPOSED_COPPER_BULB))
						.allMatch(state -> state.getValue(BlockStateProperties.LIT)),
				"Every bulb the station places must already be lit; nothing here is wired to redstone");
	}

	@Test
	void theDoorIsWellFormedAndIsTheOnlyWayThroughTheShell() {
		BlockPos door = CENTER.offset(0, 0, ZeroStationLayout.HALF_DEPTH);
		assertTrue(settled.get(door).getBlock() instanceof DoorBlock);
		assertTrue(settled.get(door.above()).getBlock() instanceof DoorBlock);
		assertEquals(DoubleBlockHalf.LOWER, settled.get(door).getValue(DoorBlock.HALF));
		assertEquals(DoubleBlockHalf.UPPER, settled.get(door.above()).getValue(DoorBlock.HALF));
		assertEquals(settled.get(door).getValue(DoorBlock.FACING),
				settled.get(door.above()).getValue(DoorBlock.FACING),
				"A door whose halves disagree pops itself on the first neighbour update");
		assertFalse(settled.get(door.below()).isAir(), "The door needs a floor block to survive on");

		// The collapsed east bay is meant to be seen and heard through, not walked through: the
		// station is sealed on the first night whether or not the player finds the door.
		for (int z = 2; z <= 3; z++) {
			for (int y = 0; y <= 2; y++) {
				BlockState tear = settled.get(CENTER.offset(ZeroStationLayout.HALF_WIDTH, y, z));
				assertNotNull(tear, "The tear must be grilled, not open");
				assertFalse(tear.isAir(), "The tear must be grilled, not open");
			}
		}
	}

	@Test
	void bedHalvesAgreeSoNeitherPopsOnPlacement() {
		BlockPos foot = CENTER.offset(-3, 0, 0);
		BlockPos head = CENTER.offset(-4, 0, 0);
		BlockState footState = settled.get(foot);
		BlockState headState = settled.get(head);
		assertEquals(BedPart.FOOT, footState.getValue(BedBlock.PART));
		assertEquals(BedPart.HEAD, headState.getValue(BedBlock.PART));
		assertEquals(footState.getValue(BedBlock.FACING), headState.getValue(BedBlock.FACING));
		assertEquals(head, foot.relative(BedBlock.getConnectedDirection(footState)));
		assertEquals(foot, head.relative(BedBlock.getConnectedDirection(headState)));

		int footIndex = lastIndexAt(foot);
		int headIndex = lastIndexAt(head);
		assertEquals(1, Math.abs(footIndex - headIndex),
				"Both halves must be placed back to back so no tick boundary splits the pair");
	}

	@Test
	void persistenceProofSampleIsPlainMasonry() {
		// M0ClientGameTest breaks this block and reopens the world to prove a completed station is
		// never rebuilt. If it ever became a window, a lamp or a doorway, the proof would be empty.
		BlockState sample = settled.get(ZeroStationLayout.solidWallSample(CENTER));
		assertNotNull(sample);
		assertFalse(sample.isAir());
		assertEquals(0, sample.getLightEmission());
		assertTrue(sample.is(Blocks.STONE_BRICKS) || sample.is(Blocks.CRACKED_STONE_BRICKS)
				|| sample.is(Blocks.MOSSY_STONE_BRICKS));
	}

	private static int lastIndexAt(BlockPos position) {
		for (int index = plan.size() - 1; index >= 0; index--) {
			if (plan.get(index).position().equals(position)) {
				return index;
			}
		}
		throw new AssertionError("No placement at " + position);
	}
}
