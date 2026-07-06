<template>
  <div class="auth-page">
    <!-- 背景柔光 -->
    <div class="bg-glow" aria-hidden="true">
      <span class="blob blob-a" />
      <span class="blob blob-b" />
    </div>

    <!-- 返回首页 -->
    <button class="back-home" @click="router.push('/')">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M19 12H5M12 19l-7-7 7-7" />
      </svg>
      <span>返回首页</span>
    </button>

    <!-- 卡片 -->
    <div class="auth-card">
      <!-- Header -->
      <div class="auth-header">
        <div class="logo" aria-hidden="true">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
          </svg>
        </div>
        <h1 class="auth-title">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h1>
        <p class="auth-subtitle">{{ mode === 'login' ? '登录开启你的 AI 智能体之旅' : '注册即可体验 AI 超级智能体' }}</p>
      </div>

      <!-- Tabs -->
      <div class="auth-tabs">
        <button
          :class="['tab', { active: mode === 'login' }]"
          @click="switchMode('login')"
        >登录</button>
        <button
          :class="['tab', { active: mode === 'register' }]"
          @click="switchMode('register')"
        >注册</button>
        <span class="tab-indicator" :style="{ transform: mode === 'login' ? 'translateX(0)' : 'translateX(100%)' }" />
      </div>

      <!-- Form -->
      <form class="auth-form" @submit.prevent="handleSubmit">
        <!-- 用户名 -->
        <div class="field">
          <label class="field-label">用户名</label>
          <div class="field-control" :class="{ focused: focused === 'username', 'has-error': !!error }">
            <span class="field-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </span>
            <input
              v-model.trim="form.username"
              type="text"
              placeholder="请输入用户名"
              autocomplete="username"
              @focus="focused = 'username'"
              @blur="focused = ''"
            />
          </div>
        </div>

        <!-- 密码 -->
        <div class="field">
          <label class="field-label">密码</label>
          <div class="field-control" :class="{ focused: focused === 'password', 'has-error': !!error }">
            <span class="field-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
            </span>
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              :placeholder="mode === 'register' ? '至少 6 位' : '请输入密码'"
              :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
              @focus="focused = 'password'"
              @blur="focused = ''"
            />
            <button type="button" class="eye-btn" @click="showPassword = !showPassword" tabindex="-1">
              <svg v-if="showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            </button>
          </div>
        </div>

        <!-- 确认密码（仅注册） -->
        <transition name="slide">
          <div v-if="mode === 'register'" class="field">
            <label class="field-label">确认密码</label>
            <div class="field-control" :class="{ focused: focused === 'confirm', 'has-error': !!error }">
              <span class="field-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
              </span>
              <input
                v-model="form.confirmPassword"
                type="password"
                placeholder="再次输入密码"
                autocomplete="new-password"
                @focus="focused = 'confirm'"
                @blur="focused = ''"
              />
            </div>
          </div>
        </transition>

        <!-- 错误提示 -->
        <transition name="fade">
          <div v-if="error" class="auth-error">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{{ error }}</span>
          </div>
        </transition>

        <!-- 提交按钮 -->
        <button type="submit" class="auth-submit" :disabled="loading">
          <span v-if="loading" class="spinner" />
          <span>{{ mode === 'login' ? '登 录' : '注 册' }}</span>
        </button>
      </form>

      <!-- 切换 -->
      <p class="auth-footer">
        <template v-if="mode === 'login'">
          还没有账号？<a @click="switchMode('register')">立即注册</a>
        </template>
        <template v-else>
          已有账号？<a @click="switchMode('login')">直接登录</a>
        </template>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useHead } from '@vueuse/head'
import { login, register } from '../api'
import { useUserStore } from '../stores/userStore'
import { useChatStore } from '../stores/chatStore'

useHead({
  title: '登录 - AI 超级智能体',
  meta: [{ name: 'description', content: '登录或注册以使用 AI 超级智能体' }]
})

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const mode = ref('login') // login | register
const loading = ref(false)
const error = ref('')
const showPassword = ref(false)
const focused = ref('')

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const switchMode = (m) => {
  mode.value = m
  error.value = ''
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
}

const handleSubmit = async () => {
  error.value = ''

  if (!form.username) {
    error.value = '请输入用户名'
    return
  }
  if (!form.password || form.password.length < 6) {
    error.value = '密码至少 6 位'
    return
  }
  if (mode.value === 'register' && form.password !== form.confirmPassword) {
    error.value = '两次密码不一致'
    return
  }

  loading.value = true
  try {
    const apiFn = mode.value === 'login' ? login : register
    const res = await apiFn(form.username, form.password)
    if (res.success) {
      chatStore.reset()
      userStore.setAuth(res.token, res.username)
      const redirect = route.query.redirect || '/'
      router.push(redirect)
    } else {
      error.value = res.message || '操作失败，请重试'
    }
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '网络错误，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background: var(--bg-base);
  background-image: radial-gradient(ellipse 80% 60% at 50% 0%, rgba(79,70,229,0.06), transparent 60%);
}

/* ---------- 背景光晕 ---------- */
.bg-glow {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.22;
}
.blob-a {
  width: 420px; height: 420px;
  top: -120px; left: -80px;
  background: radial-gradient(circle, #6366f1 0%, transparent 70%);
  animation: drift 20s ease-in-out infinite alternate;
}
.blob-b {
  width: 380px; height: 380px;
  bottom: -120px; right: -80px;
  background: radial-gradient(circle, #a78bfa 0%, transparent 70%);
  animation: drift 24s ease-in-out infinite alternate-reverse;
}

/* ---------- 返回首页 ---------- */
.back-home {
  position: absolute;
  top: 24px;
  left: 24px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 100px;
  font-size: 0.8125rem;
  color: var(--text-secondary);
  background: white;
  border: 1px solid var(--border-subtle);
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  transition: all 0.2s;
}
.back-home:hover {
  color: var(--accent);
  border-color: var(--border-active);
}

/* ---------- 卡片 ---------- */
.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 40px 36px 32px;
  background: white;
  border: 1px solid var(--border-subtle);
  border-radius: 20px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.06);
  animation: card-in 0.4s ease;
}
@keyframes card-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ---------- Header ---------- */
.auth-header {
  text-align: center;
  margin-bottom: 28px;
}
.logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px; height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, #818cf8, #6366f1);
  color: white;
  margin-bottom: 16px;
  box-shadow: 0 6px 18px rgba(99,102,241,0.32);
}
.auth-title {
  font-family: var(--font-display);
  font-size: 1.625rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.auth-subtitle {
  font-size: 0.875rem;
  color: var(--text-tertiary);
}

/* ---------- Tabs ---------- */
.auth-tabs {
  position: relative;
  display: flex;
  margin-bottom: 28px;
  background: var(--bg-base);
  border-radius: 12px;
  padding: 4px;
}
.tab {
  flex: 1;
  padding: 9px 0;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-tertiary);
  border-radius: 9px;
  transition: color 0.25s;
  position: relative;
  z-index: 1;
}
.tab.active {
  color: var(--accent);
}
.tab-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: white;
  border-radius: 9px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ---------- Field ---------- */
.field {
  margin-bottom: 16px;
}
.field-label {
  display: block;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 7px;
}
.field-control {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  height: 46px;
  background: var(--bg-base);
  border: 1.5px solid transparent;
  border-radius: 12px;
  transition: all 0.2s;
}
.field-control.focused {
  border-color: var(--accent);
  background: white;
  box-shadow: 0 0 0 4px var(--accent-soft);
}
.field-control.has-error {
  border-color: #ef4444;
}
.field-icon {
  display: flex;
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.field-control.focused .field-icon {
  color: var(--accent);
}
.field-control input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.9375rem;
  color: var(--text-primary);
  font-family: inherit;
}
.field-control input::placeholder {
  color: var(--text-tertiary);
}
.eye-btn {
  display: flex;
  color: var(--text-tertiary);
  padding: 4px;
  flex-shrink: 0;
}
.eye-btn:hover {
  color: var(--text-secondary);
}

/* ---------- 错误提示 ---------- */
.auth-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: rgba(239,68,68,0.06);
  border: 1px solid rgba(239,68,68,0.2);
  border-radius: 10px;
  font-size: 0.8125rem;
  color: #dc2626;
}

/* ---------- 提交按钮 ---------- */
.auth-submit {
  width: 100%;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 0.9375rem;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(79,70,229,0.28);
  transition: all 0.2s;
}
.auth-submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 26px rgba(79,70,229,0.36);
}
.auth-submit:active:not(:disabled) {
  transform: translateY(0);
}
.auth-submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.spinner {
  width: 16px; height: 16px;
  border: 2px solid rgba(255,255,255,0.4);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------- Footer ---------- */
.auth-footer {
  text-align: center;
  margin-top: 22px;
  font-size: 0.8125rem;
  color: var(--text-tertiary);
}
.auth-footer a {
  color: var(--accent);
  font-weight: 500;
  cursor: pointer;
}
.auth-footer a:hover {
  text-decoration: underline;
}

/* ---------- 动画 ---------- */
@keyframes drift {
  0%   { transform: translate(0, 0) scale(1); }
  100% { transform: translate(30px, 40px) scale(1.08); }
}
.slide-enter-active, .slide-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.slide-enter-from, .slide-leave-to {
  opacity: 0;
  max-height: 0;
  margin-bottom: 0;
}
.slide-enter-to, .slide-leave-from {
  max-height: 80px;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ---------- 响应式 ---------- */
@media (max-width: 480px) {
  .auth-card { padding: 32px 24px 24px; border-radius: 16px; }
  .auth-title { font-size: 1.375rem; }
  .back-home { top: 16px; left: 16px; }
}
</style>
