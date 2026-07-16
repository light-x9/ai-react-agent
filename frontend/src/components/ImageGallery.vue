<template>
  <div class="image-gallery">
    <!-- 网格布局：根据图片数量动态 class -->
    <div class="image-grid" :class="gridClass">
      <div
        v-for="(img, idx) in images"
        :key="idx"
        class="image-card"
        @click="openLightbox(idx)"
      >
        <img
          :src="img.thumbnailUrl || img.url"
          :alt="img.alt"
          loading="lazy"
          class="image-thumb"
          @error="onImageError($event, idx)"
        />
        <div v-if="img.alt" class="image-overlay">
          <span class="image-alt">{{ img.alt }}</span>
        </div>
        <div class="image-zoom-hint">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            <line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/>
          </svg>
        </div>
      </div>
    </div>

    <!-- Lightbox 放大预览 -->
    <div v-if="lightboxOpen" class="lightbox" @click.self="closeLightbox" @keydown.escape="closeLightbox" tabindex="0" ref="lightboxRef">
      <button class="lightbox-close" @click="closeLightbox" aria-label="关闭">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
      <button v-if="hasMultiple" class="lightbox-nav lightbox-prev" @click="prevImage" aria-label="上一张">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
      </button>
      <button v-if="hasMultiple" class="lightbox-nav lightbox-next" @click="nextImage" aria-label="下一张">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </button>

      <div class="lightbox-content">
        <img :src="currentImage.url" :alt="currentImage.alt" class="lightbox-img" />
        <div class="lightbox-footer">
          <span class="lightbox-alt">{{ currentImage.alt || '图片' }}</span>
          <div class="lightbox-actions">
            <span v-if="hasMultiple" class="lightbox-counter">{{ currentIndex + 1 }} / {{ images.length }}</span>
            <a
              :href="pexelsSourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="lightbox-original-link"
              @click.stop
            >
              查看原图 ↗
            </a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

const props = defineProps({
  images: { type: Array, required: true },
})

const lightboxOpen = ref(false)
const currentIndex = ref(0)
const lightboxRef = ref(null)

const hasMultiple = computed(() => props.images.length > 1)

const currentImage = computed(() => props.images[currentIndex.value] || {})

// Pexels 源页 URL：从图片 URL 中提取 photo ID
const pexelsSourceUrl = computed(() => {
  const url = currentImage.value.url || currentImage.value.thumbnailUrl || ''
  const match = url.match(/photos\/(\d+)/)
  return match ? `https://www.pexels.com/photo/${match[1]}/` : 'https://www.pexels.com/'
})

// 网格布局 class：根据图片数量决定
const gridClass = computed(() => {
  const len = props.images.length
  if (len === 1) return 'grid-1'
  if (len === 2) return 'grid-2'
  if (len === 3) return 'grid-3'
  return 'grid-4'
})

function openLightbox(idx) {
  currentIndex.value = idx
  lightboxOpen.value = true
  nextTick(() => lightboxRef.value?.focus())
}
function closeLightbox() {
  lightboxOpen.value = false
}
function prevImage() {
  currentIndex.value = (currentIndex.value - 1 + props.images.length) % props.images.length
}
function nextImage() {
  currentIndex.value = (currentIndex.value + 1) % props.images.length
}

// 图片加载失败时显示占位
function onImageError(e, idx) {
  e.target.style.opacity = '0.3'
  e.target.parentElement.classList.add('image-card-error')
}

// 监听 lightbox 打开时锁定 body 滚动
watch(lightboxOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})
</script>

<style scoped>
.image-gallery {
  margin-top: 12px;
}

.image-grid {
  display: grid;
  gap: 6px;
  border-radius: var(--radius-md, 10px);
  overflow: hidden;
}

/* 1 张：全宽大图 */
.grid-1 {
  grid-template-columns: 1fr;
}
.grid-1 .image-card {
  aspect-ratio: 16 / 10;
}

/* 2 张：左右各半 */
.grid-2 {
  grid-template-columns: 1fr 1fr;
}
.grid-2 .image-card {
  aspect-ratio: 1 / 1;
}

/* 3 张：左侧大图 + 右侧两小图 */
.grid-3 {
  grid-template-columns: 2fr 1fr;
  grid-template-rows: 1fr 1fr;
}
.grid-3 .image-card:first-child {
  grid-row: 1 / -1;
  aspect-ratio: auto;
}
.grid-3 .image-card:not(:first-child) {
  aspect-ratio: 1 / 1;
}

/* 4+ 张：九宫格 */
.grid-4 {
  grid-template-columns: repeat(3, 1fr);
}
.grid-4 .image-card {
  aspect-ratio: 1 / 1;
}

.image-card {
  position: relative;
  cursor: pointer;
  overflow: hidden;
  border-radius: var(--radius-sm, 6px);
  background: var(--gray-100, #f3f4f6);
}
.image-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s ease;
}
.image-card:hover .image-thumb {
  transform: scale(1.05);
}

.image-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px 10px 6px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
  opacity: 0;
  transition: opacity 0.2s;
}
.image-card:hover .image-overlay {
  opacity: 1;
}
.image-alt {
  color: #fff;
  font-size: 0.75rem;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.image-zoom-hint {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s;
}
.image-card:hover .image-zoom-hint {
  opacity: 1;
}

.image-card-error {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ========== Lightbox ========== */
.lightbox {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  outline: none;
  animation: lightbox-fadein 0.2s ease;
}
@keyframes lightbox-fadein {
  from { opacity: 0; }
  to { opacity: 1; }
}

.lightbox-content {
  display: flex;
  flex-direction: column;
  max-width: 92vw;
  max-height: 90vh;
}

.lightbox-img {
  max-width: 92vw;
  max-height: 80vh;
  object-fit: contain;
  border-radius: var(--radius-sm, 6px);
}

.lightbox-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 4px 0;
  gap: 12px;
}

.lightbox-alt {
  color: rgba(255, 255, 255, 0.85);
  font-size: 0.8125rem;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lightbox-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.lightbox-counter {
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.75rem;
}

.lightbox-original-link {
  color: #fff;
  font-size: 0.8125rem;
  text-decoration: none;
  padding: 4px 10px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  transition: background 0.2s, border-color 0.2s;
}
.lightbox-original-link:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.5);
}

.lightbox-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}
.lightbox-close:hover {
  background: rgba(255, 255, 255, 0.25);
}

.lightbox-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}
.lightbox-nav:hover {
  background: rgba(255, 255, 255, 0.25);
}
.lightbox-prev { left: 16px; }
.lightbox-next { right: 16px; }

/* 移动端适配 */
@media (max-width: 640px) {
  .lightbox-nav {
    width: 36px;
    height: 36px;
  }
  .lightbox-prev { left: 8px; }
  .lightbox-next { right: 8px; }
  .grid-3 {
    grid-template-columns: 1fr 1fr;
    grid-template-rows: 2fr 1fr;
  }
  .grid-3 .image-card:first-child {
    grid-column: 1 / -1;
    grid-row: 1;
    aspect-ratio: 16 / 9;
  }
}
</style>
