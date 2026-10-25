<script setup lang="ts">
import { Message, Modal } from '@arco-design/web-vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  type EveAgentApplication,
  type EveAgentAudit,
  createEveAgentApplication,
  getEveAgentApplications,
  getEveAgentAudits,
  revokeEveAgentApplication,
  rotateEveAgentCredential,
} from '@/apis/eve/agents'
import { formatEveTime, parseEveTime } from '@/utils/eveTime'

defineOptions({ name: 'EveAgents' })

const applications = ref<EveAgentApplication[]>([])
const audits = ref<EveAgentAudit[]>([])
const auditTotal = ref(0)
const auditQuery = reactive({ page: 1, size: 20 })
const loading = ref(false)
const auditLoading = ref(false)
const loadError = ref(false)
const auditLoadError = ref(false)
const saving = ref(false)
const actingId = ref('')
const activeTab = ref('applications')
const createVisible = ref(false)
const credentialVisible = ref(false)
const showSecret = ref(false)
const secret = ref('')
const credentialName = ref('')
const form = reactive({ name: '', expiresInDays: 90, acknowledgeMailAccess: false })
const endpoint = new URL(`${(import.meta.env.VITE_API_PREFIX || '/api').replace(/\/$/, '')}/mcp`, window.location.origin).href
const clientConfig = computed(() => JSON.stringify({
  mcpServers: { eve: { transport: 'streamable_http', url: endpoint, headers: { Authorization: `Bearer ${secret.value}` } } },
}, null, 2))
const toolLabels: Record<string, string> = {
  'eve_catalog': '查询数据目录',
  'eve_query': '查询业务数据',
  'eve_aggregate': '统计汇总',
  'eve_read_document': '读取已缓存正文',
  'system.application_created': '创建应用授权',
  'system.token_rotated': '更换密钥',
  'system.application_revoked': '撤销应用授权',
}

function state(application: EveAgentApplication) {
  if (application.status !== 'ACTIVE') return { label: '已撤销', color: 'red' }
  if (parseEveTime(application.expiresAt).valueOf() <= Date.now()) return { label: '已到期', color: 'gray' }
  return { label: '已授权', color: 'green' }
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
    await loadApplications()
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

function rotate(application: EveAgentApplication) {
  Modal.warning({
    title: '更换应用密钥',
    content: `“${application.name}”的旧密钥将立即失效，有效期不变。`,
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

function revoke(application: EveAgentApplication) {
  Modal.warning({
    title: '撤销应用授权',
    content: `撤销后，“${application.name}”将无法继续读取本军团数据。`,
    hideCancel: false,
    onOk: async () => {
      actingId.value = application.id
      try {
        await revokeEveAgentApplication(application.id)
        await loadApplications()
        Message.success('应用授权已撤销')
      } finally {
        actingId.value = ''
      }
    },
  })
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
  if (value === 'audit') loadAudits()
}

onMounted(loadApplications)
onBeforeUnmount(clearCredential)
</script>

<template>
  <main class="eve-agents gi_page">
    <header class="eve-agents__header">
      <h1>AI Agent 接入</h1>
      <a-button type="primary" @click="openCreate"><template #icon><icon-plus /></template>授权新应用</a-button>
    </header>

    <section class="eve-agents__connection" aria-label="MCP 连接信息">
      <span>MCP 地址</span>
      <code>{{ endpoint }}</code>
      <a-tooltip content="复制 MCP 地址"><a-button aria-label="复制 MCP 地址" @click="copy(endpoint)"><template #icon><icon-copy /></template></a-button></a-tooltip>
      <a-tag>Streamable HTTP</a-tag>
    </section>
    <a-alert type="warning">授权范围：本军团全部业务数据，包含已授权邮件、成员追踪和内部备注。访问方式：只读。</a-alert>

    <a-tabs v-model:active-key="activeTab" @change="changeTab">
      <a-tab-pane key="applications" title="已授权应用">
        <div class="eve-agents__toolbar"><span>共 {{ applications.length }} 个应用</span><a-tooltip content="刷新应用列表"><a-button :loading="loading" aria-label="刷新应用列表" @click="loadApplications"><template #icon><icon-refresh /></template></a-button></a-tooltip></div>
        <a-result v-if="loadError" status="error" title="应用授权加载失败"><template #extra><a-button @click="loadApplications">重新加载</a-button></template></a-result>
        <a-table v-else :data="applications" :loading="loading" :pagination="false" :scroll="{ x: 880 }" row-key="id">
          <template #columns>
            <a-table-column title="应用名称" data-index="name" :width="190" ellipsis tooltip />
            <a-table-column title="授权状态" :width="100"><template #cell="{ record }"><a-tag :color="state(record).color">{{ state(record).label }}</a-tag></template></a-table-column>
            <a-table-column title="密钥标识" :width="150"><template #cell="{ record }"><code>{{ record.tokenPrefix }}</code></template></a-table-column>
            <a-table-column title="有效期至" :width="160"><template #cell="{ record }">{{ formatEveTime(record.expiresAt) }}</template></a-table-column>
            <a-table-column title="最近访问" :width="160"><template #cell="{ record }">{{ record.lastUsedAt ? formatEveTime(record.lastUsedAt) : '尚未访问' }}</template></a-table-column>
            <a-table-column title="操作" :width="120" fixed="right">
              <template #cell="{ record }">
                <a-space>
                  <a-tooltip content="更换密钥"><a-button :disabled="state(record).label !== '已授权'" :loading="actingId === record.id" aria-label="更换密钥" @click="rotate(record)"><template #icon><icon-sync /></template></a-button></a-tooltip>
                  <a-tooltip content="撤销授权"><a-button status="danger" :disabled="record.status !== 'ACTIVE'" :loading="actingId === record.id" aria-label="撤销授权" @click="revoke(record)"><template #icon><icon-stop /></template></a-button></a-tooltip>
                </a-space>
              </template>
            </a-table-column>
          </template>
          <template #empty><a-empty><p>尚未授权外部应用</p></a-empty></template>
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

    <a-modal v-model:visible="createVisible" title="授权新应用" width="min(520px, calc(100vw - 32px))" :ok-loading="saving" :on-before-ok="saveApplication" unmount-on-close>
      <a-form :model="form" layout="vertical">
        <a-form-item label="应用名称" required><a-input v-model="form.name" placeholder="例如：军团 QQ 机器人" :max-length="60" show-word-limit /></a-form-item>
        <a-form-item label="密钥有效期（天）" required><a-input-number v-model="form.expiresInDays" :min="1" :max="365" :precision="0" /></a-form-item>
        <a-form-item><a-checkbox v-model="form.acknowledgeMailAccess">我确认允许该应用读取本军团全部业务数据，包括已授权邮件。</a-checkbox></a-form-item>
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
  </main>
</template>

<style scoped lang="scss">
.eve-agents { display: grid; min-width: 0; gap: 1rem; color: var(--color-text-1); }
.eve-agents__header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: .75rem; }
.eve-agents__header h1 { margin: 0; font-size: 1.375rem; }
.eve-agents__connection { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: .5rem; padding-block: .5rem; }
.eve-agents__connection > span { color: var(--color-text-3); font-size: .8125rem; }
.eve-agents__connection code { min-width: 0; flex: 1 1 12rem; overflow-wrap: anywhere; font-size: .8125rem; }
.eve-agents__toolbar { display: flex; align-items: center; justify-content: space-between; gap: .5rem; margin-bottom: .75rem; color: var(--color-text-3); }
.eve-agents :deep(.arco-tabs-content), .eve-agents :deep(.arco-tabs-content-item), .eve-agents :deep(.arco-table-container) { min-width: 0; }
.eve-agents :deep(.arco-pagination) { justify-content: flex-end; flex-wrap: wrap; gap: .5rem; margin-top: .75rem; }
.eve-agents__credential { display: grid; min-width: 0; gap: .75rem; margin-top: 1rem; }
.eve-agents__credential :deep(.arco-space) { flex-wrap: wrap; }
.eve-agents__credential pre { max-height: 20rem; overflow: auto; padding: .75rem; border-radius: 4px; background: var(--color-fill-2); white-space: pre-wrap; overflow-wrap: anywhere; font-size: .8125rem; line-height: 1.6; }
@media (min-width: 768px) { .eve-agents__connection code { flex-grow: 0; } }
</style>
