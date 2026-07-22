package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.EndBossIntrusionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class BoundTerminalContainerMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$preventBoundTerminalStorage(int slotId, int button, ClickType clickType,
			Player player, CallbackInfo callback) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		Slot clicked = slotId >= 0 && slotId < menu.slots.size() ? menu.slots.get(slotId) : null;
		boolean intrusionDrop = (clickType == ClickType.THROW && clicked != null
				&& clicked.container instanceof Inventory
				&& EndBossIntrusionService.isSlotLocked(serverPlayer, clicked.getContainerSlot()))
				|| (slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE
				&& EndBossIntrusionService.isLockedStack(serverPlayer, menu.getCarried()));
		if (intrusionDrop) {
			EndBossIntrusionService.notifyRejected(serverPlayer);
			callback.cancel();
			return;
		}
		boolean nonPlayerTarget = clicked != null && !(clicked.container instanceof Inventory);
		boolean blocked = (nonPlayerTarget && TerminalData.isBound(menu.getCarried()))
				|| (clickType == ClickType.QUICK_MOVE && clicked != null
						&& clicked.container instanceof Inventory && TerminalData.isBound(clicked.getItem()))
				|| (clickType == ClickType.THROW && clicked != null && TerminalData.isBound(clicked.getItem()))
				|| (slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && TerminalData.isBound(menu.getCarried()))
				|| (clickType == ClickType.SWAP && nonPlayerTarget && button >= 0 && button < 9
						&& TerminalData.isBound(player.getInventory().getItem(button)));
		if (!blocked) {
			return;
		}
		serverPlayer.displayClientMessage(Component.translatable("message.thefourthfrequency.terminal.bound_no_container"), true);
		callback.cancel();
	}
}
