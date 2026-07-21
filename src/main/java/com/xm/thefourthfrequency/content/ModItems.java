package com.xm.thefourthfrequency.content;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	private static final Identifier OLD_TERMINAL_ID = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "old_terminal");
	private static final ResourceKey<Item> OLD_TERMINAL_KEY = ResourceKey.create(Registries.ITEM, OLD_TERMINAL_ID);
	private static final ResourceKey<Item> TERMINATION_SPIKE_KEY = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "termination_spike"));

	public static final Item OLD_TERMINAL = Registry.register(
			BuiltInRegistries.ITEM,
			OLD_TERMINAL_KEY,
			new OldTerminalItem(new Item.Properties().setId(OLD_TERMINAL_KEY).stacksTo(1))
	);
	public static final Item TERMINATION_SPIKE = Registry.register(
			BuiltInRegistries.ITEM,
			TERMINATION_SPIKE_KEY,
			new Item(new Item.Properties().setId(TERMINATION_SPIKE_KEY).stacksTo(1))
	);

	private ModItems() {
	}

	public static void initialize() {
		TheFourthFrequency.LOGGER.info("Registered the old information terminal and the archive termination spike");
	}
}
