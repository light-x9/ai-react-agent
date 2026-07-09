<template>
  <div class="ai-avatar-fallback" :class="type">
    <!-- 知识大师 -->
    <svg v-if="type === 'love'" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" class="avatar-svg">
      <defs>
        <linearGradient id="love-grad" x1="0" y1="0" x2="40" y2="40" gradientUnits="userSpaceOnUse">
          <stop stop-color="#f472b6" />
          <stop offset="1" stop-color="#ec4899" />
        </linearGradient>
      </defs>
      <circle cx="20" cy="20" r="20" fill="url(#love-grad)" />
      <path d="M20 28c-6-4-10-8-10-12a5 5 0 0 1 10-2 5 5 0 0 1 10 2c0 4-4 8-10 12z" fill="white" opacity="0.9" />
    </svg>

    <!-- 超级智能体（默认） -->
    <svg v-else viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" class="avatar-svg">
      <defs>
        <linearGradient id="agent-grad" x1="0" y1="0" x2="40" y2="40" gradientUnits="userSpaceOnUse">
          <stop stop-color="#3b82f6" />
          <stop offset="1" stop-color="#2563eb" />
        </linearGradient>
      </defs>
      <circle cx="20" cy="20" r="20" fill="url(#agent-grad)" />
      <circle cx="20" cy="20" r="14" fill="white" fill-opacity="0.15" />
      <circle cx="20" cy="20" r="6" fill="white" opacity="0.9" />
      <circle cx="20" cy="20" r="2" fill="#2563eb" />
    </svg>
  </div>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    default: 'default'
  }
})
</script>

<style scoped>
.ai-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-svg {
  width: 100%;
  height: 100%;
}
</style>
