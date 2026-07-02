package com.light.reactagent.controller;

import com.light.reactagent.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Knowledge base management API.
 * Supports upload, list, and delete of user knowledge base documents.
 */
@RestController
@RequestMapping("/knowledge-base")
@Slf4j
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * Upload a document to the knowledge base.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "File is empty")
            );
        }
        try {
            String result = knowledgeBaseService.processAndStore(file);
            log.info("upload success: {}", result);
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
     * List all user-uploaded files in the knowledge base.
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles() {
        try {
            List<Map<String, Object>> files = knowledgeBaseService.listUploadedFiles();
            return ResponseEntity.ok(Map.of("success", true, "files", files));
        } catch (Exception e) {
            log.error("list files failed", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Failed to list files: " + e.getMessage()));
        }
    }

    /**
     * Delete all chunks of a specific uploaded file from the knowledge base.
     */
    @DeleteMapping("/files/{sourceName}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable("sourceName") String sourceName) {
        try {
            int deleted = knowledgeBaseService.deleteBySource(sourceName);
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
}