<template>
  <span class="eve-type-icon" :class="{ 'eve-type-icon--fallback': !iconUrl || imageFailed }" :style="{ width: `${size}px`, height: `${size}px` }">
    <img
      v-if="iconUrl && !imageFailed"
      :src="iconUrl"
      :alt="alt"
      loading="lazy"
      @error="imageFailed = true"
    />
    <icon-image v-else aria-hidden="true" />
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getEveTypeIconUrl } from '@/utils/eveImage'

defineOptions({ name: 'EveTypeIcon' })

const props = withDefaults(defineProps<Props>(), {
  size: 24,
  alt: 'EVE 物品图标',
})
const imageFailed = ref(false)
const iconUrl = computed(() => getEveTypeIconUrl(props.typeId))

interface Props {
  typeId?: string | number
  size?: number
  alt?: string
}

/** 类型 ID 变化时重置失败状态，以便虚拟列表复用节点后继续加载新图标。 */
watch(() => props.typeId, () => {
  imageFailed.value = false
})
</script>

<style scoped lang="scss">
.eve-type-icon { display: grid; flex: 0 0 auto; place-items: center; overflow: hidden; border-radius: 4px; }
.eve-type-icon img { display: block; width: 100%; height: 100%; object-fit: contain; }
.eve-type-icon--fallback { color: var(--color-text-3); background: var(--color-fill-2); }
</style>
