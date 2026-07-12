<template>
  <div class="chat-container">
    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer">
            <div v-if="!hasUserMessages" class="welcome-panel">
        <h2 class="welcome-title">今天想完成什么？</h2>
        <p class="welcome-desc">我可以帮你联网调研、写作、内容分析，并把成果导出为文档、表格或 PDF。</p>
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
              <div v-else>
                <!-- 推理过程面板：有步骤时展示 -->
                <div v-if="msg.steps && msg.steps.length > 0" class="thinking-panel" :class="{ collapsed: msg.collapsed }">
                  <div class="thinking-header" @click="msg.collapsed = !msg.collapsed">
                    <span class="thinking-icon">🧠</span>
                    <span class="thinking-title">推理过程</span>
                    <span class="thinking-count" v-if="msg.collapsed">{{ msg.steps.length }} 步</span>
                    <svg class="thinking-arrow" :class="{ rotated: !msg.collapsed }" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
                  </div>
                  <div v-show="!msg.collapsed" class="thinking-body">
                    <div v-for="(step, si) in msg.steps" :key="si" class="thinking-step">
                      <div class="step-header">
                        <span class="step-num">{{ si + 1 }}</span>
                        <span class="step-label">步骤</span>
                      </div>
                      <div class="step-content">
                        <div v-if="step.thought" class="step-thought">{{ stripMarkdown(step.thought) }}</div>
                        <div v-if="step.tool" class="step-tool">
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
                          {{ step.tool }}
                        </div>
                        <div v-if="step.observation" class="step-observation">{{ stripMarkdown(step.observation) }}</div>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- 消息气泡 -->
                <div class="message-bubble">
                  <div class="message-content">
                    <span v-if="msg.thinking" class="thinking-live">💭 思考中…</span>
                    <span v-else>{{ stripMarkdown(msg.content) }}</span>
                    <span v-if="connectionStatus === 'connecting' && index === messages.length - 1 && !msg.thinking" class="typing-indicator">▋</span>
                  </div>
                </div>
              </div>
              <div class="message-time">{{ formatTime(msg.time) }}</div>
              <!-- 对话生成的图表卡片（AnalyzeDataTool 产出 [CHART_FILE=...] 标记时渲染） -->
              <div v-if="msg.chartFiles && msg.chartFiles.length > 0" class="chart-cards">
                <ChartCard
                  v-for="cf in msg.chartFiles"
                  :key="cf.fileId"
                  :file-id="cf.fileId"
                  :chat-id="chatId"
                  :title="cf.title"
                />
              </div>
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

      <!-- 附件 chip（有附件时显示在输入框上方） -->
      <transition name="fade">
        <div v-if="attachmentFile" class="attachment-chip">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
          </svg>
          <span class="attachment-name" :title="attachmentFile.name + ' (' + formatFileSize(attachmentFile.size) + ')'">
            {{ attachmentFile.name }}
          </span>
          <span class="attachment-size">{{ formatFileSize(attachmentFile.size) }}</span>
          <button class="attachment-remove" @click="clearAttachment" title="移除附件">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </transition>

      <!-- 附件解析错误提示 -->
      <transition name="fade">
        <div v-if="attachmentError" class="attachment-error">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          {{ attachmentError }}
          <button class="attachment-error-close" @click="attachmentError = ''">×</button>
        </div>
      </transition>

      <div class="chat-input" :class="{ 'has-attachment': attachmentFile }">
        <textarea
          ref="inputEl"
          v-model="inputMessage"
          @keydown.enter="handleEnter"
          @input="autoResize"
          @paste="handlePaste"
          :placeholder="inputPlaceholder"
          class="input-box"
          :disabled="connectionStatus === 'connecting' || quotaReached"
          rows="1"
        ></textarea>
        <button
          ref="attachBtn"
          @click="triggerFileInput"
          class="attach-button"
          :class="{ active: attachmentFile }"
          :disabled="connectionStatus === 'connecting' || quotaReached"
          title="上传文件让 AI 阅读（PDF/DOCX/TXT）"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
          </svg>
        </button>
        <button
          @click="sendMessage"
          class="send-button"
          :class="{ active: inputMessage.trim() }"
          :disabled="connectionStatus === 'connecting' || (!inputMessage.trim() && !attachmentFile) || quotaReached"
          aria-label="发送消息"
        >
          <svg v-if="connectionStatus !== 'connecting'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
          <span v-else class="send-spinner" />
        </button>
      </div>

      <!-- 隐藏的文件选择器 -->
      <input
        ref="fileInputEl"
        type="file"
        class="file-input-hidden"
        accept=".pdf,.docx,.xlsx,.pptx,.doc,.xls,.ppt,.txt,.md,.csv,.json"
        @change="handleFileSelect"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'
import ReActSteps from './ReActSteps.vue'
import ChartCard from './ChartCard.vue'
import { downloadFile as downloadFileApi } from '@/api'
import { stripMarkdown } from '@/utils/markdown'

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
  },
  // 父组件传入的能力开关初始值（用于同步重置）
  initialCaps: {
    type: Object,
    default: () => ({ webSearch: true, knowledgeBase: false })
  }
})

const emit = defineEmits(['send-message', 'capability-change'])

const inputMessage = ref('')
const messagesContainer = ref(null)
const inputEl = ref(null)
const fileInputEl = ref(null)

// 对话附件（一次性的，发出即清空；与知识库持久上传不同）
const attachmentFile = ref(null)   // File 对象
const attachmentError = ref('')    // 选择文件时的校验错误（大小、后缀等）

/** 校验并设入附件，失败时写 attachmentError 提示用户 */
function setAttachment(file) {
  attachmentError.value = ''
  const ALLOWED = /\.(pdf|docx|xlsx|pptx|doc|xls|ppt|txt|md|csv|json)$/i
  const MAX_BYTES = 10 * 1024 * 1024 // 与后端一致 10MB
  if (!ALLOWED.test(file.name)) {
    attachmentError.value = '不支持的文件格式：' + file.name
    return
  }
  if (file.size > MAX_BYTES) {
    attachmentError.value = '文件超过 10MB 限制：' + file.name
    return
  }
  attachmentFile.value = file
}

function clearAttachment() {
  attachmentFile.value = null
  attachmentError.value = ''
  if (fileInputEl.value) fileInputEl.value.value = ''
}

function triggerFileInput() {
  if (fileInputEl.value) fileInputEl.value.click()
}

function handleFileSelect(e) {
  const file = e.target.files && e.target.files[0]
  if (file) setAttachment(file)
  // 同一文件重复选择时 value 不变导致 onchange 不触发，清空后允许重复选
  e.target.value = ''
}

/** 剪贴板粘贴文件（如从截图工具粘贴 PDF/文档） */
function handlePaste(e) {
  const items = e.clipboardData && e.clipboardData.items
  if (!items) return
  for (const item of items) {
    if (item.kind === 'file') {
      e.preventDefault()
      setAttachment(item.getAsFile())
      break
    }
  }
}

// 能力开关（toggle，持续生效直到关闭）
const capabilities = [
  {
    key: 'webSearch',
    name: '深度思考',
    desc: '多步推理 + 联网搜索，适合需要实时信息或复杂分析的问题',
    color: '#2563eb',
    bg: 'rgba(37, 99, 235,0.08)'
  },
  {
    key: 'knowledgeBase',
    name: '知识库',
    desc: '检索已上传的文档',
    color: '#0891b2',
    bg: 'rgba(8,145,178,0.08)'
  }
]

const activeCaps = reactive({ ...props.initialCaps })

// 父组件传入的初始值变化时同步（如新建对话重置能力开关）
watch(() => props.initialCaps, (val) => {
  activeCaps.webSearch = val.webSearch
  activeCaps.knowledgeBase = val.knowledgeBase
}, { deep: true })

const toggleCap = (key) => {
  activeCaps[key] = !activeCaps[key]
  // userInitiated = true 表示用户主动操作，用于父组件判断是否展示提示
  emit('capability-change', { ...activeCaps }, true)
}

/**
 * 题库：任务导向型快捷建议，每次随机抽取 4 条展示。
 * 覆盖联网调研、写作创作、内容分析、文档导出、知识问答等真实支持的能力场景。
 */
const QUESTION_POOL = [
  // 联网调研 / 分析
  '整理一份竞品调研并导出表格',
  '搜索今天的 AI 领域热点新闻',
  '对比 Vue 3 和 React 的核心差异',
  '帮我做一份 SWOT 分析并生成表格',
  // 写作 / 创作
  '帮我写一篇小红书文案',
  '写一份周报，涵盖本周主要进展',
  '帮我写一封正式的商务邮件',
  '润色并优化我这段自我介绍',
  // 文档 / 导出
  '整理一份市场调研报告并导出 PDF',
  '把一份产品介绍整理成 Markdown 文档',
  '生成项目 README 并下载',
  '帮我列一份健身周计划表并导出',
  // 知识 / 概念
  '解释一下什么是 MCP 协议',
  '解释一下 RAG 检索增强生成的原理',
  '推荐几本适合入门的编程书',
  // 对话附件：上传文档 / PDF / Word 让 AI 直接阅读（非知识库，一次性分析）
  '我想上传一份 PDF 合同，帮我找出关键条款',
  // 生活 / 其他
  '规划一个杭州三日游路线',
  '月薪一万怎么开始学理财',
  '帮我拟一份周末家庭聚餐菜单',
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
  emit('send-message', question, null)
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
  // 允许「没文字但选了附件」这种情况，自动补一句引导语
  const text = inputMessage.value.trim()
  if (!text && !attachmentFile.value) return
  const finalText = text || '请阅读这个文件并给我一个概述，然后告诉我你想了解什么。'
  emit('send-message', finalText, attachmentFile.value)
  inputMessage.value = ''
  if (inputEl.value) inputEl.value.style.height = 'auto'
  // 发送后即清空附件——一次性用途，下一轮需重新选
  attachmentFile.value = null
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
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
  margin: 0;
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
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

/* ---------- 空状态引导页 ---------- */
.welcome-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px 24px 32px;
  margin: auto;
  animation: fadeUp 0.5s ease-out;
  max-width: 640px;
}

.welcome-title {
  font-family: var(--font-body);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.02em;
  text-align: center;
}

.welcome-desc {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  color: var(--text-tertiary);
  margin: 0;
  text-align: center;
  line-height: 1.6;
  max-width: 480px;
}

/* ---------- 建议提问卡片 ---------- */
.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  max-width: 600px;
  width: 100%;
}

.suggestion-chip {
  font-family: var(--font-body);
  font-size: 0.875rem;
  padding: 10px 20px;
  border-radius: 12px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.15s, box-shadow 0.2s;
  line-height: 1.4;
  text-align: center;
  min-width: 140px;
  max-width: 280px;
}

.suggestion-chip:hover {
  background: #f0f4ff;
  border-color: rgba(37, 99, 235, 0.3);
  color: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.1);
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
  max-width: 75%;
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
  border: 1px solid rgba(37, 99, 235, 0.15);
  border-radius: 50%;
}

/* ---------- AI 消息体 ---------- */
.ai-message-body {
  flex: 1;
  min-width: 0;
  max-width: 75%;
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
  overflow-wrap: anywhere;
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
  background: var(--bg-elevated);
  border-top: 1px solid var(--border-subtle);
  z-index: 100;
  flex-shrink: 0;
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
  background: var(--bg-elevated);
  color: var(--accent);
  border-color: var(--border-active);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.quick-cap-btn.active {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.2);
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
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
  background: var(--bg-elevated);
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
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.06);
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
  background: var(--bg-elevated);
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
  border: 2px solid rgba(37, 99, 235, 0.2);
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

/* ---------- 图表卡片 ---------- */
.chart-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
  min-width: 320px;
}

/* 文件卡片入场动画 */
.file-card-enter-active {
  transition: opacity 0.3s, transform 0.3s;
}
.file-card-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

/* ---------- 推理过程面板 ---------- */
.thinking-panel {
  margin-bottom: 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.04), rgba(124, 58, 237, 0.04));
  border: 1px solid rgba(37, 99, 235, 0.12);
  overflow: hidden;
  transition: all 0.25s ease;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.thinking-header:hover {
  background: rgba(37, 99, 235, 0.06);
}

.thinking-icon {
  font-size: 0.9375rem;
}

.thinking-title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--accent);
}

.thinking-count {
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  margin-left: auto;
  margin-right: 4px;
}

.thinking-arrow {
  color: var(--text-tertiary);
  transition: transform 0.25s ease;
}

.thinking-arrow.rotated {
  transform: rotate(180deg);
}

.thinking-body {
  padding: 0 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  animation: fadeSlideDown 0.25s ease-out;
}

.thinking-step {
  display: flex;
  gap: 10px;
  animation: fadeSlideIn 0.3s ease-out;
}

.step-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  padding-top: 2px;
}

.step-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--accent);
  color: white;
  font-size: 0.6875rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-label {
  font-size: 0.625rem;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.step-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.step-thought {
  font-size: 0.8125rem;
  line-height: 1.55;
  color: var(--text-secondary);
  font-style: italic;
}

.step-tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--accent);
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.08);
  width: fit-content;
}

.step-observation {
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--text-tertiary);
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--bg-base);
  border-left: 2px solid var(--border-subtle);
}

/* 折叠状态：只显示 header */
.thinking-panel.collapsed .thinking-header {
  padding: 8px 14px;
}

.thinking-panel.collapsed {
  opacity: 0.85;
}

.thinking-panel.collapsed:hover {
  opacity: 1;
  border-color: rgba(37, 99, 235, 0.25);
}

/* 思考中实时提示 */
.thinking-live {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--accent);
  font-size: 0.875rem;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

@keyframes fadeSlideDown {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fadeSlideIn {
  from { opacity: 0; transform: translateX(-8px); }
  to { opacity: 1; transform: translateX(0); }
}

/* ---------- 响应式 ---------- */
@media (max-width: 768px) {
  .message { max-width: 95%; }
  .ai-message-body { max-width: 95%; }
  .user-message { max-width: 95%; }
  .message-content { font-size: 0.875rem; }
  .chat-input { padding: 10px 12px 12px; }
  .quick-cap-bar { padding: 8px 12px 0; }
}

@media (max-width: 480px) {
  .avatar { width: 32px; height: 32px; }
  .message-bubble { padding: 10px 14px; }
  .message-content { font-size: 0.8125rem; }
  .welcome-panel { padding: 20px 12px; }
}

/* ---------- 对话附件 chip ---------- */
.attachment-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 16px 0;
  padding: 6px 12px;
  border-radius: 10px;
  background: var(--accent-bg, rgba(37, 99, 235, 0.06));
  border: 1px solid rgba(37, 99, 235, 0.18);
  width: fit-content;
  max-width: calc(100% - 32px);
}
.attachment-chip svg { flex-shrink: 0; color: var(--accent); }
.attachment-name {
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-primary);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.attachment-size {
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.attachment-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: transparent;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}
.attachment-remove:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

/* 附件校验错误 */
.attachment-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 16px 0;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 0.75rem;
  width: fit-content;
  max-width: calc(100% - 32px);
}
.attachment-error-close {
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
  font-size: 0.875rem;
  margin-left: 4px;
  padding: 0 2px;
}

/* 附件按钮（在输入框左侧） */
.attach-button {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-base);
  color: var(--text-tertiary);
  border: 1.5px solid var(--border-subtle);
  cursor: pointer;
  transition: background 0.2s, color 0.2s, transform 0.15s, border-color 0.2s;
}
.attach-button:hover:not(:disabled) {
  background: var(--accent-bg, rgba(37, 99, 235, 0.08));
  color: var(--accent);
  border-color: var(--accent);
  transform: scale(1.05);
}
.attach-button.active {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--accent-bg, rgba(37, 99, 235, 0.08));
}
.attach-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 隐藏的原生 file input */
.file-input-hidden {
  display: none;
}

/*有附件时输入框容器左侧留白配合 attach 按钮 */
.chat-input.has-attachment .input-box {
  background: var(--bg-elevated);
}

/* 附件 chip 过渡 */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
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
