package com.lege.client.gui;

import com.lege.Temp1792221464;
import com.lege.client.material.MaterialRefreshScheduler;
import com.lege.client.material.MaterialSession;
import com.lege.client.material.SchematicFileBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 投影文件浏览器（模组入口界面）：目录列表 + 文件选择。
 * 默认定位 schematics 目录（与 litematica 一致），点击目录进入、点击文件解析并打开材料界面。
 * 目录在前文件在后，按名称排序；复用 Scrollbar 组件。
 */
public final class SchematicBrowserScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 18;
    /** 顶部标题 + 路径栏高度 */
    private static final int HEADER_HEIGHT = 42;
    private static final int FOOTER_HEIGHT = 34;
    private static final int LIST_HEADER_HEIGHT = 6;

    private static final int COLOR_DIR = 0x55FFFF;
    private static final int COLOR_FILE = 0xFFFFFF;
    private static final int COLOR_UP = 0xFFFF55;

    private final Scrollbar scrollbar = new Scrollbar();
    private Path currentDir;
    private Path parentDir;
    /** 显示行：子目录（前）+ 可解析投影文件（后） */
    private List<Path> entries = List.of();
    private Component errorMessage;

    public SchematicBrowserScreen() {
        this(defaultStartDir());
    }

    public SchematicBrowserScreen(Path startDir) {
        super(Component.translatable("gui.temp1792221464.browser.title"));
        navigateTo(startDir != null ? startDir : defaultStartDir());
    }

    /** 初始目录：每次进入固定为投影默认目录（游戏目录下的 schematics，与 litematica 一致） */
    private static Path defaultStartDir() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width - MARGIN - 60, this.height - FOOTER_HEIGHT + 8, 60, 20).build());
    }

    /** 进入目录并刷新列表；目录不存在时显示错误并保留空列表 */
    private void navigateTo(Path dir) {
        this.currentDir = dir;
        this.parentDir = dir.getParent();
        this.entries = List.of();
        this.errorMessage = null;

        if (!Files.isDirectory(dir)) {
            this.errorMessage = Component.translatable("gui.temp1792221464.browser.dir_missing", dir.toString());
            return;
        }

        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                if (Files.isDirectory(path)) {
                    dirs.add(path);
                } else if (SchematicFileBridge.isSupportedFile(path.getFileName().toString())) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            Temp1792221464.LOGGER.error("读取目录失败: {}", dir, e);
            this.errorMessage = Component.translatable("gui.temp1792221464.browser.dir_missing", dir.toString());
            return;
        }

        Comparator<Path> byName = Comparator.comparing(p -> p.getFileName().toString().toLowerCase());
        dirs.sort(byName);
        files.sort(byName);
        List<Path> all = new ArrayList<>(dirs.size() + files.size());
        all.addAll(dirs);
        all.addAll(files);
        this.entries = all;
    }

    /** 点击投影文件：解析成功后打开材料界面，失败则显示错误 */
    private void openFile(Path file) {
        if (MaterialSession.getInstance().open(file)) {
            MaterialRefreshScheduler.getInstance().notifyNow();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new MaterialListScreen());
            }
        } else {
            this.errorMessage = Component.translatable("gui.temp1792221464.browser.parse_failed",
                    file.getFileName().toString());
        }
    }

    /** 与主界面一致：跳过 vanilla 高斯模糊，仅暗化渐变保持背景清晰 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level != null) {
            graphics.fillGradient(0, 0, this.width, this.height, 0x66000000, 0x99101010);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, MARGIN, 0xFFFFFF);
        // 当前路径（过长时截断显示）
        String pathText = this.font.plainSubstrByWidth(this.currentDir.toString(), this.width - MARGIN * 2);
        graphics.drawString(this.font, pathText, MARGIN, MARGIN + 14, 0xAAAAAA, true);

        if (this.errorMessage != null) {
            graphics.drawString(this.font, this.errorMessage, MARGIN, MARGIN + 26, 0xFF7777, true);
        } else if (this.entries.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.temp1792221464.browser.empty"), MARGIN, MARGIN + 26, 0xDDDDDD, true);
        }

        renderList(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 可滚动条目列表（scissor 裁剪），含 ".." 返回上级行与悬停高亮 */
    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listTop = MARGIN + HEADER_HEIGHT;
        int listBottom = this.height - FOOTER_HEIGHT;
        int headerBottom = listTop + LIST_HEADER_HEIGHT;
        int viewHeight = listBottom - headerBottom;

        int rowCount = this.entries.size() + (this.parentDir != null ? 1 : 0);
        this.scrollbar.beginFrame(headerBottom, viewHeight, Math.max(0, rowCount * ROW_HEIGHT - viewHeight));

        graphics.enableScissor(0, headerBottom, this.width, listBottom);
        int hoverIndex = hoverRowIndex(mouseX, mouseY);
        for (int i = 0; i < rowCount; i++) {
            int y = headerBottom + i * ROW_HEIGHT - this.scrollbar.scrollOffset();
            if (y + ROW_HEIGHT < headerBottom || y > listBottom) {
                continue;
            }
            renderRow(graphics, i, y, i == hoverIndex);
        }
        graphics.disableScissor();
        this.scrollbar.render(graphics, this.scrollbar.trackX(this.width));
    }

    private void renderRow(GuiGraphics graphics, int index, int y, boolean hovered) {
        if (hovered) {
            graphics.fill(MARGIN, y, this.width - MARGIN, y + ROW_HEIGHT, 0x30FFFFFF);
        }
        if (this.parentDir != null && index == 0) {
            graphics.drawString(this.font, "[..]", MARGIN + 4, y + 5, COLOR_UP, true);
            return;
        }
        Path path = this.entries.get(index - (this.parentDir != null ? 1 : 0));
        boolean isDir = Files.isDirectory(path);
        String name = path.getFileName().toString();
        String label = this.font.plainSubstrByWidth(isDir ? "[" + name + "]" : name,
                this.width - MARGIN * 2 - 8 - 10);
        graphics.drawString(this.font, label, MARGIN + 4, y + 5, isDir ? COLOR_DIR : COLOR_FILE, true);
    }

    /** 鼠标所在行索引（不在列表区或无悬停时返回 -1） */
    private int hoverRowIndex(double mouseX, double mouseY) {
        if (!this.scrollbar.inViewArea(mouseY) || mouseX >= this.scrollbar.trackX(this.width)) {
            return -1;
        }
        int index = (int) ((mouseY - this.scrollbar.trackTop() + this.scrollbar.scrollOffset()) / ROW_HEIGHT);
        int rowCount = this.entries.size() + (this.parentDir != null ? 1 : 0);
        return index >= 0 && index < rowCount ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int trackX = this.scrollbar.trackX(this.width);
        if (this.scrollbar.mouseClicked(mouseX, mouseY, button, trackX)) {
            return true;
        }
        if (button == 0) {
            int index = hoverRowIndex(mouseX, mouseY);
            if (index >= 0) {
                onRowClicked(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onRowClicked(int index) {
        if (this.parentDir != null) {
            if (index == 0) {
                navigateTo(this.parentDir);
                return;
            }
            index -= 1;
        }
        Path path = this.entries.get(index);
        if (Files.isDirectory(path)) {
            navigateTo(path);
        } else {
            openFile(path);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrollbar.mouseDragged(mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollbar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int listTop = MARGIN + HEADER_HEIGHT;
        int listBottom = this.height - FOOTER_HEIGHT;
        if (mouseY >= listTop && mouseY <= listBottom) {
            this.scrollbar.scrollBy(-(int) deltaY * ROW_HEIGHT);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
