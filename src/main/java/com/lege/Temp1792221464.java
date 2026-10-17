package com.lege;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Temp1792221464 implements ModInitializer {
	public static final String MOD_ID = "temp1792221464";

	/** 以 modid 命名 logger，日志来源清晰 */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 主环境暂无注册逻辑（本模组功能全在客户端），入口保留供扩展
		LOGGER.info("Material Assistant initialized");
	}
}