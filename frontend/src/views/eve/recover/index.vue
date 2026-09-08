<script setup lang="ts">
import { type FormInstance, Message } from '@arco-design/web-vue'
import { reactive, ref } from 'vue'
import {
  type EvePasswordRecoveryCallbackResult,
  completeEvePasswordRecovery,
  startEvePasswordRecovery,
  verifyEvePasswordRecoveryCallback,
} from '@/apis/eve'
import { encryptByRsa } from '@/utils/encrypt'
import {
  closePreparedEveAuthorizationWindow,
  navigateEveAuthorizationWindow,
  openEveAccountLogoutWindow,
  prepareEveAuthorizationWindow,
} from '@/utils/eveAuthorizationWindow'

defineOptions({ name: 'EvePasswordRecovery' })

const router = useRouter()
const step = ref(1)
const loading = ref(false)
const callbackUrl = ref('')
const identity = ref<EvePasswordRecoveryCallbackResult>()
const formRef = ref<FormInstance>()
const form = reactive({ password: '', confirmPassword: '' })
const rules: FormInstance['rules'] = {
  password: [{ required: true, message: '请输入新密码' }, { minLength: 8, message: '密码至少 8 位' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    { validator: (value, cb) => value === form.password ? cb() : cb('两次输入的密码不一致') },
  ],
}

/** 发起独立的找回密码授权事务。 */
async function startRecovery() {
  const popup = prepareEveAuthorizationWindow()
  if (!popup) {
    Message.warning('浏览器阻止了授权窗口，请允许本站打开弹窗后重试')
    return
  }
  loading.value = true
  try {
    const { data } = await startEvePasswordRecovery()
    if (!navigateEveAuthorizationWindow(popup, data.authorizationUri)) {
      Message.warning('授权窗口已关闭，请重新发起授权')
      return
    }
    step.value = 2
  } catch (error) {
    closePreparedEveAuthorizationWindow(popup)
    throw error
  } finally {
    loading.value = false
  }
}

/** 可选退出网易登录态，便于改用其他网易账号验证。 */
function switchEveAccount() {
  if (!openEveAccountLogoutWindow()) Message.warning('浏览器阻止了账号切换窗口，请允许本站打开弹窗后重试')
}

/** 验证回调后清空完整地址，避免授权码残留。 */
async function verifyCallback() {
  if (!callbackUrl.value.trim()) {
    Message.warning('请粘贴国服授权完成后的完整回调地址')
    return
  }
  loading.value = true
  const submittedUrl = callbackUrl.value.trim()
  callbackUrl.value = ''
  try {
    const { data } = await verifyEvePasswordRecoveryCallback(submittedUrl)
    identity.value = data
    step.value = 3
  } finally {
    loading.value = false
  }
}

/** 设置新密码；完成后返回登录页，不复用注册登录会话。 */
async function completeRecovery() {
  if (!identity.value) return
  const invalid = await formRef.value?.validate()
  if (invalid) return
  loading.value = true
  try {
    await completeEvePasswordRecovery({
      credential: identity.value.credential,
      password: encryptByRsa(form.password) || '',
      confirmPassword: encryptByRsa(form.confirmPassword) || '',
    })
    identity.value = undefined
    form.password = ''
    form.confirmPassword = ''
    step.value = 4
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="eve-recovery-page">
    <section class="eve-recovery-page__shell">
      <a-button type="text" class="eve-recovery-page__back" @click="router.push('/login')">
        <template #icon><icon-left /></template>
        返回登录
      </a-button>
      <header class="eve-recovery-page__header">
        <span>EVE IDENTITY RECOVERY</span>
        <h1>使用 EVE 找回本站密码</h1>
        <p>验证已绑定的国服角色后设置新密码。此流程不会修改你的游戏账号。</p>
      </header>
      <a-steps :current="step" class="eve-recovery-page__steps">
        <a-step title="授权" /><a-step title="验证" /><a-step title="新密码" /><a-step title="完成" />
      </a-steps>

      <section class="eve-recovery-page__panel">
        <div v-if="step === 1" class="eve-recovery-page__center">
          <div class="eve-recovery-page__icon"><icon-safe /></div>
          <h2>验证已绑定的游戏角色</h2>
          <p>授权页会在小窗口打开。系统只接受与你本站账号绑定关系一致的角色。</p>
          <div class="eve-recovery-page__actions eve-recovery-page__actions--center"><a-button @click="switchEveAccount">切换网易账号（可选）</a-button><a-button type="primary" size="large" :loading="loading" @click="startRecovery">打开国服验证页</a-button></div>
        </div>

        <div v-else-if="step === 2" class="eve-recovery-page__center">
          <h2>粘贴验证结果</h2>
          <p>复制浏览器最终停留的完整地址；提交后输入框立即清空。</p>
          <a-textarea v-model="callbackUrl" :auto-size="{ minRows: 4, maxRows: 7 }" allow-clear placeholder="https://.../callback?code=...&state=..." />
          <div class="eve-recovery-page__actions"><a-button @click="step = 1">重新授权</a-button><a-button type="primary" :loading="loading" @click="verifyCallback">验证身份</a-button></div>
        </div>

        <div v-else-if="step === 3 && identity" class="eve-recovery-page__password-stage">
          <aside class="eve-recovery-page__identity">
            <span>已验证角色</span><strong>{{ identity.characterName }}</strong><p>身份验证通过，可以设置本站新密码。</p>
          </aside>
          <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
            <h2>设置本站新密码</h2>
            <a-form-item field="password" label="新密码"><a-input-password v-model="form.password" allow-clear /></a-form-item>
            <a-form-item field="confirmPassword" label="确认新密码"><a-input-password v-model="form.confirmPassword" allow-clear /></a-form-item>
            <div class="eve-recovery-page__actions"><a-button @click="step = 2">上一步</a-button><a-button type="primary" :loading="loading" @click="completeRecovery">更新密码</a-button></div>
          </a-form>
        </div>

        <a-result v-else-if="step === 4" status="success" title="本站密码已更新" subtitle="请使用本站用户名和新密码重新登录。">
          <template #extra><a-button type="primary" @click="router.replace('/login')">返回登录</a-button></template>
        </a-result>
      </section>
    </section>
  </main>
</template>

<style scoped lang="scss">
.eve-recovery-page { min-height: 100%; padding: 32px 20px; box-sizing: border-box; color: var(--color-text-1); background: radial-gradient(circle at 80% 12%, rgba(var(--arcoblue-6), 0.13), transparent 28%), var(--color-bg-5); }
.eve-recovery-page__shell { width: min(820px, 100%); margin: auto; }
.eve-recovery-page__back { margin-left: -12px; }
.eve-recovery-page__header { margin: 12px auto 26px; text-align: center; }
.eve-recovery-page__header span { color: rgb(var(--arcoblue-6)); font-family: DINPro, sans-serif; font-size: 11px; letter-spacing: 0.15em; }
.eve-recovery-page__header h1 { margin: 8px 0; font-size: clamp(27px, 4vw, 38px); }
.eve-recovery-page__header p, .eve-recovery-page__center > p { color: var(--color-text-2); line-height: 1.65; }
.eve-recovery-page__steps { margin-bottom: 22px; }
.eve-recovery-page__panel { min-height: 330px; padding: clamp(24px, 5vw, 46px); border: 1px solid var(--color-border-2); border-radius: 18px; background: var(--color-bg-1); box-shadow: 0 18px 55px rgba(25, 35, 50, 0.08); }
.eve-recovery-page__center { max-width: 580px; margin: auto; text-align: center; }
.eve-recovery-page__center .arco-textarea-wrapper { margin: 22px 0; text-align: left; }
.eve-recovery-page__icon { display: inline-grid; width: 52px; height: 52px; place-items: center; border-radius: 15px; color: rgb(var(--arcoblue-6)); background: rgba(var(--arcoblue-6), 0.1); font-size: 24px; }
.eve-recovery-page__actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 18px; }
.eve-recovery-page__actions--center { justify-content: center; }
.eve-recovery-page__password-stage { display: grid; grid-template-columns: 0.8fr 1.2fr; gap: 30px; }
.eve-recovery-page__identity { display: flex; align-items: flex-start; flex-direction: column; padding: 24px; border-radius: 14px; color: #fff; background: linear-gradient(145deg, #17233d, #244f73); }
.eve-recovery-page__identity span { font-size: 11px; letter-spacing: 0.14em; opacity: 0.6; }
.eve-recovery-page__identity strong { margin-top: 18px; font-size: 23px; }
.eve-recovery-page__identity p { opacity: 0.72; }
.eve-recovery-page__identity code { margin-top: auto; color: rgba(255, 255, 255, 0.72); }
@media (max-width: 650px) { .eve-recovery-page { padding: 18px 12px; } .eve-recovery-page__panel { padding: 22px 16px; } .eve-recovery-page__password-stage { grid-template-columns: 1fr; } .eve-recovery-page__identity code { margin-top: 20px; } }
</style>
