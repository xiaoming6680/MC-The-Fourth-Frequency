package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.TerminalHandheldAnimator;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Damps vanilla's walk bob while the terminal is the held item.
 *
 * <p>{@code bobView} runs inside {@code renderItemInHand}, so it moves the held item and the arms
 * and nothing else - the world behind them is already drawn. Scaling it therefore steadies the
 * terminal without touching the player's view of anything else, which is exactly the scope wanted:
 * a large centred display that swings at sprinting pace cannot be read, and it is meant to be
 * braced in both hands rather than swung on the end of one arm.
 *
 * <p>Redirecting the amplitude rather than replacing the method. Every one of vanilla's three
 * transforms - the sideways translate, the drop, the roll and the pitch - is multiplied by this one
 * interpolated figure, so scaling it at the source damps all four in proportion and leaves the
 * phase, the direction and the curve exactly as vanilla wrote them. Cancelling {@code bobView} and
 * reimplementing it at a lower amplitude would be four literals copied out of a private method,
 * which is four things to get wrong now and again on every update.
 *
 * <p>Costs nothing when no terminal is held: {@link TerminalHandheldAnimator#viewBobScale} returns
 * exactly 1 for every other item, and the multiply is then the identity.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererTerminalBobMixin {
	@Redirect(method = "bobView",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/entity/ClientAvatarState;getInterpolatedBob(F)F"))
	private float thefourthfrequency$dampenBobUnderTheTerminal(ClientAvatarState state,
			float partialTick) {
		return state.getInterpolatedBob(partialTick) * TerminalHandheldAnimator.viewBobScale();
	}
}
