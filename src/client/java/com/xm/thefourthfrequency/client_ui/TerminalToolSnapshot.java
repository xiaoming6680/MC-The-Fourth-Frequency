package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.TerminalToolSnapshotPayload;
import com.xm.thefourthfrequency.terminal.TerminalNavigationMath;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.terminal.TerminalStructureTarget;
import net.minecraft.network.chat.Component;

public record TerminalToolSnapshot(TerminalToolSnapshotPayload payload) {
	public TerminalToolSnapshot {
		if (payload.protocolVersion() != TerminalToolSnapshotPayload.CURRENT_PROTOCOL_VERSION) {
			throw new IllegalStateException("Terminal tool protocol mismatch: server=" + payload.protocolVersion()
					+ ", client=" + TerminalToolSnapshotPayload.CURRENT_PROTOCOL_VERSION);
		}
	}

	public static TerminalToolSnapshot empty() {
		return new TerminalToolSnapshot(new TerminalToolSnapshotPayload(
				TerminalToolSnapshotPayload.CURRENT_PROTOCOL_VERSION,
				bit(TerminalTool.HOME) | bit(TerminalTool.WEATHER),
				TerminalToolService.NO_TOOL, TerminalToolService.NO_TOOL,
				TerminalTool.WEATHER.slot(), TerminalTool.HOME.slot(), 0, 0,
				TerminalStructureTarget.NONE.wireId(), false,
				false, 0, TerminalResource.NONE.wireId(), 0, false, 0,
				0, 0L, 13_000, 0,
				false, false, false, 0, 0, 0, "",
				false, false, 0, 0, 0, "",
				0, false, 0, 0, 0,
				0, 0, 0, false, false, 0, 0, "", 0, 0));
	}

	public boolean available(TerminalTool tool) {
		return (payload.availableToolsMask() & bit(tool)) != 0;
	}

	public TerminalTool selectedTool() {
		return TerminalTool.fromSlot(payload.selectedTool());
	}

	public TerminalTool guidanceTool() {
		return TerminalTool.fromSlot(payload.guidanceTool());
	}

	public TerminalTool recommendedPrimaryTool() {
		return TerminalTool.fromSlot(payload.recommendedPrimaryTool());
	}

	public TerminalTool recommendedSecondaryTool() {
		return TerminalTool.fromSlot(payload.recommendedSecondaryTool());
	}

	public boolean resourceAvailable(TerminalResource resource) {
		return resource != TerminalResource.NONE
				&& (payload.availableResourcesMask() & 1 << resource.wireId()) != 0;
	}

	public boolean navigationTargetAvailable(TerminalStructureTarget target) {
		return target != TerminalStructureTarget.NONE
				&& (payload.navigationTargetsMask() & TerminalStructureTarget.bit(target)) != 0;
	}

	public TerminalStructureTarget selectedNavigationTarget() {
		return TerminalStructureTarget.fromWire(payload.selectedNavigationTarget());
	}

	public boolean unstableSignalAvailable() {
		return payload.unstableSignalAvailable();
	}

	public boolean toolsDisabled() {
		return payload.toolsDisabled();
	}

	public TerminalResource selectedResource() {
		return TerminalResource.fromWire(payload.selectedResource());
	}

	public boolean mineralScanning() {
		return payload.mineralScanTicks() > 0;
	}

	public int mineralScanTicks() {
		return Math.clamp(payload.mineralScanTicks(), 0, 60);
	}

	public boolean navigationCompletionAvailable() {
		return payload.navigationCompletionAvailable();
	}

	public Component navigationCompletionLine() {
		return Component.translatable("terminal.thefourthfrequency.navigation.completed",
				Component.translatable("terminal.thefourthfrequency.relative_direction."
						+ TerminalNavigationMath.relativeDirectionId(payload.navigationCompletionDirection())));
	}

	public Component disabledLine() {
		int seconds = Math.max(1, (payload.toolsDisabledTicks() + 19) / 20);
		return Component.translatable("terminal.thefourthfrequency.tool.disabled", seconds);
	}

	public Component weatherLine() {
		String weather = switch (Math.clamp(payload.weather(), 0, 2)) {
			case 1 -> "rain";
			case 2 -> "thunder";
			default -> "clear";
		};
		long dayTime = Math.floorMod(payload.dayTime(), 24_000L);
		boolean day = dayTime < 13_000L || dayTime >= 23_000L;
		Component current = Component.translatable("terminal.thefourthfrequency.tool.weather.current",
				Component.translatable("terminal.thefourthfrequency.environment.weather." + weather),
				Component.translatable("terminal.thefourthfrequency.tool.weather." + (day ? "day" : "night")));
		int minutes = Math.max(1, (payload.ticksUntilLightChange() + 1_199) / 1_200);
		return current.copy().append(Component.literal(" ")).append(Component.translatable(
				"terminal.thefourthfrequency.tool.weather.until_" + (day ? "dark" : "light"), minutes));
	}

	public Component homeLine() {
		if (!payload.homeKnown()) return Component.translatable("terminal.thefourthfrequency.tool.home.none");
		Component prefix = Component.translatable(payload.homeUsesBed()
				? "terminal.thefourthfrequency.tool.home.bed" : "terminal.thefourthfrequency.tool.home.saved");
		return prefix.copy().append(Component.literal(" ")).append(locationLine(payload.homeSameDimension(),
				payload.homeDx(), payload.homeDz(), payload.homeY(), payload.homeDimension()));
	}

	public Component portalLine() {
		if (!payload.portalKnown()) return Component.translatable("terminal.thefourthfrequency.tool.portal.none");
		return locationLine(payload.portalSameDimension(), payload.portalDx(), payload.portalDz(),
				payload.portalY(), payload.portalDimension());
	}

	public Component lockedLine(TerminalTool tool) {
		return Component.translatable("terminal.thefourthfrequency.tool." + tool.id() + ".locked");
	}

	public boolean receiverAvailable() {
		return payload.receiverAvailable();
	}

	public int receiverTarget() {
		return Math.clamp(payload.receiverTarget(), 0, 100);
	}

	public int receiverStrength() {
		return Math.clamp(payload.receiverStrength(), 0, 100);
	}

	public int receiverLockTicks() {
		return Math.clamp(payload.receiverLockTicks(), 0, 20);
	}

	public Component strongholdLine() {
		int samples = Math.max(0, payload.eyeSampleCount());
		if (samples < 2 || !payload.strongholdKnown()) return Component.translatable(
				"terminal.thefourthfrequency.tool.stronghold.samples", samples, 2);
		if (!payload.strongholdSameDimension()) return Component.translatable(
				"terminal.thefourthfrequency.tool.stronghold.other_dimension", payload.strongholdDimension());
		return Component.translatable("terminal.thefourthfrequency.tool.stronghold.estimate",
				Component.translatable("terminal.thefourthfrequency.direction."
						+ TerminalNavigationMath.direction(payload.strongholdDx(), payload.strongholdDz())),
				Math.max(0, payload.strongholdMinDistance()), Math.max(0, payload.strongholdMaxDistance()), samples);
	}

	public int playerY() {
		return payload.playerY();
	}

	private static Component locationLine(boolean sameDimension, int dx, int dz, int y, String dimension) {
		if (!sameDimension) return Component.translatable(
				"terminal.thefourthfrequency.tool.location.other_dimension", dimension);
		return Component.translatable("terminal.thefourthfrequency.tool.location.same_dimension",
				Component.translatable("terminal.thefourthfrequency.direction."
						+ TerminalNavigationMath.direction(dx, dz)),
				TerminalNavigationMath.distance(dx, dz), y);
	}

	private static int bit(TerminalTool tool) {
		return 1 << tool.slot();
	}
}
