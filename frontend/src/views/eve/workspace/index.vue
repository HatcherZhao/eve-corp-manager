<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, ref } from 'vue'
import {
  type EveContext,
  type EveCorporationStructure,
  type EveMiningLedgerSummary,
  type EveMoonExtraction,
  getEveContext,
  getEveCorporationStructures,
  getEveMiningLedgerSummary,
  getEveMoonExtractions,
} from '@/apis/eve'
import { getEveCharacterPortraitUrl, getEveCorporationLogoUrl } from '@/utils/eveImage'

defineOptions({ name: 'EveWorkspace' })

const router = useRouter()
const context = ref<EveContext>()
const structures = ref<EveCorporationStructure[]>([])
const extractions = ref<EveMoonExtraction[]>([])
const miningSummary = ref<EveMiningLedgerSummary>()
const structureTotal = ref(0)
const extractionTotal = ref(0)
const structuresReady = ref(false)
const extractionsReady = ref(false)
const miningReady = ref(false)
const loading = ref(false)
const failed = ref(false)
const corporationLogoFailed = ref(false)
const characterPortraitFailed = ref(false)

const identityLabels: Record<string, string> = {
  OWNER: '军团 CEO',
  ADMIN: '军团总监',
  MEMBER: '军团成员',
}
const fuelWarningHours = 7 * 24
const canManageAccess = computed(() => ['OWNER', 'ADMIN'].includes(context.value?.derivedIdentity ?? ''))
const corporationLogoUrl = computed(() => getEveCorporationLogoUrl(context.value?.corporation?.corporationId))
const characterPortraitUrl = computed(() => getEveCharacterPortraitUrl(context.value?.character?.characterId))

/** 即将耗尽燃料或已经到期的建筑，需要优先处理。 */
const fuelRisks = computed(() => structures.value
  .filter((item) => item.fuelExpiresAt && dayjs(item.fuelExpiresAt).diff(dayjs(), 'hour', true) <= fuelWarningHours)
  .sort((left, right) => dayjs(left.fuelExpiresAt).valueOf() - dayjs(right.fuelExpiresAt).valueOf()))

/** 月矿矿块到达即代表进入可开采窗口，按最早到达时间展示。 */
const upcomingExtractions = computed(() => extractions.value
  .filter((item) => dayjs(item.chunkArrivalAt).isAfter(dayjs()))
  .sort((left, right) => dayjs(left.chunkArrivalAt).valueOf() - dayjs(right.chunkArrivalAt).valueOf()))

const nextExtraction = computed(() => upcomingExtractions.value[0])
const fuelOverview = computed(() => {
  if (!structuresReady.value) return { title: '暂无法取得', description: '进入建筑管理查看详情' }
  return fuelRisks.value.length
    ? { title: `${fuelRisks.value.length} 座需关注`, description: `${formatNumber(structureTotal.value)} 座军团建筑` }
    : { title: '暂无临期燃料', description: `${formatNumber(structureTotal.value)} 座军团建筑` }
})
const miningOverview = computed(() => miningReady.value
  ? { quantity: formatNumber(miningSummary.value?.quantity), description: `账本累计 · 覆盖 ${formatNumber(miningSummary.value?.characterCount)} 名采矿成员` }
  : { quantity: '暂无法取得', description: '进入月矿开采统计查看详情' })
const moonOverview = computed(() => {
  if (!extractionsReady.value) return { title: '暂无法取得', description: '进入月矿情报查看详情' }
  return nextExtraction.value
    ? { title: relativeDate(nextExtraction.value.chunkArrivalAt), description: `${resolvedName(nextExtraction.value.structureName, '月矿堡')}下一次可开采` }
    : { title: '暂无计划', description: `${formatNumber(extractionTotal.value)} 条月矿时间线` }
})
const attentionItems = computed(() => [
  ...fuelRisks.value.slice(0, 3).map((item) => ({
    key: `fuel-${item.structureId}`,
    icon: 'fuel',
    title: `${resolvedName(item.structureName, '建筑')}燃料${fuelText(item)}`,
    description: `${resolvedName(item.solarSystemName, '星系')} · ${resolvedName(item.typeName, '建筑')}`,
    action: '查看建筑',
    route: '/eve/structures',
    urgent: true,
  })),
  ...upcomingExtractions.value.slice(0, 3).map((item) => ({
    key: `moon-${item.id}`,
    icon: 'moon',
    title: `${formatMiningDay(item.chunkArrivalAt)}可开采`,
    description: `${resolvedName(item.structureName, '月矿堡')} · ${resolvedName(item.moonName, '月球')}`,
    action: '查看月矿',
    route: '/eve/extractions',
    urgent: false,
  })),
].slice(0, 5))

const quickEntries = [
  { title: '成员与权限', description: '查看成员、身份与军团分工', route: '/eve/members', icon: 'user-group' },
  { title: '军团资产', description: '按星系与建筑查看物资', route: '/eve/assets', icon: 'storage' },
  { title: '建筑管理', description: '建筑状态、服务与燃料', route: '/eve/structures', icon: 'home' },
  { title: '月矿情报', description: '追踪矿块到达与开采窗口', route: '/eve/extractions', icon: 'calendar' },
  { title: '月矿开采统计', description: '分析建筑与成员的开采产出', route: '/eve/mining', icon: 'bar-chart' },
  { title: '游戏内通信', description: '处理军团邮件与游戏通知', route: '/eve/mail/inbox', icon: 'message' },
]

function resolvedName(value: string | undefined, fallback: string) {
  return value || `${fallback}名称待补齐`
}

function formatNumber(value?: number) {
  return (value ?? 0).toLocaleString()
}

function formatMiningDay(value?: string) {
  if (!value) return '—'
  const time = dayjs(value)
  const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][time.day()]
  return `${time.format('M 月 D 日')} ${weekday}`
}

function relativeDate(value?: string) {
  if (!value) return '—'
  const date = dayjs(value).startOf('day')
  const today = dayjs().startOf('day')
  const days = date.diff(today, 'day')
  if (days === 0) return '今天'
  if (days === 1) return '明天'
  if (days > 1 && days <= 7) return `${days} 天后`
  return formatMiningDay(value)
}

function fuelText(item: EveCorporationStructure) {
  if (!item.fuelExpiresAt) return '状态待确认'
  const hours = dayjs(item.fuelExpiresAt).diff(dayjs(), 'hour', true)
  if (hours <= 0) return '已到期'
  if (hours <= 24) return `${Math.ceil(hours)} 小时后到期`
  return `${Math.ceil(hours / 24)} 天后到期`
}

async function loadWorkspace() {
  loading.value = true
  failed.value = false
  try {
    const [contextResult, structuresResult, extractionsResult, miningResult] = await Promise.allSettled([
      getEveContext(),
      getEveCorporationStructures({ page: 1, size: 100 }),
      getEveMoonExtractions({ page: 1, size: 100 }),
      getEveMiningLedgerSummary({}),
    ])
    if (contextResult.status !== 'fulfilled') {
      failed.value = true
      return
    }
    context.value = contextResult.value.data
    structuresReady.value = structuresResult.status === 'fulfilled'
    structures.value = structuresReady.value ? structuresResult.value.data.list : []
    structureTotal.value = structuresReady.value ? structuresResult.value.data.total : 0
    extractionsReady.value = extractionsResult.status === 'fulfilled'
    extractions.value = extractionsReady.value ? extractionsResult.value.data.list : []
    extractionTotal.value = extractionsReady.value ? extractionsResult.value.data.total : 0
    miningReady.value = miningResult.status === 'fulfilled'
    miningSummary.value = miningReady.value ? miningResult.value.data : undefined
    corporationLogoFailed.value = false
    characterPortraitFailed.value = false
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadWorkspace)
</script>

<template>
  <main class="eve-workspace-page gi_page">
    <a-spin :loading="loading" class="eve-workspace-page__loading">
      <a-result v-if="failed" status="error" title="暂时无法打开军团工作台" subtitle="请重新加载；如果提示登录已过期，请重新登录后再试。">
        <template #extra><a-button type="primary" @click="loadWorkspace">重新加载</a-button></template>
      </a-result>

      <template v-else-if="context">
        <header class="eve-workspace-page__hero">
          <div class="eve-workspace-page__identity-block">
            <div class="eve-workspace-page__corporation-logo" aria-label="军团游戏徽标">
              <img v-if="corporationLogoUrl && !corporationLogoFailed" :src="corporationLogoUrl" :alt="`${context.corporation?.name || '军团'}徽标`" @error="corporationLogoFailed = true" />
              <icon-user-group v-else />
            </div>
            <div class="eve-workspace-page__identity">
              <span class="eve-workspace-page__eyebrow">CORPORATION OPERATIONS</span>
              <h1>{{ context.corporation?.name || '未绑定军团' }}</h1>
              <div class="eve-workspace-page__identity-meta">
                <span v-if="context.corporation">[{{ context.corporation.ticker }}]</span>
                <span class="eve-workspace-page__character">
                  <img v-if="characterPortraitUrl && !characterPortraitFailed" :src="characterPortraitUrl" :alt="`${context.character?.name || '当前角色'}肖像`" @error="characterPortraitFailed = true" />
                  {{ context.character?.name || '未绑定角色' }}
                </span>
                <a-tag color="arcoblue">{{ identityLabels[context.derivedIdentity] || '军团成员' }}</a-tag>
              </div>
            </div>
          </div>
          <div class="eve-workspace-page__hero-actions">
            <a-button @click="router.push('/user/profile')">账号与绑定</a-button>
            <a-button v-if="canManageAccess" type="primary" @click="router.push('/eve/access-management')">成员权限管理</a-button>
          </div>
        </header>

        <section class="eve-workspace-page__overview" aria-label="军团运营概览">
          <button type="button" class="eve-workspace-page__metric eve-workspace-page__metric--mining" @click="router.push('/eve/mining')">
            <span>采矿总量</span>
            <strong>{{ miningOverview.quantity }}</strong>
            <small>{{ miningOverview.description }}</small>
            <icon-bar-chart class="eve-workspace-page__metric-icon" />
          </button>
          <button type="button" class="eve-workspace-page__metric eve-workspace-page__metric--fuel" @click="router.push('/eve/structures')">
            <span>建筑燃料</span>
            <strong>{{ fuelOverview.title }}</strong>
            <small>{{ fuelOverview.description }}</small>
            <icon-fire class="eve-workspace-page__metric-icon" />
          </button>
          <button type="button" class="eve-workspace-page__metric eve-workspace-page__metric--moon" @click="router.push('/eve/extractions')">
            <span>月矿周期</span>
            <strong>{{ moonOverview.title }}</strong>
            <small>{{ moonOverview.description }}</small>
            <icon-calendar class="eve-workspace-page__metric-icon" />
          </button>
        </section>

        <section class="eve-workspace-page__content-grid">
          <section class="eve-workspace-page__attention">
            <div class="eve-workspace-page__section-heading">
              <div><span>行动清单</span><h2>近期需要关注</h2></div>
              <p>从燃料和月矿周期中筛出近期事项。</p>
            </div>
            <div v-if="attentionItems.length" class="eve-workspace-page__attention-list">
              <button v-for="item in attentionItems" :key="item.key" type="button" class="eve-workspace-page__attention-item" :class="{ 'eve-workspace-page__attention-item--urgent': item.urgent }" @click="router.push(item.route)">
                <span class="eve-workspace-page__attention-icon"><icon-fire v-if="item.icon === 'fuel'" /><icon-calendar v-else /></span>
                <span class="eve-workspace-page__attention-copy"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span>
                <span class="eve-workspace-page__attention-action">{{ item.action }}<icon-right /></span>
              </button>
            </div>
            <a-empty v-else description="暂无近期燃料或月矿事项" />
          </section>

          <aside class="eve-workspace-page__cycle-card">
            <span>下一次月矿窗口</span>
            <template v-if="nextExtraction">
              <strong>{{ relativeDate(nextExtraction.chunkArrivalAt) }}</strong>
              <p>{{ resolvedName(nextExtraction.structureName, '月矿堡') }}</p>
              <small>{{ resolvedName(nextExtraction.moonName, '月球') }} · {{ resolvedName(nextExtraction.solarSystemName, '星系') }}</small>
              <a-button type="outline" long @click="router.push('/eve/extractions')">查看完整周期</a-button>
            </template>
            <template v-else>
              <strong>暂无计划</strong>
              <p>尚未取得可用的月矿时间线</p>
              <a-button type="outline" long @click="router.push('/eve/extractions')">查看月矿情报</a-button>
            </template>
          </aside>
        </section>

        <section class="eve-workspace-page__shortcuts">
          <div class="eve-workspace-page__section-heading"><div><span>军团工具</span><h2>从这里开始处理事务</h2></div></div>
          <div class="eve-workspace-page__shortcut-grid">
            <button v-for="entry in quickEntries" :key="entry.route" type="button" class="eve-workspace-page__shortcut" @click="router.push(entry.route)">
              <span class="eve-workspace-page__shortcut-icon">
                <icon-user-group v-if="entry.icon === 'user-group'" /><icon-storage v-else-if="entry.icon === 'storage'" /><icon-home v-else-if="entry.icon === 'home'" /><icon-calendar v-else-if="entry.icon === 'calendar'" /><icon-bar-chart v-else-if="entry.icon === 'bar-chart'" /><icon-message v-else />
              </span>
              <span><strong>{{ entry.title }}</strong><small>{{ entry.description }}</small></span>
              <icon-right />
            </button>
          </div>
        </section>
      </template>
    </a-spin>
  </main>
</template>

<style scoped lang="scss">
.eve-workspace-page { color: var(--color-text-1); }
.eve-workspace-page__loading { display: block; min-height: 360px; }
.eve-workspace-page__hero { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px 30px; border: 1px solid rgba(var(--arcoblue-6), .2); border-radius: 18px; background: linear-gradient(110deg, rgba(var(--arcoblue-6), .15), transparent 52%), var(--color-bg-1); }
.eve-workspace-page__identity-block { display: flex; align-items: center; min-width: 0; gap: 18px; }.eve-workspace-page__corporation-logo { display: grid; width: 76px; height: 76px; flex: 0 0 auto; place-items: center; overflow: hidden; border: 1px solid rgba(var(--arcoblue-6), .28); border-radius: 16px; background: rgba(var(--arcoblue-6), .08); color: rgb(var(--arcoblue-6)); font-size: 30px; }.eve-workspace-page__corporation-logo img { width: 100%; height: 100%; object-fit: cover; }.eve-workspace-page__identity { min-width: 0; }.eve-workspace-page__eyebrow, .eve-workspace-page__section-heading > div > span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: .14em; }.eve-workspace-page__identity h1 { margin: 7px 0 9px; font-size: clamp(26px, 3vw, 34px); }.eve-workspace-page__identity-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; color: var(--color-text-2); }.eve-workspace-page__character { display: inline-flex; align-items: center; gap: 6px; }.eve-workspace-page__character img { width: 22px; height: 22px; border-radius: 50%; object-fit: cover; }.eve-workspace-page__hero-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }

.eve-workspace-page__overview { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }.eve-workspace-page__metric { position: relative; min-height: 142px; padding: 20px; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 15px; background: var(--color-bg-1); color: inherit; cursor: pointer; text-align: left; transition: transform .18s ease, border-color .18s ease; }.eve-workspace-page__metric:hover, .eve-workspace-page__shortcut:hover { transform: translateY(-2px); border-color: rgba(var(--arcoblue-6), .46); }.eve-workspace-page__metric > span { display: block; color: var(--color-text-2); font-size: 13px; }.eve-workspace-page__metric strong { display: block; max-width: calc(100% - 50px); margin: 10px 0 7px; font-size: 25px; line-height: 1.2; }.eve-workspace-page__metric small { color: var(--color-text-3); }.eve-workspace-page__metric-icon { position: absolute; right: 19px; bottom: 18px; color: var(--color-text-4); font-size: 38px; }.eve-workspace-page__metric--fuel { border-color: rgba(var(--warning-6), .2); }.eve-workspace-page__metric--fuel .eve-workspace-page__metric-icon { color: rgba(var(--warning-6), .72); }.eve-workspace-page__metric--moon { border-color: rgba(var(--purple-6), .18); }.eve-workspace-page__metric--moon .eve-workspace-page__metric-icon { color: rgba(var(--purple-6), .7); }

.eve-workspace-page__content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; margin-top: 28px; }.eve-workspace-page__attention, .eve-workspace-page__cycle-card { padding: 22px; border: 1px solid var(--color-border-2); border-radius: 16px; background: var(--color-bg-1); }.eve-workspace-page__section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 16px; }.eve-workspace-page__section-heading h2 { margin: 5px 0 0; font-size: 20px; }.eve-workspace-page__section-heading p { margin: 0; color: var(--color-text-3); font-size: 13px; }.eve-workspace-page__attention-list { display: flex; flex-direction: column; }.eve-workspace-page__attention-item { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 13px 0; border: 0; border-bottom: 1px solid var(--color-border-2); background: transparent; color: inherit; cursor: pointer; text-align: left; }.eve-workspace-page__attention-item:last-child { border-bottom: 0; }.eve-workspace-page__attention-icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 11px; background: rgba(var(--arcoblue-6), .1); color: rgb(var(--arcoblue-6)); font-size: 18px; }.eve-workspace-page__attention-item--urgent .eve-workspace-page__attention-icon { background: rgba(var(--danger-6), .1); color: rgb(var(--danger-6)); }.eve-workspace-page__attention-copy { min-width: 0; }.eve-workspace-page__attention-copy strong, .eve-workspace-page__attention-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.eve-workspace-page__attention-copy small { margin-top: 4px; color: var(--color-text-3); }.eve-workspace-page__attention-action { display: inline-flex; align-items: center; gap: 4px; color: rgb(var(--arcoblue-6)); font-size: 12px; }.eve-workspace-page__cycle-card { display: flex; flex-direction: column; border-color: rgba(var(--purple-6), .26); background: linear-gradient(145deg, rgba(var(--purple-6), .11), transparent 65%), var(--color-bg-1); }.eve-workspace-page__cycle-card > span, .eve-workspace-page__cycle-card small { color: var(--color-text-3); font-size: 12px; }.eve-workspace-page__cycle-card strong { margin: 13px 0 7px; font-size: 28px; }.eve-workspace-page__cycle-card p { margin: 0 0 5px; font-weight: 600; }.eve-workspace-page__cycle-card small { min-height: 34px; line-height: 1.5; }.eve-workspace-page__cycle-card .arco-btn { margin-top: auto; }

.eve-workspace-page__shortcuts { margin-top: 28px; }.eve-workspace-page__shortcut-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }.eve-workspace-page__shortcut { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 16px; border: 1px solid var(--color-border-2); border-radius: 14px; background: var(--color-bg-1); color: inherit; cursor: pointer; text-align: left; transition: transform .18s ease, border-color .18s ease; }.eve-workspace-page__shortcut-icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 10px; background: var(--color-fill-2); color: rgb(var(--arcoblue-6)); font-size: 18px; }.eve-workspace-page__shortcut strong, .eve-workspace-page__shortcut small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.eve-workspace-page__shortcut small { margin-top: 4px; color: var(--color-text-3); font-size: 12px; }.eve-workspace-page__shortcut > .arco-icon { color: var(--color-text-4); }

@media (max-width: 980px) { .eve-workspace-page__hero { align-items: flex-start; flex-direction: column; }.eve-workspace-page__hero-actions { justify-content: flex-start; }.eve-workspace-page__content-grid { grid-template-columns: 1fr; }.eve-workspace-page__cycle-card { min-height: 210px; }.eve-workspace-page__shortcut-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 640px) { .eve-workspace-page__hero { padding: 22px 18px; }.eve-workspace-page__identity-block { align-items: flex-start; }.eve-workspace-page__corporation-logo { width: 58px; height: 58px; border-radius: 13px; }.eve-workspace-page__hero-actions { width: 100%; }.eve-workspace-page__hero-actions > * { flex: 1; }.eve-workspace-page__overview, .eve-workspace-page__shortcut-grid { grid-template-columns: 1fr; }.eve-workspace-page__attention, .eve-workspace-page__cycle-card { padding: 18px; }.eve-workspace-page__section-heading { align-items: flex-start; flex-direction: column; }.eve-workspace-page__attention-action { display: none; } }
@media (prefers-reduced-motion: reduce) { .eve-workspace-page__metric, .eve-workspace-page__shortcut { transition: none; } }
</style>
