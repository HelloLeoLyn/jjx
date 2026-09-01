<template>
  <div class="m-pick">
    <header class="m-header">
      <el-button link @click="router.back()">← 返回</el-button>
      <span class="m-header-title">生产领料</span>
      <el-button link type="primary" :disabled="!orderId" @click="loadData">刷新</el-button>
    </header>

    <div class="m-pick-body">
      <!-- 工单定位 -->
      <div class="m-pick-locate">
        <el-input
          v-model="orderNo"
          placeholder="扫码/输入工单号（如 WPO2608120001）"
          size="large"
          clearable
          @keyup.enter="handleLocate"
        />
        <el-button type="primary" size="large" :loading="locating" class="m-pick-locate-btn" @click="handleLocate">
          定位工单
        </el-button>
      </div>

      <template v-if="orderId">
        <div class="m-pick-card" v-if="order">
          <div class="m-pick-card-title">{{ order.productName }}</div>
          <div class="m-pick-card-meta">
            <span>{{ order.orderNo }}</span>
            <span>计划 {{ fmtQty(order.plannedQuantity) }} {{ order.productUnit || '' }}</span>
            <el-tag size="small" type="warning" v-if="insufficientCount > 0">
              {{ insufficientCount }} 项库存不足
            </el-tag>
          </div>
        </div>

        <div v-loading="loading" class="m-pick-list">
          <div class="m-section-title">领料明细（剩余可领量）</div>
          <div v-for="row in rows" :key="`${row.materialId}-${row.substitute ? 's' : 'm'}`" class="m-pick-item">
            <div class="m-pick-item-head">
              <span class="m-pick-item-name">
                {{ row.materialName }}
                <el-tag v-if="row.substitute" size="small" type="warning" effect="plain">替代</el-tag>
              </span>
              <el-tag v-if="row.insufficient" size="small" type="danger" effect="plain">库存不足</el-tag>
            </div>
            <div class="m-pick-item-meta">
              <span>{{ row.materialCode }}</span>
              <span v-if="row.specification">{{ row.specification }}</span>
            </div>
            <div class="m-pick-item-qty">
              <span>需求 {{ fmtQty(row.qtyNeeded) }} {{ row.unit }}</span>
              <span>可用 {{ fmtQty(row.available) }}</span>
              <span>剩余可领 <b>{{ fmtQty(row.remaining ?? row.qtyPick) }}</b></span>
            </div>
            <div class="m-pick-item-input">
              <span class="m-pick-item-input-label">本次领料</span>
              <el-input-number
                v-model="pickMap[row.materialId]"
                :min="0"
                :max="Number(row.remaining ?? row.qtyPick)"
                :precision="4"
                :step="1"
                style="flex: 1"
              />
              <span class="m-pick-item-unit">{{ row.unit }}</span>
            </div>
          </div>
          <el-empty v-if="!loading && !rows.length" description="无领料物料（BOM 无 buy 类明细或工单无已审批 BOM）" />

          <div v-if="rows.length" class="m-pick-submit">
            <el-button type="primary" size="large" :loading="submitting" class="m-pick-submit-btn" @click="handleSubmit">
              提交领料
            </el-button>
            <div class="m-pick-submit-tip">已领 {{ pickedCount }} 项 · 本次 {{ submitCount }} 项</div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductionOrderByCode } from '@/api/production/order'
import { outboundApi } from '@/api/inventory/outbound'
import type { PickPreviewRow, PickRemainingRow } from '@/types/inventory/outbound'
import type { ProductionOrderVO } from '@/types/production/order'

const route = useRoute()
const router = useRouter()

const orderNo = ref(String(route.query.orderNo || ''))
const orderId = ref<number | null>(null)
const order = ref<ProductionOrderVO | null>(null)
const locating = ref(false)
const loading = ref(false)
const submitting = ref(false)

const rows = ref<(PickPreviewRow & { remaining?: number })[]>([])
const pickMap = ref<Record<number, number | undefined>>({})

const insufficientCount = computed(() => rows.value.filter((r) => r.insufficient).length)
const submitCount = computed(
  () => Object.values(pickMap.value).filter((v) => Number(v) > 0).length,
)
const pickedCount = computed(() => rows.value.filter((r) => Number(r.qtyPick) > 0).length)

function fmtQty(v?: number | string | null): string {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

async function handleLocate() {
  const no = orderNo.value.trim()
  if (!no) {
    ElMessage.warning('请输入工单号')
    return
  }
  locating.value = true
  try {
    const res: any = await getProductionOrderByCode(no)
    const o = res?.data
    if (!o?.orderId) throw new Error(res?.msg || '工单不存在')
    orderId.value = Number(o.orderId)
    order.value = o
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '定位失败')
  } finally {
    locating.value = false
  }
}

async function loadData() {
  if (!orderId.value) return
  loading.value = true
  try {
    const [previewRes, remainingRes]: any = await Promise.all([
      outboundApi.pickPreview(orderId.value),
      outboundApi.pickRemaining(orderId.value),
    ])
    const preview: PickPreviewRow[] = previewRes?.data || []
    const remaining: PickRemainingRow[] = remainingRes?.data || []
    const remMap: Record<number, number> = {}
    for (const r of remaining) {
      remMap[r.materialId] = Number(r.remaining ?? 0)
    }
    rows.value = preview.map((r) => ({ ...r, remaining: remMap[r.materialId] ?? r.qtyPick }))
    pickMap.value = {}
    for (const r of rows.value) {
      pickMap.value[r.materialId] = undefined
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载领料明细失败')
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!orderId.value) return
  const items = rows.value
    .filter((r) => Number(pickMap.value[r.materialId]) > 0)
    .map((r) => ({
      materialId: r.materialId,
      materialCode: r.materialCode,
      quantity: Number(pickMap.value[r.materialId]),
    }))
  if (!items.length) {
    ElMessage.warning('请至少填写一项领料数量')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认提交领料？共 ${items.length} 项物料。\n后端将校验每项 ≤ 剩余可领量。`,
      '提交领料',
      { type: 'warning', confirmButtonText: '确认领料', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    const res: any = await outboundApi.createProductionPick(orderId.value, items)
    ElMessage.success(`领料出库单已创建（${res?.data?.outboundId || ''}）`)
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '领料失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.m-pick {
  min-height: 100vh;
  background: #f5f7fa;
}
.m-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 10;
}
.m-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-pick-body {
  padding: 12px;
}
.m-pick-locate {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
}
.m-pick-locate-btn {
  width: 100%;
  margin-top: 10px;
}
.m-pick-card {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
}
.m-pick-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}
.m-pick-card-meta {
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.m-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #909399;
  margin: 8px 0;
}
.m-pick-item {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
}
.m-pick-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.m-pick-item-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.m-pick-item-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  display: flex;
  gap: 8px;
}
.m-pick-item-qty {
  font-size: 13px;
  color: #606266;
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}
.m-pick-item-qty b {
  color: #e6a23c;
}
.m-pick-item-input {
  display: flex;
  align-items: center;
  gap: 8px;
}
.m-pick-item-input-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}
.m-pick-item-unit {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}
.m-pick-submit {
  margin-top: 12px;
  padding-bottom: 24px;
}
.m-pick-submit-btn {
  width: 100%;
}
.m-pick-submit-tip {
  text-align: center;
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}
</style>
