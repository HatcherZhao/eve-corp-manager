<script setup lang="ts">
interface Props {
  title?: string
  description?: string
  flush?: boolean
}

withDefaults(defineProps<Props>(), {
  title: '',
  description: '',
  flush: false,
})
</script>

<template>
  <section class="industry-panel" :class="{ 'industry-panel--flush': flush }">
    <header v-if="title || description || $slots.actions" class="industry-panel__header">
      <div class="industry-panel__copy">
        <strong v-if="title">{{ title }}</strong>
        <small v-if="description">{{ description }}</small>
      </div>
      <div v-if="$slots.actions" class="industry-panel__actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="industry-panel__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.industry-panel {
  min-width: 0;
  padding: .75rem;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 14px;
  background: var(--color-bg-1);
  color: var(--color-text-1);
}

.industry-panel--flush {
  padding: 0;
}

.industry-panel__header {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: .75rem;
  margin-bottom: .75rem;
}

.industry-panel--flush .industry-panel__header {
  margin: 0;
  padding: .75rem;
  border-bottom: 1px solid var(--color-border-2);
}

.industry-panel__copy {
  min-width: 0;
}

.industry-panel__copy strong,
.industry-panel__copy small {
  display: block;
}

.industry-panel__copy strong {
  font-size: .95rem;
}

.industry-panel__copy small {
  margin-top: .2rem;
  color: var(--color-text-3);
  line-height: 1.5;
}

.industry-panel__actions {
  display: flex;
  width: 100%;
  flex-wrap: wrap;
  gap: .5rem;
}

.industry-panel--flush .industry-panel__body {
  min-width: 0;
}

@media (min-width: 768px) {
  .industry-panel {
    padding: 1rem;
  }

  .industry-panel--flush {
    padding: 0;
  }

  .industry-panel__header {
    flex-direction: row;
    align-items: flex-start;
    justify-content: space-between;
  }

  .industry-panel--flush .industry-panel__header {
    padding: 1rem;
  }

  .industry-panel__actions {
    width: auto;
    justify-content: flex-end;
  }
}
</style>
