package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** At-most-once client acknowledgement for reading or explicitly skipping an ending poem. */
public record PoemCompleteC2S(UUID encounterId, long sequence, int completionId) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = WorldInterfaceProtocol.VERSION;
	public static final Type<PoemCompleteC2S> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_interface_poem_complete_v1"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PoemCompleteC2S> CODEC = StreamCodec.of(
			PoemCompleteC2S::write, PoemCompleteC2S::read);

	public PoemCompleteC2S {
		Objects.requireNonNull(encounterId, "encounterId");
		WorldInterfaceProtocol.requireSequence(sequence);
		WorldInterfaceProtocol.PoemCompletion.fromWireId(completionId);
	}

	public PoemCompleteC2S(UUID encounterId, long sequence,
			WorldInterfaceProtocol.PoemCompletion completion) {
		this(encounterId, sequence, Objects.requireNonNull(completion, "completion").wireId());
	}

	public WorldInterfaceProtocol.PoemCompletion completion() {
		return WorldInterfaceProtocol.PoemCompletion.fromWireId(completionId);
	}

	private static void write(RegistryFriendlyByteBuf buffer, PoemCompleteC2S value) {
		buffer.writeUUID(value.encounterId);
		buffer.writeVarLong(value.sequence);
		buffer.writeVarInt(value.completionId);
	}

	private static PoemCompleteC2S read(RegistryFriendlyByteBuf buffer) {
		return new PoemCompleteC2S(buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
