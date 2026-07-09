package com.light.reactagent.tools.file;

import com.light.reactagent.constant.FileConstant;

import java.io.File;

/**
 * 文件工具共享辅助方法。
 * <p>
 * 三个文件工具（FileOperationTool / PDFGenerationTool / ResourceDownloadTool）原本各自内联了一份
 * 逐字相同的 {@code resolveBaseDir()} + {@code chatSub()}，仅子目录前缀（file / pdf / download）不同。
 * 此处集中为共享静态实现，消除重复，后续调整沙箱根目录或兜底策略只需改一处。
 */
public final class FileToolSupport {

    private FileToolSupport() {
    }

    /**
     * 取当前会话隔离子目录名（无 chatId 时兜底 "default"）
     */
    public static String chatSub() {
        String chatId = FileContextHolder.getChatId();
        return (chatId == null || chatId.isBlank()) ? "default" : chatId;
    }

    /**
     * 文件读写基准目录（沙箱）：FILE_SAVE_DIR/&lt;subType&gt;/&lt;chatId&gt;
     *
     * @param subType 子目录类型（file / pdf / download）
     */
    public static File resolveBaseDir(String subType) {
        return new File(FileConstant.FILE_SAVE_DIR + "/" + subType + "/" + chatSub());
    }
}
