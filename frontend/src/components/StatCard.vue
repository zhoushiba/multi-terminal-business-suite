<template>
  <div class="stat-card" :class="`stat-card--${tone}`">
    <div class="stat-card__icon">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-card__body">
      <div class="stat-card__label">{{ label }}</div>
      <div class="stat-card__value">{{ value }}</div>
      <div v-if="hint" class="stat-card__hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    icon?: string
    hint?: string
    tone?: 'blue' | 'green' | 'orange' | 'purple'
  }>(),
  { icon: 'DataLine', hint: '', tone: 'blue' }
)
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid var(--suite-border);
  border-radius: var(--suite-radius);
  box-shadow: var(--suite-shadow);
}

.stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  flex-shrink: 0;
}

.stat-card--blue .stat-card__icon {
  color: #2f6bff;
  background: rgba(47, 107, 255, 0.1);
}

.stat-card--green .stat-card__icon {
  color: #12b76a;
  background: rgba(18, 183, 106, 0.1);
}

.stat-card--orange .stat-card__icon {
  color: #f79009;
  background: rgba(247, 144, 9, 0.12);
}

.stat-card--purple .stat-card__icon {
  color: #7a5af8;
  background: rgba(122, 90, 248, 0.1);
}

.stat-card__label {
  font-size: 13px;
  color: var(--suite-text-secondary);
}

.stat-card__value {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-card__hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--suite-text-secondary);
}
</style>
