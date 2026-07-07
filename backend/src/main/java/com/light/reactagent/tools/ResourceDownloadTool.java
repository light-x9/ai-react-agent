package com.light.reactagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.light.reactagent.constant.FileConstant;
import com.light.reactagent.tools.file.FileContextHolder;
import com.light.reactagent.tools.file.FileMetadataManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * 资源下载工具：从 URL 下载文件到沙箱目录
 * <p>
 * 安全策略：文件名经路径穿越校验，下载完成后注册元数据供前端下载。
 */
public class ResourceDownloadTool {

    private final FileMetadataManager fileMetadataManager;

    private final File baseDir = new File(FileConstant.FILE_SAVE_DIR + "/download");

    public ResourceDownloadTool() {
        this.fileMetadataManager = null;
    }

    public ResourceDownloadTool(FileMetadataManager fileMetadataManager) {
        this.fileMetadataManager = fileMetadataManager;
    }

    @Tool(description = "Download a resource from a given URL and save it as a file. Returns a fileId for download.")
    public String downloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Name of the file to save the downloaded resource, e.g. image.png") String fileName) {
        // 安全校验：防止路径穿越
        if (fileName == null || fileName.isBlank()) {
            return "下载失败：文件名为空";
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return "下载失败：文件名包含非法字符";
        }
        if (url == null || url.isBlank()) {
            return "下载失败：URL 为空";
        }

        // SSRF 防护：校验 URL 协议和目标地址
        String ssrfCheck = checkUrlSafety(url);
        if (ssrfCheck != null) {
            return ssrfCheck;
        }

        File target = resolveSafe(fileName);
        if (target == null) {
            return "下载失败：文件路径越界";
        }
        try {
            FileUtil.mkdir(baseDir);
            HttpUtil.downloadFile(url, target);
            // 不向调用方（LLM）暴露服务器绝对路径
            String result = "文件下载成功：" + target.getName();

            // 注册文件元数据，使前端可通过 fileId 下载
            if (fileMetadataManager != null) {
                String chatId = FileContextHolder.getChatId();
                if (chatId != null && target.exists()) {
                    String fileId = fileMetadataManager.registerFile(
                            chatId,
                            target.getName(),
                            resolveContentType(fileName),
                            resolveContentType(fileName),
                            target.length()
                    );
                    result += " [fileId=" + fileId + "]";
                    FileContextHolder.recordFileId(fileId);
                }
            }
            return result;
        } catch (Exception e) {
            return "下载资源出错：" + e.getMessage();
        }
    }

    private File resolveSafe(String fileName) {
        Path basePath = baseDir.toPath().normalize();
        Path targetPath = new File(baseDir, fileName).toPath().normalize();
        if (!targetPath.startsWith(basePath)) {
            return null;
        }
        return targetPath.toFile();
    }

    /**
     * SSRF 防护：禁止访问内网地址、非 HTTP(S) 协议
     * <p>
     * 检查项：
     * 1. 协议必须是 http 或 https（防止 file:///etc/passwd、gopher:// 等）
     * 2. 禁止 localhost / 127.0.0.1 / 0.0.0.0
     * 3. 禁止私有网段（10.x、172.16-31.x、192.168.x、169.254.x）
     *
     * @param url 待检查的 URL
     * @return null=安全；非 null=拒绝原因
     */
    private String checkUrlSafety(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "下载失败：URL 格式非法";
        }
        // 1. 协议校验
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return "下载失败：仅支持 HTTP/HTTPS 协议";
        }
        // 2. 主机校验
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "下载失败：URL 缺少主机名";
        }
        String lowerHost = host.toLowerCase();
        // 3. 禁止 localhost 和回环地址
        if (lowerHost.equals("localhost") || lowerHost.equals("127.0.0.1") || lowerHost.equals("0.0.0.0") || lowerHost.equals("::1")) {
            return "下载失败：禁止访问本机地址（SSRF 防护）";
        }
        // 4. 禁止私有网段（防止访问云服务器元数据 API、内网服务）
        if (isPrivateIp(host)) {
            return "下载失败：禁止访问内网地址（SSRF 防护）";
        }
        return null;
    }

    /**
     * 判断是否为私有/内网 IP 地址
     */
    private boolean isPrivateIp(String host) {
        // 简单前缀匹配（覆盖常见私有网段）
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) {
            return true;
        }
        // 172.16.0.0 ~ 172.31.255.255
        if (host.startsWith("172.")) {
            try {
                int secondOctet = Integer.parseInt(host.substring(4, host.indexOf('.', 4)));
                return secondOctet >= 16 && secondOctet <= 31;
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                return false;
            }
        }
        // 链路本地地址
        if (host.startsWith("[fe80:") || host.startsWith("fe80:")) {
            return true;
        }
        return false;
    }

    private String resolveContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        return "application/octet-stream";
    }
}
