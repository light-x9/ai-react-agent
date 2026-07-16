<template>
  <div class="user-center-container">
    <!-- ====== Header ====== -->
    <header class="header">
      <button class="back-button" @click="goBack">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        返回
      </button>
      <h1 class="page-title">个人中心</h1>
      <div class="header-right"></div>
    </header>

    <!-- ====== Content ====== -->
    <main class="content">
      <!-- 加载中 -->
      <div v-if="loading" class="loading-wrap">
        <span class="spinner"></span>
        <span>加载中...</span>
      </div>

      <template v-else>
        <!-- 个人信息卡 -->
        <section class="card profile-card">
          <div class="avatar-wrap">
            <img v-if="form.avatar" :src="form.avatar" alt="头像" class="avatar-img" @error="avatarLoadError" />
            <div v-else class="avatar-default">{{ initial }}</div>
          </div>
          <div class="profile-identity">
            <div class="name-row">
              <input
                v-if="editingNickname"
                v-model="form.nickname"
                class="info-input nickname-input"
                maxlength="50"
                placeholder="给自己起个名字"
                @blur="saveNickname"
                @keyup.enter="saveNickname"
                @keyup.escape="cancelEditNickname"
                ref="nicknameInput"
              />
              <span v-else class="display-name">
                {{ profile.nickname || profile.username }}
              </span>
              <button v-if="!editingNickname" class="edit-icon-btn" @click="startEditNickname" title="修改昵称">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
              </button>
              <span v-else class="editing-actions">
                <button class="action-btn save" @click="saveNickname" :disabled="saving">{{ saving ? '...' : '保存' }}</button>
                <button class="action-btn cancel" @click="cancelEditNickname">取消</button>
              </span>
            </div>
            <div class="login-hint">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
              登录用 @{{ profile.username }}，昵称仅用于展示
            </div>
          </div>
        </section>

        <!-- 用量统计 -->
        <section class="card stats-card">
          <div class="stats-grid">
            <div class="stat-item">
              <span class="stat-value">{{ stats.conversationCount }}</span>
              <span class="stat-label">对话总数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.chatUsed }}/{{ stats.chatLimit }}</span>
              <span class="stat-label">今日对话</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.fileCount }}</span>
              <span class="stat-label">知识库文件</span>
            </div>
          </div>
        </section>

        <!-- 收藏对话 -->
        <section class="card">
          <h2 class="card-title">收藏对话</h2>
          <div v-if="favoriteSessions.length === 0" class="empty-favorites">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" /></svg>
            还没有收藏对话，点击会话列表的 ⭐ 来收藏
          </div>
          <ul v-else class="favorite-list">
            <li
              v-for="s in favoriteSessions"
              :key="s.id"
              class="favorite-item"
              @click="goToSession(s.id)"
            >
              <span class="favorite-title">{{ s.title || '新对话' }}</span>
              <button class="unfav-btn" @click.stop="handleToggleFavorite(s)" title="取消收藏">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" /></svg>
              </button>
            </li>
          </ul>
        </section>

        <!-- 密码修改：默认折叠，点开才显示表单 -->
        <section class="card">
          <div class="card-header-row" @click="showPasswordForm = !showPasswordForm">
            <h2 class="card-title">修改密码</h2>
            <button class="expand-btn" :class="{ open: showPasswordForm }">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </button>
          </div>
          <transition name="expand">
            <div v-show="showPasswordForm" class="password-form">
              <div class="form-group">
                <label class="form-label">原密码</label>
                <input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  class="form-input"
                  placeholder="请输入当前密码"
                  maxlength="64"
                />
              </div>
              <div class="form-group">
                <label class="form-label">新密码</label>
                <input
                  v-model="passwordForm.newPassword"
                  type="password"
                  class="form-input"
                  placeholder="请设置新密码（至少 6 位）"
                  maxlength="64"
                />
              </div>
              <div class="form-group">
                <label class="form-label">确认新密码</label>
                <input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  class="form-input"
                  placeholder="请再输一次新密码"
                  maxlength="64"
                />
              </div>
              <button class="submit-btn" @click="handleChangePassword" :disabled="changingPassword">
                {{ changingPassword ? '修改中...' : '更新密码' }}
              </button>
            </div>
          </transition>
        </section>

        <!-- 底部注册信息（小字一行） -->
        <p class="account-footerr">注册于 {{ formatDate(profile.createdAt) }}</p>
      </template>
    </main>

    <!-- ====== Toast ====== -->
    <transition name="toast-fade">
      <div v-if="toast.show" class="toast" :class="'toast-' + toast.type">
        {{ toast.message }}
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getProfile, updateProfile, changePassword, listConversations, getUsageToday, listKnowledgeFiles, toggleFavorite } from '@/api'
import { useChatStore } from '@/stores/chatStore'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

// ---- 状态 ----
const loading = ref(true)
const saving = ref(false)
const changingPassword = ref(false)
const editingNickname = ref(false)
const showAvatarModal = ref(false)
const showPasswordForm = ref(false)
const avatarUrlInput = ref('')
const nicknameInput = ref(null)
const profile = reactive({
  username: '',
  nickname: '',
  avatar: '',
  createdAt: ''
})

const form = reactive({
  nickname: '',
  avatar: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const stats = reactive({
  conversationCount: 0,
  chatUsed: 0,
  chatLimit: 100,
  fileCount: 0
})

const toast = reactive({ show: false, message: '', type: 'success' })
let toastTimer = null

const initial = computed(() => {
  return (profile.nickname || profile.username || 'U').charAt(0).toUpperCase()
})

const favoriteSessions = computed(() => {
  return chatStore.sessions.filter(s => s.favorite)
})

const goToSession = (id) => {
  chatStore.activeId = id
  router.push('/')
}

const handleToggleFavorite = async (s) => {
  const res = await toggleFavorite(s.id)
  if (res.success) {
    s.favorite = res.favorite
  }
}

// ---- 方法 ----
const showToast = (message, type = 'success') => {
  clearTimeout(toastTimer)
  toast.message = message
  toast.type = type
  toast.show = true
  toastTimer = setTimeout(() => { toast.show = false }, 2500)
}

const fetchProfile = async () => {
  loading.value = true
  try {
    const res = await getProfile()
    if (res.success) {
      Object.assign(profile, res)
      form.nickname = res.nickname || ''
      form.avatar = res.avatar || ''
    } else {
      showToast(res.message || '获取用户信息失败', 'error')
    }
  } catch (err) {
    showToast('网络错误，请稍后重试', 'error')
  } finally {
    loading.value = false
  }
}

const startEditNickname = async () => {
  editingNickname.value = true
  await nextTick()
  nicknameInput.value?.focus()
}

const cancelEditNickname = () => {
  form.nickname = profile.nickname || ''
  editingNickname.value = false
}

const saveNickname = async () => {
  if (!form.nickname.trim()) {
    showToast('昵称不能为空', 'error')
    return
  }
  saving.value = true
  try {
    const res = await updateProfile({ nickname: form.nickname.trim() })
    if (res.success) {
      profile.nickname = form.nickname.trim()
      editingNickname.value = false
      showToast('昵称已更新')
    } else {
      showToast(res.message || '保存失败', 'error')
    }
  } catch (err) {
    showToast('保存失败，请稍后重试', 'error')
  } finally {
    saving.value = false
  }
}

const saveAvatar = async () => {
  const url = avatarUrlInput.value.trim()
  if (url && !url.match(/^https?:\/\/.+/)) {
    showToast('请输入合法的 http(s) 链接', 'error')
    return
  }
  saving.value = true
  try {
    const res = await updateProfile({ avatar: url })
    if (res.success) {
      profile.avatar = url
      form.avatar = url
      showAvatarModal.value = false
      showToast('头像已更新')
    } else {
      showToast(res.message || '保存失败', 'error')
    }
  } catch (err) {
    showToast('保存失败，请稍后重试', 'error')
  } finally {
    saving.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordForm.oldPassword) {
    showToast('请输入原密码', 'error')
    return
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
    showToast('新密码至少 6 位', 'error')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    showToast('两次输入的新密码不一致', 'error')
    return
  }
  changingPassword.value = true
  try {
    const res = await changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    if (res.success) {
      showToast('密码修改成功，请重新登录')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      // 改完密码等 1.5s 后退出让用户重新登录
      setTimeout(() => {
        userStore.logout()
        router.push('/login')
      }, 1500)
    } else {
      showToast(res.message || '修改失败', 'error')
    }
  } catch (err) {
    showToast('修改失败，请稍后重试', 'error')
  } finally {
    changingPassword.value = false
  }
}

const avatarLoadError = () => {
  // 图片加载失败时回退到默认头像
  profile.avatar = ''
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  try {
    return new Date(dateStr).toLocaleDateString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit'
    }).replace(/\//g, '-')
  } catch {
    return dateStr
  }
}

const goBack = () => router.push('/')

const fetchStats = async () => {
  try {
    const [convRes, usageRes, filesRes] = await Promise.all([
      listConversations(),
      getUsageToday(),
      listKnowledgeFiles()
    ])
    // /conversation → { success, sessions: [...] }
    stats.conversationCount = Array.isArray(convRes?.sessions) ? convRes.sessions.length : 0
    // /usage/today → { chatUsed, chatLimit, searchUsed, searchLimit }
    stats.chatUsed = usageRes?.chatUsed ?? 0
    stats.chatLimit = usageRes?.chatLimit ?? 100
    // /knowledge-base/files → { success, files: [...] }
    stats.fileCount = Array.isArray(filesRes?.files) ? filesRes.files.length : 0
  } catch {
    // 统计失败不影响页面
  }
}

onMounted(() => {
  fetchProfile()
  fetchStats()
})
</script>

<style scoped>
/* =============================================
   UserCenter — 个人中心页
   沿用 SuperAgent 的设计令牌系统
   ============================================= */

.user-center-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-base);
}

/* === Header === */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border-subtle);
  position: sticky;
  top: 0;
  z-index: 10;
  flex-shrink: 0;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  color: var(--text-secondary);
  padding: 6px 10px;
  border-radius: 8px;
  transition: color 0.2s, background 0.2s;
}
.back-button:hover {
  color: var(--accent);
  background: var(--accent-bg);
}

.page-title {
  font-family: var(--font-display);
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.header-right {
  width: 80px;
}

/* === Content === */
.content {
  flex: 1;
  width: 100%;
  max-width: 600px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.loading-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 0;
  color: var(--text-secondary);
  font-size: 0.875rem;
}
.spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid var(--gray-300);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* === 头像 === */
.avatar-wrap {
  position: relative;
  width: 88px;
  height: 88px;
}
.avatar-img,
.avatar-default {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: 700;
  color: white;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  box-shadow: var(--shadow-md);
}
.avatar-edit {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 50%;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s, background 0.2s;
}
.avatar-edit:hover {
  color: var(--accent);
  border-color: var(--border-active);
  background: var(--accent-bg);
}

/* === 卡片 === */
.card {
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}
.card-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle);
}

/* === 统计卡片 === */
.stats-card {
  padding: 20px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  background: var(--bg-base);
  border-radius: var(--radius-md);
}
.stat-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-mono);
}
.stat-label {
  font-size: 0.75rem;
  color: var(--text-tertiary);
}

/* 折叠卡片头部 */
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  margin: -20px;
  padding: 20px;
  transition: background 0.15s;
  border-radius: var(--radius-lg);
}
.card-header-row:hover {
  background: var(--accent-bg);
}
.card-header-row .card-title {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.expand-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: var(--text-tertiary);
  transition: transform 0.2s, color 0.2s;
}
.expand-btn.open {
  transform: rotate(180deg);
}
.expand-btn:hover {
  color: var(--accent);
}

/* 折叠动画 */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.25s ease;
  max-height: 300px;
  overflow: hidden;
  opacity: 1;
}
.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
  margin-top: 0;
}

/* === 个人信息卡 === */
.profile-card {
  padding: 28px 24px 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}
.profile-identity {
  flex: 1;
  min-width: 0;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.display-name {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-display);
}
.nickname-input {
  font-size: 1.25rem !important;
  font-weight: 700;
  max-width: 300px;
}
.login-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 0.75rem;
  color: var(--text-tertiary);
}
.login-hint svg {
  flex-shrink: 0;
}
.edit-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: var(--text-tertiary);
  transition: color 0.2s, background 0.2s;
  flex-shrink: 0;
}
.edit-icon-btn:hover {
  color: var(--accent);
  background: var(--accent-bg);
}
.info-input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  font-size: 0.875rem;
  border: 1px solid var(--border-active);
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  color: var(--text-primary);
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
}
.info-input:focus {
  border-color: var(--accent);
}

.editing-actions {
  display: flex;
  gap: 6px;
}
.action-btn {
  padding: 4px 12px;
  font-size: 0.75rem;
  border-radius: var(--radius-sm);
  transition: background 0.2s, color 0.2s;
  white-space: nowrap;
}
.action-btn.save {
  background: var(--accent);
  color: white;
}
.action-btn.save:hover {
  background: var(--accent-hover);
}
.action-btn.save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.action-btn.cancel {
  color: var(--text-secondary);
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
}
.action-btn.cancel:hover {
  background: var(--gray-100);
}

/* === 密码表单 === */
.password-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-label {
  font-size: 0.8125rem;
  color: var(--text-tertiary);
}
.form-input {
  padding: 8px 12px;
  font-size: 0.875rem;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-base);
  color: var(--text-primary);
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
}
.form-input:focus {
  border-color: var(--accent);
}
.form-hint {
  font-size: 0.75rem;
  color: var(--text-tertiary);
  margin-top: 2px;
}
.submit-btn {
  align-self: flex-end;
  padding: 8px 24px;
  font-size: 0.875rem;
  font-weight: 500;
  color: white;
  background: var(--accent);
  border-radius: var(--radius-sm);
  transition: background 0.2s;
  margin-top: 4px;
}
.submit-btn:hover {
  background: var(--accent-hover);
}
.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.submit-btn.secondary {
  background: var(--bg-base);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
}
.submit-btn.secondary:hover {
  background: var(--gray-100);
}

/* === 头像弹窗 === */
.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  padding: 20px;
}
.modal {
  width: 100%;
  max-width: 420px;
  background: var(--bg-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
}
.modal-header h3 {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.modal-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: var(--text-tertiary);
  transition: color 0.2s, background 0.2s;
}
.modal-close:hover {
  color: var(--text-primary);
  background: var(--gray-100);
}
.modal-body {
  padding: 20px;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--border-subtle);
}

/* === 收藏对话 === */
.empty-favorites {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 12px;
  color: var(--text-tertiary);
  font-size: 0.8125rem;
}
.favorite-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.favorite-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}
.favorite-item:hover {
  background: var(--accent-bg);
}
.favorite-title {
  flex: 1;
  font-size: 0.875rem;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.unfav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: var(--accent);
  flex-shrink: 0;
  transition: background 0.15s;
}
.unfav-btn:hover {
  background: var(--accent-soft);
}

/* === 底部账户信息 === */
.account-footerr {
  text-align: center;
  font-size: 0.75rem;
  color: var(--text-tertiary);
  margin-top: 8px;
}

/* === Toast === */
.toast {
  position: fixed;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  font-size: 0.875rem;
  color: white;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  z-index: 200;
}
.toast-success {
  background: var(--color-success);
}
.toast-error {
  background: var(--color-error);
}
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

/* === 响应式 === */
@media (max-width: 640px) {
  .content {
    padding: 16px 14px 32px;
  }
  .card {
    padding: 16px;
  }
  .info-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
  .info-label {
    width: auto;
  }
}
</style>
