package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.terminal.MenuErosionRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MenuErosionStageS2C(int stage) implements CustomPacketPayload {
	public static final Type<MenuErosionStageS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "menu_erosion_stage"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MenuErosionStageS2C> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeVarInt(value.stage), buf -> new MenuErosionStageS2C(buf.readVarInt()));
	public static int stageFor(int effectiveCeiling, boolean restored) {
		return MenuErosionRules.stageFor(effectiveCeiling, restored);
	}
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
