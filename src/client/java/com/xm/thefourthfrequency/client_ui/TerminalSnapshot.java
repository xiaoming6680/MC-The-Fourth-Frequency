package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.narrative.WitnessArchive;
import com.xm.thefourthfrequency.narrative.HiddenFilePolicy;
import com.xm.thefourthfrequency.networking.TerminalNavigationPayload;
import com.xm.thefourthfrequency.networking.TerminalSnapshotPayload;
import com.xm.thefourthfrequency.networking.TerminalLogEntryPayload;
import com.xm.thefourthfrequency.networking.TerminalFilePayload;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalRecordPolicy;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.terminal.TerminalNavigationMath;
import com.xm.thefourthfrequency.narrative.NarrativeFileCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record TerminalSnapshot(TerminalSnapshotPayload payload) {

	public TerminalSnapshot {
		if (payload.protocolVersion() != TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION) {
			throw new IllegalStateException("Terminal protocol mismatch: server=" + payload.protocolVersion()
					+ ", client=" + TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION);
		}
	}

	public int mode() { return Math.clamp(payload.mode(), 0, 1); }
	public int initialPage() { return Math.clamp(payload.initialPage(), 0, 3); }
	public int tuning() { return Math.clamp(payload.tuning(), 0, 100); }
	public int visualStage() { return Math.clamp(payload.visualStage(), 0, 2); }
	public int bandStage() { return Math.clamp(payload.bandStage(), 0, 3); }
	public boolean localFileUnlocked() { return payload.localFileUnlocked(); }
	public int unreadCount() { return Math.max(0, payload.unreadCount()); }
	public int unreadFileCount() { return Math.max(0, payload.unreadFileCount()); }
	public String objectiveId() { return payload.objectiveId(); }
	public int objectiveProgress() { return Math.max(0, payload.objectiveProgress()); }
	public int objectiveTarget() { return Math.max(0, payload.objectiveTarget()); }
	public int objectiveIndex() { return Math.max(0, payload.objectiveIndex()); }
	public boolean objectiveClaimable() { return payload.objectiveClaimable(); }
	public int objectiveRewardCount() { return Math.max(0, payload.objectiveRewardCount()); }
	public double objectiveFraction() {
		return objectiveTarget() <= 0 ? 0.0D : Math.clamp(objectiveProgress() / (double) objectiveTarget(), 0.0D, 1.0D);
	}
	public ItemStack objectiveReward() {
		Identifier id = Identifier.tryParse(payload.objectiveRewardItem());
		Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR || objectiveRewardCount() <= 0) return ItemStack.EMPTY;
		return new ItemStack(item, Math.min(objectiveRewardCount(), item.getDefaultMaxStackSize()));
	}
	public int portalTransitions() { return Math.max(0, payload.portalTransitions()); }
	public Component objectiveLine() {
		return Component.translatable("terminal.thefourthfrequency.objective." + payload.objectiveId(),
				payload.objectiveProgress(), payload.objectiveTarget());
	}
	public List<TerminalLogEntryPayload> signalToolEntries() {
		return payload.signalEvents().stream().filter(entry -> entry.band() == SignalBand.UNKNOWN.wireId()
				|| entry.type().startsWith("fragment_")).toList();
	}
	public List<TerminalLogEntryPayload> recordEntries() {
		Set<Integer> seenCandidateLocations = new HashSet<>();
		return payload.signalEvents().stream()
				.filter(entry -> TerminalRecordPolicy.visibleInRecords(entry.type()))
				.filter(entry -> !entry.type().startsWith("fragment_candidate_")
						|| seenCandidateLocations.add(entry.variant()))
				.toList();
	}
	public Component latestSignalEvent() {
		List<TerminalLogEntryPayload> entries = recordEntries();
		if (entries.isEmpty()) return Component.translatable("terminal.thefourthfrequency.home.no_recent");
		TerminalLogEntryPayload latest = entries.getFirst();
		return Component.literal("[" + signalTime(latest) + "] ").append(signalEvent(latest));
	}
	public Component weatherLine() {
		int weather = 0;
		for (TerminalLogEntryPayload entry : payload.signalEvents()) {
			if (entry.type().equals("weather_changed")) weather = Math.clamp(entry.variant(), 0, 2);
		}
		String weatherId = switch (weather) {
			case 1 -> "rain";
			case 2 -> "thunder";
			default -> "clear";
		};
		long dayTime = Math.floorMod(payload.gameTime(), 24000L);
		String light = dayTime < 13000L || dayTime >= 23000L ? "day" : "night";
		return Component.translatable("terminal.thefourthfrequency.tool.weather.current",
				Component.translatable("terminal.thefourthfrequency.environment.weather." + weatherId),
				Component.translatable("terminal.thefourthfrequency.tool.weather." + light));
	}
	public List<TerminalFilePayload> files() { return payload.files(); }
	public List<TerminalFilePayload> directoryFiles() {
		return payload.files();
	}
	public TerminalFilePayload fragmentFile(int index) {
		if (index < 0 || index >= HiddenFilePolicy.FILE_COUNT) return null;
		String id = HiddenFilePolicy.fileId(index);
		return payload.files().stream().filter(file -> file.id().equals(id)).findFirst().orElse(null);
	}
	public boolean fragmentReceived(int index) { return fragmentFile(index) != null; }
	public int discoveredHiddenFileCount() {
		return (int) payload.files().stream().filter(file -> HiddenFilePolicy.isHiddenFile(file.id())).count();
	}
	public int readHiddenFileCount() {
		return (int) payload.files().stream()
				.filter(file -> HiddenFilePolicy.isHiddenFile(file.id()) && file.read()).count();
	}
	public int hiddenFileReadPercent() { return readHiddenFileCount() * 100 / HiddenFilePolicy.FILE_COUNT; }
	public Component navigationLine(TerminalNavigationPayload navigation, int playerY) {
		if (navigation.targetKind() == TerminalNavigationPayload.UNSTABLE_SIGNAL) {
			if (!navigation.located()) return Component.translatable("terminal.thefourthfrequency.navigation.fragment.scanning");
			if (!navigation.navigable()) return Component.translatable(
					"terminal.thefourthfrequency.navigation.fragment.unavailable", navigation.targetY());
			return Component.translatable("terminal.thefourthfrequency.navigation.fragment.located",
					Component.translatable("terminal.thefourthfrequency.direction."
							+ direction(navigation.targetDx(), navigation.targetDz())),
					distance(navigation.targetDx(), navigation.targetDz()));
		}
		if (navigation.targetKind() == TerminalNavigationPayload.HOME
				|| navigation.targetKind() == TerminalNavigationPayload.PORTAL) {
			String id = navigation.targetKind() == TerminalNavigationPayload.HOME ? "home" : "portal";
			if (!navigation.located()) return Component.translatable(
					"terminal.thefourthfrequency.navigation." + id + ".missing");
			if (!navigation.navigable()) return Component.translatable(
					"terminal.thefourthfrequency.navigation." + id + ".other_dimension");
			return Component.translatable("terminal.thefourthfrequency.navigation." + id + ".located",
					Component.translatable("terminal.thefourthfrequency.direction."
							+ direction(navigation.targetDx(), navigation.targetDz())),
					distance(navigation.targetDx(), navigation.targetDz()));
		}
		if (navigation.targetKind() == TerminalNavigationPayload.STRONGHOLD) {
			if (!navigation.located()) return Component.translatable(
					"terminal.thefourthfrequency.navigation.stronghold.missing");
			if (!navigation.navigable()) return Component.translatable(
					"terminal.thefourthfrequency.navigation.stronghold.other_dimension");
			return Component.translatable("terminal.thefourthfrequency.navigation.stronghold.located",
					Component.translatable("terminal.thefourthfrequency.direction."
							+ direction(navigation.targetDx(), navigation.targetDz())));
		}
		if (navigation.targetKind() >= TerminalNavigationPayload.VILLAGE
				&& navigation.targetKind() <= TerminalNavigationPayload.BASTION) {
			String id = switch (navigation.targetKind()) {
				case TerminalNavigationPayload.VILLAGE -> "village";
				case TerminalNavigationPayload.RUINED_PORTAL -> "ruined_portal";
				case TerminalNavigationPayload.MINESHAFT -> "mineshaft";
				case TerminalNavigationPayload.TRIAL_CHAMBERS -> "trial_chambers";
				case TerminalNavigationPayload.FORTRESS -> "fortress";
				default -> "bastion";
			};
			Component target = Component.translatable("terminal.thefourthfrequency.navigation.target." + id);
			if (!navigation.located()) return Component.translatable(
					"terminal.thefourthfrequency.navigation.structure.scanning", target);
			if (!navigation.navigable()) return Component.translatable(
					"terminal.thefourthfrequency.navigation.structure.unavailable", target);
			return Component.translatable("terminal.thefourthfrequency.navigation.structure.located", target,
					Component.translatable("terminal.thefourthfrequency.direction."
							+ direction(navigation.targetDx(), navigation.targetDz())),
					distance(navigation.targetDx(), navigation.targetDz()), navigation.targetY() - playerY);
		}
		String target = switch (navigation.targetKind()) {
			case TerminalNavigationPayload.IRON -> "iron";
			case TerminalNavigationPayload.COAL -> "coal";
			case TerminalNavigationPayload.GOLD -> "gold";
			case TerminalNavigationPayload.DIAMOND -> "diamond";
			default -> "unresolved";
		};
		Component mineral = Component.translatable("terminal.thefourthfrequency.resource." + target);
		if (!navigation.located()) return Component.translatable(
				"terminal.thefourthfrequency.navigation.scanning", mineral);
		if (!navigation.navigable()) return Component.translatable(
				"terminal.thefourthfrequency.navigation.unavailable", mineral, navigation.targetY());
		return Component.translatable("terminal.thefourthfrequency.navigation.located", mineral,
				Component.translatable("terminal.thefourthfrequency.direction."
						+ direction(navigation.targetDx(), navigation.targetDz())),
				distance(navigation.targetDx(), navigation.targetDz()), navigation.targetY() - playerY);
	}

	public String signalTime(TerminalLogEntryPayload entry) { return TerminalSignalLog.clock(entry.dayTime()); }

	public Component signalEvent(TerminalLogEntryPayload entry) {
		if (entry.type().startsWith("pursuit_warning_")) return Component.empty()
				.append(Component.translatable(
						"terminal.thefourthfrequency.signal.event.pursuit_warning.approaching")
						.withStyle(ChatFormatting.GREEN))
				.append(Component.literal(" "))
				.append(Component.translatable(
						"terminal.thefourthfrequency.signal.event.pursuit_warning.prepare")
						.withStyle(ChatFormatting.RED));
		if (entry.type().startsWith("fragment_candidate_")) return Component.translatable(
				"terminal.thefourthfrequency.signal.event.fragment_candidate", fragmentLocationName(entry));
		if (entry.type().startsWith("fragment_marker_")) return Component.translatable(
				"terminal.thefourthfrequency.signal.event.fragment_marker");
		if (entry.type().startsWith("fragment_action_")) return Component.translatable(
				"terminal.thefourthfrequency.signal.event.fragment_action");
		if (entry.type().startsWith("fragment_shared_") || entry.type().startsWith("fragment_received_")) {
			return Component.translatable("terminal.thefourthfrequency.signal.event.fragment_file");
		}
		if (entry.type().equals("weather_changed")) return Component.translatable(
				"terminal.thefourthfrequency.signal.event.weather_changed",
				Component.translatable("terminal.thefourthfrequency.environment.weather."
						+ switch (Math.clamp(entry.variant(), 0, 2)) {
							case 1 -> "rain";
							case 2 -> "thunder";
							default -> "clear";
						}));
		if (entry.type().equals("dimension_changed")) return Component.translatable(
				"terminal.thefourthfrequency.signal.event.dimension_changed", entry.dimension());
		if (entry.type().startsWith("resource_")) return Component.translatable(
				"terminal.thefourthfrequency.signal.event." + entry.type(),
				Component.translatable("terminal.thefourthfrequency.resource."
						+ switch (entry.variant()) {
							case 0 -> "iron";
							case 4 -> "coal";
							case 5 -> "gold";
							case 2 -> "diamond";
							default -> "unresolved";
						}));
		return Component.translatable("terminal.thefourthfrequency.signal.event." + entry.type(), entry.variant());
	}

	public Component fragmentLocationName(TerminalLogEntryPayload entry) {
		String resolved = Component.translatable("terminal.thefourthfrequency.structure."
				+ Math.clamp(entry.variant(), 0, 13)).getString();
		int codePoints = resolved.codePointCount(0, resolved.length());
		int insertion = Math.floorMod(entry.sequence() * 31 + entry.variant() * 17, codePoints + 1);
		int split = resolved.offsetByCodePoints(0, insertion);
		return Component.empty()
				.append(Component.literal(resolved.substring(0, split)))
				.append(Component.literal("x").withStyle(Style.EMPTY.withObfuscated(true)))
				.append(Component.literal(resolved.substring(split)));
	}

	public Component fileTitle(TerminalFilePayload file) {
		if (HiddenFilePolicy.isHiddenFile(file.id())) {
			return damagedFileTitle(file.id());
		}
		if (file.id().equals(HiddenFilePolicy.COMPLETE_FILE_ID)) {
			int stage = discoveredHiddenFileCount();
			var title = Component.translatable(
					"terminal.thefourthfrequency.file.encrypted_witness_file.revealed." + stage);
			if (stage < HiddenFilePolicy.FILE_COUNT) {
				title.append(Component.translatable(
						"terminal.thefourthfrequency.file.encrypted_witness_file.masked." + stage)
						.withStyle(ChatFormatting.OBFUSCATED));
			}
			return title;
		}
		return Component.translatable(NarrativeFileCatalog.require(file.id()).titleKey());
	}

	public List<Component> fileContent(TerminalFilePayload file) {
		if (!file.unlocked() && !file.id().equals("encrypted_witness_file")) {
			return List.of(Component.translatable("terminal.thefourthfrequency.file.locked"));
		}
		if (HiddenFilePolicy.isHiddenFile(file.id())) return damagedFileContent(file.id());
		if (file.id().equals("encrypted_witness_file")) return file.unlocked()
				? archive() : List.of(Component.translatable("terminal.thefourthfrequency.file.locked"));
		List<Component> lines = new ArrayList<>();
		for (String key : NarrativeFileCatalog.require(file.id()).lineKeys()) lines.add(Component.translatable(key));
		return List.copyOf(lines);
	}

	private static List<Component> damagedFileContent(String fileId) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("terminal.thefourthfrequency.file.damaged.notice")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		for (int index = 1; index <= 3; index++) {
			Component readable = Component.translatable("terminal.thefourthfrequency.file." + fileId
					+ ".readable." + index).withStyle(Style.EMPTY
					.withColor(ChatFormatting.GRAY).withObfuscated(false));
			String resolved = readable.getString();
			int readableCodePoints = resolved.codePointCount(0, resolved.length());
			int prefixMask = readableCodePoints / 2;
			int suffixMask = readableCodePoints - prefixMask;
			Component line = Component.empty()
					.append(damagedMask(prefixMask))
					.append(Component.literal(" "))
					.append(readable)
					.append(Component.literal(" "))
					.append(damagedMask(suffixMask));
			lines.add(line);
		}
		return List.copyOf(lines);
	}

	private static Component damagedFileTitle(String fileId) {
		int fileIndex = HiddenFilePolicy.indexOf(fileId);
		String title = Component.translatable(NarrativeFileCatalog.require(fileId).titleKey()).getString();
		int codePoints = title.codePointCount(0, title.length());
		int requestedMask = fileIndex % 2 == 0 ? 2 : 3;
		int maskCount = Math.clamp(requestedMask, 1, Math.max(1, codePoints - 2));
		int maskStart = Math.max(0, (codePoints - maskCount) / 2);
		int prefixEnd = title.offsetByCodePoints(0, maskStart);
		int suffixStart = title.offsetByCodePoints(prefixEnd, maskCount);
		return Component.empty()
				.append(Component.literal(title.substring(0, prefixEnd)))
				.append(damagedMask(maskCount))
				.append(Component.literal(title.substring(suffixStart)))
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	}

	private static Component damagedMask(int codePoints) {
		return Component.literal("x".repeat(Math.max(0, codePoints))).withStyle(Style.EMPTY
				.withColor(ChatFormatting.DARK_GRAY).withObfuscated(true));
	}

	public Component anomalyType(TerminalLogEntryPayload entry) {
		return Component.translatable("terminal.thefourthfrequency.log.type." + entry.type());
	}

	public Component anomalySummary(TerminalLogEntryPayload entry) {
		return Component.translatable("terminal.thefourthfrequency.log.summary." + entry.type(), entry.variant());
	}

	public List<Component> archive() {
		List<Component> lines = new ArrayList<>();
		for (ArchiveSection section : archiveSections()) lines.addAll(section.lines());
		return lines;
	}

	public List<ArchiveSection> archiveSections() {
		List<ArchiveSection> sections = new ArrayList<>();
		List<Component> localFile = new ArrayList<>();
		if (payload.localFileUnlocked()) {
			WitnessArchive file = WitnessArchive.get();
			localFile.add(Component.translatable("terminal.thefourthfrequency.archive.file_identity", file.version(), file.contentHash()));
			for (String lineKey : file.lineKeys()) localFile.add(Component.translatable(lineKey));
			if (portalTransitions() >= 2) {
				localFile.add(Component.translatable("text.thefourthfrequency.archive.line.continuation.return"));
			} else if (portalTransitions() >= 1) {
				localFile.add(Component.translatable("text.thefourthfrequency.archive.line.continuation.nether"));
			} else {
				localFile.add(Component.translatable("text.thefourthfrequency.archive.line.continuation.pending"));
			}
			sections.add(new ArchiveSection(
					Component.translatable("terminal.thefourthfrequency.ui.archive.section.local_file"), localFile));
		}
		if (sections.isEmpty()) {
			sections.add(new ArchiveSection(Component.empty(),
					List.of(Component.translatable("terminal.thefourthfrequency.archive.empty"))));
		}
		return sections;
	}

	public record ArchiveSection(Component title, List<Component> lines) {
	}

	private static String direction(int dx, int dz) {
		return TerminalNavigationMath.direction(dx, dz);
	}

	private static int distance(int dx, int dz) {
		return TerminalNavigationMath.distance(dx, dz);
	}

}
