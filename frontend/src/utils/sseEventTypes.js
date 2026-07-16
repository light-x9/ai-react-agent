/**
 * SSE 事件类型常量 —— 与后端 SseEventTypes.java 保持同步。
 * 替代 SuperAgent.vue 中散落的魔法字符串，统一前后端协议引用。
 *
 * 一次 Agent 会话的事件流：
 *   1. phase       : {type, phase, mode, persona, toolCount}
 *   2. thought     : {type, content}
 *   3. action      : {type, tool, params}
 *   4. resource    : {type, resourceType, fileId, name, size, fileType}
 *   5. image       : {type, images: [{url, thumbnailUrl, width, height, alt}]}
 *   6. observation : {type, summary}
 *   7. (重复 2~6 直到完成)
 *   8. final       : {type, content, files?}
 *   9. [DONE]      : 终止标记（非 JSON，纯文本行）
 */

export const SSE_TYPES = {
  PHASE: 'phase',
  THOUGHT: 'thought',
  ACTION: 'action',
  RESOURCE: 'resource',
  IMAGE: 'image',
  OBSERVATION: 'observation',
  FINAL: 'final',
  ERROR: 'error',
};
