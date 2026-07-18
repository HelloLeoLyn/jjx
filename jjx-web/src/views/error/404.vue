<template>
  <div class="error-page">
    <div class="error-content">
      <el-result icon="error" title="404" sub-title="抱歉，您访问的页面不存在">
        <template #extra>
          <el-button type="primary" @click="goHome">返回首页</el-button>
          <el-button @click="goBack">返回上一页</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const goHome = () => {
  // 尝试导航到首页，如果失败则导航到登录页
  router.push('/dashboard').catch((error) => {
    console.error('导航到首页失败:', error)
    // 如果导航失败，可能是用户未登录，跳转到登录页
    ElMessage.warning('无法访问首页，请先登录')
    router.push('/login')
  })
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    // 如果没有历史记录，则跳转到首页
    goHome()
  }
}
</script>

<style scoped lang="scss">
.error-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f7fa;
}

.error-content {
  text-align: center;
}
</style>
