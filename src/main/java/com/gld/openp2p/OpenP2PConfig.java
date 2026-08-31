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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 模组配置文件（.minecraft/config/openp2p.json）。
 * uid 默认为空 = 使用当前游戏 ID；可手动修改。
 */
public class OpenP2PConfig {

    /** 自己的 UID，空表示使用游戏 ID */
    public String uid = "";

    public String serverHost = "api.openp2p.cn";
    public int serverPort = 27183;

    /**
     * 中继账号凭据（默认值）。
     * <p>
     * 【免责声明】默认值为发布者自有的 OpenP2P 账号，仅作为「开箱即用」便利选项提供，
     * <b>并非为使用者单独分配</b>。所有沿用默认值的用户共享同一身份，
     * 其可用性、带宽与存续依赖 OpenP2P 官方服务，本项目不作任何保证、不承担任何责任。
     * <p>
     * 建议使用者前往 https://console.openp2p.cn 自行免费注册，
     * 并在此处（或游戏内「UID 设置」）替换为自己的 user / token。
     */
    public String token = "17082085814755773893";
    public String user = "rttmcp2p";
    public int shareBandwidth = 10;

    /** 主机端“对局域网开放”界面上的分享开关（记忆上次状态） */
    public boolean shareEnabled = false;

    /**
     * 关闭正版验证（允许离线/破解客户端加入），主机端“对局域网开放”界面可切换。
     * <p>
     * 【免责声明】默认为 false，即<b>保持正版验证开启</b>，需用户主动切换到关闭状态。
     * 关闭后服务器不再校验账号归属，由此产生的一切后果由使用者自行承担。
     * 本功能仅应用于已购买正版的用户与持有离线账号的好友联机，
     * 不得用于向未持有 Minecraft 的人提供游戏访问。
     */
    public boolean offlineMode = false;

    /** 联机端记忆上次输入的 UID / 端口 */
    public String lastPeerUid = "";
    public int lastPeerPort = 25565;

    /** 主机端上次使用的局域网端口（默认 25565，修改后记住，绝不随机） */
    public int lastLanPort = 25565;

    /** 远程联机列表条目（服务器地址 → "UID|远程端口"），用于点击列表项时重建隧道 */
    public java.util.Map<String, String> remoteEntries = new java.util.LinkedHashMap<>();

    /** GitHub 下载镜像前缀（国内网络下载节点失败时可用，如 https://ghfast.top/ ），留空走官方源 */
    public String downloadMirrorPrefix = "";

    /** 有效的 UID：配置为空时使用游戏 ID */
    public String effectiveUid() {
        if (uid != null && !uid.trim().isEmpty()) {
            return uid.trim();
        }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null) {
                String name = mc.getSession().getUsername();
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return "player" + (int) (Math.random() * 90000 + 10000);
    }

    public static OpenP2PConfig load() {
        OpenP2PConfig cfg = new OpenP2PConfig();
        try {
            Path p = path();
            if (Files.exists(p)) {
                OpenP2PConfig loaded = new Gson().fromJson(Files.readString(p), OpenP2PConfig.class);
                if (loaded != null) {
                    cfg = loaded;
                }
            }
        } catch (Exception ignored) {
        }
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Path p = path();
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(this));
        } catch (Exception ignored) {
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("openp2p.json");
    }
}