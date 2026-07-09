package com.light.reactagent.tools.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文件元数据仓库（持久化到 file_metadata 表）
 */
@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, String> {

    /**
     * 按 chatId + fileId 精确查询（归属校验：fileId 必须属于该 chatId）
     */
    Optional<FileMetadata> findByChatIdAndFileId(String chatId, String fileId);

    /**
     * 列出某会话下的全部文件元数据
     */
    List<FileMetadata> findByChatId(String chatId);

    /**
     * 按 chatId + 存储文件名精确查询（用于同会话同名去重，判断目标 storageName 是否已被占用）
     */
    Optional<FileMetadata> findByChatIdAndStorageName(String chatId, String storageName);

    /**
     * 查询早于指定时间戳的文件（供定时清理过期文件使用）
     */
    List<FileMetadata> findByCreatedAtBefore(long cutoff);

    /**
     * 按 chatId + fileId 删除
     */
    void deleteByChatIdAndFileId(String chatId, String fileId);
}
