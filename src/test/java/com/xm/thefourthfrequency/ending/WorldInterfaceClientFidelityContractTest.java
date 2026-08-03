package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldInterfaceClientFidelityContractTest {
	private static final Path CLIENT = Path.of("src/client/java/com/xm/thefourthfrequency");

	@Test
	void threeFormsUseIndependentStaticTreesWithinAStablePerformanceBudget() throws Exception {
		String model = read("client_render/WorldInterfaceModel.java");
		for (int form = 1; form <= 3; form++) {
			for (String group : new String[]{"core", "eye", "ring", "jaw"}) {
				assertTrue(model.contains("\"form_" + form + "_" + group + "\""),
						"missing independent form tree: " + form + "/" + group);
			}
		}
		assertTrue(model.contains("ANIMATED_BONE_COUNT = 15"));
		assertTrue(model.contains("MAX_VISIBLE_STATIC_PARTS = 1_024"));
		assertTrue(model.contains("STATIC_PART_BUDGET = {512, 768, MAX_VISIBLE_STATIC_PARTS}"));
		String setup = model.substring(model.indexOf("public void setupAnim"));
		assertFalse(setup.contains("addOrReplaceChild"), "form geometry must remain bake-time static");
	}

	@Test
	void reusableClipsServeEveryLiveWireAction() throws Exception {
		String animations = read("client_render/WorldInterfaceAnimations.java");
		assertTrue(animations.contains("PROTOCOL_ACTION_COUNT = 13"));
		assertTrue(animations.contains("CLIPS_PER_ACTION = 3"));
		assertTrue(animations.contains("AUTHORED_CLIP_COUNT = 37"));
		assertEquals(37, count(animations, "public static final AnimationDefinition "));
		// Wire id 3 held the retired grab-slam. The table is indexed by wire id, so the slot has to
		// stay - collapsing it would silently shift every action after it onto the wrong clips.
		assertTrue(animations.contains("// 3: the grab-slam is retired"),
				"the retired wire id must keep its slot in the clip table");
		// Each attack owns an authored recovery, so the release has somewhere to settle instead of
		// the body simply stopping. The resolution and morph actions keep their original two.
		for (String composition : new String[]{
				"{LASER_AIM, LASER_APERTURE, LASER_RECOVER}", "{ORB_CHARGE, ORB_RELEASE, ORB_RECOVER}",
				"{LANCE_FOCUS, LANCE_DESCENT, LANCE_RECOVER}",
				"{WEAPON_REACH, WEAPON_HOLD, WEAPON_RECOVER}",
				"{THROW_CAPTURE, THROW_RELEASE, THROW_RECOVER}",
				"{HOTBAR_GAZE, HOTBAR_PURGE, HOTBAR_RECOVER}",
				"{TENDRIL_REAR, TENDRIL_LASH, TENDRIL_RECOVER}",
				"{EVICTION_CORRUPTION, FORCED_EXPULSION, EXPULSION_RECOVER}",
				"{SUMMON_CORE, SUMMON_LIMBS}",
				"{MORPH_SECOND_CORE, MORPH_SECOND_LIMBS}",
				"{MORPH_THIRD_CORE, MORPH_THIRD_LIMBS}",
				"{SUCCESS_COLLAPSE, SUCCESS_FADE}", "{FAILURE_BLACKEN, FAILURE_ESCAPE}"}) {
			assertTrue(animations.contains(composition), composition);
		}
	}

	@Test
	void everyWorldSpacePrimitiveSharesOneBoundedBatchAndAmbientAudioIsStoppable() throws Exception {
		String beams = read("client_render/WorldInterfaceBeamBatchRenderer.java");
		String presentation = read("client_ui/WorldInterfacePresentationController.java");
		String renderer = read("client_render/WorldInterfaceRenderer.java");
		assertTrue(beams.contains("RENDER_LAYER_COUNT = 1"));
		// The gate ring is gone: the structures stopped being built, so the shafts were twenty
		// lights standing in empty air. Nothing may put it back.
		assertFalse(beams.contains("extractGateways"));
		assertFalse(beams.contains("MAX_VERTICES_PER_GATE"));
		// The per-family allowances are headroom now, not ceilings to design against; what still
		// matters is that every family is declared and they all resolve to the one batch below.
		assertTrue(beams.contains("MAX_VERTICES_PER_TETHER = 16"));
		assertTrue(beams.contains("FULL_DETAIL_DISTANCE = 96.0D"));
		assertTrue(beams.contains("RENDER_CUTOFF_DISTANCE = 192.0D"));
		assertTrue(beams.contains("WorldRenderEvents.END_EXTRACTION.register"));
		assertTrue(beams.contains("WorldRenderEvents.BEFORE_TRANSLUCENT.register"));
		// The whole point of folding the laser, tethers and orb halos in here: still one layer.
		assertEquals(1, count(beams, "getBuffer(RenderTypes.lightning())"));
		assertTrue(beams.contains("MAX_BATCH_VERTICES = MAX_ANCHOR_TETHERS * MAX_VERTICES_PER_TETHER"),
				"every primitive family must stay inside one declared vertex budget");
		assertTrue(beams.contains("public static void resetSession()"),
				"entity references must be droppable so a disconnect cannot strand a stale level");
		assertFalse(presentation.contains("addParticle("));
		assertTrue(presentation.contains("extends AbstractTickableSoundInstance"));
		assertTrue(presentation.contains("this.looping = true"));
		assertTrue(presentation.contains("canStartSilent()"));
		assertTrue(presentation.contains("stopAmbientLoops()"));
		assertFalse(presentation.contains("AMBIENT_LOOP_TICKS"));
		assertTrue(renderer.contains("MAX_RENDER_LAYERS = 6"));
	}

	@Test
	void erosionRebuildsOnlyTheCylinderItCanTouch() throws Exception {
		String presentation = read("client_ui/WorldInterfacePresentationController.java");
		// allChanged() recreates the whole section render dispatcher; twelve of them during the
		// collapse was a visible hitch at the worst possible moment.
		assertFalse(presentation.contains("levelRenderer.allChanged()"));
		assertTrue(presentation.contains("setSectionRangeDirty("));
		// The radius is the policy's, not the renderer's. Two different numbers meant the render
		// reached four fifths further than the server ever committed, so the island healed most of
		// its damage the instant the encounter cleared.
		assertTrue(presentation.contains("EROSION_RADIUS_BLOCKS = WorldInterfacePolicy.EROSION_RADIUS_BLOCKS"),
				"render and commit must erode the same disc");
	}

	@Test
	void screenTreatmentsNeverStealAChainTheyDoNotOwn() throws Exception {
		String post = read("client_ui/WorldInterfacePostEffectController.java");
		assertTrue(post.contains("if (active != null && !isOwned(active)) return;"),
				"the pursuit chain must survive an overlapping encounter action");
		assertTrue(post.contains("clearOwned("));
		// One shared lock treatment across every action that telegraphs, rather than a chain owned
		// by a single attack: being singled out has to look the same whoever is doing the singling.
		assertTrue(post.contains("WorldInterfaceProtocol.lockWarningTicks("),
				"the screen treatment must run on the protocol's own lock clock");
		for (String effect : new String[]{"world_interface_lock", "world_interface_lock_peak",
				"world_interface_expulsion"}) {
			assertTrue(Files.exists(Path.of("src/main/resources/assets/thefourthfrequency/post_effect",
					effect + ".json")), "missing post effect: " + effect);
		}
	}

	@Test
	void escalationPaletteIsSharedRatherThanRestatedPerRenderer() throws Exception {
		String palette = read("client_render/WorldInterfacePalette.java");
		String renderer = read("client_render/WorldInterfaceRenderer.java");
		String beams = read("client_render/WorldInterfaceBeamBatchRenderer.java");
		String hud = read("client_ui/WorldInterfaceHud.java");
		assertTrue(palette.contains("PHASE_BAND_COUNT = 3"));
		for (String consumer : new String[]{renderer, beams, hud}) {
			assertTrue(consumer.contains("WorldInterfacePalette."),
					"escalation colour must come from the shared palette");
		}
	}

	@Test
	void customEncounterHudIsTheOnlyBossBar() throws Exception {
		String entity = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/entity/WorldInterfaceEntity.java"),
				StandardCharsets.UTF_8);
		String hud = read("client_ui/WorldInterfaceHud.java");
		assertFalse(entity.contains("ServerBossEvent"));
		assertFalse(entity.contains("BossEvent.BossBar"));
		assertFalse(entity.contains("startSeenByPlayer"));
		assertFalse(hud.contains("VANILLA_BOSS_BAR_CLEARANCE"));
		assertTrue(hud.contains("int top = 12"));
	}

	private static String read(String relative) throws Exception {
		return Files.readString(CLIENT.resolve(relative), StandardCharsets.UTF_8);
	}

	private static int count(String text, String needle) {
		int total = 0;
		for (int offset = 0; (offset = text.indexOf(needle, offset)) >= 0; offset += needle.length()) total++;
		return total;
	}
}
