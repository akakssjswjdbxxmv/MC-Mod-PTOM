package com.lege.client.config;

import com.lege.Temp1792221464;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.gui.GuiConfigsBase;

import java.util.List;

/**
 * malilib 风格配置界面。
 * 选项对象仅作为 GUI 载体，值变化经 onSettingsChanged 回写 MaterialConfig（JSON 持久化），
 * 不走 malilib 的 ConfigManager 存储。忽略物品在主界面管理，不在此处。
 */
public final class MaterialConfigGui extends GuiConfigsBase {

    private static final ConfigBoolean HUD_ENABLED =
            new ConfigBoolean("temp1792221464.config.hud_enabled", true, "temp1792221464.config.hud_enabled.comment");
    private static final ConfigInteger HUD_TOP_N =
            new ConfigInteger("temp1792221464.config.hud_top_n", 5, 1, 15, "temp1792221464.config.hud_top_n.comment");

    public MaterialConfigGui() {
        super(10, 36, Temp1792221464.MOD_ID, null, "temp1792221464.gui.title.configs");
        // 主界面 / HUD / 快捷键可能在本 GUI 之外改过配置，打开时同步为当前值
        HUD_ENABLED.setBooleanValue(MaterialConfig.isHudEnabled());
        HUD_TOP_N.setIntegerValue(MaterialConfig.getHudTopN());
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(List.of(HUD_ENABLED, HUD_TOP_N, MaterialHotkeys.OPEN_GUI));
    }

    @Override
    protected boolean useKeybindSearch() {
        return false;
    }

    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        MaterialConfig.setHudEnabled(HUD_ENABLED.getBooleanValue());
        MaterialConfig.setHudTopN(HUD_TOP_N.getIntegerValue());
    }
}
