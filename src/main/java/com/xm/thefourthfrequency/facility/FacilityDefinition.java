package com.xm.thefourthfrequency.facility;

import java.util.List;

public record FacilityDefinition(
		String id,
		String category,
		int offsetX,
		int offsetZ,
		String yMode,
		int width,
		int length,
		int height,
		String floorBlock,
		String wallBlock,
		String roofBlock,
		String accentBlock,
		String markerBlock,
		List<String> templates,
		int clueIndex
) {
	public FacilityDefinition {
		if (id == null || !id.matches("[a-z0-9_]{3,32}")) {
			throw new IllegalArgumentException("Invalid facility id: " + id);
		}
		if (category == null || !category.matches("[a-z_]{3,32}")) {
			throw new IllegalArgumentException("Invalid facility category for " + id);
		}
		if (Math.abs(offsetX) > 512 || Math.abs(offsetZ) > 512 || Math.abs(offsetX) + Math.abs(offsetZ) < 64) {
			throw new IllegalArgumentException("Facility offset outside safe exploration band: " + id);
		}
		if (!(yMode.equals("surface") || yMode.equals("underground"))) {
			throw new IllegalArgumentException("Unknown y_mode for " + id + ": " + yMode);
		}
		if (width < 5 || width > 15 || length < 5 || length > 15 || height < 3 || height > 7) {
			throw new IllegalArgumentException("Unsafe facility dimensions: " + id);
		}
		if (clueIndex < -1 || clueIndex > 3) {
			throw new IllegalArgumentException("Invalid clue index for " + id);
		}
		if (templates == null || templates.size() != 2
				|| templates.stream().anyMatch(value -> value == null || !value.matches("facility/[a-z0-9_]{3,48}"))) {
			throw new IllegalArgumentException("Every facility requires two safe structure templates: " + id);
		}
		templates = List.copyOf(templates);
	}

	public String template(int variant) {
		return templates.get(Math.floorMod(variant, templates.size()));
	}
}
