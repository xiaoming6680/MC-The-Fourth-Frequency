package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ArchivePasswordResultPayload(int result) implements CustomPacketPayload {
	public static final int SUCCESS = 0;
	public static final int INVALID = 1;
	public static final int UNAVAILABLE = 2;
	public static final int INCOMPLETE = 3;
	public static final int WRONG = 4;
	public static final Type<ArchivePasswordResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "archive_password_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ArchivePasswordResultPayload> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeVarInt(value.result),
			buf -> new ArchivePasswordResultPayload(buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
