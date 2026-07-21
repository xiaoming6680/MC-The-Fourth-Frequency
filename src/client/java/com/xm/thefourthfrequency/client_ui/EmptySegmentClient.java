package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.EmptySegmentPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

public final class EmptySegmentClient {
	private static String activeEvent = "none";
	private static int remainingTicks;
	private static int cameraEntityId = -1;
	private static CameraType previousCameraType;

	private EmptySegmentClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(EmptySegmentPayload.TYPE, (payload, context) -> {
			if (payload.eventId().equals("end")) {
				end(context.client());
				return;
			}
			activeEvent = payload.eventId();
			remainingTicks = payload.durationTicks();
			cameraEntityId = payload.cameraEntityId();
			if (activeEvent.equals("viewpoint_separation")) {
				previousCameraType = context.client().options.getCameraType();
				context.client().options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			if (activeEvent.equals("experience_gap")) {
				context.client().setScreen(new EmptySegmentOverlayScreen());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(EmptySegmentClient::tick);
	}

	private static void tick(Minecraft client) {
		if (remainingTicks <= 0) {
			return;
		}
		remainingTicks--;
		if (activeEvent.equals("viewpoint_separation") && cameraEntityId >= 0 && client.level != null) {
			var camera = client.level.getEntity(cameraEntityId);
			if (camera != null && client.getCameraEntity() != camera) {
				client.setCameraEntity(camera);
			}
		}
		if (remainingTicks == 0) {
			end(client);
		}
	}

	private static void end(Minecraft client) {
		if (client.player != null && client.getCameraEntity() != client.player) {
			client.setCameraEntity(client.player);
		}
		if (previousCameraType != null) {
			client.options.setCameraType(previousCameraType);
			previousCameraType = null;
		}
		if (client.screen instanceof EmptySegmentOverlayScreen) {
			client.setScreen(null);
		}
		activeEvent = "none";
		remainingTicks = 0;
		cameraEntityId = -1;
	}

	public static String activeEvent() {
		return activeEvent;
	}
}
