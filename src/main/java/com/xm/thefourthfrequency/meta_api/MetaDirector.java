package com.xm.thefourthfrequency.meta_api;

import java.util.ArrayList;
import java.util.List;

public final class MetaDirector {
	private final MetaPlatformAdapter primary;
	private final MetaPlatformAdapter fallback;
	private final List<MetaEvent> history = new ArrayList<>();
	private boolean enabled;

	public MetaDirector(MetaPlatformAdapter primary, MetaPlatformAdapter fallback, boolean enabled) {
		this.primary = primary;
		this.fallback = fallback;
		this.enabled = enabled;
	}

	public MetaExecution dispatch(MetaEvent event, MetaContext context) {
		if (!enabled) return MetaExecution.disabled(event);
		history.add(event);
		try {
			return primary.execute(event, context);
		} catch (Exception primaryFailure) {
			try {
				MetaExecution fallbackResult = fallback.execute(event, context);
				return new MetaExecution(event, fallbackResult.externalEffects(), true,
						fallbackResult.artifacts(), fallbackResult.ownedProcesses());
			} catch (Exception fallbackFailure) {
				return MetaExecution.disabled(event);
			}
		}
	}

	public void restore() {
		try { primary.restore(); } catch (Exception ignored) { }
		try { fallback.restore(); } catch (Exception ignored) { }
	}

	public boolean enabled() { return enabled; }
	public void setEnabled(boolean enabled) {
		if (this.enabled && !enabled) restore();
		this.enabled = enabled;
	}
	public List<MetaEvent> history() { return List.copyOf(history); }
}
