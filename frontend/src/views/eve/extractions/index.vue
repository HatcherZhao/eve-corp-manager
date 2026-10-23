<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import DataFreshnessBanner from '../components/DataFreshnessBanner.vue'
import {
  type EveMoonExtraction,
  getEveMoonExtractions,
  refreshEveMoonOreProfile,
  saveEveMoonExtractionNote,
  syncEveMoonExtractions,
} from '@/apis/eve'
import { useUserStore } from '@/stores'
import { formatEveTime, nowEveTime, parseEveTime } from '@/utils/eveTime'

defineOptions({ name: 'EveExtractions' })

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const savingNote = ref(false)
const refreshingOreProfileIds = ref<string[]>([])
const drawerVisible = ref(false)
const records = ref<EveMoonExtraction[]>([])
const total = ref(0)
const selected = ref<EveMoonExtraction>()
const structureNote = ref('')
const query = reactive({ page: 1, size: 20, keyword: '', timeline: '' })
const canSync = computed(() => userStore.permissions.includes('eve:extractions:sync') || userStore.permissions.includes('*:*:*'))
const freshnessVersion = ref(0)
const canManage = computed(() => userStore.permissions.includes('eve:extractions:manage') || userStore.permissions.includes('*:*:*'))

function formatTime(value?: string) {
  return formatEveTime(value)
}

function formatProportion(value?: number) {
  return value === undefined || value === null ? '—' : `${(value * 100).toLocaleString(undefined, { maximumFractionDigits: 2 })}%`
}

/** 将矿块到达时间转换为玩家可直接判断的可开采日期和星期。 */
function formatMiningDay(value?: string) {
  if (!value) return '—'
  const time = parseEveTime(value)
  const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][time.day()]
  return `${time.format('YYYY-MM-DD')} ${weekday}`
}

function resolvedName(name: string | undefined, label: string) {
  return name || `${label}名称待补齐`
}

function timeState(record: EveMoonExtraction) {
  return parseEveTime(record.chunkArrivalAt).isAfter(nowEveTime()) ? '待到达' : '已到达'
}

function timeStateColor(record: EveMoonExtraction) {
  return parseEveTime(record.chunkArrivalAt).isAfter(nowEveTime()) ? 'arcoblue' : 'orange'
}

async function loadExtractions() {
  loading.value = true
  try {
    const { data } = await getEveMoonExtractions({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      timeline: query.timeline as 'UPCOMING' | 'ARRIVED' | undefined,
    })
    records.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadExtractions()
}

function reset() {
  Object.assign(query, { page: 1, keyword: '', timeline: '' })
  loadExtractions()
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveMoonExtractions()
    Message.success(data.message)
    await loadExtractions()
  } finally {
    syncing.value = false
    freshnessVersion.value += 1
  }
}

function openDetail(record: EveMoonExtraction) {
  selected.value = record
  structureNote.value = record.structureNote || ''
  drawerVisible.value = true
}

async function saveNote() {
  if (!selected.value) return
  savingNote.value = true
  try {
    const { data } = await saveEveMoonExtractionNote(selected.value.id, { note: structureNote.value.trim() || undefined })
    selected.value = data
    structureNote.value = data.structureNote || ''
    records.value = records.value.map((item) => item.structureId === data.structureId
      ? { ...item, structureNote: data.structureNote }
      : item)
    Message.success('月矿堡备注已保存')
  } finally {
    savingNote.value = false
  }
}

function isRefreshingOreProfile(record: EveMoonExtraction) {
  return refreshingOreProfileIds.value.includes(record.structureId)
}

/** 手动刷新矿堡档案，并同步更新列表内该矿堡的所有时间线记录。 */
async function refreshOreProfile(record: EveMoonExtraction) {
  if (isRefreshingOreProfile(record)) return
  refreshingOreProfileIds.value = [...refreshingOreProfileIds.value, record.structureId]
  try {
    const { data } = await refreshEveMoonOreProfile(record.id)
    records.value = records.value.map((item) => item.structureId === data.structureId
      ? { ...item, oreProfile: data.oreProfile }
      : item)
    if (selected.value?.structureId === data.structureId) {
      selected.value = { ...selected.value, oreProfile: data.oreProfile }
    }
    if (data.oreProfile?.status === 'READY') {
      Message.success('月矿种类档案已更新')
    } else {
      Message.info('暂未找到可用的月矿通知')
    }
  } finally {
    refreshingOreProfileIds.value = refreshingOreProfileIds.value.filter((id) => id !== record.structureId)
  }
}

onMounted(loadExtractions)
</script>

<template>
  <main class="eve-extractions-page gi_page">
    <header class="eve-extractions-page__header">
      <div>
        <span class="eve-extractions-page__eyebrow">MOON EXTRACTION · INTELLIGENCE TIMELINE</span>
        <h1>月矿情报</h1>
        <p>集中查看本军团精炼厂的矿块提取、到达与自然碎裂时间，帮助成员及时掌握月矿动态。</p>
      </div>
      <div class="eve-extractions-page__header-tools">
        <DataFreshnessBanner :modules="['MOON_EXTRACTIONS']" :reload-token="freshnessVersion" />
        <a-button v-if="canSync" type="primary" :loading="syncing" @click="sync"><template #icon><icon-sync /></template>同步月矿时间线</a-button>
      </div>
    </header>

    <a-alert class="eve-extractions-page__guide" type="info" :show-icon="true">
      数据来自国服官方接口；时间和“可开采日”统一按北京时间（UTC+8）显示。矿种档案来自游戏通知，展示各矿种的长期产出比例。
    </a-alert>
    <section class="eve-extractions-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索精炼厂、月球、星系或矿种" @press-enter="search"><template #prefix><icon-search /></template></a-input>
      <a-select v-model="query.timeline" allow-clear placeholder="全部时间线">
        <a-option value="UPCOMING">待到达</a-option><a-option value="ARRIVED">已到达</a-option>
      </a-select>
      <a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button>
      <span class="eve-extractions-page__count">共 {{ total.toLocaleString() }} 条时间线</span>
    </section>

    <section class="eve-extractions-page__table-wrap">
      <a-table :data="records" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 1160 }">
        <template #columns>
          <a-table-column title="精炼厂" :width="180" ellipsis tooltip><template #cell="{ record }"><strong>{{ resolvedName(record.structureName, '建筑') }}</strong><small>{{ record.structureTypeName || '建筑类型待补齐' }}</small></template></a-table-column>
          <a-table-column title="月球 / 星系" :width="160" ellipsis tooltip><template #cell="{ record }"><strong>{{ resolvedName(record.moonName, '月球') }}</strong><small>{{ resolvedName(record.solarSystemName, '星系') }}</small></template></a-table-column>
          <a-table-column title="开采窗口" :width="170"><template #cell="{ record }"><a-tag :color="timeStateColor(record)">{{ timeState(record) }}</a-tag><strong>{{ formatMiningDay(record.chunkArrivalAt) }}</strong><small>{{ formatTime(record.chunkArrivalAt) }}</small></template></a-table-column>
          <a-table-column title="自然碎裂" :width="130"><template #cell="{ record }">{{ formatTime(record.naturalDecayAt) }}</template></a-table-column>
          <a-table-column title="矿种档案" :width="250">
            <template #cell="{ record }">
              <div v-if="record.oreProfile?.status === 'READY' && record.oreProfile.ores.length" class="eve-extractions-page__ore-summary">
                <div class="eve-extractions-page__ore-tags">
                  <a-tooltip v-for="ore in record.oreProfile.ores.slice(0, 3)" :key="ore.typeId" :content="`${ore.typeName} ${formatProportion(ore.proportion)}`">
                    <a-tag color="cyan">{{ ore.typeName }} {{ formatProportion(ore.proportion) }}</a-tag>
                  </a-tooltip>
                  <a-tag v-if="record.oreProfile.ores.length > 3" color="gray">+{{ record.oreProfile.ores.length - 3 }}</a-tag>
                </div>
              </div>
              <span v-else class="eve-extractions-page__muted">{{ record.oreProfile?.status === 'PENDING' ? '等待通知补全' : '矿种未知' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="月矿堡备注" :width="180" ellipsis tooltip><template #cell="{ record }"><span :class="{ 'eve-extractions-page__muted': !record.structureNote }">{{ record.structureNote || '暂无备注' }}</span></template></a-table-column>
          <a-table-column title="操作" :width="112" fixed="right">
            <template #cell="{ record }">
              <a-space :size="4">
                <a-button type="text" @click="openDetail(record)">详情</a-button>
                <a-tooltip v-if="canManage" content="刷新该矿堡矿种档案">
                  <a-button type="text" size="small" aria-label="刷新该矿堡矿种档案" :loading="isRefreshingOreProfile(record)" @click.stop="refreshOreProfile(record)"><template #icon><icon-refresh /></template></a-button>
                </a-tooltip>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadExtractions" @page-size-change="search" />
    </section>

    <a-drawer v-model:visible="drawerVisible" width="min(560px, 100vw)" :footer="false" unmount-on-close title="月矿情报详情">
      <template v-if="selected">
        <div class="eve-extractions-page__drawer-title">
          <strong>{{ resolvedName(selected.structureName, '建筑') }}</strong>
          <span>{{ resolvedName(selected.moonName, '月球') }} · {{ resolvedName(selected.solarSystemName, '星系') }}</span>
          <small>可开采：{{ formatTime(selected.chunkArrivalAt) }}（{{ formatMiningDay(selected.chunkArrivalAt) }}） · 自然碎裂：{{ formatTime(selected.naturalDecayAt) }}</small>
        </div>
        <section class="eve-extractions-page__ore-profile">
          <div class="eve-extractions-page__section-heading">
            <div>
              <h3>月矿种类档案</h3>
              <a-tag :color="selected.oreProfile?.status === 'READY' ? 'green' : 'gray'">{{ selected.oreProfile?.status === 'READY' ? '已有档案' : selected.oreProfile?.status === 'PENDING' ? '等待通知补全' : '矿种未知' }}</a-tag>
            </div>
            <a-tooltip v-if="canManage" content="从已有游戏通知重新读取">
              <a-button size="small" :loading="isRefreshingOreProfile(selected)" @click="refreshOreProfile(selected)"><template #icon><icon-refresh /></template>刷新矿种</a-button>
            </a-tooltip>
          </div>
          <template v-if="selected.oreProfile?.status === 'READY' && selected.oreProfile.ores.length">
            <div class="eve-extractions-page__ore-list">
              <div class="eve-extractions-page__ore-list-heading"><span>矿种</span><span>产出比例</span></div>
              <div v-for="ore in selected.oreProfile.ores" :key="ore.typeId" class="eve-extractions-page__ore-item">
                <span>{{ ore.typeName }}</span>
                <strong>{{ formatProportion(ore.proportion) }}</strong>
              </div>
            </div>
            <dl class="eve-extractions-page__ore-meta">
              <div><dt>通知发送时间</dt><dd>{{ formatTime(selected.oreProfile.sourceSentAt) }}</dd></div>
              <div><dt>档案更新时间</dt><dd>{{ formatTime(selected.oreProfile.updatedAt) }}</dd></div>
            </dl>
          </template>
          <a-empty v-else :description="selected.oreProfile?.status === 'PENDING' ? '尚无可用于补全档案的月矿通知' : '该矿堡暂无矿种档案'" />
        </section>
        <a-descriptions :column="1" bordered size="large">
          <a-descriptions-item label="情报状态"><a-tag :color="timeStateColor(selected)">{{ timeState(selected) }}</a-tag></a-descriptions-item>
          <a-descriptions-item label="提取开始">{{ formatTime(selected.extractionStartAt) }}</a-descriptions-item>
          <a-descriptions-item label="矿块到达">{{ formatTime(selected.chunkArrivalAt) }}</a-descriptions-item>
          <a-descriptions-item label="可开采日">{{ formatMiningDay(selected.chunkArrivalAt) }}（矿块到达后）</a-descriptions-item>
          <a-descriptions-item label="自然碎裂">{{ formatTime(selected.naturalDecayAt) }}</a-descriptions-item>
          <a-descriptions-item label="最近快照">{{ formatTime(selected.lastSeenAt) }}</a-descriptions-item>
          <a-descriptions-item label="上游缓存到期">{{ formatTime(selected.sourceExpiresAt) }}</a-descriptions-item>
        </a-descriptions>
        <section class="eve-extractions-page__note">
          <h3>月矿堡备注</h3>
          <template v-if="canManage">
            <a-textarea v-model="structureNote" :max-length="500" show-word-limit allow-clear placeholder="记录该月矿堡的开采提示、风险或其他长期信息" />
            <a-button type="primary" :loading="savingNote" @click="saveNote">保存月矿堡备注</a-button>
          </template>
          <p v-else>{{ selected.structureNote || '暂无备注' }}</p>
        </section>
      </template>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-extractions-page { color: var(--color-text-1); }
.eve-extractions-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-extractions-page__header-tools { display: flex; align-self: flex-start; flex-direction: column; align-items: flex-end; gap: 10px; }
.eve-extractions-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }.eve-extractions-page h1 { margin: 5px 0; font-size: 26px; }.eve-extractions-page__header p { max-width: 720px; margin: 0; color: var(--color-text-3); }
.eve-extractions-page__guide { margin-top: 12px; }.eve-extractions-page__filter-bar { display: flex; align-items: center; gap: 8px; margin: 12px 0; padding: 10px 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }.eve-extractions-page__filter-bar .arco-input-wrapper { width: min(340px, 100%); }.eve-extractions-page__filter-bar .arco-select { width: 160px; }.eve-extractions-page__count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-extractions-page__table-wrap { padding: 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }.eve-extractions-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 12px; }.eve-extractions-page__table-wrap small, .eve-extractions-page__drawer-title small { display: block; margin-top: 3px; color: var(--color-text-3); }.eve-extractions-page__muted { color: var(--color-text-3); }
.eve-extractions-page__ore-summary { min-width: 0; }.eve-extractions-page__ore-tags { display: flex; min-width: 0; flex-wrap: wrap; gap: 4px; }.eve-extractions-page__ore-tags .arco-tag { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.eve-extractions-page__drawer-title { display: grid; gap: 3px; margin-bottom: 16px; }.eve-extractions-page__drawer-title span { color: var(--color-text-2); }.eve-extractions-page__drawer-title + .arco-descriptions { margin-top: 18px; }
.eve-extractions-page__ore-profile { margin-bottom: 18px; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-fill-1); }.eve-extractions-page__section-heading, .eve-extractions-page__section-heading > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.eve-extractions-page__section-heading h3 { margin: 0; font-size: 16px; }.eve-extractions-page__ore-list { display: grid; gap: 6px; margin-top: 12px; }.eve-extractions-page__ore-list-heading, .eve-extractions-page__ore-item { display: flex; min-width: 0; align-items: baseline; justify-content: space-between; gap: 12px; }.eve-extractions-page__ore-list-heading { color: var(--color-text-3); font-size: 12px; }.eve-extractions-page__ore-item span { min-width: 0; overflow-wrap: anywhere; }.eve-extractions-page__ore-item strong { flex: none; font-family: DINPro, sans-serif; font-variant-numeric: tabular-nums; }.eve-extractions-page__ore-meta { display: grid; gap: 5px; margin: 10px 0 0; padding-top: 10px; border-top: 1px solid var(--color-border-2); color: var(--color-text-3); font-size: 12px; }.eve-extractions-page__ore-meta div { display: flex; justify-content: space-between; gap: 12px; }.eve-extractions-page__ore-meta dt, .eve-extractions-page__ore-meta dd { margin: 0; }.eve-extractions-page__ore-meta dd { text-align: right; overflow-wrap: anywhere; }
.eve-extractions-page__note { margin-top: 24px; }.eve-extractions-page__note h3 { margin: 0 0 12px; }.eve-extractions-page__note .arco-btn { margin-top: 12px; }.eve-extractions-page__note p { margin: 0; color: var(--color-text-2); white-space: pre-wrap; }
@media (max-width: 860px) { .eve-extractions-page__header { align-items: flex-start; flex-direction: column; }.eve-extractions-page__header-tools { align-items: flex-start; }.eve-extractions-page__filter-bar { flex-wrap: wrap; }.eve-extractions-page__filter-bar .arco-input-wrapper, .eve-extractions-page__filter-bar .arco-select { width: 100%; }.eve-extractions-page__count { width: 100%; margin-left: 0; } }
@media (max-width: 420px) { .eve-extractions-page__section-heading { align-items: flex-start; flex-direction: column; }.eve-extractions-page__section-heading .arco-btn { min-height: 36px; }.eve-extractions-page__ore-list-heading { display: none; }.eve-extractions-page__ore-item, .eve-extractions-page__ore-meta div { align-items: flex-start; flex-direction: column; gap: 2px; }.eve-extractions-page__ore-meta dd { text-align: left; } }
</style>
