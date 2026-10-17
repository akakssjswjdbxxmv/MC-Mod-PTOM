package com.lege.client.material;

import com.lege.client.config.MaterialConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料统计 HUD 渲染器。
 * 从 MaterialRefreshScheduler 接收快照变化，缓存在 volatile 字段供渲染线程读取。
 * 渲染线程与 tick 线程隔离，仅靠 volatile 保证可见性，不加锁。
 */
public final class MaterialHudRenderer implements HudRenderCallback {

    private static final MaterialHudRenderer INSTANCE = new MaterialHudRenderer();

    private static final int MARGIN = 8;

    private volatile MaterialListSnapshot currentSnapshot;
    /** 主界面打开时抑制 HUD，避免与界面统计重复且被界面模糊层糊住 */
    private volatile boolean suppressed;

    public static MaterialHudRenderer getInstance() {
        return INSTANCE;
    }

    private MaterialHudRenderer() {
    }

    /** 设置渲染抑制（主界面生命周期调用） */
    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    /** 注册到 Fabric HUD 渲染回调，并订阅刷新调度器 */
    public void register() {
        HudRenderCallback.EVENT.register(this);
        MaterialRefreshScheduler.getInstance().addListener(snapshot -> this.currentSnapshot = snapshot);
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker tickDelta) {
        if (this.suppressed || !MaterialConfig.isHudEnabled()) {
            return;
        }

        MaterialListSnapshot snapshot = this.currentSnapshot;
        if (snapshot == null || !snapshot.hasMaterialList()) {
            return;
        }

        MaterialStatistics stats = MaterialStatisticsCalculator.calculate(snapshot, MaterialConfig.getHudTopN());
        if (stats.countTotal() == 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }

        drawStats(graphics, stats);
    }

    // 渲染形式对齐 litematica MaterialListHudRenderer：深底 + 图标 + 名称 + 右对齐缺失数
    private void drawStats(GuiGraphics graphics, MaterialStatistics stats) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        var mode = MaterialConfig.getDisplayMode();

        // 收集缺失 > 0 的显示行
        List<MaterialEntrySnapshot> entries = new ArrayList<>();
        List<String> countStrings = new ArrayList<>();
        for (MaterialEntrySnapshot entry : stats.topMissing()) {
            if (entry.missing() > 0) {
                entries.add(entry);
                countStrings.add(mode == MaterialConfig.CountMode.INDIVIDUAL
                        ? String.valueOf(entry.missing())
                        : MaterialCountFormatter.format(entry.missing(), mode, entry.stack().getMaxStackSize()));
            }
        }

        // 标题行：加粗模块名 + 进度
        String title = String.format("§l材料列表§r %.1f%% (%d/%d)",
                stats.progressPercent(), stats.countAvailable(), stats.countTotal());

        // 量出块尺寸：行高 16（图标高度），标题区 14
        int lineHeight = 16;
        int bgMargin = 2;
        int maxTextLength = font.width(title);
        int maxCountLength = 0;
        for (int i = 0; i < entries.size(); i++) {
            maxTextLength = Math.max(maxTextLength,
                    font.width(entries.get(i).stack().getHoverName().getString()));
            maxCountLength = Math.max(maxCountLength, font.width(countStrings.get(i)));
        }
        // 名称列与数量列间距保持紧凑（12px）
        int maxLineLength = maxTextLength + maxCountLength + 12;
        int contentHeight = entries.size() * lineHeight + bgMargin + 10;

        // 按配置锚点定位整个块，并沿锚点向内方向叠加像素偏移
        MaterialConfig.HudAnchor anchor = MaterialConfig.getHudAnchor();
        boolean right = anchor == MaterialConfig.HudAnchor.TOP_RIGHT
                || anchor == MaterialConfig.HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == MaterialConfig.HudAnchor.BOTTOM_LEFT
                || anchor == MaterialConfig.HudAnchor.BOTTOM_RIGHT;
        int offsetX = MaterialConfig.getHudOffsetX();
        int offsetY = MaterialConfig.getHudOffsetY();
        int posX = right ? graphics.guiWidth() - MARGIN - maxLineLength - bgMargin - offsetX
                : MARGIN + bgMargin + offsetX;
        int posY = bottom ? graphics.guiHeight() - MARGIN - contentHeight - bgMargin - offsetY
                : MARGIN + bgMargin + offsetY;

        // 半透明深色背景
        graphics.fill(posX - bgMargin, posY - bgMargin,
                posX + maxLineLength + bgMargin, posY + contentHeight + bgMargin, 0xA0000000);

        // 标题
        graphics.drawString(font, title, posX + 2, posY + 2, 0xFFFFFFFF, false);

        // 图标列（文本行向下偏 4px 使文字垂直居中于图标）
        int iconY = posY + 12;
        for (MaterialEntrySnapshot entry : entries) {
            graphics.renderItem(entry.stack(), posX, iconY);
            iconY += lineHeight;
        }

        // 名称（左，白） + 缺失数（右对齐到块内右缘，红）
        int y = posY + 16;
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i).stack().getHoverName().getString();
            String count = countStrings.get(i);
            graphics.drawString(font, name, posX + 18, y, 0xFFFFFFFF, false);
            graphics.drawString(font, count, posX + maxLineLength - font.width(count) - 2, y, 0xFFFF5555, false);
            y += lineHeight;
        }
    }
}
