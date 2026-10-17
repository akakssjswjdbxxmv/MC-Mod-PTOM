package com.lege.client.material;

import com.lege.client.config.MaterialConfig.CountMode;

/**
 * 数量显示格式化：个数优先 / 组数优先 / 盒数优先（1 盒 = 27 组）。
 * 纯函数，无状态。
 */
public final class MaterialCountFormatter {

    private static final int GROUPS_PER_BOX = 27;

    private MaterialCountFormatter() {
    }

    public static String format(long count, CountMode mode, int stackSize) {
        int size = Math.max(1, stackSize);
        return switch (mode) {
            case INDIVIDUAL -> String.valueOf(count);
            case STACKS -> formatStacks(count, size);
            case SHULKERS -> formatShulkers(count, size);
        };
    }

    /** 组数优先：不足一组只显示个数，如 "7组+8个"、"8个"、"7组" */
    private static String formatStacks(long count, int stackSize) {
        long groups = count / stackSize;
        long rem = count % stackSize;
        if (groups == 0) {
            return rem + "个";
        }
        return rem > 0 ? groups + "组+" + rem + "个" : groups + "组";
    }

    /** 盒数优先：零段省略，如 "1盒+2组+3个"、"2组+3个"、"3个"、"1盒" */
    private static String formatShulkers(long count, int stackSize) {
        long boxSize = (long) stackSize * GROUPS_PER_BOX;
        long boxes = count / boxSize;
        long groups = count % boxSize / stackSize;
        long individual = count % stackSize;

        StringBuilder sb = new StringBuilder();
        if (boxes > 0) {
            sb.append(boxes).append("盒");
        }
        if (groups > 0) {
            sb.append(sb.isEmpty() ? "" : "+").append(groups).append("组");
        }
        if (individual > 0) {
            sb.append(sb.isEmpty() ? "" : "+").append(individual).append("个");
        }
        return sb.isEmpty() ? "0个" : sb.toString();
    }
}
