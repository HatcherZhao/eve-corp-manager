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

const notificationTypeLabels: Record<string, string> = {
  MoonminingExtractionStarted: '月矿开始提取',
  MoonminingExtractionFinished: '月矿提取完成',
  MoonminingAutomaticFracture: '月矿自然碎裂',
  MoonminingLaserFired: '月矿激光启动',
  MoonminingExtractionCancelled: '月矿提取已取消',
  StructureFuelAlert: '建筑燃料预警',
  StructureWentHighPower: '建筑进入高能状态',
  StructureWentLowPower: '建筑进入低能状态',
  StructureServicesOffline: '建筑服务离线',
  StructureUnderAttack: '建筑遭受攻击',
  StructureImpendingAbandonmentAssetsAtRisk: '建筑即将废弃，资产存在风险',
  StructureAnchoring: '建筑开始锚定',
  StructureOnline: '建筑已上线',
  StructureUnanchoring: '建筑开始解除锚定',
  StructureItemsMovedToSafety: '建筑物资已移入资产安全',
  CorpAppNewMsg: '收到新的军团申请',
  CorpTaxChangeMsg: '军团税率变更',
  CharAppAcceptMsg: '军团申请已接受',
  CharAppWithdrawMsg: '军团申请已撤回',
  CharTerminationMsg: '角色军团关系变更',
  WarDeclared: '战争已宣战',
  WarRetractedByConcord: '战争已撤销',
}

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

function notificationTitle(record: EveGameNotification) {
  return notificationTypeLabels[record.type || ''] || '其他游戏通知'
}

/** 解码原始国服报文，供默认折叠的诊断区使用。 */
function readableContent(value?: string) {
  return (value || '')
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex: string) => String.fromCharCode(Number.parseInt(hex, 16)))
    .replace(/\\"/g, '"')
    .replace(/\\n/g, '\n')
}

/** 兼容服务端尚未升级时的旧通知响应，确保列表至少保留简短摘要。 */
function fieldValue(value: string | undefined, field: string) {
  const text = readableContent(value)
  const match = text.match(new RegExp(`(?:^|\\s)${field}:\\s*(?:"([^"\\n]+)"|([^\\s]+))`))
  return (match?.[1] || match?.[2] || '').trim()
}

function notificationSummary(record: EveGameNotification) {
  if (record.summary) return record.summary
  const structureName = fieldValue(record.content, 'structureName')
  const structure = structureName ? `建筑「${structureName}」` : '该建筑'
  switch (record.type) {
    case 'MoonminingExtractionStarted': return `${structure}已开始月矿提取。`
    case 'MoonminingExtractionFinished': return `${structure}的月矿提取已完成。`
    case 'MoonminingAutomaticFracture': return `${structure}的月矿已自然碎裂。`
    case 'MoonminingLaserFired': return `${structure}的月矿激光已启动。`
    case 'MoonminingExtractionCancelled': return `${structure}的月矿提取已取消。`
    case 'StructureFuelAlert': return `${structure}触发燃料预警。`
    case 'StructureWentHighPower': return `${structure}已进入高能状态。`
    case 'StructureWentLowPower': return `${structure}已进入低能状态。`
    case 'StructureServicesOffline': return `${structure}存在离线服务。`
    case 'StructureUnderAttack': return `${structure}正在遭受攻击。`
    case 'StructureImpendingAbandonmentAssetsAtRisk': return `${structure}即将废弃，资产存在风险。`
    case 'CorpTaxChangeMsg': {
      const previousRate = fieldValue(record.content, 'oldTaxRate')
      const currentRate = fieldValue(record.content, 'newTaxRate')
      return previousRate && currentRate ? `军团税率已从 ${previousRate} 调整为 ${currentRate}。` : '军团税率已变更。'
    }
    default: return `已收到${notificationTitle(record)}。`
  }
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
        <a-table :data="records" :loading="loading" :pagination="false" row-key="notificationId" :scroll="{ x: 820 }">
          <template #columns>
            <a-table-column title="状态" :width="70">
              <template #cell="{ record }"><a-tag :color="record.read ? 'gray' : 'arcoblue'">{{ record.read ? '已读' : '未读' }}</a-tag></template>
            </a-table-column>
            <a-table-column title="通知类型" :width="150" ellipsis tooltip>
              <template #cell="{ record }">{{ notificationTitle(record) }}</template>
            </a-table-column>
            <a-table-column title="发送方" :width="130" ellipsis tooltip>
              <template #cell="{ record }">{{ senderText(record) }}</template>
            </a-table-column>
            <a-table-column title="内容摘要" :min-width="220" ellipsis tooltip>
              <template #cell="{ record }">{{ notificationSummary(record) }}</template>
            </a-table-column>
            <a-table-column title="时间" :width="135"><template #cell="{ record }">{{ formatTime(record.sentAt) }}</template></a-table-column>
            <a-table-column title="操作" :width="80" fixed="right"><template #cell="{ record }"><a-button type="text" @click="openDetail(record)">查看</a-button></template></a-table-column>
          </template>
        </a-table>
        <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadNotifications" @page-size-change="search" />
      </template>
    </section>

    <a-drawer v-model:visible="detailVisible" width="640px" :footer="false" unmount-on-close title="游戏通知详情">
      <template v-if="detail">
        <div class="eve-notifications-page__detail-heading">
          <a-tag :color="detail.read ? 'gray' : 'arcoblue'">{{ detail.read ? '已读' : '未读' }}</a-tag>
          <h2>{{ notificationTitle(detail) }}</h2>
          <span>{{ senderText(detail) }} · {{ formatTime(detail.sentAt) }}</span>
        </div>
        <section class="eve-notifications-page__content">
          <h3>通知摘要</h3>
          <p>{{ notificationSummary(detail) }}</p>
          <template v-if="detail.details?.length">
            <h3>详细信息</h3>
            <a-descriptions :column="1" bordered size="small">
              <a-descriptions-item v-for="item in detail.details" :key="item.label" :label="item.label">
                {{ item.value }}
              </a-descriptions-item>
            </a-descriptions>
          </template>
          <a-collapse v-if="detail.content" class="eve-notifications-page__raw-content">
            <a-collapse-item key="raw" header="技术原文（仅供排查通知类型）">
              <pre>{{ readableContent(detail.content) }}</pre>
            </a-collapse-item>
          </a-collapse>
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
.eve-notifications-page { display: grid; gap: 12px; }
.eve-notifications-page__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.eve-notifications-page__eyebrow { color: rgb(var(--primary-6)); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.eve-notifications-page__header h1 { margin: 6px 0; font-size: 26px; }
.eve-notifications-page__header p { margin: 0; color: var(--color-text-2); }
.eve-notifications-page__actions, .eve-notifications-page__filter-bar { display: flex; align-items: center; gap: 8px; }
.eve-notifications-page__filter-bar { flex-wrap: wrap; padding: 10px 12px; border: 1px solid var(--color-neutral-3); border-radius: 8px; background: var(--color-bg-2); }
.eve-notifications-page__filter-bar :deep(.arco-input-wrapper) { width: min(400px, 100%); }
.eve-notifications-page__count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-notifications-page__table-wrap { overflow: hidden; border: 1px solid var(--color-neutral-3); border-radius: 8px; background: var(--color-bg-2); }
.eve-notifications-page__table-wrap :deep(.arco-pagination) { justify-content: flex-end; padding: 12px; }
.eve-notifications-page__detail-heading { display: grid; gap: 8px; }
.eve-notifications-page__detail-heading h2 { margin: 0; font-size: 20px; word-break: break-word; }
.eve-notifications-page__detail-heading span, .eve-notifications-page__detail-meta { color: var(--color-text-3); font-size: 13px; }
.eve-notifications-page__content { margin-top: 24px; }
.eve-notifications-page__content h3 { margin-bottom: 10px; }
.eve-notifications-page__raw-content { margin-top: 18px; }
.eve-notifications-page__content pre { margin: 0; padding: 14px; overflow: auto; white-space: pre-wrap; word-break: break-word; border-radius: 6px; background: var(--color-fill-2); color: var(--color-text-1); font-family: inherit; line-height: 1.7; }
.eve-notifications-page__detail-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 20px; }
@media (max-width: 760px) { .eve-notifications-page__header { flex-direction: column; } .eve-notifications-page__count { width: 100%; margin-left: 0; } }
</style>
