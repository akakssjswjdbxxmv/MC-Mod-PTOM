package com.lege.client.material;

import java.util.List;

/**
 * 一次采集的材料列表完整快照（不可变）。
 * hasMaterialList 为 false 表示用户尚未在 Litematica 中创建材料列表。
 */
public record MaterialListSnapshot(
        List<MaterialEntrySnapshot> entries,
        long countTotal,
        long countMissing,
        long countMismatched,
        long timestampMillis,
        boolean hasMaterialList
) {
    public MaterialListSnapshot {
        entries = List.copyOf(entries);
    }

    /** 未创建材料列表时的空快照，调用方无需判 null */
    public static MaterialListSnapshot empty() {
        return new MaterialListSnapshot(List.of(), 0L, 0L, 0L, System.currentTimeMillis(), false);
    }
}
