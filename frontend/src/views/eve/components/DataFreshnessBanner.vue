<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, ref, watch } from 'vue'
import { type EveDataFreshness, getEveDataFreshness } from '@/apis/eve'

const props = withDefaults(defineProps<{
  /** 要展示的模块；成员页同时展示名册与追踪两项。 */
  modules: EveDataFreshness['module'][]
  /** 父页面成功或失败同步后递增该值，以重新取得最新状态。 */
  reloadToken?: number
}>(), { reloadToken: 0 })

const loading = ref(false)
const failed = ref(false)
const records = ref<EveDataFreshness[]>([])

const visibleRecords = computed(() => props.modules.map((module) => records.value.find((item) => item.module === module))
  .filter((item): item is EveDataFreshness => Boolean(item)))

function formatTime(value?: string) {
  return value ? dayjs(value).format('MM-DD HH:mm') : '—'
}

/** 将多模块健康状态收敛为页头右上角的一条状态文字。 */
const summary = computed(() => {
  if (failed.value) return { text: '同步状态读取失败', type: 'error' }
  if (loading.value) return { text: '正在读取同步状态', type: 'info' }
  const records = visibleRecords.value
  if (!records.length) return { text: '暂无同步状态', type: 'info' }
  const failedRecord = records.find((item) => item.status === 'SYNC_FAILED')
  if (failedRecord) return { text: `最近同步失败：${failedRecord.title}`, type: 'error' }
  const staleRecord = records.find((item) => item.status === 'STALE')
  if (staleRecord) return { text: `数据已过期：${staleRecord.title}`, type: 'warning' }
  const noDataRecord = records.find((item) => item.status === 'NO_DATA')
  if (noDataRecord) return { text: `尚未同步：${noDataRecord.title}`, type: 'info' }
  const latestSuccessfulAt = records
    .map((item) => item.lastSuccessfulAt)
    .filter((item): item is string => Boolean(item))
    .sort()
    .at(-1)
  return { text: `数据同步：${formatTime(latestSuccessfulAt)}`, type: 'success' }
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    const { data } = await getEveDataFreshness()
    records.value = data
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.reloadToken, load)
onMounted(load)
</script>

<template>
  <span class="eve-data-freshness" :class="`eve-data-freshness--${summary.type}`" aria-live="polite">
    <i class="eve-data-freshness__dot" aria-hidden="true" />
    <span>{{ summary.text }}</span>
    <a-button v-if="failed" type="text" size="mini" @click="load">重试</a-button>
  </span>
</template>

<style scoped lang="scss">
.eve-data-freshness { display: inline-flex; align-items: center; gap: 6px; min-height: 24px; color: var(--color-text-3); font-size: 12px; line-height: 1.4; white-space: nowrap; }
.eve-data-freshness__dot { width: 6px; height: 6px; border-radius: 50%; background: rgb(var(--green-6)); box-shadow: 0 0 0 3px rgba(var(--green-6), .1); }
.eve-data-freshness--warning { color: rgb(var(--orange-6)); }.eve-data-freshness--warning .eve-data-freshness__dot { background: rgb(var(--orange-6)); box-shadow: 0 0 0 3px rgba(var(--orange-6), .1); }
.eve-data-freshness--error { color: rgb(var(--red-6)); }.eve-data-freshness--error .eve-data-freshness__dot { background: rgb(var(--red-6)); box-shadow: 0 0 0 3px rgba(var(--red-6), .1); }
.eve-data-freshness--info { color: var(--color-text-3); }.eve-data-freshness--info .eve-data-freshness__dot { background: rgb(var(--arcoblue-6)); box-shadow: 0 0 0 3px rgba(var(--arcoblue-6), .1); }
.eve-data-freshness :deep(.arco-btn-text) { height: auto; padding: 0 2px; font-size: 12px; }
</style>
