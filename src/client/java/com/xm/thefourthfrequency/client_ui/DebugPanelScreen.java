package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.DebugActionPayload;
import com.xm.thefourthfrequency.networking.DebugStatusPayload;
import com.xm.thefourthfrequency.terminal.AnomalyCatalog;
import com.xm.thefourthfrequency.terminal.DebugNames;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class DebugPanelScreen extends Screen {
	private static final int SCREEN_MARGIN = 12;
	private static final int MAX_PANEL_WIDTH = 700;
	private static final int MAX_PANEL_HEIGHT = 400;
	private static final int MIN_SIDEBAR_WIDTH = 88;
	private static final int MAX_SIDEBAR_WIDTH = 112;
	private static final int HEADER_HEIGHT = 40;
	private static final int FOOTER_HEIGHT = 34;
	private static final int CONTENT_GAP = 10;
	private static final int NAV_BUTTON_HEIGHT = 22;
	private static final int NAV_BUTTON_GAP = 5;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;
	private static final int ANOMALY_INFO_HEIGHT = 58;
	private static final int ANOMALY_ROW_HEIGHT = 26;
	private static final int ENDING_GROUP_TITLE_HEIGHT = 14;
	private static final int ENDING_ROW_HEIGHT = 24;
	private static final int ENDING_GROUP_GAP = 8;

	private static final int OVERLAY = 0xCC05090B;
	private static final int PANEL = 0xFF10181B;
	private static final int HEADER = 0xFF131F22;
	private static final int SIDEBAR = 0xFF0B1113;
	private static final int CARD = 0xFF172225;
	private static final int CARD_ACTIVE = 0xFF1C3033;
	private static final int BORDER = 0xFF31575A;
	private static final int ACCENT = 0xFF6AC8C9;
	private static final int ACCENT_BRIGHT = 0xFF91E5E5;
	private static final int TEXT = 0xFFE6F0F0;
	private static final int MUTED = 0xFF91A7A8;
	private static final int WARNING = 0xFFE8C48A;
	private static final int DANGER = 0xFFFF6A63;

	private static final List<String> ANOMALIES = AnomalyCatalog.definitions().stream()
			.map(value -> value.id()).toList();
	private static final List<ActionSpec> ALTAR_ACTIONS = List.of(
			new ActionSpec("已找到祭坛", "altar_state", "", 1, false),
			new ActionSpec("开始祭坛战", "altar_state", "", 2, false),
			new ActionSpec("装置剩 1 个", "altar_state", "", 3, false),
			new ActionSpec("重置祭坛", "altar_state", "", 0, true));
	private static final List<ActionSpec> ENDING_ACTIONS = List.of(
			new ActionSpec("战斗中", "ending_state", "", 0, true),
			new ActionSpec("未读真相结局", "ending_state", "", 1, true),
			new ActionSpec("保护失败结局", "ending_state", "", 2, true),
			new ActionSpec("保护成功结局", "ending_state", "", 3, true));
	private static final List<ActionSpec> BOSS_ACTIONS = List.of(
			new ActionSpec("生命 25%", "boss_health", "", 25, false),
			new ActionSpec("生命 100%", "boss_health", "", 100, false));

	private static PageMemory pageMemory = new PageMemory(0, 0, 0);
	private DebugStatusPayload status;
	private Section section;
	private int anomalyScrollRow;
	private int anomalyMaxScrollRow;
	private int endingScroll;
	private int endingMaxScroll;
	private Pending pending;

	public DebugPanelScreen(DebugStatusPayload status) {
		super(Component.literal("第四频段 · 测试工作台"));
		this.status = status;
		this.section = Section.values()[Math.clamp(pageMemory.sectionIndex, 0, Section.values().length - 1)];
		this.anomalyScrollRow = Math.max(0, pageMemory.anomalyScrollRow);
		this.endingScroll = Math.max(0, pageMemory.endingScroll);
	}

	public void update(DebugStatusPayload payload) {
		this.status = payload;
	}

	@Override
	protected void init() {
		Layout layout = layout();
		calculateScrollLimits(layout);
		if (pending != null) {
			buildConfirmation(layout);
			return;
		}
		buildNavigation(layout);
		switch (section) {
			case OVERVIEW -> { }
			case PROGRESS -> buildProgressActions(layout);
			case ANOMALIES -> buildAnomalyActions(layout);
			case ENDING -> buildEndingActions(layout);
		}
		buildFooterActions(layout);
	}

	private void calculateScrollLimits(Layout layout) {
		int listHeight = Math.max(ANOMALY_ROW_HEIGHT, layout.contentBottom - anomalyListTop(layout));
		int visibleRows = Math.max(1, listHeight / ANOMALY_ROW_HEIGHT);
		anomalyMaxScrollRow = Math.max(0, ANOMALIES.size() - visibleRows);
		anomalyScrollRow = Math.clamp(anomalyScrollRow, 0, anomalyMaxScrollRow);

		int columns = endingColumns(layout);
		int totalHeight = endingContentHeight(columns);
		endingMaxScroll = Math.max(0, totalHeight - layout.contentHeight());
		endingScroll = Math.clamp(endingScroll, 0, endingMaxScroll);
	}

	private void buildNavigation(Layout layout) {
		int y = layout.top + HEADER_HEIGHT + 28;
		for (Section value : Section.values()) {
			Button button = Button.builder(Component.literal(value.label), ignored -> {
				section = value;
				pending = null;
				rememberPage();
				rebuildWidgets();
			}).bounds(layout.left + 10, y, layout.sidebarWidth - 20, NAV_BUTTON_HEIGHT).build();
			button.active = value != section;
			addRenderableWidget(button);
			y += NAV_BUTTON_HEIGHT + NAV_BUTTON_GAP;
		}
	}

	private void buildFooterActions(Layout layout) {
		int y = layout.footerTop + 7;
		addRenderableWidget(Button.builder(Component.literal("刷新"), ignored -> send("refresh", "", 0))
				.bounds(layout.right() - 136, y, 60, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("关闭"), ignored -> onClose())
				.bounds(layout.right() - 70, y, 60, BUTTON_HEIGHT).build());
	}

	private void buildProgressActions(Layout layout) {
		int y = layout.contentTop + (layout.contentHeight() < 150 ? 48 : 70);
		if (layout.contentWidth() >= 280) {
			int width = (layout.contentWidth() - BUTTON_GAP) / 2;
			actionButton(layout.contentLeft, y, width, new ActionSpec("完成前期准备", "prelude_ready", "", 0, false));
			actionButton(layout.contentLeft + width + BUTTON_GAP, y, width,
					new ActionSpec("主线前进一步", "progress_next", "", 0, false));
			actionButton(layout.contentLeft, y + BUTTON_HEIGHT + BUTTON_GAP, layout.contentWidth(),
					new ActionSpec("重置个人主线", "progress_reset", "", 0, true));
		} else {
			actionButton(layout.contentLeft, y, layout.contentWidth(),
					new ActionSpec("完成前期准备", "prelude_ready", "", 0, false));
			actionButton(layout.contentLeft, y + BUTTON_HEIGHT + BUTTON_GAP, layout.contentWidth(),
					new ActionSpec("主线前进一步", "progress_next", "", 0, false));
			actionButton(layout.contentLeft, y + (BUTTON_HEIGHT + BUTTON_GAP) * 2, layout.contentWidth(),
					new ActionSpec("重置个人主线", "progress_reset", "", 0, true));
		}
	}

	private void buildAnomalyActions(Layout layout) {
		int toolbarY = layout.contentTop + 31;
		int toolbarWidth = (layout.contentWidth() - BUTTON_GAP) / 2;
		actionButton(layout.contentLeft, toolbarY, toolbarWidth,
				new ActionSpec("停止当前异象", "anomaly_stop", "", 0, true));
		actionButton(layout.contentLeft + toolbarWidth + BUTTON_GAP, toolbarY, toolbarWidth,
				new ActionSpec("恢复自动触发", "anomaly_resume", "", 0, false));

		int listTop = anomalyListTop(layout);
		int visibleRows = Math.max(1, (layout.contentBottom - listTop) / ANOMALY_ROW_HEIGHT);
		int buttonWidth = Math.clamp((layout.contentWidth() - 18) / 5, 42, 56);
		int strongestX = layout.contentRight - 8 - buttonWidth;
		int triggerX = strongestX - BUTTON_GAP - buttonWidth;
		for (int row = 0; row < visibleRows; row++) {
			int index = anomalyScrollRow + row;
			if (index >= ANOMALIES.size()) break;
			String id = ANOMALIES.get(index);
			int y = listTop + row * ANOMALY_ROW_HEIGHT + 3;
			boolean destructive = AnomalyCatalog.require(id).destructive();
			actionButton(triggerX, y, buttonWidth,
					new ActionSpec("触发", "anomaly", id, 0, destructive));
			actionButton(strongestX, y, buttonWidth,
					new ActionSpec("最强", "anomaly", id, 1, true));
		}
	}

	private void buildEndingActions(Layout layout) {
		int columns = endingColumns(layout);
		int y = layout.contentTop - endingScroll;
		y = buildEndingGroup(layout, y, columns, ALTAR_ACTIONS);
		y += ENDING_GROUP_GAP;
		y = buildEndingGroup(layout, y, columns, ENDING_ACTIONS);
		y += ENDING_GROUP_GAP;
		buildEndingGroup(layout, y, columns, BOSS_ACTIONS);
	}

	private int buildEndingGroup(Layout layout, int groupTop, int columns, List<ActionSpec> actions) {
		int availableWidth = layout.contentWidth() - (endingMaxScroll > 0 ? 8 : 0);
		int buttonWidth = (availableWidth - BUTTON_GAP * (columns - 1)) / columns;
		int buttonsTop = groupTop + ENDING_GROUP_TITLE_HEIGHT;
		for (int index = 0; index < actions.size(); index++) {
			int row = index / columns;
			int column = index % columns;
			int x = layout.contentLeft + column * (buttonWidth + BUTTON_GAP);
			int y = buttonsTop + row * ENDING_ROW_HEIGHT;
			if (y >= layout.contentTop && y + BUTTON_HEIGHT <= layout.contentBottom)
				actionButton(x, y, buttonWidth, actions.get(index));
		}
		return groupTop + endingGroupHeight(actions.size(), columns);
	}

	private void actionButton(int x, int y, int width, ActionSpec action) {
		addRenderableWidget(Button.builder(Component.literal(action.label), ignored -> {
			if (action.confirm) {
				pending = new Pending(action.label, action.action, action.target, action.value);
				rebuildWidgets();
			} else send(action.action, action.target, action.value);
		}).bounds(x, y, width, BUTTON_HEIGHT).build());
	}

	private void buildConfirmation(Layout layout) {
		Modal modal = modal(layout);
		int buttonY = modal.bottom() - 30;
		addRenderableWidget(Button.builder(Component.literal("确认执行"), ignored -> {
			Pending action = pending;
			pending = null;
			rebuildWidgets();
			send(action.action, action.target, action.value);
		}).bounds(modal.left + 18, buttonY, (modal.width - 42) / 2, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("取消"), ignored -> cancelConfirmation())
				.bounds(modal.left + 24 + (modal.width - 42) / 2, buttonY,
						(modal.width - 42) / 2, BUTTON_HEIGHT).build());
	}

	private void cancelConfirmation() {
		pending = null;
		rebuildWidgets();
	}

	private void send(String action, String target, int value) {
		if (ClientPlayNetworking.canSend(DebugActionPayload.TYPE)) {
			if (action.equals("anomaly")) DebugPanelClient.expectAnomalyResponse(target);
			ClientPlayNetworking.send(new DebugActionPayload(action, target, value));
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Layout layout = layout();
		graphics.fill(0, 0, width, height, OVERLAY);
		graphics.fill(layout.left, layout.top, layout.right(), layout.bottom(), PANEL);
		graphics.fill(layout.left, layout.top, layout.right(), layout.top + HEADER_HEIGHT, HEADER);
		graphics.fill(layout.left, layout.top + HEADER_HEIGHT, layout.left + layout.sidebarWidth,
				layout.footerTop, SIDEBAR);
		graphics.fill(layout.left, layout.footerTop, layout.right(), layout.bottom(), HEADER);
		graphics.renderOutline(layout.left, layout.top, layout.panelWidth, layout.panelHeight, ACCENT);
		graphics.fill(layout.left + layout.sidebarWidth, layout.top + HEADER_HEIGHT,
				layout.left + layout.sidebarWidth + 1, layout.footerTop, BORDER);

		renderHeader(graphics, layout);
		renderNavigation(graphics, layout);
		switch (section) {
			case OVERVIEW -> renderOverview(graphics, layout);
			case PROGRESS -> renderProgress(graphics, layout);
			case ANOMALIES -> renderAnomalies(graphics, layout);
			case ENDING -> renderEnding(graphics, layout);
		}
		renderFooter(graphics, layout);

		if (pending != null) renderConfirmation(graphics, layout);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderHeader(GuiGraphics graphics, Layout layout) {
		graphics.drawString(font, title, layout.left + 12, layout.top + 9, ACCENT_BRIGHT, false);
		graphics.drawString(font, Component.literal("仅供开发测试"), layout.left + 12, layout.top + 23, MUTED, false);
		String context = "玩家：" + status.playerName() + "  ·  " + section.label;
		int maxWidth = Math.max(40, layout.panelWidth - font.width(title) - 42);
		context = fit(context, maxWidth);
		graphics.drawString(font, Component.literal(context), layout.right() - 12 - font.width(context),
				layout.top + 15, TEXT, false);
	}

	private void renderNavigation(GuiGraphics graphics, Layout layout) {
		graphics.drawString(font, Component.literal("模块"), layout.left + 12,
				layout.top + HEADER_HEIGHT + 10, MUTED, false);
		int y = layout.top + HEADER_HEIGHT + 28;
		for (Section value : Section.values()) {
			if (value == section) {
				graphics.fill(layout.left + 4, y, layout.left + 7, y + NAV_BUTTON_HEIGHT, ACCENT);
				graphics.fill(layout.left + 9, y - 1, layout.left + layout.sidebarWidth - 9,
						y + NAV_BUTTON_HEIGHT + 1, CARD_ACTIVE);
			}
			y += NAV_BUTTON_HEIGHT + NAV_BUTTON_GAP;
		}
	}

	private void renderOverview(GuiGraphics graphics, Layout layout) {
		int gap = 6;
		int cardHeight = Math.clamp((layout.contentHeight() - gap * 2) / 3, 34, 70);
		int y = layout.contentTop;
		drawCard(graphics, layout.contentLeft, y, layout.contentWidth(), cardHeight, "主线",
				List.of("阶段 " + status.plotStage() + "  ·  绑定 " + yes(status.bound()),
						"异常信号：" + DebugNames.bandStage(status.bandStage())));
		y += cardHeight + gap;
		String active = status.activeAnomaly().equals("none") ? "无" : DebugNames.anomaly(status.activeAnomaly());
		drawCard(graphics, layout.contentLeft, y, layout.contentWidth(), cardHeight, "异象",
				List.of("当前：" + active + (status.activeSeconds() > 0 ? "  ·  " + status.activeSeconds() + " 秒" : ""),
						"等级 " + status.anomalyTier() + " / " + status.anomalyCeiling() + "  ·  热度 " + status.anomalyHeat() + "%",
						"自动触发：" + (status.anomaliesSuspended() ? "停止" : "运行") + "  ·  下次 " + status.nextSeconds() + " 秒"));
		y += cardHeight + gap;
		String boss = status.bossAlive() ? status.bossHealth() + " / " + status.bossMaxHealth() : "未生成";
		drawCard(graphics, layout.contentLeft, y, layout.contentWidth(), cardHeight, "终局",
				List.of("结局：" + DebugNames.ending(status.ending()), "最终怪物：" + boss,
						"崩坏等级：" + status.decayStage() + (status.decayAuto() ? "（自动）" : "（手动）")));
	}

	private void renderProgress(GuiGraphics graphics, Layout layout) {
		graphics.drawString(font, Component.literal("主线快捷操作"), layout.contentLeft, layout.contentTop + 2,
				ACCENT_BRIGHT, false);
		graphics.drawString(font, Component.literal("当前阶段：" + status.plotStage() + "  ·  绑定：" + yes(status.bound())),
				layout.contentLeft, layout.contentTop + 20, TEXT, false);
		graphics.drawString(font, Component.literal("异常信号：" + DebugNames.bandStage(status.bandStage())),
				layout.contentLeft, layout.contentTop + 34, MUTED, false);
		if (layout.contentHeight() >= 150)
			graphics.drawString(font, Component.literal("重置会清空个人主线与异象进度。"),
					layout.contentLeft, layout.contentTop + 50, WARNING, false);
	}

	private void renderAnomalies(GuiGraphics graphics, Layout layout) {
		String active = status.activeAnomaly().equals("none") ? "无" : DebugNames.anomaly(status.activeAnomaly());
		graphics.drawString(font, Component.literal("当前：" + fit(active, Math.max(40, layout.contentWidth() - 150))),
				layout.contentLeft, layout.contentTop + 2, TEXT, false);
		String auto = status.anomaliesSuspended() ? "自动触发已停止" : "自动触发运行中 · 下次 " + status.nextSeconds() + " 秒";
		graphics.drawString(font, Component.literal(auto), layout.contentLeft, layout.contentTop + 16,
				status.anomaliesSuspended() ? WARNING : MUTED, false);

		int listTop = anomalyListTop(layout);
		int listHeight = Math.max(0, layout.contentBottom - listTop);
		graphics.fill(layout.contentLeft, listTop, layout.contentRight, layout.contentBottom, SIDEBAR);
		graphics.renderOutline(layout.contentLeft, listTop, layout.contentWidth(), listHeight, BORDER);
		int visibleRows = Math.max(1, listHeight / ANOMALY_ROW_HEIGHT);
		int buttonWidth = Math.clamp((layout.contentWidth() - 18) / 5, 42, 56);
		int nameWidth = Math.max(20, layout.contentWidth() - buttonWidth * 2 - BUTTON_GAP - 22);
		for (int row = 0; row < visibleRows; row++) {
			int index = anomalyScrollRow + row;
			if (index >= ANOMALIES.size()) break;
			String id = ANOMALIES.get(index);
			int y = listTop + row * ANOMALY_ROW_HEIGHT;
			boolean current = id.equals(status.activeAnomaly());
			if (current) graphics.fill(layout.contentLeft + 1, y + 1, layout.contentRight - 7,
					y + ANOMALY_ROW_HEIGHT - 1, CARD_ACTIVE);
			else if ((index & 1) == 0) graphics.fill(layout.contentLeft + 1, y + 1,
					layout.contentRight - 7, y + ANOMALY_ROW_HEIGHT - 1, CARD);
			boolean destructive = AnomalyCatalog.require(id).destructive();
			if (destructive) graphics.fill(layout.contentLeft + 5, y + 9, layout.contentLeft + 8, y + 16, DANGER);
			String name = fit(DebugNames.anomaly(id), nameWidth);
			graphics.drawString(font, Component.literal(name), layout.contentLeft + 12, y + 9,
					current ? ACCENT_BRIGHT : TEXT, false);
		}
		renderScrollbar(graphics, layout.contentRight - 5, listTop + 2, Math.max(0, listHeight - 4),
				visibleRows, ANOMALIES.size(), anomalyScrollRow, anomalyMaxScrollRow);
	}

	private void renderEnding(GuiGraphics graphics, Layout layout) {
		int columns = endingColumns(layout);
		int y = layout.contentTop - endingScroll;
		graphics.enableScissor(layout.contentLeft, layout.contentTop, layout.contentRight, layout.contentBottom);
		y = renderEndingGroup(graphics, layout, y, columns, "祭坛", ALTAR_ACTIONS.size());
		y += ENDING_GROUP_GAP;
		y = renderEndingGroup(graphics, layout, y, columns, "结局", ENDING_ACTIONS.size());
		y += ENDING_GROUP_GAP;
		renderEndingGroup(graphics, layout, y, columns,
				"最终怪物 · " + (status.bossAlive() ? status.bossHealth() + " / " + status.bossMaxHealth() : "未生成"),
				BOSS_ACTIONS.size());
		graphics.disableScissor();
		if (endingMaxScroll > 0) renderScrollbar(graphics, layout.contentRight - 4, layout.contentTop + 2,
				layout.contentHeight() - 4, layout.contentHeight(), layout.contentHeight() + endingMaxScroll,
				endingScroll, endingMaxScroll);
	}

	private int renderEndingGroup(GuiGraphics graphics, Layout layout, int groupTop, int columns,
			String label, int actionCount) {
		int height = endingGroupHeight(actionCount, columns);
		graphics.fill(layout.contentLeft, groupTop, layout.contentRight - (endingMaxScroll > 0 ? 8 : 0),
				groupTop + height, CARD);
		graphics.fill(layout.contentLeft, groupTop, layout.contentLeft + 3, groupTop + height, ACCENT);
		graphics.drawString(font, Component.literal(label), layout.contentLeft + 9, groupTop + 3,
				ACCENT_BRIGHT, false);
		return groupTop + height;
	}

	private void drawCard(GuiGraphics graphics, int x, int y, int width, int height,
			String heading, List<String> lines) {
		graphics.fill(x, y, x + width, y + height, CARD);
		graphics.fill(x, y, x + 3, y + height, ACCENT);
		graphics.drawString(font, Component.literal(heading), x + 9, y + 6, ACCENT_BRIGHT, false);
		int lineY = y + 20;
		for (String line : lines) {
			if (lineY + 9 > y + height - 3) break;
			graphics.drawString(font, Component.literal(fit(line, width - 18)), x + 9, lineY, TEXT, false);
			lineY += 13;
		}
	}

	private void renderFooter(GuiGraphics graphics, Layout layout) {
		int available = Math.max(20, layout.panelWidth - 168);
		String message = fit(status.message(), available);
		graphics.drawString(font, Component.literal(message), layout.left + 12, layout.footerTop + 13,
				statusColor(status.message()), false);
	}

	private void renderConfirmation(GuiGraphics graphics, Layout layout) {
		graphics.fill(layout.left + 1, layout.top + HEADER_HEIGHT, layout.right() - 1,
				layout.footerTop, 0xD905090B);
		Modal modal = modal(layout);
		graphics.fill(modal.left, modal.top, modal.right(), modal.bottom(), PANEL);
		graphics.renderOutline(modal.left, modal.top, modal.width, modal.height, DANGER);
		graphics.drawCenteredString(font, Component.literal("危险操作确认"),
				modal.left + modal.width / 2, modal.top + 13, DANGER);
		graphics.drawWordWrap(font, Component.literal("是否执行“" + pending.label + "”？该操作可能修改个人存档或世界状态。"),
				modal.left + 16, modal.top + 34, modal.width - 32, TEXT, false);
		graphics.drawCenteredString(font, Component.literal("Esc 取消"),
				modal.left + modal.width / 2, modal.bottom() - 43, MUTED);
	}

	private void renderScrollbar(GuiGraphics graphics, int x, int y, int height, int visible, int total,
			int offset, int maxOffset) {
		if (maxOffset <= 0 || height <= 0 || total <= 0) return;
		graphics.fill(x, y, x + 2, y + height, BORDER);
		int thumbHeight = Math.max(12, height * visible / total);
		int travel = Math.max(0, height - thumbHeight);
		int thumbY = y + (maxOffset == 0 ? 0 : travel * offset / maxOffset);
		graphics.fill(x - 1, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (pending != null || verticalAmount == 0.0D)
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		Layout layout = layout();
		int delta = verticalAmount > 0.0D ? -1 : 1;
		if (section == Section.ANOMALIES && inside(mouseX, mouseY, layout.contentLeft,
				anomalyListTop(layout), layout.contentRight, layout.contentBottom) && anomalyMaxScrollRow > 0) {
			anomalyScrollRow = Math.clamp(anomalyScrollRow + delta, 0, anomalyMaxScrollRow);
			rememberPage();
			rebuildWidgets();
			return true;
		}
		if (section == Section.ENDING && inside(mouseX, mouseY, layout.contentLeft, layout.contentTop,
				layout.contentRight, layout.contentBottom) && endingMaxScroll > 0) {
			endingScroll = Math.clamp(endingScroll + delta * ENDING_ROW_HEIGHT, 0, endingMaxScroll);
			rememberPage();
			rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && pending != null) {
			cancelConfirmation();
			return true;
		}
		return super.keyPressed(event);
	}

	private Layout layout() {
		int panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, width - SCREEN_MARGIN * 2));
		int panelHeight = Math.max(1, Math.min(MAX_PANEL_HEIGHT, height - SCREEN_MARGIN * 2));
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		int sidebarWidth = Math.clamp(panelWidth / 5, MIN_SIDEBAR_WIDTH, MAX_SIDEBAR_WIDTH);
		int footerTop = top + panelHeight - FOOTER_HEIGHT;
		int contentLeft = left + sidebarWidth + CONTENT_GAP;
		int contentTop = top + HEADER_HEIGHT + CONTENT_GAP;
		int contentRight = left + panelWidth - CONTENT_GAP;
		int contentBottom = footerTop - CONTENT_GAP;
		return new Layout(left, top, panelWidth, panelHeight, sidebarWidth, footerTop,
				contentLeft, contentTop, contentRight, contentBottom);
	}

	private Modal modal(Layout layout) {
		int width = Math.min(360, layout.panelWidth - 32);
		int height = Math.min(126, layout.panelHeight - 48);
		return new Modal(layout.left + (layout.panelWidth - width) / 2,
				layout.top + (layout.panelHeight - height) / 2, width, height);
	}

	private int anomalyListTop(Layout layout) {
		return layout.contentTop + ANOMALY_INFO_HEIGHT;
	}

	private int endingColumns(Layout layout) {
		return layout.contentWidth() >= 360 ? 2 : 1;
	}

	private int endingContentHeight(int columns) {
		return endingGroupHeight(ALTAR_ACTIONS.size(), columns) + ENDING_GROUP_GAP
				+ endingGroupHeight(ENDING_ACTIONS.size(), columns) + ENDING_GROUP_GAP
				+ endingGroupHeight(BOSS_ACTIONS.size(), columns);
	}

	private int endingGroupHeight(int actionCount, int columns) {
		int rows = (actionCount + columns - 1) / columns;
		return ENDING_GROUP_TITLE_HEIGHT + rows * ENDING_ROW_HEIGHT;
	}

	private String fit(String value, int maxWidth) {
		if (maxWidth <= 0) return "";
		if (font.width(value) <= maxWidth) return value;
		String result = value;
		while (!result.isEmpty() && font.width(result + "…") > maxWidth)
			result = result.substring(0, result.length() - 1);
		return result + "…";
	}

	private static int statusColor(String message) {
		return message.contains("拒绝") || message.contains("失败") || message.contains("未知")
				|| message.contains("没有") || message.contains("请先") ? DANGER : WARNING;
	}

	private static boolean inside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
		return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
	}

	private static String yes(boolean value) {
		return value ? "是" : "否";
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		rememberPage();
		super.onClose();
	}

	private void rememberPage() {
		pageMemory = new PageMemory(section.ordinal(), anomalyScrollRow, endingScroll);
	}

	public int sectionCountForTesting() { return Section.values().length; }
	public int anomalyCountForTesting() { return ANOMALIES.size(); }
	public int rememberedSectionForTesting() { return pageMemory.sectionIndex; }
	public int rememberedAnomalyScrollForTesting() { return pageMemory.anomalyScrollRow; }
	public void triggerAnomalyForTesting(String id) { send("anomaly", id, 0); }
	public String statusMessageForTesting() { return status.message(); }

	private enum Section {
		OVERVIEW("总览"), PROGRESS("主线"), ANOMALIES("异象"), ENDING("终局");
		private final String label;
		Section(String label) { this.label = label; }
	}

	private record ActionSpec(String label, String action, String target, int value, boolean confirm) { }
	private record Pending(String label, String action, String target, int value) { }
	private record PageMemory(int sectionIndex, int anomalyScrollRow, int endingScroll) { }
	private record Modal(int left, int top, int width, int height) {
		private int right() { return left + width; }
		private int bottom() { return top + height; }
	}
	private record Layout(int left, int top, int panelWidth, int panelHeight, int sidebarWidth, int footerTop,
			int contentLeft, int contentTop, int contentRight, int contentBottom) {
		private int right() { return left + panelWidth; }
		private int bottom() { return top + panelHeight; }
		private int contentWidth() { return contentRight - contentLeft; }
		private int contentHeight() { return contentBottom - contentTop; }
	}
}
