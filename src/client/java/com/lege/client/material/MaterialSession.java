package com.lege.client.material;

import com.lege.Temp1792221464;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;

/**
 * 当前打开的投影文件会话（单例，仅客户端主线程访问）。
 * 持有自行解析出的材料列表，生命周期与 litematica 运行时状态完全隔离，
 * 因此无需有效性校验——打开即确定，关闭即失效。
 */
public final class MaterialSession {

    private static final MaterialSession INSTANCE = new MaterialSession();

    private MaterialListBase materialList;
    private MaterialListSnapshot snapshot = MaterialListSnapshot.empty();
    private Path currentFile;

    private MaterialSession() {
    }

    public static MaterialSession getInstance() {
        return INSTANCE;
    }

    /** 打开并解析投影文件；成功返回 true，失败（损坏 / 类型不支持 / 状态异常）返回 false */
    public boolean open(Path file) {
        try {
            MaterialListBase parsed = SchematicFileBridge.parse(file);
            if (parsed == null) {
                return false;
            }
            this.materialList = parsed;
            this.currentFile = file;
            this.snapshot = SchematicFileBridge.toSnapshot(parsed);
            return true;
        } catch (Exception e) {
            // 异常隔离：单个文件解析失败不崩溃，仅视为打开失败
            Temp1792221464.LOGGER.error("解析投影文件失败: {}", file, e);
            return false;
        }
    }

    /** 关闭当前文件，回到无材料列表状态 */
    public void close() {
        this.materialList = null;
        this.currentFile = null;
        this.snapshot = MaterialListSnapshot.empty();
    }

    public boolean hasOpenFile() {
        return this.materialList != null;
    }

    public MaterialListSnapshot getSnapshot() {
        return this.snapshot;
    }

    /** 当前文件名（界面标题显示用），未打开时为 null */
    public String getCurrentFileName() {
        return this.currentFile != null ? this.currentFile.getFileName().toString() : null;
    }

    /** A 类刷新：仅更新背包已有数并重拍快照，不重建材料列表（避免大投影卡顿） */
    public void refreshAvailable(Player player) {
        if (this.materialList == null) {
            return;
        }
        MaterialListUtils.updateAvailableCounts(this.materialList.getMaterialsAll(), player);
        this.materialList.updateCounts();
        this.snapshot = SchematicFileBridge.toSnapshot(this.materialList);
    }

    /** 忽略列表等过滤条件变更后重拍快照（不重新统计背包数） */
    public void refreshSnapshot() {
        if (this.materialList != null) {
            this.snapshot = SchematicFileBridge.toSnapshot(this.materialList);
        }
    }
}
