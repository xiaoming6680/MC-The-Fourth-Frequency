package com.xm.thefourthfrequency.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherAnomalyMixin {
	private static final ThreadLocal<Boolean> REPLACING = ThreadLocal.withInitial(() -> false);

	@Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$privateBlockPresentation(BlockState state, BlockPos pos,
			BlockAndTintGetter level, PoseStack pose, VertexConsumer consumer, boolean checkSides,
			List<BlockModelPart> modelParts, CallbackInfo callback) {
		if (REPLACING.get()) return;
		if (AnomalyPresentationController.isLightSourceHidden(pos)) {
			callback.cancel();
			return;
		}
		BlockState replacement = AnomalyPresentationController.visualReplacement(pos, state);
		if (replacement == state) return;
		AnomalyPresentationController.markTraceRendered(pos);
		REPLACING.set(true);
		try {
			List<BlockModelPart> replacementParts = ((BlockRenderDispatcher) (Object) this)
					.getBlockModel(replacement).collectParts(RandomSource.create(pos.asLong()));
			((BlockRenderDispatcher) (Object) this).renderBatched(replacement, pos, level, pose, consumer,
					checkSides, replacementParts);
			callback.cancel();
		} finally { REPLACING.set(false); }
	}
}
