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
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * “关闭正版验证”开关：isOnlineMode 返回 false 时，
 * 1.20.1 登录流程（ServerLoginNetworkHandler.onHello / acceptPlayer）
 * 会直接以离线模式接受玩家，离线/破解客户端即可加入。
 * <p>
 * 【免责声明】本开关默认关闭（{@code offlineMode = false}），即保持正版验证开启，
 * 仅在使用者于「对局域网开放」界面主动切换后生效。关闭正版验证会使服务器不再校验
 * 账号归属，由此产生的一切后果由使用者自行承担。本功能仅应用于已购买正版的用户
 * 与使用离线账号的好友联机，不得用于向未持有 Minecraft 的人提供游戏访问。
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerAuthMixin {

    @Inject(method = "isOnlineMode", at = @At("HEAD"), cancellable = true)
    private void openp2p$offlineMode(CallbackInfoReturnable<Boolean> cir) {
        OpenP2PMod mod = OpenP2PMod.get();
        if (mod != null && mod.config().offlineMode) {
            cir.setReturnValue(false);
        }
    }
}