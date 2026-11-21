<template>
  <div class="login">
    <div class="login__brand">
      <div class="brand-inner">
        <div class="brand-mark">中</div>
        <h1 class="brand-title">多端业务中台套件</h1>
        <p class="brand-desc">
          一套后端同时服务管理后台与微信小程序：<br />
          Spring Boot 3 提供 RESTful 接口，Vue3 管理端负责运营，小程序负责触达游客。
        </p>
        <ul class="brand-list">
          <li><span class="dot"></span>JWT 双令牌 + RBAC 菜单/按钮级权限</li>
          <li><span class="dot"></span>内容审核流转、活动场次与报名核销</li>
          <li><span class="dot"></span>地理点位与数据看板（统计趋势/转化率）</li>
          <li><span class="dot"></span>AI 摘要与问答（可离线 Mock，无网络也能演示）</li>
        </ul>
        <div class="brand-stack">
          <span>Spring Boot 3.3</span><span>MyBatis-Plus</span><span>MySQL / H2</span>
          <span>Vue 3.5</span><span>Element Plus</span><span>微信小程序</span>
        </div>
      </div>
    </div>

    <div class="login__panel">
      <div class="login-card">
        <h2 class="login-card__title">登录管理后台</h2>
        <p class="login-card__sub">使用种子账号即可体验完整权限体系</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-card__submit" :loading="loading" @click="onSubmit">
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-card__accounts">
          <span class="login-card__accounts-title">演示账号</span>
          <el-button
            v-for="item in demoAccounts"
            :key="item.username"
            size="small"
            text
            bg
            @click="fill(item.username, item.password)"
          >
            {{ item.label }}
          </el-button>
        </div>

        <p class="login-card__tip">
          接口地址 <code>{{ apiBase }}</code> · 后端健康检查
          <code>GET /api/ping</code>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'

const form = reactive({ username: 'admin', password: 'admin123' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

const demoAccounts = [
  { label: '超级管理员', username: 'admin', password: 'admin123' },
  { label: '内容运营', username: 'editor', password: 'editor123' },
  { label: '活动运营', username: 'operator', password: 'operator123' }
]

function fill(username: string, password: string): void {
  form.username = username
  form.password = password
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.login({ username: form.username, password: form.password })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    // 路由守卫会调用 init() 拉取资料与菜单，然后按首个可见菜单跳转
    await router.replace(redirect)
  } catch {
    // 错误提示已由 axios 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  min-height: 100vh;
  background: #fff;
}

.login__brand {
  position: relative;
  display: flex;
  align-items: center;
  padding: 64px 72px;
  color: #fff;
  background:
    radial-gradient(circle at 18% 22%, rgba(122, 90, 248, 0.55), transparent 45%),
    radial-gradient(circle at 82% 78%, rgba(47, 107, 255, 0.5), transparent 48%),
    linear-gradient(140deg, #182046 0%, #1f2740 52%, #131a30 100%);
  overflow: hidden;
}

.login__brand::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(circle at 30% 30%, #000 20%, transparent 78%);
  pointer-events: none;
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 520px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: linear-gradient(135deg, #6f9cff, #2f6bff);
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 22px;
}

.brand-title {
  margin: 0 0 14px;
  font-size: 34px;
  font-weight: 600;
  letter-spacing: 1px;
}

.brand-desc {
  margin: 0 0 26px;
  font-size: 14px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.72);
}

.brand-list {
  margin: 0 0 30px;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
  font-size: 13.5px;
  color: rgba(255, 255, 255, 0.82);
}

.brand-list li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6f9cff;
  flex-shrink: 0;
}

.brand-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.brand-stack span {
  padding: 5px 11px;
  border-radius: 999px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.78);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.login__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: var(--suite-bg);
}

.login-card {
  width: 100%;
  max-width: 380px;
  padding: 36px 34px 28px;
  background: #fff;
  border: 1px solid var(--suite-border);
  border-radius: 14px;
  box-shadow: 0 18px 44px rgba(31, 39, 64, 0.09);
}

.login-card__title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
}

.login-card__sub {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--suite-text-secondary);
}

.login-card__submit {
  width: 100%;
  height: 42px;
  font-size: 15px;
  letter-spacing: 2px;
}

.login-card__accounts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 14px;
  border-top: 1px dashed var(--suite-border);
}

.login-card__accounts-title {
  font-size: 12px;
  color: var(--suite-text-secondary);
  margin-right: 2px;
}

.login-card__tip {
  margin: 18px 0 0;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--suite-text-secondary);
}

code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(47, 107, 255, 0.07);
  color: #2f6bff;
}
</style>
