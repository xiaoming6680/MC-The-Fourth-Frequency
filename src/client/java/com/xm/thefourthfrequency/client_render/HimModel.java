package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * The default player silhouette, standing perfectly still.
 *
 * <p>Built on vanilla's own humanoid mesh rather than a hand-authored one, because the entire point
 * of the figure is that it is Steve-shaped. Anything with its own proportions reads as a custom mob
 * inside the fifth of a second it is on screen, and the sighting turns into an identification.
 */
public final class HimModel extends HumanoidModel<HimRenderState> {
	public HimModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
	}

	@Override
	public void setupAnim(HimRenderState state) {
		super.setupAnim(state);
		// Every limb pinned. The humanoid model idles with a breathing sway and swings its arms off
		// the walk animation; a figure that is subtly alive is a figure the eye keeps tracking, and
		// this one has to be over before it is resolved. Total stillness is what makes it read as a
		// still frame rather than as something standing there.
		head.xRot = 0.0F;
		head.yRot = 0.0F;
		head.zRot = 0.0F;
		hat.xRot = 0.0F;
		hat.yRot = 0.0F;
		hat.zRot = 0.0F;
		rightArm.xRot = 0.0F;
		rightArm.yRot = 0.0F;
		rightArm.zRot = 0.0F;
		leftArm.xRot = 0.0F;
		leftArm.yRot = 0.0F;
		leftArm.zRot = 0.0F;
		rightLeg.xRot = 0.0F;
		rightLeg.yRot = 0.0F;
		rightLeg.zRot = 0.0F;
		leftLeg.xRot = 0.0F;
		leftLeg.yRot = 0.0F;
		leftLeg.zRot = 0.0F;
	}
}
