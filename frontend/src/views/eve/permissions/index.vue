<script setup lang="ts">
import type { TreeNodeData } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, ref, watch } from 'vue'
import AuthorizationActions from '../components/AuthorizationActions.vue'
import { type EvePermissionNode, type EvePermissionTree, getEvePermissionTree } from '@/apis/eve'

defineOptions({ name: 'EvePermissions' })

type PermissionNodeKind = 'root' | 'group' | 'identity' | 'game-role' | 'site-role' | 'site-permission' | 'scope' | 'capability'

interface PermissionTreeNode extends TreeNodeData {
  kind: PermissionNodeKind
  subtitle?: string
  code?: string
  owned?: boolean
  state?: string
  badges?: string[]
  children?: PermissionTreeNode[]
}

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
const scopesVerified = computed(() => Boolean(tree.value?.scopes?.authorizationStatus))
const gamePermissionsVerified = computed(() => Boolean(tree.value?.gamePermissions?.checkedAt))
const gameOwnedCount = computed(() => {
  const ceo = tree.value?.gamePermissions?.ceo?.owned ? 1 : 0
  return ceo + (tree.value?.gamePermissions?.groups ?? []).flatMap((group) => group.children).filter((node) => node.owned).length
})
const gameTotalCount = computed(() => (tree.value?.gamePermissions?.ceo ? 1 : 0)
  + (tree.value?.gamePermissions?.groups ?? []).flatMap((group) => group.children).length)
const scopeOwnedCount = computed(() => (tree.value?.scopes?.items ?? []).filter((scope) => scope.owned).length)

function gamePermissionLabel(node: EvePermissionNode) {
  if (node.owned) return '已拥有'
  return gamePermissionsVerified.value ? '未拥有' : '未知'
}

function scopePermissionLabel(owned: boolean) {
  if (owned) return '已授权'
  return scopesVerified.value ? '未授权' : '未知'
}

function gameNodeVisible(node: EvePermissionNode) {
  return (!ownedOnly.value || node.owned)
    && matches(node.displayName, node.code, node.description, node.sourceScope, ...node.capabilities)
}

function createGameLeaf(node: EvePermissionNode, kind: PermissionNodeKind = 'game-role'): PermissionTreeNode {
  return {
    key: node.key,
    title: node.displayName,
    subtitle: node.description,
    code: node.code,
    kind,
    owned: node.owned,
    state: gamePermissionLabel(node),
    badges: node.capabilities,
  }
}

function createGameTree(): PermissionTreeNode | undefined {
  const ceo = tree.value?.gamePermissions?.ceo
  const groups = (tree.value?.gamePermissions?.groups ?? []).map((group) => {
    const children = group.children.filter(gameNodeVisible).map((node) => createGameLeaf(node))
    return {
      key: group.key,
      title: group.displayName,
      subtitle: `国服角色范围：${group.sourceScope}`,
      kind: 'group' as const,
      children,
    }
  }).filter((group) => group.children.length || matches(group.title, group.subtitle))
  const children: PermissionTreeNode[] = [
    ...(ceo && gameNodeVisible(ceo) ? [createGameLeaf(ceo, 'identity')] : []),
    ...groups,
  ]
  if (!children.length) return undefined
  return {
    key: 'section:game',
    title: '游戏身份与军团角色',
    subtitle: 'CEO 身份 → 国服四类角色范围 → 具体游戏权限',
    kind: 'root',
    children,
  }
}

function createSiteTree(): PermissionTreeNode | undefined {
  const children = (tree.value?.siteRoles?.roles ?? []).map((role) => {
    const roleMatches = matches(role.name, role.code, role.description, role.source, ...role.permissions)
    const permissions = role.permissions.filter((permission) => matches(permission)).map((permission) => ({
      key: `site-permission:${role.id}:${permission}`,
      title: permission,
      subtitle: '本站业务权限',
      kind: 'site-permission' as const,
    }))
    if (!roleMatches && !permissions.length) return undefined
    return {
      key: `site-role:${role.id}`,
      title: role.name,
      subtitle: role.description || '暂无角色说明',
      code: role.code,
      kind: 'site-role' as const,
      state: role.derived ? '游戏派生' : '本站配置',
      badges: [role.system ? '系统角色' : '', `数据范围：${role.dataScope}`].filter(Boolean),
      children: permissions,
    }
  }).filter((node): node is PermissionTreeNode => Boolean(node))
  if (!children.length) return undefined
  return {
    key: 'section:site',
    title: '本站角色与业务权限',
    subtitle: '本站角色 → 已生效业务权限',
    kind: 'root',
    children,
  }
}

function createScopeTree(): PermissionTreeNode | undefined {
  const children = (tree.value?.scopes?.items ?? []).filter((scope) => (!ownedOnly.value || scope.owned)
    && matches(scope.displayName, scope.scope, ...scope.capabilities)).map((scope) => ({
    key: scope.key,
    title: scope.displayName,
    subtitle: scope.identityRequired ? '身份识别与军团运营授权包' : '已确认功能的国服授权范围',
    code: scope.scope,
    kind: 'scope' as const,
    owned: scope.owned,
    state: scopePermissionLabel(scope.owned),
    badges: [scope.identityRequired ? '身份必需' : ''].filter(Boolean),
    children: scope.capabilities.map((capability) => ({
      key: `${scope.key}:capability:${capability}`,
      title: capability,
      subtitle: '关联平台能力',
      kind: 'capability' as const,
    })),
  }))
  if (!children.length) return undefined
  return {
    key: 'section:scope',
    title: '国服 OAuth 授权范围',
    subtitle: '功能用途 → OAuth Scope → 关联平台能力',
    kind: 'root',
    children,
  }
}

const permissionTree = computed<PermissionTreeNode[]>(() => [
  createGameTree(),
  createSiteTree(),
  createScopeTree(),
].filter((node): node is PermissionTreeNode => Boolean(node)))

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
}

function collectBranchKeys(nodes: PermissionTreeNode[]): string[] {
  return nodes.flatMap((node) => [
    ...(node.children?.length ? [String(node.key)] : []),
    ...collectBranchKeys(node.children ?? []),
  ])
}

function expandAll() {
  expandedKeys.value = collectBranchKeys(permissionTree.value)
}

/** 搜索时自动展开匹配路径，避免结果隐藏在折叠的角色范围内。 */
watch(keyword, (value) => {
  if (value.trim() && permissionTree.value.length) {
    expandAll()
  }
})

async function loadTree() {
  loading.value = true
  failed.value = false
  try {
    const { data } = await getEvePermissionTree()
    tree.value = data
    // 首屏只展开三条主干；四类游戏角色范围默认折叠，避免近两百项权限平铺成普通列表。
    expandedKeys.value = ['section:game', 'section:site', 'section:scope']
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
        <p>沿着树状层级查看身份、游戏角色、本站权限和授权范围；页面仅供核对，不在此处直接授予权限。</p>
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
        <a-alert v-if="tree.status !== 'READY'" type="warning" show-icon class="eve-permissions-page__status-alert">
          <template #title>权限数据尚未就绪</template>
          {{ tree.message }}。下方仍展示完整目录；尚无有效快照的数据统一标记为“未知”。
          <template #action><AuthorizationActions @refreshed="loadTree" /></template>
        </a-alert>

        <section class="eve-permissions-page__summary-strip">
          <div><span>当前角色</span><strong>{{ tree.character?.name || '—' }}</strong></div>
          <div><span>游戏权限</span><strong>{{ gamePermissionsVerified ? `${gameOwnedCount}/${gameTotalCount}` : `待验证/${gameTotalCount}` }}</strong></div>
          <div><span>本站角色</span><strong>{{ tree.siteRoles?.roles.length ?? 0 }}</strong></div>
          <div><span>OAuth Scope</span><strong>{{ scopesVerified ? `${scopeOwnedCount}/${tree.scopes?.items.length ?? 0}` : `待验证/${tree.scopes?.items.length ?? 0}` }}</strong></div>
          <div><span>权限快照</span><strong>{{ formatTime(tree.gamePermissions?.checkedAt) }}</strong></div>
          <div><span>{{ tree.gamePermissions?.sourceExpiryEstimated ? '本地建议刷新时间' : '上游数据有效至' }}</span><strong>{{ formatTime(tree.gamePermissions?.sourceExpiresAt) }}</strong></div>
        </section>

        <section class="eve-permissions-page__tree-section">
          <div class="eve-permissions-page__section-heading">
            <div><span>权限关系图</span><h2>从身份到能力的完整树</h2></div>
            <a-tag>{{ tree.scopes?.authorizationStatus || '未授权' }}</a-tag>
          </div>
          <p class="eve-permissions-page__tree-guide">展开父节点即可逐级查看：身份与角色、本站角色与业务权限、OAuth Scope 与关联能力。</p>
          <a-tree v-model:expanded-keys="expandedKeys" :data="permissionTree" block-node show-line :selectable="false" class="eve-permissions-page__tree">
            <template #title="node">
              <div class="eve-permissions-page__tree-node" :class="`eve-permissions-page__tree-node--${node.kind}`">
                <span v-if="node.owned !== undefined" class="eve-permissions-page__tree-status" :class="{ 'eve-permissions-page__tree-status--owned': node.owned }">
                  <icon-check v-if="node.owned" /><icon-minus v-else />
                </span>
                <span v-else class="eve-permissions-page__tree-bullet" />
                <div class="eve-permissions-page__tree-copy"><strong>{{ node.title }}</strong><small v-if="node.subtitle">{{ node.subtitle }}</small></div>
                <code v-if="node.code">{{ node.code }}</code>
                <div v-if="node.badges?.length" class="eve-permissions-page__tree-badges"><span v-for="badge in node.badges" :key="badge">{{ badge }}</span></div>
                <a-tag v-if="node.state" :color="node.owned ? 'green' : node.owned === false ? (gamePermissionsVerified || scopesVerified ? 'gray' : 'orange') : 'arcoblue'">{{ node.state }}</a-tag>
              </div>
            </template>
          </a-tree>
          <a-empty v-if="!permissionTree.length" description="没有匹配的权限节点" />
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
.eve-permissions-page__tree-section { margin-top: 18px; padding: 22px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-permissions-page__section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 8px; }
.eve-permissions-page__section-heading h2 { margin: 4px 0 0; font-size: 19px; }
.eve-permissions-page__tree-guide { margin: 0 0 18px; color: var(--color-text-3); font-size: 13px; }
.eve-permissions-page__tree { padding: 10px 8px; border: 1px solid var(--color-border-1); border-radius: 12px; background: linear-gradient(90deg, rgba(var(--arcoblue-6), 0.035), transparent 30%); }
.eve-permissions-page__tree :deep(.arco-tree-node) { min-height: 44px; }
.eve-permissions-page__tree :deep(.arco-tree-node-title) { min-width: 0; padding: 3px 8px; border-radius: 8px; }
.eve-permissions-page__tree :deep(.arco-tree-node-title:hover) { background: var(--color-fill-1); }
.eve-permissions-page__tree-node { display: flex; align-items: center; min-width: 0; gap: 10px; padding: 6px 0; }
.eve-permissions-page__tree-copy { display: flex; flex: 1; flex-direction: column; min-width: 160px; gap: 2px; }
.eve-permissions-page__tree-copy strong { font-size: 14px; line-height: 20px; }
.eve-permissions-page__tree-copy small { overflow: hidden; color: var(--color-text-3); font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.eve-permissions-page__tree-node code { max-width: 34%; overflow-wrap: anywhere; color: var(--color-text-2); font-size: 12px; }
.eve-permissions-page__tree-status, .eve-permissions-page__tree-bullet { display: grid; width: 24px; height: 24px; flex: 0 0 24px; place-items: center; border-radius: 7px; color: var(--color-text-4); background: var(--color-fill-2); }
.eve-permissions-page__tree-status--owned { color: rgb(var(--success-6)); background: rgba(var(--success-6), 0.11); }
.eve-permissions-page__tree-bullet { width: 8px; height: 8px; flex-basis: 8px; margin-inline: 8px; border-radius: 50%; background: rgb(var(--arcoblue-5)); }
.eve-permissions-page__tree-badges { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 5px; }
.eve-permissions-page__tree-badges span { padding: 3px 7px; border-radius: 6px; color: var(--color-text-2); background: var(--color-fill-2); font-size: 11px; }
.eve-permissions-page__tree-node--root .eve-permissions-page__tree-copy strong { color: rgb(var(--arcoblue-6)); font-size: 15px; }
.eve-permissions-page__tree-node--group .eve-permissions-page__tree-copy strong { font-weight: 600; }
.eve-permissions-page__tree-node--identity { padding-left: 2px; background: rgba(var(--warning-6), 0.06); }
.eve-permissions-page__tree-node--scope .eve-permissions-page__tree-copy strong { color: rgb(var(--arcoblue-6)); }
@media (max-width: 1120px) { .eve-permissions-page__summary-strip { grid-template-columns: repeat(3, 1fr); } .eve-permissions-page__summary-strip > div:nth-child(3) { border-right: 0; } .eve-permissions-page__summary-strip > div:nth-child(-n+3) { border-bottom: 1px solid var(--color-border-2); } }
@media (max-width: 820px) { .eve-permissions-page__header { align-items: flex-start; flex-direction: column; } .eve-permissions-page__authorization-actions { min-width: 0; width: 100%; } .eve-permissions-page__toolbar { align-items: flex-start; flex-wrap: wrap; } .eve-permissions-page__search { flex-basis: 100%; } .eve-permissions-page__tree-node { align-items: flex-start; flex-wrap: wrap; } .eve-permissions-page__tree-copy { flex-basis: calc(100% - 42px); } .eve-permissions-page__tree-node code, .eve-permissions-page__tree-badges { width: calc(100% - 42px); max-width: none; margin-left: 42px; } .eve-permissions-page__tree-badges { justify-content: flex-start; } }
@media (max-width: 560px) { .eve-permissions-page__summary-strip { grid-template-columns: 1fr 1fr; } .eve-permissions-page__summary-strip > div { border-bottom: 1px solid var(--color-border-2); } .eve-permissions-page__summary-strip > div:nth-child(even) { border-right: 0; } .eve-permissions-page__tree-section { padding: 16px 10px; } .eve-permissions-page__tree { padding-inline: 2px; } }
</style>
