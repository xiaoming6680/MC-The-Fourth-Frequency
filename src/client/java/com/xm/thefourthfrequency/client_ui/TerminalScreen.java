package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.networking.ArchivePasswordPayload;
import com.xm.thefourthfrequency.networking.ArchivePasswordResultPayload;
import com.xm.thefourthfrequency.networking.TerminalControlPayload;
import com.xm.thefourthfrequency.networking.TerminalLogEntryPayload;
import com.xm.thefourthfrequency.networking.TerminalFilePayload;
import com.xm.thefourthfrequency.networking.TerminalNavigationPayload;
import com.xm.thefourthfrequency.networking.TerminalSnapshotPayload;
import com.xm.thefourthfrequency.networking.TerminalToolSnapshotPayload;
import com.xm.thefourthfrequency.terminal.TerminalControlPolicy;
import com.xm.thefourthfrequency.terminal.TerminalPage;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalStructureTarget;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.terminal.TerminalUiLayout;
import com.xm.thefourthfrequency.terminal.TerminalNavigationMath;
import com.xm.thefourthfrequency.terminal.TuningTransition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.AMBER;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.CYAN;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.DARK_BORDER;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.DIM;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.GLASS;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.GREEN;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.HOT;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.LCD_BACKGROUND;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.LCD_BORDER;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.SELECTED;

public final class TerminalScreen extends Screen {
	private static final int BASE_WIDTH = 512;
	private static final int BASE_HEIGHT = 256;
	private static final long TRANSITION_MILLIS = 160L;
	private static final long WAVE_MORPH_MILLIS = 260L;
	private static final long WAVE_COLOR_MILLIS = 180L;
	private static final int ROW_HEIGHT = 10;
	private static final int SIGNAL_ROW_HEIGHT = 12;
	private static final int WAVE_SAMPLES = 48;

	private TerminalSnapshot snapshot;
	private TerminalToolSnapshot tools = TerminalToolSnapshot.empty();
	private int mode;
	private TerminalPage page;
	private TerminalTool selectedTool;
	private int tuning;
	private int age;
	private double renderAge;
	private long renderNowMillis;
	private final TuningTransition tuningTransition;
	private final TuningTransition waveformMorphTransition;
	private final double[] waveFromSamples = new double[WAVE_SAMPLES];
	private int waveFromColor = GREEN;
	private int waveTargetColor = GREEN;
	private long waveColorStartedAtMillis;
	private boolean draggingTuner;
	private boolean closedByServer;
	private TerminalNavigationPayload navigation = new TerminalNavigationPayload(
			TerminalNavigationPayload.CURRENT_PROTOCOL_VERSION, 0, false, false, 0, 0, 0, 0.0F);
	private boolean navigationInitialized;
	private double northNeedle;
	private double northNeedleTarget;
	private double mineralNeedle;
	private double mineralNeedleTarget;
	private double displayedObjectiveFraction;
	private double targetObjectiveFraction;
	private String animatedObjectiveId;
	private boolean toolOpenedFromHome;
	private boolean localLockedTool;
	private boolean localTuningOnly;

	private LogView logView = LogView.DIRECTORY;
	private TerminalFilePayload detailFile;
	private int scrollRow;
	private int maxScrollRow;
	private int recordsScrollRow;
	private int recordsMaxScrollRow;
	private int fileListScroll;
	private int fileContentScroll;
	private int fileContentMaxScroll;
	private int selectedFile = -1;
	private int hoveredFile = -1;
	private boolean detailFromFragments;
	private String expandedSignalCard = "";
	private int selectedSignalCard = -1;
	private List<String> signalCardKeys = List.of();
	private final List<SignalHit> signalHits = new ArrayList<>();
	private final List<NavigationHit> navigationHits = new ArrayList<>();
	private final StringBuilder password = new StringBuilder(4);
	private int passwordResult = -1;

	public TerminalScreen(TerminalSnapshotPayload payload) {
		super(Component.translatable("screen.thefourthfrequency.terminal"));
		this.snapshot = new TerminalSnapshot(payload);
		this.mode = snapshot.mode();
		this.page = TerminalPage.initialPage(mode);
		this.tuning = snapshot.tuning();
		this.tuningTransition = new TuningTransition(tuning, TRANSITION_MILLIS);
		this.waveformMorphTransition = new TuningTransition(
				waveformMorphTarget(tuning), WAVE_MORPH_MILLIS);
		this.waveFromColor = signalColor(tuning);
		this.waveTargetColor = waveFromColor;
		this.displayedObjectiveFraction = snapshot.objectiveFraction();
		this.targetObjectiveFraction = displayedObjectiveFraction;
		this.animatedObjectiveId = snapshot.objectiveId();
	}

	public void update(TerminalSnapshotPayload payload) {
		TerminalSnapshot next = new TerminalSnapshot(payload);
		long nowMillis = nowMillis();
		int nextTuning = next.tuning();
		if (nextTuning != tuning && (!localTuningOnly || receiverGameplayActive())) {
			retargetTuningVisual(nextTuning, nowMillis);
		}
		if (!next.objectiveId().equals(animatedObjectiveId)) {
			animatedObjectiveId = next.objectiveId();
			displayedObjectiveFraction = 0.0D;
		}
		targetObjectiveFraction = next.objectiveFraction();
		snapshot = next;
		mode = next.mode();
		waveformMorphTransition.retarget(waveformMorphTarget(tuning), nowMillis);
		retargetSignalColor(signalColor(tuning), nowMillis);
		if (logView == LogView.FRAGMENTS && detailFile != null && detailFile.id().equals("encrypted_witness_file")) {
			detailFile = snapshot.directoryFiles().stream().filter(file -> file.id().equals("encrypted_witness_file"))
					.findFirst().orElse(detailFile);
		}
	}

	public void updateNavigation(TerminalNavigationPayload payload) {
		if (payload.protocolVersion() != TerminalNavigationPayload.CURRENT_PROTOCOL_VERSION) {
			throw new IllegalStateException("Terminal navigation protocol mismatch: server=" + payload.protocolVersion()
					+ ", client=" + TerminalNavigationPayload.CURRENT_PROTOCOL_VERSION);
		}
		navigation = payload;
		northNeedleTarget = TerminalNavigationMath.northNeedleDegrees(payload.playerYaw());
		if (payload.navigable()) {
			mineralNeedleTarget = TerminalNavigationMath.targetNeedleDegrees(
					payload.targetDx(), payload.targetDz(), payload.playerYaw());
		}
		if (!navigationInitialized) {
			navigationInitialized = true;
			northNeedle = northNeedleTarget;
			mineralNeedle = mineralNeedleTarget;
		}
	}

	public void updateTools(TerminalToolSnapshotPayload payload) {
		boolean gameplayBefore = receiverGameplayActive();
		tools = new TerminalToolSnapshot(payload);
		if (selectedTool != null && !tools.available(selectedTool) && !localLockedTool) clearSelectedTool(false);
		if (!gameplayBefore && receiverGameplayActive() && localTuningOnly) {
			localTuningOnly = false;
			if (snapshot.tuning() != tuning) retargetTuningVisual(snapshot.tuning(), nowMillis());
		}
		long nowMillis = nowMillis();
		waveformMorphTransition.retarget(waveformMorphTarget(tuning), nowMillis);
		retargetSignalColor(signalColor(tuning), nowMillis);
	}

	public void acceptPasswordResult(int result) {
		passwordResult = Math.clamp(result, ArchivePasswordResultPayload.SUCCESS, ArchivePasswordResultPayload.WRONG);
		if (result == ArchivePasswordResultPayload.SUCCESS) {
			logView = LogView.DETAIL;
			scrollRow = 0;
		}
	}

	public void closeFromServer() {
		closedByServer = true;
		onClose();
	}

	@Override
	public void tick() {
		age++;
		displayedObjectiveFraction += (targetObjectiveFraction - displayedObjectiveFraction) * 0.18D;
		if (Math.abs(targetObjectiveFraction - displayedObjectiveFraction) < 0.001D) {
			displayedObjectiveFraction = targetObjectiveFraction;
		}
		northNeedle = TerminalNavigationMath.interpolateDegrees(northNeedle, northNeedleTarget, 0.35D);
		if (navigation.navigable()) {
			mineralNeedle = TerminalNavigationMath.interpolateDegrees(mineralNeedle, mineralNeedleTarget, 0.35D);
		}
		TerminalClientAudio.tick();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderNowMillis = nowMillis();
		renderAge = age + partialTick;
		float scale = panelScale();
		int panelWidth = Math.round(BASE_WIDTH * scale);
		int panelHeight = Math.round(BASE_HEIGHT * scale);
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		Identifier panel = Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
				"textures/gui/terminal/panel_" + snapshot.visualStage() + ".png");
		graphics.blit(RenderPipelines.GUI_TEXTURED, panel, left, top, 0.0F, 0.0F,
				panelWidth, panelHeight, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);

		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(scale, scale);
		double[] localMouse = local(mouseX, mouseY);
		hoveredFile = page == TerminalPage.FILES
				? TerminalUiLayout.fileIndexAt(localMouse[0], localMouse[1], fileListScroll, snapshot.files().size()) : -1;
		drawGlass(graphics);
		drawTabs(graphics);
		switch (page) {
			case HOME -> drawHome(graphics);
			case TOOLS -> drawTools(graphics);
			case RECORDS -> drawRecords(graphics);
			case FILES -> drawLog(graphics);
		}
		drawScope(graphics);
		drawCompass(graphics);
		drawReceiverSlider(graphics);
		drawLcd(graphics);
		drawCenteredFitted(graphics, Component.translatable("terminal.thefourthfrequency.close_hint"),
				TerminalUiLayout.CLOSE_HINT, DIM);
		graphics.pose().popMatrix();
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawGlass(GuiGraphics graphics) {
		var display = TerminalUiLayout.DISPLAY;
		graphics.fill(display.left(), display.top(), display.right(), display.bottom(), 0x6507110B);
	}

	private void drawTabs(GuiGraphics graphics) {
		drawTab(graphics, TerminalUiLayout.HOME_TAB, TerminalPage.HOME, "terminal.thefourthfrequency.tab.home");
		drawTab(graphics, TerminalUiLayout.TOOLS_TAB, TerminalPage.TOOLS, "terminal.thefourthfrequency.tab.tools");
		drawTab(graphics, TerminalUiLayout.RECORDS_TAB, TerminalPage.RECORDS, "terminal.thefourthfrequency.tab.records");
		drawTab(graphics, TerminalUiLayout.FILES_TAB, TerminalPage.FILES, "terminal.thefourthfrequency.tab.files");
	}

	private void drawTab(GuiGraphics graphics, TerminalUiLayout.Bounds bounds,
			TerminalPage tab, String key) {
		boolean selected = page == tab;
		graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), selected ? SELECTED : GLASS);
		if (selected) graphics.fill(bounds.left(), bounds.top() + 2, bounds.left() + 2, bounds.bottom() - 2, pageAccent());
		drawCenteredFitted(graphics, Component.translatable(key), bounds, selected ? pageAccent() : DIM);
	}

	private void drawHome(GuiGraphics graphics) {
		drawCard(graphics, TerminalUiLayout.HOME_TASK, pageAccent());
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.home.current_task"),
				TerminalUiLayout.HOME_TASK.left() + 7, TerminalUiLayout.HOME_TASK.top() + 6, AMBER, false);
		List<FormattedCharSequence> objective = font.split(snapshot.objectiveLine(), TerminalUiLayout.HOME_TASK.width() - 14);
		if (!objective.isEmpty()) graphics.drawString(font, objective.getFirst(), TerminalUiLayout.HOME_TASK.left() + 7,
				TerminalUiLayout.HOME_TASK.top() + 20, GREEN, false);
		int progressWidth = TerminalUiLayout.HOME_TASK.width() - 14;
		int filled = (int) Math.round(progressWidth * Math.clamp(displayedObjectiveFraction, 0.0D, 1.0D));
		int progressY = TerminalUiLayout.HOME_TASK.bottom() - 9;
		graphics.fill(TerminalUiLayout.HOME_TASK.left() + 7, progressY,
				TerminalUiLayout.HOME_TASK.right() - 7, progressY + 3, DARK_BORDER);
		graphics.fill(TerminalUiLayout.HOME_TASK.left() + 7, progressY,
				TerminalUiLayout.HOME_TASK.left() + 7 + filled, progressY + 3, pageAccent());

		if (selectedTool != null && toolOpenedFromHome) {
			drawHomeToolDetail(graphics, selectedTool);
		} else {
			drawQuickTool(graphics, tools.recommendedPrimaryTool(), TerminalUiLayout.HOME_QUICK_PRIMARY);
			drawQuickTool(graphics, tools.recommendedSecondaryTool(), TerminalUiLayout.HOME_QUICK_SECONDARY);
		}

		drawCard(graphics, TerminalUiLayout.HOME_RECENT, DIM);
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.home.recent"),
				TerminalUiLayout.HOME_RECENT.left() + 7, TerminalUiLayout.HOME_RECENT.top() + 5, AMBER, false);
		Component recent = snapshot.latestSignalEvent();
		graphics.drawString(font, font.split(recent, TerminalUiLayout.HOME_RECENT.width() - 14).getFirst(),
				TerminalUiLayout.HOME_RECENT.left() + 7, TerminalUiLayout.HOME_RECENT.top() + 16, GREEN, false);
	}

	private void drawQuickTool(GuiGraphics graphics, TerminalTool tool, TerminalUiLayout.Bounds bounds) {
		if (tool == null) {
			drawCard(graphics, bounds, DIM);
			graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.home.pending_hint"),
					bounds.left() + 7, bounds.top() + 8, DIM, false);
			Component detail = Component.translatable("terminal.thefourthfrequency.home.pending_hint.detail");
			graphics.drawString(font, ellipsize(detail.getString(), bounds.width() - 14),
					bounds.left() + 7, bounds.top() + 23, DIM, false);
			return;
		}
		drawCard(graphics, bounds, GREEN);
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.home.quick"),
				bounds.left() + 7, bounds.top() + 6, DIM, false);
		graphics.drawString(font, toolName(tool), bounds.left() + 7, bounds.top() + 20, GREEN, false);
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.tool.open"),
				bounds.right() - 7 - font.width(Component.translatable("terminal.thefourthfrequency.tool.open")),
				bounds.bottom() - 13, AMBER, false);
	}

	private void drawHomeToolDetail(GuiGraphics graphics, TerminalTool tool) {
		TerminalUiLayout.Bounds bounds = TerminalUiLayout.HOME_TOOL_DETAIL;
		drawCard(graphics, bounds, tools.available(tool) ? pageAccent() : DIM);
		drawToolButton(graphics, TerminalUiLayout.HOME_TOOL_BACK,
				Component.translatable("terminal.thefourthfrequency.tool.back_home"), false);
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.home.live_tool", toolName(tool)),
				104, bounds.top() + 6, tools.available(tool) ? AMBER : DIM, false);
		List<Component> lines = toolDetailLines(tool);
		Component status = lines.size() > 1 ? lines.get(1) : lines.getFirst();
		graphics.drawString(font, ellipsize(status.getString(), bounds.right() - 110),
				104, bounds.top() + 21, tools.available(tool) ? GREEN : DIM, false);
		Component detail = Component.translatable("terminal.thefourthfrequency.tool.open_detail");
		graphics.drawString(font, detail, bounds.right() - 7 - font.width(detail), bounds.top() + 6, AMBER, false);
	}

	private void drawTools(GuiGraphics graphics) {
		if (selectedTool != null) {
			drawToolDetail(graphics, selectedTool);
			return;
		}
		for (TerminalTool tool : TerminalTool.values()) {
			TerminalUiLayout.Bounds cell = TerminalUiLayout.toolCell(tool.slot());
			boolean available = tools.available(tool);
			drawCard(graphics, cell, available ? GREEN : DARK_BORDER);
			drawToolGlyph(graphics, tool, cell);
			drawCenteredFitted(graphics, toolName(tool),
					new TerminalUiLayout.Bounds(cell.left() + 3, cell.bottom() - 18, cell.right() - 3, cell.bottom() - 3),
					available ? GREEN : DIM);
			if (!available) drawPixelLock(graphics, cell.right() - 14, cell.top() + 5);
		}
	}

	private void drawToolGlyph(GuiGraphics graphics, TerminalTool tool, TerminalUiLayout.Bounds cell) {
		String[] pixels = switch (tool) {
			case HOME -> new String[]{"....#....", "...###...", "..####...", ".##..##..", ".#....#..", ".#..+.#..", ".#..+.#..", ".######..", "........."};
			case MINERALS -> new String[]{".######..", "##....##.", ".....##..", "....##...", "...##....", "..##.....", ".##......", "##.......", "........."};
			case PORTAL -> new String[]{"..#####..", "..#~~~#..", "..#~~~#..", "..#~~~#..", "..#~~~#..", "..#~~~#..", "..#~~~#..", "..#####..", "........."};
			case WEATHER -> new String[]{"...###...", "..#####..", "..#####..", "...###...", ".++++++..", "++++++++.", "..~..~...", ".~..~....", "........."};
			case NAVIGATION -> new String[]{"....!....", "...!!!...", "...!#!...", "..!###!..", ".!##+##!.", "..!#+#!..", "...!#!...", "....!....", "........."};
			case STRONGHOLD -> new String[]{".........", "..++++...", ".++##++..", "++#~~#++.", "+#~!!~#+.", "++#~~#++.", ".++##++..", "..++++...", "........."};
		};
		int x = (cell.left() + cell.right()) / 2 - 9;
		int y = cell.top() + 4;
		drawPixelPattern(graphics, pixels, x, y, tools.available(tool));
	}

	private void drawPixelPattern(GuiGraphics graphics, String[] pixels, int x, int y, boolean available) {
		for (int row = 0; row < pixels.length; row++) {
			for (int column = 0; column < pixels[row].length(); column++) {
				char pixel = pixels[row].charAt(column);
				if (pixel == '.') continue;
				int color = !available ? DIM : switch (pixel) {
					case '+' -> GREEN;
					case '~' -> CYAN;
					case '!' -> HOT;
					default -> AMBER;
				};
				graphics.fill(x + column * 2, y + row * 2, x + column * 2 + 2, y + row * 2 + 2, color);
			}
		}
	}

	private void drawPixelLock(GuiGraphics graphics, int x, int y) {
		graphics.fill(x + 2, y, x + 7, y + 1, DIM);
		graphics.fill(x + 1, y + 1, x + 3, y + 5, DIM);
		graphics.fill(x + 6, y + 1, x + 8, y + 5, DIM);
		graphics.fill(x, y + 4, x + 9, y + 11, 0xFF111711);
		graphics.renderOutline(x, y + 4, 9, 7, DIM);
	}

	private void drawToolDetail(GuiGraphics graphics, TerminalTool tool) {
		drawCard(graphics, TerminalUiLayout.TOOL_HEADER, pageAccent());
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.tool.back"),
				TerminalUiLayout.TOOL_HEADER.left() + 7, TerminalUiLayout.TOOL_HEADER.top() + 6, AMBER, false);
		if (toolOpenedFromHome) {
			graphics.fill(TerminalUiLayout.TOOL_HEADER.left() + 4, TerminalUiLayout.TOOL_HEADER.top() + 3,
					TerminalUiLayout.TOOL_HEADER.left() + 60, TerminalUiLayout.TOOL_HEADER.bottom() - 3, 0xFF0C1710);
			graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.tool.back_home"),
				TerminalUiLayout.TOOL_HEADER.left() + 7, TerminalUiLayout.TOOL_HEADER.top() + 6, AMBER, false);
		}
		graphics.drawString(font, toolName(tool), TerminalUiLayout.TOOL_HEADER.left() + 68,
				TerminalUiLayout.TOOL_HEADER.top() + 6, GREEN, false);
		drawCard(graphics, TerminalUiLayout.TOOL_DETAIL, GREEN);
		List<Component> lines = toolDetailLines(tool);
		int y = TerminalUiLayout.TOOL_DETAIL.top() + 8;
		for (Component line : lines) {
			for (FormattedCharSequence wrapped : font.split(line, TerminalUiLayout.TOOL_DETAIL.width() - 14)) {
				int detailLimit = tool == TerminalTool.NAVIGATION ? 122 : 136;
				if (y + font.lineHeight > detailLimit) break;
				graphics.drawString(font, wrapped, TerminalUiLayout.TOOL_DETAIL.left() + 7, y, GREEN, false);
				y += 12;
			}
		}
		if (tool == TerminalTool.NAVIGATION) drawNavigationToolList(graphics);
		else navigationHits.clear();
		drawToolActions(graphics, tool);
	}

	private List<Component> toolDetailLines(TerminalTool tool) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("terminal.thefourthfrequency.tool." + tool.id() + ".summary"));
		switch (tool) {
			case HOME -> lines.add(tools.homeLine());
			case MINERALS -> lines.add(navigation.targetKind() >= 1 && navigation.targetKind() <= 3
					? snapshot.navigationLine(navigation, tools.playerY())
					: Component.translatable("terminal.thefourthfrequency.tool.minerals.waiting"));
			case PORTAL -> lines.add(tools.portalLine());
			case WEATHER -> lines.add(tools.weatherLine());
			case NAVIGATION -> {
				lines.add(tools.navigationSummaryLine());
				if (navigation.navigable()) lines.add(snapshot.navigationLine(navigation, tools.playerY()));
			}
			case STRONGHOLD -> lines.add(tools.strongholdLine());
		}
		if (!tools.available(tool)) lines.add(tools.lockedLine(tool));
		if (tools.toolsDisabled() && tool != TerminalTool.WEATHER) lines.add(tools.disabledLine());
		return List.copyOf(lines);
	}

	private void drawNavigationToolList(GuiGraphics graphics) {
		navigationHits.clear();
		List<TerminalStructureTarget> targets = new ArrayList<>();
		for (TerminalStructureTarget target : TerminalStructureTarget.values()) {
			if (tools.navigationTargetAvailable(target)) targets.add(target);
		}
		int index = 0;
		for (TerminalStructureTarget target : targets) {
			TerminalUiLayout.Bounds bounds = navigationOptionBounds(index++);
			boolean selected = tools.selectedNavigationTarget() == target;
			Component name = Component.translatable("terminal.thefourthfrequency.navigation.target." + target.id());
			Component label = target.sideRoute()
					? Component.translatable("terminal.thefourthfrequency.navigation.side_route", name) : name;
			drawToolButton(graphics, bounds, label, selected);
			navigationHits.add(new NavigationHit(bounds, TerminalControlPayload.SELECT_STRUCTURE_TARGET, target.wireId()));
		}
		if (tools.unstableSignalAvailable() && index < 4) {
			TerminalUiLayout.Bounds bounds = navigationOptionBounds(index++);
			double pulse = (Math.sin(renderAge * 0.075D) + 1.0D) * 0.5D;
			int red = lerpColor(0xFF754D50, 0xFFE5A0A4, pulse);
			graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFF171012);
			graphics.renderOutline(bounds.left(), bounds.top(), bounds.width(), bounds.height(), red);
			drawCenteredFitted(graphics, Component.translatable(
					"terminal.thefourthfrequency.navigation.unstable_signal"), bounds, red);
			navigationHits.add(new NavigationHit(bounds, TerminalControlPayload.SELECT_NEAREST_UNSTABLE, 0));
		}
		if (index == 0) {
			drawCenteredFitted(graphics, Component.translatable("terminal.thefourthfrequency.navigation.no_targets"),
					new TerminalUiLayout.Bounds(50, 124, 342, 162), DIM);
		}
	}

	private static TerminalUiLayout.Bounds navigationOptionBounds(int index) {
		int column = Math.floorMod(index, 2);
		int row = Math.floorDiv(index, 2);
		int left = column == 0 ? 50 : 198;
		int top = 124 + row * 20;
		return new TerminalUiLayout.Bounds(left, top, left + 144, top + 17);
	}

	private void drawSignalToolList(GuiGraphics graphics) {
		TerminalUiLayout.Bounds list = new TerminalUiLayout.Bounds(50, 124, 342, 163);
		List<SignalRow> rows = signalRows(list.width() - 12);
		drawSignalRows(graphics, rows, list);
	}

	private void drawToolActions(GuiGraphics graphics, TerminalTool tool) {
		if (!tools.available(tool) || tools.toolsDisabled() && tool != TerminalTool.WEATHER) return;
		switch (tool) {
			case HOME -> {
				drawToolButton(graphics, TerminalUiLayout.TOOL_OPTION_ONE,
						Component.translatable("terminal.thefourthfrequency.tool.home.set_here"), false);
				if (tools.payload().homeKnown()) drawGuidanceButtons(graphics, tool);
			}
			case MINERALS -> {
				List<TerminalResource> resources = visibleResources();
				for (int index = 0; index < resources.size(); index++)
					drawResourceButton(graphics, resourceOptionBounds(index), resources.get(index));
				drawToolButton(graphics, TerminalUiLayout.TOOL_ACTION_PRIMARY,
						Component.translatable("terminal.thefourthfrequency.tool.rescan"), false);
				drawToolButton(graphics, TerminalUiLayout.TOOL_ACTION_SECONDARY,
						Component.translatable("terminal.thefourthfrequency.tool.stop"), false);
			}
			case PORTAL, STRONGHOLD -> drawGuidanceButtons(graphics, tool);
			case NAVIGATION -> drawToolButton(graphics, TerminalUiLayout.TOOL_ACTION_SECONDARY,
					Component.translatable("terminal.thefourthfrequency.tool.stop"), false);
			case WEATHER -> { }
		}
	}

	private void drawGuidanceButtons(GuiGraphics graphics, TerminalTool tool) {
		drawToolButton(graphics, TerminalUiLayout.TOOL_ACTION_PRIMARY,
				Component.translatable("terminal.thefourthfrequency.tool.guide"), tools.guidanceTool() == tool);
		drawToolButton(graphics, TerminalUiLayout.TOOL_ACTION_SECONDARY,
				Component.translatable("terminal.thefourthfrequency.tool.stop"), false);
	}

	private void drawResourceButton(GuiGraphics graphics, TerminalUiLayout.Bounds bounds, TerminalResource resource) {
		drawToolButton(graphics, bounds, Component.translatable(
				"terminal.thefourthfrequency.resource." + resource.id()), tools.selectedResource() == resource);
	}

	private List<TerminalResource> visibleResources() {
		List<TerminalResource> resources = new ArrayList<>();
		for (TerminalResource resource : new TerminalResource[]{TerminalResource.IRON,
				TerminalResource.REDSTONE, TerminalResource.DIAMOND})
			if (tools.resourceAvailable(resource)) resources.add(resource);
		return resources;
	}

	private static TerminalUiLayout.Bounds resourceOptionBounds(int index) {
		return switch (index) {
			case 0 -> TerminalUiLayout.TOOL_OPTION_ONE;
			case 1 -> TerminalUiLayout.TOOL_OPTION_TWO;
			default -> TerminalUiLayout.TOOL_OPTION_THREE;
		};
	}

	private void drawToolButton(GuiGraphics graphics, TerminalUiLayout.Bounds bounds, Component label, boolean selected) {
		graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), selected ? SELECTED : 0xFF10140E);
		graphics.renderOutline(bounds.left(), bounds.top(), bounds.width(), bounds.height(), selected ? AMBER : GREEN);
		drawCenteredFitted(graphics, label, bounds, selected ? AMBER : GREEN);
	}

	private void drawRecords(GuiGraphics graphics) {
		var body = TerminalUiLayout.RECORDS_BODY;
		graphics.fill(body.left(), body.top(), body.right(), body.bottom(), GLASS);
		List<StyledRow> rows = new ArrayList<>();
		for (TerminalLogEntryPayload entry : snapshot.allSignalEntries()) {
			Component line = Component.literal("[" + snapshot.signalTime(entry) + "] ").append(snapshot.signalEvent(entry));
			rows.addAll(styledRows(List.of(line), body.width() - 16, entry.unread() ? AMBER : GREEN, 6, entry.unread()));
		}
		if (rows.isEmpty()) rows.addAll(styledRows(List.of(Component.translatable(
				"terminal.thefourthfrequency.records.empty")), body.width() - 16, DIM, 6, false));
		int visible = Math.max(1, (body.height() - 10) / ROW_HEIGHT);
		recordsMaxScrollRow = Math.max(0, rows.size() - visible);
		recordsScrollRow = Math.clamp(recordsScrollRow, 0, recordsMaxScrollRow);
		int y = body.top() + 5;
		for (int index = recordsScrollRow; index < rows.size() && index < recordsScrollRow + visible; index++) {
			StyledRow row = rows.get(index);
			if (row.marker()) graphics.fill(body.left() + 4, y + 2, body.left() + 6, y + 7, row.color());
			if (row.text() != null) graphics.drawString(font, row.text(), body.left() + row.indent(), y, row.color(), false);
			y += ROW_HEIGHT;
		}
	}

	private void drawCard(GuiGraphics graphics, TerminalUiLayout.Bounds bounds, int outline) {
		graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0x650C1710);
		graphics.renderOutline(bounds.left(), bounds.top(), bounds.width(), bounds.height(), outline);
	}

	private Component toolName(TerminalTool tool) {
		return Component.translatable("terminal.thefourthfrequency.tool." + tool.id());
	}

	private boolean toolVisible(TerminalTool tool) {
		return tool != null;
	}

	private List<SignalRow> signalRows(int width) {
		List<SignalRow> rows = new ArrayList<>();
		List<String> cards = new ArrayList<>();
		if (navigation.targetKind() == TerminalNavigationPayload.UNSTABLE_SIGNAL) {
			appendSignalRows(rows, Component.translatable("terminal.thefourthfrequency.signal.navigation_prefix")
					.append(snapshot.navigationLine(navigation, tools.playerY())), GREEN, true, "", false, -1, width);
		}
		List<TerminalLogEntryPayload> entries = snapshot.signalToolEntries();
		Map<Integer, List<TerminalLogEntryPayload>> candidateGroups = new LinkedHashMap<>();
		Map<Integer, TerminalLogEntryPayload> candidateMarkers = new LinkedHashMap<>();
		for (TerminalLogEntryPayload entry : entries) {
			int[] candidate = candidateParts(entry.type());
			if (candidate != null) candidateGroups.computeIfAbsent(candidate[0], ignored -> new ArrayList<>()).add(entry);
			int marker = markerFragment(entry.type());
			if (marker > 0) candidateMarkers.putIfAbsent(marker, entry);
		}
		for (var group : candidateGroups.entrySet()) {
			if (!snapshot.fragmentReceived(group.getKey() - 1)) {
				appendCandidateCard(rows, cards, group.getKey(), group.getValue(), width);
			}
		}
		for (var marker : candidateMarkers.entrySet()) {
			if (!candidateGroups.containsKey(marker.getKey()) && !snapshot.fragmentReceived(marker.getKey() - 1)) {
				appendSignalRows(rows, Component.translatable(
						"terminal.thefourthfrequency.signal.marker.unrecorded"), DIM,
						false, "", false, -1, width);
			}
		}
		for (TerminalLogEntryPayload entry : entries) {
			int[] candidate = candidateParts(entry.type());
			if (candidate != null || markerFragment(entry.type()) > 0) continue;
			if (entry.type().startsWith("fragment_action_")) {
				appendActionCard(rows, cards, entry, width);
			} else if (entry.type().startsWith("fragment_shared_") || entry.type().startsWith("fragment_received_")) {
				appendSharedCard(rows, cards, entry, width);
			} else if (entry.type().equals("dimension_changed")) {
				String key = "dimension:" + entry.sequence();
				List<CardDetail> details = List.of(new CardDetail(Component.translatable(
						"terminal.thefourthfrequency.signal.card.dimension.detail", entry.dimension()), -1));
				appendCard(rows, cards, key, Component.translatable(
						"terminal.thefourthfrequency.signal.card.dimension.title"), details, entry.unread(), width);
			} else {
				appendSignalRows(rows, Component.literal("[" + snapshot.signalTime(entry) + "] ")
						.append(snapshot.signalEvent(entry)), GREEN, entry.unread(), "", false, -1, width);
			}
		}
		if (rows.isEmpty()) appendSignalRows(rows,
				Component.translatable("terminal.thefourthfrequency.signal.feed.empty"), DIM, false, "", false, -1, width);
		signalCardKeys = List.copyOf(cards);
		if (selectedSignalCard >= signalCardKeys.size()) selectedSignalCard = signalCardKeys.size() - 1;
		if (!expandedSignalCard.isEmpty() && !signalCardKeys.contains(expandedSignalCard)) expandedSignalCard = "";
		return rows;
	}

	private void appendCandidateCard(List<SignalRow> rows, List<String> cards, int fragment,
			List<TerminalLogEntryPayload> entries, int width) {
		entries.sort(java.util.Comparator.comparingInt(value -> candidateParts(value.type())[1]));
		String key = "candidates:" + fragment;
		Component title = Component.translatable("terminal.thefourthfrequency.signal.card.candidates.title");
		List<CardDetail> details = new ArrayList<>();
		for (TerminalLogEntryPayload entry : entries) {
			int[] parts = candidateParts(entry.type());
			int slot = parts[1];
			BlockPos position = BlockPos.of(entry.position());
			Component structure = Component.translatable("terminal.thefourthfrequency.structure." + Math.clamp(entry.variant(), 0, 13));
			Component place = Component.translatable("terminal.thefourthfrequency.structure.location."
					+ Math.clamp(entry.severity(), 0, 2));
			String dimension = dimensionLabel(entry.dimension());
			int distance = candidateDistance(entry);
			Component line = distance >= 0
					? Component.translatable("terminal.thefourthfrequency.signal.card.candidate.same_dimension",
							structure, dimension, position.getX(), position.getZ(), place, distance)
					: Component.translatable("terminal.thefourthfrequency.signal.card.candidate.other_dimension",
							structure, dimension, position.getX(), position.getZ(), place);
			details.add(new CardDetail(line, (fragment - 1) * 3 + slot));
		}
		appendCard(rows, cards, key, title, details, entries.stream().anyMatch(TerminalLogEntryPayload::unread), width);
	}

	private void appendActionCard(List<SignalRow> rows, List<String> cards, TerminalLogEntryPayload entry, int width) {
		int fragment = trailingNumber(entry.type());
		String key = "action:" + entry.sequence();
		int gap = 5 + Math.floorMod(entry.variant() + fragment, 4);
		int movement = 8 + Math.floorMod(entry.variant() * 3 + fragment, 7);
		int torches = fragment == 1 ? 1 : Math.floorMod(entry.variant(), 2);
		int turns = 1 + Math.floorMod(entry.variant() + fragment, 3);
		List<CardDetail> details = new ArrayList<>();
		details.add(new CardDetail(Component.translatable("terminal.thefourthfrequency.signal.card.action.gap", gap), -1));
		details.add(new CardDetail(Component.translatable("terminal.thefourthfrequency.signal.card.action.record"), -1));
		details.add(new CardDetail(Component.translatable("terminal.thefourthfrequency.signal.card.action.move", movement), -1));
		details.add(new CardDetail(Component.translatable("terminal.thefourthfrequency.signal.card.action.torch", torches), -1));
		details.add(new CardDetail(Component.translatable("terminal.thefourthfrequency.signal.card.action.turn", turns), -1));
		appendCard(rows, cards, key, Component.translatable("terminal.thefourthfrequency.signal.card.action.title"),
				details, entry.unread(), width);
	}

	private void appendSharedCard(List<SignalRow> rows, List<String> cards, TerminalLogEntryPayload entry, int width) {
		boolean received = entry.type().startsWith("fragment_received_");
		Component title = received
				? Component.translatable("terminal.thefourthfrequency.signal.card.file.received", entry.dimension())
				: Component.translatable("terminal.thefourthfrequency.signal.card.file.shared");
		appendCard(rows, cards, "file:" + entry.sequence(), title,
				List.of(new CardDetail(Component.translatable("terminal.thefourthfrequency.signal.card.file.detail"), -1)),
				entry.unread(), width);
	}

	private void appendCard(List<SignalRow> rows, List<String> cards, String key, Component title,
			List<CardDetail> details, boolean unread, int width) {
		cards.add(key);
		boolean expanded = key.equals(expandedSignalCard);
		Component header = title.copy().append(Component.literal(expanded ? "  －" : "  ＋"));
		appendSignalRows(rows, header, expanded ? AMBER : GREEN, unread, key, true, -1, width);
		if (!expanded) return;
		for (CardDetail detail : details) {
			Component item = Component.literal("    · ").append(detail.text());
			appendSignalRows(rows, item, DIM, false, key, false, detail.navigation(), width);
		}
	}

	private void appendSignalRows(List<SignalRow> rows, Component line, int color, boolean marker,
			String cardKey, boolean header, int navigationValue, int width) {
		List<FormattedCharSequence> wrapped = font.split(line, Math.max(1, width));
		if (wrapped.isEmpty()) rows.add(new SignalRow(null, color, marker, cardKey, header, navigationValue));
		for (int index = 0; index < wrapped.size(); index++) rows.add(new SignalRow(wrapped.get(index), color,
				marker && index == 0, cardKey, header && index == 0, navigationValue));
	}

	private void drawSignalRows(GuiGraphics graphics, List<SignalRow> rows, TerminalUiLayout.Bounds bounds) {
		int visible = Math.max(1, bounds.height() / SIGNAL_ROW_HEIGHT);
		maxScrollRow = Math.max(0, rows.size() - visible);
		scrollRow = Math.clamp(scrollRow, 0, maxScrollRow);
		signalHits.clear();
		int y = bounds.top();
		for (int index = scrollRow; index < rows.size() && index < scrollRow + visible; index++) {
			SignalRow row = rows.get(index);
			boolean selected = row.header() && selectedSignalCard >= 0 && selectedSignalCard < signalCardKeys.size()
					&& signalCardKeys.get(selectedSignalCard).equals(row.cardKey());
			if (row.marker()) graphics.fill(bounds.left(), y + 2, bounds.left() + 2, y + 7, HOT);
			if (row.text() != null) graphics.drawString(font, row.text(), bounds.left() + 5, y,
					selected ? AMBER : row.color(), false);
			if (row.header() || row.navigationValue() >= 0) signalHits.add(new SignalHit(
					new TerminalUiLayout.Bounds(bounds.left(), y - 1, bounds.right(), y + SIGNAL_ROW_HEIGHT - 1),
					row.cardKey(), row.header(), row.navigationValue()));
			y += SIGNAL_ROW_HEIGHT;
		}
	}

	private int candidateDistance(TerminalLogEntryPayload entry) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || !client.level.dimension().identifier().toString().equals(entry.dimension())) return -1;
		BlockPos target = BlockPos.of(entry.position());
		return (int) Math.round(Math.hypot(target.getX() - client.player.getX(), target.getZ() - client.player.getZ()));
	}

	private static String dimensionLabel(String id) {
		return Component.translatable("terminal.thefourthfrequency.dimension." +
				(id.equals("minecraft:the_nether") ? "nether" : id.equals("minecraft:the_end") ? "end" : "overworld")).getString();
	}

	private static int[] candidateParts(String type) {
		if (!type.startsWith("fragment_candidate_")) return null;
		String[] parts = type.substring("fragment_candidate_".length()).split("_");
		if (parts.length != 2) return null;
		try { return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])}; }
		catch (NumberFormatException ignored) { return null; }
	}

	private static int markerFragment(String type) {
		if (!type.startsWith("fragment_marker_")) return -1;
		try {
			int fragment = Integer.parseInt(type.substring("fragment_marker_".length()));
			return fragment >= 1 && fragment <= 4 ? fragment : -1;
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static int trailingNumber(String type) {
		int separator = type.lastIndexOf('_');
		if (separator < 0 || separator + 1 >= type.length()) return 1;
		try { return Integer.parseInt(type.substring(separator + 1)); }
		catch (NumberFormatException ignored) { return 1; }
	}

	private void drawLog(GuiGraphics graphics) {
		drawLogDirectory(graphics);
	}

	private void drawLogDirectory(GuiGraphics graphics) {
		var body = TerminalUiLayout.FILE_BODY;
		graphics.fill(body.left(), body.top(), body.right(), body.bottom(), GLASS);
		graphics.fill(TerminalUiLayout.FILE_DIVIDER.left(), TerminalUiLayout.FILE_DIVIDER.top(),
				TerminalUiLayout.FILE_DIVIDER.right(), TerminalUiLayout.FILE_DIVIDER.bottom(), DARK_BORDER);
		int total = snapshot.files().size();
		fileListScroll = Math.clamp(fileListScroll, 0, TerminalUiLayout.fileMaxScrollRow(total));
		if (selectedFile >= 0) selectedFile = Math.clamp(selectedFile, 0, Math.max(0, total - 1));
		for (int row = 0; row < TerminalUiLayout.FILE_LIST_VISIBLE_ROWS; row++) {
			int index = fileListScroll + row;
			if (index < total) drawDirectoryEntry(graphics, index, TerminalUiLayout.fileListRow(row));
		}
		if (total == 0) {
			drawCenteredFitted(graphics, Component.translatable("terminal.thefourthfrequency.file.empty"),
					TerminalUiLayout.FILE_LIST, DIM);
		}
		drawSelectedFileContent(graphics);
	}

	private void drawDirectoryEntry(GuiGraphics graphics, int index, TerminalUiLayout.Bounds cell) {
		if (index < 0 || index >= snapshot.files().size()) return;
		TerminalFilePayload file = snapshot.files().get(index);
		boolean focused = index == selectedFile || index == hoveredFile;
		graphics.fill(cell.left(), cell.top(), cell.right(), cell.bottom(), focused ? 0xAA172A1D : 0x650C1710);
		if (focused) graphics.fill(cell.left(), cell.top(), cell.left() + 2, cell.bottom(), pageAccent());
		boolean fragmentParent = file.id().equals("encrypted_witness_file");
		Component title = file.unlocked() || fragmentParent ? snapshot.fileTitle(file)
				: Component.translatable("terminal.thefourthfrequency.file.locked_title", snapshot.fileTitle(file));
		int color = file.unlocked() ? GREEN : fragmentParent ? AMBER : CYAN;
		graphics.drawString(font, ellipsize(title.getString(), cell.width() - 10), cell.left() + 5,
				cell.top() + Math.max(4, (cell.height() - font.lineHeight) / 2), color, false);
	}

	private void drawSelectedFileContent(GuiGraphics graphics) {
		var content = TerminalUiLayout.FILE_CONTENT;
		if (selectedFile < 0 || detailFile == null) {
			drawCenteredFitted(graphics, Component.translatable("terminal.thefourthfrequency.file.select_prompt"), content, DIM);
			fileContentScroll = 0;
			fileContentMaxScroll = 0;
			return;
		}
		switch (logView) {
			case DIRECTORY, DETAIL -> drawLogDetail(graphics);
			case FRAGMENTS -> drawFragments(graphics);
			case PASSWORD -> drawPassword(graphics);
		}
	}

	private void drawLogDetail(GuiGraphics graphics) {
		var content = TerminalUiLayout.FILE_CONTENT;
		List<Component> lines = detailLines();
		List<StyledRow> rows = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			rows.addAll(styledRows(List.of(lines.get(i)), content.width() - 12, i == 0 ? AMBER : GREEN, 5, false));
		}
		int visible = Math.max(1, (content.height() - 8) / ROW_HEIGHT);
		fileContentMaxScroll = Math.max(0, rows.size() - visible);
		fileContentScroll = Math.clamp(fileContentScroll, 0, fileContentMaxScroll);
		int y = content.top() + 4;
		for (int index = fileContentScroll; index < rows.size() && index < fileContentScroll + visible; index++) {
			StyledRow row = rows.get(index);
			if (row.text() != null) graphics.drawString(font, row.text(), content.left() + row.indent(), y, row.color(), false);
			y += ROW_HEIGHT;
		}
	}

	private List<Component> detailLines() {
		if (detailFile == null) return List.of();
		List<Component> result = new ArrayList<>();
		result.add(snapshot.fileTitle(detailFile));
		result.addAll(snapshot.fileContent(detailFile));
		return List.copyOf(result);
	}

	private void drawFragments(GuiGraphics graphics) {
		var body = TerminalUiLayout.FILE_CONTENT;
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.fragments.title"),
				body.left() + 7, body.top() + 5, AMBER, false);
		for (int fragment = 0; fragment < 4; fragment++) {
			TerminalUiLayout.Bounds row = fragmentBounds(fragment);
			TerminalFilePayload file = snapshot.fragmentFile(fragment);
			boolean received = file != null;
			graphics.fill(row.left(), row.top(), row.right(), row.bottom(), received ? 0xA514281B : 0x75101511);
			graphics.renderOutline(row.left(), row.top(), row.width(), row.height(), received ? GREEN : DARK_BORDER);
			graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.fragments.row", fragment + 1),
					row.left() + 6, row.top() + 5, received ? GREEN : DIM, false);
			Component status = Component.translatable(received
					? "terminal.thefourthfrequency.fragments.received" : "terminal.thefourthfrequency.fragments.missing");
			graphics.drawString(font, status, row.right() - 6 - font.width(status), row.top() + 5,
					received ? AMBER : DIM, false);
		}
		drawCenteredFitted(graphics, Component.translatable("terminal.thefourthfrequency.fragments.waiting"),
				fragmentCompleteBounds(), DIM);
	}

	private void drawPassword(GuiGraphics graphics) {
		var body = TerminalUiLayout.FILE_CONTENT;
		graphics.drawCenteredString(font, Component.translatable("terminal.thefourthfrequency.password.title"),
				(body.left() + body.right()) / 2, body.top() + 4, CYAN);
		for (int index = 0; index < 4; index++) {
			int x = body.left() + 6 + (index % 2) * 103;
			int y = body.top() + 17 + (index / 2) * 11;
			boolean found = snapshot.evidence(index) >= 0;
			graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.password.clue." + index
					+ (found ? ".found" : ".missing")), x, y, found ? GREEN : DIM, false);
		}
		String shown = password + "_".repeat(4 - password.length());
		graphics.drawCenteredString(font, Component.literal(shown), (body.left() + body.right()) / 2, body.top() + 43, AMBER);
		for (int digit = 0; digit < 4; digit++) drawKey(graphics, digitBounds(digit), Integer.toString(digit), CYAN);
		drawKey(graphics, clearBounds(), Component.translatable("terminal.thefourthfrequency.password.clear").getString(), DIM);
		drawKey(graphics, submitBounds(), Component.translatable("terminal.thefourthfrequency.password.submit").getString(), AMBER);
		if (passwordResult >= 0 && passwordResult != ArchivePasswordResultPayload.SUCCESS) {
			graphics.drawCenteredString(font, Component.translatable("terminal.thefourthfrequency.password.result."
					+ passwordResult), (body.left() + body.right()) / 2, body.bottom() - 10, HOT);
		}
	}

	private void drawKey(GuiGraphics graphics, TerminalUiLayout.Bounds bounds, String label, int color) {
		graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFF15241A);
		graphics.renderOutline(bounds.left(), bounds.top(), bounds.width(), bounds.height(), color);
		graphics.drawCenteredString(font, label, (bounds.left() + bounds.right()) / 2, bounds.top() + 4, color);
	}

	private void drawFooter(GuiGraphics graphics, Component hint, boolean ignored) {
		var footer = TerminalUiLayout.FOOTER;
		graphics.drawString(font, hint, footer.left(), footer.top() + 3, DIM, false);
	}

	private void drawScope(GuiGraphics graphics) {
		var scope = TerminalUiLayout.SCOPE;
		graphics.fill(scope.left(), scope.top(), scope.right(), scope.bottom(), 0xFF050B08);
		graphics.renderOutline(scope.left(), scope.top(), scope.width(), scope.height(), 0xFF716B43);
		for (int x = scope.left() + 12; x < scope.right(); x += 20)
			graphics.fill(x, scope.top() + 2, x + 1, scope.bottom() - 2, 0x182D6A35);
		for (int y = scope.top() + 12; y < scope.bottom(); y += 16)
			graphics.fill(scope.left() + 2, y, scope.right() - 2, y + 1, 0x182D6A35);
		int centerY = (scope.top() + scope.bottom()) / 2;
		int previous = centerY + (int) Math.round(currentWaveOffset(0, renderNowMillis, renderAge));
		int waveLeft = scope.left() + 4;
		int waveWidth = scope.width() - 8;
		for (int sample = 1; sample < WAVE_SAMPLES; sample++) {
			int value = centerY + (int) Math.round(currentWaveOffset(sample, renderNowMillis, renderAge));
			int x = waveLeft + (int) Math.round((waveWidth - 1) * sample / (double) (WAVE_SAMPLES - 1));
			graphics.fill(x, Math.min(previous, value), x + 1, Math.max(previous, value) + 1,
					currentSignalColor(renderNowMillis));
			previous = value;
		}
		graphics.fill(scope.left() + 2, scope.top() + 2, scope.right() - 2, scope.top() + 3, 0x226FFFFF);
	}

	private void drawCompass(GuiGraphics graphics) {
		var compass = TerminalUiLayout.COMPASS;
		graphics.fill(compass.left(), compass.top(), compass.right(), compass.bottom(), 0xFF080D09);
		graphics.renderOutline(compass.left(), compass.top(), compass.width(), compass.height(), 0xFF716B43);
		int cx = (compass.left() + compass.right()) / 2;
		int cy = (compass.top() + compass.bottom()) / 2;
		graphics.fill(cx, compass.top() + 2, cx + 1, compass.top() + 5, 0xFFB8A96D);
		graphics.fill(compass.left() + 2, cy, compass.left() + 5, cy + 1, 0xFF5A6249);
		graphics.fill(compass.right() - 5, cy, compass.right() - 2, cy + 1, 0xFF5A6249);
		graphics.fill(cx, compass.bottom() - 5, cx + 1, compass.bottom() - 2, 0xFF5A6249);
		drawNeedle(graphics, cx, cy, northNeedle, 9, HOT, 0);
		if (navigation.navigable()) {
			int color = navigation.targetKind() == TerminalNavigationPayload.UNSTABLE_SIGNAL ? 0xFFE5A0A4
					: navigation.targetKind() >= TerminalNavigationPayload.VILLAGE ? GREEN
					: switch (Math.clamp(navigation.targetKind(), 1, 3)) {
				case 2 -> 0xFFE34B46;
				case 3 -> CYAN;
				default -> AMBER;
			};
			drawNeedle(graphics, cx, cy, mineralNeedle, 7, color, navigation.targetKind());
		}
		graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFF2DEA0);
		String front = Component.translatable("terminal.thefourthfrequency.compass.front").getString();
		String right = Component.translatable("terminal.thefourthfrequency.compass.right").getString();
		String back = Component.translatable("terminal.thefourthfrequency.compass.back").getString();
		String left = Component.translatable("terminal.thefourthfrequency.compass.left").getString();
		drawCompassLabel(graphics, front, cx - font.width(front) / 2, compass.top() + 1);
		drawCompassLabel(graphics, right, compass.right() - font.width(right) - 1, cy - font.lineHeight / 2);
		drawCompassLabel(graphics, back, cx - font.width(back) / 2, compass.bottom() - font.lineHeight);
		drawCompassLabel(graphics, left, compass.left() + 1, cy - font.lineHeight / 2);
	}

	private void drawCompassLabel(GuiGraphics graphics, String label, int x, int y) {
		graphics.drawString(font, label, x, y, DIM, false);
	}

	private void drawReceiverSlider(GuiGraphics graphics) {
		var slider = TerminalUiLayout.RECEIVER_SLIDER;
		graphics.fill(slider.left(), slider.top(), slider.right(), slider.bottom(), 0xFF17180F);
		graphics.renderOutline(slider.left(), slider.top(), slider.width(), slider.height(), 0xFF716B43);
		int trackY = (slider.top() + slider.bottom()) / 2;
		graphics.fill(slider.left() + 3, trackY - 1, slider.right() - 3, trackY + 2, 0xFF423D23);
		for (int tick = 0; tick <= 10; tick++) {
			int x = slider.left() + (int) Math.round((slider.width() - 1) * tick / 10.0D);
			graphics.fill(x, slider.top() + 3, x + 1, slider.top() + 7, 0xFF827747);
			graphics.fill(x, slider.bottom() - 7, x + 1, slider.bottom() - 3, 0xFF827747);
		}
		int displayedTuning = (int) Math.round(tuningTransition.valueAt(renderNowMillis));
		int thumb = TerminalUiLayout.sliderX(displayedTuning);
		graphics.fill(thumb - 3, slider.top() + 3, thumb + 4, slider.bottom() - 3, 0xFFB9A561);
		graphics.renderOutline(thumb - 3, slider.top() + 3, 7, slider.height() - 6, 0xFFF1D98B);
	}

	private void drawLcd(GuiGraphics graphics) {
		var lcd = TerminalUiLayout.RECEIVER_LCD;
		graphics.fill(lcd.left(), lcd.top(), lcd.right(), lcd.bottom(), LCD_BACKGROUND);
		graphics.renderOutline(lcd.left(), lcd.top(), lcd.width(), lcd.height(), LCD_BORDER);
		Component lineOne;
		Component lineTwo;
		int lineOneColor = GREEN;
		int lineTwoColor = DIM;
		if (receiverGameplayActive()) {
			int strength = receiverStrength(tuning);
			boolean locked = receiverLocked(tuning);
			lineOne = Component.translatable("terminal.thefourthfrequency.receiver.strength", strength);
			if (!locked) {
				lineTwo = Component.translatable("terminal.thefourthfrequency.receiver.search");
				lineTwoColor = HOT;
			} else if (tools.receiverLockTicks() < 20) {
				lineTwo = Component.translatable("terminal.thefourthfrequency.receiver.locking",
						tools.receiverLockTicks(), 20);
				lineTwoColor = AMBER;
			} else {
				lineTwo = Component.translatable("terminal.thefourthfrequency.receiver.locked");
				lineTwoColor = AMBER;
			}
		} else {
			lineOne = Component.translatable("terminal.thefourthfrequency.receiver.label");
			lineTwo = Component.translatable(tools.receiverAvailable()
					? "terminal.thefourthfrequency.receiver.open_navigation"
					: "terminal.thefourthfrequency.receiver.mechanical_only");
			if (tools.receiverAvailable()) lineTwoColor = HOT;
		}
		graphics.drawString(font, ellipsize(lineOne.getString(), lcd.width() - 8),
				lcd.left() + 4, lcd.top() + 4, lineOneColor, false);
		graphics.drawString(font, ellipsize(lineTwo.getString(), lcd.width() - 8),
				lcd.left() + 4, lcd.top() + 15, lineTwoColor, false);
	}

	private double currentWaveOffset(int sample, long nowMillis, double phaseAge) {
		double progress = tuningTransition.progressAt(nowMillis);
		double target = targetWaveOffset(tuning, sample, phaseAge, nowMillis);
		return waveFromSamples[sample] + (target - waveFromSamples[sample]) * progress;
	}

	private double targetWaveOffset(int tuningValue, int sample, double phaseAge, long nowMillis) {
		double coherence = tools.receiverAvailable() ? receiverStrength(tuningValue) / 100.0D : 0.0D;
		double x = sample;
		double primary = Math.sin((x + phaseAge * 2.1D) * (0.31D - coherence * 0.20D)
				+ tuningValue * 0.085D);
		double secondary = Math.sin(x * (1.17D - coherence * 0.94D) + phaseAge * 0.19D)
				* (0.52D - coherence * 0.28D);
		double receiver = (primary + secondary) * (9.0D - coherence * 3.0D);
		double cycle = positiveModulo(x + phaseAge * 1.35D, 31.0D) / 31.0D;
		double electrocardiogram = Math.sin((x + phaseAge * 1.35D) * 0.08D) * 0.45D
				- gaussian(cycle, 0.18D, 0.045D) * 2.0D
				+ gaussian(cycle, 0.345D, 0.022D) * 3.0D
				- gaussian(cycle, 0.385D, 0.016D) * 16.0D
				+ gaussian(cycle, 0.435D, 0.024D) * 7.0D
				- gaussian(cycle, 0.68D, 0.075D) * 3.5D;
		double morph = waveformMorphTransition.valueAt(nowMillis);
		double pulse = AmbientAnomalyClient.pulse();
		double anomaly = pulse <= 0.0F ? 0.0D : Math.exp(-Math.pow((sample % 43 - 14) / 4.0D, 2.0D)) * pulse * 12.0D;
		return Math.clamp(receiver + (electrocardiogram - receiver) * morph - anomaly * 0.55D,
				-17.0D, 17.0D);
	}

	private int currentSignalColor(long nowMillis) {
		double progress = Math.clamp((nowMillis - waveColorStartedAtMillis) / (double) WAVE_COLOR_MILLIS,
				0.0D, 1.0D);
		progress = progress * progress * (3.0D - 2.0D * progress);
		return lerpColor(waveFromColor, waveTargetColor, progress);
	}

	private int signalColor(int tuningValue) {
		if (tools.receiverAvailable() && receiverLocked(tuningValue)) return AMBER;
		if (tools.receiverAvailable()) return HOT;
		if (snapshot.visualStage() >= 2) return HOT;
		return snapshot.bandStage() > 0 && snapshot.visualStage() > 0 ? CYAN : GREEN;
	}

	private void retargetTuningVisual(int value, long nowMillis) {
		for (int sample = 0; sample < WAVE_SAMPLES; sample++) {
			waveFromSamples[sample] = currentWaveOffset(sample, nowMillis, renderAge);
		}
		tuningTransition.retarget(value, nowMillis);
		tuning = value;
		waveformMorphTransition.retarget(waveformMorphTarget(tuning), nowMillis);
		retargetSignalColor(signalColor(tuning), nowMillis);
	}

	private void retargetSignalColor(int color, long nowMillis) {
		if (color == waveTargetColor) return;
		waveFromColor = currentSignalColor(nowMillis);
		waveTargetColor = color;
		waveColorStartedAtMillis = nowMillis;
	}

	private double waveformMorphTarget(int tuningValue) {
		double base = snapshot.visualStage() >= 2 ? 1.0D : snapshot.visualStage() > 0 ? 0.42D : 0.0D;
		if (!tools.receiverAvailable()) return base;
		double receiver = receiverLocked(tuningValue) ? 0.78D : receiverStrength(tuningValue) * 0.0025D;
		return Math.max(base, receiver);
	}

	private boolean receiverGameplayActive() {
		return (page == TerminalPage.TOOLS || page == TerminalPage.HOME)
				&& selectedTool == TerminalTool.NAVIGATION && tools.available(TerminalTool.NAVIGATION)
				&& tools.receiverAvailable() && !tools.toolsDisabled();
	}

	private boolean receiverMechanicalInteractive() {
		return !tools.toolsDisabled();
	}

	private boolean receiverLocked(int value) {
		return tools.receiverAvailable()
				&& TerminalControlPolicy.receiverLocked(value, tools.receiverTarget());
	}

	private int receiverStrength(int value) {
		return tools.receiverAvailable()
				? TerminalControlPolicy.receiverStrength(value, tools.receiverTarget()) : 0;
	}

	private static double gaussian(double value, double center, double width) {
		double normalized = (value - center) / width;
		return Math.exp(-(normalized * normalized));
	}

	private static double positiveModulo(double value, double divisor) {
		double result = value % divisor;
		return result < 0.0D ? result + divisor : result;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double[] local = local(event.x(), event.y());
		if (TerminalUiLayout.HOME_TAB.contains(local[0], local[1])) return selectPage(TerminalPage.HOME);
		if (TerminalUiLayout.TOOLS_TAB.contains(local[0], local[1])) return selectPage(TerminalPage.TOOLS);
		if (TerminalUiLayout.RECORDS_TAB.contains(local[0], local[1])) return selectPage(TerminalPage.RECORDS);
		if (TerminalUiLayout.FILES_TAB.contains(local[0], local[1])) return selectPage(TerminalPage.FILES);
		if (receiverMechanicalInteractive() && TerminalUiLayout.RECEIVER_SLIDER.contains(local[0], local[1])) {
			draggingTuner = true;
			updateTuningFromSlider(local[0]);
			return true;
		}
		if (page == TerminalPage.HOME) {
			if (selectedTool != null && toolOpenedFromHome) {
				if (TerminalUiLayout.HOME_TOOL_BACK.contains(local[0], local[1])) {
					clearSelectedTool(true);
					return true;
				}
				if (TerminalUiLayout.HOME_TOOL_DETAIL.contains(local[0], local[1])) {
					page = TerminalPage.TOOLS;
					setMode(TerminalControlPolicy.Mode.SIGNAL.ordinal());
					TerminalClientAudio.click();
					return true;
				}
			} else if (TerminalUiLayout.HOME_QUICK_PRIMARY.contains(local[0], local[1])) {
				TerminalTool recommended = tools.recommendedPrimaryTool();
				if (recommended != null) openTool(recommended);
				return true;
			} else if (TerminalUiLayout.HOME_QUICK_SECONDARY.contains(local[0], local[1])) {
				TerminalTool recommended = tools.recommendedSecondaryTool();
				if (recommended != null) openTool(recommended);
				return true;
			}
		}
		if (page == TerminalPage.TOOLS) {
			if (selectedTool != null && TerminalUiLayout.TOOL_BACK.contains(local[0], local[1])) {
				boolean returnHome = toolOpenedFromHome;
				clearSelectedTool(true);
				if (returnHome) {
					page = TerminalPage.HOME;
					setMode(TerminalPage.HOME.wireMode());
				}
				return true;
			}
			if (selectedTool == null) {
				TerminalTool tool = TerminalTool.fromSlot(TerminalUiLayout.toolSlotAt(local[0], local[1]));
				if (tool != null && toolVisible(tool)) {
					openTool(tool);
					return true;
				}
			} else if (handleToolAction(local[0], local[1])) return true;
		}
		if (page == TerminalPage.FILES && handleLogClick(local[0], local[1])) return true;
		return super.mouseClicked(event, doubled);
	}

	private boolean handleToolAction(double x, double y) {
		if (selectedTool == null || !tools.available(selectedTool)
				|| tools.toolsDisabled() && selectedTool != TerminalTool.WEATHER) return false;
		switch (selectedTool) {
			case HOME -> {
				if (TerminalUiLayout.TOOL_OPTION_ONE.contains(x, y)) {
					send(TerminalControlPayload.SET_HOME, 0);
					TerminalClientAudio.click();
					return true;
				}
			}
			case MINERALS -> {
				List<TerminalResource> resources = visibleResources();
				for (int index = 0; index < resources.size(); index++) {
					if (!resourceOptionBounds(index).contains(x, y)) continue;
					send(TerminalControlPayload.SELECT_RESOURCE, resources.get(index).wireId());
					TerminalClientAudio.click();
					return true;
				}
				if (TerminalUiLayout.TOOL_ACTION_PRIMARY.contains(x, y)) {
					send(TerminalControlPayload.REQUEST_RESCAN, 0);
					TerminalClientAudio.click();
					return true;
				}
			}
			case NAVIGATION -> {
				if (new TerminalUiLayout.Bounds(50, 124, 342, 163).contains(x, y)
						&& handleNavigationClick(x, y)) return true;
			}
			case PORTAL, STRONGHOLD, WEATHER -> { }
		}
		if (selectedTool != TerminalTool.WEATHER && selectedTool != TerminalTool.NAVIGATION
				&& TerminalUiLayout.TOOL_ACTION_PRIMARY.contains(x, y)) {
			send(TerminalControlPayload.START_GUIDANCE, selectedTool.slot());
			TerminalClientAudio.click();
			return true;
		}
		if (selectedTool != TerminalTool.WEATHER
				&& TerminalUiLayout.TOOL_ACTION_SECONDARY.contains(x, y)) {
			send(TerminalControlPayload.STOP_GUIDANCE, 0);
			TerminalClientAudio.click();
			return true;
		}
		return false;
	}

	private boolean handleNavigationClick(double x, double y) {
		for (NavigationHit hit : navigationHits) {
			if (!hit.bounds().contains(x, y)) continue;
			send(hit.action(), hit.value());
			TerminalClientAudio.click();
			return true;
		}
		return false;
	}

	private boolean handleLogClick(double x, double y) {
		int index = TerminalUiLayout.fileIndexAt(x, y, fileListScroll, snapshot.files().size());
		if (index >= 0) {
			openDirectoryEntry(index);
			return true;
		}
		if (logView == LogView.PASSWORD && TerminalUiLayout.FILE_CONTENT.contains(x, y)) {
			return handlePasswordClick(x, y);
		}
		return TerminalUiLayout.FILE_BODY.contains(x, y);
	}

	private void openDirectoryEntry(int index) {
		if (index < 0 || index >= snapshot.files().size()) return;
		selectedFile = index;
		TerminalFilePayload file = snapshot.files().get(index);
		detailFile = file;
		fileContentScroll = 0;
		if (file.id().equals("encrypted_witness_file")) {
			logView = file.unlocked() ? LogView.DETAIL : allFragmentsReceived() ? LogView.PASSWORD : LogView.FRAGMENTS;
			if (file.unlocked()) send(TerminalControlPayload.READ_TRUTH_FILE, 0);
		} else logView = LogView.DETAIL;
		TerminalClientAudio.click();
	}

	private void openDetail(TerminalFilePayload file) {
		logView = LogView.DETAIL;
		detailFile = file;
		fileContentScroll = 0;
		if (file.id().equals("encrypted_witness_file") && file.unlocked()) {
			send(TerminalControlPayload.READ_TRUTH_FILE, 0);
		}
	}

	private boolean allFragmentsReceived() {
		for (int fragment = 0; fragment < 4; fragment++) if (!snapshot.fragmentReceived(fragment)) return false;
		return true;
	}

	private boolean handleSignalClick(double x, double y) {
		for (SignalHit hit : signalHits) {
			if (!hit.bounds().contains(x, y)) continue;
			if (hit.navigationValue() >= 0) {
				send(TerminalControlPayload.SELECT_FRAGMENT_TARGET, hit.navigationValue());
				TerminalClientAudio.click();
				return true;
			}
			if (hit.header()) {
				expandedSignalCard = expandedSignalCard.equals(hit.cardKey()) ? "" : hit.cardKey();
				selectedSignalCard = signalCardKeys.indexOf(hit.cardKey());
				scrollRow = 0;
				TerminalClientAudio.click();
				return true;
			}
		}
		return false;
	}

	private boolean handlePasswordClick(double x, double y) {
		for (int digit = 0; digit < 4; digit++) {
			if (digitBounds(digit).contains(x, y)) {
				if (password.length() < 4) password.append(digit);
				passwordResult = -1;
				TerminalClientAudio.passwordKey();
				return true;
			}
		}
		if (clearBounds().contains(x, y)) {
			password.setLength(0);
			passwordResult = -1;
			TerminalClientAudio.passwordKey();
			return true;
		}
		if (submitBounds().contains(x, y)) {
			submitPassword();
			return true;
		}
		return false;
	}

	private void submitPassword() {
		if (password.length() != 4) {
			passwordResult = ArchivePasswordResultPayload.INVALID;
			return;
		}
		if (ClientPlayNetworking.canSend(ArchivePasswordPayload.TYPE)) {
			ClientPlayNetworking.send(new ArchivePasswordPayload(password.toString()));
			TerminalClientAudio.passwordKey();
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		double[] local = local(event.x(), event.y());
		if (draggingTuner) {
			updateTuningFromSlider(local[0]);
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingTuner) {
			draggingTuner = false;
			TerminalClientAudio.endTuningInput();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount == 0.0D) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		int delta = verticalAmount > 0.0D ? -1 : 1;
		double[] local = local(mouseX, mouseY);
		if (page == TerminalPage.RECORDS && TerminalUiLayout.RECORDS_BODY.contains(local[0], local[1])) {
			recordsScrollRow = TerminalUiLayout.scroll(recordsScrollRow, delta, recordsMaxScrollRow);
		} else if (page == TerminalPage.FILES && TerminalUiLayout.FILE_LIST.contains(local[0], local[1])) {
			fileListScroll = TerminalUiLayout.scroll(fileListScroll, delta,
					TerminalUiLayout.fileMaxScrollRow(snapshot.files().size()));
		} else if (page == TerminalPage.FILES && TerminalUiLayout.FILE_CONTENT.contains(local[0], local[1])) {
			fileContentScroll = TerminalUiLayout.scroll(fileContentScroll, delta, fileContentMaxScroll);
		} else if (receiverMechanicalInteractive() && TerminalUiLayout.RECEIVER_SLIDER.contains(local[0], local[1])) {
			setTuning(tuning - delta);
		} else return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() >= GLFW.GLFW_KEY_1 && event.key() <= GLFW.GLFW_KEY_4) {
			selectPage(TerminalPage.fromIndex(event.key() - GLFW.GLFW_KEY_1));
			return true;
		}
		if (receiverMechanicalInteractive()
				&& (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_RIGHT)) {
			setTuning(tuning + (event.key() == GLFW.GLFW_KEY_LEFT ? -1 : 1));
			return true;
		}
		if (page == TerminalPage.RECORDS && (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN)) {
			recordsScrollRow = TerminalUiLayout.scroll(recordsScrollRow,
					event.key() == GLFW.GLFW_KEY_UP ? -1 : 1, recordsMaxScrollRow);
			return true;
		}
		if (page == TerminalPage.FILES) {
			if (event.key() == GLFW.GLFW_KEY_ENTER) {
				openDirectoryEntry(selectedFile);
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
				moveFileSelection(event.key() == GLFW.GLFW_KEY_UP ? -1 : 1);
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
				fileContentScroll = TerminalUiLayout.scroll(fileContentScroll,
						event.key() == GLFW.GLFW_KEY_PAGE_UP ? -3 : 3, fileContentMaxScroll);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private boolean selectPage(TerminalPage next) {
		if (next == page) {
			if (selectedTool != null && (next == TerminalPage.TOOLS || next == TerminalPage.HOME)) {
				clearSelectedTool(true);
			}
			return true;
		}
		TerminalPage previous = page;
		if (selectedTool != null) clearSelectedTool(false);
		page = next;
		if (next == TerminalPage.RECORDS) send(TerminalControlPayload.MARK_RECORDS_READ, 0);
		if (next == TerminalPage.FILES && previous != TerminalPage.FILES) resetLogView();
		recordsScrollRow = next == TerminalPage.RECORDS ? recordsScrollRow : 0;
		TerminalClientAudio.click();
		setMode(next.wireMode());
		return true;
	}

	private void openTool(TerminalTool tool) {
		if (!toolVisible(tool)) return;
		boolean fromHome = page == TerminalPage.HOME;
		toolOpenedFromHome = fromHome;
		localLockedTool = !tools.available(tool);
		selectedTool = tool;
		if (tools.available(tool)) send(TerminalControlPayload.SELECT_TOOL, tool.slot());
		else send(TerminalControlPayload.SELECT_TOOL, TerminalToolService.NO_TOOL);
		if (!fromHome) {
			page = TerminalPage.TOOLS;
			setMode(TerminalControlPolicy.Mode.SIGNAL.ordinal());
		}
		TerminalClientAudio.click();
	}

	private void clearSelectedTool(boolean audio) {
		if (selectedTool != null) send(TerminalControlPayload.SELECT_TOOL, TerminalToolService.NO_TOOL);
		selectedTool = null;
		toolOpenedFromHome = false;
		localLockedTool = false;
		navigationHits.clear();
		if (audio) TerminalClientAudio.click();
	}

	private void setMode(int value) {
		int safe = TerminalControlPolicy.mode(value);
		if (safe == mode) return;
		mode = safe;
		send(TerminalControlPayload.MODE, mode);
	}

	private void setTuning(int value) {
		if (!receiverMechanicalInteractive()) return;
		int safe = TerminalControlPolicy.tuning(value);
		if (safe == tuning) return;
		boolean gameplay = receiverGameplayActive();
		boolean receiverLockBefore = gameplay && receiverLocked(tuning);
		retargetTuningVisual(safe, nowMillis());
		boolean receiverLockAfter = gameplay && receiverLocked(tuning);
		TerminalClientAudio.tuningInput();
		if (!receiverLockBefore && receiverLockAfter) TerminalClientAudio.lock();
		if (gameplay) {
			localTuningOnly = false;
			send(TerminalControlPayload.TUNE, tuning);
		} else {
			localTuningOnly = true;
		}
	}

	private void updateTuningFromSlider(double x) {
		setTuning(TerminalUiLayout.sliderTuning(x));
	}

	private void moveFileSelection(int delta) {
		int total = snapshot.files().size();
		if (total == 0) return;
		selectedFile = selectedFile < 0 ? (delta < 0 ? total - 1 : 0)
				: Math.clamp(selectedFile + delta, 0, total - 1);
		if (selectedFile < fileListScroll) fileListScroll = selectedFile;
		if (selectedFile >= fileListScroll + TerminalUiLayout.FILE_LIST_VISIBLE_ROWS) {
			fileListScroll = selectedFile - TerminalUiLayout.FILE_LIST_VISIBLE_ROWS + 1;
		}
		fileListScroll = Math.clamp(fileListScroll, 0, TerminalUiLayout.fileMaxScrollRow(total));
		TerminalClientAudio.click();
	}

	private void resetLogView() {
		logView = LogView.DIRECTORY;
		fileListScroll = 0;
		fileContentScroll = 0;
		fileContentMaxScroll = 0;
		selectedFile = -1;
		detailFile = null;
		detailFromFragments = false;
		password.setLength(0);
		passwordResult = -1;
	}

	private void scrollBy(int delta) {
		if (page == TerminalPage.RECORDS) {
			recordsScrollRow = TerminalUiLayout.scroll(recordsScrollRow, delta, recordsMaxScrollRow);
		} else if (page == TerminalPage.FILES) {
			fileContentScroll = TerminalUiLayout.scroll(fileContentScroll, delta, fileContentMaxScroll);
		} else scrollRow = TerminalUiLayout.scroll(scrollRow, delta, maxScrollRow);
	}

	private void send(int action, int value) {
		if (ClientPlayNetworking.canSend(TerminalControlPayload.TYPE)) {
			ClientPlayNetworking.send(new TerminalControlPayload(action, value));
		}
	}

	public void selectModeForTesting(int value) { selectPage(TerminalPage.initialPage(value)); }
	public void selectPageForTesting(int value) { selectPage(TerminalPage.fromIndex(value)); }
	public void setTuningForTesting(int value) { setTuning(value); }
	public String objectiveIdForTesting() { return snapshot.objectiveId(); }
	public void openToolForTesting(int slot) {
		TerminalTool tool = TerminalTool.fromSlot(slot);
		if (tool != null) openTool(tool);
	}
	public void setHomeForTesting() { send(TerminalControlPayload.SET_HOME, 0); }
	public void selectResourceForTesting(int value) { send(TerminalControlPayload.SELECT_RESOURCE, value); }
	public void startGuidanceForTesting(int slot) { send(TerminalControlPayload.START_GUIDANCE, slot); }
	public void stopGuidanceForTesting() { send(TerminalControlPayload.STOP_GUIDANCE, 0); }
	public void selectCacheForTesting(int value) {
		selectPage(TerminalPage.FILES);
		openDirectoryEntry(Math.clamp(value, 0, 1));
	}
	public void openLogEntryForTesting(int value) {
		selectPage(TerminalPage.FILES);
		openDirectoryEntry(Math.max(0, value));
	}
	public void openLogDirectoryForTesting() {
		selectPage(TerminalPage.FILES);
		resetLogView();
	}
	public void openWitnessFragmentsForTesting() {
		selectPage(TerminalPage.FILES);
		int index = -1;
		for (int current = 0; current < snapshot.files().size(); current++) {
			if (snapshot.files().get(current).id().equals("encrypted_witness_file")) index = current;
		}
		openDirectoryEntry(index);
	}
	public void openFragmentForTesting(int fragment) {
		TerminalFilePayload file = snapshot.fragmentFile(fragment);
		if (file != null) {
			detailFromFragments = true;
			openDetail(file);
		}
	}
	public void openCompleteFileForTesting() {
		TerminalFilePayload parent = snapshot.directoryFiles().stream()
				.filter(file -> file.id().equals("encrypted_witness_file") && file.unlocked()).findFirst().orElse(null);
		if (parent != null) {
			detailFromFragments = false;
			openDetail(parent);
		}
	}
	public void expandFirstSignalCardForTesting() {
		if (!signalCardKeys.isEmpty()) {
			selectedSignalCard = 0;
			expandedSignalCard = signalCardKeys.getFirst();
			scrollRow = 0;
		}
	}
	public void markAllReadForTesting() { send(TerminalControlPayload.MARK_RECORDS_READ, 0); }
	public void enterPasswordForTesting(String digits) {
		password.setLength(0);
		passwordResult = -1;
		for (int index = 0; index < digits.length() && password.length() < 4; index++) {
			char digit = digits.charAt(index);
			if (digit >= '0' && digit <= '3') password.append(digit);
		}
	}
	public void submitPasswordForTesting() { submitPassword(); }
	public int passwordResultForTesting() { return passwordResult; }
	public int unreadCountForTesting() { return snapshot.unreadCount(); }
	public String logViewForTesting() { return logView.name(); }
	public void scrollRowsForTesting(int rows) { scrollBy(rows); }
	public int modeForTesting() { return mode; }
	public String pageForTesting() { return page.name(); }
	public boolean toolAvailableForTesting(int slot) {
		TerminalTool tool = TerminalTool.fromSlot(slot);
		return tool != null && tools.available(tool);
	}
	public int selectedResourceForTesting() { return tools.selectedResource().wireId(); }
	public int guidanceToolForTesting() {
		TerminalTool tool = tools.guidanceTool();
		return tool == null ? TerminalToolService.NO_TOOL : tool.slot();
	}
	public int tuningForTesting() { return tuning; }
	public double displayedTuningForTesting(long nowMillis) { return tuningTransition.valueAt(nowMillis); }
	public int selectedFileForTesting() { return selectedFile; }
	public int fileScrollRowForTesting() { return fileListScroll; }
	public int fileContentScrollForTesting() { return fileContentScroll; }
	public int fileCountForTesting() { return snapshot.files().size(); }
	public String fileIdForTesting(int index) { return snapshot.files().get(index).id(); }
	public double waveformMorphTargetForTesting() { return waveformMorphTarget(tuning); }
	public boolean navigationActiveForTesting() { return navigation.navigable(); }
	public void moveFileSelectionForTesting(int delta) { moveFileSelection(delta); }
	public void openSelectedFileForTesting() { openDirectoryEntry(selectedFile); }

	@Override
	public void onClose() {
		TerminalClientAudio.endTuningInput();
		if (!closedByServer) send(TerminalControlPayload.CLOSE, 0);
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() { return false; }

	private List<StyledRow> styledRows(List<Component> lines, int width, int color, int indent, boolean marker) {
		List<StyledRow> rows = new ArrayList<>();
		for (Component line : lines) {
			List<FormattedCharSequence> wrapped = font.split(line, Math.max(1, width));
			if (wrapped.isEmpty()) rows.add(new StyledRow(null, color, indent, false));
			for (int i = 0; i < wrapped.size(); i++) {
				rows.add(new StyledRow(wrapped.get(i), color, indent, marker && i == 0));
			}
		}
		return rows;
	}

	private void drawRows(GuiGraphics graphics, List<StyledRow> rows,
			TerminalUiLayout.Bounds bounds, int rowHeight, boolean scrollable) {
		int visible = Math.max(1, bounds.height() / rowHeight);
		maxScrollRow = scrollable ? Math.max(0, rows.size() - visible) : 0;
		scrollRow = Math.clamp(scrollRow, 0, maxScrollRow);
		int y = bounds.top();
		for (int i = scrollRow; i < rows.size() && i < scrollRow + visible; i++) {
			StyledRow row = rows.get(i);
			if (row.marker()) graphics.fill(bounds.left(), y + 2, bounds.left() + 2, y + 7, row.color());
			if (row.text() != null) graphics.drawString(font, row.text(), bounds.left() + row.indent(), y, row.color(), false);
			y += rowHeight;
		}
	}

	private void drawCenteredFitted(GuiGraphics graphics, Component text, TerminalUiLayout.Bounds bounds, int color) {
		List<FormattedCharSequence> fitted = font.split(text, Math.max(1, bounds.width() - 6));
		if (!fitted.isEmpty()) {
			FormattedCharSequence line = fitted.getFirst();
			graphics.drawString(font, line, bounds.left() + Math.max(3, (bounds.width() - font.width(line)) / 2),
					bounds.top() + Math.max(3, (bounds.height() - font.lineHeight) / 2), color, false);
		}
	}

	private int pageAccent() {
		return snapshot.visualStage() >= 2 ? HOT : snapshot.visualStage() == 1 ? CYAN : AMBER;
	}

	private static int lerpColor(int from, int to, double progress) {
		int a = lerpChannel(from >>> 24, to >>> 24, progress);
		int r = lerpChannel(from >>> 16 & 0xFF, to >>> 16 & 0xFF, progress);
		int g = lerpChannel(from >>> 8 & 0xFF, to >>> 8 & 0xFF, progress);
		int b = lerpChannel(from & 0xFF, to & 0xFF, progress);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int lerpChannel(int from, int to, double progress) {
		return (int) Math.round(from + (to - from) * progress);
	}

	private static void drawNeedle(GuiGraphics graphics, int cx, int cy, double degrees, int length, int color, int style) {
		double radians = Math.toRadians(degrees);
		int endX = cx;
		int endY = cy;
		for (int step = 2; step <= length; step++) {
			endX = cx + (int) Math.round(Math.sin(radians) * step);
			endY = cy - (int) Math.round(Math.cos(radians) * step);
			graphics.fill(endX, endY, endX + 1, endY + 1, color);
		}
		switch (style) {
			case 1 -> graphics.fill(endX - 1, endY - 1, endX + 2, endY + 2, color);
			case 2 -> {
				graphics.fill(endX - 2, endY, endX + 3, endY + 1, color);
				graphics.fill(endX, endY - 2, endX + 1, endY + 3, color);
			}
			case 3 -> {
				graphics.fill(endX, endY - 2, endX + 1, endY + 3, color);
				graphics.fill(endX - 2, endY, endX + 3, endY + 1, color);
			}
			default -> {
				graphics.fill(endX - 1, endY, endX + 2, endY + 1, color);
				graphics.fill(endX, endY - 1, endX + 1, endY + 2, color);
			}
		}
	}

	private void drawFileIcon(GuiGraphics graphics, TerminalFilePayload file, int x, int y) {
		int accent = file.unlocked() ? pageAccent() : DIM;
		graphics.fill(x + 3, y + 2, x + 20, y + 23, 0xFF26372A);
		graphics.renderOutline(x + 3, y + 2, 17, 21, accent);
		graphics.fill(x + 15, y + 3, x + 19, y + 7, 0xFF101A12);
		graphics.fill(x + 6, y + 8, x + 17, y + 9, 0xFF6E8B6F);
		graphics.fill(x + 6, y + 12, x + 16, y + 13, 0xFF6E8B6F);
		graphics.fill(x + 6, y + 16, x + 14, y + 17, 0xFF6E8B6F);
		if (!file.unlocked() && !file.id().equals("encrypted_witness_file")) {
			int lockAccent = snapshot.visualStage() >= 2 ? HOT : AMBER;
			graphics.fill(x + 13, y + 10, x + 25, y + 24, 0xE8070B08);
			graphics.fill(x + 16, y + 8, x + 22, y + 9, lockAccent);
			graphics.fill(x + 15, y + 9, x + 17, y + 14, lockAccent);
			graphics.fill(x + 21, y + 9, x + 23, y + 14, lockAccent);
			graphics.fill(x + 13, y + 13, x + 25, y + 24, 0xFF151C17);
			graphics.renderOutline(x + 13, y + 13, 12, 11, lockAccent);
			graphics.fill(x + 18, y + 17, x + 20, y + 20, lockAccent);
			graphics.fill(x + 19, y + 20, x + 20, y + 22, lockAccent);
		}
		if (file.id().contains("warning")) {
			graphics.fill(x, y + 14, x + 7, y + 22, HOT);
			graphics.fill(x + 3, y + 16, x + 4, y + 19, 0xFFFFFFFF);
			graphics.fill(x + 3, y + 20, x + 4, y + 21, 0xFFFFFFFF);
		}
	}

	private List<String> titleLines(String raw, int width) {
		String text = raw == null ? "" : raw.strip();
		List<String> lines = new ArrayList<>(2);
		for (int line = 0; line < 2 && !text.isEmpty(); line++) {
			if (font.width(text) <= width) {
				lines.add(text);
				text = "";
				break;
			}
			int cut = fittingPrefix(text, width);
			if (cut <= 0) break;
			String part = text.substring(0, cut).stripTrailing();
			text = text.substring(cut).stripLeading();
			lines.add(line == 1 && !text.isEmpty() ? ellipsize(part + "…", width) : part);
		}
		if (lines.isEmpty()) lines.add(ellipsize(raw, width));
		return List.copyOf(lines);
	}

	private String ellipsize(String raw, int width) {
		String text = raw == null ? "" : raw;
		if (font.width(text) <= width) return text;
		String suffix = "…";
		int cut = fittingPrefix(text, Math.max(1, width - font.width(suffix)));
		return text.substring(0, Math.max(0, cut)).stripTrailing() + suffix;
	}

	private int fittingPrefix(String text, int width) {
		int cut = 0;
		while (cut < text.length() && font.width(text.substring(0, cut + 1)) <= width) cut++;
		return cut;
	}

	private static TerminalUiLayout.Bounds digitBounds(int digit) {
		int left = 149 + digit * 46;
		return new TerminalUiLayout.Bounds(left, 127, left + 38, 145);
	}

	private static TerminalUiLayout.Bounds clearBounds() {
		return new TerminalUiLayout.Bounds(149, 150, 232, 169);
	}

	private static TerminalUiLayout.Bounds submitBounds() {
		return new TerminalUiLayout.Bounds(241, 150, 336, 169);
	}

	private static TerminalUiLayout.Bounds fragmentBounds(int fragment) {
		int top = 94 + Math.clamp(fragment, 0, 3) * 22;
		return new TerminalUiLayout.Bounds(143, top, 342, top + 18);
	}

	private static TerminalUiLayout.Bounds fragmentCompleteBounds() {
		return new TerminalUiLayout.Bounds(143, 182, 342, 195);
	}

	private float panelScale() {
		return Math.min(2.0F, Math.max(0.55F,
				Math.min((width - 16) / (float) BASE_WIDTH, (height - 16) / (float) BASE_HEIGHT)));
	}

	private double[] local(double mouseX, double mouseY) {
		float scale = panelScale();
		return new double[]{
				(mouseX - (width - BASE_WIDTH * scale) / 2.0D) / scale,
				(mouseY - (height - BASE_HEIGHT * scale) / 2.0D) / scale};
	}

	private static long nowMillis() { return System.nanoTime() / 1_000_000L; }

	private enum LogView { DIRECTORY, DETAIL, FRAGMENTS, PASSWORD }
	private record StyledRow(FormattedCharSequence text, int color, int indent, boolean marker) { }
	private record CardDetail(Component text, int navigation) { }
	private record SignalRow(FormattedCharSequence text, int color, boolean marker, String cardKey,
			boolean header, int navigationValue) { }
	private record SignalHit(TerminalUiLayout.Bounds bounds, String cardKey, boolean header, int navigationValue) { }
	private record NavigationHit(TerminalUiLayout.Bounds bounds, int action, int value) { }
}
