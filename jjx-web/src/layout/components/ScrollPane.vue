<!-- src/layout/components/ScrollPane.vue -->
<template>
  <div ref="scrollContainer" class="scroll-container" @wheel="handleScroll">
    <div
      ref="scrollWrapper"
      :style="{ left: left + 'px' }"
      class="scroll-wrapper"
    >
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const scrollContainer = ref<HTMLElement>()
const scrollWrapper = ref<HTMLElement>()
const left = ref(0)

const handleScroll = (e: WheelEvent) => {
  e.preventDefault()

  const $scrollWrapper = scrollWrapper.value
  const $scrollContainer = scrollContainer.value

  if ($scrollWrapper && $scrollContainer) {
    // 使用 deltaY 获取滚动距离
    const delta = e.deltaY
    const step = delta > 0 ? -40 : 40

    if ($scrollWrapper.clientWidth > $scrollContainer.clientWidth) {
      const maxLeft = $scrollContainer.clientWidth - $scrollWrapper.clientWidth
      left.value = Math.min(0, Math.max(maxLeft, left.value + step))
    } else {
      left.value = Math.min(0, left.value + step)
    }
  }
}
</script>

<style scoped lang="scss">
.scroll-container {
  white-space: nowrap;
  position: relative;
  overflow: hidden;
  width: 100%;

  .scroll-wrapper {
    position: relative;
    transition: left 0.3s ease;
  }
}
</style>
