<template>
  <aside class="session-sidebar">
    <!-- 顶部：新对话 -->
    <div class="sidebar-header">
      <button class="new-chat-btn" @click="newChat" title="开始新对话">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        <span>新对话</span>
      </button>
    </div>

    <!-- 会话列表 -->
    <div class="session-list">
      <div
        v-for="s in chatStore.sessions"
        :key="s.id"
        :class="['session-item', `cap-${s.capability || 'chat'}`, { active: String(s.id) === String(chatStore.activeId) }]"
        @click="selectSession(s.id)"
      >
        <!-- 激活高亮色带 -->
        <span class="active-bar"></span>

        <!-- 会话类型图标 -->
        <span class="session-icon-wrap">
          <!-- 普通对话 -->
          <svg v-if="(s.capability || 'chat') === 'chat'" class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          <!-- 网页搜索 -->
          <svg v-else-if="(s.capability || 'chat') === 'webSearch'" class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="2" y1="12" x2="22" y2="12" />
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
          </svg>
          <!-- 知识库检索 -->
          <svg v-else-if="(s.capability || 'chat') === 'knowledgeBase'" class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            <line x1="12" y1="6" x2="12" y2="14" />
            <line x1="8" y1="10" x2="16" y2="10" />
          </svg>
          <!-- 双重能力（搜索+知识库） -->
          <svg v-else class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
            <line x1="11" y1="8" x2="11" y2="14" />
            <line x1="8" y1="11" x2="14" y2="11" />
          </svg>
        </span>

        <!-- 标题 / 重命名编辑框 -->
        <span v-if="renamingId !== s.id" class="session-title" :title="s.title || '新对话'">
          {{ s.title || '新对话' }}
        </span>
        <input
          v-else
          v-model="renameValue"
          class="session-rename-input"
          maxlength="50"
          @click.stop
          @keydown.enter="confirmRename(s)"
          @keydown.escape="cancelRename"
          @blur="confirmRename(s)"
        />

        <!-- hover 操作按钮 -->
        <span v-if="renamingId !== s.id" class="session-actions">
          <button class="session-action-btn" @click.stop="startRename(s)" title="重命名">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
            </svg>
          </button>
          <button class="session-action-btn session-delete" @click.stop="askDelete(s)" title="删除">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6" />
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
            </svg>
          </button>
        </span>
      </div>
      <div v-if="chatStore.sessions.length === 0" class="empty-tip">暂无对话，点击上方新建</div>
    </div>

    <!-- 底部：用量 + 用户 -->
    <div class="sidebar-footer">
      <div class="usage-box">
        <div class="usage-row">
          <span class="usage-label">今日对话</span>
          <span class="usage-value" :class="{ near: usage.chatUsed >= usage.chatLimit * 0.8 }">
            {{ usage.chatUsed }}/{{ usage.chatLimit }}
          </span>
        </div>
        <div class="usage-row">
          <span class="usage-label">联网搜索</span>
          <span class="usage-value" :class="{ near: usage.searchUsed >= usage.searchLimit * 0.8 }">
            {{ usage.searchUsed }}/{{ usage.searchLimit }}
          </span>
        </div>
      </div>
      <div class="user-box">
        <span class="user-avatar">{{ (userStore.username || 'U').charAt(0).toUpperCase() }}</span>
        <span class="user-name">{{ userStore.username }}</span>
        <button class="logout-btn" @click="logout" title="登出">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
            <polyline points="16 17 21 12 16 7" />
            <line x1="21" y1="12" x2="9" y2="12" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 删除确认：Teleport 到 body，避免被 sidebar 的 flex/sticky 容器约束 -->
    <Teleport to="body">
      <div v-if="deleteTarget" class="delete-overlay" @click.self="cancelDelete">
        <div class="delete-dialog">
          <p>确定要删除该条会话吗？删除后历史记录无法恢复。</p>
          <div class="delete-actions">
            <button class="btn-cancel" @click="cancelDelete">取消</button>
            <button class="btn-danger" @click="doDelete" :disabled="deleting">
              {{ deleting ? '删除中…' : '删除' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 操作结果提示 toast：同样 Teleport 到 body -->
    <Teleport to="body">
      <transition name="fade">
        <div v-if="actionToast" class="action-toast" :class="actionToastType">
          {{ actionToast }}
        </div>
      </transition>
    </Teleport>
  </aside>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chatStore'
import { useUserStore } from '@/stores/userStore'
import { getUsageToday } from '@/api'

const router = useRouter()
const chatStore = useChatStore()
const userStore = useUserStore()

const usage = ref({ chatUsed: 0, chatLimit: 100, searchUsed: 0, searchLimit: 30 })
const deleteTarget = ref(null)
// 删除进行中状态：锁住按钮防重复点击
const deleting = ref(false)

// 操作结果 toast（删除成功 / 删除失败）
const actionToast = ref('')
const actionToastType = ref('success')
let toastTimer = null
const showToast = (msg, type = 'success') => {
  actionToast.value = msg
  actionToastType.value = type
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { actionToast.value = '' }, 3000)
}

// 重命名状态
const renamingId = ref(null)        // 当前正在重命名的会话 id
const renameValue = ref('')         // 重命名输入框的值

const refreshUsage = async () => {
  try {
    const res = await getUsageToday()
    if (res) usage.value = res
  } catch (e) {
    // 静默失败，不阻塞
  }
}

const newChat = async () => {
  await chatStore.createSession()
  refreshUsage()
}

const selectSession = async (id) => {
  // 如果在重命名中，先取消
  if (renamingId.value) cancelRename()
  await chatStore.switchSession(id)
}

const askDelete = (s) => {
  deleteTarget.value = s
}

// 取消删除（点取消键或点击遮罩）
const cancelDelete = () => {
  // 删除过程中不允许取消，防误触
  if (deleting.value) return
  deleteTarget.value = null
}

const doDelete = async () => {
  if (!deleteTarget.value || deleting.value) return
  const target = deleteTarget.value
  if (renamingId.value === target.id) cancelRename()
  deleting.value = true
  try {
    await chatStore.deleteSession(target.id)
    showToast('会话已删除', 'success')
  } catch (e) {
    showToast('删除失败：' + (e?.message || '请稍后重试'), 'error')
  } finally {
    deleting.value = false
    deleteTarget.value = null
  }
}

// 开始重命名
const startRename = async (s) => {
  renamingId.value = s.id
  renameValue.value = s.title || '新对话'
  // 等待 DOM 更新后聚焦输入框（仅有一个会话处于重命名状态）
  await nextTick()
  const input = document.querySelector('.session-rename-input')
  if (input) {
    input.focus()
    input.select()
  }
}

// 确认重命名
const confirmRename = (s) => {
  if (renamingId.value !== s.id) return
  const newTitle = renameValue.value.trim()
  if (newTitle && newTitle !== s.title) {
    chatStore.renameSession(s.id, newTitle)
  }
  renamingId.value = null
  renameValue.value = ''
}

// 取消重命名
const cancelRename = () => {
  renamingId.value = null
  renameValue.value = ''
}

const logout = () => {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  refreshUsage()
})

onBeforeUnmount(() => {
  if (toastTimer) clearTimeout(toastTimer)
})

// 暴露给父组件刷新用量（发消息后调用）
defineExpose({ refreshUsage })
</script>

<style scoped>
.session-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: var(--bg-elevated);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: sticky;
  top: 0;
}

/* ---------- 顶部新对话 ---------- */
.sidebar-header {
  padding: 14px;
  border-bottom: 1px solid var(--border-subtle);
}
.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border-radius: 10px;
  font-size: 0.875rem;
  font-weight: 500;
  color: white;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  box-shadow: 0 2px 8px rgba(79,70,229,0.2);
  transition: all 0.2s;
}
.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(79,70,229,0.3);
}

/* ---------- 会话列表 ---------- */
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: background 0.15s, color 0.15s;
  position: relative;
  overflow: hidden;
}
/* 激活色带：平时透明，激活时显色 */
.session-item .active-bar {
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: transparent;
  transition: background 0.2s;
}
.session-item:hover {
  background: var(--bg-base);
  color: var(--text-primary);
}
/* 激活态：更明显的高亮 */
.session-item.active {
  background: var(--accent-bg);
  color: var(--accent);
  font-weight: 600;
}
.session-item.active .active-bar {
  background: var(--accent);
}

/* 会话类型图标（带底色圆形） */
.session-icon-wrap {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--bg-base);
  transition: background 0.2s, color 0.2s;
  color: var(--text-tertiary);
}
.session-item:hover .session-icon-wrap {
  background: rgba(79,70,229,0.06);
  color: var(--text-secondary);
}
.session-item.active .session-icon-wrap {
  background: rgba(79,70,229,0.12);
  color: var(--accent);
}
/* 网页搜索：蓝色调 */
.session-item.cap-webSearch .session-icon-wrap { background: rgba(59,130,246,0.08); color: #3b82f6; }
.session-item.cap-webSearch.active .session-icon-wrap { background: rgba(59,130,246,0.15); color: #2563eb; }
.session-item.cap-webSearch.active .active-bar { background: #3b82f6; }
/* 知识库：青色调 */
.session-item.cap-knowledgeBase .session-icon-wrap { background: rgba(6,182,212,0.08); color: #0891b2; }
.session-item.cap-knowledgeBase.active .session-icon-wrap { background: rgba(6,182,212,0.15); color: #0e7490; }
.session-item.cap-knowledgeBase.active .active-bar { background: #0891b2; }
/* 双重能力：紫色调 */
.session-item.cap-both .session-icon-wrap { background: rgba(139,92,246,0.08); color: #8b5cf6; }
.session-item.cap-both.active .session-icon-wrap { background: rgba(139,92,246,0.15); color: #7c3aed; }
.session-item.cap-both.active .active-bar { background: #8b5cf6; }

.session-icon {
  flex-shrink: 0;
}

.session-title {
  flex: 1;
  font-size: 0.8125rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* 重命名输入框 */
.session-rename-input {
  flex: 1;
  min-width: 0;
  font-size: 0.8125rem;
  font-family: inherit;
  padding: 2px 6px;
  border: 1.5px solid var(--accent);
  border-radius: 6px;
  background: white;
  color: var(--text-primary);
  outline: none;
  box-shadow: 0 0 0 3px rgba(79,70,229,0.08);
}

/* hover 操作按钮容器 */
.session-actions {
  display: none;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.session-item:hover .session-actions {
  display: flex;
}
.session-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: var(--text-tertiary);
  transition: color 0.15s, background 0.15s;
}
.session-action-btn:hover {
  color: var(--accent);
  background: rgba(79,70,229,0.08);
}
.session-action-btn.session-delete:hover {
  color: #dc2626;
  background: rgba(220,38,38,0.08);
}

.empty-tip {
  padding: 24px 12px;
  text-align: center;
  font-size: 0.8125rem;
  color: var(--text-tertiary);
}

/* ---------- 底部 ---------- */
.sidebar-footer {
  border-top: 1px solid var(--border-subtle);
  padding: 12px;
}
.usage-box {
  background: var(--bg-base);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.usage-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.75rem;
  padding: 3px 0;
}
.usage-label {
  color: var(--text-tertiary);
}
.usage-value {
  font-family: var(--font-mono);
  color: var(--text-secondary);
  font-weight: 500;
}
.usage-value.near {
  color: #d97706;
}
.user-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
}
.user-avatar {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #818cf8, #6366f1);
  color: white;
  font-size: 0.75rem;
  font-weight: 600;
  flex-shrink: 0;
}
.user-name {
  flex: 1;
  font-size: 0.8125rem;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: var(--text-tertiary);
  transition: all 0.2s;
}
.logout-btn:hover {
  color: #dc2626;
  background: rgba(220,38,38,0.08);
}

/* ---------- 删除确认 ---------- */
.delete-overlay {
  position: fixed;
  inset: 0;
  background: rgba(20,20,30,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(4px);
  animation: overlayIn 0.18s ease;
}
@keyframes overlayIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.delete-dialog {
  background: white;
  border-radius: 14px;
  padding: 20px;
  width: 90%;
  max-width: 360px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.15);
  animation: dialogIn 0.2s ease;
}
@keyframes dialogIn {
  from { opacity: 0; transform: scale(0.95) translateY(10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
.delete-dialog p {
  font-size: 0.875rem;
  color: var(--text-primary);
  margin-bottom: 16px;
  line-height: 1.6;
}
.delete-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.btn-cancel, .btn-danger {
  font-size: 0.8125rem;
  padding: 7px 16px;
  border-radius: 8px;
  cursor: pointer;
  border: none;
  transition: background 0.2s;
}
.btn-cancel {
  background: var(--bg-base);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
}
.btn-cancel:hover { background: #e5e7eb; }
.btn-danger {
  background: #dc2626;
  color: white;
}
.btn-danger:hover { background: #b91c1c; }
.btn-danger:disabled {
  background: #fca5a5;
  cursor: not-allowed;
}

/* ---------- 操作结果 toast ---------- */
.action-toast {
  position: fixed;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  border-radius: 10px;
  font-size: 0.8125rem;
  z-index: 10000;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  animation: toastIn 0.25s ease;
}
.action-toast.success {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}
.action-toast.error {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}
@keyframes toastIn {
  from { opacity: 0; transform: translateX(-50%) translateY(10px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

/* toast 淡入淡出过渡 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ---------- 响应式 ---------- */
@media (max-width: 768px) {
  .session-sidebar { width: 220px; }
}
</style>
