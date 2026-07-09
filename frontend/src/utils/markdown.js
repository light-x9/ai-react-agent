/**
 * 剥离 markdown 标记，将格式文本转为纯文本（用于思考面板的简洁展示）。
 *
 * 原本 ChatRoom.vue 与 ReActSteps.vue 各内联了一份逐字相同的实现（#2 去重），
 * 现统一抽取到此处，任一组件 import 使用即可。
 *
 * @param {string} text 含 markdown 标记的原始文本
 * @returns {string} 清理后的纯文本
 */
export function stripMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```[\s\S]*?```/g, '（代码）') // 代码块
    .replace(/`([^`]+)`/g, '$1') // 行内代码
    .replace(/\*\*([^*]+)\*\*/g, '$1') // 粗体
    .replace(/\*([^*]+)\*/g, '$1') // 斜体
    .replace(/^#{1,6}\s+/gm, '') // 标题
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1') // 链接
    .replace(/^[-*+]\s+/gm, '• ') // 列表
    .replace(/\n{2,}/g, '\n') // 多余空行
    .trim()
}
