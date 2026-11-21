<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">系统配置与操作日志</h2>
        <p class="page-header__desc">
          运行时自述来自 <code>GET /api/system/info</code>（已脱敏）；操作日志来自 operation_log 表
        </p>
      </div>
      <div class="page-header__extra">
        <el-button :loading="infoLoading" @click="loadInfo">刷新运行时信息</el-button>
      </div>
    </div>

    <section class="panel">
      <div class="panel__header">运行时信息</div>
      <div class="panel__body">
        <el-alert v-if="infoError" type="error" show-icon :closable="false" title="运行时信息加载失败" />
        <el-descriptions v-else-if="info" :column="3" border>
          <el-descriptions-item label="应用">{{ info.app }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ info.version }}</el-descriptions-item>
          <el-descriptions-item label="环境">{{ info.profiles }}</el-descriptions-item>
          <el-descriptions-item label="Java">{{ info.javaVersion }}</el-descriptions-item>
          <el-descriptions-item label="操作系统">{{ info.os }}</el-descriptions-item>
          <el-descriptions-item label="运行时长">{{ formatUptime(info.uptimeSeconds) }}</el-descriptions-item>
          <el-descriptions-item label="服务器时间">{{ info.serverTime }}</el-descriptions-item>
          <el-descriptions-item label="缓存后端">{{ info.cacheBackend }}</el-descriptions-item>
          <el-descriptions-item label="JWT 有效期">{{ Math.round(info.jwtAccessTokenSeconds / 60) }} 分钟</el-descriptions-item>
          <el-descriptions-item label="存储后端">
            {{ info.storageBackend }} · 上限 {{ info.storageMaxMb }} MB
          </el-descriptions-item>
          <el-descriptions-item label="AI Provider">
            {{ info.aiProvider }} / {{ info.aiModel }}
            <el-tag v-if="info.aiMock" size="small" type="warning" class="ml-6">Mock</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="微信登录">
            <el-tag size="small" :type="info.wechatMock ? 'warning' : 'success'">
              {{ info.wechatMock ? 'Mock 模式' : '真实 AppID' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="CORS 白名单" :span="3">
            <span class="text-mono">{{ info.corsAllowedOrigins }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-skeleton v-else :rows="4" animated />
      </div>
    </section>

    <section class="panel">
      <div class="panel__header">
        操作日志
        <span class="text-muted">最近写入的审计记录</span>
      </div>
      <div class="filter-bar">
        <el-input v-model="logQuery.keyword" placeholder="操作人 / 动作 / 路径 / traceId" clearable @keyup.enter="searchLogs" />
        <el-button type="primary" @click="searchLogs">查询</el-button>
        <el-button @click="resetLogs">重置</el-button>
      </div>
      <el-table v-loading="logLoading" :data="logs" stripe empty-text="暂无操作日志">
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="action" label="动作" width="140" />
        <el-table-column label="请求" min-width="240">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" class="method-tag">{{ row.method }}</el-tag>
            <span class="text-mono path">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.costMs != null ? `${row.costMs} ms` : '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column label="traceId" width="150">
          <template #default="{ row }">
            <span class="text-mono trace">{{ row.traceId }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <span class="text-muted">共 {{ logTotal }} 条</span>
        <el-pagination
          v-model:current-page="logQuery.page"
          v-model:page-size="logQuery.size"
          :total="logTotal"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          background
          @current-change="loadLogs"
          @size-change="onLogSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getSystemInfo, listOperationLogs } from '@/api/system'
import type { OperationLogItem, SystemInfo } from '@/types/api'
import { formatDateTime, formatUptime } from '@/utils/format'

const info = ref<SystemInfo | null>(null)
const infoLoading = ref(false)
const infoError = ref(false)

const logs = ref<OperationLogItem[]>([])
const logTotal = ref(0)
const logLoading = ref(false)

const logQuery = reactive({
  keyword: '',
  page: 1,
  size: 10
})

async function loadInfo(): Promise<void> {
  infoLoading.value = true
  infoError.value = false
  try {
    info.value = await getSystemInfo()
  } catch {
    infoError.value = true
  } finally {
    infoLoading.value = false
  }
}

async function loadLogs(): Promise<void> {
  logLoading.value = true
  try {
    const page = await listOperationLogs({
      keyword: logQuery.keyword || undefined,
      page: logQuery.page,
      size: logQuery.size
    })
    logs.value = page.list
    logTotal.value = page.total
  } catch {
    logs.value = []
    logTotal.value = 0
  } finally {
    logLoading.value = false
  }
}

function searchLogs(): void {
  logQuery.page = 1
  void loadLogs()
}

function resetLogs(): void {
  logQuery.keyword = ''
  searchLogs()
}

function onLogSizeChange(): void {
  logQuery.page = 1
  void loadLogs()
}

onMounted(() => {
  void loadInfo()
  void loadLogs()
})
</script>

<style scoped>
.panel + .panel {
  margin-top: 14px;
}

.method-tag {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 10px;
  margin-right: 6px;
}

.path {
  font-size: 12px;
}

.trace {
  font-size: 11px;
  color: var(--suite-text-secondary);
}

.ml-6 {
  margin-left: 6px;
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
