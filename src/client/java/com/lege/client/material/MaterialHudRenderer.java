package com.lege.client.material;

import com.lege.client.config.MaterialConfig;
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

    private static final MaterialHudRenderer INSTANCE = new MaterialHudRenderer();

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;

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

    private void drawStats(GuiGraphics graphics, MaterialStatistics stats) {
        int x = MARGIN;
        int y = MARGIN;

        // 第一行：进度百分比
        String progressLine = String.format("材料进度: %.1f%% (%d/%d)",
                stats.progressPercent(), stats.countAvailable(), stats.countTotal());
        graphics.drawString(Minecraft.getInstance().font, progressLine, x, y, 0xFFFFFF, false);
        y += LINE_HEIGHT;

        // Top N 紧缺材料（按建造缺失排序），数量按当前计数模式显示
        var mode = MaterialConfig.getDisplayMode();
        for (MaterialEntrySnapshot entry : stats.topMissing()) {
            if (entry.missing() <= 0) {
                continue;
            }
            String itemName = entry.stack().getHoverName().getString();
            String count = mode == MaterialConfig.CountMode.INDIVIDUAL
                    ? "x" + entry.missing()
                    : MaterialCountFormatter.format(entry.missing(), mode, entry.stack().getMaxStackSize());
            String line = String.format("  %s %s", itemName, count);
            graphics.drawString(Minecraft.getInstance().font, line, x, y, 0xFFAA55, false);
            y += LINE_HEIGHT;
        }
    }
}
