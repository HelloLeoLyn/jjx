<template>
  <div class="material-search-option">
    <div class="row-main">
      <span
        v-for="field in mainFields"
        :key="field.key"
        :class="[field.className, field.emphasis, { missing: isBlank(item, field) }]"
      >{{ fieldText(item, field) }}</span>
      <!-- 用轻量标签替代 el-tag：避免把 element-plus 组件样式带进公共选择器依赖（复核修复） -->
      <span v-if="item.isCustomerSupplied" class="badge badge-supplied">客供</span>
      <span v-if="item.isSample" class="badge badge-sample">样品</span>
    </div>
    <div class="row-meta">
      <span
        v-for="field in metaFields"
        :key="field.key"
        :class="{ missing: isBlank(item, field) }"
      >{{ fieldText(item, field) }}</span>
    </div>
    <div v-if="item.remark" class="row-remark">
      <el-button link type="primary" size="small" @click.stop="expanded = !expanded">
        {{ expanded ? '收起备注' : '展开备注' }}
      </el-button>
      <div v-if="expanded" class="remark-text">{{ item.remark }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 物料搜索候选行（BOM 物料选择器与通用物料选择器共用）。
 * 显示哪些字段、顺序、所在行、空值占位都在 materialSearchOptionFields.ts 里配，本组件只负责渲染。
 */
import { computed, ref } from 'vue'
import type { InventoryMaterial } from '@/types/inventory/material'
import { fieldText, fieldsOfRow, isBlank } from './materialSearchOptionFields'

const props = defineProps<{ item: InventoryMaterial }>()
const expanded = ref(false)
const mainFields = computed(() => fieldsOfRow(props.item, 1))
const metaFields = computed(() => fieldsOfRow(props.item, 2))
</script>

<style scoped>
.material-search-option {
  padding: 2px 0;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
}
.row-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.row-main > span,
.row-meta > span {
  min-width: 0;
  max-width: 100%;
}
.name,
.strong {
  font-weight: 600;
}
.code,
.muted {
  font-size: 12px;
  color: #909399;
}
.specification {
  font-size: 12px;
  color: #606266;
}
.row-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #606266;
}
.missing {
  color: #c0c4cc;
}
.badge {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  height: 18px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1;
  border: 1px solid transparent;
}
.badge-supplied {
  color: #e6a23c;
  border-color: #f3d19e;
  background: #fdf6ec;
}
.badge-sample {
  color: #909399;
  border-color: #dcdfe6;
  background: #f4f4f5;
}
.row-remark {
  font-size: 12px;
}
.remark-text {
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
