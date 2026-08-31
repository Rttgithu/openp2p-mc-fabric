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

import com.gld.openp2p.OpenP2PConfig;
import com.gld.openp2p.OpenP2PMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * 修改自己 UID 的界面。默认 UID = 游戏 ID，修改后将保存到配置，下次开启分享生效。
 */
public class OpenP2PUidScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget uidField;

    public OpenP2PUidScreen(Screen parent) {
        super(Text.literal("修改 OpenP2P UID"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        OpenP2PConfig cfg = OpenP2PMod.get().config();

        uidField = new TextFieldWidget(this.textRenderer, cx - 100, 70, 200, 20, Text.literal("我的 UID"));
        uidField.setMaxLength(32);
        uidField.setText(OpenP2PMod.get().node().effectiveUid());
        this.addDrawableChild(uidField);

        ButtonWidget copyButton = ButtonWidget.builder(Text.literal("复制 UID"), b -> {
                    if (this.client != null && this.client.keyboard != null) {
                        this.client.keyboard.setClipboard(OpenP2PMod.get().node().effectiveUid());
                        this.client.inGameHud.getChatHud().addMessage(
                                Text.literal("[OpenP2P] 已复制 UID: " + OpenP2PMod.get().node().effectiveUid()));
                    }
                })
                .dimensions(cx - 105, 108, 100, 20)
                .build();
        ButtonWidget saveButton = ButtonWidget.builder(Text.literal("保存"), b -> {
                    String v = uidField.getText().trim().replace(" ", "");
                    if (v.isEmpty()) {
                        return;
                    }
                    cfg.uid = v;
                    cfg.save();
                    OpenP2PMod.get().node().log("UID 已修改为 " + v + "（下次开启分享/连接时生效）");
                    this.client.setScreen(parent);
                })
                .dimensions(cx + 5, 108, 100, 20)
                .build();
        ButtonWidget backButton = ButtonWidget.builder(ScreenTexts.BACK, b -> this.client.setScreen(parent))
                .dimensions(cx - 50, 134, 100, 20)
                .build();
        this.addDrawableChild(copyButton);
        this.addDrawableChild(saveButton);
        this.addDrawableChild(backButton);
    }

    @Override
    public void tick() {
        super.tick();
        uidField.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 24, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("默认等于你的游戏 ID，可改成任意字母数字下划线"), this.width / 2, 46, 0xA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("真实节点名会自动附加统一隐藏后缀（如 -op2pmc），无需自己加"), this.width / 2, 94, 0x777777);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}