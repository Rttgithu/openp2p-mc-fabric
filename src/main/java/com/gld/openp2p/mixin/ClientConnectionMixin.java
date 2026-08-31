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
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端连接关闭（被踢 / 服务器关闭 / 超时等任何强制断开）时，
 * 立即关闭 OpenP2P 隧道、节点并释放端口。
 * 只认客户端自己的连接（网络处理器指向的那条），不会误伤主机端局域网连接。
 */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Inject(method = "handleDisconnection", at = @At("HEAD"))
    private void openp2p$onDisconnect(CallbackInfo ci) {
        ClientConnection self = (ClientConnection) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null) {
            return;
        }
        if (mc.getNetworkHandler().getConnection() == self) {
            OpenP2PMod mod = OpenP2PMod.get();
            if (mod != null) {
                mod.onServerStopping();
            }
        }
    }
}