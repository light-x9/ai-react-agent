package com.light.reactagent.tools.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件元数据（JPA 实体，持久化到 file_metadata 表）
 * <p>
 * 记录一次文件生成操作的信息，供下载接口定位文件和前端展示用。
 * 改为数据库存储后，后端重启不再丢失元数据，多实例部署也能共享一致的归属信息。
 */
@Entity
@Table(name = "file_metadata", indexes = {
        @Index(name = "idx_file_metadata_chat", columnList = "chat_id"),
        @Index(name = "idx_file_metadata_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    /**
     * 文件唯一 ID（UUID，代码内生成），下载接口赖以定位文件
     */
    @Id
    @Column(name = "file_id", length = 64, nullable = false)
    private String fileId;

    /**
     * 原始文件名（展示用，如 report.pdf）
     */
    @Column(name = "original_name", length = 255)
    private String originalName;

    /**
     * 服务器上的存储文件名（原始文件名；路径穿越由 LocalFileStorageService 双重校验兜底）
     */
    @Column(name = "storage_name", length = 255, nullable = false)
    private String storageName;

    /**
     * 文件在服务器上的相对存储目录（相对于 FILE_SAVE_DIR）
     */
    @Column(name = "storage_dir", length = 64)
    private String storageDir;

    /**
     * 文件 MIME 类型（如 application/pdf、text/markdown）
     */
    @Column(name = "content_type", length = 128)
    private String contentType;

    /**
     * 文件扩展名（如 pdf、md）
     */
    @Column(name = "extension", length = 20)
    private String extension;

    /**
     * 生成该文件的会话 ID（用于归属校验，防止 A 下载 B 的文件）
     */
    @Column(name = "chat_id", length = 64, nullable = false)
    private String chatId;

    /**
     * 生成该文件的用户 ID（取自 JWT，下载时校验当前用户是否拥有该文件，防止越权下载）
     */
    @Column(name = "user_id", length = 100)
    private String userId;

    /**
     * 文件生成时间戳（毫秒）
     */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /**
     * 文件大小（字节）
     */
    @Column(name = "size_bytes")
    private long sizeBytes;
}
