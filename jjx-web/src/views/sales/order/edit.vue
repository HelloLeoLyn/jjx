<template>
  <div class="app-container">
    <!-- 页面标题与面包屑 -->
    <div class="page-header">
      <el-page-header :icon="ArrowLeft" title="返回订单列表" @back="goBack">
        <template #content>
          <span class="text-large font-bold mr-3"> 修改订单 </span>
        </template>
      </el-page-header>
    </div>

    <!-- 表单卡片 -->
    <el-card class="form-card" shadow="never">
      <OrderForm
        ref="orderFormRef"
        :is-edit="true"
        :order-id="orderId"
        @success="handleSuccess"
        @cancel="goBack"
      />
    </el-card>

    <!-- 底部操作栏 -->
    <div class="footer-bar">
      <el-button @click="goBack">取 消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">保 存</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'SalesOrderEdit',
})

import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import OrderForm from './components/OrderForm.vue'

const route = useRoute()
const router = useRouter()

// 从路由参数获取订单ID
const orderId = ref<number | undefined>(undefined)
const orderFormRef = ref<InstanceType<typeof OrderForm> | null>(null)
const submitting = ref(false)

// 初始化
onMounted(() => {
  const id = route.params.id
  if (id) {
    orderId.value = Number(id)
  } else {
    ElMessage.warning('未指定要修改的订单')
    goBack()
  }
})

// 返回订单列表
const goBack = () => {
  router.push('/sales/order')
}

// 提交表单
const handleSubmit = async () => {
  if (!orderFormRef.value) return

  submitting.value = true
  try {
    const success = await orderFormRef.value.submitForm()
    if (success) {
      ElMessage.success('修改订单成功')
      goBack()
    }
  } catch (error) {
    console.error('修改订单失败:', error)
    ElMessage.error('修改订单失败')
  } finally {
    submitting.value = false
  }
}

// 提交成功回调
const handleSuccess = () => {
  // OrderForm 内部已处理成功逻辑
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.form-card {
  margin-bottom: 80px;
}

.footer-bar {
  position: fixed;
  bottom: 0;
  left: 200px; /* 适配侧边栏宽度 */
  right: 0;
  padding: 12px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  text-align: right;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.08);
  z-index: 100;
}
</style>
