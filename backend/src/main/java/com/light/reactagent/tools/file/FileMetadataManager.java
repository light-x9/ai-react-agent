package com.light.reactagent.tools.file;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文件元数据管理器（数据库持久化版）
 * <p>
 * 元数据持久化到 file_metadata 表（JPA），后端重启不再丢失，多实例部署也能共享一致的归属信息。
 * 文件物理 I/O 委托给 {@link FileStorageService}（当前为本地磁盘实现），便于后续接入对象存储。
 * 内含定时清理任务：超过 ttlHours 未下载的文件自动删除（防止磁盘爆满）。
 */
@Component
@Slf4j
public class FileMetadataManager {

    /**
     * 文件过期时长（小时）：超过该时间未下载的文件会被定时任务清理
     */
    private static final long FILE_TTL_HOURS = 24;

    /**
     * 清理任务执行间隔（小时）
     */
    private static final long CLEANUP_INTERVAL_HOURS = 6;

    private final FileMetadataRepository repository;
    private final FileStorageService storageService;
    private final FileStorageProperties storageProperties;

    private ScheduledExecutorService cleanupScheduler;

    @Autowired
    public FileMetadataManager(FileMetadataRepository repository, FileStorageService storageService,
                               FileStorageProperties storageProperties) {
        this.repository = repository;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
    }

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
        log.info("[FileMetadataManager] 初始化完成（DB 持久化版），文件过期时长={}h，清理间隔={}h",
                FILE_TTL_HOURS, CLEANUP_INTERVAL_HOURS);
    }

    @PreDestroy
    public void destroy() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
    }

    /**
     * 注册一个新文件，返回生成的 fileId（元数据落库）
     *
     * @param chatId       所属会话 ID
     * @param userId       生成文件的用户 ID（取自 JWT，下载时校验归属，防越权下载）
     * @param originalName 原始文件名（如 report.pdf）
     * @param storageDir   存储子目录（相对于 FILE_SAVE_DIR 的路径）
     * @param contentType  MIME 类型
     * @param sizeBytes    文件大小
     * @return 生成的 fileId（UUID）
     */
    public String registerFile(String chatId, String userId, String originalName, String storageDir,
                               String contentType, long sizeBytes) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        // 关键修复：物理存储文件名直接采用「去重后的原始文件名」，确保三处完全一致：
        //   写盘文件名（工具侧 resolveStorageName 解析后落盘）== 元数据 storageName（此处注册）== 下载查找名
        // 此前曾用 UUID 作存储名，但工具写盘仍用原始名 → 下载按 UUID 找文件 → 404。
        // 同会话下若已存在同名文件，resolveStorageName 会追加 (1)/(2)... 序号，
        // 由工具在写盘前解析并落盘到该名，从根本上避免同名物理文件互相静默覆盖（#3）。
        // 路径穿越风险由 LocalFileStorageService.resolveAbsolutePath（storageDir 不逃根 + storageName 不逃子目录
        // 双重校验）与工具侧 resolveSafe 共同兜底；保留 UUID 仅作 DB 主键 fileId（不可读、防猜），不影响物理名。
        String storageName = resolveStorageName(chatId, originalName);

        // —— 安全闸①：单文件大小上限 ——
        long maxBytes = storageProperties.getMaxSizeMb() * 1024 * 1024L;
        if (sizeBytes > maxBytes) {
            // 文件已被工具写入磁盘，超限则删除物理文件，避免残留占用空间
            storageService.delete(storageDir, storageName);
            log.warn("[FileMetadataManager] 拒绝注册：文件 {} 大小 {}B 超过单文件上限 {}B（chatId={}）",
                    originalName, sizeBytes, maxBytes, chatId);
            throw new FileStorageException("文件「" + originalName + "」大小为 "
                    + (sizeBytes / 1024 / 1024) + "MB，超过单文件上限 "
                    + storageProperties.getMaxSizeMb() + "MB，已拒绝生成");
        }

        // —— 安全闸②：单会话累计生成文件大小上限（防止 Agent ReAct 循环写爆磁盘）——
        long usedBytes = repository.findByChatId(chatId).stream()
                .mapToLong(FileMetadata::getSizeBytes).sum();
        long totalMaxBytes = storageProperties.getMaxTotalPerChatMb() * 1024 * 1024L;
        if (usedBytes + sizeBytes > totalMaxBytes) {
            log.warn("[FileMetadataManager] 拒绝注册：会话 {} 累计将达 {}B 超过上限 {}B",
                    chatId, usedBytes + sizeBytes, totalMaxBytes);
            throw new FileStorageException("当前会话已生成文件总量超过上限 "
                    + storageProperties.getMaxTotalPerChatMb() + "MB，已停止生成新文件");
        }

        FileMetadata metadata = FileMetadata.builder()
                .fileId(fileId)
                .originalName(originalName)
                .storageName(storageName)
                .storageDir(storageDir)
                .contentType(contentType)
                .extension(extensionOf(originalName))
                .chatId(chatId)
                .userId(userId)
                .createdAt(System.currentTimeMillis())
                .sizeBytes(sizeBytes)
                .build();

        repository.save(metadata);
        log.info("[FileMetadataManager] 注册文件(已落库): chatId={}, fileId={}, name={}", chatId, fileId, originalName);
        return fileId;
    }

    /**
     * 解析一个不会与当前会话已有文件冲突的存储文件名（#3 同名覆盖修复）。
     * <p>
     * 若 (chatId, originalName) 已被占用，则追加 (1)/(2)... 序号，直到找到一个空闲名。
     * 仅做 DB 查重，不落盘、不写元数据；工具在写盘前调用本方法得到最终名，
     * 保证物理文件名 == 注册的 storageName，从根本上避免同名文件互相静默覆盖。
     *
     * @param chatId       会话 ID
     * @param originalName 期望的文件名
     * @return 与当前会话不冲突的最终存储文件名
     */
    public String resolveStorageName(String chatId, String originalName) {
        String name = originalName;
        int dup = 1;
        while (repository.findByChatIdAndStorageName(chatId, name).isPresent()) {
            name = insertSuffix(originalName, dup++);
        }
        return name;
    }

    /**
     * 在文件名（含扩展名）的第 n 次冲突时插入序号，例如 report.pdf -> report(1).pdf
     */
    private static String insertSuffix(String fileName, int n) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + "(" + n + ")";
        }
        return fileName.substring(0, dot) + "(" + n + ")" + fileName.substring(dot);
    }

    /**
     * 从文件名提取扩展名（不含点，小写）；无扩展名或非法时返回空串
     */
    private static String extensionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 根据 chatId + fileId 获取文件元数据（归属校验内置：fileId 必须属于该 chatId）
     *
     * @param chatId 会话 ID
     * @param fileId 文件 ID
     * @return FileMetadata，不存在或不属于该 chatId 时返回 null
     */
    public FileMetadata getFile(String chatId, String fileId) {
        return repository.findByChatIdAndFileId(chatId, fileId).orElse(null);
    }

    /**
     * 根据 fileId 查找对应文件在存储后端的物理位置（绝对路径或 URL）
     *
     * @param chatId 会话 ID（归属校验）
     * @param fileId 文件 ID
     * @return 文件位置，不存在时返回 null
     */
    public String resolveAbsolutePath(String chatId, String fileId) {
        FileMetadata metadata = getFile(chatId, fileId);
        if (metadata == null) {
            return null;
        }
        return storageService.resolveAbsolutePath(metadata.getStorageDir(), metadata.getStorageName());
    }

    /**
     * 获取某个会话的所有文件元数据列表（供前端展示"历史生成文件"用）
     *
     * @param chatId 会话 ID
     * @return 文件元数据列表，无文件时返回空列表
     */
    public List<FileMetadata> listFiles(String chatId) {
        return new ArrayList<>(repository.findByChatId(chatId));
    }

    /**
     * 清理过期文件：删除超过 FILE_TTL_HOURS 的物理文件和元数据
     */
    private void cleanupExpiredFiles() {
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(FILE_TTL_HOURS);
        List<FileMetadata> expired = repository.findByCreatedAtBefore(cutoff);
        int removedCount = 0;

        for (FileMetadata removed : expired) {
            // 先删物理文件，再删元数据，避免元数据已删但文件残留
            storageService.delete(removed.getStorageDir(), removed.getStorageName());
            repository.delete(removed);
            removedCount++;
        }

        if (removedCount > 0) {
            log.info("[FileMetadataManager] 定时清理完成，删除 {} 个过期文件", removedCount);
        }
    }

}
