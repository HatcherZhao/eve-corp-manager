<script setup lang="ts">
import { Message, type RequestOption } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  type EveStaticLocationReference,
  exportEveStaticLocations,
  getEveStaticLocations,
  importEveStaticReference,
} from '@/apis/eve'
import { useDownload } from '@/hooks'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveStaticLocations' })

const userStore = useUserStore()
const loading = ref(false)
const importing = ref(false)
const records = ref<EveStaticLocationReference[]>([])
const total = ref(0)
const query = reactive<{ page: number, size: number, keyword: string, referenceType: EveStaticLocationReference['referenceType'] | '' }>({
  page: 1,
  size: 20,
  keyword: '',
  referenceType: '',
})
const canExport = computed(() => userStore.permissions.includes('eve:reference:export') || userStore.permissions.includes('*:*:*'))
const canManage = computed(() => userStore.permissions.includes('eve:reference:manage') || userStore.permissions.includes('*:*:*'))

const locationTypes: Array<{ value: EveStaticLocationReference['referenceType'], label: string }> = [
  { value: 'REGION', label: '星域' },
  { value: 'CONSTELLATION', label: '星座' },
  { value: 'SOLAR_SYSTEM', label: '星系' },
  { value: 'NPC_STATION', label: 'NPC 空间站' },
  { value: 'PUBLIC_STRUCTURE', label: '公开建筑' },
]

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

function typeLabel(type: EveStaticLocationReference['referenceType']) {
  return locationTypes.find((item) => item.value === type)?.label || type
}

async function loadLocations() {
  loading.value = true
  try {
    const { data } = await getEveStaticLocations({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      referenceType: query.referenceType || undefined,
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
  loadLocations()
}

/** 导出与当前检索口径一致的静态位置资料。 */
function exportLocations() {
  useDownload(
    () => exportEveStaticLocations({ keyword: query.keyword || undefined, referenceType: query.referenceType || undefined }),
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
      await loadLocations()
      onSuccess(response)
    })
    .catch(onError)
    .finally(() => {
      importing.value = false
    })
  return { abort: () => undefined }
}

onMounted(loadLocations)
</script>

<template>
  <main class="eve-static-locations-page gi_page">
    <header class="eve-static-locations-page__header">
      <div>
        <span class="eve-static-locations-page__eyebrow">EVE STATIC REFERENCE · LOCATIONS</span>
        <h1>位置资料</h1>
        <p>统一检索国服星域、星座、星系、NPC 空间站和公开建筑，资产树会优先使用这些名称与隶属关系。</p>
      </div>
      <div class="eve-static-locations-page__action-bar">
        <a-button v-if="canExport" @click="exportLocations"><template #icon><icon-download /></template>导出 CSV</a-button>
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

    <a-alert class="eve-static-locations-page__import-guide" type="info" :show-icon="true">
      上传完整 <strong>evedata.xlsx</strong> 会同时更新物品与位置两类参考资料；上传文件不会与当前军团资产或成员数据混合。
    </a-alert>

    <section class="eve-static-locations-page__filter-bar">
      <a-input v-model="query.keyword" allow-clear placeholder="搜索位置 ID、名称或所属星系/星座/星域 ID" @press-enter="search">
        <template #prefix><icon-search /></template>
      </a-input>
      <a-select v-model="query.referenceType" allow-clear placeholder="全部位置类型"><a-option v-for="item in locationTypes" :key="item.value" :value="item.value">{{ item.label }}</a-option></a-select>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
      <span class="eve-static-locations-page__result-count">共 {{ total.toLocaleString() }} 条资料</span>
    </section>

    <section class="eve-static-locations-page__table-wrap">
      <a-table :data="records" :loading="loading" :pagination="false" row-key="referenceId" :scroll="{ x: 1210 }">
        <template #columns>
          <a-table-column title="资料类型" :width="130"><template #cell="{ record }"><a-tag color="arcoblue">{{ typeLabel(record.referenceType) }}</a-tag></template></a-table-column>
          <a-table-column title="位置 ID" :width="150"><template #cell="{ record }"><code>#{{ record.referenceId }}</code></template></a-table-column>
          <a-table-column title="位置名称" :min-width="230"><template #cell="{ record }"><strong>{{ record.referenceName }}</strong></template></a-table-column>
          <a-table-column title="所属星系 ID" :width="150"><template #cell="{ record }">{{ record.solarSystemId ? `#${record.solarSystemId}` : '—' }}</template></a-table-column>
          <a-table-column title="所属星座 ID" :width="150"><template #cell="{ record }">{{ record.constellationId ? `#${record.constellationId}` : '—' }}</template></a-table-column>
          <a-table-column title="所属星域 ID" :width="150"><template #cell="{ record }">{{ record.regionId ? `#${record.regionId}` : '—' }}</template></a-table-column>
          <a-table-column title="安全等级" :width="110"><template #cell="{ record }">{{ record.securityStatus ?? '—' }}</template></a-table-column>
          <a-table-column title="资料更新时间" :width="170"><template #cell="{ record }">{{ formatTime(record.sourceUpdatedAt) }}</template></a-table-column>
        </template>
      </a-table>
      <a-pagination v-if="total" v-model:current="query.page" v-model:page-size="query.size" :total="total" show-total show-page-size @change="loadLocations" @page-size-change="search" />
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-static-locations-page { color: var(--color-text-1); }
.eve-static-locations-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 26px 30px; border: 1px solid rgba(var(--arcoblue-6), .18); border-radius: 16px; background: linear-gradient(125deg, rgba(var(--arcoblue-6), .12), transparent 55%), var(--color-bg-1); }
.eve-static-locations-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .15em; }
.eve-static-locations-page h1 { margin: 8px 0; font-size: 30px; }
.eve-static-locations-page__header p { max-width: 760px; margin: 0; color: var(--color-text-3); }
.eve-static-locations-page__action-bar, .eve-static-locations-page__filter-bar { display: flex; align-items: center; gap: 10px; }
.eve-static-locations-page__action-bar { flex: none; }
.eve-static-locations-page__import-guide { margin-top: 18px; }
.eve-static-locations-page__filter-bar { margin: 18px 0; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-static-locations-page__filter-bar .arco-input-wrapper { width: min(360px, 100%); }
.eve-static-locations-page__filter-bar .arco-select { width: 180px; }
.eve-static-locations-page__result-count { margin-left: auto; color: var(--color-text-3); font-size: 13px; }
.eve-static-locations-page__table-wrap { padding: 18px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); }
.eve-static-locations-page__table-wrap .arco-pagination { justify-content: flex-end; margin-top: 16px; }
.eve-static-locations-page__table-wrap code { color: rgb(var(--arcoblue-6)); font-family: DINPro, monospace; }
@media (max-width: 860px) { .eve-static-locations-page__header { align-items: flex-start; flex-direction: column; } .eve-static-locations-page__filter-bar { flex-wrap: wrap; } .eve-static-locations-page__filter-bar .arco-input-wrapper, .eve-static-locations-page__filter-bar .arco-select { width: 100%; } .eve-static-locations-page__result-count { width: 100%; margin-left: 0; } }
</style>
