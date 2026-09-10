<template>
  <a-row justify="end" align="center">
    <a-space size="medium">
      <!-- 搜索 -->
      <Search v-if="isDesktop" />
      <!-- 项目配置 -->
      <a-tooltip content="项目配置" position="bl">
        <a-button size="mini" class="gi_hover_btn" @click="SettingDrawerRef?.open">
          <template #icon>
            <icon-settings :size="18" />
          </template>
        </a-button>
      </a-tooltip>

      <!-- 消息通知 -->
      <a-popover
        position="bottom"
        trigger="click"
        :content-style="{ marginTop: '-5px', padding: 0, border: 'none' }"
        :arrow-style="{ width: 0, height: 0 }"
      >
        <a-badge :count="unreadMessageCount" dot>
          <a-button size="mini" class="gi_hover_btn">
            <template #icon>
              <icon-notification :size="18" />
            </template>
          </a-button>
        </a-badge>
        <template #content>
          <Message @readall-success="getMessageCount" />
        </template>
      </a-popover>

      <!-- 全屏切换组件 -->
      <a-tooltip v-if="!['xs', 'sm'].includes(breakpoint)" content="全屏切换" position="bottom">
        <a-button size="mini" class="gi_hover_btn" @click="toggle">
          <template #icon>
            <icon-fullscreen v-if="!isFullscreen" :size="18" />
            <icon-fullscreen-exit v-else :size="18" />
          </template>
        </a-button>
      </a-tooltip>

      <!-- 暗黑模式切换 -->
      <a-tooltip content="主题切换" position="bottom">
        <GiThemeBtn></GiThemeBtn>
      </a-tooltip>

      <!-- 管理员账户 -->
      <a-dropdown trigger="hover">
        <a-row align="center" :wrap="false" class="user">
          <!-- 管理员头像 -->
          <Avatar :src="eveCharacterAvatar || userStore.avatar" :name="userStore.nickname" :size="32" />
          <span class="username">{{ userStore.nickname }}</span>
          <icon-down />
        </a-row>
        <template #content>
          <a-doption @click="router.push('/user/profile')">
            <span>个人中心</span>
          </a-doption>
          <a-doption @click="router.push('/user/message')">
            <span>消息中心</span>
          </a-doption>
          <a-divider :margin="0" />
          <a-doption @click="logout">
            <span>退出登录</span>
          </a-doption>
        </template>
      </a-dropdown>
    </a-space>
  </a-row>

  <SettingDrawer ref="SettingDrawerRef"></SettingDrawer>
</template>

<script setup lang="ts">
import { Modal } from '@arco-design/web-vue'
import { useFullscreen } from '@vueuse/core'
import { onMounted, ref } from 'vue'
import Message from './Message.vue'
import SettingDrawer from './SettingDrawer.vue'
import Search from './Search.vue'
import { getUnreadMessageCount } from '@/apis'
import { getEveContext } from '@/apis/eve'
import { useUserStore } from '@/stores'
import { getToken } from '@/utils/auth'
import { getEveCharacterPortraitUrl } from '@/utils/eveImage'
import { useBreakpoint, useDevice } from '@/hooks'

defineOptions({ name: 'HeaderRight' })

const { isDesktop } = useDevice()
const { breakpoint } = useBreakpoint()
let socket: WebSocket
onBeforeUnmount(() => {
  if (socket) {
    socket.close()
  }
})

const unreadMessageCount = ref(0)
const eveCharacterAvatar = ref<string>()
// 初始化 WebSocket
const initWebSocket = (token: string) => {
  const wsBase = import.meta.env.VITE_API_WS_URL || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}`
  socket = new WebSocket(`${wsBase}/websocket?token=${encodeURIComponent(token)}`)
  socket.onopen = () => {
    // console.log('WebSocket connection opened')
  }

  socket.onmessage = (event) => {
    unreadMessageCount.value = Number.parseInt(event.data)
  }

  socket.onerror = () => {
    // console.error('WebSocket error:', error)
  }

  socket.onclose = () => {
    // console.log('WebSocket connection closed')
  }
}

// 查询未读消息数量
const getMessageCount = async () => {
  const { data } = await getUnreadMessageCount()
  unreadMessageCount.value = data.total
  const token = getToken()
  if (token) {
    initWebSocket(token)
  }
}

/** 使用当前登录用户绑定的游戏角色肖像，不修改其自主设置的本站头像数据。 */
const loadEveCharacterAvatar = async () => {
  try {
    const { data } = await getEveContext()
    eveCharacterAvatar.value = getEveCharacterPortraitUrl(data.character?.characterId)
  } catch {
    // 未绑定游戏角色或当前上下文暂不可用时，继续显示本站头像。
    eveCharacterAvatar.value = undefined
  }
}

const { isFullscreen, toggle } = useFullscreen()

const router = useRouter()
const userStore = useUserStore()
const SettingDrawerRef = ref<InstanceType<typeof SettingDrawer>>()

// 退出登录
const logout = () => {
  Modal.warning({
    title: '提示',
    content: '确认退出登录？',
    hideCancel: false,
    closable: true,
    onBeforeOk: async () => {
      try {
        await userStore.logout()
        await router.replace('/login')
        return true
      } catch (error) {
        return false
      }
    },
  })
}

onMounted(() => {
  getMessageCount()
  loadEveCharacterAvatar()
})
</script>

<style scoped lang="scss">
.arco-dropdown-open .arco-icon-down {
  transform: rotate(180deg);
}

.user {
  cursor: pointer;
  color: var(--color-text-1);

  .username {
    margin-left: 10px;
    white-space: nowrap;
  }

  .arco-icon-down {
    transition: all 0.3s;
    margin-left: 2px;
  }
}
</style>
