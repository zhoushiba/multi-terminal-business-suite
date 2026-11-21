<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">活动管理</h2>
        <p class="page-header__desc">
          活动下挂场次，场次维护容量、剩余名额与价格；报名走 Redis/H2 乐观锁扣减，防超卖
        </p>
      </div>
      <div class="page-header__extra">
        <el-button type="primary" :disabled="!auth.can('activity:write')" @click="openCreate">
          新建活动
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
        <el-input v-model="query.keyword" placeholder="活动标题" clearable @keyup.enter="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable>
          <el-option v-for="(meta, value) in ACTIVITY_STATUS" :key="value" :label="meta.text" :value="Number(value)" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id" stripe empty-text="暂无活动">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="session-box">
              <div class="session-box__header">
                <span class="session-box__title">场次列表（{{ row.sessions?.length ?? 0 }}）</span>
                <el-button
                  v-if="auth.can('activity:write')"
                  size="small"
                  type="primary"
                  plain
                  @click="openSessionCreate(row)"
                >
                  新增场次
                </el-button>
              </div>
              <el-table :data="row.sessions" size="small" border empty-text="该活动还没有场次">
                <el-table-column prop="name" label="场次名称" min-width="160" />
                <el-table-column label="开始时间" width="160">
                  <template #default="{ row: session }">{{ formatDateTime(session.startTime) }}</template>
                </el-table-column>
                <el-table-column label="结束时间" width="160">
                  <template #default="{ row: session }">{{ formatDateTime(session.endTime) }}</template>
                </el-table-column>
                <el-table-column label="名额" width="120" align="center">
                  <template #default="{ row: session }">
                    <span class="text-mono">{{ session.capacity - session.remain }}/{{ session.capacity }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="价格" width="100" align="right">
                  <template #default="{ row: session }">{{ formatMoney(session.price) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row: session }">
                    <el-tag size="small" :type="session.soldOut ? 'danger' : 'success'">
                      {{ session.soldOut ? '已满' : '可报名' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column v-if="auth.can('activity:write')" label="操作" width="140">
                  <template #default="{ row: session }">
                    <el-button link type="primary" size="small" @click="openSessionEdit(row, session)">
                      编辑
                    </el-button>
                    <el-button link type="danger" size="small" @click="onSessionDelete(row, session)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="活动标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMeta(ACTIVITY_STATUS, row.status).type">
              {{ statusMeta(ACTIVITY_STATUS, row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" width="200">
          <template #default="{ row }">
            <div class="text-muted time-cell">
              {{ formatDateTime(row.signupStartTime) }} ~ {{ formatDateTime(row.signupEndTime) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" width="200">
          <template #default="{ row }">
            <div class="text-muted time-cell">
              {{ formatDateTime(row.startTime) }} ~ {{ formatDateTime(row.endTime) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="场次" width="80" align="center">
          <template #default="{ row }">{{ row.sessions?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.can('activity:write')" link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="auth.can('activity:write')" link type="danger" size="small" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span class="text-muted">共 {{ total }} 个活动</span>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑活动' : '新建活动'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="60" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属区域" prop="regionId">
              <el-select v-model="form.regionId" placeholder="请选择" class="w-full">
                <el-option v-for="item in regionOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" class="w-full">
                <el-option v-for="(meta, value) in ACTIVITY_STATUS" :key="value" :label="meta.text" :value="Number(value)" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="报名开始">
          <el-date-picker
            v-model="form.signupStartTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择时间"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="报名结束">
          <el-date-picker
            v-model="form.signupEndTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择时间"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="活动开始">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择时间"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="活动结束">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择时间"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.coverUrl" placeholder="/covers/xxx.jpg" />
        </el-form-item>
        <el-form-item label="活动说明">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sessionVisible" :title="sessionEditingId ? '编辑场次' : '新增场次'" width="520px">
      <el-form ref="sessionFormRef" :model="sessionForm" :rules="sessionRules" label-width="90px">
        <el-form-item label="场次名称" prop="name">
          <el-input v-model="sessionForm.name" placeholder="例如 上午场 09:00" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="sessionForm.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="sessionForm.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="sessionForm.capacity" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="sessionForm.price" :min="0" :precision="2" :step="10" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sessionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSessionSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createActivity,
  createSession,
  deleteActivity,
  deleteSession,
  listActivities,
  updateActivity,
  updateSession
} from '@/api/activity'
import { listRegions } from '@/api/geo'
import type { ActivityItem, ActivitySessionItem, RegionVO } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import {
  ACTIVITY_STATUS,
  formatDateTime,
  formatMoney,
  statusMeta
} from '@/utils/format'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const rows = ref<ActivityItem[]>([])
const total = ref(0)
const regionOptions = ref<{ id: number; label: string }[]>([])

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
  title: '',
  regionId: null as number | null,
  status: 1,
  signupStartTime: null as string | null,
  signupEndTime: null as string | null,
  startTime: null as string | null,
  endTime: null as string | null,
  coverUrl: '',
  description: ''
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }],
  regionId: [{ required: true, message: '请选择区域', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const sessionVisible = ref(false)
const sessionEditingId = ref<number | null>(null)
const sessionActivityId = ref<number | null>(null)
const sessionFormRef = ref<FormInstance>()

const sessionForm = reactive({
  name: '',
  startTime: '',
  endTime: '',
  capacity: 30,
  price: 0
})

const sessionRules: FormRules = {
  name: [{ required: true, message: '请输入场次名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }]
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
    const page = await listActivities({
      keyword: query.keyword || undefined,
      status: query.status,
      page: query.page,
      size: query.size
    })
    rows.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '活动加载失败'
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
  Object.assign(form, {
    title: '',
    regionId: null,
    status: 1,
    signupStartTime: null,
    signupEndTime: null,
    startTime: null,
    endTime: null,
    coverUrl: '',
    description: ''
  })
  dialogVisible.value = true
}

function openEdit(row: ActivityItem): void {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    regionId: row.regionId,
    status: row.status,
    signupStartTime: row.signupStartTime,
    signupEndTime: row.signupEndTime,
    startTime: row.startTime,
    endTime: row.endTime,
    coverUrl: row.coverUrl ?? '',
    description: row.description ?? ''
  })
  dialogVisible.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  const payload = {
    title: form.title,
    regionId: form.regionId,
    status: form.status,
    signupStartTime: form.signupStartTime,
    signupEndTime: form.signupEndTime,
    startTime: form.startTime,
    endTime: form.endTime,
    coverUrl: form.coverUrl || undefined,
    description: form.description || undefined
  }
  try {
    if (editingId.value) {
      await updateActivity(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createActivity(payload)
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

function openSessionCreate(activity: ActivityItem): void {
  sessionEditingId.value = null
  sessionActivityId.value = activity.id
  Object.assign(sessionForm, {
    name: '',
    startTime: activity.startTime ?? '',
    endTime: activity.endTime ?? '',
    capacity: 30,
    price: 0
  })
  sessionVisible.value = true
}

function openSessionEdit(activity: ActivityItem, session: ActivitySessionItem): void {
  sessionEditingId.value = session.id
  sessionActivityId.value = activity.id
  Object.assign(sessionForm, {
    name: session.name,
    startTime: session.startTime,
    endTime: session.endTime,
    capacity: session.capacity,
    price: session.price
  })
  sessionVisible.value = true
}

async function onSessionSave(): Promise<void> {
  const valid = await sessionFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (sessionEditingId.value) {
      await updateSession(sessionEditingId.value, { ...sessionForm })
      ElMessage.success('场次已更新')
    } else if (sessionActivityId.value) {
      await createSession(sessionActivityId.value, { ...sessionForm })
      ElMessage.success('场次已创建')
    }
    sessionVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onSessionDelete(activity: ActivityItem, session: ActivitySessionItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除场次「${session.name}」吗？`, '删除场次', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteSession(session.id)
  ElMessage.success('已删除')
  await load()
  void activity
}

async function onDelete(row: ActivityItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除活动「${row.title}」吗？其下 ${row.sessions?.length ?? 0} 个场次也会一并移除。`,
      '删除活动',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await deleteActivity(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  void load()
  try {
    regionOptions.value = flattenRegions(await listRegions())
  } catch {
    // 区域下拉失败不影响主列表
  }
})
</script>

<style scoped>
.alert {
  margin-bottom: 12px;
}

.session-box {
  padding: 8px 16px 16px 48px;
  background: #fafbfe;
}

.session-box__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.session-box__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--suite-text-secondary);
}

.time-cell {
  font-size: 12px;
  line-height: 1.5;
}

.w-full {
  width: 100%;
}
</style>
