package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.client_render.ReworkBodyModel;
import com.xm.thefourthfrequency.client_render.ReworkBodyRenderer;
import com.xm.thefourthfrequency.client_render.WatcherRenderer;
import com.xm.thefourthfrequency.client_render.WatcherModel;
import com.xm.thefourthfrequency.client_render.WorldInterfaceModel;
import com.xm.thefourthfrequency.client_render.WorldInterfaceEnergyOrbRenderer;
import com.xm.thefourthfrequency.client_render.WorldInterfaceRenderer;
import com.xm.thefourthfrequency.meta_api.MetaController;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.xm.thefourthfrequency.networking.TerminalOpenPayload;
import net.minecraft.client.renderer.entity.NoopRenderer;

public final class TheFourthFrequencyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		WorldInterfaceEndingClient.initialize();
		AlphaLoadSessionController.initialize();
		DimensionViewDistanceController.initialize();
		EntityModelLayerRegistry.registerModelLayer(ReworkBodyRenderer.STAGE_1_LAYER,
				ReworkBodyModel::createStage1Layer);
		EntityModelLayerRegistry.registerModelLayer(ReworkBodyRenderer.STAGE_2_LAYER,
				ReworkBodyModel::createStage2Layer);
		EntityModelLayerRegistry.registerModelLayer(ReworkBodyRenderer.STAGE_3_LAYER,
				ReworkBodyModel::createStage3Layer);
		EntityModelLayerRegistry.registerModelLayer(ReworkBodyRenderer.STAGE_4_LAYER,
				ReworkBodyModel::createStage4Layer);
		EntityModelLayerRegistry.registerModelLayer(ReworkBodyRenderer.STAGE_5_LAYER,
				ReworkBodyModel::createStage5Layer);
		EntityRendererRegistry.register(ModEntities.REWORK_BODY, ReworkBodyRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(WatcherRenderer.MODEL_LAYER, WatcherModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.WATCHER, WatcherRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(WorldInterfaceRenderer.MODEL_LAYER,
				WorldInterfaceModel::createLayer);
		EntityRendererRegistry.register(ModEntities.WORLD_INTERFACE, WorldInterfaceRenderer::new);
		EntityRendererRegistry.register(ModEntities.WORLD_INTERFACE_PART, NoopRenderer::new);
		EntityRendererRegistry.register(ModEntities.WORLD_INTERFACE_ENERGY_ORB,
				WorldInterfaceEnergyOrbRenderer::new);
		EmptySegmentClient.initialize();
		PrivateAnomalyClient.initialize();
		FirstRunNoticeController.initialize();
		MetaController.initialize();
		TerminalClientNetworking.initialize();
		TerminalNoticeHud.initialize();
		WorldInterfaceClientNetworking.initialize();
		WorldInterfacePresentationController.initialize();
		WorldInterfaceHud.initialize();
		AmbientAnomalyClient.initialize();
		AnomalyPresentationController.initialize();
		MenuErosionState.initialize();
		DebugPanelClient.initialize();
		WorldDecayClient.initialize();
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() && player.getItemInHand(hand).is(ModItems.OLD_TERMINAL)
					&& TerminalData.belongsTo(player.getItemInHand(hand), player.getUUID())) {
				if (ClientPlayNetworking.canSend(TerminalOpenPayload.TYPE)) {
					ClientPlayNetworking.send(new TerminalOpenPayload(hand == net.minecraft.world.InteractionHand.MAIN_HAND ? 0 : 1));
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		TheFourthFrequency.LOGGER.info("The Fourth Frequency client presentation layer is ready");
	}
}
