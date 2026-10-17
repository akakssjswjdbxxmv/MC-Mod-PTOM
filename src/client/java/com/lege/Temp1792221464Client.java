package com.lege;

import com.lege.client.material.MaterialHudRenderer;
import com.lege.client.material.MaterialRefreshScheduler;
import net.fabricmc.api.ClientModInitializer;

public class Temp1792221464Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 启动材料列表刷新调度器（JOIN 时启动，DISCONNECT 时停止）
		MaterialRefreshScheduler.getInstance().initialize();
		// 注册材料统计 HUD 渲染器
		new MaterialHudRenderer().register();
	}
}