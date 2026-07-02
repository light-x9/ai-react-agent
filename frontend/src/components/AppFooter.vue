<template>
  <footer class="app-footer">
    <p>&copy; {{ currentYear }} AI 超级智能体应用平台</p>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

/**
 * AppFooter — 全局页脚组件
 * 视觉重构仅调整外观；组件输出的 DOM 结构不变。
 */

// 预留：当前年份
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  width: 100%;
  padding: 24px 0;
  text-align: center;
  border-top: 1px solid var(--border-subtle);
  margin-top: auto;
  background: var(--bg-elevated);
}

.app-footer p {
  font-size: 0.8125rem;
  color: var(--text-tertiary);
}
</style>
