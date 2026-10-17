package com.lege.client.gui;

import com.lege.client.material.MaterialBreakdownCalculator;
import com.lege.client.material.MaterialEntrySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 材料展开树：托管"逐级手动下钻"的展开状态与展示行构建。
 * 点击材料行 / 直接材料行展开该物品的一层直接合成材料，可继续下钻；
 * 展开状态按物品 id 记忆（同一物品在任何层级出现行为一致），仅主线程访问。
 */
public final class MaterialExpansionTree {

    /** 最大展开层级（缩进空间与配方环防护） */
    private static final int MAX_EXPAND_LEVEL = 4;

    /** 展示行：材料行（level 0）或其展开的直接合成材料行（level ≥ 1） */
    public sealed interface Row permits MaterialRow, IngredientRow {
    }

    /** 材料行：expandable 为 false 表示无直接合成材料（无配方或配方仅自身），不显示展开图标 */
    public record MaterialRow(MaterialEntrySnapshot entry, boolean expandable) implements Row {
    }

    /** 直接材料行：层级决定缩进；expandable 为 false 表示无配方（不可再展开） */
    public record IngredientRow(MaterialBreakdownCalculator.BreakdownEntry entry, int level,
                                boolean expandable) implements Row {
    }

    private final Set<ResourceLocation> expandedIds = new LinkedHashSet<>();

    public boolean isExpanded(ResourceLocation itemId) {
        return this.expandedIds.contains(itemId);
    }

    /** 展开 / 收起一个物品的直接合成材料明细；无配方不展开 */
    public void toggle(ResourceLocation itemId, ItemStack stack, Minecraft client) {
        if (this.expandedIds.contains(itemId)) {
            this.expandedIds.remove(itemId);
        } else if (isExpandable(itemId, stack, client, Set.of())) {
            this.expandedIds.add(itemId);
        }
    }

    /** 由排序后的材料条目构建展示行：材料行 + 各级展开的直接材料子行 */
    public List<Row> buildRows(List<MaterialEntrySnapshot> entries, Minecraft client) {
        List<Row> rows = new ArrayList<>();
        for (MaterialEntrySnapshot entry : entries) {
            Set<ResourceLocation> ancestors = new LinkedHashSet<>();
            rows.add(new MaterialRow(entry, isExpandable(entry.itemId(), entry.stack(), client, ancestors)));
            appendIngredientRows(rows, entry.itemId(), entry.stack(), entry.total(), 1, ancestors, client);
        }
        return rows;
    }

    /** 若物品处于展开状态，插入其直接材料行并递归下钻；ancestors 按路径防配方环 */
    private void appendIngredientRows(List<Row> rows, ResourceLocation itemId, ItemStack stack, long total,
                                      int level, Set<ResourceLocation> ancestors, Minecraft client) {
        if (level > MAX_EXPAND_LEVEL || !this.expandedIds.contains(itemId) || !ancestors.add(itemId)
                || client == null || client.level == null) {
            return;
        }
        List<MaterialBreakdownCalculator.BreakdownEntry> parts = MaterialBreakdownCalculator.breakdownSingle(
                stack, total, client.level.getRecipeManager(), client.level.registryAccess());
        for (MaterialBreakdownCalculator.BreakdownEntry part : parts) {
            ResourceLocation partId = BuiltInRegistries.ITEM.getKey(part.stack().getItem());
            // 下级与上级链中任一物品相同（含父级自身，如 骨粉→骨块→骨粉 配方环）：不展示，避免递归循环
            if (ancestors.contains(partId)) {
                continue;
            }
            rows.add(new IngredientRow(part, level, isExpandable(partId, part.stack(), client, ancestors)));
            appendIngredientRows(rows, partId, part.stack(), part.totalRequired(), level + 1, ancestors, client);
        }
        ancestors.remove(itemId);
    }

    /** 物品是否有可展示的直接合成材料（上级链含自身之外的直接材料至少存在其一） */
    private boolean isExpandable(ResourceLocation itemId, ItemStack stack, Minecraft client,
                                 Set<ResourceLocation> ancestors) {
        if (client == null || client.level == null) {
            return false;
        }
        List<MaterialBreakdownCalculator.BreakdownEntry> parts = MaterialBreakdownCalculator.breakdownSingle(
                stack, 1, client.level.getRecipeManager(), client.level.registryAccess());
        for (MaterialBreakdownCalculator.BreakdownEntry part : parts) {
            ResourceLocation partId = BuiltInRegistries.ITEM.getKey(part.stack().getItem());
            if (!partId.equals(itemId) && !ancestors.contains(partId)) {
                return true;
            }
        }
        return false;
    }
}
