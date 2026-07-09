package com.light.reactagent.tools.file;

import com.light.reactagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 本地磁盘文件存储实现（默认）
 * <p>
 * 沿用既有的 FILE_SAVE_DIR 目录结构。接入对象存储时新增对应实现并替换本 Bean 即可。
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    @Override
    public String resolveAbsolutePath(String storageDir, String storageName) {
        File root = new File(FileConstant.FILE_SAVE_DIR).toPath().normalize().toFile();
        // 先规范化预期的子目录 FILE_SAVE_DIR/<storageDir>
        File intendedDir = new File(FileConstant.FILE_SAVE_DIR, storageDir).toPath().normalize().toFile();
        // 防御①：storageDir 本身不得逃逸根目录
        if (!intendedDir.toPath().startsWith(root.toPath())) {
            throw new IllegalArgumentException("非法文件存储目录（疑似路径穿越）：" + intendedDir.getPath());
        }
        // 在预期子目录内拼接文件名并规范化
        File resolved = new File(intendedDir, storageName).toPath().normalize().toFile();
        // 防御②：解析结果必须仍在预期子目录内（拦截 storageName 含 ../ 逃出子目录）
        if (!resolved.toPath().startsWith(intendedDir.toPath())) {
            throw new IllegalArgumentException("非法文件存储路径（疑似路径穿越）：" + resolved.getPath());
        }
        return resolved.getAbsolutePath();
    }

    @Override
    public boolean delete(String storageDir, String storageName) {
        String path = resolveAbsolutePath(storageDir, storageName);
        boolean deleted = new File(path).delete();
        if (!deleted) {
            log.warn("[LocalFileStorageService] 删除失败或文件不存在：{}", path);
        }
        return deleted;
    }
}
