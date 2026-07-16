package com.light.imagesearchmcpserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片搜索结果的元数据结构。
 * MCP 工具内部使用，序列化为 JSON 嵌入 <<<IMG_START>>> / <<<IMG_END>>> 分隔符，
 * 供后端 ToolCallAgent 结构化提取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageInfo {

    /** 原图 URL（Pexels medium 尺寸） */
    private String url;

    /** 缩略图 URL（Pexels small 尺寸，前端优先加载） */
    private String thumbnailUrl;

    /** 图片宽度（px） */
    private Integer width;

    /** 图片高度（px） */
    private Integer height;

    /** 替代文本（Pexels 自带描述） */
    private String alt;
}
