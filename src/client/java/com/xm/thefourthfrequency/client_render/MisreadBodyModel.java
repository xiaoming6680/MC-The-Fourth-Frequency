package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

public final class MisreadBodyModel extends EntityModel<MisreadBodyRenderState> {
	private final ModelPart core;
	private final ModelPart operatorCrown;
	private final ModelPart builderLobe;
	private final ModelPart minerMaw;
	private final ModelPart leftTendril;
	private final ModelPart rightTendril;
	private final ModelPart rearTendril;

	public MisreadBodyModel(ModelPart root) {
		super(root);
		core = root.getChild("core");
		operatorCrown = core.getChild("operator_crown");
		builderLobe = core.getChild("builder_lobe");
		minerMaw = core.getChild("miner_maw");
		leftTendril = root.getChild("left_tendril");
		rightTendril = root.getChild("right_tendril");
		rearTendril = root.getChild("rear_tendril");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		var root = mesh.getRoot();
		var core = root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-9.0F, -8.0F, -8.0F, 18.0F, 14.0F, 16.0F), PartPose.offset(0.0F, 11.0F, 0.0F));
		core.addOrReplaceChild("operator_crown", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4.0F, -10.0F, -3.0F, 8.0F, 10.0F, 6.0F)
				.texOffs(30, 32).addBox(-1.0F, -18.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offset(0.0F, -6.0F, -2.0F));
		core.addOrReplaceChild("builder_lobe", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-10.0F, -5.0F, -4.0F, 20.0F, 9.0F, 8.0F), PartPose.offset(0.0F, -2.0F, 3.0F));
		core.addOrReplaceChild("miner_maw", CubeListBuilder.create().texOffs(0, 50)
				.addBox(-5.0F, 0.0F, -9.0F, 10.0F, 5.0F, 10.0F)
				.texOffs(42, 50).addBox(-2.0F, 2.0F, -14.0F, 4.0F, 4.0F, 7.0F), PartPose.offset(0.0F, 1.0F, -5.0F));
		root.addOrReplaceChild("left_tendril", CubeListBuilder.create().texOffs(72, 0)
				.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 24.0F, 4.0F), PartPose.offset(8.0F, 13.0F, 1.0F));
		root.addOrReplaceChild("right_tendril", CubeListBuilder.create().texOffs(72, 0).mirror()
				.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 24.0F, 4.0F), PartPose.offset(-8.0F, 13.0F, 1.0F));
		root.addOrReplaceChild("rear_tendril", CubeListBuilder.create().texOffs(90, 0)
				.addBox(-2.5F, 0.0F, -2.5F, 5.0F, 20.0F, 5.0F), PartPose.offset(0.0F, 14.0F, 7.0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(MisreadBodyRenderState state) {
		super.setupAnim(state);
		operatorCrown.visible = state.phenotype == 0 || state.phenotype == 3;
		builderLobe.visible = state.phenotype == 1 || state.phenotype == 3;
		minerMaw.visible = state.phenotype == 2 || state.phenotype == 3;
		operatorCrown.xScale = operatorCrown.yScale = operatorCrown.zScale = 1.0F;
		builderLobe.xScale = builderLobe.yScale = builderLobe.zScale = 1.0F;
		minerMaw.xScale = minerMaw.yScale = minerMaw.zScale = 1.0F;
		float pulse = Mth.sin(state.ageInTicks * 0.08F);
		float phasePulse = 1.0F + Math.max(0, state.endPhase - 1) * 0.20F;
		core.yScale = 1.0F + pulse * 0.025F * phasePulse;
		core.xScale = 1.0F - pulse * 0.012F * phasePulse;
		core.zScale = core.xScale;
		leftTendril.xRot = 0.08F + Mth.sin(state.ageInTicks * 0.055F) * 0.12F;
		rightTendril.xRot = 0.08F + Mth.sin(state.ageInTicks * 0.055F + Mth.PI) * 0.12F;
		rearTendril.xRot = -0.18F + Mth.sin(state.ageInTicks * 0.041F) * 0.09F;
		minerMaw.xRot = 0.10F + (Mth.sin(state.ageInTicks * 0.13F) + 1.0F) * 0.12F;
		if (state.adaptationTicks > 0) {
			float warning = 1.0F + (Mth.sin(state.ageInTicks * 0.9F) + 1.0F) * 0.08F;
			switch (state.adaptationAction) {
				case 1 -> {
					operatorCrown.xScale = operatorCrown.zScale = warning;
					operatorCrown.zRot = Mth.sin(state.ageInTicks * 0.65F) * 0.16F;
				}
				case 2 -> builderLobe.yScale = warning;
				case 3 -> minerMaw.xRot += 0.48F;
				case 4 -> {
					core.xScale *= warning;
					core.zScale *= warning;
					leftTendril.xRot -= 0.35F;
					rightTendril.xRot -= 0.35F;
					rearTendril.xRot -= 0.25F;
				}
				case 5 -> {
					operatorCrown.xScale = operatorCrown.zScale = warning;
					builderLobe.xScale = builderLobe.yScale = warning;
					minerMaw.xScale = minerMaw.yScale = warning;
				}
				default -> { }
			}
		} else {
			operatorCrown.zRot = 0.0F;
		}
	}
}
