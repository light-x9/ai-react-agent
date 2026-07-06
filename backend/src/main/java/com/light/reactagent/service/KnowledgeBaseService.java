package com.light.reactagent.service;

import com.light.reactagent.rag.MyTokenTextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
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

    /** 预览内容最大返回字符数 */
    private static final int PREVIEW_MAX_CHARS = 2000;
    /** 检索测试默认返回条数 */
    private static final int SEARCH_TEST_TOP_K = 5;

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
     * 解析、分块、向量化并存储上传文件，metadata 带 userId 实现租户隔离。
     * 同名文件会先删除旧分块再写入新分块（覆盖更新）。
     * metadata 包含：source / type / extension / userId / fileSize / charCount / uploadedAt / status。
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

        // 覆盖更新：同名文件先删除旧分块
        deleteBySource(originalFilename, userId);

        // 扩展 metadata：fileSize / charCount / uploadedAt / status
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", originalFilename);
        metadata.put("type", "user_upload");
        metadata.put("extension", extension);
        metadata.put("userId", userId);
        metadata.put("fileSize", file.getSize());
        metadata.put("charCount", (long) fileContent.length());
        metadata.put("uploadedAt", System.currentTimeMillis());
        metadata.put("status", "completed");

        Document document = new Document(fileContent, metadata);

        List<Document> splitDocuments = myTokenTextSplitter.splitDocuments(List.of(document));
        log.info("split: {} -> {} chunks", originalFilename, splitDocuments.size());

        pgVectorVectorStore.add(splitDocuments);
        log.info("stored: {}, {} chunks in vector DB, userId={}", originalFilename, splitDocuments.size(), userId);

        return "File uploaded: " + originalFilename + " -> " + splitDocuments.size() + " chunks stored";
    }

    // ==================== List ====================

    /**
     * 列出指定用户的上传文件（按 userId 过滤）。
     * 每个文件返回：source / chunks / fileSize / charCount / uploadedAt / status。
     * 使用 DISTINCT ON 取每个 source 最新一行的元数据（PostgreSQL 特有语法），
     * 避免 GROUP BY 需要把所有 metadata 字段都加进聚合。
     */
    public List<Map<String, Object>> listUploadedFiles(String userId) {
        String sql = """
                SELECT DISTINCT ON (metadata->>'source')
                       metadata->>'source' AS source,
                       metadata->>'extension' AS extension,
                       (metadata->>'fileSize')::bigint AS file_size,
                       (metadata->>'charCount')::bigint AS char_count,
                       (metadata->>'uploadedAt')::bigint AS uploaded_at,
                       metadata->>'status' AS status,
                       COUNT(*) OVER (PARTITION BY metadata->>'source') AS chunks
                FROM vector_store
                WHERE metadata->>'type' = 'user_upload'
                  AND metadata->>'userId' = ?
                ORDER BY metadata->>'source', id DESC
                """;
        List<Map<String, Object>> files = new ArrayList<>();
        jdbcTemplate.query(sql, ps -> ps.setString(1, userId), rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", rs.getString("source"));
            row.put("extension", rs.getString("extension"));
            row.put("fileSize", rs.getObject("file_size") != null ? rs.getLong("file_size") : 0L);
            row.put("charCount", rs.getObject("char_count") != null ? rs.getLong("char_count") : 0L);
            row.put("uploadedAt", rs.getObject("uploaded_at") != null ? rs.getLong("uploaded_at") : 0L);
            row.put("status", rs.getString("status"));
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

    /**
     * 批量删除用户的多个文件
     * @return 实际删除的文件数（source 个数，非 chunk 数）
     */
    public int batchDelete(List<String> sourceNames, String userId) {
        if (sourceNames == null || sourceNames.isEmpty()) return 0;
        int fileCount = 0;
        for (String name : sourceNames) {
            int deleted = deleteBySource(name, userId);
            if (deleted > 0) fileCount++;
        }
        log.info("batch deleted {} files for userId={}", fileCount, userId);
        return fileCount;
    }

    // ==================== Preview ====================

    /**
     * 预览文件内容：返回 charCount 与最多 PREVIEW_MAX_CHARS 字符的原文
     */
    public Map<String, Object> previewFile(String sourceName, String userId) {
        // 取该 source 下 id 最小的分块（即文档开头），合并多个 PREVIEW_MAX_CHARS 内的分块
        String sql = """
                SELECT metadata->>'charCount' AS char_count, content
                FROM vector_store
                WHERE metadata->>'source' = ?
                  AND metadata->>'userId' = ?
                  AND metadata->>'type' = 'user_upload'
                ORDER BY id ASC
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbcTemplate.query(sql, ps -> {
            ps.setString(1, sourceName);
            ps.setString(2, userId);
        }, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("charCount", rs.getString("char_count"));
            row.put("content", rs.getString("content"));
            rows.add(row);
        });

        if (rows.isEmpty()) {
            return null;
        }

        long charCount = 0L;
        Object cc = rows.get(0).get("charCount");
        if (cc != null) {
            charCount = Long.parseLong(cc.toString());
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            String c = (String) row.get("content");
            if (c == null) continue;
            if (sb.length() + c.length() > PREVIEW_MAX_CHARS) {
                sb.append(c, 0, PREVIEW_MAX_CHARS - sb.length());
                break;
            }
            sb.append(c);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", sourceName);
        result.put("charCount", charCount);
        result.put("preview", sb.toString());
        result.put("truncated", sb.length() < charCount);
        return result;
    }

    // ==================== Search Test ====================

    /**
     * 检索测试：在指定用户的知识库中搜索，返回命中分块列表
     * @param query  查询关键词
     * @param sourceName 可选，限定某个文件；null 表示全库检索
     * @return 命中列表，每项包含 source / score / snippet
     */
    public List<Map<String, Object>> searchTest(String query, String sourceName, String userId) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(SEARCH_TEST_TOP_K);
        // 多租户隔离：只检索当前用户的文档
        StringBuilder filter = new StringBuilder("userId == '" + userId + "'");
        if (sourceName != null && !sourceName.isBlank()) {
            filter.append(" AND source == '").append(sourceName.replace("'", "''")).append("'");
        }
        builder.filterExpression(filter.toString());

        List<Document> hits = pgVectorVectorStore.similaritySearch(builder.build());
        List<Map<String, Object>> results = new ArrayList<>();
        for (Document doc : hits) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", doc.getMetadata().getOrDefault("source", "unknown"));
            item.put("score", doc.getMetadata().getOrDefault("score", 0.0));
            // snippet：截取前 200 字
            String text = doc.getText();
            item.put("snippet", text.length() > 200 ? text.substring(0, 200) + "..." : text);
            results.add(item);
        }
        log.info("searchTest: query='{}', source='{}', userId={}, hits={}", query, sourceName, userId, results.size());
        return results;
    }

    // ==================== Util ====================

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) return "";
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}
