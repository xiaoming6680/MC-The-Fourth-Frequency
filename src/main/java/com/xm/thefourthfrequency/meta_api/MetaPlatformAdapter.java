package com.xm.thefourthfrequency.meta_api;

import java.util.Set;

public interface MetaPlatformAdapter {
	Set<MetaCapability> capabilities();
	MetaExecution execute(MetaEvent event, MetaContext context) throws Exception;
	void restore() throws Exception;
}
