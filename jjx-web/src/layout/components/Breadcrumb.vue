<!-- src/layout/components/Breadcrumb.vue -->
<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
      <span v-if="item.meta?.title" @click="handleBreadcrumbClick(item)">
        {{ item.meta.title }}
      </span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const breadcrumbs = computed(() => {
  const matched = route.matched.filter((item) => item.meta?.title)
  return matched
})

const handleBreadcrumbClick = (item: any) => {
  if (item.path !== route.path) {
    router.push(item.path)
  }
}
</script>

<style scoped>
.el-breadcrumb {
  line-height: 50px;
  font-size: 14px;
}

.el-breadcrumb__item span {
  cursor: pointer;
  transition: color 0.3s;
}

.el-breadcrumb__item span:hover {
  color: #409eff;
}
</style>
