import { defineStore } from 'pinia'
import {
  listConversations,
  createConversation,
  deleteConversation,
  getMessages,
  saveMessage,
  renameConversation
} from '@/api'

/**
 * 会话 Store（后端权威 + 本地缓存）
 *
 * 会话列表与消息历史以后端为准，localStorage 仅作首屏缓存。
 * 切换会话时从后端拉取消息历史；发送消息后异步保存到后端。
 */
export const useChatStore = defineStore('chat', {

  state: () => ({
    // 会话列表，每个元素：{ id, title, messages[], _loaded, updatedAt }
    sessions: [],
    // 当前激活会话 id（后端 Long 转字符串）
    activeId: null,
    // 初始化状态
    initialized: false,
  }),

  persist: {
    key: 'react-agent-chat',
    storage: localStorage,
    paths: ['sessions', 'activeId'],
  },

  getters: {
    activeMessages: (state) => {
      const session = state.sessions.find(s => String(s.id) === String(state.activeId))
      return session ? session.messages : []
    },
    currentSession: (state) => {
      return state.sessions.find(s => String(s.id) === String(state.activeId)) || null
    },
  },

  actions: {

    /**
     * 重置 store：清除会话列表、激活状态、初始化标志、localStorage 缓存。
     * 用于登出 / 切换账号时防止跨用户数据泄漏。
     */
    reset() {
      this.sessions = []
      this.activeId = null
      this.initialized = false
      localStorage.removeItem('react-agent-chat')
    },

    /**
     * 初始化：从后端加载会话列表，选最近会话并加载消息；无会话则新建
     */
    async init() {
      if (this.initialized) return
      // 安全网：清除可能残留的上一用户缓存（防跨账号数据泄漏）
      this.sessions = []
      this.activeId = null
      try {
        const res = await listConversations()
        if (res.success && res.sessions && res.sessions.length > 0) {
          this.sessions = res.sessions.map(s => ({
            id: s.id,
            title: s.title,
            messages: [],
            _loaded: false,
            updatedAt: s.updatedAt,
            capability: 'chat', // 从后端加载的会话默认为普通对话类型
          }))
          this.activeId = this.sessions[0].id
          await this.loadMessages(this.activeId)
        } else {
          // 无会话，新建一个
          await this.createSession()
        }
      } catch (e) {
        console.error('chatStore init failed', e)
      }
      this.initialized = true
    },

    /**
     * 创建新会话（调后端）
     * @param {string} [capability] 会话能力类型：'chat' | 'webSearch' | 'knowledgeBase' | 'both'
     */
    async createSession(capability = 'chat') {
      try {
        const res = await createConversation('新对话')
        if (res.success) {
          const session = {
            id: res.id,
            title: res.title,
            messages: [],
            _loaded: true,
            updatedAt: Date.now(),
            capability, // 会话能力类型（用于侧边栏图标区分）
          }
          // capability 不持久化，仅当前页面生命周期有效
          this.sessions.unshift(session)
          this.activeId = res.id
          return res.id
        }
      } catch (e) {
        console.error('createSession failed', e)
      }
      return null
    },

    /**
     * 切换会话：设激活 + 按需加载消息历史
     */
    async switchSession(id) {
      this.activeId = id
      const session = this.sessions.find(s => String(s.id) === String(id))
      if (session && !session._loaded) {
        await this.loadMessages(id)
      }
    },

    /**
     * 从后端加载某会话的消息历史
     */
    async loadMessages(id) {
      const session = this.sessions.find(s => String(s.id) === String(id))
      if (!session) return
      try {
        const res = await getMessages(id)
        if (res.success && res.messages) {
          session.messages = res.messages.map(m => ({
            content: m.content,
            isUser: m.role === 'user',
            type: m.role === 'system' ? 'system' : '',
            time: m.createdAt,
            reactCycles: [],
            finalAnswer: '',
            _cycleIndex: 0,
          }))
          session._loaded = true
        }
      } catch (e) {
        console.error('loadMessages failed', e)
      }
    },

    /**
     * 删除会话（先调后端，成功后才更新本地列表）
     * @param {number|string} id - 会话 id
     * @throws 删除失败时抛出错误，由调用方处理
     */
    async deleteSession(id) {
      // 先调后端，失败时直接抛出，本地不做任何变更
      await deleteConversation(id)
      // 后端成功 → 移除本地
      this.sessions = this.sessions.filter(s => String(s.id) !== String(id))
      // 若删的是当前激活会话 → 切到别的会话或新建
      if (String(this.activeId) === String(id)) {
        if (this.sessions.length > 0) {
          this.activeId = this.sessions[0].id
          await this.loadMessages(this.activeId)
        } else {
          await this.createSession()
        }
      }
    },

    /**
     * 向当前会话追加一条消息（仅本地，用于渲染）
     */
    addMessageToActive(content, isUser, type = '', extra = {}) {
      const session = this.sessions.find(s => String(s.id) === String(this.activeId))
      if (!session) return
      session.messages.push({
        content,
        isUser,
        type,
        time: Date.now(),
        reactCycles: extra.reactCycles || [],
        finalAnswer: extra.finalAnswer || '',
        _cycleIndex: extra._cycleIndex || 0,
      })
      session.updatedAt = Date.now()
    },

    /**
     * 流式输出：追加/覆盖最后一条消息内容（仅本地渲染，流式结束才保存后端）
     */
    updateStreamContent(content, append = true) {
      const session = this.sessions.find(s => String(s.id) === String(this.activeId))
      if (!session || session.messages.length === 0) return
      const lastMsg = session.messages[session.messages.length - 1]
      if (append) {
        lastMsg.content += content
      } else {
        lastMsg.content = content
      }
    },

    /**
     * 持久化一条消息到后端（user/assistant/system）
     */
    async persistMessage(role, content) {
      const session = this.sessions.find(s => String(s.id) === String(this.activeId))
      if (!session || !content) return
      try {
        const res = await saveMessage(session.id, role, content)
        if (res.success && res.title && session.title === '新对话') {
          session.title = res.title
        }
      } catch (e) {
        console.error('persistMessage failed', e)
      }
    },

    updateSessionTitle(id, title) {
      const session = this.sessions.find(s => String(s.id) === String(id))
      if (session) {
        session.title = title
        session.updatedAt = Date.now()
      }
    },

    /**
     * 设置会话能力类型（用于侧边栏图标区分）
     */
    setCapability(id, capability) {
      const session = this.sessions.find(s => String(s.id) === String(id))
      if (session) {
        session.capability = capability
      }
    },

    /**
     * 重命名会话（调后端 + 更新本地）
     */
    async renameSession(id, title) {
      const session = this.sessions.find(s => String(s.id) === String(id))
      if (!session || !title || !title.trim()) return
      const trimmed = title.trim()
      try {
        const res = await renameConversation(id, trimmed)
        if (res.success) {
          session.title = res.title || trimmed
          session.updatedAt = Date.now()
        }
      } catch (e) {
        // 后端失败也更新本地，保证用户体验
        session.title = trimmed
        session.updatedAt = Date.now()
        console.error('renameSession failed', e)
      }
    },
  },
})
