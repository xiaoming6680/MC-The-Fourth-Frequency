package com.xm.thefourthfrequency.meta_api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetaDirectorTest {
	private static final MetaContext CONTEXT = new MetaContext("test-world", 12L, 34L);

	@Test
	void wireProtocolAcceptsOnlyFixedEventIds() {
		for (MetaEvent event : MetaEvent.values()) {
			assertEquals(event, MetaEvent.fromWireId(event.wireId()));
		}
		assertThrows(IllegalArgumentException.class, () -> MetaEvent.fromWireId(-1));
		assertThrows(IllegalArgumentException.class, () -> MetaEvent.fromWireId(99));
	}

	@Test
	void disabledMasterSwitchProducesNoEffectsOrHistory() {
		MockMetaPlatformAdapter primary = new MockMetaPlatformAdapter();
		MetaDirector director = new MetaDirector(primary, new MockMetaPlatformAdapter(), false);
		MetaExecution result = director.dispatch(MetaEvent.FINAL_BODY_AWAKENED, CONTEXT);
		assertFalse(result.externalEffects());
		assertTrue(primary.events().isEmpty());
		assertTrue(director.history().isEmpty());
	}

	@Test
	void primaryFailureUsesDegradedFallback() {
		MockMetaPlatformAdapter primary = new MockMetaPlatformAdapter(true);
		MockMetaPlatformAdapter fallback = new MockMetaPlatformAdapter();
		MetaDirector director = new MetaDirector(primary, fallback, true);
		MetaExecution result = director.dispatch(MetaEvent.TERMINAL_CAPTURED, CONTEXT);
		assertTrue(result.degraded());
		assertEquals(java.util.List.of(MetaEvent.TERMINAL_CAPTURED), fallback.events());
	}

	@Test
	void disablingRestoresAndClosesOnlyAdapterOwnedHandles() {
		MockMetaPlatformAdapter primary = new MockMetaPlatformAdapter();
		MockMetaPlatformAdapter fallback = new MockMetaPlatformAdapter();
		MetaDirector director = new MetaDirector(primary, fallback, true);
		director.dispatch(MetaEvent.FINAL_BODY_AWAKENED, CONTEXT);
		assertEquals(1, primary.ownedProcesses().size());
		director.setEnabled(false);
		assertTrue(primary.ownedProcesses().isEmpty());
		assertEquals(1, primary.restoreCount());
		assertEquals(1, fallback.restoreCount());
	}

	@Test
	void artifactNamesCannotEscapeOwnedDirectory() {
		assertThrows(IllegalArgumentException.class, () -> new MetaArtifact(
				"escape", MetaArtifact.Kind.TEXT, "../outside.txt", true));
		assertThrows(IllegalArgumentException.class, () -> new MetaArtifact(
				"escape", MetaArtifact.Kind.TEXT, "folder\\outside.txt", true));
	}
}
