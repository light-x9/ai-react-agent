package com.light.reactagent.tools.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储相关安全阈值配置
 * <p>
 * 通过 application.yml 的 {@code lightmanus.file.*} 绑定，用于防止 Agent 异常循环写爆磁盘。
 */
@Component
@ConfigurationProperties(prefix = "lightmanus.file")
public class FileStorageProperties {

    /** 单文件大小上限（MB） */
    private long maxSizeMb = 50;

    /** 单会话累计生成文件大小上限（MB） */
    private long maxTotalPerChatMb = 500;

    public long getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(long maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    public long getMaxTotalPerChatMb() {
        return maxTotalPerChatMb;
    }

    public void setMaxTotalPerChatMb(long maxTotalPerChatMb) {
        this.maxTotalPerChatMb = maxTotalPerChatMb;
    }
}
