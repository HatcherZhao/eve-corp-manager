<script setup lang="ts">
import OpportunitiesPage from './opportunities/index.vue'

/** 保留旧菜单路径的兼容入口；新菜单默认指向机会分析页面。 */
defineOptions({ name: 'EveIndustry' })
</script>

<template>
  <OpportunitiesPage />
</template>
