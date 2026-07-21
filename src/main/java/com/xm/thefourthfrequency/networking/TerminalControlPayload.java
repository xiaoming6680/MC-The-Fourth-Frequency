package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TerminalControlPayload(int action, int value) implements CustomPacketPayload {
	public static final int MODE = 0;
	public static final int TUNE = 1;
	public static final int REFRESH = 2;
	public static final int CLOSE = 3;
	public static final int SELECT_FRAGMENT_TARGET = 4;
	public static final int SELECT_TOOL = 5;
	public static final int START_GUIDANCE = 6;
	public static final int STOP_GUIDANCE = 7;
	public static final int SELECT_RESOURCE = 8;
	public static final int REQUEST_RESCAN = 9;
	public static final int SET_HOME = 10;
	/** Reserved legacy action. The server intentionally rejects it. */
	@Deprecated
	public static final int SET_AUTO_TUNING = 11;
	public static final int READ_TRUTH_FILE = 12;
	public static final int MARK_RECORDS_READ = 13;
	public static final int SELECT_STRUCTURE_TARGET = 14;
	public static final int SELECT_NEAREST_UNSTABLE = 15;

	public static final Type<TerminalControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_control"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalControlPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.action);
				buf.writeVarInt(payload.value);
			},
			buf -> new TerminalControlPayload(buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
