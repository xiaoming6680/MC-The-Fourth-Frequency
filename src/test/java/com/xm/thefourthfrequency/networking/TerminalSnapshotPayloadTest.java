package com.xm.thefourthfrequency.networking;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerminalSnapshotPayloadTest {
	@Test
	void protocolV6RoundTripsHiddenFileReadStateAndTime() {
		TerminalFilePayload file = new TerminalFilePayload("surface_shelter_record", true, true,
				10L, 20L, 11L, 21L, true, 30L, 40L, 0);
		TerminalSnapshotPayload snapshot = new TerminalSnapshotPayload(
				TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				0, 0, 50, 0, 0, 0, false, 0, 0, false, 0,
				0, 0, 0, 0, -1, -1, -1, -1, false, false,
				0, 0, 0, 0, 0, false, 100L, 0, List.of(), "none", 0,
				List.of(file), -1, "none", 0, 0);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TerminalSnapshotPayload.CODEC.encode(buffer, snapshot);
		TerminalSnapshotPayload decoded = TerminalSnapshotPayload.CODEC.decode(buffer);
		assertEquals(6, decoded.protocolVersion());
		assertEquals(List.of(file), decoded.files());
	}
}
