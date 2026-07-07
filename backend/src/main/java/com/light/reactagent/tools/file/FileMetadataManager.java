package com.light.reactagent.tools.file;

import com.light.reactagent.constant.FileConstant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文件元数据管理器（内存版）
 * <p>
 * 按 chatId 分组存储文件元数据，供下载接口定位文件和归属校验。
 * 内含定时清理任务：超过 ttlHours 未下载的文件自动删除（防止磁盘爆满）。
 * <p>
 * 上线初期够用；用户量增长后建议改为数据库存储 + 对象存储（如 OSS）。
 */
@Component
@Slf4j
public class FileMetadataManager {

    /**
     * 文件元数据存储：chatId -> (fileId -> FileMetadata)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, FileMetadata>> store = new ConcurrentHashMap<>();

    /**
     * 文件过期时长（小时）：超过该时间未下载的文件会被定时任务清理
     */
    private static final long FILE_TTL_HOURS = 24;

    /**
     * 清理任务执行间隔（小时）
     */
    private static final long CLEANUP_INTERVAL_HOURS = 6;

    private ScheduledExecutorService cleanupScheduler;

    @PostConstruct
    public void init() {
        // 定时清理过期文件（每 CLEANUP_INTERVAL_HOURS 执行一次）
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "file-metadata-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredFiles,
                CLEANUP_INTERVAL_HOURS, CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);
        log.info("[FileMetadataManager] 初始化完成，文件过期时长={}h，清理间隔={}h", FILE_TTL_HOURS, CLEANUP_INTERVAL_HOURS);
    }

    @PreDestroy
    public void destroy() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
    }

    /**
     * 注册一个新文件，返回生成的 fileId
     *
     * @param chatId       所属会话 ID
     * @param originalName 原始文件名（如 report.pdf）
     * @param storageDir   存储子目录（相对于 FILE_SAVE_DIR 的路径）
     * @param contentType  MIME 类型
     * @param sizeBytes    文件大小
     * @return 生成的 fileId（UUID）
     */
    public String registerFile(String chatId, String originalName, String storageDir,
                               String contentType, long sizeBytes) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String extension = getExtension(originalName);
        // 存储文件名用 UUID，避免路径穿越和文件名冲突
        String storageName = fileId + (extension.isEmpty() ? "" : "." + extension);

        FileMetadata metadata = FileMetadata.builder()
                .fileId(fileId)
                .originalName(originalName)
                .storageName(storageName)
                .storageDir(storageDir)
                .contentType(contentType)
                .extension(extension)
                .chatId(chatId)
                .createdAt(System.currentTimeMillis())
                .sizeBytes(sizeBytes)
                .build();

        store.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>()).put(fileId, metadata);
        log.info("[FileMetadataManager] 注册文件: chatId={}, fileId={}, name={}", chatId, fileId, originalName);
        return fileId;
    }

    /**
     * 根据 chatId + fileId 获取文件元数据（归属校验内置：fileId 必须属于该 chatId）
     *
     * @param chatId 会话 ID
     * @param fileId 文件 ID
     * @return FileMetadata，不存在或不属于该 chatId 时返回 null
     */
    public FileMetadata getFile(String chatId, String fileId) {
        Map<String, FileMetadata> chatFiles = store.get(chatId);
        if (chatFiles == null) {
            return null;
        }
        return chatFiles.get(fileId);
    }

    /**
     * 根据 fileId 查找对应文件在服务器上的绝对路径
     *
     * @param chatId 会话 ID（归属校验）
     * @param fileId 文件 ID
     * @return 文件绝对路径，不存在时返回 null
     */
    public String resolveAbsolutePath(String chatId, String fileId) {
        FileMetadata metadata = getFile(chatId, fileId);
        if (metadata == null) {
            return null;
        }
        return FileConstant.FILE_SAVE_DIR + "/" + metadata.getStorageDir() + "/" + metadata.getStorageName();
    }

    /**
     * 获取某个会话的所有文件元数据列表（供前端展示"历史生成文件"用）
     *
     * @param chatId 会话 ID
     * @return 文件元数据列表，无文件时返回空列表
     */
    public List<FileMetadata> listFiles(String chatId) {
        Map<String, FileMetadata> chatFiles = store.get(chatId);
        if (chatFiles == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(chatFiles.values());
    }

    /**
     * 清理过期文件：删除超过 FILE_TTL_HOURS 的物理文件和元数据
     */
    private void cleanupExpiredFiles() {
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(FILE_TTL_HOURS);
        int removedCount = 0;

        for (Map<String, FileMetadata> chatFiles : store.values()) {
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, FileMetadata> entry : chatFiles.entrySet()) {
                if (entry.getValue().getCreatedAt() < cutoff) {
                    toRemove.add(entry.getKey());
                }
            }
            for (String fileId : toRemove) {
                FileMetadata removed = chatFiles.remove(fileId);
                if (removed != null) {
                    // 删除物理文件
                    String path = FileConstant.FILE_SAVE_DIR + "/" + removed.getStorageDir() + "/" + removed.getStorageName();
                    boolean deleted = new File(path).delete();
                    if (deleted) {
                        removedCount++;
                    }
                }
            }
        }

        if (removedCount > 0) {
            log.info("[FileMetadataManager] 定时清理完成，删除 {} 个过期文件", removedCount);
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
