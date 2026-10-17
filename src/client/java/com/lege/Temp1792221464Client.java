package com.lege;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import net.fabricmc.api.ClientModInitializer;

public class Temp1792221464Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 客户端入口：渲染、按键、客户端事件注册写在这里
	}

	/**
	 * Litematica API 调用示例（编译期验证集成可用）。
	 * 注意：实际运行时调用须在客户端线程（事件回调/GUI）中，且等世界加载后再操作管理器。
	 */
	private void litematicaApiSample() {
		// 已加载到内存的投影文件
		int loadedCount = SchematicHolder.getInstance().getAllSchematics().size();

		// 放置管理器：查询/操作世界中的投影放置
		SchematicPlacementManager placements = DataManager.getSchematicPlacementManager();

		Temp1792221464.LOGGER.info("litematica 集成验证: 已加载投影 {}, 放置管理器 {}", loadedCount, placements != null);
	}
}