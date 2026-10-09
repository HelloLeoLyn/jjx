<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="drawing-header">
          <span class="title">图纸管理</span>
          <el-input
            v-model="keyword"
            placeholder="输入产品编码"
            clearable
            style="width: 220px; margin-left: 16px"
            @keyup.enter="onSearch"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <span class="tip"
            >工程图/技术文档 · 受控与下发（与「产品文件库」同源，产品作业规范不受影响）</span
          >
        </div>
      </template>

      <el-empty v-if="!submitted" description="请输入产品编码后查询该产品的图纸/工程文件" :image-size="80" />
      <ProductFileLibrary
        v-else
        :key="submitted"
        :product-code="submitted"
        :categories="[
          '结构图',
          '印刷指导图',
          '产品图集',
          '确认图',
          '菲林',
          '模具',
          '客供稿',
        ]"
        upload-perm="product:edit"
        delete-perm="product:delete"
        release-perm="engineering:drawing:release"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'

defineOptions({ name: 'EngineeringDrawing' })

const keyword = ref('')
const submitted = ref('')

function onSearch() {
  submitted.value = keyword.value.trim()
}
</script>

<style scoped>
.drawing-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.drawing-header .title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.drawing-header .tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
