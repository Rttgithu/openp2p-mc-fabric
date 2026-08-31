# 第三方组件声明

本项目 `openp2p-mc`（OpenP2P 联机模组）自身采用 **GNU GPL v3** 授权，见 [LICENSE](LICENSE)。
本文件列出本项目**分发物中包含的**以及**运行时依赖的**第三方组件及其许可条款。

---

## 一、随分发物（jar）一同打包的组件

### 1. OpenP2P 节点程序（openp2p.exe）

| 项目 | 内容 |
|---|---|
| 文件 | `src/main/resources/assets/openp2p/bin/openp2p.exe` |
| 版本 | 3.21.12（windows-386，兼容 x64） |
| 上游 | https://github.com/openp2p-cn/openp2p |
| 许可证 | **MIT** |
| 版权 | Copyright (c) 2021 OpenP2P.cn |

该可执行文件是 OpenP2P 项目的构建产物，经重新打包以移除 Windows 清单中的
`requireAdministrator` 声明，使其可在非管理员权限下由游戏进程直接启动。
除该清单改动外未做其他修改。本模组在 GPL-3.0 下分发该 MIT 许可的二进制，
根据 MIT 条款保留下列版权与许可声明。

```
MIT License

Copyright (c) 2021 OpenP2P.cn

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 二、运行时依赖（不随 jar 分发，由用户自行安装）

以下组件**不包含**在本项目的分发物中，仅在运行时被调用或链接。
用户需自行获取并遵守各自许可条款。

### 2. Minecraft（Mojang Studios）

- 许可证：**专有软件**，受 [Minecraft EULA](https://www.minecraft.net/en-us/eula) 与
  [Minecraft 使用准则](https://www.minecraft.net/en-us/usage-guidelines) 约束
- 说明：本模组不包含、不分发任何 Mojang 的源代码或游戏资源文件。
  模组通过 Fabric 的 Mixin 机制在运行时修改内存中的类，不重新分发 Minecraft 本体。
- 使用本模组前，你必须已合法取得 Minecraft 并同意其 EULA。

### 3. Fabric Loader

- 许可证：**Apache License 2.0**
- 上游：https://github.com/FabricMC/fabric-loader
- 版权：Copyright (c) FabricMC and contributors
- 全文：https://www.apache.org/licenses/LICENSE-2.0

### 4. Yarn mappings

- 许可证：**CC0-1.0**（公有领域贡献）
- 上游：https://github.com/FabricMC/yarn
- 说明：仅在编译期用于反混淆命名，编译产物经 remap 后回到 Mojang 官方混淆名，
  不随 jar 分发任何映射数据。

### 5. Gson

- 许可证：**Apache License 2.0**
- 上游：https://github.com/google/gson
- 说明：由 Minecraft 本体提供（游戏内置依赖），不随 jar 分发。
  Fabric 模组编译期引用，运行时使用游戏自带的版本。

---

## 三、参考来源（非代码衍生）

### 6. OPL-WpfApp

- 上游：https://github.com/Guailoudou/OPL-WpfApp
- 许可证：**GPL-3.0**
- 关系说明：本项目在构思阶段参考了该工具的 **OpenP2P 使用方式**
  （节点启动参数、`config.json` 结构、进程输出关键字、公共中继接入参数）。
  本项目为 **Java / Fabric 独立实现**（OPL-WpfApp 为 C# / WPF 应用），
  未复制其源代码，也未链接其任何二进制。

  本项目同样采用 **GPL-3.0** 授权，与该参考来源的许可证一致，
  以确保无论在法律上如何认定该参考关系的性质，本项目均处于合规状态。

- 下载镜像说明：`NodeDownloader` 中记录的 `file.gldhn.top` 镜像地址由 OPL-WpfApp
  作者维护，作为 openp2p 官方 GitHub Release 在国内网络环境下的备用下载源。
  内置节点不可用时才会访问该地址，且可用 `downloadMirrorPrefix` 配置项覆盖。

---

## 四、GPL-3.0 合规说明

本项目以 GPL-3.0 分发，据此：

1. **对应源码（Corresponding Source）**：每个 Release 均附带 `-sources.jar`，
   完整仓库源码随 GitHub Releases 与仓库本身公开发布。
2. **许可证随附**：jar 内包含 `LICENSE`（GPL-3.0 全文）与本声明文件。
3. **再分发**：任何再分发本模组（含修改版）的行为，均须在 GPL-3.0 下进行，
   并保留上述全部版权与许可声明。
4. **内置二进制**：内置 openp2p.exe 为 MIT 许可，其条款已完整保留于本文件第一节，
   GPL-3.0 与 MIT 在此方向兼容。

如有许可方面的疑问或认为本文件存在遗漏，请在项目仓库提交 issue。
