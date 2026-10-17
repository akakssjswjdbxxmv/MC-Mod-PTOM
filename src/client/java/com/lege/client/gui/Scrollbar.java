package com.lege.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 可复用滚动条组件：轨道 + 按可视比例缩放的滑块，托管滚动位置。
 * 支持拖拽滑块、点击轨道翻页；几何信息每帧由渲染方通过 beginFrame 提供
 * （render 先于鼠标事件执行），供鼠标事件命中判定使用。
 * 仅客户端主线程（Screen）使用，无并发需求。
 */
public final class Scrollbar {

    private static final int WIDTH = 6;
    private static final int MIN_SLIDER_HEIGHT = 20;

    private int scrollOffset;
    private boolean dragging;
    private double dragGrabOffset;

    /** 每帧缓存的轨道几何与最大滚动距离 */
    private int trackTop;
    private int trackHeight;
    private int maxScroll;

    public int scrollOffset() {
        return this.scrollOffset;
    }

    /** 列表可视区顶部 y（行索引换算用） */
    public int trackTop() {
        return this.trackTop;
    }

    /** 轨道左缘 x（右对齐到屏幕右缘留 2px 边距） */
    public int trackX(int screenWidth) {
        return screenWidth - WIDTH - 2;
    }

    /** y 是否落在列表可视区域内（行点击命中用） */
    public boolean inViewArea(double mouseY) {
        return mouseY >= this.trackTop && mouseY < this.trackTop + this.trackHeight;
    }

    /** 帧开始：更新几何缓存并钳制滚动位置到合法范围 */
    public void beginFrame(int trackTop, int trackHeight, int maxScroll) {
        this.trackTop = trackTop;
        this.trackHeight = trackHeight;
        this.maxScroll = maxScroll;
        this.scrollOffset = clamp(this.scrollOffset);
    }

    /** 渲染轨道与滑块（无溢出时不绘制） */
    public void render(GuiGraphics graphics, int trackX) {
        if (this.maxScroll <= 0) {
            return;
        }
        int trackBottom = this.trackTop + this.trackHeight;
        graphics.fill(trackX, this.trackTop, trackX + WIDTH, trackBottom, 0x40000000);

        int sliderHeight = sliderHeight();
        int sliderY = this.trackTop + (this.trackHeight - sliderHeight) * this.scrollOffset / this.maxScroll;
        int color = this.dragging ? 0xF0F0F0 : 0x80FFFFFF;
        graphics.fill(trackX, sliderY, trackX + WIDTH, sliderY + sliderHeight, color);
    }

    /** 点击轨道：落在滑块上开始拖拽，否则向点击方向翻一页；命中返回 true */
    public boolean mouseClicked(double mouseX, double mouseY, int button, int trackX) {
        if (button != 0 || this.maxScroll <= 0
                || mouseX < trackX || mouseX > trackX + WIDTH
                || !inViewArea(mouseY)) {
            return false;
        }
        int sliderHeight = sliderHeight();
        int sliderY = this.trackTop + (this.trackHeight - sliderHeight) * this.scrollOffset / this.maxScroll;
        if (mouseY >= sliderY && mouseY <= sliderY + sliderHeight) {
            this.dragging = true;
            this.dragGrabOffset = mouseY - sliderY;
        } else {
            int page = mouseY < sliderY ? -this.trackHeight : this.trackHeight;
            this.scrollOffset = clamp(this.scrollOffset + page);
        }
        return true;
    }

    /** 拖拽滑块滚动列表 */
    public boolean mouseDragged(double mouseY) {
        if (!this.dragging || this.maxScroll <= 0) {
            return false;
        }
        int sliderHeight = sliderHeight();
        int movable = Math.max(1, this.trackHeight - sliderHeight);
        int target = (int) Math.round(
                (mouseY - this.dragGrabOffset - this.trackTop) * this.maxScroll / movable);
        this.scrollOffset = clamp(target);
        return true;
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    /** 滚轮步进（deltaY 正为向上），已钳制到合法范围 */
    public void scrollBy(int delta) {
        this.scrollOffset = clamp(this.scrollOffset + delta);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(this.maxScroll, value));
    }

    /** 滑块高度按可视比例 */
    private int sliderHeight() {
        return Math.max(MIN_SLIDER_HEIGHT, this.trackHeight * this.trackHeight / (this.trackHeight + this.maxScroll));
    }
}
