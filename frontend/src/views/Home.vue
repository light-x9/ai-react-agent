<template>
  <div class="home">
    <!-- 背景柔光 -->
    <div class="bg-glow" aria-hidden="true">
      <span class="blob blob-a" />
      <span class="blob blob-b" />
    </div>

    <!-- ========== Hero ========== -->
    <section class="hero">
      <!-- ReAct 三环概念图 -->
      <div class="hero-badge" aria-hidden="true">
        <svg viewBox="0 0 200 80" fill="none" xmlns="http://www.w3.org/2000/svg" class="loop-svg">
          <defs>
            <linearGradient id="loop-grad" x1="0" y1="0" x2="200" y2="0" gradientUnits="userSpaceOnUse">
              <stop offset="0%" stop-color="#3b82f6" />
              <stop offset="50%" stop-color="#2563eb" />
              <stop offset="100%" stop-color="#60a5fa" />
            </linearGradient>
          </defs>
          <!-- 三个相交圆环，表示 ReAct 循环 -->
          <circle cx="50" cy="40" r="28" stroke="url(#loop-grad)" stroke-width="2.5" stroke-dasharray="6 4" opacity="0.7" />
          <circle cx="100" cy="40" r="28" stroke="url(#loop-grad)" stroke-width="2.5" stroke-dasharray="6 4" opacity="0.7" />
          <circle cx="150" cy="40" r="28" stroke="url(#loop-grad)" stroke-width="2.5" stroke-dasharray="6 4" opacity="0.7" />
          <!-- 中心图标 -->
          <text x="50" y="45" text-anchor="middle" fill="#2563eb" font-size="16" font-family="Inter, sans-serif" font-weight="600">T</text>
          <text x="100" y="45" text-anchor="middle" fill="#2563eb" font-size="16" font-family="Inter, sans-serif" font-weight="600">A</text>
          <text x="150" y="45" text-anchor="middle" fill="#2563eb" font-size="16" font-family="Inter, sans-serif" font-weight="600">O</text>
          <!-- 连接线 -->
          <path d="M78 40 H122" stroke="#2563eb" stroke-width="1.5" stroke-dasharray="3 3" opacity="0.5" />
          <path d="M85 32 C90 20, 110 20, 115 32" stroke="#2563eb" stroke-width="1" fill="none" opacity="0.3" />
          <path d="M85 48 C90 60, 110 60, 115 48" stroke="#2563eb" stroke-width="1" fill="none" opacity="0.3" />
        </svg>
      </div>

      <h1 class="hero-title">
        看得见思考过程的<br />
        <span class="hero-title-accent">AI Agent</span>
      </h1>
      <p class="hero-subtitle">
        基于 ReAct 推理框架（Thought → Action → Observation 循环），
        前端实时可视化每一步思考与工具调用，让 AI 的推理过程透明可见。
      </p>

      <!-- 技术点标签 -->
      <div class="tech-tags">
        <span class="tech-tag">ReAct 推理引擎</span>
        <span class="tech-tag">MCP 工具协议</span>
        <span class="tech-tag">RAG 知识检索</span>
      </div>
    </section>

    <!-- ========== 能力卡片 ========== -->
    <section class="features">
      <article class="feature">
        <div class="feature-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
          </svg>
        </div>
        <h3 class="feature-title">思维链可视化</h3>
        <p class="feature-text">Thought / Action / Observation 三阶段时间线，渐进式动画呈现，完整展示 AI 推理路径。</p>
      </article>

      <article class="feature">
        <div class="feature-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="3" width="20" height="14" rx="2" />
            <path d="M8 21h8M12 17v4" />
          </svg>
        </div>
        <h3 class="feature-title">工具调用面板</h3>
        <p class="feature-text">每次 Action 以卡片形式展示工具名称与参数，支持展开查看完整请求和响应 JSON。</p>
      </article>

      <article class="feature">
        <div class="feature-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            <path d="M8 7h8M8 11h6" />
          </svg>
        </div>
        <h3 class="feature-title">RAG 知识库</h3>
        <p class="feature-text">上传文档自动分块索引，Query Rewrite 优化检索，AI 结合知识库回答专业领域问题。</p>
      </article>
    </section>

    <!-- ========== 行动入口 ========== -->
    <section class="cta">
      <button class="cta-card" @click="goSuperAgent">
        <span class="cta-card-inner">
          <span class="cta-card-text">
            <span class="cta-tag">立即体验</span>
            <span class="cta-label">AI 超级智能体</span>
          </span>
          <span class="cta-arrow" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </span>
        </span>
        <span class="cta-card-shadow" aria-hidden="true" />
      </button>
    </section>

    <!-- ========== Footer ========== -->
    <footer class="footer">
      <p><small>&copy; {{ currentYear }} AI 超级智能体应用平台 · ReAct Visualization Demo</small></p>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()
const currentYear = computed(() => new Date().getFullYear())

useHead({
  title: 'LightManus-多工具AI智能体平台',
  meta: [
    {
      name: 'description',
      content: '基于 ReAct 推理框架的 AI Agent 对话平台。前端实时可视化 Thought → Action → Observation 循环，支持 MCP 工具调用与 RAG 知识库检索。'
    }
  ]
})

const router = useRouter()
const navigateTo = (path) => router.push(path)
const goSuperAgent = () => router.push(userStore.isLoggedIn ? '/super-agent' : '/login')
</script>

<style scoped>
/* =============================================
   Home — 浅色清爽风 + ReAct 视觉主题
   ============================================= */

.home {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
  overflow: hidden;
  background: var(--bg-base);
  background-image:
    radial-gradient(ellipse 80% 60% at 50% 0%, rgba(37, 99, 235,0.06), transparent 60%);
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
  opacity: 0.2;
}
.blob-a {
  width: 480px; height: 480px;
  top: -120px; left: -80px;
  background: radial-gradient(circle, #2563eb 0%, transparent 70%);
  animation: drift 20s ease-in-out infinite alternate;
}
.blob-b {
  width: 400px; height: 400px;
  bottom: -100px; right: -60px;
  background: radial-gradient(circle, #60a5fa 0%, transparent 70%);
  animation: drift 24s ease-in-out infinite alternate-reverse;
}

/* ---------- Hero ---------- */
.hero {
  position: relative;
  z-index: 1;
  max-width: 800px;
  width: 100%;
  padding: 100px 0 72px;
  text-align: center;
}

.hero-badge {
  display: inline-block;
  margin-bottom: 28px;
}
.loop-svg {
  width: 160px;
  height: 64px;
}

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(2.5rem, 6vw, 4rem);
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: var(--text-primary);
}

.hero-title-accent {
  background: linear-gradient(135deg, #3b82f6, #2563eb 60%, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  margin-top: 20px;
  font-family: var(--font-body);
  font-size: 1.05rem;
  line-height: 1.75;
  color: var(--text-secondary);
  max-width: 560px;
  margin-left: auto;
  margin-right: auto;
}

.tech-tags {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 28px;
  flex-wrap: wrap;
}

.tech-tag {
  font-family: var(--font-body);
  font-size: 0.8125rem;
  font-weight: 500;
  padding: 6px 16px;
  border-radius: 100px;
  background: var(--bg-elevated);
  color: var(--accent);
  border: 1px solid var(--border-subtle);
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

/* ---------- 能力卡片 ---------- */
.features {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  max-width: 960px;
  width: 100%;
  padding-bottom: 72px;
}

.feature {
  padding: 28px 24px;
  border-radius: 16px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}
.feature:hover {
  border-color: var(--border-active);
  transform: translateY(-4px);
  box-shadow: 0 8px 32px rgba(37, 99, 235,0.08);
}

.feature-icon {
  width: 44px; height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--accent-bg);
  color: var(--accent);
  margin-bottom: 14px;
}

.feature-title {
  font-family: var(--font-body);
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.feature-text {
  font-family: var(--font-body);
  font-size: 0.875rem;
  line-height: 1.6;
  color: var(--text-tertiary);
}

/* ---------- CTA ---------- */
.cta {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 480px;
  padding-bottom: 88px;
}

.cta-card {
  position: relative;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
}

.cta-card-inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 32px;
  border-radius: 20px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  transition: all 0.25s;
  z-index: 1;
}
.cta-card:hover .cta-card-inner {
  border-color: var(--accent);
  transform: translateX(4px);
  box-shadow: 0 8px 32px rgba(37, 99, 235,0.12);
}

.cta-card-shadow {
  position: absolute;
  inset: 0;
  border-radius: 20px;
  z-index: 0;
  opacity: 0;
  transition: opacity 0.4s;
  box-shadow: 0 8px 40px -8px rgba(37, 99, 235,0.25);
  pointer-events: none;
}
.cta-card:hover .cta-card-shadow { opacity: 1; }

.cta-card-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cta-tag {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--accent);
}

.cta-label {
  font-family: var(--font-body);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.cta-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px; height: 44px;
  border-radius: 50%;
  background: var(--accent-bg);
  color: var(--accent);
  transition: all 0.25s;
}
.cta-card:hover .cta-arrow {
  transform: translateX(4px);
  background: var(--accent);
  color: white;
}

/* ---------- Footer ---------- */
.footer {
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 24px 0;
  text-align: center;
  border-top: 1px solid var(--border-subtle);
  margin-top: auto;
}
.footer small {
  font-size: 0.8125rem;
  color: var(--text-tertiary);
}

/* ---------- 动画 ---------- */
@keyframes drift {
  0%   { transform: translate(0, 0) scale(1); }
  100% { transform: translate(30px, 40px) scale(1.08); }
}

/* ---------- 响应式 ---------- */
@media (max-width: 768px) {
  .home { padding: 0 16px; }
  .hero { padding: 64px 0 56px; }
  .hero-title { font-size: clamp(2rem, 8vw, 2.5rem); }
  .features { grid-template-columns: 1fr; gap: 16px; padding-bottom: 56px; }
  .cta-card-inner { padding: 24px 20px; }
  .cta { max-width: 100%; }
}
@media (max-width: 480px) {
  .home { padding: 0 12px; }
  .hero { padding: 48px 0 40px; }
  .hero-badge { margin-bottom: 20px; }
  .loop-svg { width: 120px; height: 48px; }
  .hero-title { font-size: clamp(1.75rem, 7vw, 2rem); }
  .hero-subtitle { font-size: 0.875rem; line-height: 1.6; }
  .tech-tags { gap: 8px; }
  .tech-tag { font-size: 0.75rem; padding: 5px 12px; }
  .feature { padding: 20px 16px; }
  .feature-icon { width: 36px; height: 36px; }
  .feature-title { font-size: 0.9375rem; }
  .feature-text { font-size: 0.8125rem; }
  .cta-card-inner { padding: 20px 16px; }
  .cta-label { font-size: 1.125rem; }
  .cta-arrow { width: 36px; height: 36px; }
  .footer { padding: 16px 0; }
  .footer small { font-size: 0.75rem; }
}
</style>
