import { createRouter, createWebHashHistory, type RouteRecordRaw, type Router } from 'vue-router'
import type { Component } from 'vue'
import Layout from '@/layout/index.vue'
import { useAuthStore } from '@/stores/auth'
import { TOKEN_KEY } from '@/api/request'
import type { RouteNode } from '@/types/api'

/**
 * 视图组件用 glob 懒加载：后端 sys_menu.component 存的是 "dashboard/index" 这类相对路径，
 * 前端据此拼出 "../views/dashboard/index.vue"，新增菜单只要按约定放文件即可，不用改路由表。
 */
const viewModules = import.meta.glob('../views/**/*.vue') as Record<
  string,
  () => Promise<{ default: Component }>
>

const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    // 布局容器：动态菜单全部注册成它的子路由
    path: '/',
    name: 'layout',
    component: Layout,
    children: []
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  // hash 路由：静态部署到 Nginx/GitHub Pages 不需要额外 rewrite 规则
  history: createWebHashHistory(),
  routes: staticRoutes,
  scrollBehavior: () => ({ top: 0 })
})

const registeredPaths = new Set<string>()

function resolveView(component: string): () => Promise<{ default: Component }> {
  const key = `../views/${component}.vue`
  return viewModules[key] ?? (() => import('@/views/error/404.vue'))
}

function flattenMenu(nodes: RouteNode[], into: RouteRecordRaw[] = []): RouteRecordRaw[] {
  for (const node of nodes) {
    // type=1 才是可访问页面；type=0 是纯目录，type=2 是按钮（后端已过滤）
    if (node.type === 1 && node.component) {
      into.push({
        path: node.path,
        name: `menu-${node.id}`,
        component: resolveView(node.component),
        meta: { title: node.name, icon: node.icon, perm: node.perm }
      })
    }
    if (node.children?.length) {
      flattenMenu(node.children, into)
    }
  }
  return into
}

/** 把后端菜单树注册成真实路由（幂等：同一路径只注册一次） */
export function registerDynamicRoutes(menus: RouteNode[], target: Router): void {
  for (const record of flattenMenu(menus)) {
    if (registeredPaths.has(record.path)) continue
    registeredPaths.add(record.path)
    target.addRoute('layout', record)
  }
}

function firstMenuPath(nodes: RouteNode[]): string | null {
  for (const node of nodes) {
    if (node.type === 1 && node.component) return node.path
    const child = node.children?.length ? firstMenuPath(node.children) : null
    if (child) return child
  }
  return null
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // 用 localStorage 判定，而不是 store：401 拦截器清理存储后 store 里可能还是旧值
  const hasToken = Boolean(localStorage.getItem(TOKEN_KEY))

  if (to.path === '/login') {
    return hasToken ? { path: '/' } : true
  }

  if (!hasToken) {
    return { path: '/login', query: to.path === '/' ? {} : { redirect: to.path } }
  }

  if (!auth.initialized) {
    try {
      await auth.init()
      registerDynamicRoutes(auth.menus, router)
      // 动态路由刚注册，需要重新解析一次目标地址
      return { path: to.path, query: to.query, hash: to.hash, replace: true }
    } catch {
      auth.clear()
      return { path: '/login', query: { redirect: to.path } }
    }
  }

  if (to.path === '/') {
    const first = firstMenuPath(auth.menus)
    return first ? { path: first } : { path: '/login' }
  }

  const perm = to.meta.perm as string | undefined
  if (perm && !auth.can(perm)) {
    return { path: '/404', query: { perm, from: to.path } }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 业务中台管理后台` : '业务中台管理后台'
})

export default router
