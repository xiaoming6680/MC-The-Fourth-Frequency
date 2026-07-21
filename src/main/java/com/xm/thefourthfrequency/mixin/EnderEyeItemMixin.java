package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.terminal.TerminalToolService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderEyeItem.class)
public abstract class EnderEyeItemMixin {
	@Inject(method = "use", at = @At("RETURN"))
	private void thefourthfrequency$recordRealEyeThrow(Level level, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> callback) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& callback.getReturnValue().consumesAction()) {
			TerminalToolService.recordEyeSample(serverPlayer);
		}
	}
}
