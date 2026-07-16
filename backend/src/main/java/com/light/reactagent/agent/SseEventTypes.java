package com.light.reactagent.agent;

/**
 * SSE 事件类型常量 —— 统一前后端协议，替代散落各处的魔法字符串。
 * <p>
 * 一次 Agent 会话的事件流：
 * <pre>
 *   1. phase       : {"type":"phase","phase":"agent_start","mode":"webSearch","persona":{...},"toolCount":6}
 *   2. thought     : {"type":"thought","content":"..."}
 *   3. action      : {"type":"action","tool":"searchWeb","params":{...}}
 *   4. resource    : {"type":"resource","resourceType":"file","fileId":"xxx","name":"chart.json","size":1234,"fileType":"json"}
 *   5. image       : {"type":"image","images":[{"url":"...","thumbnailUrl":"...","width":800,"height":600,"alt":"..."}]}
 *   6. observation : {"type":"observation","summary":"..."}
 *   7. (重复 2~6 直到完成)
 *   8. final       : {"type":"final","content":"...","files":[...]}
 *   9. [DONE]      : 终止标记
 * </pre>
 */
public final class SseEventTypes {

    /** 阶段通知：Agent 启动时发送，携带模式/画像/工具集信息 */
    public static final String PHASE = "phase";
    /** 思考内容：LLM 推理过程的文字输出 */
    public static final String THOUGHT = "thought";
    /** 工具调用：即将执行的工具及其参数 */
    public static final String ACTION = "action";
    /** 资源产出：工具执行中产生的文件/图表（渐进推送，无需等到 final） */
    public static final String RESOURCE = "resource";
    /** 图片搜索结果：工具执行中检索到的图片元数据（渐进推送，前端渲染为图片卡片） */
    public static final String IMAGE = "image";
    /** 观察结果：工具执行完成后的摘要 */
    public static final String OBSERVATION = "observation";
    /** 最终回答：Agent 生成的最终文本答案，附带未推送过的文件 */
    public static final String FINAL = "final";
    /** 错误 */
    public static final String ERROR = "error";

    private SseEventTypes() {
    }
}
