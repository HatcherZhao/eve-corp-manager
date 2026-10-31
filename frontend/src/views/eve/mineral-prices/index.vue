<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, reactive, ref } from 'vue'
import MarketPriceFreshness from '../components/MarketPriceFreshness.vue'
import { parseEveTime } from '@/utils/eveTime'
import {
  type EveMarketItem,
  type EveMineralPriceCategory,
  getEveMineralPrices,
} from '@/apis/eve'

defineOptions({ name: 'EveMineralPrices' })

const loading = ref(false)
const records = ref<EveMarketItem[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', category: 'CORPORATION_MOON' as EveMineralPriceCategory })
let pollTimer: ReturnType<typeof setTimeout> | undefined
let requestSequence = 0
let pageActive = true

const categories: Array<{ key: EveMineralPriceCategory, label: string, description: string }> = [
  { key: 'CORPORATION_MOON', label: '本军团月矿', description: '按当前军团已同步的月矿账本识别矿种，不包含开采数量。' },
  { key: 'ORE', label: '全部普通矿物', description: '游戏资料中归类为标准矿石的全部矿物。' },
  { key: 'ICE', label: '全部冰矿', description: '游戏资料中归类为冰矿的全部矿物。' },
  { key: 'MOON', label: '全部月矿', description: '游戏资料中归类为卫星矿石的全部矿物。' },
]
const currentCategory = computed(() => categories.find((item) => item.key === query.category)!)

/** 价格只代表吉他市场的每单位报价，不推断任何角色或军团的持有量。 */
function formatIsk(value?: number) {
  return value === undefined || value === null ? '暂无报价' : `${value.toLocaleString(undefined, { maximumFractionDigits: 2 })} ISK`
}

/** 切换价格目录后从第一页重新读取，关键词仍可用于同类矿物的快速定位。 */
function changeCategory(key: string | number) {
  query.category = key as EveMineralPriceCategory
  query.page = 1
  void loadPrices()
}

/** 跟进后台按需报价，并丢弃切换分类或离开页面前的旧响应。 */
async function loadPrices(background = false) {
  if (pollTimer) clearTimeout(pollTimer)
  const sequence = ++requestSequence
  if (!background) loading.value = true
  try {
    const { data } = await getEveMineralPrices({
      page: query.page,
      size: query.size,
      category: query.category,
      keyword: query.keyword || undefined,
    })
    if (!pageActive || sequence !== requestSequence) return
    records.value = data.list
    total.value = data.total
    if (data.list.some((item) => item.marketStatus === 'PENDING' || item.marketStatus === 'FAILED')) {
      pollTimer = setTimeout(() => void loadPrices(true), 8000)
    } else {
      const deadlines = data.list.map((item) => parseEveTime(item.quote?.freshnessExpiresAt))
        .filter((time) => time.isValid())
        .map((time) => time.valueOf())
      if (deadlines.length) {
        const delay = Math.max(2000, Math.min(...deadlines) - Date.now() + 250)
        pollTimer = setTimeout(() => void loadPrices(true), delay)
      }
    }
  } catch (error) {
    if (!background) throw error
    if (pageActive && sequence === requestSequence) {
      records.value = records.value.map((item) => ({ ...item, marketStatus: 'FAILED' }))
      pollTimer = setTimeout(() => void loadPrices(true), 8000)
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function search() {
  query.page = 1
  void loadPrices()
}

function reset() {
  query.keyword = ''
  query.page = 1
  void loadPrices()
}

/** 暂停隐藏页面的轮询，并在重新打开时恢复当前分类。 */
function stopPolling() {
  pageActive = false
  requestSequence++
  if (pollTimer) clearTimeout(pollTimer)
}

onMounted(() => void loadPrices())
onActivated(() => {
  if (!pageActive) {
    pageActive = true
    void loadPrices()
  }
})
onDeactivated(stopPolling)
onUnmounted(stopPolling)
</script>

<template>
  <main class="eve-mineral-prices gi_page">
    <header class="eve-mineral-prices__header">
      <div class="eve-mineral-prices__heading">
        <span class="eve-mineral-prices__eyebrow">JITA · MINERAL PRICE DIRECTORY</span>
        <h1>矿物价格</h1>
        <p>以吉他贸易中心为统一口径展示每单位买卖参考价；不显示或推断军团、成员的任何持有数量。</p>
      </div>
    </header>

    <section class="eve-mineral-prices__directory" aria-label="矿物价格分类">
      <a-tabs :active-key="query.category" type="rounded" @change="changeCategory">
        <a-tab-pane v-for="category in categories" :key="category.key" :title="category.label" />
      </a-tabs>
      <p>{{ currentCategory.description }}</p>
    </section>

    <section class="eve-mineral-prices__toolbar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索矿物名称或分类" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
    </section>

    <section class="eve-mineral-prices__table-wrap">
      <div class="eve-mineral-prices__caption">
        <div><span>{{ currentCategory.label }}</span><small>共 {{ total.toLocaleString() }} 种矿物 · 按需查询官方行情</small></div>
        <span>吉他贸易中心</span>
      </div>
      <a-table :data="records" :loading="loading" :pagination="false" row-key="type.typeId" :scroll="{ x: 660 }">
        <template #columns>
          <a-table-column title="矿物" :width="230" ellipsis tooltip>
            <template #cell="{ record }"><div class="eve-mineral-prices__identity"><EveTypeIcon :type-id="record.type.typeId" :size="30" :alt="`${record.type.typeName}图标`" /><strong>{{ record.type.typeName }}</strong></div></template>
          </a-table-column>
          <a-table-column title="最高收购价" :width="145" align="right" nowrap><template #cell="{ record }"><strong class="eve-mineral-prices__buy">{{ !record.marketStatus || record.marketStatus === 'READY' ? formatIsk(record.quote?.highestBuyPrice) : '—' }}</strong></template></a-table-column>
          <a-table-column title="最低卖出价" :width="145" align="right" nowrap><template #cell="{ record }"><strong>{{ !record.marketStatus || record.marketStatus === 'READY' ? formatIsk(record.quote?.lowestSellPrice) : '—' }}</strong></template></a-table-column>
          <a-table-column title="行情状态" :width="132" align="right"><template #cell="{ record }"><span v-if="record.marketStatus === 'PENDING'">正在查询</span><span v-else-if="record.marketStatus === 'FAILED'">查询失败，等待重试</span><MarketPriceFreshness v-else :has-quote="Boolean(record.quote)" :synchronized-at="record.quote?.synchronizedAt" :freshness-expires-at="record.quote?.freshnessExpiresAt" /></template></a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!loading && !records.length" description="暂无符合条件的矿物；本军团月矿需先同步月矿开采统计" />
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadPrices()" @page-size-change="search" />
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-mineral-prices { color: var(--color-text-1); overflow-x: clip; }
.eve-mineral-prices__header, .eve-mineral-prices__directory, .eve-mineral-prices__toolbar, .eve-mineral-prices__table-wrap { border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-mineral-prices__header { padding: 16px; border-color: rgba(var(--arcoblue-6), .22); background: linear-gradient(125deg, rgba(var(--arcoblue-6), .1), transparent 58%), var(--color-bg-1); }
.eve-mineral-prices__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: .6875rem; letter-spacing: .13em; }
.eve-mineral-prices h1 { margin: .25rem 0; font-size: clamp(1.5rem, 5vw, 1.75rem); }.eve-mineral-prices__heading p { max-width: 46rem; margin: 0; color: var(--color-text-3); line-height: 1.6; }
.eve-mineral-prices__directory { margin-top: .75rem; padding: .75rem .75rem .5rem; }.eve-mineral-prices__directory p { margin: .25rem 0 0; color: var(--color-text-3); font-size: .8125rem; line-height: 1.5; }
.eve-mineral-prices__toolbar { display: flex; flex-wrap: wrap; gap: .5rem; margin: .75rem 0; padding: .625rem; }.eve-mineral-prices__toolbar .arco-input-wrapper { flex: 1 1 14rem; min-width: 0; }
.eve-mineral-prices__table-wrap { padding: .75rem; overflow: hidden; }.eve-mineral-prices__caption { display: flex; align-items: flex-start; justify-content: space-between; gap: .5rem; margin-bottom: .625rem; color: var(--color-text-3); font-size: .75rem; }.eve-mineral-prices__caption > div > span { display: block; color: var(--color-text-1); font-size: .9375rem; font-weight: 600; }.eve-mineral-prices__caption small { display: block; margin-top: .125rem; }
.eve-mineral-prices__identity { display: flex; align-items: center; gap: .625rem; min-width: 0; }.eve-mineral-prices__identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.eve-mineral-prices__buy { color: rgb(var(--arcoblue-6)); }.eve-mineral-prices__time { color: var(--color-text-3); white-space: nowrap; }.eve-mineral-prices__table-wrap .arco-pagination { justify-content: flex-end; margin-top: .75rem; }
@media (min-width: 48rem) { .eve-mineral-prices__header { padding: 1.25rem 1.375rem; }.eve-mineral-prices__directory { padding: .75rem 1rem .5rem; }.eve-mineral-prices__toolbar { padding: .625rem .75rem; } }
@media (prefers-reduced-motion: reduce) { .eve-mineral-prices *, .eve-mineral-prices *::before, .eve-mineral-prices *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; } }
</style>
