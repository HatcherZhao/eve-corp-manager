<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import IndustryPageHeader from '../components/IndustryPageHeader.vue'
import IndustryPanel from '../components/IndustryPanel.vue'
import { formatIndustryIsk } from '../shared/format'
import {
  type EveIndustryBlueprint,
  type EveIndustryBlueprintOverride,
  type EveIndustryCalculation,
  type EveIndustryConfig,
  type EveIndustryFacility,
  calculateEveIndustryBlueprint,
  getEveIndustryBlueprintOverrides,
  getEveIndustryBlueprints,
  getEveIndustryConfigs,
  getEveIndustryFacilities,
} from '@/apis/eve'

defineOptions({ name: 'EveIndustryCalculator' })

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const calculating = ref(false)
const blueprints = ref<EveIndustryBlueprint[]>([])
const configs = ref<EveIndustryConfig[]>([])
const facilities = ref<EveIndustryFacility[]>([])
const overrides = ref<EveIndustryBlueprintOverride[]>([])
const calculation = ref<EveIndustryCalculation>()
const form = reactive({
  blueprintId: '',
  configId: '',
  facilityId: '',
  runs: 1,
  materialEfficiency: 10,
  timeEfficiency: 5,
})

const selectedBlueprint = computed(() => blueprints.value.find((item) => item.id === form.blueprintId))

/** 读取蓝图、设施和研究等级，确保主动试算与机会扫描采用同一套配置。 */
async function loadPage() {
  loading.value = true
  try {
    const [blueprintResult, configResult, facilityResult, overrideResult] = await Promise.all([
      getEveIndustryBlueprints(),
      getEveIndustryConfigs(),
      getEveIndustryFacilities(),
      getEveIndustryBlueprintOverrides(),
    ])
    blueprints.value = blueprintResult.data
    configs.value = configResult.data
    facilities.value = facilityResult.data
    overrides.value = overrideResult.data
    const queryBlueprintId = typeof route.query.blueprintId === 'string' ? route.query.blueprintId : ''
    form.blueprintId = blueprints.value.some((item) => item.id === queryBlueprintId)
      ? queryBlueprintId
      : blueprints.value[0]?.id || ''
    form.configId = configs.value[0]?.id || ''
    form.facilityId = facilities.value[0]?.id || ''
    applyBlueprintOverride()
  } finally {
    loading.value = false
  }
}

/** 把已保存的 ME/TE 自动带入试算条件。 */
function applyBlueprintOverride() {
  const blueprint = selectedBlueprint.value
  const override = blueprint && overrides.value.find((item) => item.blueprintTypeId === blueprint.blueprintTypeId)
  if (!override) return
  form.materialEfficiency = override.materialEfficiency
  form.timeEfficiency = override.timeEfficiency
}

watch(() => form.blueprintId, applyBlueprintOverride)

/** 计算当前蓝图的材料采购、安装费、工时和两种销售利润。 */
async function calculate() {
  if (!form.blueprintId || !form.configId || !form.facilityId) {
    Message.warning('请选择蓝图、生产配置和生产设施')
    return
  }
  calculating.value = true
  try {
    calculation.value = (await calculateEveIndustryBlueprint({ ...form })).data
  } finally {
    calculating.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <main class="industry-calculator-page gi_page">
    <IndustryPageHeader
      eyebrow="BLUEPRINT · COST WORKBENCH"
      title="蓝图计算"
      description="选择蓝图、研究等级和生产设施，查看实际材料采购、活动工时、安装费以及买单与挂单利润。"
    >
      <template #actions>
        <a-button @click="router.push('/eve/industry/opportunities')">返回机会分析</a-button>
        <a-button @click="router.push('/eve/industry/config')">维护配置</a-button>
      </template>
    </IndustryPageHeader>

    <a-alert class="industry-calculator-page__notice" type="info" show-icon>
      蓝图本体成本和运输成本均按 0 处理；所有材料视为无库存并从吉他海四订单簿逐档购买。
    </a-alert>

    <div class="industry-calculator-page__workspace">
      <IndustryPanel class="industry-calculator-page__conditions" title="试算条件" description="流程数代表蓝图活动执行次数，产物数量由蓝图单流程产量自动换算。">
        <a-spin :loading="loading" class="industry-calculator-page__spin">
          <a-form :model="form" layout="vertical">
            <a-form-item label="蓝图">
              <a-select v-model="form.blueprintId" allow-search>
                <a-option v-for="item in blueprints" :key="item.id" :value="item.id">
                  {{ item.productName }} · {{ item.activity }} · {{ item.blueprintName }}
                </a-option>
              </a-select>
            </a-form-item>
            <a-form-item label="生产配置">
              <a-select v-model="form.configId">
                <a-option v-for="item in configs" :key="item.id" :value="item.id">{{ item.name }}</a-option>
              </a-select>
            </a-form-item>
            <a-form-item label="生产设施">
              <a-select v-model="form.facilityId">
                <a-option v-for="item in facilities" :key="item.id" :value="item.id">
                  {{ item.name }} · {{ item.systemName || item.systemId }}
                </a-option>
              </a-select>
            </a-form-item>
            <div class="industry-calculator-page__number-grid">
              <a-form-item label="流程数"><a-input-number v-model="form.runs" :min="1" :max="1000000" /></a-form-item>
              <a-form-item label="ME"><a-input-number v-model="form.materialEfficiency" :min="0" :max="10" /></a-form-item>
              <a-form-item label="TE"><a-input-number v-model="form.timeEfficiency" :min="0" :max="20" /></a-form-item>
            </div>
            <div v-if="selectedBlueprint" class="industry-calculator-page__blueprint-note">
              <span>每流程产物</span>
              <strong>{{ selectedBlueprint.productQuantity.toLocaleString() }} 件</strong>
              <small>基础时间 {{ selectedBlueprint.baseTimeSeconds.toLocaleString() }} 秒</small>
            </div>
            <a-button long type="primary" size="large" :loading="calculating" @click="calculate">开始计算</a-button>
          </a-form>
        </a-spin>
      </IndustryPanel>

      <IndustryPanel class="industry-calculator-page__result" title="试算结果" description="金额和订单深度均来自本次计算所采用的吉他行情快照。">
        <a-empty v-if="!calculation" description="选择条件后开始计算，即可查看成本与利润" />
        <template v-else>
          <a-alert v-if="calculation.warnings.length" type="warning" show-icon>
            {{ calculation.warnings.join('；') }}
          </a-alert>
          <section class="industry-calculator-page__summary" aria-label="蓝图试算摘要">
            <article><span>活动时间</span><strong>{{ (calculation.durationSeconds / 60).toFixed(1) }} 分钟</strong></article>
            <article><span>总投入</span><strong>{{ formatIndustryIsk(calculation.totalCost) }}</strong></article>
            <article><span>立即利润</span><strong>{{ formatIndustryIsk(calculation.immediateProfit) }}</strong></article>
            <article><span>挂单利润</span><strong>{{ formatIndustryIsk(calculation.sellOrderProfit) }}</strong></article>
          </section>
          <section class="industry-calculator-page__cost-breakdown" aria-label="费用拆分">
            <div><span>材料采购</span><strong>{{ formatIndustryIsk(calculation.materialCost) }}</strong></div>
            <div><span>安装费用</span><strong>{{ formatIndustryIsk(calculation.installationCost) }}</strong></div>
            <div><span>立即收入</span><strong>{{ formatIndustryIsk(calculation.immediateRevenue) }}</strong></div>
            <div><span>挂单收入</span><strong>{{ formatIndustryIsk(calculation.sellOrderRevenue) }}</strong></div>
          </section>
          <div class="industry-calculator-page__materials-table">
            <a-table :data="calculation.materials" :pagination="false" size="small" :scroll="{ x: 680 }">
              <template #columns>
                <a-table-column title="材料" data-index="name" :width="190" />
                <a-table-column title="需求数量" data-index="quantity" :width="120" align="right" />
                <a-table-column title="成交均价" :width="150" align="right"><template #cell="{ record }">{{ formatIndustryIsk(record.averagePrice) }}</template></a-table-column>
                <a-table-column title="逐档采购成本" :width="170" align="right"><template #cell="{ record }">{{ formatIndustryIsk(record.purchaseCost) }}</template></a-table-column>
                <a-table-column title="订单深度" :width="100"><template #cell="{ record }"><a-tag :color="record.marketComplete ? 'green' : 'red'">{{ record.marketComplete ? '充足' : '不足' }}</a-tag></template></a-table-column>
              </template>
            </a-table>
          </div>
        </template>
      </IndustryPanel>
    </div>
  </main>
</template>

<style scoped lang="scss">
.industry-calculator-page {
  min-width: 0;
  overflow-x: clip;
  color: var(--color-text-1);
}

.industry-calculator-page__notice,
.industry-calculator-page__workspace {
  margin-top: .75rem;
}

.industry-calculator-page__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: .75rem;
  align-items: start;
}

.industry-calculator-page__spin {
  display: block;
  min-height: 16rem;
}

.industry-calculator-page__number-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: .5rem;
}

.industry-calculator-page__number-grid :deep(.arco-input-number) {
  width: 100%;
}

.industry-calculator-page__blueprint-note {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: .2rem .75rem;
  margin-bottom: 1rem;
  padding: .75rem;
  border-radius: 8px;
  background: var(--color-fill-1);
}

.industry-calculator-page__blueprint-note span,
.industry-calculator-page__blueprint-note small {
  color: var(--color-text-3);
  font-size: .75rem;
}

.industry-calculator-page__blueprint-note strong {
  font-variant-numeric: tabular-nums;
}

.industry-calculator-page__blueprint-note small {
  grid-column: 1 / -1;
}

.industry-calculator-page__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: .5rem;
  margin: 1rem 0;
}

.industry-calculator-page__summary article {
  min-width: 0;
  padding: .75rem;
  border: 1px solid var(--color-border-2);
  border-radius: 9px;
  background: var(--color-bg-1);
}

.industry-calculator-page__summary span,
.industry-calculator-page__summary strong {
  display: block;
}

.industry-calculator-page__summary span {
  color: var(--color-text-3);
  font-size: .75rem;
}

.industry-calculator-page__summary strong {
  margin-top: .25rem;
  overflow: hidden;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.industry-calculator-page__cost-breakdown {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1px;
  margin-bottom: 1rem;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 9px;
  background: var(--color-border-2);
}

.industry-calculator-page__cost-breakdown div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: .7rem .75rem;
  background: var(--color-bg-1);
}

.industry-calculator-page__cost-breakdown span {
  color: var(--color-text-3);
}

.industry-calculator-page__cost-breakdown strong {
  font-variant-numeric: tabular-nums;
}

.industry-calculator-page__materials-table {
  max-width: 100%;
  overflow-x: auto;
}

@media (min-width: 768px) {
  .industry-calculator-page__summary {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .industry-calculator-page__cost-breakdown {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1100px) {
  .industry-calculator-page__workspace {
    grid-template-columns: minmax(19rem, .75fr) minmax(0, 1.6fr);
  }

  .industry-calculator-page__conditions {
    position: sticky;
    top: .75rem;
  }
}
</style>
