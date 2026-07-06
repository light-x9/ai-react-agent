package com.light.reactagent.controller;

import com.light.reactagent.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（多租户隔离）
 * <p>
 * 所有操作都绑定当前登录用户（从 SecurityContext 取 userId），
 * 用户只能管理自己的知识库文档。
 */
@RestController
@RequestMapping("/knowledge-base")
@Slf4j
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传文档到当前用户的知识库（同名文件会覆盖更新）
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "File is empty"));
        }
        try {
            String userId = currentUserId();
            String result = knowledgeBaseService.processAndStore(file, userId);
            log.info("upload success: {}, userId={}", result, userId);
            return ResponseEntity.ok(Map.of("success", true, "message", result));
        } catch (IllegalArgumentException e) {
            log.warn("upload validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("upload processing failed", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Upload failed: " + e.getMessage()));
        }
    }

    /**
     * 列出当前用户的知识库文件（含扩展元数据）
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles() {
        try {
            String userId = currentUserId();
            List<Map<String, Object>> files = knowledgeBaseService.listUploadedFiles(userId);
            return ResponseEntity.ok(Map.of("success", true, "files", files));
        } catch (Exception e) {
            log.error("list files failed", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Failed to list files: " + e.getMessage()));
        }
    }

    /**
     * 删除当前用户的某个知识库文件
     */
    @DeleteMapping("/files/{sourceName}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable("sourceName") String sourceName) {
        try {
            String userId = currentUserId();
            int deleted = knowledgeBaseService.deleteBySource(sourceName, userId);
            if (deleted == 0) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "File '" + sourceName + "' not found or already deleted"));
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Deleted " + deleted + " chunks of '" + sourceName + "'"));
        } catch (Exception e) {
            log.error("delete file failed", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Delete failed: " + e.getMessage()));
        }
    }

    /**
     * 批量删除当前用户的知识库文件
     * 请求体：["file1.md", "file2.txt", ...]
     */
    @PostMapping("/files/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDelete(
            @RequestBody List<String> sourceNames) {
        try {
            String userId = currentUserId();
            int count = knowledgeBaseService.batchDelete(sourceNames, userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Deleted " + count + " file(s)",
                    "deleted", count));
        } catch (Exception e) {
            log.error("batch delete failed", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Batch delete failed: " + e.getMessage()));
        }
    }

    /**
     * 预览文件内容（返回前 2000 字符）
     */
    @GetMapping("/files/{sourceName}/preview")
    public ResponseEntity<Map<String, Object>> previewFile(
            @PathVariable("sourceName") String sourceName) {
        try {
            String userId = currentUserId();
            Map<String, Object> preview = knowledgeBaseService.previewFile(sourceName, userId);
            if (preview == null) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "File not found"));
            }
            return ResponseEntity.ok(Map.of("success", true, "preview", preview));
        } catch (Exception e) {
            log.error("preview failed", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Preview failed: " + e.getMessage()));
        }
    }

    /**
     * 检索测试：快速测试知识库检索效果
     * 请求体：{ "query": "关键词" }  或  { "query": "关键词", "sourceName": "xxx.md" }
     */
    @PostMapping("/search-test")
    public ResponseEntity<Map<String, Object>> searchTest(
            @RequestBody Map<String, Object> body) {
        try {
            String userId = currentUserId();
            String query = (String) body.get("query");
            if (query == null || query.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "query is empty"));
            }
            String sourceName = (String) body.get("sourceName");
            List<Map<String, Object>> hits = knowledgeBaseService.searchTest(query, sourceName, userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hits", hits,
                    "total", hits.size()));
        } catch (Exception e) {
            log.error("search test failed", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Search test failed: " + e.getMessage()));
        }
    }

    /**
     * 从安全上下文取当前登录用户名（作为知识库租户 key）
     */
    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        throw new IllegalStateException("未认证，无法识别用户");
    }
}
