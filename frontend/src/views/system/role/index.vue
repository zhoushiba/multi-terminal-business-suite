<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">角色权限</h2>
        <p class="page-header__desc">
          角色 × 菜单（含按钮级权限点）多对多：sys_role → sys_role_menu → sys_menu.perm
        </p>
      </div>
      <div class="page-header__extra">
        <el-button type="primary" :disabled="!auth.can('role:write')" @click="openCreate">新建角色</el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" show-icon :closable="false" class="alert">
      <template #title>
        <span>{{ error }}</span>
        <el-button link type="primary" size="small" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <div class="panel">
      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无角色">
        <el-table-column prop="code" label="角色编码" width="150">
          <template #default="{ row }">
            <span class="text-mono">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="角色名称" width="160" />
        <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="授权菜单" width="120" align="center">
          <template #default="{ row }">{{ row.menuIds?.length ?? 0 }} 项</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.can('role:write')" link type="primary" size="small" @click="openEdit(row)">
              编辑 / 授权
            </el-button>
            <el-button v-if="auth.can('role:write')" link type="danger" size="small" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色与授权' : '新建角色'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="角色编码" prop="code">
              <el-input v-model="form.code" :disabled="Boolean(editingId)" placeholder="例如 operator" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名称" prop="name">
              <el-input v-model="form.name" placeholder="例如 活动运营" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明">
          <el-input v-model="form.remark" maxlength="120" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="菜单授权">
          <div class="menu-tree">
            <el-tree
              ref="treeRef"
              :data="menus"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="{ label: 'name', children: 'children' }"
            >
              <template #default="{ data }">
                <span class="tree-node">
                  <span>{{ data.name }}</span>
                  <el-tag v-if="data.perm" size="small" effect="plain" type="info" class="tree-node__perm">
                    {{ data.perm }}
                  </el-tag>
                  <el-tag v-if="data.type === 2" size="small" effect="plain" type="warning" class="tree-node__perm">
                    按钮
                  </el-tag>
                </span>
              </template>
            </el-tree>
          </div>
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
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTree, type FormInstance, type FormRules } from 'element-plus'
import {
  createRole,
  deleteRole,
  listMenus,
  listRoles,
  updateRole
} from '@/api/rbac'
import type { MenuVO, SysRoleItem } from '@/types/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const rows = ref<SysRoleItem[]>([])
const menus = ref<MenuVO[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  code: '',
  name: '',
  remark: '',
  sort: 10
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-z0-9_-]+$/, message: '只允许小写字母、数字、下划线和短横线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    rows.value = await listRoles()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '角色加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, { code: '', name: '', remark: '', sort: 10 })
  dialogVisible.value = true
  void nextTick(() => treeRef.value?.setCheckedKeys([], false))
}

function openEdit(row: SysRoleItem): void {
  editingId.value = row.id
  Object.assign(form, {
    code: row.code,
    name: row.name,
    remark: row.remark ?? '',
    sort: row.sort ?? 0
  })
  dialogVisible.value = true
  void nextTick(() => treeRef.value?.setCheckedKeys(row.menuIds ?? [], false))
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const tree = treeRef.value
  const menuIds = tree
    ? ([...tree.getCheckedKeys(false), ...tree.getHalfCheckedKeys()] as number[])
    : []
  if (!menuIds.length) {
    ElMessage.warning('请至少勾选一个菜单')
    return
  }
  saving.value = true
  try {
    const payload = {
      code: form.code,
      name: form.name,
      remark: form.remark || undefined,
      sort: form.sort,
      menuIds
    }
    if (editingId.value) {
      await updateRole(editingId.value, payload)
      ElMessage.success('已更新授权')
    } else {
      await createRole(payload)
      ElMessage.success('已创建角色')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onDelete(row: SysRoleItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '删除角色', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteRole(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  void load()
  try {
    menus.value = await listMenus()
  } catch {
    // 菜单树失败不影响列表
  }
})
</script>

<style scoped>
.alert {
  margin-bottom: 12px;
}

.menu-tree {
  width: 100%;
  max-height: 320px;
  overflow: auto;
  padding: 8px 10px;
  border: 1px solid var(--suite-border);
  border-radius: 8px;
  background: #fafbfe;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tree-node__perm {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
}
</style>
