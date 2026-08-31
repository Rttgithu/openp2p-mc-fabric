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

import com.gld.openp2p.OpenP2PMod;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 每帧驱动客户端逻辑（聊天提示、隧道建立后自动加入服务器等）。
 * 同时监听离开世界（disconnect）——主机端退出存档时立即停止 P2P 并释放端口，无需关闭游戏。
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void openp2p$clientTick(CallbackInfo ci) {
        OpenP2PMod mod = OpenP2PMod.get();
        if (mod != null) {
            mod.clientTick((MinecraftClient) (Object) this);
        }
    }

    @Inject(method = {"disconnect()V", "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V"}, at = @At("HEAD"))
    private void openp2p$onDisconnect(CallbackInfo ci) {
        // 只在真正离开世界/场景时才停节点（world != null）。
        // 注意：原版 ConnectScreen.connect 进服前也会调用 disconnect()（此时 world == null），
        // 若在这里停节点会把刚建好的隧道杀掉，导致 Connection refused。
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (self.world != null) {
            OpenP2PMod mod = OpenP2PMod.get();
            if (mod != null) {
                mod.onServerStopping();
            }
        }
    }
}