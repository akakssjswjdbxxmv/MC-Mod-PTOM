package com.lege.client.material;

import java.util.List;

/**
 * 材料列表统计结果（不可变）。
 * countMissing 为 litematica 口径的"建造缺失总量"。
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
