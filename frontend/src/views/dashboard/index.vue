<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">数据看板</h2>
        <p class="page-header__desc">
          内容发布趋势、分类与区域分布、活动报名转化率 —— 数据来自
          <code>GET /api/stats/dashboard</code>（服务端缓存 5 分钟）
        </p>
      </div>
      <div class="page-header__extra">
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :label="7">近 7 天</el-radio-button>
          <el-radio-button :label="14">近 14 天</el-radio-button>
          <el-radio-button :label="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <DataState :loading="loading && !data" :error="error" :empty="!loading && !data" @retry="load">
      <template v-if="data">
        <div class="stat-grid">
          <StatCard label="内容总数" :value="formatNumber(data.totals.contentTotal)" icon="Document" />
          <StatCard
            label="已发布"
            :value="formatNumber(data.totals.publishedTotal)"
            icon="CircleCheck"
            tone="green"
            :hint="`待审核 ${data.totals.pendingTotal} 篇`"
          />
          <StatCard
            label="累计阅读"
            :value="formatNumber(data.totals.viewTotal)"
            icon="View"
            tone="purple"
          />
          <StatCard label="活动总数" :value="formatNumber(data.totals.activityTotal)" icon="Calendar" tone="orange" />
          <StatCard
            label="报名人次"
            :value="formatNumber(data.totals.signupTotal)"
            icon="Tickets"
            tone="blue"
            :hint="`已核销 ${data.totals.verifiedTotal} 人次`"
          />
          <StatCard label="资源点位" :value="formatNumber(data.totals.pointTotal)" icon="LocationInformation" tone="green" />
          <StatCard label="系统用户" :value="formatNumber(data.totals.userTotal)" icon="UserFilled" tone="purple" />
          <StatCard
            label="核销率"
            :value="verifyRate"
            icon="Finished"
            tone="orange"
            hint="已核销 / 总报名"
          />
          <StatCard label="待审核内容" :value="formatNumber(data.totals.pendingTotal)" icon="Warning" tone="blue" />
        </div>

        <div class="chart-row">
          <section class="panel">
            <div class="panel__header">
              内容发布趋势
              <span class="text-muted">按 publish_time 聚合，缺失日期补 0</span>
            </div>
            <div ref="trendRef" class="chart chart--md"></div>
          </section>
          <section class="panel">
            <div class="panel__header">
              分类内容分布
              <span class="text-muted">cms_category 维度</span>
            </div>
            <div ref="categoryRef" class="chart chart--md"></div>
          </section>
        </div>

        <div class="chart-row">
          <section class="panel">
            <div class="panel__header">
              区域资源分布
              <span class="text-muted">geo_region 维度</span>
            </div>
            <div ref="regionRef" class="chart chart--sm"></div>
          </section>
          <section class="panel">
            <div class="panel__header">
              活动报名转化
              <span class="text-muted">报名数 / 场次容量</span>
            </div>
            <el-table :data="data.activityConversion" size="small" class="conversion-table">
              <el-table-column prop="activityTitle" label="活动" min-width="140" show-overflow-tooltip />
              <el-table-column prop="sessionName" label="场次" min-width="110" show-overflow-tooltip />
              <el-table-column prop="capacity" label="容量" width="70" align="center" />
              <el-table-column prop="signed" label="报名" width="70" align="center" />
              <el-table-column label="转化率" width="130">
                <template #default="{ row }">
                  <el-progress
                    :percentage="Number(row.conversionRate ?? 0)"
                    :stroke-width="8"
                    :color="progressColor(Number(row.conversionRate ?? 0))"
                  />
                </template>
              </el-table-column>
            </el-table>
          </section>
        </div>
      </template>
    </DataState>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import DataState from '@/components/DataState.vue'
import StatCard from '@/components/StatCard.vue'
import { getDashboard } from '@/api/stats'
import type { DashboardData } from '@/types/api'
import { formatNumber } from '@/utils/format'

const days = ref(7)
const data = ref<DashboardData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const trendRef = ref<HTMLDivElement>()
const categoryRef = ref<HTMLDivElement>()
const regionRef = ref<HTMLDivElement>()
const charts: echarts.ECharts[] = []

const verifyRate = computed(() => {
  const total = data.value?.totals.signupTotal ?? 0
  const verified = data.value?.totals.verifiedTotal ?? 0
  return total ? `${((verified / total) * 100).toFixed(1)}%` : '0%'
})

function progressColor(value: number): string {
  if (value >= 90) return '#12b76a'
  if (value >= 50) return '#2f6bff'
  return '#f79009'
}

function ensureChart(el: HTMLDivElement | undefined): echarts.ECharts | null {
  if (!el) return null
  const chart = echarts.getInstanceByDom(el) ?? echarts.init(el)
  if (!charts.includes(chart)) charts.push(chart)
  return chart
}

function renderTrend(): void {
  if (!data.value) return
  const chart = ensureChart(trendRef.value)
  if (!chart) return
  const points = data.value.contentTrend
  chart.setOption({
    grid: { left: 8, right: 18, top: 28, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map((item) => item.date.slice(5)),
      axisLine: { lineStyle: { color: '#e5e8ef' } },
      axisLabel: { color: '#8a90a2' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f2f7' } },
      axisLabel: { color: '#8a90a2' }
    },
    series: [
      {
        name: '发布内容',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: points.map((item) => item.count),
        itemStyle: { color: '#2f6bff' },
        lineStyle: { width: 2.5, color: '#2f6bff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(47,107,255,0.28)' },
            { offset: 1, color: 'rgba(47,107,255,0.02)' }
          ])
        }
      }
    ]
  })
}

function renderCategory(): void {
  if (!data.value) return
  const chart = ensureChart(categoryRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#5a6072' } },
    color: ['#2f6bff', '#7a5af8', '#12b76a', '#f79009', '#0ba5ec', '#f04438'],
    series: [
      {
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: data.value.categoryRank.map((item) => ({ name: item.name, value: item.value }))
      }
    ]
  })
}

function renderRegion(): void {
  if (!data.value) return
  const chart = ensureChart(regionRef.value)
  if (!chart) return
  const items = [...data.value.regionDistribution].reverse()
  chart.setOption({
    grid: { left: 8, right: 24, top: 12, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f2f7' } },
      axisLabel: { color: '#8a90a2' }
    },
    yAxis: {
      type: 'category',
      data: items.map((item) => item.name),
      axisLine: { lineStyle: { color: '#e5e8ef' } },
      axisLabel: { color: '#5a6072' }
    },
    series: [
      {
        type: 'bar',
        barWidth: 12,
        data: items.map((item) => item.value),
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#7a5af8' },
            { offset: 1, color: '#2f6bff' }
          ])
        }
      }
    ]
  })
}

async function renderCharts(): Promise<void> {
  await nextTick()
  renderTrend()
  renderCategory()
  renderRegion()
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await getDashboard(days.value)
    await renderCharts()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

function onResize(): void {
  charts.forEach((chart) => chart.resize())
}

watch(data, () => {
  void renderCharts()
})

onMounted(() => {
  void load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((chart) => chart.dispose())
  charts.length = 0
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.chart {
  width: 100%;
}

.chart--md {
  height: 288px;
}

.chart--sm {
  height: 268px;
}

.conversion-table {
  padding: 4px 2px 8px;
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
