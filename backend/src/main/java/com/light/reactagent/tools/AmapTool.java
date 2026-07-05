package com.light.reactagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 高德地图工具 —— 直接调用高德开放平台 API
 * 提供天气查询、地点搜索等功能
 */
@Service
public class AmapTool {

    @Value("${amap.api-key}")
    private String amapApiKey;

    // 高德天气查询接口
    private static final String WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    // 高德POI搜索接口
    private static final String POI_URL = "https://restapi.amap.com/v3/place/text";

    /**
     * 查询指定城市的实时天气
     * @param city 城市名，如"杭州"、"北京"
     * @return 天气信息文本
     */
    @Tool(description = "查询城市实时天气（高德地图）。输入城市名如「杭州」「北京」。适用于「XX天气怎么样」类问题。")
    public String queryWeather(
            @ToolParam(description = "City name, e.g. 'Hangzhou', 'Beijing'") String city) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("key", amapApiKey);
            params.put("city", city);
            params.put("extensions", "base"); // base=实时天气, all=预报天气

            String response = HttpUtil.get(WEATHER_URL, params);
            JSONObject json = JSONUtil.parseObj(response);

            if (!"1".equals(json.getStr("status"))) {
                return "天气查询失败：" + json.getStr("info");
            }

            JSONArray lives = json.getJSONArray("lives");
            if (lives == null || lives.isEmpty()) {
                return "未找到「" + city + "」的天气信息";
            }

            JSONObject live = lives.getJSONObject(0);
            return String.format(
                    "📍 %s 实时天气\n🌡️ 温度：%s°C\n☁️ 天气：%s\n💨 风向：%s（风力%s级）\n💧 湿度：%s%%\n",
                    live.getStr("city"),
                    live.getStr("temperature"),
                    live.getStr("weather"),
                    live.getStr("winddirection"),
                    live.getStr("windpower"),
                    live.getStr("humidity")
            );
        } catch (Exception e) {
            return "天气查询失败：" + e.getMessage();
        }
    }

    /**
     * 搜索地点（POI），如餐厅、景点、医院等
     * @param keywords 搜索关键词，如"杭州西湖"
     * @param city 城市名（可选），限制搜索范围
     * @return 地点列表
     */
    @Tool(description = "搜索地点/周边设施（POI，高德地图）：可查地铁口、公交站、餐厅、景点、医院、酒店、ATM、停车场等。适用于「XX周围有什么」「XX附近有几个YY」「找附近的ZZ」类地理问题。")
    public String searchPoi(
            @ToolParam(description = "Search keywords, e.g. 'West Lake', 'restaurant'") String keywords,
            @ToolParam(description = "City name to narrow search, e.g. 'Hangzhou'. Optional, can be empty") String city) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("key", amapApiKey);
            params.put("keywords", keywords);
            params.put("offset", "5"); // 返回前5条结果
            if (city != null && !city.isBlank()) {
                params.put("city", city);
            }

            String response = HttpUtil.get(POI_URL, params);
            JSONObject json = JSONUtil.parseObj(response);

            if (!"1".equals(json.getStr("status"))) {
                return "地点搜索失败：" + json.getStr("info");
            }

            JSONArray pois = json.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                return "未找到与「" + keywords + "」相关的地点";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(pois.size()).append(" 个地点：\n");
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                sb.append(i + 1).append(". ").append(poi.getStr("name"))
                        .append("（").append(poi.getStr("type")).append("）\n")
                        .append("   地址：").append(poi.getStr("address")).append("\n");
                // 解析经纬度
                String location = poi.getStr("location");
                if (location != null) {
                    sb.append("   坐标：").append(location).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "地点搜索失败：" + e.getMessage();
        }
    }
}