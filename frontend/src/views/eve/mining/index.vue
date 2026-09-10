<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import DataFreshnessBanner from '../components/DataFreshnessBanner.vue'
import {
  type EveMiningLedger,
  type EveMiningLedgerSummary,
  getEveMiningLedger,
  getEveMiningLedgerSummary,
  syncEveMiningLedger,
} from '@/apis/eve'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveMining' })

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const records = ref<EveMiningLedger[]>([])
const total = ref(0)
const summary = ref<EveMiningLedgerSummary>({
  quantity: 0,
  entryCount: 0,
  observerCount: 0,
  characterCount: 0,
  mineralTypeCount: 0,
})
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  dateRange: [] as string[],
})
const canSync = computed(() => userStore.permissions.includes('eve:mining:sync') || userStore.permissions.includes('*:*:*'))
const freshnessVersion = ref(0)

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

/** 同时刷新表格和服务端汇总，避免统计数字由当前页数据错误推导。 */
async function loadLedger() {
  loading.value = true
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
        <span class="eve-mining-page__eyebrow">CORPORATION MINING LEDGER · OBSERVER RECORDS</span>
        <h1>采矿账本</h1>
        <p>按观察者记录查看角色、矿物和数量。国服仅返回日期粒度的聚合数据，不代表实时采矿明细。</p>
      </div>
      <div class="eve-mining-page__header-tools">
        <DataFreshnessBanner :modules="['MINING_LEDGER']" :reload-token="freshnessVersion" />
        <a-button v-if="canSync" type="primary" :loading="syncing" @click="sync">
          <template #icon><icon-sync /></template>同步采矿账本
        </a-button>
      </div>
    </header>

    <a-alert class="eve-mining-page__guide" type="info" :show-icon="true">
      同步需要已授权角色同时拥有 <strong>Accountant（会计）</strong> 游戏权限；分页任一页读取失败时，系统会保留上一次完整账本。
    </a-alert>
    <section class="eve-mining-page__metrics">
      <article><span>矿物总量</span><strong>{{ formatNumber(summary.quantity) }}</strong><small>当前筛选条件</small></article>
      <article><span>账本记录</span><strong>{{ formatNumber(summary.entryCount) }}</strong><small>日期聚合条目</small></article>
      <article><span>采矿角色</span><strong>{{ formatNumber(summary.characterCount) }}</strong><small>观察者 {{ formatNumber(summary.observerCount) }} 个</small></article>
      <article><span>最近账本日期</span><strong>{{ dateText(summary.latestRecordedAt) }}</strong><small>同步于 {{ timeText(summary.latestSynchronizedAt) }}</small></article>
    </section>

    <section class="eve-mining-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索观察者、角色或矿物名称" @press-enter="search"><template #prefix><icon-search /></template></a-input>
      <a-range-picker v-model="query.dateRange" value-format="YYYY-MM-DD" :placeholder="['开始日期', '结束日期']" />
      <a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button>
    </section>

    <section class="eve-mining-page__table-wrap">
      <div class="eve-mining-page__caption"><span>共 {{ total.toLocaleString() }} 条已发布记录</span><span>矿物类型 {{ summary.mineralTypeCount.toLocaleString() }} 种</span></div>
      <a-table :data="records" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 1220 }">
        <template #columns>
          <a-table-column title="账本日期" :width="132"><template #cell="{ record }"><strong>{{ dateText(record.recordedAt) }}</strong></template></a-table-column>
          <a-table-column title="观察者 / 地点" :width="220"><template #cell="{ record }"><strong>{{ record.observerName || '观察者名称待补齐' }}</strong></template></a-table-column>
          <a-table-column title="采矿角色" :width="190"><template #cell="{ record }"><strong>{{ record.characterName || '角色名称待补齐' }}</strong></template></a-table-column>
          <a-table-column title="矿物" :width="220"><template #cell="{ record }"><div class="eve-mining-page__type-identity"><EveTypeIcon :type-id="record.typeId" :size="30" :alt="`${record.typeName || '矿物'}图标`" /><strong>{{ record.typeName || '矿物类型待补齐' }}</strong></div></template></a-table-column>
          <a-table-column title="数量" :width="170" align="right"><template #cell="{ record }"><strong class="eve-mining-page__quantity">{{ formatNumber(record.quantity) }}</strong></template></a-table-column>
          <a-table-column title="最近同步" :width="180"><template #cell="{ record }">{{ timeText(record.lastSeenAt) }}</template></a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadLedger" @page-size-change="search" />
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-mining-page { color: var(--color-text-1); }
.eve-mining-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 26px 30px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 16px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-mining-page__header-tools { display: flex; align-self: flex-start; flex-direction: column; align-items: flex-end; gap: 10px; }
.eve-mining-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }.eve-mining-page h1 { margin: 8px 0; font-size: 30px; }.eve-mining-page__header p { max-width: 720px; margin: 0; color: var(--color-text-3); }
.eve-mining-page__guide { margin-top: 18px; }.eve-mining-page__metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin: 18px 0; }.eve-mining-page__metrics article { display: flex; flex-direction: column; gap: 5px; min-height: 100px; padding: 17px 18px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }.eve-mining-page__metrics span, .eve-mining-page__metrics small { color: var(--color-text-3); font-size: 12px; }.eve-mining-page__metrics strong { overflow: hidden; color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 24px; text-overflow: ellipsis; white-space: nowrap; }
.eve-mining-page__filter-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 18px 0; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }.eve-mining-page__filter-bar .arco-input-wrapper { width: 195px; }.eve-mining-page__filter-bar .arco-input-wrapper:first-child { width: min(310px, 100%); }.eve-mining-page__filter-bar .arco-input-number, .eve-mining-page__filter-bar .arco-picker { width: 190px; }
.eve-mining-page__table-wrap { padding: 18px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }.eve-mining-page__caption { display: flex; justify-content: space-between; margin-bottom: 12px; color: var(--color-text-3); font-size: 13px; }.eve-mining-page__table-wrap small { display: block; margin-top: 4px; color: var(--color-text-3); }.eve-mining-page__quantity { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; }.eve-mining-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 16px; }
.eve-mining-page__type-identity { display: flex; align-items: center; gap: 10px; }.eve-mining-page__type-identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 980px) { .eve-mining-page__header { align-items: flex-start; flex-direction: column; }.eve-mining-page__header-tools { align-items: flex-start; }.eve-mining-page__metrics { grid-template-columns: repeat(2, 1fr); }.eve-mining-page__filter-bar .arco-input-wrapper, .eve-mining-page__filter-bar .arco-input-wrapper:first-child, .eve-mining-page__filter-bar .arco-input-number, .eve-mining-page__filter-bar .arco-picker { width: 100%; } }
@media (max-width: 560px) { .eve-mining-page__metrics { grid-template-columns: 1fr; }.eve-mining-page__caption { gap: 5px; flex-direction: column; } }
</style>
