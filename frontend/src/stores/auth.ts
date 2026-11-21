import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginPayload, RouteNode, TokenResponse, UserInfo } from '@/types/api'
import { loginApi, logoutApi, profileApi, routesApi } from '@/api/auth'
import { clearAuthStorage, REFRESH_KEY, TOKEN_KEY, USER_KEY } from '@/api/request'

function readUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

/**
 * 登录态与授权信息。
 *
 * token / refreshToken / user 都落 localStorage，刷新页面不需要重新登录；
 * 菜单树来自后端 /api/auth/routes（按角色过滤），前端只负责渲染，
 * 这样「谁能看到哪个菜单」由数据库决定，不在前端硬编码。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<UserInfo | null>(readUser())
  const menus = ref<RouteNode[]>([])
  const initialized = ref(false)
  const loading = ref(false)

  const isLogin = computed(() => token.value.length > 0)
  const nickname = computed(() => user.value?.nickname || user.value?.username || '未登录')
  const roles = computed(() => user.value?.roles ?? [])
  const permissions = computed(() => user.value?.permissions ?? [])

  /** 按钮级权限判断：admin 角色直接放行（后端同样是 admin 绕过校验） */
  function can(perm?: string | null): boolean {
    if (!perm) return true
    if (roles.value.includes('admin')) return true
    return permissions.value.includes(perm)
  }

  function saveTokens(accessToken: string, refreshToken: string): void {
    token.value = accessToken
    localStorage.setItem(TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_KEY, refreshToken)
  }

  function setUser(value: UserInfo | null): void {
    user.value = value
    if (value) {
      localStorage.setItem(USER_KEY, JSON.stringify(value))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  }

  /** 登录：拿到 token 后不立即拉菜单，交给路由守卫统一 init()，避免两处逻辑重复 */
  async function login(payload: LoginPayload): Promise<TokenResponse> {
    loading.value = true
    try {
      const data = await loginApi(payload)
      saveTokens(data.accessToken, data.refreshToken)
      setUser(data.user)
      initialized.value = false
      return data
    } finally {
      loading.value = false
    }
  }

  /** 拉取当前用户资料 + 角色菜单树；失败说明 token 不可用，由调用方清理 */
  async function init(): Promise<void> {
    if (!token.value) throw new Error('未登录')
    const [profile, tree] = await Promise.all([profileApi(), routesApi()])
    setUser(profile)
    menus.value = tree
    initialized.value = true
  }

  function clear(): void {
    token.value = ''
    user.value = null
    menus.value = []
    initialized.value = false
    clearAuthStorage()
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // 后端不可用也要能退出：本地清干净即可
    }
    clear()
  }

  return {
    token,
    user,
    menus,
    initialized,
    loading,
    isLogin,
    nickname,
    roles,
    permissions,
    can,
    saveTokens,
    setUser,
    login,
    init,
    clear,
    logout
  }
})
