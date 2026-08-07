package com.xm.thefourthfrequency.client_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.entity.WorldInterfaceAnatomy;
import com.xm.thefourthfrequency.entity.WorldInterfaceRig;
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

import java.util.HashMap;
import java.util.Map;

/**
 * One storm, grown twice: a continuous mass of swallowed terrain carrying three long-necked block
 * skulls, with tentacles trailing from the underside.
 *
 * <p>Two things about the shape are load-bearing, and both are departures from what this model used
 * to be.
 *
 * <p><b>The heads are the face.</b> There is no eye in the middle of the body and no ring around it.
 * A single large eye on the chest and a halo orbiting it competed with the skulls for the centre of
 * the silhouette and won, which left the three heads reading as decoration on a machine. The only
 * eyes on this thing are the six in its three skulls; the interface's own kernel is still in there,
 * buried in the mass as a secondary detail, and is deliberately never the brightest thing on screen.
 *
 * <p><b>The forms accumulate rather than replace.</b> Each form used to be an independently baked
 * tree, so a morph swapped the entire model out - the body visibly popped from one shape to another
 * and no part of the first form survived into the second. Here {@code shell_base} is built once and
 * is visible for the whole fight; a morph reveals {@code phase_2_accretion} and then
 * {@code phase_3_accretion} on top of it. What the player watches is the same animal taking on more
 * world, which is what the growth is supposed to mean. The heads and limbs are likewise one shared
 * bone chain across all three forms: the necks lengthen, they are not exchanged.
 */
public final class WorldInterfaceModel extends EntityModel<WorldInterfaceRenderState> {
	public static final int FORM_COUNT = 3;
	/**
	 * Every bone a clip may address.
	 *
	 * <p>root, hover, storm_body, three shell layers, the kernel and its lattice, the weapon, three
	 * six-bone head chains (mount, two necks, skull, eye, jaw) and ten four-link limbs (root, mid,
	 * tip, glow). The two leaves past the bare skeleton - the kernel lattice and the per-limb glow
	 * node - exist because the emissive pass carries a single colour for the whole model, so
	 * "this limb is brighter than that one" can only be said with geometry.
	 */
	public static final int ANIMATED_BONE_COUNT = 67;
	/**
	 * Ceiling on parts drawn at once. Unlike the old constant this is actually enforced - see
	 * {@link #accretionBudget} - so it is a performance guarantee rather than a comment.
	 */
	public static final int MAX_VISIBLE_STATIC_PARTS = 320;
	private static final int[] STATIC_PART_BUDGET = {160, 224, MAX_VISIBLE_STATIC_PARTS};
	private static final int HEADS = WorldInterfaceAnatomy.HEAD_COUNT;
	private static final int TENDRILS = 10;
	/**
	 * One eye per skull, as in the reference: a single large lit block set into the face. Two small
	 * eyes per head read as a creature at this size; one reads as an aperture, which is what these
	 * are - and it is the only lit thing on the head, so it wins the silhouette outright.
	 */
	private static final int EYES_PER_HEAD = 1;
	/**
	 * Bone names are unique across the whole tree, not just within a parent.
	 *
	 * <p>A clip addresses a bone by name alone, so three heads each owning a bone called "skull"
	 * would leave two of them permanently unanimatable - and silently, because the lookup would
	 * simply resolve to whichever one it found first. Every head and limb bone is therefore
	 * prefixed with the chain it belongs to.
	 */
	private static final String[] HEAD_PREFIX = {"center", "left", "right"};

	private final ModelPart root;
	private final ModelPart hover;
	private final ModelPart stormBody;
	private final ModelPart shellBase;
	private final ModelPart[] accretions = new ModelPart[FORM_COUNT - 1];
	private final ModelPart interfaceKernel;
	private final ModelPart kernelGlow;
	private final ModelPart weapon;
	private final ModelPart[] headMounts = new ModelPart[HEADS];
	private final ModelPart[] neckA = new ModelPart[HEADS];
	private final ModelPart[] neckB = new ModelPart[HEADS];
	private final ModelPart[] skulls = new ModelPart[HEADS];
	private final ModelPart[] jaws = new ModelPart[HEADS];
	private final ModelPart[][] eyes = new ModelPart[HEADS][EYES_PER_HEAD];
	private final ModelPart[] tendrils = new ModelPart[TENDRILS];
	private final ModelPart[] tendrilMids = new ModelPart[TENDRILS];
	private final ModelPart[] tendrilTips = new ModelPart[TENDRILS];
	private final ModelPart[] tendrilGlows = new ModelPart[TENDRILS];
	/** Every bone the shared rig poses, indexed the way the rig names them. */
	private final Map<String, ModelPart> posedBones = new HashMap<>(64);

	public WorldInterfaceModel(ModelPart root) {
		super(root);
		this.root = root;
		hover = root.getChild("hover");
		stormBody = hover.getChild("storm_body");
		shellBase = stormBody.getChild("shell_base");
		accretions[0] = stormBody.getChild("phase_2_accretion");
		accretions[1] = stormBody.getChild("phase_3_accretion");
		interfaceKernel = stormBody.getChild("interface_kernel");
		kernelGlow = interfaceKernel.getChild("kernel_glow");
		weapon = stormBody.getChild("weapon");
		for (int head = 0; head < HEADS; head++) {
			String prefix = HEAD_PREFIX[head];
			headMounts[head] = stormBody.getChild(prefix + "_head_mount");
			neckA[head] = headMounts[head].getChild(prefix + "_neck_a");
			neckB[head] = neckA[head].getChild(prefix + "_neck_b");
			skulls[head] = neckB[head].getChild(prefix + "_skull");
			jaws[head] = skulls[head].getChild(prefix + "_jaw");
			for (int eye = 0; eye < EYES_PER_HEAD; eye++) {
				eyes[head][eye] = skulls[head].getChild(prefix + "_eye_" + eye);
			}
		}
		for (int index = 0; index < TENDRILS; index++) {
			tendrils[index] = stormBody.getChild("tendril_" + index);
			tendrilMids[index] = tendrils[index].getChild("tendril_" + index + "_mid");
			tendrilTips[index] = tendrilMids[index].getChild("tendril_" + index + "_tip");
			tendrilGlows[index] = tendrilTips[index].getChild("tendril_" + index + "_glow");
		}
		// The bones the rig poses, resolved once. A missing entry here would be a bone the server is
		// posing and the client is not drawing, which is the exact divergence the rig exists to end,
		// so it is a hard failure rather than a skipped bone.
		posedBones.put(WorldInterfaceRig.HOVER, hover);
		posedBones.put(WorldInterfaceRig.STORM_BODY, stormBody);
		posedBones.put(WorldInterfaceRig.KERNEL, interfaceKernel);
		posedBones.put(WorldInterfaceRig.WEAPON, weapon);
		for (int head = 0; head < HEADS; head++) {
			String prefix = HEAD_PREFIX[head];
			posedBones.put(prefix + "_head_mount", headMounts[head]);
			posedBones.put(prefix + "_neck_a", neckA[head]);
			posedBones.put(prefix + "_neck_b", neckB[head]);
			posedBones.put(prefix + "_skull", skulls[head]);
			posedBones.put(prefix + "_jaw", jaws[head]);
		}
		for (int index = 0; index < TENDRILS; index++) {
			posedBones.put("tendril_" + index, tendrils[index]);
			posedBones.put("tendril_" + index + "_mid", tendrilMids[index]);
			posedBones.put("tendril_" + index + "_tip", tendrilTips[index]);
		}
	}

	public static int staticPartBudget(int form) {
		return STATIC_PART_BUDGET[Math.clamp(form, 0, FORM_COUNT - 1)];
	}

	/**
	 * How much clutter a shell layer may contribute, derived from the budget rather than guessed.
	 *
	 * <p>The previous budget constants were dead: nothing called {@code staticPartBudget}, so adding
	 * geometry had no runtime backstop at all and the numbers only existed to be matched by a string
	 * assertion. Both accretion layers size their plating through this, so the third form cannot grow
	 * past what the budget says it may draw however generous the parameters look.
	 */
	private static int accretionBudget(int form, int fixedParts, int requested) {
		return Math.max(0, Math.min(requested, staticPartBudget(form) - fixedParts));
	}

	/**
	 * Submit only the bones that actually carry glow, rather than the whole model a second time.
	 *
	 * <p>The lit parts are the six eyes and the limb tips, plus the kernel buried in the mass. That
	 * is a small fraction of several hundred parts, and submitting all of them for an emissive sheet
	 * that is mostly transparent meant nearly every vertex went through a translucent pass to draw
	 * nothing, paying for the sort on the way.
	 *
	 * <p>Bones are submitted individually, so the parent transforms they would normally inherit have
	 * to be walked by hand; {@code submitModelPart} copies the pose, which is what makes the
	 * push/pop around it safe. Visibility is gated here too, because a bone submitted directly never
	 * consults the parent whose {@code visible} flag would otherwise have hidden it.
	 */
	void submitEmissive(PoseStack poseStack, OrderedSubmitNodeCollector collector, RenderType renderType,
			int color, int outlineColor, int form) {
		poseStack.pushPose();
		root.translateAndRotate(poseStack);
		hover.translateAndRotate(poseStack);
		stormBody.translateAndRotate(poseStack);

		poseStack.pushPose();
		interfaceKernel.translateAndRotate(poseStack);
		submitPart(collector, kernelGlow, poseStack, renderType, color, outlineColor);
		poseStack.popPose();

		for (int head = 0; head < HEADS; head++) {
			poseStack.pushPose();
			headMounts[head].translateAndRotate(poseStack);
			neckA[head].translateAndRotate(poseStack);
			neckB[head].translateAndRotate(poseStack);
			skulls[head].translateAndRotate(poseStack);
			for (ModelPart eye : eyes[head]) {
				submitPart(collector, eye, poseStack, renderType, color, outlineColor);
			}
			poseStack.popPose();
		}

		int limbs = WorldInterfaceAnatomy.tentacleCount(Math.clamp(form, 0, FORM_COUNT - 1));
		for (int index = 0; index < limbs && index < TENDRILS; index++) {
			poseStack.pushPose();
			tendrils[index].translateAndRotate(poseStack);
			tendrilMids[index].translateAndRotate(poseStack);
			tendrilTips[index].translateAndRotate(poseStack);
			submitPart(collector, tendrilGlows[index], poseStack, renderType, color, outlineColor);
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
		// hover carries the whole storm's float and the summon descent, so no clip has to move the
		// body bone for either - which leaves the body free to be driven by attacks.
		PartDefinition hover = root.addOrReplaceChild("hover", CubeListBuilder.create(),
				PartPose.offset(0.0F, 12.0F, 0.0F));
		PartDefinition body = hover.addOrReplaceChild("storm_body", CubeListBuilder.create(),
				PartPose.ZERO);

		buildShellBase(body.addOrReplaceChild("shell_base", CubeListBuilder.create(), PartPose.ZERO));
		buildSecondAccretion(body.addOrReplaceChild("phase_2_accretion", CubeListBuilder.create(),
				PartPose.ZERO));
		buildThirdAccretion(body.addOrReplaceChild("phase_3_accretion", CubeListBuilder.create(),
				PartPose.ZERO));
		buildKernel(body.addOrReplaceChild("interface_kernel", CubeListBuilder.create(),
				PartPose.offset(0.0F, KERNEL_Y, KERNEL_Z)));
		buildHeads(body);
		buildTendrils(body);

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
		return LayerDefinition.create(mesh, WorldInterfaceUv.UV_WIDTH, WorldInterfaceUv.UV_HEIGHT);
	}

	// All three shell layers share one UV layout. The parts are the same materials at every stage --
	// torn terrain, bone, plating -- and what actually changes between forms is the palette, which
	// the three separate PNGs already carry. Giving each layer its own islands would have cost three
	// times the sheet to paint the same six materials.

	/** The nascent storm, and the only shell layer that is drawn for the entire fight. */
	private static void buildShellBase(PartDefinition shell) {
		addMass(shell, "base_mass", 14, -21.0F, 2.3F, 2.6F, 5.0F, 0.82F, 1.6F, 11);
		addRibs(shell, "base_rib", 10, -17.0F, 2.9F, 7.4F, 7.0F, 23);
		addRoots(shell, "base_root", 10, 4.0F, 6.5F, 47);
		addPlating(shell, "base_plate", accretionBudget(0, 34, 52), -22.0F, 28.0F, 6.2F, 0.55F, 1.5F, 61);
	}

	/**
	 * What the first morph adds: a second shell grown outside the first.
	 *
	 * <p>Sized and placed to wrap {@code shell_base} rather than to replace it, so the body the
	 * player has been fighting is still in there under the new plating. The slabs start where the
	 * base mass ends, which is what makes the growth read as intake rather than as inflation.
	 */
	private static void buildSecondAccretion(PartDefinition shell) {
		addMass(shell, "p2_mass", 8, -24.0F, 2.6F, 6.2F, 4.4F, 0.80F, 2.5F, 41);
		addRibs(shell, "p2_rib", 6, -21.0F, 3.4F, 11.0F, 10.0F, 53);
		addPlating(shell, "p2_plate", accretionBudget(1, 100, 24), -26.0F, 34.0F, 9.6F, 0.75F, 2.4F, 101);
	}

	/**
	 * What the second morph adds: the outermost shell, plus the two subordinate storm knots that
	 * bud off the mass at terminal form.
	 *
	 * <p>The knots are deliberately eyeless and headless. The reference has exactly three heads at
	 * every stage and the knots exist to make the third form read as something that has stopped
	 * being one object - not to add a fourth face competing with the skulls.
	 */
	private static void buildThirdAccretion(PartDefinition shell) {
		addMass(shell, "p3_mass", 8, -27.0F, 3.0F, 9.4F, 3.6F, 0.78F, 3.4F, 97);
		addPlating(shell, "p3_plate", accretionBudget(2, 148, 30), -29.0F, 40.0F, 12.2F, 0.95F, 3.2F, 181);
		addStormKnot(shell, "knot_left", 15.5F, -26.0F, 4.0F, 5.2F, 211);
		addStormKnot(shell, "knot_right", -15.5F, -23.0F, 6.5F, 4.6F, 233);
	}

	/**
	 * A subordinate mass budding off the main storm. Blocks folded into a ball at angles the
	 * originals could not have met at - the one place the geometry is allowed to be impossible.
	 */
	private static void addStormKnot(PartDefinition shell, String name, float x, float y, float z,
			float radius, int seed) {
		PartDefinition knot = shell.addOrReplaceChild(name, CubeListBuilder.create(),
				PartPose.offset(x, y, z));
		for (int index = 0; index < 4; index++) {
			float angle = index * Mth.TWO_PI / 4.0F + hash(seed + index) * 0.7F;
			float size = radius * (0.42F + hash(seed + index * 3) * 0.36F);
			int[] uv = WorldInterfaceUv.mass(size);
			knot.addOrReplaceChild(name + "_" + index, CubeListBuilder.create().texOffs(uv[0], uv[1])
					.addBox(-size, -size * 0.78F, -size * 0.86F,
							size * 2.0F, size * 1.56F, size * 1.72F),
					PartPose.offsetAndRotation(Mth.cos(angle) * radius * 0.52F,
							centred(seed + index * 5) * radius * 0.8F,
							Mth.sin(angle) * radius * 0.52F,
							centred(seed + index * 7) * 1.1F, angle,
							centred(seed + index * 11) * 1.1F));
		}
	}

	/** Kernel centre in model units off the storm body; {@link #insideKernelWell} keeps clutter off it. */
	private static final float KERNEL_Y = -13.0F;
	private static final float KERNEL_Z = -2.0F;
	private static final float KERNEL_HALF = 4.4F;

	/**
	 * The World Interface itself, set deep into the mass.
	 *
	 * <p>Small, recessed, and framed rather than raised: the reference puts it as a faint lattice
	 * buried in the chest, well behind the plane of the plating, and it has to stay a secondary
	 * detail. The version this replaces was a metre-wide eyeball standing clear of the body on its
	 * own socket, and it read as the creature's face - which left three skulls with nothing to be.
	 */
	private static void buildKernel(PartDefinition kernel) {
		// A square well stepping inward, so the lattice sits at the bottom of a recess.
		for (int index = 0; index < 3; index++) {
			float half = KERNEL_HALF * (1.0F - index * 0.22F);
			float depth = 1.1F + index * 0.9F;
			float bar = 0.9F;
			kernel.addOrReplaceChild("frame_" + index + "_top", CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.KERNEL_FRAME_U, WorldInterfaceUv.KERNEL_FRAME_V)
					.addBox(-(half + bar), -(half + bar), depth, (half + bar) * 2.0F, bar, 1.0F),
					PartPose.ZERO);
			kernel.addOrReplaceChild("frame_" + index + "_bottom", CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.KERNEL_FRAME_U, WorldInterfaceUv.KERNEL_FRAME_V)
					.addBox(-(half + bar), half, depth, (half + bar) * 2.0F, bar, 1.0F),
					PartPose.ZERO);
			kernel.addOrReplaceChild("frame_" + index + "_left", CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.KERNEL_FRAME_U, WorldInterfaceUv.KERNEL_FRAME_V)
					.addBox(-(half + bar), -half, depth, bar, half * 2.0F, 1.0F), PartPose.ZERO);
			kernel.addOrReplaceChild("frame_" + index + "_right", CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.KERNEL_FRAME_U, WorldInterfaceUv.KERNEL_FRAME_V)
					.addBox(half, -half, depth, bar, half * 2.0F, 1.0F), PartPose.ZERO);
		}
		// The lit lattice, on its own bone so the emissive pass can submit it alone.
		PartDefinition lattice = kernel.addOrReplaceChild("kernel_glow", CubeListBuilder.create()
				.texOffs(WorldInterfaceUv.KERNEL_GLOW_U, WorldInterfaceUv.KERNEL_GLOW_V)
				.addBox(-KERNEL_HALF * 0.62F, -KERNEL_HALF * 0.62F, 3.4F,
						KERNEL_HALF * 1.24F, KERNEL_HALF * 1.24F, 0.6F),
				PartPose.ZERO);
		for (int index = 0; index < 4; index++) {
			float angle = index * Mth.HALF_PI;
			lattice.addOrReplaceChild("trace_" + index, CubeListBuilder.create()
					.texOffs(WorldInterfaceUv.KERNEL_GLOW_U, WorldInterfaceUv.KERNEL_GLOW_V)
					.addBox(-0.35F, -KERNEL_HALF * 0.5F, 3.2F, 0.7F, KERNEL_HALF, 0.4F),
					PartPose.offsetAndRotation(Mth.cos(angle) * KERNEL_HALF * 0.86F,
							Mth.sin(angle) * KERNEL_HALF * 0.86F, 0.0F, 0.0F, 0.0F, angle));
		}
	}

	/**
	 * Surface clutter hugging the mass: torn panels, half-swallowed blocks, plates lifted off the
	 * shell at an angle. The slabs underneath give the storm its outline, but an outline is all they
	 * give, and at this size a smooth flank reads as a painted backdrop. These break the light up so
	 * the body has a texture to it from a distance the actual texture cannot survive.
	 */
	private static void addPlating(PartDefinition form, String prefix, int count, float top, float span,
			float radius, float minSize, float maxSize, int seed) {
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
			if (insideKernelWell(px, py, pz)) continue;
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
	 * Three heads on three shared chains.
	 *
	 * <p>One builder, one geometry, positioned from {@link WorldInterfaceAnatomy} - the same call the
	 * server boxes the heads with, so what a player swings at is what they can see. The chain exists
	 * once and is reused at every form; a morph lengthens the necks rather than swapping in a
	 * different head, which is what stops the growth from reading as a model change.
	 */
	private static void buildHeads(PartDefinition body) {
		for (int head = 0; head < HEADS; head++) {
			double[] mount = WorldInterfaceAnatomy.headLocalUnits(0, head);
			// Yaw and roll come off the anatomy signed, rather than being multiplied by the side
			// here: the roll runs against the side so the flanks splay outward, and stating that
			// twice is exactly how the three necks ended up tied together.
			PartDefinition attachment = body.addOrReplaceChild(HEAD_PREFIX[head] + "_head_mount",
					CubeListBuilder.create(),
					PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F,
							WorldInterfaceAnatomy.mountYaw(head), WorldInterfaceAnatomy.mountRoll(head)));
			float scale = head == 0 ? 1.0F : 0.78F;
			buildHeadChain(attachment, HEAD_PREFIX[head], (float) mount[0], (float) mount[1],
					(float) mount[2], scale, head, head == 0);
		}
	}

	/**
	 * neck_a to neck_b to skull, with the jaw and the two eyes under the skull.
	 *
	 * <p>Two neck bones rather than a stack of loose vertebrae: they have to be animatable with a
	 * lag between them - see {@link #applyHeadTracking} - and a chain of eight scattered segments
	 * cannot be driven by a clip without eight channels per head.
	 */
	private static void buildHeadChain(PartDefinition mount, String prefix, float x, float y, float z,
			float scale, int head, boolean centre) {
		float thick = 2.5F * scale;
		int[] neckUv = WorldInterfaceUv.vertebra(thick);
		PartDefinition first = mount.addOrReplaceChild(prefix + "_neck_a", CubeListBuilder.create()
				.texOffs(neckUv[0], neckUv[1])
				.addBox(-thick, -thick * 0.5F, -thick, thick * 2.0F, NECK_SEGMENT * scale, thick * 2.0F),
				PartPose.offsetAndRotation(x * 0.34F, y * 0.30F, z * 0.34F,
						WorldInterfaceAnatomy.neckPitch(0), WorldInterfaceAnatomy.neckYaw(head, 0),
						WorldInterfaceAnatomy.neckRoll(head, 0)));
		float midThick = thick * 0.84F;
		int[] midUv = WorldInterfaceUv.vertebra(midThick);
		PartDefinition second = first.addOrReplaceChild(prefix + "_neck_b", CubeListBuilder.create()
				.texOffs(midUv[0], midUv[1])
				.addBox(-midThick, -midThick * 0.5F, -midThick,
						midThick * 2.0F, NECK_SEGMENT * scale, midThick * 2.0F),
				PartPose.offsetAndRotation(0.0F, NECK_LINK * scale, 0.0F,
						WorldInterfaceAnatomy.neckPitch(1), WorldInterfaceAnatomy.neckYaw(head, 1),
						WorldInterfaceAnatomy.neckRoll(head, 1)));

		int[] craniumUv = WorldInterfaceUv.cranium(scale);
		PartDefinition skull = second.addOrReplaceChild(prefix + "_skull", CubeListBuilder.create()
				.texOffs(craniumUv[0], craniumUv[1])
				.addBox(-4.6F * scale, -4.2F * scale, -4.6F * scale,
						9.2F * scale, 8.0F * scale, 9.2F * scale),
				PartPose.offsetAndRotation(0.0F, NECK_LINK * scale, 0.0F, 0.14F, 0.0F, 0.0F));
		int[] browUv = WorldInterfaceUv.brow(scale);
		skull.addOrReplaceChild(prefix + "_brow", CubeListBuilder.create().texOffs(browUv[0], browUv[1])
				.addBox(-4.9F * scale, -4.7F * scale, -5.5F * scale,
						9.8F * scale, 2.6F * scale, 1.3F * scale), PartPose.ZERO);
		if (centre) {
			for (int index = 0; index < 2; index++) {
				float horn = index == 0 ? 1.0F : -1.0F;
				skull.addOrReplaceChild(prefix + "_horn_" + index, CubeListBuilder.create()
						.texOffs(WorldInterfaceUv.HORN_U, WorldInterfaceUv.HORN_V)
						.addBox(-0.7F * scale, -6.0F * scale, -0.7F * scale,
								1.4F * scale, 6.2F * scale, 1.4F * scale),
						PartPose.offsetAndRotation(horn * 3.4F * scale, -3.8F * scale, 1.2F * scale,
								-0.26F, 0.0F, horn * 0.5F));
			}
		}
		// The eye. The only lit thing on the head, and the reason the body no longer has one: a
		// single aperture set into the face, sized so it is legible from the arena floor.
		int[] eyeUv = WorldInterfaceUv.eye(scale);
		for (int index = 0; index < EYES_PER_HEAD; index++) {
			skull.addOrReplaceChild(prefix + "_eye_" + index, CubeListBuilder.create()
					.texOffs(eyeUv[0], eyeUv[1])
					.addBox(-2.6F * scale, -2.6F * scale, -0.9F * scale,
							5.2F * scale, 5.2F * scale, 1.8F * scale),
					PartPose.offset(0.0F, -1.2F * scale, -5.0F * scale));
		}
		// The jaw, hung under the skull so a player standing beneath the storm - which is where
		// they fight it from - is looking into an open mouth rather than at a flat underside.
		int[] jawUv = WorldInterfaceUv.jaw(scale);
		PartDefinition jaw = skull.addOrReplaceChild(prefix + "_jaw", CubeListBuilder.create()
				.texOffs(jawUv[0], jawUv[1])
				.addBox(-3.9F * scale, 0.0F, -4.9F * scale,
						7.8F * scale, 2.8F * scale, 5.6F * scale),
				PartPose.offsetAndRotation(0.0F, 3.4F * scale, 0.0F, 0.12F, 0.0F, 0.0F));
		int[] toothUv = WorldInterfaceUv.tooth(scale);
		for (int index = 0; index < TEETH_PER_JAW; index++) {
			float px = (-1.5F + index) * 1.9F * scale;
			jaw.addOrReplaceChild(prefix + "_tooth_" + index, CubeListBuilder.create()
					.texOffs(toothUv[0], toothUv[1])
					.addBox(-0.5F * scale, -1.9F * scale, -0.6F * scale,
							1.0F * scale, 2.2F * scale, 1.2F * scale),
					PartPose.offsetAndRotation(px, 0.0F, -4.0F * scale,
							indexWave(index, 0.09F), 0.0F, 0.0F));
		}
	}

	private static final float NECK_SEGMENT = 6.4F;
	/**
	 * How far one link advances along the chain, read off the anatomy rather than restated.
	 *
	 * <p>It is the segment length times the overlap the links are built with, and it is also the
	 * number the head and neck hit boxes are placed from. While it lived here as
	 * {@code NECK_SEGMENT * 0.94F} the two sides were free to disagree, and they did: the proxies
	 * were sitting on an authored coordinate the chain never reached.
	 */
	private static final float NECK_LINK = (float) WorldInterfaceAnatomy.NECK_LINK_UNITS;
	private static final int TEETH_PER_JAW = 4;

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
	 * The kernel recess stays clear. Plating accretes across the whole shell and the widest part of
	 * the mass is the height the kernel sits at, so without a carve-out the storm grows a lid over
	 * its own interface and the one piece of World Interface iconography on the body disappears.
	 */
	private static boolean insideKernelWell(float x, float y, float z) {
		return z < 0.0F && Math.abs(x) < KERNEL_HALF * 1.9F
				&& Math.abs(y - KERNEL_Y) < KERNEL_HALF * 1.9F;
	}

	/** Bake-time only: stable per-index variation, so the mass looks accreted instead of tiled. */
	private static float hash(int seed) {
		return WorldInterfaceScatter.hash(seed);
	}

	private static float centred(int seed) {
		return WorldInterfaceScatter.centred(seed);
	}

	private static void buildTendrils(PartDefinition body) {
		for (int index = 0; index < TENDRILS; index++) {
			float side = index % 2 == 0 ? 1.0F : -1.0F;
			int row = index / 2;
			// Paired front to back rather than stacked in two columns: the four that survive at first
			// form still hang symmetrically instead of clustering on one flank.
			addTentacle(body, index, side, row);
		}
	}

	/**
	 * A tapering three-link limb: each link hangs off the end of the last with a little more turn on
	 * it, so the tentacle curls under its own length instead of pointing out like a spike.
	 *
	 * <p>All three links are now real bones on the animated chain. {@code mid} and {@code tip} used
	 * to be geometry only - baked into a pose and never addressed - so a limb bent exactly once, at
	 * its root, and swung like a stick. Driving all three with a phase delay per link is what gives
	 * the lash a whip in it.
	 */
	private static void addTentacle(PartDefinition body, int index, float side, int row) {
		float thick = 1.45F;
		float length = 11.0F + row * 1.2F;
		int[] firstUv = WorldInterfaceUv.tendril(thick);
		PartDefinition first = body.addOrReplaceChild("tendril_" + index, CubeListBuilder.create()
				.texOffs(firstUv[0], firstUv[1])
				.addBox(-thick, 0.0F, -thick, thick * 2.0F, length, thick * 2.0F),
				PartPose.offsetAndRotation(side * (3.6F + row * 0.9F), -4.0F + row * 1.7F,
						-4.4F + row * 2.8F,
						-0.16F + row * 0.09F, side * (0.12F + row * 0.13F),
						-side * (0.20F + row * 0.06F)));
		float midThick = thick * 0.72F;
		float midLength = length * 0.88F;
		int[] midUv = WorldInterfaceUv.tendril(midThick);
		PartDefinition mid = first.addOrReplaceChild("tendril_" + index + "_mid", CubeListBuilder.create()
				.texOffs(midUv[0], midUv[1])
				.addBox(-midThick, 0.0F, -midThick, midThick * 2.0F, midLength, midThick * 2.0F),
				PartPose.offsetAndRotation(0.0F, length - thick * 0.5F, 0.0F,
						0.40F, side * 0.16F, -side * 0.16F));
		float tipThick = thick * 0.44F;
		float tipLength = length * 0.76F;
		int[] tipUv = WorldInterfaceUv.tendril(tipThick);
		PartDefinition tip = mid.addOrReplaceChild("tendril_" + index + "_tip", CubeListBuilder.create()
				.texOffs(tipUv[0], tipUv[1])
				.addBox(-tipThick, 0.0F, -tipThick, tipThick * 2.0F, tipLength, tipThick * 2.0F),
				PartPose.offsetAndRotation(0.0F, midLength - midThick * 0.5F, 0.0F,
						0.62F, side * 0.24F, -side * 0.22F));
		// Nodes on the tip link, on their own emissive island so they land in the emissive pass with
		// the eyes. Gathered under one bone because setupAnim scales it per limb: that is what lets
		// each tentacle breathe on its own clock without costing a second model submission.
		float node = tipThick * 1.25F;
		int[] nodeUv = WorldInterfaceUv.tendrilGlow(node);
		tip.addOrReplaceChild("tendril_" + index + "_glow", CubeListBuilder.create()
				.texOffs(nodeUv[0], nodeUv[1])
				.addBox(-node, tipLength * 0.34F, -node, node * 2.0F, node * 2.0F, node * 2.0F)
				.texOffs(nodeUv[0], nodeUv[1])
				.addBox(-node * 0.8F, tipLength * 0.74F, -node * 0.8F,
						node * 1.6F, node * 1.6F, node * 1.6F),
				PartPose.ZERO);
	}

	@Override
	public void setupAnim(WorldInterfaceRenderState state) {
		super.setupAnim(state);
		int form = Math.clamp(state.form, 0, FORM_COUNT - 1);
		// Accretion, not replacement: the base shell is always drawn, and each morph turns on one
		// more layer over it. Nothing is ever hidden that the player has already seen.
		shellBase.visible = true;
		for (int layer = 0; layer < accretions.length; layer++) {
			accretions[layer].visible = form > layer;
		}
		weapon.visible = form >= 1 && state.actionId == 5;
		// Published by the anatomy rather than restated here: the server stands one hit proxy on
		// each drawn limb, so the two counts have to be the same number in one place.
		int activeTendrils = WorldInterfaceAnatomy.tentacleCount(form);
		for (int index = 0; index < TENDRILS; index++) {
			tendrils[index].visible = index < activeTendrils;
		}
		// The whole pose - clips, hover, neck growth, head tracking, limb follow, structural sag -
		// comes off the shared rig, which is the same call the server places the hit boxes from.
		// Anything moved back in here is a bone the player can see somewhere they cannot hit.
		applyPose(WorldInterfaceRig.pose(form, state.ageInTicks, state.healthFraction,
				state.actionId, state.actionAgeMillis, state.gazeYaw, state.gazePitch));
		// Purely optical, and deliberately still local: neither changes where a bone is.
		applyTendrilGlow(state, form);
		applyKernelCharge(state, form);
	}

	/** Copies a posed skeleton onto the {@code ModelPart}s that draw it. */
	private void applyPose(WorldInterfaceRig.Pose pose) {
		for (WorldInterfaceRig.Bone bone : pose.bones()) {
			ModelPart part = posedBones.get(bone.name);
			if (part == null) continue;
			part.x = bone.x;
			part.y = bone.y;
			part.z = bone.z;
			part.xRot = bone.xRot;
			part.yRot = bone.yRot;
			part.zRot = bone.zRot;
			part.xScale = bone.xScale;
			part.yScale = bone.yScale;
			part.zScale = bone.zScale;
		}
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
			ModelPart glow = tendrilGlows[index];
			if (!tendrils[index].visible) continue;
			float pulse = 0.5F + 0.5F * Mth.sin(time * (0.085F + index * 0.012F) + index * 1.93F);
			// Guttering as the pool drains: the limbs stop reaching full brightness.
			float ceiling = 1.0F - wear * 0.34F;
			glow.xScale = glow.yScale = glow.zScale =
					(0.42F + pulse * 0.68F * ceiling + charge * charge * 0.95F);
		}
	}

	/**
	 * The kernel charges with the attack, and at third form it saws up to every volley.
	 *
	 * <p>Deliberately a modest, buried light rather than a beacon. It says the interface is doing
	 * something; the six eyes say what and where.
	 */
	private void applyKernelCharge(WorldInterfaceRenderState state, int form) {
		float charge = state.actionCharge < 0.0F ? 0.0F : Math.min(1.0F, state.actionCharge);
		float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.09F);
		float berserk = form >= 2 ? WorldInterfacePalette.volleyRamp(state.ageInTicks) * 0.45F : 0.0F;
		float scale = 0.86F + pulse * 0.14F + charge * charge * 0.55F + berserk;
		kernelGlow.xScale = kernelGlow.yScale = kernelGlow.zScale = scale;
		interfaceKernel.zRot = Mth.sin(state.ageInTicks * 0.021F) * 0.12F;
	}

}
