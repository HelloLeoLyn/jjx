<template>
  <div class="print-search-option">
    <div class="row-main">
      <span class="name">{{ item.displayName || item.artworkNo || item.versionNo || '-' }}</span>
      <span v-if="item.matchedType" class="badge">{{ typeLabel(item.matchedType) }}</span>
      <span v-if="item.availability === false" class="badge unavailable">不可用</span>
    </div>
    <div class="row-meta">
      <span v-if="item.versionNo">版本：{{ item.versionNo }}</span>
      <span v-if="item.productCode || item.productName">产品：{{ item.productCode || '' }} {{ item.productName || '' }}</span>
      <span v-if="item.frameNo">网框：{{ item.frameNo }}</span>
      <span v-if="item.mesh">目数：{{ item.mesh }}</span>
      <span v-if="item.position">位置：{{ item.position }}</span>
      <span v-if="item.purpose">用途：{{ item.purpose }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PrintSearchRow } from '@/api/engineering/printingSearch'

defineProps<{ item: PrintSearchRow }>()

const LABELS: Record<string, string> = {
  ARTWORK: '图案',
  VERSION: '版本',
  FILM: '菲林',
  PLATE: '网版',
  FRAME: '网框',
  PRODUCT: '产品',
  PURPOSE: '用途',
}
function typeLabel(type: string): string {
  return LABELS[type] || type
}
</script>

<style scoped>
.print-search-option { padding: 2px 0; line-height: 1.4; }
.row-main { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.row-main .name { font-weight: 600; }
.badge { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: #ecf5ff; color: #409eff; }
.badge.unavailable { background: #fde2e2; color: #c45656; }
.row-meta { margin-top: 2px; display: flex; gap: 12px; flex-wrap: wrap; font-size: 12px; color: var(--el-text-color-secondary); }
</style>
