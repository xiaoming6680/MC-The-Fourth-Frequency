package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.entity.WorldInterfaceAnatomy;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Stable animation bones hosting three visually independent, statically baked form hierarchies.
 *
 * <p>The silhouette is a storm rather than a machine: an off-axis mass of swallowed terrain, three
 * skulls carried on necks that grow out of it, and tentacles trailing from the underside. Every
 * form is the same animal further along, so the read never resets across a morph — the necks
 * lengthen and then sink back into the mass, the body takes on more world, and the tentacle count
 * more than doubles.
 */
public final class WorldInterfaceModel extends EntityModel<WorldInterfaceRenderState> {
	public static final int FORM_COUNT = 3;
	public static final int ANIMATED_BONE_COUNT = 15;
	public static final int MAX_VISIBLE_STATIC_PARTS = 1_024;
	private static final int[] STATIC_PART_BUDGET = {512, 768, MAX_VISIBLE_STATIC_PARTS};
	private static final int SKULLS_PER_FORM = 3;
	/** Core centre in form space; {@link #insideCoreSocket} keeps accretion off it. */
	private static final float CORE_Y = -13.0F;
	/** Steps in the funnel that seats the core; see {@link #addCoreSocket}. */
	private static final int CORE_SOCKET_RINGS = 3;
	private static final float CORE_SOCKET_BAR = 1.6F;

	private static final int SOCKETS_PER_SKULL = 2;

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart eye;
	private final ModelPart ring;
	private final ModelPart jaw;
	private final ModelPart weapon;
	private final ModelPart[] formCores = new ModelPart[FORM_COUNT];
	private final ModelPart[] formEyes = new ModelPart[FORM_COUNT];
	private final ModelPart[] formRings = new ModelPart[FORM_COUNT];
	private final ModelPart[] formJaws = new ModelPart[FORM_COUNT];
	private final ModelPart[][] formRingSegments = new ModelPart[FORM_COUNT][];
	private final ModelPart[][] formSkulls = new ModelPart[FORM_COUNT][SKULLS_PER_FORM];
	/** Held separately from the skulls because the emissive pass submits them without their parent. */
	private final ModelPart[][][] formSockets = new ModelPart[FORM_COUNT][SKULLS_PER_FORM][SOCKETS_PER_SKULL];
	private final ModelPart[] tendrils = new ModelPart[10];
	private final ModelPart[][] tendrilForms = new ModelPart[10][FORM_COUNT];
	private final ModelPart[][] tendrilGlows = new ModelPart[10][FORM_COUNT];
	private final KeyframeAnimation[] idleAnimations;
	private final KeyframeAnimation[][] actionAnimations =
			new KeyframeAnimation[WorldInterfaceAnimations.PROTOCOL_ACTION_COUNT + 1][];

	public WorldInterfaceModel(ModelPart root) {
		super(root);
		this.root = root;
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
			for (int skull = 0; skull < SKULLS_PER_FORM; skull++) {
				formSkulls[form][skull] = formCores[form].getChild("skull_" + skull);
				for (int socket = 0; socket < SOCKETS_PER_SKULL; socket++) {
					formSockets[form][skull][socket] = formSkulls[form][skull].getChild("socket_" + socket);
				}
			}
		}
		formRingSegments[0] = collectSegments(formRings[0], "fragment", 8);
		formRingSegments[1] = collectSegments(formRings[1], "segment", 16);
		ModelPart[] outer = collectSegments(formRings[2], "outer_segment", 24);
		ModelPart[] inner = collectSegments(formRings[2], "inner_segment", 16);
		ModelPart[] third = new ModelPart[outer.length + inner.length];
		System.arraycopy(outer, 0, third, 0, outer.length);
		System.arraycopy(inner, 0, third, outer.length, inner.length);
		formRingSegments[2] = third;
		for (int index = 0; index < tendrils.length; index++) {
			tendrils[index] = body.getChild("tendril_" + index);
			for (int form = 0; form < FORM_COUNT; form++) {
				tendrilForms[index][form] = tendrils[index].getChild("form_" + (form + 1));
				tendrilGlows[index][form] = tendrilForms[index][form].getChild("glow");
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

	/**
	 * Submit only the bones that actually carry glow, rather than the whole model a second time.
	 *
	 * <p>The emissive sheet is under half a percent non-transparent, so submitting all of the third
	 * form's several hundred parts for it meant nearly every vertex went through a translucent pass
	 * to draw nothing — and paid for the sort on the way. The lit parts are the core, the inner
	 * halo, the skull sockets and the tentacle nodes, and between them they are a small fraction of
	 * the body.
	 *
	 * <p>Bones are submitted individually, so the parent transforms they would normally inherit
	 * have to be walked by hand; {@code submitModelPart} copies the pose, which is what makes the
	 * push/pop around it safe. Visibility is gated here too, because a bone submitted directly
	 * never consults the parent whose {@code visible} flag would otherwise have hidden it.
	 */
	void submitEmissive(PoseStack poseStack, OrderedSubmitNodeCollector collector, RenderType renderType,
			int color, int outlineColor, int form) {
		int active = Math.clamp(form, 0, FORM_COUNT - 1);
		poseStack.pushPose();
		root.translateAndRotate(poseStack);
		body.translateAndRotate(poseStack);

		// The eye and halo subtrees carry their own per-form visible flags, so one submit each
		// draws exactly the form that is showing.
		submitPart(collector, eye, poseStack, renderType, color, outlineColor);
		submitPart(collector, ring, poseStack, renderType, color, outlineColor);

		poseStack.pushPose();
		formCores[active].translateAndRotate(poseStack);
		for (int skull = 0; skull < SKULLS_PER_FORM; skull++) {
			poseStack.pushPose();
			formSkulls[active][skull].translateAndRotate(poseStack);
			for (ModelPart socket : formSockets[active][skull]) {
				submitPart(collector, socket, poseStack, renderType, color, outlineColor);
			}
			poseStack.popPose();
		}
		poseStack.popPose();

		int limbs = WorldInterfaceAnatomy.tentacleCount(active);
		for (int index = 0; index < limbs && index < tendrils.length; index++) {
			poseStack.pushPose();
			tendrils[index].translateAndRotate(poseStack);
			tendrilForms[index][active].translateAndRotate(poseStack);
			submitPart(collector, tendrilGlows[index][active], poseStack, renderType, color, outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void submitPart(OrderedSubmitNodeCollector collector, ModelPart part,
			PoseStack poseStack, RenderType renderType, int color, int outlineColor) {
		collector.submitModelPart(part, poseStack, renderType, LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, null, false, false, color, null, outlineColor);
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

		// Eye, halo and maw all sit well clear of the mass rather than inside it. Buried, the core
		// only ever showed as a bloom through the plating and the telegraph shapes were invisible;
		// standing proud of the body is what makes them the thing you actually aim at.
		PartDefinition eye = body.addOrReplaceChild("eye", CubeListBuilder.create(),
				PartPose.offset(0.0F, -13.0F, -11.0F));
		buildEyes(eye);
		PartDefinition ring = body.addOrReplaceChild("ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, -13.0F, -8.0F));
		buildRings(ring);
		PartDefinition jaw = body.addOrReplaceChild("jaw", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, -4.0F, -7.0F, 0.12F, 0.0F, 0.0F));
		buildJaws(jaw);

		// BossActionS2C v1 carries no ItemStack or item id; keep this silhouette until custody data is versioned.
		body.addOrReplaceChild("weapon", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.WEAPON_HAFT_U, WorldInterfaceUv.WEAPON_HAFT_V)
				.addBox(-0.75F, -12.0F, -0.75F, 1.5F, 16.0F, 1.5F)
				.texOffs(WorldInterfaceUv.WEAPON_GUARD_U, WorldInterfaceUv.WEAPON_GUARD_V)
				.addBox(-3.0F, -11.5F, -0.5F, 6.0F, 2.0F, 1.0F)
				.texOffs(WorldInterfaceUv.WEAPON_BLADE_U, WorldInterfaceUv.WEAPON_BLADE_V)
				.addBox(-1.5F, -23.0F, -0.5F, 3.0F, 12.0F, 1.0F)
				.texOffs(WorldInterfaceUv.WEAPON_FULLER_U, WorldInterfaceUv.WEAPON_FULLER_V)
				.addBox(-2.4F, -21.0F, -0.35F, 4.8F, 1.2F, 0.7F),
				PartPose.offsetAndRotation(6.0F, -7.0F, -2.0F, -0.35F, 0.0F, -0.65F));
		buildTendrils(body);
		return LayerDefinition.create(mesh, WorldInterfaceUv.UV_WIDTH, WorldInterfaceUv.UV_HEIGHT);
	}

	// All three forms share one UV layout. The parts are the same materials at every stage -- torn
	// terrain, bone, plating -- and what actually changes between forms is the palette, which the
	// three separate PNGs already carry. Giving each form its own islands would have cost three
	// times the sheet to paint the same six materials.

	/** Nascent: long exposed necks, so it still reads as a wither that something went wrong with. */
	private static void buildFirstForm(PartDefinition form) {
		addMass(form, "mass", 14, -21.0F, 2.3F, 2.6F, 5.0F, 0.82F, 1.6F, 11);
		addSkulls(form, 0.62F, -19.0F, 3, false, 6.4F, 2.6F);
		addRibs(form, "rib", 10, -17.0F, 2.9F, 7.4F, 7.0F, 23);
		addDebris(form, "debris", 16, -19.0F, 23.0F, 7.8F, 0.85F, 1.8F, 7.0F, 6.5F, 31);
		addRoots(form, "root", 9, 4.0F, 6.5F, 47);
		addPlating(form, "plate", 42, -22.0F, 28.0F, 6.2F, 0.55F, 1.5F, 7.0F, 6.5F, 61);
		addCoreSocket(form, -6.7F, -11.7F, 7.0F, 6.5F, 5.0F, 5.0F);
	}

	/** Grown: the mass has taken enough terrain that the necks are half sunk into it. */
	private static void buildSecondForm(PartDefinition form) {
		addMass(form, "mass", 20, -24.0F, 1.8F, 3.4F, 7.4F, 0.80F, 2.5F, 41);
		addSkulls(form, 0.88F, -21.0F, 2, true, 9.0F, 3.4F);
		addRibs(form, "rib", 22, -21.0F, 2.7F, 11.0F, 10.0F, 53);
		addDebris(form, "debris", 34, -23.0F, 29.0F, 11.4F, 1.2F, 3.0F, 9.5F, 8.0F, 67);
		addRoots(form, "root", 16, 5.2F, 9.5F, 83);
		addPlating(form, "plate", 96, -26.0F, 34.0F, 9.0F, 0.75F, 2.4F, 9.5F, 8.0F, 101);
		addCoreSocket(form, -11.9F, -15.2F, 9.5F, 8.0F, 7.0F, 5.4F);
	}

	/** Terminal: the heads sit straight on the body, and the body is mostly other people's world. */
	private static void buildThirdForm(PartDefinition form) {
		addMass(form, "mass", 26, -26.0F, 1.5F, 4.2F, 9.4F, 0.78F, 3.4F, 97);
		addSkulls(form, 1.35F, -23.0F, 1, true, 11.5F, 4.4F);
		addRibs(form, "rib", 32, -25.0F, 2.3F, 13.6F, 13.0F, 109);
		addDebris(form, "debris", 56, -27.0F, 35.0F, 14.0F, 1.5F, 4.2F, 12.5F, 10.5F, 127);
		addRoots(form, "root", 28, 6.4F, 12.5F, 149);
		addPlating(form, "plate", 168, -29.0F, 40.0F, 11.5F, 0.95F, 3.2F, 12.5F, 10.5F, 181);
		addCoreSocket(form, -12.4F, -17.0F, 12.5F, 10.5F, 9.2F, 7.4F);
	}

	/**
	 * The seat the core sits in.
	 *
	 * <p>Two earlier decisions met badly in the middle of the chest. {@link #insideCoreSocket} keeps
	 * plating and debris off the intake so the telegraph shapes stay readable, which leaves a hole
	 * in the front of the mass some twenty-five units across; and the eye is built well forward of
	 * the body so it is a thing to aim at rather than a bloom under the armour. Between them the
	 * chest was an empty socket with a disc hovering in front of it and nothing in between — around
	 * three blocks of daylight at third form, plainly visible from any angle but dead ahead.
	 *
	 * <p>This closes it: frames stepping inward and forward from the mass's own front surface to the
	 * rim of the eye, so the core reads as set into the body. Deliberately a funnel rather than a
	 * filled plug — the intake has to stay open, or the halo and the jaw lose the recess they are
	 * arranged around and the whole assembly flattens back into a plate with a light on it.
	 */
	private static void addCoreSocket(PartDefinition form, float frontZ, float backZ,
			float outerWidth, float outerHeight, float innerWidth, float innerHeight) {
		PartDefinition socket = form.addOrReplaceChild("core_socket", CubeListBuilder.create(),
				PartPose.offset(0.0F, CORE_Y, 0.0F));
		// Frames are spaced across the span by the gaps between them, not by their count, so the
		// depth has to divide by one less than the ring count. Dividing by the count left the first
		// form a hairline of daylight between consecutive frames. The extra is overlap on top.
		float depth = Math.abs(backZ - frontZ) / (CORE_SOCKET_RINGS - 1) + 0.8F;
		float bar = CORE_SOCKET_BAR;
		for (int index = 0; index < CORE_SOCKET_RINGS; index++) {
			float progress = index / (float) (CORE_SOCKET_RINGS - 1);
			float halfWidth = Mth.lerp(progress, outerWidth, innerWidth);
			float halfHeight = Mth.lerp(progress, outerHeight, innerHeight);
			float z = Mth.lerp(progress, frontZ, backZ);
			// Top and bottom run the full width so the corners are closed without doubling up.
			int[] rail = WorldInterfaceUv.coreCollarRail(Math.max(halfWidth, halfHeight));
			socket.addOrReplaceChild("collar_" + index + "_top", CubeListBuilder.create()
					.texOffs(rail[0], rail[1]).addBox(-(halfWidth + bar), -(halfHeight + bar), z,
							(halfWidth + bar) * 2.0F, bar, depth), PartPose.ZERO);
			socket.addOrReplaceChild("collar_" + index + "_bottom", CubeListBuilder.create()
					.texOffs(rail[0], rail[1]).addBox(-(halfWidth + bar), halfHeight, z,
							(halfWidth + bar) * 2.0F, bar, depth), PartPose.ZERO);
			int[] post = WorldInterfaceUv.coreCollarPost(Math.max(halfWidth, halfHeight));
			socket.addOrReplaceChild("collar_" + index + "_left", CubeListBuilder.create()
					.texOffs(post[0], post[1]).addBox(-(halfWidth + bar), -halfHeight, z,
							bar, halfHeight * 2.0F, depth), PartPose.ZERO);
			socket.addOrReplaceChild("collar_" + index + "_right", CubeListBuilder.create()
					.texOffs(post[0], post[1]).addBox(halfWidth, -halfHeight, z,
							bar, halfHeight * 2.0F, depth), PartPose.ZERO);
		}
	}

	/**
	 * Surface clutter hugging the mass: torn panels, half-swallowed blocks, plates lifted off the
	 * shell at an angle. The slabs underneath give the storm its outline, but an outline is all they
	 * give, and at this size a smooth flank reads as a painted backdrop. These break the light up so
	 * the body has a texture to it from a distance the actual texture cannot survive.
	 */
	private static void addPlating(PartDefinition form, String prefix, int count, float top, float span,
			float radius, float minSize, float maxSize, float socketWidth, float socketHeight,
			int seed) {
		for (int index = 0; index < count; index++) {
			float height = hash(seed + index * 5);
			// Follows the same swell the mass uses, so the clutter sits on the body rather than in a
			// cylinder around it.
			float shell = radius * (0.62F + Mth.sin(Math.min(1.0F, height * 1.22F) * Mth.PI) * 0.68F);
			float angle = hash(seed + index * 5 + 1) * Mth.TWO_PI;
			float size = minSize + hash(seed + index * 5 + 2) * (maxSize - minSize);
			float lift = 0.92F + hash(seed + index * 5 + 3) * 0.26F;
			float px = Mth.cos(angle) * shell * lift;
			float py = top + span * height;
			float pz = Mth.sin(angle) * shell * 0.78F * lift;
			if (insideCoreSocket(px, py, pz, socketWidth, socketHeight)) continue;
			int[] uv = WorldInterfaceUv.plate(size);
			form.addOrReplaceChild(prefix + "_" + index, CubeListBuilder.create().texOffs(uv[0], uv[1])
					.addBox(-size, -size * 0.42F, -size * 0.72F,
							size * 2.0F, size * 0.84F, size * 1.44F),
					PartPose.offsetAndRotation(px, py, pz,
							centred(seed + index * 7) * 0.9F, -angle,
							centred(seed + index * 11) * 0.9F));
		}
	}

	/**
	 * The body is not a shape so much as an accumulation: slabs of unequal width, each shoved off
	 * the axis and turned, so no two silhouettes read the same as the storm rotates. A clean stack
	 * of concentric sections read as a capsule from every angle, which is exactly the wrong idea.
	 *
	 * <p>The swell peaks around two fifths of the way down and is clamped to nothing below that, so
	 * the mass is top heavy and tapers into the stalk the tentacles hang off — a symmetric bulge put
	 * as much body under the storm as over it, which read as a floating egg.
	 */
	private static void addMass(PartDefinition form, String prefix, int count, float top, float step,
			float base, float swell, float depth, float drift, int seed) {
		for (int index = 0; index < count; index++) {
			float span = Math.min(1.0F, (index + 0.5F) / count * 1.22F);
			float halfWidth = base + swell * Mth.sin(span * Mth.PI) * (0.70F + hash(seed + index) * 0.55F);
			// Depth graded separately from width, or every slab is the same flattened plank and the
			// stack reads as a woodpile instead of a lump.
			float halfDepth = halfWidth * depth * (0.72F + hash(seed + index * 9) * 0.58F);
			// Slabs range from three units across to nearly thirty; one island for all of them would
			// have left the small ones sampling a corner of the large ones' material.
			int[] uv = WorldInterfaceUv.mass(halfWidth);
			form.addOrReplaceChild(prefix + "_" + index, CubeListBuilder.create().texOffs(uv[0], uv[1])
					.addBox(-halfWidth, -step * 0.62F, -halfDepth,
							halfWidth * 2.0F, step * 1.24F, halfDepth * 2.0F),
					PartPose.offsetAndRotation(centred(seed + index * 5) * drift,
							top + index * step, centred(seed + index * 5 + 2) * drift * 0.8F,
							centred(seed + index * 3) * 0.18F,
							index * 0.37F + hash(seed + index) * 0.45F,
							centred(seed + index * 7) * 0.22F));
		}
	}

	/**
	 * Centre head forward and high, the other two set back and turned outward, so the three read as
	 * one animal from any angle instead of as a row of trophies.
	 */
	private static void addSkulls(PartDefinition form, float scale, float top, int neckSegments,
			boolean horns, float spread, float drop) {
		addSkull(form, "skull_0", scale, neckSegments, horns,
				0.0F, top, -2.6F * scale, -0.14F, 0.0F, 0.0F);
		addSkull(form, "skull_1", scale * 0.84F, neckSegments, horns,
				spread, top + drop, 1.4F * scale, 0.08F, 0.66F, 0.28F);
		addSkull(form, "skull_2", scale * 0.84F, neckSegments, horns,
				-spread, top + drop, 1.4F * scale, 0.08F, -0.66F, -0.28F);
	}

	/**
	 * One builder at three scales, so the head stays recognisably the same head as the body it grew
	 * out of closes over it. The eye sockets get their own emissive island rather than plating,
	 * which is what puts them in the emissive pass alongside the core.
	 */
	private static void addSkull(PartDefinition form, String name, float scale, int neckSegments,
			boolean horns, float x, float y, float z, float xRot, float yRot, float zRot) {
		PartDefinition neck = form.addOrReplaceChild(name, CubeListBuilder.create(),
				PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot));
		float lift = 0.0F;
		float push = 0.0F;
		// One skull builder runs at six scales -- three forms times centre and flank -- so every
		// piece of it picks its island by that scale rather than sharing one across a 2.6x spread.
		for (int index = 0; index < neckSegments; index++) {
			float thick = scale * (2.4F - index * 0.28F);
			int[] uv = WorldInterfaceUv.vertebra(thick);
			neck.addOrReplaceChild("vertebra_" + index, CubeListBuilder.create().texOffs(uv[0], uv[1])
					.addBox(-thick, -thick, -thick, thick * 2.0F, thick * 2.2F, thick * 2.0F),
					PartPose.offsetAndRotation(0.0F, lift, push, -0.11F, 0.0F, 0.0F));
			lift -= thick * 1.85F;
			push -= thick * 0.55F;
		}
		float headY = lift - scale * 3.2F;
		float headZ = push - scale * 0.9F;
		int[] craniumUv = WorldInterfaceUv.cranium(scale);
		neck.addOrReplaceChild("cranium", CubeListBuilder.create().texOffs(craniumUv[0], craniumUv[1])
				.addBox(-4.0F * scale, -4.0F * scale, -4.0F * scale,
						8.0F * scale, 7.2F * scale, 8.0F * scale),
				PartPose.offset(0.0F, headY, headZ));
		int[] browUv = WorldInterfaceUv.brow(scale);
		neck.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(browUv[0], browUv[1])
				.addBox(-4.3F * scale, -4.5F * scale, -4.9F * scale,
						8.6F * scale, 2.4F * scale, 1.2F * scale),
				PartPose.offset(0.0F, headY, headZ));
		int[] mawUv = WorldInterfaceUv.maw(scale);
		neck.addOrReplaceChild("maw", CubeListBuilder.create().texOffs(mawUv[0], mawUv[1])
				.addBox(-3.4F * scale, 2.6F * scale, -4.6F * scale,
						6.8F * scale, 2.6F * scale, 5.0F * scale),
				PartPose.offsetAndRotation(0.0F, headY, headZ, 0.20F, 0.0F, 0.0F));
		int[] socketUv = WorldInterfaceUv.socket(scale);
		for (int index = 0; index < 2; index++) {
			float side = index == 0 ? 1.0F : -1.0F;
			neck.addOrReplaceChild("socket_" + index, CubeListBuilder.create().texOffs(socketUv[0], socketUv[1])
					.addBox(-1.15F * scale, -1.15F * scale, -0.55F * scale,
							2.3F * scale, 2.3F * scale, 1.1F * scale),
					PartPose.offset(side * 1.95F * scale, headY - 0.6F * scale, headZ - 4.3F * scale));
		}
		if (horns) {
			int[] hornUv = WorldInterfaceUv.horn(scale);
			for (int index = 0; index < 2; index++) {
				float side = index == 0 ? 1.0F : -1.0F;
				neck.addOrReplaceChild("horn_" + index, CubeListBuilder.create().texOffs(hornUv[0], hornUv[1])
						.addBox(-0.7F * scale, -5.6F * scale, -0.7F * scale,
								1.4F * scale, 5.8F * scale, 1.4F * scale),
						PartPose.offsetAndRotation(side * 3.1F * scale, headY - 3.4F * scale,
								headZ + 1.1F * scale, -0.24F, 0.0F, side * 0.48F));
			}
		}
	}

	/** Ribcage breaking the surface in pairs, so the mass reads as something that burst rather than grew. */
	private static void addRibs(PartDefinition form, String prefix, int count, float top, float step,
			float radius, float length, int seed) {
		int rows = Math.max(1, count / 2);
		for (int index = 0; index < count; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			int row = index / 2;
			float grade = 0.66F + Mth.sin((row + 1) * Mth.PI / (rows + 1.0F)) * 0.62F;
			float thick = 0.62F + hash(seed + index) * 0.5F;
			// Length dominates a rib's UV footprint, so that is what the island is chosen by.
			int[] uv = WorldInterfaceUv.rib(length * grade);
			form.addOrReplaceChild(prefix + "_" + index, CubeListBuilder.create().texOffs(uv[0], uv[1])
					.mirror(side < 0).addBox(-thick, -thick * 1.2F, -length * grade * 0.5F,
							thick * 2.0F, thick * 2.4F, length * grade),
					PartPose.offsetAndRotation(side * radius * grade, top + row * step, 0.8F,
							centred(seed + index * 3) * 0.24F,
							side * (0.24F + row * 0.06F),
							side * (0.30F + hash(seed + index * 5) * 0.34F)));
		}
	}

	/** Blocks torn out of the world and still turning where the mass caught them. */
	private static void addDebris(PartDefinition form, String prefix, int count, float top, float span,
			float radius, float minSize, float maxSize, float socketWidth, float socketHeight,
			int seed) {
		for (int index = 0; index < count; index++) {
			float angle = hash(seed + index * 3) * Mth.TWO_PI;
			float distance = radius * (0.62F + hash(seed + index * 3 + 1) * 0.58F);
			float size = minSize + hash(seed + index * 3 + 2) * (maxSize - minSize);
			float px = Mth.cos(angle) * distance;
			float py = top + span * hash(seed + index * 7);
			float pz = Mth.sin(angle) * distance * 0.74F;
			if (insideCoreSocket(px, py, pz, socketWidth, socketHeight)) continue;
			int[] uv = WorldInterfaceUv.debris(size);
			form.addOrReplaceChild(prefix + "_" + index, CubeListBuilder.create().texOffs(uv[0], uv[1])
					.addBox(-size, -size, -size, size * 2.0F, size * 2.0F, size * 2.0F),
					PartPose.offsetAndRotation(px, py, pz,
							hash(seed + index) * 1.3F, angle, hash(seed + index * 11) * 1.3F));
		}
	}

	/** The underside never closes: it trails torn roots that keep the storm looking anchored to nothing. */
	private static void addRoots(PartDefinition form, String prefix, int count, float radius, float length,
			int seed) {
		for (int index = 0; index < count; index++) {
			float angle = index * Mth.TWO_PI / count + hash(seed + index) * 0.5F;
			float grade = 0.66F + hash(seed + index * 3) * 0.72F;
			int[] uv = WorldInterfaceUv.root(grade);
			form.addOrReplaceChild(prefix + "_" + index, CubeListBuilder.create().texOffs(uv[0], uv[1])
					.addBox(-0.85F * grade, -1.0F, -0.85F * grade,
							1.7F * grade, length * grade, 1.7F * grade),
					PartPose.offsetAndRotation(Mth.cos(angle) * radius, 6.5F,
							Mth.sin(angle) * radius * 0.78F,
							Mth.cos(angle) * 0.46F, -angle, Mth.sin(angle) * -0.46F));
		}
	}

	private static float indexWave(int index, float scale) {
		return (index % 2 == 0 ? 1.0F : -1.0F) * scale;
	}

	/**
	 * The intake stays clear. Plating and debris both accrete across the whole shell, and the widest
	 * part of the mass is exactly the height the core sits at, so without a carve-out the storm grows
	 * a lid over its own eye and the telegraph shapes go back to being unreadable.
	 */
	private static boolean insideCoreSocket(float x, float y, float z, float halfWidth, float halfHeight) {
		return z < 0.0F && Math.abs(x) < halfWidth && Math.abs(y - CORE_Y) < halfHeight;
	}

	/** Bake-time only: stable per-index variation, so the mass looks accreted instead of tiled. */
	private static float hash(int seed) {
		return WorldInterfaceScatter.hash(seed);
	}

	private static float centred(int seed) {
		return WorldInterfaceScatter.centred(seed);
	}

	/**
	 * The core is the thing the player aims at, so each form's eyeball and pupil get a dedicated
	 * island instead of the three sharing one. They differ by a factor of two in size, and sharing
	 * meant the first form sampled a corner of the third form's sclera and its pupil landed off
	 * the painted glyph entirely.
	 */
	private static void buildEyes(PartDefinition eye) {
		PartDefinition first = eye.addOrReplaceChild("form_1_eye", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.EYE_1_BALL_U, WorldInterfaceUv.EYE_1_BALL_V)
				.addBox(-4.2F, -4.2F, -1.2F, 8.4F, 8.4F, 2.0F)
				.texOffs(WorldInterfaceUv.EYE_1_PUPIL_U, WorldInterfaceUv.EYE_1_PUPIL_V)
				.addBox(-1.8F, -1.8F, -1.9F, 3.6F, 3.6F, 1.0F),
				PartPose.offset(0.0F, 0.0F, -1.5F));
		for (int index = 0; index < 6; index++) {
			float angle = index * Mth.TWO_PI / 6.0F;
			first.addOrReplaceChild("shutter_" + index, CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.EYE_1_SHUTTER_U, WorldInterfaceUv.EYE_1_SHUTTER_V)
					.addBox(-0.9F, -2.6F, -0.7F, 1.8F, 5.2F, 1.4F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 3.6F, Mth.sin(angle) * 3.6F,
							-1.4F, 0.0F, 0.0F, angle));
		}

		PartDefinition second = eye.addOrReplaceChild("form_2_eye", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.EYE_2_BALL_U, WorldInterfaceUv.EYE_2_BALL_V)
				.addBox(-6.2F, -4.6F, -1.4F, 12.4F, 9.2F, 2.2F)
				.texOffs(WorldInterfaceUv.EYE_2_PUPIL_U, WorldInterfaceUv.EYE_2_PUPIL_V)
				.addBox(-2.4F, -2.4F, -2.1F, 4.8F, 4.8F, 1.2F),
				PartPose.offset(0.0F, 0.0F, -5.0F));
		for (int index = 0; index < 10; index++) {
			float angle = index * Mth.TWO_PI / 10.0F;
			second.addOrReplaceChild("aperture_" + index, CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.EYE_2_APERTURE_U, WorldInterfaceUv.EYE_2_APERTURE_V)
					.addBox(-1.0F, -3.2F, -0.8F, 2.0F, 6.4F, 1.6F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 5.4F, Mth.sin(angle) * 4.1F,
							-1.6F, 0.0F, 0.0F, angle));
		}

		PartDefinition third = eye.addOrReplaceChild("form_3_eye", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.EYE_3_BALL_U, WorldInterfaceUv.EYE_3_BALL_V)
				.addBox(-8.4F, -6.6F, -1.8F, 16.8F, 13.2F, 2.8F)
				.texOffs(WorldInterfaceUv.EYE_3_PUPIL_U, WorldInterfaceUv.EYE_3_PUPIL_V)
				.addBox(-3.4F, -3.4F, -2.7F, 6.8F, 6.8F, 1.5F)
				.texOffs(WorldInterfaceUv.EYE_3_SLIT_U, WorldInterfaceUv.EYE_3_SLIT_V)
				.addBox(-0.9F, -3.2F, -3.3F, 1.8F, 6.4F, 0.8F),
				PartPose.offset(0.0F, -1.0F, -7.0F));
		for (int index = 0; index < 16; index++) {
			float angle = index * Mth.TWO_PI / 16.0F;
			third.addOrReplaceChild("eye_cage_" + index, CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.EYE_3_CAGE_U, WorldInterfaceUv.EYE_3_CAGE_V)
					.addBox(-1.1F, -4.6F, -0.9F, 2.2F, 9.2F, 1.8F),
					PartPose.offsetAndRotation(Mth.cos(angle) * 7.7F, Mth.sin(angle) * 6.0F,
							-2.0F, 0.0F, 0.0F, angle));
		}
	}

	private static void buildRings(PartDefinition ring) {
		PartDefinition first = ring.addOrReplaceChild("form_1_ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, -2.5F));
		addRingSegments(first, "fragment", 8, 8.5F, 1.4F, 1.8F,
				WorldInterfaceUv.RING_FRAGMENT_U, WorldInterfaceUv.RING_FRAGMENT_V);
		PartDefinition second = ring.addOrReplaceChild("form_2_ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, -4.5F));
		addRingSegments(second, "segment", 16, 12.5F, 1.9F, 2.4F,
				WorldInterfaceUv.RING_SEGMENT_U, WorldInterfaceUv.RING_SEGMENT_V);
		PartDefinition third = ring.addOrReplaceChild("form_3_ring", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, -6.5F));
		addRingSegments(third, "outer_segment", 24, 17.5F, 2.6F, 3.2F,
				WorldInterfaceUv.RING_OUTER_U, WorldInterfaceUv.RING_OUTER_V);
		// The inner band is the only ring that lands on an emissive island: at the third form the
		// halo is large enough that lighting all of it would drown the core it is meant to frame.
		addRingSegments(third, "inner_segment", 16, 11.5F, 1.5F, 1.9F,
				WorldInterfaceUv.RING_INNER_U, WorldInterfaceUv.RING_INNER_V);
	}

	/**
	 * The halo is wreckage caught in the intake, not a machined ring, so every fragment gets its own
	 * grade and tilt. {@link #applyRingTelegraph} still steers by segment x/y, which stays intact.
	 */
	private static void addRingSegments(PartDefinition ring, String prefix, int count,
			float radius, float width, float length, int u, int v) {
		for (int index = 0; index < count; index++) {
			float angle = index * Mth.TWO_PI / count;
			float grade = 0.62F + hash(index * 13 + count) * 0.86F;
			ring.addOrReplaceChild(prefix + "_" + index, CubeListBuilder.create().texOffs(u, v)
					.addBox(-width * 0.5F * grade, -length * 0.5F * grade, -width * 0.5F * grade,
							width * grade, length * grade, width * grade),
					PartPose.offsetAndRotation(Mth.cos(angle) * radius, Mth.sin(angle) * radius,
							Mth.sin(angle * 3.0F) * 1.7F,
							hash(index * 7 + count) * 1.1F, angle * 0.6F, angle));
		}
	}

	private static void buildJaws(PartDefinition jaw) {
		jaw.addOrReplaceChild("form_1_jaw", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.JAW_1_U, WorldInterfaceUv.JAW_1_V)
				.addBox(-4.4F, 0.0F, -3.4F, 8.8F, 2.4F, 5.6F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition second = jaw.addOrReplaceChild("form_2_jaw", CubeListBuilder.create(),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		second.addOrReplaceChild("left_mandible", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.JAW_2_U, WorldInterfaceUv.JAW_2_V)
				.addBox(-6.6F, 0.0F, -4.4F, 6.0F, 3.4F, 7.6F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.18F, 0.05F));
		second.addOrReplaceChild("right_mandible", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.JAW_2_U, WorldInterfaceUv.JAW_2_V).mirror()
				.addBox(0.6F, 0.0F, -4.4F, 6.0F, 3.4F, 7.6F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.18F, -0.05F));
		PartDefinition third = jaw.addOrReplaceChild("form_3_jaw", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.JAW_3_U, WorldInterfaceUv.JAW_3_V)
				.addBox(-8.4F, 0.0F, -6.0F, 16.8F, 4.4F, 9.8F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		for (int index = 0; index < 14; index++) {
			float x = -7.2F + index * 1.1F;
			third.addOrReplaceChild("tooth_" + index, CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.TOOTH_U, WorldInterfaceUv.TOOTH_V)
					.addBox(-0.45F, 0.0F, -0.55F, 0.9F, 2.8F + index % 3 * 0.45F, 1.1F),
					PartPose.offsetAndRotation(x, 3.6F, -5.6F, indexWave(index, 0.10F), 0.0F, 0.0F));
		}
	}

	private static void buildTendrils(PartDefinition body) {
		for (int index = 0; index < 10; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			int row = index / 2;
			// Paired front to back rather than stacked in two columns: the four that survive at first
			// form still hang symmetrically instead of clustering on one flank.
			PartDefinition root = body.addOrReplaceChild("tendril_" + index, CubeListBuilder.create(),
					PartPose.offsetAndRotation(side * (3.6F + row * 0.9F), -4.0F + row * 1.7F,
							-4.4F + row * 2.8F,
							-0.16F + row * 0.09F, side * (0.12F + row * 0.13F),
							-side * (0.20F + row * 0.06F)));
			addTentacle(root, "form_1", 1, 0.85F, 9.0F + row, side);
			addTentacle(root, "form_2", 2, 1.25F, 11.5F + row * 1.1F, side);
			addTentacle(root, "form_3", 3, 1.75F, 14.0F + row * 1.4F, side);
		}
	}

	/** A limb tapers to under half its thickness across three links, so each link picks its own. */
	private static int[] tendrilUv(int form, float thick) {
		return switch (form) {
			case 1 -> WorldInterfaceUv.tendril1(thick);
			case 2 -> WorldInterfaceUv.tendril2(thick);
			default -> WorldInterfaceUv.tendril3(thick);
		};
	}

	/**
	 * A tapering three-link limb: each link hangs off the end of the last with a little more turn on
	 * it, so the tentacle curls under its own length instead of pointing out like a spike. The roll
	 * per link stays small on purpose — compounded over three links it is what decides how far the
	 * limbs splay, and a limb that ends up near horizontal is thirty blocks of nothing to cull.
	 */
	private static void addTentacle(PartDefinition root, String name, int form, float thick,
			float length, float side) {
		int[] firstUv = tendrilUv(form, thick);
		PartDefinition first = root.addOrReplaceChild(name, CubeListBuilder.create()
				.texOffs(firstUv[0], firstUv[1])
				.addBox(-thick, 0.0F, -thick, thick * 2.0F, length, thick * 2.0F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		// Nodes down the limb, on their own emissive islands so they land in the emissive pass with
		// the core. Gathered under one bone because setupAnim scales it per limb: that is what lets
		// each tentacle breathe on its own clock without costing a second model submission.
		float node = thick * 0.62F;
		int[] nodeUv = WorldInterfaceUv.tendrilGlow(node);
		int[] midNodeUv = WorldInterfaceUv.tendrilGlow(node * 0.86F);
		int[] tipNodeUv = WorldInterfaceUv.tendrilGlow(node * 0.72F);
		first.addOrReplaceChild("glow", CubeListBuilder.create()
				.texOffs(nodeUv[0], nodeUv[1])
				.addBox(-node, length * 0.28F, -node, node * 2.0F, node * 2.0F, node * 2.0F)
				.texOffs(midNodeUv[0], midNodeUv[1])
				.addBox(-node * 0.86F, length * 0.58F, -node * 0.86F,
						node * 1.72F, node * 1.72F, node * 1.72F)
				.texOffs(tipNodeUv[0], tipNodeUv[1])
				.addBox(-node * 0.72F, length * 0.86F, -node * 0.72F,
						node * 1.44F, node * 1.44F, node * 1.44F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		float midThick = thick * 0.72F;
		float midLength = length * 0.88F;
		int[] midUv = tendrilUv(form, midThick);
		PartDefinition mid = first.addOrReplaceChild("mid", CubeListBuilder.create()
				.texOffs(midUv[0], midUv[1])
				.addBox(-midThick, 0.0F, -midThick, midThick * 2.0F, midLength, midThick * 2.0F),
				PartPose.offsetAndRotation(0.0F, length - thick * 0.5F, 0.0F,
						0.40F, side * 0.16F, -side * 0.16F));
		float tipThick = thick * 0.44F;
		int[] tipUv = tendrilUv(form, tipThick);
		mid.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(tipUv[0], tipUv[1])
				.addBox(-tipThick, 0.0F, -tipThick, tipThick * 2.0F, length * 0.76F, tipThick * 2.0F),
				PartPose.offsetAndRotation(0.0F, midLength - midThick * 0.5F, 0.0F,
						0.62F, side * 0.24F, -side * 0.22F));
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
		// Published by the anatomy rather than restated here: the server stands one hit proxy on
		// each drawn limb, so the two counts have to be the same number in one place.
		int activeTendrils = WorldInterfaceAnatomy.tentacleCount(form);
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
		applySkullDrift(state, form);
		applyTendrilGlow(state, form);
		applyRingTelegraph(state, form);
		applyStructuralDamage(state, form);
	}

	/**
	 * Every limb glows on its own clock. The emissive pass can only carry one colour for the whole
	 * model, so per-limb intensity has to come from geometry instead: scaling each limb's node bone
	 * changes how much glyph it puts on screen, which reads as that tentacle brightening. Slightly
	 * different rates per index mean they never pulse in unison, and winding up an action drives all
	 * of them at once — the limbs light before the thing they light for.
	 */
	private void applyTendrilGlow(WorldInterfaceRenderState state, int form) {
		float time = state.ageInTicks;
		float charge = state.actionCharge < 0.0F ? 0.0F : Math.min(1.0F, state.actionCharge);
		float wear = 1.0F - Math.clamp(state.healthFraction, 0.0F, 1.0F);
		for (int index = 0; index < tendrilGlows.length; index++) {
			ModelPart glow = tendrilGlows[index][form];
			if (!glow.visible) continue;
			float pulse = 0.5F + 0.5F * Mth.sin(time * (0.085F + index * 0.012F) + index * 1.93F);
			// Guttering as the pool drains: the limbs stop reaching full brightness.
			float ceiling = 1.0F - wear * 0.34F;
			glow.xScale = glow.yScale = glow.zScale =
					(0.42F + pulse * 0.68F * ceiling + charge * charge * 0.95F);
		}
	}

	/**
	 * Three heads on three necks, each drifting at its own rate and converging on the target while an
	 * action winds up. None of this is keyframed: the clips own the fifteen wire bones and the skulls
	 * have to stay outside that contract, so their life comes off the clock instead.
	 */
	private void applySkullDrift(WorldInterfaceRenderState state, int form) {
		float time = state.ageInTicks;
		float reach = state.actionCharge < 0.0F ? 0.0F
				: Mth.sin(Math.min(1.0F, state.actionCharge) * Mth.HALF_PI);
		float wear = 1.0F - Math.clamp(state.healthFraction, 0.0F, 1.0F);
		ModelPart[] skulls = formSkulls[form];
		for (int index = 0; index < skulls.length; index++) {
			ModelPart skull = skulls[index];
			float phase = index * 2.1F;
			float side = index == 0 ? 0.0F : index == 1 ? 1.0F : -1.0F;
			skull.xRot += Mth.sin(time * 0.062F + phase) * 0.13F - reach * 0.34F;
			skull.yRot += Mth.cos(time * 0.048F + phase) * 0.17F + side * reach * 0.30F;
			// The looser the shell gets, the further the necks lag behind the body they hang off.
			skull.zRot += Mth.sin(time * 0.037F + phase * 1.7F) * (0.09F + wear * 0.28F);
		}
	}

	/**
	 * The ring reshapes itself while an action winds up, so what is coming is legible from the body
	 * instead of only from the HUD label. Three silhouettes, one per family of attack: a slit for
	 * anything that fires along a line, a pincer for anything that reaches out and takes hold, and
	 * a bloom for anything that goes off where it stands.
	 */
	private void applyRingTelegraph(WorldInterfaceRenderState state, int form) {
		float charge = state.actionCharge;
		if (charge < 0.0F) return;
		Telegraph shape = telegraphFor(state.actionId);
		if (shape == null) return;
		// Eased so the shape is already readable well before the attack resolves.
		float open = Mth.sin(Math.min(1.0F, charge) * Mth.HALF_PI);
		ModelPart[] segments = formRingSegments[form];
		for (int index = 0; index < segments.length; index++) {
			ModelPart segment = segments[index];
			float angle = (float) Mth.atan2(segment.y, segment.x);
			switch (shape) {
				case SLIT -> {
					// Flattened onto the firing axis: the ring becomes an aperture.
					segment.y -= segment.y * open * 0.78F;
					segment.zRot += (0.0F - segment.zRot) * open * 0.5F;
				}
				case PINCER -> {
					// Split into two arcs that pull apart, leaving an open jaw across the middle.
					float side = Mth.cos(angle) >= 0.0F ? 1.0F : -1.0F;
					segment.x += side * open * 4.6F;
					segment.y *= 1.0F - open * 0.34F;
					segment.zRot += side * open * 0.55F;
				}
				case BLOOM -> {
					float push = 1.0F + open * 0.46F;
					segment.x *= push;
					segment.y *= push;
					segment.zRot += open * (index % 2 == 0 ? 0.6F : -0.6F);
				}
			}
		}
	}

	private static Telegraph telegraphFor(int actionId) {
		return switch (actionId) {
			case 1, 8 -> Telegraph.SLIT;
			case 3, 5, 6 -> Telegraph.PINCER;
			case 2, 4, 9 -> Telegraph.BLOOM;
			default -> null;
		};
	}

	private static ModelPart[] collectSegments(ModelPart ring, String prefix, int count) {
		ModelPart[] segments = new ModelPart[count];
		for (int index = 0; index < count; index++) segments[index] = ring.getChild(prefix + "_" + index);
		return segments;
	}

	private enum Telegraph {
		SLIT,
		PINCER,
		BLOOM
	}

	/**
	 * The shell remembers what it has taken. As the virtual pool drains, the ring stops returning
	 * to true between breaths and the jaw hangs progressively out of line, so a player who never
	 * reads the boss bar can still tell how far in they are from the silhouette alone.
	 */
	private void applyStructuralDamage(WorldInterfaceRenderState state, int form) {
		float wear = 1.0F - Math.clamp(state.healthFraction, 0.0F, 1.0F);
		if (wear <= 0.001F) return;
		ModelPart ring = formRings[form];
		// Two incommensurable rates, so the wobble never settles into a readable loop.
		ring.zRot += Mth.sin(state.ageInTicks * 0.13F) * wear * 0.30F;
		ring.xRot += Mth.cos(state.ageInTicks * 0.097F) * wear * 0.22F;
		ring.y += Mth.sin(state.ageInTicks * 0.071F) * wear * 1.4F;
		// The drift itself is permanent: this term never returns to zero once health is spent.
		ring.yRot += wear * 0.55F;
		ModelPart jaw = formJaws[form];
		jaw.xRot += wear * 0.42F;
		jaw.zRot += Mth.sin(state.ageInTicks * 0.11F) * wear * 0.16F;
	}
}
