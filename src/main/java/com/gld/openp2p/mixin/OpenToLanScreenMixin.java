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

package com.gld.openp2p.mixin;

import com.gld.openp2p.OpenP2PConfig;
import com.gld.openp2p.OpenP2PMod;
import com.gld.openp2p.ui.OpenP2PLogScreen;
import com.gld.openp2p.ui.OpenP2PUidScreen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在“对局域网开放”界面底部追加 OpenP2P 控制面板：
 * 分享开/关、查看日志、复制我的 UID、修改 UID。
 * 开启分享并点击“Start LAN World”后，MinecraftServerMixin 会自动启动分享。
 */
@Mixin(OpenToLanScreen.class)
public abstract class OpenToLanScreenMixin extends Screen {

    @Shadow
    private TextFieldWidget portField;

    protected OpenToLanScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void openp2p$addWidgets(CallbackInfo ci) {
        OpenToLanScreen self = (OpenToLanScreen) (Object) this;
        OpenP2PConfig cfg = OpenP2PMod.get().config();

        // 自适应：一行 4 个紧凑按钮，放在端口输入框与底部按钮之间，
        // 小窗口也不会盖住端口输入框（高 = 窗口高度，240 时位于 188）。
        int rowY = self.height - 52;
        if (rowY < 184) {
            rowY = 184;
        }
        int x0 = self.width / 2 - 155;

        ButtonWidget toggle = ButtonWidget.builder(
                        Text.literal(cfg.shareEnabled ? "远程联机:开" : "远程联机:关"),
                        b -> {
                            cfg.shareEnabled = !cfg.shareEnabled;
                            cfg.save();
                            b.setMessage(Text.literal(cfg.shareEnabled ? "远程联机:开" : "远程联机:关"));
                            // 世界已处于局域网开放状态时，开分享立即生效（isRemote() = 局域网已开放）
                            if (cfg.shareEnabled && this.client != null && this.client.getServer() != null) {
                                var srv = this.client.getServer();
                                if (srv.isRemote() && srv.getServerPort() > 0) {
                                    OpenP2PMod.get().onLanOpened(srv.getServerPort());
                                }
                            }
                        })
                .dimensions(x0, rowY, 86, 20)
                .build();

        ButtonWidget logButton = ButtonWidget.builder(Text.literal("日志"), b ->
                        this.client.setScreen(new OpenP2PLogScreen(self)))
                .dimensions(x0 + 87, rowY, 68, 20)
                .build();

        ButtonWidget uidButton = ButtonWidget.builder(Text.literal("UID 设置"), b ->
                        this.client.setScreen(new OpenP2PUidScreen(self)))
                .dimensions(x0 + 156, rowY, 78, 20)
                .build();

        ButtonWidget offlineToggle = ButtonWidget.builder(
                        Text.literal(cfg.offlineMode ? "正版:关" : "正版:开"),
                        b -> {
                            cfg.offlineMode = !cfg.offlineMode;
                            cfg.save();
                            b.setMessage(Text.literal(cfg.offlineMode ? "正版:关" : "正版:开"));
                            if (cfg.offlineMode) {
                                OpenP2PMod.get().sendChat("§e已在局域网世界允许离线（破解）客户端加入");
                            } else {
                                OpenP2PMod.get().sendChat("§e已恢复正版验证");
                            }
                        })
                .dimensions(x0 + 235, rowY, 75, 20)
                .build();

        this.addDrawableChild(toggle);
        this.addDrawableChild(logButton);
        this.addDrawableChild(uidButton);
        this.addDrawableChild(offlineToggle);

        // 端口默认 25565，如果上次手动改过端口则记住上次的（绝不随机），玩家仍可手动修改
        if (portField != null) {
            int p = cfg.lastLanPort;
            if (p < 1024 || p > 65535) {
                p = 25565;
            }
            portField.setText(String.valueOf(p));
        }

        // 提示只发一次，避免每次打开界面重复刷屏
        if (!HINT_SENT && this.client != null && this.client.inGameHud != null) {
            HINT_SENT = true;
            this.client.inGameHud.getChatHud().addMessage(Text.literal(
                    "[OpenP2P] 开启「远程联机:开」后点「创建局域网世界」，把 UID 和端口告诉好友即可远程联机"));
        }
    }

    private static boolean HINT_SENT = false;
}