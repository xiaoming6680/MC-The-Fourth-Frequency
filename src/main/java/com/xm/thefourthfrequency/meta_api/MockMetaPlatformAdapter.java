package com.xm.thefourthfrequency.meta_api;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class MockMetaPlatformAdapter implements MetaPlatformAdapter {
	private final List<MetaEvent> events = new ArrayList<>();
	private final List<MetaProcessHandle> owned = new ArrayList<>();
	private boolean fail;
	private int restoreCount;

	public MockMetaPlatformAdapter() { }
	public MockMetaPlatformAdapter(boolean fail) { this.fail = fail; }

	@Override
	public Set<MetaCapability> capabilities() {
		return EnumSet.allOf(MetaCapability.class);
	}

	@Override
	public MetaExecution execute(MetaEvent event, MetaContext context) throws Exception {
		if (fail) throw new IllegalStateException("Configured mock failure");
		events.add(event);
		MetaArtifact artifact = new MetaArtifact("mock_" + event.wireId(), MetaArtifact.Kind.DATA,
				"event_" + event.wireId() + ".dat", true);
		MetaProcessHandle handle = new MetaProcessHandle(1000L + events.size(), "mock_viewer",
				"owned-" + events.size());
		owned.add(handle);
		return new MetaExecution(event, true, false, List.of(artifact), List.of(handle));
	}

	@Override
	public void restore() {
		restoreCount++;
		owned.clear();
	}

	public List<MetaEvent> events() { return List.copyOf(events); }
	public List<MetaProcessHandle> ownedProcesses() { return List.copyOf(owned); }
	public int restoreCount() { return restoreCount; }
	public void setFail(boolean fail) { this.fail = fail; }
}
