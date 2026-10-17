package com.lege.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lege.Temp1792221464;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模组客户端配置（轻量 JSON 持久化）。
 * 仅在客户端主线程访问；忽略列表以 volatile 不可变集发布，供调度器线程安全读取。
 */
public final class MaterialConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("temp1792221464.json");

    private static final int TOP_N_MIN = 1;
    private static final int TOP_N_MAX = 15;

    /** 数量显示模式：个数 / 组数 / 盒数优先 */
    public enum CountMode { INDIVIDUAL, STACKS, SHULKERS }

    /** HUD 屏幕锚点：四角 */
    public enum HudAnchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private static final int OFFSET_MAX = 200;

    private static boolean hudEnabled = true;
    private static int hudTopN = 5;
    private static CountMode displayMode = CountMode.INDIVIDUAL;
    private static HudAnchor hudAnchor = HudAnchor.TOP_LEFT;
    private static int hudOffsetX = 0;
    private static int hudOffsetY = 0;
    /** 忽略的物品 id（不参与统计与列表），不可变集整体替换 */
    private static volatile Set<String> ignoredItems = Set.of();

    private MaterialConfig() {
    }

    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static int getHudTopN() {
        return hudTopN;
    }

    public static CountMode getDisplayMode() {
        return displayMode;
    }

    public static HudAnchor getHudAnchor() {
        return hudAnchor;
    }

    public static void setHudAnchor(HudAnchor anchor) {
        if (anchor != null && anchor != hudAnchor) {
            hudAnchor = anchor;
            save();
        }
    }

    public static int getHudOffsetX() {
        return hudOffsetX;
    }

    public static int getHudOffsetY() {
        return hudOffsetY;
    }

    public static void setHudOffsetX(int x) {
        x = clampOffset(x);
        if (x != hudOffsetX) {
            hudOffsetX = x;
            save();
        }
    }

    public static void setHudOffsetY(int y) {
        y = clampOffset(y);
        if (y != hudOffsetY) {
            hudOffsetY = y;
            save();
        }
    }

    private static int clampOffset(int v) {
        return Math.max(0, Math.min(OFFSET_MAX, v));
    }

    /** 循环切换显示模式：个数 → 组数 → 盒数 → 个数 */
    public static void cycleDisplayMode() {
        CountMode[] modes = CountMode.values();
        displayMode = modes[(displayMode.ordinal() + 1) % modes.length];
        save();
    }

    public static boolean isIgnored(String itemId) {
        return ignoredItems.contains(itemId);
    }

    public static int getIgnoredCount() {
        return ignoredItems.size();
    }

    public static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
        save();
    }

    public static void setHudTopN(int topN) {
        hudTopN = Math.max(TOP_N_MIN, Math.min(TOP_N_MAX, topN));
        save();
    }

    /** 忽略物品（已存在则无操作），非法 id 忽略；不可变集整体替换保证线程安全发布 */
    public static void addIgnoredItem(String itemId) {
        if (ResourceLocation.tryParse(itemId) == null) {
            return;
        }
        LinkedHashSet<String> mutable = new LinkedHashSet<>(ignoredItems);
        mutable.add(itemId);
        ignoredItems = Set.copyOf(mutable);
        save();
    }

    /** 清除全部忽略，恢复所有物品显示 */
    public static void clearIgnored() {
        ignoredItems = Set.of();
        save();
    }

    /** 客户端入口调用；加载失败时使用默认值，不阻断启动 */
    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data data = GSON.fromJson(Files.readString(FILE), Data.class);
                if (data != null) {
                    hudEnabled = data.hudEnabled;
                    hudTopN = Math.max(TOP_N_MIN, Math.min(TOP_N_MAX, data.hudTopN));
                    displayMode = clampMode(data.displayMode);
                    hudAnchor = clampAnchor(data.hudAnchor);
                    hudOffsetX = clampOffset(data.hudOffsetX);
                    hudOffsetY = clampOffset(data.hudOffsetY);
                    ignoredItems = Set.copyOf(data.ignoredItems);
                }
            }
        } catch (Exception e) {
            Temp1792221464.LOGGER.error("配置加载失败，使用默认值", e);
        }
    }

    private static CountMode clampMode(int ordinal) {
        CountMode[] modes = CountMode.values();
        return modes[Math.max(0, Math.min(modes.length - 1, ordinal))];
    }

    private static HudAnchor clampAnchor(int ordinal) {
        HudAnchor[] anchors = HudAnchor.values();
        return anchors[Math.max(0, Math.min(anchors.length - 1, ordinal))];
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Data data = new Data();
            data.hudEnabled = hudEnabled;
            data.hudTopN = hudTopN;
            data.displayMode = displayMode.ordinal();
            data.hudAnchor = hudAnchor.ordinal();
            data.hudOffsetX = hudOffsetX;
            data.hudOffsetY = hudOffsetY;
            data.ignoredItems = new ArrayList<>(ignoredItems);
            Files.writeString(FILE, GSON.toJson(data));
        } catch (Exception e) {
            Temp1792221464.LOGGER.error("配置保存失败", e);
        }
    }

    private static class Data {
        boolean hudEnabled;
        int hudTopN;
        int displayMode;
        int hudAnchor;
        int hudOffsetX;
        int hudOffsetY;
        List<String> ignoredItems = new ArrayList<>();
    }
}
