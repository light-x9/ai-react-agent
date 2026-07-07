package com.light.reactagent.constant;

/**
 * 文件常量
 */
public interface FileConstant {

    /**
     * 文件保存目录
     * 默认值为用户目录下的 .react-agent/files（持久化，不受系统 tmp 清理影响）
     * 可通过环境变量 FILE_SAVE_DIR 覆盖
     */
    String FILE_SAVE_DIR = System.getenv().getOrDefault("FILE_SAVE_DIR",
            System.getProperty("user.home") + "/.react-agent/files");
}
