<template>
  <div class="kanban-iframe-wrapper">
    <iframe
      :src="kanbanUrl"
      class="kanban-iframe"
      frameborder="0"
      allowfullscreen
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'ProductionKanban' })

const kanbanUrl = 'http://localhost:5173'
</script>

<style scoped>
.kanban-iframe-wrapper {
  width: 100%;
  height: calc(100vh - 84px);
  overflow: hidden;
  background: #f0f2f5;
}

.kanban-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
