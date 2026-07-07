package com.light.reactagent.controller;

import com.light.reactagent.tools.file.FileMetadata;
import com.light.reactagent.tools.file.FileMetadataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载接口
 * <p>
 * 供前端下载 AI 生成的文件（PDF / MD 等）。
 * 通过 fileId 定位文件，并校验 chatId 归属（防止 A 下载 B 的文件）。
 */
@RestController
@RequestMapping("/files")
@Slf4j
public class FileDownloadController {

    @Autowired
    private FileMetadataManager fileMetadataManager;

    /**
     * 下载文件
     *
     * @param fileId 文件 ID（必填，由工具返回）
     * @param chatId 会话 ID（必填，用于归属校验）
     * @return 文件流响应
     */
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> download(
            @RequestParam("fileId") String fileId,
            @RequestParam("chatId") String chatId) {

        // 0. 参数校验：防止空字符串绕过（@RequestParam required=true 只校验 null，不校验空字符串）
        if (fileId == null || fileId.isBlank() || chatId == null || chatId.isBlank()) {
            log.warn("[FileDownload] 拒绝下载：参数为空（fileId={}, chatId={}）",
                    fileId == null ? "null" : "len=" + fileId.length(),
                    chatId == null ? "null" : "len=" + chatId.length());
            return ResponseEntity.badRequest().build();
        }

        // 1. 归属校验：fileId 必须属于该 chatId
        FileMetadata metadata = fileMetadataManager.getFile(chatId, fileId);
        if (metadata == null) {
            log.warn("[FileDownload] 拒绝下载：fileId={} 不属于 chatId={}", fileId, chatId);
            return ResponseEntity.notFound().build();
        }

        // 2. 定位物理文件
        String absolutePath = fileMetadataManager.resolveAbsolutePath(chatId, fileId);
        if (absolutePath == null) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(absolutePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("[FileDownload] 物理文件不存在：{}", absolutePath);
            return ResponseEntity.notFound().build();
        }

        // 3. 构建响应（文件名 URL 编码，防止中文乱码）
        String encodedFileName = URLEncoder.encode(metadata.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .contentLength(file.length())
                .body(resource);
    }
}
