<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import DataFreshnessBanner from '../components/DataFreshnessBanner.vue'
import { type EveMoonExtraction, getEveMoonExtractions, saveEveMoonExtractionNote, syncEveMoonExtractions } from '@/apis/eve'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveExtractions' })

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const savingNote = ref(false)
const drawerVisible = ref(false)
const records = ref<EveMoonExtraction[]>([])
const total = ref(0)
const selected = ref<EveMoonExtraction>()
const note = ref('')
const query = reactive({ page: 1, size: 20, keyword: '', timeline: '' })
const canSync = computed(() => userStore.permissions.includes('eve:extractions:sync') || userStore.permissions.includes('*:*:*'))
const freshnessVersion = ref(0)
const canManage = computed(() => userStore.permissions.includes('eve:extractions:manage') || userStore.permissions.includes('*:*:*'))

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function resolvedName(name: string | undefined, label: string) {
  return name || `${label}名称待补齐`
}

function timeState(record: EveMoonExtraction) {
  return dayjs(record.chunkArrivalAt).isAfter(dayjs()) ? '待到达' : '已到达'
}

function timeStateColor(record: EveMoonExtraction) {
  return dayjs(record.chunkArrivalAt).isAfter(dayjs()) ? 'arcoblue' : 'orange'
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
  } finally {
    syncing.value = false
    freshnessVersion.value += 1
  }
}

function openDetail(record: EveMoonExtraction) {
  selected.value = record
  note.value = record.note || ''
  drawerVisible.value = true
}

async function saveNote() {
  if (!selected.value) return
  savingNote.value = true
  try {
    const { data } = await saveEveMoonExtractionNote(selected.value.id, { note: note.value.trim() || undefined })
    selected.value = data
    note.value = data.note || ''
    const index = records.value.findIndex((item) => item.id === data.id)
    if (index >= 0) records.value.splice(index, 1, data)
    Message.success('备注已保存')
  } finally {
    savingNote.value = false
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
      数据来自国服官方接口；手动同步会完整读取全部分页，再原子发布当前军团的最新月矿情报快照。
    </a-alert>
    <section class="eve-extractions-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索精炼厂、月球或星系名称" @press-enter="search"><template #prefix><icon-search /></template></a-input>
      <a-select v-model="query.timeline" allow-clear placeholder="全部时间线">
        <a-option value="UPCOMING">待到达</a-option><a-option value="ARRIVED">已到达</a-option>
      </a-select>
      <a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button>
      <span class="eve-extractions-page__count">共 {{ total.toLocaleString() }} 条时间线</span>
    </section>

    <section class="eve-extractions-page__table-wrap">
      <a-table :data="records" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 1480 }">
        <template #columns>
          <a-table-column title="精炼厂" :width="235"><template #cell="{ record }"><strong>{{ resolvedName(record.structureName, '建筑') }}</strong><small>{{ record.structureTypeName || '建筑类型待补齐' }}</small></template></a-table-column>
          <a-table-column title="月球 / 星系" :width="240"><template #cell="{ record }"><strong>{{ resolvedName(record.moonName, '月球') }}</strong><small>{{ resolvedName(record.solarSystemName, '星系') }}</small></template></a-table-column>
          <a-table-column title="提取开始" :width="165"><template #cell="{ record }">{{ formatTime(record.extractionStartAt) }}</template></a-table-column>
          <a-table-column title="矿块到达" :width="180"><template #cell="{ record }"><a-tag :color="timeStateColor(record)">{{ timeState(record) }}</a-tag><div>{{ formatTime(record.chunkArrivalAt) }}</div></template></a-table-column>
          <a-table-column title="自然碎裂" :width="165"><template #cell="{ record }">{{ formatTime(record.naturalDecayAt) }}</template></a-table-column>
          <a-table-column title="备注" :width="200" ellipsis tooltip><template #cell="{ record }"><span :class="{ 'eve-extractions-page__muted': !record.note }">{{ record.note || '暂无备注' }}</span></template></a-table-column>
          <a-table-column title="最近快照" :width="165"><template #cell="{ record }">{{ formatTime(record.lastSeenAt) }}</template></a-table-column>
          <a-table-column title="操作" :width="100" fixed="right"><template #cell="{ record }"><a-button type="text" @click="openDetail(record)">查看详情</a-button></template></a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadExtractions" @page-size-change="search" />
    </section>

    <a-drawer v-model:visible="drawerVisible" width="520px" :footer="false" unmount-on-close title="月矿情报详情">
      <template v-if="selected">
        <div class="eve-extractions-page__drawer-title">
          <strong>{{ resolvedName(selected.structureName, '建筑') }}</strong>
          <span>{{ resolvedName(selected.moonName, '月球') }} · {{ resolvedName(selected.solarSystemName, '星系') }}</span>
          <small>矿块到达：{{ formatTime(selected.chunkArrivalAt) }} · 自然碎裂：{{ formatTime(selected.naturalDecayAt) }}</small>
        </div>
        <a-descriptions :column="1" bordered size="large">
          <a-descriptions-item label="情报状态"><a-tag :color="timeStateColor(selected)">{{ timeState(selected) }}</a-tag></a-descriptions-item>
          <a-descriptions-item label="提取开始">{{ formatTime(selected.extractionStartAt) }}</a-descriptions-item>
          <a-descriptions-item label="矿块到达">{{ formatTime(selected.chunkArrivalAt) }}</a-descriptions-item>
          <a-descriptions-item label="自然碎裂">{{ formatTime(selected.naturalDecayAt) }}</a-descriptions-item>
          <a-descriptions-item label="最近快照">{{ formatTime(selected.lastSeenAt) }}</a-descriptions-item>
          <a-descriptions-item label="上游缓存到期">{{ formatTime(selected.sourceExpiresAt) }}</a-descriptions-item>
        </a-descriptions>
        <section class="eve-extractions-page__note">
          <h3>备注</h3>
          <template v-if="canManage">
            <a-textarea v-model="note" :max-length="500" show-word-limit allow-clear placeholder="记录开采提示、风险或其他简短信息" />
            <a-button type="primary" :loading="savingNote" @click="saveNote">保存备注</a-button>
          </template>
          <p v-else>{{ selected.note || '暂无备注' }}</p>
        </section>
      </template>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-extractions-page { color: var(--color-text-1); }
.eve-extractions-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 26px 30px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 16px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-extractions-page__header-tools { display: flex; align-self: flex-start; flex-direction: column; align-items: flex-end; gap: 10px; }
.eve-extractions-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }.eve-extractions-page h1 { margin: 8px 0; font-size: 30px; }.eve-extractions-page__header p { max-width: 720px; margin: 0; color: var(--color-text-3); }
.eve-extractions-page__guide { margin-top: 18px; }.eve-extractions-page__filter-bar { display: flex; align-items: center; gap: 10px; margin: 18px 0; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }.eve-extractions-page__filter-bar .arco-input-wrapper { width: min(340px, 100%); }.eve-extractions-page__filter-bar .arco-select { width: 160px; }.eve-extractions-page__count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-extractions-page__table-wrap { padding: 18px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }.eve-extractions-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 16px; }.eve-extractions-page__table-wrap small, .eve-extractions-page__drawer-title small { display: block; margin-top: 4px; color: var(--color-text-3); }.eve-extractions-page__muted { color: var(--color-text-3); }
.eve-extractions-page__drawer-title { display: grid; gap: 3px; margin-bottom: 16px; }.eve-extractions-page__drawer-title span { color: var(--color-text-2); }.eve-extractions-page__drawer-title + .arco-descriptions { margin-top: 18px; }
.eve-extractions-page__note { margin-top: 24px; }.eve-extractions-page__note h3 { margin: 0 0 12px; }.eve-extractions-page__note .arco-btn { margin-top: 12px; }.eve-extractions-page__note p { margin: 0; color: var(--color-text-2); white-space: pre-wrap; }
@media (max-width: 860px) { .eve-extractions-page__header { align-items: flex-start; flex-direction: column; }.eve-extractions-page__header-tools { align-items: flex-start; }.eve-extractions-page__filter-bar { flex-wrap: wrap; }.eve-extractions-page__filter-bar .arco-input-wrapper, .eve-extractions-page__filter-bar .arco-select { width: 100%; }.eve-extractions-page__count { width: 100%; margin-left: 0; } }
</style>
