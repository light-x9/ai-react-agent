<template>
  <div class="chat-container">
    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <!-- 空状态引导页（无用户消息时展示功能卡片） -->
      <div v-if="!hasUserMessages" class="welcome-panel">
        <!-- 标题区 -->
        <div class="welcome-hero">
          <h2 class="welcome-title">
            看得见思考过程的 <span class="welcome-accent">AI Agent</span>
          </h2>
          <p class="welcome-subtitle">
            基于 ReAct 推理框架，前端实时可视化每一步思考与工具调用
          </p>
        </div>

        <!-- 功能卡片 -->
        <div class="feature-cards">
          <article class="feature-card">
            <div class="feature-card-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="3" />
                <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
              </svg>
            </div>
            <h3 class="feature-card-title">思维链可视化</h3>
            <p class="feature-card-text">Thought / Action / Observation 三阶段时间线，渐进式动画呈现，完整展示 AI 推理路径。</p>
          </article>

          <article class="feature-card">
            <div class="feature-card-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" />
                <path d="M8 21h8M12 17v4" />
              </svg>
            </div>
            <h3 class="feature-card-title">工具调用面板</h3>
            <p class="feature-card-text">每次 Action 以卡片形式展示工具名称与参数，支持展开查看完整请求和响应 JSON。</p>
          </article>

          <article class="feature-card">
            <div class="feature-card-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
                <path d="M8 7h8M8 11h6" />
              </svg>
            </div>
            <h3 class="feature-card-title">RAG 知识库</h3>
            <p class="feature-card-text">上传文档自动分块索引，Query Rewrite 优化检索，AI 结合知识库回答专业领域问题。</p>
          </article>
        </div>

        <!-- 建议提问卡片 -->
        <p class="suggestion-label">试试这样问：</p>
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
      </div>

      <!-- 消息列表 -->
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
          :disabled="connectionStatus === 'connecting'"
          rows="1"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-button"
          :class="{ active: inputMessage.trim() }"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
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

const suggestedQuestions = [
  '帮我搜索今天的 AI 新闻',
  '解释一下什么是 MCP 协议',
  '帮我写一个 Python 爬虫',
  '总结一下 ReAct 推理的原理'
]

const hasUserMessages = computed(() => props.messages.some(m => m.isUser))

const inputPlaceholder = computed(() => {
  if (props.connectionStatus === 'connecting') return 'AI 正在思考中...'
  return '输入消息，Enter 发送，Shift+Enter 换行'
})

const handleSuggestionClick = (question) => {
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
  gap: 18px;
  padding: 32px 16px 24px;
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
</style>
