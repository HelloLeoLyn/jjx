<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="drawing-header">
          <span class="title">工程图纸</span>
          <el-input
            v-model="keyword"
            placeholder="输入产品编码"
            clearable
            style="width: 220px; margin-left: 16px"
            @keyup.enter="onSearch"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <span class="tip"
            >按产品上传工程图纸，选择图种和版本；PDF、图片可在线预览并用于文档集打印。</span
          >
        </div>
      </template>

      <el-empty v-if="!submitted" description="请输入产品编码，查询或上传该产品的工程图纸" :image-size="80" />
      <ProductFileLibrary
        v-else
        :key="submitted"
        :product-code="submitted"
        :categories="drawingCategories"
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
import { ENGINEERING_DRAWING_VISIBLE_CATEGORIES } from '@/components/product/productFileCategories'

defineOptions({ name: 'EngineeringDrawing' })

const keyword = ref('')
const submitted = ref('')
const drawingCategories = [...ENGINEERING_DRAWING_VISIBLE_CATEGORIES, '客供稿']

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
