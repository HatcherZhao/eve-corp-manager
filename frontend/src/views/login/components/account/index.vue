<template>
  <a-form
    ref="formRef"
    :model="form"
    :rules="rules"
    :label-col-style="{ display: 'none' }"
    :wrapper-col-style="{ flex: 1 }"
    size="large"
    @submit="handleLogin"
  >
    <a-form-item v-if="tenantStore.needInputTenantCode" field="tenantName" hide-label>
      <a-input v-model="tenantName" placeholder="EVE 账号请输入所属军团名称" allow-clear />
    </a-form-item>
    <a-form-item field="username" hide-label>
      <a-input v-model="form.username" placeholder="请输入用户名" allow-clear />
    </a-form-item>
    <a-form-item field="password" hide-label>
      <a-input-password v-model="form.password" placeholder="请输入密码" />
    </a-form-item>
    <a-form-item v-if="isCaptchaEnabled" field="captcha" hide-label>
      <a-input v-model="form.captcha" placeholder="请输入验证码" :max-length="4" allow-clear style="flex: 1 1" />
      <div class="captcha-container" @click="getCaptcha">
        <img :src="captchaImgBase64" alt="验证码" class="captcha" />
        <div v-if="form.expired" class="overlay">
          <p>已过期，请刷新</p>
        </div>
      </div>
    </a-form-item>
    <a-form-item>
      <a-row justify="space-between" align="center" class="w-full">
        <a-checkbox v-model="loginConfig.rememberMe">记住我</a-checkbox>
        <a-link @click="router.push('/eve/recover')">忘记密码</a-link>
      </a-row>
    </a-form-item>
    <a-form-item>
      <a-space direction="vertical" fill class="w-full">
        <a-button class="btn" type="primary" :loading="loading" html-type="submit" size="large" long>立即登录</a-button>
      </a-space>
    </a-form-item>
    <div class="eve-login-entry">
      <a-divider orientation="center">游戏身份服务</a-divider>
      <a-button long size="large" class="eve-login-entry__register" @click="router.push('/eve/register')">
        <template #icon><icon-user-add /></template>
        通过 EVE 注册
      </a-button>
      <a-link class="eve-login-entry__recover" @click="router.push('/eve/recover')">
        使用 EVE 找回密码
      </a-link>
    </div>
  </a-form>
</template>

<script setup lang="ts">
import { type FormInstance, Message } from '@arco-design/web-vue'
import { useStorage } from '@vueuse/core'
import { getImageCaptcha } from '@/apis/common'
import { useTabsStore, useTenantStore, useUserStore } from '@/stores'
import { encryptByRsa } from '@/utils/encrypt'

/**
 * 登录账号仅在用户主动勾选“记住我”后保存。
 * 使用新键隔离旧版遗留的 admin 演示默认值，避免升级后继续自动填充。
 */
const loginConfig = useStorage('eve-login-config-v2', {
  rememberMe: false,
  username: '',
})
/** 上次成功登录时填写的军团名称，与“记住我”开关无关。 */
const lastSuccessfulTenantName = useStorage('eve-last-successful-tenant-name', '')
// 是否启用验证码
const isCaptchaEnabled = ref(true)
// 验证码图片
const captchaImgBase64 = ref()
const tenantName = ref(lastSuccessfulTenantName.value)
const formRef = ref<FormInstance>()
const form = reactive({
  username: loginConfig.value.username,
  password: '',
  captcha: '',
  uuid: '',
  expired: false,
})
// 校验规则部分
const rules: FormInstance['rules'] = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }],
  captcha: [{ required: isCaptchaEnabled.value, message: '请输入验证码' }],
}

// 验证码过期定时器
let timer
const startTimer = (expireTime: number, curTime = Date.now()) => {
  if (timer) {
    clearTimeout(timer)
  }
  const remainingTime = expireTime - curTime
  if (remainingTime <= 0) {
    form.expired = true
    return
  }
  timer = setTimeout(() => {
    form.expired = true
  }, remainingTime)
}
// 组件销毁时清理定时器
onBeforeUnmount(() => {
  if (timer) {
    clearTimeout(timer)
  }
})

// 获取验证码
const getCaptcha = () => {
  getImageCaptcha().then((res) => {
    const { uuid, img, expireTime, isEnabled } = res.data
    isCaptchaEnabled.value = isEnabled
    captchaImgBase64.value = img
    form.uuid = uuid
    form.expired = false
    startTimer(expireTime, Number(res.timestamp))
  })
}

const tenantStore = useTenantStore()
const userStore = useUserStore()
const tabsStore = useTabsStore()
const router = useRouter()
const loading = ref(false)

// 登录
const handleLogin = async () => {
  try {
    const isInvalid = await formRef.value?.validate()
    if (isInvalid) return
    loading.value = true

    const submittedTenantName = tenantName.value.trim()
    await userStore.accountLogin({
      username: form.username,
      password: encryptByRsa(form.password) || '',
      captcha: form.captcha,
      uuid: form.uuid,
    }, submittedTenantName)
    tabsStore.reset()
    const { redirect, ...othersQuery } = router.currentRoute.value.query
    const { rememberMe } = loginConfig.value
    // 仅在真实登录成功且用户填写军团名称后更新，避免默认租户登录覆盖历史军团。
    if (submittedTenantName) {
      lastSuccessfulTenantName.value = submittedTenantName
    }
    loginConfig.value.username = rememberMe ? form.username : ''

    // 如果有重定向参数，解码并直接跳转到完整路径
    if (redirect) {
      const redirectPath = decodeURIComponent(redirect as string)
      await router.push(redirectPath)
    } else {
      await router.push({
        path: '/',
        query: {
          ...othersQuery,
        },
      })
    }
    Message.success('欢迎使用')
  } catch (error) {
    console.error(error)
    getCaptcha()
    form.captcha = ''
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getCaptcha()
})
</script>

<style scoped lang="scss">
.arco-input-wrapper,
:deep(.arco-select-view-single) {
  height: 40px;
  border-radius: 4px;
  font-size: 13px;
}

.arco-input-wrapper.arco-input-error {
  background-color: rgb(var(--danger-1));
  border-color: rgb(var(--danger-3));
}

.arco-input-wrapper.arco-input-error:hover {
  background-color: rgb(var(--danger-1));
  border-color: rgb(var(--danger-6));
}

.arco-input-wrapper :deep(.arco-input) {
  font-size: 13px;
  color: var(--color-text-1);
}

.arco-input-wrapper:hover {
  border-color: rgb(var(--arcoblue-6));
}

.captcha {
  width: 111px;
  height: 36px;
  margin: 0 0 0 5px;
}

.btn {
  height: 40px;
}

.captcha-container {
  position: relative;
  display: inline-block;
  cursor: pointer;
}

.overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(51, 51, 51, 0.8);
  display: flex;
  justify-content: center;
  align-items: center;
}

.overlay p {
  font-size: 12px;
  color: white;
}

.eve-login-entry {
  margin-top: -8px;
  text-align: center;

  :deep(.arco-divider-text) {
    color: var(--color-text-3);
    font-size: 12px;
  }
}

.eve-login-entry__register {
  height: 40px;
  color: rgb(var(--arcoblue-6));
  border-color: rgba(var(--arcoblue-6), 0.35);
  background: rgba(var(--arcoblue-6), 0.06);
}

.eve-login-entry__recover {
  display: inline-flex;
  margin-top: 12px;
  font-size: 12px;
}
</style>
