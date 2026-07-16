package com.light.reactagent.agent;

/**
 * 工具结果中的结构化数据分隔符常量。
 * <p>
 * 某些工具（如 searchImage）的返回文本中嵌入了 JSON 结构，
 * 由分隔符标记起止位置，供 ToolCallAgent 定位并提取。
 * 分隔符使用高熵字符串，降低与模型自由输出碰撞的概率。
 */
public final class ToolResultMarkers {

    private ToolResultMarkers() {
    }

    /** 图片搜索结构化数据的起始标记 */
    public static final String IMG_START = "<<<IMG_START>>>";

    /** 图片搜索结构化数据的结束标记 */
    public static final String IMG_END = "<<<IMG_END>>>";

    /** 图片数据 JSON 数组中每个对象的字段名（与 ImageInfo DTO 保持一致） */
    public static final String FIELD_URL = "url";
    public static final String FIELD_THUMBNAIL_URL = "thumbnailUrl";
    public static final String FIELD_WIDTH = "width";
    public static final String FIELD_HEIGHT = "height";
    public static final String FIELD_ALT = "alt";
}
