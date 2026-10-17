package com.lege.client.material;

import com.lege.Temp1792221464;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 材料列表刷新调度器（单例）。
 * 数据源为 MaterialSession（自行解析的投影文件），仅使用 A 类接口
 * updateAvailableCounts 刷新背包已有数，不触发建造进度重算，避免大投影卡顿。
 * 所有操作在客户端主线程（tick 回调）中执行。
 */
public final class MaterialRefreshScheduler {

    private static final MaterialRefreshScheduler INSTANCE = new MaterialRefreshScheduler();

    /** 刷新间隔（tick），20 tick = 1 秒 */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private final List<MaterialSnapshotListener> listeners = new CopyOnWriteArrayList<>();
    private int tickCounter;
    private long lastSnapshotHash;
    private boolean hadMaterialList;
    private boolean running;

    private MaterialRefreshScheduler() {
    }

    public static MaterialRefreshScheduler getInstance() {
        return INSTANCE;
    }

    public void addListener(MaterialSnapshotListener listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(MaterialSnapshotListener listener) {
        this.listeners.remove(listener);
    }

    /** 初始化：注册生命周期事件。应在客户端入口调用一次 */
    public void initialize() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> start());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
    }

    private void start() {
        this.tickCounter = 0;
        this.lastSnapshotHash = 0;
        this.hadMaterialList = false;
        this.running = true;
    }

    private void stop() {
        // 仅停止刷新；监听器是持久订阅者（如 HUD），清空会导致重进世界后永久失效
        this.running = false;
        // 世界断开后会话随之失效（背包统计依赖玩家），清理避免残留过期数据
        MaterialSession.getInstance().close();
    }

    private void onClientTick() {
        if (!this.running) {
            return;
        }

        if (++this.tickCounter < REFRESH_INTERVAL_TICKS) {
            return;
        }
        this.tickCounter = 0;

        try {
            refresh();
        } catch (Exception e) {
            // 异常隔离：刷新失败不影响后续 tick
            Temp1792221464.LOGGER.error("材料列表刷新失败", e);
        }
    }

    /** 忽略列表等过滤条件变更后：重拍快照并立即推送（主线程调用） */
    public void refreshNow() {
        try {
            MaterialSession session = MaterialSession.getInstance();
            session.refreshSnapshot();
            push(session.getSnapshot());
        } catch (Exception e) {
            Temp1792221464.LOGGER.error("材料列表立即刷新失败", e);
        }
    }

    /** 文件打开 / 关闭后立即推送当前快照（主线程调用） */
    public void notifyNow() {
        try {
            push(MaterialSession.getInstance().getSnapshot());
        } catch (Exception e) {
            Temp1792221464.LOGGER.error("材料快照推送失败", e);
        }
    }

    private void refresh() {
        Player player = Minecraft.getInstance().player;
        MaterialSession session = MaterialSession.getInstance();

        if (player == null || !session.hasOpenFile()) {
            // 从"有材料列表"变为"无"，通知一次空快照
            if (this.hadMaterialList) {
                this.hadMaterialList = false;
                notifyListeners(MaterialListSnapshot.empty());
            }
            return;
        }

        // A 类刷新链路：更新背包已有数 → 重拍快照
        session.refreshAvailable(player);
        MaterialListSnapshot snapshot = session.getSnapshot();
        long hash = computeHash(snapshot);

        if (hash != this.lastSnapshotHash || !this.hadMaterialList) {
            this.lastSnapshotHash = hash;
            this.hadMaterialList = true;
            notifyListeners(snapshot);
        }
    }

    /** 直接推送一份快照（跳过周期与变化检测） */
    private void push(MaterialListSnapshot snapshot) {
        this.lastSnapshotHash = computeHash(snapshot);
        this.hadMaterialList = snapshot.hasMaterialList();
        notifyListeners(snapshot);
    }

    /** 轻量变化检测 hash：仅看条目数与背包已有数总和 */
    private long computeHash(MaterialListSnapshot snapshot) {
        long sumAvailable = 0;
        for (MaterialEntrySnapshot entry : snapshot.entries()) {
            sumAvailable += entry.available();
        }
        return (long) snapshot.entries().size() * 31 + sumAvailable + snapshot.countTotal();
    }

    private void notifyListeners(MaterialListSnapshot snapshot) {
        for (MaterialSnapshotListener listener : this.listeners) {
            try {
                listener.onSnapshotChanged(snapshot);
            } catch (Exception e) {
                Temp1792221464.LOGGER.error("材料快照监听器回调异常", e);
            }
        }
    }
}
