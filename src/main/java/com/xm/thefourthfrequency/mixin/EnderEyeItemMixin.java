package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import com.xm.thefourthfrequency.ending.StrongholdPortalService;
import com.xm.thefourthfrequency.pursuit.PursuitProgressPolicy;
import com.xm.thefourthfrequency.terminal.TerminalNoticeService;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderEyeItem.class)
public abstract class EnderEyeItemMixin {
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$requirePursuitBeforeFinalEye(UseOnContext context,
			CallbackInfoReturnable<InteractionResult> callback) {
		if (context.getLevel().isClientSide()
				|| !(context.getPlayer() instanceof ServerPlayer player)) return;
		var state = context.getLevel().getBlockState(context.getClickedPos());
		if (!(state.getBlock() instanceof EndPortalFrameBlock)
				|| state.getValue(EndPortalFrameBlock.HAS_EYE)) return;
		var center = StrongholdPortalService.findPortalRingNear(
				context.getLevel(), context.getClickedPos(), 4).orElse(null);
		if (center == null || StrongholdPortalService.eyeCount(context.getLevel(), center) != 11) return;
		int encountered = FrequencyWorldData.get(player.level().getServer())
				.terminalRecord(player.getUUID())
				.map(tag -> tag.getIntOr(TerminalData.PURSUIT_ENCOUNTERED_CHASES, 0))
				.orElse(0);
		if (PursuitProgressPolicy.finalEyeReady(encountered)) return;
		TerminalNoticeService.denied(player,
				"message.thefourthfrequency.world_interface.final_eye_requires_pursuit");
		callback.setReturnValue(InteractionResult.FAIL);
	}

	/**
	 * Runs after vanilla has actually inserted the eye. This avoids preparing the
	 * End for cancelled or failed interactions and makes the twelfth eye the sole
	 * authoritative trigger.
	 */
	@Inject(method = "useOn", at = @At("RETURN"))
	private void thefourthfrequency$prepareWorldInterfaceAfterFinalEye(UseOnContext context,
			CallbackInfoReturnable<InteractionResult> callback) {
		if (!callback.getReturnValue().consumesAction() || context.getLevel().isClientSide()
				|| !(context.getPlayer() instanceof ServerPlayer player)) return;
		var state = context.getLevel().getBlockState(context.getClickedPos());
		if (!(state.getBlock() instanceof EndPortalFrameBlock)
				|| !state.getValue(EndPortalFrameBlock.HAS_EYE)) return;
		StrongholdPortalService.findPortalRingNear(context.getLevel(), context.getClickedPos(), 4)
				.filter(center -> StrongholdPortalService.eyeCount(context.getLevel(), center) == 12)
				.ifPresent(center -> EndBossEncounterService.prepareFromActivatedPortal(
						player.level(), center, player));
	}

	@Inject(method = "use", at = @At("RETURN"))
	private void thefourthfrequency$recordRealEyeThrow(Level level, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> callback) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& callback.getReturnValue().consumesAction()) {
			TerminalToolService.recordEyeSample(serverPlayer);
		}
	}
}
