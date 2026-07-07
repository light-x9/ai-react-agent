package com.light.reactagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.light.reactagent.service.UsageService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具（Google，via Serper）
 * <p>
 * 单次调用 = "搜索 + 抓取前 N 个结果的页面正文"，直接返回给用户的是结构化可读文本。
 * 避免 LLM 需要主动再调 scrapeWebPage 才能拿到内容的二步调用不稳问题。
 * 每次调用计入用户每日联网搜索额度，超限则拒绝并提示。
 * <p>
 * Serper API 文档：https://serper.dev/api
 */
@Component
public class WebSearchTool {

    private static final String SERPER_API_URL = "https://google.serper.dev/search";

    /** 单次搜索抓取的最大结果条数 */
    private static final int MAX_RESULTS = 3;

    /** 每个 URL 抓取的正文最大字符数 */
    private static final int PAGE_TEXT_MAX_LEN = 500;

    /** 抓取网页时的超时时间（毫秒） */
    private static final int SCRAPE_TIMEOUT_MS = 5000;

    /** Serper API 超时（毫秒） */
    private static final int SERPER_TIMEOUT_MS = 10000;

    private final UsageService usageService;

    @Value("${serper.api-key}")
    private String apiKey;

    public WebSearchTool(UsageService usageService) {
        this.usageService = usageService;
    }

    @Tool(description = "Search for information from Google Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {

        // 额度检查：每次搜索调用 +1，超限拒绝
        String userId = currentUserId();
        if (userId != null && !usageService.checkAndIncrementWebSearch(userId)) {
            return "今日联网搜索已达上限，请明日再试，或改用知识库回答。";
        }

        try {
            // 构造 Serper POST 请求（JSON body + X-API-KEY header）
            JSONObject requestBody = JSONUtil.createObj();
            requestBody.set("q", query);
            requestBody.set("gl", "cn");       // 地理位置：中国
            requestBody.set("hl", "zh-cn");    // 语言：中文
            requestBody.set("num", MAX_RESULTS);

            Map<String, String> headers = new HashMap<>();
            headers.put("X-API-KEY", apiKey);
            headers.put("Content-Type", "application/json");

            String response = HttpUtil.createPost(SERPER_API_URL)
                    .addHeaders(headers)
                    .body(requestBody.toString())
                    .timeout(SERPER_TIMEOUT_MS)
                    .execute()
                    .body();

            // 解析 Serper 返回的 organic 结果
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic");
            if (organicResults == null || organicResults.isEmpty()) {
                return "未搜索到相关结果";
            }
            List<JSONObject> topItems = organicResults.subList(0, Math.min(MAX_RESULTS, organicResults.size()))
                    .stream().map(o -> (JSONObject) o).collect(Collectors.toList());

            // 对每条结果：抓取页面正文，整合后直接返回可读文本
            StringBuilder sb = new StringBuilder();
            for (JSONObject item : topItems) {
                String title = item.getStr("title", "");
                String link = item.getStr("link", "");
                String snippet = item.getStr("snippet", "");
                sb.append("【").append(title).append("】\n");
                sb.append("URL: ").append(link).append("\n");
                sb.append("摘要: ").append(snippet).append("\n");

                // 抓取正文，失败就降级只展示摘要
                String pageText = fetchPageText(link);
                if (pageText != null) {
                    sb.append("正文: ").append(pageText).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error searching Google: " + e.getMessage();
        }
    }

    /**
     * 抓取 URL 的页面正文文本
     *
     * @param url 页面链接
     * @return 正文字符串（已截断）；任意失败返回 null，由调用方降级展示摘要兜底
     */
    private String fetchPageText(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(SCRAPE_TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
            String text = doc.body() != null ? doc.body().text() : doc.text();
            // 截断空白 + 长度控制
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() > PAGE_TEXT_MAX_LEN) {
                text = text.substring(0, PAGE_TEXT_MAX_LEN) + "...";
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从安全上下文取当前用户名（Agent 异步线程已传播 SecurityContext）
     */
    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }
}
