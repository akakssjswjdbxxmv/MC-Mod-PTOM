package com.lege.client.material;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成品 → 直接合成材料分解器。
 * 基于客户端配方管理器只分解一层（配方的直接输入），不递归到最原始材料：
 * 有配方的物品展开为其直接合成材料，无配方的物品视为其自身。
 * 带分解缓存。仅在客户端主线程调用。
 */
public final class MaterialBreakdownCalculator {

    /** double 累加误差容差 */
    private static final double EPSILON = 1e-6;

    /** 直接材料聚合条目：代表物品栈（count=1）与总需求量 */
    public record BreakdownEntry(ItemStack stack, long totalRequired) {
    }

    /** 每 1 个物品所需直接合成材料的配比分量 */
    private record BreakdownPart(Item item, double amount) {
    }

    private static final Map<Item, List<BreakdownPart>> BREAKDOWN_CACHE = new ConcurrentHashMap<>();
    private static volatile Map<Item, RecipeHolder<CraftingRecipe>> recipeIndex;
    private static volatile RecipeManager indexedManager;

    private MaterialBreakdownCalculator() {
    }

    /** 将材料列表快照分解为直接合成材料汇总，按总需求降序 */
    public static List<BreakdownEntry> calculate(MaterialListSnapshot snapshot,
                                                 RecipeManager recipes, RegistryAccess access) {
        Map<Item, RecipeHolder<CraftingRecipe>> index = recipeIndex(recipes, access);
        Map<Item, Double> totals = new HashMap<>();

        for (MaterialEntrySnapshot entry : snapshot.entries()) {
            for (BreakdownPart part : directPartsOf(entry.stack().getItem(), index, access)) {
                totals.merge(part.item(), part.amount() * entry.total(), Double::sum);
            }
        }

        return toEntries(totals);
    }

    /** 分解单个物品所需直接合成材料（按需求量 total 取整），供列表点击展开使用 */
    public static List<BreakdownEntry> breakdownSingle(ItemStack stack, long total,
                                                       RecipeManager recipes, RegistryAccess access) {
        Map<Item, RecipeHolder<CraftingRecipe>> index = recipeIndex(recipes, access);
        Map<Item, Double> totals = new HashMap<>();
        for (BreakdownPart part : directPartsOf(stack.getItem(), index, access)) {
            totals.merge(part.item(), part.amount() * total, Double::sum);
        }
        return toEntries(totals);
    }

    /** 聚合结果转条目列表并排序 */
    private static List<BreakdownEntry> toEntries(Map<Item, Double> totals) {
        List<BreakdownEntry> result = new ArrayList<>(totals.size());
        for (Map.Entry<Item, Double> e : totals.entrySet()) {
            long required = (long) Math.ceil(e.getValue() - EPSILON);
            if (required > 0) {
                result.add(new BreakdownEntry(new ItemStack(e.getKey()), required));
            }
        }
        result.sort(Comparator.comparingLong(BreakdownEntry::totalRequired).reversed()
                .thenComparing(en -> en.stack().getItem().toString()));
        return List.copyOf(result);
    }

    /** 配方输出索引：按配方管理器实例失效重建，同时清分解缓存 */
    private static Map<Item, RecipeHolder<CraftingRecipe>> recipeIndex(RecipeManager recipes, RegistryAccess access) {
        Map<Item, RecipeHolder<CraftingRecipe>> index = recipeIndex;
        if (index == null || indexedManager != recipes) {
            Map<Item, RecipeHolder<CraftingRecipe>> built = new HashMap<>();
            for (RecipeHolder<CraftingRecipe> holder : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
                ItemStack result = holder.value().getResultItem(access);
                if (!result.isEmpty()) {
                    // 同一输出有多个配方时保留首个（注册顺序稳定）
                    built.putIfAbsent(result.getItem(), holder);
                }
            }
            recipeIndex = Map.copyOf(built);
            indexedManager = recipes;
            BREAKDOWN_CACHE.clear();
        }
        return recipeIndex;
    }

    /** 每 1 个物品的直接合成材料配比；无配方则为其自身 */
    private static List<BreakdownPart> directPartsOf(Item item,
                                                     Map<Item, RecipeHolder<CraftingRecipe>> index,
                                                     RegistryAccess access) {
        List<BreakdownPart> cached = BREAKDOWN_CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        RecipeHolder<CraftingRecipe> holder = index.get(item);
        if (holder == null) {
            return List.of(new BreakdownPart(item, 1.0));
        }
        CraftingRecipe recipe = holder.value();
        long resultCount = Math.max(1, recipe.getResultItem(access).getCount());

        Map<Item, Double> parts = new HashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] candidates = ingredient.getItems();
            if (candidates.length == 0) {
                continue;
            }
            // 每个原料槽需求 1 个，按产出数量折算；标签多候选取首个作为代表（净需求口径）
            parts.merge(candidates[0].getItem(), 1.0 / resultCount, Double::sum);
        }
        List<BreakdownPart> list = new ArrayList<>(parts.size());
        for (Map.Entry<Item, Double> e : parts.entrySet()) {
            list.add(new BreakdownPart(e.getKey(), e.getValue()));
        }
        BREAKDOWN_CACHE.put(item, List.copyOf(list));
        return list;
    }
}
