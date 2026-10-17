package com.lege.client.gui;

import com.lege.client.config.MaterialConfig;
import com.lege.client.material.MaterialBreakdownCalculator;
import com.lege.client.material.MaterialCountFormatter;
import com.lege.client.material.MaterialEntrySnapshot;
import com.lege.client.material.MaterialHudRenderer;
import com.lege.client.material.MaterialListSnapshot;
import com.lege.client.material.MaterialRefreshScheduler;
import com.lege.client.material.MaterialSession;
import com.lege.client.material.MaterialSnapshotListener;
import com.lege.client.material.MaterialStatistics;
import com.lege.client.material.MaterialStatisticsCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private volatile MaterialListSnapshot snapshot;
    /** 滚动条组件：托管滚动位置与轨道几何（SRP，可复用） */
    private final Scrollbar scrollbar = new Scrollbar();

    /** 显示视图：材料明细 / 直接材料汇总 */
    private enum ViewMode { MATERIALS, INGREDIENTS }

    private ViewMode viewMode = ViewMode.MATERIALS;
    private volatile List<MaterialBreakdownCalculator.BreakdownEntry> breakdownEntries = List.of();
    private MaterialListSnapshot breakdownSource;
    /** 清除忽略按钮：render 中按忽略数量更新文本与可用状态 */
    private Button clearIgnoredButton;

    /** 逐级展开树：托管展开状态与展示行构建 */
    private final MaterialExpansionTree expansion = new MaterialExpansionTree();
    /** 每帧重建的展示行（材料行 + 展开的直接材料行），供点击命中使用 */
    private List<MaterialExpansionTree.Row> displayRows = List.of();

    /** 固定排序：条目集合指纹与对应的物品 id 顺序（缺口变化不再触发重排） */
    private long sortedFingerprint = Long.MIN_VALUE;
    private List<ResourceLocation> sortedOrder = List.of();

    public MaterialListScreen() {
        super(Component.translatable("gui.temp1792221464.material_list.title"));
        this.snapshot = MaterialSession.getInstance().getSnapshot();
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

        // 浏览文件：返回文件浏览器重新选择投影
        addRenderableWidget(Button.builder(
                Component.translatable("gui.temp1792221464.material_list.browse"), b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SchematicBrowserScreen());
                    }
                }).bounds(this.width - 256, MARGIN + 8, 80, 18).build());

        // 视图切换（材料明细 / 直接材料汇总）+ 清除忽略，放顶部统计区右侧
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
            // 进度后标注来源文件名，明确当前材料列表来自哪个投影文件
            String fileName = MaterialSession.getInstance().getCurrentFileName();
            if (fileName != null) {
                progress += "  -  " + fileName;
            }
            progress = this.font.plainSubstrByWidth(progress, this.width - 256 - MARGIN * 2);
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

        List<MaterialExpansionTree.Row> rows = this.expansion.buildRows(sortedEntries(snap), this.minecraft);
        this.displayRows = rows;
        renderScrollFrame(graphics, listTop, listBottom, rows.size(), (g, i, y) -> renderRow(g, rows.get(i), y));
    }

    private void renderRow(GuiGraphics graphics, MaterialExpansionTree.Row row, int y) {
        if (row instanceof MaterialExpansionTree.MaterialRow material) {
            renderMaterialRow(graphics, material, y);
        } else if (row instanceof MaterialExpansionTree.IngredientRow ingredient) {
            renderIngredientRow(graphics, ingredient, y);
        }
    }

    private void renderMaterialRow(GuiGraphics graphics, MaterialExpansionTree.MaterialRow row, int y) {
        MaterialEntrySnapshot entry = row.entry();
        // 隔行底色，提高可读性
        if (y / ROW_HEIGHT % 2 == 0) {
            graphics.fill(MARGIN, y, this.width - MARGIN, y + ROW_HEIGHT, 0x30FFFFFF);
        }

        graphics.renderItem(entry.stack(), MARGIN, y + 1);
        // 名称 + 展开/收起指示符（仅存在直接合成材料的行显示）
        String name = entry.stack().getHoverName().getString();
        if (row.expandable()) {
            name += this.expansion.isExpanded(entry.itemId()) ? " ▾" : " ▸";
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, this.width - COL_TOTAL - MARGIN - 28),
                MARGIN + 20, y + 5, 0xFFFFFF, true);

        // 数量按当前计数模式显示（个数 / 组数 / 盒数优先）；已有数按足够绿 / 不足橙标注，缺少 > 0 红色突出
        var mode = MaterialConfig.getDisplayMode();
        int stackSize = entry.stack().getMaxStackSize();
        drawRight(graphics, MaterialCountFormatter.format(entry.total(), mode, stackSize),
                this.width - COL_REQUIRED, y + 5);
        drawRight(graphics, MaterialCountFormatter.format(entry.available(), mode, stackSize),
                this.width - COL_OWNED, y + 5, entry.available() >= entry.total() ? 0x55FF55 : 0xFFAA00);
        long missing = entry.missing();
        drawRight(graphics, MaterialCountFormatter.format(missing, mode, stackSize),
                this.width - COL_MISSING, y + 5, missing > 0 ? 0xFF7777 : 0x777777);
    }

    /** 展开的直接材料行：按层级缩进渲染，有配方的行带展开指示符 */
    private void renderIngredientRow(GuiGraphics graphics, MaterialExpansionTree.IngredientRow row, int y) {
        MaterialBreakdownCalculator.BreakdownEntry entry = row.entry();
        int iconX = MARGIN + 12 * row.level();
        graphics.renderItem(entry.stack(), iconX, y + 1);
        String name = entry.stack().getHoverName().getString();
        if (row.expandable()) {
            ResourceLocation partId = BuiltInRegistries.ITEM.getKey(entry.stack().getItem());
            name += this.expansion.isExpanded(partId) ? " ▾" : " ▸";
        }
        int nameX = iconX + 20;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, this.width - COL_AVAILABLE - nameX - 8),
                nameX, y + 5, 0xCCCCCC, true);
        drawRight(graphics, MaterialCountFormatter.format(entry.totalRequired(),
                        MaterialConfig.getDisplayMode(), entry.stack().getMaxStackSize()),
                this.width - COL_AVAILABLE, y + 5, 0xCCCCCC);
    }

    /**
     * 全量条目排序：条目集合变化（打开新文件 / 忽略列表变更）时按建造缺失降序排一次并固定，
     * 之后缺口变化只更新数字与颜色（已有列绿 / 橙标注），条目位置不再跳动
     */
    private List<MaterialEntrySnapshot> sortedEntries(MaterialListSnapshot snap) {
        // 条目集合指纹：仅与物品 id 集合相关，与数量无关
        long fingerprint = 31L * snap.entries().size();
        for (MaterialEntrySnapshot entry : snap.entries()) {
            fingerprint += entry.itemId().hashCode();
        }
        if (fingerprint != this.sortedFingerprint) {
            List<MaterialEntrySnapshot> sorted = new ArrayList<>(snap.entries());
            sorted.sort(Comparator.comparingInt(MaterialEntrySnapshot::missing).reversed()
                    .thenComparing(e -> e.itemId().toString()));
            this.sortedOrder = sorted.stream().map(MaterialEntrySnapshot::itemId).toList();
            this.sortedFingerprint = fingerprint;
        }
        // 按固定顺序映射当前快照条目（快照对象随刷新重建，但 id 顺序不变）
        Map<ResourceLocation, MaterialEntrySnapshot> byId = new HashMap<>();
        for (MaterialEntrySnapshot entry : snap.entries()) {
            byId.put(entry.itemId(), entry);
        }
        List<MaterialEntrySnapshot> rows = new ArrayList<>(byId.size());
        for (ResourceLocation id : this.sortedOrder) {
            MaterialEntrySnapshot entry = byId.remove(id);
            if (entry != null) {
                rows.add(entry);
            }
        }
        // 防御：顺序表之外的残留条目追加在末尾
        rows.addAll(byId.values());
        return rows;
    }

    /** 直接材料汇总视图：成品配方一层分解后的直接合成材料总量 */
    private void renderBreakdownList(GuiGraphics graphics, int listTop, int listBottom) {
        graphics.drawString(this.font, Component.translatable("gui.temp1792221464.material_list.header_name").getString(),
                MARGIN + 20, listTop, 0xFFFF55, true);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_required").getString(),
                this.width - COL_TOTAL, listTop, 0xFFFF55);

        List<MaterialBreakdownCalculator.BreakdownEntry> rows = this.breakdownEntries;
        renderScrollFrame(graphics, listTop, listBottom, rows.size(), (g, i, y) -> renderBreakdownRow(g, rows.get(i), y));
    }

    /** 滚动帧：裁剪 + 行绘制 + 滚动条，供各视图共用 */
    private void renderScrollFrame(GuiGraphics graphics, int listTop, int listBottom, int rowCount, RowDrawer drawer) {
        int headerBottom = listTop + LIST_HEADER_HEIGHT;
        graphics.enableScissor(0, headerBottom, this.width, listBottom);

        int viewHeight = listBottom - headerBottom;
        this.scrollbar.beginFrame(headerBottom, viewHeight, Math.max(0, rowCount * ROW_HEIGHT - viewHeight));

        for (int i = 0; i < rowCount; i++) {
            int y = headerBottom + i * ROW_HEIGHT - this.scrollbar.scrollOffset();
            if (y + ROW_HEIGHT < headerBottom || y > listBottom) {
                continue;
            }
            drawer.draw(graphics, i, y);
        }

        graphics.disableScissor();
        this.scrollbar.render(graphics, this.scrollbar.trackX(this.width));
    }

    @FunctionalInterface
    private interface RowDrawer {
        void draw(GuiGraphics graphics, int index, int y);
    }

    /** 滚动条点击交给组件处理；列表视图下行点击：左键展开原材料，右键忽略该物品 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int trackX = this.scrollbar.trackX(this.width);
        if (this.scrollbar.mouseClicked(mouseX, mouseY, button, trackX)) {
            return true;
        }
        if (button == 0 || button == 1) {
            boolean inListArea = this.scrollbar.inViewArea(mouseY) && (button == 1 || mouseX < trackX);
            if (this.viewMode == ViewMode.MATERIALS && inListArea) {
                int index = (int) ((mouseY - this.scrollbar.trackTop() + this.scrollbar.scrollOffset()) / ROW_HEIGHT);
                if (index >= 0 && index < this.displayRows.size()) {
                    MaterialExpansionTree.Row row = this.displayRows.get(index);
                    if (row instanceof MaterialExpansionTree.MaterialRow material) {
                        if (button == 0) {
                            this.expansion.toggle(material.entry().itemId(), material.entry().stack(), this.minecraft);
                        } else {
                            MaterialConfig.addIgnoredItem(material.entry().itemId().toString());
                            // 立即刷新快照，忽略的物品马上从列表消失
                            MaterialRefreshScheduler.getInstance().refreshNow();
                        }
                        return true;
                    }
                    // 直接材料行左键：继续展开它的直接合成材料（逐级下钻）
                    if (button == 0 && row instanceof MaterialExpansionTree.IngredientRow ingredient) {
                        this.expansion.toggle(BuiltInRegistries.ITEM.getKey(ingredient.entry().stack().getItem()),
                                ingredient.entry().stack(), this.minecraft);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 拖拽滑块滚动列表 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrollbar.mouseDragged(mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollbar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 快照变化时重算直接材料分解（惰性，仅在直接材料视图下调用） */
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
            this.scrollbar.scrollBy(-(int) deltaY * ROW_HEIGHT);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
