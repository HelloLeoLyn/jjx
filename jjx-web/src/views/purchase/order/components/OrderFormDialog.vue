<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="1200px"
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" v-loading="formLoading">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="订单号" prop="orderNo">
            <el-input v-model="form.orderNo" placeholder="系统自动生成" readonly>
              <template #append>
                <el-button :icon="Search" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商" prop="supplierName">
            <SupplierSelector
              v-model="form.supplierId"
              placeholder="请选择供应商"
              :active-only="true"
              :show-code="true"
              @change="handleSupplierChange"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="订单日期" prop="orderDate">
            <el-date-picker
              v-model="form.orderDate"
              type="date"
              placeholder="请选择订单日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="交货日期" prop="expectedDeliveryDate">
            <el-date-picker
              v-model="form.expectedDeliveryDate"
              type="date"
              placeholder="请选择交货日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="订单类型" prop="orderType">
            <el-select v-model="form.orderType" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="dict in PurchaseOrderTypeEnum.items"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="币种" prop="currency">
            <el-select v-model="form.currency" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="dict in CurrencyEnum.items"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="紧急" prop="urgentFlag">
            <el-switch v-model="form.urgentFlag" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 紧急原因 -->
      <el-form-item v-if="form.urgentFlag" label="紧急原因" prop="urgentReason">
        <el-input v-model="form.urgentReason" placeholder="请输入紧急原因" maxlength="200" />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="合同号" prop="contractNo">
            <el-input v-model="form.contractNo" placeholder="请输入合同号" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="交货方式" prop="deliveryMethod">
            <el-input v-model="form.deliveryMethod" placeholder="请输入交货方式" maxlength="100" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="交货地址" prop="deliveryAddress">
        <el-input
          v-model="form.deliveryAddress"
          placeholder="请输入交货地址"
          maxlength="200"
          :rows="2"
          type="textarea"
        />
      </el-form-item>

      <el-divider content-position="left">订单明细</el-divider>

      <el-table :data="form.items" border style="width: 100%; margin-bottom: 16px">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="180">
          <template #default="scope">
            <MaterialSelector
              v-model="scope.row.materialCode"
              value-type="materialCode"
              placeholder="搜索并选择材料"
              :options="supplierMaterials"
              @change="(a, b) => handleMaterialChange(a, b, scope.$index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="物料名称" prop="materialName" width="180">
          <template #default="scope">
            <el-input v-model="scope.row.materialName" placeholder="自动填充" readonly />
          </template>
        </el-table-column>
        <el-table-column label="规格型号" prop="materialSpec" width="120">
          <template #default="scope">
            <el-input v-model="scope.row.materialSpec" placeholder="自动填充" readonly />
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="70">
          <template #default="scope">
            <el-input v-model="scope.row.unit" placeholder="自动填充" readonly />
          </template>
        </el-table-column>
        <el-table-column label="数量" prop="quantity" width="100">
          <template #default="scope">
            <el-input
              type="number"
              v-model.number="scope.row.quantity"
              :min="0"
              :precision="2"
              :step="1"
              style="width: 100%"
              @change="calculateRowTotal(scope.$index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="单价" prop="unitPrice" width="100">
          <template #default="scope">
            <el-input
              type="number"
              v-model.number="scope.row.unitPrice"
              :min="0"
              :precision="2"
              :step="0.01"
              style="width: 100%"
              @change="calculateRowTotal(scope.$index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="税率(%)" prop="taxRate" width="90">
          <template #default="scope">
            <el-input
              type="number"
              v-model.number="scope.row.taxRate"
              :min="0"
              :max="100"
              :precision="2"
              style="width: 100%"
              @change="calculateRowTotal(scope.$index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额" prop="amount" width="100">
          <template #default="scope">
            <span>{{ scope.row.amount?.toFixed(2) || '0.00' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="inspectionRemark">
          <template #default="scope">
            <el-input v-model="scope.row.inspectionRemark" placeholder="备注" maxlength="200" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="danger"
              icon="Delete"
              @click="removeItem(scope.$index)"
              :disabled="form.items.length <= 1"
            />
          </template>
        </el-table-column>
      </el-table>

      <el-row>
        <el-col :span="12">
          <el-button type="primary" plain icon="Plus" @click="addItem">添加行</el-button>
        </el-col>
        <el-col :span="12" style="text-align: right">
          <span style="font-size: 16px; font-weight: bold">
            合计金额：{{ totalAmount.toFixed(2) }}
          </span>
        </el-col>
      </el-row>

      <el-divider />

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          placeholder="请输入备注"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确 定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { PurchaseOrderTypeEnum, CurrencyEnum } from '@/enums/purchase'
import { materialApi } from '@/api/inventory/material'
import { addOrder, updateOrder, generateOrderNo, getOrder } from '@/api/purchase/order'
import type { InventoryMaterial } from '@/types/inventory/material'
import SupplierSelector from '@/components/Selector/SupplierSelector.vue'
import MaterialSelector from '@/components/Selector/MaterialSelector.vue'
import { Search } from '@element-plus/icons-vue'

// 订单明细项
interface OrderItem {
  itemId?: string
  materialId?: number
  materialCode: string
  materialName: string
  materialSpec?: string
  unit: string
  quantity: number
  unitPrice: number
  taxRate: number
  amount: number
  inspectionRemark?: string
}

// 订单表单
interface OrderForm {
  orderId?: number
  orderNo: string
  supplierId?: string
  supplierName: string
  orderDate: string
  expectedDeliveryDate: string
  currency: string
  orderType: number
  deliveryMethod: string
  contractNo: string
  deliveryAddress: string
  urgentFlag: boolean
  urgentReason: string
  remark: string
  items: OrderItem[]
}

const props = defineProps<{
  visible: boolean
  orderId?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const formLoading = ref(false)
const submitting = ref(false)

// 当前供应商对应的物料列表
const supplierMaterials = ref<InventoryMaterial[]>([])

const defaultForm: OrderForm = {
  orderNo: '',
  supplierId: undefined,
  supplierName: '',
  orderDate: '',
  expectedDeliveryDate: '',
  currency: 'CNY',
  orderType: 0,
  deliveryMethod: '',
  contractNo: '',
  deliveryAddress: '',
  urgentFlag: false,
  urgentReason: '',
  remark: '',
  items: [
    {
      materialCode: '',
      materialName: '',
      materialSpec: '',
      unit: '',
      quantity: 0,
      unitPrice: 0,
      taxRate: 13,
      amount: 0,
      inspectionRemark: '',
    },
  ],
}

const form = reactive<OrderForm>({ ...defaultForm })

const title = computed(() => (props.orderId ? '修改采购订单' : '新增采购订单'))

const totalAmount = computed(() => {
  return form.items.reduce((sum, item) => sum + (item.amount || 0), 0)
})

const rules = reactive<FormRules>({
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  orderDate: [{ required: true, message: '请选择订单日期', trigger: 'change' }],
  expectedDeliveryDate: [{ required: true, message: '请选择交货日期', trigger: 'change' }],
  orderType: [{ required: true, message: '请选择订单类型', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
})

// 监听 visible 变化
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      await initForm()
    }
  }
)

// 初始化表单
const initForm = async () => {
  formLoading.value = true
  try {
    // 重置表单
    Object.assign(form, JSON.parse(JSON.stringify(defaultForm)))
    supplierMaterials.value = []

    if (props.orderId) {
      // 编辑模式：加载订单数据
      const response = await getOrder(Number(props.orderId))
      const data = response.data
      if (data) {
        form.orderId = Number(data.orderId)
        form.orderNo = data.orderNo
        form.supplierId = data.supplierId
        form.supplierName = data.supplierName
        form.orderDate = data.orderDate
        form.expectedDeliveryDate = data.expectedDeliveryDate
        form.currency = data.currency
        form.orderType = data.orderType
        form.deliveryMethod = data.deliveryMethod || ''
        form.contractNo = data.contractNo || ''
        form.deliveryAddress = data.deliveryAddress || ''
        form.urgentFlag = data.urgentFlag
        form.urgentReason = data.urgentReason || ''
        form.items = data.items?.length
          ? data.items.map((item: any) => ({
              itemId: item.itemId,
              materialId: item.materialId,
              materialCode: item.materialCode,
              materialName: item.materialName,
              materialSpec: item.materialSpec || '',
              unit: item.unit,
              quantity: item.quantity,
              unitPrice: item.unitPrice,
              taxRate: item.taxRate ?? 13,
              amount: item.amount,
              inspectionRemark: item.inspectionRemark || '',
            }))
          : [...defaultForm.items]

        // 编辑模式也加载供应商物料
        if (data.supplierId) {
          await loadSupplierMaterials(data.supplierId)
        }
      }
    } else {
      getOrderNo()
      // 设置默认日期
      const today = new Date()
      form.orderDate = today.toISOString().split('T')[0]
      const nextWeek = new Date(today.getTime() + 7 * 24 * 60 * 60 * 1000)
      form.expectedDeliveryDate = nextWeek.toISOString().split('T')[0]
    }
  } catch (error) {
    console.error('初始化表单失败:', error)
    ElMessage.error('初始化表单失败')
  } finally {
    formLoading.value = false
  }
}

const getOrderNo = async () => {
  try {
    const response = await generateOrderNo()
    form.orderNo = response.data || ''
  } catch {
    form.orderNo = ''
  }
}

// 根据供应商加载物料列表
const loadSupplierMaterials = async (supplierId: string) => {
  try {
    const res = await materialApi.list({
      supplierId: Number(supplierId),
      pageNum: 1,
      pageSize: 999,
    })
    if (res.code === 200 && res.data) {
      supplierMaterials.value = res.data
    }
  } catch {
    supplierMaterials.value = []
  }
}

// 供应商选择变化
const handleSupplierChange = (supplier: any) => {
  const supplierId = supplier?.supplierId
  if (supplierId) {
    loadSupplierMaterials(String(supplierId))
    form.supplierName = supplier.supplierName
  } else {
    supplierMaterials.value = []
  }
}

// 物料选择变化
const handleMaterialChange = (materialCode: string, material: any, index: number) => {
  if (!material) return

  // 检查是否已在其他行中选择了该物料（排除当前行）
  const duplicate = form.items.some((item, i) => i !== index && item.materialCode === materialCode)
  if (duplicate) {
    ElMessage.warning(`物料 "${material.materialName}" (${materialCode}) 已在明细中，请勿重复添加`)
    // 清空当前行的物料选择
    form.items[index].materialCode = ''
    form.items[index].materialName = ''
    form.items[index].materialSpec = ''
    form.items[index].unit = ''
    return
  }

  form.items[index].materialId = material.materialId
  form.items[index].materialName = material.materialName || ''
  form.items[index].materialSpec = material.specification || ''
  form.items[index].unit = material.unit || ''
}

// 计算行金额
const calculateRowTotal = (index: number) => {
  const item = form.items[index]
  const quantity = item.quantity || 0
  const unitPrice = item.unitPrice || 0
  item.amount = quantity * unitPrice
}

// 添加行
const addItem = () => {
  form.items.push({
    materialCode: '',
    materialName: '',
    materialSpec: '',
    unit: '',
    quantity: 0,
    unitPrice: 0,
    taxRate: 13,
    amount: 0,
    inspectionRemark: '',
  })
}

// 删除行
const removeItem = (index: number) => {
  if (form.items.length > 1) {
    form.items.splice(index, 1)
  }
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 验证明细
  const hasInvalidItem = form.items.some(
    (item) => !item.materialCode || !item.quantity || item.quantity <= 0
  )
  if (hasInvalidItem) {
    ElMessage.warning('请完善订单明细信息')
    return
  }

  submitting.value = true
  try {
    // 计算订单金额
    const orderAmount = form.items.reduce((sum, item) => sum + (item.amount || 0), 0)
    const orderTax = form.items.reduce((sum, item) => {
      const taxRate = (item.taxRate || 0) / 100
      return sum + (item.amount || 0) * taxRate
    }, 0)

    const submitData = {
      orderId: form.orderId,
      orderNo: form.orderNo,
      supplierId: form.supplierId ? Number(form.supplierId) : undefined,
      supplierName: form.supplierName,
      orderDate: form.orderDate,
      expectedDeliveryDate: form.expectedDeliveryDate,
      currency: form.currency,
      orderType: form.orderType,
      deliveryMethod: form.deliveryMethod,
      contractNo: form.contractNo,
      deliveryAddress: form.deliveryAddress,
      urgentFlag: form.urgentFlag,
      urgentReason: form.urgentReason,
      remark: form.remark,
      orderAmount,
      orderTax,
      orderTotalAmount: orderAmount + orderTax,
      items: form.items.map((item) => ({
        itemId: item.itemId ? Number(item.itemId) : undefined,
        materialId: item.materialId,
        materialCode: item.materialCode,
        materialName: item.materialName,
        materialSpec: item.materialSpec,
        unit: item.unit,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        taxRate: item.taxRate,
        amount: item.amount,
        inspectionRemark: item.inspectionRemark,
      })),
    }

    if (props.orderId) {
      await updateOrder(submitData as any)
      ElMessage.success('修改采购订单成功')
    } else {
      await addOrder(submitData as any)
      ElMessage.success('新增采购订单成功')
    }

    emit('success')
    handleClose()
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}

// 关闭
const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
// 表格内输入框紧凑样式 - 紧贴单元格边框
.el-table {
  :deep(.el-input) {
    .el-input__wrapper {
      box-shadow: none !important;
      border-radius: 0;
      padding: 0 2px;
      background: transparent;
    }
    .el-input__inner {
      padding: 0;
      border: none;
      height: 28px;
    }
  }

  // 数字输入框
  :deep(.el-input-number) {
    .el-input__wrapper {
      padding: 0 2px;
    }
    .el-input-number__decrease,
    .el-input-number__increase {
      display: none;
    }
  }

  // 下拉选择框
  :deep(.el-select) {
    .el-select__wrapper {
      box-shadow: none !important;
      border-radius: 0;
      padding: 0 4px;
      min-height: 28px;
    }
  }

  // 表格单元格内边距压缩
  :deep(.el-table__cell) {
    padding: 4px 2px;
  }
}
</style>
