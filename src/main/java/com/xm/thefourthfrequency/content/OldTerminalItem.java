package com.xm.thefourthfrequency.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class OldTerminalItem extends Item {
	public OldTerminalItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> textConsumer, TooltipFlag flag) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			String owner = data.copyTag().getStringOr(TerminalData.OWNER_NAME, "?");
			textConsumer.accept(Component.translatable("tooltip.thefourthfrequency.old_terminal.owner", owner)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		textConsumer.accept(Component.translatable("tooltip.thefourthfrequency.old_terminal.stock")
				.withStyle(ChatFormatting.GRAY));
	}
}
