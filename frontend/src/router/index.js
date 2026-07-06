import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - AI超级智能体应用平台',
      description: 'AI超级智能体应用平台提供AI智能体对话服务，满足您的各种AI对话需求'
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录 - AI超级智能体',
      public: true
    }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'AI超级智能体 - AI超级智能体应用平台',
      description: 'AI超级智能体是全能助手，能解答各类专业问题，提供精准建议和解决方案',
      requiresAuth: true
    }
  }
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
