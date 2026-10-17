package com.lege.client.gui;

import com.lege.client.config.MaterialConfig;
import com.lege.client.material.MaterialBreakdownCalculator;
import com.lege.client.material.MaterialCountFormatter;
import com.lege.client.material.MaterialDataBridge;
import com.lege.client.material.MaterialEntrySnapshot;
import com.lege.client.material.MaterialHudRenderer;
import com.lege.client.material.MaterialListSnapshot;
import com.lege.client.material.MaterialRefreshScheduler;
import com.lege.client.material.MaterialSnapshotListener;
import com.lege.client.material.MaterialStatistics;
import com.lege.client.material.MaterialStatisticsCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 模组主界面：材料统计汇总 + 完整材料列表 + HUD 设置。
 * 数据来自刷新调度器的快照推送，界面只读不回写 litematica。
 */
public final class MaterialListScreen extends Screen implements MaterialSnapshotListener {

    private static final int MARGIN = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int ROW_HEIGHT = 18;
    /** 顶部标题 + 统计区高度，尽量压缩给列表留空间 */
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 34;
    private static final int LIST_HEADER_HEIGHT = 14;

    /** 列右缘距窗口右边的距离：需求 / 已有两列 */
    private static final int COL_TOTAL = 100;
    private static final int COL_AVAILABLE = 50;
    /** 材料视图三列右对齐位置：需求 / 已有 / 缺少 */
    private static final int COL_REQUIRED = 130;
    private static final int COL_OWNED = 85;
    private static final int COL_MISSING = 40;

    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_SLIDER = 20;

    private volatile MaterialListSnapshot snapshot;
    private int scrollOffset;
    /** 滚动条拖拽状态与每帧缓存的列表几何信息（render 先于鼠标事件执行） */
    private boolean scrollbarDragging;
    private double dragGrabOffset;
    private int cachedTrackTop;
    private int cachedTrackHeight;
    private int cachedMaxScroll;

    /** 显示视图：材料明细 / 原材料汇总 */
    private enum ViewMode { MATERIALS, INGREDIENTS }

    private ViewMode viewMode = ViewMode.MATERIALS;
    private volatile List<MaterialBreakdownCalculator.BreakdownEntry> breakdownEntries = List.of();
    private MaterialListSnapshot breakdownSource;
    /** 清除忽略按钮：render 中按忽略数量更新文本与可用状态 */
    private Button clearIgnoredButton;

    /** 已展开原材料明细的材料条目及其分解结果 */
    private ResourceLocation expandedItemId;
    private List<MaterialBreakdownCalculator.BreakdownEntry> expandedParts = List.of();
    /** 每帧重建的展示行（材料行 + 展开的原料行），供点击命中使用 */
    private List<Row> displayRows = List.of();

    /** 展示行：材料行或其展开的原材料行 */
    private sealed interface Row permits MaterialRow, IngredientRow {
    }

    private record MaterialRow(MaterialEntrySnapshot entry) implements Row {
    }

    private record IngredientRow(MaterialBreakdownCalculator.BreakdownEntry entry) implements Row {
    }

    public MaterialListScreen() {
        super(Component.translatable("gui.temp1792221464.material_list.title"));
        this.snapshot = MaterialDataBridge.readCurrent();
    }

    @Override
    protected void init() {
        MaterialRefreshScheduler.getInstance().addListener(this);
        // 抑制 HUD：主界面统计与 HUD 重复，且 HUD 会被界面背景模糊层糊住
        MaterialHudRenderer.getInstance().setSuppressed(true);
        int y = this.height - FOOTER_HEIGHT + 8;

        // HUD 开关
        addRenderableWidget(Button.builder(toggleLabel(), b -> {
            MaterialConfig.setHudEnabled(!MaterialConfig.isHudEnabled());
            b.setMessage(toggleLabel());
        }).bounds(MARGIN, y, 110, 20).build());

        // 计数模式切换（个数 / 组数 / 盒数优先），循环切换
        addRenderableWidget(Button.builder(countModeLabel(), b -> {
            MaterialConfig.cycleDisplayMode();
            b.setMessage(countModeLabel());
        }).bounds(124, y, 62, 20).build());

        // HUD Top N：[ - ] 数值 [ + ]（数值在 render 中绘制）
        addRenderableWidget(Button.builder(Component.literal("-"), b ->
                MaterialConfig.setHudTopN(MaterialConfig.getHudTopN() - 1)
        ).bounds(190, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b ->
                MaterialConfig.setHudTopN(MaterialConfig.getHudTopN() + 1)
        ).bounds(270, y, 20, 20).build());

        // 关闭
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width - MARGIN - 60, y, 60, 20).build());

        // 视图切换（材料明细 / 原材料汇总）+ 清除忽略，放顶部统计区右侧
        Button viewButton = Button.builder(viewLabel(), b -> {
            this.viewMode = this.viewMode == ViewMode.MATERIALS ? ViewMode.INGREDIENTS : ViewMode.MATERIALS;
            b.setMessage(viewLabel());
        }).bounds(this.width - 172, MARGIN + 8, 80, 18).build();
        viewButton.setTooltip(Tooltip.create(Component.translatable("gui.temp1792221464.material_list.view_toggle")));
        addRenderableWidget(viewButton);

        this.clearIgnoredButton = Button.builder(clearIgnoredLabel(), b -> {
            MaterialConfig.clearIgnored();
            MaterialRefreshScheduler.getInstance().refreshNow();
        }).bounds(this.width - 88, MARGIN + 8, 80, 18).build();
        this.clearIgnoredButton.setTooltip(Tooltip.create(
                Component.translatable("gui.temp1792221464.material_list.clear_ignored.tooltip")));
        addRenderableWidget(this.clearIgnoredButton);
    }

    @Override
    public void removed() {
        MaterialRefreshScheduler.getInstance().removeListener(this);
        MaterialHudRenderer.getInstance().setSuppressed(false);
        super.removed();
    }

    /**
     * 跳过 vanilla 的全屏高斯模糊（1.21 游戏内打开 Screen 默认会模糊整个画面，
     * 连带把透出的世界与 HUD 糊掉），仅做暗化渐变保持背景清晰。
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level != null) {
            graphics.fillGradient(0, 0, this.width, this.height, 0x66000000, 0x99101010);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void onSnapshotChanged(MaterialListSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        MaterialListSnapshot snap = this.snapshot;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, MARGIN, 0xFFFFFF);

        int listTop = MARGIN + HEADER_HEIGHT;
        int listBottom = this.height - FOOTER_HEIGHT;

        if (!snap.hasMaterialList()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.temp1792221464.material_list.no_list").getString(),
                    MARGIN, listTop, 0xDDDDDD, true);
        } else {
            MaterialStatistics stats = MaterialStatisticsCalculator.calculate(snap, MaterialConfig.getHudTopN());
            String progress = String.format("%s: %.1f%% (%d/%d)",
                    Component.translatable("gui.temp1792221464.material_list.progress").getString(),
                    stats.progressPercent(), stats.countAvailable(), stats.countTotal());
            graphics.drawString(this.font, progress, MARGIN, MARGIN + 14, 0xFFFFFF, true);

            if (this.viewMode == ViewMode.INGREDIENTS) {
                ensureBreakdown(snap);
                renderBreakdownList(graphics, listTop, listBottom);
            } else {
                renderList(graphics, snap, listTop, listBottom);
            }
        }

        // Top N 数值显示在 - 与 + 按钮之间
        graphics.drawString(this.font, String.format("%s: %d",
                Component.translatable("gui.temp1792221464.material_list.hud_top_n").getString(),
                MaterialConfig.getHudTopN()), 218, this.height - FOOTER_HEIGHT + 14, 0xFFFFFF, true);

        // 清除忽略按钮：随忽略数量动态更新
        int ignoredCount = MaterialConfig.getIgnoredCount();
        this.clearIgnoredButton.active = ignoredCount > 0;
        this.clearIgnoredButton.setMessage(clearIgnoredLabel());

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 表头 + 可滚动列表（scissor 裁剪），支持展开条目的原材料子行 */
    private void renderList(GuiGraphics graphics, MaterialListSnapshot snap, int listTop, int listBottom) {
        // 表头：需求 / 已有 / 缺少
        graphics.drawString(this.font, Component.translatable("gui.temp1792221464.material_list.header_name").getString(),
                MARGIN + 20, listTop, 0xFFFF55, true);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_total").getString(),
                this.width - COL_REQUIRED, listTop, 0xFFFF55);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_available").getString(),
                this.width - COL_OWNED, listTop, 0xFFFF55);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_missing").getString(),
                this.width - COL_MISSING, listTop, 0xFFFF55);

        List<Row> rows = buildDisplayRows(snap);
        this.displayRows = rows;
        renderScrollFrame(graphics, listTop, listBottom, rows.size(), (g, i, y) -> renderRow(g, rows.get(i), y));
    }

    /** 材料行 + 已展开条目的原材料子行 */
    private List<Row> buildDisplayRows(MaterialListSnapshot snap) {
        List<Row> rows = new ArrayList<>();
        for (MaterialEntrySnapshot entry : sortedEntries(snap)) {
            rows.add(new MaterialRow(entry));
            if (entry.itemId().equals(this.expandedItemId)) {
                for (MaterialBreakdownCalculator.BreakdownEntry part : this.expandedParts) {
                    rows.add(new IngredientRow(part));
                }
            }
        }
        return rows;
    }

    private void renderRow(GuiGraphics graphics, Row row, int y) {
        if (row instanceof MaterialRow material) {
            renderMaterialRow(graphics, material.entry(), y);
        } else if (row instanceof IngredientRow ingredient) {
            renderIngredientRow(graphics, ingredient.entry(), y);
        }
    }

    private void renderMaterialRow(GuiGraphics graphics, MaterialEntrySnapshot entry, int y) {
        // 隔行底色，提高可读性
        if (y / ROW_HEIGHT % 2 == 0) {
            graphics.fill(MARGIN, y, this.width - MARGIN, y + ROW_HEIGHT, 0x30FFFFFF);
        }

        graphics.renderItem(entry.stack(), MARGIN, y + 1);
        boolean expanded = entry.itemId().equals(this.expandedItemId);
        // 名称 + 展开/收起指示符
        String name = entry.stack().getHoverName().getString() + (expanded ? " ▾" : " ▸");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, this.width - COL_TOTAL - MARGIN - 28),
                MARGIN + 20, y + 5, 0xFFFFFF, true);

        // 数量按当前计数模式显示（个数 / 组数 / 盒数优先），缺少 > 0 红色突出
        var mode = MaterialConfig.getDisplayMode();
        int stackSize = entry.stack().getMaxStackSize();
        drawRight(graphics, MaterialCountFormatter.format(entry.total(), mode, stackSize),
                this.width - COL_REQUIRED, y + 5);
        drawRight(graphics, MaterialCountFormatter.format(entry.available(), mode, stackSize),
                this.width - COL_OWNED, y + 5);
        long missing = entry.missing();
        drawRight(graphics, MaterialCountFormatter.format(missing, mode, stackSize),
                this.width - COL_MISSING, y + 5, missing > 0 ? 0xFF7777 : 0x777777);
    }

    /** 展开的原材料行：缩进渲染 */
    private void renderIngredientRow(GuiGraphics graphics, MaterialBreakdownCalculator.BreakdownEntry entry, int y) {
        graphics.renderItem(entry.stack(), MARGIN + 12, y + 1);
        String name = entry.stack().getHoverName().getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, this.width - COL_AVAILABLE - MARGIN - 40),
                MARGIN + 32, y + 5, 0xCCCCCC, true);
        drawRight(graphics, MaterialCountFormatter.format(entry.totalRequired(),
                        MaterialConfig.getDisplayMode(), entry.stack().getMaxStackSize()),
                this.width - COL_AVAILABLE, y + 5, 0xCCCCCC);
    }

    /** 全量条目按建造缺失降序，其次按物品名 */
    private static List<MaterialEntrySnapshot> sortedEntries(MaterialListSnapshot snap) {
        List<MaterialEntrySnapshot> rows = new ArrayList<>(snap.entries());
        rows.sort(Comparator
                .comparingInt(MaterialEntrySnapshot::missing).reversed()
                .thenComparing(e -> e.itemId().toString()));
        return rows;
    }

    /** 原材料汇总视图：成品经配方递归分解后的基础材料总量 */
    private void renderBreakdownList(GuiGraphics graphics, int listTop, int listBottom) {
        graphics.drawString(this.font, Component.translatable("gui.temp1792221464.material_list.header_name").getString(),
                MARGIN + 20, listTop, 0xFFFF55, true);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_required").getString(),
                this.width - COL_TOTAL, listTop, 0xFFFF55);

        List<MaterialBreakdownCalculator.BreakdownEntry> rows = this.breakdownEntries;
        renderScrollFrame(graphics, listTop, listBottom, rows.size(), (g, i, y) -> renderBreakdownRow(g, rows.get(i), y));
    }

    /** 滚动帧：裁剪 + 行绘制 + 几何缓存 + 滚动条，供各视图共用 */
    private void renderScrollFrame(GuiGraphics graphics, int listTop, int listBottom, int rowCount, RowDrawer drawer) {
        int headerBottom = listTop + LIST_HEADER_HEIGHT;
        graphics.enableScissor(0, headerBottom, this.width, listBottom);

        int viewHeight = listBottom - headerBottom;
        int maxScroll = Math.max(0, rowCount * ROW_HEIGHT - viewHeight);
        this.scrollOffset = Math.min(this.scrollOffset, maxScroll);

        for (int i = 0; i < rowCount; i++) {
            int y = headerBottom + i * ROW_HEIGHT - this.scrollOffset;
            if (y + ROW_HEIGHT < headerBottom || y > listBottom) {
                continue;
            }
            drawer.draw(graphics, i, y);
        }

        graphics.disableScissor();

        this.cachedTrackTop = headerBottom;
        this.cachedTrackHeight = viewHeight;
        this.cachedMaxScroll = maxScroll;
        if (maxScroll > 0) {
            renderScrollbar(graphics, headerBottom, listBottom, viewHeight, maxScroll);
        }
    }

    @FunctionalInterface
    private interface RowDrawer {
        void draw(GuiGraphics graphics, int index, int y);
    }

    /** 轨道 + 滑块，滑块高度按可视比例，位置按滚动比例 */
    private void renderScrollbar(GuiGraphics graphics, int top, int bottom, int viewHeight, int maxScroll) {
        int trackX = this.width - SCROLLBAR_WIDTH - 2;
        graphics.fill(trackX, top, trackX + SCROLLBAR_WIDTH, bottom, 0x40000000);

        int sliderHeight = sliderHeight(viewHeight, maxScroll);
        int sliderY = top + (bottom - top - sliderHeight) * this.scrollOffset / maxScroll;
        int color = this.scrollbarDragging ? 0xF0F0F0 : 0x80FFFFFF;
        graphics.fill(trackX, sliderY, trackX + SCROLLBAR_WIDTH, sliderY + sliderHeight, color);
    }

    private static int sliderHeight(int viewHeight, int maxScroll) {
        return Math.max(SCROLLBAR_MIN_SLIDER, viewHeight * viewHeight / (viewHeight + maxScroll));
    }

    /** 点击轨道：落在滑块上开始拖拽，否则向点击方向翻一页；点击行切换原材料展开 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.cachedMaxScroll > 0
                && mouseX >= this.width - SCROLLBAR_WIDTH - 2 && mouseX <= this.width - 2
                && mouseY >= this.cachedTrackTop && mouseY <= this.cachedTrackTop + this.cachedTrackHeight) {
            int sliderHeight = sliderHeight(this.cachedTrackHeight, this.cachedMaxScroll);
            int sliderY = this.cachedTrackTop
                    + (this.cachedTrackHeight - sliderHeight) * this.scrollOffset / this.cachedMaxScroll;
            if (mouseY >= sliderY && mouseY <= sliderY + sliderHeight) {
                this.scrollbarDragging = true;
                this.dragGrabOffset = mouseY - sliderY;
            } else {
                int page = mouseY < sliderY ? -this.cachedTrackHeight : this.cachedTrackHeight;
                this.scrollOffset = clampScroll(this.scrollOffset + page, this.cachedMaxScroll);
            }
            return true;
        }
        // 材料列表视图下的行点击：左键展开原材料，右键忽略该物品
        if (button == 0 || button == 1) {
            boolean inListArea = mouseY >= this.cachedTrackTop
                    && mouseY < this.cachedTrackTop + this.cachedTrackHeight
                    && (button == 1 || mouseX < this.width - SCROLLBAR_WIDTH - 2);
            if (this.viewMode == ViewMode.MATERIALS && inListArea) {
                int index = (int) ((mouseY - this.cachedTrackTop + this.scrollOffset) / ROW_HEIGHT);
                if (index >= 0 && index < this.displayRows.size()
                        && this.displayRows.get(index) instanceof MaterialRow material) {
                    if (button == 0) {
                        toggleExpanded(material.entry());
                    } else {
                        MaterialConfig.addIgnoredItem(material.entry().itemId().toString());
                        // 立即刷新快照，忽略的物品马上从列表消失
                        MaterialRefreshScheduler.getInstance().refreshNow();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 展开 / 收起一个材料条目的原材料明细 */
    private void toggleExpanded(MaterialEntrySnapshot entry) {
        if (entry.itemId().equals(this.expandedItemId)) {
            this.expandedItemId = null;
            this.expandedParts = List.of();
            return;
        }
        Minecraft client = this.minecraft;
        if (client != null && client.level != null) {
            this.expandedParts = MaterialBreakdownCalculator.breakdownSingle(
                    entry.stack(), entry.total(), client.level.getRecipeManager(), client.level.registryAccess());
            this.expandedItemId = entry.itemId();
        }
    }

    /** 拖拽滑块滚动列表 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrollbarDragging && this.cachedMaxScroll > 0) {
            int sliderHeight = sliderHeight(this.cachedTrackHeight, this.cachedMaxScroll);
            int movable = this.cachedTrackHeight - sliderHeight;
            int target = (int) Math.round(
                    (mouseY - this.dragGrabOffset - this.cachedTrackTop) * this.cachedMaxScroll / Math.max(1, movable));
            this.scrollOffset = clampScroll(target, this.cachedMaxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollbarDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private static int clampScroll(int value, int maxScroll) {
        return Math.max(0, Math.min(maxScroll, value));
    }

    /** 快照变化时重算原材料分解（惰性，仅在原材料视图下调用） */
    private void ensureBreakdown(MaterialListSnapshot snap) {
        if (this.breakdownSource == snap) {
            return;
        }
        Minecraft client = this.minecraft;
        if (client != null && client.level != null) {
            this.breakdownEntries = MaterialBreakdownCalculator.calculate(
                    snap, client.level.getRecipeManager(), client.level.registryAccess());
        } else {
            this.breakdownEntries = List.of();
        }
        this.breakdownSource = snap;
    }

    private void renderBreakdownRow(GuiGraphics graphics, MaterialBreakdownCalculator.BreakdownEntry entry, int y) {
        if (y / ROW_HEIGHT % 2 == 0) {
            graphics.fill(MARGIN, y, this.width - MARGIN, y + ROW_HEIGHT, 0x30FFFFFF);
        }

        graphics.renderItem(entry.stack(), MARGIN, y + 1);
        String name = entry.stack().getHoverName().getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, this.width - COL_AVAILABLE - MARGIN - 28),
                MARGIN + 20, y + 5, 0xFFFFFF, true);

        drawRight(graphics, MaterialCountFormatter.format(entry.totalRequired(),
                        MaterialConfig.getDisplayMode(), entry.stack().getMaxStackSize()),
                this.width - COL_TOTAL, y + 5);
    }

    private void drawRight(GuiGraphics graphics, String text, int rightX, int y) {
        drawRight(graphics, text, rightX, y, 0xFFFFFF);
    }

    private void drawRight(GuiGraphics graphics, String text, int rightX, int y, int color) {
        graphics.drawString(this.font, text, rightX - this.font.width(text), y, color, true);
    }

    private static Component toggleLabel() {
        String state = MaterialConfig.isHudEnabled() ? "gui.temp1792221464.common.on" : "gui.temp1792221464.common.off";
        return Component.translatable("gui.temp1792221464.material_list.hud_toggle",
                Component.translatable(state));
    }

    private Component viewLabel() {
        String key = this.viewMode == ViewMode.INGREDIENTS
                ? "gui.temp1792221464.material_list.view_ingredients"
                : "gui.temp1792221464.material_list.view_materials";
        return Component.translatable(key);
    }

    private static Component clearIgnoredLabel() {
        return Component.translatable("gui.temp1792221464.material_list.clear_ignored",
                MaterialConfig.getIgnoredCount());
    }

    private static Component countModeLabel() {
        String key = switch (MaterialConfig.getDisplayMode()) {
            case INDIVIDUAL -> "gui.temp1792221464.material_list.count_individual";
            case STACKS -> "gui.temp1792221464.material_list.count_stacks";
            case SHULKERS -> "gui.temp1792221464.material_list.count_shulkers";
        };
        return Component.translatable("gui.temp1792221464.material_list.count_mode",
                Component.translatable(key));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int listTop = MARGIN + HEADER_HEIGHT;
        int listBottom = this.height - FOOTER_HEIGHT;
        if (mouseY >= listTop && mouseY <= listBottom) {
            this.scrollOffset = clampScroll(this.scrollOffset - (int) deltaY * ROW_HEIGHT,
                    Math.max(0, this.cachedMaxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
