/**
 * Markdown 渲染器 —— 将 LLM 输出的 Markdown 文本转为安全的 HTML。
 *
 * 与 stripMarkdown（剥除标记转纯文本）相反，本模块做的是"渲染"：
 * 保留格式信息，生成结构化 HTML，供 v-html 直接插入。
 *
 * 安全：所有输出经 DOMPurify 净化，阻断 <script>、事件处理器等 XSS 向量。
 *
 * @param {string} text - 含 Markdown 标记的原始文本
 * @returns {string} 净化后的 HTML 字符串
 */
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'

// ---- marked 配置：集成 highlight.js 代码高亮 ----
marked.setOptions({
  highlight(code, lang) {
    // 语言可识别则用对应语法，否则回退 plaintext（避免报错）
    const language = hljs.getLanguage(lang) ? lang : 'plaintext'
    return hljs.highlight(code, { language }).value
  },
  langPrefix: 'hljs language-',
  gfm: true,      // GitHub Flavored Markdown：表格、删除线、任务列表
  breaks: true    // 单换行转 <br>（LLM 输出常见硬换行）
})

// ---- DOMPurify 配置：允许链接新窗口打开 ----
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  // 所有 target="_blank" 链接强制加 rel="noopener noreferrer"
  if ('target' in node) {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

/**
 * 表格兜底：检测 marked 未识别为 table 的"伪表格"行（含 | 但无分隔行），
 * 手动将连续的多行 | 文本包裹成 <table> 结构，确保有基本样式。
 *
 * 匹配规则：
 * - 行首尾的 | 符号之间以 | 分隔 ≥ 2 个单元格
 * - 连续 ≥ 2 行满足上述条件
 * - 第一行作表头，后续行作数据行
 *
 * @param {string} html - marked 已解析的 HTML
 * @returns {string} 修复后的 HTML
 */
function fallbackTables(html) {
  // 匹配 <p> 标签内包含 | 的行（marked 把未识别的表格当段落处理）
  return html.replace(/<p>((?:[^<]*\|[^<]*\n?)+)<\/p>/g, (match, content) => {
    const lines = content.trim().split('\n').map(l => l.trim()).filter(Boolean)
    // 至少 2 行且每行有 ≥ 2 个 |
    if (lines.length < 2 || !lines.every(l => (l.match(/\|/g) || []).length >= 2)) {
      return match // 不是伪表格，原样返回
    }

    // 解析每行的单元格
    const rows = lines.map(line =>
      line.split('|').filter((_, i, arr) => i > 0 && i < arr.length - 1) // 去头尾空
        .map(cell => cell.trim()).filter(cell => cell.length > 0)
    )

    // 所有行单元格数一致才认为是合法表格
    const colCount = rows[0].length
    if (colCount < 2 || !rows.every(r => r.length === colCount)) {
      return match
    }

    // 第一行作 thead，其余作 tbody
    const headerCells = rows[0].map(c => `<th>${c}</th>`).join('')
    const bodyRows = rows.slice(1).map(row => {
      const cells = row.map(c => `<td>${c}</td>`).join('')
      return `<tr>${cells}</tr>`
    }).join('')

    return `<table><thead><tr>${headerCells}</tr></thead><tbody>${bodyRows}</tbody></table>`
  })
}

/**
 * Markdown → 安全 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  let rawHtml = marked.parse(text)
  rawHtml = fallbackTables(rawHtml)
  return DOMPurify.sanitize(rawHtml, {
    ADD_ATTR: ['target']   // 允许 target 属性通过
  })
}
