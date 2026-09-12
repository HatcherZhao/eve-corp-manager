<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  type EveOfficialNews,
  type EveOfficialNewsSyncStatus,
  getEveOfficialNews,
  getEveOfficialNewsDetail,
  getEveOfficialNewsSyncStatus,
  syncEveOfficialNews,
} from '@/apis/eve'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveOfficialNews' })

const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const detailLoading = ref(false)
const syncing = ref(false)
const loadError = ref(false)
const records = ref<EveOfficialNews[]>([])
const total = ref(0)
const detail = ref<EveOfficialNews>()
const detailVisible = ref(false)
const syncStatus = ref<EveOfficialNewsSyncStatus>()
const query = reactive({ page: 1, size: 12, keyword: '', category: 'ALL' })

const isVersions = computed(() => route.path === '/eve/news/versions')
const canSync = computed(() => userStore.permissions.includes('eve:news:sync') || userStore.permissions.includes('*:*:*'))
const pageTitle = computed(() => isVersions.value ? '版本更新' : '国服新闻活动')
const pageDescription = computed(() => isVersions.value
  ? '集中阅读 EVE 国服的版本更新与维护说明。'
  : '集中阅读 EVE 国服官网发布的新闻、活动和公告。')
const categoryOptions = [
  { value: 'ALL', label: '全部栏目' },
  { value: 'NEWS', label: '新闻' },
  { value: 'MAINTENANCE', label: '维护' },
  { value: 'UPDATE_NOTICE', label: '更新通知' },
]

/** 将服务端 UTC 时间转为用户易读的本地时间。 */
function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

/** 仅将已同步的安全正文送入阅读抽屉，列表始终保持轻量。 */
async function openDetail(record: EveOfficialNews) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = undefined
  try {
    detail.value = (await getEveOfficialNewsDetail(record.originalUrl)).data
  } finally {
    detailLoading.value = false
  }
}

async function loadNews() {
  loading.value = true
  loadError.value = false
  try {
    const { data } = await getEveOfficialNews({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      category: query.category,
    })
    records.value = data.list
    total.value = data.total
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function loadSyncStatus() {
  try {
    syncStatus.value = (await getEveOfficialNewsSyncStatus()).data
  } catch {
    syncStatus.value = undefined
  }
}

function search() {
  query.page = 1
  loadNews()
}

function reset() {
  query.keyword = ''
  query.category = isVersions.value ? 'VERSION' : 'ALL'
  query.page = 1
  loadNews()
}

/** CEO 或总监手动检查时立即刷新列表与同步状态，成员不展示该操作。 */
async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveOfficialNews()
    Message.success(data.message)
    await Promise.all([loadNews(), loadSyncStatus()])
  } finally {
    syncing.value = false
  }
}

/** 切换二级菜单时复位筛选，确保版本更新不会混入普通新闻。 */
function resetForRoute() {
  query.page = 1
  query.keyword = ''
  query.category = isVersions.value ? 'VERSION' : 'ALL'
  loadNews()
}

onMounted(() => {
  resetForRoute()
  loadSyncStatus()
})

watch(() => route.path, resetForRoute)
</script>

<template>
  <main class="eve-official-news gi_page">
    <header class="eve-official-news__header">
      <div class="eve-official-news__heading">
        <span class="eve-official-news__eyebrow">SERENITY · OFFICIAL INTELLIGENCE</span>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageDescription }}内容每 10 分钟自动检查一次，阅读页保留官网原文入口。</p>
      </div>
      <div class="eve-official-news__actions">
        <span v-if="syncStatus?.lastSuccessfulAt" class="eve-official-news__freshness">最近检查：{{ formatTime(syncStatus.lastSuccessfulAt) }}</span>
        <span v-else class="eve-official-news__freshness">等待首次官网检查</span>
        <a-button v-if="canSync" type="primary" :loading="syncing" @click="sync">
          <template #icon><icon-sync /></template>检查官网更新
        </a-button>
      </div>
    </header>

    <a-alert v-if="syncStatus?.lastFailureAt" type="warning" :show-icon="true" class="eve-official-news__sync-warning">
      最近检查有异常：{{ syncStatus.lastFailureMessage || '官网暂时无法访问' }}。已保留上一次成功同步的资讯，将在下一轮自动重试。
    </a-alert>

    <section class="eve-official-news__filter-bar">
      <a-select v-if="!isVersions" v-model="query.category" :options="categoryOptions" placeholder="选择官网栏目" @change="search" />
      <a-input v-model="query.keyword" allow-clear placeholder="搜索标题、摘要或正文" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
      <span class="eve-official-news__count">共 {{ total.toLocaleString() }} 篇</span>
    </section>

    <section class="eve-official-news__list">
      <a-result v-if="loadError" status="error" title="资讯加载失败" subtitle="请稍后重试；已同步的官网资讯不会因临时失败丢失。">
        <template #extra><a-button type="primary" @click="loadNews">重新加载</a-button></template>
      </a-result>
      <a-empty v-else-if="!loading && !records.length" description="尚未同步到官网资讯">
        <template #extra><a-button v-if="canSync" type="primary" :loading="syncing" @click="sync">立即检查官网</a-button></template>
      </a-empty>
      <a-spin v-else :loading="loading" class="eve-official-news__loading">
        <article v-for="record in records" :key="record.originalUrl" class="eve-official-news__item" @click="openDetail(record)">
          <img v-if="record.coverUrl" class="eve-official-news__cover" :src="record.coverUrl" :alt="`${record.title}封面`" loading="lazy">
          <div class="eve-official-news__item-main">
            <div class="eve-official-news__item-meta">
              <a-tag color="arcoblue" size="small">{{ record.categoryLabel }}</a-tag>
              <time>{{ formatTime(record.publishedAt) }}</time>
            </div>
            <h2>{{ record.title }}</h2>
            <p>{{ record.summary || '官网未提供摘要，点击阅读完整内容。' }}</p>
          </div>
          <a-button type="text" class="eve-official-news__read-button" @click.stop="openDetail(record)">阅读 <icon-right /></a-button>
        </article>
      </a-spin>
    </section>

    <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadNews" @page-size-change="search" />

    <a-drawer v-model:visible="detailVisible" width="min(780px, 100vw)" :footer="false" unmount-on-close class="eve-official-news__drawer">
      <template #title>官网资讯详情</template>
      <a-spin :loading="detailLoading" class="eve-official-news__detail-loading">
        <article v-if="detail" class="eve-official-news__article">
          <div class="eve-official-news__article-meta"><a-tag color="arcoblue">{{ detail.categoryLabel }}</a-tag><time>{{ formatTime(detail.publishedAt) }}</time></div>
          <h2>{{ detail.title }}</h2>
          <p v-if="detail.summary" class="eve-official-news__article-summary">{{ detail.summary }}</p>
          <!-- 正文已在服务端按固定标签、链接与图片域名白名单净化。 -->
          <div v-if="detail.contentHtml" class="eve-official-news__article-content" v-html="detail.contentHtml" />
          <p v-else class="eve-official-news__article-empty">正文暂未完成同步，请稍后重新打开。</p>
          <a :href="detail.originalUrl" target="_blank" rel="noopener noreferrer" class="eve-official-news__source-link">在国服官网查看原文 <icon-launch /></a>
        </article>
      </a-spin>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-official-news { display: grid; gap: 12px; min-width: 0; }
.eve-official-news__header { display: grid; gap: 12px; padding: 18px; border: 1px solid rgba(var(--arcoblue-6), .2); border-radius: 12px; background: linear-gradient(120deg, rgba(var(--arcoblue-6), .12), transparent 60%), var(--color-bg-1); }
.eve-official-news__heading { min-width: 0; }
.eve-official-news__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.eve-official-news h1 { margin: 5px 0; font-size: clamp(22px, 5vw, 26px); }
.eve-official-news__heading p { margin: 0; color: var(--color-text-3); line-height: 1.6; }
.eve-official-news__actions, .eve-official-news__filter-bar, .eve-official-news__item-meta, .eve-official-news__article-meta { display: flex; align-items: center; gap: 8px; }
.eve-official-news__actions { flex-wrap: wrap; }
.eve-official-news__freshness { color: var(--color-text-3); font-size: 13px; }
.eve-official-news__filter-bar { flex-wrap: wrap; padding: 10px 12px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.eve-official-news__filter-bar :deep(.arco-input-wrapper) { width: 100%; min-width: 0; }
.eve-official-news__filter-bar :deep(.arco-select) { width: 100%; }
.eve-official-news__count { width: 100%; color: var(--color-text-3); font-size: 13px; }
.eve-official-news__list { overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 10px; background: var(--color-bg-1); }
.eve-official-news__loading { display: block; min-height: 180px; }
.eve-official-news__item { display: flex; gap: 12px; align-items: flex-start; min-width: 0; padding: 14px; cursor: pointer; transition: background-color .2s ease; }
.eve-official-news__item + .eve-official-news__item { border-top: 1px solid var(--color-border-2); }
.eve-official-news__item:hover { background: var(--color-fill-1); }
.eve-official-news__cover { flex: 0 0 88px; width: 88px; height: 60px; object-fit: cover; border-radius: 6px; background: var(--color-fill-2); }
.eve-official-news__item-main { flex: 1; min-width: 0; }
.eve-official-news__item-meta { flex-wrap: wrap; color: var(--color-text-3); font-size: 12px; }
.eve-official-news__item h2 { margin: 7px 0 5px; overflow: hidden; font-size: 16px; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.eve-official-news__item p { display: -webkit-box; margin: 0; overflow: hidden; color: var(--color-text-3); line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.eve-official-news__read-button { align-self: center; flex: none; }
.eve-official-news :deep(.arco-pagination) { justify-content: flex-end; }
.eve-official-news__detail-loading { display: block; min-height: 160px; }
.eve-official-news__article { min-width: 0; }
.eve-official-news__article h2 { margin: 12px 0; font-size: clamp(20px, 5vw, 26px); line-height: 1.4; overflow-wrap: anywhere; }
.eve-official-news__article-summary { margin: 0 0 20px; color: var(--color-text-3); line-height: 1.7; }
.eve-official-news__article-content { color: var(--color-text-1); font-size: 15px; line-height: 1.8; overflow-wrap: anywhere; }
.eve-official-news__article-content :deep(img) { display: block; width: auto; max-width: 100%; height: auto; margin: 16px auto; border-radius: 6px; }
.eve-official-news__article-content :deep(a) { color: rgb(var(--arcoblue-6)); overflow-wrap: anywhere; }
.eve-official-news__article-content :deep(table) { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
.eve-official-news__article-content :deep(td), .eve-official-news__article-content :deep(th) { padding: 6px 8px; border: 1px solid var(--color-border-2); }
.eve-official-news__article-empty { color: var(--color-text-3); }
.eve-official-news__source-link { display: inline-flex; align-items: center; gap: 4px; margin-top: 24px; color: rgb(var(--arcoblue-6)); }

@media (min-width: 760px) {
  .eve-official-news__header { grid-template-columns: minmax(0, 1fr) auto; align-items: end; padding: 20px 22px; }
  .eve-official-news__actions { justify-content: flex-end; }
  .eve-official-news__filter-bar :deep(.arco-input-wrapper) { width: min(420px, 100%); }
  .eve-official-news__filter-bar :deep(.arco-select) { width: 132px; }
  .eve-official-news__count { width: auto; margin-left: auto; }
  .eve-official-news__item { padding: 16px 18px; }
  .eve-official-news__cover { flex-basis: 128px; width: 128px; height: 80px; }
}
</style>
