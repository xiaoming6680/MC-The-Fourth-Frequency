package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Terminal-only completion notice. It carries no text and starts no visual anomaly. */
public record TerminalAnomalyLoggedS2C(UUID instanceId) implements CustomPacketPayload {
	public static final Type<TerminalAnomalyLoggedS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_anomaly_logged"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalAnomalyLoggedS2C> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeUUID(value.instanceId),
			buf -> new TerminalAnomalyLoggedS2C(buf.readUUID()));

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
