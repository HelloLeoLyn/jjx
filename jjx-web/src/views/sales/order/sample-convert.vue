<template>
  <div class="app-container">
    <!-- 页面标题与面包屑 -->
    <div class="page-header">
      <el-page-header :icon="ArrowLeft" title="返回样品单列表" @back="goBack">
        <template #content>
          <span class="text-large font-bold mr-3">
            {{ returnToSourceOrder ? '关联回原订单' : '转量产（来自样品单）' }}
          </span>
          <el-tag v-if="!returnToSourceOrder" size="small" type="success" effect="plain"
            >数据已带入，请确认数量/单价后提交</el-tag
          >
        </template>
      </el-page-header>
    </div>

    <!-- 表单卡片：复用标准销售订单新增表单 -->
    <el-card class="form-card" shadow="never">
      <OrderForm
        ref="orderFormRef"
        :sample-order-id="sampleOrderId"
        @conversion-mode="returnToSourceOrder = $event"
        @success="handleSuccess"
        @cancel="goBack"
      />
    </el-card>

    <!-- 底部操作栏 -->
    <div class="footer-bar">
      <el-button @click="goBack">取 消</el-button>
      <el-button
        v-if="!returnToSourceOrder"
        type="primary"
        @click="handleSubmit"
        :loading="submitting"
        :disabled="!sampleOrderId"
        >确认转量产</el-button
      >
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

// 路由参数可能是缺失/非法（手输 URL、过期书签）：Number(undefined)=NaN 会让本页静默退化成
// 「普通新增订单」（NaN 在 OrderForm 里是假值，不会走 convertSample），比报错更隐蔽；
// 这里显式校验，取不到就提示并回样品单列表（dev-20261016-054）。
const rawSampleId = Array.isArray(route.params.sampleId)
  ? route.params.sampleId[0]
  : route.params.sampleId
const parsedSampleId = Number(rawSampleId)
const sampleOrderId =
  Number.isFinite(parsedSampleId) && parsedSampleId > 0 ? parsedSampleId : undefined

if (!sampleOrderId) {
  ElMessage.error('缺少样品单参数：请从「样品单列表」点「转量产」进入本页')
  router.replace('/sales/sample-order')
}

// 表单组件引用
const orderFormRef = ref<InstanceType<typeof OrderForm> | null>(null)
const returnToSourceOrder = ref(false)
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
