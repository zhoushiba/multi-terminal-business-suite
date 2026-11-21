<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">用户管理</h2>
        <p class="page-header__desc">
          后台账号与小程序用户统一在 sys_user，source 字段区分 admin / wechat 来源
        </p>
      </div>
      <div class="page-header__extra">
        <el-button type="primary" :disabled="!auth.can('user:write')" @click="openCreate">新建用户</el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" show-icon :closable="false" class="alert">
      <template #title>
        <span>{{ error }}</span>
        <el-button link type="primary" size="small" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <div class="panel">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="用户名 / 昵称 / 手机号" clearable @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无用户">
        <el-table-column prop="username" label="用户名" width="150">
          <template #default="{ row }">
            <span class="text-mono">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="phone" label="手机号" width="140">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" size="small" class="mr-6">{{ role }}</el-tag>
            <span v-if="!row.roles?.length" class="text-muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.source === 'wechat' ? 'success' : 'info'">
              {{ row.source === 'wechat' ? '小程序' : '后台' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="150">
          <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.can('user:write')" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="auth.can('user:write')" link type="warning" size="small" @click="onResetPassword(row)">
              重置密码
            </el-button>
            <el-button v-if="auth.can('user:write')" link type="danger" size="small" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span class="text-muted">共 {{ total }} 个账号</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新建用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="84px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(editingId)" placeholder="登录账号，创建后不可修改" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="30" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" class="w-full">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.name}（${role.code}）`" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createUser,
  deleteUser,
  listRoles,
  listUsers,
  resetPassword,
  updateUser
} from '@/api/rbac'
import type { SysRoleItem, SysUserItem } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const rows = ref<SysUserItem[]>([])
const total = ref(0)
const roles = ref<SysRoleItem[]>([])

const query = reactive({
  keyword: '',
  status: undefined as number | undefined,
  page: 1,
  size: 10
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  status: 1,
  roleIds: [] as number[]
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,30}$/, message: '3-30 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  roleIds: [{ required: true, message: '请至少选择一个角色', trigger: 'change' }]
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await listUsers({
      keyword: query.keyword || undefined,
      status: query.status,
      page: query.page,
      size: query.size
    })
    rows.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '用户加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.page = 1
  void load()
}

function reset(): void {
  query.keyword = ''
  query.status = undefined
  search()
}

function onSizeChange(): void {
  query.page = 1
  void load()
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, { username: '', password: '', nickname: '', phone: '', status: 1, roleIds: [] })
  dialogVisible.value = true
}

function openEdit(row: SysUserItem): void {
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    password: '',
    nickname: row.nickname ?? '',
    phone: row.phone ?? '',
    status: row.status,
    roleIds: [...(row.roleIds ?? [])]
  })
  dialogVisible.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        username: form.username,
        nickname: form.nickname || undefined,
        phone: form.phone || undefined,
        status: form.status,
        roleIds: form.roleIds
      })
      ElMessage.success('已更新')
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined,
        phone: form.phone || undefined,
        status: form.status,
        roleIds: form.roleIds
      })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onResetPassword(row: SysUserItem): Promise<void> {
  let password = ''
  try {
    const result = await ElMessageBox.prompt(`为「${row.username}」设置新密码`, '重置密码', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      inputType: 'password',
      inputValidator: (value) => (value && value.length >= 6 ? true : '密码至少 6 位')
    })
    password = result.value
  } catch {
    return
  }
  await resetPassword(row.id, password)
  ElMessage.success('密码已重置')
}

async function onDelete(row: SysUserItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '删除用户', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  void load()
  try {
    roles.value = await listRoles()
  } catch {
    // 角色下拉失败不影响列表
  }
})
</script>

<style scoped>
.alert {
  margin-bottom: 12px;
}

.w-full {
  width: 100%;
}

.mr-6 {
  margin-right: 6px;
}
</style>
