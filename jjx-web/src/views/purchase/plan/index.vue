<template>
  <div class="purchase-plan">
    <!-- 页面标题 -->
    <div style="font-size: 16px; font-weight: 600; margin-bottom: 12px">
      采购计划工作台
      <span style="font-size: 12px; color: #909399; font-weight: 400; margin-left: 8px">
        从预警/建议加载物料，动态生成采购订单（DEV-664）
      </span>
    </div>

    <!-- 操作栏 -->
    <el-card class="operation-card" shadow="never">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Warning" @click="loadSuggestions" :loading="loadingSuggestions">
            从预警加载
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="showAddMaterial = true">添加物料</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Refresh" @click="clearPlan">清空</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Download" :disabled="planRows.length === 0" @click="handleExport">导出 Excel</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Printer" @click="handlePrintPlan">打印计划</el-button>
        </el-col>
        <el-col :span="1.5" style="float: right">
          <el-button type="primary" icon="Check" :disabled="planRows.length === 0" @click="handleConfirmPlan">
            确认计划 → 生成采购订单
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 计划行表格 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="planRows" border style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="130" />
        <el-table-column label="物料名称" prop="materialName" min-width="160" show-overflow-tooltip />
        <el-table-column label="当前库存" prop="currentStock" width="100" align="right" />
        <el-table-column label="建议量" prop="suggestQuantity" width="100" align="right" />
        <el-table-column label="采购数量" width="130" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" size="small" controls-position="right" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="来源" prop="reason" min-width="160" show-overflow-tooltip />
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.priority === 'urgent' ? 'danger' : row.priority === 'high' ? 'warning' : 'info'" size="small">
              {{ row.priority === 'urgent' ? '紧急' : row.priority === 'high' ? '高' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeRow($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="planRows.length === 0" description="暂无计划物料，点击「从预警加载」或「添加物料」" :image-size="80" />
    </el-card>

    <!-- 添加物料对话框 -->
    <el-dialog v-model="showAddMaterial" title="添加物料（不限缺库存）" width="640px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="物料">
          <el-select
            v-model="addMaterialId"
            filterable
            remote
            :remote-method="searchMaterials"
            placeholder="输入物料编码/名称搜索"
            style="width: 100%"
            :loading="materialSearching"
          >
            <el-option
              v-for="m in materialOptions"
              :key="m.materialId"
              :label="`${m.materialCode} - ${m.materialName}`"
              :value="m.materialId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="addQuantity" :min="1" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMaterial = false">取消</el-button>
        <el-button type="primary" @click="confirmAddMaterial">加入计划</el-button>
      </template>
    </el-dialog>

    <!-- 确认计划对话框：选供应商 -->
    <el-dialog v-model="showConfirmDialog" title="确认计划 → 生成采购订单" width="520px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="供应商">
          <el-select v-model="confirmSupplierId" filterable placeholder="选择供应商" style="width: 100%">
            <el-option
              v-for="s in suppliers"
              :key="s.supplierId"
              :label="s.supplierName"
              :value="s.supplierId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物料数">
          <span>{{ selectedRows.length }} 个物料，合计 {{ selectedTotalQty }} 件</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConfirmDialog = false">取消</el-button>
        <el-button type="primary" :loading="confirming" @click="doConfirmPlan">生成采购订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'PurchasePlan',
})

import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { getPlanSuggestions, confirmPlan, addOrder, inTransit as orderInTransit } from '@/api/purchase/order'
import { alertApi } from '@/api/inventory/alert'
import { listSupplier } from '@/api/purchase/supplier'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'

interface PlanRow {
  materialId: number
  materialCode: string
  materialName: string
  unit?: string
  currentStock: number
  suggestQuantity: number
  quantity: number
  reason: string
  priority: string
  sourceAlertId?: number
}

const planRows = ref<PlanRow[]>([])
const loadingSuggestions = ref(false)
const showAddMaterial = ref(false)
const addMaterialId = ref<number | null>(null)
const addQuantity = ref(1)
const materialOptions = ref<InventoryMaterial[]>([])
const materialSearching = ref(false)
const suppliers = ref<any[]>([])
const showConfirmDialog = ref(false)
const confirmSupplierId = ref<number | null>(null)
const confirming = ref(false)

const selectedRows = ref<PlanRow[]>([])
const selectedTotalQty = computed(() => selectedRows.value.reduce((s, r) => s + (r.quantity || 0), 0))

const handlePrintPlan = () => window.open('/purchase/plan/print', '_blank')

// 勾选变化（DEV：确认计划按勾选行生成订单）
const handleSelectionChange = (rows: PlanRow[]) => {
  selectedRows.value = rows
}

// 从预警/建议加载
const loadSuggestions = async () => {
  loadingSuggestions.value = true
  try {
    const res: any = await getPlanSuggestions()
    const suggestions: any[] = res.data || []
    if (suggestions.length === 0) {
      ElMessage.info('当前无采购建议（无低库存/缺料预警）')
      return
    }
    // 合并：已存在的物料跳过，新的加入
    let added = 0
    for (const s of suggestions) {
      const exist = planRows.value.find((r) => r.materialCode === s.materialCode)
      if (exist) continue
      planRows.value.push({
        materialId: s.materialId || 0,
        materialCode: s.materialCode,
        materialName: s.materialName,
        unit: s.unit,
        currentStock: s.currentStock || 0,
        suggestQuantity: s.suggestQuantity || 0,
        quantity: Math.max(1, Math.round(s.suggestQuantity || 0)),
        reason: s.reason || '采购建议',
        priority: s.priority || 'normal',
        sourceAlertId: s.sourceAlertId,
      })
      added++
    }
    ElMessage.success(`加载完成：新增 ${added} 个物料`)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载采购建议失败')
  } finally {
    loadingSuggestions.value = false
  }
}

// 物料搜索（添加物料对话框）
const searchMaterials = async (keyword: string) => {
  if (!keyword) return
  materialSearching.value = true
  try {
    const res = await materialApi.search({ keyword, pageNum: 1, pageSize: 20 } as any)
    materialOptions.value = res.data?.records || []
  } catch {
    materialOptions.value = []
  } finally {
    materialSearching.value = false
  }
}

// 加入计划（2026-08-18：添加前查在途，已有未完成采购单则警告防重复下单）
const confirmAddMaterial = async () => {
  if (!addMaterialId.value) {
    ElMessage.warning('请选择物料')
    return
  }
  const mat = materialOptions.value.find((m) => m.materialId === addMaterialId.value)
  if (!mat) return
  // 在途检查
  try {
    const res: any = await orderInTransit([mat.materialId])
    const inTransit = res?.data?.[mat.materialId]
    if (inTransit && Number(inTransit) > 0) {
      ElMessage.warning(`该物料已有未完成采购单（在途 ${inTransit}），请确认是否需要重复采购`)
    }
  } catch {
    /* 在途查询失败不阻断 */
  }
  const exist = planRows.value.find((r) => r.materialCode === mat.materialCode)
  if (exist) {
    exist.quantity += addQuantity.value
    ElMessage.success(`物料已存在，数量累加至 ${exist.quantity}`)
  } else {
    planRows.value.push({
      materialId: mat.materialId,
      materialCode: mat.materialCode,
      materialName: mat.materialName,
      unit: mat.unit,
      currentStock: 0,
      suggestQuantity: 0,
      quantity: addQuantity.value,
      reason: '手动添加',
      priority: 'normal',
    })
  }
  showAddMaterial.value = false
  addMaterialId.value = null
  addQuantity.value = 1
}

const removeRow = (index: number) => {
  const row = planRows.value[index]
  // 2026-08-18：移除仅作用于列表行，预警仍保留（下次加载会回来），提示避免误解
  if (row?.sourceAlertId) {
    ElMessageBox.confirm('该行来自预警，移除后预警仍保留在待办中，下次加载会再次出现。确定移除吗？', '提示', { type: 'warning' })
      .then(() => {
        planRows.value.splice(index, 1)
      })
      .catch(() => {})
  } else {
    planRows.value.splice(index, 1)
  }
}

const clearPlan = () => {
  ElMessageBox.confirm('确定清空计划列表吗？', '提示', { type: 'warning' })
    .then(() => {
      planRows.value = []
    })
    .catch(() => {})
}

// 确认计划（按勾选行生成采购订单）
const handleConfirmPlan = () => {
  if (planRows.value.length === 0) {
    ElMessage.warning('计划列表为空')
    return
  }
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要确认的物料')
    return
  }
  loadSuppliers()
  showConfirmDialog.value = true
}

const loadSuppliers = async () => {
  try {
    const res: any = await listSupplier({ pageNum: 1, pageSize: 100 } as any)
    suppliers.value = res.data?.records || res.data?.list || []
  } catch {
    suppliers.value = []
  }
}

const doConfirmPlan = async () => {
  if (!confirmSupplierId.value) {
    ElMessage.warning('请选择供应商')
    return
  }
  const supplier = suppliers.value.find((s) => s.supplierId === confirmSupplierId.value)
  confirming.value = true
  try {
    const toConfirm = selectedRows.value
    // 勾选物料合并为一张采购订单（同一供应商）
    const orderNo = `PO-${Date.now()}`
    await addOrder({
      orderNo,
      supplierId: Number(supplier.supplierId),
      supplierName: supplier.supplierName,
      orderDate: new Date().toISOString().slice(0, 10),
      expectedDeliveryDate: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10),
      currency: 'CNY',
      orderType: '0',
      urgentFlag: toConfirm.some((r) => r.priority === 'urgent'),
      approvalStatus: '1',
      receiptStatus: '0',
      paymentStatus: '0',
      orderAmount: 0,
      orderTax: 0,
      orderTotalAmount: 0,
      items: toConfirm.map((row) => ({
        materialId: Number(row.materialId),
        materialCode: row.materialCode,
        materialName: row.materialName,
        unit: row.unit || 'PCS',
        quantity: row.quantity,
        unitPrice: 0,
        amount: 0,
      })),
      saveAsPlan: false,
    } as any)
    ElMessage.success(`已生成采购订单（${toConfirm.length} 个物料），请到「采购订单」列表提交审批`)
    // 预警闭环：勾选行来源预警 + 勾选物料全部未处理预警 一并回写（2026-08-18 P0-A/P1-A：按物料回写，修手动行/低库存复燃）
    const alertIds = toConfirm.map((r) => r.sourceAlertId).filter((id): id is number => !!id)
    const materialIds = toConfirm.map((r) => r.materialId).filter((id): id is number => !!id)
    try {
      await alertApi.batchProcess({ alertIds, materialIds, relatedOrderNo: orderNo, remark: '采购计划确认' })
    } catch (e) {
      // 2026-08-18：回写失败不再静默，明确提示（采购单已生成，预警需人工处理）
      console.warn('回写预警状态失败', e)
      ElMessage.warning('采购单已生成，但预警状态回写失败，请到库存预警页手动处理')
    }
    // 仅移除已确认的勾选行，未勾选保留
    const confirmedCodes = new Set(toConfirm.map((r) => r.materialCode))
    planRows.value = planRows.value.filter((r) => !confirmedCodes.has(r.materialCode))
    selectedRows.value = []
    showConfirmDialog.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '生成采购订单失败')
  } finally {
    confirming.value = false
  }
}

onMounted(() => {
  loadSuggestions()
  // DEV-998：从预警页跳转带参（materialId/alertId）→ 预填物料，溯源预警来源
  const route = useRoute()
  const qMaterialId = route.query.materialId
  const qAlertId = route.query.alertId
  if (qMaterialId) {
    prefetchMaterial(Number(qMaterialId), qAlertId ? Number(qAlertId) : undefined)
  }
})

// 按物料ID查详情并加入计划（DEV-998：预警溯源预填）
async function prefetchMaterial(materialId: number, alertId?: number) {
  try {
    const res: any = await materialApi.getInfo(String(materialId))
    const mat = res?.data
    if (!mat) {
      ElMessage.warning('未找到物料，请手动添加')
      return
    }
    const exist = planRows.value.find((r) => r.materialCode === mat.materialCode)
    if (exist) {
      ElMessage.info('物料已在计划中')
      return
    }
    planRows.value.push({
      materialId: mat.materialId,
      materialCode: mat.materialCode,
      materialName: mat.materialName,
      unit: mat.unit,
      currentStock: 0,
      suggestQuantity: 0,
      quantity: 1,
      reason: alertId ? `来自预警#${alertId}` : '预警跳转预填',
      priority: 'normal',
      sourceAlertId: alertId,
    })
    ElMessage.success(`已预填物料：${mat.materialName}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '预填物料失败')
  }
}

// 导出当前计划为 Excel（DEV-720）
const handleExport = () => {
  if (planRows.value.length === 0) {
    ElMessage.warning('暂无计划数据可导出')
    return
  }
  const rows = planRows.value.map((r) => ({
    物料编码: r.materialCode,
    物料名称: r.materialName,
    当前库存: r.currentStock,
    建议量: r.suggestQuantity,
    采购数量: r.quantity,
    单位: r.unit || '',
    来源: r.reason,
    优先级: r.priority === 'urgent' ? '紧急' : r.priority === 'high' ? '高' : '普通',
  }))
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '采购计划')
  XLSX.writeFile(wb, `采购计划_${new Date().toISOString().slice(0, 10)}.xlsx`)
  ElMessage.success(`已导出 ${rows.length} 行计划数据`)
}
</script>
