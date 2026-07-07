package com.light.reactagent.tools.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件元数据 —— 记录一次文件生成操作的信息，供下载接口定位文件和前端展示用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    /**
     * 文件唯一 ID（UUID），下载接口赖以定位文件
     */
    private String fileId;

    /**
     * 原始文件名（展示用，如 report.pdf）
     */
    private String originalName;

    /**
     * 服务器上的存储文件名（UUID + 后缀，防路径穿越）
     */
    private String storageName;

    /**
     * 文件在服务器上的相对存储目录（相对于 FILE_SAVE_DIR）
     */
    private String storageDir;

    /**
     * 文件 MIME 类型（如 application/pdf、text/markdown）
     */
    private String contentType;

    /**
     * 文件扩展名（如 pdf、md）
     */
    private String extension;

    /**
     * 生成该文件的会话 ID（用于归属校验，防止 A 下载 B 的文件）
     */
    private String chatId;

    /**
     * 文件生成时间戳（毫秒）
     */
    private long createdAt;

    /**
     * 文件大小（字节）
     */
    private long sizeBytes;
}
