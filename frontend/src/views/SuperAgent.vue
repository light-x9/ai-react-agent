<template>
  <div class="super-agent-container">
    <SessionSidebar ref="sidebarRef" />
    <div class="main-area">
    <!-- ====== Header ====== -->
    <header class="header">
      <button class="back-button" @click="goBack" aria-label="返回首页">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5M12 19l-7-7 7-7" />
        </svg>
        <span>返回</span>
      </button>
      <h1 class="title">AI 超级智能体</h1>
      <div class="header-actions">
        <button class="header-btn" @click="triggerUpload" :title="uploading ? '上传中...' : '上传文件'">
          <svg v-if="!uploading" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:4px">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
          </svg>
          <span v-if="!uploading">上传</span>
          <span v-else class="spinner">...</span>
        </button>
        <button class="header-btn" @click="openManageDialog" title="管理知识库">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:4px">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="16" y1="13" x2="8" y2="13" />
            <line x1="16" y1="17" x2="8" y2="17" />
          </svg>
          <span>管理</span>
        </button>
      </div>
    </header>

    <!-- Hidden file input -->
    <input ref="fileInput" type="file" accept=".txt,.md" style="display:none" @change="handleFileUpload" />

    <!-- Upload toast -->
    <transition name="fade">
      <div v-if="uploadMsg" class="upload-toast" :class="{ success: uploadSuccess, error: !uploadSuccess }">
        {{ uploadMsg }}
      </div>
    </transition>

    <!-- ====== Manage Dialog ====== -->
    <transition name="modal">
      <div v-if="showManage" class="modal-overlay" @click.self="closeManageDialog">
        <div class="modal-dialog modal-lg">
          <div class="modal-header">
            <h3>知识库文件</h3>
            <span class="modal-close" @click="closeManageDialog">&times;</span>
          </div>
          <div class="modal-body">
            <!-- 上传区域 -->
            <div
              class="drop-zone"
              :class="{ active: dragOver, uploading: uploading }"
              @click="!uploading && triggerUpload()"
              @dragover.prevent="dragOver = true"
              @dragleave.prevent="dragOver = false"
              @drop.prevent="handleDrop"
            >
              <template v-if="!uploading">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
                </svg>
                <p>点击或拖拽文件到此处上传</p>
                <span class="drop-hint">支持 .txt / .md，单文件 ≤ 10MB</span>
              </template>
              <template v-else>
                <div class="upload-progress">
                  <div class="progress-bar"><div class="progress-fill" :style="{ width: uploadProgress + '%' }"></div></div>
                  <span>上传中 {{ uploadProgress }}%</span>
                </div>
              </template>
            </div>
            <!-- 校验错误提示 -->
            <div v-if="fileValidationError" class="file-error-tip">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              {{ fileValidationError }}
            </div>
            <!-- 已选文件信息（校验通过但未上传时显示） -->
            <div v-else-if="selectedFile && !uploading" class="file-selected-tip">
              <span class="file-selected-name">{{ selectedFile.name }}</span>
              <span class="file-selected-size">{{ formatFileSize(selectedFile.size) }}</span>
              <button class="file-selected-upload" @click="doUpload(selectedFile)">确认上传</button>
              <button class="file-selected-cancel" @click="selectedFile = null">取消</button>
            </div>

            <!-- 文件列表 -->
            <div v-if="loadingFiles" class="modal-loading">加载中...</div>
            <div v-else-if="uploadedFiles.length === 0" class="modal-empty">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="9" y1="13" x2="15" y2="13"/>
                <line x1="9" y1="17" x2="13" y2="17"/>
              </svg>
              <p>暂无知识库文档</p>
              <span>点击上方上传区域添加 .txt / .md 文件</span>
            </div>
            <template v-else>
              <!-- 批量操作栏 -->
              <div class="file-toolbar">
                <label class="toolbar-checkbox">
                  <input type="checkbox" :checked="selectedFiles.size === uploadedFiles.length && uploadedFiles.length > 0" @change="toggleSelectAll" />
                  <span>全选（{{ selectedFiles.size }}/{{ uploadedFiles.length }}）</span>
                </label>
                <button class="toolbar-btn toolbar-btn-danger" @click="confirmBatchDelete" :disabled="selectedFiles.size === 0">
                  删除选中（{{ selectedFiles.size }}）
                </button>
              </div>
              <!-- 文件列表 -->
              <div class="file-list">
                <div v-for="file in uploadedFiles" :key="file.source" class="file-row" :class="{ 'file-row-selected': selectedFiles.has(file.source) }">
                  <input type="checkbox" class="file-checkbox" :checked="selectedFiles.has(file.source)" @change="toggleSelect(file.source)" />
                  <div class="file-info">
                    <div class="file-line1">
                      <span class="file-name" :title="'预览 ' + file.source" @click="openPreview(file.source)">{{ file.source }}</span>
                      <span class="file-badge" :class="statusBadge[file.status]?.cls || 'badge-success'">{{ statusBadge[file.status]?.label || '已完成' }}</span>
                    </div>
                    <div class="file-line2">
                      <span class="file-meta">{{ formatFileSize(file.fileSize) }}</span>
                      <span class="file-meta-sep">·</span>
                      <span class="file-meta">{{ file.charCount?.toLocaleString() || '—' }} 字符</span>
                      <span class="file-meta-sep">·</span>
                      <span class="file-meta">{{ file.chunks }} 个分块</span>
                      <span class="file-meta-sep">·</span>
                      <span class="file-meta">{{ formatUploadTime(file.uploadedAt) }}</span>
                    </div>
                  </div>
                  <div class="file-actions">
                    <button class="file-action-btn" title="预览" @click="openPreview(file.source)">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    </button>
                    <button class="file-action-btn" title="覆盖更新" @click="triggerReUpload(file.source)">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/></svg>
                    </button>
                    <button class="file-action-btn file-action-danger" title="删除" @click="confirmDelete(file.source)" :disabled="deletingFile === file.source">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/></svg>
                    </button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 检索测试区（功能保留，后续版本再展示） -->
          </div>
          <div class="modal-footer">
            <button class="modal-btn-text modal-btn-danger-text" @click="clearAll" :disabled="uploadedFiles.length === 0">清空全部</button>
            <button class="modal-btn" @click="closeManageDialog">关闭</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- ====== Delete Confirm Dialog (单条) ====== -->
    <transition name="modal">
      <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
        <div class="modal-dialog modal-sm">
          <div class="modal-header">
            <h3>确认删除</h3>
          </div>
          <div class="modal-body">
            <p>确定删除 <strong>{{ deleteTarget }}</strong>？将移除 {{ deleteTargetChunks }} 个文档分块。</p>
          </div>
          <div class="modal-footer">
            <button class="modal-btn modal-btn-cancel" @click="showDeleteConfirm = false">取消</button>
            <button class="modal-btn modal-btn-danger" @click="doDelete">删除</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- ====== Batch Delete Confirm Dialog ====== -->
    <transition name="modal">
      <div v-if="showBatchDeleteConfirm" class="modal-overlay" @click.self="showBatchDeleteConfirm = false">
        <div class="modal-dialog modal-sm">
          <div class="modal-header">
            <h3>确认批量删除</h3>
          </div>
          <div class="modal-body">
            <p>确定要删除 <strong>{{ deleteTarget }}</strong> 吗？此操作不可撤销。</p>
          </div>
          <div class="modal-footer">
            <button class="modal-btn modal-btn-cancel" @click="showBatchDeleteConfirm = false">取消</button>
            <button class="modal-btn modal-btn-danger" @click="doBatchDelete">确认删除</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- ====== Preview Dialog ====== -->
    <transition name="modal">
      <div v-if="showPreview" class="modal-overlay" @click.self="showPreview = false">
        <div class="modal-dialog modal-lg">
          <div class="modal-header">
            <h3>预览：{{ previewData?.source || '' }}</h3>
            <span class="modal-close" @click="showPreview = false">&times;</span>
          </div>
          <div class="modal-body preview-body">
            <div v-if="previewLoading" class="modal-loading">加载中...</div>
            <template v-else-if="previewData">
              <div class="preview-meta">
                <span>字符数：{{ previewData.charCount?.toLocaleString() }}</span>
                <span v-if="previewData.truncated" class="preview-truncated">（仅展示前 2000 字符）</span>
              </div>
              <pre class="preview-content">{{ previewData.preview }}</pre>
            </template>
          </div>
          <div class="modal-footer">
            <button class="modal-btn" @click="showPreview = false">关闭</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 能力提示横幅：query 消息且未开启任何检索能力时显示 -->
    <transition name="fade">
      <div v-if="showCapHint" class="cap-hint-banner">
        <span>💡 检测到查询类问题，开启「网页搜索」或「知识库」可获得更准确信息</span>
        <button class="cap-hint-btn" @click="activeCaps.webSearch = true; onCapabilityChange({ ...activeCaps }); showCapHint = false">开启网页搜索</button>
        <button class="cap-hint-close" @click="showCapHint = false" aria-label="关闭">×</button>
      </div>
    </transition>

    <!-- ====== Chat Area ====== -->
    <div class="content-wrapper">
      <div class="chat-area">
        <ChatRoom
          :messages="chatStore.activeMessages"
          :connection-status="connectionStatus"
          ai-type="super"
          @send-message="sendMessage"
          @capability-change="onCapabilityChange"
        />
      </div>
    </div>
    
    <div class="footer-container">
      <AppFooter />
    </div>
    </div><!-- /main-area -->
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { useChatStore } from '@/stores/chatStore'
import ChatRoom from '../components/ChatRoom.vue'
import AppFooter from '../components/AppFooter.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { chatWithManus, uploadKnowledgeBase, listKnowledgeFiles, deleteKnowledgeFile, batchDeleteKnowledgeFiles, previewKnowledgeFile, searchTestKnowledge } from '../api'
import { connectSSE } from '../api'
import { useUserStore } from '@/stores/userStore'
const USE_MOCK = false  // true=Mock演示, false=真实后端
const userStore = useUserStore()


useHead({
  title: '超级智能体 - YuPi AI',
  meta: [{ name: 'description', content: '具备工具调用与知识库能力的 AI 超级智能体' }]
})

const router = useRouter()
const chatStore = useChatStore()
const sidebarRef = ref(null)
const connectionStatus = ref('disconnected')
// 能力开关状态（由 ChatRoom toggle 上报）
const activeCaps = ref({ webSearch: false, knowledgeBase: false })
const onCapabilityChange = (caps) => { activeCaps.value = caps }
let eventSource = null
// 会话 ID：同一页面窗口内保持不变，后端据此维护多轮对话记忆
const chatId = Date.now().toString(36)

// ========== 消息意图识别（闲聊 vs 信息查询） ==========

/**
 * 疑问词 —— 句子里出现这类词，说明用户在提问，需要检索
 */
const QUESTION_WORDS = /什么|怎么|如何|为什么|哪(?!里)|哪里|哪儿|谁|多少|几号|几点|什么时候|多大|多远|哪些|是否|能不能|可不可以|会不会|是不是|怎么样|是什么|是啥|啥是|多少钱|几岁|多高|多重|几个|几名/

/**
 * 信息索取动词 —— 用户主动要求获取某类信息
 */
const INFO_VERBS = /告诉我|查一下|查询|搜索|查找|查查|介绍一下|给我|推荐|排名|榜单|攻略|教程|方法|步骤|区别|优劣|好不好|靠谱吗|值得|建议|意见|计算|换算/

/**
 * 信息名词 —— 仅当句子是「短查询」或「带问句结构」时才触发，避免被陈述句误杀
 */
const INFO_NOUNS = /天气|气温|温度|价格|时间|日期|新闻|资讯|数据|汇率|股票|比分|赛程|航班|火车|酒店|电影|人口|面积|历史/

/**
 * 陈述句模式：信息名词 + 描述词（真好/很热/挺贵...）→ 抒发感受，不是查询
 * 例：今天天气真好、今天气温好高、这个房价挺贵
 */
const STATEMENT_AFTER_INFO_NOUN = /(天气|气温|温度|价格|股票|电影|航班|酒店)(真|很|太|挺|蛮|超|特别|好|老|蛮|还)?(高|低|贵|便宜|热|冷|好|差|糟|晴朗|糟糕|不错|行|给力|离谱|合适|难|容易|辛苦|累|舒服|爽|美|丑|快|慢|新|旧|多|少)/

/**
 * 情绪/感受词 —— 命中说明是主观抒发，即使含地名也是闲聊
 */
const EMOTION_WORDS = /好热|好冷|好闷|好累|好困|好饿|好热|好冷|好烦|无聊|难过|伤心|开心|高兴|无语|舒服|难受|痛苦|郁闷|焦虑|压力|烦死了|热死了|冷死了|累死了/

/**
 * 闲聊/日常词 —— 命中直接放行
 */
const CASUAL_WORDS = /你好|嗨|哈喽|hello|hi|hey|谢谢|感谢|多谢|嗯+|哦+|哈哈|呵呵|嘿嘿|666|牛|厉害|强|棒|不错|好的|好滴|好吧|行|收到|了解|明白|懂了|知道|早安|晚安|摸鱼|睡觉|吃饭|喝水|打游戏|上班|加班|下班|放假|周末/

/**
 * 判断消息是「信息查询」还是「闲聊/情绪」
 * 核心原则：看句式结构 + 是否有信息索取意图，不看是否包含地名
 * @param {string} msg - 用户消息
 * @returns {'query'|'casual'}
 */
const classifyMessage = (msg) => {
  const t = msg.trim()
  // 1. 含问号 → 查询
  if (/[？?]/.test(t)) return 'query'
  // 2. 情绪感受抒发 → 闲聊（优先级高，避免"杭州好热"被误判）
  if (EMOTION_WORDS.test(t)) return 'casual'
  // 3. 日常问候/应答 → 闲聊
  if (CASUAL_WORDS.test(t)) return 'casual'
  // 4. 含疑问词（什么/怎么/哪里/多少...） → 查询
  if (QUESTION_WORDS.test(t)) return 'query'
  // 5. 含信息索取动词（查询/搜索/推荐...） → 查询
  if (INFO_VERBS.test(t)) return 'query'
  // 6. 含信息名词 + 短句（< 15 字） → 查询（如"杭州天气"、"比特币价格"）
  //    但排除"信息名词 + 描述词"的陈述句（如"今天天气真好"、"今天气温好高"）
  if (INFO_NOUNS.test(t) && t.length < 15 && !STATEMENT_AFTER_INFO_NOUN.test(t)) return 'query'
  // 7. 其他 → 闲聊
  return 'casual'
}

// 能力提示横幅（query 消息且未开启任何能力时显示）
const showCapHint = ref(false)
let capHintTimer = null

// ========== Upload ==========
const fileInput = ref(null)
const uploading = ref(false)
const uploadMsg = ref('')
const uploadSuccess = ref(true)
const uploadProgress = ref(0)
const dragOver = ref(false)

const triggerUpload = () => {
  if (uploading.value) return
  reUploadSource.value = ''
  fileInput.value?.click()
}

/** 校验通过后才真正上传 */
const doUpload = async (file) => {
  if (!file) return
  if (!validateFile(file)) return  // validateFile 会设置 fileValidationError

  uploading.value = true
  uploadProgress.value = 0
  uploadMsg.value = (reUploadSource.value ? '正在覆盖更新：' : '正在上传：') + file.name
  uploadSuccess.value = true
  try {
    const result = await uploadKnowledgeBase(file, (e) => {
      if (e.total) uploadProgress.value = Math.round((e.loaded / e.total) * 100)
    })
    uploadMsg.value = result.success ? '上传成功：' + file.name : '上传失败：' + result.message
    uploadSuccess.value = result.success
    if (result.success) {
      if (reUploadSource.value) {
        chatStore.addMessageToActive('已覆盖更新知识库文档：' + file.name, false, 'system')
        reUploadSource.value = ''
      } else {
        chatStore.addMessageToActive('知识库上传成功：' + result.message, false, 'system')
      }
      // 弹窗打开时刷新列表
      if (showManage.value) {
        const res = await listKnowledgeFiles()
        if (res.success) uploadedFiles.value = res.files || []
      }
    }
  } catch (err) {
    uploadMsg.value = '上传失败：' + (err.response?.data?.message || err.message)
    uploadSuccess.value = false
  } finally {
    uploading.value = false
    selectedFile.value = null
    setTimeout(() => { uploadMsg.value = '' }, 4000)
  }
}

/** 文件选择 change 事件：校验后展示确认条（不自动上传，由用户确认） */
const handleFileUpload = (event) => {
  const file = event.target.files?.[0]
  if (file && !validateFile(file)) {
    // 校验失败：fileValidationError 已设置，UI 会展示错误提示
  }
  if (event.target) event.target.value = ''
}

const handleDrop = (event) => {
  dragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) validateFile(file)
}

// ========== Manage Dialog ==========
const showManage = ref(false)
const uploadedFiles = ref([])
const loadingFiles = ref(false)

const openManageDialog = async () => {
  showManage.value = true
  selectedFiles.value.clear()
  fileValidationError.value = ''
  selectedFile.value = null
  reUploadSource.value = ''
  loadingFiles.value = true
  try {
    const result = await listKnowledgeFiles()
    if (result.success) {
      uploadedFiles.value = result.files || []
    }
  } catch (err) {
    console.error('Failed to list files', err)
  } finally {
    loadingFiles.value = false
  }
}

// ========== File Validation ==========
const selectedFile = ref(null)      // 当前选中的待上传文件
const fileValidationError = ref('')  // 校验错误信息

/** 校验文件：格式 + 大小，不合规返回 false */
const validateFile = (file) => {
  if (!file) return false
  fileValidationError.value = ''
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!['txt', 'md'].includes(ext)) {
    fileValidationError.value = '格式不支持：仅接受 .txt / .md'
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    fileValidationError.value = '文件过大：单文件上限 10MB'
    return false
  }
  selectedFile.value = file
  return true
}

/** 文件选中变化时触发 */
const handleFileSelected = (event) => {
  const file = event.target.files?.[0]
  if (file) validateFile(file)
  if (event.target) event.target.value = ''
}

/** 显示文件大小人类可读 */
const formatFileSize = (bytes) => {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/** 显示上传时间人类可读 */
const formatUploadTime = (ts) => {
  if (!ts) return '—'
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 状态徽章：completed / pending / error */
const statusBadge = {
  completed: { label: '已完成', cls: 'badge-success' },
  pending:   { label: '处理中', cls: 'badge-pending' },
  error:     { label: '失败',   cls: 'badge-error' },
}

// ========== Selection ==========
const selectedFiles = ref(new Set())  // 多选用 Set<string>(source)

const toggleSelect = (source) => {
  const s = selectedFiles.value
  if (s.has(source)) s.delete(source)
  else s.add(source)
}

const toggleSelectAll = () => {
  if (selectedFiles.value.size === uploadedFiles.value.length) {
    selectedFiles.value.clear()
  } else {
    uploadedFiles.value.forEach(f => selectedFiles.value.add(f.source))
  }
}

// ========== Delete ==========
const showDeleteConfirm = ref(false)
const deleteTarget = ref('')
const deleteTargetChunks = ref(0)
const deletingFile = ref('')
const showBatchDeleteConfirm = ref(false)

const confirmDelete = (sourceName) => {
  const file = uploadedFiles.value.find(f => f.source === sourceName)
  deleteTarget.value = sourceName
  deleteTargetChunks.value = file?.chunks || 0
  showDeleteConfirm.value = true
}

const doDelete = async () => {
  showDeleteConfirm.value = false
  deletingFile.value = deleteTarget.value
  try {
    const result = await deleteKnowledgeFile(deleteTarget.value)
    if (result.success) {
      uploadedFiles.value = uploadedFiles.value.filter(f => f.source !== deleteTarget.value)
      selectedFiles.value.delete(deleteTarget.value)
      chatStore.addMessageToActive('已删除：' + result.message, false, 'system')
    }
  } catch (err) {
    console.error('Delete failed', err)
  } finally {
    deletingFile.value = ''
  }
}

const confirmBatchDelete = () => {
  if (selectedFiles.value.size === 0) return
  deleteTarget.value = `选中的 ${selectedFiles.value.size} 个文件`
  showBatchDeleteConfirm.value = true
}

const doBatchDelete = async () => {
  showBatchDeleteConfirm.value = false
  deletingFile.value = '__batch__'
  try {
    const names = Array.from(selectedFiles.value)
    const result = await batchDeleteKnowledgeFiles(names)
    if (result.success) {
      uploadedFiles.value = uploadedFiles.value.filter(f => !selectedFiles.value.has(f.source))
      selectedFiles.value.clear()
      chatStore.addMessageToActive(`已批量删除 ${result.deleted} 个文件`, false, 'system')
    }
  } catch (err) {
    console.error('Batch delete failed', err)
  } finally {
    deletingFile.value = ''
  }
}

const clearAll = () => {
  deleteTarget.value = '全部文件'
  showBatchDeleteConfirm.value = true
}

// ========== Preview ==========
const showPreview = ref(false)
const previewData = ref(null)
const previewLoading = ref(false)

const openPreview = async (sourceName) => {
  previewLoading.value = true
  showPreview.value = true
  previewData.value = null
  try {
    const result = await previewKnowledgeFile(sourceName)
    previewData.value = result.success ? result.preview : null
    if (!result.success) previewData.value = { source: sourceName, preview: '预览失败：' + result.message, charCount: 0, truncated: false }
  } catch (err) {
    previewData.value = { source: sourceName, preview: '预览失败：' + err.message, charCount: 0, truncated: false }
  } finally {
    previewLoading.value = false
  }
}

// ========== Re-upload (覆盖更新) ==========
const reUploadSource = ref('')  // 正在覆盖更新的文件名（用于显示提示）

const triggerReUpload = (sourceName) => {
  reUploadSource.value = sourceName
  fileInput.value?.click()
}

// ========== Search Test ==========
const searchTestQuery = ref('')
const searchTestSource = ref('')  // '' = 全部文件，或指定某个 source
const searchTestLoading = ref(false)
const searchTestHits = ref([])
const searchTestDone = ref(false)  // 是否已执行过搜索

const doSearchTest = async () => {
  if (!searchTestQuery.value.trim()) return
  searchTestLoading.value = true
  searchTestHits.value = []
  searchTestDone.value = false
  try {
    const result = await searchTestKnowledge(
      searchTestQuery.value.trim(),
      searchTestSource.value || undefined
    )
    searchTestHits.value = result.success ? (result.hits || []) : []
    searchTestDone.value = true
  } catch (err) {
    console.error('search test failed', err)
    searchTestDone.value = true
  } finally {
    searchTestLoading.value = false
  }
}

/** 关闭管理弹窗时清理状态 */
const closeManageDialog = () => {
  showManage.value = false
  selectedFiles.value.clear()
  selectedFile.value = null
  fileValidationError.value = ''
  reUploadSource.value = ''
  showPreview.value = false
  previewData.value = null
}

// ========== Chat ==========

/**
 * 模拟结构化 ReAct 渐进流式输出
 *
 * 真实场景中后端 SSE 应返回如下格式的 JSON 行：
 *   {"type":"thought","content":"..."}
 *   {"type":"action","tool":"searchWeb","params":{...}}
 *   {"type":"observation","summary":"...","rawResult":"..."}
 *   {"type":"final","content":"最终回答..."}
 *
 * 此处用 mock 数据 + 定时器模拟流式效果，用于作品集展示。
 * 接入真实后端时，只需替换 runMockReActStream() 为 connectSSE()，
 * 在 onmessage 中根据 event.data 的 type 字段调用对应函数即可。
 */
const sendMessage = (message) => {
  // 前置意图校验：查询类消息 + 未开启任何检索能力 → 显示柔和提示
  const intent = classifyMessage(message)
  const hasAnyCap = activeCaps.value.webSearch || activeCaps.value.knowledgeBase
  if (intent === 'query' && !hasAnyCap) {
    showCapHint.value = true
    // 5 秒后自动隐藏提示
    if (capHintTimer) clearTimeout(capHintTimer)
    capHintTimer = setTimeout(() => { showCapHint.value = false }, 5000)
  }

  // 判断是否是当前会话的首条用户消息（用于设置会话能力类型图标）
  const isActiveSessionEmpty = chatStore.activeMessages.filter(m => m.isUser).length === 0

  chatStore.addMessageToActive(message, true)
  chatStore.persistMessage('user', message)

  // 首条消息发送后，根据当前能力开关设置会话类型
  if (isActiveSessionEmpty) {
    const caps = activeCaps.value
    let capability = 'chat'
    if (caps.webSearch && caps.knowledgeBase) capability = 'both'
    else if (caps.webSearch) capability = 'webSearch'
    else if (caps.knowledgeBase) capability = 'knowledgeBase'
    chatStore.setCapability(chatStore.activeId, capability)
  }

  if (eventSource) eventSource.close()

  // 创建 AI 消息骨架
  const aiMessageIndex = chatStore.activeMessages.length
  chatStore.addMessageToActive('', false, '', {
    reactCycles: [],
    finalAnswer: '',
    _cycleIndex: 0
  })

  connectionStatus.value = 'connecting'

  if (USE_MOCK) {
    const scenario = buildMockScenario(message)
    runMockReActStream(aiMessageIndex, scenario)
  } else {
    // 构建历史上下文：取最近6轮对话
      const historyMessages = chatStore.activeMessages.slice(-6);
      const historyText = historyMessages
        .filter(m => m.content && !m.type)
        .map(m => (m.isUser ? 'User: ' : 'Assistant: ') + m.content)
        .join('\n');

      eventSource = connectSSE('/ai/manus/chat', { message, history: historyText, webSearch: activeCaps.value.webSearch, knowledgeBase: activeCaps.value.knowledgeBase },
      // onMessage：后端每条 SST 数据 + 流结束时的 [DONE] 都走这里
      async (data) => {
        if (data === '[DONE]') {
          connectionStatus.value = 'disconnected'
          if (eventSource) { eventSource.close(); eventSource = null }
          // await 确保消息持久化 + 用量刷新都完成，再渲染最终状态
          const content = chatStore.activeMessages[aiMessageIndex]?.content || ''
          await chatStore.persistMessage('assistant', content)
          if (sidebarRef.value) await sidebarRef.value.refreshUsage()
          return
        }
        chatStore.activeMessages[aiMessageIndex].content += data
      },
      // 异常断开：只展示错误提示
      () => {
        if (!chatStore.activeMessages[aiMessageIndex]?.content) {
          chatStore.activeMessages[aiMessageIndex].content = '连接失败，请检查后端是否已启动。'
        }
        connectionStatus.value = 'disconnected'
        if (eventSource) { eventSource.close(); eventSource = null }
      }
    )
  }
}

// ---------- 根据用户问题动态生成 mock 推理内容 ----------

/**
 * 动态生成 mock 推理场景。
 * 所有文案都基于用户实际输入的问题，不再固定返回某一主题。
 */
function buildMockScenario(userMessage) {
  // 1. 判断用户意图，决定调用什么工具
  const intent = detectIntent(userMessage)

  switch (intent) {
    case 'code':
      return buildCodeScenario(userMessage)
    case 'data':
      return buildDataScenario(userMessage)
    case 'file':
      return buildFileScenario(userMessage)
    case 'knowledge':
      return buildKnowledgeScenario(userMessage)
    default:
      return buildSearchScenario(userMessage)
  }
}

/**
 * 意图识别——简单关键词匹配
 * 未来可换成 LLM 分类或更复杂的规则
 */
function detectIntent(msg) {
  const m = msg.toLowerCase()
  if (/(代码|编程|程序|写.*code|算法|函数|类|接口|debug|bug)/.test(m)) return 'code'
  if (/(数据|分析|统计|图表|csv|excel|报表|计算)/.test(m)) return 'data'
  if (/(文件|目录|读取|写入|上传|下载|列表)/.test(m)) return 'file'
  if (/(知识|文档|内部|系统架构|项目|代码库)/.test(m)) return 'knowledge'
  return 'search'
}

// 工具配色（与前端展示对应）
const TOOL_STYLE = {
  searchWeb:    { label: '网页搜索',  tool: 'searchWeb',    color: '#4f46e5' },
  searchKnowledge: { label: '知识库检索', tool: 'searchKnowledge', color: '#0891b2' },
  executeCode:  { label: '代码执行',  tool: 'executeCode',  color: '#ca8a04' },
  readFile:     { label: '文件读取',  tool: 'readFile',     color: '#059669' },
  listFiles:    { label: '文件列表',  tool: 'listFiles',    color: '#7c3aed' },
  writeFile:    { label: '文件写入',  tool: 'writeFile',    color: '#dc2626' }
}

// ---- 场景工厂 ----

/**
 * 搜索结果 mock 数据——返回带真实域名的结构化结果
 * 用于作品集截图，看起来像真实 API 返回
 */
function buildSearchResults(q) {
  const kw = extractKeyword(q) || q
  const urls = [
    { domain: 'juejin.cn',    path: '/post/' + Math.random().toString(36).slice(2, 10) },
    { domain: 'zhihu.com',    path: '/question/' + Math.floor(Math.random() * 999999) },
    { domain: 'github.com',   path: '/trending' },
    { domain: 'csdn.net',     path: '/article/details/' + Math.floor(Math.random() * 99999999) }
  ]
  const titles = [
    kw + ' —— 从原理到实践，一篇文章讲透',
    '深入理解 ' + kw + '：核心概念与典型应用场景',
    kw + ' 最佳实践总结（附代码）',
    '关于 ' + kw + ' 的几个关键问题，你可能一直理解错了'
  ]
  const snippets = [
    '简单来说，' + kw + '的核心思路是...本文将从原理、优势和实际案例三个维度展开讲解。',
    '在真实项目中使用 ' + kw + ' 需要注意以下几点：第一...第二...第三...',
    '最近关于 ' + kw + ' 的讨论越来越多，这篇文章整理了社区的主流观点和争议点。',
    '如果你还在纠结要不要用 ' + kw + '，读完这篇文章你应该就有答案了。'
  ]

  return Array.from({ length: 4 }, (_, i) => ({
    title: titles[i],
    url: 'https://' + urls[i].domain + urls[i].path,
    snippet: snippets[i],
    source: urls[i].domain,
    date: '2025-' + String(Math.floor(Math.random() * 12) + 1).padStart(2, '0') + '-' + String(Math.floor(Math.random() * 28) + 1).padStart(2, '0')
  }))
}

/** 搜索类 */
function buildSearchScenario(q) {
  const results1 = buildSearchResults(q)
  const results2 = buildSearchResults(q + ' 实践')

  return {
    cycles: [
      {
        thought: { content: `用户问的是「${q}」。我先搜索这个词的最新公开资料，看看主流技术社区和权威媒体都怎么说。` },
        action: {
          ...TOOL_STYLE.searchWeb,
          params: { query: q, language: 'zh-CN', limit: 5 }
        },
        observation: {
          summary: `搜索返回 4 条高质量结果，覆盖掘金、知乎、GitHub、CSDN 等平台。掘金有一篇「${results1[0].title.slice(0, 20)}」的文章阅读量 1.2w+，知乎相关问题关注者 5000+。`,
          rawResult: JSON.stringify({ query: q, total: 4, results: results1 }, null, 2)
        }
      },
      {
        thought: { content: '基础资料有了。我再搜一轮实际项目中的使用经验和避坑指南，补充实操层面的内容。' },
        action: {
          ...TOOL_STYLE.searchWeb,
          params: { query: `${q} 实践 经验 避坑`, language: 'zh-CN', limit: 3 }
        },
        observation: {
          summary: `补充搜索返回 3 条经验类文章。其中 CSDN 一篇文章总结了 5 个常见踩坑点，GitHub 上有一个 star 数 2.4k 的完整示例项目。`,
          rawResult: JSON.stringify({ query: q + ' 实践 经验', total: 3, results: results2.slice(0, 3) }, null, 2)
        }
      }
    ],
    finalAnswer: buildSearchFinalAnswer(q, results1)
  }
}

/** 搜索场景的终答 —— 基于真实搜索结果组织回复 */
function buildSearchFinalAnswer(q, results) {
  const top = results.slice(0, 3).map(r => '- ' + r.title + '（' + r.source + '）').join('\n')
  return `关于「${q}」，我帮你搜了一圈主流技术社区，以下是整理后的要点：\n\n**核心结论**：\n${results[0].snippet}\n\n**推荐资料**：\n${top}\n\n如果你对某个方向特别感兴趣（比如具体实现、性能对比、适用场景），可以直接问我，我再深挖。`
}

/** 从问题中提取关键词 */
function extractKeyword(q) {
  return q
    .replace(/^(帮我|帮我写|帮我分析|请|我想|我要|告诉我|查找|搜索|查询|列出|读取|处理|解释|说明|什么是|如何|怎么|怎样)\s*/g, '')
    .trim()
    .slice(0, 12)
}

/** 代码类 */
function buildCodeScenario(q) {
  const kw = extractKeyword(q) || 'task'
  return {
    cycles: [
      {
        thought: { content: `用户想要「${q.slice(0, 25)}${q.length > 25 ? '...' : ''}」。这是一个编程任务，我在代码沙箱里写一个可用的示例并运行验证。` },
        action: {
          ...TOOL_STYLE.executeCode,
          params: {
            language: 'python',
            code: 'def process_' + kw.replace(/\s+/g, '_') + '():\n    """' + q.slice(0, 40) + '"""\n    result = []\n    # TODO: 核心逻辑\n    return result\n\nif __name__ == "__main__":\n    print(process_' + kw.replace(/\s+/g, '_') + '())'
          }
        },
        observation: {
          summary: '代码执行成功，无异常退出。函数可直接调用。',
          rawResult: JSON.stringify({ output: 'done: ' + kw, exitCode: 0, duration: '0.15s' }, null, 2)
        }
      }
    ],
    finalAnswer: '关于「' + q + '」：\n\n这里有一个简单的实现示例，核心逻辑直接处理你描述的场景。代码已在沙箱中运行验证，可以正常运行。\n\n如果需要改成其他语言（JavaScript / Java / Go），或者要处理边界情况，告诉我即可。'
  }
}

/** 数据分析类 */
function buildDataScenario(q) {
  return {
    cycles: [
      {
        thought: { content: `用户需要对数据进行分析（「${q}」）。先查看数据目录，找到可用的数据集。` },
        action: {
          ...TOOL_STYLE.listFiles,
          params: { path: '/data', pattern: '*.csv' }
        },
        observation: {
          summary: '发现 /data 目录下有 3 个 CSV 文件可用作分析数据源。',
          rawResult: JSON.stringify({ path: '/data', files: ['sales_2025.csv', 'users.csv', 'metrics.json'] }, null, 2)
        }
      },
      {
        thought: { content: '数据文件已确认。使用代码执行工具加载数据并进行基本统计分析。' },
        action: {
          ...TOOL_STYLE.executeCode,
          params: { language: 'python', code: 'import pandas as pd\ndf = pd.read_csv("/data/sales_2025.csv")\nprint(df.describe())\nprint("\\n相关性矩阵：")\nprint(df.corr())' }
        },
        observation: {
          summary: '代码成功运行。数据包含数值型字段，标准差和相关性分析完成。',
          rawResult: JSON.stringify({ output: '       sales_qty    revenue\ncount    10000.0   10000.0\nmean       152.3   45680.5\nstd         48.7   14230.1', exitCode: 0 }, null, 2)
        }
      },
      {
        thought: { content: '分析完成，整理关键指标并生成总结报告。' },
        action: {
          ...TOOL_STYLE.readFile,
          params: { path: '/data/sales_2025.csv', limit: 3 }
        },
        observation: {
          summary: '确认数据结构完整，样本充足（10,000条记录），分析结论可信。',
          rawResult: JSON.stringify({ row_count: 10000, columns: ['month', 'product', 'sales_qty', 'unit_price', 'revenue'] }, null, 2)
        }
      }
    ],
    finalAnswer: `## 数据分析结果（${q}）\n\n基于 `/data/sales_2025.csv` 的分析：\n\n| 指标 | 值 |\n|------|----|\n| 总记录数 | 10,000 |\n| 月均销量 | 152 件 |\n| 月均销售额 | ¥45,680 |\n| 销量标准差 | 48.7 |\n\n**建议**：可以进一步按产品类别或时间段细分，获取更深入的洞察。`
  }
}

/** 文件操作类 */
function buildFileScenario(q) {
  return {
    cycles: [
      {
        thought: { content: `用户想要进行文件操作（「${q}」）。先列出工作目录，了解当前文件结构。` },
        action: {
          ...TOOL_STYLE.listFiles,
          params: { path: '.', recursive: false }
        },
        observation: {
          summary: '工作目录包含：src/、config.yaml、README.md、requirements.txt。',
          rawResult: JSON.stringify({ path: '.', entries: ['src/', 'config.yaml', 'README.md', 'requirements.txt'] }, null, 2)
        }
      },
      {
        thought: { content: '目录结构已确认。读取用户关心的文件内容。' },
        action: {
          ...TOOL_STYLE.readFile,
          params: { path: 'config.yaml', limit: 20 }
        },
        observation: {
          summary: 'config.yaml 包含应用的主要配置项，如数据库连接、API端口、日志级别等。',
          rawResult: JSON.stringify({ file: 'config.yaml', lines: 25, content_preview: 'server:\n  port: 8123\ndatabase:\n  url: localhost:5432\n...' }, null, 2)
        }
      }
    ],
    finalAnswer: `已完成文件操作（${q}）。\n\n当前工作目录结构：\n\`\`\`\n.\n├── src/\n├── config.yaml\n├── README.md\n└── requirements.txt\n\`\`\`\n\n如需进一步处理特定文件，请告诉我。`
  }
}

/** 知识库类 */
function buildKnowledgeScenario(q) {
  return {
    cycles: [
      {
        thought: { content: `用户想了解内部知识（「${q}」）。检索本地知识库获取相关文档。` },
        action: {
          ...TOOL_STYLE.searchKnowledge,
          params: { query: q, topK: 3 }
        },
        observation: {
          summary: '知识库命中 2 个相关文档分块，信息充足。',
          rawResult: JSON.stringify({
            matches: [
              { source: 'README.md', score: 0.91, snippet: `${q}相关的基础说明和快速开始指南...` },
              { source: 'architecture.md', score: 0.82, snippet: `系统架构：ReActAgent → ToolCallAgent → LightManus...` }
            ]
          }, null, 2)
        }
      }
    ],
    finalAnswer: '## 关于「' + q + '」\n\n根据知识库文档：\n\n**快速开始**：\nmvn spring-boot:run\n\n**项目结构**：\n- backend/ — Spring Boot 后端（Java 17）\n- frontend/ — Vue 3 + Vite 前端\n\n**核心依赖**：Spring AI、PgVector、Vue 3。\n\n需要更详细的某个方面可以继续问我。'
  }
}

// 异步模拟 ReAct 渐进流式输出
async function runMockReActStream(msgIndex, scenario) {
  const msg = () => chatStore.activeMessages[msgIndex]
  const delay = (ms) => new Promise(r => setTimeout(r, ms))

  msg().reactCycles = []
  msg().finalAnswer = ''

  for (let i = 0; i < scenario.cycles.length; i++) {
    const cycleData = scenario.cycles[i]
    const roundIndex = i

    // 创建空的轮次骨架
    msg().reactCycles.push({
      round: i + 1,
      thought: { content: '' },
      action: { ...cycleData.action, expanded: false },
      observation: { ...cycleData.observation, expanded: false }
    })

    // 流式输出 Thought（逐字）
    for (let j = 0; j < cycleData.thought.content.length; j++) {
      msg().reactCycles[roundIndex].thought.content += cycleData.thought.content[j]
      await delay(18)
    }
    await delay(300)

    // 快照标记 action（不流式，一帧到位即可）
    // （params 已通过骨架设置，这里无需额外操作）
    await delay(400)

    // 流式输出 Observation summary（逐字）
    const obsSummary = msg().reactCycles[roundIndex].observation.summary
    for (let k = 0; k < cycleData.observation.summary.length; k++) {
      msg().reactCycles[roundIndex].observation.summary = cycleData.observation.summary.slice(0, k + 1)
      await delay(14)
    }
    await delay(500)
  }

  // 最终答案
  connectionStatus.value = 'disconnected'
  msg().finalAnswer = scenario.finalAnswer

  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

const goBack = () => router.push('/')
const logout = () => {
  userStore.logout()
  router.push('/login')
}

onMounted(async () => {
  // 初始化：从后端加载会话列表与历史消息（无会话则新建）
  await chatStore.init()
  // 空会话时不塞欢迎语——ChatRoom 的空状态引导页（功能卡片）自然展示
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  if (capHintTimer) clearTimeout(capHintTimer)
})
</script>

<style scoped>
/* =============================================
   SuperAgent — 浅色清爽风
   中文内容全部使用 var(--font-body) 字体栈。
   ============================================= */

.super-agent-container {
  display: flex;
  flex-direction: row;
  min-height: 100vh;
  background: var(--bg-base);
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
}

/* === Header === */
.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 14px 24px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-primary);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  color: var(--text-secondary);
  transition: color 0.2s;
  justify-self: start;
  background: none;
  border: none;
  padding: 6px 10px;
  border-radius: 8px;
  cursor: pointer;
}
.back-button:hover {
  color: var(--accent);
  background: var(--accent-bg);
}

.title {
  font-family: var(--font-display);
  font-size: 1.0625rem;
  font-weight: 600;
  margin: 0;
  text-align: center;
  justify-self: center;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  justify-self: end;
  gap: 8px;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 10px;
  margin-left: 4px;
  border-left: 1px solid var(--border-subtle);
}
.user-avatar {
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #818cf8, #6366f1);
  color: white;
  font-size: 0.75rem;
  font-weight: 600;
  flex-shrink: 0;
}
.user-name {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logout-btn {
  color: var(--text-tertiary);
}
.logout-btn:hover {
  color: #dc2626;
  border-color: rgba(220,38,38,0.3);
  background: rgba(220,38,38,0.05);
}

.header-btn {
  display: inline-flex;
  align-items: center;
  font-size: 0.8125rem;
  padding: 6px 12px;
  border-radius: 8px;
  background: var(--bg-base);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  transition: background 0.2s, color 0.2s, border-color 0.2s;
  cursor: pointer;
}
.header-btn:hover {
  background: white;
  color: var(--accent);
  border-color: var(--border-active);
  box-shadow: 0 2px 8px rgba(79,70,229,0.08);
}

.spinner {
  display: inline-block;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* === Upload Toast === */
.upload-toast {
  position: fixed;
  top: 70px;
  right: 20px;
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 0.875rem;
  z-index: 100;
  max-width: 400px;
  background: white;
  color: var(--text-primary);
  border: 1px solid var(--border-subtle);
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}
.upload-toast.success {
  border-left: 3px solid #10b981;
  color: #059669;
}
.upload-toast.error {
  border-left: 3px solid #ef4444;
  color: #dc2626;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* === Modal === */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(20, 20, 30, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  backdrop-filter: blur(4px);
}
.modal-dialog {
  background: white;
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  width: 90%;
  max-width: 480px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 40px rgba(0,0,0,0.12);
}
.modal-sm { max-width: 380px; }
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
}
.modal-header h3 {
  margin: 0;
  font-size: 1rem;
  color: var(--text-primary);
}
.modal-close {
  font-size: 1.5rem;
  cursor: pointer;
  color: var(--text-tertiary);
  background: none;
  border: none;
  padding: 0 4px;
}
.modal-close:hover { color: var(--text-primary); }
.modal-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
  color: var(--text-primary);
}
.modal-loading, .modal-empty {
  text-align: center;
  color: var(--text-tertiary);
  padding: 30px 0;
}

/* ---------- 拖拽上传区 ---------- */
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 28px 16px;
  margin-bottom: 16px;
  border: 2px dashed var(--border-subtle);
  border-radius: 12px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}
.drop-zone:hover {
  border-color: var(--border-active);
  color: var(--accent);
  background: var(--accent-bg);
}
.drop-zone.active {
  border-color: var(--accent);
  background: var(--accent-bg);
  color: var(--accent);
}
.drop-zone.uploading {
  cursor: default;
}
.drop-zone p {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin: 0;
}
.drop-hint {
  font-size: 0.75rem;
  color: var(--text-tertiary);
}
.upload-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 240px;
  font-size: 0.8125rem;
  color: var(--accent);
}
.progress-bar {
  width: 100%;
  height: 6px;
  background: var(--bg-base);
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #818cf8, #4f46e5);
  border-radius: 3px;
  transition: width 0.2s;
}
.modal-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* === File List === */
.file-list { display: flex; flex-direction: column; gap: 8px; }
.file-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
}
.file-name {
  flex: 1;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-chunks {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  color: var(--text-tertiary);
  white-space: nowrap;
}
.file-delete-btn {
  font-size: 0.75rem;
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
  cursor: pointer;
  transition: background 0.2s;
}
.file-delete-btn:hover { background: rgba(239, 68, 68, 0.15); }
.file-delete-btn:disabled { opacity: 0.5; cursor: default; }

/* === Manage Dialog 扩展 === */
.modal-dialog.modal-lg { max-width: 600px; width: 92%; }

/* 校验错误提示 */
.file-error-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 0.8125rem;
}
/* 选中文件展示条 */
.file-selected-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--accent-bg);
  border: 1px solid var(--border-active);
  font-size: 0.8125rem;
}
.file-selected-name { color: var(--text-primary); font-weight: 500; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-selected-size { color: var(--text-tertiary); font-size: 0.75rem; }
.file-selected-upload {
  padding: 4px 12px;
  border-radius: 6px;
  background: var(--accent);
  color: white;
  font-size: 0.75rem;
  border: none;
  cursor: pointer;
}
.file-selected-upload:hover { background: #4338ca; }
.file-selected-cancel {
  padding: 4px 10px;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.75rem;
  border: 1px solid var(--border-subtle);
  cursor: pointer;
}
.file-selected-cancel:hover { background: #e5e7eb; }

/* 批量操作栏 */
.file-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 6px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.toolbar-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8125rem;
  color: var(--text-secondary);
  cursor: pointer;
}
.toolbar-checkbox input { accent-color: var(--accent); }
.toolbar-btn {
  font-size: 0.75rem;
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid var(--border-subtle);
  background: white;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}
.toolbar-btn:disabled { opacity: 0.4; cursor: default; }
.toolbar-btn-danger:not(:disabled):hover {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
  border-color: rgba(239, 68, 68, 0.3);
}

/* 文件列表行（增强版） */
.file-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.file-row:hover {
  background: white;
  border-color: var(--border-active);
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.06);
}
.file-row-selected { background: var(--accent-bg); border-color: var(--border-active); }
.file-checkbox { accent-color: var(--accent); flex-shrink: 0; }
.file-info { flex: 1; min-width: 0; }
.file-line1 { display: flex; align-items: center; gap: 8px; }
.file-name {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--accent);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: underline;
  text-decoration-color: transparent;
  transition: text-decoration-color 0.15s;
}
.file-name:hover { text-decoration-color: var(--accent); }
.file-line2 {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 0.75rem;
  color: var(--text-tertiary);
  flex-wrap: wrap;
}
.file-meta-sep { opacity: 0.5; margin: 0 2px; }

/* 状态徽章 */
.file-badge {
  font-size: 0.625rem;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}
.badge-success { background: rgba(16, 185, 129, 0.1); color: #059669; }
.badge-pending { background: rgba(245, 158, 11, 0.1); color: #b45309; }
.badge-error   { background: rgba(239, 68, 68, 0.1); color: #dc2626; }

/* 操作按钮组 */
.file-actions { display: flex; gap: 4px; flex-shrink: 0; }
.file-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all 0.15s;
}
.file-action-btn:hover {
  background: white;
  border-color: var(--border-subtle);
  color: var(--accent);
}
.file-action-danger:hover {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.2);
  color: #dc2626;
}
.file-action-btn:disabled { opacity: 0.4; cursor: default; }

/* 空状态 */
.modal-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--text-tertiary);
  text-align: center;
}
.modal-empty p { font-size: 0.9375rem; color: var(--text-secondary); margin: 0; }
.modal-empty span { font-size: 0.8125rem; }

/* 检索测试 */
.search-test {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
}
.search-test-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 10px;
}
.search-test-bar {
  display: flex;
  gap: 8px;
}
.search-test-input {
  flex: 1;
  padding: 7px 12px;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  font-size: 0.8125rem;
  background: var(--bg-base);
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.2s;
}
.search-test-input:focus { border-color: var(--accent); background: white; }
.search-test-select {
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  font-size: 0.8125rem;
  background: white;
  color: var(--text-primary);
  outline: none;
  max-width: 140px;
}
.search-test-btn {
  padding: 7px 16px;
  border-radius: 8px;
  background: var(--accent);
  color: white;
  font-size: 0.8125rem;
  border: none;
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}
.search-test-btn:hover { background: #4338ca; }
.search-test-btn:disabled { opacity: 0.6; cursor: default; }
.search-test-results { margin-top: 10px; }
.search-test-empty { text-align: center; color: var(--text-tertiary); font-size: 0.8125rem; padding: 12px 0; }
.search-test-hits-info { font-size: 0.75rem; color: var(--text-tertiary); margin-bottom: 6px; }
.search-test-hit {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
  margin-bottom: 6px;
}
.hit-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.hit-source { font-size: 0.75rem; font-weight: 500; color: var(--accent); }
.hit-score { font-size: 0.6875rem; color: var(--text-tertiary); font-family: var(--font-mono); }
.hit-snippet { font-size: 0.8125rem; color: var(--text-secondary); line-height: 1.5; }

/* 预览弹窗 */
.preview-body { display: flex; flex-direction: column; }
.preview-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.75rem;
  color: var(--text-tertiary);
  margin-bottom: 10px;
}
.preview-truncated { color: #b45309; }
.preview-content {
  font-family: var(--font-body);
  font-size: 0.875rem;
  line-height: 1.7;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 50vh;
  overflow-y: auto;
  padding: 12px;
  background: var(--bg-base);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  margin: 0;
}

/* 底部按钮扩展 */
.modal-btn-text {
  font-size: 0.8125rem;
  padding: 6px 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  transition: color 0.2s;
}
.modal-btn-text:disabled { opacity: 0.4; cursor: default; }
.modal-btn-danger-text:not(:disabled):hover { color: #dc2626; }

/* === 能力提示横幅 === */
.cap-hint-banner {
  position: fixed;
  top: 64px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border: 1px solid #f59e0b;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.2);
  font-size: 0.8125rem;
  color: #92400e;
  max-width: 90vw;
  animation: slideDown 0.25s ease;
}
.cap-hint-banner span { flex: 1; }
.cap-hint-btn {
  padding: 4px 12px;
  border: 1px solid #f59e0b;
  border-radius: 6px;
  background: white;
  color: #b45309;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
}
.cap-hint-btn:hover { background: #fffbeb; }
.cap-hint-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  color: #b45309;
  font-size: 1.25rem;
  cursor: pointer;
  border-radius: 4px;
  opacity: 0.7;
  flex-shrink: 0;
}
.cap-hint-close:hover { opacity: 1; background: rgba(180, 83, 9, 0.1); }
@keyframes slideDown {
  from { opacity: 0; transform: translateX(-50%) translateY(-10px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

/* === Modal Buttons === */
.modal-btn {
  font-size: 0.875rem;
  padding: 8px 20px;
  border-radius: 8px;
  cursor: pointer;
  background: var(--accent);
  color: white;
  border: none;
  transition: background 0.2s;
}
.modal-btn:hover { background: #4f46e5; }
.modal-btn-cancel {
  background: var(--bg-base);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
}
.modal-btn-cancel:hover { background: #e5e7eb; }
.modal-btn-danger {
  background: rgba(239, 68, 68, 0.08);
  color: #dc2626;
}
.modal-btn-danger:hover { background: rgba(239, 68, 68, 0.15); }

/* === Modal Animation === */
.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

/* === Chat === */
.content-wrapper { display: flex; flex-direction: column; flex: 1; }
.chat-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  min-height: calc(100vh - 56px - 180px);
  margin-bottom: 16px;
}
.footer-container { margin-top: auto; }

@media (max-width: 768px) {
  .header { padding: 12px 16px; }
  .title { font-size: 0.9375rem; }
  .chat-area { padding: 12px; min-height: calc(100vh - 48px - 160px); margin-bottom: 12px; }
}
@media (max-width: 480px) {
  .header { padding: 10px 12px; }
  .title { font-size: 0.875rem; }
  .chat-area { padding: 8px; min-height: calc(100vh - 42px - 150px); margin-bottom: 8px; }
}
</style>
