package com.xm.thefourthfrequency.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ArchivePasswordPayload(String code) implements CustomPacketPayload {
	public static final Type<ArchivePasswordPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("thefourthfrequency", "archive_password"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ArchivePasswordPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.stringUtf8(4), ArchivePasswordPayload::code, ArchivePasswordPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
