<template>
  <a-range-picker
    :placeholder="placeholder"
    :format="format"
    :show-time="showTime"
    :shortcuts="shortcuts"
    shortcuts-position="left"
    style="height: 32px"
    :allow-clear="allowClear"
  />
</template>

<script setup lang="ts">
import type { ShortcutType } from '@arco-design/web-vue'
import { nowEveTime } from '@/utils/eveTime'

defineOptions({ name: 'DateRangePicker' })

defineProps({
  format: {
    type: String,
    default: 'YYYY-MM-DD HH:mm:ss',
  },
  showTime: {
    type: Boolean,
    default: true,
  },
  placeholder: {
    type: Array as PropType<string[]>,
    default: (): string[] => ['开始时间', '结束时间'],
  },
  allowClear: {
    type: Boolean,
    default: true,
  },
})

const shortcuts = computed<ShortcutType[]>(() => {
  return [
    {
      label: '今天',
      format: 'YYYY-MM-DD HH:mm:ss',
      value: (): string[] => [nowEveTime().startOf('day').format('YYYY-MM-DD HH:mm:ss'), nowEveTime().format('YYYY-MM-DD HH:mm:ss')],
    },
    {
      label: '昨天',
      format: 'YYYY-MM-DD HH:mm:ss',
      value: (): string[] => [
        nowEveTime().subtract(1, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss'),
        nowEveTime().subtract(1, 'day').endOf('day').format('YYYY-MM-DD HH:mm:ss'),
      ],
    },
    {
      label: '本周',
      format: 'YYYY-MM-DD HH:mm:ss',
      value: (): string[] => [nowEveTime().startOf('day').subtract((nowEveTime().day() + 6) % 7, 'day').format('YYYY-MM-DD HH:mm:ss'), nowEveTime().format('YYYY-MM-DD HH:mm:ss')],
    },
    {
      label: '本月',
      format: 'YYYY-MM-DD HH:mm:ss',
      value: (): string[] => [nowEveTime().startOf('month').format('YYYY-MM-DD HH:mm:ss'), nowEveTime().format('YYYY-MM-DD HH:mm:ss')],
    },
    {
      label: '本年',
      format: 'YYYY-MM-DD HH:mm:ss',
      value: (): string[] => [nowEveTime().startOf('year').format('YYYY-MM-DD HH:mm:ss'), nowEveTime().format('YYYY-MM-DD HH:mm:ss')],
    },
  ]
})
</script>

<style scoped lang="less"></style>
