package com.xm.thefourthfrequency.meta_api;

import com.xm.thefourthfrequency.client_ui.TerminalNoticeHud;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class InGameMetaPlatformAdapter implements MetaPlatformAdapter {
	@Override
	public Set<MetaCapability> capabilities() {
		return EnumSet.of(MetaCapability.IN_GAME_FALLBACK);
	}

	@Override
	public MetaExecution execute(MetaEvent event, MetaContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			TerminalNoticeHud.enqueue(Component.translatable(
					"message.thefourthfrequency.meta." + event.name().toLowerCase(java.util.Locale.ROOT)));
		}
		return new MetaExecution(event, false, true, List.of(), List.of());
	}

	@Override
	public void restore() {
		// The fallback owns no external state.
	}
}
