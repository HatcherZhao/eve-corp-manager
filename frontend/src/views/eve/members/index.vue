<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import DataFreshnessBanner from '../components/DataFreshnessBanner.vue'
import {
  type EveMember,
  type EveMemberOperationAudit,
  type EveMemberSyncRun,
  exportEveMembers,
  getEveContext,
  getEveMember,
  getEveMemberOrganizationAudit,
  getEveMemberSyncRuns,
  getEveMembers,
  syncEveMembers,
  updateEveMemberOrganization,
} from '@/apis/eve'
import { useDownload } from '@/hooks'
import { useUserStore } from '@/stores'
import { getEveCharacterPortraitUrl } from '@/utils/eveImage'

defineOptions({ name: 'EveMembers' })

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedMember = ref<EveMember>()
const members = ref<EveMember[]>([])
const total = ref(0)
const syncRuns = ref<EveMemberSyncRun[]>([])
const freshnessVersion = ref(0)
const organizationAudits = ref<EveMemberOperationAudit[]>([])
const organizationVisible = ref(false)
const organizationSaving = ref(false)
const organizationTarget = ref<EveMember>()
const organizationForm = reactive({ organizationGroup: '', memberNote: '' })
const query = reactive({ page: 1, size: 20, keyword: '', status: '' })
const canViewTracking = computed(() => userStore.permissions.includes('eve:members:track:view') || userStore.permissions.includes('*:*:*'))
const canOrganize = computed(() => userStore.permissions.includes('eve:members:organize') || userStore.permissions.includes('*:*:*'))
const canViewHistory = computed(() => userStore.permissions.includes('eve:members:history:view') || userStore.permissions.includes('*:*:*'))
const canExport = computed(() => userStore.permissions.includes('eve:members:export') || userStore.permissions.includes('*:*:*'))

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function resolvedName(name?: string, field = '信息') {
  return name || `${field}名称待补齐`
}

/** 成员肖像由国服公开图片服务按游戏角色 ID 提供，无需额外授权。 */
function memberPortraitUrl(characterId?: string) {
  return getEveCharacterPortraitUrl(characterId)
}

async function loadMembers() {
  loading.value = true
  try {
    const { data } = await getEveMembers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    })
    members.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 进入成员页时让服务端刷新在线军团成员权限，再同步前端权限状态。 */
async function refreshSessionPermissions() {
  try {
    await getEveContext()
    await userStore.getInfo()
  } catch {
    // 成员名册加载仍会按服务端权限校验，此处无需阻断页面基础数据展示。
  }
}

/** 按当前本站权限加载可见的同步和成员组织审计。 */
async function loadOperations() {
  const tasks: Promise<void>[] = []
  if (canViewHistory.value) {
    tasks.push(getEveMemberSyncRuns().then(({ data }) => {
      syncRuns.value = data
    }))
  }
  if (canOrganize.value) {
    tasks.push(getEveMemberOrganizationAudit().then(({ data }) => {
      organizationAudits.value = data
    }))
  }
  await Promise.all(tasks)
}

function search() {
  query.page = 1
  loadMembers()
}

/** 导出与当前筛选条件一致的成员资料。 */
function exportMembers() {
  useDownload(
    () => exportEveMembers({ keyword: query.keyword || undefined, status: query.status || undefined }),
    '军团成员.csv',
    '.csv',
  )
}

async function showDetail(record: EveMember) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    selectedMember.value = (await getEveMember(record.characterId)).data
  } finally {
    detailLoading.value = false
  }
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveMembers()
    Message.success(data.message)
  } finally {
    syncing.value = false
    freshnessVersion.value += 1
  }
}

/** 打开成员内部组织信息编辑框，不将追踪信息作为编辑字段。 */
function openOrganization(record: EveMember) {
  organizationTarget.value = record
  Object.assign(organizationForm, {
    organizationGroup: record.organizationGroup || '',
    memberNote: record.memberNote || '',
  })
  organizationVisible.value = true
}

/** 保存分组与备注，并同步更新列表、详情和审计记录。 */
async function saveOrganization() {
  if (!organizationTarget.value) return
  organizationSaving.value = true
  try {
    const { data } = await updateEveMemberOrganization(organizationTarget.value.characterId, organizationForm)
    const index = members.value.findIndex((item) => item.characterId === data.characterId)
    if (index >= 0) members.value[index] = { ...members.value[index], ...data }
    if (selectedMember.value?.characterId === data.characterId) selectedMember.value = data
    organizationVisible.value = false
    Message.success('成员组织信息已保存')
    await loadOperations()
  } finally {
    organizationSaving.value = false
  }
}

onMounted(async () => {
  await refreshSessionPermissions()
  await Promise.all([loadMembers(), loadOperations()])
})
</script>

<template>
  <main class="eve-members gi_page">
    <header class="eve-members__header">
      <div>
        <span class="eve-members__eyebrow">CORPORATION ROSTER</span>
        <h1>成员管理</h1>
        <p>查看当前军团真实游戏成员名册。登录与登出是上游最近记录，不代表实时在线。</p>
      </div>
      <div class="eve-members__header-tools">
        <DataFreshnessBanner :modules="['MEMBER_ROSTER', 'MEMBER_TRACKING']" :reload-token="freshnessVersion" />
        <a-button v-permission="['eve:members:manage']" type="primary" :loading="syncing" @click="sync">
          <template #icon><icon-sync /></template>同步成员数据
        </a-button>
      </div>
    </header>

    <section class="eve-members__filters">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索角色名称" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-select v-model="query.status" allow-clear placeholder="全部状态">
        <a-option value="ACTIVE">在团</a-option>
        <a-option value="LEFT">已离团</a-option>
      </a-select>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="Object.assign(query, { keyword: '', status: '', page: 1 }); loadMembers()">重置</a-button>
      <a-button v-if="canExport" @click="exportMembers"><template #icon><icon-download /></template>导出 CSV</a-button>
    </section>

    <section class="eve-members__table-wrap">
      <a-alert v-if="!canViewTracking" type="info" :show-icon="true">
        你可以查看基础名册。位置、舰船、最近登录/登出和基地需要军团管理员授予“成员追踪信息”权限。
      </a-alert>
      <a-table :data="members" :loading="loading" row-key="characterId" :pagination="false" :scroll="{ x: canViewTracking ? 1470 : 900 }">
        <template #columns>
          <a-table-column title="角色" :width="210">
            <template #cell="{ record }">
              <div class="eve-members__member-identity">
                <Avatar :src="memberPortraitUrl(record.characterId)" :name="record.characterName" :size="36" :alt="`${record.characterName || '成员'}游戏肖像`" />
                <strong>{{ record.characterName || '角色名称待补齐' }}</strong>
              </div>
            </template>
          </a-table-column>
          <a-table-column title="分组" :width="130"><template #cell="{ record }">{{ record.organizationGroup || '未分组' }}</template></a-table-column>
          <a-table-column title="状态" :width="90"><template #cell="{ record }"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'gray'">{{ record.status === 'ACTIVE' ? '在团' : '已离团' }}</a-tag></template></a-table-column>
          <a-table-column title="入团时间" :width="155"><template #cell="{ record }">{{ formatTime(record.joinedAt) }}</template></a-table-column>
          <template v-if="canViewTracking">
            <a-table-column title="最近登录" :width="155"><template #cell="{ record }">{{ formatTime(record.tracking?.lastLogonAt) }}</template></a-table-column>
            <a-table-column title="最近登出" :width="155"><template #cell="{ record }">{{ formatTime(record.tracking?.lastLogoffAt) }}</template></a-table-column>
            <a-table-column title="位置" :width="180"><template #cell="{ record }">{{ resolvedName(record.tracking?.locationName, '位置') }}</template></a-table-column>
            <a-table-column title="舰船" :width="180"><template #cell="{ record }"><span class="eve-members__ship-identity"><EveTypeIcon v-if="record.tracking?.shipTypeId" :type-id="record.tracking.shipTypeId" :size="24" :alt="`${record.tracking.shipTypeName || '舰船'}图标`" />{{ resolvedName(record.tracking?.shipTypeName, '舰船类型') }}</span></template></a-table-column>
          </template>
          <a-table-column title="最近同步" :width="155"><template #cell="{ record }">{{ formatTime(record.lastSeenAt) }}</template></a-table-column>
          <a-table-column title="操作" :width="130" fixed="right"><template #cell="{ record }"><a-space><a-link @click="showDetail(record)">详情</a-link><a-link v-if="canOrganize" @click="openOrganization(record)">组织信息</a-link></a-space></template></a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadMembers" @page-size-change="search" />
    </section>

    <a-drawer v-model:visible="detailVisible" width="520px" :footer="false" title="成员详情">
      <a-spin :loading="detailLoading" class="eve-members__detail-loading">
        <template v-if="selectedMember">
          <a-descriptions :column="1" bordered>
            <a-descriptions-item label="角色">{{ selectedMember.characterName || '角色名称待补齐' }}</a-descriptions-item>
            <a-descriptions-item label="成员分组">{{ selectedMember.organizationGroup || '未分组' }}</a-descriptions-item>
            <a-descriptions-item label="成员备注">{{ selectedMember.memberNote || '—' }}</a-descriptions-item>
            <a-descriptions-item label="在团状态">{{ selectedMember.status === 'ACTIVE' ? '在团' : '已离团' }}</a-descriptions-item>
            <a-descriptions-item label="入团时间">{{ formatTime(selectedMember.joinedAt) }}</a-descriptions-item>
          </a-descriptions>
          <a-button v-if="canOrganize" type="outline" class="eve-members__organization-button" @click="openOrganization(selectedMember)">编辑组织信息</a-button>
          <template v-if="canViewTracking">
            <h3>运营追踪</h3>
            <a-descriptions :column="1" bordered>
              <a-descriptions-item label="最近登录">{{ formatTime(selectedMember.tracking?.lastLogonAt) }}</a-descriptions-item>
              <a-descriptions-item label="最近登出">{{ formatTime(selectedMember.tracking?.lastLogoffAt) }}</a-descriptions-item>
              <a-descriptions-item label="位置">{{ resolvedName(selectedMember.tracking?.locationName, '位置') }}</a-descriptions-item>
              <a-descriptions-item label="舰船"><span class="eve-members__ship-identity"><EveTypeIcon v-if="selectedMember.tracking?.shipTypeId" :type-id="selectedMember.tracking.shipTypeId" :size="24" :alt="`${selectedMember.tracking.shipTypeName || '舰船'}图标`" />{{ resolvedName(selectedMember.tracking?.shipTypeName, '舰船类型') }}</span></a-descriptions-item>
              <a-descriptions-item label="追踪数据时间">{{ formatTime(selectedMember.tracking?.sourceObservedAt) }}</a-descriptions-item>
            </a-descriptions>
          </template>
        </template>
      </a-spin>
    </a-drawer>

    <section v-if="canViewHistory || canOrganize" class="eve-members__operations">
      <a-collapse :default-active-key="['sync-history']">
        <a-collapse-item v-if="canViewHistory" key="sync-history" header="同步历史">
          <a-table :data="syncRuns" :pagination="false" size="small" row-key="id">
            <template #columns>
              <a-table-column title="资源" data-index="resource" :width="110" />
              <a-table-column title="结果" :width="110"><template #cell="{ record }"><a-tag :color="record.status === 'SUCCEEDED' ? 'green' : record.status === 'FAILED' ? 'red' : 'gray'">{{ record.status }}</a-tag></template></a-table-column>
              <a-table-column title="记录数" data-index="recordCount" :width="100" />
              <a-table-column title="开始时间" :width="180"><template #cell="{ record }">{{ formatTime(record.startedAt) }}</template></a-table-column>
              <a-table-column title="数据有效至" :width="180"><template #cell="{ record }">{{ formatTime(record.sourceExpiresAt) }}</template></a-table-column>
              <a-table-column title="失败分类"><template #cell="{ record }">{{ record.failureCode || '—' }}</template></a-table-column>
            </template>
          </a-table>
        </a-collapse-item>
        <a-collapse-item v-if="canOrganize" key="organization-audit" header="成员组织操作记录">
          <a-table :data="organizationAudits" :pagination="false" size="small" row-key="id">
            <template #columns>
              <a-table-column title="操作" data-index="summary" />
              <a-table-column title="操作者" :width="180"><template #cell="{ record }">{{ record.actorUsername || '未知操作者' }}</template></a-table-column>
              <a-table-column title="时间" :width="180"><template #cell="{ record }">{{ formatTime(record.occurredAt) }}</template></a-table-column>
            </template>
          </a-table>
        </a-collapse-item>
      </a-collapse>
    </section>

    <a-modal v-model:visible="organizationVisible" title="编辑成员组织信息" :ok-loading="organizationSaving" @ok="saveOrganization">
      <a-form :model="organizationForm" layout="vertical">
        <a-form-item label="成员分组"><a-input v-model="organizationForm.organizationGroup" allow-clear :max-length="50" placeholder="例如：后勤、生产、外交" /></a-form-item>
        <a-form-item label="成员备注"><a-textarea v-model="organizationForm.memberNote" allow-clear :max-length="500" show-word-limit placeholder="仅军团内部可见" /></a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.eve-members { color: var(--color-text-1); }
.eve-members__header { display: flex; align-items: end; justify-content: space-between; gap: 24px; padding: 28px 30px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 16px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-members__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .16em; }
.eve-members h1 { margin: 8px 0; font-size: 30px; }
.eve-members__header p { margin: 0; color: var(--color-text-3); }
.eve-members__header-tools { display: flex; align-self: flex-start; flex-direction: column; align-items: flex-end; gap: 10px; }
.eve-members__filters { display: flex; gap: 10px; margin: 18px 0; }
.eve-members__member-identity { display: flex; align-items: center; min-width: 0; gap: 10px; }
.eve-members__member-identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.eve-members__ship-identity { display: inline-flex; align-items: center; gap: 8px; }
.eve-members__filters .arco-input-wrapper { width: 280px; }
.eve-members__filters .arco-select { width: 130px; }
.eve-members__table-wrap { padding: 18px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-members__table-wrap .arco-alert { margin-bottom: 14px; }
.eve-members__table-wrap small { display: block; margin-top: 3px; color: var(--color-text-3); font-size: 11px; }
.eve-members__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 16px; }
.eve-members h3 { margin: 24px 0 12px; }
.eve-members__detail-loading { display: block; min-height: 120px; }
.eve-members__organization-button { margin-top: 14px; }
.eve-members__operations { margin-top: 18px; padding: 0 18px 18px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
@media (max-width: 720px) { .eve-members__header { align-items: flex-start; flex-direction: column; } .eve-members__header-tools { align-items: flex-start; } .eve-members__filters { flex-wrap: wrap; } .eve-members__filters .arco-input-wrapper { width: 100%; } }
</style>
