<template>
  <div class="outbound-create">
    <!-- 基本信息 -->
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      class="base-form"
    >
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="出库类型" prop="outboundType">
            <el-select v-model="formData.outboundType" placeholder="请选择出库类型" style="width: 100%">
              <el-option label="销售发货" value="sales" />
              <el-option label="生产领料" value="production" />
              <el-option label="退货出库" value="return" />
              <el-option label="调拨出库" value="transfer" />
              <el-option label="其他出库" value="other" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="仓库" prop="warehouseId">
            <el-select
              v-model="formData.warehouseId"
              placeholder="请选择仓库"
              style="width: 100%"
              @change="handleWarehouseChange"
            >
              <el-option
                v-for="warehouse in warehouseList"
                :key="warehouse.warehouseId"
                :label="warehouse.warehouseName"
                :value="warehouse.warehouseId"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="来源单号">
            <el-input v-model="formData.sourceNo" placeholder="来源单号（选填）" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="来源类型">
            <el-select v-model="formData.sourceType" placeholder="请选择来源类型" style="width: 100%">
              <el-option label="销售订单" value="sales_order" />
              <el-option label="生产工单" value="work_order" />
              <el-option label="调拨单" value="transfer_order" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="备注">
            <el-input v-model="formData.remark" placeholder="请输入备注" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 物料明细 -->
    <div class="item-section">
      <div class="section-header">
        <h3>出库明细</h3>
        <div class="section-actions">
          <el-button type="primary" @click="handleAddItem" size="small">
            <el-icon><Plus /></el-icon>添加物料
          </el-button>
        </div>
      </div>

      <el-table :data="formData.items" border style="width: 100%">
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="物料" min-width="220">
          <template #default="{ row, $index }">
            <MaterialSelector
              v-model="row.materialId"
              value-type="materialId"
              placeholder="搜索并选择材料"
              @change="(val: any, material: any) => handleMaterialChange(row, material)"
            />
          </template>
        </el-table-column>
        <el-table-column label="物料编码" width="130">
          <template #default="{ row }">
            <el-input v-model="row.materialCode" placeholder="编码" readonly />
          </template>
        </el-table-column>
        <el-table-column label="物料名称" width="150">
          <template #default="{ row }">
            <el-input v-model="row.materialName" placeholder="名称" readonly />
          </template>
        </el-table-column>
        <el-table-column label="规格型号" width="120">
          <template #default="{ row }">
            <el-input v-model="row.specification" placeholder="规格" readonly />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="70" align="center">
          <template #default="{ row }">
            <el-input v-model="row.unit" placeholder="单位" readonly />
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="120">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="批次号" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120" align="right">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="0.001"
              :precision="3"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="库位" width="130">
          <template #default="{ row }">
            <el-select v-model="row.locationId" placeholder="请选择库位" style="width: 100%">
              <el-option
                v-for="location in locationList"
                :key="location.locationId"
                :label="location.locationCode"
                :value="location.locationId"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              @click="handleRemoveItem($index)"
              :disabled="formData.items.length <= 1"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 汇总信息 -->
      <div class="summary-section">
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="summary-item">
              <span class="label">物料种类：</span>
              <span class="value">{{ itemCount }}</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-item">
              <span class="label">出库总数量：</span>
              <span class="value">{{ formatNumber(totalQuantity) }}</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-item">
              <span class="label">出库总金额：</span>
              <span class="value">¥ {{ formatCurrency(totalAmount) }}</span>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <div class="form-actions">
      <el-button @click="router.back()">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ isEdit ? '保存修改' : '创建出库单' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { outboundApi } from '@/api/inventory/outbound'
import { warehouseApi } from '@/api/inventory/warehouse'
import { locationApi } from '@/api/inventory/location'
import { formatCurrency, formatNumber } from '@/utils/format'
import MaterialSelector from '@/components/Selector/MaterialSelector.vue'
import type { InventoryWarehouse } from '@/types/inventory/warehouse'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 编辑模式：路由带 :id
const isEdit = computed(() => !!route.params.id)
const outboundId = computed(() => String(route.params.id || ''))

interface OutboundItemRow {
  materialId: string
  materialCode: string
  materialName: string
  specification: string
  unit: string
  batchNo: string
  quantity: number
  unitPrice: number
  locationId: string
  remark: string
}

const formData = reactive({
  outboundType: 'sales',
  warehouseId: '',
  sourceType: '',
  sourceNo: '',
  remark: '',
  items: [
    {
      materialId: '',
      materialCode: '',
      materialName: '',
      specification: '',
      unit: '',
      batchNo: '',
      quantity: 1,
      unitPrice: 0,
      locationId: '',
      remark: '',
    } as OutboundItemRow,
  ],
})

const formRules = {
  outboundType: [{ required: true, message: '请选择出库类型', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
}

const warehouseList = ref<InventoryWarehouse[]>([])
const locationList = ref<any[]>([])

const itemCount = computed(() => formData.items.length)
const totalQuantity = computed(() => formData.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0))
const totalAmount = computed(() =>
  formData.items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0),
)

onMounted(async () => {
  await loadWarehouses()
  if (isEdit.value) {
    await loadDetail()
  }
})

// 编辑模式：加载单头+明细回显
const loadDetail = async () => {
  try {
    const res: any = await outboundApi.getById(outboundId.value)
    if (res.code === 200 || res.code === 0) {
      const d = res.data || {}
      formData.outboundType = d.outboundType || 'sales'
      formData.warehouseId = d.warehouseId ? String(d.warehouseId) : ''
      formData.sourceType = d.sourceType || ''
      formData.sourceNo = d.sourceNo || ''
      formData.remark = d.remark || ''
      if (d.warehouseId) await loadLocations(String(d.warehouseId))
      const items: any[] = d.items || []
      if (items.length > 0) {
        formData.items = items.map((it) => ({
          materialId: String(it.materialId ?? ''),
          materialCode: it.materialCode || '',
          materialName: it.materialName || '',
          specification: it.specification || '',
          unit: it.unit || '',
          batchNo: it.batchNo || '',
          quantity: Number(it.quantity ?? 1),
          unitPrice: Number(it.unitPrice ?? 0),
          locationId: it.locationId ? String(it.locationId) : '',
          remark: it.remark || '',
        }))
      }
    } else {
      ElMessage.error(res.msg || '加载出库单失败')
    }
  } catch (error) {
    console.error('加载出库单失败:', error)
    ElMessage.error('加载出库单失败')
  }
}

// 加载库位列表（抽出来供回显复用）
const loadLocations = async (warehouseId: string) => {
  try {
    const res = await locationApi.getByWarehouse(Number(warehouseId))
    locationList.value = res.data || []
  } catch (error) {
    console.error('加载库位列表失败:', error)
    ElMessage.error('加载库位列表失败')
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const res = await warehouseApi.list({ current: 1, pageSize: 100 })
    warehouseList.value = res.data?.records || []
  } catch (error) {
    console.error('加载仓库列表失败:', error)
    ElMessage.error('加载仓库列表失败')
  }
}

// 仓库变化：加载库位列表
const handleWarehouseChange = async (warehouseId: string) => {
  formData.items.forEach((item) => {
    item.locationId = ''
  })
  locationList.value = []

  if (!warehouseId) return
  await loadLocations(warehouseId)
}

// 选择物料后回填
const handleMaterialChange = (row: OutboundItemRow, material: any) => {
  if (!material) return
  row.materialId = String(material.materialId ?? row.materialId)
  row.materialCode = material.materialCode || ''
  row.materialName = material.materialName || ''
  row.specification = material.specification || ''
  row.unit = material.unit || ''
}

// 添加物料行
const handleAddItem = () => {
  formData.items.push({
    materialId: '',
    materialCode: '',
    materialName: '',
    specification: '',
    unit: '',
    batchNo: '',
    quantity: 1,
    unitPrice: 0,
    locationId: '',
    remark: '',
  })
}

// 删除物料行
const handleRemoveItem = (index: number) => {
  if (formData.items.length > 1) {
    formData.items.splice(index, 1)
  }
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (formData.items.length === 0) {
    ElMessage.warning('请至少添加一条物料明细')
    return
  }
  for (const item of formData.items) {
    if (!item.materialId) {
      ElMessage.warning('请选择物料')
      return
    }
    if (!item.quantity || item.quantity <= 0) {
      ElMessage.warning('物料数量必须大于0')
      return
    }
  }

  ElMessageBox.confirm('确认创建出库单吗？', '提示', { type: 'warning' })
    .then(async () => {
      loading.value = true
      try {
        const payload: any = {
          outboundId: isEdit.value ? outboundId.value : undefined,
          outboundType: formData.outboundType,
          warehouseId: formData.warehouseId,
          sourceType: formData.sourceType || undefined,
          sourceNo: formData.sourceNo || undefined,
          remark: formData.remark || undefined,
          items: formData.items.map((item) => ({
            materialId: item.materialId,
            materialCode: item.materialCode,
            materialName: item.materialName,
            specification: item.specification,
            unit: item.unit,
            batchNo: item.batchNo || undefined,
            quantity: Number(item.quantity),
            unitPrice: Number(item.unitPrice || 0),
            locationId: item.locationId || undefined,
            remark: item.remark || undefined,
          })),
        }
        let res: any
        if (isEdit.value) {
          res = await outboundApi.update(payload)
        } else {
          res = await outboundApi.create(payload)
        }
        ElMessage.success(isEdit.value ? '出库单已更新' : '出库单创建成功')
        router.push('/inventory/outbound')
      } catch (error) {
        console.error('创建出库单失败:', error)
        ElMessage.error('创建出库单失败')
      } finally {
        loading.value = false
      }
    })
    .catch(() => {})
}
</script>

<style scoped>
.outbound-create {
  padding: 20px;
}

.base-form {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.item-section {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.summary-section {
  margin-top: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
}

.summary-item {
  font-size: 14px;
}

.summary-item .label {
  color: #909399;
}

.summary-item .value {
  font-weight: 600;
  color: #303133;
}

.form-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  background: #fff;
  padding: 16px 20px;
  border-radius: 8px;
}
</style>
