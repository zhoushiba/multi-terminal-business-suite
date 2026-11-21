<template>
  <template v-for="item in nodes" :key="item.id">
    <el-sub-menu v-if="item.children && item.children.length" :index="`group-${item.id}`">
      <template #title>
        <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
        <span>{{ item.name }}</span>
      </template>
      <MenuNode :nodes="item.children" />
    </el-sub-menu>
    <el-menu-item v-else :index="item.path">
      <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
      <template #title>{{ item.name }}</template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { RouteNode } from '@/types/api'

defineOptions({ name: 'MenuNode' })

defineProps<{ nodes: RouteNode[] }>()
</script>
