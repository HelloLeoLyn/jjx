<template>
  <slot />
  <!-- SkeletonWrapper定义通用样式，暂不实现，可以自定义颜色和hover颜色，默认是灰色 -->
  <!-- <SkeletonWrapper color="rgba(64, 158, 255, 0.35)" hover-color="rgba(64, 158, 255, 0.75)">
    <slot />
  </SkeletonWrapper> -->
</template>

<script setup lang="ts">
defineOptions({ name: 'SkeletonQuery' })
</script>
