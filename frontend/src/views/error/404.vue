<template>
  <div class="page error-page">
    <el-result icon="warning" :title="title" :sub-title="subTitle">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const perm = computed(() => (typeof route.query.perm === 'string' ? route.query.perm : ''))
const title = computed(() => (perm.value ? '没有访问权限' : '页面不存在'))
const subTitle = computed(() =>
  perm.value ? `当前账号缺少 ${perm.value} 权限，请联系管理员分配角色` : '地址可能已失效，或菜单尚未在 sys_menu 中配置'
)

function goHome(): void {
  router.replace('/')
}
</script>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  background: #fff;
  border-radius: var(--suite-radius);
  border: 1px solid var(--suite-border);
}
</style>
