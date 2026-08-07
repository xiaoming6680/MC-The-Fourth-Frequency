package com.xm.thefourthfrequency.ending;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AltarShapeTest {
	@Test
	void finalExitReplacesTheExistingTopPlatformInsteadOfAddingAnotherTerrace() {
		BlockPos floor = new BlockPos(7, 64, -9);
		BlockPos core = AltarShape.corePosition(floor);
		BlockPos portal = AltarShape.exitPortalCenter(core);

		assertEquals(floor.above(AltarShape.topOffset(0, 0)), portal);
		assertEquals(core.below(), portal,
				"the portal must replace the platform under the core, not be built above it");

		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (!AltarShape.isExitFrame(x, z)) continue;
				assertEquals(1, AltarShape.topOffset(x, z),
						"every same-height frame block must rest on the altar's middle step");
			}
		}
	}
}
