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
 * Knowledge base document service.
 * Handles file upload, parsing, chunking, vectorization, listing, and deletion.
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
     * Parse, split, embed and store an uploaded file into PGvector.
     */
    public String processAndStore(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("filename is empty");
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "unsupported file type: " + extension + ", only: " + ALLOWED_EXTENSIONS);
        }

        String fileContent = new String(file.getBytes(), "UTF-8");
        if (fileContent.isBlank()) {
            throw new IllegalArgumentException("file content is empty");
        }
        log.info("file read: {}, {} chars", originalFilename, fileContent.length());

        Document document = new Document(
                fileContent,
                Map.of(
                        "source", originalFilename,
                        "type", "user_upload",
                        "extension", extension
                )
        );

        List<Document> splitDocuments = myTokenTextSplitter.splitDocuments(List.of(document));
        log.info("split: {} -> {} chunks", originalFilename, splitDocuments.size());

        pgVectorVectorStore.add(splitDocuments);
        log.info("stored: {}, {} chunks in vector DB", originalFilename, splitDocuments.size());

        return "File uploaded: " + originalFilename + " -> " + splitDocuments.size() + " chunks stored";
    }

    // ==================== List ====================

    /**
     * List all user-uploaded files with their chunk counts.
     * Queries the PGvector table directly via metadata.
     */
    public List<Map<String, Object>> listUploadedFiles() {
        String sql = """
                SELECT metadata->>'source' AS source,
                       COUNT(*) AS chunks
                FROM vector_store
                WHERE metadata->>'type' = 'user_upload'
                GROUP BY metadata->>'source'
                ORDER BY source
                """;
        List<Map<String, Object>> files = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", rs.getString("source"));
            row.put("chunks", rs.getLong("chunks"));
            files.add(row);
        });
        log.info("listed {} uploaded files", files.size());
        return files;
    }

    // ==================== Delete ====================

    /**
     * Delete all chunks of a specific uploaded file by source name.
     */
    public int deleteBySource(String sourceName) {
        String sql = "DELETE FROM vector_store WHERE metadata->>'source' = ?";
        int deleted = jdbcTemplate.update(sql, sourceName);
        log.info("deleted {} chunks for source: {}", deleted, sourceName);
        return deleted;
    }

    // ==================== Util ====================

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) return "";
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}