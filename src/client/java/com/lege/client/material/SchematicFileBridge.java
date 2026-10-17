package com.lege.client.material;

import com.lege.client.config.MaterialConfig;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 投影文件 → 材料列表的解析桥（DIP：上层模块不直接接触 litematica 类）。
 * 仅使用 litematica 的文件解析能力（LitematicaSchematic + MaterialCache 统计链路），
 * 不触碰其运行时状态（DataManager / SchematicHolder）。
 * 必须在客户端主线程调用，且玩家必须在世界中（统计背包已有数需要 player）。
 */
public final class SchematicFileBridge {

    /** 支持的投影文件扩展名（与 litematica FileType 对应） */
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".litematic", ".schematic", ".schem", ".nbt");

    private SchematicFileBridge() {
    }

    /** 文件名是否为可解析的投影文件 */
    public static boolean isSupportedFile(String fileName) {
        String lower = fileName.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析投影文件并统计材料。
     * 返回 null 表示文件无法解析（损坏 / 类型不支持）。
     */
    public static MaterialListBase parse(Path file) {
        Path dir = file.getParent();
        if (dir == null || !Files.isRegularFile(file)) {
            return null;
        }
        String fileName = file.getFileName().toString();
        FileType type = FileType.fromName(fileName);
        if (type != FileType.LITEMATICA_SCHEMATIC && type != FileType.SCHEMATICA_SCHEMATIC
                && type != FileType.SPONGE_SCHEMATIC && type != FileType.VANILLA_STRUCTURE) {
            return null;
        }

        LitematicaSchematic schematic = LitematicaSchematic.createFromFile(dir, fileName, type);
        if (schematic == null) {
            return null;
        }
        // reCreate=true：构造即同步完成全量材料统计（官方 GUI 同款调用方式）
        return new MaterialListSchematic(schematic, true);
    }

    /**
     * 将材料列表拷贝为不可变快照。
     * 忽略列表统一在快照层过滤；缺口口径为 max(0, 待放置数 - 背包已有数)。
     */
    public static MaterialListSnapshot toSnapshot(MaterialListBase materialList) {
        List<MaterialEntrySnapshot> entries = new ArrayList<>();
        long countMissing = 0;
        for (MaterialListEntry entry : materialList.getMaterialsAll()) {
            // 拷贝 ItemStack，防止 litematica 复用内部对象导致数据被改写
            ItemStack stack = entry.getStack().copy();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (MaterialConfig.isIgnored(itemId.toString())) {
                continue;
            }
            int missing = Math.max(0, entry.getCountMissing() - entry.getCountAvailable());
            countMissing += missing;
            entries.add(new MaterialEntrySnapshot(
                    itemId,
                    stack,
                    entry.getCountTotal(),
                    entry.getCountAvailable(),
                    missing,
                    entry.getCountMismatched()
            ));
        }

        return new MaterialListSnapshot(
                entries,
                materialList.getCountTotal(),
                countMissing,
                materialList.getCountMismatched(),
                System.currentTimeMillis(),
                true
        );
    }
}
