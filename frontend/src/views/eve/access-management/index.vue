<script setup lang="ts">
import { Message, Modal } from '@arco-design/web-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  type EveBusinessRoleReq,
  type EveMemberOperationAudit,
  type EveRbacOverview,
  assignEveMemberRoles,
  createEveBusinessRole,
  deleteEveBusinessRole,
  getEveMemberRoleAudit,
  getEveRbacOverview,
  updateEveBusinessRole,
} from '@/apis/eve'

defineOptions({ name: 'EveAccessManagement' })

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const overview = ref<EveRbacOverview>({ permissions: [], roles: [], members: [] })
const roleAudits = ref<EveMemberOperationAudit[]>([])
const roleModalVisible = ref(false)
const editingRoleId = ref<string>()
const form = reactive<EveBusinessRoleReq>({ name: '', description: '', permissions: [] })
const identityLabels = { OWNER: '军团 CEO', ADMIN: '军团总监', MEMBER: '普通成员', NONE: '未识别' }
const roleOptions = computed(() => overview.value.roles.map((role) => ({ label: role.name, value: role.id })))

/** 将权限审计的站内用户标识转换为当前军团成员的可读名称。 */
function memberDisplayName(userId?: string) {
  const member = overview.value.members.find((item) => item.id === userId)
  return member?.nickname || member?.username || '成员信息不可用'
}

async function loadOverview() {
  loading.value = true
  try {
    const [overviewResponse, auditResponse] = await Promise.all([getEveRbacOverview(), getEveMemberRoleAudit()])
    overview.value = overviewResponse.data
    roleAudits.value = auditResponse.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingRoleId.value = undefined
  Object.assign(form, { name: '', description: '', permissions: [] })
  roleModalVisible.value = true
}

function openEdit(role: EveRbacOverview['roles'][number]) {
  editingRoleId.value = role.id
  Object.assign(form, { name: role.name, description: role.description ?? '', permissions: [...role.permissions] })
  roleModalVisible.value = true
}

async function saveRole() {
  if (!form.name.trim()) {
    Message.warning('请输入角色名称')
    return
  }
  saving.value = true
  try {
    const data = { ...form, name: form.name.trim(), permissions: [...form.permissions] }
    if (editingRoleId.value) await updateEveBusinessRole(editingRoleId.value, data)
    else await createEveBusinessRole(data)
    roleModalVisible.value = false
    Message.success('角色已保存')
    await loadOverview()
  } finally {
    saving.value = false
  }
}

function removeRole(role: EveRbacOverview['roles'][number]) {
  Modal.warning({
    title: '删除业务角色',
    content: `确认删除“${role.name}”？成员的该角色关联会一并移除。`,
    hideCancel: false,
    onOk: async () => {
      await deleteEveBusinessRole(role.id)
      Message.success('角色已删除')
      await loadOverview()
    },
  })
}

async function updateMemberRoles(userId: string, roleIds: string[]) {
  await assignEveMemberRoles(userId, roleIds)
  const member = overview.value.members.find((item) => item.id === userId)
  if (member) member.businessRoleIds = [...roleIds]
  Message.success('成员权限已更新')
  roleAudits.value = (await getEveMemberRoleAudit()).data
}

onMounted(loadOverview)
</script>

<template>
  <main class="eve-access gi_page">
    <header class="eve-access__header">
      <div>
        <a-button type="text" class="eve-access__back" @click="router.push('/eve/workspace')">
          <template #icon><icon-left /></template>返回工作台
        </a-button>
        <h1>军团成员权限</h1>
        <p>CEO 与总监可组合站内权限，并将业务角色分配给本军团成员。</p>
      </div>
      <a-button type="primary" @click="openCreate"><template #icon><icon-plus /></template>新建业务角色</a-button>
    </header>

    <a-spin :loading="loading" class="eve-access__loading">
      <section class="eve-access__section">
        <div class="eve-access__title"><div><span>01</span><h2>业务角色</h2></div><small>游戏派生的 CEO、总监、成员身份不在此处编辑</small></div>
        <div v-if="overview.roles.length" class="eve-access__roles">
          <article v-for="role in overview.roles" :key="role.id">
            <div class="eve-access__role-heading"><h3>{{ role.name }}</h3><code>{{ role.code }}</code></div>
            <p>{{ role.description || '暂无说明' }}</p>
            <div class="eve-access__permissions"><a-tag v-for="permission in role.permissions" :key="permission">{{ permission }}</a-tag></div>
            <footer><a-button size="small" @click="openEdit(role)">编辑</a-button><a-button size="small" status="danger" @click="removeRole(role)">删除</a-button></footer>
          </article>
        </div>
        <a-empty v-else description="尚未创建业务角色" />
      </section>

      <section class="eve-access__section">
        <div class="eve-access__title"><div><span>03</span><h2>权限变更记录</h2></div><small>仅保留操作者、目标成员和操作时间，不记录权限快照</small></div>
        <a-table :data="roleAudits" :pagination="false" size="small" row-key="id">
          <template #columns>
            <a-table-column title="操作" data-index="summary" />
            <a-table-column title="操作者" :width="180"><template #cell="{ record }">{{ record.actorUsername || '未知操作者' }}</template></a-table-column>
            <a-table-column title="目标成员" :width="140"><template #cell="{ record }">{{ memberDisplayName(record.targetUserId) }}</template></a-table-column>
            <a-table-column title="时间" data-index="occurredAt" :width="190" />
          </template>
        </a-table>
      </section>

      <section class="eve-access__section">
        <div class="eve-access__title"><div><span>02</span><h2>成员分配</h2></div><small>保存后在线会话立即刷新权限</small></div>
        <div class="eve-access__members">
          <article v-for="member in overview.members" :key="member.id">
            <div><strong>{{ member.nickname }}</strong><small>@{{ member.username }}</small></div>
            <a-tag color="arcoblue">{{ identityLabels[member.derivedIdentity] }}</a-tag>
            <a-select
              :model-value="member.businessRoleIds"
              multiple
              allow-clear
              placeholder="选择业务角色"
              :options="roleOptions"
              @change="(value) => updateMemberRoles(member.id, value as string[])"
            />
          </article>
        </div>
      </section>
    </a-spin>

    <a-modal v-model:visible="roleModalVisible" :title="editingRoleId ? '编辑业务角色' : '新建业务角色'" :ok-loading="saving" @ok="saveRole">
      <a-form :model="form" layout="vertical">
        <a-form-item label="角色名称" required><a-input v-model="form.name" :max-length="30" show-word-limit /></a-form-item>
        <a-form-item label="角色说明"><a-textarea v-model="form.description" :max-length="200" show-word-limit /></a-form-item>
        <a-form-item label="允许查看的模块">
          <a-checkbox-group v-model="form.permissions" direction="vertical">
            <a-checkbox v-for="item in overview.permissions" :key="item.permission" :value="item.permission">
              {{ item.name }} <code>{{ item.permission }}</code>
            </a-checkbox>
          </a-checkbox-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>

<style scoped lang="scss">
.eve-access { color: var(--color-text-1); }
.eve-access__header, .eve-access__title, .eve-access__role-heading, .eve-access__members article { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.eve-access__header { align-items: flex-end; margin-bottom: 20px; }
.eve-access__header h1 { margin: 8px 0; font-size: 30px; }
.eve-access__header p, .eve-access__title small, .eve-access article p { color: var(--color-text-3); }
.eve-access__back { margin-left: -12px; }
.eve-access__loading { display: block; min-height: 380px; }
.eve-access__section { margin-bottom: 18px; padding: 22px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-access__title { margin-bottom: 16px; }
.eve-access__title > div { display: flex; align-items: baseline; gap: 10px; }
.eve-access__title span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; }
.eve-access__title h2 { margin: 0; font-size: 19px; }
.eve-access__roles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.eve-access__roles article { padding: 18px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-fill-1); }
.eve-access__role-heading { align-items: flex-start; }
.eve-access__role-heading h3 { margin: 0; }
.eve-access code { color: var(--color-text-3); font-size: 11px; }
.eve-access__permissions { display: flex; flex-wrap: wrap; gap: 6px; min-height: 24px; }
.eve-access__roles footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.eve-access__members { border: 1px solid var(--color-border-1); border-radius: 10px; }
.eve-access__members article { display: grid; grid-template-columns: minmax(180px, 0.7fr) 120px minmax(280px, 1fr); padding: 14px; border-bottom: 1px solid var(--color-border-1); }
.eve-access__members article:last-child { border-bottom: 0; }
.eve-access__members strong, .eve-access__members small { display: block; }
@media (max-width: 900px) { .eve-access__roles { grid-template-columns: 1fr; } .eve-access__members article { grid-template-columns: 1fr; } }
</style>
