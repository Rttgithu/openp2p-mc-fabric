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

import com.gld.openp2p.NodeManager;
import com.gld.openp2p.OpenP2PMod;
import com.gld.openp2p.ui.OpenP2PConnectScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截多人游戏列表的“加入服务器”：对「远程联机-xxx」条目不按原版方式直接连
 * 127.0.0.1，而是先建立 OpenP2P 隧道（UID+端口），隧道就绪后自动进服。
 * 同时在多人游戏界面直接绘制连接进度（聊天栏在界面打开时不可见）。
 */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenJoinMixin extends Screen {

    protected MultiplayerScreenJoinMixin() {
        super(Text.empty());
    }

    @Inject(method = "connect(Lnet/minecraft/client/network/ServerInfo;)V", at = @At("HEAD"), cancellable = true)
    private void openp2p$onJoin(ServerInfo info, CallbackInfo ci) {
        if (info == null || info.name == null || info.address == null) {
            return;
        }
        String address = info.address;
        int local = 25565;
        int idx = address.lastIndexOf(':');
        if (idx >= 0) {
            try {
                local = Integer.parseInt(address.substring(idx + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        // 只处理我们的远程联机条目（条目身份 = 名字，同端口不同 UID 互不影响）
        if (!info.name.startsWith("远程联机-")) {
            return;
        }
        ci.cancel();

        String uid = info.name.substring("远程联机-".length());
        int port = local;

        OpenP2PMod.get().sendChat("§e正在连接远程主机 §f" + uid + ":" + port + "§e…隧道建立后自动加入");
        OpenP2PMod.get().node().connectFromList(uid, port, port);
    }

    /**
     * 编辑「远程联机-」条目时打开自己的编辑界面（对方 UID / 端口）。
     * 注意：原版「编辑」按钮实际调用 method_19915（editEntry 是死代码），两个入口都拦截。
     */
    @Inject(method = "editEntry", at = @At("HEAD"), cancellable = true)
    private void openp2p$onEditA(boolean bl, CallbackInfo ci) {
        if (openEditForSelected(ci)) {
            ci.cancel();
        }
    }

    @Inject(method = "method_19915", at = @At("HEAD"), cancellable = true)
    private void openp2p$onEditB(ButtonWidget button, CallbackInfo ci) {
        if (openEditForSelected(ci)) {
            ci.cancel();
        }
    }

    private boolean openEditForSelected(CallbackInfo ci) {
        MultiplayerScreen self = (MultiplayerScreen) (Object) this;
        MultiplayerServerListWidget.Entry entry = ((MultiplayerScreenAccessor) self).openp2p$serverListWidget()
                .getSelectedOrNull();
        if (!(entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry)) {
            return false;
        }
        ServerInfo server = serverEntry.getServer();
        if (server == null || server.address == null || server.name == null
                || !server.name.startsWith("远程联机-")) {
            return false;
        }

        String uid = server.name.substring("远程联机-".length());
        int port = 25565;
        int idx = server.address.lastIndexOf(':');
        if (idx >= 0) {
            try {
                port = Integer.parseInt(server.address.substring(idx + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        this.client.setScreen(new OpenP2PConnectScreen(self, server.address, uid, port));
        return true;
    }

    /** 连接进度/失败原因直接画在多人游戏界面上 */
    @Inject(method = "render", at = @At("TAIL"))
    private void openp2p$renderProgress(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        NodeManager nm = OpenP2PMod.get().node();
        if (nm.sharing()) {
            return;
        }
        String text = nm.overlayText();
        if (text == null || text.isEmpty()) {
            return;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("[OpenP2P] " + text),
                this.width / 2, 8, nm.overlayColor());
    }
}