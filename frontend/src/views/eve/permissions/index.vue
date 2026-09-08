<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, ref } from 'vue'
import AuthorizationActions from '../components/AuthorizationActions.vue'
import { type EvePermissionNode, type EvePermissionTree, getEvePermissionTree } from '@/apis/eve'

defineOptions({ name: 'EvePermissions' })

const router = useRouter()
const tree = ref<EvePermissionTree>()
const loading = ref(false)
const failed = ref(false)
const keyword = ref('')
const ownedOnly = ref(false)
const expandedKeys = ref<string[]>([])

const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())
const matches = (...values: Array<string | undefined>) => !normalizedKeyword.value
  || values.some((value) => value?.toLowerCase().includes(normalizedKeyword.value))
const nodeVisible = (node: EvePermissionNode) => (!ownedOnly.value || node.owned)
  && matches(node.displayName, node.code, node.description, node.sourceScope, ...node.capabilities)

const filteredCeo = computed(() => {
  const ceo = tree.value?.gamePermissions?.ceo
  return ceo && nodeVisible(ceo) ? ceo : undefined
})
const filteredGroups = computed(() => (tree.value?.gamePermissions?.groups ?? []).map((group) => ({
  ...group,
  children: group.children.filter(nodeVisible),
})).filter((group) => group.children.length || matches(group.displayName, group.sourceScope)))
const filteredRoles = computed(() => (tree.value?.siteRoles?.roles ?? []).filter((role) => matches(
  role.name,
  role.code,
  role.description,
  role.source,
  ...role.permissions,
)))
const filteredScopes = computed(() => (tree.value?.scopes?.items ?? []).filter((scope) => (!ownedOnly.value || scope.owned)
  && matches(scope.displayName, scope.scope, ...scope.capabilities)))

const gameOwnedCount = computed(() => {
  const ceo = tree.value?.gamePermissions?.ceo?.owned ? 1 : 0
  return ceo + (tree.value?.gamePermissions?.groups ?? []).flatMap((group) => group.children).filter((node) => node.owned).length
})
const gameTotalCount = computed(() => (tree.value?.gamePermissions?.ceo ? 1 : 0)
  + (tree.value?.gamePermissions?.groups ?? []).flatMap((group) => group.children).length)
const scopeOwnedCount = computed(() => (tree.value?.scopes?.items ?? []).filter((scope) => scope.owned).length)
const gamePermissionsVerified = computed(() => Boolean(tree.value?.gamePermissions?.checkedAt))
const scopesVerified = computed(() => Boolean(tree.value?.scopes?.authorizationStatus))

function gamePermissionLabel(node: EvePermissionNode) {
  if (node.owned) return '已拥有'
  return gamePermissionsVerified.value ? '未拥有' : '未知'
}

function gamePermissionColor(node: EvePermissionNode) {
  if (node.owned) return 'green'
  return gamePermissionsVerified.value ? 'gray' : 'orange'
}

function scopePermissionLabel(owned: boolean) {
  if (owned) return '已授权'
  return scopesVerified.value ? '未授权' : '未知'
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
}

function expandAll() {
  expandedKeys.value = filteredGroups.value.map((group) => group.key)
}

async function loadTree() {
  loading.value = true
  failed.value = false
  try {
    const { data } = await getEvePermissionTree()
    tree.value = data
    expandedKeys.value = data.gamePermissions?.groups.map((group) => group.key) ?? []
  } catch {
    tree.value = undefined
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadTree)
</script>

<template>
  <main class="eve-permissions-page gi_page">
    <header class="eve-permissions-page__header">
      <div>
        <a-button type="text" class="eve-permissions-page__back" @click="router.push('/eve/workspace')">
          <template #icon><icon-left /></template>
          返回工作台
        </a-button>
        <span class="eve-permissions-page__eyebrow">READ-ONLY ACCESS MAP</span>
        <h1>EVE 权限总览</h1>
        <p>完整展示游戏权限、本站角色与 OAuth Scope；页面仅供查看，不在此处直接授予权限。</p>
      </div>
      <AuthorizationActions class="eve-permissions-page__authorization-actions" @refreshed="loadTree" />
    </header>

    <section class="eve-permissions-page__toolbar">
      <a-input-search v-model="keyword" allow-clear placeholder="搜索权限名称、代码、Scope 或能力" class="eve-permissions-page__search" />
      <a-checkbox v-model="ownedOnly">仅看已拥有</a-checkbox>
      <a-button size="small" @click="expandAll">全部展开</a-button>
      <a-button size="small" @click="expandedKeys = []">全部收起</a-button>
    </section>

    <a-spin :loading="loading" class="eve-permissions-page__loading">
      <a-result v-if="failed" status="error" title="权限树加载失败" subtitle="请检查服务连接后重试。">
        <template #extra><a-button type="primary" @click="loadTree">重新加载</a-button></template>
      </a-result>
      <template v-else-if="tree">
        <a-alert
          v-if="tree.status !== 'READY'"
          type="warning"
          show-icon
          class="eve-permissions-page__status-alert"
        >
          <template #title>权限数据尚未就绪</template>
          {{ tree.message }}。下方仍展示完整权限目录；尚无有效快照的数据统一标记为“未知”。
          <template #action><AuthorizationActions @refreshed="loadTree" /></template>
        </a-alert>

        <section class="eve-permissions-page__summary-strip">
          <div><span>当前角色</span><strong>{{ tree.character?.name || '—' }}</strong></div>
          <div><span>游戏权限</span><strong>{{ gamePermissionsVerified ? `${gameOwnedCount}/${gameTotalCount}` : `待验证/${gameTotalCount}` }}</strong></div>
          <div><span>本站角色</span><strong>{{ tree.siteRoles?.roles.length ?? 0 }}</strong></div>
          <div><span>OAuth Scope</span><strong>{{ scopesVerified ? `${scopeOwnedCount}/${tree.scopes?.items.length ?? 0}` : `待验证/${tree.scopes?.items.length ?? 0}` }}</strong></div>
          <div><span>权限快照</span><strong>{{ formatTime(tree.gamePermissions?.checkedAt) }}</strong></div>
          <div>
            <span>{{ tree.gamePermissions?.sourceExpiryEstimated ? '本地建议刷新时间' : '上游数据有效至' }}</span>
            <strong>{{ formatTime(tree.gamePermissions?.sourceExpiresAt) }}</strong>
          </div>
        </section>

        <section class="eve-permissions-page__section eve-permissions-page__game-permissions">
          <div class="eve-permissions-page__section-heading">
            <div><span>01 / 游戏权限</span><h2>军团角色权限树</h2></div>
            <a-tag>{{ tree.gamePermissions?.dataSource }}</a-tag>
          </div>

          <article v-if="filteredCeo" class="eve-permissions-page__permission-row eve-permissions-page__permission-row--ceo">
            <div class="eve-permissions-page__permission-status" :class="{ 'eve-permissions-page__permission-status--owned': filteredCeo.owned }">
              <icon-check v-if="filteredCeo.owned" /><icon-minus v-else />
            </div>
            <div><strong>{{ filteredCeo.displayName }}</strong><p>{{ filteredCeo.description }}</p></div>
            <code>{{ filteredCeo.code }}</code>
            <a-tag :color="gamePermissionColor(filteredCeo)">{{ gamePermissionLabel(filteredCeo) }}</a-tag>
          </article>

          <a-collapse v-model:active-key="expandedKeys" multiple class="eve-permissions-page__permission-groups">
            <a-collapse-item v-for="group in filteredGroups" :key="group.key" :header="group.displayName">
              <template #extra><span class="eve-permissions-page__scope-label">{{ group.sourceScope }}</span></template>
              <article v-for="node in group.children" :key="node.key" class="eve-permissions-page__permission-row">
                <div class="eve-permissions-page__permission-status" :class="{ 'eve-permissions-page__permission-status--owned': node.owned }">
                  <icon-check v-if="node.owned" /><icon-minus v-else />
                </div>
                <div><strong>{{ node.displayName }}</strong><p>{{ node.description }}</p></div>
                <div class="eve-permissions-page__permission-code">
                  <code>{{ node.code }}</code>
                  <small v-if="node.capabilities.length">关联：{{ node.capabilities.join('、') }}</small>
                </div>
                <a-tag :color="gamePermissionColor(node)">{{ gamePermissionLabel(node) }}</a-tag>
              </article>
              <a-empty v-if="!group.children.length" description="当前筛选条件下没有权限" />
            </a-collapse-item>
          </a-collapse>
          <a-empty v-if="!filteredCeo && !filteredGroups.length" description="没有匹配的游戏权限" />
        </section>

        <section class="eve-permissions-page__section eve-permissions-page__site-roles">
          <div class="eve-permissions-page__section-heading">
            <div><span>02 / 本站角色</span><h2>站内角色与有效权限</h2></div>
            <span class="eve-permissions-page__timestamp">检查于 {{ formatTime(tree.siteRoles?.checkedAt) }}</span>
          </div>
          <div class="eve-permissions-page__role-grid">
            <article v-for="role in filteredRoles" :key="role.id" class="eve-permissions-page__role-card">
              <div><a-tag :color="role.derived ? 'arcoblue' : 'gray'">{{ role.derived ? '游戏派生' : '本站配置' }}</a-tag><a-tag v-if="role.system" color="purple">系统角色</a-tag></div>
              <h3>{{ role.name }}</h3>
              <code>{{ role.code }}</code>
              <p>{{ role.description || '暂无角色说明' }}</p>
              <div class="eve-permissions-page__role-permissions">
                <span v-for="permission in role.permissions" :key="permission">{{ permission }}</span>
              </div>
            </article>
          </div>
          <a-empty v-if="!filteredRoles.length" description="没有匹配的本站角色" />
        </section>

        <section class="eve-permissions-page__section eve-permissions-page__oauth-scopes">
          <div class="eve-permissions-page__section-heading">
            <div><span>03 / OAuth Scope</span><h2>国服授权范围</h2></div>
            <a-tag>{{ tree.scopes?.authorizationStatus }}</a-tag>
          </div>
          <div class="eve-permissions-page__scope-list">
            <article v-for="scope in filteredScopes" :key="scope.key" class="eve-permissions-page__scope-row">
              <div class="eve-permissions-page__permission-status" :class="{ 'eve-permissions-page__permission-status--owned': scope.owned }">
                <icon-check v-if="scope.owned" /><icon-minus v-else />
              </div>
              <div><strong>{{ scope.displayName }}</strong><code>{{ scope.scope }}</code></div>
              <div class="eve-permissions-page__scope-capabilities">
                <a-tag v-if="scope.identityRequired" color="arcoblue">身份必需</a-tag>
                <span v-for="capability in scope.capabilities" :key="capability">{{ capability }}</span>
              </div>
              <a-tag :color="scope.owned ? 'green' : scopesVerified ? 'orange' : 'gray'">
                {{ scopePermissionLabel(scope.owned) }}
              </a-tag>
            </article>
          </div>
          <a-empty v-if="!filteredScopes.length" description="没有匹配的 OAuth Scope" />
        </section>
      </template>
    </a-spin>
  </main>
</template>

<style scoped lang="scss">
.eve-permissions-page { color: var(--color-text-1); }
.eve-permissions-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.eve-permissions-page__back { display: flex; margin: 0 0 12px -12px; }
.eve-permissions-page__eyebrow, .eve-permissions-page__section-heading span:first-child { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: 0.14em; }
.eve-permissions-page__header h1 { margin: 6px 0 6px; font-size: 30px; }
.eve-permissions-page__header p { margin: 0; color: var(--color-text-2); }
.eve-permissions-page__authorization-actions { min-width: 360px; }
.eve-permissions-page__toolbar { display: flex; align-items: center; gap: 14px; margin: 24px 0 16px; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-permissions-page__search { width: min(520px, 100%); margin-right: auto; }
.eve-permissions-page__loading { display: block; min-height: 420px; }
.eve-permissions-page__status-alert { margin-bottom: 16px; }
.eve-permissions-page__summary-strip { display: grid; grid-template-columns: repeat(6, 1fr); overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-permissions-page__summary-strip > div { display: flex; flex-direction: column; gap: 5px; padding: 16px 18px; border-right: 1px solid var(--color-border-2); }
.eve-permissions-page__summary-strip > div:last-child { border-right: 0; }
.eve-permissions-page__summary-strip span { color: var(--color-text-3); font-size: 12px; }
.eve-permissions-page__summary-strip strong { font-family: DINPro, sans-serif; font-size: 15px; }
.eve-permissions-page__section { margin-top: 18px; padding: 22px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-permissions-page__section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.eve-permissions-page__section-heading h2 { margin: 4px 0 0; font-size: 19px; }
.eve-permissions-page__timestamp, .eve-permissions-page__scope-label { color: var(--color-text-3); font-size: 12px; }
.eve-permissions-page__permission-row { display: grid; grid-template-columns: auto minmax(200px, 1fr) minmax(180px, 0.8fr) auto; align-items: center; gap: 14px; padding: 13px 14px; border-bottom: 1px solid var(--color-border-1); }
.eve-permissions-page__permission-row:last-child { border-bottom: 0; }
.eve-permissions-page__permission-row--ceo { margin-bottom: 14px; border: 1px solid rgba(var(--warning-6), 0.24); border-radius: 10px; background: rgba(var(--warning-6), 0.05); }
.eve-permissions-page__permission-row p { margin: 3px 0 0; color: var(--color-text-3); font-size: 12px; }
.eve-permissions-page__permission-status { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 8px; color: var(--color-text-4); background: var(--color-fill-2); }
.eve-permissions-page__permission-status--owned { color: rgb(var(--success-6)); background: rgba(var(--success-6), 0.11); }
.eve-permissions-page__permission-code { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.eve-permissions-page__permission-code code, .eve-permissions-page__scope-row code, .eve-permissions-page__role-card code { overflow-wrap: anywhere; color: var(--color-text-2); font-size: 12px; }
.eve-permissions-page__permission-code small { color: var(--color-text-4); }
.eve-permissions-page__permission-groups { border-radius: 10px; }
.eve-permissions-page__role-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.eve-permissions-page__role-card { padding: 18px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-fill-1); }
.eve-permissions-page__role-card h3 { margin: 14px 0 4px; }
.eve-permissions-page__role-card p { min-height: 38px; color: var(--color-text-3); }
.eve-permissions-page__role-card .arco-tag + .arco-tag { margin-left: 6px; }
.eve-permissions-page__role-permissions { display: flex; flex-wrap: wrap; gap: 6px; }
.eve-permissions-page__role-permissions span, .eve-permissions-page__scope-capabilities span { padding: 3px 7px; border-radius: 6px; color: var(--color-text-2); background: var(--color-fill-2); font-size: 11px; }
.eve-permissions-page__scope-list { border: 1px solid var(--color-border-1); border-radius: 10px; }
.eve-permissions-page__scope-row { display: grid; grid-template-columns: auto minmax(260px, 1.2fr) minmax(220px, 1fr) auto; align-items: center; gap: 14px; padding: 14px; border-bottom: 1px solid var(--color-border-1); }
.eve-permissions-page__scope-row:last-child { border-bottom: 0; }
.eve-permissions-page__scope-row > div:nth-child(2) { display: flex; flex-direction: column; gap: 4px; }
.eve-permissions-page__scope-capabilities { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }

@media (max-width: 1120px) {
  .eve-permissions-page__summary-strip { grid-template-columns: repeat(3, 1fr); }
  .eve-permissions-page__summary-strip > div:nth-child(3) { border-right: 0; }
  .eve-permissions-page__summary-strip > div:nth-child(-n+3) { border-bottom: 1px solid var(--color-border-2); }
  .eve-permissions-page__role-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 820px) {
  .eve-permissions-page__header { align-items: flex-start; flex-direction: column; }
  .eve-permissions-page__authorization-actions { min-width: 0; width: 100%; }
  .eve-permissions-page__toolbar { align-items: flex-start; flex-wrap: wrap; }
  .eve-permissions-page__search { flex-basis: 100%; }
  .eve-permissions-page__permission-row, .eve-permissions-page__scope-row { grid-template-columns: auto 1fr auto; }
  .eve-permissions-page__permission-code, .eve-permissions-page__scope-capabilities { grid-column: 2 / -1; }
}
@media (max-width: 560px) {
  .eve-permissions-page__summary-strip { grid-template-columns: 1fr 1fr; }
  .eve-permissions-page__summary-strip > div { border-bottom: 1px solid var(--color-border-2); }
  .eve-permissions-page__summary-strip > div:nth-child(even) { border-right: 0; }
  .eve-permissions-page__role-grid { grid-template-columns: 1fr; }
  .eve-permissions-page__section { padding: 16px 12px; }
  .eve-permissions-page__permission-row, .eve-permissions-page__scope-row { padding-inline: 6px; }
}
</style>
