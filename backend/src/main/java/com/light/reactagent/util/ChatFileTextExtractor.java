package com.light.reactagent.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 对话附件文本提取工具。
 * <p>
 * 仅做「读 → 提取 → 返回」，不持久化、不索引、不分块、不入向量库。
 * 目的：用户在一次对话中临时上传文件让 AI 读懂，下次再说就没了。
 * <p>
 * 支持格式：PDF、DOCX、XLSX、PPTX、DOC（旧 Office）、TXT/MD/CSV/JSON 等纯文本。
 */
public final class ChatFileTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ChatFileTextExtractor.class);

    /** 单文件上限 10 MB（与知识库 KnowledgeBaseService 保持一致） */
    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;

    /** 提取后文本最大字符数（约等于 ~20k token，足以覆盖大多数 10 页文档） */
    private static final int MAX_CHARS = 60_000;

    private ChatFileTextExtractor() {
        // 纯静态工具类
    }

    /**
     * 从 MultipartFile 提取纯文本。
     *
     * @param file Spring 上传进来的文件对象
     * @return 提取结果，包含原文（可能截断）及元数据；永不抛异常，格式失败时返回友好提示让 AI 告知用户
     */
    public static ExtractResult extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ExtractResult.failed("附件为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return ExtractResult.failed("附件超过 10MB 限制，无法读取");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "附件";
        String lower = filename.toLowerCase();

        try {
            String text;
            if (lower.endsWith(".pdf")) {
                text = extractPdf(file.getInputStream());
            } else if (lower.endsWith(".docx") || lower.endsWith(".xlsx")
                    || lower.endsWith(".pptx") || lower.endsWith(".doc")
                    || lower.endsWith(".xls") || lower.endsWith(".ppt")) {
                text = extractOffice(file.getInputStream());
            } else {
                // 纯文本家族：UTF-8 直读（txt/md/csv/json/xml/log/… 都靠这个兜底）
                text = new String(file.getBytes(), "UTF-8");
            }

            if (text == null || text.isBlank()) {
                return ExtractResult.failed("「" + filename + "」未提取到任何可读文本，可能是扫描件 PDF 或空文档");
            }
            text = text.strip();
            if (text.length() > MAX_CHARS) {
                return ExtractResult.truncated(filename, text.substring(0, MAX_CHARS), text.length(), MAX_CHARS);
            }
            return ExtractResult.success(filename, text);
        } catch (Exception e) {
            log.warn("ChatFileTextExtractor: 解析附件 {} 失败: {}", filename, e.getMessage());
            return ExtractResult.failed("无法解析「" + filename + "」: " + e.getMessage());
        }
    }

    private static String extractPdf(InputStream in) throws Exception {
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private static String extractOffice(InputStream in) throws Exception {
        // ExtractorFactory 全部是静态方法，按文件魔数自动识别 DOC/DOCX/XLS/XLSX/PPT 等
        try (org.apache.poi.extractor.POITextExtractor extractor
                = org.apache.poi.extractor.ExtractorFactory.createExtractor(in)) {
            return extractor.getText();
        }
    }

    /** 提取结果（不变对象） */
    public record ExtractResult(
            String filename,     // 原始文件名
            String text,         // 提取的原文（可能截断后只剩前 N 字）
            String status,       // ok | truncated | failed
            Integer originalLen, // 截断前长度（仅 truncated 时有值）
            Integer truncatedTo  // 截断后保留长度（仅 truncated 时有值）
    ) {
        public boolean ok() {
            return "ok".equals(status) || "truncated".equals(status);
        }

        public String buildAttachmentBlock() {
            if ("failed".equals(status)) {
                return "【附件 \"{filename}\" 解析失败：{text}】".replace("{filename}", filename).replace("{text}", text);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("【附件 \"").append(filename).append("\" 原文如下】\n");
            if ("truncated".equals(status)) {
                sb.append("（注：原文共 ").append(originalLen).append(" 字，已截取前 ").append(truncatedTo).append(" 字供参考）\n");
            }
            sb.append("<<<ATTACHMENT_START>>>");
            sb.append(text);
            sb.append("<<<ATTACHMENT_END>>>");
            return sb.toString();
        }

        static ExtractResult success(String filename, String text) {
            return new ExtractResult(filename, text, "ok", null, null);
        }

        static ExtractResult truncated(String filename, String text, int originalLen, int truncatedTo) {
            return new ExtractResult(filename, text, "truncated", originalLen, truncatedTo);
        }

        static ExtractResult failed(String reason) {
            // filename 当占位，text 字段里放失败原因
            return new ExtractResult("未知", reason, "failed", null, null);
        }
    }
}
