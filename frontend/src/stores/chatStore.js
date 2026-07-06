import { defineStore } from 'pinia'
import {
  listConversations,
  createConversation,
  deleteConversation,
  getMessages,
  saveMessage
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
     * 初始化：从后端加载会话列表，选最近会话并加载消息；无会话则新建
     */
    async init() {
      if (this.initialized) return
      try {
        const res = await listConversations()
        if (res.success && res.sessions && res.sessions.length > 0) {
          this.sessions = res.sessions.map(s => ({
            id: s.id,
            title: s.title,
            messages: [],
            _loaded: false,
            updatedAt: s.updatedAt,
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
     */
    async createSession() {
      try {
        const res = await createConversation('新对话')
        if (res.success) {
          const session = {
            id: res.id,
            title: res.title,
            messages: [],
            _loaded: true,
            updatedAt: Date.now(),
          }
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
     * 删除会话（调后端 + 本地删）
     */
    async deleteSession(id) {
      try {
        await deleteConversation(id)
      } catch (e) {
        console.error('deleteSession failed', e)
      }
      this.sessions = this.sessions.filter(s => String(s.id) !== String(id))
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
  },
})
