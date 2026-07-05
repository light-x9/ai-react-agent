import { defineStore } from 'pinia'

/**
 * 会话持久化 Store
 * 
 * 把 messages 从「组件内部变量」提升到「全局持久化存储」，
 * 解决路由切换/页面刷新后对话丢失的问题。
 * 
 * 数据结构：
 *   sessions[]  ← 多会话列表，每个会话包含 chatId + messages
 *   activeChatId ← 当前激活的会话 ID
 * 
 * 持久化：通过 pinia-plugin-persistedstate 自动同步到 localStorage
 */
export const useChatStore = defineStore('chat', {

  // ========== 状态 ==========
  state: () => ({
    // 会话列表，每个元素：{ chatId, title, messages[], createdAt, updatedAt }
    sessions: [],
    // 当前激活的会话 ID
    activeChatId: null,
  }),

  // ========== 持久化配置 ==========
  persist: {
    key: 'react-agent-chat',    // localStorage 的 key 名
    storage: localStorage,
    // 只持久化 sessions 和 activeChatId，不存其他临时状态
    paths: ['sessions', 'activeChatId'],
  },

  // ========== 计算属性 ==========
  getters: {
    /** 当前会话的消息列表（组件绑定的核心数据） */
    activeMessages: (state) => {
      const session = state.sessions.find(s => s.chatId === state.activeChatId)
      return session ? session.messages : []
    },

    /** 当前会话对象 */
    currentSession: (state) => {
      return state.sessions.find(s => s.chatId === state.activeChatId) || null
    },
  },

  // ========== 操作方法 ==========
  actions: {

    /**
     * 创建新会话
     * @returns {string} 新会话的 chatId
     */
    createSession() {
      const chatId = Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
      const session = {
        chatId,
        title: '新对话',
        messages: [],
        createdAt: Date.now(),
        updatedAt: Date.now(),
      }
      this.sessions.push(session)
      this.activeChatId = chatId
      return chatId
    },

    /**
     * 切换当前会话
     */
    switchSession(chatId) {
      this.activeChatId = chatId
    },

    /**
     * 删除会话
     * 删完后自动切换到最近会话，若没有则新建
     */
    deleteSession(chatId) {
      this.sessions = this.sessions.filter(s => s.chatId !== chatId)
      if (this.activeChatId === chatId) {
        if (this.sessions.length > 0) {
          // 切换到最后一个会话
          this.activeChatId = this.sessions[this.sessions.length - 1].chatId
        } else {
          // 没有会话了，新建一个
          this.createSession()
        }
      }
    },

    /**
     * 向当前会话追加一条消息
     * @param {string} content  消息内容
     * @param {boolean} isUser  是否用户消息
     * @param {string} type     消息类型：'' 普通, 'system' 系统提示
     * @param {object} extra    扩展字段：reactCycles, finalAnswer, _cycleIndex
     */
    addMessageToActive(content, isUser, type = '', extra = {}) {
      const session = this.sessions.find(s => s.chatId === this.activeChatId)
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
     * 流式输出：追加或覆盖最后一条消息的内容
     * （用于 SSE 回调中逐 token 更新 AI 回答）
     * @param {string} content  要追加的文本
     * @param {boolean} append  true=追加, false=覆盖
     */
    updateStreamContent(content, append = true) {
      const session = this.sessions.find(s => s.chatId === this.activeChatId)
      if (!session || session.messages.length === 0) return
      const lastMsg = session.messages[session.messages.length - 1]
      if (append) {
        lastMsg.content += content
      } else {
        lastMsg.content = content
      }
    },

    /**
     * 更新会话标题（默认取第一条用户消息的前 20 个字）
     */
    updateSessionTitle(chatId, title) {
      const session = this.sessions.find(s => s.chatId === chatId)
      if (session) {
        session.title = title
        session.updatedAt = Date.now()
      }
    },
  },
})
