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
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注意：1.20.1 里 MinecraftServer.openToLan / stop 基类只是空壳，
 * 集成服务器（单机/局域网世界）真实执行的是 IntegratedServer 的重写。
 * 之前混入基类导致主机端分享从未触发，v1.0.5 起改为注入此类。
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {

    /** 局域网世界成功开启（openToLan 返回 true）后，把实际端口交给分享逻辑，并记住该端口 */
    @Inject(method = "openToLan", at = @At("RETURN"))
    private void openp2p$onLanOpened(GameMode gameMode, boolean allowCheats, int port, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            IntegratedServer self = (IntegratedServer) (Object) this;
            int realPort = self.getServerPort();
            if (realPort > 0) {
                OpenP2PMod mod = OpenP2PMod.get();
                if (mod != null) {
                    // 记住本次使用的端口，下次打开“对局域网开放”时默认填入
                    if (mod.config().lastLanPort != realPort) {
                        mod.config().lastLanPort = realPort;
                        mod.config().save();
                    }
                    mod.onLanOpened(realPort);
                }
            }
        }
    }

    /** 存档停止（退出单机/停止服务器）时关闭分享 */
    @Inject(method = "stop", at = @At("HEAD"))
    private void openp2p$onStop(boolean waitForServer, CallbackInfo ci) {
        OpenP2PMod mod = OpenP2PMod.get();
        if (mod != null) {
            mod.onServerStopping();
        }
    }
}