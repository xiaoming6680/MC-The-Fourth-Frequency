package com.xm.thefourthfrequency.networking;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerminalSnapshotPayloadTest {
	@Test
	void protocolV13RoundTripsInitialPageUnreadFilesTaskRewardOnboardingAttentionAndCurrentFileReadState() {
		TerminalFilePayload file = new TerminalFilePayload("surface_shelter_record", true, true,
				10L, 20L, 11L, 21L, true, 30L, 40L, 0);
		TerminalSnapshotPayload snapshot = new TerminalSnapshotPayload(
				TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				0, 0, 2, 50, 0, 0, 0, false, 0, 0, false, 0,
				0, false, false, 100L, 0, 3, List.of(), "none", 0,
				List.of(file), -1, "learn_terminal", 4, 4,
				0, true, "minecraft:bread", 6, true, true);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TerminalSnapshotPayload.CODEC.encode(buffer, snapshot);
		TerminalSnapshotPayload decoded = TerminalSnapshotPayload.CODEC.decode(buffer);
		assertEquals(13, decoded.protocolVersion());
		assertEquals(2, decoded.initialPage());
		assertEquals(3, decoded.unreadFileCount());
		assertEquals(List.of(file), decoded.files());
		assertEquals("minecraft:bread", decoded.objectiveRewardItem());
		assertEquals(6, decoded.objectiveRewardCount());
		assertEquals(true, decoded.objectiveClaimable());
		assertEquals(true, decoded.onboardingRequired());
		// The unread lamp is the last field on the wire. Reading is positional, so a decode that
		// lands on the wrong boolean here means every varint before it shifted too.
		assertEquals(true, decoded.attentionActive());
	}

	@Test
	void trailingFlagsSurviveBeingFalse() {
		TerminalSnapshotPayload snapshot = new TerminalSnapshotPayload(
				TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				0, 0, 0, 50, 0, 0, 0, false, 0, 0, false, 0,
				0, false, false, 100L, 0, 0, List.of(), "none", 0,
				List.of(), -1, "mine_logs", 3, 12,
				1, false, "minecraft:stone_axe", 1, false, false);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TerminalSnapshotPayload.CODEC.encode(buffer, snapshot);
		TerminalSnapshotPayload decoded = TerminalSnapshotPayload.CODEC.decode(buffer);
		assertEquals(false, decoded.onboardingRequired());
		assertEquals(false, decoded.attentionActive());
		assertEquals("mine_logs", decoded.objectiveId());
		assertEquals(1, decoded.objectiveIndex());
	}

	/**
	 * The two trailing booleans are independent on the wire.
	 *
	 * <p>Both are read positionally from adjacent bytes, so a codec that dropped or duplicated one
	 * would still pass every test above as long as the two happened to agree. Setting them opposite
	 * ways is what makes that failure visible.</p>
	 */
	@Test
	void unreadLampAndWalkthroughFlagsDoNotShadowEachOther() {
		for (boolean onboarding : new boolean[]{true, false}) {
			TerminalSnapshotPayload snapshot = new TerminalSnapshotPayload(
					TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION,
					0, 0, 0, 50, 0, 0, 0, false, 0, 0, false, 0,
					0, false, false, 100L, 0, 0, List.of(), "none", 0,
					List.of(), -1, "mine_logs", 3, 12,
					1, false, "minecraft:stone_axe", 1, onboarding, !onboarding);
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			TerminalSnapshotPayload.CODEC.encode(buffer, snapshot);
			TerminalSnapshotPayload decoded = TerminalSnapshotPayload.CODEC.decode(buffer);
			assertEquals(onboarding, decoded.onboardingRequired());
			assertEquals(!onboarding, decoded.attentionActive());
		}
	}
}
