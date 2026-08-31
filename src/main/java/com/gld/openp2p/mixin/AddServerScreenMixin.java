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

import com.gld.openp2p.ui.OpenP2PConnectScreen;
import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 多人游戏 → “添加服务器”界面追加 “OpenP2P 远程联机” 入口。
 * 位置：原版“完成/取消”按钮下方（Y 取原版按钮真实坐标 + 48），任何窗口尺寸都不重叠。
 */
@Mixin(AddServerScreen.class)
public abstract class AddServerScreenMixin extends Screen {

    @Shadow
    private ButtonWidget addButton;

    @Shadow
    @Final
    private Screen parent;

    @Shadow
    @Final
    private ServerInfo server;

    protected AddServerScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void openp2p$addWidgets(CallbackInfo ci) {
        AddServerScreen self = (AddServerScreen) (Object) this;

        // 只在新添加服务器（地址为空）时显示我们的入口按钮；
        // 编辑已有条目时不再出现（远程联机条目被拦截走自己的编辑界面，原版条目不显示该按钮）
        if (server != null && server.address != null && !server.address.isEmpty()) {
            return;
        }

        ButtonWidget remoteButton = ButtonWidget.builder(Text.literal("OpenP2P 远程联机"), b ->
                        this.client.setScreen(new OpenP2PConnectScreen(this.parent)))
                .dimensions(self.width / 2 - 100, addButton.getY() + 48, 200, 20)
                .build();

        this.addDrawableChild(remoteButton);
    }
}