<template>
  <!--
    ReActSteps — 推理步骤时间线
    用法：<ReActSteps :cycles="cycles" />

    cycles 数据结构：
    [
      {
        round: 1,                        // 轮次编号
        thought: { content: "..." },     // 思考内容
        action: {                        // 工具调用
          tool: "searchWeb",
          toolLabel: "网页搜索",
          params: { query: "..." },
          expanded: false
        },
        observation: {                   // 观察结果
          summary: "...",
          rawResult: "{\"items\":[...]}",
          expanded: false
        }
      },
      ...
    ]
  -->
  <div class="react-timeline">
    <div
      v-for="(cycle, idx) in cycles"
      :key="idx"
      class="react-cycle"
      :style="{ animationDelay: idx * 0.15 + 's' }"
    >
      <!-- 轮次标签 -->
      <div class="cycle-header">
        <span class="cycle-dot" />
        <span class="cycle-label">第 {{ cycle.round }} 轮推理</span>
      </div>

      <div class="cycle-body">
        <!-- 1. Thought -->
        <div class="step-item step-thought">
          <div class="step-marker">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 17h.01"/>
            </svg>
          </div>
          <div class="step-body">
            <span class="step-tag">Thought</span>
            <p class="step-text">{{ stripMarkdown(cycle.thought.content) }}</p>
          </div>
        </div>

        <!-- 2. Action -->
        <div class="step-item step-action">
          <div class="step-marker">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="16 18 22 12 16 6"/>
              <polyline points="8 6 2 12 8 18"/>
            </svg>
          </div>
          <div class="step-body">
            <span class="step-tag">Action</span>
            <div class="action-card" :class="{ expanded: cycle.action.expanded }">
              <button class="action-card-header" @click="cycle.action.expanded = !cycle.action.expanded">
                <span class="action-tool-icon" :style="{ background: toolColor(cycle.action.tool) }">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="11" cy="11" r="8"/>
                    <path d="M21 21l-4.35-4.35"/>
                  </svg>
                </span>
                <span class="action-tool-name">{{ cycle.action.toolLabel }}</span>
                <span class="action-toggle">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" :class="{ rotated: cycle.action.expanded }">
                    <polyline points="6 9 12 15 18 9"/>
                  </svg>
                </span>
              </button>
              <transition name="expand">
                <div v-if="cycle.action.expanded" class="action-card-body">
                  <div class="json-block">
                    <pre><code>{{ formatJson(cycle.action.params) }}</code></pre>
                  </div>
                </div>
              </transition>
            </div>
          </div>
        </div>

        <!-- 3. Observation -->
        <div class="step-item step-observation">
          <div class="step-marker">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </div>
          <div class="step-body">
            <span class="step-tag">Observation</span>
            <div class="observation-card" :class="{ expanded: cycle.observation.expanded }">
              <p class="observation-summary">{{ stripMarkdown(cycle.observation.summary) }}</p>
              <button
                v-if="cycle.observation.rawResult"
                class="observation-toggle"
                @click="cycle.observation.expanded = !cycle.observation.expanded"
              >
                {{ cycle.observation.expanded ? '收起返回结果' : '查看返回结果' }}
              </button>
              <transition name="expand">
                <div v-if="cycle.observation.expanded" class="observation-body">
                  <div class="json-block">
                    <pre><code>{{ formatJson(cycle.observation.rawResult) }}</code></pre>
                  </div>
                </div>
              </transition>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 最终答案 -->
    <div v-if="finalAnswer" class="react-final">
      <div class="final-divider">
        <span>最终回答</span>
      </div>
      <div class="final-content">{{ stripMarkdown(finalAnswer) }}</div>
    </div>
  </div>
</template>

<script setup>
import { stripMarkdown } from '@/utils/markdown'

// ReActSteps — 接收结构化推理数据并渲染时间线
// 仅做展示，不含业务逻辑

const props = defineProps({
  cycles: {
    type: Array,
    default: () => []
  },
  finalAnswer: {
    type: String,
    default: ''
  }
})

// 工具对应主题色
const toolColors = {
  searchWeb: '#2563eb',
  readFile: '#0891b2',
  writeFile: '#ca8a04',
  searchKnowledge: '#059669',
  listFiles: '#7c3aed',
  executeCode: '#dc2626',
  default: '#2563eb'
}

const toolColor = (tool) => toolColors[tool] || toolColors.default

// JSON 格式化
const formatJson = (val) => {
  if (!val) return ''
  if (typeof val === 'string') {
    try { return JSON.stringify(JSON.parse(val), null, 2) } catch { return val }
  }
  return JSON.stringify(val, null, 2)
}
</script>

<style scoped>
/* =============================================
   ReActSteps — 推理时间线
   ============================================= */

.react-timeline {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px 0;
}

/* ---------- 轮次 ---------- */
.react-cycle {
  animation: fadeSlideIn 0.5s ease-out both;
}

.cycle-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.cycle-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12);
}

.cycle-label {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 500;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

/* ---------- 三阶段列表 ---------- */
.cycle-body {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-left: 3px;
  padding-left: 16px;
  border-left: 2px solid var(--border-subtle);
}

.step-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  position: relative;
}

.step-item:last-child {
  padding-bottom: 0;
}

/* 标记点 —— 覆盖连接线 */
.step-marker {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: absolute;
  left: -31px;
  background: var(--bg-elevated);
  border: 2px solid var(--border-subtle);
  color: var(--text-tertiary);
}

.step-thought .step-marker {
  color: #3b82f6;
  border-color: rgba(59, 130, 246, 0.3);
  background: rgba(59, 130, 246, 0.06);
}

.step-action .step-marker {
  color: #0891b2;
  border-color: rgba(8, 145, 178, 0.3);
  background: rgba(8, 145, 178, 0.06);
}

.step-observation .step-marker {
  color: #059669;
  border-color: rgba(5, 150, 105, 0.3);
  background: rgba(5, 150, 105, 0.06);
}

.step-body {
  flex: 1;
  min-width: 0;
}

.step-tag {
  display: inline-block;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 6px;
}

.step-thought .step-tag {
  background: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
}

.step-action .step-tag {
  background: rgba(8, 145, 178, 0.08);
  color: #0891b2;
}

.step-observation .step-tag {
  background: rgba(5, 150, 105, 0.08);
  color: #059669;
}

.step-text {
  font-family: var(--font-body);
  font-size: 0.875rem;
  line-height: 1.65;
  color: var(--text-secondary);
}

/* ---------- Action 卡片 ---------- */
.action-card {
  margin-top: 4px;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  background: rgba(8, 145, 178, 0.03);
  overflow: hidden;
}

.action-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.action-card-header:hover {
  background: rgba(8, 145, 178, 0.05);
}

.action-tool-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-tool-name {
  font-family: var(--font-body);
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
}

.action-toggle {
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
}

.action-toggle svg {
  transition: transform 0.2s;
}

.action-toggle svg.rotated {
  transform: rotate(180deg);
}

.action-card-body {
  padding: 0 14px 14px;
}

/* ---------- Observation ---------- */
.observation-card {
  margin-top: 4px;
}

.observation-summary {
  font-family: var(--font-body);
  font-size: 0.875rem;
  line-height: 1.65;
  color: var(--text-secondary);
  padding: 10px 14px;
  background: rgba(5, 150, 105, 0.04);
  border: 1px solid rgba(5, 150, 105, 0.1);
  border-radius: 8px;
}

.observation-toggle {
  display: inline-block;
  font-family: var(--font-body);
  font-size: 0.75rem;
  color: #059669;
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px 0;
  font-weight: 500;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.observation-toggle:hover {
  color: #047857;
}

.observation-body {
  margin-top: 8px;
}

/* ---------- JSON 块 ---------- */
.json-block {
  border-radius: 6px;
  background: #1e2236;
  overflow: hidden;
}

.json-block pre {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
}

.json-block code {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  line-height: 1.6;
  color: #c7d2fe;
  white-space: pre;
}

/* ---------- 最终回答 ---------- */
.react-final {
  margin-top: 4px;
}

.final-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.final-divider::before,
.final-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-subtle);
}

.final-divider span {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--accent);
}

.final-content {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  line-height: 1.7;
  color: var(--text-primary);
  padding: 16px 20px;
  background: var(--accent-bg);
  border: 1px solid rgba(37, 99, 235, 0.1);
  border-radius: 10px;
  border-left: 3px solid var(--accent);
}

/* ---------- 展开动画 ---------- */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}
.expand-enter-to,
.expand-leave-from {
  max-height: 600px;
}

/* ---------- 入场动画 ---------- */
@keyframes fadeSlideIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
