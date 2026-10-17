package com.lege;

import com.lege.client.config.MaterialConfig;
import com.lege.client.config.MaterialConfigGui;
import com.lege.client.config.MaterialHotkeys;
import com.lege.client.material.MaterialHudRenderer;
import com.lege.client.material.MaterialRefreshScheduler;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.fabricmc.api.ClientModInitializer;

public class Temp1792221464Client implements ClientModInitializer {

	/** malilib 配置列表中显示的模组名 */
	private static final String MOD_NAME = "Material Assistant";

	@Override
	public void onInitializeClient() {
		MaterialConfig.load();
		// 启动材料列表刷新调度器（JOIN 时启动，DISCONNECT 时停止）
		MaterialRefreshScheduler.getInstance().initialize();
		// 注册材料统计 HUD 渲染器
		MaterialHudRenderer.getInstance().register();
		// 快捷键走 malilib 输入体系（可在 malilib 配置界面编辑键位）
		MaterialHotkeys.init();

		// 注册到 malilib 配置界面列表（malilib 游戏初始化完成后统一回调）
		InitializationHandler.getInstance().registerInitializationHandler(() ->
				Registry.CONFIG_SCREEN.registerConfigScreenFactory(
						new ModInfo(Temp1792221464.MOD_ID, MOD_NAME, MaterialConfigGui::new)));
	}
}
