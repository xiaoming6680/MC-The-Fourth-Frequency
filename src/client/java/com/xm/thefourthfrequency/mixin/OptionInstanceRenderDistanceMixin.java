package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AtmosphericFogProfile;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceRenderDistanceMixin {
	@ModifyVariable(method = "set", at = @At("HEAD"), argsOnly = true)
	private Object thefourthfrequency$rejectRenderDistanceChanges(Object requestedValue) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null && (Object) this == client.options.renderDistance()) {
			return AtmosphericFogProfile.FIXED_RENDER_DISTANCE_CHUNKS;
		}
		return requestedValue;
	}

	@Inject(method = "createButton(Lnet/minecraft/client/Options;IIILjava/util/function/Consumer;)"
			+ "Lnet/minecraft/client/gui/components/AbstractWidget;", at = @At("RETURN"))
	private void thefourthfrequency$disableRenderDistanceControl(Options options, int x, int y, int width,
			Consumer<?> onValueChanged, CallbackInfoReturnable<AbstractWidget> callback) {
		if ((Object) this != options.renderDistance()) return;
		AbstractWidget widget = callback.getReturnValue();
		widget.active = false;
		widget.setMessage(Component.translatable("options.thefourthfrequency.render_distance_locked",
				AtmosphericFogProfile.FIXED_RENDER_DISTANCE_CHUNKS));
		widget.setTooltip(Tooltip.create(Component.translatable(
				"options.thefourthfrequency.render_distance_locked.tooltip")));
	}
}
