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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隧道条目（远程联机-xxx）在服务器列表中只显示 ID 和端口：
 * 屏蔽原版自动 ping 与“无法连接至服务器”等状态行，改用灰色“端口 xx”一行。
 */
@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryRendererMixin {

    @Shadow
    @Final
    private ServerInfo server;

    private boolean openp2p$isOurs() {
        return server != null && server.name != null && server.name.startsWith("远程联机-");
    }

    /** 渲染前：标记在线并清空状态文本（阻止自动 ping，也不显示“无法连接”） */
    @Inject(method = "render", at = @At("HEAD"))
    private void openp2p$neutralizeStatus(DrawContext context, int index, int y, int x, int entryWidth,
                                          int entryHeight, int mouseX, int mouseY, boolean hovered,
                                          float tickDelta, CallbackInfo ci) {
        if (!openp2p$isOurs()) {
            return;
        }
        server.online = true;
        server.label = Text.empty();
        server.playerCountLabel = Text.empty();
        server.version = Text.empty();
    }

    /** 渲染后：在名称下方画一行灰色“端口 xx” */
    @Inject(method = "render", at = @At("TAIL"))
    private void openp2p$renderPort(DrawContext context, int index, int y, int x, int entryWidth,
                                    int entryHeight, int mouseX, int mouseY, boolean hovered,
                                    float tickDelta, CallbackInfo ci) {
        if (!openp2p$isOurs()) {
            return;
        }
        String port = "端口 ?";
        if (server.address != null) {
            int idx = server.address.lastIndexOf(':');
            if (idx >= 0) {
                port = "端口 " + server.address.substring(idx + 1);
            }
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        context.drawText(mc.textRenderer, port, x + 35, y + 12, 0x909090, false);
    }

    /** 屏蔽条目所有纹理绘制（绿色信号、在线状态、图标等），只显示 ID 和端口 */
    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIFFIIII)V"))
    private void openp2p$hideSignalIcon(DrawContext context, Identifier texture, int x, int y,
                                        float u, float v, int width, int height, int textureWidth,
                                        int textureHeight) {
        if (!openp2p$isOurs()) {
            context.drawTexture(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }
}