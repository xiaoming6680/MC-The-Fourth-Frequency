package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.ConfiscationService;
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
	/**
	 * The two refusals share this injection because they share a reason: the stack is not the
	 * player's to throw away right now.
	 *
	 * <p>{@code Player#drop(ItemStack, boolean)} is where the drop key and {@code Inventory#dropAll}
	 * both end up, so a death destroys a custody placeholder rather than scattering a barrier across
	 * the arena - the real stack is in the encounter's ledger and comes back on respawn.</p>
	 */
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$preventUndroppableDrop(ItemStack stack, boolean randomDirection,
			CallbackInfoReturnable<ItemEntity> callback) {
		String refusal;
		if (TerminalData.isBound(stack)) {
			refusal = "message.thefourthfrequency.terminal.bound_no_drop";
		} else if (ConfiscationService.isPlaceholder(stack)) {
			refusal = "message.thefourthfrequency.world_interface.confiscated_locked";
		} else {
			return;
		}
		if ((Object) this instanceof ServerPlayer serverPlayer) {
			com.xm.thefourthfrequency.terminal.TerminalNoticeService.denied(serverPlayer, refusal);
		}
		callback.setReturnValue(null);
	}
}
