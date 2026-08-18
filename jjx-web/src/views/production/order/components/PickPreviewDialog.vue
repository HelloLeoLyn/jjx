<template>
  <el-dialog
    :model-value="modelValue"
    title="生成领料单 - 预览确认"
    width="860px"
    append-to-body
    class="pick-preview-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-loading="loading" class="preview-body">
      <el-alert
        v-if="errorMsg"
        type="error"
        :closable="false"
        show-icon
        :title="errorMsg"
        style="margin-bottom: 12px"
      />
      <!-- A4 打印页样式预览 -->
      <div class="a4-preview">
        <div class="doc-title">领 料 单（预览）</div>
        <div class="doc-info">
          <div class="info-item"><span class="info-label">工单号</span>{{ orderNo }}</div>
          <div class="info-item"><span class="info-label">产品</span>{{ productName || '-' }}</div>
          <div class="info-item"><span class="info-label">产品编码</span>{{ productCode || '-' }}</div>
          <div class="info-item"><span class="info-label">计划数量</span>{{ fmtNum(plannedQuantity) }}</div>
          <div class="info-item"><span class="info-label">物料种类</span>{{ mainRows.length }} 项</div>
        </div>
        <table class="doc-items">
          <thead>
            <tr>
              <th style="width: 5%">序号</th>
              <th style="width: 14%">物料编码</th>
              <th>物料名称</th>
              <th style="width: 10%">单位</th>
              <th style="width: 9%">需求</th>
              <th style="width: 9%">可用</th>
              <th style="width: 12%">实领</th>
              <th style="width: 10%">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in previewRows" :key="i" :class="{ 'row-sub': r.substitute, 'row-insufficient': r.insufficient }">
              <td class="col-center">{{ i + 1 }}</td>
              <td>{{ r.materialCode }}</td>
              <td>
                {{ r.materialName }}
                <el-tag v-if="r.substitute" type="warning" size="small" style="margin-left: 6px">替代 {{ r.substituteOf }}</el-tag>
              </td>
              <td class="col-center">{{ r.unit || '-' }}</td>
              <td class="col-center">{{ fmtNum(r.qtyNeeded) }}</td>
              <td class="col-center">{{ fmtNum(r.available) }}</td>
              <td class="col-center">
                <el-input-number
                  v-if="!r.substitute"
                  v-model="r.qtyPick"
                  :min="0"
                  :max="Number(r.qtyPickMax)"
                  size="small"
                  controls-position="right"
                  style="width: 96px"
                />
                <span v-else class="auto-sub">自动补足</span>
              </td>
              <td class="col-center">
                <el-tag v-if="r.substitute" type="warning" size="small">替代料</el-tag>
                <el-tag v-else-if="r.insufficient" type="danger" size="small">库存不足</el-tag>
                <el-tag v-else type="success" size="small">充足</el-tag>
              </td>
            </tr>
            <tr v-if="previewRows.length === 0 && !loading && !errorMsg">
              <td colspan="8" class="col-center">暂无领料明细</td>
            </tr>
          </tbody>
        </table>
        <div class="doc-tip">
          <span>💡 实领数量可调整（0 ~ 可用量）；主料领不足时，替代料由系统按短缺自动补足。</span>
        </div>
        <div class="doc-signs">
          <div class="sign-item"><div class="sign-line">领料人：</div><div class="sign-underline"></div></div>
          <div class="sign-item"><div class="sign-line">仓管员：</div><div class="sign-underline"></div></div>
          <div class="sign-item"><div class="sign-line">日期：</div><div class="sign-underline"></div></div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!!errorMsg" @click="handleConfirm">
        确认生成领料单
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  workOrderId: number
  orderNo: string
  productCode?: string
  productName?: string
  plannedQuantity?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  success: [outboundId: number]
}>()

const loading = ref(false)
const submitting = ref(false)
const errorMsg = ref('')
const rows = ref<any[]>([])

// 主料行（可调整）
const mainRows = computed(() => rows.value.filter((r) => !r.substitute))
// 预览行：主料（带调整上限）+ 替代料（只读）
const previewRows = computed(() =>
  rows.value.map((r) => ({
    ...r,
    qtyPickMax: r.substitute ? r.qtyPick : Number(r.qtyPick),
  })),
)

function fmtNum(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

async function loadPreview() {
  loading.value = true
  errorMsg.value = ''
  rows.value = []
  try {
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    const res: any = await materialPickApi.pickPreview(props.workOrderId)
    rows.value = (res?.data || []).map((r: any) => ({
      ...r,
      qtyPick: Number(r.qtyPick),
    }))
  } catch (e: any) {
    errorMsg.value = e?.message || '领料预览加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) loadPreview()
  },
  // 2026-08-18：immediate——组件首次挂载时 modelValue 已是 true，无变化事件，不加会漏首次加载
  { immediate: true },
)

async function handleConfirm() {
  submitting.value = true
  try {
    // 仅传有调整的主料行（数量 ≠ 默认实领）
    const adjusted = mainRows.value
      .filter((r) => Number(r.qtyPick) !== Number(r.qtyPickMax))
      .map((r) => ({ materialId: Number(r.materialId), quantity: Number(r.qtyPick) }))
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    const res: any = await materialPickApi.createFromProduction(props.workOrderId, adjusted)
    ElMessage.success(`领料单已生成（出库单 ${res?.data}）`)
    emit('update:modelValue', false)
    emit('success', res?.data)
  } catch (e: any) {
    ElMessage.error(e?.message || '生成领料单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.preview-body {
  max-height: 70vh;
  overflow-y: auto;
  padding: 4px;
}
.a4-preview {
  background: #fff;
  border: 1px solid #dcdfe6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 24px 28px;
  font-size: 13px;
  color: #303133;
}
.doc-title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 6px;
  margin-bottom: 14px;
  color: #000;
}
.doc-info {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 24px;
  margin-bottom: 10px;
  font-size: 12px;
}
.info-item .info-label {
  color: #909399;
  margin-right: 4px;
}
.doc-items {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.doc-items th,
.doc-items td {
  border: 1px solid #c0c4cc;
  padding: 4px 6px;
  text-align: left;
}
.doc-items th {
  background: #f5f7fa;
  font-weight: 600;
}
.col-center {
  text-align: center !important;
}
.row-sub {
  background: #fdf6ec;
}
.row-insufficient td {
  color: #f56c6c;
}
.auto-sub {
  color: #e6a23c;
  font-size: 12px;
}
.doc-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 32px;
}
.sign-item {
  width: 140px;
}
.sign-line {
  font-size: 12px;
  color: #303133;
}
.sign-underline {
  border-bottom: 1px solid #303133;
  height: 24px;
}
</style>
