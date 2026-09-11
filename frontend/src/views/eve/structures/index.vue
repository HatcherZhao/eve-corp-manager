<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import DataFreshnessBanner from '../components/DataFreshnessBanner.vue'
import {
  type EveCorporationStructure,
  getEveCorporationStructures,
  syncEveCorporationStructures,
} from '@/apis/eve'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveStructures' })

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const records = ref<EveCorporationStructure[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', state: '' })
const freshnessVersion = ref(0)
const canSync = computed(() => userStore.permissions.includes('eve:structures:manage') || userStore.permissions.includes('*:*:*'))

const stateLabels: Record<string, string> = {
  anchor_vulnerable: '可受攻击',
  anchoring: '部署中',
  armor_reinforce: '装甲强化',
  armor_vulnerable: '装甲可受攻击',
  deploy_vulnerable: '部署可受攻击',
  fitting_invulnerable: '装配保护中',
  hull_reinforce: '船体强化',
  hull_vulnerable: '船体可受攻击',
  online_deprecated: '在线',
  onlining_vulnerable: '上线可受攻击',
  shield_vulnerable: '护盾可受攻击',
  unanchored: '已拆锚',
  unknown: '未知',
}

const serviceLabels: Record<string, string> = {
  'Clone Bay': '克隆舱',
  'Invention': '发明研究',
  'Manufacturing (Standard)': '制造（标准）',
  'Moon Drilling': '月矿钻井',
  'Reprocessing': '再处理',
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '上游未提供'
}

function stateLabel(state: string) {
  return stateLabels[state] || state || '未知'
}

function stateColor(state: string) {
  return ['hull_vulnerable', 'armor_vulnerable', 'shield_vulnerable', 'unanchored'].includes(state) ? 'orangered' : state === 'online_deprecated' ? 'green' : 'arcoblue'
}

function serviceLabel(name: string) {
  return serviceLabels[name] || name
}

function fuelRisk(record: EveCorporationStructure) {
  if (!record.fuelExpiresAt) return '上游未提供'
  const hours = dayjs(record.fuelExpiresAt).diff(dayjs(), 'hour', true)
  if (hours <= 0) return '已到期'
  if (hours <= 72) return `${Math.ceil(hours)} 小时后到期`
  return formatTime(record.fuelExpiresAt)
}

function fuelColor(record: EveCorporationStructure) {
  if (!record.fuelExpiresAt) return 'gray'
  const hours = dayjs(record.fuelExpiresAt).diff(dayjs(), 'hour', true)
  return hours <= 72 ? 'orangered' : 'green'
}

async function loadStructures() {
  loading.value = true
  try {
    const { data } = await getEveCorporationStructures({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      state: query.state || undefined,
    })
    records.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadStructures()
}

function reset() {
  Object.assign(query, { page: 1, keyword: '', state: '' })
  loadStructures()
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveCorporationStructures()
    Message.success(data.message)
  } finally {
    syncing.value = false
    freshnessVersion.value += 1
  }
}

onMounted(loadStructures)
</script>

<template>
  <main class="eve-structures-page gi_page">
    <header class="eve-structures-page__header">
      <div>
        <span class="eve-structures-page__eyebrow">CORPORATION STRUCTURE SNAPSHOT</span>
        <h1>军团建筑</h1>
        <p>查看本军团自有玩家建筑的状态、在线服务、燃料到期与状态计时；所有时间均标注为最近一次完整国服快照。</p>
      </div>
      <div class="eve-structures-page__header-tools">
        <DataFreshnessBanner :modules="['STRUCTURES']" :reload-token="freshnessVersion" />
        <a-button v-if="canSync" type="primary" :loading="syncing" @click="sync"><template #icon><icon-sync /></template>同步建筑数据</a-button>
      </div>
    </header>

    <a-alert class="eve-structures-page__guide" type="info" :show-icon="true">
      燃料、服务和状态字段由国服建筑接口按授权范围返回；“上游未提供”不等同于燃料充足或建筑不存在。
    </a-alert>
    <section class="eve-structures-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索建筑名称、类型或星系" @press-enter="search"><template #prefix><icon-search /></template></a-input>
      <a-select v-model="query.state" allow-clear placeholder="全部建筑状态">
        <a-option v-for="(label, value) in stateLabels" :key="value" :value="value">{{ label }}</a-option>
      </a-select>
      <a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button>
      <span class="eve-structures-page__count">共 {{ total.toLocaleString() }} 座建筑</span>
    </section>

    <section class="eve-structures-page__table-wrap">
      <a-table :data="records" :loading="loading" :pagination="false" row-key="structureId" :scroll="{ x: 920 }">
        <template #columns>
          <a-table-column title="建筑" :width="210" ellipsis tooltip><template #cell="{ record }"><div class="eve-structures-page__structure-identity"><EveTypeIcon :type-id="record.typeId" :size="30" :alt="`${record.typeName || record.structureName || '建筑'}图标`" /><div><strong>{{ record.structureName || '建筑名称待补齐' }}</strong><small>{{ record.typeName || '建筑类型待补齐' }}</small></div></div></template></a-table-column>
          <a-table-column title="星系" :width="125" ellipsis tooltip><template #cell="{ record }">{{ record.solarSystemName || '星系名称待补齐' }}</template></a-table-column>
          <a-table-column title="状态" :width="105"><template #cell="{ record }"><a-tag :color="stateColor(record.state)">{{ stateLabel(record.state) }}</a-tag></template></a-table-column>
          <a-table-column title="燃料" :width="135"><template #cell="{ record }"><a-tag :color="fuelColor(record)">{{ fuelRisk(record) }}</a-tag></template></a-table-column>
          <a-table-column title="在线服务" :width="185"><template #cell="{ record }"><div class="eve-structures-page__services"><a-tag v-for="service in record.services" :key="service.name" :color="service.state === 'online' ? 'green' : 'gray'">{{ serviceLabel(service.name) }} · {{ service.state === 'online' ? '在线' : service.state }}</a-tag><span v-if="!record.services.length">上游未提供</span></div></template></a-table-column>
          <a-table-column title="计时" :width="160"><template #cell="{ record }"><strong>{{ formatTime(record.stateTimerEndAt) }}</strong><small v-if="record.unanchorsAt">拆锚：{{ formatTime(record.unanchorsAt) }}</small></template></a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadStructures" @page-size-change="search" />
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-structures-page { color: var(--color-text-1); }
.eve-structures-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-structures-page__header-tools { display: flex; align-self: flex-start; flex-direction: column; align-items: flex-end; gap: 10px; }
.eve-structures-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-structures-page h1 { margin: 5px 0; font-size: 26px; }.eve-structures-page__header p { max-width: 720px; margin: 0; color: var(--color-text-3); }
.eve-structures-page__guide { margin-top: 12px; }.eve-structures-page__filter-bar { display: flex; align-items: center; gap: 8px; margin: 12px 0; padding: 10px 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-structures-page__filter-bar .arco-input-wrapper { width: min(340px, 100%); }.eve-structures-page__filter-bar .arco-select { width: 180px; }.eve-structures-page__count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-structures-page__table-wrap { padding: 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }.eve-structures-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 12px; }.eve-structures-page__table-wrap small { display: block; margin-top: 3px; color: var(--color-text-3); }.eve-structures-page__services { display: flex; flex-wrap: wrap; gap: 3px; }.eve-structures-page__services span { color: var(--color-text-3); }
.eve-structures-page__structure-identity { display: flex; align-items: center; gap: 10px; }.eve-structures-page__structure-identity > div { min-width: 0; }.eve-structures-page__structure-identity strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 860px) { .eve-structures-page__header { align-items: flex-start; flex-direction: column; }.eve-structures-page__header-tools { align-items: flex-start; }.eve-structures-page__filter-bar { flex-wrap: wrap; }.eve-structures-page__filter-bar .arco-input-wrapper, .eve-structures-page__filter-bar .arco-select { width: 100%; }.eve-structures-page__count { width: 100%; margin-left: 0; } }
</style>
