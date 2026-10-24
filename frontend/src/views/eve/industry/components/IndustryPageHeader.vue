<script setup lang="ts">
interface Props {
  eyebrow: string
  title: string
  description: string
}

defineProps<Props>()
</script>

<template>
  <header class="industry-page-header">
    <div class="industry-page-header__copy">
      <span class="industry-page-header__eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="industry-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.industry-page-header {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid rgba(var(--arcoblue-6), .22);
  border-radius: 14px;
  background: linear-gradient(125deg, rgba(var(--arcoblue-6), .1), transparent 58%), var(--color-bg-1);
  color: var(--color-text-1);
}

.industry-page-header__copy {
  min-width: 0;
}

.industry-page-header__eyebrow {
  color: rgb(var(--arcoblue-6));
  font-family: DINPro, sans-serif;
  font-size: .6875rem;
  letter-spacing: 0;
}

.industry-page-header h1 {
  margin: .25rem 0;
  font-size: 1.5rem;
  text-wrap: balance;
}

.industry-page-header p {
  max-width: 48rem;
  margin: 0;
  color: var(--color-text-3);
  line-height: 1.65;
  text-wrap: pretty;
}

.industry-page-header__actions {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-wrap: wrap;
  gap: .5rem;
}

@media (min-width: 768px) {
  .industry-page-header {
    padding: 1.25rem;
  }
}

@media (min-width: 1200px) {
  .industry-page-header {
    flex-direction: row;
    align-items: flex-end;
    justify-content: space-between;
  }

  .industry-page-header__actions {
    width: auto;
    justify-content: flex-end;
  }
}
</style>
