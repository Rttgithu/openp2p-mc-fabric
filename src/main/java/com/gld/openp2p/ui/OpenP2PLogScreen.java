/*
 * openp2p-mc - OpenP2P remote play mod for Minecraft 1.20.1 (Fabric)
 * Copyright (C) 2025 gld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.gld.openp2p.ui;

import com.gld.openp2p.NodeManager;
import com.gld.openp2p.OpenP2PMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志查看界面：实时显示 OpenP2P 节点日志（也可直接打开
 * <游戏目录>/openp2p/openp2p.log 查看完整日志）。
 */
public class OpenP2PLogScreen extends Screen {

    private final Screen parent;
    private int scroll;
    private String cached = "";
    private List<String> wrapped = new ArrayList<>();
    private long lastWrap = 0;
    private String hint = "";

    public OpenP2PLogScreen(Screen parent) {
        super(Text.literal("OpenP2P 日志"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ButtonWidget copyButton = ButtonWidget.builder(Text.literal("复制全部"), b -> {
                    if (this.client != null && this.client.keyboard != null) {
                        this.client.keyboard.setClipboard(String.join("\n", OpenP2PMod.get().node().log().snapshot()));
                    }
                })
                .dimensions(this.width / 2 - 155, this.height - 28, 100, 20)
                .build();
        ButtonWidget clearButton = ButtonWidget.builder(Text.literal("清空界面"), b -> {
                    wrapped = new ArrayList<>();
                    hint = "（内容已清空，新日志仍会追加）";
                })
                .dimensions(this.width / 2 - 45, this.height - 28, 100, 20)
                .build();
        ButtonWidget backButton = ButtonWidget.builder(ScreenTexts.BACK, b -> this.client.setScreen(parent))
                .dimensions(this.width / 2 + 65, this.height - 28, 90, 20)
                .build();
        this.addDrawableChild(copyButton);
        this.addDrawableChild(clearButton);
        this.addDrawableChild(backButton);
    }

    private List<String> wrapLines(List<String> lines) {
        List<String> out = new ArrayList<>();
        int maxWidth = this.width - 40;
        for (String line : lines) {
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                String ch = line.substring(i, i + 1);
                if (cur.length() > 0 && this.textRenderer.getWidth(cur.toString() + ch) > maxWidth) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                cur.append(ch);
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
            }
        }
        return out;
    }

    @Override
    public void tick() {
        super.tick();
        NodeManager nm = OpenP2PMod.get().node();
        String snapshot = String.join("\u0001", nm.log().snapshot());
        if (!snapshot.equals(cached)) {
            cached = snapshot;
            wrapped = wrapLines(new ArrayList<>(nm.log().snapshot()));
        }
        int maxScroll = Math.max(0, wrapped.size() - visibleLines());
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    private int visibleLines() {
        return Math.max(0, (this.height - 70) / 10);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxScroll = Math.max(0, wrapped.size() - visibleLines());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) amount));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        NodeManager nm = OpenP2PMod.get().node();
        String stateLine = nm.statusText() + "  ·  我的 UID: " + nm.effectiveUid();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(stateLine), this.width / 2, 22, 0x55FF55);

        int visible = visibleLines();
        int total = wrapped.size();
        int start = Math.max(0, Math.min(scroll, Math.max(0, total - visible)));
        int maxY = this.height - 44;
        int y = 34;
        for (int i = start; i < total && y <= maxY; i++) {
            context.drawText(this.textRenderer, Text.literal(wrapped.get(i)), 20, y, 0xDDDDDD, false);
            y += 10;
        }
        if (total == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("暂无日志（尚未启动过 OpenP2P）"), this.width / 2, 60, 0x888888);
        }
        if (!hint.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint), this.width / 2, this.height - 62, 0x888888);
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("完整日志文件: <游戏目录>/openp2p/openp2p.log  ·  滚轮滚动"),
                this.width / 2, this.height - 52, 0x777777);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}