package com.light.reactagent.tools;

import cn.hutool.core.io.FileUtil;
import com.light.reactagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.nio.file.Path;

/**
 * 文件操作工具类（提供文件读写功能，已做路径穿越防护）
 * <p>
 * 安全策略：所有读写都限制在 baseDir 沙箱目录内，传入的 fileName 经 normalize
 * 后校验是否仍在 baseDir 之下，越界（如 ../../application-local.yml）直接拒绝。
 */
public class FileOperationTool {

    /**
     * 文件读写基准目录（沙箱），所有文件操作只能在此目录内进行
     */
    private final File baseDir = new File(FileConstant.FILE_SAVE_DIR + "/file");

    @Tool(description = "Read content from a file. Only files within the workspace sandbox directory are accessible.")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        File target = resolveSafe(fileName);
        if (target == null) {
            return "拒绝访问：文件路径越界，只能访问工作区内的文件。";
        }
        if (!target.exists() || !target.isFile()) {
            return "文件不存在：" + fileName;
        }
        try {
            return FileUtil.readUtf8String(target);
        } catch (Exception e) {
            return "读取文件出错：" + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file. Only files within the workspace sandbox directory are writable.")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content
    ) {
        File target = resolveSafe(fileName);
        if (target == null) {
            return "拒绝访问：文件路径越界，只能写入工作区内的文件。";
        }
        try {
            FileUtil.mkdir(baseDir);
            FileUtil.writeUtf8String(content, target);
            // 出于安全考虑，不向调用方（LLM）暴露服务器绝对路径
            return "文件写入成功：" + target.getName();
        } catch (Exception e) {
            return "写入文件出错：" + e.getMessage();
        }
    }

    /**
     * 安全解析文件路径，防止路径穿越（如 ../../etc/passwd、..\\..\\application-local.yml）
     * <p>
     * 将用户传入的 fileName 相对 baseDir 解析并 normalize，再校验结果是否仍以 baseDir 为前缀。
     *
     * @param fileName 用户传入的文件名（可为相对子路径，如 "sub/a.txt"）
     * @return 安全的绝对路径 File 对象；越界或非法时返回 null
     */
    private File resolveSafe(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path basePath = baseDir.toPath().normalize();
        // new File(baseDir, fileName) 会把 fileName 当作相对路径拼接，再 normalize 消除 ..
        Path targetPath = new File(baseDir, fileName).toPath().normalize();
        if (!targetPath.startsWith(basePath)) {
            return null;
        }
        return targetPath.toFile();
    }
}
