package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitStreamWindowTest {
	@Test
	void initialWindowContainsTwentyFiveUniqueColumnsCenteredOnPlayer() {
		var window = PursuitStreamWindow.centeredAt(12, -7);
		assertEquals(25, window.size());
		assertEquals(25, new HashSet<>(window).size());
		assertEquals(new PursuitStreamWindow.Column(12, -7), window.getFirst());
		assertTrue(window.contains(new PursuitStreamWindow.Column(10, -9)));
		assertTrue(window.contains(new PursuitStreamWindow.Column(14, -5)));
	}

	@Test
	void crossingOneChunkAddsOnlyOneFiveColumnStrip() {
		var before = new HashSet<>(PursuitStreamWindow.centeredAt(0, 0));
		var after = new HashSet<>(PursuitStreamWindow.centeredAt(1, 0));
		after.removeAll(before);
		assertEquals(5, after.size());
		for (int z = -2; z <= 2; z++) {
			assertTrue(after.contains(new PursuitStreamWindow.Column(3, z)));
		}
	}

	@Test
	void packedKeysRemainUniqueAcrossNegativeCoordinates() {
		var keys = new HashSet<Long>();
		for (var column : PursuitStreamWindow.centeredAt(-1, -1)) {
			assertTrue(keys.add(column.key()));
		}
		assertEquals(25, keys.size());
	}
}
