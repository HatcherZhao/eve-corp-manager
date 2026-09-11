<script setup lang="ts">
import { Message, Modal } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthorizationActions from '../components/AuthorizationActions.vue'
import {
  type EveGameMailDetail,
  type EveGameMailLabel,
  type EveGameMailParty,
  type EveGameMailSendReq,
  type EveGameMailSummary,
  type EveMailRecipientInput,
  type EveMailRecipientType,
  getEveGameMail,
  getEveGameMailLabels,
  getEveGameMails,
  markEveGameMailRead,
  resolveEveGameMailRecipient,
  sendEveGameMail,
  syncEveGameMails,
} from '@/apis/eve'

defineOptions({ name: 'EveGameMail' })

type RecipientLookupStatus = 'idle' | 'waiting' | 'resolving' | 'resolved' | 'unresolved'
type SendRecipient = EveMailRecipientInput & {
  key: number
  lookupStatus: RecipientLookupStatus
  party?: EveGameMailParty
}

const recipientTypeOptions: Array<{ label: string, value: EveMailRecipientInput['recipientType'] }> = [
  { label: '游戏角色', value: 'character' },
  { label: '军团', value: 'corporation' },
  { label: '联盟', value: 'alliance' },
]
const recipientTypeLabels: Record<EveMailRecipientType, string> = {
  character: '游戏角色',
  corporation: '军团',
  alliance: '联盟',
  mailing_list: '邮件列表',
}

type MailWorkspace = 'inbox' | 'sent' | 'compose'

const route = useRoute()
const router = useRouter()
const workspace = computed<MailWorkspace>(() => {
  if (route.path === '/eve/mail/sent') return 'sent'
  if (route.path === '/eve/mail/compose') return 'compose'
  return 'inbox'
})
const workspaceTitle = computed(() => ({
  inbox: '收件箱',
  sent: '已发送',
  compose: '写邮件',
})[workspace.value])

const loading = ref(false)
const syncing = ref(false)
const loadError = ref(false)
const mailAuthorizationRequired = ref(false)
const records = ref<EveGameMailSummary[]>([])
const total = ref(0)
const labels = ref<EveGameMailLabel[]>([])
const query = reactive({ page: 1, size: 20, keyword: '', labelId: undefined as number | undefined })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref(false)
const detail = ref<EveGameMailDetail>()
const markingRead = ref(false)

const sendVisible = ref(false)
const sending = ref(false)
let nextRecipientKey = 1
const recipientLookupTimers = new Map<number, ReturnType<typeof setTimeout>>()
const sendForm = reactive<{
  recipients: Array<SendRecipient>
  subject: string
  body: string
  approvedCost?: number
}>({
  recipients: [newSendRecipient()],
  subject: '',
  body: '',
  approvedCost: undefined,
})

function newSendRecipient(): SendRecipient {
  return { key: nextRecipientKey++, recipientName: '', recipientType: 'character', lookupStatus: 'idle' }
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function labelText(labels: number[]) {
  return labels.length
    ? labels.map((id) => labelName(id)).join('、')
    : '未归类'
}

function labelName(labelId: number) {
  return labels.value.find((item) => item.labelId === labelId)?.name || '游戏分类'
}

function partyText(party?: EveGameMailParty) {
  if (!party) return '上游未提供参与方'
  const type = recipientTypeLabels[party.type] || '参与方'
  return party.name ? `${type}：${party.name}` : `未解析${type}`
}

/**
 * 游戏邮件正文为上游 HTML；仅保留基础排版和 http(s) 链接，杜绝脚本、样式及危险协议进入管理端。
 */
function sanitizeMailBody(body?: string) {
  if (!body?.trim()) return '该邮件没有正文。'
  const parsed = new DOMParser().parseFromString(body, 'text/html')
  const allowedTags = new Set(['a', 'b', 'br', 'code', 'em', 'i', 'li', 'ol', 'p', 'pre', 'strong', 'u', 'ul'])
  parsed.body.querySelectorAll('base, embed, form, iframe, link, math, meta, object, script, style, svg').forEach((element) => element.remove())
  parsed.body.querySelectorAll('*').forEach((element) => {
    const tag = element.tagName.toLowerCase()
    if (!allowedTags.has(tag)) {
      element.replaceWith(...Array.from(element.childNodes))
      return
    }
    const href = tag === 'a' ? element.getAttribute('href')?.trim() : undefined
    element.getAttributeNames().forEach((name) => element.removeAttribute(name))
    if (!href) return
    try {
      const url = new URL(href, window.location.origin)
      if (url.protocol === 'http:' || url.protocol === 'https:') {
        element.setAttribute('href', url.href)
        element.setAttribute('target', '_blank')
        element.setAttribute('rel', 'noopener noreferrer')
      }
    } catch {
      // 无法解析的游戏内标记按普通文字展示，不创建可点击链接。
    }
  })
  return parsed.body.innerHTML.trim() || '该邮件没有正文。'
}

async function loadMails() {
  loading.value = true
  loadError.value = false
  mailAuthorizationRequired.value = false
  try {
    const { data } = await getEveGameMails({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
      labelId: query.labelId,
    })
    records.value = data.list
    total.value = data.total
  } catch (error) {
    loadError.value = true
    mailAuthorizationRequired.value = error instanceof Error && error.message.includes('缺少游戏内邮件授权')
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 直接读取国服原生文件夹，包含收件箱、已发送和角色自定义分类。 */
async function loadLabels() {
  try {
    labels.value = (await getEveGameMailLabels()).data
  } catch {
    labels.value = []
  }
}

/** 根据国服返回的原生标签名称定位收件箱或已发送，避免将上游分类 ID 暴露给用户。 */
function findNativeMailboxLabel(target: Exclude<MailWorkspace, 'compose'>) {
  const keywords = target === 'inbox'
    ? ['收件箱', 'inbox']
    : ['已发送', '发件箱', 'sent']
  return labels.value.find((label) => keywords.includes(label.name.trim().toLocaleLowerCase()))?.labelId
}

/** 切换二级菜单时重置对应的原生邮箱筛选；写邮件入口会直接打开编辑抽屉。 */
function applyWorkspace() {
  query.page = 1
  query.keyword = ''
  query.labelId = workspace.value === 'compose' ? undefined : findNativeMailboxLabel(workspace.value)
  detailVisible.value = false
  if (workspace.value === 'compose') openSend()
}

function search() {
  query.page = 1
  loadMails()
}

function reset() {
  Object.assign(query, {
    page: 1,
    keyword: '',
    labelId: workspace.value === 'compose' ? undefined : findNativeMailboxLabel(workspace.value),
  })
  loadMails()
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveGameMails()
    Message.success(data.message)
  } finally {
    syncing.value = false
  }
}

async function openDetail(record: EveGameMailSummary) {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = false
  detail.value = undefined
  try {
    detail.value = (await getEveGameMail(record.mailId)).data
    const index = records.value.findIndex((item) => item.mailId === record.mailId)
    if (index >= 0) records.value[index] = { ...records.value[index], read: detail.value.read }
  } catch {
    detailError.value = true
  } finally {
    detailLoading.value = false
  }
}

/** 当前授权已包含整理 Scope 时，将游戏内邮件的已读状态同步回国服。 */
async function markDetailRead() {
  if (!detail.value) return
  markingRead.value = true
  try {
    detail.value = (await markEveGameMailRead(detail.value.mailId)).data
    const index = records.value.findIndex((item) => item.mailId === detail.value?.mailId)
    if (index >= 0) records.value[index] = { ...records.value[index], read: true }
    Message.success('已同步为游戏内已读')
  } finally {
    markingRead.value = false
  }
}

function resetSendForm() {
  clearRecipientLookupTimers()
  sendForm.recipients.splice(0, sendForm.recipients.length, newSendRecipient())
  sendForm.subject = ''
  sendForm.body = ''
  sendForm.approvedCost = undefined
}

function openSend() {
  resetSendForm()
  sendVisible.value = true
}

function addRecipient() {
  sendForm.recipients.push(newSendRecipient())
}

function removeRecipient(index: number) {
  if (sendForm.recipients.length === 1) {
    Message.warning('至少需要一名收件人')
    return
  }
  const [removed] = sendForm.recipients.splice(index, 1)
  if (removed) clearRecipientLookupTimer(removed.key)
}

/** 清除页面关闭或输入变化后未执行的收件人反查任务。 */
function clearRecipientLookupTimer(key: number) {
  const timer = recipientLookupTimers.get(key)
  if (timer !== undefined) clearTimeout(timer)
  recipientLookupTimers.delete(key)
}

/** 重新打开写信抽屉前清除全部防抖任务，避免旧输入覆盖新表单状态。 */
function clearRecipientLookupTimers() {
  recipientLookupTimers.forEach((timer) => clearTimeout(timer))
  recipientLookupTimers.clear()
}

/** 输入停止后预校验收件人名称；发送时仍会在服务端再次解析，避免竞态误投。 */
function scheduleRecipientLookup(recipient: SendRecipient) {
  clearRecipientLookupTimer(recipient.key)
  recipient.party = undefined
  if (!recipient.recipientName.trim()) {
    recipient.lookupStatus = 'idle'
    return
  }
  recipient.lookupStatus = 'waiting'
  recipientLookupTimers.set(recipient.key, setTimeout(() => resolveRecipient(recipient), 450))
}

/** 向本站只读反查接口确认名称、类型和最终游戏 ID。 */
async function resolveRecipient(recipient: SendRecipient) {
  clearRecipientLookupTimer(recipient.key)
  const requestedName = recipient.recipientName.trim()
  const requestedType = recipient.recipientType
  if (!requestedName) return
  recipient.lookupStatus = 'resolving'
  try {
    const { data } = await resolveEveGameMailRecipient({ recipientName: requestedName, recipientType: requestedType })
    if (recipient.recipientName.trim() === requestedName && recipient.recipientType === requestedType) {
      recipient.party = data
      recipient.lookupStatus = 'resolved'
    }
  } catch {
    if (recipient.recipientName.trim() === requestedName && recipient.recipientType === requestedType) {
      recipient.party = undefined
      recipient.lookupStatus = 'unresolved'
    }
  }
}

function normalizedRecipients(): EveMailRecipientInput[] {
  return sendForm.recipients.map((recipient) => ({
    recipientName: recipient.recipientName.trim(),
    recipientType: recipient.recipientType,
  }))
}

function validateSend() {
  const recipients = normalizedRecipients()
  if (!recipients.length || recipients.some((recipient) => !recipient.recipientName)) {
    Message.warning('请填写收件人的游戏内名称')
    return false
  }
  if (sendForm.recipients.some((recipient) => recipient.lookupStatus !== 'resolved')) {
    sendForm.recipients.filter((recipient) => recipient.recipientName && recipient.lookupStatus !== 'resolving')
      .forEach(scheduleRecipientLookup)
    Message.warning('请等待所有收件人名称校验通过后再发送')
    return false
  }
  if (recipients.length > 50) {
    Message.warning('单封邮件最多可添加 50 名收件人')
    return false
  }
  if (!sendForm.subject.trim()) {
    Message.warning('请输入邮件主题')
    return false
  }
  if (!sendForm.body.trim()) {
    Message.warning('请输入邮件正文')
    return false
  }
  return true
}

function confirmSend() {
  if (!validateSend()) return
  Modal.warning({
    title: '确认立即发送游戏内邮件',
    content: `这封邮件将以当前授权角色立即写入游戏并发送给 ${sendForm.recipients.length} 名收件人。发送后无法在本系统撤回，是否继续？`,
    okText: '确认发送',
    cancelText: '返回检查',
    hideCancel: false,
    onOk: submitSend,
  })
}

async function submitSend() {
  sending.value = true
  try {
    const payload: EveGameMailSendReq = {
      recipients: normalizedRecipients(),
      subject: sendForm.subject.trim(),
      body: sendForm.body.trim(),
      approvedCost: sendForm.approvedCost,
    }
    await sendEveGameMail(payload)
    Message.success('游戏内邮件已发送')
    sendVisible.value = false
    await router.push('/eve/mail/sent')
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  await loadLabels()
  applyWorkspace()
  await loadMails()
})
onBeforeUnmount(clearRecipientLookupTimers)

watch(() => route.path, async () => {
  applyWorkspace()
  if (workspace.value !== 'compose') await loadMails()
})
</script>

<template>
  <main class="eve-mail-page gi_page">
    <header class="eve-mail-page__header">
      <div>
        <span class="eve-mail-page__eyebrow">CHARACTER MAIL · SERENITY ESI</span>
        <h1>{{ workspaceTitle }}</h1>
        <p>收件箱和已发送均按国服原生分类筛选；也可在下方查看当前角色的其他游戏内分类。</p>
      </div>
      <div class="eve-mail-page__actions">
        <a-button :loading="syncing" @click="sync"><template #icon><icon-sync /></template>同步收件箱</a-button>
        <a-button type="primary" @click="openSend"><template #icon><icon-send /></template>写邮件</a-button>
      </div>
    </header>

    <a-alert class="eve-mail-page__guide" type="warning" :show-icon="true">
      收件箱和发件身份均属于<strong>当前登录用户绑定的游戏角色</strong>。发信会立即写入游戏，不是站内草稿或模拟消息。
    </a-alert>

    <section class="eve-mail-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索主题或发件人" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-select v-model="query.labelId" allow-clear placeholder="全部游戏分类" @change="search">
        <a-option v-for="label in labels" :key="label.labelId" :value="label.labelId">
          {{ label.name }}（{{ label.unreadCount }} 未读）
        </a-option>
      </a-select>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
      <span class="eve-mail-page__count">共 {{ total.toLocaleString() }} 封邮件</span>
    </section>

    <section class="eve-mail-page__table-wrap">
      <a-result
        v-if="loadError && !loading"
        :status="mailAuthorizationRequired ? 'warning' : 'error'"
        :title="mailAuthorizationRequired ? '需要补充游戏内邮件授权' : '收件箱加载失败'"
        :subtitle="mailAuthorizationRequired ? '当前角色尚未授予读取邮件权限。重新授权后可查看、同步和读取邮件正文。' : '请检查游戏授权状态或稍后重试。'"
      >
        <template #extra>
          <AuthorizationActions v-if="mailAuthorizationRequired" @refreshed="loadMails" />
          <a-button v-else type="primary" @click="loadMails">重新加载</a-button>
        </template>
      </a-result>
      <a-empty v-else-if="!loading && !records.length" description="当前没有已同步的游戏内邮件">
        <a-button type="primary" :loading="syncing" @click="sync">立即同步</a-button>
      </a-empty>
      <template v-else>
        <a-table :data="records" :loading="loading" :pagination="false" row-key="mailId" :scroll="{ x: 885 }">
          <template #columns>
            <a-table-column title="状态" :width="70">
              <template #cell="{ record }"><a-tag :color="record.read ? 'gray' : 'arcoblue'">{{ record.read ? '已读' : '未读' }}</a-tag></template>
            </a-table-column>
            <a-table-column title="主题" :width="250" ellipsis tooltip>
              <template #cell="{ record }"><strong>{{ record.subject || '（无主题）' }}</strong></template>
            </a-table-column>
            <a-table-column title="发件人" :width="150" ellipsis tooltip><template #cell="{ record }">{{ partyText(record.from) }}</template></a-table-column>
            <a-table-column title="收件人" :width="80"><template #cell="{ record }">{{ record.recipients.length }} 人</template></a-table-column>
            <a-table-column title="分类" :width="120" ellipsis tooltip><template #cell="{ record }">{{ labelText(record.labels) }}</template></a-table-column>
            <a-table-column title="时间" :width="135"><template #cell="{ record }">{{ formatTime(record.sentAt) }}</template></a-table-column>
            <a-table-column title="操作" :width="80" fixed="right"><template #cell="{ record }"><a-button type="text" @click="openDetail(record)">查看</a-button></template></a-table-column>
          </template>
        </a-table>
        <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadMails" @page-size-change="search" />
      </template>
    </section>

    <a-drawer v-model:visible="detailVisible" width="640px" :footer="false" unmount-on-close title="游戏内邮件详情">
      <a-spin :loading="detailLoading" class="eve-mail-page__detail-loading">
        <a-result v-if="detailError" status="error" title="邮件正文读取失败" subtitle="请确认邮件仍属于当前授权角色，并检查读取邮件授权。" />
        <template v-else-if="detail">
          <div class="eve-mail-page__detail-heading">
            <a-tag :color="detail.read ? 'gray' : 'arcoblue'">{{ detail.read ? '已读' : '未读' }}</a-tag>
            <h2>{{ detail.subject || '（无主题）' }}</h2>
            <span>{{ partyText(detail.from) }} · {{ formatTime(detail.sentAt) }}</span>
          </div>
          <section class="eve-mail-page__recipients">
            <h3>收件人</h3>
            <div>
              <a-tag v-for="recipient in detail.recipients" :key="`${recipient.type}-${recipient.id || recipient.name}`">
                {{ partyText(recipient) }}
              </a-tag>
              <span v-if="!detail.recipients.length">上游未返回收件人</span>
            </div>
          </section>
          <section class="eve-mail-page__body">
            <h3>正文</h3>
            <div class="eve-mail-page__body-content" v-html="sanitizeMailBody(detail.body)" />
          </section>
          <footer class="eve-mail-page__detail-meta">
            <span>标签：{{ labelText(detail.labels) }}</span>
            <span>正文同步：{{ formatTime(detail.bodySynchronizedAt) }}</span>
            <span>正文缓存到期：{{ formatTime(detail.bodySourceExpiresAt) }}</span>
            <a-button v-if="!detail.read" size="small" type="outline" :loading="markingRead" @click="markDetailRead">标为已读</a-button>
          </footer>
        </template>
      </a-spin>
    </a-drawer>

    <a-drawer v-model:visible="sendVisible" width="680px" unmount-on-close title="发送游戏内邮件">
      <a-alert type="warning" :show-icon="true">
        <strong>这不是站内消息。</strong>提交后将以当前授权角色立即向游戏内收件人发送，发送结果受游戏接口和角色余额限制。
      </a-alert>
      <a-form class="eve-mail-page__send-form" :model="sendForm" layout="vertical">
        <a-form-item label="收件人" required>
          <div class="eve-mail-page__recipient-list">
            <div v-for="(recipient, index) in sendForm.recipients" :key="recipient.key" class="eve-mail-page__recipient-row">
              <a-select v-model="recipient.recipientType" :options="recipientTypeOptions" @change="scheduleRecipientLookup(recipient)" />
              <div class="eve-mail-page__recipient-input">
                <a-input v-model="recipient.recipientName" :max-length="100" placeholder="输入游戏内角色、军团或联盟名称" @input="scheduleRecipientLookup(recipient)" @blur="resolveRecipient(recipient)" />
                <span v-if="recipient.lookupStatus === 'waiting' || recipient.lookupStatus === 'resolving'" class="eve-mail-page__recipient-lookup eve-mail-page__recipient-lookup--loading">正在查询国服身份…</span>
                <span v-else-if="recipient.lookupStatus === 'resolved'" class="eve-mail-page__recipient-lookup eve-mail-page__recipient-lookup--success">已匹配：{{ partyText(recipient.party) }}</span>
                <span v-else-if="recipient.lookupStatus === 'unresolved'" class="eve-mail-page__recipient-lookup eve-mail-page__recipient-lookup--error">未找到该名称或收件人类型不符</span>
              </div>
              <a-button status="danger" type="text" @click="removeRecipient(index)"><template #icon><icon-delete /></template></a-button>
            </div>
            <a-button v-if="sendForm.recipients.length < 50" type="dashed" long @click="addRecipient"><template #icon><icon-plus /></template>添加收件人</a-button>
          </div>
          <template #extra>填写游戏内显示的完整名称；系统会在发送前按所选类型确认唯一收件对象。</template>
        </a-form-item>
        <a-form-item label="主题" required>
          <a-input v-model="sendForm.subject" :max-length="1000" show-word-limit placeholder="输入邮件主题" />
        </a-form-item>
        <a-form-item label="正文" required>
          <a-textarea v-model="sendForm.body" :max-length="10000" show-word-limit :auto-size="{ minRows: 10, maxRows: 18 }" placeholder="输入游戏内邮件正文" />
          <template #extra>发送时按普通文本提交；阅读上游邮件时会保留安全的基础格式和可点击的网页链接。</template>
        </a-form-item>
        <a-form-item label="批准费用（ISK，可选）">
          <a-input-number v-model="sendForm.approvedCost" :min="0" :precision="0" hide-button placeholder="游戏要求收费时允许支付的最高费用" />
          <template #extra>通常可留空；向邮件列表发送时，上游可能按列表设置收取费用。</template>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button @click="sendVisible = false">取消</a-button>
        <a-button type="primary" :loading="sending" @click="confirmSend">检查并发送</a-button>
      </template>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-mail-page { color: var(--color-text-1); }
.eve-mail-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-mail-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-mail-page h1 { margin: 5px 0; font-size: 26px; }
.eve-mail-page__header p { max-width: 720px; margin: 0; color: var(--color-text-3); }
.eve-mail-page__actions { display: flex; gap: 10px; flex-shrink: 0; }
.eve-mail-page__guide { margin-top: 12px; }
.eve-mail-page__filter-bar { display: flex; align-items: center; gap: 8px; margin: 12px 0; padding: 10px 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-mail-page__filter-bar .arco-input-wrapper:first-child { width: min(340px, 100%); }
.eve-mail-page__filter-bar .arco-input-wrapper:nth-child(2) { width: 220px; }
.eve-mail-page__count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-mail-page__table-wrap { min-height: 240px; padding: 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-mail-page__table-wrap small { display: block; margin-top: 3px; color: var(--color-text-3); }
.eve-mail-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 12px; }
.eve-mail-page__table-wrap > .arco-empty, .eve-mail-page__table-wrap > .arco-result { padding: 68px 0; }
.eve-mail-page__detail-loading { display: block; min-height: 320px; }
.eve-mail-page__detail-heading { padding-bottom: 18px; border-bottom: 1px solid var(--color-border-2); }
.eve-mail-page__detail-heading h2 { margin: 12px 0 7px; font-size: 23px; line-height: 1.35; }
.eve-mail-page__detail-heading span, .eve-mail-page__detail-meta { color: var(--color-text-3); font-size: 13px; }
.eve-mail-page__recipients, .eve-mail-page__body { margin-top: 24px; }
.eve-mail-page__recipients h3, .eve-mail-page__body h3 { margin: 0 0 12px; font-size: 15px; }
.eve-mail-page__recipients > div { display: flex; flex-wrap: wrap; gap: 6px; }
.eve-mail-page__body-content { min-height: 180px; padding: 18px; overflow-wrap: anywhere; border: 1px solid var(--color-border-2); border-radius: 10px; background: var(--color-fill-1); color: var(--color-text-2); line-height: 1.75; white-space: pre-wrap; }
.eve-mail-page__body-content :deep(p:first-child), .eve-mail-page__body-content :deep(ul:first-child), .eve-mail-page__body-content :deep(ol:first-child), .eve-mail-page__body-content :deep(pre:first-child) { margin-top: 0; }
.eve-mail-page__body-content :deep(p:last-child), .eve-mail-page__body-content :deep(ul:last-child), .eve-mail-page__body-content :deep(ol:last-child), .eve-mail-page__body-content :deep(pre:last-child) { margin-bottom: 0; }
.eve-mail-page__body-content :deep(a) { color: rgb(var(--arcoblue-6)); text-decoration: underline; text-underline-offset: 2px; }
.eve-mail-page__body-content :deep(pre) { overflow-x: auto; white-space: pre-wrap; }
.eve-mail-page__detail-meta { display: flex; flex-wrap: wrap; gap: 8px 18px; margin-top: 18px; }
.eve-mail-page__send-form { margin-top: 20px; }
.eve-mail-page__recipient-list { display: grid; width: 100%; gap: 9px; }
.eve-mail-page__recipient-row { display: grid; grid-template-columns: 150px minmax(0, 1fr) 36px; align-items: start; gap: 8px; width: 100%; }
.eve-mail-page__recipient-input { min-width: 0; }
.eve-mail-page__recipient-lookup { display: block; margin-top: 5px; font-size: 12px; line-height: 1.45; }
.eve-mail-page__recipient-lookup--loading { color: var(--color-text-3); }
.eve-mail-page__recipient-lookup--success { color: rgb(var(--green-6)); }
.eve-mail-page__recipient-lookup--error { color: rgb(var(--red-6)); }
.eve-mail-page__send-form .arco-input-number { width: 100%; }
@media (max-width: 760px) {
  .eve-mail-page__header { align-items: flex-start; flex-direction: column; }
  .eve-mail-page__actions { width: 100%; }
  .eve-mail-page__actions .arco-btn { flex: 1; }
  .eve-mail-page__filter-bar { flex-wrap: wrap; }
  .eve-mail-page__filter-bar .arco-input-wrapper, .eve-mail-page__filter-bar .arco-input-wrapper:first-child, .eve-mail-page__filter-bar .arco-input-wrapper:nth-child(2) { width: 100%; }
  .eve-mail-page__count { width: 100%; margin-left: 0; }
  .eve-mail-page__recipient-row { grid-template-columns: 120px minmax(0, 1fr) 36px; }
}
</style>
