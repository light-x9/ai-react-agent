import { defineStore } from 'pinia'

/**
 * 用户登录态
 * token 同时写入 localStorage 'token'，供 api/index.js 的 axios 拦截器与 SSE fetch 读取
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || ''
  }),
  getters: {
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    setAuth(token, username) {
      this.token = token
      this.username = username
      localStorage.setItem('token', token)
      localStorage.setItem('username', username)
    },
    logout() {
      this.token = ''
      this.username = ''
      localStorage.removeItem('token')
      localStorage.removeItem('username')
    }
  },
  persist: true
})
