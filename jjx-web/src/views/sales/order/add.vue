<template>
  <div class="app-container">
    <!-- 页面标题与面包屑 -->
    <div class="page-header">
      <el-page-header :icon="ArrowLeft" title="返回订单列表" @back="goBack">
        <template #content>
          <span class="text-large font-bold mr-3"> 新增订单 </span>
        </template>
      </el-page-header>
    </div>

    <!-- 表单卡片 -->
    <el-card class="form-card" shadow="never">
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
      <el-alert v-if="quotationId && ready" title="已带入报价资料；补充订单信息并提交后完成转换，取消不会创建订单。" type="info" :closable="false" />
      <el-alert v-if="quotationId && ready && omittedAttachmentCount > 0" :title="omittedNotice" type="warning" :closable="false" show-icon />
      <OrderForm v-if="ready" :key="quotationId || 'manual'" ref="orderFormRef" :initial-data="initialData" :source-quotation-id="quotationId" :quotation-source-version="sourceVersion" @success="handleSuccess" @cancel="goBack" />
    </el-card>

    <!-- 底部操作栏 -->
    <div class="footer-bar">
      <el-button @click="goBack">取 消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting" :disabled="!ready">提 交</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'SalesOrderAdd',
})

import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import OrderForm from './components/OrderForm.vue'
import { quotationApi } from '@/api/sales/quotation'

const router = useRouter()
const route = useRoute()
const quotationId = computed(() => Number(route.query?.quotationId) || undefined)
const ready = ref(false)
const loadError = ref('')
const initialData = ref<Record<string, any>>({})
const sourceVersion = ref('')
const omittedAttachmentCount = ref(0)
const omittedAttachmentTypes = ref<string[]>([])
const omittedNotice = computed(() => {
  const types = omittedAttachmentTypes.value.length ? `（${omittedAttachmentTypes.value.join('、')}）` : ''
  return `有 ${omittedAttachmentCount.value} 个报价专用证据未带入订单${types}；这些证据仍保留在报价单上，可按同一 trace 在报价/订单流水查看。`
})
let loadRequest = 0
watch(quotationId, async id => {
  const request = ++loadRequest
  ready.value = false; loadError.value = ''; initialData.value = {}; sourceVersion.value = ''
  omittedAttachmentCount.value = 0; omittedAttachmentTypes.value = []
  if (!id) { ready.value = true; return }
  try {
    const res = await quotationApi.getOrderDraft(id)
    if (request !== loadRequest) return
    sourceVersion.value = res.data.sourceVersion
    omittedAttachmentCount.value = Number(res.data.omittedAttachmentCount) || 0
    omittedAttachmentTypes.value = Array.isArray(res.data.omittedAttachmentTypes) ? res.data.omittedAttachmentTypes : []
    const order = res.data.order
    initialData.value = { ...order, shippingAddress: order.deliveryAddress || '', salesPersonId: order.salesManagerId,
      salesPersonName: order.salesManagerName, amountBreakdownConfirmed: true,
      items: (order.items || []).map(item => ({ ...item, specification: item.specification || '', deliveryDays: item.deliveryDays || 0 })) }
    ready.value = true
  } catch (error: any) { if (request === loadRequest) loadError.value = error?.message || '读取报价预填资料失败，请重新打开' }
}, { immediate: true })

// 表单组件引用
const orderFormRef = ref<InstanceType<typeof OrderForm> | null>(null)
const submitting = ref(false)

// 返回订单列表
const goBack = () => {
  router.push('/sales/order')
}

// 提交表单
const handleSubmit = async () => {
  if (!orderFormRef.value || submitting.value) return

  submitting.value = true
  try {
    const success = await orderFormRef.value.submitForm()
    if (success) {
      ElMessage.success('新增订单成功')
      goBack()
    }
  } catch (error) {
    console.error('新增订单失败:', error)
    ElMessage.error('新增订单失败')
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
