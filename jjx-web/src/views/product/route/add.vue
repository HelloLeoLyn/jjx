<template>
  <div class="product-route-add">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>新增工艺路线</span>
          <div>
            <el-button @click="handleBack">返回</el-button>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmit"
              >保存</el-button
            >
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <ProductSelector
                v-model="selectedProduct"
                value-type="object"
                @change="handleProductChange"
              />
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
          <el-col :span="12">
            <el-form-item label="路线说明" prop="description">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="2"
                placeholder="请输入路线说明"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
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
  name: 'EngineeringRoutingAdd',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { productRouteApi } from '@/api/product/routing'
import type { StandardProcessOption, ProductItem } from '@/types/product'
import type {
  ProductRouteFormData,
  EngineeringRoutingItemDTO,
  EngineeringRoutingItemVO,
} from '@/types/product/routing'
import RouteItemIconEditor from './components/RouteItemIconEditor.vue'
import ProductSelector from '@/components/Selector/ProductSelector.vue'

const router = useRouter()

const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const routeItemEditorRef = ref<InstanceType<typeof RouteItemIconEditor>>()

const standardProcesses = ref<StandardProcessOption[]>([])

// 选中的产品对象
const selectedProduct = ref<ProductItem | null>(null)

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

// 产品选择变更
const handleProductChange = (val: any, product: ProductItem | null) => {
  if (product) {
    formData.productId = product.productId
    formData.productCode = product.productCode
    formData.productName = product.productName
    // 自动填充默认值
    formData.routingCode = product.productCode + '-ROUTING'
    formData.routingName = product.productCode + '工艺路线'
    formData.routingVersion = 'V1.0'
  } else {
    formData.productId = 0
    formData.productCode = ''
    formData.productName = ''
    formData.routingCode = ''
    formData.routingName = ''
  }
}

// 加载标准工序
const loadStandardProcesses = async () => {
  try {
    const response = await productRouteApi.getEnabledProcesses()
    standardProcesses.value = response.data || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}

// 提交保存
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    // 从 RouteItemEditor 获取最新的工序数据
    const items = routeItemEditorRef.value?.getItems() || []
    if (items.length === 0) {
      ElMessage.warning('请至少添加一道工序')
      return
    }

    // 将 VO 转换为 DTO（只保留后端需要的字段）
    const itemDTOs: EngineeringRoutingItemDTO[] = items.map((item: EngineeringRoutingItemVO) => ({
      itemId: item.itemId,
      routingId: item.routingId,
      groupId: item.groupId,
      groupOrder: item.groupOrder,
      groupName: item.groupName,
      processId: item.processId,
      processOrder: item.processOrder,
      customLaborHours: item.customLaborHours,
      customMachineHours: item.customMachineHours,
      customProcessParams: item.customProcessParams,
      description: item.description,
      processCategory: item.processCategory,
      majorCategory: item.majorCategory,
    }))

    submitLoading.value = true
    await productRouteApi.addProductRoute({
      routingId: formData.routingId,
      routingCode: formData.routingCode,
      routingName: formData.routingName,
      productId: formData.productId,
      productCode: formData.productCode,
      productName: formData.productName,
      routingVersion: formData.routingVersion,
      description: formData.description,
      remark: formData.remark,
      items: itemDTOs as any,
    })
    ElMessage.success('新增成功')
    router.push('/engineering/route')
  } catch (error) {
    console.error('新增工艺路线失败:', error)
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
})
</script>

<style scoped>
.product-route-add {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
