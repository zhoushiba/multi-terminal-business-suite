<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">内容管理</h2>
        <p class="page-header__desc">
          审核状态机：草稿 → 待审核 → 已发布 → 已下架 · 接口 <code>GET /api/contents</code>
        </p>
      </div>
      <div class="page-header__extra">
        <el-button type="primary" :disabled="!auth.can('content:write')" @click="openCreate">
          新建内容
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
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="标题 / 摘要关键字" clearable @keyup.enter="search" />
        <el-select v-model="query.categoryId" placeholder="全部分类" clearable>
          <el-option v-for="item in categoryOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable>
          <el-option v-for="(meta, value) in CONTENT_STATUS" :key="value" :label="meta.text" :value="Number(value)" />
        </el-select>
        <el-select v-model="query.contentType" placeholder="全部类型" clearable>
          <el-option v-for="(meta, value) in CONTENT_TYPE" :key="value" :label="meta.text" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        empty-text="暂无内容，换个筛选条件试试"
        class="content-table"
      >
        <el-table-column label="标题" min-width="300">
          <template #default="{ row }">
            <div class="title-cell">
              <el-tag v-if="row.topFlag === 1" type="danger" size="small" effect="plain">置顶</el-tag>
              <span class="title-cell__text">{{ row.title }}</span>
            </div>
            <div class="text-muted title-cell__summary">{{ row.summary }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="130" />
        <el-table-column label="类型" width="92">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ statusMeta(CONTENT_TYPE, row.contentType).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="98">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMeta(CONTENT_STATUS, row.status).type">
              {{ statusMeta(CONTENT_STATUS, row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="作者" width="110" />
        <el-table-column label="阅读量" width="92" align="right">
          <template #default="{ row }">{{ formatNumber(row.viewCount) }}</template>
        </el-table-column>
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="248" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.can('content:write')" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="auth.can('content:write') && row.status === 0"
              link
              type="primary"
              size="small"
              @click="onSubmitFlow(row)"
            >
              提审
            </el-button>
            <el-button
              v-if="auth.can('content:review') && row.status === 1"
              link
              type="success"
              size="small"
              @click="onPublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="auth.can('content:review') && row.status === 1"
              link
              type="warning"
              size="small"
              @click="onReject(row)"
            >
              驳回
            </el-button>
            <el-button
              v-if="auth.can('content:review') && row.status === 2"
              link
              type="info"
              size="small"
              @click="onOffline(row)"
            >
              下架
            </el-button>
            <el-button
              v-if="auth.can('content:write')"
              link
              type="danger"
              size="small"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span class="text-muted">共 {{ total }} 条 · 第 {{ query.page }} / {{ Math.max(1, Math.ceil(total / query.size)) }} 页</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑内容' : '新建内容'"
      width="700px"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="84px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="80" show-word-limit placeholder="请输入内容标题" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" class="w-full">
                <el-option
                  v-for="item in categoryOptions"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="contentType">
              <el-select v-model="form.contentType" class="w-full">
                <el-option
                  v-for="(meta, value) in CONTENT_TYPE"
                  :key="value"
                  :label="meta.text"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.body" type="textarea" :rows="6" placeholder="支持 HTML 片段，小程序端按富文本解析" />
        </el-form-item>
        <el-form-item label="封面地址">
          <el-input v-model="form.coverUrl" placeholder="/covers/xxx.jpg 或 https://..." />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标签">
              <el-select
                v-model="form.tags"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入标签后回车"
                class="w-full"
              >
                <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="置顶">
              <el-switch v-model="form.topFlag" />
            </el-form-item>
          </el-col>
        </el-row>
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
  createContent,
  deleteContent,
  getContent,
  listCategories,
  listContents,
  listTags,
  offlineContent,
  publishContent,
  rejectContent,
  submitContent,
  updateContent
} from '@/api/content'
import type { CategoryVO, ContentItem, TagVO } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import {
  CONTENT_STATUS,
  CONTENT_TYPE,
  formatDateTime,
  formatNumber,
  statusMeta
} from '@/utils/format'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const rows = ref<ContentItem[]>([])
const total = ref(0)
const categoryOptions = ref<{ id: number; label: string }[]>([])
const tagOptions = ref<TagVO[]>([])

const query = reactive({
  keyword: '',
  categoryId: undefined as number | undefined,
  status: undefined as number | undefined,
  contentType: '' as string | undefined,
  page: 1,
  size: 10
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  title: '',
  summary: '',
  body: '',
  coverUrl: '',
  categoryId: null as number | null,
  contentType: 'article',
  topFlag: false,
  sort: 0,
  tags: [] as string[]
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  contentType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function flattenCategories(nodes: CategoryVO[], depth = 0): { id: number; label: string }[] {
  const result: { id: number; label: string }[] = []
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'　'.repeat(depth)}${depth ? '└ ' : ''}${node.name}` })
    if (node.children?.length) {
      result.push(...flattenCategories(node.children, depth + 1))
    }
  }
  return result
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await listContents({
      keyword: query.keyword || undefined,
      categoryId: query.categoryId,
      status: query.status,
      contentType: query.contentType || undefined,
      page: query.page,
      size: query.size
    })
    rows.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '内容列表加载失败'
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
  query.categoryId = undefined
  query.status = undefined
  query.contentType = ''
  search()
}

function onSizeChange(): void {
  query.page = 1
  void load()
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    title: '',
    summary: '',
    body: '',
    coverUrl: '',
    categoryId: null,
    contentType: 'article',
    topFlag: false,
    sort: 0,
    tags: []
  })
  dialogVisible.value = true
}

async function openEdit(row: ContentItem): Promise<void> {
  editingId.value = row.id
  try {
    const detail = await getContent(row.id)
    Object.assign(form, {
      title: detail.title,
      summary: detail.summary ?? '',
      body: detail.body ?? '',
      coverUrl: detail.coverUrl ?? '',
      categoryId: detail.categoryId,
      contentType: detail.contentType,
      topFlag: detail.topFlag === 1,
      sort: detail.sort ?? 0,
      tags: [...(detail.tags ?? [])]
    })
    dialogVisible.value = true
  } catch {
    // 拦截器已提示错误
  }
}

function onDialogClosed(): void {
  formRef.value?.clearValidate()
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  const payload = {
    title: form.title,
    summary: form.summary || undefined,
    body: form.body || undefined,
    coverUrl: form.coverUrl || undefined,
    categoryId: form.categoryId,
    contentType: form.contentType,
    topFlag: form.topFlag,
    sort: form.sort,
    tags: form.tags
  }
  try {
    if (editingId.value) {
      await updateContent(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createContent(payload)
      ElMessage.success('已创建，状态为草稿')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onSubmitFlow(row: ContentItem): Promise<void> {
  await submitContent(row.id)
  ElMessage.success('已提交审核')
  await load()
}

async function onPublish(row: ContentItem): Promise<void> {
  await publishContent(row.id)
  ElMessage.success('已发布')
  await load()
}

async function onOffline(row: ContentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定下架《${row.title}》吗？下架后小程序端不可见。`, '下架内容', {
      type: 'warning',
      confirmButtonText: '下架',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await offlineContent(row.id)
  ElMessage.success('已下架')
  await load()
}

async function onReject(row: ContentItem): Promise<void> {
  let remark = ''
  try {
    const result = await ElMessageBox.prompt('请输入驳回原因，作者会在后台看到。', '驳回内容', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value) => (value && value.trim().length > 0 ? true : '驳回原因不能为空')
    })
    remark = result.value
  } catch {
    return
  }
  await rejectContent(row.id, remark)
  ElMessage.success('已驳回')
  await load()
}

async function onDelete(row: ContentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除后不可恢复，确定删除《${row.title}》吗？`, '删除内容', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteContent(row.id)
  ElMessage.success('已删除')
  if (rows.value.length === 1 && query.page > 1) query.page -= 1
  await load()
}

onMounted(async () => {
  void load()
  try {
    const [categories, tags] = await Promise.all([listCategories(), listTags()])
    categoryOptions.value = flattenCategories(categories)
    tagOptions.value = tags
  } catch {
    // 下拉数据失败不影响主列表
  }
})
</script>

<style scoped>
.alert {
  margin-bottom: 12px;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-cell__text {
  font-weight: 500;
}

.title-cell__summary {
  margin-top: 2px;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}

.w-full {
  width: 100%;
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
