import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // 根路径直接进入对话页（原独立首页已融合为对话页空状态）
  {
    path: '/',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'LightManus-多工具AI智能体平台',
      description: '基于 ReAct 推理框架的 AI Agent 对话平台。前端实时可视化 Thought → Action → Observation 循环，支持 MCP 工具调用与 RAG 知识库检索。',
      requiresAuth: true
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录 - LightManus',
      public: true
    }
  },
  {
    path: '/user',
    name: 'UserCenter',
    component: () => import('../views/UserCenter.vue'),
    meta: {
      title: '个人中心',
      requiresAuth: true
    }
  },
  // 旧路径兼容（书签/外链），重定向到根路径
  { path: '/super-agent', redirect: '/' },
  // 原 Home 组件保留但不挂路由，方便后续回退
  // { path: '/home', name: 'Home', component: () => import('../views/Home.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫：设置标题 + 登录态校验
router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title
  }
  const token = localStorage.getItem('token')
  // 需登录但未登录 → 跳登录页（带 redirect）
  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }
  // 已登录还访问登录页 → 直接进应用
  if (to.name === 'Login' && token) {
    next({ name: 'SuperAgent' })
    return
  }
  next()
})

export default router
