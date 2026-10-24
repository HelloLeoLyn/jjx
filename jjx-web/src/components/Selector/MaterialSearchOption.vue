<template>
  <div class="material-search-option">
    <div class="row-main">
      <span class="name">{{ item.materialName || '未填写名称' }}</span>
      <span class="code">{{ item.materialCode || '无编码' }}</span>
      <el-tag v-if="item.isCustomerSupplied" size="small" type="warning" effect="plain">客供</el-tag>
      <el-tag v-if="item.isSample" size="small" type="info" effect="plain">样品</el-tag>
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
.row-remark {
  font-size: 12px;
}
.remark-text {
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
