<script setup lang="ts">
import { type FormInstance, Message } from '@arco-design/web-vue'
import { computed, reactive, ref } from 'vue'
import {
  type EveRegistrationCallbackResult,
  completeEveRegistration,
  startEveRegistration,
  verifyEveRegistrationCallback,
} from '@/apis/eve'
import { useTenantStore } from '@/stores/modules/tenant'
import { encryptByRsa } from '@/utils/encrypt'
import { setToken } from '@/utils/auth'
import {
  closePreparedEveAuthorizationWindow,
  navigateEveAuthorizationWindow,
  openEveAccountLogoutWindow,
  prepareEveAuthorizationWindow,
} from '@/utils/eveAuthorizationWindow'

defineOptions({ name: 'EveRegistration' })

const router = useRouter()
const tenantStore = useTenantStore()
const currentStep = ref(1)
const starting = ref(false)
const loginStateCleared = ref(false)
const verifying = ref(false)
const completing = ref(false)
const callbackUrl = ref('')
const identity = ref<EveRegistrationCallbackResult>()
const completeResult = ref<{ tenantCreated: boolean }>()
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})
let authorizationPopup: Window | null = null

const identityLabel = computed(() => {
  if (!identity.value) return ''
  if (identity.value.ceo) return '军团 CEO'
  if (identity.value.director) return '军团总监'
  return '军团成员'
})

const passwordRules: FormInstance['rules'] = {
  username: [
    { required: true, message: '请输入邮箱账号' },
    {
      validator: (value, cb) => {
        const username = String(value ?? '').trim()
        return username.length <= 64
          && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(username)
          ? cb()
          : cb('请输入有效的邮箱账号，最长 64 个字符')
      },
    },
  ],
  password: [
    { required: true, message: '请输入本站登录密码' },
    { minLength: 8, message: '密码至少 8 位' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码' },
    { validator: (value, cb) => value === form.password ? cb() : cb('两次输入的密码不一致') },
  ],
}

/** 发起国服授权，只在当前浏览器内打开地址，不持久化授权参数。 */
async function handleStart() {
  if (!loginStateCleared.value) {
    Message.warning('请先清除旧的网易登录状态')
    return
  }
  const popup = authorizationPopup && !authorizationPopup.closed
    ? authorizationPopup
    : prepareEveAuthorizationWindow()
  if (!popup) {
    Message.warning('浏览器阻止了授权窗口，请允许本站打开弹窗后重试')
    return
  }
  starting.value = true
  try {
    const { data } = await startEveRegistration()
    if (!navigateEveAuthorizationWindow(popup, data.authorizationUri)) {
      Message.warning('授权窗口已关闭，请重新发起授权')
      return
    }
    currentStep.value = 2
  } catch (error) {
    closePreparedEveAuthorizationWindow(popup)
    throw error
  } finally {
    starting.value = false
  }
}

/** 注册前打开网易退出页，并复用该窗口完成后续授权。 */
function handleClearEveLoginState() {
  authorizationPopup = openEveAccountLogoutWindow()
  if (!authorizationPopup) {
    Message.warning('浏览器阻止了清理窗口，请允许本站打开弹窗后重试')
    return
  }
  loginStateCleared.value = true
  Message.info('旧登录状态清理页已打开；无需关闭窗口，可直接点击下一步授权')
}

/** 导入回调地址后立即清空输入框，避免授权码残留在页面状态。 */
async function handleVerify() {
  if (!callbackUrl.value.trim()) {
    Message.warning('请粘贴国服授权完成后的完整回调地址')
    return
  }
  verifying.value = true
  const submittedUrl = callbackUrl.value.trim()
  callbackUrl.value = ''
  try {
    const { data } = await verifyEveRegistrationCallback(submittedUrl)
    identity.value = data
    form.username = ''
    currentStep.value = 3
  } finally {
    verifying.value = false
  }
}

/** 激活本站账号并保存本站会话令牌；EVE 凭证仍只保留在组件内存中。 */
async function handleComplete() {
  if (!identity.value) return
  const invalid = await formRef.value?.validate()
  if (invalid) return
  completing.value = true
  try {
    const { data } = await completeEveRegistration({
      credential: identity.value.credential,
      username: form.username.trim().toLowerCase(),
      password: encryptByRsa(form.password) || '',
      confirmPassword: encryptByRsa(form.confirmPassword) || '',
      clientId: import.meta.env.VITE_CLIENT_ID,
    })
    setToken(data.token)
    tenantStore.setTenantId(String(data.tenantId))
    completeResult.value = { tenantCreated: data.tenantCreated }
    identity.value = undefined
    form.password = ''
    form.confirmPassword = ''
    currentStep.value = 4
  } finally {
    completing.value = false
  }
}
</script>

<template>
  <main class="eve-registration-page">
    <section class="eve-registration-page__shell">
      <header class="eve-registration-page__header">
        <a-button type="text" class="eve-registration-page__back" @click="router.push('/login')">
          <template #icon><icon-left /></template>
          返回登录
        </a-button>
        <div class="eve-registration-page__eyebrow">EVE Online · 网易国服</div>
        <h1>通过游戏身份创建本站账号</h1>
        <p>国服授权只用于验证角色、军团与游戏权限。本站登录仍使用你设置的用户名和密码。</p>
      </header>

      <a-steps :current="currentStep" label-placement="vertical" class="eve-registration-page__steps">
        <a-step title="国服授权" />
        <a-step title="验证角色" />
        <a-step title="设置账号" />
        <a-step title="完成" />
      </a-steps>

      <section class="eve-registration-page__content">
        <div v-if="currentStep === 1" class="eve-registration-page__step-panel">
          <div class="eve-registration-page__step-icon"><icon-safe /></div>
          <h2>先验证你的游戏角色</h2>
          <p>新用户需要先清除旧登录状态，再在同一个小窗口中重新登录网易并选择游戏角色。</p>
          <a-alert type="info">系统不会要求你输入游戏密码，也不会把授权码、回调地址或 EVE Token 保存到 localStorage。</a-alert>
          <div class="eve-registration-page__actions eve-registration-page__actions--center">
            <a-button @click="handleClearEveLoginState">1. 清除旧登录状态</a-button>
            <a-button type="primary" size="large" :disabled="!loginStateCleared" :loading="starting" @click="handleStart">
              2. 登录并授权角色
            </a-button>
          </div>
        </div>

        <div v-else-if="currentStep === 2" class="eve-registration-page__step-panel">
          <div class="eve-registration-page__step-icon"><icon-link /></div>
          <h2>导入授权结果</h2>
          <p>粘贴包含 <code>code</code> 和 <code>state</code> 的完整回调地址，验证成功后输入框会立即清空。</p>
          <a-textarea
            v-model="callbackUrl"
            :auto-size="{ minRows: 4, maxRows: 7 }"
            placeholder="https://.../callback?code=...&state=..."
            allow-clear
          />
          <div class="eve-registration-page__actions">
            <a-button @click="currentStep = 1">重新授权</a-button>
            <a-button type="primary" :loading="verifying" @click="handleVerify">验证角色</a-button>
          </div>
        </div>

        <div v-else-if="currentStep === 3 && identity" class="eve-registration-page__account-stage">
          <div class="eve-registration-page__identity-card">
            <span class="eve-registration-page__identity-kicker">已验证角色</span>
            <strong>{{ identity.characterName }}</strong>
            <span>{{ identity.corporationName }}</span>
            <a-tag color="arcoblue">{{ identityLabel }}</a-tag>
            <p v-if="identity.ceo || identity.director">如果军团尚未入驻，你将创建军团租户；否则加入现有军团。</p>
            <p v-else>普通成员只能加入已经由 CEO 或总监创建的军团租户。</p>
          </div>
          <a-form ref="formRef" :model="form" :rules="passwordRules" layout="vertical" class="eve-registration-page__account-form">
            <h2>设置本站登录方式</h2>
            <p>以后可直接使用本站用户名和密码登录，无需每次进行游戏授权。</p>
            <a-form-item field="username" label="登录账号">
              <a-input v-model="form.username" placeholder="请输入邮箱账号" allow-clear />
            </a-form-item>
            <a-form-item field="password" label="登录密码">
              <a-input-password v-model="form.password" placeholder="至少 8 位" allow-clear />
            </a-form-item>
            <a-form-item field="confirmPassword" label="确认密码">
              <a-input-password v-model="form.confirmPassword" placeholder="再次输入密码" allow-clear />
            </a-form-item>
            <div class="eve-registration-page__actions">
              <a-button @click="currentStep = 2">上一步</a-button>
              <a-button type="primary" :loading="completing" @click="handleComplete">创建并登录</a-button>
            </div>
          </a-form>
        </div>

        <a-result
          v-else-if="currentStep === 4"
          status="success"
          :title="completeResult?.tenantCreated ? '军团空间已创建' : '已加入军团空间'"
          subtitle="本站账号已激活，之后可直接使用用户名和密码登录。"
        >
          <template #extra>
            <a-button type="primary" @click="router.replace('/eve/workspace')">进入军团工作台</a-button>
          </template>
        </a-result>
      </section>
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-registration-page {
  min-height: 100%;
  padding: 32px 20px;
  box-sizing: border-box;
  color: var(--color-text-1);
  background:
    radial-gradient(circle at 15% 8%, rgba(var(--arcoblue-6), 0.14), transparent 28%),
    radial-gradient(circle at 86% 92%, rgba(var(--success-6), 0.08), transparent 25%),
    var(--color-bg-5);
}

.eve-registration-page__shell {
  width: min(920px, 100%);
  margin: 0 auto;
}

.eve-registration-page__header {
  position: relative;
  max-width: 680px;
  margin: 0 auto 28px;
  text-align: center;

  h1 { margin: 8px 0 10px; font-size: clamp(28px, 4vw, 40px); line-height: 1.18; }
  p { margin: 0; color: var(--color-text-2); line-height: 1.7; }
}

.eve-registration-page__back { position: absolute; top: -4px; left: -96px; }
.eve-registration-page__eyebrow { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; letter-spacing: 0.12em; }
.eve-registration-page__steps { margin-bottom: 24px; }

.eve-registration-page__content {
  min-height: 390px;
  padding: clamp(24px, 5vw, 48px);
  border: 1px solid var(--color-border-2);
  border-radius: 20px;
  background: color-mix(in srgb, var(--color-bg-1) 94%, transparent);
  box-shadow: 0 20px 60px rgba(25, 35, 50, 0.09);
}

.eve-registration-page__step-panel {
  max-width: 620px;
  margin: 0 auto;
  text-align: center;

  h2 { margin: 16px 0 8px; }
  > p { margin: 0 0 24px; color: var(--color-text-2); line-height: 1.7; }
  .arco-alert { margin-bottom: 24px; text-align: left; }
  .arco-textarea-wrapper { margin-bottom: 22px; text-align: left; }
}

.eve-registration-page__step-icon {
  display: inline-grid;
  width: 56px;
  height: 56px;
  place-items: center;
  border-radius: 16px;
  color: rgb(var(--arcoblue-6));
  background: rgba(var(--arcoblue-6), 0.1);
  font-size: 26px;
}

.eve-registration-page__account-stage { display: grid; grid-template-columns: 0.82fr 1.18fr; gap: 32px; }
.eve-registration-page__identity-card {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  padding: 28px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(145deg, #17233d, #244f73);

  strong { margin: 14px 0 4px; font-size: 24px; }
  > span:not(.eve-registration-page__identity-kicker) { opacity: 0.76; }
  .arco-tag { margin-top: 18px; }
  p { margin: auto 0 0; padding-top: 28px; color: rgba(255, 255, 255, 0.72); line-height: 1.6; }
}
.eve-registration-page__identity-kicker { font-size: 12px; letter-spacing: 0.14em; opacity: 0.6; }
.eve-registration-page__account-form { h2 { margin: 0 0 6px; } > p { margin: 0 0 22px; color: var(--color-text-2); } }
.eve-registration-page__actions { display: flex; justify-content: flex-end; gap: 12px; }
.eve-registration-page__actions--center { justify-content: center; }

@media (max-width: 760px) {
  .eve-registration-page { padding: 20px 12px; }
  .eve-registration-page__back { position: static; display: flex; margin-bottom: 18px; }
  .eve-registration-page__content { padding: 24px 18px; }
  .eve-registration-page__account-stage { grid-template-columns: 1fr; }
  .eve-registration-page__identity-card p { margin-top: 20px; }
  .eve-registration-page__steps :deep(.arco-steps-item-title) { font-size: 12px; }
}

@media (prefers-reduced-motion: reduce) {
  .eve-registration-page *, .eve-registration-page *::before, .eve-registration-page *::after { scroll-behavior: auto !important; }
}
</style>
