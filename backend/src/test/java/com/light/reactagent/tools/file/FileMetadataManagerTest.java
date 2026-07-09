package com.light.reactagent.tools.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileMetadataManager 文件大小上限安全闸单元测试（不加载 Spring 上下文）
 */
@ExtendWith(MockitoExtension.class)
public class FileMetadataManagerTest {

    @Mock
    private FileMetadataRepository repository;

    @Mock
    private FileStorageService storageService;

    @Test
    void registerFile_shouldThrowWhenSingleFileExceedsLimit() {
        FileStorageProperties props = new FileStorageProperties();
        props.setMaxSizeMb(1); // 单文件上限 1MB
        props.setMaxTotalPerChatMb(500);

        FileMetadataManager manager = new FileMetadataManager(repository, storageService, props);

        long overSize = 2L * 1024 * 1024; // 2MB，超限
        FileStorageException ex = assertThrows(FileStorageException.class, () ->
                manager.registerFile("chat1", "user1", "big.pdf", "pdf", "application/pdf", overSize));

        assertTrue(ex.getMessage().contains("超过单文件上限"));
        // 超限应尝试删除已落盘的物理文件
        verify(storageService).delete(eq("pdf"), anyString());
        // 不应落库
        verify(repository, never()).save(any());
    }

    @Test
    void registerFile_shouldThrowWhenChatTotalExceedsLimit() {
        FileStorageProperties props = new FileStorageProperties();
        props.setMaxSizeMb(100);
        props.setMaxTotalPerChatMb(2); // 单会话累计上限 2MB

        FileMetadataManager manager = new FileMetadataManager(repository, storageService, props);

        // 该会话已有 1.5MB 文件
        List<FileMetadata> existing = new ArrayList<>();
        existing.add(FileMetadata.builder()
                .fileId("old").chatId("chat1").sizeBytes(1536L * 1024).build());
        when(repository.findByChatId("chat1")).thenReturn(existing);

        long newSize = 1L * 1024 * 1024; // 再 +1MB => 2.5MB > 2MB
        FileStorageException ex = assertThrows(FileStorageException.class, () ->
                manager.registerFile("chat1", "user1", "more.pdf", "pdf", "application/pdf", newSize));

        assertTrue(ex.getMessage().contains("会话已生成文件总量超过上限"));
        verify(repository, never()).save(any());
    }

    @Test
    void registerFile_shouldSucceedWithinLimits() {
        FileStorageProperties props = new FileStorageProperties();
        props.setMaxSizeMb(50);
        props.setMaxTotalPerChatMb(500);

        FileMetadataManager manager = new FileMetadataManager(repository, storageService, props);
        when(repository.findByChatId("chat1")).thenReturn(new ArrayList<>());

        String fileId = manager.registerFile("chat1", "user1", "ok.pdf", "pdf", "application/pdf", 1024);
        assertNotNull(fileId);
        verify(repository).save(any(FileMetadata.class));
    }
}
