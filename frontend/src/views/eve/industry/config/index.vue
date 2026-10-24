<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import IndustryPageHeader from '../components/IndustryPageHeader.vue'
import IndustryPanel from '../components/IndustryPanel.vue'
import { formatIndustryPercent, industryActivityLabel } from '../shared/format'
import { readIndustryProductionFields, writeIndustryProductionFields } from '../shared/productionSettings'
import {
  type EveIndustryBlueprint,
  type EveIndustryBlueprintOverride,
  type EveIndustryConfig,
  type EveIndustryFacility,
  createEveIndustryBlueprintOverride,
  createEveIndustryConfig,
  createEveIndustryFacility,
  getEveIndustryBlueprintOverrides,
  getEveIndustryBlueprints,
  getEveIndustryConfigs,
  getEveIndustryFacilities,
  syncEveIndustryFacilitySci,
  updateEveIndustryBlueprintOverride,
  updateEveIndustryConfig,
  updateEveIndustryFacility,
} from '@/apis/eve'

defineOptions({ name: 'EveIndustryConfig' })

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const syncingFacilityId = ref('')
const activeTab = ref('production')
const configs = ref<EveIndustryConfig[]>([])
const facilities = ref<EveIndustryFacility[]>([])
const blueprints = ref<EveIndustryBlueprint[]>([])
const overrides = ref<EveIndustryBlueprintOverride[]>([])
const configModal = ref(false)
const facilityModal = ref(false)
const blueprintModal = ref(false)
const editingConfigId = ref('')
const editingFacilityId = ref('')
const editingOverrideId = ref('')

const configForm = reactive({
  name: '',
  productionSkillJson: '{"productionLines":1,"timeMultiplier":1}',
  marketFeeJson: '{"salesTax":0,"brokerFee":0}',
  targetDays: 1,
  marketShareLimit: 0.1,
  freshnessPolicyJson: '{}',
})
const productionFields = reactive(readIndustryProductionFields())
const marketSharePercent = computed({
  get: () => configForm.marketShareLimit * 100,
  set: (value: number) => { configForm.marketShareLimit = value / 100 },
})

const facilityForm = reactive({
  name: '',
  realityType: 'HYPOTHETICAL' as 'ACTUAL' | 'HYPOTHETICAL',
  systemId: '30000142',
  systemName: '吉他',
  securityStatus: undefined as number | undefined,
  facilityName: '',
  supportedActivitiesJson: '["MANUFACTURING","REACTION"]',
  onlineServicesJson: '["Manufacturing"]',
  rigTypeIdsJson: '[]',
  facilityTax: 0,
  versionedSurcharge: 0,
  materialMultiplier: 1,
  timeMultiplier: 1,
  costMultiplier: 1,
  manufacturingSci: undefined as number | undefined,
  reactionSci: undefined as number | undefined,
  source: 'MANUAL',
})

const blueprintForm = reactive({
  blueprintId: '',
  blueprintKind: 'BPO' as 'BPO' | 'BPC',
  materialEfficiency: 10,
  timeEfficiency: 5,
  remainingRuns: undefined as number | undefined,
  source: 'MANUAL',
})

/** 加载配置中心的三类配置，页面内的试算和机会页直接复用这些版本。 */
async function loadPage() {
  loading.value = true
  try {
    const [configResult, facilityResult, blueprintResult, overrideResult] = await Promise.all([
      getEveIndustryConfigs(),
      getEveIndustryFacilities(),
      getEveIndustryBlueprints(),
      getEveIndustryBlueprintOverrides(),
    ])
    configs.value = configResult.data
    facilities.value = facilityResult.data
    blueprints.value = blueprintResult.data
    overrides.value = overrideResult.data
  } finally {
    loading.value = false
  }
}

function resetConfigForm() {
  Object.assign(configForm, {
    name: '',
    productionSkillJson: '{"productionLines":1,"timeMultiplier":1}',
    marketFeeJson: '{"salesTax":0,"brokerFee":0}',
    targetDays: 1,
    marketShareLimit: 0.1,
    freshnessPolicyJson: '{}',
  })
}

function resetFacilityForm() {
  Object.assign(facilityForm, {
    name: '',
    realityType: 'HYPOTHETICAL',
    systemId: '30000142',
    systemName: '吉他',
    securityStatus: undefined,
    facilityName: '',
    supportedActivitiesJson: '["MANUFACTURING","REACTION"]',
    onlineServicesJson: '["Manufacturing"]',
    rigTypeIdsJson: '[]',
    facilityTax: 0,
    versionedSurcharge: 0,
    materialMultiplier: 1,
    timeMultiplier: 1,
    costMultiplier: 1,
    manufacturingSci: undefined,
    reactionSci: undefined,
    source: 'MANUAL',
  })
}

function openConfig(item?: EveIndustryConfig) {
  editingConfigId.value = item?.id || ''
  if (!item) resetConfigForm()
  else Object.assign(configForm, {
    name: item.name,
    productionSkillJson: item.productionSkillJson || '{}',
    marketFeeJson: item.marketFeeJson || '{}',
    targetDays: item.targetDays,
    marketShareLimit: item.marketShareLimit,
    freshnessPolicyJson: item.freshnessPolicyJson || '{}',
  })
  try {
    Object.assign(productionFields, readIndustryProductionFields(configForm.productionSkillJson, configForm.marketFeeJson))
    configModal.value = true
  } catch {
    Message.error('生产配置数据格式不正确，请联系管理员检查')
  }
}

function openFacility(item?: EveIndustryFacility) {
  editingFacilityId.value = item?.id || ''
  if (!item) return resetFacilityForm()
  Object.assign(facilityForm, {
    name: item.name,
    realityType: item.realityType,
    systemId: item.systemId,
    systemName: item.systemName || '',
    securityStatus: item.securityStatus,
    facilityName: item.facilityName || '',
    supportedActivitiesJson: item.supportedActivitiesJson || '["MANUFACTURING"]',
    onlineServicesJson: item.onlineServicesJson || '[]',
    rigTypeIdsJson: item.rigTypeIdsJson || '[]',
    facilityTax: item.facilityTax,
    versionedSurcharge: item.versionedSurcharge,
    materialMultiplier: item.materialMultiplier ?? 1,
    timeMultiplier: item.timeMultiplier ?? 1,
    costMultiplier: item.costMultiplier ?? 1,
    manufacturingSci: item.manufacturingSci,
    reactionSci: item.reactionSci,
    source: item.source || 'MANUAL',
  })
}

function openBlueprint(item?: EveIndustryBlueprintOverride) {
  editingOverrideId.value = item?.id || ''
  if (!item) {
    Object.assign(blueprintForm, { blueprintId: blueprints.value[0]?.id || '', blueprintKind: 'BPO', materialEfficiency: 10, timeEfficiency: 5, remainingRuns: undefined, source: 'MANUAL' })
    return
  }
  const blueprint = blueprints.value.find((candidate) => candidate.blueprintTypeId === item.blueprintTypeId)
  Object.assign(blueprintForm, { blueprintId: blueprint?.id || '', blueprintKind: item.blueprintKind, materialEfficiency: item.materialEfficiency, timeEfficiency: item.timeEfficiency, remainingRuns: item.remainingRuns, source: item.source || 'MANUAL' })
}

async function saveConfig() {
  let productionPayload
  try {
    productionPayload = writeIndustryProductionFields(productionFields, configForm.productionSkillJson, configForm.marketFeeJson)
  } catch (error) {
    Message.warning(error instanceof Error ? error.message : '请检查生产设置')
    return false
  }
  saving.value = true
  try {
    const payload = { ...configForm, ...productionPayload }
    if (editingConfigId.value) await updateEveIndustryConfig(editingConfigId.value, payload)
    else await createEveIndustryConfig(payload)
    configModal.value = false
    await loadPage()
    Message.success('生产配置已保存')
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

async function saveFacility() {
  saving.value = true
  try {
    if (editingFacilityId.value) await updateEveIndustryFacility(editingFacilityId.value, { ...facilityForm })
    else await createEveIndustryFacility({ ...facilityForm })
    facilityModal.value = false
    await loadPage()
    Message.success('生产设施已保存')
  } finally {
    saving.value = false
  }
}

async function saveBlueprint() {
  const blueprint = blueprints.value.find((item) => item.id === blueprintForm.blueprintId)
  if (!blueprint) {
    Message.warning('请选择蓝图')
    return
  }
  saving.value = true
  try {
    const payload = {
      blueprintTypeId: blueprint.blueprintTypeId,
      blueprintKind: blueprintForm.blueprintKind,
      materialEfficiency: blueprintForm.materialEfficiency,
      timeEfficiency: blueprintForm.timeEfficiency,
      remainingRuns: blueprintForm.blueprintKind === 'BPC' ? blueprintForm.remainingRuns : undefined,
      source: blueprintForm.source,
    }
    if (editingOverrideId.value) await updateEveIndustryBlueprintOverride(editingOverrideId.value, payload)
    else await createEveIndustryBlueprintOverride(payload)
    blueprintModal.value = false
    await loadPage()
    Message.success('蓝图配置已保存')
  } finally {
    saving.value = false
  }
}

async function syncSci(item: EveIndustryFacility) {
  syncingFacilityId.value = item.id
  try {
    await syncEveIndustryFacilitySci(item.id)
    await loadPage()
    Message.success('已从国服官方接口更新星系成本指数')
  } finally {
    syncingFacilityId.value = ''
  }
}

onMounted(loadPage)
</script>

<template>
  <main class="industry-config-page gi_page">
    <IndustryPageHeader
      eyebrow="工业生产"
      title="工业配置"
      description="设置生产线、市场税费、生产设施，以及蓝图的材料效率和时间效率。"
    >
      <template #actions>
        <a-button @click="router.push('/eve/industry/opportunities')">返回机会分析</a-button>
        <a-button v-if="activeTab === 'production'" type="primary" @click="openConfig()">新增生产配置</a-button>
        <a-button v-else-if="activeTab === 'facility'" type="primary" @click="openFacility(); facilityModal = true">新增生产设施</a-button>
        <a-button v-else type="primary" @click="openBlueprint(); blueprintModal = true">新增蓝图配置</a-button>
      </template>
    </IndustryPageHeader>

    <a-alert class="industry-config-page__notice" type="info" show-icon>
      市场固定为吉他海四，不计运输费。可使用实际设施或假设设施估算；星系成本指数可手动填写或从国服官方接口同步。
    </a-alert>

    <IndustryPanel class="industry-config-page__panel" flush>
      <a-tabs v-model:active-key="activeTab" lazy-load>
        <a-tab-pane key="production" title="生产配置">
          <a-table :data="configs" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 680 }">
            <template #columns>
              <a-table-column title="配置名称" data-index="name" />
              <a-table-column title="目标周期" :width="120" align="right"><template #cell="{ record }">{{ record.targetDays }} 天</template></a-table-column>
              <a-table-column title="市场份额上限" :width="150" align="right"><template #cell="{ record }">{{ (record.marketShareLimit * 100).toFixed(1) }}%</template></a-table-column>
              <a-table-column title="版本" :width="90" align="center"><template #cell="{ record }">v{{ record.version || 1 }}</template></a-table-column>
              <a-table-column title="状态" :width="90"><template #cell="{ record }"><a-tag :color="record.enabled ? 'green' : 'gray'">{{ record.enabled ? '启用' : '停用' }}</a-tag></template></a-table-column>
              <a-table-column title="操作" :width="100"><template #cell="{ record }"><a-button size="small" @click="openConfig(record)">编辑</a-button></template></a-table-column>
            </template>
          </a-table>
          <a-empty v-if="!loading && !configs.length" description="尚未保存生产配置" />
        </a-tab-pane>

        <a-tab-pane key="facility" title="生产设施">
          <a-table :data="facilities" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 980 }">
            <template #columns>
              <a-table-column title="设施" :width="210"><template #cell="{ record }"><strong>{{ record.name }}</strong><small class="industry-config-page__muted">{{ record.facilityName || '建筑未填写' }}</small></template></a-table-column>
              <a-table-column title="地点" :width="180"><template #cell="{ record }">{{ record.systemName || record.systemId }}<small class="industry-config-page__muted">{{ record.realityType === 'ACTUAL' ? '实际地点' : '假设地点' }}</small></template></a-table-column>
              <a-table-column title="制造成本指数" :width="140" align="right"><template #cell="{ record }">{{ record.manufacturingSci == null ? '未填写' : formatIndustryPercent(record.manufacturingSci) }}</template></a-table-column>
              <a-table-column title="反应成本指数" :width="140" align="right"><template #cell="{ record }">{{ record.reactionSci == null ? '未填写' : formatIndustryPercent(record.reactionSci) }}</template></a-table-column>
              <a-table-column title="设施加成与税率" :width="230"><template #cell="{ record }"><span class="industry-config-page__muted">材料用量 ×{{ record.materialMultiplier ?? 1 }} · 耗时 ×{{ record.timeMultiplier ?? 1 }} · 税率 {{ formatIndustryPercent(record.facilityTax) }}</span></template></a-table-column>
              <a-table-column title="操作" :width="220"><template #cell="{ record }"><a-space><a-button size="small" @click="openFacility(record); facilityModal = true">编辑</a-button><a-button size="small" :loading="syncingFacilityId === record.id" @click="syncSci(record)">同步成本指数</a-button></a-space></template></a-table-column>
            </template>
          </a-table>
          <a-empty v-if="!loading && !facilities.length" description="尚未保存生产设施" />
        </a-tab-pane>

        <a-tab-pane key="blueprint" title="蓝图配置">
          <a-table :data="overrides" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 950 }">
            <template #columns>
              <a-table-column title="蓝图" :width="300"><template #cell="{ record }"><strong>{{ blueprints.find((item) => item.blueprintTypeId === record.blueprintTypeId)?.productName || `蓝图 ${record.blueprintTypeId}` }}</strong><small class="industry-config-page__muted">{{ blueprints.find((item) => item.blueprintTypeId === record.blueprintTypeId)?.blueprintName || '名称待补充' }}</small></template></a-table-column>
              <a-table-column title="类型" :width="100"><template #cell="{ record }">{{ record.blueprintKind === 'BPO' ? '蓝图原本' : '蓝图拷贝' }}</template></a-table-column>
              <a-table-column title="材料效率" :width="120" align="right"><template #cell="{ record }">{{ record.materialEfficiency }}%</template></a-table-column>
              <a-table-column title="时间效率" :width="120" align="right"><template #cell="{ record }">{{ record.timeEfficiency }}%</template></a-table-column>
              <a-table-column title="剩余生产次数" :width="140" align="right"><template #cell="{ record }">{{ record.remainingRuns ?? '不限' }}</template></a-table-column>
              <a-table-column title="操作" :width="100"><template #cell="{ record }"><a-button size="small" @click="openBlueprint(record); blueprintModal = true">编辑</a-button></template></a-table-column>
            </template>
          </a-table>
          <a-empty v-if="!loading && !overrides.length" description="尚未保存蓝图研究等级" />
        </a-tab-pane>
      </a-tabs>
    </IndustryPanel>

    <a-modal v-model:visible="configModal" modal-class="industry-settings-modal" :title="editingConfigId ? '编辑生产配置' : '新增生产配置'" :ok-loading="saving" :on-before-ok="saveConfig">
      <a-form :model="configForm" layout="vertical">
        <a-form-item label="配置名称"><a-input v-model="configForm.name" placeholder="例如：满技能·主力制造线" /></a-form-item>
        <div class="industry-settings-grid">
          <a-form-item label="目标周期（天）"><a-input-number v-model="configForm.targetDays" :min="0.01" /></a-form-item>
          <a-form-item label="市场份额上限（%）"><a-input-number v-model="marketSharePercent" :min="0.01" :max="100" :step="1" /></a-form-item>
          <a-form-item label="同时使用的生产线数量"><a-input-number v-model="productionFields.productionLines" :min="1" :precision="0" /></a-form-item>
          <a-form-item label="技能调整后耗时（原耗时%）"><a-input-number v-model="productionFields.skillTimePercent" :min="0.0001" /></a-form-item>
          <a-form-item label="销售税（%）"><a-input-number v-model="productionFields.salesTaxPercent" :min="0" :max="100" :step="0.1" /></a-form-item>
          <a-form-item label="经纪人费（%）"><a-input-number v-model="productionFields.brokerFeePercent" :min="0" :max="100" :step="0.1" /></a-form-item>
        </div>
        <a-collapse :bordered="false"><a-collapse-item key="advanced" header="高级数据维护"><a-form-item label="行情有效期规则（JSON）"><a-textarea v-model="configForm.freshnessPolicyJson" :auto-size="{ minRows: 2, maxRows: 4 }" /></a-form-item></a-collapse-item></a-collapse>
      </a-form>
    </a-modal>

    <a-modal v-model:visible="facilityModal" modal-class="industry-settings-modal" :title="editingFacilityId ? '编辑生产设施' : '新增生产设施'" :ok-loading="saving" @ok="saveFacility">
      <a-form :model="facilityForm" layout="vertical">
        <a-form-item label="设施配置名称"><a-input v-model="facilityForm.name" /></a-form-item>
        <a-space fill><a-form-item label="地点类型"><a-select v-model="facilityForm.realityType"><a-option value="ACTUAL">实际地点</a-option><a-option value="HYPOTHETICAL">假设地点</a-option></a-select></a-form-item><a-form-item label="安全等级"><a-input-number v-model="facilityForm.securityStatus" :min="-1" :max="1" :step="0.1" /></a-form-item></a-space>
        <a-space fill><a-form-item label="星系 ID"><a-input v-model="facilityForm.systemId" /></a-form-item><a-form-item label="星系名称"><a-input v-model="facilityForm.systemName" /></a-form-item></a-space>
        <a-form-item label="建筑名称"><a-input v-model="facilityForm.facilityName" /></a-form-item>
        <a-space fill>
          <a-form-item label="设施税率（%）"><a-input-number :model-value="Number((facilityForm.facilityTax * 100).toPrecision(15))" :min="0" :step="0.1" @update:model-value="value => facilityForm.facilityTax = value == null ? 0 : Number((value / 100).toPrecision(15))" /></a-form-item>
          <a-form-item label="附加税率（%）"><a-input-number :model-value="Number((facilityForm.versionedSurcharge * 100).toPrecision(15))" :min="0" :step="0.1" @update:model-value="value => facilityForm.versionedSurcharge = value == null ? 0 : Number((value / 100).toPrecision(15))" /></a-form-item>
        </a-space>
        <div class="industry-settings-grid">
          <a-form-item label="材料用量系数"><a-input-number v-model="facilityForm.materialMultiplier" :min="0.000001" /></a-form-item>
          <a-form-item label="生产耗时系数"><a-input-number v-model="facilityForm.timeMultiplier" :min="0.000001" /></a-form-item>
          <a-form-item label="安装成本系数"><a-input-number v-model="facilityForm.costMultiplier" :min="0.000001" /></a-form-item>
        </div>
        <a-space fill>
          <a-form-item label="制造成本指数（%）"><a-input-number :model-value="facilityForm.manufacturingSci == null ? undefined : Number((facilityForm.manufacturingSci * 100).toPrecision(15))" :min="0" :step="0.01" @update:model-value="value => facilityForm.manufacturingSci = value == null ? undefined : Number((value / 100).toPrecision(15))" /></a-form-item>
          <a-form-item label="反应成本指数（%）"><a-input-number :model-value="facilityForm.reactionSci == null ? undefined : Number((facilityForm.reactionSci * 100).toPrecision(15))" :min="0" :step="0.01" @update:model-value="value => facilityForm.reactionSci = value == null ? undefined : Number((value / 100).toPrecision(15))" /></a-form-item>
        </a-space>
        <a-collapse :bordered="false">
          <a-collapse-item key="advanced" header="高级数据维护">
            <a-form-item label="支持的生产类型（JSON）"><a-input v-model="facilityForm.supportedActivitiesJson" /></a-form-item>
            <a-form-item label="已启用的建筑服务（JSON）"><a-input v-model="facilityForm.onlineServicesJson" /></a-form-item>
            <a-form-item label="建筑改装件物品编号（JSON）"><a-input v-model="facilityForm.rigTypeIdsJson" /></a-form-item>
          </a-collapse-item>
        </a-collapse>
      </a-form>
    </a-modal>

    <a-modal v-model:visible="blueprintModal" modal-class="industry-settings-modal" :title="editingOverrideId ? '编辑蓝图配置' : '新增蓝图配置'" :ok-loading="saving" @ok="saveBlueprint">
      <a-alert type="info" show-icon>材料效率、时间效率和蓝图类型用于利润分析与蓝图计算；不计蓝图购入费用。</a-alert>
      <a-form :model="blueprintForm" layout="vertical">
        <a-form-item label="蓝图"><a-select v-model="blueprintForm.blueprintId" allow-search><a-option v-for="item in blueprints" :key="item.id" :value="item.id">{{ item.productName }} · {{ industryActivityLabel(item.activity) }} · {{ item.blueprintName }}</a-option></a-select></a-form-item>
        <div class="industry-settings-grid">
          <a-form-item label="蓝图类型"><a-select v-model="blueprintForm.blueprintKind"><a-option value="BPO">蓝图原本</a-option><a-option value="BPC">蓝图拷贝</a-option></a-select></a-form-item>
          <a-form-item label="材料效率（%）"><a-input-number v-model="blueprintForm.materialEfficiency" :min="0" :max="10" /></a-form-item>
          <a-form-item label="时间效率（%）"><a-input-number v-model="blueprintForm.timeEfficiency" :min="0" :max="20" /></a-form-item>
        </div>
        <a-form-item v-if="blueprintForm.blueprintKind === 'BPC'" label="拷贝剩余生产次数"><a-input-number v-model="blueprintForm.remainingRuns" :min="1" /></a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.industry-config-page {
  min-width: 0;
  overflow-x: clip;
  color: var(--color-text-1);
}

.industry-config-page__notice,
.industry-config-page__panel {
  margin-top: .75rem;
}

.industry-config-page__muted {
  display: block;
  color: var(--color-text-3);
  font-size: .75rem;
  line-height: 1.5;
}

.industry-config-page :deep(.arco-tabs-content) {
  padding-top: 0;
}

.industry-config-page :deep(.arco-table-container) {
  min-width: 0;
}
</style>

<style lang="scss">
.industry-settings-modal {
  width: min(36rem, calc(100vw - 2rem));

  .arco-space-item, .arco-form-item, .arco-select, .arco-input-number { min-width: 0; width: 100%; }
  .arco-space-horizontal { display: grid; grid-template-columns: minmax(0, 1fr); gap: .5rem; }
}

.industry-settings-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0 .75rem; }

@media (min-width: 600px) {
  .industry-settings-modal .arco-space-horizontal, .industry-settings-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
