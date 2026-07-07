package com.light.reactagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.light.reactagent.constant.FileConstant;
import com.light.reactagent.tools.file.FileContextHolder;
import com.light.reactagent.tools.file.FileMetadataManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
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
