package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.facility.FacilityService;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class M4Networking {
	private M4Networking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(ArchivePasswordPayload.TYPE, ArchivePasswordPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ArchivePasswordResultPayload.TYPE, ArchivePasswordResultPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ArchivePasswordPayload.TYPE,
				(payload, context) -> {
					int result = FacilityService.tryUnlockArchiveResult(context.player(), payload.code());
					ServerPlayNetworking.send(context.player(), new ArchivePasswordResultPayload(result));
					TerminalRuntimeService.refresh(context.player());
				});
	}
}
