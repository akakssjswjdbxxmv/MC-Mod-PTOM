package com.lege.client.material;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Litematica 材料列表的唯一读取入口（DIP：上层模块不直接接触 litematica 类）。
 * 将 litematica 内部对象拷贝为不可变快照，避免读到其原地重建的中间态。
 * 必须在客户端主线程调用。
 */
public final class MaterialDataBridge {

    private MaterialDataBridge() {
    }

    /** 读取当前材料列表快照；未创建材料列表或来源已失效时返回空快照 */
    public static MaterialListSnapshot readCurrent() {
        MaterialListBase materialList = DataManager.getMaterialList();
        if (materialList == null || !isCurrentValid()) {
            return MaterialListSnapshot.empty();
        }

        List<MaterialEntrySnapshot> entries = new ArrayList<>();
        for (MaterialListEntry entry : materialList.getMaterialsAll()) {
            // 拷贝 ItemStack，防止 litematica 复用内部对象导致数据被改写
            ItemStack stack = entry.getStack().copy();
            entries.add(new MaterialEntrySnapshot(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack,
                    entry.getCountTotal(),
                    entry.getCountAvailable(),
                    entry.getCountMissing(),
                    entry.getCountMismatched()
            ));
        }

        return new MaterialListSnapshot(
                entries,
                materialList.getCountTotal(),
                materialList.getCountMissing(),
                materialList.getCountMismatched(),
                System.currentTimeMillis(),
                true
        );
    }

    /**
     * 校验当前材料列表的来源是否仍存在。
     * litematica 卸载投影时不会清空 DataManager 中的材料列表，
     * 不校验会持续读取已卸载投影的过期数据。
     * 用名称反向匹配而非引用比较：placement 的 getMaterialList() 懒加载会
     * 为未打开过的投影触发重统计任务（reCreateMaterialList），不能遍历调用。
     */
    public static boolean isCurrentValid() {
        MaterialListBase materialList = DataManager.getMaterialList();
        if (materialList == null) {
            return true;
        }
        if (materialList instanceof MaterialListPlacement) {
            return matchesAnyPlacement(materialList.getName());
        }
        if (materialList instanceof MaterialListSchematic) {
            return matchesAnyLoadedSchematic(materialList.getName());
        }
        // 区域分析等其他来源不做校验
        return true;
    }

    /** 投影来源：名称仍存在于当前投影管理器中则有效 */
    private static boolean matchesAnyPlacement(String name) {
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (placement.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** 投影文件来源：名称仍存在于已加载的投影中则有效 */
    private static boolean matchesAnyLoadedSchematic(String name) {
        for (LitematicaSchematic schematic : SchematicHolder.getInstance().getAllSchematics()) {
            if (schematic.getMetadata().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
