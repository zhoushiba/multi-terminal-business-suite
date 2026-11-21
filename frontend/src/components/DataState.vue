<template>
  <div class="data-state">
    <div v-if="loading" class="data-state__loading">
      <el-skeleton :rows="rows" animated />
    </div>
    <el-result v-else-if="error" icon="error" title="加载失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="$emit('retry')">重新加载</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="empty" :description="emptyText" :image-size="96" />
    <slot v-else />
  </div>
</template>

<script setup lang="ts">
/**
 * 统一的「加载中 / 失败 / 空」三态容器。
 * 列表页和图表卡片都用它，避免每个页面各写一套 v-if 分支导致状态不一致。
 */
withDefaults(
  defineProps<{
    loading?: boolean
    error?: string | null
    empty?: boolean
    emptyText?: string
    rows?: number
  }>(),
  { loading: false, error: null, empty: false, emptyText: '暂无数据', rows: 5 }
)

defineEmits<{ retry: [] }>()
</script>

<style scoped>
.data-state {
  min-height: 120px;
}

.data-state__loading {
  padding: 12px 4px;
}
</style>
