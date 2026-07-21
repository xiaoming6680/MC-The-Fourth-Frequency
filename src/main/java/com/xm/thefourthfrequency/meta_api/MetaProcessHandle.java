package com.xm.thefourthfrequency.meta_api;

public record MetaProcessHandle(long processId, String programId, String ownershipToken) {
	public MetaProcessHandle {
		if (processId <= 0 || programId == null || !programId.matches("[a-z_]{1,24}")
				|| ownershipToken == null || ownershipToken.isBlank()) {
			throw new IllegalArgumentException("Meta process handle requires a positive owned process identity");
		}
	}
}
