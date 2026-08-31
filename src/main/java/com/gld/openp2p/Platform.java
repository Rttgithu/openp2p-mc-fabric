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

/**
 * 平台识别：决定下载哪个 OpenP2P 官方 release 压缩包与其中的可执行文件名。
 */
public final class Platform {

    public final boolean windows;
    public final boolean mac;
    public final String osName;
    public final String archName;

    private Platform(boolean windows, boolean mac, String osName, String archName) {
        this.windows = windows;
        this.mac = mac;
        this.osName = osName;
        this.archName = archName;
    }

    public static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean win = os.contains("win");
        boolean macOs = os.contains("mac") || os.contains("darwin");
        return new Platform(win, macOs, os, arch);
    }

    /** 例如 windows-amd64.zip / linux-amd64.tar.gz */
    public String assetName() {
        String osPart;
        if (windows) {
            osPart = "windows";
        } else if (mac) {
            osPart = "darwin";
        } else if (osName.contains("linux")) {
            osPart = "linux";
        } else {
            throw new UnsupportedOperationException("不支持的操作系统: " + osName);
        }
        String archPart;
        switch (archName) {
            case "amd64":
            case "x86_64":
                archPart = "amd64";
                break;
            case "aarch64":
            case "arm64":
                archPart = "arm64";
                break;
            case "x86":
            case "i386":
            case "i686":
                archPart = "386";
                break;
            case "arm":
            case "armv7l":
                archPart = "arm";
                break;
            default:
                throw new UnsupportedOperationException("不支持的架构: " + archName + " (os=" + osName + ")");
        }
        String ext = windows ? ".zip" : ".tar.gz";
        return osPart + "-" + archPart + ext;
    }

    /** openp2p.exe (windows) / openp2p (linux/mac) */
    public String binaryName() {
        return windows ? "openp2p.exe" : "openp2p";
    }

    public String ext() {
        return windows ? ".zip" : ".tar.gz";
    }

    public boolean isWindows() {
        return windows;
    }
}