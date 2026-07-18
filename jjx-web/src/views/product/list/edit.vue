<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <el-button text @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <span class="page-title">编辑产品</span>
        </div>
      </template>

      <ProductFormContent ref="formContentRef" :product-id="productId" @success="handleSuccess" />

      <div class="form-footer">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductEdit',
})

import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import ProductFormContent from './components/ProductFormContent.vue'

const router = useRouter()
const route = useRoute()
const formContentRef = ref<InstanceType<typeof ProductFormContent>>()
const submitting = ref(false)

const productId = computed(() => Number(route.params.id))

const goBack = () => {
  router.push('/product/list')
}

const handleSubmit = async () => {
  if (!formContentRef.value) return
  submitting.value = true
  try {
    await formContentRef.value.handleSubmit()
  } catch {
    // 验证失败，不跳转
  } finally {
    submitting.value = false
  }
}

const handleSuccess = () => {
  ElMessage.success('保存成功')
  goBack()
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.form-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}
</style>
