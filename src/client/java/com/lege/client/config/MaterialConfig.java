package com.lege.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lege.Temp1792221464;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组客户端配置（轻量 JSON 持久化）。
 * 仅在客户端主线程访问，无需并发保护。
 */
public final class MaterialConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("temp1792221464.json");

    private static final int TOP_N_MIN = 1;
    private static final int TOP_N_MAX = 15;

    private static boolean hudEnabled = true;
    private static int hudTopN = 5;

    private MaterialConfig() {
    }

    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static int getHudTopN() {
        return hudTopN;
    }

    public static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
        save();
    }

    public static void setHudTopN(int topN) {
        hudTopN = Math.max(TOP_N_MIN, Math.min(TOP_N_MAX, topN));
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
                }
            }
        } catch (Exception e) {
            Temp1792221464.LOGGER.error("配置加载失败，使用默认值", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Data data = new Data();
            data.hudEnabled = hudEnabled;
            data.hudTopN = hudTopN;
            Files.writeString(FILE, GSON.toJson(data));
        } catch (Exception e) {
            Temp1792221464.LOGGER.error("配置保存失败", e);
        }
    }

    private static class Data {
        boolean hudEnabled;
        int hudTopN;
    }
}
