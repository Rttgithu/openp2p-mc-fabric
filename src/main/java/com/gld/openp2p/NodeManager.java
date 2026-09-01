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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenP2P 节点生命周期管理：
 * 1. 确保本地有 openp2p 节点程序（没有则自动从模组内置资源释放 / 下载）；
 * 2. 写 config.json（network 注册 UID + apps 隧道定义），目录为 <游戏目录>/openp2p/；
 * 3. 以子进程方式启动节点（无参数，节点自动读取同目录 config.json），读取 stdout 解析状态；
 * 4. 状态变化时给出聊天提示；联机端隧道建立后提示玩家去服务器列表点击进入（像原版一样联机）。
 * <p>
 * 节点的启动参数、config.json 结构与 stdout 输出关键字，是 OpenP2P 自身
 * （https://github.com/openp2p-cn/openp2p ，MIT）定义的进程间接口，
 * 在构思时参考了 OPL-WpfApp（https://github.com/Guailoudou/OPL-WpfApp ，GPL-3.0）
 * 对这些接口的调用方式。本类为 Java / Fabric 环境下的独立实现，未复制其源代码。
 * 详见仓库根目录 THIRD-PARTY-NOTICES.md 第三节。
 */
public final class NodeManager {

    public enum State {
        STOPPED, DOWNLOADING, STARTING, ONLINE, CONNECTING, TUNNEL_UP, ERROR
    }

    private static final Pattern LISTEN = Pattern.compile("LISTEN ON PORT (tcp|udp):(\\d+) (START|END)");
    private static final Pattern NODE_LOGIN = Pattern.compile("node=([\\w\\-.:]+)");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 隐藏的统一 UID 后缀：所有本模组玩家相同，主机/联机端自动补全，玩家无需输入。
     * 真实节点名 = 显示 UID（游戏ID或自定义）+ [冲突去重序号] + 本后缀，
     * 从而大幅避免与他人撞名。
     * <p>
     * 名字冲突时，OpenP2P 服务器会把我们的节点重命名（在其内部加一个去重序号）。
     * 为避免服务器把序号加在隐藏后缀“之后”（形如 displayUid-op2pmc-1，导致好友端
     * normalizePeerUid 二次补后缀后匹配不上），本模组主动在隐藏后缀“之前”插入去重序号
     * （形如 displayUid-1-openp2pmc），并自动重试直到服务器原样接受我们给的名字。
     */
    public static final String UID_SUFFIX = "-openp2pmc";

    /** 显示 UID（游戏 ID 或自定义） */
    public String displayUid() {
        return effectiveUid();
    }

    /** 自己的真实节点名 = 显示 UID + [冲突去重序号] + 隐藏后缀（序号为 0 时省略） */
    public String nodeUid() {
        return nodeUidWith(conflictIndex);
    }

    private String nodeUidWith(int idx) {
        String base = effectiveUid();
        return base + (idx > 0 ? "-" + idx : "") + UID_SUFFIX;
    }

    /** 把玩家输入的 UID 规范化为真实节点名（自动补后缀；已带后缀则不重复加） */
    public String normalizePeerUid(String uid) {
        return appendSuffix(uid == null ? "" : uid.trim());
    }

    private static String appendSuffix(String base) {
        if (base.isEmpty()) {
            return base;
        }
        return base.endsWith(UID_SUFFIX) ? base : base + UID_SUFFIX;
    }

    /** 去掉隐藏后缀，得到给玩家看的“干净”显示名（如 displayUid-1；冲突重试耗尽等极端情况按原值返回） */
    private static String displayNameOf(String actualUid) {
        if (actualUid != null && actualUid.endsWith(UID_SUFFIX)) {
            return actualUid.substring(0, actualUid.length() - UID_SUFFIX.length());
        }
        return actualUid;
    }

    private final Object lock = new Object();
    private volatile State state = State.STOPPED;

    private Path nodeDir;
    private Path configPath;
    private LogStore log;

    private Process process;
    private boolean intentionalStop;

    private volatile boolean sharing;
    private volatile int sharePort;
    private volatile String peerUid;
    private volatile int localPort;
    private volatile boolean announced;
    private volatile boolean tunnelNotified;

    /** 名字冲突时自动去重的序号：插入到隐藏后缀“之前”（如 displayUid-1-openp2pmc），0 表示未冲突 */
    private volatile int conflictIndex = 0;
    /** 最近一次 writeConfig 使用的 apps（冲突重试时复用，仅 Node 名随 conflictIndex 变化） */
    private JsonArray pendingApps = new JsonArray();
    /** 冲突自动重试上限，避免极端情况下无限重试 */
    private static final int MAX_CONFLICT_RETRY = 20;

    /** 联机失败/对方不在线的明显提示（界面顶部显示，直到下次操作） */
    private volatile boolean peerOfflineWarned;
    private volatile String lastFailureText;

    /** 点击列表项后的“待加入”状态：隧道建立后自动进服；未进入世界则断开并释放端口 */
    private volatile int pendingJoinPort = -1;
    private volatile String pendingJoinUid = "";
    private volatile long pendingJoinTime;
    private volatile long tunnelUpSince;

    /** 最近一次启动时间戳 / 登录失败或超时是否已提醒过 */
    private volatile long launchedAt;
    private volatile boolean loginWarned;

    /** 最近一次错误原因（直接显示在界面上） */
    private volatile String lastError = "";

    public void init() {
        nodeDir = FabricLoader.getInstance().getGameDir().resolve("openp2p").toAbsolutePath().normalize();
        try {
            Files.createDirectories(nodeDir);
        } catch (IOException e) {
            System.err.println("[OpenP2P] 无法创建目录: " + nodeDir);
        }
        configPath = nodeDir.resolve("config.json");
        log = new LogStore(nodeDir.resolve("openp2p.log"));
        Runtime.getRuntime().addShutdownHook(new Thread(this::killNodeSilently, "openp2p-shutdown"));
    }

    // ---------- 对外状态 ----------

    public State state() {
        return state;
    }

    public boolean sharing() {
        return sharing;
    }

    public int sharePort() {
        return sharePort;
    }

    public String peerUid() {
        return peerUid;
    }

    public int localPort() {
        return localPort;
    }

    public LogStore log() {
        return log;
    }

    public String effectiveUid() {
        return OpenP2PMod.get().config().effectiveUid();
    }

    public String statusText() {
        return switch (state) {
            case STOPPED -> "OpenP2P 未启动";
            case DOWNLOADING -> "正在准备 OpenP2P 节点…";
            case STARTING -> "正在连接 OpenP2P 服务器…";
            case ONLINE -> {
                if (sharing) {
                    yield "OpenP2P 已就绪 (UID: " + effectiveUid() + ")";
                }
                yield pendingJoinPort > 0 ? "正在连接对方主机 " + (peerUid == null ? "" : peerUid) + "…"
                        : "OpenP2P 已就绪";
            }
            case CONNECTING -> "正在连接对方主机 " + (peerUid == null ? "" : peerUid) + "…";
            case TUNNEL_UP -> "隧道已建立 → 127.0.0.1:" + localPort;
            case ERROR -> lastError == null || lastError.isEmpty() ? "OpenP2P 出错，请查看日志"
                    : "OpenP2P 出错：" + lastError;
        };
    }

    public String lastError() {
        return lastError == null ? "" : lastError;
    }

    /** 多人游戏界面顶部的提示文案（null=不显示） */
    public String overlayText() {
        if (state == State.ERROR) {
            return "出错：" + (lastError == null || lastError.isEmpty() ? "请查看日志" : lastError);
        }
        if (state == State.TUNNEL_UP) {
            return "隧道已建立，正在进入世界…";
        }
        if (state == State.CONNECTING && peerOfflineWarned) {
            return "对方不在线：请让对方保持「远程联机:开」的世界打开，隧道会自动重连";
        }
        if (state != State.STOPPED) {
            return statusText();
        }
        if (lastFailureText != null) {
            return "连接失败：" + lastFailureText;
        }
        return null;
    }

    /** 多人游戏界面顶部提示的颜色 */
    public int overlayColor() {
        if (state == State.TUNNEL_UP) {
            return 0x55FF55;
        }
        if (state == State.ERROR || (state == State.STOPPED && lastFailureText != null)
                || (state == State.CONNECTING && peerOfflineWarned)) {
            return 0xFF5555;
        }
        return 0xFFFF55;
    }

    private void setError(String reason) {
        lastError = reason;
        state = State.ERROR;
    }

    public void log(String line) {
        if (log != null) {
            log.add(line);
        }
    }

    // ---------- 主流程 ----------

    /** 主机端：开启分享（apps 为空，仅注册 UID） */
    public void startShare(int lanPort) {
        synchronized (lock) {
            if (sharing && sharePort == lanPort && state == State.ONLINE && process != null && process.isAlive()) {
                log("已处于分享状态，无需重复开启 (端口 " + lanPort + ")");
                return;
            }
            sharing = true;
            sharePort = lanPort;
            peerUid = null;
            announced = false;
            tunnelNotified = false;
            conflictIndex = 0;
            pendingJoinPort = -1;
            tunnelUpSince = 0;
            peerOfflineWarned = false;
            lastFailureText = null;
            lastError = "";
            log("===== 开启 OpenP2P 分享  UID=" + effectiveUid() + "（内部名 " + nodeUid() + "）端口=" + lanPort + " =====");
            pendingApps = new JsonArray();
            writeConfig(pendingApps);
            launch();
        }
    }

    /** 联机端：点击列表项后建立隧道（uid/远程端口/本地端口；隧道就绪自动进服） */
    public void connectFromList(String uid, int dstPort, int local) {
        synchronized (lock) {
            sharing = false;
            sharePort = 0;
            String fullPeer = normalizePeerUid(uid);
            peerUid = fullPeer;
            localPort = local;
            announced = false;
            tunnelNotified = false;
            conflictIndex = 0;
            pendingJoinPort = local;
            pendingJoinUid = uid;
            pendingJoinTime = System.currentTimeMillis();
            tunnelUpSince = 0;
            peerOfflineWarned = false;
            lastFailureText = null;
            lastError = "";
            log("===== 点击列表项：连接 OpenP2P 主机 " + uid + "（内部名 " + fullPeer + "）:" + dstPort
                    + "  本地端口=" + local + " =====");
            pendingApps = oneApp(fullPeer, dstPort, local);
            writeConfig(pendingApps);
            launch();
        }
    }

    public void stopAll(String why) {
        synchronized (lock) {
            intentionalStop = true;
            killProcess();
            state = State.STOPPED;
            sharing = false;
            peerUid = null;
            tunnelNotified = false;
            pendingJoinPort = -1;
            pendingJoinUid = "";
            tunnelUpSince = 0;
            log("===== OpenP2P 已停止 (" + why + ") =====");
        }
    }

    public void onIntegratedServerStopped() {
        synchronized (lock) {
            // 无条件关闭：主机退出存档 / 客机断开连接，一律关闭所有隧道并释放端口
            if (state != State.STOPPED) {
                stopAll(sharing ? "退出存档" : "断开连接");
            }
        }
    }

    // ---------- 进程 ----------

    private void launch() {
        synchronized (lock) {
            intentionalStop = false;
            killProcess();
            state = State.STARTING;
            launchedAt = System.currentTimeMillis();
            loginWarned = false;
        }
        Thread worker = new Thread(this::launchWorker, "openp2p-launch");
        worker.setDaemon(true);
        worker.start();
    }

    private void launchWorker() {
        synchronized (lock) {
            if (state == State.STARTING) {
                state = State.DOWNLOADING;
            }
        }
        Path bin = NodeDownloader.ensureBinary(nodeDir, log, OpenP2PMod.get().config());
        if (bin == null) {
            synchronized (lock) {
                setError("节点程序获取失败（下载失败或被杀毒拦截）");
            }
            OpenP2PMod.get().sendChat("§cOpenP2P 节点程序获取失败，请查看日志（多人游戏界面 → OpenP2P 日志）");
            return;
        }
        synchronized (lock) {
            launchedAt = System.currentTimeMillis();
            if (state != State.STARTING && state != State.DOWNLOADING) {
                return; // 已被停止
            }
            state = State.STARTING;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.directory(nodeDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            synchronized (lock) {
                if (intentionalStop) {
                    p.destroyForcibly();
                    return;
                }
                process = p;
            }
            Thread reader = new Thread(() -> readLoop(p), "openp2p-reader");
            reader.setDaemon(true);
            reader.start();
            Thread watcher = new Thread(() -> {
                try {
                    int code = p.waitFor();
                    synchronized (lock) {
                        if (!intentionalStop && process == p) {
                            setError("节点进程异常退出 (代码 " + code + ")");
                        }
                    }
                    if (!intentionalStop) {
                        log("OpenP2P 节点进程已退出 (代码 " + code + ")，请查看日志");
                    }
                } catch (InterruptedException ignored) {
                }
            }, "openp2p-watcher");
            watcher.setDaemon(true);
            watcher.start();
        } catch (IOException e) {
            synchronized (lock) {
                setError("节点启动失败（可能被杀毒拦截）");
            }
            log("[错误] 启动节点失败: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("740")) {
                log("[指引] 节点程序要求管理员权限 (error=740)：请使用 1.0.4+ 模组（自动使用无需管理员的节点版本），或以管理员身份运行游戏");
            } else {
                log("[指引] 程序可能被杀毒软件拦截，请为 " + nodeDir + " 目录添加信任/白名单后重试");
            }
            OpenP2PMod.get().sendChat("§cOpenP2P 节点启动失败（可能被杀毒拦截），详见日志");
        }
    }

    private void readLoop(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                onLine(line);
            }
        } catch (IOException ignored) {
        }
    }

    private void killProcess() {
        Process p = process;
        process = null;
        if (p != null && p.isAlive()) {
            try {
                p.destroy();
            } catch (Exception ignored) {
            }
            try {
                if (!p.waitFor(1200, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void killNodeSilently() {
        try {
            Process p = process;
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------- config.json ----------

    private JsonObject baseNetwork() {
        OpenP2PConfig cfg = OpenP2PMod.get().config();
        JsonObject n = new JsonObject();
        n.add("Token", new JsonPrimitive(new BigDecimal(cfg.token.trim())));
        n.add("Node", new JsonPrimitive(nodeUid()));
        n.add("User", new JsonPrimitive(cfg.user));
        n.add("ShareBandwidth", new JsonPrimitive(cfg.shareBandwidth));
        n.add("ServerHost", new JsonPrimitive(cfg.serverHost));
        n.add("ServerPort", new JsonPrimitive(cfg.serverPort));
        // 使用随机 UDP 端口，避免本机端口被占用（同机多开 / 其他程序占用 27182/27183 时也能工作）
        int udp1 = 10240 + (int) (Math.random() * 50000);
        n.add("UDPPort1", new JsonPrimitive(udp1));
        n.add("UDPPort2", new JsonPrimitive(udp1 + 1));
        n.add("PublicIPPort", new JsonPrimitive(0));
        return n;
    }

    private JsonArray oneApp(String peer, int dstPort, int local) {
        JsonObject app = new JsonObject();
        app.add("AppName", new JsonPrimitive("Minecraft"));
        app.add("Protocol", new JsonPrimitive("tcp"));
        app.add("SrcPort", new JsonPrimitive(local));
        app.add("PeerNode", new JsonPrimitive(peer));
        app.add("DstPort", new JsonPrimitive(dstPort));
        app.add("DstHost", new JsonPrimitive("localhost"));
        app.add("Whitelist", new JsonPrimitive(""));
        app.add("PeerUser", new JsonPrimitive(""));
        app.add("RelayNode", new JsonPrimitive(""));
        app.add("Enabled", new JsonPrimitive(1));
        JsonArray a = new JsonArray();
        a.add(app);
        return a;
    }

    private void writeConfig(JsonArray apps) {
        JsonObject root = new JsonObject();
        root.add("network", baseNetwork());
        root.add("apps", apps);
        root.add("LogLevel", new JsonPrimitive(1));
        try {
            Files.writeString(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            log("[错误] 写 config.json 失败: " + e.getMessage());
        }
    }

    // ---------- stdout 解析 ----------

    private void onLine(String raw) {
        String stamped = "[" + LocalTime.now().format(TIME) + "] " + raw;
        log(stamped);

        if (raw.contains("login ok")) {
            synchronized (lock) {
                if (state != State.ERROR) {
                    state = State.ONLINE;
                }
            }
            Matcher m = NODE_LOGIN.matcher(raw);
            String actualUid = m.find() ? m.group(1) : nodeUid();
            log("已登录 OpenP2P 服务器，实际 UID=" + actualUid);

            // 名字冲突：服务器把我们的节点重命名了（重命名后缀被加到了隐藏后缀之后）。
            // 这里自动加一个去重序号到隐藏后缀“之前”，并重启节点重试，直到服务器原样接受我们给的名字。
            synchronized (lock) {
                if (!actualUid.equals(nodeUid()) && conflictIndex < MAX_CONFLICT_RETRY) {
                    conflictIndex++;
                    String candidate = nodeUid();
                    log("名字与他人冲突（服务器重命名为 " + actualUid + "），自动加去重序号重试为 " + candidate);
                    writeConfig(pendingApps);
                    launch();
                    return;
                }
            }

            boolean host = sharing;
            if (host && !announced) {
                announced = true;
                // 播报给好友的是“干净”的显示名（去掉隐藏后缀 openp2pmc，好友端会自动补回）
                String display = displayNameOf(actualUid);
                if (conflictIndex == 0) {
                    OpenP2PMod.get().sendChat("§aOpenP2P 远程联机已开启! §r告诉好友你的 §eUID=" + display
                            + " §r和 §e端口=" + sharePort);
                } else {
                    OpenP2PMod.get().sendChat("§aOpenP2P 远程联机已开启! §r你的名字与他人冲突，已自动改为 §e"
                            + display + "§r（端口=§e" + sharePort + "§r），请把这个 UID 发给好友");
                }
            }
            return;
        }

        if (raw.toLowerCase().contains("login error") || raw.toLowerCase().contains("login fail")
                || (raw.toLowerCase().contains("login") && raw.toLowerCase().contains("reject"))) {
            synchronized (lock) {
                setError("OpenP2P 服务器登录失败");
            }
            log("[错误] OpenP2P 服务器登录失败: 请检查 config/openp2p.json 中 token/user/serverHost 设置及网络");
            OpenP2PMod.get().sendChat("§cOpenP2P 服务器登录失败，请查看 OpenP2P 日志");
            return;
        }

        if (raw.contains("autorunApp start")) {
            synchronized (lock) {
                if (!sharing && state == State.ONLINE) {
                    state = State.CONNECTING;
                }
            }
            return;
        }

        Matcher m = LISTEN.matcher(raw);
        if (m.find()) {
            String proto = m.group(1);
            int port = Integer.parseInt(m.group(2));
            boolean start = "START".equals(m.group(3));
            if (start) {
                log("隧道已建立! 本地 " + proto + ":" + port + " → 对方主机");
                synchronized (lock) {
                    state = State.TUNNEL_UP;
                    tunnelUpSince = System.currentTimeMillis();
                }
                if (!sharing) {
                    OpenP2PMod.get().sendChat("§a隧道已建立! 正在进入世界…");
                }
            } else {
                if (!sharing) {
                    // 客机：隧道断开 = 与主机失联，立即关闭节点并释放端口
                    log("隧道已断开 (" + proto + ":" + port + ")，立即关闭节点并释放端口");
                    lastFailureText = "与对方的连接已断开（隧道中断）";
                    stopAll("隧道已断开");
                } else {
                    log("隧道已断开 (" + proto + ":" + port + ")");
                    synchronized (lock) {
                        if (state == State.TUNNEL_UP) {
                            state = State.CONNECTING;
                        }
                    }
                }
            }
            return;
        }

        if (raw.contains("Only one usage of each socket address")) {
            log("[警告] 本地端口被占用! 请更换本地端口，或关闭占用该端口的程序");
            return;
        }

        if (raw.contains("it will auto reconnect when peer node online") || raw.contains("peer offline")) {
            log("[提示] 对方主机当前不在线");
            if (!sharing) {
                // 不做后台自动重连：立即停止并明确提示，由玩家重新点击条目重试
                lastFailureText = "对方不在线：请让对方开启「远程联机:开」并保持在游戏世界内，然后重新点击条目连接";
                OpenP2PMod.get().sendChat("§c对方不在线：请让对方（房主）开启「远程联机:开」并保持在游戏世界内；"
                        + "本端已停止（不会自动重连），对方上线后请重新点击该条目连接");
                stopAll("对方不在线");
            }
        }
    }

    // ---------- 客户端每帧 ----------

    public void clientTick(MinecraftClient mc) {
        long now = System.currentTimeMillis();

        // 登录超时提醒（防止一直卡在“正在连接”没有任何提示）
        if (!loginWarned && launchedAt > 0 && (state == State.STARTING || state == State.DOWNLOADING)
                && now - launchedAt > 35_000) {
            loginWarned = true;
            OpenP2PConfig cfg = OpenP2PMod.get().config();
            log("[提示] 启动已超过 35 秒仍未登录: 请确认网络可访问 " + cfg.serverHost + ":" + cfg.serverPort
                    + "，且 openp2p 节点未被防火墙/杀毒拦截（可打开 OpenP2P 日志查看详情）");
            OpenP2PMod.get().sendChat("§eOpenP2P 连接服务器超时，请查看 OpenP2P 日志（可能被防火墙/杀毒拦截）");
        }

        if (sharing) {
            return;
        }

        // 点击列表项后：隧道建立 → 自动进服（和原版点击加入一样，但先建隧道）
        if (pendingJoinPort > 0 && state == State.TUNNEL_UP) {
            int port = pendingJoinPort;
            String uid = pendingJoinUid;
            pendingJoinPort = -1;
            pendingJoinUid = "";
            if (mc.world == null) {
                Screen parent = mc.currentScreen;
                ServerInfo info = new ServerInfo("远程联机-" + uid, "127.0.0.1:" + port, false);
                if (parent != null) {
                    mc.setScreen(null);
                    ConnectScreen.connect(parent, mc, new ServerAddress("127.0.0.1", port), info, false);
                    log("自动加入服务器 127.0.0.1:" + port);
                } else {
                    OpenP2PMod.get().sendChat("§a隧道已建立: §e127.0.0.1:" + port + "§a，请到服务器列表点击进入");
                }
            }
        }

        // 核心规则：只要没有进入世界，隧道一律断开、关闭 openp2p、释放端口
        if (mc.world == null) {
            if (state == State.TUNNEL_UP && tunnelUpSince > 0 && now - tunnelUpSince > 25_000) {
                lastFailureText = "隧道已建立但未能进入世界（可能本地端口被占用）";
                stopAll("未进入世界，隧道已断开并释放端口");
            } else if (pendingJoinPort > 0 && now - pendingJoinTime > 60_000) {
                // 长时间连不上对方（对方不在线等），不挂机
                pendingJoinPort = -1;
                pendingJoinUid = "";
                lastFailureText = "连接超时：请核对 UID 与端口，确认对方已开启远程联机";
                OpenP2PMod.get().sendChat("§c连接对方超时：请核对 UID 与端口是否正确，"
                        + "并确认对方已开启「远程联机:开」且保持在游戏世界内");
                stopAll("连接对方超时（60 秒），已断开并释放端口");
            }
        }
    }
}