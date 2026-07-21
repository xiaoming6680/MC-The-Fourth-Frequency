package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Native 2.9-block Watcher model. The eye and iris are geometry, never camera-facing quads. */
public final class WatcherModel extends EntityModel<WatcherRenderState> {
	private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
	private static final float FULL_TURN = (float) (Math.PI * 2.0);
	private final ModelPart torso;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart leftArm;
	private final ModelPart leftForearm;
	private final ModelPart leftHand;
	private final ModelPart rightArm;
	private final ModelPart rightForearm;
	private final ModelPart rightHand;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;
	private final ModelPart eye;
	private final ModelPart iris;

	public WatcherModel(ModelPart root) {
		super(root);
		torso = root.getChild("torso");
		neck = torso.getChild("neck");
		head = neck.getChild("head");
		eye = head.getChild("eye");
		iris = eye.getChild("iris");
		leftArm = torso.getChild("left_arm");
		leftForearm = leftArm.getChild("forearm");
		leftHand = leftForearm.getChild("hand");
		rightArm = torso.getChild("right_arm");
		rightForearm = rightArm.getChild("forearm");
		rightHand = rightForearm.getChild("hand");
		leftLeg = root.getChild("left_leg");
		rightLeg = root.getChild("right_leg");
	}

	/**
	 * Uses a 128-unit virtual UV canvas sampled by the 256px runtime textures at 2x density.
	 * The matching exported guide is docs/art/watcher/watcher_uv_template.png.
	 */
	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float pelvisY = 4.0F;

		root.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(22, 0)
				.addBox(-2.3F, -2.0F, -1.6F, 4.6F, 4.0F, 3.2F),
				PartPose.offsetAndRotation(0.0F, pelvisY, 0.0F, 0.02F, 0.0F, 0.0F));
		PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3.0F, -14.0F, -1.7F, 6.0F, 14.0F, 3.4F),
				PartPose.offsetAndRotation(0.0F, pelvisY, 0.0F, 0.10F, 0.0F, 0.0F));
		torso.addOrReplaceChild("chest_fascia", CubeListBuilder.create().texOffs(48, 66)
				.addBox(-2.35F, -11.8F, -2.02F, 4.7F, 6.6F, 0.34F), PartPose.ZERO);
		torso.addOrReplaceChild("spine", spineGeometry(), PartPose.ZERO);
		torso.addOrReplaceChild("left_scapula", CubeListBuilder.create().texOffs(0, 66)
				.addBox(-2.7F, -12.2F, 1.62F, 2.4F, 6.4F, 0.52F),
				PartPose.rotation(-0.04F, -0.05F, -0.16F));
		torso.addOrReplaceChild("right_scapula", CubeListBuilder.create().texOffs(10, 66)
				.addBox(0.3F, -12.2F, 1.62F, 2.4F, 6.4F, 0.52F),
				PartPose.rotation(-0.04F, 0.05F, 0.16F));

		PartDefinition neck = torso.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(42, 0)
				.addBox(-1.1F, -5.0F, -1.1F, 2.2F, 5.0F, 2.2F),
				PartPose.offsetAndRotation(0.0F, -14.0F, -0.25F, -0.045F, 0.0F, 0.0F));
		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(56, 0)
				.addBox(-3.25F, -7.4F, -1.8F, 6.5F, 7.4F, 3.8F),
				PartPose.offsetAndRotation(0.0F, -5.0F, -0.20F, 0.045F, 0.0F, 0.0F));
		PartDefinition eye = head.addOrReplaceChild("eye", CubeListBuilder.create().texOffs(80, 0)
				.addBox(-2.60F, -1.90F, -1.02F, 5.20F, 3.80F, 1.55F)
				.texOffs(98, 0).addBox(-2.20F, -2.30F, -0.88F, 4.40F, 4.60F, 1.28F),
				PartPose.offset(0.0F, -3.65F, -2.0F));
		PartDefinition iris = eye.addOrReplaceChild("iris", CubeListBuilder.create().texOffs(112, 0)
				.addBox(-1.75F, -1.75F, -0.20F, 3.50F, 3.50F, 0.34F),
				PartPose.offset(0.0F, 0.0F, -1.16F));
		iris.addOrReplaceChild("pupil", CubeListBuilder.create().texOffs(122, 0)
				.addBox(-0.78F, -1.02F, -0.18F, 1.56F, 2.04F, 0.28F),
				PartPose.offset(0.0F, 0.0F, -0.30F));

		addArm(torso, true);
		addArm(torso, false);
		addLeg(root, true, pelvisY);
		addLeg(root, false, pelvisY);
		return LayerDefinition.create(mesh, 128, 128);
	}

	private static CubeListBuilder spineGeometry() {
		CubeListBuilder spine = CubeListBuilder.create();
		for (int index = 0; index < 9; index++) {
			float width = index == 2 || index == 3 ? 1.20F : 0.92F;
			spine.texOffs(22 + (index % 3) * 5, 66 + (index / 3) * 4)
					.addBox(-width * 0.5F, -13.1F + index * 1.42F, 1.62F,
							width, 0.86F, 0.82F);
		}
		return spine;
	}

	private static void addArm(PartDefinition torso, boolean left) {
		float side = left ? 1.0F : -1.0F;
		int upperU = left ? 0 : 44;
		int foreU = left ? 9 : 53;
		int handU = left ? 18 : 62;
		int fingerU = left ? 28 : 72;
		PartDefinition upper = torso.addOrReplaceChild(left ? "left_arm" : "right_arm",
				CubeListBuilder.create().texOffs(upperU, 24).mirror(!left)
						.addBox(-0.75F, 0.0F, -0.80F, 1.50F, 12.5F, 1.60F),
				PartPose.offsetAndRotation(side * 3.15F, -12.3F, -0.15F,
						0.055F, 0.0F, -side * 0.055F));
		PartDefinition forearm = upper.addOrReplaceChild("forearm",
				CubeListBuilder.create().texOffs(foreU, 24).mirror(!left)
						.addBox(-0.63F, 0.0F, -0.68F, 1.26F, 13.0F, 1.36F),
				PartPose.offsetAndRotation(0.0F, 12.5F, 0.0F, -0.045F, 0.0F, side * 0.025F));
		forearm.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(handU, 24).mirror(!left)
				.addBox(-0.72F, 0.0F, -0.76F, 1.44F, 3.55F, 1.52F)
				.texOffs(fingerU, 24).addBox(-0.66F, 2.65F, -0.62F, 0.28F, 1.75F, 0.34F)
				.texOffs(fingerU + 3, 24).addBox(-0.14F, 2.65F, -0.68F, 0.28F, 1.95F, 0.34F)
				.texOffs(fingerU + 6, 24).addBox(0.38F, 2.65F, -0.62F, 0.28F, 1.65F, 0.34F),
				PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.04F, 0.0F, -side * 0.018F));
	}

	private static void addLeg(PartDefinition root, boolean left, float pelvisY) {
		float side = left ? 1.0F : -1.0F;
		int upperU = left ? 0 : 32;
		int lowerU = left ? 9 : 41;
		int footU = left ? 18 : 50;
		PartDefinition upper = root.addOrReplaceChild(left ? "left_leg" : "right_leg",
				CubeListBuilder.create().texOffs(upperU, 46).mirror(!left)
						.addBox(-0.78F, 0.0F, -0.84F, 1.56F, 9.5F, 1.68F),
				PartPose.offsetAndRotation(side * 1.28F, pelvisY, 0.10F, 0.025F, 0.0F, -side * 0.02F));
		PartDefinition lower = upper.addOrReplaceChild("lower_leg",
				CubeListBuilder.create().texOffs(lowerU, 46).mirror(!left)
						.addBox(-0.67F, 0.0F, -0.72F, 1.34F, 10.5F, 1.44F),
				PartPose.offset(0.0F, 9.5F, 0.0F));
		lower.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(footU, 46).mirror(!left)
				.addBox(-0.82F, -0.80F, -2.05F, 1.64F, 0.80F, 2.75F),
				PartPose.offset(0.0F, 10.5F, 0.15F));
	}

	@Override
	public void setupAnim(WatcherRenderState state) {
		super.setupAnim(state);
		float slowWave = Mth.sin(state.ageInTicks * 0.035F);
		float counterWave = Mth.sin(state.ageInTicks * 0.021F + 1.7F);
		torso.xRot += 0.018F + slowWave * 0.012F;
		torso.zRot += slowWave * 0.018F + counterWave * 0.009F;
		neck.xRot -= slowWave * 0.010F;

		float twitch = Mth.sin(state.ageInTicks * 0.071F + 0.9F) * 0.017F
				+ Mth.sin(state.ageInTicks * 0.019F) * 0.011F;
		head.yRot += Mth.clamp(state.yRot * DEG_TO_RAD, -0.55F, 0.55F) + twitch;
		head.xRot += Mth.clamp(state.xRot * DEG_TO_RAD, -0.42F, 0.42F)
				+ Mth.sin(state.ageInTicks * 0.053F + 2.1F) * 0.012F;
		head.zRot += twitch * 0.62F;

		leftArm.xRot += 0.035F + slowWave * 0.018F;
		rightArm.xRot += 0.035F - slowWave * 0.018F;
		leftForearm.xRot += 0.045F + counterWave * 0.012F;
		rightForearm.xRot += 0.045F - counterWave * 0.012F;
		leftHand.zRot += slowWave * 0.010F;
		rightHand.zRot -= slowWave * 0.010F;
		leftLeg.xRot += counterWave * 0.004F;
		rightLeg.xRot -= counterWave * 0.004F;

		float irisPhase = state.ageInTicks * FULL_TURN / 120.0F;
		float irisScale = 1.0F + Mth.sin(irisPhase) * 0.03F;
		iris.xScale = irisScale;
		iris.yScale = irisScale;
		iris.zScale = 1.0F;
		eye.xScale = eye.yScale = eye.zScale = 1.0F;
	}
}
