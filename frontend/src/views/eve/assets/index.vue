<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, ref } from 'vue'
import DataFreshnessBanner from '../components/DataFreshnessBanner.vue'
import {
  type EveCorporationAssetTreeNode,
  exportEveCorporationAssets,
  getEveCorporationAssetTree,
  syncEveCorporationAssets,
} from '@/apis/eve'
import { useDownload } from '@/hooks'
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
const inspectorMode = ref<'detail' | 'fitting'>('detail')
const freshnessVersion = ref(0)
const canSync = computed(() => userStore.permissions.includes('eve:assets:manage') || userStore.permissions.includes('*:*:*'))
const canExport = computed(() => userStore.permissions.includes('eve:assets:view') || userStore.permissions.includes('*:*:*'))

const treeNodes = computed(() => hideFittingGroups(filterTree(nodes.value, keyword.value.trim().toLowerCase())))
const selectedFittingGroup = computed(() => findFittingGroup(selectedNode.value))
const selectedFittingSections = computed(() => collectFittingSections(selectedFittingGroup.value))

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
    structure_storage_group: '仓库',
    structure_corporation_hangar_group: '军团机库',
    structure_fitting_group: '建筑装备',
    structure_storage: '仓库分区',
    structure_fitting_slot: '建筑装备槽',
    ship: '舰船',
    ship_storage_group: '舰船仓舱',
    ship_fitting_group: '装配',
    ship_storage: '舰船仓舱',
    ship_fitting_slot: '装配槽',
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

/** 装配只在右侧检查器查看，资产树保留可直接浏览的物理仓舱和容器。 */
function hideFittingGroups(source: EveCorporationAssetTreeNode[]): EveCorporationAssetTreeNode[] {
  return source
    .filter((node) => !['ship_fitting_group', 'structure_fitting_group'].includes(node.kind))
    .map((node) => ({ ...node, children: hideFittingGroups(node.children || []) }))
}

/** 获取舰船或建筑可在右侧展示的装配分区。 */
function findFittingGroup(node?: EveCorporationAssetTreeNode) {
  return node?.children?.find((child) => ['ship_fitting_group', 'structure_fitting_group'].includes(child.kind))
}

/** 判断当前树节点是否拥有已同步的舰船装配或建筑装备。 */
function hasFittings(node: EveCorporationAssetTreeNode) {
  const sourceNode = findNode(nodes.value, node.key) || node
  return Boolean(findFittingGroup(sourceNode)?.children.length)
}

/** 为舰船和建筑节点提供与游戏语义一致的操作文字。 */
function fittingActionLabel(node: EveCorporationAssetTreeNode) {
  return node.kind === 'ship' ? '查看装配' : '查看建筑装备'
}

/** 将已装配项目按游戏槽位整理为右侧检查器中的分区。 */
function collectFittingSections(group?: EveCorporationAssetTreeNode) {
  if (!group) return []
  const sectionOrder = ['高能量槽', '中能量槽', '低能量槽', '改装件', '子系统', '服务槽', '燃料', '量子核心', '其他']
  const sections = new Map<string, EveCorporationAssetTreeNode[]>()
  group.children.forEach((slot) => {
    const title = fittingSectionTitle(slot.title)
    sections.set(title, [...(sections.get(title) || []), slot])
  })
  return [...sections.entries()]
    .sort(([left], [right]) => sectionOrder.indexOf(left) - sectionOrder.indexOf(right))
    .map(([title, slots]) => ({ title, slots }))
}

/** 将槽位名称归入游戏内同类装备分区，未知位置仍保留在可见的“其他”分区。 */
function fittingSectionTitle(slotTitle: string) {
  if (slotTitle.startsWith('高能量槽')) return '高能量槽'
  if (slotTitle.startsWith('中能量槽')) return '中能量槽'
  if (slotTitle.startsWith('低能量槽')) return '低能量槽'
  if (slotTitle.startsWith('改装件槽')) return '改装件'
  if (slotTitle.startsWith('子系统槽')) return '子系统'
  if (slotTitle.startsWith('服务槽')) return '服务槽'
  if (slotTitle === '建筑燃料仓') return '燃料'
  if (slotTitle === '量子核心仓') return '量子核心'
  return '其他'
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
    inspectorMode.value = 'detail'
  } finally {
    loading.value = false
  }
}

async function sync() {
  syncing.value = true
  try {
    const { data } = await syncEveCorporationAssets()
    Message.success(data.message)
    await loadTree()
  } finally {
    syncing.value = false
    freshnessVersion.value += 1
  }
}

/** 导出与当前检索关键词一致的完整资产快照，不以树的折叠状态截断数据。 */
function exportAssets() {
  useDownload(
    () => exportEveCorporationAssets({ keyword: keyword.value.trim() || undefined }),
    '军团资产快照.csv',
    '.csv',
  )
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
  inspectorMode.value = 'detail'
}

/** 从舰船或建筑名称旁直接进入右侧装配检查器。 */
function openFittings(node: EveCorporationAssetTreeNode) {
  selectedKeys.value = [node.key]
  selectedNode.value = findNode(nodes.value, node.key)
  inspectorMode.value = 'fitting'
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
      <div class="eve-assets-page__header-tools">
        <DataFreshnessBanner :modules="['ASSETS']" :reload-token="freshnessVersion" />
        <div class="eve-assets-page__action-bar">
          <a-button v-if="canExport" @click="exportAssets"><template #icon><icon-download /></template>导出 CSV</a-button>
          <a-button v-if="canSync" type="primary" :loading="syncing" @click="sync">
            <template #icon><icon-sync /></template>同步资产数据
          </a-button>
        </div>
      </div>
    </header>

    <section class="eve-assets-page__filter-bar">
      <a-input v-model="keyword" allow-clear placeholder="搜索物品、类型或仓位" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
      <span class="eve-assets-page__count">当前快照 {{ assetCount }} 条资产</span>
      <a-button size="small" @click="expandedKeys = collectExpandableKeys(treeNodes)">全部展开</a-button>
      <a-button size="small" @click="expandedKeys = []">全部收起</a-button>
    </section>

    <a-alert class="eve-assets-page__guide" type="info" :show-icon="false">
      资料分类由数据库中的 evedata.xlsx 静态资料驱动。资产树只展示物理位置、仓库和货舱；舰船装配与建筑装备请从名称旁的按钮在右侧查看。物品箱仅在该物品确有下级资产时显示。
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
                <EveTypeIcon v-if="node.typeId" :type-id="node.typeId" :alt="`${node.typeName || node.title}图标`" />
                <a-tag size="small" :color="node.kind === 'container' ? 'orange' : node.kind === 'ship' ? 'purple' : node.kind === 'asset_safety_package' ? 'red' : node.kind === 'asset' ? 'gray' : 'arcoblue'">
                  {{ nodeKindLabel(node.kind) }}
                </a-tag>
                <strong>{{ node.title }}</strong>
                <a-button v-if="hasFittings(node)" class="eve-assets-page__fitting-action" type="text" size="mini" @click.stop="openFittings(node)">
                  <template #icon><icon-eye /></template>{{ fittingActionLabel(node) }}
                </a-button>
                <span v-if="node.itemId && node.quantity !== undefined" class="eve-assets-page__quantity">× {{ node.quantity }}</span>
              </div>
            </template>
          </a-tree>
          <a-empty v-else description="没有匹配的资产节点" />
        </div>

        <aside class="eve-assets-page__asset-detail">
          <template v-if="selectedNode?.itemId">
            <template v-if="inspectorMode === 'fitting' && selectedFittingGroup">
              <div class="eve-assets-page__detail-identity eve-assets-page__detail-identity--fitting">
                <EveTypeIcon v-if="selectedNode.typeId" :type-id="selectedNode.typeId" :size="64" :alt="`${selectedNode.typeName || selectedNode.title}图标`" />
                <div>
                  <span class="eve-assets-page__detail-eyebrow">{{ selectedNode.kind === 'ship' ? 'SHIP FITTING' : 'STRUCTURE FITTING' }}</span>
                  <h2>{{ selectedNode.itemName || selectedNode.typeName || selectedNode.title }}</h2>
                  <p>{{ selectedNode.kind === 'ship' ? '舰船装配' : '建筑装备' }}</p>
                </div>
                <a-button size="small" @click="inspectorMode = 'detail'">资产详情</a-button>
              </div>
              <section class="eve-assets-page__fitting-panel">
                <div v-for="section in selectedFittingSections" :key="section.title" class="eve-assets-page__fitting-section">
                  <h3>{{ section.title }}</h3>
                  <div class="eve-assets-page__fitting-slots">
                    <article v-for="slot in section.slots" :key="slot.key" class="eve-assets-page__fitting-slot">
                      <span class="eve-assets-page__fitting-slot-name">{{ slot.title }}</span>
                      <div v-for="module in slot.children" :key="module.key" class="eve-assets-page__fitting-module">
                        <EveTypeIcon v-if="module.typeId" :type-id="module.typeId" :size="32" :alt="`${module.typeName || module.title}图标`" />
                        <strong>{{ module.itemName || module.typeName || module.title }}</strong>
                      </div>
                      <span v-if="!slot.children.length" class="eve-assets-page__fitting-empty">未装配</span>
                    </article>
                  </div>
                </div>
              </section>
            </template>
            <template v-else>
              <div class="eve-assets-page__detail-identity">
                <EveTypeIcon v-if="selectedNode.typeId" :type-id="selectedNode.typeId" :size="72" :alt="`${selectedNode.typeName || selectedNode.title}图标`" />
                <div>
                  <span class="eve-assets-page__detail-eyebrow">{{ nodeKindLabel(selectedNode.kind) }}</span>
                  <h2>{{ selectedNode.itemName || selectedNode.typeName || selectedNode.title }}</h2>
                  <p>{{ selectedNode.typeName || '物品类型待补齐' }}</p>
                </div>
              </div>
              <dl>
                <div><dt>数量</dt><dd>{{ selectedNode.quantity }}</dd></div>
                <div><dt>仓位</dt><dd>{{ selectedNode.locationFlag || '—' }}</dd></div>
                <div><dt>物品属性</dt><dd>{{ selectedNode.singleton ? '独立物品' : '普通堆叠' }}{{ selectedNode.blueprintCopy ? ' · 蓝图拷贝' : '' }}</dd></div>
                <div><dt>最近同步</dt><dd>{{ formatTime(selectedNode.lastSeenAt) }}</dd></div>
                <div><dt>数据有效至</dt><dd>{{ formatTime(selectedNode.sourceExpiresAt) }}</dd></div>
              </dl>
            </template>
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
.eve-assets-page__header { display: flex; align-items: end; justify-content: space-between; gap: 16px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-assets-page__eyebrow, .eve-assets-page__detail-eyebrow, .eve-assets-page__panel-heading span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-assets-page h1 { margin: 5px 0; font-size: 26px; }
.eve-assets-page__header p { margin: 0; color: var(--color-text-3); }
.eve-assets-page__header-tools { display: flex; align-self: flex-start; flex-direction: column; align-items: flex-end; gap: 10px; }
.eve-assets-page__action-bar { display: flex; flex-wrap: wrap; gap: 10px; }
.eve-assets-page__filter-bar { display: flex; align-items: center; gap: 8px; margin: 12px 0; padding: 10px 12px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-assets-page__filter-bar .arco-input-wrapper { width: min(360px, 100%); }
.eve-assets-page__count { margin-right: auto; color: var(--color-text-3); font-size: 13px; }
.eve-assets-page__guide { margin-bottom: 12px; }
.eve-assets-page__loading { display: block; min-height: 500px; }
.eve-assets-page__workspace { display: grid; grid-template-columns: minmax(0, 1fr) minmax(260px, 320px); gap: 12px; }
.eve-assets-page__tree-panel, .eve-assets-page__asset-detail { min-width: 0; padding: 14px; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-assets-page__panel-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.eve-assets-page__panel-heading h2 { margin: 4px 0 0; font-size: 19px; }
.eve-assets-page__tree { padding: 8px; border: 1px solid var(--color-border-1); border-radius: 10px; background: linear-gradient(90deg, rgba(var(--arcoblue-6), .035), transparent 30%); }
.eve-assets-page__tree :deep(.arco-tree-node) { min-height: 38px; }
.eve-assets-page__tree :deep(.arco-tree-node-title) { min-width: 0; padding: 3px 8px; border-radius: 8px; }
.eve-assets-page__tree-node { display: flex; align-items: center; min-width: 0; gap: 8px; padding: 4px 0; }
.eve-assets-page__tree-node strong { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.eve-assets-page__fitting-action { flex: none; margin-left: 2px; color: rgb(var(--purple-6)); }
.eve-assets-page__tree-node small { color: var(--color-text-3); font-size: 11px; }
.eve-assets-page__tree-node--solar_system strong, .eve-assets-page__tree-node--npc_station strong, .eve-assets-page__tree-node--player_structure strong, .eve-assets-page__tree-node--corporation_structure strong { font-weight: 600; }
.eve-assets-page__tree-node--corporation_structure strong { color: rgb(var(--arcoblue-6)); }
.eve-assets-page__tree-node--warehouse strong, .eve-assets-page__tree-node--structure_storage strong, .eve-assets-page__tree-node--ship_storage strong { color: var(--color-text-2); }
.eve-assets-page__tree-node--structure_storage_group strong, .eve-assets-page__tree-node--structure_corporation_hangar_group strong, .eve-assets-page__tree-node--ship_storage_group strong { color: rgb(var(--arcoblue-6)); font-weight: 600; }
.eve-assets-page__quantity { margin-left: auto; color: var(--color-text-2); font-family: DINPro, sans-serif; font-size: 12px; }
.eve-assets-page__asset-detail { align-self: start; position: sticky; top: 18px; }
.eve-assets-page__detail-identity { display: flex; align-items: flex-start; gap: 14px; }
.eve-assets-page__detail-identity--fitting { align-items: center; }
.eve-assets-page__detail-identity--fitting > .arco-btn { margin-left: auto; }
.eve-assets-page__asset-detail h2 { margin: 7px 0 4px; font-size: 20px; }
.eve-assets-page__asset-detail p { margin: 0 0 20px; color: var(--color-text-3); }
.eve-assets-page__asset-detail dl { margin: 0; }
.eve-assets-page__asset-detail dl > div { display: flex; justify-content: space-between; gap: 16px; padding: 11px 0; border-top: 1px solid var(--color-border-1); }
.eve-assets-page__asset-detail dt { color: var(--color-text-3); font-size: 12px; }
.eve-assets-page__asset-detail dd { margin: 0; text-align: right; font-size: 13px; }
.eve-assets-page__fitting-panel { display: grid; gap: 18px; margin-top: 22px; }
.eve-assets-page__fitting-section { padding-top: 14px; border-top: 1px solid var(--color-border-1); }
.eve-assets-page__fitting-section h3 { margin: 0 0 10px; color: rgb(var(--purple-6)); font-size: 13px; }
.eve-assets-page__fitting-slots { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 8px; }
.eve-assets-page__fitting-slot { display: grid; min-width: 0; gap: 8px; padding: 10px; border: 1px solid rgba(var(--purple-6), .22); border-radius: 10px; background: rgba(var(--purple-6), .035); }
.eve-assets-page__fitting-slot-name { color: var(--color-text-3); font-size: 11px; }
.eve-assets-page__fitting-module { display: flex; align-items: center; min-width: 0; gap: 7px; }
.eve-assets-page__fitting-module strong { overflow: hidden; font-size: 12px; line-height: 1.45; text-overflow: ellipsis; }
.eve-assets-page__fitting-empty { color: var(--color-text-4); font-size: 12px; }
@media (max-width: 900px) { .eve-assets-page__workspace { grid-template-columns: 1fr; } .eve-assets-page__asset-detail { position: static; } }
@media (max-width: 720px) { .eve-assets-page__header { align-items: flex-start; flex-direction: column; } .eve-assets-page__header-tools { align-items: flex-start; } .eve-assets-page__filter-bar { flex-wrap: wrap; } .eve-assets-page__filter-bar .arco-input-wrapper { width: 100%; } .eve-assets-page__count { width: 100%; margin-right: 0; } }
</style>
