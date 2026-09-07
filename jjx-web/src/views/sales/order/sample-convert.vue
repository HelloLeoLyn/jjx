<template>
  <div class="app-container">
    <!-- 页面标题与面包屑 -->
    <div class="page-header">
      <el-page-header :icon="ArrowLeft" title="返回样品单列表" @back="goBack">
        <template #content>
          <span class="text-large font-bold mr-3"> 转量产（来自样品单） </span>
          <el-tag size="small" type="success" effect="plain">数据已带入，请确认数量/单价后提交</el-tag>
        </template>
      </el-page-header>
    </div>

    <!-- 表单卡片：复用标准销售订单新增表单 -->
    <el-card class="form-card" shadow="never">
      <OrderForm
        ref="orderFormRef"
        :sample-order-id="sampleOrderId"
        @success="handleSuccess"
        @cancel="goBack"
      />
    </el-card>

    <!-- 底部操作栏 -->
    <div class="footer-bar">
      <el-button @click="goBack">取 消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">确认转量产</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'SalesOrderSampleConvert',
})

import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import OrderForm from './components/OrderForm.vue'

const route = useRoute()
const router = useRouter()

const sampleOrderId = Number(route.params.sampleId)

// 表单组件引用
const orderFormRef = ref<InstanceType<typeof OrderForm> | null>(null)
const submitting = ref(false)

// 返回样品单列表
const goBack = () => {
  router.push('/sales/sample-order')
}

const handleSuccess = () => {
  goBack()
}

// 提交表单（submitForm 成功后会 emit success → 返回样品单列表）
const handleSubmit = async () => {
  if (!orderFormRef.value) return

  submitting.value = true
  try {
    await orderFormRef.value.submitForm()
  } catch (error) {
    console.error('转量产失败:', error)
    ElMessage.error('转量产失败')
  } finally {
    submitting.value = false
  }
}
</script>
