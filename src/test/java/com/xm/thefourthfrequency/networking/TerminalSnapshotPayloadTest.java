package com.xm.thefourthfrequency.networking;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerminalSnapshotPayloadTest {
	@Test
	void protocolV8RoundTripsTaskRewardAndCurrentFileReadState() {
		TerminalFilePayload file = new TerminalFilePayload("surface_shelter_record", true, true,
				10L, 20L, 11L, 21L, true, 30L, 40L, 0);
		TerminalSnapshotPayload snapshot = new TerminalSnapshotPayload(
				TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				0, 0, 50, 0, 0, 0, false, 0, 0, false, 0,
				0, 0, 0, 0, false, false,
				0, 0, 0, false, 100L, 0, List.of(), "none", 0,
				List.of(file), -1, "learn_terminal", 4, 4,
				0, true, "minecraft:bread", 6);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TerminalSnapshotPayload.CODEC.encode(buffer, snapshot);
		TerminalSnapshotPayload decoded = TerminalSnapshotPayload.CODEC.decode(buffer);
		assertEquals(8, decoded.protocolVersion());
		assertEquals(List.of(file), decoded.files());
		assertEquals("minecraft:bread", decoded.objectiveRewardItem());
		assertEquals(6, decoded.objectiveRewardCount());
		assertEquals(true, decoded.objectiveClaimable());
	}
}
