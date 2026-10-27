<!--
  @file Layout 组件
  @description 布局根组件，支持默认布局和双列布局
-->
<template>
  <component :is="currentLayout" />
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores'

/** 组件名称 */
defineOptions({ name: 'Layout' })
const LayoutDefault = defineAsyncComponent(() => import('./LayoutDefault.vue'))
const LayoutColumns = defineAsyncComponent(() => import('./LayoutColumns.vue'))

/** 状态管理 */
const appStore = useAppStore()

/** 布局组件映射 */
const layoutMap = {
  left: LayoutDefault,
  columns: LayoutColumns,
} as const

/** 当前布局组件 */
const currentLayout = computed(() =>
  layoutMap[appStore.layout as keyof typeof layoutMap] || layoutMap.left,
)
</script>

<style lang="scss" scoped></style>
