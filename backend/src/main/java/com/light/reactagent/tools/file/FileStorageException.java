package com.light.reactagent.tools.file;

/**
 * 文件存储相关异常（大小超限等）。
 * <p>
 * 抛出后由工具层（FileOperationTool / PDFGenerationTool / ResourceDownloadTool）捕获，
 * 转换为对 LLM 的友好提示，使其停止继续生成，而非抛出裸异常中断流程。
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }
}
