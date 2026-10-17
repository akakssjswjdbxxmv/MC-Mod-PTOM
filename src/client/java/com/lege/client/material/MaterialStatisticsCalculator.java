package com.lege.client.material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 材料统计计算器（纯函数，无状态）。
 * 输入不可变快照，输出不可变统计结果，天然线程安全。
 */
public final class MaterialStatisticsCalculator {

    private MaterialStatisticsCalculator() {
    }

    /**
     * 计算材料统计。
     *
     * @param snapshot 材料列表快照
     * @param topN     返回的紧缺材料数量上限
     * @return 统计结果
     */
    public static MaterialStatistics calculate(MaterialListSnapshot snapshot, int topN) {
        if (snapshot == null || !snapshot.hasMaterialList() || snapshot.entries().isEmpty()) {
            return MaterialStatistics.empty();
        }

        long countTotal = 0;
        long countAvailable = 0;

        // 收集建造缺失的条目（missing > 0）
        List<MaterialEntrySnapshot> missingEntries = new ArrayList<>();

        for (MaterialEntrySnapshot entry : snapshot.entries()) {
            countTotal += entry.total();
            countAvailable += entry.available();

            if (entry.missing() > 0) {
                missingEntries.add(entry);
            }
        }

        // 按建造缺失降序排序，取 Top N
        missingEntries.sort(Comparator.comparingInt(MaterialEntrySnapshot::missing).reversed());
        List<MaterialEntrySnapshot> topMissing = missingEntries.size() > topN
                ? List.copyOf(missingEntries.subList(0, topN))
                : List.copyOf(missingEntries);

        double progressPercent = countTotal > 0 ? countAvailable * 100.0 / countTotal : 0.0;

        return new MaterialStatistics(
                countTotal,
                countAvailable,
                snapshot.countMissing(),
                progressPercent,
                topMissing,
                snapshot.timestampMillis()
        );
    }
}
