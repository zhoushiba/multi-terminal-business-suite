<template>
  <el-container class="layout">
    <el-aside class="layout__aside" :width="collapsed ? '64px' : '232px'">
      <div class="brand" :class="{ 'brand--mini': collapsed }">
        <div class="brand__logo">中</div>
        <div v-show="!collapsed" class="brand__text">
          <div class="brand__title">业务中台</div>
          <div class="brand__sub">Multi-Terminal Suite</div>
        </div>
      </div>

      <el-scrollbar class="layout__menu">
        <el-menu
          :default-active="activePath"
          :collapse="collapsed"
          :collapse-transition="false"
          unique-opened
          router
          background-color="#1f2740"
          text-color="#c2c9dc"
          active-text-color="#ffffff"
          class="side-menu"
        >
          <MenuNode :nodes="auth.menus" />
        </el-menu>
      </el-scrollbar>

      <div v-show="!collapsed" class="layout__aside-foot">
        Spring Boot 3.3 · Vue 3.5
      </div>
    </el-aside>

    <el-container class="layout__body">
      <el-header class="layout__header" height="56px">
        <div class="header__left">
          <el-icon class="header__action" @click="toggleCollapse">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
              {{ item }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header__right">
          <el-tooltip :content="online ? 'GET /api/ping 正常' : '无法访问后端服务'" placement="bottom">
            <span class="health">
              <i class="health__dot" :class="online ? 'is-on' : 'is-off'"></i>
              {{ online ? '后端已连接' : '后端未连接' }}
            </span>
          </el-tooltip>
          <el-tag effect="plain" size="small" round>{{ roleLabel }}</el-tag>
          <el-dropdown trigger="click" @command="onCommand">
            <span class="user">
              <el-avatar :size="26" class="user__avatar">{{ initial }}</el-avatar>
              <span class="user__name">{{ auth.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">账号信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout__content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>

      <el-footer height="34px" class="layout__footer">
        © 2026 多端业务中台套件 · Spring Boot + Vue3 + 微信小程序
      </el-footer>
    </el-container>

    <el-dialog v-model="profileVisible" title="账号信息" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ auth.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ auth.user?.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ auth.user?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="role in auth.roles" :key="role" size="small" class="mr-6">{{ role }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="权限数量">{{ auth.permissions.length }} 项</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="profileVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import MenuNode from './MenuNode.vue'
import { useAuthStore } from '@/stores/auth'
import { ping } from '@/api/system'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const collapsed = ref(localStorage.getItem('suite_sidebar_collapsed') === '1')
const online = ref(false)
const profileVisible = ref(false)

const activePath = computed(() => route.path)
const initial = computed(() => (auth.nickname || 'U').slice(0, 1).toUpperCase())

const ROLE_LABELS: Record<string, string> = {
  admin: '超级管理员',
  editor: '内容运营',
  operator: '活动运营'
}
const roleLabel = computed(() => {
  const first = auth.roles[0]
  return first ? ROLE_LABELS[first] ?? first : '访客'
})

const breadcrumbs = computed(() =>
  route.matched
    .filter((item) => item.meta.title && item.name !== 'layout')
    .map((item) => item.meta.title as string)
)

function toggleCollapse(): void {
  collapsed.value = !collapsed.value
  localStorage.setItem('suite_sidebar_collapsed', collapsed.value ? '1' : '0')
}

async function onCommand(command: string): Promise<void> {
  if (command === 'profile') {
    profileVisible.value = true
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
    await auth.logout()
    router.replace('/login')
  }
}

onMounted(async () => {
  try {
    await ping()
    online.value = true
  } catch {
    online.value = false
  }
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

.layout__aside {
  display: flex;
  flex-direction: column;
  background: var(--suite-sidebar-bg);
  transition: width 0.2s ease;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.brand--mini {
  justify-content: center;
  padding: 0;
}

.brand__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(135deg, #4f8bff, #2f6bff);
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}

.brand__title {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  line-height: 1.2;
}

.brand__sub {
  font-size: 10px;
  letter-spacing: 0.4px;
  color: rgba(255, 255, 255, 0.45);
}

.layout__menu {
  flex: 1;
  padding: 8px 0;
}

.side-menu {
  border-right: none;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(47, 107, 255, 0.92), rgba(47, 107, 255, 0.6));
  border-radius: 6px;
  margin: 0 8px;
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  margin: 2px 8px;
  border-radius: 6px;
}

.layout__aside-foot {
  padding: 10px 16px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.layout__body {
  background: var(--suite-bg);
  min-width: 0;
}

.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  background: #fff;
  border-bottom: 1px solid var(--suite-border);
}

.header__left,
.header__right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header__action {
  font-size: 18px;
  cursor: pointer;
  color: var(--suite-text-secondary);
}

.header__action:hover {
  color: var(--suite-primary);
}

.health {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--suite-text-secondary);
}

.health__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.health__dot.is-on {
  background: #12b76a;
  box-shadow: 0 0 0 3px rgba(18, 183, 106, 0.15);
}

.health__dot.is-off {
  background: #f04438;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user__avatar {
  background: var(--suite-primary);
  font-size: 12px;
}

.user__name {
  font-size: 13px;
}

.layout__content {
  padding: 16px 18px;
  overflow-y: auto;
}

.layout__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--suite-text-secondary);
  background: transparent;
}

.mr-6 {
  margin-right: 6px;
}
</style>
