<template>
  <el-tooltip content="全屏" placement="bottom">
    <el-icon class="screenfull-icon" @click="toggleFullscreen">
      <FullScreen />
    </el-icon>
  </el-tooltip>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { FullScreen } from '@element-plus/icons-vue'

const isFullscreen = ref(false)

// 切换全屏
const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    // 进入全屏
    document.documentElement.requestFullscreen().then(() => {
      isFullscreen.value = true
    }).catch(err => {
      console.error('全屏失败:', err)
    })
  } else {
    // 退出全屏
    if (document.exitFullscreen) {
      document.exitFullscreen().then(() => {
        isFullscreen.value = false
      })
    }
  }
}

// 监听全屏变化
document.addEventListener('fullscreenchange', () => {
  isFullscreen.value = !!document.fullscreenElement
})
</script>

<style scoped>
.screenfull-icon {
  font-size: 18px;
  cursor: pointer;
  color: #666;

  &:hover {
    color: #409eff;
  }
}
</style>
