<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, ref } from 'vue'
import {
  type EveCorporationAssetTreeNode,
  getEveCorporationAssetTree,
  syncEveCorporationAssets,
} from '@/apis/eve'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveAssets' })

const userStore = useUserStore()
const loading = ref(false)
const syncing = ref(false)
const keyword = ref('')
const assetCount = ref(0)
const nodes = ref<EveCorporationAssetTreeNode[]>([])
const selectedKeys = ref<string[]>([])
const expandedKeys = ref<string[]>([])
const selectedNode = ref<EveCorporationAssetTreeNode>()
const canSync = computed(() => userStore.permissions.includes('eve:assets:manage') || userStore.permissions.includes('*:*:*'))

const treeNodes = computed(() => filterTree(nodes.value, keyword.value.trim().toLowerCase()))

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function nodeKindLabel(kind: EveCorporationAssetTreeNode['kind']) {
  return {
    solar_system: '星系',
    npc_station: 'NPC 空间站',
    player_structure: '玩家建筑',
    corporation_structure: '本军团建筑',
    npc_structure: 'NPC 建筑',
    space_asset: '太空资产',
    corporation_office: '军团办公室',
    corporation_hangar: '军团机库',
    asset_safety_package: '资产安全包裹',
    asset_safety_origin: '原存放位置',
    space_assets: '太空资产',
    warehouse: '仓库',
    structure_compartment: '建筑舱位',
    structure_compartment_group: '建筑分区',
    ship: '舰船',
    ship_compartment_group: '舰船分区',
    ship_compartment: '舰船舱位',
    container: '物品箱',
    asset: '物品',
    unresolved: '未归类',
  }[kind]
}

/** 筛选时保留命中节点的全部父级，让资产物理位置始终可读。 */
function filterTree(source: EveCorporationAssetTreeNode[], search: string): EveCorporationAssetTreeNode[] {
  if (!search) return source
  return source.reduce<EveCorporationAssetTreeNode[]>((result, node) => {
    const children = filterTree(node.children || [], search)
    const searchable = [node.title, node.itemId, node.typeId, node.typeName, node.itemName, node.locationFlag]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    if (searchable.includes(search) || children.length) result.push({ ...node, children })
    return result
  }, [])
}

/** 搜索后收集所有可展开节点，展示命中资产的完整位置路径。 */
function collectExpandableKeys(source: EveCorporationAssetTreeNode[]) {
  return source.flatMap((node) => (node.children?.length ? [node.key, ...collectExpandableKeys(node.children)] : []))
}

/** 按键在树中定位节点，为右侧详情面板提供原始资产信息。 */
function findNode(source: EveCorporationAssetTreeNode[], key?: string): EveCorporationAssetTreeNode | undefined {
  for (const node of source) {
    if (node.key === key) return node
    const child = findNode(node.children || [], key)
    if (child) return child
  }
}

async function loadTree() {
  loading.value = true
  try {
    const { data } = await getEveCorporationAssetTree()
    nodes.value = data.nodes
    assetCount.value = data.assetCount
    expandedKeys.value = []
    selectedKeys.value = []
    selectedNode.value = undefined
  } finally {
    loading.value = false
  }
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveCorporationAssets()
    Message.success(`已同步 ${data.assetCount} 条资产（${data.pageCount} 页）`)
    await loadTree()
  } finally {
    syncing.value = false
  }
}

function search() {
  expandedKeys.value = collectExpandableKeys(treeNodes.value)
}

function reset() {
  keyword.value = ''
  expandedKeys.value = []
}

function selectNode(keys: string[]) {
  selectedKeys.value = keys
  selectedNode.value = findNode(nodes.value, keys[0])
}

onMounted(loadTree)
</script>

<template>
  <main class="eve-assets-page gi_page">
    <header class="eve-assets-page__header">
      <div>
        <span class="eve-assets-page__eyebrow">CORPORATION ASSET SNAPSHOT</span>
        <h1>军团资产</h1>
        <p>按星系、NPC 空间站、玩家建筑、仓位和实际物品容器查看最近一次完整资产快照。</p>
      </div>
      <a-button v-if="canSync" type="primary" :loading="syncing" @click="sync">
        <template #icon><icon-sync /></template>同步资产数据
      </a-button>
    </header>

    <section class="eve-assets-page__filter-bar">
      <a-input v-model="keyword" allow-clear placeholder="搜索物品、类型、仓位或物品 ID" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
      <span class="eve-assets-page__count">当前快照 {{ assetCount }} 条资产</span>
      <a-button size="small" @click="expandedKeys = collectExpandableKeys(treeNodes)">全部展开</a-button>
      <a-button size="small" @click="expandedKeys = []">全部收起</a-button>
    </section>

    <a-alert class="eve-assets-page__guide" type="info" :show-icon="false">
      资料分类由数据库中的 evedata.xlsx 静态资料驱动。舰船内部按“舰船分区 → 装配槽 / 改装槽 / 无人机舱 / 货仓 → 物品”归类；物品箱仅在该物品确有下级资产时显示，本军团建筑内部按“建筑分区 → 具体建筑舱位”归类。
    </a-alert>

    <a-spin :loading="loading" class="eve-assets-page__loading">
      <section class="eve-assets-page__workspace">
        <div class="eve-assets-page__tree-panel">
          <div class="eve-assets-page__panel-heading">
            <div><span>资产位置树</span><h2>从星系逐级展开</h2></div>
            <a-tag>{{ treeNodes.length }} 个星系分组</a-tag>
          </div>
          <a-tree
            v-if="treeNodes.length"
            v-model:expanded-keys="expandedKeys"
            :data="treeNodes"
            :selected-keys="selectedKeys"
            block-node
            show-line
            class="eve-assets-page__tree"
            @select="selectNode"
          >
            <template #title="node">
              <div class="eve-assets-page__tree-node" :class="`eve-assets-page__tree-node--${node.kind}`">
                <a-tag size="small" :color="node.kind === 'container' ? 'orange' : node.kind === 'ship' ? 'purple' : node.kind === 'asset_safety_package' ? 'red' : node.kind === 'asset' ? 'gray' : 'arcoblue'">
                  {{ nodeKindLabel(node.kind) }}
                </a-tag>
                <strong>{{ node.title }}</strong>
                <span v-if="node.itemId && node.quantity !== undefined" class="eve-assets-page__quantity">× {{ node.quantity }}</span>
                <small v-if="node.itemId">#{{ node.itemId }}</small>
              </div>
            </template>
          </a-tree>
          <a-empty v-else description="没有匹配的资产节点" />
        </div>

        <aside class="eve-assets-page__asset-detail">
          <template v-if="selectedNode?.itemId">
            <span class="eve-assets-page__detail-eyebrow">{{ nodeKindLabel(selectedNode.kind) }}</span>
            <h2>{{ selectedNode.itemName || selectedNode.typeName || selectedNode.title }}</h2>
            <p>{{ selectedNode.typeName || `类型 #${selectedNode.typeId}` }}</p>
            <dl>
              <div><dt>物品 ID</dt><dd>#{{ selectedNode.itemId }}</dd></div>
              <div><dt>数量</dt><dd>{{ selectedNode.quantity }}</dd></div>
              <div><dt>仓位</dt><dd>{{ selectedNode.locationFlag || '—' }}</dd></div>
              <div><dt>物品属性</dt><dd>{{ selectedNode.singleton ? '独立物品' : '普通堆叠' }}{{ selectedNode.blueprintCopy ? ' · 蓝图拷贝' : '' }}</dd></div>
              <div><dt>最近同步</dt><dd>{{ formatTime(selectedNode.lastSeenAt) }}</dd></div>
              <div><dt>数据有效至</dt><dd>{{ formatTime(selectedNode.sourceExpiresAt) }}</dd></div>
            </dl>
          </template>
          <a-empty v-else description="选择一个物品查看详情">
            <template #image><icon-storage /></template>
          </a-empty>
        </aside>
      </section>
    </a-spin>
  </main>
</template>

<style scoped lang="scss">
.eve-assets-page { color: var(--color-text-1); }
.eve-assets-page__header { display: flex; align-items: end; justify-content: space-between; gap: 24px; padding: 28px 30px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 16px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-assets-page__eyebrow, .eve-assets-page__detail-eyebrow, .eve-assets-page__panel-heading span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-assets-page h1 { margin: 8px 0; font-size: 30px; }
.eve-assets-page__header p { margin: 0; color: var(--color-text-3); }
.eve-assets-page__filter-bar { display: flex; align-items: center; gap: 10px; margin: 18px 0; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-assets-page__filter-bar .arco-input-wrapper { width: min(360px, 100%); }
.eve-assets-page__count { margin-right: auto; color: var(--color-text-3); font-size: 13px; }
.eve-assets-page__guide { margin-bottom: 16px; }
.eve-assets-page__loading { display: block; min-height: 500px; }
.eve-assets-page__workspace { display: grid; grid-template-columns: minmax(0, 1fr) minmax(280px, 360px); gap: 18px; }
.eve-assets-page__tree-panel, .eve-assets-page__asset-detail { min-width: 0; padding: 20px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-assets-page__panel-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.eve-assets-page__panel-heading h2 { margin: 4px 0 0; font-size: 19px; }
.eve-assets-page__tree { padding: 8px; border: 1px solid var(--color-border-1); border-radius: 10px; background: linear-gradient(90deg, rgba(var(--arcoblue-6), .035), transparent 30%); }
.eve-assets-page__tree :deep(.arco-tree-node) { min-height: 38px; }
.eve-assets-page__tree :deep(.arco-tree-node-title) { min-width: 0; padding: 3px 8px; border-radius: 8px; }
.eve-assets-page__tree-node { display: flex; align-items: center; min-width: 0; gap: 8px; padding: 4px 0; }
.eve-assets-page__tree-node strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.eve-assets-page__tree-node small { color: var(--color-text-3); font-size: 11px; }
.eve-assets-page__tree-node--solar_system strong, .eve-assets-page__tree-node--npc_station strong, .eve-assets-page__tree-node--player_structure strong, .eve-assets-page__tree-node--corporation_structure strong { font-weight: 600; }
.eve-assets-page__tree-node--corporation_structure strong { color: rgb(var(--arcoblue-6)); }
.eve-assets-page__tree-node--warehouse strong, .eve-assets-page__tree-node--structure_compartment strong { color: var(--color-text-2); }
.eve-assets-page__tree-node--structure_compartment_group strong { color: rgb(var(--arcoblue-6)); font-weight: 600; }
.eve-assets-page__quantity { margin-left: auto; color: var(--color-text-2); font-family: DINPro, sans-serif; font-size: 12px; }
.eve-assets-page__asset-detail { align-self: start; position: sticky; top: 18px; }
.eve-assets-page__asset-detail h2 { margin: 7px 0 4px; font-size: 20px; }
.eve-assets-page__asset-detail > p { margin: 0 0 20px; color: var(--color-text-3); }
.eve-assets-page__asset-detail dl { margin: 0; }
.eve-assets-page__asset-detail dl > div { display: flex; justify-content: space-between; gap: 16px; padding: 11px 0; border-top: 1px solid var(--color-border-1); }
.eve-assets-page__asset-detail dt { color: var(--color-text-3); font-size: 12px; }
.eve-assets-page__asset-detail dd { margin: 0; text-align: right; font-size: 13px; }
@media (max-width: 900px) { .eve-assets-page__workspace { grid-template-columns: 1fr; } .eve-assets-page__asset-detail { position: static; } }
@media (max-width: 720px) { .eve-assets-page__header { align-items: flex-start; flex-direction: column; } .eve-assets-page__filter-bar { flex-wrap: wrap; } .eve-assets-page__filter-bar .arco-input-wrapper { width: 100%; } .eve-assets-page__count { width: 100%; margin-right: 0; } }
</style>
