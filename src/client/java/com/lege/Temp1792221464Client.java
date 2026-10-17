package com.lege;

import com.lege.client.config.MaterialConfig;
import com.lege.client.gui.MaterialListScreen;
import com.lege.client.material.MaterialHudRenderer;
import com.lege.client.material.MaterialRefreshScheduler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class Temp1792221464Client implements ClientModInitializer {

	private static final String KEY_CATEGORY = "key.categories.temp1792221464";

	/** 打开模组主界面的快捷键（默认 H，可在控制设置中修改） */
	private static final KeyMapping OPEN_MENU = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.temp1792221464.open_menu", GLFW.GLFW_KEY_H, KEY_CATEGORY));

	@Override
	public void onInitializeClient() {
		MaterialConfig.load();
		// 启动材料列表刷新调度器（JOIN 时启动，DISCONNECT 时停止）
		MaterialRefreshScheduler.getInstance().initialize();
		// 注册材料统计 HUD 渲染器
		MaterialHudRenderer.getInstance().register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_MENU.consumeClick()) {
				if (client.player != null && client.screen == null) {
					client.setScreen(new MaterialListScreen());
				}
			}
		});
	}
}
