<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">资源点位</h2>
        <p class="page-header__desc">
          景区 / 人文古迹 / 遥感地块 / 服务设施四类点位，带经纬度供小程序地图与附近查询使用
        </p>
      </div>
      <div class="page-header__extra">
        <el-button type="primary" :disabled="!auth.can('geo:write')" @click="openCreate">新增点位</el-button>
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
        <el-input v-model="query.keyword" placeholder="点位名称 / 地址" clearable @keyup.enter="search" />
        <el-select v-model="query.regionId" placeholder="全部区域" clearable>
          <el-option v-for="item in regionOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
        <el-select v-model="query.type" placeholder="全部类型" clearable>
          <el-option v-for="(meta, value) in POINT_TYPE" :key="value" :label="meta.text" :value="value" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无点位数据">
        <el-table-column prop="name" label="点位名称" min-width="180" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="statusMeta(POINT_TYPE, row.type).type">
              {{ row.typeText || statusMeta(POINT_TYPE, row.type).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="regionName" label="所属区域" width="120" />
        <el-table-column label="经纬度" width="190">
          <template #default="{ row }">
            <span class="text-mono">{{ row.lng?.toFixed(5) }}, {{ row.lat?.toFixed(5) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.can('geo:write')" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="auth.can('geo:write')" link type="danger" size="small" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span class="text-muted">共 {{ total }} 个点位</span>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑点位' : '新增点位'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="84px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="区域" prop="regionId">
              <el-select v-model="form.regionId" placeholder="请选择" class="w-full">
                <el-option v-for="item in regionOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="form.type" class="w-full">
                <el-option v-for="(meta, value) in POINT_TYPE" :key="value" :label="meta.text" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经度" prop="lng">
              <el-input-number v-model="form.lng" :precision="6" :step="0.0001" :min="-180" :max="180" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="lat">
              <el-input-number v-model="form.lat" :precision="6" :step="0.0001" :min="-90" :max="90" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地址">
          <el-input v-model="form.address" maxlength="120" />
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.coverUrl" placeholder="/covers/xxx.jpg" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="200" show-word-limit />
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
import { createPoint, deletePoint, listPoints, listRegions, updatePoint } from '@/api/geo'
import type { GeoPointItem, RegionVO } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import { POINT_TYPE, statusMeta } from '@/utils/format'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const rows = ref<GeoPointItem[]>([])
const total = ref(0)
const regionOptions = ref<{ id: number; label: string }[]>([])

const query = reactive({
  keyword: '',
  regionId: undefined as number | undefined,
  type: '' as string | undefined,
  page: 1,
  size: 10
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  regionId: null as number | null,
  type: 'scenic',
  lng: 106.6302,
  lat: 26.6477,
  address: '',
  coverUrl: '',
  summary: '',
  status: 1
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入点位名称', trigger: 'blur' }],
  regionId: [{ required: true, message: '请选择区域', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  lng: [{ required: true, message: '请输入经度', trigger: 'blur' }],
  lat: [{ required: true, message: '请输入纬度', trigger: 'blur' }]
}

function flattenRegions(nodes: RegionVO[], depth = 0): { id: number; label: string }[] {
  const result: { id: number; label: string }[] = []
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'　'.repeat(depth)}${node.name}` })
    if (node.children?.length) {
      result.push(...flattenRegions(node.children, depth + 1))
    }
  }
  return result
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await listPoints({
      keyword: query.keyword || undefined,
      regionId: query.regionId,
      type: query.type || undefined,
      page: query.page,
      size: query.size
    })
    rows.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '点位加载失败'
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
  query.regionId = undefined
  query.type = ''
  search()
}

function onSizeChange(): void {
  query.page = 1
  void load()
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    name: '',
    regionId: null,
    type: 'scenic',
    lng: 106.6302,
    lat: 26.6477,
    address: '',
    coverUrl: '',
    summary: '',
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: GeoPointItem): void {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    regionId: row.regionId,
    type: row.type,
    lng: row.lng,
    lat: row.lat,
    address: row.address ?? '',
    coverUrl: row.coverUrl ?? '',
    summary: row.summary ?? '',
    status: row.status
  })
  dialogVisible.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  const payload = {
    name: form.name,
    regionId: form.regionId,
    type: form.type,
    lng: form.lng,
    lat: form.lat,
    address: form.address || undefined,
    coverUrl: form.coverUrl || undefined,
    summary: form.summary || undefined,
    status: form.status
  }
  try {
    if (editingId.value) {
      await updatePoint(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createPoint(payload)
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

async function onDelete(row: GeoPointItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除点位「${row.name}」吗？`, '删除点位', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deletePoint(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  void load()
  try {
    regionOptions.value = flattenRegions(await listRegions())
  } catch {
    // 区域下拉失败不影响列表
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
</style>
