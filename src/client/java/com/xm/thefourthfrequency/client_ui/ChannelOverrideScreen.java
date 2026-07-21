package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.terminal.ChannelOverrideScript;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Native Minecraft chat UI whose input is scripted locally and cannot reach the connection. */
public final class ChannelOverrideScreen extends ChatScreen {
	private int age;

	public ChannelOverrideScreen() {
		super("", false);
	}

	@Override
	protected void init() {
		super.init();
		input.setEditable(false);
		input.setTextColorUneditable(0xFFFF3038);
		input.setValue(scriptedText(age));
	}

	@Override
	public void tick() {
		age++;
		if (input != null) input.setValue(scriptedText(age));
	}

	public static String scriptedText(int tick) {
		return ChannelOverrideScript.textAt(tick);
	}

	@Override public boolean keyPressed(KeyEvent event) { return true; }
	@Override public boolean charTyped(CharacterEvent event) { return true; }
	@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) { return true; }
	@Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return true; }
	@Override public void handleChatInput(String message, boolean addToRecentChat) { }
	@Override public boolean shouldCloseOnEsc() { return false; }
	@Override public void onClose() { }
	@Override public boolean isPauseScreen() { return false; }
}
