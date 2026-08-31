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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 下载并解压官方 openp2p 节点程序到 <游戏目录>/openp2p/。
 * 下载失败时在日志给出手动下载指引（用户也可自行放置可执行文件）。
 */
public final class NodeDownloader {

    private static final String REPO_API = "https://api.github.com/repos/openp2p-cn/openp2p/releases/latest";
    private static final String RELEASE_BASE = "https://github.com/openp2p-cn/openp2p/releases/download/";
    /** 注意：这是完整 tag（带 v），release 下载路径必须用完整 tag，而资产文件名里的版本不带 v */
    private static final String FALLBACK_TAG = "v3.25.11";

    /** 已验证可用的国内镜像（OPL-WpfApp 生产环境同款二进制，3.25.11 windows-386，兼容 x64） */
    private static final String GLDHN_MIRROR = "https://file.gldhn.top/file/openp2p-3.25.11.windows-386.zip";

    /**
     * 3.21.12 版内嵌清单不要求管理员权限（3.25.x 官方版 manifest 带 requireAdministrator，
     * 非管理员运行游戏时会报 CreateProcess error=740），Windows 优先使用该版本。
     */
    private static final String OLD_RELEASE_TAG = "v3.21.12";
    private static final String OLD_GH_URL = RELEASE_BASE + OLD_RELEASE_TAG + "/openp2p3.21.12.windows-386.zip";
    private static final String OLD_GLDHN_URL = "https://file.gldhn.top/file/openp2p-r3.21.12.windows-386.zip";

    private NodeDownloader() {
    }

    /** 返回就绪的可执行文件路径；失败返回 null（指引已写入日志） */
    public static Path ensureBinary(Path nodeDir, LogStore log, OpenP2PConfig cfg) {
        Platform p = Platform.detect();
        Path bin = nodeDir.resolve(p.binaryName());
        try {
            Files.createDirectories(nodeDir);
        } catch (IOException ignored) {
        }

        // 1) 本地已有可用的（无管理员要求）节点 → 直接用
        if (!requiresAdmin(bin)) {
            log.add("[节点] 使用本地节点程序: " + bin);
            return bin;
        }
        if (Files.exists(bin)) {
            log.add("[节点] 本地节点程序需要管理员权限（或已损坏），将重新获取");
            deleteQuietly(bin);
        }

        // 2) Windows：优先解包模组内置的免管理员节点（只装 mod 即可用，无需联网下载）
        if (p.isWindows() && tryExtractBundled(bin, log)) {
            return bin;
        }

        log.add("[节点] 未找到 " + p.binaryName() + "，开始下载（也可手动下载解压后放到 " + nodeDir + "）…");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String fullTag = latestTag(client, log);
        if (fullTag == null || fullTag.isEmpty()) {
            fullTag = FALLBACK_TAG;
            log.add("[节点] 获取最新版本失败，尝试固定版本 " + fullTag);
        }
        String version = fullTag.startsWith("v") ? fullTag.substring(1) : fullTag;
        String asset = "openp2p-" + version + "." + p.assetName();
        String officialUrl = RELEASE_BASE + fullTag + "/" + asset;
        log.add("[下载] GitHub 官方: " + officialUrl);

        String prefix = "";
        if (cfg.downloadMirrorPrefix != null && !cfg.downloadMirrorPrefix.isBlank()) {
            prefix = cfg.downloadMirrorPrefix.trim();
            while (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
        }

        List<String> urls = new ArrayList<>();
        if (p.isWindows()) {
            // Windows：优先 3.21.12（无管理员要求）；3.25.x 官方版均要求管理员，会在校验时被跳过
            log.add("[下载] Windows 优先使用无需管理员权限的节点 3.21.12");
            if (!prefix.isEmpty()) {
                urls.add(prefix + OLD_GLDHN_URL);
            }
            urls.add(OLD_GLDHN_URL);
            if (!prefix.isEmpty()) {
                urls.add(prefix + OLD_GH_URL);
            }
            urls.add(OLD_GH_URL);
            if (!prefix.isEmpty()) {
                urls.add(prefix + officialUrl);
            }
            urls.add(officialUrl);
            if (!prefix.isEmpty()) {
                urls.add(prefix + GLDHN_MIRROR);
            }
            urls.add(GLDHN_MIRROR);
            log.add("[下载] 已加入国内镜像备用: " + GLDHN_MIRROR);
        } else {
            if (!prefix.isEmpty()) {
                urls.add(prefix + officialUrl);
            }
            urls.add(officialUrl);
        }

        IOException lastErr = null;
        for (String u : urls) {
            log.add("[下载] " + u);
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(u))
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", "openp2p-mc-mod")
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() != 200) {
                    throw new IOException("HTTP " + resp.statusCode());
                }
                byte[] body = resp.body();
                if (!looksLikeArchive(body, p)) {
                    log.add("[下载] 该地址返回内容不是有效压缩包（可能被网络拦截/劫持），已跳过此地址");
                    throw new IOException("invalid archive content (" + body.length + " bytes)");
                }
                Path tmp = Files.createTempFile("openp2p-dl", p.ext());
                try {
                    Files.write(tmp, body);
                    extract(tmp, nodeDir, p, log);
                } finally {
                    deleteQuietly(tmp);
                }
                if (!p.isWindows()) {
                    bin.toFile().setExecutable(true, true);
                }
                if (requiresAdmin(bin)) {
                    log.add("[下载] 该版本要求管理员权限（不适用），已换下一个地址");
                    deleteQuietly(bin);
                    throw new IOException("requires administrator");
                }
                log.add("[节点] 就绪: " + bin);
                return bin;
            } catch (IOException e) {
                lastErr = e;
                log.add("[下载] 失败: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.add("[下载] 已中断");
                return null;
            }
        }

        String msg = "全部下载地址失败" + (lastErr != null ? " (" + lastErr.getMessage() + ")" : "");
        log.add("[错误] " + msg);
        log.add("[指引] 请手动下载: " + officialUrl);
        log.add("[指引] 国内镜像: " + GLDHN_MIRROR);
        log.add("[指引] 解压后把 " + p.binaryName() + " 放到: " + nodeDir);
        log.add("[指引] 国内网络可在 config/openp2p.json 中设置 downloadMirrorPrefix 镜像前缀后重试");
        log.add("[提示] openp2p 是内网穿透程序，杀毒软件可能误报/拦截下载，请把 " + nodeDir + " 加入杀毒白名单");
        return null;
    }

    /** 检查节点程序是否要求管理员权限（requireAdministrator 清单）或不可用 */
    private static boolean requiresAdmin(Path bin) {
        if (!Files.exists(bin) || Files.isDirectory(bin)) {
            return true;
        }
        try {
            if (Files.size(bin) == 0) {
                return true;
            }
            byte[] d = Files.readAllBytes(bin);
            return new String(d, StandardCharsets.US_ASCII).indexOf("requireAdministrator") >= 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    /** 从模组 jar 内置资源释放 Windows 节点（免下载、免管理员权限） */
    private static boolean tryExtractBundled(Path bin, LogStore log) {
        try (InputStream in = NodeDownloader.class.getResourceAsStream("/assets/openp2p/bin/openp2p.exe")) {
            if (in == null) {
                log.add("[节点] 模组内未包含内置节点（非 Windows 版），转入联网下载");
                return false;
            }
            Files.copy(in, bin, StandardCopyOption.REPLACE_EXISTING);
            if (requiresAdmin(bin)) {
                log.add("[节点] 内置节点异常，改用联网下载");
                deleteQuietly(bin);
                return false;
            }
            log.add("[节点] 已从模组内置资源释放免管理员节点: " + bin);
            return true;
        } catch (IOException e) {
            log.add("[节点] 释放内置节点失败: " + e.getMessage());
            deleteQuietly(bin);
            return false;
        }
    }

    private static boolean looksLikeArchive(byte[] body, Platform p) {
        if (body == null || body.length < 4) {
            return false;
        }
        if (p.isWindows()) {
            // zip 魔数 PK\x03\x04
            return (body[0] & 0xFF) == 0x50 && (body[1] & 0xFF) == 0x4B
                    && (body[2] & 0xFF) == 0x03 && (body[3] & 0xFF) == 0x04;
        }
        // gzip 魔数
        return (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B;
    }

    private static String latestTag(HttpClient client, LogStore log) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(REPO_API))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "openp2p-mc-mod")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
            String tag = obj.has("tag_name") ? obj.get("tag_name").getAsString() : null;
            if (tag == null) {
                return null;
            }
            return tag.startsWith("v") ? tag.substring(1) : tag;
        } catch (Exception e) {
            log.add("[下载] 获取最新版本失败: " + e.getMessage());
            return null;
        }
    }

    private static void extract(Path archive, Path nodeDir, Platform p, LogStore log) throws IOException {
        if (p.isWindows()) {
            extractZip(archive, nodeDir, p, log);
        } else {
            extractTarGz(archive, nodeDir, p, log);
        }
    }

    private static void extractZip(Path zip, Path nodeDir, Platform p, LogStore log) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                if (matchesBasename(baseName(e.getName()), p)) {
                    Path out = nodeDir.resolve(p.binaryName());
                    try (InputStream in = zf.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                }
            }
        }
    }

    private static void extractTarGz(Path archive, Path nodeDir, Platform p, LogStore log) throws IOException {
        try (InputStream fin = Files.newInputStream(archive);
             GZIPInputStream gin = new GZIPInputStream(fin);
             BufferedInputStream bin = new BufferedInputStream(gin)) {

            byte[] header = new byte[512];
            while (true) {
                int n = readFully(bin, header);
                if (n == -1) {
                    break; // EOF
                }
                if (n < 512) {
                    break;
                }
                if (isZeroBlock(header)) {
                    break;
                }
                String name = cstr(header, 0, 100);
                long size = parseOctal(header, 124, 12);
                int type = header[156] & 0xFF;

                if (type == 'L') { // GNU long name
                    byte[] data = new byte[(int) size];
                    readFully(bin, data);
                    skipN(bin, pad(size));
                    continue;
                }
                boolean isFile = type == 0 || type == '0';
                if (isFile && matchesBasename(baseName(name), p)) {
                    Path out = nodeDir.resolve(p.binaryName());
                    try (OutputStream fos = Files.newOutputStream(out)) {
                        copyN(bin, fos, size);
                    }
                    return;
                }
                skipN(bin, size + pad(size));
            }
        }
    }

    private static boolean matchesBasename(String base, Platform p) {
        if (base.isEmpty()) {
            return false;
        }
        if (base.equalsIgnoreCase(p.binaryName())) {
            return true;
        }
        String lower = base.toLowerCase();
        if (p.isWindows()) {
            return lower.startsWith("openp2p") && lower.endsWith(".exe");
        }
        return lower.startsWith("openp2p") && !lower.contains(".");
    }

    private static String baseName(String path) {
        String norm = path.replace('\\', '/');
        int i = norm.lastIndexOf('/');
        return i >= 0 ? norm.substring(i + 1) : norm;
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) {
            end++;
        }
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long parseOctal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            char c = (char) (b[i] & 0xFF);
            if (c >= '0' && c <= '7') {
                v = (v << 3) + (c - '0');
            }
        }
        return v;
    }

    private static boolean isZeroBlock(byte[] b) {
        for (byte x : b) {
            if (x != 0) {
                return false;
            }
        }
        return true;
    }

    /** 读取至填满 buf；EOF 时返回 -1（读 0 字节但非 EOF 时也返回 -1） */
    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) {
                return off == 0 ? -1 : off;
            }
            off += r;
        }
        return off;
    }

    private static int readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) {
                break;
            }
            off += r;
        }
        return off;
    }

    private static void copyN(InputStream in, OutputStream out, long n) throws IOException {
        byte[] buf = new byte[8192];
        long left = n;
        while (left > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) {
                break;
            }
            out.write(buf, 0, r);
            left -= r;
        }
    }

    private static long pad(long size) {
        long rem = size % 512;
        return rem == 0 ? 0 : 512 - rem;
    }

    private static void skipN(InputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            long s = in.skip(left);
            if (s <= 0) {
                if (in.read() < 0) {
                    return;
                }
                left--;
            } else {
                left -= s;
            }
        }
    }
}