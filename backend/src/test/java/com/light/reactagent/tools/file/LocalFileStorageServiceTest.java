package com.light.reactagent.tools.file;

import cn.hutool.core.io.FileUtil;
import com.light.reactagent.constant.FileConstant;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证「下载解析路径 == 工具实际落盘路径」这一核心不变量。
 * <p>
 * 三个生成工具现在都把文件写到 FILE_SAVE_DIR/<sub>/<chatId>/<原始文件名>，
 * 并注册 storageDir="<sub>/<chatId>"、storageName="<原始文件名>"，
 * 因此 resolveAbsolutePath(storageDir, storageName) 必须精确还原该路径，否则下载 404。
 */
class LocalFileStorageServiceTest {

    private final LocalFileStorageService storageService = new LocalFileStorageService();

    private String expected(String sub, String chatId, String fileName) {
        return new File(FileConstant.FILE_SAVE_DIR + "/" + sub + "/" + chatId + "/" + fileName)
                .toPath().normalize().toFile().getAbsolutePath();
    }

    @Test
    void resolvesFileToolPathExactly() {
        String path = storageService.resolveAbsolutePath("file/abc123", "report.md");
        assertEquals(expected("file", "abc123", "report.md"), path);
    }

    @Test
    void resolvesPdfToolPathExactly() {
        String path = storageService.resolveAbsolutePath("pdf/abc123", "weather.pdf");
        assertEquals(expected("pdf", "abc123", "weather.pdf"), path);
    }

    @Test
    void resolvesResourceToolPathExactly() {
        String path = storageService.resolveAbsolutePath("download/abc123", "cat.png");
        assertEquals(expected("download", "abc123", "cat.png"), path);
    }

    @Test
    void rejectsPathTraversalInStorageName() {
        assertThrows(IllegalArgumentException.class,
                () -> storageService.resolveAbsolutePath("file/abc123", "../evil.txt"));
    }

    @Test
    void rejectsPathTraversalInStorageDir() {
        assertThrows(IllegalArgumentException.class,
                () -> storageService.resolveAbsolutePath("../other", "report.md"));
    }

    /**
     * 真实落盘验证：把一个文件写到「工具实际落盘路径」(FILE_SAVE_DIR/file/<chatId>/<name>)，
     * 再用下载解析器按 (storageDir="file/<chatId>", storageName=<name>) 定位，
     * 确认解析出的就是同一个真实文件且内容一致。这是下载链路能否打通的关键不变量。
     */
    @Test
    void resolvesToRealWrittenFileOnDisk() throws Exception {
        String storageDir = "file/e2e-chat";
        String storageName = "hello.md";
        String resolved = storageService.resolveAbsolutePath(storageDir, storageName);
        File file = new File(resolved);
        try {
            FileUtil.mkdir(file.getParentFile());
            FileUtil.writeUtf8String("# 端到端验证\n这是真实落盘的文件内容。", file);
            assertTrue(file.exists(), "工具落盘的文件必须存在");
            String content = FileUtil.readUtf8String(file);
            assertTrue(content.contains("真实落盘的文件内容"), "下载解析出的文件内容应与工具写入的一致");
        } finally {
            FileUtil.del(file);
            FileUtil.del(file.getParentFile());
        }
    }
}
