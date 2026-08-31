# OpenP2P 远程联机模组（我的世界 1.20.1 Fabric）

[![最新 Release](https://img.shields.io/github/v/release/Rttgithu/openp2p-mc-fabric?label=Download&color=blue)](https://github.com/Rttgithu/openp2p-mc-fabric/releases/latest)
[![许可证 GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

> ### 使用前请先看这三条
>
> **1. 联机中继默认走一组预设账号。** 模组默认使用一组预设的 OpenP2P 中继接入参数
> （发布者自有账号，与 [OPL-WpfApp](https://github.com/Guailoudou/OPL-WpfApp) 同源）。
> 该账号**并非为你单独分配**，所有沿用默认值的用户共享同一身份，可用性与带宽依赖 OpenP2P 官方服务，可能随时变更或失效。
> **建议你在 [console.openp2p.cn](https://console.openp2p.cn) 自行免费注册，
> 并把 `config/openp2p.json` 里的 `user` / `token` 换成自己的。** 详见下方「配置」。
>
> **2. 「正版:关」仅用于你已经购买正版、但好友使用离线账号的情况。** 默认保持开启，
> 需要你手动切换。关闭正版验证意味着服务器不再校验账号归属，由此产生的任何风险由你自己承担。
> 请勿用它为未持有 Minecraft 的人提供游戏访问。
>
> **3. 模组会释放并运行一个内网穿透程序。** 它会被部分杀毒软件误报，
> 首次运行时请把 `<游戏目录>/openp2p` 加入白名单。内置节点为
> [OpenP2P](https://github.com/openp2p-cn/openp2p) 官方代码构建（MIT 许可，开源可审计）。

基于 [OPL-WpfApp](https://github.com/Guailoudou/OPL-WpfApp) 的 OpenP2P 用法实现的联机模组。
**只装这一个 jar 就能用**：节点程序已内置在模组包里（Windows 免下载、免管理员权限），无需安装任何外部程序。

## 使用

### 主机（开房）
1. 暂停菜单 →「对局域网开放」→ 底部 `[远程联机:开] [日志] [UID 设置] [正版:开/关]`
2. 打开 `远程联机:开` →「创建局域网世界」（端口默认 25565，改过会自动记住）
3. 聊天栏出现绿色提示：`OpenP2P 远程联机已开启! UID=xxx 端口=xxx`，把 UID 和端口告诉好友
4. 好友多（离线/破解端）时，把 `正版:关` 打开即可允许离线客户端加入

### 客机（联机）
1. 多人游戏 →「添加服务器」→ 底部「OpenP2P 远程联机」→ 填房主游戏 ID + 端口 →「添加」
2. 列表出现 `远程联机-<UID>`（只显示 ID 和端口，无多余的 ping 状态）
3. 点该条目 → 顶部实时进度 → 隧道建立后自动进服
4. 需要修改时点「编辑」→ 自己的编辑界面（对方 UID + 端口）

### UID
默认 = 你的游戏 ID；真实节点名会自动附加统一隐藏后缀 `-op2pmc`（两端自动补齐，无需输入）。
可在「UID 设置」中修改；若名字与他人冲突，以聊天栏提示的实际注册名为准。

## 安全与生命周期

- **不进入世界，隧道一律断开**：隧道建好 25 秒未进服、60 秒连不上对方、退出世界、被踢/强制断开、主机关闭世界——所有情况都会立即关闭 OpenP2P 并释放端口
- 日志：任一面板「OpenP2P 日志」或 `<游戏目录>/openp2p/openp2p.log`；错误原因直接显示在界面
- 杀毒提示：openp2p 是内网穿透程序，如被杀软拦截请把 `<游戏目录>/openp2p` 加入白名单

## 配置（config/openp2p.json）

| 字段 | 说明 |
|---|---|
| `uid` | 你的联机标识，默认取游戏 ID |
| `shareEnabled` | 是否开启远程联机分享 |
| `offlineMode` | 正版验证开关，**默认关闭（即开启正版验证）** |
| `lastLanPort` | 主机端口记忆 |
| `serverHost` / `serverPort` | 中继服务器地址，默认 `api.openp2p.cn:27183` |
| `user` / `token` | 中继账号凭据，**强烈建议换成自己注册的** |
| `downloadMirrorPrefix` | 节点下载镜像前缀（国内网络可选填） |

### 关于中继账号（重要）

`user` 与 `token` 的默认值是一组**发布者预设的账号凭据**（与 OPL-WpfApp 同源），
并非为你单独分配。所有沿用默认值的用户共享同一身份。这意味着：

- 所有使用该默认值的用户共享同一身份，中继资源由全体使用者竞争
- 该账号的可用性、带宽、存续完全取决于 OpenP2P 官方，本项目无法保证、也不承担责任
- 一旦该账号被官方停用或限流，使用默认值的用户将全部受影响

注册自己的账号是免费的，只需邮箱，耗时不到一分钟：

1. 打开 https://console.openp2p.cn 注册
2. 在游戏内「UID 设置」或直接编辑 `config/openp2p.json`，填入你自己的 `user` 与 `token`

## 构建

需要 JDK 17+：
```
./gradlew build
```
产物：`build/libs/openp2p-mc-1.0.33.jar`（需要 Fabric Loader 0.15+，无需 Fabric API）。
版本号以 [gradle.properties](gradle.properties) 中的 `mod_version` 为准。

详细设计与版本历史见 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)。

---

## 许可证

**本项目采用 [GNU General Public License v3.0](LICENSE) 授权。**

你可以自由使用、修改和再分发本模组，条件是再分发（包括修改版）时同样以 GPL-3.0 开源，
并保留全部版权与许可声明。

第三方组件的许可与来源见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)，摘要如下：

| 组件 | 许可证 | 是否随 jar 分发 |
|---|---|---|
| [OpenP2P](https://github.com/openp2p-cn/openp2p) 节点程序 | MIT | **是**（内置） |
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) | Apache-2.0 | 否，用户自行安装 |
| [Yarn](https://github.com/FabricMC/yarn) mappings | CC0-1.0 | 否，仅编译期 |
| Minecraft | Mojang EULA（专有） | **否** |

## 免责声明

- 本项目**与 Mojang Studios、Microsoft 无任何关联**，非官方产品，不受其支持。
  使用本模组前你须已合法取得 Minecraft 并同意其 EULA。
- 本模组**按「原样」提供，不附带任何明示或暗示的担保**。详见 GPL-3.0 第 15、16 条。
- 本模组会建立 P2P 隧道，使你的本地端口在隧道存续期间对指定的对端可达。
  请只向你信任的人提供 UID，联机结束后模组会自动关闭隧道。
- 中继服务由 OpenP2P 官方提供，其可用性不在本项目控制范围内。
- 内置节点程序为开源项目 OpenP2P 的构建产物，源码可审计，
  不含任何遥测、广告或用户数据收集代码。若你的杀毒软件报毒，可对照上游源码自行核验。