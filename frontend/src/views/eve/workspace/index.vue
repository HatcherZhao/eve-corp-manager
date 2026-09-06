<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { type Workspace, getWorkspace } from '@/apis/eve'

defineOptions({ name: 'EveWorkspace' })

const workspace = ref<Workspace>()
const loading = ref(false)
const failed = ref(false)

async function loadWorkspace() {
  loading.value = true
  failed.value = false
  try {
    const { data } = await getWorkspace()
    workspace.value = data
  } catch {
    workspace.value = undefined
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadWorkspace)
</script>

<template>
  <div class="eve-workspace">
    <a-page-header title="军团工作台" subtitle="EVE Online · 网易国服" :show-back="false" />
    <a-alert type="info" class="intro">
      游戏账号接入尚未开放。当前登录的是本站管理账号，暂无军团数据。
    </a-alert>
    <a-spin :loading="loading" class="content">
      <a-result v-if="failed" status="error" title="无法加载工作台" subtitle="请检查服务连接和本站访问权限。">
        <template #extra>
          <a-button type="primary" @click="loadWorkspace">重试</a-button>
        </template>
      </a-result>
      <a-row v-else-if="workspace" :gutter="[16, 16]">
        <a-col v-for="item in workspace.capabilities" :key="item.key" :xs="24" :sm="12" :lg="8">
          <a-card :title="item.title">
            <template #extra><a-tag>暂未开放</a-tag></template>
            <p>{{ item.description }}</p>
            <a-empty description="暂无数据" />
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<style scoped lang="scss">
.eve-workspace {
  padding: 16px;
  .intro { margin: 16px 0 24px; }
  .content { display: block; min-height: 240px; }
  p { color: var(--color-text-2); margin-bottom: 24px; }
}
</style>
