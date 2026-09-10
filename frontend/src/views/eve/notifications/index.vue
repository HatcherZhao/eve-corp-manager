<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AuthorizationActions from '../components/AuthorizationActions.vue'
import {
  type EveGameNotification,
  type EveGameNotificationCategory,
  getEveGameNotifications,
  syncEveGameNotifications,
} from '@/apis/eve'

defineOptions({ name: 'EveGameNotifications' })

const route = useRoute()
const categoryByPath: Record<string, EveGameNotificationCategory> = {
  '/eve/notifications/all': 'ALL',
  '/eve/notifications/corporation-members': 'CORPORATION_MEMBER',
  '/eve/notifications/structures-assets': 'STRUCTURE_ASSET_SAFETY',
  '/eve/notifications/war-sovereignty': 'WAR_SOVEREIGNTY',
  '/eve/notifications/moon-industry': 'MOON_INDUSTRY',
  '/eve/notifications/other': 'OTHER',
}
const categoryMeta: Record<EveGameNotificationCategory, { title: string, description: string }> = {
  ALL: { title: '全部游戏通知', description: '显示当前授权角色收到的全部游戏通知，未知的新通知类型会保留在“其他”。' },
  CORPORATION_MEMBER: { title: '军团与成员', description: '军团、联盟、成员、申请与角色变化等相关游戏通知。' },
  STRUCTURE_ASSET_SAFETY: { title: '建筑与资产安全', description: '建筑状态、资产安全、燃料和设施相关游戏通知。' },
  WAR_SOVEREIGNTY: { title: '战争与主权', description: '战争、主权与领地变化相关游戏通知。' },
  MOON_INDUSTRY: { title: '月矿与工业', description: '月矿、采矿、制造、研究与工业流程相关游戏通知。' },
  OTHER: { title: '其他游戏通知', description: '尚未归入固定分类的游戏通知；原始游戏类型会完整保留。' },
}

const activeCategory = computed(() => categoryByPath[route.path] || 'ALL')
const pageMeta = computed(() => categoryMeta[activeCategory.value])
const loading = ref(false)
const syncing = ref(false)
const loadError = ref(false)
const authorizationRequired = ref(false)
const records = ref<EveGameNotification[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detail = ref<EveGameNotification>()
const query = reactive({ page: 1, size: 20, keyword: '' })

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function senderText(record: EveGameNotification) {
  if (record.senderName) return record.senderName
  return record.senderType ? `${senderTypeLabel(record.senderType)}（上游未提供名称）` : '系统通知'
}

function senderTypeLabel(value?: string) {
  const labels: Record<string, string> = {
    character: '游戏角色',
    corporation: '军团',
    alliance: '联盟',
    faction: '势力',
  }
  return labels[value || ''] || '发送方'
}

function excerpt(value?: string) {
  const text = (value || '').replace(/\s+/g, ' ').trim()
  return text || '上游未提供通知正文'
}

async function loadNotifications() {
  loading.value = true
  loadError.value = false
  authorizationRequired.value = false
  try {
    const { data } = await getEveGameNotifications({
      page: query.page,
      size: query.size,
      category: activeCategory.value,
      keyword: query.keyword.trim() || undefined,
    })
    records.value = data.list
    total.value = data.total
  } catch (error) {
    loadError.value = true
    authorizationRequired.value = error instanceof Error && error.message.includes('缺少游戏通知授权')
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadNotifications()
}

function reset() {
  query.page = 1
  query.keyword = ''
  loadNotifications()
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveGameNotifications()
    Message.success(data.message)
  } finally {
    syncing.value = false
  }
}

function openDetail(record: EveGameNotification) {
  detail.value = record
  detailVisible.value = true
}

watch(() => route.path, () => {
  query.page = 1
  query.keyword = ''
  detailVisible.value = false
  loadNotifications()
})
onMounted(loadNotifications)
</script>

<template>
  <main class="eve-notifications-page gi_page">
    <header class="eve-notifications-page__header">
      <div>
        <span class="eve-notifications-page__eyebrow">CHARACTER NOTIFICATIONS · SERENITY ESI</span>
        <h1>{{ pageMeta.title }}</h1>
        <p>{{ pageMeta.description }}</p>
      </div>
      <div class="eve-notifications-page__actions">
        <a-button :loading="syncing" @click="sync"><template #icon><icon-sync /></template>同步通知</a-button>
      </div>
    </header>

    <a-alert class="eve-notifications-page__guide" type="info" :show-icon="true">
      游戏通知属于<strong>当前登录用户绑定的游戏角色</strong>。系统会自动低频同步；手动同步只会加入队列，仍会遵守国服缓存和限流。
    </a-alert>

    <section class="eve-notifications-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索通知内容或游戏类型" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
      <span class="eve-notifications-page__count">共 {{ total.toLocaleString() }} 条通知</span>
    </section>

    <section class="eve-notifications-page__table-wrap">
      <a-result
        v-if="loadError && !loading"
        :status="authorizationRequired ? 'warning' : 'error'"
        :title="authorizationRequired ? '需要补充游戏通知授权' : '游戏通知加载失败'"
        :subtitle="authorizationRequired ? '当前角色尚未授予读取游戏通知权限。重新授权后即可查看和自动同步通知。' : '请检查游戏授权状态或稍后重试。'"
      >
        <template #extra>
          <AuthorizationActions v-if="authorizationRequired" @refreshed="loadNotifications" />
          <a-button v-else type="primary" @click="loadNotifications">重新加载</a-button>
        </template>
      </a-result>
      <a-empty v-else-if="!loading && !records.length" description="当前分类没有已同步的游戏通知">
        <a-button type="primary" :loading="syncing" @click="sync">立即同步</a-button>
      </a-empty>
      <template v-else>
        <a-table :data="records" :loading="loading" :pagination="false" row-key="notificationId" :scroll="{ x: 1000 }">
          <template #columns>
            <a-table-column title="状态" :width="90">
              <template #cell="{ record }"><a-tag :color="record.read ? 'gray' : 'arcoblue'">{{ record.read ? '已读' : '未读' }}</a-tag></template>
            </a-table-column>
            <a-table-column title="游戏类型" :width="220" ellipsis tooltip>
              <template #cell="{ record }">{{ record.type || '上游未提供类型' }}</template>
            </a-table-column>
            <a-table-column title="发送方" :width="180" ellipsis tooltip>
              <template #cell="{ record }">{{ senderText(record) }}</template>
            </a-table-column>
            <a-table-column title="内容摘要" :min-width="320" ellipsis tooltip>
              <template #cell="{ record }">{{ excerpt(record.content) }}</template>
            </a-table-column>
            <a-table-column title="发生时间" :width="180"><template #cell="{ record }">{{ formatTime(record.sentAt) }}</template></a-table-column>
            <a-table-column title="操作" :width="100" fixed="right"><template #cell="{ record }"><a-button type="text" @click="openDetail(record)">查看详情</a-button></template></a-table-column>
          </template>
        </a-table>
        <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadNotifications" @page-size-change="search" />
      </template>
    </section>

    <a-drawer v-model:visible="detailVisible" width="640px" :footer="false" unmount-on-close title="游戏通知详情">
      <template v-if="detail">
        <div class="eve-notifications-page__detail-heading">
          <a-tag :color="detail.read ? 'gray' : 'arcoblue'">{{ detail.read ? '已读' : '未读' }}</a-tag>
          <h2>{{ detail.type || '游戏通知' }}</h2>
          <span>{{ senderText(detail) }} · {{ formatTime(detail.sentAt) }}</span>
        </div>
        <section class="eve-notifications-page__content">
          <h3>通知内容</h3>
          <pre>{{ detail.content || '上游未提供通知正文' }}</pre>
        </section>
        <footer class="eve-notifications-page__detail-meta">
          <span>系统分类：{{ categoryMeta[detail.category].title }}</span>
          <span>最近同步：{{ formatTime(detail.lastSeenAt) }}</span>
        </footer>
      </template>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-notifications-page { display: grid; gap: 18px; }
.eve-notifications-page__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.eve-notifications-page__eyebrow { color: rgb(var(--primary-6)); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.eve-notifications-page__header h1 { margin: 6px 0; font-size: 26px; }
.eve-notifications-page__header p { margin: 0; color: var(--color-text-2); }
.eve-notifications-page__actions, .eve-notifications-page__filter-bar { display: flex; align-items: center; gap: 12px; }
.eve-notifications-page__filter-bar { flex-wrap: wrap; padding: 16px; border: 1px solid var(--color-neutral-3); border-radius: 8px; background: var(--color-bg-2); }
.eve-notifications-page__filter-bar :deep(.arco-input-wrapper) { width: min(400px, 100%); }
.eve-notifications-page__count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-notifications-page__table-wrap { overflow: hidden; border: 1px solid var(--color-neutral-3); border-radius: 8px; background: var(--color-bg-2); }
.eve-notifications-page__table-wrap :deep(.arco-pagination) { justify-content: flex-end; padding: 16px; }
.eve-notifications-page__detail-heading { display: grid; gap: 8px; }
.eve-notifications-page__detail-heading h2 { margin: 0; font-size: 20px; word-break: break-word; }
.eve-notifications-page__detail-heading span, .eve-notifications-page__detail-meta { color: var(--color-text-3); font-size: 13px; }
.eve-notifications-page__content { margin-top: 24px; }
.eve-notifications-page__content h3 { margin-bottom: 10px; }
.eve-notifications-page__content pre { margin: 0; padding: 14px; overflow: auto; white-space: pre-wrap; word-break: break-word; border-radius: 6px; background: var(--color-fill-2); color: var(--color-text-1); font-family: inherit; line-height: 1.7; }
.eve-notifications-page__detail-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 20px; }
@media (max-width: 760px) { .eve-notifications-page__header { flex-direction: column; } .eve-notifications-page__count { width: 100%; margin-left: 0; } }
</style>
