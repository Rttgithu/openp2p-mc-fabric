# OpenP2P 联机模组 v1.0.33

基于 [OpenP2P](https://github.com/openp2p-cn/openp2p) 的《我的世界》1.20.1 Fabric 联机模组。
主机在「对局域网开放」界面一键开启分享，好友在多人游戏界面输入 UID 即可联机。
**只装这一个 jar 即可用**：节点程序已内置（Windows 免下载、免管理员权限），无需安装任何外部程序。

## 许可证

**GPL-3.0**。源码随 `-sources.jar` 与仓库完整提供。
内置的 OpenP2P 节点程序为 **MIT 许可**（© 2021 OpenP2P.cn），其版权与许可全文见
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)，并随 jar 一并分发。

## 安装

1. 安装 Fabric Loader 0.15+（无需 Fabric API）
2. 将 `openp2p-mc-1.0.33.jar` 放入 `.minecraft/mods/`
3. 启动游戏

## 使用

- **主机**：暂停菜单 →「对局域网开放」→ 点「远程联机:开」→ 创建局域网世界 → 聊天栏提示 `UID=xxx 端口=xxx`，把 UID 和端口告诉好友
- **客机**：多人游戏 →「添加服务器」→ 底部「OpenP2P 远程联机」→ 填房主 UID + 端口 → 列表出现 `远程联机-<UID>` → 点击自动进服

## ⚠️ 重要说明

1. **中继账号**：模组默认使用一组发布者预设的 OpenP2P 中继接入参数（与 OPL-WpfApp 同源），
   并非为你单独分配，所有沿用默认值的用户共享同一身份。可用性与带宽依赖 OpenP2P 官方服务，
   可能随时变更或失效。**建议前往 https://console.openp2p.cn 自行免费注册，并在
   `config/openp2p.json` 中替换为自己的 user / token。**
2. **正版验证**：开关默认保持开启（正版验证），需你主动切换到关闭状态。关闭后服务器不再校验
   账号归属，由此产生的一切后果由使用者自行承担；不得用于向未持有 Minecraft 的人提供游戏访问。
3. **杀毒误报**：内置节点是内网穿透程序，可能被杀软拦截，请把 `<游戏目录>/openp2p` 加入白名单。
4. **非官方**：本项目与 Mojang Studios、Microsoft 无任何关联，按「原样」提供、不附带任何担保。

## 下载

- `openp2p-mc-1.0.33.jar` — 模组本体（含内置节点）
- `openp2p-mc-1.0.33-sources.jar` — 对应源码（GPL-3.0 要求）
