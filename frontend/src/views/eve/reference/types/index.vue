<script setup lang="ts">
import { Message, type RequestOption } from '@arco-design/web-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  type EveStaticTypeCategoryNode,
  type EveStaticTypeReference,
  exportEveStaticTypes,
  getEveStaticTypeCategories,
  getEveStaticTypes,
  importEveStaticReference,
} from '@/apis/eve'
import { useDownload } from '@/hooks'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveStaticTypes' })

const userStore = useUserStore()
const loading = ref(false)
const categoryLoading = ref(false)
const importing = ref(false)
const records = ref<EveStaticTypeReference[]>([])
const total = ref(0)
const selectedType = ref<EveStaticTypeReference>()
const categoryTree = ref<MarketCategoryTreeNode[]>([])
const selectedCategory = ref<MarketCategoryTreeNode>()
const selectedCategoryKeys = ref<string[]>([])
const expandedCategoryKeys = ref<string[]>([])
const query = reactive({ page: 1, size: 20, keyword: '' })
const canExport = computed(() => userStore.permissions.includes('eve:reference:export') || userStore.permissions.includes('*:*:*'))
const canManage = computed(() => userStore.permissions.includes('eve:reference:manage') || userStore.permissions.includes('*:*:*'))
const activeCategoryLabel = computed(() => selectedCategory.value?.name || '物品列表')

interface MarketCategoryTreeNode extends EveStaticTypeCategoryNode {
  key: string
  title: string
  children: MarketCategoryTreeNode[]
}

/** 将最多六级市场分类压缩为便于扫描的一行路径。 */
function categoryPath(record: EveStaticTypeReference) {
  return [record.marketCategoryL1, record.marketCategoryL2, record.marketCategoryL3, record.marketCategoryL4, record.marketCategoryL5, record.marketCategoryL6]
    .filter(Boolean)
    .join(' / ') || '未分类'
}

/** 用不会出现在市场分类名称中的分隔符生成前端树节点键。 */
function categoryKey(path: string[]) {
  return path.join('\u001F') || 'unclassified'
}

/** 将接口分类节点补齐为 Arco 树组件可直接识别的键与标题。 */
function toTreeNode(source: EveStaticTypeCategoryNode): MarketCategoryTreeNode {
  return {
    ...source,
    key: source.unclassified ? 'unclassified' : categoryKey(source.path),
    title: source.name,
    children: source.children.map(toTreeNode),
  }
}

/** 递归收集分类树键，用于一键展开或收起游戏市场导航。 */
function categoryKeys(source: MarketCategoryTreeNode[]): string[] {
  return source.flatMap((node) => [node.key, ...categoryKeys(node.children)])
}

/** 按选中的分类节点生成服务端筛选参数，父分类会包含其全部下级物品。 */
function selectedCategoryQuery() {
  const path = selectedCategory.value?.path || []
  return {
    marketCategoryL1: path[0] || undefined,
    marketCategoryL2: path[1] || undefined,
    marketCategoryL3: path[2] || undefined,
    marketCategoryL4: path[3] || undefined,
    marketCategoryL5: path[4] || undefined,
    marketCategoryL6: path[5] || undefined,
    unclassified: selectedCategory.value?.unclassified || undefined,
  }
}

/** 打开物品资料详情，展示不适合在表格中完整展开的说明和分类。 */
function openDetail(record: EveStaticTypeReference) {
  selectedType.value = record
}

async function loadTypes() {
  loading.value = true
  try {
    const { data } = await getEveStaticTypes({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      ...selectedCategoryQuery(),
    })
    records.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadTypes()
}

function reset() {
  Object.assign(query, { page: 1, keyword: '' })
  selectedCategory.value = undefined
  selectedCategoryKeys.value = []
  loadTypes()
}

/** 读取市场分类树；资料导入成功后会重新加载以反映最新快照。 */
async function loadCategories() {
  categoryLoading.value = true
  try {
    const { data } = await getEveStaticTypeCategories()
    categoryTree.value = data.map(toTreeNode)
  } finally {
    categoryLoading.value = false
  }
}

/** 选择左侧市场分类，并在右侧保留全局关键词搜索能力。 */
function selectCategory(keys: string[]) {
  const key = keys[0]
  selectedCategoryKeys.value = key ? [key] : []
  selectedCategory.value = key ? findCategory(categoryTree.value, key) : undefined
  query.page = 1
  loadTypes()
}

/** 在分类树中按键定位选中节点。 */
function findCategory(source: MarketCategoryTreeNode[], key: string): MarketCategoryTreeNode | undefined {
  for (const node of source) {
    if (node.key === key) return node
    const matched = findCategory(node.children, key)
    if (matched) return matched
  }
}

/** 导出与当前检索口径一致的物品类型资料。 */
function exportTypes() {
  useDownload(
    () => exportEveStaticTypes({ keyword: query.keyword || undefined, ...selectedCategoryQuery() }),
    'EVE静态物品资料.csv',
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
      await Promise.all([loadCategories(), loadTypes()])
      onSuccess(response)
    })
    .catch(onError)
    .finally(() => {
      importing.value = false
    })
  return { abort: () => undefined }
}

onMounted(() => Promise.all([loadCategories(), loadTypes()]))
</script>

<template>
  <main class="eve-static-types-page gi_page">
    <header class="eve-static-types-page__header">
      <div>
        <h1>物品资料</h1>
        <p>按游戏市场分类查找物品，查看图标和说明。</p>
      </div>
      <div class="eve-static-types-page__action-bar">
        <a-button v-if="canExport" @click="exportTypes"><template #icon><icon-download /></template>导出</a-button>
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

    <section class="eve-static-types-page__workspace">
      <aside class="eve-static-types-page__market-panel">
        <div class="eve-static-types-page__panel-heading">
          <h2>市场分类</h2>
        </div>
        <div class="eve-static-types-page__tree-actions">
          <a-button size="mini" @click="expandedCategoryKeys = categoryKeys(categoryTree)">全部展开</a-button>
          <a-button size="mini" @click="expandedCategoryKeys = []">全部收起</a-button>
        </div>
        <a-spin :loading="categoryLoading" class="eve-static-types-page__tree-loading">
          <a-tree
            v-if="categoryTree.length"
            v-model:expanded-keys="expandedCategoryKeys"
            :data="categoryTree"
            :selected-keys="selectedCategoryKeys"
            block-node
            show-line
            class="eve-static-types-page__category-tree"
            @select="selectCategory"
          >
            <template #title="node">
              <div class="eve-static-types-page__category-node">
                <span>{{ node.name }}</span>
              </div>
            </template>
          </a-tree>
          <a-empty v-else description="没有可用的市场分类" />
        </a-spin>
      </aside>

      <section class="eve-static-types-page__items-panel">
        <div class="eve-static-types-page__items-heading">
          <h2>{{ activeCategoryLabel }}</h2>
          <span>共 {{ total.toLocaleString() }} 项</span>
        </div>
        <section class="eve-static-types-page__filter-bar">
          <a-input v-model="query.keyword" allow-clear placeholder="搜索物品名称、说明或分类" @press-enter="search">
            <template #prefix><icon-search /></template>
          </a-input>
          <a-button type="primary" @click="search">查询</a-button>
          <a-button @click="reset">重置</a-button>
        </section>
        <a-table :data="records" :loading="loading" :pagination="false" row-key="typeId" :scroll="{ x: 700 }">
          <template #columns>
            <a-table-column title="物品" :width="210" ellipsis tooltip><template #cell="{ record }"><div class="eve-static-types-page__type-identity"><EveTypeIcon :type-id="record.typeId" :size="26" :alt="`${record.typeName}图标`" /><strong>{{ record.typeName }}</strong></div></template></a-table-column>
            <a-table-column title="完整分类" :width="220" ellipsis tooltip><template #cell="{ record }"><span class="eve-static-types-page__category-path">{{ categoryPath(record) }}</span></template></a-table-column>
            <a-table-column title="物品说明" :min-width="180" ellipsis tooltip><template #cell="{ record }">{{ record.typeDescription || '暂无说明' }}</template></a-table-column>
            <a-table-column title="操作" :width="70" fixed="right"><template #cell="{ record }"><a-button type="text" @click="openDetail(record)">查看</a-button></template></a-table-column>
          </template>
        </a-table>
        <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadTypes" @page-size-change="search" />
      </section>
    </section>

    <a-drawer :visible="Boolean(selectedType)" :width="520" :footer="false" unmount-on-close class="eve-static-types-page__detail-drawer" @update:visible="(visible) => { if (!visible) selectedType = undefined }">
      <template #title>物品资料详情</template>
      <template v-if="selectedType">
        <section class="eve-static-types-page__detail-identity">
          <EveTypeIcon :type-id="selectedType.typeId" :size="64" :alt="`${selectedType.typeName}图标`" />
          <strong>{{ selectedType.typeName }}</strong>
        </section>
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="分类">{{ categoryPath(selectedType) }}</a-descriptions-item>
          <a-descriptions-item label="物品说明"><p class="eve-static-types-page__detail-description">{{ selectedType.typeDescription || '暂无说明' }}</p></a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </main>
</template>

<style scoped lang="scss">
.eve-static-types-page { color: var(--color-text-1); }
.eve-static-types-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px 22px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 14px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-static-types-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-static-types-page h1 { margin: 5px 0; font-size: 26px; }
.eve-static-types-page__header p { max-width: 760px; margin: 0; color: var(--color-text-3); }
.eve-static-types-page__action-bar, .eve-static-types-page__filter-bar { display: flex; align-items: center; gap: 10px; }
.eve-static-types-page__action-bar { flex: none; }
.eve-static-types-page__import-guide { margin-top: 18px; }
.eve-static-types-page__workspace { display: grid; grid-template-columns: minmax(230px, 280px) minmax(0, 1fr); gap: 12px; margin-top: 12px; align-items: start; }
.eve-static-types-page__market-panel, .eve-static-types-page__items-panel { border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-static-types-page__market-panel { overflow: hidden; }
.eve-static-types-page__panel-heading, .eve-static-types-page__items-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 14px 14px 10px; border-bottom: 1px solid var(--color-border-2); }
.eve-static-types-page__panel-heading span, .eve-static-types-page__items-heading span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 10px; letter-spacing: .13em; }
.eve-static-types-page__panel-heading h2, .eve-static-types-page__items-heading h2 { margin: 5px 0 0; font-size: 18px; }
.eve-static-types-page__items-heading > span { color: var(--color-text-3); font-family: inherit; font-size: 13px; letter-spacing: 0; white-space: nowrap; }
.eve-static-types-page__tree-actions { display: flex; gap: 8px; padding: 12px 14px 4px; }
.eve-static-types-page__tree-loading { display: block; min-height: 520px; max-height: calc(100vh - 330px); overflow: auto; padding: 8px 10px 18px; }
.eve-static-types-page__category-tree { min-width: 0; }
.eve-static-types-page__category-node { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-width: 0; }
.eve-static-types-page__category-node > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.eve-static-types-page__category-node small { flex: none; color: var(--color-text-4); font-family: DINPro, monospace; font-size: 11px; }
.eve-static-types-page__items-panel { min-width: 0; overflow: hidden; }
.eve-static-types-page__filter-bar { margin: 10px 14px; }
.eve-static-types-page__filter-bar .arco-input-wrapper { width: min(380px, 100%); }
.eve-static-types-page__items-panel :deep(.arco-table) { border-top: 1px solid var(--color-border-2); }
.eve-static-types-page__items-panel :deep(.arco-pagination) { justify-content: flex-end; margin: 12px 14px; }
.eve-static-types-page__items-panel code { display: block; margin-top: 3px; color: var(--color-text-4); font-family: DINPro, monospace; font-size: 11px; }
.eve-static-types-page__category-path { color: var(--color-text-2); font-size: 13px; }
.eve-static-types-page__type-identity { display: flex; align-items: center; gap: 10px; min-width: 0; }.eve-static-types-page__type-identity > div { min-width: 0; }.eve-static-types-page__type-identity strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.eve-static-types-page__detail-identity { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; padding: 16px; border: 1px solid rgba(var(--arcoblue-6), .16); border-radius: 12px; background: rgba(var(--arcoblue-6), .05); }
.eve-static-types-page__detail-identity div { display: grid; gap: 5px; min-width: 0; }.eve-static-types-page__detail-identity strong { overflow: hidden; font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }.eve-static-types-page__detail-identity code { color: var(--color-text-3); }
.eve-static-types-page__detail-description { margin: 0; color: var(--color-text-2); line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 960px) { .eve-static-types-page__header { align-items: flex-start; flex-direction: column; } .eve-static-types-page__workspace { grid-template-columns: 1fr; } .eve-static-types-page__tree-loading { max-height: 360px; min-height: 180px; } .eve-static-types-page__filter-bar { flex-wrap: wrap; } .eve-static-types-page__filter-bar .arco-input-wrapper { width: 100%; } }
</style>
