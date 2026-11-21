<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">报名核销</h2>
        <p class="page-header__desc">
          输入 8 位核销码完成现场签到：<code>POST /api/signups/verify</code> ·
          并发报名由场次 remain 字段乐观锁保证不超卖
        </p>
      </div>
    </div>

    <div class="verify-row">
      <section class="panel verify-panel">
        <div class="panel__header">扫码 / 输入核销码</div>
        <div class="verify-panel__body">
          <el-input
            v-model="verifyCode"
            size="large"
            placeholder="请输入核销码，例如 SUITE2026"
            clearable
            @keyup.enter="onVerify"
          >
            <template #prefix><el-icon><Tickets /></el-icon></template>
          </el-input>
          <el-button
            type="primary"
            size="large"
            :disabled="!auth.can('signup:verify')"
            :loading="verifying"
            @click="onVerify"
          >
            立即核销
          </el-button>
        </div>
        <p class="text-muted verify-panel__tip">
          核销成功后报名状态变为「已核销」，并写入 signup_verify_log 审计记录。
        </p>
      </section>

      <section class="panel verify-result">
        <div class="panel__header">最近一次核销结果</div>
        <div v-if="lastResult" class="verify-result__body">
          <el-result icon="success" :title="lastResult.activityTitle" :sub-title="`${lastResult.contactName} · ${lastResult.contactPhone}`">
            <template #extra>
              <el-tag type="success">{{ lastResult.statusText }}</el-tag>
              <span class="text-muted ml-8">{{ formatDateTime(lastResult.verifyTime) }}</span>
            </template>
          </el-result>
        </div>
        <el-empty v-else description="还没有核销记录" :image-size="72" />
      </section>
    </div>

    <div class="panel">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="联系人 / 手机号 / 核销码" clearable @keyup.enter="search" />
        <el-select v-model="query.activityId" placeholder="全部活动" clearable>
          <el-option v-for="item in activityOptions" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable>
          <el-option v-for="(meta, value) in SIGNUP_STATUS" :key="value" :label="meta.text" :value="Number(value)" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无报名记录">
        <el-table-column label="核销码" width="160">
          <template #default="{ row }">
            <span class="text-mono code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="activityTitle" label="活动" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sessionName" label="场次" width="140" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="110" />
        <el-table-column prop="contactPhone" label="手机号" width="140">
          <template #default="{ row }"><span class="text-mono">{{ row.contactPhone }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMeta(SIGNUP_STATUS, row.status).type">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.signupTime) }}</template>
        </el-table-column>
        <el-table-column label="核销时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.verifyTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="auth.can('signup:verify') && row.status === 1"
              link
              type="primary"
              size="small"
              @click="onVerifyCode(row.code)"
            >
              核销
            </el-button>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span class="text-muted">共 {{ total }} 条报名</span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listActivities, listSignups, verifySignup } from '@/api/activity'
import type { SignupItem, VerifyResult } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import { SIGNUP_STATUS, formatDateTime, statusMeta } from '@/utils/format'

const auth = useAuthStore()

const loading = ref(false)
const verifying = ref(false)
const error = ref<string | null>(null)
const rows = ref<SignupItem[]>([])
const total = ref(0)
const verifyCode = ref('')
const lastResult = ref<VerifyResult | null>(null)
const activityOptions = ref<{ id: number; title: string }[]>([])

const query = reactive({
  keyword: '',
  activityId: undefined as number | undefined,
  status: undefined as number | undefined,
  page: 1,
  size: 10
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await listSignups({
      keyword: query.keyword || undefined,
      activityId: query.activityId,
      status: query.status,
      page: query.page,
      size: query.size
    })
    rows.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : '报名列表加载失败'
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
  query.activityId = undefined
  query.status = undefined
  search()
}

function onSizeChange(): void {
  query.page = 1
  void load()
}

async function onVerify(): Promise<void> {
  const code = verifyCode.value.trim()
  if (!code) {
    ElMessage.warning('请输入核销码')
    return
  }
  await doVerify(code)
}

async function onVerifyCode(code: string): Promise<void> {
  await doVerify(code)
}

async function doVerify(code: string): Promise<void> {
  verifying.value = true
  try {
    const result = await verifySignup(code)
    lastResult.value = result
    verifyCode.value = ''
    ElMessage.success(`核销成功：${result.contactName}`)
    await load()
  } catch (err) {
    const message = err instanceof Error ? err.message : '核销失败'
    await ElMessageBox.alert(message, '核销失败', { type: 'error', confirmButtonText: '知道了' })
  } finally {
    verifying.value = false
  }
}

onMounted(async () => {
  void load()
  try {
    const page = await listActivities({ page: 1, size: 100 })
    activityOptions.value = page.list.map((item) => ({ id: item.id, title: item.title }))
  } catch {
    // 活动下拉失败不影响列表
  }
})
</script>

<style scoped>
.verify-row {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.verify-panel__body {
  display: flex;
  gap: 10px;
  padding: 4px 0 2px;
}

.verify-panel__body .el-input {
  flex: 1;
}

.verify-panel__tip {
  margin: 10px 0 0;
  font-size: 12px;
}

.verify-result__body {
  padding: 2px 0 6px;
}

.code {
  font-weight: 600;
  letter-spacing: 0.5px;
}

.ml-8 {
  margin-left: 8px;
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
