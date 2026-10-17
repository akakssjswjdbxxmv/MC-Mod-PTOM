package com.lege.client.material;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;

/**
 * 材料统计 HUD 渲染器。
 * 从 MaterialRefreshScheduler 接收快照变化，缓存在 volatile 字段供渲染线程读取。
 * 渲染线程与 tick 线程隔离，仅靠 volatile 保证可见性，不加锁。
 */
public final class MaterialHudRenderer implements HudRenderCallback {

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int TOP_MISSING_LIMIT = 5;

    private volatile MaterialListSnapshot currentSnapshot;

    /** 注册到 Fabric HUD 渲染回调，并订阅刷新调度器 */
    public void register() {
        HudRenderCallback.EVENT.register(this);
        MaterialRefreshScheduler.getInstance().addListener(snapshot -> this.currentSnapshot = snapshot);
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker tickDelta) {
        MaterialListSnapshot snapshot = this.currentSnapshot;
        if (snapshot == null || !snapshot.hasMaterialList()) {
            return;
        }

        MaterialStatistics stats = MaterialStatisticsCalculator.calculate(snapshot, TOP_MISSING_LIMIT);
        if (stats.countTotal() == 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }

        drawStats(graphics, stats);
    }

    private void drawStats(GuiGraphics graphics, MaterialStatistics stats) {
        int x = MARGIN;
        int y = MARGIN;

        // 第一行：进度百分比
        String progressLine = String.format("材料进度: %.1f%% (%d/%d)",
                stats.progressPercent(), stats.countAvailable(), stats.countTotal());
        graphics.drawString(Minecraft.getInstance().font, progressLine, x, y, 0xFFFFFF, false);
        y += LINE_HEIGHT;

        // 背包缺口汇总
        if (stats.countMissingInBag() > 0) {
            String missingLine = String.format("背包缺口: %d 件", stats.countMissingInBag());
            graphics.drawString(Minecraft.getInstance().font, missingLine, x, y, 0xFF8888, false);
            y += LINE_HEIGHT;
        }

        // Top N 紧缺材料
        for (MaterialEntrySnapshot entry : stats.topMissing()) {
            int bagMissing = Math.max(0, entry.missing() - entry.available());
            if (bagMissing <= 0) {
                continue;
            }
            String itemName = entry.stack().getHoverName().getString();
            String line = String.format("  %s x%d", itemName, bagMissing);
            graphics.drawString(Minecraft.getInstance().font, line, x, y, 0xFFAA55, false);
            y += LINE_HEIGHT;
        }
    }
}
