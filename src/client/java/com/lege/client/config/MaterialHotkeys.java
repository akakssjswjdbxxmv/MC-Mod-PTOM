package com.lege.client.config;

import com.lege.Temp1792221464;
import com.lege.client.gui.MaterialListScreen;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 快捷键定义与注册。
 * 走 malilib 输入体系（KeybindProvider + 回调），替代 Fabric KeyMapping，
 * 可在 malilib 配置界面直接编辑键位。
 */
public final class MaterialHotkeys {

    /** 打开材料主界面（松开触发，避免按键穿透到界面） */
    public static final ConfigHotkey OPEN_GUI =
            new ConfigHotkey("temp1792221464.hotkey.open_menu", "H",
                    KeybindSettings.RELEASE_EXCLUSIVE, "temp1792221464.hotkey.open_menu.comment");

    private static final List<ConfigHotkey> HOTKEYS = List.of(OPEN_GUI);

    private MaterialHotkeys() {
    }

    /** 客户端初始化时调用：注册回调 + 键位提供者 */
    public static void init() {
        OPEN_GUI.getKeybind().setCallback((action, key) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.screen == null) {
                client.setScreen(new MaterialListScreen());
                return true;
            }
            return false;
        });
        InputEventHandler.getKeybindManager().registerKeybindProvider(new Provider());
    }

    /** 将快捷键接入 malilib 输入分发与总键位列表 */
    private record Provider() implements IKeybindProvider {
        @Override
        public void addKeysToMap(IKeybindManager manager) {
            manager.addKeybindToMap(OPEN_GUI.getKeybind());
        }

        @Override
        public void addHotkeys(IKeybindManager manager) {
            manager.addHotkeysForCategory(Temp1792221464.MOD_ID,
                    "temp1792221464.hotkeys.category", List.<IHotkey>copyOf(HOTKEYS));
        }
    }
}
