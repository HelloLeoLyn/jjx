<template>
  <!-- 添加或修改产品对话框 -->
  <el-dialog :title="title" v-model="dialogVisible" width="800px" append-to-body>
    <el-form ref="productFormRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="产品编码" prop="productCode">
            <el-input
              v-model="form.productCode"
              placeholder="系统自动生成"
              maxlength="50"
              :readonly="true"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品名称" prop="productName">
            <el-input v-model="form.productName" placeholder="请输入产品名称" maxlength="100" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="产品分类" prop="categoryId">
            <el-select
              v-model="form.categoryId"
              placeholder="请选择产品分类"
              style="width: 100%"
              @change="handleCategoryChange"
            >
              <el-option
                v-for="item in categoryOptions"
                :key="item.categoryId"
                :label="item.categoryName"
                :value="item.categoryId"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位" prop="unit">
            <el-input v-model="form.unit" placeholder="请输入单位" maxlength="20" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="BOM选择" prop="currentBomId">
            <el-select
              v-model="form.currentBomId"
              placeholder="请选择BOM（可选）"
              style="width: 100%"
              clearable
              filterable
              @change="handleBomChange"
            >
              <el-option
                v-for="item in bomOptions"
                :key="item.bomId"
                :label="`${item.bomCode}_${item.bomVersion} - ${item.bomName || ''}`"
                :value="item.bomId"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工艺路线选择" prop="currentRouteId">
            <el-select
              v-model="form.currentRouteId"
              placeholder="请选择工艺路线（可选）"
              style="width: 100%"
              clearable
              filterable
              @change="handleRouteChange"
            >
              <el-option
                v-for="item in routeOptions"
                :key="item.routingId"
                :label="`${item.routingCode}_${item.routingVersion} - ${item.routingName || ''}`"
                :value="item.routingId"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="产品描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              placeholder="请输入产品描述"
              :rows="3"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="form.remark"
              type="textarea"
              placeholder="请输入备注"
              :rows="2"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="规格参数" prop="specJson">
            <ProductSpecForm
              v-model="form.specJson"
              v-model:use-default="form.useDefaultSpec"
              @change="handleSpecChange"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, nextTick } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { ProductFormData, ProductVo } from '@/types/product'
import type { ProductBomVO } from '@/types/product/bom'
import type { ProductRoutingVO } from '@/types/product/routing'
import { getApprovedBomList } from '@/api/product'
import ProductSpecForm from './ProductSpecForm.vue'

// Props定义
interface Props {
  visible: boolean
  title: string
  formData: ProductFormData
  categoryOptions: Array<{ categoryId: number; categoryName: string; categoryCode: string }>
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  title: '产品表单',
  formData: () => ({
    productId: undefined,
    productCode: '',
    productName: '',
    categoryId: undefined,
    categoryName: '',
    categoryCode: '',
    specification: '',
    unit: 'PCS',
    weight: 0,
    volume: 0,
    material: '',
    color: '',
    brand: '',
    model: '',
    description: '',
    productStatus: 0,
    approvalStatus: 'pending',
    remark: '',
    attachments: [],
    specJson: '',
    useDefaultSpec: false,
    currentBomId: undefined,
    currentRouteId: undefined,
    bomName: '',
    bomCode: '',
    bomVersion: '',
    routeName: '',
    routeCode: '',
    routeVersion: '',
    leadTime: 0,
  }),
  categoryOptions: () => [],
})

// Emits定义
interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'submit', formData: ProductFormData): void
  (e: 'cancel'): void
}

const emit = defineEmits<Emits>()

// 计算属性：控制对话框显示
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

// 表单引用
const productFormRef = ref<FormInstance>()

// 表单数据（使用props传入的数据副本）
const form = reactive<ProductFormData>({ ...props.formData })

// BOM和Route选项
const bomOptions = ref<ProductBomVO[]>([])
const routeOptions = ref<ProductRoutingVO[]>([])

// 监听props.formData变化，更新本地表单数据
watch(
  () => props.formData,
  (newFormData) => {
    Object.assign(form, newFormData)
    // 当表单数据变化时，重新加载BOM和Route选项
    if (newFormData.productId) {
      loadBomAndRouteOptions(newFormData.productId)
    }
  },
  { deep: true }
)

// 监听对话框打开，加载BOM和Route选项
watch(
  () => props.visible,
  (newVal) => {
    if (newVal && form.productId) {
      loadBomAndRouteOptions(form.productId)
    } else if (newVal && !form.productId) {
      // 新增模式，清空选项
      bomOptions.value = []
      routeOptions.value = []
    }
  }
)

// 加载BOM和Route选项
const loadBomAndRouteOptions = async (productId: number) => {
  try {
    const [bomRes, routeRes] = await Promise.all([
      getApprovedBomList(productId),
      getApprovedBomList(productId),
    ])
    bomOptions.value = (bomRes as any).data?.data || (bomRes as any).data || []
    routeOptions.value = (routeRes as any).data?.data || (routeRes as any).data || []
  } catch (error) {
    console.error('加载BOM/Route选项失败:', error)
    bomOptions.value = []
    routeOptions.value = []
  }
}

// 表单验证规则
const rules = reactive<FormRules>({
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择产品分类', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
})

// 处理分类变化
const handleCategoryChange = (categoryId: number) => {
  const selectedCategory = props.categoryOptions.find((item) => item.categoryId === categoryId)
  if (selectedCategory) {
    form.categoryName = selectedCategory.categoryName
    form.categoryCode = selectedCategory.categoryCode
  }
}

// 处理BOM选择变化
const handleBomChange = (bomId: number | undefined) => {
  if (bomId) {
    const selected = bomOptions.value.find((item) => item.bomId === bomId)
    if (selected) {
      form.bomName = selected.bomName || ''
      form.bomCode = selected.bomCode || ''
      form.bomVersion = selected.bomVersion || ''
    }
  } else {
    form.bomName = ''
    form.bomCode = ''
    form.bomVersion = ''
  }
}

// 处理Route选择变化
const handleRouteChange = (routeId: number | undefined) => {
  if (routeId) {
    const selected = routeOptions.value.find((item) => item.routingId === routeId)
    if (selected) {
      form.routeName = selected.routingName || ''
      form.routeCode = selected.routingCode || ''
      form.routeVersion = selected.routingVersion || ''
    }
  } else {
    form.routeName = ''
    form.routeCode = ''
    form.routeVersion = ''
  }
}

// 处理规格参数变化
const handleSpecChange = (specItems: any[]) => {
  // 这里可以添加额外的处理逻辑
  console.log('规格参数发生变化:', specItems)
}

// 提交表单
const submitForm = () => {
  if (!productFormRef.value) return

  productFormRef.value.validate((valid) => {
    if (valid) {
      emit('submit', { ...form })
    }
  })
}

// 取消按钮
const cancel = () => {
  emit('cancel')
}

// 暴露方法给父组件
defineExpose({
  resetForm: () => {
    if (productFormRef.value) {
      productFormRef.value.resetFields()
    }
  },
})
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}
</style>
