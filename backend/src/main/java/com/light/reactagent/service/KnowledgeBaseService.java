package com.light.reactagent.service;

import com.light.reactagent.rag.MyTokenTextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档服务（多租户隔离版）
 * <p>
 * 所有写入/查询/删除都带 userId，用户之间数据隔离。
 * userId 来自 JWT 认证（SecurityContext），由 Controller 层传入。
 */
@Service
@Slf4j
public class KnowledgeBaseService {

    private final VectorStore pgVectorVectorStore;
    private final MyTokenTextSplitter myTokenTextSplitter;
    private final JdbcTemplate jdbcTemplate;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("txt", "md");

    public KnowledgeBaseService(
            @Qualifier("pgVectorVectorStore") VectorStore pgVectorVectorStore,
            MyTokenTextSplitter myTokenTextSplitter,
            JdbcTemplate jdbcTemplate) {
        this.pgVectorVectorStore = pgVectorVectorStore;
        this.myTokenTextSplitter = myTokenTextSplitter;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== Upload ====================

    /**
     * 解析、分块、向量化并存储上传文件，metadata 带 userId 实现租户隔离
     */
    public String processAndStore(MultipartFile file, String userId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("filename is empty");
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "unsupported file type: " + extension + ", only: " + ALLOWED_EXTENSIONS);
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("文件过大，单文件上限 10MB");
        }

        String fileContent = new String(file.getBytes(), "UTF-8");
        if (fileContent.isBlank()) {
            throw new IllegalArgumentException("file content is empty");
        }
        log.info("file read: {}, {} chars, userId={}", originalFilename, fileContent.length(), userId);

        Document document = new Document(
                fileContent,
                Map.of(
                        "source", originalFilename,
                        "type", "user_upload",
                        "extension", extension,
                        "userId", userId
                )
        );

        List<Document> splitDocuments = myTokenTextSplitter.splitDocuments(List.of(document));
        log.info("split: {} -> {} chunks", originalFilename, splitDocuments.size());

        pgVectorVectorStore.add(splitDocuments);
        log.info("stored: {}, {} chunks in vector DB, userId={}", originalFilename, splitDocuments.size(), userId);

        return "File uploaded: " + originalFilename + " -> " + splitDocuments.size() + " chunks stored";
    }

    // ==================== List ====================

    /**
     * 列出指定用户的上传文件及其分块数（按 userId 过滤）
     */
    public List<Map<String, Object>> listUploadedFiles(String userId) {
        String sql = """
                SELECT metadata->>'source' AS source,
                       COUNT(*) AS chunks
                FROM vector_store
                WHERE metadata->>'type' = 'user_upload'
                  AND metadata->>'userId' = ?
                GROUP BY metadata->>'source'
                ORDER BY source
                """;
        List<Map<String, Object>> files = new ArrayList<>();
        jdbcTemplate.query(sql, ps -> ps.setString(1, userId), rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", rs.getString("source"));
            row.put("chunks", rs.getLong("chunks"));
            files.add(row);
        });
        log.info("listed {} uploaded files for userId={}", files.size(), userId);
        return files;
    }

    // ==================== Delete ====================

    /**
     * 删除指定用户的某个上传文件的所有分块（按 userId 过滤，防越权删除他人文件）
     */
    public int deleteBySource(String sourceName, String userId) {
        String sql = "DELETE FROM vector_store WHERE metadata->>'source' = ? AND metadata->>'userId' = ?";
        int deleted = jdbcTemplate.update(sql, sourceName, userId);
        log.info("deleted {} chunks for source: {}, userId={}", deleted, sourceName, userId);
        return deleted;
    }

    // ==================== Util ====================

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) return "";
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}
