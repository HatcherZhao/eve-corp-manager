<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import IndustryPageHeader from '../components/IndustryPageHeader.vue'
import IndustryPanel from '../components/IndustryPanel.vue'
import { formatIndustryIsk } from '../shared/format'
import { formatEveTime } from '@/utils/eveTime'
import { type EveIndustryOpportunity, type EveIndustryPlan, type EveIndustryPlanDetail, type EveIndustryPlanResponse, copyEveIndustryPlan, createEveIndustryPlan, getEveIndustryOpportunities, getEveIndustryPlan, getEveIndustryPlans, recalculateEveIndustryPlan, updateEveIndustryPlanStatus } from '@/apis/eve'

defineOptions({ name: 'EveIndustryPlans' })
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const records = ref<EveIndustryPlan[]>([])
const opportunities = ref<EveIndustryOpportunity[]>([])
const selected = ref<EveIndustryPlanDetail>()
const detailVisible = ref(false)
const createVisible = ref(false)
const form = reactive({ opportunityId: '', name: '' })
const statusMeta: Record<string, { label: string, color: string }> = { DRAFT: { label: '草稿', color: 'gray' }, CONFIRMED: { label: '已确认', color: 'blue' }, PURCHASING: { label: '采购中', color: 'orange' }, PRODUCING: { label: '生产中', color: 'arcoblue' }, DELIVERABLE: { label: '可交付', color: 'green' }, DELIVERED: { label: '已交付', color: 'green' }, CANCELLED: { label: '已取消', color: 'red' } }
const statusOptions = ['DRAFT', 'CONFIRMED', 'PURCHASING', 'PRODUCING', 'DELIVERABLE', 'DELIVERED', 'CANCELLED']
const executableOpportunities = computed(() => opportunities.value.filter((item) => item.status === 'EXECUTABLE'))
const selectedOpportunity = computed(() => opportunities.value.find((item) => item.id === form.opportunityId))
function statusLabel(status: string) {
  return statusMeta[status]?.label || status
}
function statusColor(status: string) {
  return statusMeta[status]?.color || 'gray'
}
function resetForm() {
  Object.assign(form, { opportunityId: '', name: '' })
}
/** 将后端主记录和步骤边界转换为页面详情。 */
function normalizeDetail(response: EveIndustryPlanResponse): EveIndustryPlanDetail {
  const purchaseSteps = response.steps.filter((step) => step.stepType === 'PURCHASE')
  return {
    ...response.plan,
    totalCost: (response.plan.totalMaterialCost || 0) + (response.plan.totalInstallCost || 0),
    materials: purchaseSteps.map((step) => ({
      typeId: step.typeId || 0,
      name: step.title.replace(/^采购\s*/, ''),
      requiredQuantity: step.quantity || 0,
      purchaseCost: step.totalCost,
      source: 'MARKET_ONLY',
    })),
    steps: response.steps.map((step) => ({
      id: step.id,
      stepType: step.stepType,
      name: step.title,
      status: step.status,
      quantity: step.quantity,
      sequence: step.sequenceNo,
    })),
  }
}
/** 加载计划列表和创建计划时可用的机会快照。 */
async function loadPage() {
  loading.value = true
  try {
    const [planResult, opportunityResult] = await Promise.all([getEveIndustryPlans(), getEveIndustryOpportunities(100)])
    records.value = planResult.data || []
    opportunities.value = opportunityResult.data || []
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}
async function openDetail(record: EveIndustryPlan) {
  try {
    selected.value = normalizeDetail((await getEveIndustryPlan(record.id)).data)
    detailVisible.value = true
  } catch {
    Message.error('计划详情加载失败，请稍后重试')
  }
}
function openCreate() {
  resetForm()
  createVisible.value = true
}
function useOpportunity() {
  const opportunity = selectedOpportunity.value
  if (!opportunity) return
  if (!form.name) form.name = `${opportunity.productName} · 生产计划`
}
async function savePlan() {
  if (!form.opportunityId || !form.name.trim()) {
    Message.warning('请选择可执行机会并填写计划名称')
    return
  }
  if (selectedOpportunity.value?.status !== 'EXECUTABLE') {
    Message.warning('只有可执行机会才能创建生产计划')
    return
  }
  saving.value = true
  try {
    await createEveIndustryPlan({
      opportunityId: form.opportunityId,
      name: form.name.trim(),
      runs: selectedOpportunity.value?.recommendedRuns,
    })
    createVisible.value = false
    Message.success('生产计划草稿已创建')
    await loadPage()
  } finally {
    saving.value = false
  }
}
async function changeStatus(status: string) {
  if (!selected.value || status === selected.value.status) return
  saving.value = true
  try {
    const updated = (await updateEveIndustryPlanStatus(selected.value.id, status)).data
    selected.value = { ...selected.value, ...updated }
    const index = records.value.findIndex((item) => item.id === selected.value?.id)
    if (index >= 0) records.value[index] = { ...records.value[index], ...updated, status }
    Message.success(`计划状态已更新为${statusLabel(status)}`)
  } finally {
    saving.value = false
  }
}
async function recalculate() {
  if (!selected.value) return
  saving.value = true
  try {
    selected.value = normalizeDetail((await recalculateEveIndustryPlan(selected.value.id)).data)
    Message.success('计划摘要已恢复为来源机会快照')
  } finally {
    saving.value = false
  }
}
async function copyPlan(record: EveIndustryPlan) {
  saving.value = true
  try {
    await copyEveIndustryPlan(record.id)
    Message.success('计划已复制为草稿')
    await loadPage()
  } finally {
    saving.value = false
  }
}
onMounted(loadPage)
</script>

<template>
  <main class="industry-plans-page gi_page">
    <IndustryPageHeader eyebrow="INDUSTRY · PRODUCTION PLAN" title="生产计划" description="把已验证的工业机会固化为可复盘的材料采购、生产批次和销售跟踪计划。">
      <template #actions><a-button @click="router.push('/eve/industry/opportunities')">机会分析</a-button><a-button type="primary" @click="openCreate">新建计划</a-button></template>
    </IndustryPageHeader>
    <a-alert class="industry-plans-page__notice" type="info" show-icon>计划使用创建时的机会、市场和设施快照；当前不自动提交游戏内制造或市场订单。</a-alert>
    <IndustryPanel class="industry-plans-page__list" title="计划列表" description="计划状态由人工推进，详情中的材料和步骤来自后端快照。" flush>
      <a-table :data="records" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 900 }" @row-click="openDetail">
        <template #columns>
          <a-table-column title="计划" :width="230" fixed="left" ellipsis tooltip><template #cell="{ record }"><div><strong>{{ record.name }}</strong><small class="muted">{{ record.productName || '等待产品信息' }}</small></div></template></a-table-column>
          <a-table-column title="状态" :width="100"><template #cell="{ record }"><a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag></template></a-table-column>
          <a-table-column title="目标产量" :width="110" align="right"><template #cell="{ record }">{{ record.targetQuantity?.toLocaleString() || '—' }}</template></a-table-column>
          <a-table-column title="预计利润" :width="150" align="right"><template #cell="{ record }">{{ formatIndustryIsk(record.expectedProfit) }}</template></a-table-column>
          <a-table-column title="更新时间" :width="150"><template #cell="{ record }">{{ record.updatedAt || record.updateTime ? formatEveTime(record.updatedAt || record.updateTime, 'MM-DD HH:mm') : '—' }}</template></a-table-column>
          <a-table-column title="操作" :width="120"><template #cell="{ record }"><a-button type="text" size="small" @click.stop="copyPlan(record)">复制</a-button></template></a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!loading && !records.length" description="暂无生产计划"><template #extra><a-space wrap><a-button @click="router.push('/eve/industry/opportunities')">先查看机会</a-button><a-button type="primary" @click="openCreate">从机会创建计划</a-button></a-space></template></a-empty>
    </IndustryPanel>
    <a-drawer v-model:visible="detailVisible" :width="520" title="计划详情" unmount-on-close>
      <template v-if="selected">
        <div class="plan-detail__headline"><div><h2>{{ selected.name }}</h2><span>{{ selected.productName || '未关联产品' }}</span></div><a-tag :color="statusColor(selected.status)">{{ statusLabel(selected.status) }}</a-tag></div>
        <a-space wrap class="plan-detail__actions"><a-select :model-value="selected.status" :loading="saving" @change="changeStatus"><a-option v-for="status in statusOptions" :key="status" :value="status">{{ statusLabel(status) }}</a-option></a-select><a-button :loading="saving" @click="recalculate">恢复来源快照</a-button></a-space>
        <section class="plan-detail__summary"><article><span>目标产量</span><strong>{{ selected.targetQuantity?.toLocaleString() || '—' }}</strong></article><article><span>预计投入</span><strong>{{ formatIndustryIsk(selected.totalCost) }}</strong></article><article><span>预计利润</span><strong>{{ formatIndustryIsk(selected.expectedProfit) }}</strong></article></section>
        <IndustryPanel title="材料采购" description="每次按无库存、吉他海四市场购买。" flush><a-table :data="selected.materials || []" :pagination="false" :scroll="{ x: 480 }"><template #columns><a-table-column title="材料" data-index="name" /><a-table-column title="需求量" data-index="requiredQuantity" align="right" /><a-table-column title="采购成本" align="right"><template #cell="{ record }">{{ formatIndustryIsk(record.purchaseCost) }}</template></a-table-column></template></a-table></IndustryPanel>
        <IndustryPanel class="plan-detail__steps" title="执行步骤" flush><a-timeline><a-timeline-item v-for="step in selected.steps || []" :key="step.id || `${step.sequence}-${step.name}`"><strong>{{ step.name }}</strong><span class="muted">{{ statusLabel(step.status) }} · {{ step.stepType }}</span></a-timeline-item></a-timeline><a-empty v-if="!selected.steps?.length" description="暂无步骤数据" /></IndustryPanel>
      </template>
    </a-drawer>
    <a-modal v-model:visible="createVisible" title="从机会创建生产计划" :ok-loading="saving" @ok="savePlan"><a-form :model="form" layout="vertical"><a-form-item field="opportunityId" label="可执行工业机会" required><a-select v-model="form.opportunityId" placeholder="选择可执行机会" @change="useOpportunity"><a-option v-for="item in executableOpportunities" :key="item.id" :value="item.id">{{ item.productName }} · {{ item.recommendedQuantity.toLocaleString() }}</a-option></a-select></a-form-item><a-form-item v-if="selectedOpportunity" label="固化批量"><a-input :model-value="`${selectedOpportunity.recommendedRuns} 流程 / ${selectedOpportunity.recommendedQuantity.toLocaleString()} 件`" disabled /><template #extra>当前计划严格复用机会快照批量，避免材料、订单档位和利润口径失真。</template></a-form-item><a-form-item field="name" label="计划名称" required><a-input v-model="form.name" placeholder="例如：月度护卫舰生产" /></a-form-item></a-form></a-modal>
  </main>
</template>

<style scoped lang="scss">
.industry-plans-page { min-width: 0; overflow-x: clip; color: var(--color-text-1); }
.industry-plans-page__notice, .industry-plans-page__list { margin-top: .75rem; }
.industry-plans-page__list :deep(.arco-table-td) { cursor: pointer; }
.industry-plans-page small, .muted { display: block; color: var(--color-text-3); font-size: .75rem; margin-top: .2rem; }
.plan-detail__headline { display: flex; justify-content: space-between; gap: 1rem; align-items: start; margin-bottom: 1rem; }
.plan-detail__headline h2 { margin: 0; font-size: 1.25rem; }
.plan-detail__headline span { color: var(--color-text-3); }
.plan-detail__actions { margin-bottom: 1rem; }
.plan-detail__summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .5rem; margin-bottom: 1rem; }
.plan-detail__summary article { padding: .7rem; background: var(--color-fill-1); border-radius: 8px; }
.plan-detail__summary span, .plan-detail__summary strong { display: block; }
.plan-detail__summary span { color: var(--color-text-3); font-size: .75rem; }
.plan-detail__summary strong { margin-top: .2rem; }
.plan-detail__steps { margin-top: .75rem; }
@media (max-width: 600px) { .plan-detail__summary { grid-template-columns: 1fr; } }
</style>
