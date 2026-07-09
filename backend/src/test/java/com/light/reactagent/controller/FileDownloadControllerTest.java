package com.light.reactagent.controller;

import com.light.reactagent.tools.file.FileMetadata;
import com.light.reactagent.tools.file.FileMetadataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

/**
 * FileDownloadController 归属/越权校验单元测试（不加载 Spring 上下文，纯单元）。
 * 重点守护 P0 修复：当前登录用户必须等于文件归属用户，否则拒绝下载。
 *
 * 说明：FileMetadataManager 被 mock，因此本测试与"内存版 / DB 持久化版"实现解耦，
 * 仅验证 Controller 的归属校验分支逻辑（与存储后端无关）。
 */
public class FileDownloadControllerTest {

    private FileMetadataManager metadataManager;
    private FileDownloadController controller;
    private File tempFile;

    @BeforeEach
    void setUp() throws Exception {
        metadataManager = Mockito.mock(FileMetadataManager.class);
        controller = new FileDownloadController();
        // 直接注入 mock（无 Spring，反射绕过 @Autowired）
        var field = FileDownloadController.class.getDeclaredField("fileMetadataManager");
        field.setAccessible(true);
        field.set(controller, metadataManager);

        // 真实存在的物理文件，供"归属匹配 / 无主历史"用例验证下载成功
        tempFile = Files.createTempFile("dl-test", ".txt").toFile();
        tempFile.deleteOnExit();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    private void setUser(String userId) {
        SecurityContextHolder.clearContext();
        var auth = new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 构造一条文件元数据（fileId 同时用作存储名，便于测试） */
    private FileMetadata meta(String fileId, String userId) {
        return FileMetadata.builder()
                .fileId(fileId)
                .originalName(fileId + ".txt")
                .storageDir("file")
                .storageName(fileId + ".txt")
                .contentType("text/plain")
                .chatId("c1")
                .userId(userId)
                .sizeBytes(10)
                .build();
    }

    /** 越权：文件归属 userA，当前登录 userB → 必须 404 拒绝（校验在 resolveAbsolutePath 之前，故无需 mock 路径） */
    @Test
    void crossUserDownload_shouldBeRejected() {
        setUser("userB");
        when(metadataManager.getFile("c1", "fid1")).thenReturn(meta("fid1", "userA"));

        ResponseEntity<Resource> resp = controller.download("fid1", "c1");
        assertEquals(404, resp.getStatusCode().value(), "越权下载必须被拒绝(404)");
    }

    /** 归属匹配：当前用户 == 文件归属用户 → 通过越权校验，走到物理文件并返回 200 */
    @Test
    void ownerMatches_shouldAllowAndServe() {
        setUser("userA");
        when(metadataManager.getFile("c1", "fid2")).thenReturn(meta("fid2", "userA"));
        when(metadataManager.resolveAbsolutePath("c1", "fid2")).thenReturn(tempFile.getAbsolutePath());

        ResponseEntity<Resource> resp = controller.download("fid2", "c1");
        assertEquals(200, resp.getStatusCode().value(), "归属匹配应正常下载(200)");
    }

    /** 历史无主文件（userId 为空，改造前生成）：保持兼容放行，不触发越权 404 */
    @Test
    void legacyFileWithoutOwner_shouldNotBeBlockedByOwnership() {
        setUser("userA");
        when(metadataManager.getFile("c1", "fid3")).thenReturn(meta("fid3", null));
        when(metadataManager.resolveAbsolutePath("c1", "fid3")).thenReturn(tempFile.getAbsolutePath());

        ResponseEntity<Resource> resp = controller.download("fid3", "c1");
        assertEquals(200, resp.getStatusCode().value(), "无主历史文件应保持兼容放行");
    }

    /** 参数为空字符串（非 null）也应被拒绝，防止绕过 @RequestParam 的 null 校验 */
    @Test
    void blankParams_shouldBeRejected() {
        setUser("userA");
        ResponseEntity<Resource> resp = controller.download("", "c1");
        assertEquals(400, resp.getStatusCode().value(), "空 fileId 必须被拒绝(400)");
    }
}
