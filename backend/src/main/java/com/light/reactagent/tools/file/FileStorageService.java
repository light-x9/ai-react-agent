package com.light.reactagent.tools.file;

/**
 * 文件存储抽象层
 * <p>
 * 将"文件物理定位 / 删除"从业务代码中解耦，便于后续接入对象存储（OSS / MinIO / S3）。
 * 当前默认实现为 {@link LocalFileStorageService}（本地磁盘）。
 * 接入对象存储时只需新增一个实现类并切换 Spring Bean 即可，业务侧（Manager / Controller）无需改动。
 */
public interface FileStorageService {

    /**
     * 解析文件在存储后端的位置
     * <ul>
     *   <li>本地盘：返回绝对路径（FILE_SAVE_DIR/storageDir/storageName）</li>
     *   <li>对象存储（未来）：返回签名下载 URL 或预签名路径，由调用方自行处理</li>
     * </ul>
     *
     * @param storageDir   相对存储目录（如 file / pdf / download）
     * @param storageName  存储文件名（原始文件名，与工具落盘名一致）
     * @return 文件位置（绝对路径或 URL）
     */
    String resolveAbsolutePath(String storageDir, String storageName);

    /**
     * 删除指定文件
     *
     * @param storageDir   相对存储目录
     * @param storageName  存储文件名
     * @return 是否删除成功（文件不存在也视为成功，避免因元数据已删而反复失败）
     */
    boolean delete(String storageDir, String storageName);
}
