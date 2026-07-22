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
		assertTrue(model.contains("MAX_VISIBLE_STATIC_PARTS = 208"));
		assertTrue(model.contains("STATIC_PART_BUDGET = {64, 128, MAX_VISIBLE_STATIC_PARTS}"));
		String setup = model.substring(model.indexOf("public void setupAnim"));
		assertFalse(setup.contains("addOrReplaceChild"), "form geometry must remain bake-time static");
	}

	@Test
	void thirtyOneReusableClipsServeAllFourteenWireActions() throws Exception {
		String animations = read("client_render/WorldInterfaceAnimations.java");
		assertTrue(animations.contains("PROTOCOL_ACTION_COUNT = 14"));
		assertTrue(animations.contains("CLIPS_PER_ACTION = 2"));
		assertTrue(animations.contains("AUTHORED_CLIP_COUNT = 31"));
		assertEquals(31, count(animations, "public static final AnimationDefinition "));
		for (String pair : new String[]{
				"{LASER_AIM, LASER_APERTURE}", "{ORB_CHARGE, ORB_RELEASE}",
				"{GRAB_REACH, GRAB_SLAM}", "{MENTAL_FOCUS, MENTAL_DISTORTION}",
				"{WEAPON_REACH, WEAPON_HOLD}", "{THROW_CAPTURE, THROW_RELEASE}",
				"{HOTBAR_GAZE, HOTBAR_PURGE}", "{ARROW_CATCH, ARROW_REFLECTION}",
				"{EVICTION_CORRUPTION, FORCED_EXPULSION}", "{SUMMON_CORE, SUMMON_LIMBS}",
				"{MORPH_SECOND_CORE, MORPH_SECOND_LIMBS}",
				"{MORPH_THIRD_CORE, MORPH_THIRD_LIMBS}",
				"{SUCCESS_COLLAPSE, SUCCESS_FADE}", "{FAILURE_BLACKEN, FAILURE_ESCAPE}"}) {
			assertTrue(animations.contains(pair), pair);
		}
	}

	@Test
	void gatewaysUseOneBoundedWorldBatchAndAmbientAudioIsStoppable() throws Exception {
		String gateway = read("client_render/WorldInterfaceGatewayBatchRenderer.java");
		String presentation = read("client_ui/WorldInterfacePresentationController.java");
		String renderer = read("client_render/WorldInterfaceRenderer.java");
		assertTrue(gateway.contains("MAX_GATEWAYS = WorldInterfaceProtocol.MAX_GATEWAYS"));
		assertTrue(gateway.contains("RENDER_LAYER_COUNT = 1"));
		assertTrue(gateway.contains("MAX_VERTICES_PER_GATE = 16"));
		assertTrue(gateway.contains("FULL_DETAIL_DISTANCE = 36.0D"));
		assertTrue(gateway.contains("RENDER_CUTOFF_DISTANCE = 112.0D"));
		assertTrue(gateway.contains("WorldRenderEvents.END_EXTRACTION.register"));
		assertTrue(gateway.contains("WorldRenderEvents.BEFORE_TRANSLUCENT.register"));
		assertEquals(1, count(gateway, "getBuffer(RenderTypes.lightning())"));
		assertTrue(gateway.contains("deltaX * deltaX + deltaZ * deltaZ"),
				"gateway LOD must use horizontal distance so the radius-96 ring remains visible");
		assertFalse(presentation.contains("addParticle("));
		assertTrue(presentation.contains("extends AbstractTickableSoundInstance"));
		assertTrue(presentation.contains("this.looping = true"));
		assertTrue(presentation.contains("canStartSilent()"));
		assertTrue(presentation.contains("stopAmbientLoops()"));
		assertFalse(presentation.contains("AMBIENT_LOOP_TICKS"));
		assertTrue(renderer.contains("MAX_RENDER_LAYERS = 2"));
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
