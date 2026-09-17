<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps<{
  /** 最近一次成功取得吉他报价的时间。 */
  synchronizedAt?: string
  /** 服务端按当前报价刷新周期计算的失效时间。 */
  freshnessExpiresAt?: string
}>()

const currentTime = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined

/** 每半分钟重算一次，让列表停留时的柱形新鲜度自然衰减。 */
onMounted(() => {
  timer = setInterval(() => {
    currentTime.value = Date.now()
  }, 30_000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const freshness = computed(() => {
  const synchronizedAt = props.synchronizedAt ? dayjs(props.synchronizedAt) : undefined
  if (!synchronizedAt?.isValid()) {
    return { level: 0, state: 'empty', label: '等待首次报价', detail: '尚未取得吉他市场报价' }
  }
  const expiresAt = props.freshnessExpiresAt ? dayjs(props.freshnessExpiresAt) : synchronizedAt.add(15, 'minute')
  const total = Math.max(1, expiresAt.valueOf() - synchronizedAt.valueOf())
  const remaining = expiresAt.valueOf() - currentTime.value
  const ratio = Math.max(0, Math.min(1, remaining / total))
  const level = Math.max(1, Math.ceil(ratio * 4))
  if (remaining <= 0) {
    return { level, state: 'expired', label: '报价已过期', detail: `报价时间：${synchronizedAt.format('MM-DD HH:mm')}` }
  }
  const remainingMinutes = Math.max(1, Math.ceil(remaining / 60_000))
  if (ratio <= 0.5) {
    return { level, state: 'aging', label: '即将更新', detail: `约 ${remainingMinutes} 分钟后刷新` }
  }
  return { level, state: 'fresh', label: '报价新鲜', detail: `约 ${remainingMinutes} 分钟内有效` }
})
</script>

<template>
  <span
    class="eve-market-freshness"
    :class="`eve-market-freshness--${freshness.state}`"
    :aria-label="`价格新鲜度：${freshness.label}，${freshness.detail}`"
    :title="`${freshness.label} · ${freshness.detail}`"
  >
    <i v-for="bar in 4" :key="bar" :class="{ 'eve-market-freshness__bar--active': bar <= freshness.level }" aria-hidden="true" />
    <span class="eve-market-freshness__label">{{ freshness.label }}</span>
  </span>
</template>

<style scoped lang="scss">
.eve-market-freshness { display: inline-flex; align-items: end; gap: 2px; min-width: 0; color: var(--color-text-3); font-size: .75rem; line-height: 1; white-space: nowrap; }
.eve-market-freshness__bar { width: 3px; border-radius: 999px; background: var(--color-fill-3); transition: background-color .45s ease, box-shadow .45s ease, opacity .45s ease; }.eve-market-freshness__bar:nth-child(1) { height: 6px; }.eve-market-freshness__bar:nth-child(2) { height: 9px; }.eve-market-freshness__bar:nth-child(3) { height: 12px; }.eve-market-freshness__bar:nth-child(4) { height: 15px; }
.eve-market-freshness__bar--active { background: currentcolor; box-shadow: 0 0 5px currentcolor; }.eve-market-freshness--fresh { color: rgb(var(--green-6)); }.eve-market-freshness--aging { color: rgb(var(--orange-6)); }.eve-market-freshness--expired { color: rgb(var(--red-6)); }.eve-market-freshness--empty { color: var(--color-text-4); }.eve-market-freshness__label { margin-left: 4px; overflow: hidden; text-overflow: ellipsis; }
@media (prefers-reduced-motion: reduce) { .eve-market-freshness__bar { transition: none; } }
</style>
