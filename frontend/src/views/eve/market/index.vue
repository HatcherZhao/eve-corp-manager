<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import type { EChartsOption } from 'echarts'
import { computed, onMounted, reactive, ref } from 'vue'
import MarketPriceFreshness from '../components/MarketPriceFreshness.vue'
import {
  type EveMarketDetail,
  type EveMarketItem,
  type EveStaticTypeCategoryNode,
  getEveMarket,
  getEveMarketDetail,
  getEveStaticTypeCategories,
  syncEveMarketDetail,
} from '@/apis/eve'
import { useChart } from '@/hooks'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveMarket' })

const userStore = useUserStore()
const loading = ref(false)
const categoryLoading = ref(false)
const detailLoading = ref(false)
const syncing = ref(false)
const records = ref<EveMarketItem[]>([])
const total = ref(0)
const categoryTree = ref<MarketCategoryTreeNode[]>([])
const selectedCategory = ref<MarketCategoryTreeNode>()
const selectedCategoryKeys = ref<string[]>([])
const expandedCategoryKeys = ref<string[]>([])
const detail = ref<EveMarketDetail>()
const query = reactive({ page: 1, size: 20, keyword: '' })
const canSync = computed(() => userStore.permissions.includes('eve:market:sync') || userStore.permissions.includes('*:*:*'))
const activeCategoryLabel = computed(() => selectedCategory.value?.name || '吉他行情')
const detailHistory = computed(() => detail.value?.history.slice(-120) || [])
const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

interface MarketCategoryTreeNode extends EveStaticTypeCategoryNode {
  key: string
  title: string
  children: MarketCategoryTreeNode[]
}

/** 将静态资料的分类路径转换为用户可读的市场面包屑。 */
function categoryPath(item: EveMarketItem['type']) {
  return [item.marketCategoryL1, item.marketCategoryL2, item.marketCategoryL3, item.marketCategoryL4, item.marketCategoryL5, item.marketCategoryL6]
    .filter(Boolean)
    .join(' / ') || '未分类'
}

/** 市场分类名称不含此分隔符，可稳定表示六级路径。 */
function categoryKey(path: string[]) {
  return path.join('\u001F') || 'unclassified'
}

/** 将服务端分类节点补齐为 Arco 树可直接消费的节点。 */
function toTreeNode(source: EveStaticTypeCategoryNode): MarketCategoryTreeNode {
  return {
    ...source,
    key: source.unclassified ? 'unclassified' : categoryKey(source.path),
    title: source.name,
    children: source.children.map(toTreeNode),
  }
}

/** 递归收集全部分类键，供用户按需展开。 */
function categoryKeys(source: MarketCategoryTreeNode[]): string[] {
  return source.flatMap((item) => [item.key, ...categoryKeys(item.children)])
}

/** 从当前选择生成后端的六级分类筛选条件。 */
function selectedCategoryQuery() {
  const path = selectedCategory.value?.path || []
  return {
    marketCategoryL1: path[0] || undefined,
    marketCategoryL2: path[1] || undefined,
    marketCategoryL3: path[2] || undefined,
    marketCategoryL4: path[3] || undefined,
    marketCategoryL5: path[4] || undefined,
    marketCategoryL6: path[5] || undefined,
    unclassified: selectedCategory.value?.unclassified || undefined,
  }
}

/** 在已加载的市场分类树中找到用户选中的节点。 */
function findCategory(nodes: MarketCategoryTreeNode[], key: string): MarketCategoryTreeNode | undefined {
  for (const node of nodes) {
    if (node.key === key) return node
    const found = findCategory(node.children, key)
    if (found) return found
  }
}

/** 吉他行情列表一次最多加载二十项，服务端会合并为一次公开报价请求。 */
async function loadMarket() {
  loading.value = true
  try {
    const { data } = await getEveMarket({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      ...selectedCategoryQuery(),
    })
    records.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 读取与基础信息完全相同的分类树，保证市场与物品资料分类不会分叉。 */
async function loadCategories() {
  categoryLoading.value = true
  try {
    categoryTree.value = (await getEveStaticTypeCategories()).data.map(toTreeNode)
  } finally {
    categoryLoading.value = false
  }
}

/** 切换市场分类即回到分类浏览，避免分类与全局搜索两个口径叠加。 */
function selectCategory(keys: string[]) {
  const key = keys[0]
  selectedCategoryKeys.value = key ? [key] : []
  selectedCategory.value = key ? findCategory(categoryTree.value, key) : undefined
  query.keyword = ''
  query.page = 1
  loadMarket()
}

/** 市场关键词始终搜索全部可交易物品，不受当前左侧分类限制。 */
function search() {
  selectedCategory.value = undefined
  selectedCategoryKeys.value = []
  query.page = 1
  loadMarket()
}

function reset() {
  Object.assign(query, { page: 1, keyword: '' })
  selectedCategory.value = undefined
  selectedCategoryKeys.value = []
  loadMarket()
}

/** 打开物品详情，默认由服务端按缓存时效补齐订单簿和历史。 */
async function openDetail(record: EveMarketItem) {
  detailLoading.value = true
  try {
    detail.value = (await getEveMarketDetail(record.type.typeId)).data
  } finally {
    detailLoading.value = false
  }
}

/** 整行点击只负责打开行情，避免表格事件对象被误作强制刷新参数。 */
function openDetailFromRow(record: EveMarketItem) {
  void openDetail(record)
}

/** 管理角色可直接更新当前物品；完成后同步刷新列表中同一条报价。 */
async function syncDetail() {
  if (!detail.value) return
  syncing.value = true
  try {
    const { data } = await syncEveMarketDetail(detail.value.type.typeId)
    if (data.refreshed) {
      detail.value = (await getEveMarketDetail(detail.value.type.typeId)).data
      await loadMarket()
      Message.success(data.message)
    } else {
      Message.warning(data.message)
    }
  } finally {
    syncing.value = false
  }
}

function formatPrice(value?: number) {
  return value === undefined || value === null ? '—' : `${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ISK`
}

function formatVolume(value?: number) {
  return value === undefined || value === null ? '—' : value.toLocaleString()
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('MM-DD HH:mm') : '等待首次同步'
}

function spread(item: EveMarketItem) {
  const buy = item.quote?.highestBuyPrice
  const sell = item.quote?.lowestSellPrice
  return buy === undefined || sell === undefined ? undefined : sell - buy
}

const chartTextColor = (isDark: boolean) => (isDark ? 'rgba(255, 255, 255, .68)' : '#4E5969')
const chartGridColor = (isDark: boolean) => (isDark ? 'rgba(255, 255, 255, .12)' : '#E5E6EB')

/** 日线以收盘价呈现，并将成交量放在独立轴，避免量级压扁价格趋势。 */
const { chartOption: historyChartOption } = useChart((isDark): EChartsOption => ({
  animation: !prefersReducedMotion,
  animationDuration: 640,
  animationEasing: 'cubicOut',
  grid: [{ top: 24, right: 48, bottom: 86, left: 58 }, { top: '73%', right: 48, bottom: 28, left: 58 }],
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value) => typeof value === 'number' ? formatPrice(value) : String(value),
  },
  xAxis: [{
    type: 'category',
    boundaryGap: false,
    data: detailHistory.value.map((item) => dayjs(item.date).format('M/D')),
    axisLine: { lineStyle: { color: chartGridColor(isDark) } },
    axisLabel: { color: chartTextColor(isDark), fontSize: 11 },
    axisTick: { show: false },
  }, {
    type: 'category',
    gridIndex: 1,
    boundaryGap: false,
    data: detailHistory.value.map((item) => dayjs(item.date).format('M/D')),
    axisLine: { lineStyle: { color: chartGridColor(isDark) } },
    axisLabel: { show: false },
    axisTick: { show: false },
  }],
  yAxis: [{
    type: 'value',
    splitLine: { lineStyle: { color: chartGridColor(isDark), type: 'dashed' } },
    axisLabel: { color: chartTextColor(isDark), fontSize: 11 },
  }, {
    type: 'value',
    gridIndex: 1,
    splitLine: { show: false },
    axisLabel: { color: chartTextColor(isDark), fontSize: 10, formatter: (value: number) => value >= 1_000_000 ? `${Math.round(value / 1_000_000)}M` : value.toLocaleString() },
  }],
  dataZoom: [{ type: 'inside', xAxisIndex: [0, 1], startValue: Math.max(0, detailHistory.value.length - 45), endValue: Math.max(0, detailHistory.value.length - 1) }],
  series: [{
    name: '收盘价',
    type: 'line',
    smooth: true,
    showSymbol: false,
    lineStyle: { width: 2.5, color: '#246EFF' },
    itemStyle: { color: '#246EFF' },
    areaStyle: { color: 'rgba(36, 110, 255, .12)' },
    data: detailHistory.value.map((item) => item.closePrice),
  }, {
    name: '成交量',
    type: 'bar',
    xAxisIndex: 1,
    yAxisIndex: 1,
    barMaxWidth: 10,
    itemStyle: { color: 'rgba(112, 101, 240, .58)', borderRadius: [2, 2, 0, 0] },
    data: detailHistory.value.map((item) => item.volume),
  }],
}))

onMounted(() => Promise.all([loadCategories(), loadMarket()]))
</script>

<template>
  <main class="eve-market-page gi_page">
    <header class="eve-market-page__header">
      <div>
        <span class="eve-market-page__eyebrow">JITA · TRADE HUB</span>
        <h1>吉他市场</h1>
        <p>以吉他贸易中心为固定口径，查看物品报价、买卖单和每日价格走势。价格仅作军团资产与月矿估值参考。</p>
      </div>
      <div class="eve-market-page__header-note"><icon-location /> 吉他贸易中心 · 国服公开市场数据</div>
    </header>

    <section class="eve-market-page__workspace">
      <aside class="eve-market-page__category-panel">
        <div class="eve-market-page__panel-heading"><div><span>基础信息</span><h2>物品分类</h2></div></div>
        <div class="eve-market-page__tree-actions"><a-button size="mini" @click="expandedCategoryKeys = categoryKeys(categoryTree)">全部展开</a-button><a-button size="mini" @click="expandedCategoryKeys = []">全部收起</a-button></div>
        <a-spin :loading="categoryLoading" class="eve-market-page__tree-loading">
          <a-tree v-if="categoryTree.length" v-model:expanded-keys="expandedCategoryKeys" :data="categoryTree" :selected-keys="selectedCategoryKeys" block-node show-line class="eve-market-page__category-tree" @select="selectCategory">
            <template #title="node"><span class="eve-market-page__category-node">{{ node.name }}</span></template>
          </a-tree>
          <a-empty v-else description="没有可用的市场分类" />
        </a-spin>
      </aside>

      <section class="eve-market-page__items-panel">
        <div class="eve-market-page__items-heading"><div><span>吉他贸易中心</span><h2>{{ activeCategoryLabel }}</h2></div><small>共 {{ total.toLocaleString() }} 项</small></div>
        <section class="eve-market-page__filter-bar">
          <a-input v-model="query.keyword" allow-clear placeholder="全局搜索物品名称、说明或市场分类" @press-enter="search"><template #prefix><icon-search /></template></a-input>
          <a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button>
        </section>
        <a-table :data="records" :loading="loading" :pagination="false" row-key="type.typeId" :scroll="{ x: 860 }" class="eve-market-page__table" @row-click="openDetailFromRow">
          <template #columns>
            <a-table-column title="物品" :width="205" ellipsis tooltip><template #cell="{ record }"><div class="eve-market-page__type"><EveTypeIcon :type-id="record.type.typeId" :size="28" :alt="`${record.type.typeName}图标`" /><div><strong>{{ record.type.typeName }}</strong><small>{{ categoryPath(record.type) }}</small></div></div></template></a-table-column>
            <a-table-column title="最高求购" :width="175" align="right"><template #cell="{ record }"><strong class="eve-market-page__price eve-market-page__buy">{{ formatPrice(record.quote?.highestBuyPrice) }}</strong></template></a-table-column>
            <a-table-column title="最低卖出" :width="175" align="right"><template #cell="{ record }"><strong class="eve-market-page__price eve-market-page__sell">{{ formatPrice(record.quote?.lowestSellPrice) }}</strong></template></a-table-column>
            <a-table-column title="买卖价差" :width="175" align="right"><template #cell="{ record }"><span class="eve-market-page__price">{{ spread(record) === undefined ? '—' : formatPrice(spread(record)) }}</span></template></a-table-column>
            <a-table-column title="价格新鲜度" :width="128"><template #cell="{ record }"><MarketPriceFreshness :synchronized-at="record.quote?.synchronizedAt" :freshness-expires-at="record.quote?.freshnessExpiresAt" /></template></a-table-column>
          </template>
        </a-table>
        <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total @change="loadMarket" />
      </section>
    </section>

    <a-drawer :visible="Boolean(detail) || detailLoading" width="min(1280px, calc(100vw - 32px))" :footer="false" unmount-on-close class="eve-market-page__drawer" @update:visible="(visible) => { if (!visible) detail = undefined }">
      <template #title>吉他市场详情</template>
      <a-spin :loading="detailLoading" class="eve-market-page__detail-loading">
        <template v-if="detail">
          <section class="eve-market-page__detail-header">
            <div class="eve-market-page__detail-identity"><EveTypeIcon :type-id="detail.type.typeId" :size="56" :alt="`${detail.type.typeName}图标`" /><div><h2>{{ detail.type.typeName }}</h2><p>{{ categoryPath(detail.type) }}</p></div></div>
            <a-button v-if="canSync" type="primary" :loading="syncing" @click="syncDetail"><template #icon><icon-refresh /></template>刷新行情</a-button>
          </section>
          <a-alert v-if="detail.upstreamMessage" type="warning" :show-icon="true" class="eve-market-page__cache-warning">{{ detail.upstreamMessage }}</a-alert>
          <section class="eve-market-page__quote-grid">
            <article><span>最高求购</span><strong class="eve-market-page__buy">{{ formatPrice(detail.quote?.highestBuyPrice) }}</strong><small>总量 {{ formatVolume(detail.quote?.buyVolume) }}</small></article>
            <article><span>最低卖出</span><strong class="eve-market-page__sell">{{ formatPrice(detail.quote?.lowestSellPrice) }}</strong><small>总量 {{ formatVolume(detail.quote?.sellVolume) }}</small></article>
            <article><span>行情时间</span><strong>{{ formatTime(detail.quote?.synchronizedAt) }}</strong><MarketPriceFreshness :synchronized-at="detail.quote?.synchronizedAt" :freshness-expires-at="detail.quote?.freshnessExpiresAt" /><small>{{ detail.servedFromCache ? '展示最近缓存' : '已读取服务器缓存' }}</small></article>
          </section>
          <a-tabs default-active-key="orders" class="eve-market-page__tabs">
            <a-tab-pane key="orders" title="市场订单">
              <section class="eve-market-page__order-grid">
                <article><h3>买单 <small>高价优先</small></h3><a-table :data="detail.buyOrders" :pagination="false" size="small" :scroll="{ x: 520, y: 360 }"><template #columns><a-table-column title="价格" :width="190" align="right"><template #cell="{ record }"><strong class="eve-market-page__price eve-market-page__buy">{{ formatPrice(record.price) }}</strong></template></a-table-column><a-table-column title="数量" :width="112" align="right"><template #cell="{ record }"><span class="eve-market-page__price">{{ formatVolume(record.volume) }}</span></template></a-table-column><a-table-column title="空间站" :min-width="190" ellipsis tooltip><template #cell="{ record }">{{ record.stationName || '未知空间站' }}</template></a-table-column></template></a-table><a-empty v-if="!detail.buyOrders.length" description="暂无可用买单" /></article>
                <article><h3>卖单 <small>低价优先</small></h3><a-table :data="detail.sellOrders" :pagination="false" size="small" :scroll="{ x: 520, y: 360 }"><template #columns><a-table-column title="价格" :width="190" align="right"><template #cell="{ record }"><strong class="eve-market-page__price eve-market-page__sell">{{ formatPrice(record.price) }}</strong></template></a-table-column><a-table-column title="数量" :width="112" align="right"><template #cell="{ record }"><span class="eve-market-page__price">{{ formatVolume(record.volume) }}</span></template></a-table-column><a-table-column title="空间站" :min-width="190" ellipsis tooltip><template #cell="{ record }">{{ record.stationName || '未知空间站' }}</template></a-table-column></template></a-table><a-empty v-if="!detail.sellOrders.length" description="暂无可用卖单" /></article>
              </section>
            </a-tab-pane>
            <a-tab-pane key="history" title="价格历史">
              <section class="eve-market-page__history"><div class="eve-market-page__history-heading"><div><h3>每日价格趋势</h3><p>显示最近 {{ detailHistory.length }} 天收盘价与成交量，可拖拽或滚动缩放时间轴。</p></div><small>数据仅供参考</small></div><Chart v-if="detailHistory.length" :option="historyChartOption" height="390px" /><a-empty v-else :description="detail.historySynchronizedAt ? '该物品暂无历史成交数据' : '价格历史正在由后台补齐'" /></section>
            </a-tab-pane>
            <a-tab-pane key="reference" title="物品资料"><a-descriptions :column="1" bordered><a-descriptions-item label="市场分类">{{ categoryPath(detail.type) }}</a-descriptions-item><a-descriptions-item label="物品说明"><p class="eve-market-page__description">{{ detail.type.typeDescription || '暂无说明' }}</p></a-descriptions-item></a-descriptions></a-tab-pane>
          </a-tabs>
        </template>
      </a-spin>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-market-page { display: grid; gap: 12px; color: var(--color-text-1); min-width: 0; }
.eve-market-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .2); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 58%), var(--color-bg-1); }.eve-market-page__eyebrow, .eve-market-page__panel-heading span, .eve-market-page__items-heading span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; font-weight: 700; letter-spacing: .14em; }.eve-market-page h1 { margin: 5px 0; font-size: 26px; }.eve-market-page__header p { max-width: 760px; margin: 0; color: var(--color-text-3); line-height: 1.6; }.eve-market-page__header-note { display: inline-flex; flex: none; align-items: center; gap: 6px; color: var(--color-text-2); font-size: 13px; white-space: nowrap; }
.eve-market-page__workspace { display: grid; grid-template-columns: minmax(220px, 272px) minmax(0, 1fr); gap: 12px; align-items: start; }.eve-market-page__category-panel, .eve-market-page__items-panel { overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }.eve-market-page__panel-heading, .eve-market-page__items-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 13px 14px 10px; border-bottom: 1px solid var(--color-border-2); }.eve-market-page__panel-heading h2, .eve-market-page__items-heading h2 { margin: 4px 0 0; font-size: 18px; }.eve-market-page__items-heading small { color: var(--color-text-3); white-space: nowrap; }.eve-market-page__tree-actions { display: flex; gap: 8px; padding: 11px 14px 4px; }.eve-market-page__tree-loading { display: block; min-height: 500px; max-height: calc(100vh - 320px); overflow: auto; padding: 8px 10px 18px; }.eve-market-page__category-node { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.eve-market-page__items-panel { min-width: 0; }.eve-market-page__filter-bar { display: flex; align-items: center; gap: 10px; margin: 10px 14px; }.eve-market-page__filter-bar .arco-input-wrapper { width: min(400px, 100%); }.eve-market-page__table :deep(.arco-table) { border-top: 1px solid var(--color-border-2); }.eve-market-page__table :deep(.arco-table-tr) { cursor: pointer; }.eve-market-page__table :deep(.arco-table-tr:hover .arco-table-td) { background: var(--color-fill-2); }.eve-market-page__items-panel :deep(.arco-pagination) { justify-content: flex-end; margin: 12px 14px; }.eve-market-page__type { display: flex; align-items: center; gap: 9px; min-width: 0; }.eve-market-page__type > div { min-width: 0; }.eve-market-page__type strong, .eve-market-page__type small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.eve-market-page__type small { margin-top: 3px; color: var(--color-text-4); font-size: 11px; }.eve-market-page__price { display: inline-block; font-variant-numeric: tabular-nums; white-space: nowrap; }.eve-market-page__buy { color: rgb(var(--success-6)); }.eve-market-page__sell { color: rgb(var(--danger-6)); }.eve-market-page__updated { color: var(--color-text-3); font-size: 12px; }.eve-market-page__updated--stale { color: rgb(var(--warning-6)); }
.eve-market-page__detail-loading { display: block; min-height: 180px; }.eve-market-page__detail-header { display: flex; align-items: center; justify-content: space-between; gap: 14px; }.eve-market-page__detail-identity { display: flex; align-items: center; gap: 13px; min-width: 0; }.eve-market-page__detail-identity h2 { margin: 0; overflow: hidden; font-size: 21px; text-overflow: ellipsis; white-space: nowrap; }.eve-market-page__detail-identity p { margin: 5px 0 0; overflow: hidden; color: var(--color-text-3); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.eve-market-page__cache-warning { margin-top: 14px; }.eve-market-page__quote-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 16px 0; }.eve-market-page__quote-grid article { display: grid; gap: 5px; padding: 13px; border: 1px solid var(--color-border-2); border-radius: 10px; background: var(--color-fill-1); }.eve-market-page__quote-grid span, .eve-market-page__quote-grid small { color: var(--color-text-3); font-size: 12px; }.eve-market-page__quote-grid strong { font-size: 18px; white-space: nowrap; }.eve-market-page__tabs { margin-top: 6px; }.eve-market-page__order-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }.eve-market-page__order-grid article { min-width: 0; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 10px; }.eve-market-page__order-grid h3 { display: flex; align-items: baseline; gap: 7px; margin: 0; padding: 12px; border-bottom: 1px solid var(--color-border-2); font-size: 15px; }.eve-market-page__order-grid h3 small { color: var(--color-text-3); font-size: 11px; font-weight: 400; }.eve-market-page__order-grid :deep(.arco-empty) { padding: 28px 12px; }.eve-market-page__history { padding: 12px; border: 1px solid var(--color-border-2); border-radius: 10px; }.eve-market-page__history-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }.eve-market-page__history-heading h3 { margin: 0; font-size: 16px; }.eve-market-page__history-heading p, .eve-market-page__history-heading small { margin: 5px 0 12px; color: var(--color-text-3); font-size: 12px; }.eve-market-page__history-heading small { white-space: nowrap; }.eve-market-page__description { margin: 0; color: var(--color-text-2); line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 960px) { .eve-market-page__header { align-items: flex-start; flex-direction: column; }.eve-market-page__workspace { grid-template-columns: 1fr; }.eve-market-page__tree-loading { min-height: 180px; max-height: 360px; }.eve-market-page__filter-bar { flex-wrap: wrap; }.eve-market-page__filter-bar .arco-input-wrapper { width: 100%; } }

@media (max-width: 680px) { .eve-market-page__header { padding: 18px; }.eve-market-page__header-note { white-space: normal; }.eve-market-page__quote-grid, .eve-market-page__order-grid { grid-template-columns: 1fr; }.eve-market-page__detail-header { align-items: flex-start; flex-direction: column; }.eve-market-page__detail-header .arco-btn { width: 100%; }.eve-market-page__history-heading { flex-direction: column; }.eve-market-page__history-heading small { margin-top: -8px; } }
</style>
