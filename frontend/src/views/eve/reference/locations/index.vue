<script setup lang="ts">
import { Message, type RequestOption } from '@arco-design/web-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  type EveStaticLocationReference,
  type EveStaticLocationTreeNode,
  exportEveStaticLocations,
  getEveStaticLocationTree,
  getEveStaticLocations,
  importEveStaticReference,
} from '@/apis/eve'
import { useDownload } from '@/hooks'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveStaticLocations' })

const userStore = useUserStore()
const loading = ref(false)
const treeLoading = ref(false)
const importing = ref(false)
const records = ref<EveStaticLocationReference[]>([])
const total = ref(0)
const selectedLocation = ref<EveStaticLocationReference>()
const locationTree = ref<LocationTreeNode[]>([])
const selectedTreeNode = ref<LocationTreeNode>()
const selectedTreeKeys = ref<string[]>([])
const expandedTreeKeys = ref<string[]>([])
const query = reactive<{ page: number, size: number, keyword: string, referenceType: EveStaticLocationReference['referenceType'] | '' }>({
  page: 1,
  size: 20,
  keyword: '',
  referenceType: '',
})
const canExport = computed(() => userStore.permissions.includes('eve:reference:export') || userStore.permissions.includes('*:*:*'))
const canManage = computed(() => userStore.permissions.includes('eve:reference:manage') || userStore.permissions.includes('*:*:*'))
const activeLocationLabel = computed(() => selectedTreeNode.value?.referenceName || '全部位置')
interface LocationTreeNode extends EveStaticLocationTreeNode {
  key: string
  title: string
  children: LocationTreeNode[]
}

const locationTypes: Array<{ value: EveStaticLocationReference['referenceType'], label: string }> = [
  { value: 'REGION', label: '星域' },
  { value: 'CONSTELLATION', label: '星座' },
  { value: 'SOLAR_SYSTEM', label: '星系' },
  { value: 'NPC_STATION', label: 'NPC 空间站' },
  { value: 'PUBLIC_STRUCTURE', label: '公开建筑' },
]

function typeLabel(type: EveStaticLocationReference['referenceType']) {
  return locationTypes.find((item) => item.value === type)?.label || type
}

/** 生成不会向用户展示的稳定树节点键。 */
function locationTreeKey(type: EveStaticLocationTreeNode['referenceType'], id: string) {
  return `${type}:${id}`
}

/** 将位置导航接口节点转换为树组件可识别的结构。 */
function toLocationTreeNode(source: EveStaticLocationTreeNode): LocationTreeNode {
  return {
    ...source,
    key: locationTreeKey(source.referenceType, source.referenceId),
    title: source.referenceName,
    children: source.children.map(toLocationTreeNode),
  }
}

/** 递归收集位置树键，用于一键展开或收起。 */
function locationTreeKeys(source: LocationTreeNode[]): string[] {
  return source.flatMap((node) => [node.key, ...locationTreeKeys(node.children)])
}

/** 查找选中的星域、星座或星系节点。 */
function findLocationTreeNode(source: LocationTreeNode[], key: string): LocationTreeNode | undefined {
  for (const node of source) {
    if (node.key === key) return node
    const matched = findLocationTreeNode(node.children, key)
    if (matched) return matched
  }
}

/** 将选中的导航节点转换为服务端下级位置筛选条件。 */
function selectedHierarchyQuery() {
  return selectedTreeNode.value
    ? { hierarchyType: selectedTreeNode.value.referenceType, hierarchyId: selectedTreeNode.value.referenceId }
    : {}
}

/** 打开位置资料详情，保留完整名称和位置归属字段供人工核对。 */
function openDetail(record: EveStaticLocationReference) {
  selectedLocation.value = record
}

async function loadLocations() {
  loading.value = true
  try {
    const { data } = await getEveStaticLocations({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      referenceType: query.referenceType || undefined,
      ...selectedHierarchyQuery(),
    })
    records.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadLocations()
}

function reset() {
  Object.assign(query, { page: 1, keyword: '', referenceType: '' })
  selectedTreeNode.value = undefined
  selectedTreeKeys.value = []
  loadLocations()
}

/** 读取完整星域、星座和星系导航树；资料导入后重新读取最新快照。 */
async function loadLocationTree() {
  treeLoading.value = true
  try {
    const { data } = await getEveStaticLocationTree()
    locationTree.value = data.map(toLocationTreeNode)
  } finally {
    treeLoading.value = false
  }
}

/** 选择位置树节点后，在右侧列出其全部下级位置。 */
function selectLocationTreeNode(keys: string[]) {
  const key = keys[0]
  selectedTreeKeys.value = key ? [key] : []
  selectedTreeNode.value = key ? findLocationTreeNode(locationTree.value, key) : undefined
  query.page = 1
  loadLocations()
}

/** 导出与当前检索口径一致的静态位置资料。 */
function exportLocations() {
  useDownload(
    () => exportEveStaticLocations({ keyword: query.keyword || undefined, referenceType: query.referenceType || undefined, ...selectedHierarchyQuery() }),
    'EVE静态位置资料.csv',
    '.csv',
  )
}

/** 上传完整 evedata.xlsx，服务端校验后同时原子更新物品与位置资料。 */
function importWorkbook(options: RequestOption) {
  const { fileItem, name = 'file', onError, onProgress, onSuccess } = options
  importing.value = true
  onProgress(20)
  const formData = new FormData()
  formData.append(name as string, fileItem.file as Blob)
  importEveStaticReference(formData)
    .then(async (response) => {
      const result = response.data
      Message.success(`静态资料已更新：${result.typeCount} 个物品类型、${result.locationCount} 个位置`)
      await Promise.all([loadLocationTree(), loadLocations()])
      onSuccess(response)
    })
    .catch(onError)
    .finally(() => {
      importing.value = false
    })
  return { abort: () => undefined }
}

onMounted(() => Promise.all([loadLocationTree(), loadLocations()]))
</script>

<template>
  <main class="eve-static-locations-page gi_page">
    <header class="eve-static-locations-page__header">
      <div>
        <h1>位置资料</h1>
        <p>按星图位置查找星域、星座、星系和空间站。</p>
      </div>
      <div class="eve-static-locations-page__action-bar">
        <a-button v-if="canExport" @click="exportLocations"><template #icon><icon-download /></template>导出</a-button>
        <a-upload
          v-if="canManage"
          accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx"
          :show-file-list="false"
          :custom-request="importWorkbook"
        >
          <a-button type="primary" :loading="importing"><template #icon><icon-upload /></template>批量更新资料</a-button>
        </a-upload>
      </div>
    </header>

    <section class="eve-static-locations-page__workspace">
      <aside class="eve-static-locations-page__tree-panel">
        <div class="eve-static-locations-page__panel-heading">
          <h2>星图位置</h2>
        </div>
        <div class="eve-static-locations-page__tree-actions">
          <a-button size="mini" @click="expandedTreeKeys = locationTreeKeys(locationTree)">全部展开</a-button>
          <a-button size="mini" @click="expandedTreeKeys = []">全部收起</a-button>
        </div>
        <a-spin :loading="treeLoading" class="eve-static-locations-page__tree-loading">
          <a-tree
            v-if="locationTree.length"
            v-model:expanded-keys="expandedTreeKeys"
            :data="locationTree"
            :selected-keys="selectedTreeKeys"
            block-node
            show-line
            class="eve-static-locations-page__location-tree"
            @select="selectLocationTreeNode"
          >
            <template #title="node">
              <div class="eve-static-locations-page__tree-node">
                <span>{{ node.referenceName }}</span>
              </div>
            </template>
          </a-tree>
          <a-empty v-else description="没有可用的位置层级" />
        </a-spin>
      </aside>

      <section class="eve-static-locations-page__items-panel">
        <div class="eve-static-locations-page__items-heading">
          <h2>{{ activeLocationLabel }}</h2>
          <span>共 {{ total.toLocaleString() }} 项</span>
        </div>
        <section class="eve-static-locations-page__filter-bar">
          <a-input v-model="query.keyword" allow-clear placeholder="搜索位置名称" @press-enter="search">
            <template #prefix><icon-search /></template>
          </a-input>
          <a-select v-model="query.referenceType" allow-clear placeholder="全部位置类型"><a-option v-for="item in locationTypes" :key="item.value" :value="item.value">{{ item.label }}</a-option></a-select>
          <a-button type="primary" @click="search">查询</a-button>
          <a-button @click="reset">重置</a-button>
        </section>
        <a-table :data="records" :loading="loading" :pagination="false" row-key="referenceId" :scroll="{ x: 560 }">
          <template #columns>
            <a-table-column title="位置" :min-width="180"><template #cell="{ record }"><span class="eve-static-locations-page__location-name"><icon-location /><strong>{{ record.referenceName }}</strong></span></template></a-table-column>
            <a-table-column title="类型" :width="100"><template #cell="{ record }"><a-tag color="arcoblue">{{ typeLabel(record.referenceType) }}</a-tag></template></a-table-column>
            <a-table-column title="安全" :width="75"><template #cell="{ record }">{{ record.securityStatus ?? '—' }}</template></a-table-column>
            <a-table-column title="操作" :width="70" fixed="right"><template #cell="{ record }"><a-button type="text" @click="openDetail(record)">查看</a-button></template></a-table-column>
          </template>
        </a-table>
        <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadLocations" @page-size-change="search" />
      </section>
    </section>

    <a-drawer :visible="Boolean(selectedLocation)" :width="500" :footer="false" unmount-on-close class="eve-static-locations-page__detail-drawer" @update:visible="(visible) => { if (!visible) selectedLocation = undefined }">
      <template #title>位置资料详情</template>
      <template v-if="selectedLocation">
        <section class="eve-static-locations-page__detail-identity">
          <span class="eve-static-locations-page__detail-icon"><icon-location /></span>
          <div><strong>{{ selectedLocation.referenceName }}</strong><span>{{ typeLabel(selectedLocation.referenceType) }}</span></div>
        </section>
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="类型">{{ typeLabel(selectedLocation.referenceType) }}</a-descriptions-item>
          <a-descriptions-item label="安全等级">{{ selectedLocation.securityStatus ?? '—' }}</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-static-locations-page { color: var(--color-text-1); }
.eve-static-locations-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-static-locations-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-static-locations-page h1 { margin: 5px 0; font-size: 26px; }
.eve-static-locations-page__header p { max-width: 760px; margin: 0; color: var(--color-text-3); }
.eve-static-locations-page__action-bar, .eve-static-locations-page__filter-bar { display: flex; align-items: center; gap: 10px; }
.eve-static-locations-page__action-bar { flex: none; }
.eve-static-locations-page__import-guide { margin-top: 18px; }
.eve-static-locations-page__workspace { display: grid; grid-template-columns: minmax(240px, 290px) minmax(0, 1fr); gap: 12px; margin-top: 12px; align-items: start; }
.eve-static-locations-page__tree-panel, .eve-static-locations-page__items-panel { border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-static-locations-page__tree-panel { overflow: hidden; }
.eve-static-locations-page__panel-heading, .eve-static-locations-page__items-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 14px 14px 10px; border-bottom: 1px solid var(--color-border-2); }
.eve-static-locations-page__panel-heading span, .eve-static-locations-page__items-heading span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 10px; letter-spacing: .13em; }
.eve-static-locations-page__panel-heading h2, .eve-static-locations-page__items-heading h2 { margin: 5px 0 0; font-size: 18px; }
.eve-static-locations-page__items-heading > span { color: var(--color-text-3); font-family: inherit; font-size: 13px; letter-spacing: 0; white-space: nowrap; }
.eve-static-locations-page__tree-actions { display: flex; gap: 8px; padding: 12px 14px 4px; }
.eve-static-locations-page__tree-loading { display: block; min-height: 520px; max-height: calc(100vh - 330px); overflow: auto; padding: 8px 10px 18px; }
.eve-static-locations-page__tree-node { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-width: 0; }
.eve-static-locations-page__tree-node > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.eve-static-locations-page__tree-node small { flex: none; color: var(--color-text-4); font-family: DINPro, monospace; font-size: 11px; }
.eve-static-locations-page__items-panel { min-width: 0; overflow: hidden; }
.eve-static-locations-page__filter-bar { margin: 10px 14px; }
.eve-static-locations-page__filter-bar .arco-input-wrapper { width: min(360px, 100%); }
.eve-static-locations-page__filter-bar .arco-select { width: 180px; }
.eve-static-locations-page__items-panel :deep(.arco-table) { border-top: 1px solid var(--color-border-2); }
.eve-static-locations-page__items-panel :deep(.arco-pagination) { justify-content: flex-end; margin: 12px 14px; }
.eve-static-locations-page__location-name { display: inline-flex; align-items: center; gap: 8px; max-width: 100%; color: var(--color-text-2); }.eve-static-locations-page__location-name strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.eve-static-locations-page__detail-identity { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; padding: 16px; border: 1px solid rgba(var(--arcoblue-6), .16); border-radius: 12px; background: rgba(var(--arcoblue-6), .05); }.eve-static-locations-page__detail-icon { display: grid; width: 56px; height: 56px; flex: 0 0 auto; place-items: center; border-radius: 14px; color: rgb(var(--arcoblue-6)); background: rgba(var(--arcoblue-6), .11); font-size: 28px; }.eve-static-locations-page__detail-identity div { display: grid; min-width: 0; gap: 5px; }.eve-static-locations-page__detail-identity strong { overflow: hidden; font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }.eve-static-locations-page__detail-identity div span { color: var(--color-text-3); }
@media (max-width: 960px) { .eve-static-locations-page__header { align-items: flex-start; flex-direction: column; } .eve-static-locations-page__workspace { grid-template-columns: 1fr; } .eve-static-locations-page__tree-loading { max-height: 360px; min-height: 180px; } .eve-static-locations-page__filter-bar { flex-wrap: wrap; } .eve-static-locations-page__filter-bar .arco-input-wrapper, .eve-static-locations-page__filter-bar .arco-select { width: 100%; } }
</style>
