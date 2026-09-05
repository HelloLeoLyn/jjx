<template>
  <div class="print-toolbar no-print">
    <div class="print-toolbar__left">
      <el-button @click="handleBack">{{ backText }}</el-button>
      <div class="print-toolbar__title">
        <slot name="title">{{ title }}</slot>
      </div>
    </div>

    <div class="print-toolbar__actions">
      <slot name="actions">
        <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    backText?: string
  }>(),
  {
    title: '',
    backText: '返回',
  }
)

function handleBack() {
  window.history.back()
}

function handlePrint() {
  window.print()
}
</script>

<style scoped>
.print-toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.print-toolbar__left,
.print-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.print-toolbar__title {
  color: #606266;
  font-size: 14px;
}

@media print {
  .no-print {
    display: none !important;
  }
}
</style>
