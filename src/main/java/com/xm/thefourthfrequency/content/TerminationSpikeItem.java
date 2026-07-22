package com.xm.thefourthfrequency.content;

import com.xm.thefourthfrequency.ending.FinalConfrontationService;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A real entity-interaction item for both the legacy altar and the End encounter. */
public final class TerminationSpikeItem extends Item {
	public TerminationSpikeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
			InteractionHand hand) {
		if (!(target instanceof MisreadBodyEntity body)) return InteractionResult.PASS;
		if (player.level().isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
		return FinalConfrontationService.useTerminationSpike(serverPlayer, body, stack)
				? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}
}
