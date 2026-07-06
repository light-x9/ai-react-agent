import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 请求拦截器：自动携带 JWT
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 响应拦截器：401 清登录态并跳登录页
request.interceptors.response.use(
  res => res,
  err => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  }
)

// 从 localStorage 读取 JWT（与 userStore.setAuth 写入的 key 一致）
const getToken = () => localStorage.getItem('token') || ''

/**
 * 连接 SSE 流（基于 fetch + ReadableStream）
 * 支持 POST body 与 Authorization header（JWT 认证）。
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
      // 401：token 失效，清登录态跳登录页
      if (response.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('username')
        window.location.href = '/login'
        return
      }
      throw new Error('HTTP ' + response.status)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let idx
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const chunk = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        parseSSEChunk(chunk, dispatch)
      }
    }
    if (buffer.trim()) {
      parseSSEChunk(buffer, dispatch)
    }
    if (!receivedDone && onMessage) {
      onMessage('[DONE]')
    }
  }).catch((err) => {
    if (err && err.name === 'AbortError') return
    if (onError) onError(err)
  })

  return {
    close: () => controller.abort()
  }
}

function parseSSEChunk(chunk, dispatch) {
  const lines = chunk.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (data) dispatch(data)
    }
  }
}

// ============ 认证接口 ============
export const login = async (username, password) => {
  const res = await request.post('/auth/login', { username, password })
  return res.data
}

export const register = async (username, password) => {
  const res = await request.post('/auth/register', { username, password })
  return res.data
}

export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

// ============ 知识库接口 ============
export const uploadKnowledgeBase = async (file, onProgress) => {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post('/knowledge-base/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
  return response.data
}

export const listKnowledgeFiles = async () => {
  const response = await request.get('/knowledge-base/files')
  return response.data
}

export const deleteKnowledgeFile = async (sourceName) => {
  const response = await request.delete('/knowledge-base/files/' + encodeURIComponent(sourceName))
  return response.data
}

// ============ 对话管理接口 ============
export const createConversation = async (title) => {
  const res = await request.post('/conversation', { title })
  return res.data
}
export const listConversations = async () => {
  const res = await request.get('/conversation')
  return res.data
}
export const deleteConversation = async (id) => {
  const res = await request.delete('/conversation/' + id)
  if (!res.data || res.data.success === false) {
    throw new Error(res.data?.message || '删除失败')
  }
  return res.data
}
export const getMessages = async (id) => {
  const res = await request.get('/conversation/' + id + '/messages')
  return res.data
}
export const saveMessage = async (conversationId, role, content) => {
  const res = await request.post('/conversation/' + conversationId + '/messages', { role, content })
  return res.data
}
export const renameConversation = async (id, title) => {
  const res = await request.put('/conversation/' + id, { title })
  return res.data
}

// ============ 用量额度接口 ============
export const getUsageToday = async () => {
  const res = await request.get('/usage/today')
  return res.data
}

export default {
  chatWithManus,
  uploadKnowledgeBase,
  listKnowledgeFiles,
  deleteKnowledgeFile,
  login,
  register,
  createConversation,
  listConversations,
  deleteConversation,
  getMessages,
  saveMessage,
  renameConversation,
  getUsageToday
}
