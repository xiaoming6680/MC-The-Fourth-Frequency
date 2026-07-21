package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionAnomalyMixin {
	@Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
	private void thefourthfrequency$compilePrivateTrace(BlockPos pos,
			CallbackInfoReturnable<BlockState> callback) {
		if (AnomalyPresentationController.isLightSourceHidden(pos)) {
			callback.setReturnValue(Blocks.AIR.defaultBlockState());
			return;
		}
		BlockState original = callback.getReturnValue();
		BlockState replacement = AnomalyPresentationController.visualReplacement(pos, original);
		if (replacement == original) return;
		AnomalyPresentationController.markTraceRendered(pos);
		callback.setReturnValue(replacement);
	}
}
