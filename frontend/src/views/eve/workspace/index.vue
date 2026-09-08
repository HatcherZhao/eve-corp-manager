<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, ref } from 'vue'
import AuthorizationActions from '../components/AuthorizationActions.vue'
import { type EveCapabilityStatus, type EveContext, getEveContext } from '@/apis/eve'

defineOptions({ name: 'EveWorkspace' })

const router = useRouter()
const context = ref<EveContext>()
const loading = ref(false)
const failed = ref(false)

const statusMeta: Record<EveCapabilityStatus, { label: string, color: string, reason: string }> = {
  AVAILABLE: { label: '可用', color: 'green', reason: '本站权限、OAuth Scope 与游戏角色均满足' },
  MISSING_SITE_PERMISSION: { label: '缺少本站权限', color: 'orange', reason: '请联系军团管理员授予对应本站角色' },
  MISSING_SCOPE: { label: '缺少授权 Scope', color: 'orange', reason: '需要重新授权并补充模块 Scope' },
  MISSING_GAME_ROLE: { label: '缺少游戏权限', color: 'orangered', reason: '当前角色未拥有模块要求的军团权限' },
  AUTH_EXPIRED: { label: '授权已失效', color: 'red', reason: '请重新授权 EVE 国服角色' },
  NO_DATA_SOURCE: { label: '暂无权限数据', color: 'gray', reason: '尚未取得角色、授权或游戏权限快照' },
}

const identityLabels: Record<string, string> = {
  OWNER: '军团 CEO',
  ADMIN: '军团总监',
  MEMBER: '军团成员',
}

const availableCount = computed(() => context.value?.capabilities.filter((item) => item.status === 'AVAILABLE').length ?? 0)
const health = computed(() => context.value ? statusMeta[context.value.authorizationStatus] : statusMeta.NO_DATA_SOURCE)

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
}
const canManageAccess = computed(() => ['OWNER', 'ADMIN'].includes(context.value?.derivedIdentity ?? ''))

async function loadContext() {
  loading.value = true
  failed.value = false
  try {
    const { data } = await getEveContext()
    context.value = data
  } catch {
    context.value = undefined
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadContext)
</script>

<template>
  <main class="eve-workspace-page gi_page">
    <a-spin :loading="loading" class="eve-workspace-page__loading">
      <a-result v-if="failed" status="error" title="无法加载军团工作台" subtitle="请检查服务连接和当前账号的军团归属。">
        <template #extra><a-button type="primary" @click="loadContext">重新加载</a-button></template>
      </a-result>

      <template v-else-if="context">
        <header class="eve-workspace-page__header">
          <div class="eve-workspace-page__identity">
            <span class="eve-workspace-page__eyebrow">SERENITY CORPORATION SPACE</span>
            <h1>{{ context.corporation?.name || '未绑定军团' }}</h1>
            <div class="eve-workspace-page__identity-meta">
              <span v-if="context.corporation">[{{ context.corporation.ticker }}]</span>
              <span>{{ context.character?.name || '未绑定角色' }}</span>
              <a-tag color="arcoblue">{{ identityLabels[context.derivedIdentity] || context.derivedIdentity || '未识别角色' }}</a-tag>
            </div>
          </div>
          <div class="eve-workspace-page__header-actions">
            <a-button @click="router.push('/user/profile')">账号与绑定</a-button>
            <a-button v-if="canManageAccess" @click="router.push('/eve/access-management')">成员权限管理</a-button>
            <a-button type="primary" @click="router.push('/eve/permissions')">查看全部权限</a-button>
          </div>
        </header>

        <section class="eve-workspace-page__telemetry">
          <div class="eve-workspace-page__health-mark" :class="`eve-workspace-page__health-mark--${health.color}`">
            <icon-check-circle-fill v-if="context.authorizationStatus === 'AVAILABLE'" />
            <icon-exclamation-circle-fill v-else />
          </div>
          <div class="eve-workspace-page__health-copy">
            <span>授权健康</span>
            <strong>{{ health.label }}</strong>
            <p>{{ health.reason }}</p>
          </div>
          <div class="eve-workspace-page__health-stat">
            <strong>{{ availableCount }}/{{ context.capabilities.length }}</strong>
            <span>模块能力可用</span>
          </div>
          <div class="eve-workspace-page__freshness">
            <span>最近权限检查</span>
            <strong>{{ formatTime(context.permissionFreshness?.lastCheckedAt) }}</strong>
            <small>快照 {{ formatTime(context.permissionFreshness?.snapshotCapturedAt) }}</small>
            <small>
              {{ context.permissionFreshness?.cacheExpiryEstimated ? '本地建议刷新' : '上游数据有效至' }}
              {{ formatTime(context.permissionFreshness?.cacheExpiresAt) }}
            </small>
          </div>
          <AuthorizationActions class="eve-workspace-page__authorization-actions" @refreshed="loadContext" />
        </section>

        <section class="eve-workspace-page__capability-section">
          <div class="eve-workspace-page__section-heading">
            <div><span>能力矩阵</span><h2>当前角色可访问的军团模块</h2></div>
            <p>每项能力同时校验本站角色、国服 Scope 和真实游戏权限。</p>
          </div>
          <div class="eve-workspace-page__capability-grid">
            <article
              v-for="(item, index) in context.capabilities"
              :key="item.key"
              class="eve-workspace-page__capability-card"
              :class="{ 'eve-workspace-page__capability-card--available': item.status === 'AVAILABLE' }"
            >
              <div class="eve-workspace-page__capability-index">0{{ index + 1 }}</div>
              <div class="eve-workspace-page__capability-title">
                <h3>{{ item.title }}</h3>
                <a-tag :color="statusMeta[item.status].color">{{ statusMeta[item.status].label }}</a-tag>
              </div>
              <p>{{ item.description }}</p>
              <div class="eve-workspace-page__capability-reason">
                <icon-check-circle v-if="item.status === 'AVAILABLE'" />
                <icon-info-circle v-else />
                <span>{{ statusMeta[item.status].reason }}</span>
              </div>
              <dl v-if="item.status !== 'AVAILABLE'">
                <template v-if="item.requiredScope"><dt>需要 Scope</dt><dd>{{ item.requiredScope }}</dd></template>
                <template v-if="item.requiredGameRole"><dt>需要游戏权限</dt><dd>{{ item.requiredGameRole }}</dd></template>
              </dl>
            </article>
          </div>
        </section>
      </template>
    </a-spin>
  </main>
</template>

<style scoped lang="scss">
.eve-workspace-page { color: var(--color-text-1); }
.eve-workspace-page__loading { display: block; min-height: 360px; }
.eve-workspace-page__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 30px;
  border: 1px solid rgba(var(--arcoblue-6), 0.16);
  border-radius: 18px 18px 0 0;
  background:
    linear-gradient(120deg, rgba(var(--arcoblue-6), 0.13), transparent 55%),
    var(--color-bg-1);
}
.eve-workspace-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: 0.16em; }
.eve-workspace-page__identity h1 { margin: 8px 0 10px; font-size: clamp(26px, 3vw, 36px); }
.eve-workspace-page__identity-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; color: var(--color-text-2); }
.eve-workspace-page__header-actions { display: flex; flex-wrap: wrap; gap: 10px; }

.eve-workspace-page__telemetry {
  display: grid;
  grid-template-columns: auto minmax(180px, 0.7fr) auto minmax(210px, 0.9fr) minmax(360px, 1.5fr);
  align-items: center;
  gap: 18px;
  padding: 20px 28px;
  border: 1px solid var(--color-border-2);
  border-top: 0;
  border-radius: 0 0 18px 18px;
  background: var(--color-bg-2);
}
.eve-workspace-page__health-mark { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 13px; font-size: 22px; }
.eve-workspace-page__health-mark--green { color: rgb(var(--success-6)); background: rgba(var(--success-6), 0.12); }
.eve-workspace-page__health-mark--orange, .eve-workspace-page__health-mark--orangered { color: rgb(var(--warning-6)); background: rgba(var(--warning-6), 0.12); }
.eve-workspace-page__health-mark--red { color: rgb(var(--danger-6)); background: rgba(var(--danger-6), 0.12); }
.eve-workspace-page__health-mark--gray { color: var(--color-text-3); background: var(--color-fill-2); }
.eve-workspace-page__health-copy { display: flex; flex-direction: column; gap: 2px; }
.eve-workspace-page__health-copy span, .eve-workspace-page__health-stat span { color: var(--color-text-3); font-size: 12px; }
.eve-workspace-page__health-copy strong { font-size: 17px; }
.eve-workspace-page__health-copy p { margin: 1px 0 0; color: var(--color-text-2); font-size: 12px; }
.eve-workspace-page__health-stat { min-width: 112px; padding-left: 18px; border-left: 1px solid var(--color-border-2); }
.eve-workspace-page__health-stat strong { display: block; font-family: DINPro, sans-serif; font-size: 24px; }
.eve-workspace-page__freshness { display: flex; min-width: 210px; flex-direction: column; gap: 3px; }
.eve-workspace-page__freshness span, .eve-workspace-page__freshness small { color: var(--color-text-3); font-size: 12px; }
.eve-workspace-page__freshness strong { font-family: DINPro, sans-serif; font-size: 14px; }
.eve-workspace-page__authorization-actions :deep(.eve-authorization-actions__result) { grid-template-columns: 1fr 1fr; }

.eve-workspace-page__capability-section { margin-top: 28px; }
.eve-workspace-page__section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 16px; }
.eve-workspace-page__section-heading span { color: rgb(var(--arcoblue-6)); font-size: 12px; }
.eve-workspace-page__section-heading h2 { margin: 5px 0 0; font-size: 20px; }
.eve-workspace-page__section-heading p { max-width: 420px; margin: 0; color: var(--color-text-3); text-align: right; }
.eve-workspace-page__capability-grid { display: grid; grid-template-columns: repeat(5, minmax(180px, 1fr)); gap: 14px; }
.eve-workspace-page__capability-card {
  position: relative;
  min-height: 230px;
  padding: 20px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 15px;
  background: var(--color-bg-1);
  transition: transform 0.18s ease, border-color 0.18s ease;

  &:hover { transform: translateY(-2px); border-color: rgba(var(--arcoblue-6), 0.38); }
  > p { min-height: 44px; color: var(--color-text-2); line-height: 1.6; }
  dl { margin: 14px 0 0; padding-top: 12px; border-top: 1px dashed var(--color-border-2); font-size: 12px; }
  dt { color: var(--color-text-3); }
  dd { margin: 2px 0 8px; overflow-wrap: anywhere; color: var(--color-text-2); }
}
.eve-workspace-page__capability-card--available { border-color: rgba(var(--success-6), 0.24); }
.eve-workspace-page__capability-index { color: var(--color-text-4); font-family: DINPro, sans-serif; font-size: 12px; letter-spacing: 0.12em; }
.eve-workspace-page__capability-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.eve-workspace-page__capability-title h3 { margin: 12px 0; font-size: 17px; }
.eve-workspace-page__capability-reason { display: flex; align-items: flex-start; gap: 7px; color: var(--color-text-3); font-size: 12px; line-height: 1.45; }

@media (max-width: 1450px) { .eve-workspace-page__capability-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 1050px) {
  .eve-workspace-page__telemetry { grid-template-columns: auto 1fr auto; }
  .eve-workspace-page__freshness { grid-column: 1 / -1; }
  .eve-workspace-page__authorization-actions { grid-column: 1 / -1; }
}
@media (max-width: 760px) {
  .eve-workspace-page__header { align-items: flex-start; flex-direction: column; padding: 22px 20px; }
  .eve-workspace-page__header-actions { width: 100%; }
  .eve-workspace-page__header-actions > * { flex: 1; }
  .eve-workspace-page__telemetry { grid-template-columns: auto 1fr; padding: 18px; }
  .eve-workspace-page__health-stat { grid-column: 1 / -1; padding: 12px 0 0; border-top: 1px solid var(--color-border-2); border-left: 0; }
  .eve-workspace-page__capability-grid { grid-template-columns: 1fr; }
  .eve-workspace-page__section-heading { align-items: flex-start; flex-direction: column; }
  .eve-workspace-page__section-heading p { text-align: left; }
}
@media (prefers-reduced-motion: reduce) { .eve-workspace-page__capability-card { transition: none; } }
</style>
