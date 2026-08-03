package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A bounded bottom-of-screen notice with an optional attention tone. */
public record TerminalNoticePayload(Component message, int tone) implements CustomPacketPayload {
	public static final int TONE_NONE = 0;
	public static final int TONE_UNREAD = 1;
	public static final int TONE_TASK_COMPLETE = 2;
	public static final int TONE_PURSUIT_WARNING = 3;
	/** A refused action: distinct presentation so it is never read as another progress line. */
	public static final int TONE_DENIED = 4;
	/**
	 * The finale's own narration - phases, resolution, the fight starting.
	 *
	 * <p>These used to go to the chat log, where the encounter's most consequential lines sat in
	 * the same undifferentiated stack as death messages and whatever anyone happened to type. The
	 * three tones below give the fight a legible voice: what the encounter is doing, what the
	 * anchors are doing, and what the dragon is saying are each their own colour.</p>
	 */
	public static final int TONE_ENCOUNTER = 5;
	/** Anchor pressure: the one boss-fight channel that is about something the players can act on. */
	public static final int TONE_ANCHOR = 6;
	/** The dragon speaking, which is nobody else's voice in the whole mod. */
	public static final int TONE_DRAGON = 7;
	public static final Type<TerminalNoticePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_notice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalNoticePayload> CODEC = StreamCodec.of(
			TerminalNoticePayload::write, TerminalNoticePayload::read);

	private static void write(RegistryFriendlyByteBuf buf, TerminalNoticePayload value) {
		ComponentSerialization.STREAM_CODEC.encode(buf, value.message);
		buf.writeVarInt(Math.clamp(value.tone, TONE_NONE, TONE_DRAGON));
	}

	private static TerminalNoticePayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalNoticePayload(ComponentSerialization.STREAM_CODEC.decode(buf),
				Math.clamp(buf.readVarInt(), TONE_NONE, TONE_DRAGON));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
