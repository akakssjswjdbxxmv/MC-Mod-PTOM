package com.lege.client.material;

import java.util.List;

/**
 * 材料列表统计结果（不可变）。
 * countMissing 为扣除背包已有后的"还需准备的缺口"总量（忽略物品已过滤）。
 */
public record MaterialStatistics(
        long countTotal,
        long countAvailable,
        long countMissing,
        double progressPercent,
        List<MaterialEntrySnapshot> topMissing,
        long timestampMillis
) {
    /** 空统计（未创建材料列表或无条目时） */
    public static MaterialStatistics empty() {
        return new MaterialStatistics(0L, 0L, 0L, 0.0, List.of(), System.currentTimeMillis());
    }
}
