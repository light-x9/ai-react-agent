package com.light.imagesearchmcpserver.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.light.imagesearchmcpserver.dto.ImageInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

    // Pexels API Key，通过环境变量注入，避免硬编码泄露
    private static final String API_KEY = System.getenv("PEXELS_API_KEY");

    // Pexels 常规搜索接口（请以文档为准）
    private static final String API_URL = "https://api.pexels.com/v1/search";

    /** 结构化图片数据的起始分隔符 —— 供后端 ToolCallAgent 定位 JSON 数组 */
    private static final String IMG_START = "<<<IMG_START>>>";

    /** 结构化图片数据的结束分隔符 */
    private static final String IMG_END = "<<<IMG_END>>>";

    /** 单次搜索返回的图片数量（够前端展示，不过多浪费 token） */
    private static final int PER_PAGE = 5;

    @Tool(description = "search image from web, returns image URLs with metadata (thumbnail, dimensions, alt text)")
    public String searchImage(@ToolParam(description = "image search keyword") String query) {
        try {
            List<ImageInfo> images = searchImages(query);

            if (images.isEmpty()) {
                return "未找到与\"" + query + "\"相关的图片。";
            }

            // 序列化为 JSON 嵌入分隔符，供 ToolCallAgent 结构化提取
            String json = JSONUtil.toJsonStr(images);
            return String.format(
                    "搜索到 %d 张与\"%s\"相关的图片：\n" + IMG_START + "\n" + json + "\n" + IMG_END,
                    images.size(), query
            );
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 搜索图片并返回完整元数据列表（含原图、缩略图、宽高、alt）
     *
     * @param query 搜索关键词
     * @return 图片元数据列表
     */
    public List<ImageInfo> searchImages(String query) {
        // 设置请求头（包含API密钥）
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("per_page", PER_PAGE);

        // 发送 GET 请求
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        // 解析响应 JSON，提取每张图的完整元数据
        var photos = JSONUtil.parseObj(response).getJSONArray("photos");
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return photos.stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(this::parseImageInfo)
                .filter(info -> StrUtil.isNotBlank(info.getUrl()))
                .collect(Collectors.toList());
    }

    /**
     * 从 Pexels 单条 photo 对象解析出 ImageInfo
     * <p>
     * Pexels src 对象包含：original, large2x, large, medium, small, portrait, landscape, tiny
     */
    private ImageInfo parseImageInfo(JSONObject photo) {
        JSONObject src = photo.getJSONObject("src");
        return ImageInfo.builder()
                .url(src.getStr("medium"))
                .thumbnailUrl(src.getStr("small"))
                .width(photo.getInt("width"))
                .height(photo.getInt("height"))
                .alt(photo.getStr("alt"))
                .build();
    }
}
