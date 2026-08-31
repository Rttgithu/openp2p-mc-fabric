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

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问原版多人游戏界面的服务器列表，用于把 OpenP2P 对端写进列表。
 */
@Mixin(MultiplayerScreen.class)
public interface MultiplayerScreenAccessor {

    @Accessor("serverList")
    ServerList openp2p$serverList();

    @Accessor("serverListWidget")
    MultiplayerServerListWidget openp2p$serverListWidget();
}