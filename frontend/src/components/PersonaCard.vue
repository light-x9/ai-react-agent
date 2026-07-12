<template>
  <div v-if="visible && persona && persona.loggedIn !== false" class="persona"
       :class="{ compact, empty: !hasHistory }">
    <!-- 紧凑模式：欢迎页顶部横条 -->
    <template v-if="compact">
      <div class="pc-compact">
        <div class="pc-compact-left">
          <span v-if="persona.techStack" class="pc-tags">{{ topTags }}</span>
          <span v-else class="pc-subtle">轻点继续聊，接着上次的聊</span>
        </div>
        <div class="pc-compact-right">
          <button v-if="hasHistory" class="pc-pill-btn" @click="toggleExpand">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            继续聊
          </button>
          <button class="pc-close" @click="dismiss" title="收起">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
      </div>

      <!-- 展开的话题面板 -->
      <transition name="slide">
        <div v-if="expanded && hasHistory" class="pc-expand">
          <div class="pc-expand-greet">{{ greetText }}，接着上次聊 ↓</div>
          <div class="pc-section" v-if="recentTopics.length > 0">
            <div class="pc-section-label">最近聊过</div>
            <div class="pc-topics">
              <button v-for="t in recentTopics" :key="t" class="pc-topic-chip" @click="onTopicClick(t)">
                {{ t }}
              </button>
            </div>
          </div>
          <div class="pc-section" v-if="suggestions.length > 0">
            <div class="pc-section-label">你可能想继续</div>
            <button v-for="s in suggestions" :key="s" class="pc-suggestion" @click="onSuggestionClick(s)">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
              {{ s }}
            </button>
          </div>
        </div>
      </transition>
    </template>

    <!-- 全宽模式：侧边栏等场景使用 -->
    <template v-else>
      <div class="pc-full-header">
        <div class="pc-avatar-large">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
          </svg>
        </div>
        <div class="pc-full-info">
          <div class="pc-full-greet">{{ greetText }}</div>
          <div class="pc-full-meta">
            <span v-if="persona.conversationCount > 0">第 {{ persona.conversationCount }} 次对话</span>
            <span v-if="persona.techStack">· 常用 {{ persona.techStack.split(',').slice(0, 3).join(' / ') }}</span>
          </div>
        </div>
        <button class="pc-close" @click="dismiss" title="收起">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
      <div v-if="recentTopics.length > 0" class="pc-section">
        <div class="pc-section-label">最近聊过</div>
        <div class="pc-topics">
          <button v-for="t in recentTopics" :key="t" class="pc-topic-chip" @click="onTopicClick(t)">{{ t }}</button>
        </div>
      </div>
      <div v-if="suggestions.length > 0" class="pc-section">
        <div class="pc-section-label">你可能想继续</div>
        <button v-for="s in suggestions" :key="s" class="pc-suggestion" @click="onSuggestionClick(s)">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
          {{ s }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMyPersona } from '@/api'

const props = defineProps({
  compact: { type: Boolean, default: true },
  visible: { type: Boolean, default: true },
  onTopicClick: { type: Function, default: null },
  onSuggestionClick: { type: Function, default: null },
})

const emit = defineEmits(['dismiss', 'loaded'])

const persona = ref({})
const suggestions = ref([])
const loading = ref(true)
const expanded = ref(false)

const recentTopics = computed(() => {
  const t = persona.value.recentTopics
  return Array.isArray(t) ? t.slice(-5) : []
})

const hasHistory = computed(() =>
  persona.value.conversationCount > 0 && recentTopics.value.length > 0
)

const greetText = computed(() => buildGreeting(persona.value.nickname))

const topTags = computed(() => {
  const ts = persona.value.techStack
  if (!ts) return ''
  const arr = ts.split(',').filter(Boolean).slice(0, 3)
  return arr.join(' / ')
})

onMounted(async () => {
  try {
    const data = await getMyPersona()
    if (data && data.loggedIn) {
      persona.value = data
      suggestions.value = data.suggestions || []
      // 通知父组件画像已加载，传问候语过去用于标题拼接
      emit('loaded', {
        greeting: buildGreeting(data.nickname),
        nickname: data.nickname,
      })
    }
  } catch (_) {
    // 未登录时静默隐藏
  } finally {
    loading.value = false
  }
})

function buildGreeting(nickname) {
  if (!nickname) return ''
  const h = new Date().getHours()
  const period = h < 6 ? '凌晨好' : h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好'
  return `${period}，${nickname}`
}

function toggleExpand() { expanded.value = !expanded.value }

function onTopicClick(topic) {
  if (props.onTopicClick) props.onTopicClick(topic)
  else emit('topic-click', topic)
}

function onSuggestionClick(s) {
  const clean = s.replace(/^(继续聊：|深入了解：)/, '')
  if (props.onSuggestionClick) props.onSuggestionClick(clean)
  else emit('suggestion-click', clean)
}

function dismiss() { emit('dismiss') }
</script>

<style scoped>
/* ===== 紧凑模式（欢迎页横条） ===== */
.persona {
  --c-persona-bg: rgba(37, 99, 235, 0.04);
  --c-persona-border: rgba(37, 99, 235, 0.12);
  --c-persona-text: var(--accent);
  width: 100%;
  box-sizing: border-box;
}

.pc-compact {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: var(--c-persona-bg);
  border: 1px solid var(--c-persona-border);
  border-radius: 12px;
  backdrop-filter: blur(4px);
}
.pc-compact-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.pc-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.08);
  color: var(--c-persona-text);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pc-greet {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}
.pc-tags {
  font-size: 0.75rem;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.pc-subtle {
  font-size: 0.75rem;
  color: var(--text-tertiary);
}
.pc-expand-greet {
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.pc-compact-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.pc-pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  padding: 5px 12px;
  border-radius: 100px;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.2);
  color: var(--accent);
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.pc-pill-btn:hover { background: rgba(37, 99, 235, 0.14); }
.pc-pill-btn svg { transition: transform 0.2s; }
.pc-pill-btn:hover svg { transform: translateY(1px); }
.pc-close {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: transparent;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}
.pc-close:hover { background: var(--bg-base); color: var(--text-primary); }

/* 展开面板 */
.pc-expand {
  margin-top: 8px;
  padding: 12px 16px;
  background: var(--c-persona-bg);
  border: 1px solid var(--c-persona-border);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ===== 全宽模式（侧边栏等） ===== */
.persona:not(.compact) {
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.pc-full-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.pc-avatar-large {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.08);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pc-full-info { flex: 1; min-width: 0; }
.pc-full-greet { font-size: 0.9375rem; font-weight: 600; color: var(--text-primary); }
.pc-full-meta { font-size: 0.75rem; color: var(--text-tertiary); margin-top: 2px; }

/* 共用 section 样式 */
.pc-section { display: flex; flex-direction: column; gap: 6px; }
.pc-section + .pc-section { margin-top: 4px; }
.pc-section-label {
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.pc-topics { display: flex; flex-wrap: wrap; gap: 6px; }
.pc-topic-chip {
  font-size: 0.75rem;
  padding: 4px 10px;
  border-radius: 100px;
  background: rgba(37, 99, 235, 0.06);
  border: 1px solid rgba(37, 99, 235, 0.15);
  color: var(--accent);
  cursor: pointer;
  transition: background 0.15s;
}
.pc-topic-chip:hover { background: rgba(37, 99, 235, 0.12); }
.pc-suggestion {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8125rem;
  padding: 7px 12px;
  border-radius: 8px;
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.pc-suggestion:hover {
  background: rgba(37, 99, 235, 0.04);
  border-color: rgba(37, 99, 235, 0.2);
  color: var(--accent);
}
.pc-suggestion svg { flex-shrink: 0; transition: transform 0.15s; }
.pc-suggestion:hover svg { transform: translateX(2px); }

/* 动画 */
.slide-enter-active, .slide-leave-active {
  transition: opacity 0.2s, transform 0.2s;
  overflow: hidden;
}
.slide-enter-from, .slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
