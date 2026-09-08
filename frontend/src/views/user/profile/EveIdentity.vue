<script setup lang="ts">
import { Message } from '@arco-design/web-vue'
import { onMounted, ref } from 'vue'
import AuthorizationActions from '@/views/eve/components/AuthorizationActions.vue'
import { type EveContext, completeEveCharacterBinding, getEveContext, startEveCharacterBinding } from '@/apis/eve'
import {
  closePreparedEveAuthorizationWindow,
  navigateEveAuthorizationWindow,
  prepareEveAuthorizationWindow,
} from '@/utils/eveAuthorizationWindow'

const context = ref<EveContext>()
const loading = ref(false)
const failed = ref(false)
const binding = ref(false)
const completingBinding = ref(false)
const bindingModalVisible = ref(false)
const bindingCallbackUrl = ref('')

const identityLabels: Record<string, string> = {
  OWNER: '军团 CEO',
  ADMIN: '军团总监',
  MEMBER: '军团成员',
}

/** 加载当前本站账号绑定的主游戏角色。 */
async function loadContext() {
  loading.value = true
  failed.value = false
  try {
    const { data } = await getEveContext()
    context.value = data
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadContext)

/** 发起当前本站账号的游戏角色绑定。 */
async function startBinding() {
  const popup = prepareEveAuthorizationWindow()
  if (!popup) {
    Message.warning('浏览器阻止了授权窗口，请允许本站打开弹窗后重试')
    return
  }
  binding.value = true
  try {
    const { data } = await startEveCharacterBinding()
    if (!navigateEveAuthorizationWindow(popup, data.authorizationUri)) {
      Message.warning('授权窗口已关闭，请重新发起授权')
      return
    }
    bindingCallbackUrl.value = ''
    bindingModalVisible.value = true
  } catch (error) {
    closePreparedEveAuthorizationWindow(popup)
    throw error
  } finally {
    binding.value = false
  }
}

/** 导入绑定回调，提交后立即清空完整地址。 */
async function completeBinding(): Promise<boolean> {
  if (!bindingCallbackUrl.value.trim()) {
    Message.warning('请粘贴绑定授权后的完整回调地址')
    return false
  }
  completingBinding.value = true
  const submittedUrl = bindingCallbackUrl.value.trim()
  bindingCallbackUrl.value = ''
  try {
    const { data } = await completeEveCharacterBinding(submittedUrl)
    context.value = data
    bindingModalVisible.value = false
    Message.success('EVE 游戏角色绑定成功')
    await loadContext()
    return true
  } catch {
    return false
  } finally {
    completingBinding.value = false
  }
}
</script>

<template>
  <a-card title="EVE 游戏身份" bordered class="gradient-card eve-profile-binding">
    <a-spin :loading="loading" class="eve-profile-binding__loading">
      <a-result v-if="failed" status="error" title="游戏身份加载失败">
        <template #extra><a-button @click="loadContext">重试</a-button></template>
      </a-result>
      <template v-else-if="context">
        <div class="eve-profile-binding__identity">
          <div class="eve-profile-binding__avatar"><icon-user /></div>
          <div>
            <strong>{{ context.character?.name || '未绑定角色' }}</strong>
            <p>{{ context.corporation ? `${context.corporation.name} [${context.corporation.ticker}]` : '未加入军团空间' }}</p>
          </div>
          <a-tag color="arcoblue">{{ identityLabels[context.derivedIdentity] || context.derivedIdentity || '未识别' }}</a-tag>
        </div>
        <div class="eve-profile-binding__actions">
          <AuthorizationActions @refreshed="loadContext" />
          <div class="eve-profile-binding__secondary-actions">
            <a-button :loading="binding" @click="startBinding">绑定 EVE 角色</a-button>
            <a-button type="text" @click="$router.push('/eve/permissions')">查看权限树</a-button>
          </div>
        </div>
      </template>
    </a-spin>
  </a-card>

  <a-modal
    v-model:visible="bindingModalVisible"
    title="完成 EVE 角色绑定"
    :ok-loading="completingBinding"
    ok-text="验证并绑定"
    @before-ok="completeBinding"
    @cancel="bindingCallbackUrl = ''"
  >
    <p class="eve-profile-binding__modal-hint">完成国服授权后粘贴浏览器最终停留的完整地址。地址仅用于本次验证，不会写入浏览器存储。</p>
    <a-textarea v-model="bindingCallbackUrl" :auto-size="{ minRows: 4, maxRows: 7 }" allow-clear placeholder="https://.../callback?code=...&state=..." />
  </a-modal>
</template>

<style scoped lang="scss">
.eve-profile-binding { margin-top: 16px; }
.eve-profile-binding__loading { display: block; min-height: 100px; }
.eve-profile-binding__identity { display: flex; align-items: center; gap: 14px; }
.eve-profile-binding__identity > div:nth-child(2) { min-width: 0; flex: 1; }
.eve-profile-binding__identity strong { font-size: 16px; }
.eve-profile-binding__identity p { margin: 4px 0 0; overflow: hidden; color: var(--color-text-3); text-overflow: ellipsis; white-space: nowrap; }
.eve-profile-binding__avatar { display: grid; width: 44px; height: 44px; flex: 0 0 44px; place-items: center; border-radius: 13px; color: rgb(var(--arcoblue-6)); background: rgba(var(--arcoblue-6), 0.1); font-size: 20px; }
.eve-profile-binding__actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--color-border-2); }
.eve-profile-binding__secondary-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.eve-profile-binding__modal-hint { margin-top: 0; color: var(--color-text-2); line-height: 1.65; }
@media (max-width: 600px) { .eve-profile-binding__actions { flex-direction: column; } }
</style>
