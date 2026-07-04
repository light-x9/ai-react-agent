package com.light.reactagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图片搜索工具 —— 直接调用 Pexels API 搜索图片
 * 不需要通过 MCP Server，简化部署
 */
@Service
public class ImageSearchTool {

    // Pexels API Key，从环境变量注入
    @Value("${PEXELS_API_KEY}")
    private String pexelsApiKey;

    // Pexels 图片搜索接口
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "Search image from the web using Pexels")
    public String searchImage(
            @ToolParam(description = "Search query keyword, e.g. 'west lake hangzhou'") String query) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", pexelsApiKey);

            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", 5);

            String response = HttpUtil.createGet(API_URL)
                    .addHeaders(headers)
                    .form(params)
                    .execute()
                    .body();

            // 解析返回的图片 URL 列表
            List<String> imageUrls = JSONUtil.parseObj(response)
                    .getJSONArray("photos")
                    .stream()
                    .map(photoObj -> (JSONObject) photoObj)
                    .map(photoObj -> photoObj.getJSONObject("src"))
                    .map(src -> src.getStr("medium"))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());

            if (imageUrls.isEmpty()) {
                return "未找到与「" + query + "」相关的图片";
            }

            // 返回图片 URL 列表，网页访问后可点击
            return "找到 " + imageUrls.size() + " 张图片：\n" +
                    imageUrls.stream()
                            .map(url -> "- " + url)
                            .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "图片搜索失败：" + e.getMessage();
        }
    }
}