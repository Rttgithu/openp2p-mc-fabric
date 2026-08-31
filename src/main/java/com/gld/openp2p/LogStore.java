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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 日志存储：内存环形缓冲（最多 1000 行）+ 同步追加写入日志文件，供日志界面展示。
 */
public final class LogStore {

    private final ArrayDeque<String> ring = new ArrayDeque<>();
    private final Path file;

    public LogStore(Path file) {
        this.file = file;
    }

    public synchronized void add(String line) {
        ring.addLast(line);
        while (ring.size() > 1000) {
            ring.pollFirst();
        }
        try {
            Files.writeString(file, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    public synchronized List<String> snapshot() {
        return new ArrayList<>(ring);
    }
}