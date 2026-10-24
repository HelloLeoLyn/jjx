<template>
  <div class="material-search-option">
    <div class="row-main">
      <span class="name">{{ item.materialName || '未填写名称' }}</span>
      <span class="code">{{ item.materialCode || '无编码' }}</span>
      <!-- 用轻量标签替代 el-tag：避免把 element-plus 组件样式带进公共选择器依赖（复核修复） -->
      <span v-if="item.isCustomerSupplied" class="badge badge-supplied">客供</span>
      <span v-if="item.isSample" class="badge badge-sample">样品</span>
    </div>
    <div class="row-meta">
      <span v-if="item.categoryName">品类：{{ item.categoryName }}</span>
      <span v-else class="missing">品类：未归类</span>
      <span>{{ item.specification ? '规格：' + item.specification : '规格：未填写' }}</span>
      <span v-if="item.size">尺寸：{{ item.size }}</span>
      <span v-if="item.unit">单位：{{ item.unit }}</span>
      <span v-if="item.materialMeta">材质：{{ item.materialMeta }}</span>
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
import { ref } from 'vue'
import type { InventoryMaterial } from '@/types/inventory/material'

defineProps<{ item: InventoryMaterial }>()
const expanded = ref(false)
</script>

<style scoped>
.material-search-option {
  padding: 2px 0;
  line-height: 1.4;
}
.row-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.name {
  font-weight: 600;
}
.code {
  font-size: 12px;
  color: #909399;
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
