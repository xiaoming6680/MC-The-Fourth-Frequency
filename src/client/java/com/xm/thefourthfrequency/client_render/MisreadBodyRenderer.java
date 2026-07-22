package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public final class MisreadBodyRenderer extends MobRenderer<MisreadBodyEntity, MisreadBodyRenderState, MisreadBodyModel> {
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "misread_body"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "textures/entity/misread_body.png");

	public MisreadBodyRenderer(EntityRendererProvider.Context context) {
		super(context, new MisreadBodyModel(context.bakeLayer(MODEL_LAYER)), 1.1F);
	}

	@Override
	public MisreadBodyRenderState createRenderState() {
		return new MisreadBodyRenderState();
	}

	@Override
	public void extractRenderState(MisreadBodyEntity entity, MisreadBodyRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.phenotype = entity.phenotype();
		state.massStage = entity.massStage();
		state.adaptationAction = entity.adaptationAction();
		state.adaptationTicks = entity.adaptationTicks();
		state.endPhase = entity.endPhase().id();
	}

	@Override
	protected void scale(MisreadBodyRenderState state, PoseStack poseStack) {
		float scale = 1.0F + state.massStage * 0.24F;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTextureLocation(MisreadBodyRenderState state) {
		return TEXTURE;
	}
}
