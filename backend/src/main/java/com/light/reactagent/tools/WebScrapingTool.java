package com.light.reactagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具（LLM 需要时使用；searchWeb 已集成即时抓取，此工具为兜底补充）
 * <p>
 * 返回页面正文文本而非完整 HTML，避免 CSS/JS/导航栏噪声污染上下文。
 */
public class WebScrapingTool {

    /** 单次抓取正文字符数上限 */
    private static final int MAX_TEXT_LEN = 3000;

    private static final int TIMEOUT_MS = 8000;

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
            String text = doc.body() != null ? doc.body().text() : doc.text();
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() > MAX_TEXT_LEN) {
                text = text.substring(0, MAX_TEXT_LEN) + "...";
            }
            return text;
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
