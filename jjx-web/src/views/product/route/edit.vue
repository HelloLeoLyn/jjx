<template>
  <div class="product-route-edit">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>编辑工艺路线</span>
          <div>
            <el-button @click="handleBack">返回</el-button>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmit"
              >保存</el-button
            >
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
        v-loading="loading"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品Id" prop="productId">
              <el-input v-model="formData.productId" placeholder="请选择产品" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路线编码" prop="routingCode">
              <el-input v-model="formData.routingCode" placeholder="请输入路线编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="路线名称" prop="routingName">
              <el-input v-model="formData.routingName" placeholder="请输入路线名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" prop="routingVersion">
              <el-input v-model="formData.routingVersion" placeholder="请输入版本号，如 V1.0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="路线说明" prop="description">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="2"
                placeholder="请输入路线说明"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="formData.remark"
                type="textarea"
                :rows="2"
                placeholder="请输入备注"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 工序明细编辑器 -->
      <RouteItemIconEditor
        ref="routeItemEditorRef"
        :model-value="formData.items"
        :standard-processes="standardProcesses"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductRouteEdit',
})

import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { productRouteApi } from '@/api/product/routing'
import type { StandardProcessOption } from '@/types/product'
import type { ProductRouteFormData, EngineeringRoutingItemVO } from '@/types/product/routing'
import RouteItemIconEditor from './components/RouteItemIconEditor.vue'

const route = useRoute()
const router = useRouter()

const routingId = Number(route.params.routingId)

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitLoading = ref(false)
const routeItemEditorRef = ref<InstanceType<typeof RouteItemIconEditor>>()

const standardProcesses = ref<StandardProcessOption[]>([])

// 表单数据
const formData = reactive<ProductRouteFormData>({
  routingCode: '',
  routingName: '',
  productId: 0,
  productCode: '',
  productName: '',
  routingVersion: '',
  description: '',
  remark: '',
  items: [],
})

const rules = reactive<FormRules<ProductRouteFormData>>({
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  routingCode: [{ required: true, message: '请输入路线编码', trigger: 'blur' }],
  routingName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }],
  routingVersion: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
})

// 加载标准工序
const loadStandardProcesses = async () => {
  try {
    const response = await productRouteApi.getEnabledProcesses()
    standardProcesses.value = response.data || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}

// 加载工艺路线详情
const loadRouteDetail = async () => {
  if (!routingId) {
    ElMessage.error('缺少工艺路线ID')
    return
  }

  loading.value = true
  try {
    const response = await productRouteApi.getProductRouteInfo(routingId)
    const detail = response.data
    if (!detail) {
      ElMessage.error('加载工艺路线详情失败')
      return
    }

    const items = (detail.items || []).map((item: any) => ({
      itemId: item.itemId || 0,
      routingId: item.routingId || 0,
      groupId: item.groupId || undefined,
      groupOrder: item.groupOrder || 0,
      groupName: item.groupName || '',
      processId: item.processId || 0,
      processOrder: item.processOrder || 0,
      customLaborHours: item.customLaborHours || 0,
      customMachineHours: item.customMachineHours || 0,
      customProcessParams: item.customProcessParams || '',
      description: item.description || '',
      processCategory: item.processCategory || '',
    }))

    Object.assign(formData, {
      routingId: detail.routingId,
      routingCode: detail.routingCode,
      routingName: detail.routingName,
      productId: detail.productId,
      productCode: detail.productCode,
      productName: detail.productName,
      routingVersion: detail.routingVersion,
      description: detail.description,
      remark: detail.remark,
      items,
    })

    // 等待组件挂载后，通过 ref 设置 RouteItemIconEditor 的数据
    await nextTick()
    if (routeItemEditorRef.value) {
      routeItemEditorRef.value.setItems(items as any)
    }
  } catch (error) {
    console.error('加载工艺路线详情失败:', error)
    ElMessage.error('加载工艺路线详情失败')
  } finally {
    loading.value = false
  }
}

// 提交保存
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    // 从 RouteItemIconEditor 获取最新的工序数据
    const items = routeItemEditorRef.value?.getItems() || []
    if (items.length === 0) {
      ElMessage.warning('请至少添加一道工序')
      return
    }

    submitLoading.value = true
    await productRouteApi.editProductRoute(routingId, { ...formData, items })
    ElMessage.success('保存成功')
    router.push('/engineering/route')
  } catch (error) {
    console.error('保存工艺路线失败:', error)
  } finally {
    submitLoading.value = false
  }
}

// 返回
const handleBack = () => {
  router.push('/engineering/route')
}

onMounted(() => {
  loadStandardProcesses()
  loadRouteDetail()
})
</script>

<style scoped>
.product-route-edit {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
