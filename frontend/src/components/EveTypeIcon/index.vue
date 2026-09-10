<template>
  <img
    v-if="iconUrl && !imageFailed"
    class="eve-type-icon"
    :src="iconUrl"
    :alt="alt"
    :style="{ width: `${size}px`, height: `${size}px` }"
    loading="lazy"
    @error="imageFailed = true"
  />
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
.eve-type-icon { display: block; flex: 0 0 auto; border-radius: 4px; object-fit: contain; }
</style>
