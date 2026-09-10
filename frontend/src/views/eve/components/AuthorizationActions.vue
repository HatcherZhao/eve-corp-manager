<script setup lang="ts">
import dayjs from 'dayjs'
import { Message } from '@arco-design/web-vue'
import { ref } from 'vue'
import {
  type EvePermissionRefreshResult,
  completeEveReauthorization,
  refreshEvePermissions,
  startEveReauthorization,
} from '@/apis/eve'
import {
  closePreparedEveAuthorizationWindow,
  navigateEveReauthorizationWindow,
  prepareEveAuthorizationWindow,
} from '@/utils/eveAuthorizationWindow'

defineOptions({ name: 'EveAuthorizationActions' })

const emit = defineEmits<{ refreshed: [result: EvePermissionRefreshResult] }>()
const refreshing = ref(false)
const starting = ref(false)
const completing = ref(false)
const modalVisible = ref(false)
const callbackUrl = ref('')
const latestResult = ref<EvePermissionRefreshResult>()

const statusCopy: Record<EvePermissionRefreshResult['status'], string> = {
  REFRESHED: '已从国服更新权限',
  COOLDOWN: '仍在刷新冷却期',
  REAUTHORIZATION_REQUIRED: '授权已永久失效或 Scope 已变化，需要重新授权',
  AUTHORIZATION_DISABLED: '当前授权不可用',
  UPSTREAM_UNAVAILABLE: '国服暂时不可用，请稍后重试',
  MEMBERSHIP_INVALID: '角色已离开或更换军团',
}

const scopeLabels: Record<string, string> = {
  ceo: 'CEO 身份',
  roles: '全局角色',
  roles_at_hq: '总部角色',
  roles_at_base: '基地角色',
  roles_at_other: '其他地点角色',
}

const identityLabels: Record<string, string> = {
  OWNER: '军团 CEO',
  ADMIN: '军团总监',
  MEMBER: '军团成员',
}

const capabilityLabels: Record<string, string> = {
  assets: '军团资产',
  structures: '军团建筑',
  extractions: '月矿情报',
  mining: '月矿账本',
  members: '成员追踪',
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
}

function formatIdentity(value?: string) {
  return value ? identityLabels[value] || value : '无派生身份'
}

function acceptResult(result: EvePermissionRefreshResult) {
  latestResult.value = result
  emit('refreshed', result)
  Message[result.status === 'REFRESHED' ? 'success' : 'info'](statusCopy[result.status])
}

/** 主动刷新只更新权限事实，不持久化任何 EVE 授权参数。 */
async function handleRefresh() {
  refreshing.value = true
  try {
    const { data } = await refreshEvePermissions()
    acceptResult(data)
  } finally {
    refreshing.value = false
  }
}

/** 在小窗口退出旧网易会话后发起重新授权，避免复用异常的旧登录态。 */
async function handleReauthorize() {
  const popup = prepareEveAuthorizationWindow()
  if (!popup) {
    Message.warning('浏览器阻止了授权窗口，请允许本站打开弹窗后重试')
    return
  }
  starting.value = true
  try {
    const { data } = await startEveReauthorization()
    if (!navigateEveReauthorizationWindow(popup, data.authorizationUri)) {
      Message.warning('授权窗口已关闭，请重新发起授权')
      return
    }
    callbackUrl.value = ''
    modalVisible.value = true
  } catch (error) {
    closePreparedEveAuthorizationWindow(popup)
    throw error
  } finally {
    starting.value = false
  }
}

/** 导入回调后立即清空输入值，避免 code 与 state 在页面内继续停留。 */
async function handleCompleteReauthorization(): Promise<boolean> {
  if (!callbackUrl.value.trim()) {
    Message.warning('请粘贴重新授权后的完整回调地址')
    return false
  }
  completing.value = true
  const submittedUrl = callbackUrl.value.trim()
  callbackUrl.value = ''
  try {
    const { data } = await completeEveReauthorization(submittedUrl)
    modalVisible.value = false
    acceptResult(data)
    return true
  } catch {
    return false
  } finally {
    completing.value = false
  }
}
</script>

<template>
  <div class="eve-authorization-actions">
    <div class="eve-authorization-actions__buttons">
      <a-button :loading="refreshing" @click="handleRefresh">
        <template #icon><icon-refresh /></template>
        刷新权限
      </a-button>
      <a-button type="primary" :loading="starting" @click="handleReauthorize">
        <template #icon><icon-link /></template>
        重新授权
      </a-button>
    </div>

    <div v-if="latestResult" class="eve-authorization-actions__result">
      <div>
        <span>本次结果</span>
        <strong>{{ statusCopy[latestResult.status] }}</strong>
      </div>
      <div>
        <span>上游检查时间</span>
        <strong>{{ formatTime(latestResult.upstreamCheckedAt) }}</strong>
      </div>
      <div>
        <span>{{ latestResult.sourceExpiryEstimated ? '本地建议刷新时间' : '上游接口数据有效至' }}</span>
        <strong>{{ formatTime(latestResult.sourceExpiresAt) }}</strong>
      </div>
      <div>
        <span>最近权限变化</span>
        <strong>{{ formatTime(latestResult.lastRoleChangedAt) }}</strong>
      </div>
      <div>
        <span>下次建议刷新</span>
        <strong>{{ formatTime(latestResult.nextSuggestedRefreshAt) }}</strong>
      </div>
      <a-alert v-if="latestResult.missingScopes?.length" type="warning">
        缺少 Scope：{{ latestResult.missingScopes.join('、') }}
      </a-alert>
      <div
        v-if="latestResult.addedGameRoles?.length || latestResult.removedGameRoles?.length || latestResult.derivedIdentityChange || latestResult.capabilityChanges?.length"
        class="eve-authorization-actions__changes"
      >
        <strong>本次权限变化</strong>
        <ul v-if="latestResult.addedGameRoles?.length">
          <li v-for="item in latestResult.addedGameRoles" :key="`added-${item.sourceScope}-${item.role}`">
            新增 {{ scopeLabels[item.sourceScope] || item.sourceScope }}：{{ item.role }}
          </li>
        </ul>
        <ul v-if="latestResult.removedGameRoles?.length">
          <li v-for="item in latestResult.removedGameRoles" :key="`removed-${item.sourceScope}-${item.role}`">
            移除 {{ scopeLabels[item.sourceScope] || item.sourceScope }}：{{ item.role }}
          </li>
        </ul>
        <p v-if="latestResult.derivedIdentityChange">
          站内派生身份：{{ formatIdentity(latestResult.derivedIdentityChange.before) }} →
          {{ formatIdentity(latestResult.derivedIdentityChange.after) }}
        </p>
        <ul v-if="latestResult.capabilityChanges?.length">
          <li v-for="item in latestResult.capabilityChanges" :key="`capability-${item.key}`">
            {{ capabilityLabels[item.key] || item.key }}：{{ item.before || '无' }} → {{ item.after || '无' }}
          </li>
        </ul>
      </div>
    </div>

    <aside v-if="modalVisible" class="eve-reauthorization-side-guide" aria-label="网易授权操作指引">
      <span>授权进行中</span>
      <strong>完成后带回回调地址</strong>
      <ol>
        <li><b>1</b><div>在网易小窗口登录游戏账号</div></li>
        <li><b>2</b><div>选择角色，点击“授权”</div></li>
        <li><b>3</b><div>复制地址栏中的完整 URL</div></li>
        <li><b>4</b><div>关闭小窗口，回到本站粘贴并验证</div></li>
      </ol>
      <p>请复制地址栏链接，不要复制网易页面文字。</p>
    </aside>

    <a-modal
      v-model:visible="modalVisible"
      title="完成 EVE 重新授权"
      :width="820"
      :ok-loading="completing"
      ok-text="验证并刷新"
      @before-ok="handleCompleteReauthorization"
      @cancel="callbackUrl = ''"
    >
      <div class="eve-reauthorization-modal">
        <aside class="eve-reauthorization-modal__guide" aria-label="重新授权操作指引">
          <div class="eve-reauthorization-modal__guide-heading">
            <span>操作指引</span>
            <strong>按顺序完成授权</strong>
          </div>
          <ol class="eve-reauthorization-modal__step-list">
            <li>
              <span>1</span>
              <div><strong>登录网易 EVE</strong><p>在刚打开的小窗口中登录需要授权的游戏账号。</p></div>
            </li>
            <li>
              <span>2</span>
              <div><strong>选择角色并授权</strong><p>确认角色无误后，点击网易页面中的“授权”。</p></div>
            </li>
            <li>
              <span>3</span>
              <div><strong>复制完整地址</strong><p>授权结束后，复制小窗口浏览器地址栏中的完整 URL。</p></div>
            </li>
            <li>
              <span>4</span>
              <div><strong>关闭网易小窗口</strong><p>地址已复制即可关闭；无需在网易页面继续操作。</p></div>
            </li>
            <li>
              <span>5</span>
              <div><strong>粘贴并验证</strong><p>回到本窗口粘贴地址，再点击右下角“验证并刷新”。</p></div>
            </li>
          </ol>
        </aside>

        <section class="eve-reauthorization-modal__callback-panel" aria-label="授权回调地址">
          <div class="eve-reauthorization-modal__callback-heading">
            <span>最后一步</span>
            <strong>粘贴授权回调地址</strong>
          </div>
          <p>请粘贴地址栏中的完整链接，而不是网易页面中的文字或提示内容。验证后，链接会立即从页面中清空。</p>
          <a-textarea
            v-model="callbackUrl"
            :auto-size="{ minRows: 7, maxRows: 10 }"
            placeholder="https://.../oauth2-redirect.html?code=...&state=..."
            allow-clear
          />
          <a-alert type="info" :show-icon="false">
            如需切换网易账号，请关闭当前小窗口后再次点击“重新授权”；系统会先退出旧的网易 EVE 登录会话。
          </a-alert>
        </section>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.eve-authorization-actions__buttons { display: flex; flex-wrap: wrap; gap: 10px; }
.eve-authorization-actions__result {
  display: grid;
  grid-template-columns: repeat(5, minmax(130px, 1fr));
  gap: 12px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  background: var(--color-fill-1);

  > div { display: flex; flex-direction: column; gap: 4px; }
  span { color: var(--color-text-3); font-size: 12px; }
  strong { color: var(--color-text-1); font-size: 13px; }
  .arco-alert { grid-column: 1 / -1; }
}
.eve-authorization-actions__changes {
  grid-column: 1 / -1;
  padding-top: 10px;
  border-top: 1px dashed var(--color-border-2);

  > strong { display: block; margin-bottom: 6px; }
  ul { margin: 4px 0; padding-left: 20px; color: var(--color-text-2); line-height: 1.7; }
  p { margin: 6px 0 0; color: var(--color-text-2); }
}
.eve-reauthorization-side-guide {
  position: fixed;
  z-index: 1002;
  top: 50%;
  left: 18px;
  box-sizing: border-box;
  width: min(310px, calc((100vw - 960px) / 2 - 28px));
  padding: 14px;
  transform: translateY(-50%);
  border: 1px solid rgba(var(--primary-6), .35);
  border-radius: 12px;
  background: var(--color-bg-2);
  box-shadow: 0 12px 30px rgba(0, 0, 0, .18);
  pointer-events: none;

  > span { display: block; color: rgb(var(--primary-6)); font-size: 12px; font-weight: 600; letter-spacing: .08em; }
  > strong { display: block; margin-top: 5px; color: var(--color-text-1); font-size: 15px; line-height: 1.45; }
  ol { display: grid; gap: 11px; margin: 16px 0; padding: 0; list-style: none; }
  li { display: grid; grid-template-columns: 20px minmax(0, 1fr); gap: 8px; color: var(--color-text-2); font-size: 12px; line-height: 1.55; }
  b {
    display: grid;
    width: 20px;
    height: 20px;
    place-items: center;
    border-radius: 50%;
    background: rgb(var(--primary-6));
    color: var(--color-white);
    font-size: 11px;
  }
  p { margin: 0; padding-top: 12px; border-top: 1px solid var(--color-border-2); color: var(--color-text-3); font-size: 12px; line-height: 1.55; }
}
.eve-reauthorization-modal {
  display: grid;
  grid-template-columns: minmax(270px, .92fr) minmax(0, 1.28fr);
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  background: var(--color-bg-2);
}
.eve-reauthorization-modal__guide {
  padding: 20px;
  border-right: 1px solid var(--color-border-2);
  background: linear-gradient(155deg, rgba(var(--primary-6), .1), transparent 58%), var(--color-fill-1);
}
.eve-reauthorization-modal__guide-heading,
.eve-reauthorization-modal__callback-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;

  span { color: rgb(var(--primary-6)); font-size: 12px; font-weight: 600; letter-spacing: .08em; }
  strong { color: var(--color-text-1); font-size: 16px; }
}
.eve-reauthorization-modal__step-list {
  display: grid;
  gap: 14px;
  margin: 20px 0 0;
  padding: 0;
  list-style: none;

  li { display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 10px; }
  li > span {
    display: grid;
    width: 24px;
    height: 24px;
    place-items: center;
    border-radius: 50%;
    background: rgb(var(--primary-6));
    color: var(--color-white);
    font-size: 12px;
    font-weight: 700;
  }
  strong { display: block; color: var(--color-text-1); font-size: 13px; }
  p { margin: 3px 0 0; color: var(--color-text-3); font-size: 12px; line-height: 1.55; }
}
.eve-reauthorization-modal__callback-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;

  > p { margin: 0; color: var(--color-text-2); line-height: 1.65; }
  .arco-alert { margin-top: auto; line-height: 1.55; }
}

@media (max-width: 720px) {
  .eve-authorization-actions__result { grid-template-columns: 1fr 1fr; }
  .eve-reauthorization-modal { grid-template-columns: 1fr; }
  .eve-reauthorization-modal__guide { border-right: 0; border-bottom: 1px solid var(--color-border-2); }
  .eve-reauthorization-modal__step-list { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 1359px) {
  .eve-reauthorization-side-guide { display: none; }
}
@media (max-width: 440px) {
  .eve-authorization-actions__buttons > * { flex: 1; }
  .eve-authorization-actions__result { grid-template-columns: 1fr; }
  .eve-reauthorization-modal__step-list { grid-template-columns: 1fr; }
  .eve-reauthorization-modal__guide, .eve-reauthorization-modal__callback-panel { padding: 16px; }
}
</style>
