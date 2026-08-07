package com.xm.thefourthfrequency.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import com.xm.thefourthfrequency.client_ui.TerminalHandheldAnimator;
import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.terminal.TerminalHandheldPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the held terminal its opening and closing performance.
 *
 * <p>The terminal is a heavy two-handed instrument, so the normal path replaces vanilla's
 * single-hand pass entirely: both hands come up under the device, it sits centred in the frame,
 * and opening it raises and enlarges it while {@code GameRendererTerminalFovMixin} closes the lens
 * a little. The device itself never deforms - it has no hinge, and what moves is the camera's
 * relationship to it.</p>
 *
 * <p>Drawing it in absolute camera space rather than hanging it off an arm is what keeps it inside
 * the frame. An earlier version rode vanilla's item transform and drove itself forward toward the
 * near plane, and at full size the brass rim went through both the arm and all four screen
 * edges.</p>
 *
 * <p>Nothing about the stack is touched. The six forms are chosen by the item definition from
 * {@code custom_model_data} slot 0, which the server owns, and the animation adds no second index
 * of its own. That is what keeps vanilla's equip swing out of the performance -
 * {@code ItemInHandRenderer.tick} replays it whenever the visible stack changes, so a per-frame
 * component write would have re-equipped the terminal several times a second for as long as it
 * played. The equip <em>height</em> is still honoured here, read from vanilla's own counter, so
 * switching to the terminal still raises it into view.</p>
 *
 * <h2>When the two-handed path stands down</h2>
 *
 * <ul>
 * <li><b>Something in the off hand.</b> Two hands on the terminal would mean silently not drawing
 * the other item, which is the same rule vanilla applies to maps. Then the single-hand branch
 * below adjusts vanilla's own rendering instead.</li>
 * <li><b>An anomaly is hiding the hands.</b> {@code ItemInHandRendererAnomalyMixin} cancels this
 * method for a detached second-person camera; taking over would put the hands back.</li>
 * </ul>
 *
 * <p>Third person and every other display context are untouched, so the terminal a bystander sees
 * is unaffected and no camera outside the holder's own first-person view moves.</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererTerminalMixin {
	@Shadow
	private ItemStack mainHandItem;
	@Shadow
	private ItemStack offHandItem;
	@Shadow
	private float mainHandHeight;
	@Shadow
	private float oMainHandHeight;

	@Shadow
	private void renderMapHand(PoseStack poseStack, SubmitNodeCollector collector, int light,
			HumanoidArm arm) {
		throw new AssertionError("shadow");
	}

	@Shadow
	public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			PoseStack poseStack, SubmitNodeCollector collector, int light);

	/**
	 * Stops the terminal's own state updates from being mistaken for a change of item.
	 *
	 * <p>Vanilla replays the equip animation whenever the visible stack changes, and it decides
	 * "changed" by comparing components - skipping only the component types that declare
	 * {@code ignoreSwapAnimation}. Neither {@code custom_data} nor {@code custom_model_data} does,
	 * and the terminal rewrites both: the first on every sync, the second whenever the unread lamp
	 * or the visual stage moves. Left alone, a terminal held open would drop out of the hands and
	 * climb back in several times a second, and simply receiving a signal would look like the
	 * player had switched items.</p>
	 *
	 * <p>Narrow on purpose. Only a terminal replacing a terminal is instant; every real swap -
	 * to the terminal, away from it, between two different items - still plays vanilla's animation,
	 * and the two-handed pass above honours the resulting equip height so the device really does
	 * rise into frame when it is selected.</p>
	 */
	@Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$ignoreTerminalStateChurn(ItemStack from, ItemStack to,
			CallbackInfoReturnable<Boolean> callback) {
		if (from.is(ModItems.OLD_TERMINAL) && to.is(ModItems.OLD_TERMINAL)) {
			callback.setReturnValue(true);
		}
	}

	/**
	 * Draws the terminal in both hands, in place of vanilla's whole first-person pass.
	 *
	 * <p>Order matters: the hands are drawn before the device is scaled up, so they stay life-sized
	 * underneath it. That is the same order vanilla uses for a two-handed map.</p>
	 */
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$presentTerminalInBothHands(float partialTick, PoseStack poseStack,
			SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo callback) {
		if (!this.mainHandItem.is(ModItems.OLD_TERMINAL) || !this.offHandItem.isEmpty()) return;
		if (AnomalyPresentationController.isFirstPersonHandHidden()) return;
		TerminalHandheldPose.Presentation pose = TerminalHandheldAnimator.presentation();
		// Vanilla's own equip counter, so switching to the terminal still raises it into frame and
		// switching away still drops it - without this the device would pop in fully placed.
		float stowed = 1.0F - Mth.lerp(partialTick, this.oMainHandHeight, this.mainHandHeight);
		float drop = stowed * -0.6F;

		poseStack.pushPose();
		// Vanilla's turning lag, reproduced because cancelling the method skipped it. xBob and yBob
		// are smoothed copies of the view angles, so the difference is how far the head has turned
		// ahead of them, and a tenth of it is how far the hands trail behind. Without it the device
		// is welded rigidly to the camera and every turn of the head looks wrong - which is exactly
		// what a player notices first, because the terminal is large and centred.
		float trailX = Mth.lerp(partialTick, player.xBobO, player.xBob);
		float trailY = Mth.lerp(partialTick, player.yBobO, player.yBob);
		poseStack.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTick) - trailX) * 0.1F));
		poseStack.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTick) - trailY) * 0.1F));
		if (!player.isInvisible()) {
			poseStack.pushPose();
			poseStack.translate(0.0F, pose.handY() + drop, pose.handZ());
			// The quarter turn is what puts the two arms on the left and right of the object rather
			// than both reaching in from the same side; vanilla's two-handed map does the same.
			// After it, the local Z axis is the camera's X, which is how each hand is pushed out to
			// its own side without unwinding the rotation.
			poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
			for (HumanoidArm arm : new HumanoidArm[]{HumanoidArm.RIGHT, HumanoidArm.LEFT}) {
				poseStack.pushPose();
				poseStack.translate(0.0F, 0.0F,
						(arm == HumanoidArm.RIGHT ? 1.0F : -1.0F) * pose.handSpread());
				this.renderMapHand(poseStack, collector, light, arm);
				poseStack.popPose();
			}
			poseStack.popPose();
		}
		poseStack.translate(0.0F, pose.y() + drop, pose.z());
		poseStack.mulPose(Axis.XP.rotationDegrees(pose.pitch()));
		poseStack.scale(pose.scale(), pose.scale(), pose.scale());
		// FIXED rather than a first-person context: it is the one transform that leaves the model
		// centred on the origin at unit scale, which is what lets every number above be read as an
		// absolute position in the frame instead of an offset from wherever an arm ended up.
		this.renderItem(player, this.mainHandItem, ItemDisplayContext.FIXED, poseStack, collector, light);
		poseStack.popPose();
		// The two lines vanilla ends this method with, and the reason cancelling it is not enough on
		// its own. renderItem only *submits* nodes; they are drawn when the dispatcher is flushed,
		// and the flush lives at the bottom of the method being cancelled. Without it the terminal
		// sat in the queue until the next frame's flush and was drawn against that frame's matrices
		// - one frame stale, pinned to the previous camera pose. Turning the head made it look like
		// the device had been left behind in the world.
		Minecraft client = Minecraft.getInstance();
		client.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
		client.renderBuffers().bufferSource().endBatch();
		callback.cancel();
	}

	/**
	 * The one-handed fallback: a slight tilt and the idle breath on top of vanilla's own pose.
	 *
	 * <p>Paired with the pop below on the same condition and the same arguments, so the matrix
	 * stack stays balanced. Only reached when the two-handed path stood down, and only for the
	 * holder's own first-person view.</p>
	 */
	@Inject(method = "renderItem", at = @At("HEAD"))
	private void thefourthfrequency$tiltCarriedTerminal(LivingEntity entity, ItemStack stack,
			ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector,
			int light, CallbackInfo callback) {
		if (!thefourthfrequency$isCarriedOneHanded(stack, context)) return;
		poseStack.pushPose();
		TerminalHandheldPose.Carried carried = TerminalHandheldAnimator.carried(
				context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
		poseStack.translate(0.0F, carried.lift(), 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(carried.pitch()));
		poseStack.mulPose(Axis.ZP.rotationDegrees(carried.roll()));
	}

	@Inject(method = "renderItem", at = @At("RETURN"))
	private void thefourthfrequency$restoreCarriedTerminal(LivingEntity entity, ItemStack stack,
			ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector,
			int light, CallbackInfo callback) {
		if (!thefourthfrequency$isCarriedOneHanded(stack, context)) return;
		poseStack.popPose();
	}

	private static boolean thefourthfrequency$isCarriedOneHanded(ItemStack stack,
			ItemDisplayContext context) {
		return stack.is(ModItems.OLD_TERMINAL)
				&& (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
				|| context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
	}
}
