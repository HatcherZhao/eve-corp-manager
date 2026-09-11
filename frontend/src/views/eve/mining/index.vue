<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { type EChartsOption, graphic } from 'echarts'
import { computed, onMounted, reactive, ref } from 'vue'
import DataFreshnessBanner from '../components/DataFreshnessBanner.vue'
import {
  type EveMiningAnalytics,
  type EveMiningLedger,
  type EveMiningLedgerSummary,
  getEveMiningAnalytics,
  getEveMiningLedger,
  getEveMiningLedgerSummary,
  syncEveMiningLedger,
} from '@/apis/eve'
import { useChart } from '@/hooks'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveMining' })

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const analyticsLoading = ref(false)
const records = ref<EveMiningLedger[]>([])
const total = ref(0)
const summary = ref<EveMiningLedgerSummary>({
  quantity: 0,
  entryCount: 0,
  observerCount: 0,
  characterCount: 0,
  mineralTypeCount: 0,
})
const analytics = ref<EveMiningAnalytics>({
  corporation: { quantity: 0, entryCount: 0, observerCount: 0, characterCount: 0, mineralTypeCount: 0 },
  timeline: [],
  observers: [],
  characters: [],
})
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  dateRange: [] as string[],
})
const canSync = computed(() => userStore.permissions.includes('eve:mining:sync') || userStore.permissions.includes('*:*:*'))
const freshnessVersion = ref(0)
const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

const chartTextColor = (isDark: boolean) => (isDark ? 'rgba(255, 255, 255, .68)' : '#4E5969')
const chartGridColor = (isDark: boolean) => (isDark ? 'rgba(255, 255, 255, .12)' : '#E5E6EB')
const formatTooltipNumber = (value: number) => value.toLocaleString()

/** 数据可视化按完整筛选结果渲染，不受下方分页表格影响。 */
const { chartOption: trendChartOption } = useChart((isDark): EChartsOption => ({
  animation: !prefersReducedMotion,
  animationDuration: 680,
  animationEasing: 'cubicOut',
  grid: { top: 26, right: 20, bottom: 28, left: 58 },
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value) => `${formatTooltipNumber(Number(value))} 单位`,
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: analytics.value.timeline.map((item) => dayjs(item.recordedAt).format('M/D')),
    axisLine: { lineStyle: { color: chartGridColor(isDark) } },
    axisLabel: { color: chartTextColor(isDark), fontSize: 11 },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: chartGridColor(isDark), type: 'dashed' } },
    axisLabel: { color: chartTextColor(isDark), formatter: (value: number) => formatTooltipNumber(value), fontSize: 11 },
  },
  series: [{
    name: '开采量',
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { width: 3, color: '#246EFF' },
    itemStyle: { color: '#246EFF', borderColor: isDark ? '#1D2129' : '#FFF', borderWidth: 2 },
    areaStyle: { color: new graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(36, 110, 255, .34)' }, { offset: 1, color: 'rgba(36, 110, 255, .02)' }]) },
    data: analytics.value.timeline.map((item) => item.quantity),
  }],
}))

/** 建筑排行使用横向条形图，优先保证名称在窄屏下仍清晰可读。 */
const { chartOption: observerChartOption } = useChart((isDark): EChartsOption => {
  const items = [...analytics.value.observers].reverse()
  return {
    animation: !prefersReducedMotion,
    animationDuration: 560,
    animationDelay: (index: number) => index * 45,
    animationEasing: 'cubicOut',
    grid: { top: 12, right: 48, bottom: 8, left: 118 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (value) => `${formatTooltipNumber(Number(value))} 单位` },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: chartGridColor(isDark), type: 'dashed' } }, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false } },
    yAxis: { type: 'category', data: items.map((item) => item.observerName || '名称待补齐'), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: chartTextColor(isDark), width: 106, overflow: 'truncate', fontSize: 12 } },
    series: [{
      name: '开采量',
      type: 'bar',
      barMaxWidth: 20,
      itemStyle: { color: new graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#6B5AED' }, { offset: 1, color: '#9B8CFF' }]), borderRadius: [0, 7, 7, 0] },
      label: { show: true, position: 'right', color: chartTextColor(isDark), fontSize: 11, formatter: ({ value }: { value: number }) => formatTooltipNumber(value) },
      data: items.map((item) => item.quantity),
    }],
  }
})

/** 玩家排行与建筑视图并列展示，帮助识别贡献分布而非只查看单条记录。 */
const { chartOption: characterChartOption } = useChart((isDark): EChartsOption => {
  const items = [...analytics.value.characters].reverse()
  return {
    animation: !prefersReducedMotion,
    animationDuration: 560,
    animationDelay: (index: number) => index * 45,
    animationEasing: 'cubicOut',
    grid: { top: 12, right: 48, bottom: 8, left: 118 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (value) => `${formatTooltipNumber(Number(value))} 单位` },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: chartGridColor(isDark), type: 'dashed' } }, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false } },
    yAxis: { type: 'category', data: items.map((item) => item.characterName || '角色名称待补齐'), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: chartTextColor(isDark), width: 106, overflow: 'truncate', fontSize: 12 } },
    series: [{
      name: '开采量',
      type: 'bar',
      barMaxWidth: 20,
      itemStyle: { color: new graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#F2994A' }, { offset: 1, color: '#F2C94C' }]), borderRadius: [0, 7, 7, 0] },
      label: { show: true, position: 'right', color: chartTextColor(isDark), fontSize: 11, formatter: ({ value }: { value: number }) => formatTooltipNumber(value) },
      data: items.map((item) => item.quantity),
    }],
  }
})

function dateText(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD') : '—'
}

function timeText(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function formatNumber(value?: number) {
  return (value || 0).toLocaleString()
}

/** 将组件字段转换为和后端同名、可选的筛选条件。 */
function filters() {
  return {
    keyword: query.keyword || undefined,
    fromDate: query.dateRange[0],
    toDate: query.dateRange[1],
  }
}

/** 单独加载图表；统计暂不可用时不影响明细账本继续查看。 */
async function loadAnalytics() {
  analyticsLoading.value = true
  try {
    const { data } = await getEveMiningAnalytics(filters())
    analytics.value = data
  } finally {
    analyticsLoading.value = false
  }
}

/** 同时刷新表格、汇总和图表，所有统计使用相同筛选范围。 */
async function loadLedger() {
  loading.value = true
  const analyticsTask = loadAnalytics()
  try {
    const [pageResult, summaryResult] = await Promise.all([
      getEveMiningLedger({ page: query.page, size: query.size, ...filters() }),
      getEveMiningLedgerSummary(filters()),
    ])
    records.value = pageResult.data.list
    total.value = pageResult.data.total
    summary.value = summaryResult.data
  } finally {
    loading.value = false
  }
  await analyticsTask
}

function search() {
  query.page = 1
  loadLedger()
}

function reset() {
  Object.assign(query, { page: 1, keyword: '', dateRange: [] })
  loadLedger()
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveMiningLedger()
    Message.success(data.message)
    await loadLedger()
  } finally {
    syncing.value = false
    freshnessVersion.value += 1
  }
}

onMounted(loadLedger)
</script>

<template>
  <main class="eve-mining-page gi_page">
    <header class="eve-mining-page__header">
      <div>
        <span class="eve-mining-page__eyebrow">MOON MINING · CORPORATION OPERATIONS</span>
        <h1>月矿开采统计</h1>
        <p>从军团观察者账本汇总开采趋势、建筑和成员贡献；数据按游戏返回的日期粒度统计。</p>
      </div>
      <div class="eve-mining-page__header-tools">
        <DataFreshnessBanner :modules="['MINING_LEDGER']" :reload-token="freshnessVersion" />
        <a-button v-if="canSync" type="primary" :loading="syncing" @click="sync"><template #icon><icon-sync /></template>同步开采数据</a-button>
      </div>
    </header>

    <a-alert class="eve-mining-page__guide" type="info" :show-icon="true">同步需要已授权角色同时拥有 <strong>Accountant（会计）</strong> 游戏权限；分页任一页读取失败时，系统会保留上一次完整账本。</a-alert>

    <section class="eve-mining-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索建筑、角色或矿物名称" @press-enter="search"><template #prefix><icon-search /></template></a-input>
      <a-range-picker v-model="query.dateRange" value-format="YYYY-MM-DD" :placeholder="['开始日期', '结束日期']" />
      <a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button>
    </section>

    <section class="eve-mining-page__metrics" aria-label="月矿开采概览">
      <article class="eve-mining-page__metric eve-mining-page__metric--corporation"><span>当前军团开采量</span><strong>{{ formatNumber(analytics.corporation.quantity || summary.quantity) }}</strong><small>{{ analytics.corporation.name || '当前已选军团' }} · {{ formatNumber(analytics.corporation.entryCount || summary.entryCount) }} 条记录</small></article>
      <article class="eve-mining-page__metric"><span>开采建筑</span><strong>{{ formatNumber(summary.observerCount) }}</strong><small>有开采记录的观察者</small></article>
      <article class="eve-mining-page__metric"><span>参与玩家</span><strong>{{ formatNumber(summary.characterCount) }}</strong><small>当前筛选范围内</small></article>
      <article class="eve-mining-page__metric"><span>最近开采日期</span><strong>{{ dateText(summary.latestRecordedAt) }}</strong><small>已收录 {{ formatNumber(summary.mineralTypeCount) }} 种矿物</small></article>
    </section>

    <a-spin :loading="analyticsLoading" class="eve-mining-page__analytics-loading">
      <section class="eve-mining-page__analytics" aria-label="月矿开采分析图表">
        <article class="eve-mining-page__chart-card eve-mining-page__chart-card--trend"><div class="eve-mining-page__chart-heading"><div><span>时间趋势</span><h2>每日开采量</h2></div><small>筛选范围内的累计单位</small></div><Chart v-if="analytics.timeline.length" :option="trendChartOption" height="250px" /><a-empty v-else description="暂无可用于趋势统计的开采数据" /></article>
        <article class="eve-mining-page__chart-card"><div class="eve-mining-page__chart-heading"><div><span>建筑维度</span><h2>开采建筑排行</h2></div><small>前 {{ analytics.observers.length }} 座</small></div><Chart v-if="analytics.observers.length" :option="observerChartOption" height="250px" /><a-empty v-else description="暂无开采建筑数据" /></article>
        <article class="eve-mining-page__chart-card"><div class="eve-mining-page__chart-heading"><div><span>玩家维度</span><h2>开采成员排行</h2></div><small>前 {{ analytics.characters.length }} 名</small></div><Chart v-if="analytics.characters.length" :option="characterChartOption" height="250px" /><a-empty v-else description="暂无成员开采数据" /></article>
      </section>
    </a-spin>

    <section class="eve-mining-page__table-wrap">
      <div class="eve-mining-page__caption"><div><span>原始开采明细</span><small>共 {{ total.toLocaleString() }} 条已发布记录</small></div><span>最近同步 {{ timeText(summary.latestSynchronizedAt) }}</span></div>
      <a-table :data="records" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 720 }">
        <template #columns>
          <a-table-column title="日期" :width="112"><template #cell="{ record }"><strong>{{ dateText(record.recordedAt) }}</strong></template></a-table-column>
          <a-table-column title="开采建筑" :width="150" ellipsis tooltip><template #cell="{ record }"><strong>{{ record.observerName || '建筑名称待补齐' }}</strong></template></a-table-column>
          <a-table-column title="采矿角色" :width="140" ellipsis tooltip><template #cell="{ record }"><strong>{{ record.characterName || '角色名称待补齐' }}</strong></template></a-table-column>
          <a-table-column title="矿物" :width="180" ellipsis tooltip><template #cell="{ record }"><div class="eve-mining-page__type-identity"><EveTypeIcon :type-id="record.typeId" :size="26" :alt="`${record.typeName || '矿物'}图标`" /><strong>{{ record.typeName || '矿物类型待补齐' }}</strong></div></template></a-table-column>
          <a-table-column title="数量" :width="120" align="right"><template #cell="{ record }"><strong class="eve-mining-page__quantity">{{ formatNumber(record.quantity) }}</strong></template></a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadLedger" @page-size-change="search" />
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-mining-page { color: var(--color-text-1); }
.eve-mining-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-mining-page__header-tools { display: flex; align-self: flex-start; flex-direction: column; align-items: flex-end; gap: 10px; }.eve-mining-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }.eve-mining-page h1 { margin: 5px 0; font-size: 26px; }.eve-mining-page__header p { max-width: 720px; margin: 0; color: var(--color-text-3); }
.eve-mining-page__guide { margin-top: 12px; }.eve-mining-page__filter-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 12px 0; padding: 10px 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }.eve-mining-page__filter-bar .arco-input-wrapper { width: min(310px, 100%); }.eve-mining-page__filter-bar .arco-picker { width: 190px; }
.eve-mining-page__metrics { display: grid; grid-template-columns: 1.18fr repeat(3, 1fr); gap: 10px; margin-bottom: 12px; }.eve-mining-page__metric { display: flex; min-width: 0; flex-direction: column; gap: 3px; min-height: 84px; padding: 13px 14px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }.eve-mining-page__metric--corporation { border-color: rgba(var(--arcoblue-6), .28); background: linear-gradient(120deg, rgba(var(--arcoblue-6), .1), transparent 72%), var(--color-bg-1); }.eve-mining-page__metric span, .eve-mining-page__metric small { overflow: hidden; color: var(--color-text-3); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.eve-mining-page__metric strong { overflow: hidden; color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 22px; text-overflow: ellipsis; white-space: nowrap; }
.eve-mining-page__analytics-loading { width: 100%; }.eve-mining-page__analytics { display: grid; grid-template-columns: minmax(0, 1.12fr) minmax(280px, .94fr) minmax(280px, .94fr); gap: 10px; }.eve-mining-page__chart-card { display: flex; min-width: 0; min-height: 318px; flex-direction: column; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }.eve-mining-page__chart-card--trend { border-color: rgba(var(--arcoblue-6), .24); background: linear-gradient(135deg, rgba(var(--arcoblue-6), .07), transparent 50%), var(--color-bg-1); }.eve-mining-page__chart-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; min-height: 40px; }.eve-mining-page__chart-heading span, .eve-mining-page__chart-heading small { color: var(--color-text-3); font-size: 12px; }.eve-mining-page__chart-heading h2 { margin: 2px 0 0; font-size: 16px; }.eve-mining-page__chart-heading small { padding-top: 3px; text-align: right; }.eve-mining-page__chart-card :deep(.arco-empty) { flex: 1; }
.eve-mining-page__table-wrap { margin-top: 12px; padding: 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }.eve-mining-page__caption { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 8px; color: var(--color-text-3); font-size: 12px; }.eve-mining-page__caption > div > span { display: block; color: var(--color-text-1); font-size: 15px; font-weight: 600; }.eve-mining-page__caption small { display: block; margin-top: 2px; }.eve-mining-page__quantity { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; }.eve-mining-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 12px; }.eve-mining-page__type-identity { display: flex; align-items: center; gap: 10px; }.eve-mining-page__type-identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 1180px) { .eve-mining-page__analytics { grid-template-columns: repeat(2, minmax(0, 1fr)); }.eve-mining-page__chart-card--trend { grid-column: 1 / -1; } }.eve-mining-page__chart-card--trend :deep(.echarts) { min-height: 250px; }
@media (max-width: 980px) { .eve-mining-page__header { align-items: flex-start; flex-direction: column; }.eve-mining-page__header-tools { align-items: flex-start; }.eve-mining-page__metrics { grid-template-columns: repeat(2, 1fr); }.eve-mining-page__filter-bar .arco-input-wrapper, .eve-mining-page__filter-bar .arco-picker { width: 100%; } }
@media (max-width: 680px) { .eve-mining-page__analytics, .eve-mining-page__metrics { grid-template-columns: 1fr; }.eve-mining-page__chart-card--trend { grid-column: auto; }.eve-mining-page__caption { flex-direction: column; } }
@media (prefers-reduced-motion: reduce) { .eve-mining-page *, .eve-mining-page *::before, .eve-mining-page *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; } }
</style>
