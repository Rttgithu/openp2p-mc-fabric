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

package com.gld.openp2p;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 模组主入口（仅客户端）。
 * 负责持有全局单例：配置、节点管理；并在客户端 tick 上执行队列动作（聊天提示、自动加入服务器等）。
 */
public final class OpenP2PMod implements ClientModInitializer {

    private static OpenP2PMod INSTANCE;

    private OpenP2PConfig config;
    private final NodeManager nodeManager = new NodeManager();
    private final ConcurrentLinkedQueue<Runnable> clientActions = new ConcurrentLinkedQueue<>();

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        config = OpenP2PConfig.load();
        nodeManager.init();
    }

    public static OpenP2PMod get() {
        return INSTANCE;
    }

    public OpenP2PConfig config() {
        return config;
    }

    public NodeManager node() {
        return nodeManager;
    }

    /** 由 MinecraftClientMixin 在客户端每帧调用 */
    public void clientTick(MinecraftClient mc) {
        Runnable act;
        while ((act = clientActions.poll()) != null) {
            try {
                act.run();
            } catch (Throwable t) {
                nodeManager.log("[内部错误] " + t);
            }
        }
        nodeManager.clientTick(mc);
    }

    /** 在客户端线程执行某个动作（可从任意线程调用） */
    public void onClient(Runnable r) {
        clientActions.add(r);
    }

    /** 点击“对局域网开放”且分享开关开启后，LAN 端口就绪时调用（任意线程，通常为客户端线程） */
    public void onLanOpened(int port) {
        if (config.shareEnabled) {
            nodeManager.startShare(port);
        }
    }

    /** 集成服务器停止（退出存档等）时调用 */
    public void onServerStopping() {
        nodeManager.onIntegratedServerStopped();
    }

    /** 向游戏聊天栏发送消息（线程安全） */
    public void sendChat(String message) {
        onClient(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[OpenP2P] " + message));
            }
        });
    }
}