package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.EndBossIntrusionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDropMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$preventBoundTerminalDrop(ItemStack stack, boolean randomDirection,
			CallbackInfoReturnable<ItemEntity> callback) {
		if ((Object) this instanceof ServerPlayer serverPlayer
				&& EndBossIntrusionService.isLockedStack(serverPlayer, stack)) {
			EndBossIntrusionService.notifyRejected(serverPlayer);
			callback.setReturnValue(null);
			return;
		}
		if (!TerminalData.isBound(stack)) {
			return;
		}
		if ((Object) this instanceof ServerPlayer serverPlayer) {
			serverPlayer.displayClientMessage(Component.translatable("message.thefourthfrequency.terminal.bound_no_drop"), true);
		}
		callback.setReturnValue(null);
	}
}
