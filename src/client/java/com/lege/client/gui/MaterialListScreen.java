package com.lege.client.gui;

import com.lege.client.config.MaterialConfig;
import com.lege.client.material.MaterialDataBridge;
import com.lege.client.material.MaterialEntrySnapshot;
import com.lege.client.material.MaterialHudRenderer;
import com.lege.client.material.MaterialListSnapshot;
import com.lege.client.material.MaterialRefreshScheduler;
import com.lege.client.material.MaterialSnapshotListener;
import com.lege.client.material.MaterialStatistics;
import com.lege.client.material.MaterialStatisticsCalculator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    private static final int HEADER_HEIGHT = 46;
    private static final int FOOTER_HEIGHT = 34;
    private static final int LIST_HEADER_HEIGHT = 14;

    /** 列右缘距窗口右边的距离：需求 / 已有 / 缺口三列 */
    private static final int COL_TOTAL = 150;
    private static final int COL_AVAILABLE = 100;
    private static final int COL_MISSING = 50;

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
            graphics.drawString(this.font, String.format("%s: %d",
                    Component.translatable("gui.temp1792221464.material_list.bag_missing").getString(),
                    stats.countMissingInBag()), MARGIN, MARGIN + 27, 0xFF7777, true);

            renderList(graphics, snap, listTop, listBottom);
        }

        // Top N 数值显示在 - 与 + 按钮之间
        graphics.drawString(this.font, String.format("%s: %d",
                Component.translatable("gui.temp1792221464.material_list.hud_top_n").getString(),
                MaterialConfig.getHudTopN()), 218, this.height - FOOTER_HEIGHT + 14, 0xFFFFFF, true);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 表头 + 可滚动列表（scissor 裁剪） */
    private void renderList(GuiGraphics graphics, MaterialListSnapshot snap, int listTop, int listBottom) {
        // 表头
        graphics.drawString(this.font, Component.translatable("gui.temp1792221464.material_list.header_name").getString(),
                MARGIN + 20, listTop, 0xFFFF55, true);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_total").getString(),
                this.width - COL_TOTAL, listTop);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_available").getString(),
                this.width - COL_AVAILABLE, listTop);
        drawRight(graphics, Component.translatable("gui.temp1792221464.material_list.header_missing").getString(),
                this.width - COL_MISSING, listTop);

        int headerBottom = listTop + LIST_HEADER_HEIGHT;
        graphics.enableScissor(0, headerBottom, this.width, listBottom);

        List<MaterialEntrySnapshot> rows = sortedEntries(snap);
        int viewHeight = listBottom - headerBottom;
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - viewHeight);
        this.scrollOffset = Math.min(this.scrollOffset, maxScroll);

        for (int i = 0; i < rows.size(); i++) {
            int y = headerBottom + i * ROW_HEIGHT - this.scrollOffset;
            if (y + ROW_HEIGHT < headerBottom || y > listBottom) {
                continue;
            }
            renderRow(graphics, rows.get(i), y);
        }

        graphics.disableScissor();

        // 缓存几何信息供鼠标事件使用，并绘制滚动条
        this.cachedTrackTop = headerBottom;
        this.cachedTrackHeight = viewHeight;
        this.cachedMaxScroll = maxScroll;
        if (maxScroll > 0) {
            renderScrollbar(graphics, headerBottom, listBottom, viewHeight, maxScroll);
        }
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

    /** 点击轨道：落在滑块上开始拖拽，否则向点击方向翻一页 */
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
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void renderRow(GuiGraphics graphics, MaterialEntrySnapshot entry, int y) {
        // 隔行底色，提高可读性
        if ((y / ROW_HEIGHT) % 2 == 0) {
            graphics.fill(MARGIN, y, this.width - MARGIN, y + ROW_HEIGHT, 0x30FFFFFF);
        }

        graphics.renderItem(entry.stack(), MARGIN, y + 1);
        String name = entry.stack().getHoverName().getString();
        // 名称区可用宽度 = 数字列起点 - 名称起点
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, this.width - COL_TOTAL - MARGIN - 28),
                MARGIN + 20, y + 5, 0xFFFFFF, true);

        drawRight(graphics, String.valueOf(entry.total()), this.width - COL_TOTAL, y + 5);
        drawRight(graphics, String.valueOf(entry.available()), this.width - COL_AVAILABLE, y + 5);
        int bagMissing = Math.max(0, entry.missing() - entry.available());
        drawRight(graphics, String.valueOf(bagMissing), this.width - COL_MISSING, y + 5,
                bagMissing > 0 ? 0xFF5555 : 0x55FF55);
    }

    /** 全量条目按背包缺口降序，其次按物品名 */
    private static List<MaterialEntrySnapshot> sortedEntries(MaterialListSnapshot snap) {
        List<MaterialEntrySnapshot> rows = new ArrayList<>(snap.entries());
        rows.sort(Comparator
                .comparingInt(MaterialListScreen::bagMissing).reversed()
                .thenComparing(e -> e.itemId().toString()));
        return rows;
    }

    private static int bagMissing(MaterialEntrySnapshot entry) {
        return Math.max(0, entry.missing() - entry.available());
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
