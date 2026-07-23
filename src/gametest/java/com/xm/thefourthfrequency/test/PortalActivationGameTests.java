package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.ending.StrongholdPortalService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;

import java.lang.reflect.Method;

public final class PortalActivationGameTests implements CustomTestMethodInvoker {
	private static final int[][] FRAMES = {
			{-1, -2}, {0, -2}, {1, -2}, {-1, 2}, {0, 2}, {1, 2},
			{-2, -1}, {-2, 0}, {-2, 1}, {2, -1}, {2, 0}, {2, 1}
	};

	@GameTest
	public void completeInwardFacingVanillaFrameIsRequired(GameTestHelper helper) {
		BlockPos center = helper.absolutePos(new BlockPos(8, 3, 8));
		for (int[] offset : FRAMES) {
			helper.getLevel().setBlockAndUpdate(center.offset(offset[0], 0, offset[1]),
					Blocks.END_PORTAL_FRAME.defaultBlockState()
							.setValue(EndPortalFrameBlock.FACING, facing(offset[0], offset[1]))
							.setValue(EndPortalFrameBlock.HAS_EYE, true));
		}
		helper.assertTrue(StrongholdPortalService.validPortalRing(helper.getLevel(), center),
				"World Interface activation requires a complete inward-facing vanilla portal ring");
		helper.assertValueEqual(StrongholdPortalService.findPortalRingNear(
				helper.getLevel(), center, 4).orElseThrow(), center,
				"Nearby portal detection recovers the real ring center");
		helper.assertValueEqual(StrongholdPortalService.eyeCount(helper.getLevel(), center), 12,
				"The activation ring reports all twelve inserted eyes");
		BlockPos wrong = center.offset(-1, 0, -2);
		helper.getLevel().setBlockAndUpdate(wrong, helper.getLevel().getBlockState(wrong)
				.setValue(EndPortalFrameBlock.FACING, Direction.NORTH));
		helper.assertFalse(StrongholdPortalService.validPortalRing(helper.getLevel(), center),
				"A decorative or wrongly oriented frame ring cannot activate the finale");
		helper.succeed();
	}

	private static Direction facing(int x, int z) {
		if (x == -2) return Direction.EAST;
		if (x == 2) return Direction.WEST;
		if (z == -2) return Direction.SOUTH;
		return Direction.NORTH;
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
