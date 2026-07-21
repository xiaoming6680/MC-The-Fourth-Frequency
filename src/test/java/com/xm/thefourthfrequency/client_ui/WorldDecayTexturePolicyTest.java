package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldDecayTexturePolicyTest {
	@Test
	void entityTexturesAlwaysUseTheSelectedResourceStack() {
		String[] representativeEntities = {
				"textures/entity/zombie/zombie.png",
				"textures/entity/horse/horse_white.png",
				"textures/entity/horse/horse_brown.png",
				"textures/entity/projectiles/arrow.png",
				"textures/entity/projectiles/tipped_arrow.png"
		};
		for (int stage = 1; stage <= 5; stage++) {
			for (String path : representativeEntities) {
				assertFalse(WorldDecayTexturePolicy.shouldCorrupt(stage, "minecraft", path),
						"entity texture must fall back through the active resource packs: " + path);
			}
		}
	}

	@Test
	void nonEntityTextureDecayRemainsAvailable() {
		assertFalse(WorldDecayTexturePolicy.shouldCorrupt(0, "minecraft",
				"textures/particle/generic_0.png"));
		assertTrue(WorldDecayTexturePolicy.shouldCorrupt(5, "minecraft",
				"textures/particle/generic_0.png"));
		assertFalse(WorldDecayTexturePolicy.shouldCorrupt(5, "thefourthfrequency",
				"textures/particle/generic_0.png"));
	}
}
