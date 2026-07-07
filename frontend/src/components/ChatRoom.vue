<template>
  <div class="chat-container">
    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer">
            <div v-if="!hasUserMessages" class="welcome-panel">
        <div class="empty-brand" aria-hidden="true">
          <svg width="36" height="36" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="18" cy="11" r="5.5" stroke="#6366f1" stroke-width="1.5" fill="none" opacity="0.4" />
            <circle cx="10.5" cy="25" r="5.5" stroke="#6366f1" stroke-width="1.5" fill="none" opacity="0.4" />
            <circle cx="25.5" cy="25" r="5.5" stroke="#6366f1" stroke-width="1.5" fill="none" opacity="0.4" />
            <circle cx="18" cy="19" r="2" fill="#6366f1" opacity="0.6">
              <animate attributeName="opacity" values="0.4;0.8;0.4" dur="3s" repeatCount="indefinite" />
            </circle>
          </svg>
        </div>
        <p class="empty-tagline">试试这样问：</p>
        <div class="suggestions">
          <button
            v-for="q in suggestedQuestions"
            :key="q"
            class="suggestion-chip"
            @click="handleSuggestionClick(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>  <!-- 消息列表 -->
      <template v-else>
        <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
          <!-- AI 消息 -->
          <div v-if="!msg.isUser" class="message ai-message">
            <div class="avatar ai-avatar">
              <AiAvatarFallback :type="aiType" />
            </div>
            <div class="ai-message-body">
              <ReActSteps
                v-if="msg.reactCycles && msg.reactCycles.length > 0"
                :cycles="msg.reactCycles"
                :final-answer="msg.finalAnswer"
              />
              <div v-else class="message-bubble">
                <div class="message-content">
                  {{ msg.content }}
                  <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-indicator">▋</span>
                </div>
              </div>
              <div class="message-time">{{ formatTime(msg.time) }}</div>
              <!-- 文件下载卡片：AI 生成文件后展示 -->
              <transition-group v-if="msg.files && msg.files.length > 0" name="file-card" tag="div" class="file-cards">
                <div v-for="file in msg.files" :key="file.fileId" class="file-card">
                  <div class="file-card-icon" :class="'file-type-' + (file.type || 'default')">
                    <svg v-if="file.type === 'pdf'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <path d="M9 15l2 2 4-4"/>
                    </svg>
                    <svg v-else-if="file.type === 'md' || file.type === 'markdown' || file.type === 'txt'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <line x1="16" y1="13" x2="8" y2="13"/>
                      <line x1="16" y1="17" x2="8" y2="17"/>
                    </svg>
                    <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
                      <polyline points="13 2 13 9 20 9"/>
                    </svg>
                  </div>
                  <div class="file-card-info">
                    <div class="file-card-name" :title="file.name">{{ file.name }}</div>
                    <div class="file-card-size">{{ formatFileSize(file.size) }}</div>
                  </div>
                  <button
                    class="file-card-btn"
                    :disabled="downloadingFileId === file.fileId"
                    @click="downloadFile(file, msg.time)"
                    :title="'下载 ' + file.name"
                  >
                    <span v-if="downloadingFileId === file.fileId" class="file-btn-spinner"></span>
                    <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                      <polyline points="7 10 12 15 17 10"/>
                      <line x1="12" y1="15" x2="12" y2="3"/>
                    </svg>
                  </button>
                </div>
              </transition-group>
              <!-- 下载错误提示 -->
              <transition name="fade">
                <div v-if="downloadError && downloadError.messageId === msg.time" class="file-download-error">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                  {{ downloadError.text }}
                </div>
              </transition>
            </div>
          </div>

          <!-- 用户消息 -->
          <div v-else class="message user-message">
            <div class="message-bubble">
              <div class="message-content">{{ msg.content }}</div>
              <div class="message-time">{{ formatTime(msg.time) }}</div>
            </div>
            <div class="avatar user-avatar">
              <div class="avatar-inner">我</div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-container">
      <!-- 配额达上限提示横幅 -->
      <transition name="fade">
        <div v-if="quotaReached" class="quota-banner">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>今日对话已达上限，请明日再试</span>
        </div>
      </transition>
      <!-- 能力开关条（toggle，持续生效） -->
      <div class="quick-cap-bar">
        <button
          v-for="cap in capabilities"
          :key="cap.key"
          :class="['quick-cap-btn', { active: activeCaps[cap.key] }]"
          :title="cap.desc"
          @click="toggleCap(cap.key)"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" :stroke="activeCaps[cap.key] ? '#fff' : cap.color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
          <span>{{ cap.name }}</span>
        </button>
        <span v-if="activeCaps.webSearch || activeCaps.knowledgeBase" class="cap-hint">已开启能力，再次点击关闭</span>
      </div>

      <div class="chat-input">
        <textarea
          ref="inputEl"
          v-model="inputMessage"
          @keydown.enter="handleEnter"
          @input="autoResize"
          :placeholder="inputPlaceholder"
          class="input-box"
          :disabled="connectionStatus === 'connecting' || quotaReached"
          rows="1"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-button"
          :class="{ active: inputMessage.trim() }"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim() || quotaReached"
          aria-label="发送消息"
        >
          <svg v-if="connectionStatus !== 'connecting'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
          <span v-else class="send-spinner" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'
import ReActSteps from './ReActSteps.vue'
import { downloadFile as downloadFileApi } from '@/api'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'default'
  },
  chatId: {
    type: String,
    default: ''
  },
  quotaReached: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send-message', 'capability-change'])

const inputMessage = ref('')
const messagesContainer = ref(null)
const inputEl = ref(null)

// 能力开关（toggle，持续生效直到关闭）
const capabilities = [
  {
    key: 'webSearch',
    name: '网页搜索',
    desc: '实时查询互联网信息',
    color: '#4f46e5',
    bg: 'rgba(79,70,229,0.08)'
  },
  {
    key: 'knowledgeBase',
    name: '知识库',
    desc: '检索已上传的文档',
    color: '#0891b2',
    bg: 'rgba(8,145,178,0.08)'
  }
]

const activeCaps = reactive({
  webSearch: false,
  knowledgeBase: false
})

const toggleCap = (key) => {
  activeCaps[key] = !activeCaps[key]
  emit('capability-change', { ...activeCaps })
}

/**
 * 题库：覆盖联网搜索 / 知识问答 / 代码生成 / 创意科普
 * 每次组件挂载时随机抽取 4 条展示
 */
const QUESTION_POOL = [
  // 联网搜索
  '帮我搜索今天的 AI 新闻',
  '今天有什么热门科技资讯',
  '最近比特币价格怎么样',
  '今天北京的天气如何',
  // 知识问答
  '解释一下什么是 MCP 协议',
  '总结一下 ReAct 推理的原理',
  '什么是大语言模型的幻觉问题',
  '解释一下量子计算的基本原理',
  '区块链的工作原理是什么',
  // 代码生成
  '用 JavaScript 实现一个防抖函数',
  '帮我写一个 Python 爬虫',
  '写一个 Docker Compose 配置',
  '帮我写一个 Vue 3 组件示例',
  // 创意 / 科普
  '用通俗的方式解释什么是神经网络',
  '帮我写一首关于 AI 的诗',
  'React 和 Vue 有哪些主要区别',
]

const suggestedQuestions = ref([])

onMounted(() => {
  // Fisher-Yates 洗牌后取前 4
  const shuffled = [...QUESTION_POOL]
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
  }
  suggestedQuestions.value = shuffled.slice(0, 4)
})

const hasUserMessages = computed(() => props.messages.some(m => m.isUser))

const inputPlaceholder = computed(() => {
  if (props.quotaReached) return '今日对话已达上限，请明日再试'
  if (props.connectionStatus === 'connecting') return 'AI 正在思考中...'
  return '输入消息，Enter 发送，Shift+Enter 换行'
})

const handleSuggestionClick = (question) => {
  // 自动开启联网搜索，不再弹出"需要开启联网"的提示
  if (!activeCaps.webSearch) {
    activeCaps.webSearch = true
    emit('capability-change', { ...activeCaps })
  }
  emit('send-message', question)
}

// Enter 发送，Shift+Enter 换行
const handleEnter = (e) => {
  if (e.shiftKey) {
    // 允许换行，不阻止默认行为
    return
  }
  e.preventDefault()
  sendMessage()
}

// textarea 自动扩高
const autoResize = () => {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
  // 重置高度
  if (inputEl.value) inputEl.value.style.height = 'auto'
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// ---------- 文件下载 ----------
const downloadingFileId = ref(null)    // 当前正在下载的 fileId（loading 状态）
const downloadError = ref(null)        // 下载错误信息 { messageId, text }

/**
 * 格式化文件大小为人类可读字符串
 */
const formatFileSize = (bytes) => {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/**
 * 下载 AI 生成的文件
 * @param {Object} file - 文件信息 { fileId, name, size, type }
 * @param {number} messageId - 消息的时间戳（用于定位错误提示到具体消息）
 */
const downloadFile = async (file, messageId) => {
  if (!file || !file.fileId) return
  downloadingFileId.value = file.fileId
  downloadError.value = null
  try {
    const result = await downloadFileApi(file.fileId, props.chatId)
    if (!result.success) {
      showDownloadError(messageId, result.error || '下载失败')
      return
    }
    // 触发浏览器下载
    const url = URL.createObjectURL(result.blob)
    const a = document.createElement('a')
    a.href = url
    a.download = result.fileName || file.name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (err) {
    showDownloadError(messageId, '下载出错：' + (err.message || '未知错误'))
  } finally {
    downloadingFileId.value = null
  }
}

/**
 * 显示下载错误提示（3 秒后自动消失）
 */
const showDownloadError = (messageId, text) => {
  downloadError.value = { messageId, text }
  setTimeout(() => {
    if (downloadError.value && downloadError.value.messageId === messageId) {
      downloadError.value = null
    }
  }, 3000)
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(
  () => props.messages.map(m => JSON.stringify(m)).join(''),
  () => scrollToBottom()
)

watch(
  () => props.messages.length,
  () => scrollToBottom()
)

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
/* =============================================
   ChatRoom — 浅色清爽风 + ReAct 扩展
   ============================================= */

.chat-container {
  display: flex;
  flex-direction: column;
  height: 78vh;
  min-height: 500px;
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-subtle);
  overflow: hidden;
  position: relative;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

/* ---------- 消息滚动区 ---------- */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  padding-bottom: 24px;
  display: flex;
  flex-direction: column;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 120px;
}

/* ---------- 空状态引导页 ---------- */
.welcome-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 48px 16px 24px;
  animation: fadeUp 0.5s ease-out;
}

.welcome-hero {
  text-align: center;
  margin-bottom: 4px;
}

.welcome-title {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.welcome-accent {
  background: linear-gradient(135deg, #818cf8, #6366f1 60%, #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  margin-top: 8px;
  font-family: var(--font-body);
  font-size: 0.875rem;
  line-height: 1.6;
  color: var(--text-tertiary);
}

/* ---------- 功能卡片 ---------- */
.feature-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  width: 100%;
  max-width: 720px;
}

.feature-card {
  padding: 20px 18px;
  border-radius: 14px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}

.feature-card:hover {
  border-color: var(--border-active);
  transform: translateY(-3px);
  box-shadow: 0 6px 24px rgba(79,70,229,0.08);
}

.feature-card-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--accent-bg);
  color: var(--accent);
  margin-bottom: 12px;
}

.feature-card-title {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 5px;
}

.feature-card-text {
  font-family: var(--font-body);
  font-size: 0.8125rem;
  line-height: 1.55;
  color: var(--text-tertiary);
}

/* ---------- 建议提问 ---------- */
.suggestion-label {
  font-family: var(--font-body);
  font-size: 0.8125rem;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 600px;
}

.suggestion-chip {
  font-family: var(--font-body);
  font-size: 0.8125rem;
  padding: 8px 16px;
  border-radius: 100px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.15s;
}

.suggestion-chip:hover {
  background: white;
  border-color: var(--border-active);
  color: var(--accent);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.suggestion-chip:active {
  transform: translateY(0);
}

/* ---------- 消息通用 ---------- */
.message-wrapper {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.message {
  display: flex;
  align-items: flex-start;
}

.user-message {
  margin-left: auto;
  flex-direction: row;
}

.ai-message {
  margin-right: auto;
  gap: 10px;
}

/* ---------- 头像 ---------- */
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  margin-left: 10px;
}

.ai-avatar {
  margin-top: 2px;
}

.avatar-inner {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 0.8125rem;
  font-weight: 600;
  border: 1px solid rgba(79, 70, 229, 0.15);
  border-radius: 50%;
}

/* ---------- AI 消息体 ---------- */
.ai-message-body {
  flex: 1;
  min-width: 0;
  max-width: calc(100% - 46px);
}

/* ---------- 用户气泡 ---------- */
.message-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  position: relative;
  word-wrap: break-word;
  min-width: 100px;
}

.user-message .message-bubble {
  background: var(--accent);
  color: white;
  border-bottom-right-radius: 4px;
  text-align: left;
}

.ai-message .message-bubble {
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
  text-align: left;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.message-content {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  line-height: 1.6;
  white-space: pre-wrap;
}

.message-time {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  opacity: 0.5;
  margin-top: 8px;
  color: var(--text-tertiary);
  padding-left: 4px;
}

.typing-indicator {
  display: inline-block;
  animation: blink 0.7s infinite;
  margin-left: 2px;
  color: var(--accent);
}

/* ---------- 输入区 ---------- */
.chat-input-container {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--bg-elevated);
  border-top: 1px solid var(--border-subtle);
  z-index: 100;
}

.quick-cap-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px 0;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.quick-cap-bar::-webkit-scrollbar {
  display: none;
}

.cap-hint {
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  margin-left: 4px;
  white-space: nowrap;
}

.quick-cap-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-body);
  font-size: 0.75rem;
  font-weight: 500;
  padding: 5px 12px;
  border-radius: 100px;
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  white-space: nowrap;
  transition: background 0.2s, border-color 0.2s, color 0.2s;
  cursor: pointer;
  flex-shrink: 0;
}

.quick-cap-btn:hover {
  background: white;
  color: var(--accent);
  border-color: var(--border-active);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.quick-cap-btn.active {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.2);
}

.chat-input {
  display: flex;
  padding: 10px 16px 14px;
  box-sizing: border-box;
  align-items: flex-end;
  gap: 10px;
}

.input-box {
  flex-grow: 1;
  border: 1.5px solid var(--border-subtle);
  border-radius: 20px;
  padding: 10px 16px;
  font-family: var(--font-body);
  font-size: 0.9375rem;
  color: var(--text-primary);
  background: var(--bg-card);
  resize: none;
  min-height: 20px;
  max-height: 160px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  line-height: 1.5;
}

.input-box::placeholder {
  color: var(--text-tertiary);
}

.input-box::-webkit-scrollbar {
  display: none;
}

.input-box:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.08);
  background: white;
}

/* ---------- 发送按钮 ---------- */
.send-button {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-base);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: background 0.2s, color 0.2s, transform 0.15s;
}
.send-button.active {
  background: var(--accent);
  color: white;
}
.send-button:hover:not(:disabled) {
  background: var(--accent);
  color: white;
  transform: scale(1.05);
}
.send-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

/* ---------- 动画 ---------- */
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes blink {
  0% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------- 文件下载卡片 ---------- */
.file-cards {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.file-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.file-card:hover {
  border-color: var(--border-active);
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.06);
}

.file-card-icon {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  flex-shrink: 0;
  background: var(--accent-bg);
  color: var(--accent);
}

.file-card-icon.file-type-pdf {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
}

.file-card-icon.file-type-md,
.file-card-icon.file-type-markdown,
.file-card-icon.file-type-txt {
  background: rgba(16, 185, 129, 0.08);
  color: #059669;
}

.file-card-info {
  flex: 1;
  min-width: 0;
}

.file-card-name {
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-card-size {
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  margin-top: 1px;
}

.file-card-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  background: white;
  color: var(--accent);
  cursor: pointer;
  transition: background 0.2s, transform 0.15s;
  flex-shrink: 0;
}

.file-card-btn:hover:not(:disabled) {
  background: var(--accent-bg);
  transform: scale(1.05);
}

.file-card-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.file-btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(79, 70, 229, 0.2);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.file-download-error {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  padding: 5px 10px;
  border-radius: 6px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 0.75rem;
}

/* 文件卡片入场动画 */
.file-card-enter-active {
  transition: opacity 0.3s, transform 0.3s;
}
.file-card-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

/* ---------- 响应式 ---------- */
@media (max-width: 768px) {
  .message { max-width: 95%; }
  .message-content { font-size: 0.875rem; }
  .chat-input { padding: 10px 12px 12px; }
  .quick-cap-bar { padding: 8px 12px 0; }
  .feature-cards { grid-template-columns: 1fr; gap: 12px; max-width: 420px; }
}

@media (max-width: 480px) {
  .avatar { width: 32px; height: 32px; }
  .message-bubble { padding: 10px 14px; }
  .message-content { font-size: 0.8125rem; }
  .chat-messages { bottom: 120px; }
  .welcome-panel { padding: 20px 12px 16px; }
  .welcome-title { font-size: 1.25rem; }
  .feature-card { padding: 16px 14px; }
}

/* ---------- 配额达上限横幅 ---------- */
.quota-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  margin: 8px 16px 0;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 0.8125rem;
  font-weight: 500;
}
</style>
