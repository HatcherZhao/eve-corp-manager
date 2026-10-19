<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { useWindowSize } from '@vueuse/core'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import IndustryPageHeader from '../components/IndustryPageHeader.vue'
import IndustryPanel from '../components/IndustryPanel.vue'
import {
  formatIndustryIsk,
  formatIndustryPercent,
  industryRiskLabel,
  industryStatusColor,
  industryStatusLabel,
} from '../shared/format'
import {
  type EveIndustryConfig,
  type EveIndustryDataStatus,
  type EveIndustryFacility,
  type EveIndustryOpportunity,
  type EveIndustryOpportunityMaterial,
  createEveIndustryPlan,
  getEveIndustryConfigs,
  getEveIndustryDataStatus,
  getEveIndustryFacilities,
  getEveIndustryOpportunities,
  getEveIndustryOpportunityMaterials,
  scanEveIndustryOpportunities,
} from '@/apis/eve'

defineOptions({ name: 'EveIndustryOpportunities' })

const router = useRouter()
const { width } = useWindowSize()
const loading = ref(false)
const scanning = ref(false)
const records = ref<EveIndustryOpportunity[]>([])
const configs = ref<EveIndustryConfig[]>([])
const facilities = ref<EveIndustryFacility[]>([])
const status = ref<EveIndustryDataStatus>()
const selected = ref<EveIndustryOpportunity>()
const materials = ref<EveIndustryOpportunityMaterial[]>([])
const detailVisible = ref(false)
const scan = reactive({ configId: '', facilityId: '', activity: '', limit: 100 })

const executableCount = computed(() => records.value.filter((item) => item.status === 'EXECUTABLE').length)
const riskCount = computed(() => records.value.filter((item) => item.riskTags.length > 0).length)
const bestOpportunity = computed(() => records.value[0])
const detailDrawerWidth = computed(() => width.value < 768 ? '100%' : 500)
const creatingPlan = ref(false)

/** 同时读取扫描上下文和最近结果，保证筛选区与列表使用同一份配置。 */
async function loadPage() {
  loading.value = true
  try {
    const [opportunityResult, configResult, facilityResult, statusResult] = await Promise.all([
      getEveIndustryOpportunities(100),
      getEveIndustryConfigs(),
      getEveIndustryFacilities(),
      getEveIndustryDataStatus(),
    ])
    records.value = opportunityResult.data
    configs.value = configResult.data
    facilities.value = facilityResult.data
    status.value = statusResult.data
    if (!scan.configId && configs.value.length) scan.configId = configs.value[0].id
    if (!scan.facilityId && facilities.value.length) scan.facilityId = facilities.value[0].id
  } finally {
    loading.value = false
  }
}

/** 使用已保存的生产条件刷新机会快照。 */
async function runScan() {
  if (!scan.configId || !scan.facilityId) {
    Message.warning('请先在工业配置中保存生产配置和生产设施')
    return
  }
  scanning.value = true
  try {
    records.value = (await scanEveIndustryOpportunities({
      ...scan,
      activity: scan.activity || undefined,
    })).data
    Message.success('工业机会扫描完成')
  } finally {
    scanning.value = false
  }
}

/** 打开机会快照采用的材料采购明细。 */
async function openOpportunity(record: EveIndustryOpportunity) {
  selected.value = record
  materials.value = (await getEveIndustryOpportunityMaterials(record.id)).data
  detailVisible.value = true
}

/** 仅允许可执行机会固化为生产计划，计划引用当前机会快照。 */
async function addToPlan() {
  if (!selected.value || selected.value.status !== 'EXECUTABLE') return
  creatingPlan.value = true
  try {
    await createEveIndustryPlan({
      opportunityId: selected.value.id,
      name: `${selected.value.productName} · 生产计划`,
      runs: selected.value.recommendedRuns,
    })
    detailVisible.value = false
    Message.success('已创建生产计划草稿')
    router.push('/eve/industry/plans')
  } finally {
    creatingPlan.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <main class="industry-opportunities-page gi_page">
    <IndustryPageHeader
      eyebrow="SERENITY · INDUSTRY OPPORTUNITY"
      title="工业机会分析"
      description="用国服吉他海四订单、市场成交量和生产设施条件，筛选利润高且市场能够承接的制造机会。"
    >
      <template #actions>
        <a-button @click="router.push('/eve/industry/calculator')">蓝图计算</a-button>
        <a-button @click="router.push('/eve/industry/config')">工业配置</a-button>
        <a-button type="primary" :loading="scanning" @click="runScan">
          <template #icon><icon-search /></template>
          扫描机会
        </a-button>
      </template>
    </IndustryPageHeader>

    <a-alert class="industry-opportunities-page__notice" type="info" show-icon>
      当前固定按无库存、材料全部从吉他海四购买、运输成本为 0 计算。蓝图、行情或 SCI 缺失时，结果会降级为可试算。
    </a-alert>

    <section class="industry-opportunities-page__summary" aria-label="工业机会摘要">
      <article>
        <span>可执行机会</span>
        <strong>{{ executableCount }}</strong>
        <small>当前扫描结果</small>
      </article>
      <article>
        <span>最佳每日利润</span>
        <strong>{{ formatIndustryIsk(bestOpportunity?.dailyProfit) }}</strong>
        <small>{{ bestOpportunity?.productName || '等待扫描' }}</small>
      </article>
      <article>
        <span>已校验配方</span>
        <strong>{{ status?.validatedBlueprintCount ?? 0 }}</strong>
        <small>共 {{ status?.blueprintCount ?? 0 }} 条活动</small>
      </article>
      <article>
        <span>风险机会</span>
        <strong>{{ riskCount }}</strong>
        <small>订单、成交量或数据缺口</small>
      </article>
    </section>

    <IndustryPanel class="industry-opportunities-page__filters" title="扫描条件" description="生产配置与设施会共同影响材料、时间、SCI 和费用。">
      <div class="industry-opportunities-page__filter-grid">
        <a-select v-model="scan.configId" placeholder="选择生产配置" allow-clear>
          <a-option v-for="item in configs" :key="item.id" :value="item.id">{{ item.name }}</a-option>
        </a-select>
        <a-select v-model="scan.facilityId" placeholder="选择生产设施" allow-clear>
          <a-option v-for="item in facilities" :key="item.id" :value="item.id">
            {{ item.name }} · {{ item.systemName || item.systemId }}
          </a-option>
        </a-select>
        <a-select v-model="scan.activity" placeholder="全部工业活动" allow-clear>
          <a-option value="MANUFACTURING">制造</a-option>
          <a-option value="REACTION">反应</a-option>
          <a-option value="INVENTION">发明</a-option>
        </a-select>
        <div class="industry-opportunities-page__data-note">{{ status?.message || '正在读取工业数据状态' }}</div>
      </div>
    </IndustryPanel>

    <IndustryPanel
      class="industry-opportunities-page__results"
      title="生产机会"
      description="结果按每日可实现利润排序；点击记录查看材料、费用和风险依据。"
      flush
    >
      <template #actions>
        <span class="industry-opportunities-page__updated-at">
          更新于 {{ records[0]?.calculatedAt ? dayjs(records[0].calculatedAt).format('MM-DD HH:mm') : '—' }}
        </span>
      </template>

      <div class="industry-opportunities-page__desktop-table">
        <a-table
          :data="records" :loading="loading" :pagination="false" row-key="id"
          :scroll="{ x: 1260 }" @row-click="openOpportunity"
        >
          <template #columns>
            <a-table-column title="产品 / 蓝图" :width="240" fixed="left" ellipsis tooltip>
              <template #cell="{ record }">
                <div class="industry-opportunities-page__identity">
                  <strong>{{ record.productName }}</strong>
                  <small>{{ record.blueprintName || '蓝图待导入' }} · {{ record.activity || '—' }}</small>
                </div>
              </template>
            </a-table-column>
            <a-table-column title="生产地点" :width="180" ellipsis tooltip>
              <template #cell="{ record }"><div>{{ record.facilityName || '—' }}</div><small>{{ record.systemName || '—' }}</small></template>
            </a-table-column>
            <a-table-column title="状态" :width="100">
              <template #cell="{ record }"><a-tag :color="industryStatusColor(record.status)">{{ industryStatusLabel(record.status) }}</a-tag></template>
            </a-table-column>
            <a-table-column title="推荐产量" :width="110" align="right"><template #cell="{ record }">{{ record.recommendedQuantity.toLocaleString() }}</template></a-table-column>
            <a-table-column title="总投入" :width="160" align="right"><template #cell="{ record }">{{ formatIndustryIsk(record.materialCost + record.installCost) }}</template></a-table-column>
            <a-table-column title="立即利润" :width="150" align="right"><template #cell="{ record }"><strong class="industry-opportunities-page__profit">{{ formatIndustryIsk(record.immediateProfit) }}</strong></template></a-table-column>
            <a-table-column title="挂单利润" :width="150" align="right"><template #cell="{ record }">{{ formatIndustryIsk(record.sellOrderProfit) }}</template></a-table-column>
            <a-table-column title="利润率" :width="105" align="right"><template #cell="{ record }">{{ formatIndustryPercent(record.margin) }}</template></a-table-column>
            <a-table-column title="风险" :width="220" ellipsis tooltip><template #cell="{ record }"><span :class="record.riskScore > 40 ? 'risk-high' : 'risk-normal'">{{ industryRiskLabel(record.riskTags) }}</span></template></a-table-column>
          </template>
        </a-table>
      </div>

      <div class="industry-opportunities-page__mobile-list">
        <button v-for="record in records" :key="record.id" type="button" class="industry-opportunity-card" @click="openOpportunity(record)">
          <div class="industry-opportunity-card__header">
            <div><strong>{{ record.productName }}</strong><small>{{ record.blueprintName || record.activity || '蓝图待导入' }}</small></div>
            <a-tag :color="industryStatusColor(record.status)">{{ industryStatusLabel(record.status) }}</a-tag>
          </div>
          <dl>
            <div><dt>每日利润</dt><dd>{{ formatIndustryIsk(record.dailyProfit) }}</dd></div>
            <div><dt>立即利润</dt><dd>{{ formatIndustryIsk(record.immediateProfit) }}</dd></div>
            <div><dt>总投入</dt><dd>{{ formatIndustryIsk(record.materialCost + record.installCost) }}</dd></div>
            <div><dt>推荐产量</dt><dd>{{ record.recommendedQuantity.toLocaleString() }}</dd></div>
          </dl>
          <p>{{ industryRiskLabel(record.riskTags) }}</p>
        </button>
      </div>

      <a-empty v-if="!loading && !records.length" description="暂无扫描结果">
        <a-space wrap>
          <a-button @click="router.push('/eve/industry/config')">配置生产条件</a-button>
          <a-button @click="router.push('/eve/industry/data')">检查工业数据</a-button>
          <a-button type="primary" :loading="scanning" @click="runScan">运行扫描</a-button>
        </a-space>
      </a-empty>
    </IndustryPanel>

    <a-drawer v-model:visible="detailVisible" :width="detailDrawerWidth" title="机会详情" unmount-on-close>
      <template v-if="selected">
        <div class="industry-opportunity-detail__headline">
          <div><span>{{ selected.activity || '工业活动' }}</span><h2>{{ selected.productName }}</h2></div>
          <a-tag :color="industryStatusColor(selected.status)">{{ industryStatusLabel(selected.status) }}</a-tag>
        </div>
        <section class="industry-profit-waterfall" aria-label="利润构成">
          <article><span>挂单收入</span><strong>{{ formatIndustryIsk(selected.sellOrderRevenue) }}</strong></article>
          <article><span>材料采购</span><strong>- {{ formatIndustryIsk(selected.materialCost) }}</strong></article>
          <article><span>安装费用</span><strong>- {{ formatIndustryIsk(selected.installCost) }}</strong></article>
          <article class="industry-profit-waterfall__result"><span>预计净利润</span><strong>{{ formatIndustryIsk(selected.sellOrderProfit) }}</strong></article>
        </section>
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="生产地点">{{ selected.systemName || '—' }} · {{ selected.facilityName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="推荐流程 / 数量">{{ selected.recommendedRuns.toLocaleString() }} / {{ selected.recommendedQuantity.toLocaleString() }}</a-descriptions-item>
          <a-descriptions-item label="立即变现利润">{{ formatIndustryIsk(selected.immediateProfit) }}</a-descriptions-item>
          <a-descriptions-item label="每小时利润">{{ formatIndustryIsk(selected.hourlyProfit) }}</a-descriptions-item>
          <a-descriptions-item label="每日利润">{{ formatIndustryIsk(selected.dailyProfit) }}</a-descriptions-item>
          <a-descriptions-item label="风险说明">{{ industryRiskLabel(selected.riskTags) }}</a-descriptions-item>
        </a-descriptions>
        <a-alert v-if="selected.status !== 'EXECUTABLE'" class="industry-opportunity-detail__plan-note" type="warning" show-icon>
          当前机会不是可执行状态，完成蓝图、行情或设施校验后才能加入生产计划。
        </a-alert>
        <a-button v-else class="industry-opportunity-detail__plan-action" type="primary" long :loading="creatingPlan" @click="addToPlan">加入生产计划</a-button>
        <div v-if="materials.length" class="industry-opportunity-detail__materials">
          <h3>材料采购明细</h3>
          <div class="industry-opportunity-detail__materials-table">
            <a-table :data="materials" :pagination="false" size="small" :scroll="{ x: 430 }">
              <template #columns>
                <a-table-column title="材料" data-index="materialName" :width="170" />
                <a-table-column title="数量" data-index="requiredQuantity" :width="100" align="right" />
                <a-table-column title="采购成本" :width="150" align="right"><template #cell="{ record }">{{ formatIndustryIsk(record.purchaseCost) }}</template></a-table-column>
              </template>
            </a-table>
          </div>
        </div>
      </template>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.industry-opportunities-page {
  min-width: 0;
  overflow-x: clip;
  color: var(--color-text-1);
}

.industry-opportunities-page__notice,
.industry-opportunities-page__summary,
.industry-opportunities-page__filters,
.industry-opportunities-page__results {
  margin-top: .75rem;
}

.industry-opportunities-page__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 14px;
  background: var(--color-border-2);
}

.industry-opportunities-page__summary article {
  min-width: 0;
  padding: .875rem;
  background: var(--color-bg-1);
}

.industry-opportunities-page__summary span,
.industry-opportunities-page__summary small {
  display: block;
  color: var(--color-text-3);
  font-size: .75rem;
}

.industry-opportunities-page__summary strong {
  display: block;
  margin: .25rem 0;
  overflow: hidden;
  font-size: 1.2rem;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.industry-opportunities-page__filter-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: .5rem;
}

.industry-opportunities-page__data-note {
  min-width: 0;
  padding: .55rem .75rem;
  border-radius: 8px;
  background: var(--color-fill-1);
  color: var(--color-text-3);
  font-size: .75rem;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.industry-opportunities-page__updated-at {
  color: var(--color-text-3);
  font-size: .75rem;
}

.industry-opportunities-page__desktop-table {
  display: none;
  min-width: 0;
  overflow-x: auto;
}

.industry-opportunities-page__mobile-list {
  display: grid;
  gap: .625rem;
  padding: .75rem;
}

.industry-opportunity-card {
  width: 100%;
  min-width: 0;
  padding: .875rem;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-1);
  color: var(--color-text-1);
  text-align: left;
  transition: border-color 160ms cubic-bezier(.23, 1, .32, 1), background-color 160ms cubic-bezier(.23, 1, .32, 1);
}

.industry-opportunity-card:hover,
.industry-opportunity-card:focus-visible {
  border-color: rgba(var(--arcoblue-6), .45);
  background: rgba(var(--arcoblue-6), .035);
  outline: none;
}

.industry-opportunity-card:active {
  transform: scale(.99);
}

.industry-opportunity-card__header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: .75rem;
}

.industry-opportunity-card__header > div {
  min-width: 0;
}

.industry-opportunity-card__header strong,
.industry-opportunity-card__header small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.industry-opportunity-card__header small {
  margin-top: .2rem;
  color: var(--color-text-3);
}

.industry-opportunity-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: .625rem;
  margin: .875rem 0 0;
}

.industry-opportunity-card dt {
  color: var(--color-text-3);
  font-size: .6875rem;
}

.industry-opportunity-card dd {
  margin: .15rem 0 0;
  font-size: .8125rem;
  font-variant-numeric: tabular-nums;
}

.industry-opportunity-card p {
  margin: .75rem 0 0;
  color: var(--color-text-3);
  font-size: .75rem;
  line-height: 1.5;
}

.industry-opportunities-page__identity strong,
.industry-opportunities-page__identity small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.industry-opportunities-page__identity small,
.industry-opportunities-page__desktop-table small {
  color: var(--color-text-3);
}

.industry-opportunities-page__profit {
  color: rgb(var(--green-6));
  font-variant-numeric: tabular-nums;
}

.risk-high { color: rgb(var(--red-6)); }
.risk-normal { color: var(--color-text-3); }

.industry-opportunity-detail__headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.industry-opportunity-detail__headline span {
  color: var(--color-text-3);
  font-size: .75rem;
}

.industry-opportunity-detail__headline h2 {
  margin: .2rem 0 0;
  font-size: 1.25rem;
}

.industry-profit-waterfall {
  display: grid;
  gap: 1px;
  margin-bottom: 1rem;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-border-2);
}

.industry-profit-waterfall article {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: .75rem;
  background: var(--color-bg-1);
  font-variant-numeric: tabular-nums;
}

.industry-profit-waterfall span { color: var(--color-text-3); }
.industry-profit-waterfall__result strong { color: rgb(var(--green-6)); }

.industry-opportunity-detail__materials {
  margin-top: 1rem;
}

.industry-opportunity-detail__materials h3 {
  margin: 0 0 .5rem;
  font-size: .95rem;
}

.industry-opportunity-detail__materials-table {
  max-width: 100%;
  overflow-x: auto;
}

@media (min-width: 768px) {
  .industry-opportunities-page__summary {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .industry-opportunities-page__summary article {
    padding: 1rem;
  }

  .industry-opportunities-page__filter-grid {
    grid-template-columns: repeat(3, minmax(12rem, 1fr));
  }

  .industry-opportunities-page__data-note {
    grid-column: 1 / -1;
  }

  .industry-opportunities-page__desktop-table {
    display: block;
  }

  .industry-opportunities-page__mobile-list {
    display: none;
  }
}

@media (min-width: 1200px) {
  .industry-opportunities-page__filter-grid {
    grid-template-columns: repeat(3, minmax(12rem, 16rem)) minmax(18rem, 1fr);
  }

  .industry-opportunities-page__data-note {
    grid-column: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .industry-opportunity-card {
    transition: none;
  }
}
</style>
