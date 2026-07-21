package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.world.TerminalActivityTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Inject(method = "onTake", at = @At("TAIL"))
	private void thefourthfrequency$recordCrafting(Player player, ItemStack crafted, CallbackInfo callback) {
		if (player instanceof ServerPlayer serverPlayer && !crafted.isEmpty()) {
			TerminalActivityTracker.recordCrafted(serverPlayer, crafted);
		}
	}
}
