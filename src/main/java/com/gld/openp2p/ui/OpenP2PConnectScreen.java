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
import com.gld.openp2p.OpenP2PConfig;
import com.gld.openp2p.OpenP2PMod;
import com.gld.openp2p.mixin.MultiplayerScreenAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * 联机端输入界面：多人游戏界面点击 “OpenP2P UID 联机” 后打开。
 * 输入对方 UID（可带 :端口）与本地端口，点击连接后由 NodeManager 自动建立隧道并加入服务器。
 */
public class OpenP2PConnectScreen extends Screen {

    private final Screen parent;
    /** 编辑模式：非 null 表示正在编辑该地址的远程联机条目 */
    private final String editAddress;
    private final String editUid;
    private final int editPort;
    private TextFieldWidget uidField;
    private TextFieldWidget portField;
    private TextFieldWidget localField;
    private ButtonWidget connectButton;

    public OpenP2PConnectScreen(Screen parent) {
        super(Text.literal("添加远程联机"));
        this.parent = parent;
        this.editAddress = null;
        this.editUid = "";
        this.editPort = -1;
    }

    public OpenP2PConnectScreen(Screen parent, String editAddress, String editUid, int editPort) {
        super(Text.literal("编辑远程联机"));
        this.parent = parent;
        this.editAddress = editAddress;
        this.editUid = editUid == null ? "" : editUid;
        this.editPort = editPort;
    }

    private boolean isEdit() {
        return editAddress != null;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        OpenP2PConfig cfg = OpenP2PMod.get().config();

        // 第一行：UID（编辑模式预填；添加模式默认留空，不记忆）
        uidField = new TextFieldWidget(this.textRenderer, cx - 100, 78, 200, 20, Text.literal("对方 UID"));
        uidField.setMaxLength(64);
        if (isEdit()) {
            uidField.setText(editUid);
        }
        this.addDrawableChild(uidField);

        // 第二行：端口 + 本地端口
        int defPort = isEdit() ? editPort : cfg.lastPeerPort;
        if (defPort < 1 || defPort > 65535) {
            defPort = 25565;
        }
        portField = new TextFieldWidget(this.textRenderer, cx - 100, 116, 92, 20, Text.literal("远程端口"));
        portField.setMaxLength(6);
        portField.setText(String.valueOf(defPort));
        this.addDrawableChild(portField);

        localField = new TextFieldWidget(this.textRenderer, cx + 14, 116, 86, 20, Text.literal("本地端口"));
        localField.setMaxLength(6);
        localField.setText(String.valueOf(defPort));
        this.addDrawableChild(localField);

        // 第三行：按钮
        connectButton = ButtonWidget.builder(Text.literal(isEdit() ? "保存" : "添加"), b -> onAdd())
                .dimensions(cx - 155, 152, 100, 20)
                .build();
        ButtonWidget logButton = ButtonWidget.builder(Text.literal("查看日志"), b -> this.client.setScreen(new OpenP2PLogScreen(this)))
                .dimensions(cx - 45, 152, 100, 20)
                .build();
        ButtonWidget backButton = ButtonWidget.builder(ScreenTexts.BACK, b -> this.client.setScreen(parent))
                .dimensions(cx + 65, 152, 90, 20)
                .build();
        this.addDrawableChild(connectButton);
        this.addDrawableChild(logButton);
        this.addDrawableChild(backButton);
    }

    private void onAdd() {
        String uid = uidField.getText().trim().replace(" ", "");
        int dst;
        try {
            dst = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            dst = 0;
        }
        int local;
        try {
            local = Integer.parseInt(localField.getText().trim());
        } catch (NumberFormatException e) {
            local = 0;
        }
        if (uid.isEmpty()) {
            OpenP2PMod.get().sendChat("§c请输入对方 UID");
            return;
        }
        if (local < 1 || local > 65535 || dst < 1 || dst > 65535) {
            dst = 25565;
            local = dst;
        }
        OpenP2PConfig cfg = OpenP2PMod.get().config();
        cfg.lastPeerPort = dst;
        cfg.save();

        String address = "127.0.0.1:" + local;

        // 同步原版服务器列表：添加时完全可重复（同名异端口 / 同端口异名均并存）；
        // 编辑时用“旧名字+旧地址”精确匹配被编辑的那一条并替换
        if (parent instanceof MultiplayerScreen) {
            MultiplayerScreen mps = (MultiplayerScreen) parent;
            MultiplayerScreenAccessor acc = (MultiplayerScreenAccessor) mps;
            ServerList list = acc.openp2p$serverList();
            ServerInfo info = new ServerInfo("远程联机-" + uid, address, false);
            if (isEdit()) {
                String oldName = "远程联机-" + editUid;
                boolean replaced = false;
                int n = list.size();
                for (int i = 0; i < n; i++) {
                    ServerInfo s = list.get(i);
                    if (s.name != null && s.name.equals(oldName)
                            && editAddress != null && editAddress.equals(s.address)) {
                        list.set(i, info);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    list.add(info, false);
                }
            } else {
                list.add(info, false);
            }
            list.saveFile();
            acc.openp2p$serverListWidget().setServers(list);
        }
        OpenP2PMod.get().sendChat(isEdit()
                ? "§a已更新远程联机: §e" + uid + ":" + local
                : "§a已添加远程联机: §e远程联机-" + uid + "§a（点击该项即连接隧道并加入游戏）");

        // 回到多人游戏服务器列表
        if (connectButton != null) {
            connectButton.active = false;
        }
        this.client.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        uidField.tick();
        portField.tick();
        localField.tick();
        // 出错后允许重新点“连接”重试
        if (connectButton != null && !connectButton.active
                && OpenP2PMod.get().node().state() == NodeManager.State.ERROR) {
            connectButton.active = true;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 24, 0xFFFFFF);
        context.drawText(this.textRenderer, Text.literal("对方 UID（填房主的游戏 ID 即可，隐藏后缀自动补全）"), this.width / 2 - 100, 62, 0xA0A0A0, false);
        context.drawText(this.textRenderer, Text.literal("远程端口（房主的端口）"), this.width / 2 - 100, 100, 0xA0A0A0, false);
        context.drawText(this.textRenderer, Text.literal("本地端口(默认=远程)"), this.width / 2 + 14, 100, 0xA0A0A0, false);

        NodeManager nm = OpenP2PMod.get().node();
        String status = nm.statusText();
        int color;
        switch (nm.state()) {
            case TUNNEL_UP -> color = 0x55FF55;
            case ERROR -> color = 0xFF5555;
            case ONLINE, STARTING, CONNECTING, DOWNLOADING -> color = 0xFFFF55;
            default -> color = 0xAAAAAA;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, 188, color);
        if (nm.state() == NodeManager.State.ERROR) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("点击「查看日志」查看详细原因"),
                    this.width / 2, 202, 0xAAAAAA);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("我的 UID: " + nm.effectiveUid()), this.width / 2, 216, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}