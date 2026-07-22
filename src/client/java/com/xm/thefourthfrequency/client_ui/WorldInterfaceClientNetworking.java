package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.AltarActionC2S;
import com.xm.thefourthfrequency.networking.AltarSnapshotS2C;
import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.PoemCompleteC2S;
import com.xm.thefourthfrequency.networking.PoemStartS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client receiver and the only C2S send surface for the world-interface encounter. */
public final class WorldInterfaceClientNetworking {
	private static boolean initialized;

	private WorldInterfaceClientNetworking() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(AltarSnapshotS2C.TYPE, (payload, context) ->
				context.client().execute(() -> acceptAltar(payload)));
		ClientPlayNetworking.registerGlobalReceiver(WorldInterfaceSnapshotS2C.TYPE, (payload, context) ->
				context.client().execute(() -> acceptEncounter(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(BossActionS2C.TYPE, (payload, context) ->
				context.client().execute(() -> WorldInterfaceClientState.accept(payload)));
		// This receiver only mutates synchronized state. Keeping it inline preserves wire order with
		// the vanilla WIN_GAME packet that immediately follows and constructs WinScreen.
		ClientPlayNetworking.registerGlobalReceiver(PoemStartS2C.TYPE, (payload, context) ->
				acceptPoem(payload));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				clearClientSession());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				clearClientSession());
	}

	public static boolean sendAltarAction(AltarSnapshotS2C snapshot,
			WorldInterfaceProtocol.AltarAction action) {
		if (!ClientPlayNetworking.canSend(AltarActionC2S.TYPE)) return false;
		ClientPlayNetworking.send(new AltarActionC2S(snapshot.encounterId(), snapshot.revision(), action));
		return true;
	}

	public static boolean sendPoemComplete(PoemStartS2C poem,
			WorldInterfaceProtocol.PoemCompletion completion) {
		if (!ClientPlayNetworking.canSend(PoemCompleteC2S.TYPE)) return false;
		ClientPlayNetworking.send(new PoemCompleteC2S(poem.encounterId(), poem.sequence(), completion));
		return true;
	}

	private static void acceptAltar(AltarSnapshotS2C payload) {
		if (!WorldInterfaceClientState.accept(payload)) return;
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof ResonanceAltarScreen altar && altar.matches(payload.encounterId())) {
			altar.update(payload);
		} else {
			client.setScreen(new ResonanceAltarScreen(payload));
		}
	}

	private static void acceptEncounter(Minecraft client, WorldInterfaceSnapshotS2C payload) {
		if (!WorldInterfaceClientState.accept(payload)) return;
		WorldInterfaceEndingClient.observeEncounter(client, payload);
		if (client.screen instanceof ResonanceAltarScreen altar
				&& payload.stage() != WorldInterfaceProtocol.Stage.WAITING_TERMINALS) {
			altar.closeFromServer();
		}
	}

	private static void acceptPoem(PoemStartS2C payload) {
		if (!WorldInterfaceClientState.accept(payload)) return;
		WorldInterfaceVanillaPoemClient.arm(payload);
	}

	private static void clearClientSession() {
		WorldInterfaceClientState.clearSession();
		WorldInterfaceVanillaPoemClient.clearPending();
	}
}
