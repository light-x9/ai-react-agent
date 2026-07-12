<template>
  <div class="chart-card">
    <div class="chart-card-header">
      <div class="chart-icon">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 3v18h18"/><path d="M18 9l-5 5-4-4-3 3"/>
        </svg>
      </div>
      <div class="chart-title">{{ title || '数据图表' }}</div>
      <!-- 下载原图 -->
      <button class="chart-dl-btn" @click="downloadImage" title="下载图表为 PNG" v-if="hasChart">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
      </button>
    </div>
    <div class="chart-loading" v-if="loading">
      <span class="chart-spinner" />
      <span>图表数据加载中…</span>
    </div>
    <div class="chart-error" v-else-if="errorMsg">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
      {{ errorMsg }}
    </div>
    <div class="chart-dom" ref="chartEl" v-show="hasChart"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { downloadFile as downloadFileApi } from '@/api'

const props = defineProps({
  fileId: { type: String, required: true },
  chatId: { type: String, default: '' },
  title: { type: String, default: '' }
})

const chartEl = ref(null)
const loading = ref(true)
const errorMsg = ref('')
const hasChart = ref(false)
let chartInstance = null

// ECharts 通过 CDN 注入到 window
const ec = () => window.echarts

onMounted(async () => {
  await loadAndRender()
})

async function loadAndRender() {
  const echarts = ec()
  if (!echarts) {
    errorMsg.value = '图表库未加载'
    loading.value = false
    return
  }
  try {
    const result = await downloadFileApi(props.fileId, props.chatId)
    if (!result.success) {
      errorMsg.value = '图表数据下载失败：' + (result.error || '未知')
      loading.value = false
      return
    }
    const blob = result.blob
    const text = await blob.text()
    let opt
    try {
      opt = JSON.parse(text)
    } catch (e) {
      errorMsg.value = '图表配置 JSON 解析失败'
      loading.value = false
      return
    }
    await nextTick()
    if (!chartEl.value) return
    // 销毁旧实例（watch 时）
    if (chartInstance) chartInstance.dispose()
    chartInstance = echarts.init(chartEl.value)
    chartInstance.setOption(opt)
    hasChart.value = true
    loading.value = false
    // 自适应窗口
    ro = new ResizeObserver(() => chartInstance && chartInstance.resize())
    ro.observe(chartEl.value)
  } catch (e) {
    errorMsg.value = '图表加载失败：' + (e.message || '未知')
    loading.value = false
  }
}

let ro = null
onBeforeUnmount(() => {
  if (ro) ro.disconnect()
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

function downloadImage() {
  if (!chartInstance) return
  const url = chartInstance.getDataURL({
    type: 'png',
    pixelRatio: 2,
    backgroundColor: '#fff'
  })
  const a = document.createElement('a')
  a.href = url
  a.download = (props.title || 'chart') + '.png'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

watch(() => props.fileId, () => {
  loading.value = true
  errorMsg.value = ''
  hasChart.value = false
  loadAndRender()
})
</script>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
  margin-top: 10px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  overflow: hidden;
}
.chart-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-subtle);
}
.chart-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.08);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.chart-title {
  flex: 1;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chart-dl-btn {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: transparent;
  border: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}
.chart-dl-btn:hover {
  background: rgba(37, 99, 235, 0.06);
  color: var(--accent);
  border-color: var(--accent);
}
.chart-dom {
  width: 100%;
  height: 320px;
  min-height: 240px;
}
.chart-loading, .chart-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px;
  color: var(--text-tertiary);
  font-size: 0.8125rem;
  justify-content: center;
}
.chart-error { color: #dc2626; }
.chart-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(37, 99, 235, 0.2);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
