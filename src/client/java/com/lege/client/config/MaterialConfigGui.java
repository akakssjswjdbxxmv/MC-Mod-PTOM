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
    private static final ConfigInteger HUD_ANCHOR =
            new ConfigInteger("temp1792221464.config.hud_anchor", 0, 0, 3, "temp1792221464.config.hud_anchor.comment");
    private static final ConfigInteger HUD_OFFSET_X =
            new ConfigInteger("temp1792221464.config.hud_offset_x", 0, 0, 200, "temp1792221464.config.hud_offset_x.comment");
    private static final ConfigInteger HUD_OFFSET_Y =
            new ConfigInteger("temp1792221464.config.hud_offset_y", 0, 0, 200, "temp1792221464.config.hud_offset_y.comment");

    public MaterialConfigGui() {
        super(10, 36, Temp1792221464.MOD_ID, null, "temp1792221464.gui.title.configs");
        // 主界面 / HUD / 快捷键可能在本 GUI 之外改过配置，打开时同步为当前值
        HUD_ENABLED.setBooleanValue(MaterialConfig.isHudEnabled());
        HUD_TOP_N.setIntegerValue(MaterialConfig.getHudTopN());
        HUD_ANCHOR.setIntegerValue(MaterialConfig.getHudAnchor().ordinal());
        HUD_OFFSET_X.setIntegerValue(MaterialConfig.getHudOffsetX());
        HUD_OFFSET_Y.setIntegerValue(MaterialConfig.getHudOffsetY());
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(List.of(
                HUD_ENABLED, HUD_TOP_N, HUD_ANCHOR, HUD_OFFSET_X, HUD_OFFSET_Y, MaterialHotkeys.OPEN_GUI));
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
        MaterialConfig.setHudAnchor(MaterialConfig.HudAnchor.values()[HUD_ANCHOR.getIntegerValue()]);
        MaterialConfig.setHudOffsetX(HUD_OFFSET_X.getIntegerValue());
        MaterialConfig.setHudOffsetY(HUD_OFFSET_Y.getIntegerValue());
    }
}
