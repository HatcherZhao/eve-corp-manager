<script setup lang="ts">
import { Message, type RequestOption } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import IndustryPageHeader from '../components/IndustryPageHeader.vue'
import IndustryPanel from '../components/IndustryPanel.vue'
import {
  type EveIndustryBlueprint,
  type EveIndustryConfig,
  type EveIndustryDataStatus,
  type EveIndustryFacility,
  type EveIndustryGoldenCase,
  type EveIndustryStaticVersion,
  createEveIndustryGoldenCase,
  getEveIndustryBlueprints,
  getEveIndustryConfigs,
  getEveIndustryDataStatus,
  getEveIndustryFacilities,
  getEveIndustryGoldenCases,
  getEveIndustryStaticVersions,
  importEveIndustryStaticData,
  syncEveIndustryPublicBlueprints,
} from '@/apis/eve'

defineOptions({ name: 'EveIndustryData' })

const router = useRouter()
const loading = ref(false)
const syncing = ref(false)
const importing = ref(false)
const goldenSaving = ref(false)
const importModal = ref(false)
const goldenModal = ref(false)
const status = ref<EveIndustryDataStatus>()
const versions = ref<EveIndustryStaticVersion[]>([])
const goldenCases = ref<EveIndustryGoldenCase[]>([])
const blueprints = ref<EveIndustryBlueprint[]>([])
const configs = ref<EveIndustryConfig[]>([])
const facilities = ref<EveIndustryFacility[]>([])
const importForm = reactive({ source: 'SERENITY_EXPORT', serverVersion: '', buildNumber: undefined as number | undefined, schemaVersion: 'eve-industry-normalized-v1' })
const goldenForm = reactive({ name: '', blueprintId: '', configId: '', facilityId: '', runs: 1, materialEfficiency: 0, timeEfficiency: 0, expectedMaterialsJson: '[]', expectedTimeSeconds: 0, expectedInstallCost: 0, adjustedPricesJson: '{}', materialTolerance: 0, timeToleranceSeconds: 1, installCostTolerance: 1 })

/** 加载工业数据状态和回归校验记录。 */
async function loadPage() {
  loading.value = true
  try {
    const [statusResult, versionResult, goldenResult, blueprintResult, configResult, facilityResult] = await Promise.all([
      getEveIndustryDataStatus(),
      getEveIndustryStaticVersions(),
      getEveIndustryGoldenCases(),
      getEveIndustryBlueprints(),
      getEveIndustryConfigs(),
      getEveIndustryFacilities(),
    ])
    status.value = statusResult.data
    versions.value = versionResult.data
    goldenCases.value = goldenResult.data
    blueprints.value = blueprintResult.data
    configs.value = configResult.data
    facilities.value = facilityResult.data
    goldenForm.blueprintId ||= blueprints.value[0]?.id || ''
    goldenForm.configId ||= configs.value[0]?.id || ''
    goldenForm.facilityId ||= facilities.value[0]?.id || ''
  } finally {
    loading.value = false
  }
}

/** 同步第三方公开蓝图并保存可追溯的哈希版本。 */
async function syncBlueprints() {
  syncing.value = true
  try {
    const { data } = await syncEveIndustryPublicBlueprints()
    Message.success(data.imported ? `已同步 ${data.activityCount.toLocaleString()} 条工业活动` : '公开蓝图未变化')
    await loadPage()
  } finally {
    syncing.value = false
  }
}

/** 上传整批规范化工业配方，服务端校验通过后才会入库。 */
function importStaticData(options: RequestOption) {
  const { fileItem, onError, onProgress, onSuccess } = options
  importing.value = true
  onProgress(20)
  importEveIndustryStaticData(fileItem.file as File, { ...importForm })
    .then(async (response) => {
      if (!response.data.imported && response.data.errors.length) {
        const first = response.data.errors[0]
        Message.error(`导入校验失败：${first.line ? `第 ${first.line} 行 ` : ''}${first.message}`)
      } else {
        Message.success(response.data.imported ? `已导入 ${response.data.activityCount} 项工业活动` : '相同文件已存在')
        importModal.value = false
        await loadPage()
      }
      onSuccess(response)
    })
    .catch(onError)
    .finally(() => { importing.value = false })
  return { abort: () => undefined }
}

/** 保存国服游戏内样例并立即执行材料、时间和安装费对账。 */
async function saveGoldenCase() {
  goldenSaving.value = true
  try {
    const expectedMaterials = JSON.parse(goldenForm.expectedMaterialsJson)
    const adjustedPriceObject = JSON.parse(goldenForm.adjustedPricesJson)
    const adjustedPrices = Object.keys(adjustedPriceObject).length ? adjustedPriceObject : undefined
    const { data } = await createEveIndustryGoldenCase({ ...goldenForm, expectedMaterials, adjustedPrices })
    Message[data.validationStatus === 'PASSED' ? 'success' : 'warning'](
      data.validationStatus === 'PASSED' ? 'Golden Case 校验通过' : `存在差异：${data.validationDetails.join('；')}`,
    )
    goldenModal.value = false
    await loadPage()
  } catch (error) {
    if (error instanceof SyntaxError) Message.error('材料或 adjusted price JSON 格式不正确')
    else throw error
  } finally {
    goldenSaving.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <main class="industry-data-page gi_page">
    <IndustryPageHeader
      eyebrow="INDUSTRY · DATA GOVERNANCE"
      title="数据与校验"
      description="查看蓝图版本、市场覆盖和国服实机回归结果，保证工业利润能够追溯到具体数据来源和计算输入。"
    >
      <template #actions>
        <a-button @click="router.push('/eve/industry/opportunities')">返回机会分析</a-button>
        <a-button @click="importModal = true">导入工业配方</a-button>
        <a-button @click="goldenModal = true">录入 Golden Case</a-button>
        <a-button type="primary" :loading="syncing" @click="syncBlueprints">同步公开蓝图</a-button>
      </template>
    </IndustryPageHeader>

    <section class="industry-data-page__summary" aria-label="工业数据状态">
      <article><span>工业活动</span><strong>{{ status?.blueprintCount ?? 0 }}</strong><small>已导入蓝图活动</small></article>
      <article><span>已校验配方</span><strong>{{ status?.validatedBlueprintCount ?? 0 }}</strong><small>可进入正式计算</small></article>
      <article><span>市场快照</span><strong>{{ status?.marketSnapshotCount ?? 0 }}</strong><small>吉他行情覆盖</small></article>
      <article><span>Golden Case</span><strong>{{ goldenCases.length }}</strong><small>{{ goldenCases.filter((item) => item.validationStatus === 'PASSED').length }} 条通过</small></article>
    </section>

    <a-alert class="industry-data-page__notice" :type="status?.validatedBlueprintCount ? 'info' : 'warning'" show-icon>
      {{ status?.message || '正在读取工业数据状态' }}
    </a-alert>

    <div class="industry-data-page__grid">
      <IndustryPanel title="蓝图数据版本" description="每次同步或导入都保存来源、时间与 SHA-256，不覆盖历史批次。" flush>
        <a-table :data="versions" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 760 }">
          <template #columns>
            <a-table-column title="来源" data-index="source" :width="190" />
            <a-table-column title="版本" :width="160"><template #cell="{ record }">{{ record.serverVersion || record.schemaVersion || '未填写' }}</template></a-table-column>
            <a-table-column title="状态" :width="110"><template #cell="{ record }"><a-tag :color="record.validationStatus === 'VALIDATED' ? 'green' : record.validationStatus === 'REJECTED' ? 'red' : 'orange'">{{ record.validationStatus }}</a-tag></template></a-table-column>
            <a-table-column title="SHA-256" data-index="sha256" :width="220" ellipsis tooltip />
            <a-table-column title="导入时间" :width="160"><template #cell="{ record }">{{ dayjs(record.downloadedAt).format('YYYY-MM-DD HH:mm') }}</template></a-table-column>
          </template>
        </a-table>
        <a-empty v-if="!loading && !versions.length" description="尚未同步或导入工业配方" />
      </IndustryPanel>

      <IndustryPanel title="Golden Case" description="差异只用于回归和风险展示，不阻断已采用规则的试算。" flush>
        <a-table :data="goldenCases" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 720 }">
          <template #columns>
            <a-table-column title="样例" :width="230"><template #cell="{ record }"><strong>{{ record.name }}</strong><small class="industry-data-page__muted">{{ record.blueprintName || '蓝图' }}</small></template></a-table-column>
            <a-table-column title="状态" :width="100"><template #cell="{ record }"><a-tag :color="record.validationStatus === 'PASSED' ? 'green' : record.validationStatus === 'FAILED' ? 'red' : 'orange'">{{ record.validationStatus }}</a-tag></template></a-table-column>
            <a-table-column title="流程 / ME / TE" :width="140"><template #cell="{ record }">{{ record.runs }} / {{ record.materialEfficiency }} / {{ record.timeEfficiency }}</template></a-table-column>
            <a-table-column title="差异" :width="260" ellipsis tooltip><template #cell="{ record }">{{ record.validationDetails.join('；') || '材料、时间与安装费均在容差内' }}</template></a-table-column>
          </template>
        </a-table>
        <a-empty v-if="!loading && !goldenCases.length" description="尚未录入国服游戏内校验样例" />
      </IndustryPanel>
    </div>

    <a-modal v-model:visible="importModal" title="导入规范化工业配方" :footer="false">
      <a-alert type="warning" show-icon>手工导入用于覆盖或补充公开蓝图；建议优先使用公开蓝图同步。</a-alert>
      <a-form :model="importForm" layout="vertical">
        <a-form-item label="数据来源"><a-select v-model="importForm.source"><a-option value="SERENITY_EXPORT">国服版本对应导出</a-option><a-option value="TRANQUILITY_SDE">国际服 SDE 候选回退</a-option></a-select></a-form-item>
        <a-form-item label="国服 server version"><a-input v-model="importForm.serverVersion" /></a-form-item>
        <a-form-item label="构建号"><a-input-number v-model="importForm.buildNumber" :min="1" /></a-form-item>
        <a-form-item label="Schema 版本"><a-input v-model="importForm.schemaVersion" /></a-form-item>
      </a-form>
      <a-upload accept=".json,.jsonl" :show-file-list="false" :custom-request="importStaticData">
        <a-button long type="primary" :loading="importing"><template #icon><icon-upload /></template>选择 JSON / JSONL 并导入</a-button>
      </a-upload>
    </a-modal>

    <a-modal v-model:visible="goldenModal" title="录入国服 Golden Case" :ok-loading="goldenSaving" @ok="saveGoldenCase">
      <a-alert type="info" show-icon>录入游戏内工业窗口显示的实际材料、任务秒数和安装费，系统会保存输入快照并立即对账。</a-alert>
      <a-form :model="goldenForm" layout="vertical">
        <a-form-item label="样例名称"><a-input v-model="goldenForm.name" /></a-form-item>
        <a-form-item label="蓝图"><a-select v-model="goldenForm.blueprintId" allow-search><a-option v-for="item in blueprints" :key="item.id" :value="item.id">{{ item.productName }} · {{ item.activity }} · {{ item.blueprintName }}</a-option></a-select></a-form-item>
        <a-form-item label="生产配置"><a-select v-model="goldenForm.configId"><a-option v-for="item in configs" :key="item.id" :value="item.id">{{ item.name }}</a-option></a-select></a-form-item>
        <a-form-item label="生产设施"><a-select v-model="goldenForm.facilityId"><a-option v-for="item in facilities" :key="item.id" :value="item.id">{{ item.name }} · {{ item.systemName || item.systemId }}</a-option></a-select></a-form-item>
        <a-space fill><a-form-item label="流程数"><a-input-number v-model="goldenForm.runs" :min="1" /></a-form-item><a-form-item label="ME"><a-input-number v-model="goldenForm.materialEfficiency" :min="0" :max="10" /></a-form-item><a-form-item label="TE"><a-input-number v-model="goldenForm.timeEfficiency" :min="0" :max="20" /></a-form-item></a-space>
        <a-form-item label="实际材料 JSON"><a-textarea v-model="goldenForm.expectedMaterialsJson" :auto-size="{ minRows: 3, maxRows: 6 }" /></a-form-item>
        <a-space fill><a-form-item label="实际时间（秒）"><a-input-number v-model="goldenForm.expectedTimeSeconds" :min="0" /></a-form-item><a-form-item label="实际安装费"><a-input-number v-model="goldenForm.expectedInstallCost" :min="0" /></a-form-item></a-space>
        <a-form-item label="材料 adjusted price JSON（可选）"><a-textarea v-model="goldenForm.adjustedPricesJson" :auto-size="{ minRows: 2, maxRows: 5 }" /></a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.industry-data-page {
  min-width: 0;
  overflow-x: clip;
  color: var(--color-text-1);
}

.industry-data-page__summary,
.industry-data-page__notice,
.industry-data-page__grid {
  margin-top: .75rem;
}

.industry-data-page__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 14px;
  background: var(--color-border-2);
}

.industry-data-page__summary article {
  min-width: 0;
  padding: .875rem;
  background: var(--color-bg-1);
}

.industry-data-page__summary span,
.industry-data-page__summary small {
  display: block;
  color: var(--color-text-3);
  font-size: .75rem;
}

.industry-data-page__summary strong {
  display: block;
  margin: .25rem 0;
  font-size: 1.25rem;
  font-variant-numeric: tabular-nums;
}

.industry-data-page__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: .75rem;
}

.industry-data-page__muted {
  display: block;
  color: var(--color-text-3);
  font-size: .75rem;
}

@media (min-width: 768px) {
  .industry-data-page__summary {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .industry-data-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
