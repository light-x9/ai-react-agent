package com.light.reactagent.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 层内部传递的图片数据对象。
 * 由 ToolCallAgent 从工具结果中提取，经 ReActAgent 推送到前端。
 * 与 MCP 工具层的 ImageInfo DTO 解耦，各自独立演化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageItem {

    /** 原图 URL */
    private String url;

    /** 缩略图 URL（前端优先加载） */
    private String thumbnailUrl;

    /** 图片宽度（px） */
    private Integer width;

    /** 图片高度（px） */
    private Integer height;

    /** 替代文本 */
    private String alt;
}
