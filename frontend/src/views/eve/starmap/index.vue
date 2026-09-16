<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import dayjs from 'dayjs'
import type { EChartsOption } from 'echarts'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  type EveStarMapGraph,
  type EveStarMapRoute,
  type EveStarMapSuggestion,
  type EveStarMapSystem,
  archiveEveStarMapAnnotation,
  createEveStarMapAnnotation,
  createEveStarMapRoute,
  getEveStarMapGraph,
  getEveStarMapRoutes,
  getEveStarMapSystem,
  previewEveStarMapRoute,
  suggestEveStarMapSystems,
} from '@/apis/eve'
import { useChart } from '@/hooks'
import { useUserStore } from '@/stores'

defineOptions({ name: 'EveStarMap' })

const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const routesLoading = ref(false)
const searchLoading = ref(false)
const detailLoading = ref(false)
const routeLoading = ref(false)
const graph = ref<EveStarMapGraph>()
const routes = ref<EveStarMapRoute[]>([])
const suggestions = ref<EveStarMapSuggestion[]>([])
const selectedSystem = ref<EveStarMapSystem>()
const selectedRoute = ref<EveStarMapRoute>()
const searchKeyword = ref('')
const detailVisible = ref(false)
const annotationVisible = ref(false)
const routeVisible = ref(false)
const selectedNodeId = ref<string>()
const annotation = reactive({ category: '运营', title: '', note: '', colorKey: 'blue', expiresAt: '' })
const routeForm = reactive({ title: '', description: '', originSystemId: '', destinationSystemId: '' })
const routePreview = ref<EveStarMapRoute>()
const routeSuggestions = ref<EveStarMapSuggestion[]>([])
const routeSuggestionLoading = ref(false)
const mapViewMode = ref<'corporation' | 'universe'>('corporation')
const isRouteLibrary = computed(() => route.path.endsWith('/routes'))
const canManageAnnotations = computed(() => userStore.permissions.includes('eve:starmap:annotation:manage') || userStore.permissions.includes('*:*:*'))
const canManageRoutes = computed(() => userStore.permissions.includes('eve:starmap:route:manage') || userStore.permissions.includes('*:*:*'))
const coveragePercent = computed(() => {
  const coverage = graph.value?.coverage
  return !coverage?.indexedSystemCount ? 0 : Math.floor(coverage.synchronizedSystemCount / coverage.indexedSystemCount * 100)
})
const selectedSystemName = computed(() => selectedSystem.value?.name || '未选择星系')
const structureSystemIds = computed(() => new Set((graph.value?.nodes || []).filter((item) => item.hasStructure).map((item) => item.systemId)))
const canFocusStructures = computed(() => structureSystemIds.value.size > 0)

/** 军团聚焦模式保留建筑星系及其直接星门邻居，默认突出真正与军团有关的活动范围。 */
const visibleMapNodes = computed(() => {
  const nodes = graph.value?.nodes || []
  if (mapViewMode.value === 'universe' || !canFocusStructures.value) return nodes
  const systemIds = new Set(structureSystemIds.value)
  for (const edge of graph.value?.edges || []) {
    if (systemIds.has(edge.fromSystemId)) systemIds.add(edge.toSystemId)
    if (systemIds.has(edge.toSystemId)) systemIds.add(edge.fromSystemId)
  }
  return nodes.filter((item) => systemIds.has(item.systemId))
})

/** 当前视图只绘制两端均可见的星门，避免聚焦图出现悬空连线。 */
const visibleMapEdges = computed(() => {
  const systemIds = new Set(visibleMapNodes.value.map((item) => item.systemId))
  return (graph.value?.edges || []).filter((edge) => systemIds.has(edge.fromSystemId) && systemIds.has(edge.toSystemId))
})

/** 安全等级按游戏展示口径截断到一位小数，绝不四舍五入。 */
function formatSecurityStatus(value?: number) {
  if (value === undefined || value === null) return '—'
  return (Math.trunc(value * 10) / 10).toFixed(1)
}

/** 将完整三维坐标投影为当前二维画布中可稳定渲染的坐标。 */
function normalizedNodes() {
  const nodes = visibleMapNodes.value
  const xs = nodes.map((item) => item.x || 0)
  const ys = nodes.map((item) => item.y || 0)
  const minX = Math.min(...xs, 0)
  const maxX = Math.max(...xs, 1)
  const minY = Math.min(...ys, 0)
  const maxY = Math.max(...ys, 1)
  return nodes.map((item) => {
    const x = ((item.x || 0) - minX) / (maxX - minX || 1) * 1000
    const y = ((item.y || 0) - minY) / (maxY - minY || 1) * 680
    const annotated = graph.value?.annotations.some((annotation) => annotation.systemId === item.systemId)
    return {
      ...item,
      x,
      y,
      symbolSize: annotated ? 18 : item.hasStructure ? 16 : item.hasAsset ? 10 : 7,
      itemStyle: { color: annotated ? '#F53F3F' : securityColor(item.securityStatus) },
      label: { show: item.hasStructure || annotated || nodes.length <= 160 || item.systemId === selectedNodeId.value, formatter: item.name },
    }
  })
}

/** 安全等级采用由红到蓝的游戏直觉色阶，避免把数值藏在 Tooltip 中。 */
function securityColor(security?: number) {
  if (security === undefined || security === null) return '#86909C'
  if (security < 0.1) return '#F53F3F'
  if (security < 0.5) return '#FF7D00'
  return '#246EFF'
}

const { chartOption } = useChart((isDark): EChartsOption => {
  const nodes = normalizedNodes()
  const index = new Map(nodes.map((item, position) => [item.systemId, position]))
  return {
    animation: !(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false),
    animationDuration: 500,
    animationEasing: 'cubicOut',
    tooltip: {
      formatter: (params: any) => {
        const item = params.data
        return `${item.name}<br>安等：${formatSecurityStatus(item.securityStatus)}${item.hasStructure ? '<br>军团建筑' : ''}${item.hasMoonExtraction ? '<br>月矿提取' : ''}${item.hasAsset ? '<br>军团资产' : ''}${item.trackedMemberCount ? `<br>成员追踪：${item.trackedMemberCount} 人` : ''}`
      },
    },
    series: [{
      type: 'graph',
      layout: 'none',
      roam: true,
      data: nodes,
      links: visibleMapEdges.value.map((edge) => ({ source: index.get(edge.fromSystemId), target: index.get(edge.toSystemId) })).filter((edge) => edge.source !== undefined && edge.target !== undefined),
      lineStyle: { color: isDark ? 'rgba(198, 218, 255, .22)' : 'rgba(74, 110, 165, .26)', width: 1, curveness: 0.06 },
      label: { color: isDark ? '#E5E6EB' : '#1D2129', fontSize: 11, position: 'right' },
      emphasis: { focus: 'adjacency', lineStyle: { width: 2, color: '#246EFF' }, scale: true },
    }],
  }
})

/** 读取当前可渲染的底图和路线库；地图仍可在公开同步未完成时使用已完成区域。 */
async function loadData() {
  loading.value = true
  try {
    graph.value = (await getEveStarMapGraph()).data
  } finally {
    loading.value = false
  }
}

async function loadRoutes() {
  routesLoading.value = true
  try {
    routes.value = (await getEveStarMapRoutes()).data
  } finally {
    routesLoading.value = false
  }
}

/** 星系搜索不依赖星图覆盖率，直接使用 evedata 静态资料。 */
async function searchSystems() {
  if (!searchKeyword.value.trim()) {
    suggestions.value = []
    return
  }
  searchLoading.value = true
  try {
    suggestions.value = (await suggestEveStarMapSystems(searchKeyword.value)).data
  } finally {
    searchLoading.value = false
  }
}

async function openSystem(systemId: string) {
  detailVisible.value = true
  detailLoading.value = true
  selectedNodeId.value = systemId
  try {
    selectedSystem.value = (await getEveStarMapSystem(systemId)).data
  } finally {
    detailLoading.value = false
  }
}

/** ECharts 单击节点时打开详细侧栏，不从图组件事件对象读取不可信 ID。 */
function handleChartClick(params: any) {
  if (params?.data?.systemId) void openSystem(params.data.systemId)
}

function chooseSuggestion(item: EveStarMapSuggestion) {
  searchKeyword.value = item.name
  suggestions.value = []
  void openSystem(item.systemId)
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '等待首次同步'
}

function beginAnnotation() {
  if (!selectedSystem.value) return
  Object.assign(annotation, { category: '运营', title: '', note: '', colorKey: 'blue', expiresAt: '' })
  annotationVisible.value = true
}

async function saveAnnotation() {
  if (!selectedSystem.value) return
  await createEveStarMapAnnotation({
    systemId: selectedSystem.value.systemId,
    category: annotation.category,
    title: annotation.title,
    note: annotation.note || undefined,
    colorKey: annotation.colorKey,
    expiresAt: annotation.expiresAt || undefined,
  })
  annotationVisible.value = false
  Message.success('运营标注已保存')
  await Promise.all([loadData(), openSystem(selectedSystem.value.systemId)])
}

async function removeAnnotation(annotationId: string) {
  await archiveEveStarMapAnnotation(annotationId)
  Message.success('运营标注已归档')
  if (selectedSystem.value) await Promise.all([loadData(), openSystem(selectedSystem.value.systemId)])
}

function beginRoute() {
  Object.assign(routeForm, { title: '', description: '', originSystemId: selectedSystem.value?.systemId || '', destinationSystemId: '' })
  routePreview.value = undefined
  routeVisible.value = true
}

async function previewRoute() {
  if (!routeForm.title.trim() || !routeForm.originSystemId || !routeForm.destinationSystemId) {
    Message.warning('请填写航线名称，并选择起点和终点星系')
    return
  }
  routeLoading.value = true
  try {
    routePreview.value = (await previewEveStarMapRoute({ ...routeForm })).data
  } finally {
    routeLoading.value = false
  }
}

async function saveRoute() {
  if (!routePreview.value) {
    await previewRoute()
    if (!routePreview.value) return
  }
  routeLoading.value = true
  try {
    const saved = (await createEveStarMapRoute({ ...routeForm })).data
    routeVisible.value = false
    selectedRoute.value = saved
    Message.success('军团航线已保存')
    await loadRoutes()
  } finally {
    routeLoading.value = false
  }
}

/** 路线编辑器按名称检索星系，内部 ID 仅作为 API 参数保存，不要求用户知晓。 */
async function searchRouteSystems(keyword: string) {
  if (!keyword.trim()) {
    routeSuggestions.value = []
    return
  }
  routeSuggestionLoading.value = true
  try {
    routeSuggestions.value = (await suggestEveStarMapSystems(keyword)).data
  } finally {
    routeSuggestionLoading.value = false
  }
}

watch(() => route.path, () => {
  if (isRouteLibrary.value) void loadRoutes()
})

onMounted(() => {
  void Promise.all([loadData(), loadRoutes()])
})
</script>

<template>
  <main class="eve-starmap gi_page">
    <header class="eve-starmap__header">
      <div class="eve-starmap__heading">
        <span>NEW EDEN · CORPORATION NAVIGATION</span>
        <h1>{{ isRouteLibrary ? '军团航线库' : '军团星图' }}</h1>
        <p>{{ isRouteLibrary ? '保存并复用经国服路由验证的共享路线。' : '公开星门拓扑叠加军团建筑、资产与运营标注。地图只展示当前账号有权查看的业务信息。' }}</p>
      </div>
      <div class="eve-starmap__header-actions">
        <span class="eve-starmap__freshness">宇宙资料覆盖 {{ coveragePercent }}% · {{ graph?.coverage.synchronizedSystemCount?.toLocaleString() || 0 }}/{{ graph?.coverage.indexedSystemCount?.toLocaleString() || 0 }} 星系</span>
        <a-button v-if="canManageRoutes" type="primary" @click="beginRoute"><template #icon><icon-route /></template>新建航线</a-button>
      </div>
    </header>

    <a-alert v-if="coveragePercent < 100" type="info" :show-icon="true">公开宇宙底图正在后台持续同步，军团建筑所在星系优先完成。已完成区域可以使用；未覆盖区域不会被误显示为没有星门。</a-alert>
    <a-alert v-if="graph?.coverage.lastFailureAt" type="warning" :show-icon="true">最近一次星图同步失败，系统将自动重试；当前继续展示最后成功的公开资料。</a-alert>

    <section class="eve-starmap__search">
      <a-input v-model="searchKeyword" allow-clear placeholder="搜索星系中文或英文名称" @press-enter="searchSystems"><template #prefix><icon-search /></template></a-input>
      <a-button type="primary" :loading="searchLoading" @click="searchSystems">搜索星系</a-button>
      <a-button @click="loadData"><template #icon><icon-refresh /></template>刷新地图</a-button>
      <div v-if="suggestions.length" class="eve-starmap__suggestions">
        <button v-for="item in suggestions" :key="item.systemId" type="button" @click="chooseSuggestion(item)"><strong>{{ item.name }}</strong><span>{{ item.breadcrumb }}</span></button>
      </div>
    </section>

    <section v-if="!isRouteLibrary" class="eve-starmap__map-card">
      <div class="eve-starmap__map-toolbar"><a-radio-group v-if="canFocusStructures" v-model="mapViewMode" type="button" size="small" aria-label="星图显示范围"><a-radio value="corporation">军团建筑聚焦</a-radio><a-radio value="universe">完整星图</a-radio></a-radio-group><span><i class="eve-starmap__dot eve-starmap__dot--high" />高安</span><span><i class="eve-starmap__dot eve-starmap__dot--low" />低安</span><span><i class="eve-starmap__dot eve-starmap__dot--null" />零安</span><span><i class="eve-starmap__dot eve-starmap__dot--marked" />运营标注</span><span>节点大小：建筑 / 月矿 / 资产</span><small>{{ mapViewMode === 'corporation' && canFocusStructures ? `聚焦 ${structureSystemIds.size} 个军团建筑星系及相邻星门` : '拖拽平移，滚轮缩放，点击星系查看详情' }}</small></div>
      <a-spin :loading="loading" class="eve-starmap__chart-loading"><Chart v-if="graph?.nodes.length" :option="chartOption" height="min(68vh, 660px)" @click="handleChartClick" /><a-empty v-else description="公开星图正在准备中，请稍后刷新" /></a-spin>
    </section>

    <section v-else class="eve-starmap__routes">
      <a-spin :loading="routesLoading"><a-empty v-if="!routes.length" description="尚未保存军团航线"><template #extra><a-button v-if="canManageRoutes" type="primary" @click="beginRoute">新建航线</a-button></template></a-empty><article v-for="item in routes" :key="item.id" class="eve-starmap__route-card" @click="selectedRoute = item"><div><span>已验证 {{ formatTime(item.validatedAt) }}</span><h2>{{ item.title }}</h2><p>{{ item.description || '未填写路线说明' }}</p></div><div class="eve-starmap__route-meta"><strong>{{ item.jumpCount }} 跳</strong><small>{{ item.points.map(point => point.systemName).join(' → ') }}</small></div></article></a-spin>
    </section>

    <a-drawer v-model:visible="detailVisible" width="min(620px, 100vw)" :footer="false" unmount-on-close title="星系详情">
      <a-spin :loading="detailLoading" class="eve-starmap__detail-loading">
        <template v-if="selectedSystem">
          <section class="eve-starmap__system-title"><div><span>{{ selectedSystem.regionName || '未知星域' }} / {{ selectedSystem.constellationName || '未知星座' }}</span><h2>{{ selectedSystem.name }}</h2></div><a-tag :color="selectedSystem.securityStatus && selectedSystem.securityStatus < .1 ? 'red' : selectedSystem.securityStatus && selectedSystem.securityStatus < .5 ? 'orange' : 'arcoblue'">安等 {{ formatSecurityStatus(selectedSystem.securityStatus) }}</a-tag></section>
          <div class="eve-starmap__detail-actions"><a-button v-if="canManageAnnotations" @click="beginAnnotation">添加运营标注</a-button><a-button v-if="canManageRoutes" @click="beginRoute">从此星系规划路线</a-button></div>
          <section class="eve-starmap__detail-section"><h3>相邻星系 <small>{{ selectedSystem.neighbors.length }}</small></h3><div class="eve-starmap__neighbor-list"><button v-for="neighbor in selectedSystem.neighbors" :key="neighbor.systemId" type="button" @click="openSystem(neighbor.systemId)">{{ neighbor.name }}</button><span v-if="!selectedSystem.neighbors.length">星门拓扑尚未同步到该星系</span></div></section>
          <section class="eve-starmap__detail-section"><h3>军团建筑 <small>{{ selectedSystem.structures.length }}</small></h3><a-empty v-if="!selectedSystem.structures.length" description="当前账号无可见建筑或该星系没有军团建筑" /><div v-for="structure in selectedSystem.structures" :key="structure.structureId" class="eve-starmap__structure"><strong>{{ structure.name || structure.typeName || '未命名建筑' }}</strong><span>{{ structure.typeName }} · {{ structure.state || '状态未知' }}</span><small v-if="structure.fuelExpiresAt">燃料至 {{ formatTime(structure.fuelExpiresAt) }}</small></div></section>
          <section class="eve-starmap__detail-section"><h3>运营标注 <small>{{ selectedSystem.annotations.length }}</small></h3><a-empty v-if="!selectedSystem.annotations.length" description="尚无当前军团运营标注" /><article v-for="item in selectedSystem.annotations" :key="item.id" class="eve-starmap__annotation"><div><a-tag :color="item.colorKey === 'red' ? 'red' : item.colorKey === 'orange' ? 'orange' : 'arcoblue'">{{ item.category }}</a-tag><strong>{{ item.title }}</strong><p v-if="item.note">{{ item.note }}</p><small v-if="item.expiresAt">到期隐藏：{{ formatTime(item.expiresAt) }}</small></div><a-button v-if="canManageAnnotations" type="text" status="danger" size="mini" @click="removeAnnotation(item.id)">归档</a-button></article></section>
        </template>
      </a-spin>
    </a-drawer>

    <a-modal v-model:visible="annotationVisible" title="添加运营标注" :ok-button-props="{ disabled: !annotation.title.trim() }" @ok="saveAnnotation"><a-form :model="annotation" layout="vertical"><a-form-item label="星系"><a-input :model-value="selectedSystemName" disabled /></a-form-item><a-form-item label="分类" required><a-select v-model="annotation.category" :options="['运营', '安全', '后勤', '集结', '观察']" /></a-form-item><a-form-item label="标题" required><a-input v-model="annotation.title" :max-length="80" show-word-limit /></a-form-item><a-form-item label="说明"><a-textarea v-model="annotation.note" :max-length="1000" show-word-limit /></a-form-item><a-form-item label="颜色"><a-select v-model="annotation.colorKey" :options="[{ label: '蓝色', value: 'blue' }, { label: '橙色', value: 'orange' }, { label: '红色', value: 'red' }]" /></a-form-item><a-form-item label="到期隐藏时间"><a-date-picker v-model="annotation.expiresAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" /></a-form-item></a-form></a-modal>

    <a-modal v-model:visible="routeVisible" title="新建军团航线" :ok-loading="routeLoading" :ok-button-props="{ disabled: !routePreview }" @ok="saveRoute"><a-form :model="routeForm" layout="vertical"><a-alert type="info" :show-icon="true">按星系名称选择起点和终点；保存前会重新按国服当前拓扑验证。</a-alert><a-form-item label="航线名称" required><a-input v-model="routeForm.title" :max-length="80" /></a-form-item><a-form-item label="说明"><a-textarea v-model="routeForm.description" :max-length="1000" /></a-form-item><a-form-item label="起点星系" required><a-select v-model="routeForm.originSystemId" allow-search :filter-option="false" :loading="routeSuggestionLoading" :options="routeSuggestions.map(item => ({ label: item.breadcrumb, value: item.systemId }))" placeholder="输入星系名称" @search="searchRouteSystems" /></a-form-item><a-form-item label="终点星系" required><a-select v-model="routeForm.destinationSystemId" allow-search :filter-option="false" :loading="routeSuggestionLoading" :options="routeSuggestions.map(item => ({ label: item.breadcrumb, value: item.systemId }))" placeholder="输入星系名称" @search="searchRouteSystems" /></a-form-item><a-button type="outline" :loading="routeLoading" @click="previewRoute">按国服规则预览</a-button><a-alert v-if="routePreview" class="eve-starmap__route-preview" type="success" :show-icon="true">{{ routePreview.jumpCount }} 跳：{{ routePreview.points.map(point => point.systemName).join(' → ') }}</a-alert></a-form></a-modal>
  </main>
</template>

<style scoped lang="scss">
.eve-starmap { display: grid; gap: 12px; min-width: 0; }
.eve-starmap__header, .eve-starmap__search, .eve-starmap__map-card, .eve-starmap__routes { min-width: 0; border: 1px solid var(--color-border-2); border-radius: 12px; background: var(--color-bg-1); }
.eve-starmap__header { display: grid; gap: 12px; padding: 18px; border-color: rgba(var(--arcoblue-6), .22); background: linear-gradient(120deg, rgba(var(--arcoblue-6), .11), transparent 60%), var(--color-bg-1); }
.eve-starmap__heading { min-width: 0; }.eve-starmap__heading > span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; font-weight: 700; letter-spacing: .14em; }.eve-starmap h1 { margin: 5px 0; font-size: clamp(22px, 5vw, 27px); }.eve-starmap__heading p { max-width: 780px; margin: 0; color: var(--color-text-3); line-height: 1.65; }.eve-starmap__header-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }.eve-starmap__freshness { color: var(--color-text-3); font-size: 13px; }
.eve-starmap__search { position: relative; display: flex; flex-wrap: wrap; gap: 8px; padding: 10px; z-index: 3; }.eve-starmap__search :deep(.arco-input-wrapper) { width: 100%; min-width: 0; }.eve-starmap__suggestions { position: absolute; z-index: 5; top: calc(100% - 4px); right: 10px; left: 10px; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 8px; box-shadow: 0 8px 24px rgba(0, 0, 0, .14); background: var(--color-bg-1); }.eve-starmap__suggestions button { display: grid; width: 100%; gap: 2px; padding: 9px 12px; border: 0; color: var(--color-text-1); text-align: left; cursor: pointer; background: transparent; }.eve-starmap__suggestions button:hover { background: var(--color-fill-1); }.eve-starmap__suggestions span { overflow: hidden; color: var(--color-text-3); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.eve-starmap__map-card { overflow: hidden; }.eve-starmap__map-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 10px 12px; color: var(--color-text-3); font-size: 12px; border-bottom: 1px solid var(--color-border-2); }.eve-starmap__map-toolbar small { width: 100%; }.eve-starmap__dot { display: inline-block; width: 8px; height: 8px; margin-right: 3px; border-radius: 50%; }.eve-starmap__dot--high { background: #246EFF; }.eve-starmap__dot--low { background: #FF7D00; }.eve-starmap__dot--null, .eve-starmap__dot--marked { background: #F53F3F; }.eve-starmap__chart-loading, .eve-starmap__detail-loading { display: block; min-height: 200px; }.eve-starmap__chart-loading :deep(.echarts) { min-width: 0; }
.eve-starmap__routes { overflow: hidden; }.eve-starmap__route-card { display: grid; gap: 12px; padding: 14px; cursor: pointer; }.eve-starmap__route-card + .eve-starmap__route-card { border-top: 1px solid var(--color-border-2); }.eve-starmap__route-card:hover { background: var(--color-fill-1); }.eve-starmap__route-card span, .eve-starmap__route-card p, .eve-starmap__route-meta small { color: var(--color-text-3); font-size: 12px; }.eve-starmap__route-card h2 { margin: 4px 0; font-size: 17px; }.eve-starmap__route-card p { margin: 0; line-height: 1.55; }.eve-starmap__route-meta { display: grid; justify-items: start; gap: 6px; min-width: 0; }.eve-starmap__route-meta strong { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 20px; }.eve-starmap__route-meta small { overflow: hidden; width: 100%; text-overflow: ellipsis; white-space: nowrap; }
.eve-starmap__system-title, .eve-starmap__detail-actions, .eve-starmap__annotation { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }.eve-starmap__system-title span, .eve-starmap__detail-section h3 small { color: var(--color-text-3); font-size: 12px; }.eve-starmap__system-title h2 { margin: 3px 0 0; font-size: 23px; }.eve-starmap__detail-actions { flex-wrap: wrap; justify-content: flex-start; margin: 18px 0; }.eve-starmap__detail-section { padding: 14px 0; border-top: 1px solid var(--color-border-2); }.eve-starmap__detail-section h3 { margin: 0 0 10px; font-size: 15px; }.eve-starmap__neighbor-list { display: flex; flex-wrap: wrap; gap: 6px; }.eve-starmap__neighbor-list button { padding: 5px 8px; border: 1px solid var(--color-border-2); border-radius: 5px; color: rgb(var(--arcoblue-6)); cursor: pointer; background: var(--color-fill-1); }.eve-starmap__neighbor-list span { color: var(--color-text-3); font-size: 13px; }.eve-starmap__structure { display: grid; gap: 3px; padding: 8px 0; }.eve-starmap__structure + .eve-starmap__structure { border-top: 1px dashed var(--color-border-2); }.eve-starmap__structure span, .eve-starmap__structure small, .eve-starmap__annotation p, .eve-starmap__annotation small { color: var(--color-text-3); font-size: 13px; }.eve-starmap__annotation { padding: 9px; border: 1px solid var(--color-border-2); border-radius: 8px; }.eve-starmap__annotation div { min-width: 0; }.eve-starmap__annotation strong { display: inline-block; margin-left: 8px; }.eve-starmap__annotation p { margin: 7px 0; overflow-wrap: anywhere; }
.eve-starmap__route-preview { margin-top: 12px; }
@media (min-width: 760px) { .eve-starmap__header { grid-template-columns: minmax(0, 1fr) auto; align-items: end; padding: 20px 22px; }.eve-starmap__header-actions { justify-content: flex-end; }.eve-starmap__search :deep(.arco-input-wrapper) { width: min(460px, 100%); }.eve-starmap__map-toolbar small { width: auto; margin-left: auto; }.eve-starmap__route-card { grid-template-columns: minmax(0, 1fr) minmax(210px, .8fr); align-items: center; padding: 16px 18px; }.eve-starmap__route-meta { justify-items: end; }.eve-starmap__route-meta small { text-align: right; } }
@media (prefers-reduced-motion: reduce) { .eve-starmap *, .eve-starmap *::before, .eve-starmap *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; } }
</style>
