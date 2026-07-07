package com.light.reactagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.light.reactagent.constant.FileConstant;
import com.light.reactagent.tools.file.FileContextHolder;
import com.light.reactagent.tools.file.FileMetadataManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 生成工具
 * <p>
 * 支持接收 markdown 内容，生成带格式（标题层级、列表、代码块、引用）的 PDF 文件。
 * 使用内置中文字体 STSongStd-Light，能正确渲染中文。
 * <p>
 * 生成后自动注册元数据到 FileMetadataManager，返回结果中携带 fileId 供前端下载。
 */
public class PDFGenerationTool {

    private final FileMetadataManager fileMetadataManager;

    public PDFGenerationTool(FileMetadataManager fileMetadataManager) {
        this.fileMetadataManager = fileMetadataManager;
    }

    /**
     * PDF 文件写入基准目录（沙箱），所有 PDF 只能写入此目录
     */
    private final File pdfBaseDir = new File(FileConstant.FILE_SAVE_DIR + "/pdf");

    @Tool(description = "Generate a PDF file from markdown content. Supports headings, lists, code blocks, quotes, and Chinese text. Returns a fileId for download.", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF, e.g. report.pdf") String fileName,
            @ToolParam(description = "Markdown content to convert to PDF") String content) {
        // 安全校验：防止路径穿越（沙箱模式，与 FileOperationTool 一致）
        File target = resolveSafePdfPath(fileName);
        if (target == null) {
            return "PDF 生成失败：文件名非法或越界";
        }
        try {
            // 创建目录
            FileUtil.mkdir(pdfBaseDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(target.getAbsolutePath());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // 使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);

                // 解析 markdown 并渲染为格式化段落
                List<PdfParagraph> paragraphs = parseMarkdown(content);
                for (PdfParagraph pp : paragraphs) {
                    Paragraph p = new Paragraph(pp.text);
                    p.setFont(font);
                    applyStyle(p, pp.style);
                    document.add(p);
                }
            }

            // 注册元数据
            String result = "PDF 生成成功：" + target.getName();
            String chatId = FileContextHolder.getChatId();
            if (chatId != null) {
                if (target.exists()) {
                    String fileId = fileMetadataManager.registerFile(
                            chatId,
                            target.getName(),
                            "pdf",
                            "application/pdf",
                            target.length()
                    );
                    result += " [fileId=" + fileId + "]";
                    // 记录到本次请求的文件列表，供 final 事件汇总返回给前端
                    FileContextHolder.recordFileId(fileId);
                }
            }
            return result;
        } catch (IOException e) {
            return "生成 PDF 出错：" + e.getMessage();
        } catch (Exception e) {
            return "PDF 生成失败：" + e.getMessage();
        }
    }

    /**
     * 安全解析 PDF 文件路径，防止路径穿越
     * <p>
     * 将 fileName 相对 pdfBaseDir 解析并 normalize，确保结果仍在 pdfBaseDir 沙箱内。
     * 与 FileOperationTool.resolveSafe() 安全策略一致。
     *
     * @param fileName 传入的文件名
     * @return 安全的 File 对象；越界或非法时返回 null
     */
    private File resolveSafePdfPath(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        // 文件名中不允许出现路径分隔符（LLM 可能传入 sub/dir/file.pdf）
        if (containsPathSeparator(fileName)) {
            return null;
        }
        // 确保扩展名为 .pdf
        String normalizedName = fileName.toLowerCase().endsWith(".pdf") ? fileName : fileName + ".pdf";
        Path basePath = pdfBaseDir.toPath().normalize();
        Path targetPath = new File(pdfBaseDir, normalizedName).toPath().normalize();
        if (!targetPath.startsWith(basePath)) {
            return null;
        }
        return targetPath.toFile();
    }

    /**
     * 检查字符串是否包含路径分隔符（包括 URL 编码形式的常见绕过）
     */
    private boolean containsPathSeparator(String s) {
        return s.contains("/") || s.contains("\\") || s.contains("..")
                || s.contains("%2f") || s.contains("%2F")
                || s.contains("%5c") || s.contains("%5C");
    }

    // ---------- markdown 解析与样式渲染 ----------

    /**
     * 段落样式标记
     */
    private enum PdfStyle {
        HEADING1, HEADING2, HEADING3, PARAGRAPH, LIST_ITEM, CODE_BLOCK, QUOTE
    }

    /**
     * 带样式的段落
     */
    private record PdfParagraph(String text, PdfStyle style) {
    }

    /**
     * 简易 markdown 解析器：将 markdown 文本解析为带样式标记的段落列表
     * <p>
     * 支持：# ~ ###### 标题、- / * 列表、``` 代码块、> 引用、** 粗体（纯文本展示）、--- 分隔线
     */
    private List<PdfParagraph> parseMarkdown(String markdown) {
        List<PdfParagraph> result = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }

        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();

        for (String line : lines) {
            // 代码块边界
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    // 结束代码块
                    result.add(new PdfParagraph(codeBuffer.toString().trim(), PdfStyle.CODE_BLOCK));
                    codeBuffer.setLength(0);
                    inCodeBlock = false;
                } else {
                    // 开始代码块
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                codeBuffer.append(line).append("\n");
                continue;
            }

            // 空行跳过
            if (line.isBlank()) {
                continue;
            }

            // 分隔线
            if (line.trim().matches("^[-*_]{3,}$")) {
                result.add(new PdfParagraph("——————————————————————", PdfStyle.PARAGRAPH));
                continue;
            }

            // 标题
            if (line.startsWith("# ")) {
                result.add(new PdfParagraph(line.substring(2).trim(), PdfStyle.HEADING1));
                continue;
            } else if (line.startsWith("## ")) {
                result.add(new PdfParagraph(line.substring(3).trim(), PdfStyle.HEADING2));
                continue;
            } else if (line.startsWith("### ")) {
                result.add(new PdfParagraph(line.substring(4).trim(), PdfStyle.HEADING3));
                continue;
            }

            // 引用
            if (line.trim().startsWith(">")) {
                result.add(new PdfParagraph(line.trim().replaceFirst("^>\\s*", ""), PdfStyle.QUOTE));
                continue;
            }

            // 列表项
            if (line.trim().matches("^[-*+]\\s+.*")) {
                String item = line.trim().replaceFirst("^[-*+]\\s+", "• ");
                result.add(new PdfParagraph(item, PdfStyle.LIST_ITEM));
                continue;
            }

            // 普通段落（简单去除 ** 标记，避免乱码）
            String cleaned = line.replaceAll("\\*\\*(.+?)\\*\\*$", "$1").replaceAll("\\*(.+?)\\*", "$1");
            result.add(new PdfParagraph(cleaned, PdfStyle.PARAGRAPH));
        }
        return result;
    }

    /**
     * 将样式应用到 PDF 段落
     */
    private void applyStyle(Paragraph p, PdfStyle style) {
        switch (style) {
            case HEADING1 -> {
                p.setFontSize(20f);
                p.simulateBold();
                p.setMarginTop(16f);
                p.setMarginBottom(8f);
            }
            case HEADING2 -> {
                p.setFontSize(16f);
                p.simulateBold();
                p.setMarginTop(12f);
                p.setMarginBottom(6f);
            }
            case HEADING3 -> {
                p.setFontSize(13f);
                p.simulateBold();
                p.setMarginTop(10f);
                p.setMarginBottom(4f);
            }
            case LIST_ITEM -> {
                p.setFontSize(11f);
                p.setMarginLeft(16f);
                p.setMarginBottom(2f);
            }
            case CODE_BLOCK -> {
                p.setFontSize(9f);
                p.setMarginLeft(12f);
                p.setMarginRight(12f);
                p.setMarginTop(6f);
                p.setMarginBottom(6f);
                p.setPadding(8f);
                p.setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY);
            }
            case QUOTE -> {
                p.setFontSize(10f);
                p.simulateItalic();
                p.setMarginLeft(16f);
                p.setFontColor(com.itextpdf.kernel.colors.ColorConstants.GRAY);
            }
            case PARAGRAPH -> {
                p.setFontSize(11f);
                p.setMarginBottom(4f);
            }
        }
    }
}
