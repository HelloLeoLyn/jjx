<template>
  <div class="spec-head">
    <ProductBasicInfo :detail="detail" />
    <div class="spec-head-actions">
      <el-select
        :model-value="versionId"
        size="small"
        style="width: 150px; margin-right: 8px"
        title="查看发布版本快照（只读）"
        @update:model-value="(val: number) => emit('update:versionId', val)"
      >
        <el-option label="工作态" :value="0" />
        <el-option v-for="v in versions" :key="v.id" :label="v.versionNo" :value="v.id" />
      </el-select>
      <el-button
        type="primary"
        icon="View"
        :disabled="loading || !productId"
        @click="emit('preview')"
      >预览 / 打印文档集</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import ProductBasicInfo from './ProductBasicInfo.vue'
import type { ProductFullVO } from '@/types/product'

/**
 * 作业规范抽屉 header（公共组件；dev-20261010-032）
 * 组合「产品基本信息 + 发布版本切换 + 预览/打印按钮」，预览打印入口归口于此。
 */
withDefaults(
  defineProps<{
    detail?: ProductFullVO | null
    productId?: number
    versions?: any[]
    versionId?: number
    loading?: boolean
  }>(),
  { productId: 0, versions: () => [], versionId: 0, loading: false }
)

const emit = defineEmits<{
  (e: 'update:versionId', value: number): void
  (e: 'preview'): void
}>()
</script>

<style scoped>
.spec-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.spec-head-actions {
  flex-shrink: 0;
}
</style>
