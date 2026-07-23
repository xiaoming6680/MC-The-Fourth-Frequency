package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TerminalNavigationPayload(
		int protocolVersion,
		int targetKind,
		boolean located,
		boolean navigable,
		int targetDx,
		int targetDz,
		int targetY,
		float playerYaw
) implements CustomPacketPayload {
	public static final int CURRENT_PROTOCOL_VERSION = 6;
	public static final int NONE = 0;
	public static final int IRON = 1;
	public static final int DIAMOND = 3;
	public static final int UNSTABLE_SIGNAL = 4;
	public static final int HOME = 5;
	public static final int PORTAL = 6;
	public static final int STRONGHOLD = 7;
	public static final int VILLAGE = 8;
	public static final int RUINED_PORTAL = 9;
	public static final int MINESHAFT = 10;
	public static final int TRIAL_CHAMBERS = 11;
	public static final int FORTRESS = 12;
	public static final int BASTION = 13;
	public static final int COAL = 14;
	public static final int GOLD = 15;
	public static final Type<TerminalNavigationPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_navigation"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalNavigationPayload> CODEC = StreamCodec.of(
			TerminalNavigationPayload::write, TerminalNavigationPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, TerminalNavigationPayload value) {
		buf.writeVarInt(value.protocolVersion);
		buf.writeVarInt(value.targetKind);
		buf.writeBoolean(value.located);
		buf.writeBoolean(value.navigable);
		buf.writeVarInt(value.targetDx);
		buf.writeVarInt(value.targetDz);
		buf.writeVarInt(value.targetY);
		buf.writeFloat(value.playerYaw);
	}

	private static TerminalNavigationPayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalNavigationPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static boolean isMineral(int kind) {
		return kind == IRON || kind == COAL || kind == GOLD || kind == DIAMOND;
	}
}
