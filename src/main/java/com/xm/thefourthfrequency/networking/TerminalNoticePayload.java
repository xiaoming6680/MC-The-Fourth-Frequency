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
	public static final Type<TerminalNoticePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_notice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalNoticePayload> CODEC = StreamCodec.of(
			TerminalNoticePayload::write, TerminalNoticePayload::read);

	private static void write(RegistryFriendlyByteBuf buf, TerminalNoticePayload value) {
		ComponentSerialization.STREAM_CODEC.encode(buf, value.message);
		buf.writeVarInt(Math.clamp(value.tone, TONE_NONE, TONE_TASK_COMPLETE));
	}

	private static TerminalNoticePayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalNoticePayload(ComponentSerialization.STREAM_CODEC.decode(buf),
				Math.clamp(buf.readVarInt(), TONE_NONE, TONE_TASK_COMPLETE));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
