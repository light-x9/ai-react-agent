<template>
  <div class="model-selector" v-if="models.length > 1">
    <button class="ms-trigger" @click="open = !open" :class="{ open }">
      <span class="ms-label">{{ currentModel }}</span>
      <span class="ms-arrow">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </span>
    </button>

    <transition name="fade">
      <div v-if="open" class="ms-dropdown" @click.stop>
        <div class="ms-options">
          <button
            v-for="m in models"
            :key="m.name"
            class="ms-option"
            :class="{ active: m.name === locked, primary: m.primary, unhealthy: m.circuitOpen }"
            @click="selectModel(m)"
          >
            <span class="ms-dot" :class="{ ok: !m.circuitOpen, bad: m.circuitOpen }" />
            <span class="ms-name">{{ m.name }}</span>
            <span v-if="m.primary" class="ms-badge">首选</span>
            <span v-if="m.circuitOpen" class="ms-badge warn">熔断</span>
          </button>
        </div>
        <div class="ms-footer">
          <button class="ms-clear" :class="{ disabled: !locked }" @click="clearLock">
            {{ locked ? '解除锁定（自动）' : '自动选择' }}
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listModels, lockModel } from '@/api'

const open = ref(false)
const models = ref([])
const locked = ref('')

const currentModel = computed(() => {
  if (locked.value) return locked.value + ' (锁定)'
  const primary = models.value.find(m => m.primary)
  return primary ? primary.name : '自动'
})

async function refresh() {
  try {
    const data = await listModels()
    models.value = data.models || []
  } catch (_) { /* ignore */ }
}

async function selectModel(m) {
  try {
    await lockModel(m.name)
    locked.value = m.name
    open.value = false
  } catch (_) { /* ignore */ }
}

async function clearLock() {
  try {
    await lockModel('')
    locked.value = ''
    open.value = false
  } catch (_) { /* ignore */ }
}

onMounted(() => {
  refresh()
  // 点击外部关闭
  document.addEventListener('click', () => { open.value = false })
})

defineExpose({ refresh })
</script>

<style scoped>
.model-selector {
  position: relative;
  display: inline-flex;
}
.ms-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-body);
  font-size: 0.75rem;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 100px;
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  cursor: pointer;
  transition: border-color 0.15s;
}
.ms-trigger:hover { border-color: var(--border-active); }
.ms-arrow svg { transition: transform 0.15s; }
.ms-trigger.open .ms-arrow svg { transform: rotate(180deg); }

.ms-dropdown {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  right: 0;
  min-width: 160px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  z-index: 100;
  overflow: hidden;
}
.ms-options {
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.ms-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.12s;
  text-align: left;
}
.ms-option:hover { background: rgba(0, 0, 0, 0.04); }
.ms-option.active { background: rgba(37, 99, 235, 0.06); }
.ms-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.ms-dot.ok { background: #22c55e; }
.ms-dot.bad { background: #ef4444; }
.ms-name {
  flex: 1;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-primary);
}
.ms-option.active .ms-name { color: var(--accent); }
.ms-badge {
  font-size: 0.625rem;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(34, 197, 94, 0.1);
  color: #16a34a;
  font-weight: 500;
}
.ms-badge.warn {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}
.ms-footer {
  border-top: 1px solid var(--border-subtle);
  padding: 4px;
}
.ms-clear {
  width: 100%;
  padding: 5px 8px;
  font-size: 0.6875rem;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  color: var(--text-tertiary);
  text-align: center;
}
.ms-clear:hover:not(.disabled) { background: rgba(0, 0, 0, 0.04); }
.ms-clear.disabled { opacity: 0.4; cursor: default; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
