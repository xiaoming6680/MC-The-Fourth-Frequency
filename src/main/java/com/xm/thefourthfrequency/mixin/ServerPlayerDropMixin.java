package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.ending.EndBossIntrusionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops the drop-key path before vanilla removes the selected stack from its leased slot. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void thefourthfrequency$clearIntrusionLeaseOnDeath(DamageSource source, CallbackInfo callback) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		EndBossIntrusionService.handleDeath(player.level().getServer(), player);
	}

	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$preventIntrusionLeaseDrop(boolean entireStack, CallbackInfo callback) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (!EndBossIntrusionService.isSelectedSlotLocked(player)) return;
		EndBossIntrusionService.notifyRejected(player);
		callback.cancel();
	}

	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$preventIntrusionLeaseStackDrop(ItemStack stack, boolean randomOffset,
			boolean retainOwnership, CallbackInfoReturnable<ItemEntity> callback) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (!EndBossIntrusionService.isLockedStack(player, stack)) return;
		EndBossIntrusionService.notifyRejected(player);
		callback.setReturnValue(null);
	}
}
