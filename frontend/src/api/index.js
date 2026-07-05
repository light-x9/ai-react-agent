import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 从 localStorage 读取 JWT（认证系统上线后，登录时存入 'token' 即自动生效）
const getToken = () => localStorage.getItem('token') || ''

/**
 * 连接 SSE 流（基于 fetch + ReadableStream）
 *
 * 为何弃用 EventSource：
 *  1. EventSource 只支持 GET，无法携带 Authorization header —— 会阻塞后续 JWT 认证
 *  2. GET 会把 message/history 拼进 URL，泄露进 Nginx 日志 / 浏览器历史，且受 URL 长度限制
 *
 * @param {string} url       接口路径（如 '/ai/manus/chat'）
 * @param {object} body      POST 请求体（JSON）
 * @param {function} onMessage 收到数据时回调，参数为解析出的 data 字符串；'[DONE]' 表示流结束
 * @param {function} onError   发生错误时回调
 * @returns {{ close: Function }} 兼容 EventSource 的句柄，close() 用于中断流
 */
export const connectSSE = (url, body, onMessage, onError) => {
  const controller = new AbortController()
  const fullUrl = API_BASE_URL + url

  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream'
  }
  const token = getToken()
  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }

  let receivedDone = false
  const dispatch = (data) => {
    if (data === '[DONE]') receivedDone = true
    if (onMessage) onMessage(data)
  }

  fetch(fullUrl, {
    method: 'POST',
    headers,
    body: JSON.stringify(body || {}),
    signal: controller.signal
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error('HTTP ' + response.status)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 事件以空行（\n\n）分隔，可能一次读到多个事件
      let idx
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const chunk = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        parseSSEChunk(chunk, dispatch)
      }
    }
    // 处理缓冲区尾部残留
    if (buffer.trim()) {
      parseSSEChunk(buffer, dispatch)
    }
    // 兜底：若后端未显式发送 [DONE] 就关闭流，补发一次确保 UI 状态收尾
    if (!receivedDone && onMessage) {
      onMessage('[DONE]')
    }
  }).catch((err) => {
    // 主动中断（close()）不算错误
    if (err && err.name === 'AbortError') return
    if (onError) onError(err)
  })

  return {
    close: () => controller.abort()
  }
}

/**
 * 解析单个 SSE 事件块，提取 data: 行内容并回调
 */
function parseSSEChunk(chunk, dispatch) {
  const lines = chunk.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (data) dispatch(data)
    }
  }
}

export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

// Upload a file to knowledge base
export const uploadKnowledgeBase = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post('/knowledge-base/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}

// List uploaded files in knowledge base
export const listKnowledgeFiles = async () => {
  const response = await request.get('/knowledge-base/files')
  return response.data
}

// Delete a file from knowledge base
export const deleteKnowledgeFile = async (sourceName) => {
  const response = await request.delete('/knowledge-base/files/' + encodeURIComponent(sourceName))
  return response.data
}

export default {
  chatWithManus,
  uploadKnowledgeBase,
  listKnowledgeFiles,
  deleteKnowledgeFile
}
