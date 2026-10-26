<script setup lang="ts">
import { Message, Modal } from '@arco-design/web-vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  type EveAgentApplication,
  type EveAgentAudit,
  type EveAgentGrant,
  type EveAgentInvitation,
  approveEveAgentInvitation,
  createEveAgentApplication,
  getEveAgentApplicationGrants,
  getEveAgentApplications,
  getEveAgentAudits,
  getEveAgentCorporationGrants,
  getEveAgentInvitation,
  revokeEveAgentApplication,
  revokeEveAgentGrant,
  rotateEveAgentCredential,
} from '@/apis/eve/agents'
import { formatEveTime, parseEveTime } from '@/utils/eveTime'

defineOptions({ name: 'EveAgents' })

const route = useRoute()
const router = useRouter()
const applications = ref<EveAgentApplication[]>([])
const corporationGrants = ref<EveAgentGrant[]>([])
const applicationGrants = ref<EveAgentGrant[]>([])
const audits = ref<EveAgentAudit[]>([])
const auditTotal = ref(0)
const auditQuery = reactive({ page: 1, size: 20 })
const loading = ref(false)
const grantsLoading = ref(false)
const applicationGrantsLoading = ref(false)
const auditLoading = ref(false)
const loadError = ref(false)
const grantsLoadError = ref(false)
const applicationGrantsLoadError = ref(false)
const auditLoadError = ref(false)
const saving = ref(false)
const approving = ref(false)
const actingId = ref('')
const activeTab = ref('applications')
const createVisible = ref(false)
const credentialVisible = ref(false)
const applicationGrantsVisible = ref(false)
const invitationVisible = ref(false)
const invitationLoading = ref(false)
const invitationError = ref(false)
const invitationCode = ref('')
const invitation = ref<EveAgentInvitation>()
const invitationAcknowledged = ref(false)
const selectedApplication = ref<EveAgentApplication>()
const showSecret = ref(false)
const secret = ref('')
const credentialName = ref('')
const form = reactive({ name: '', expiresInDays: 90, acknowledgeMailAccess: false })
let applicationGrantsRequestId = 0
let invitationRequestId = 0
let approvalRequestId = 0
const endpoint = new URL(`${(import.meta.env.VITE_API_PREFIX || '/api').replace(/\/$/, '')}/mcp`, window.location.origin).href
const clientConfig = computed(() => JSON.stringify({
  mcpServers: { eve: { transport: 'streamable_http', url: endpoint, headers: { Authorization: `Bearer ${secret.value}` } } },
}, null, 2))
const toolLabels: Record<string, string> = {
  'eve_catalog': '查询数据目录',
  'eve_corporations': '查询已授权军团',
  'eve_query': '查询业务数据',
  'eve_aggregate': '统计汇总',
  'eve_read_document': '读取已缓存正文',
  'system.application_created': '登记机器人应用',
  'system.token_rotated': '更换应用密钥',
  'system.application_revoked': '停用机器人应用',
  'system.grant_approved': '同意军团授权',
  'system.grant_reapproved': '重新同意军团授权',
  'system.grant_revoked': '撤销军团授权',
}

function applicationState(application: EveAgentApplication) {
  if (application.status !== 'ACTIVE') return { label: '已停用', color: 'red' }
  if (parseEveTime(application.expiresAt).valueOf() <= Date.now()) return { label: '已到期', color: 'gray' }
  return { label: '运行中', color: 'green' }
}

function grantState(grant: EveAgentGrant) {
  if (grant.status !== 'ACTIVE') return { label: '已撤销', color: 'red' }
  if (grant.applicationStatus === 'REVOKED') return { label: '应用已停用', color: 'red' }
  if (grant.applicationStatus === 'DELETED') return { label: '应用已删除', color: 'gray' }
  if (grant.applicationExpiresAt && parseEveTime(grant.applicationExpiresAt).valueOf() <= Date.now()) {
    return { label: '应用已到期', color: 'gray' }
  }
  return { label: '已授权', color: 'green' }
}

function invitationState(value?: EveAgentInvitation) {
  if (value?.grantStatus === 'ACTIVE') return { label: '本军团已授权', color: 'green' }
  if (value?.grantStatus === 'REVOKED') return { label: '本军团已撤销', color: 'red' }
  return { label: '等待确认', color: 'orange' }
}

function authorizationUrl(application: EveAgentApplication) {
  const href = router.resolve({ path: '/eve/agents', query: { authorize: application.invitationCode } }).href
  return new URL(href, window.location.origin).href
}

async function loadApplications() {
  loading.value = true
  loadError.value = false
  try {
    applications.value = (await getEveAgentApplications()).data
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function loadCorporationGrants() {
  grantsLoading.value = true
  grantsLoadError.value = false
  try {
    corporationGrants.value = (await getEveAgentCorporationGrants()).data
  } catch {
    grantsLoadError.value = true
  } finally {
    grantsLoading.value = false
  }
}

async function loadAudits() {
  auditLoading.value = true
  auditLoadError.value = false
  try {
    const { data } = await getEveAgentAudits(auditQuery)
    audits.value = data.list
    auditTotal.value = data.total
  } catch {
    auditLoadError.value = true
  } finally {
    auditLoading.value = false
  }
}

function openCreate() {
  Object.assign(form, { name: '', expiresInDays: 90, acknowledgeMailAccess: false })
  createVisible.value = true
}

/** 原始密钥只在内存中保留到弹窗关闭，不写入浏览器存储。 */
function displayCredential(name: string, token: string) {
  credentialName.value = name
  secret.value = token
  showSecret.value = false
  credentialVisible.value = true
}

function clearCredential() {
  secret.value = ''
  showSecret.value = false
}

async function saveApplication() {
  if (!form.name.trim() || !Number.isInteger(form.expiresInDays) || form.expiresInDays < 1 || form.expiresInDays > 365) {
    Message.warning('请填写应用名称和 1 至 365 天的有效期')
    return false
  }
  if (!form.acknowledgeMailAccess) {
    Message.warning('请确认包含邮件的数据授权范围')
    return false
  }
  saving.value = true
  try {
    const { data } = await createEveAgentApplication({ ...form, name: form.name.trim() })
    createVisible.value = false
    displayCredential(data.application.name, data.token)
    await Promise.all([loadApplications(), loadCorporationGrants()])
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

function rotate(application: EveAgentApplication) {
  Modal.warning({
    title: '更换机器人密钥',
    content: `“${application.name}”当前配置的旧密钥将立即失效，已授权军团不受影响。`,
    hideCancel: false,
    onOk: async () => {
      actingId.value = application.id
      try {
        const { data } = await rotateEveAgentCredential(application.id)
        displayCredential(data.application.name, data.token)
        await loadApplications()
      } finally {
        actingId.value = ''
      }
    },
  })
}

function revokeApplication(application: EveAgentApplication) {
  Modal.warning({
    title: '停用整个机器人应用',
    content: `停用后，“${application.name}”的密钥会失效，并立即停止读取所有已授权军团的数据。此操作不是只撤销本军团。`,
    hideCancel: false,
    okText: '停用整个应用',
    onOk: async () => {
      actingId.value = application.id
      try {
        await revokeEveAgentApplication(application.id)
        await Promise.all([loadApplications(), loadCorporationGrants()])
        Message.success('机器人应用已停用')
      } finally {
        actingId.value = ''
      }
    },
  })
}

function revokeGrant(grant: EveAgentGrant) {
  Modal.warning({
    title: '撤销本军团授权',
    content: `撤销后，“${grant.applicationName}”将不能继续读取 ${grant.corporationName} 的数据，不影响它已获授权的其他军团。再次授权需要重新打开链接并确认。`,
    hideCancel: false,
    okText: '撤销本军团授权',
    onOk: async () => {
      actingId.value = grant.id
      try {
        await revokeEveAgentGrant(grant.id)
        await Promise.all([
          loadCorporationGrants(),
          applicationGrantsVisible.value && selectedApplication.value
            ? loadApplicationGrants(selectedApplication.value)
            : Promise.resolve(),
        ])
        Message.success('本军团授权已撤销')
      } finally {
        actingId.value = ''
      }
    },
  })
}

async function loadApplicationGrants(application: EveAgentApplication) {
  // 只允许最后一次点击的应用更新弹窗，避免慢响应覆盖当前选择。
  const requestId = ++applicationGrantsRequestId
  const applicationId = application.id
  selectedApplication.value = application
  applicationGrantsVisible.value = true
  applicationGrantsLoading.value = true
  applicationGrantsLoadError.value = false
  try {
    const { data } = await getEveAgentApplicationGrants(applicationId)
    if (requestId !== applicationGrantsRequestId || selectedApplication.value?.id !== applicationId) return
    applicationGrants.value = data
  } catch {
    if (requestId !== applicationGrantsRequestId || selectedApplication.value?.id !== applicationId) return
    applicationGrantsLoadError.value = true
  } finally {
    if (requestId === applicationGrantsRequestId && selectedApplication.value?.id === applicationId) {
      applicationGrantsLoading.value = false
    }
  }
}

async function loadInvitation(code = invitationCode.value) {
  if (!code) return
  // 授权链接切换时使旧预览和旧审批失效，响应只能写回发起时的目标。
  const requestId = ++invitationRequestId
  if (invitationCode.value !== code) {
    approvalRequestId++
    approving.value = false
  }
  invitationCode.value = code
  invitation.value = undefined
  invitationAcknowledged.value = false
  invitationError.value = false
  invitationLoading.value = true
  invitationVisible.value = true
  try {
    const { data } = await getEveAgentInvitation(code)
    if (requestId !== invitationRequestId || invitationCode.value !== code) return
    invitation.value = data
  } catch {
    if (requestId !== invitationRequestId || invitationCode.value !== code) return
    invitationError.value = true
  } finally {
    if (requestId === invitationRequestId && invitationCode.value === code) invitationLoading.value = false
  }
}

async function approveInvitation() {
  if (!invitationAcknowledged.value) {
    Message.warning('请先确认包含邮件的数据授权范围')
    return
  }
  const target = invitation.value
  const code = invitationCode.value
  if (!target || target.grantStatus === 'ACTIVE' || !code) return
  // 冻结本次确认的应用、军团和链接，不能借后续路由变化改写审批目标。
  const requestId = ++approvalRequestId
  const applicationId = target.applicationId
  const corporationId = target.corporationId
  approving.value = true
  try {
    await approveEveAgentInvitation(code)
    if (requestId !== approvalRequestId
      || invitationCode.value !== code
      || invitation.value?.applicationId !== applicationId
      || invitation.value?.corporationId !== corporationId) return
    Message.success('本军团已授权给该机器人')
    await Promise.all([loadCorporationGrants(), loadInvitation(code)])
  } finally {
    if (requestId === approvalRequestId && invitationCode.value === code) approving.value = false
  }
}

async function clearInvitationQuery() {
  if (!route.query.authorize) return
  const { authorize, ...query } = route.query
  await router.replace({ path: route.path, query })
}

async function copy(value: string) {
  try {
    await navigator.clipboard.writeText(value)
    Message.success('已复制')
  } catch {
    Message.warning('无法访问剪贴板，请选中内容复制')
  }
}

function changeTab(value: string | number) {
  if (value === 'grants') loadCorporationGrants()
  if (value === 'audit') loadAudits()
}

watch(
  () => route.query.authorize,
  (value) => {
    const code = Array.isArray(value) ? value[0] : value
    if (typeof code === 'string' && code) {
      loadInvitation(code)
      return
    }
    invitationRequestId++
    approvalRequestId++
    invitationLoading.value = false
    approving.value = false
    invitationCode.value = ''
    invitation.value = undefined
  },
  { immediate: true },
)

onMounted(() => Promise.all([loadApplications(), loadCorporationGrants()]))
onBeforeUnmount(clearCredential)
</script>

<template>
  <main class="eve-agents gi_page">
    <header class="eve-agents__header">
      <h1>AI Agent 接入</h1>
      <a-button type="primary" @click="openCreate"><template #icon><icon-plus /></template>登记机器人</a-button>
    </header>

    <section class="eve-agents__connection" aria-label="MCP 连接信息">
      <span>MCP 地址</span>
      <code>{{ endpoint }}</code>
      <a-tooltip content="复制 MCP 地址"><a-button aria-label="复制 MCP 地址" @click="copy(endpoint)"><template #icon><icon-copy /></template></a-button></a-tooltip>
      <a-tag>Streamable HTTP</a-tag>
    </section>
    <a-alert type="warning">
      授权范围：当前军团全部业务数据，包含已授权邮件、成员追踪和内部备注。访问方式：只读。
    </a-alert>

    <a-tabs v-model:active-key="activeTab" @change="changeTab">
      <a-tab-pane key="applications" title="登记的机器人">
        <div class="eve-agents__toolbar">
          <span>共 {{ applications.length }} 个机器人</span>
          <a-tooltip content="刷新机器人列表"><a-button :loading="loading" aria-label="刷新机器人列表" @click="loadApplications"><template #icon><icon-refresh /></template></a-button></a-tooltip>
        </div>
        <a-result v-if="loadError" status="error" title="机器人列表加载失败"><template #extra><a-button @click="loadApplications">重新加载</a-button></template></a-result>
        <a-table v-else :data="applications" :loading="loading" :pagination="false" :scroll="{ x: 1080 }" row-key="id">
          <template #columns>
            <a-table-column title="机器人名称" data-index="name" :width="180" ellipsis tooltip />
            <a-table-column title="应用状态" :width="100"><template #cell="{ record }"><a-tag :color="applicationState(record).color">{{ applicationState(record).label }}</a-tag></template></a-table-column>
            <a-table-column title="密钥标识" :width="145"><template #cell="{ record }"><code>{{ record.tokenPrefix }}</code></template></a-table-column>
            <a-table-column title="有效期至" :width="165"><template #cell="{ record }">{{ formatEveTime(record.expiresAt) }}</template></a-table-column>
            <a-table-column title="最近访问" :width="165"><template #cell="{ record }">{{ record.lastUsedAt ? formatEveTime(record.lastUsedAt) : '尚未访问' }}</template></a-table-column>
            <a-table-column title="接入军团" :width="120"><template #cell="{ record }"><a-button type="text" @click="loadApplicationGrants(record)">查看军团</a-button></template></a-table-column>
            <a-table-column title="操作" :width="190" fixed="right">
              <template #cell="{ record }">
                <a-space>
                  <a-tooltip content="复制军团授权链接"><a-button :disabled="applicationState(record).label !== '运行中'" aria-label="复制军团授权链接" @click="copy(authorizationUrl(record))"><template #icon><icon-link /></template></a-button></a-tooltip>
                  <a-tooltip content="更换机器人密钥"><a-button :disabled="applicationState(record).label !== '运行中'" :loading="actingId === record.id" aria-label="更换机器人密钥" @click="rotate(record)"><template #icon><icon-sync /></template></a-button></a-tooltip>
                  <a-tooltip content="停用整个机器人应用"><a-button status="danger" :disabled="record.status !== 'ACTIVE'" :loading="actingId === record.id" aria-label="停用整个机器人应用" @click="revokeApplication(record)"><template #icon><icon-stop /></template></a-button></a-tooltip>
                </a-space>
              </template>
            </a-table-column>
          </template>
          <template #empty><a-empty><p>尚未登记机器人应用</p></a-empty></template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="grants" title="本军团授权">
        <div class="eve-agents__toolbar">
          <span>共 {{ corporationGrants.length }} 条授权</span>
          <a-tooltip content="刷新本军团授权"><a-button :loading="grantsLoading" aria-label="刷新本军团授权" @click="loadCorporationGrants"><template #icon><icon-refresh /></template></a-button></a-tooltip>
        </div>
        <a-result v-if="grantsLoadError" status="error" title="本军团授权加载失败"><template #extra><a-button @click="loadCorporationGrants">重新加载</a-button></template></a-result>
        <a-table v-else :data="corporationGrants" :loading="grantsLoading" :pagination="false" :scroll="{ x: 820 }" row-key="id">
          <template #columns>
            <a-table-column title="机器人名称" data-index="applicationName" :width="200" ellipsis tooltip />
            <a-table-column title="授权军团" :width="220"><template #cell="{ record }">{{ record.corporationName }} [{{ record.corporationTicker }}]</template></a-table-column>
            <a-table-column title="状态" :width="100"><template #cell="{ record }"><a-tag :color="grantState(record).color">{{ grantState(record).label }}</a-tag></template></a-table-column>
            <a-table-column title="授权时间" :width="170"><template #cell="{ record }">{{ formatEveTime(record.grantedAt) }}</template></a-table-column>
            <a-table-column title="撤销时间" :width="170"><template #cell="{ record }">{{ record.revokedAt ? formatEveTime(record.revokedAt) : '—' }}</template></a-table-column>
            <a-table-column title="操作" :width="90" fixed="right"><template #cell="{ record }"><a-tooltip content="只撤销本军团授权"><a-button status="danger" :disabled="record.status !== 'ACTIVE'" :loading="actingId === record.id" aria-label="只撤销本军团授权" @click="revokeGrant(record)"><template #icon><icon-stop /></template></a-button></a-tooltip></template></a-table-column>
          </template>
          <template #empty><a-empty><p>本军团尚未授权任何外部机器人</p></a-empty></template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="audit" title="访问记录">
        <div class="eve-agents__toolbar"><span>共 {{ auditTotal }} 条记录</span><a-tooltip content="刷新访问记录"><a-button :loading="auditLoading" aria-label="刷新访问记录" @click="loadAudits"><template #icon><icon-refresh /></template></a-button></a-tooltip></div>
        <a-result v-if="auditLoadError" status="error" title="访问记录加载失败"><template #extra><a-button @click="loadAudits">重新加载</a-button></template></a-result>
        <a-table v-else :data="audits" :loading="auditLoading" :pagination="false" :scroll="{ x: 800 }" row-key="id">
          <template #columns>
            <a-table-column title="时间" :width="170"><template #cell="{ record }">{{ formatEveTime(record.occurredAt) }}</template></a-table-column>
            <a-table-column title="应用" data-index="applicationName" :width="160" ellipsis tooltip />
            <a-table-column title="操作" :width="180"><template #cell="{ record }">{{ toolLabels[record.toolName] || record.toolName }}</template></a-table-column>
            <a-table-column title="结果" :width="90"><template #cell="{ record }"><a-tag :color="record.success ? 'green' : 'red'">{{ record.success ? '成功' : '失败' }}</a-tag></template></a-table-column>
            <a-table-column title="耗时" :width="100"><template #cell="{ record }">{{ record.durationMs }} ms</template></a-table-column>
            <a-table-column title="返回条数" data-index="resultCount" :width="100" />
          </template>
        </a-table>
        <a-pagination v-if="auditTotal" v-model:current="auditQuery.page" v-model:page-size="auditQuery.size" :total="auditTotal" :page-size-options="[10, 20, 50]" show-total show-page-size @change="loadAudits" @page-size-change="() => { auditQuery.page = 1; loadAudits() }" />
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:visible="createVisible" title="登记机器人应用" width="min(520px, calc(100vw - 32px))" :ok-loading="saving" :on-before-ok="saveApplication" unmount-on-close>
      <a-form :model="form" layout="vertical">
        <a-form-item label="机器人名称" required><a-input v-model="form.name" placeholder="例如：汐栗 QQ 机器人" :max-length="60" show-word-limit /></a-form-item>
        <a-form-item label="密钥有效期（天）" required><a-input-number v-model="form.expiresInDays" :min="1" :max="365" :precision="0" /></a-form-item>
        <a-form-item><a-checkbox v-model="form.acknowledgeMailAccess">我确认允许该机器人读取本军团全部业务数据，包括已授权邮件。</a-checkbox></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:visible="credentialVisible" :title="`${credentialName} · 连接凭据`" width="min(640px, calc(100vw - 32px))" :footer="false" unmount-on-close @close="clearCredential">
      <a-alert type="warning">密钥仅显示本次。关闭后不能再次查看；遗失时需要更换密钥。</a-alert>
      <div class="eve-agents__credential">
        <label for="eve-agent-secret">应用密钥</label>
        <a-input id="eve-agent-secret" :model-value="secret" :type="showSecret ? 'text' : 'password'" readonly autocomplete="off" />
        <a-space>
          <a-tooltip :content="showSecret ? '隐藏密钥' : '显示密钥'"><a-button :aria-label="showSecret ? '隐藏密钥' : '显示密钥'" @click="showSecret = !showSecret"><template #icon><icon-eye-invisible v-if="showSecret" /><icon-eye v-else /></template></a-button></a-tooltip>
          <a-button @click="copy(secret)"><template #icon><icon-copy /></template>复制密钥</a-button>
          <a-button @click="copy(clientConfig)"><template #icon><icon-copy /></template>复制 MCP 配置</a-button>
        </a-space>
        <pre v-if="showSecret">{{ clientConfig }}</pre>
      </div>
    </a-modal>

    <a-modal v-model:visible="applicationGrantsVisible" :title="`${selectedApplication?.name || '机器人'} · 已接入军团`" width="min(760px, calc(100vw - 32px))" :footer="false" unmount-on-close>
      <a-result v-if="applicationGrantsLoadError" status="error" title="已接入军团加载失败"><template #extra><a-button v-if="selectedApplication" @click="loadApplicationGrants(selectedApplication)">重新加载</a-button></template></a-result>
      <a-table v-else class="eve-agents__modal-table" :data="applicationGrants" :loading="applicationGrantsLoading" :pagination="false" :scroll="{ x: 680 }" row-key="id">
        <template #columns>
          <a-table-column title="军团" :width="250"><template #cell="{ record }">{{ record.corporationName }} [{{ record.corporationTicker }}]</template></a-table-column>
          <a-table-column title="状态" :width="100"><template #cell="{ record }"><a-tag :color="grantState(record).color">{{ grantState(record).label }}</a-tag></template></a-table-column>
          <a-table-column title="授权时间" :width="170"><template #cell="{ record }">{{ formatEveTime(record.grantedAt) }}</template></a-table-column>
          <a-table-column title="撤销时间" :width="170"><template #cell="{ record }">{{ record.revokedAt ? formatEveTime(record.revokedAt) : '—' }}</template></a-table-column>
        </template>
        <template #empty><a-empty><p>尚无军团授权</p></a-empty></template>
      </a-table>
    </a-modal>

    <a-modal v-model:visible="invitationVisible" title="确认军团数据授权" width="min(560px, calc(100vw - 32px))" :footer="false" :mask-closable="false" unmount-on-close @close="clearInvitationQuery">
      <div class="eve-agents__invitation">
        <a-spin v-if="invitationLoading" tip="正在核对申请和当前军团..." />
        <a-result v-else-if="invitationError" status="error" title="授权申请无法读取" subtitle="链接可能无效、应用已停用或已经到期。">
          <template #extra><a-button @click="loadInvitation()">重新加载</a-button></template>
        </a-result>
        <template v-else-if="invitation">
          <div class="eve-agents__invitation-heading">
            <div>
              <span>申请应用</span>
              <strong>{{ invitation.applicationName }}</strong>
            </div>
            <a-tag :color="invitationState(invitation).color">{{ invitationState(invitation).label }}</a-tag>
          </div>
          <dl class="eve-agents__invitation-details">
            <div><dt>应用登记方</dt><dd>{{ invitation.ownerCorporationName }}</dd></div>
            <div><dt>即将授权的军团</dt><dd>{{ invitation.corporationName }} [{{ invitation.corporationTicker }}]</dd></div>
            <div><dt>应用有效期</dt><dd>{{ formatEveTime(invitation.expiresAt) }}</dd></div>
            <div><dt>读取范围</dt><dd>本军团全部业务数据，包括已授权邮件、成员追踪和内部备注</dd></div>
            <div><dt>访问方式</dt><dd>只读，不能修改本站或游戏数据</dd></div>
          </dl>
          <template v-if="invitation.grantStatus !== 'ACTIVE'">
            <a-checkbox v-model="invitationAcknowledged">我确认自己正在为上述军团授权，并同意该机器人读取包含邮件在内的全部业务数据。</a-checkbox>
            <div class="eve-agents__invitation-actions">
              <a-button @click="invitationVisible = false">取消</a-button>
              <a-button type="primary" :loading="approving" :disabled="!invitationAcknowledged" @click="approveInvitation">{{ invitation.grantStatus === 'REVOKED' ? '重新授权' : '同意授权' }}</a-button>
            </div>
          </template>
          <a-alert v-else type="success">本军团已经授权，无需重复操作。</a-alert>
        </template>
      </div>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.eve-agents { display: grid; min-width: 0; gap: 1rem; color: var(--color-text-1); }
.eve-agents__header { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: .75rem; }
.eve-agents__header h1 { margin: 0; font-size: 1.375rem; }
.eve-agents__connection { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: .5rem; padding-block: .5rem; }
.eve-agents__connection > span { color: var(--color-text-3); font-size: .8125rem; }
.eve-agents__connection code { min-width: 0; flex: 1 1 12rem; overflow-wrap: anywhere; font-size: .8125rem; }
.eve-agents__toolbar { display: flex; align-items: center; justify-content: space-between; gap: .5rem; margin-bottom: .75rem; color: var(--color-text-3); line-height: 1.5; }
.eve-agents :deep(.arco-tabs-content), .eve-agents :deep(.arco-tabs-content-item), .eve-agents :deep(.arco-table-container) { min-width: 0; }
.eve-agents :deep(.arco-pagination) { justify-content: flex-end; flex-wrap: wrap; gap: .5rem; margin-top: .75rem; }
.eve-agents__credential { display: grid; min-width: 0; gap: .75rem; margin-top: 1rem; }
.eve-agents__credential :deep(.arco-space) { flex-wrap: wrap; }
.eve-agents__credential pre { max-height: 20rem; overflow: auto; padding: .75rem; border-radius: 4px; background: var(--color-fill-2); white-space: pre-wrap; overflow-wrap: anywhere; font-size: .8125rem; line-height: 1.6; }
.eve-agents__modal-table { margin-top: 1rem; }
.eve-agents__invitation { display: grid; min-height: 8rem; gap: 1rem; }
.eve-agents__invitation > :deep(.arco-spin) { justify-self: center; align-self: center; }
.eve-agents__invitation-heading { display: flex; align-items: center; justify-content: space-between; gap: .75rem; }
.eve-agents__invitation-heading > div { display: grid; min-width: 0; gap: .25rem; }
.eve-agents__invitation-heading span { color: var(--color-text-3); font-size: .75rem; }
.eve-agents__invitation-heading strong { overflow-wrap: anywhere; font-size: 1.125rem; }
.eve-agents__invitation-details { display: grid; gap: .75rem; margin: 0; }
.eve-agents__invitation-details > div { display: grid; min-width: 0; gap: .25rem; padding-bottom: .75rem; border-bottom: 1px solid var(--color-border-2); }
.eve-agents__invitation-details dt { color: var(--color-text-3); font-size: .8125rem; }
.eve-agents__invitation-details dd { margin: 0; overflow-wrap: anywhere; line-height: 1.6; }
.eve-agents__invitation-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: .75rem; }
@media (min-width: 768px) {
  .eve-agents__connection code { flex: 0 1 auto; }
  .eve-agents__invitation-details > div { grid-template-columns: 7rem minmax(0, 1fr); align-items: baseline; gap: .75rem; }
}
</style>
