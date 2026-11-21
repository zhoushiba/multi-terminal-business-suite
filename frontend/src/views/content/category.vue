<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">分类管理</h2>
        <p class="page-header__desc">
          单表树形结构（parent_id 自关联），分类删除前会校验是否仍被内容引用
        </p>
      </div>
      <div class="page-header__extra">
        <el-button type="primary" :disabled="!auth.can('content:write')" @click="openCreate(null)">
          新建分类
        </el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" show-icon :closable="false" class="alert">
      <template #title>
        <span>{{ error }}</span>
        <el-button link type="primary" size="small" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <div class="panel">
      <el-table
        v-loading="loading"
        :data="categories"
        row-key="id"
        default-expand-all
        stripe
        empty-text="暂无分类"
      >
        <el-table-column prop="name" label="分类名称" min-width="220" />
        <el-table-column prop="code" label="编码" width="180" />
        <el-table-column prop="sort" label="排序" width="90" align="center" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.can('content:write')" link type="primary" size="small" @click="openCreate(row)">
              新增子分类
            </el-button>
            <el-button v-if="auth.can('content:write')" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="auth.can('content:write')" link type="danger" size="small" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新建分类'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" class="w-full">
            <el-option label="顶级分类" :value="0" />
            <el-option v-for="item in flatOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="英文小写 + 短横线，例如 travel-news" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory
} from '@/api/content'
import type { CategoryVO } from '@/types/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const categories = ref<CategoryVO[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  parentId: 0,
  name: '',
  code: '',
  sort: 10,
  status: 1
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^[a-z0-9-]+$/, message: '只允许小写字母、数字和短横线', trigger: 'blur' }
  ]
}

interface FlatOption {
  id: number
  label: string
}

function flatten(nodes: CategoryVO[], depth = 0): FlatOption[] {
  const result: FlatOption[] = []
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'　'.repeat(depth)}${depth ? '└ ' : ''}${node.name}` })
    if (node.children?.length) {
      result.push(...flatten(node.children, depth + 1))
    }
  }
  return result
}

const flatOptions = computed(() => flatten(categories.value))

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    categories.value = await listCategories()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '分类加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(parent: CategoryVO | null): void {
  editingId.value = null
  Object.assign(form, {
    parentId: parent?.id ?? 0,
    name: '',
    code: '',
    sort: 10,
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: CategoryVO): void {
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId ?? 0,
    name: row.name,
    code: row.code,
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      parentId: form.parentId,
      name: form.name,
      code: form.code,
      sort: form.sort,
      status: form.status
    }
    if (editingId.value) {
      await updateCategory(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createCategory(payload)
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

async function onDelete(row: CategoryVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除分类', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.alert {
  margin-bottom: 12px;
}

.w-full {
  width: 100%;
}
</style>
