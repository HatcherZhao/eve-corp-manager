<script setup lang="ts">
import { Message, type RequestOption } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  type EveStaticTypeReference,
  exportEveStaticTypes,
  getEveStaticTypes,
  importEveStaticReference,
} from '@/apis/eve'
import { useDownload } from '@/hooks'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveStaticTypes' })

const userStore = useUserStore()
const loading = ref(false)
const importing = ref(false)
const records = ref<EveStaticTypeReference[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', marketCategoryL1: '' })
const canExport = computed(() => userStore.permissions.includes('eve:reference:export') || userStore.permissions.includes('*:*:*'))
const canManage = computed(() => userStore.permissions.includes('eve:reference:manage') || userStore.permissions.includes('*:*:*'))

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

/** 将最多六级市场分类压缩为便于扫描的一行路径。 */
function categoryPath(record: EveStaticTypeReference) {
  return [record.marketCategoryL1, record.marketCategoryL2, record.marketCategoryL3, record.marketCategoryL4, record.marketCategoryL5, record.marketCategoryL6]
    .filter(Boolean)
    .join(' / ') || '未分类'
}

async function loadTypes() {
  loading.value = true
  try {
    const { data } = await getEveStaticTypes({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      marketCategoryL1: query.marketCategoryL1 || undefined,
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
  Object.assign(query, { page: 1, keyword: '', marketCategoryL1: '' })
  loadTypes()
}

/** 导出与当前检索口径一致的物品类型资料。 */
function exportTypes() {
  useDownload(
    () => exportEveStaticTypes({ keyword: query.keyword || undefined, marketCategoryL1: query.marketCategoryL1 || undefined }),
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
      await loadTypes()
      onSuccess(response)
    })
    .catch(onError)
    .finally(() => {
      importing.value = false
    })
  return { abort: () => undefined }
}

onMounted(loadTypes)
</script>

<template>
  <main class="eve-static-types-page gi_page">
    <header class="eve-static-types-page__header">
      <div>
        <span class="eve-static-types-page__eyebrow">EVE STATIC REFERENCE · TYPES</span>
        <h1>物品资料</h1>
        <p>来自 evedata.xlsx 的完整国服物品类型、说明与六级市场分类，用作资产名称和分类的统一基准。</p>
      </div>
      <div class="eve-static-types-page__action-bar">
        <a-button v-if="canExport" @click="exportTypes"><template #icon><icon-download /></template>导出 CSV</a-button>
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

    <a-alert class="eve-static-types-page__import-guide" type="info" :show-icon="true">
      批量更新需上传完整的 <strong>evedata.xlsx</strong>；系统会先校验六张工作表，再原子替换物品与位置资料，失败时保留原版本。
    </a-alert>

    <section class="eve-static-types-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索物品 ID、名称、说明或任意市场分类" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-input v-model="query.marketCategoryL1" allow-clear placeholder="一级市场分类（可选）" @press-enter="search" />
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
      <span class="eve-static-types-page__result-count">共 {{ total.toLocaleString() }} 条资料</span>
    </section>

    <section class="eve-static-types-page__table-wrap">
      <a-table :data="records" :loading="loading" :pagination="false" row-key="typeId" :scroll="{ x: 1260 }">
        <template #columns>
          <a-table-column title="类型 ID" :width="120"><template #cell="{ record }"><code>#{{ record.typeId }}</code></template></a-table-column>
          <a-table-column title="物品名称" :width="220"><template #cell="{ record }"><strong>{{ record.typeName }}</strong></template></a-table-column>
          <a-table-column title="市场分类" :width="350"><template #cell="{ record }"><span class="eve-static-types-page__category-path">{{ categoryPath(record) }}</span></template></a-table-column>
          <a-table-column title="物品说明" :min-width="300" ellipsis tooltip><template #cell="{ record }">{{ record.typeDescription || '—' }}</template></a-table-column>
          <a-table-column title="资料更新时间" :width="170"><template #cell="{ record }">{{ formatTime(record.sourceUpdatedAt) }}</template></a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadTypes" @page-size-change="search" />
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-static-types-page { color: var(--color-text-1); }
.eve-static-types-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 26px 30px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 16px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-static-types-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-static-types-page h1 { margin: 8px 0; font-size: 30px; }
.eve-static-types-page__header p { max-width: 760px; margin: 0; color: var(--color-text-3); }
.eve-static-types-page__action-bar, .eve-static-types-page__filter-bar { display: flex; align-items: center; gap: 10px; }
.eve-static-types-page__action-bar { flex: none; }
.eve-static-types-page__import-guide { margin-top: 18px; }
.eve-static-types-page__filter-bar { margin: 18px 0; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-static-types-page__filter-bar .arco-input-wrapper { width: min(330px, 100%); }
.eve-static-types-page__result-count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-static-types-page__table-wrap { padding: 18px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-static-types-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 16px; }
.eve-static-types-page__table-wrap code { color: rgb(var(--arcoblue-6)); font-family: DINPro, monospace; }
.eve-static-types-page__category-path { color: var(--color-text-2); font-size: 13px; }
@media (max-width: 860px) { .eve-static-types-page__header { align-items: flex-start; flex-direction: column; } .eve-static-types-page__filter-bar { flex-wrap: wrap; } .eve-static-types-page__filter-bar .arco-input-wrapper { width: 100%; } .eve-static-types-page__result-count { width: 100%; margin-left: 0; } }
</style>
