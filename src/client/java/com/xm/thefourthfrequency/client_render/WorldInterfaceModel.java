package com.xm.thefourthfrequency.client_render;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Stable animation bones hosting three visually independent, statically baked form hierarchies. */
public final class WorldInterfaceModel extends EntityModel<WorldInterfaceRenderState> {
	public static final int FORM_COUNT = 3;
	public static final int ANIMATED_BONE_COUNT = 15;
	public static final int MAX_VISIBLE_STATIC_PARTS = 208;
	private static final int[] STATIC_PART_BUDGET = {64, 128, MAX_VISIBLE_STATIC_PARTS};

	private final ModelPart body;
	private final ModelPart eye;
	private final ModelPart ring;
	private final ModelPart jaw;
	private final ModelPart weapon;
	private final ModelPart[] formCores = new ModelPart[FORM_COUNT];
	private final ModelPart[] formEyes = new ModelPart[FORM_COUNT];
	private final ModelPart[] formRings = new ModelPart[FORM_COUNT];
	private final ModelPart[] formJaws = new ModelPart[FORM_COUNT];
	private final ModelPart[] tendrils = new ModelPart[10];
	private final ModelPart[][] tendrilForms = new ModelPart[10][FORM_COUNT];
	private final KeyframeAnimation[] idleAnimations;
	private final KeyframeAnimation[][] actionAnimations =
			new KeyframeAnimation[WorldInterfaceAnimations.PROTOCOL_ACTION_COUNT + 1][];

	public WorldInterfaceModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		eye = body.getChild("eye");
		ring = body.getChild("ring");
		jaw = body.getChild("jaw");
		weapon = body.getChild("weapon");
		for (int form = 0; form < FORM_COUNT; form++) {
			String suffix = Integer.toString(form + 1);
			formCores[form] = body.getChild("form_" + suffix + "_core");
			formEyes[form] = eye.getChild("form_" + suffix + "_eye");
			formRings[form] = ring.getChild("form_" + suffix + "_ring");
			formJaws[form] = jaw.getChild("form_" + suffix + "_jaw");
		}
		for (int index = 0; index < tendrils.length; index++) {
			tendrils[index] = body.getChild("tendril_" + index);
			for (int form = 0; form < FORM_COUNT; form++) {
				tendrilForms[index][form] = tendrils[index].getChild("form_" + (form + 1));
			}
		}
		AnimationDefinition[] idleClips = WorldInterfaceAnimations.idleClips();
		idleAnimations = new KeyframeAnimation[idleClips.length];
		for (int index = 0; index < idleClips.length; index++) idleAnimations[index] = idleClips[index].bake(root);
		for (int actionId = 1; actionId < actionAnimations.length; actionId++) {
			AnimationDefinition[] clips = WorldInterfaceAnimations.clipsForAction(actionId);
			actionAnimations[actionId] = new KeyframeAnimation[clips.length];
			for (int index = 0; index < clips.length; index++) {
				actionAnimations[actionId][index] = clips[index].bake(root);
			}
		}
	}

	public static int staticPartBudget(int form) {
		return STATIC_PART_BUDGET[Math.clamp(form, 0, FORM_COUNT - 1)];
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(),
				PartPose.offset(0.0F, 12.0F, 0.0F));

		buildFirstForm(body.addOrReplaceChild("form_1_core", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 0.0F)));
		buildSecondForm(body.addOrReplaceChild("form_2_core", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 0.0F)));
		buildThirdForm(body.addOrReplaceChild("form_3_core", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 0.0F)));

		PartDefinition eye = body.addOrReplaceChild("eye", CubeListBuilder.create(),
				PartPose.offset(0.0F, -12.0F, -4.0F));
		buildEyes(eye);
		PartDefinition ring = body.addOrReplaceChild("ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, -12.0F, 2.0F));
		buildRings(ring);
		PartDefinition jaw = body.addOrReplaceChild("jaw", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, -7.0F, -1.0F, 0.12F, 0.0F, 0.0F));
		buildJaws(jaw);

		// BossActionS2C v1 carries no ItemStack or item id; keep this silhouette until custody data is versioned.
		body.addOrReplaceChild("weapon", CubeListBuilder.create()
				.texOffs(96, 24).addBox(-0.75F, -12.0F, -0.75F, 1.5F, 16.0F, 1.5F)
				.texOffs(104, 24).addBox(-3.0F, -11.5F, -0.5F, 6.0F, 2.0F, 1.0F)
				.texOffs(96, 44).addBox(-1.5F, -23.0F, -0.5F, 3.0F, 12.0F, 1.0F)
				.texOffs(108, 48).addBox(-2.4F, -21.0F, -0.35F, 4.8F, 1.2F, 0.7F),
				PartPose.offsetAndRotation(6.0F, -7.0F, -2.0F, -0.35F, 0.0F, -0.65F));
		buildTendrils(body);
		return LayerDefinition.create(mesh, 128, 128);
	}

	private static void buildFirstForm(PartDefinition form) {
		float[] radii = {2.8F, 3.8F, 4.8F, 5.4F, 5.7F, 5.4F, 4.8F, 3.8F, 2.7F};
		for (int index = 0; index < radii.length; index++) {
			float radius = radii[index];
			form.addOrReplaceChild("capsule_section_" + index, CubeListBuilder.create().texOffs(0, index * 4)
					.addBox(-radius, -1.65F, -radius * 0.72F, radius * 2.0F, 3.3F, radius * 1.44F),
					PartPose.offset(0.0F, -22.0F + index * 3.8F, 0.0F));
		}
		for (int index = 0; index < 12; index++) {
			float angle = index * Mth.TWO_PI / 12.0F;
			float x = Mth.cos(angle) * 6.0F;
			float z = Mth.sin(angle) * 4.1F;
			form.addOrReplaceChild("signal_rib_" + index, CubeListBuilder.create().texOffs(48, 0)
					.addBox(-0.55F, -3.0F, -0.7F, 1.1F, 6.0F, 1.4F),
					PartPose.offsetAndRotation(x, -18.0F + index % 4 * 7.0F, z,
							Mth.sin(angle) * 0.18F, -angle, Mth.cos(angle) * 0.22F));
		}
		for (int index = 0; index < 8; index++) {
			float angle = index * Mth.TWO_PI / 8.0F;
			form.addOrReplaceChild("crown_prong_" + index, CubeListBuilder.create().texOffs(56, 0)
					.addBox(-0.5F, -5.0F, -0.5F, 1.0F, 6.0F, 1.0F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 4.3F, -24.0F,
							Mth.sin(angle) * 3.2F, Mth.cos(angle) * -0.22F, -angle,
							Mth.sin(angle) * 0.22F));
		}
		for (int index = 0; index < 8; index++) {
			float angle = index * Mth.TWO_PI / 8.0F;
			form.addOrReplaceChild("lower_buttress_" + index, CubeListBuilder.create().texOffs(60, 8)
					.addBox(-0.65F, -1.0F, -0.9F, 1.3F, 7.0F, 1.8F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 3.7F, 8.0F,
							Mth.sin(angle) * 3.0F, Mth.cos(angle) * 0.34F, -angle,
							Mth.sin(angle) * -0.34F));
		}
		for (int index = 0; index < 6; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			int tier = index / 2;
			form.addOrReplaceChild("receiver_antenna_" + index, CubeListBuilder.create().texOffs(68, 0)
					.addBox(-0.4F, -4.0F, -0.4F, 0.8F, 8.0F + tier, 0.8F),
					PartPose.offsetAndRotation(side * (5.0F + tier), -18.0F + tier * 8.0F,
							1.0F, 0.0F, 0.0F, side * (0.52F + tier * 0.08F)));
		}
	}

	private static void buildSecondForm(PartDefinition form) {
		for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
			float side = sideIndex == 0 ? 1.0F : -1.0F;
			for (int tier = 0; tier < 10; tier++) {
				float width = 3.4F + Mth.sin((tier + 1) * Mth.PI / 11.0F) * 2.4F;
				form.addOrReplaceChild((side > 0 ? "right" : "left") + "_hull_" + tier,
						CubeListBuilder.create().texOffs(0, 40 + tier * 3).mirror(side < 0)
								.addBox(-width, -1.35F, -3.6F, width * 2.0F, 2.7F, 7.2F),
						PartPose.offsetAndRotation(side * (4.0F + tier * 0.16F), -23.0F + tier * 3.3F,
								0.4F, 0.0F, side * -0.12F, side * (0.10F + tier * 0.018F)));
			}
		}
		for (int index = 0; index < 9; index++) {
			form.addOrReplaceChild("bridge_" + index, CubeListBuilder.create().texOffs(36, 40)
					.addBox(-2.4F, -1.0F, -2.4F, 4.8F, 2.0F, 4.8F),
					PartPose.offsetAndRotation(0.0F, -22.0F + index * 3.9F, 0.0F,
							0.0F, index * 0.18F, 0.0F));
		}
		for (int index = 0; index < 18; index++) {
			float angle = index * Mth.TWO_PI / 18.0F;
			float radius = 8.0F + (index % 3) * 0.8F;
			form.addOrReplaceChild("frequency_blade_" + index, CubeListBuilder.create().texOffs(56, 40)
					.addBox(-0.55F, -4.8F, -1.0F, 1.1F, 9.6F, 2.0F),
					PartPose.offsetAndRotation(Mth.cos(angle) * radius,
							-13.0F + (index % 3 - 1) * 7.0F, Mth.sin(angle) * 5.0F,
							Mth.sin(angle) * 0.30F, -angle, Mth.cos(angle) * 0.42F));
		}
		for (int index = 0; index < 16; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			int tier = index / 2;
			form.addOrReplaceChild("open_rib_" + index, CubeListBuilder.create().texOffs(64, 40)
					.addBox(-0.7F, -0.7F, -4.0F, 1.4F, 1.4F, 8.0F),
					PartPose.offsetAndRotation(side * (7.0F + tier * 0.42F), -23.0F + tier * 4.5F,
							0.0F, 0.0F, side * (0.30F + tier * 0.04F), side * 0.16F));
		}
		for (int index = 0; index < 12; index++) {
			float angle = index * Mth.TWO_PI / 12.0F;
			form.addOrReplaceChild("ground_spur_" + index, CubeListBuilder.create().texOffs(84, 40)
					.addBox(-0.75F, -1.0F, -0.75F, 1.5F, 9.0F, 1.5F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 5.6F, 8.0F,
							Mth.sin(angle) * 4.2F, Mth.cos(angle) * 0.52F, -angle,
							Mth.sin(angle) * -0.52F));
		}
	}

	private static void buildThirdForm(PartDefinition form) {
		for (int tier = 0; tier < 13; tier++) {
			float width = 4.0F + Mth.sin((tier + 1) * Mth.PI / 14.0F) * 4.8F;
			form.addOrReplaceChild("thorax_" + tier, CubeListBuilder.create().texOffs(0, 82)
					.addBox(-width, -1.2F, -width * 0.58F, width * 2.0F, 2.4F, width * 1.16F),
					PartPose.offsetAndRotation(0.0F, -27.0F + tier * 3.4F, 1.0F,
							0.0F, tier * 0.13F, indexWave(tier, 0.035F)));
		}
		for (int index = 0; index < 32; index++) {
			float angle = index * Mth.TWO_PI / 32.0F;
			float radius = 8.5F + index % 4 * 0.7F;
			form.addOrReplaceChild("world_rib_" + index, CubeListBuilder.create().texOffs(38, 82)
					.addBox(-0.65F, -5.0F, -0.9F, 1.3F, 10.0F, 1.8F),
					PartPose.offsetAndRotation(Mth.cos(angle) * radius,
							-16.0F + (index % 4 - 1.5F) * 7.0F, Mth.sin(angle) * 5.8F,
							Mth.sin(angle) * 0.48F, -angle, Mth.cos(angle) * 0.54F));
		}
		for (int index = 0; index < 24; index++) {
			float angle = index * Mth.TWO_PI / 24.0F;
			form.addOrReplaceChild("interface_crown_" + index, CubeListBuilder.create().texOffs(48, 82)
					.addBox(-0.55F, -7.5F, -0.7F, 1.1F, 9.5F + index % 4, 1.4F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 7.2F, -28.0F,
							Mth.sin(angle) * 5.0F, Mth.cos(angle) * -0.44F, -angle,
							Mth.sin(angle) * 0.44F));
		}
		for (int index = 0; index < 24; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			int tier = index / 2;
			form.addOrReplaceChild("flank_plate_" + index, CubeListBuilder.create().texOffs(58, 82)
					.addBox(-0.8F, -1.6F, -4.8F, 1.6F, 3.2F, 9.6F),
					PartPose.offsetAndRotation(side * (8.0F + tier * 0.28F), -27.0F + tier * 3.5F,
							0.5F, 0.0F, side * (0.34F + tier * 0.025F), side * 0.22F));
		}
		for (int index = 0; index < 20; index++) {
			float angle = index * Mth.TWO_PI / 20.0F;
			form.addOrReplaceChild("root_socket_" + index, CubeListBuilder.create().texOffs(80, 82)
					.addBox(-0.85F, -1.0F, -0.85F, 1.7F, 11.0F + index % 3, 1.7F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 7.0F, 8.5F,
							Mth.sin(angle) * 5.0F, Mth.cos(angle) * 0.64F, -angle,
							Mth.sin(angle) * -0.64F));
		}
	}

	private static float indexWave(int index, float scale) {
		return (index % 2 == 0 ? 1.0F : -1.0F) * scale;
	}

	private static void buildEyes(PartDefinition eye) {
		PartDefinition first = eye.addOrReplaceChild("form_1_eye", CubeListBuilder.create()
				.texOffs(72, 0).addBox(-3.8F, -3.8F, -1.2F, 7.6F, 7.6F, 2.0F)
				.texOffs(72, 12).addBox(-1.8F, -1.8F, -1.9F, 3.6F, 3.6F, 1.0F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		for (int index = 0; index < 6; index++) {
			float angle = index * Mth.TWO_PI / 6.0F;
			first.addOrReplaceChild("shutter_" + index, CubeListBuilder.create().texOffs(88, 0)
					.addBox(-0.45F, -2.5F, -0.35F, 0.9F, 5.0F, 0.7F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 3.2F, Mth.sin(angle) * 3.2F,
							-1.4F, 0.0F, 0.0F, angle));
		}

		PartDefinition second = eye.addOrReplaceChild("form_2_eye", CubeListBuilder.create()
				.texOffs(72, 0).addBox(-5.6F, -4.2F, -1.4F, 11.2F, 8.4F, 2.2F)
				.texOffs(72, 12).addBox(-2.4F, -2.4F, -2.1F, 4.8F, 4.8F, 1.2F),
				PartPose.offset(0.0F, 0.0F, -0.4F));
		for (int index = 0; index < 10; index++) {
			float angle = index * Mth.TWO_PI / 10.0F;
			second.addOrReplaceChild("aperture_" + index, CubeListBuilder.create().texOffs(92, 8)
					.addBox(-0.5F, -3.1F, -0.4F, 1.0F, 6.2F, 0.8F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 4.8F, Mth.sin(angle) * 3.7F,
							-1.6F, 0.0F, 0.0F, angle));
		}

		PartDefinition third = eye.addOrReplaceChild("form_3_eye", CubeListBuilder.create()
				.texOffs(72, 0).addBox(-7.5F, -6.0F, -1.8F, 15.0F, 12.0F, 2.8F)
				.texOffs(72, 16).addBox(-3.4F, -3.4F, -2.7F, 6.8F, 6.8F, 1.5F)
				.texOffs(88, 16).addBox(-0.9F, -2.8F, -3.3F, 1.8F, 5.6F, 0.8F),
				PartPose.offset(0.0F, -1.0F, -0.8F));
		for (int index = 0; index < 16; index++) {
			float angle = index * Mth.TWO_PI / 16.0F;
			third.addOrReplaceChild("eye_cage_" + index, CubeListBuilder.create().texOffs(100, 0)
					.addBox(-0.55F, -4.4F, -0.45F, 1.1F, 8.8F, 0.9F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 6.9F, Mth.sin(angle) * 5.4F,
							-2.0F, 0.0F, 0.0F, angle));
		}
	}

	private static void buildRings(PartDefinition ring) {
		PartDefinition first = ring.addOrReplaceChild("form_1_ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		addRingSegments(first, "fragment", 8, 6.5F, 1.0F, 3.8F, 96, 0);
		PartDefinition second = ring.addOrReplaceChild("form_2_ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		addRingSegments(second, "segment", 16, 10.0F, 1.25F, 4.8F, 96, 0);
		PartDefinition third = ring.addOrReplaceChild("form_3_ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 1.5F));
		addRingSegments(third, "outer_segment", 24, 14.0F, 1.5F, 5.8F, 96, 0);
		addRingSegments(third, "inner_segment", 16, 9.5F, 0.8F, 3.6F, 108, 0);
	}

	private static void addRingSegments(PartDefinition ring, String prefix, int count,
			float radius, float width, float length, int u, int v) {
		for (int index = 0; index < count; index++) {
			float angle = index * Mth.TWO_PI / count;
			ring.addOrReplaceChild(prefix + "_" + index, CubeListBuilder.create().texOffs(u, v)
					.addBox(-width * 0.5F, -length * 0.5F, -0.8F, width, length, 1.6F),
					PartPose.offsetAndRotation(Mth.cos(angle) * radius, Mth.sin(angle) * radius,
							0.0F, 0.0F, 0.0F, angle));
		}
	}

	private static void buildJaws(PartDefinition jaw) {
		jaw.addOrReplaceChild("form_1_jaw", CubeListBuilder.create().texOffs(32, 24)
				.addBox(-3.8F, 0.0F, -3.0F, 7.6F, 2.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition second = jaw.addOrReplaceChild("form_2_jaw", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		second.addOrReplaceChild("left_mandible", CubeListBuilder.create().texOffs(32, 24)
				.addBox(-5.8F, 0.0F, -4.0F, 5.2F, 3.2F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.16F, 0.04F));
		second.addOrReplaceChild("right_mandible", CubeListBuilder.create().texOffs(32, 24).mirror()
				.addBox(0.6F, 0.0F, -4.0F, 5.2F, 3.2F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.16F, -0.04F));
		PartDefinition third = jaw.addOrReplaceChild("form_3_jaw", CubeListBuilder.create().texOffs(32, 24)
				.addBox(-7.5F, 0.0F, -5.5F, 15.0F, 4.0F, 9.0F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		for (int index = 0; index < 14; index++) {
			float x = -6.5F + index;
			third.addOrReplaceChild("tooth_" + index, CubeListBuilder.create().texOffs(68, 24)
					.addBox(-0.35F, 0.0F, -0.45F, 0.7F, 2.6F + index % 3 * 0.35F, 0.9F),
					PartPose.offsetAndRotation(x, 3.2F, -5.2F, indexWave(index, 0.08F), 0.0F, 0.0F));
		}
	}

	private static void buildTendrils(PartDefinition body) {
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			int row = index / 2;
			PartDefinition root = body.addOrReplaceChild("tendril_" + index, CubeListBuilder.create(),
					PartPose.offsetAndRotation(side * 4.5F, -18.0F + row * 5.0F, 1.5F,
							-0.22F + row * 0.08F, side * (0.2F + row * 0.05F), -side * 0.75F));
			PartDefinition first = root.addOrReplaceChild("form_1", CubeListBuilder.create().texOffs(0, 52)
					.addBox(-0.8F, 0.0F, -0.8F, 1.6F, 10.0F + row, 1.6F),
					PartPose.offset(0.0F, 0.0F, 0.0F));
			first.addOrReplaceChild("needle", CubeListBuilder.create().texOffs(8, 52)
					.addBox(-0.35F, 0.0F, -0.35F, 0.7F, 8.0F, 0.7F),
					PartPose.offsetAndRotation(0.0F, 9.0F + row, 0.0F, -0.28F, 0.0F, -side * 0.36F));

			PartDefinition second = root.addOrReplaceChild("form_2", CubeListBuilder.create().texOffs(12, 52)
					.addBox(-1.15F, 0.0F, -1.15F, 2.3F, 13.0F + row, 2.3F),
					PartPose.offset(0.0F, 0.0F, 0.0F));
			second.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(22, 52)
					.addBox(-0.55F, 0.0F, -2.4F, 1.1F, 10.0F, 4.8F),
					PartPose.offsetAndRotation(0.0F, 11.0F + row, 0.0F, -0.38F, 0.0F, -side * 0.42F));
			second.addOrReplaceChild("fork", CubeListBuilder.create().texOffs(34, 52)
					.addBox(-1.5F, 0.0F, -0.5F, 3.0F, 8.0F, 1.0F),
					PartPose.offsetAndRotation(side * 1.2F, 18.0F + row, 0.0F,
							-0.46F, side * 0.18F, -side * 0.54F));

			PartDefinition third = root.addOrReplaceChild("form_3", CubeListBuilder.create().texOffs(44, 52)
					.addBox(-1.45F, 0.0F, -1.45F, 2.9F, 15.0F + row, 2.9F),
					PartPose.offset(0.0F, 0.0F, 0.0F));
			PartDefinition mid = third.addOrReplaceChild("armored_mid", CubeListBuilder.create().texOffs(56, 52)
					.addBox(-1.05F, 0.0F, -1.8F, 2.1F, 13.0F + row, 3.6F),
					PartPose.offsetAndRotation(0.0F, 13.5F + row, 0.0F,
							-0.34F, side * 0.12F, -side * 0.46F));
			mid.addOrReplaceChild("world_hook", CubeListBuilder.create().texOffs(72, 52)
					.addBox(-0.8F, 0.0F, -2.7F, 1.6F, 13.0F + row, 5.4F),
					PartPose.offsetAndRotation(0.0F, 11.5F + row, 0.0F,
							-0.50F, side * 0.22F, -side * 0.58F));
		}
	}

	@Override
	public void setupAnim(WorldInterfaceRenderState state) {
		super.setupAnim(state);
		int form = Math.clamp(state.form, 0, FORM_COUNT - 1);
		for (int index = 0; index < FORM_COUNT; index++) {
			boolean active = index == form;
			formCores[index].visible = active;
			formEyes[index].visible = active;
			formRings[index].visible = active;
			formJaws[index].visible = active;
		}
		weapon.visible = form >= 1 && state.actionId == 5;
		int activeTendrils = form == 0 ? 4 : form == 1 ? 6 : 10;
		for (int index = 0; index < tendrils.length; index++) {
			boolean active = index < activeTendrils;
			tendrils[index].visible = active;
			for (int variant = 0; variant < FORM_COUNT; variant++) {
				tendrilForms[index][variant].visible = active && variant == form;
			}
		}
		long idleAgeMillis = (long) (state.ageInTicks * 50.0F);
		for (KeyframeAnimation idle : idleAnimations) idle.apply(idleAgeMillis, 1.0F);
		if (state.actionId > 0 && state.actionId < actionAnimations.length) {
			KeyframeAnimation[] clips = actionAnimations[state.actionId];
			if (clips != null) for (KeyframeAnimation clip : clips) clip.apply(state.actionAgeMillis, 1.0F);
		}
		float formBreath = 0.015F + form * 0.009F;
		body.y += Mth.sin(state.ageInTicks * 0.055F) * (0.35F + form * 0.15F);
		body.xScale = body.zScale = 1.0F + Mth.sin(state.ageInTicks * 0.08F) * formBreath;
	}
}
