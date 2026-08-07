<template>
  <el-dialog
    :title="`发送报价 - ${info?.quotationNo || ''}`"
    v-model="dialogVisible"
    width="880px"
    append-to-body
    destroy-on-close
    @open="initData"
  >
    <div class="send-wrapper" v-loading="loading">
      <template v-if="info">
        <!-- 工具栏（打印时隐藏） -->
        <div class="send-toolbar no-print">
          <el-button @click="handlePrint">
            <el-icon style="margin-right: 4px"><Printer /></el-icon>打印
          </el-button>
          <el-button type="success" plain @click="handleExportExcel">导出Excel</el-button>
          <span class="toolbar-tip">发送前可打印或导出报价单给客户确认</span>
        </div>

        <!-- 报价单信息预览（普通表单+表格，打印走独立干净页） -->
        <el-descriptions :column="2" border size="small" class="quotation-preview">
          <el-descriptions-item label="报价单号">{{ info.quotationNo }}</el-descriptions-item>
          <el-descriptions-item label="报价日期">{{ info.quotationDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ info.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">{{ info.validUntil || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ contactText }}</el-descriptions-item>
          <el-descriptions-item label="币种">{{ currencyText }}</el-descriptions-item>
          <el-descriptions-item label="来源询价">{{ info.inquiryNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销售负责人">{{ info.salesPersonName || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 明细表格 -->
        <el-table :data="itemsList" border size="small" class="quotation-items" max-height="300">
          <el-table-column label="序号" type="index" width="55" align="center" />
          <el-table-column prop="productCode" label="产品编码" width="120" />
          <el-table-column label="产品名称/规格" min-width="200">
            <template #default="{ row }">{{ buildSpec(row) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" align="right" />
          <el-table-column prop="unit" label="单位" width="60" align="center" />
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">{{ fmt(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <!-- 金额汇总 -->
        <el-descriptions :column="2" border size="small" class="quotation-total" direction="vertical">
          <el-descriptions-item label="小计">{{ fmt(info.subtotalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税率(%)">{{ info.taxRate ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ fmt(info.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="折扣">{{ fmt(info.discountAmount) }}</el-descriptions-item>
        </el-descriptions>
        <div class="quotation-final">
          <span class="final-label">合计</span>
          <span class="final-value">{{ fmt(info.finalAmount) }}</span>
        </div>

        <div v-if="info.remark" class="quotation-remark">备注：{{ info.remark }}</div>
      </template>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="sending" @click="handleSend">确认发送</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Printer } from '@element-plus/icons-vue'
import { quotationApi } from '@/api/sales/quotation'
import { download } from '@/utils/format'

interface Props {
  visible: boolean
  quotationId?: number
}

const props = defineProps<Props>()

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}

const emit = defineEmits<Emits>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const loading = ref(false)
const sending = ref(false)
const info = ref<any>(null)

// 明细列表（类型稳定，避免模板索引推断问题）
const itemsList = computed<any[]>(() => info.value?.items || [])

const contactText = computed(() => {
  if (!info.value) return '-'
  const person = info.value.contactPerson || ''
  const phone = info.value.contactPhone || ''
  if (person && phone) return `${person} ${phone}`
  return person || phone || '-'
})

const currencyText = computed(() => {
  if (!info.value) return '-'
  const cur = info.value.currency || 'CNY'
  const rate = info.value.exchangeRate
  if (rate && Number(rate) !== 1) return `${cur} (汇率 ${rate})`
  return cur
})

// 金额千分位
const fmt = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 规格描述（与后端 PDF 导出一致：尺寸×厚度 / 材质 / 颜色 / 线路 / 连接器）
const buildSpec = (item: any): string => {
  const parts: string[] = []
  const dims: string[] = []
  if (item.width != null && item.height != null) {
    dims.push(`${item.width}×${item.height}`)
    if (item.thickness != null) dims.push(String(item.thickness))
  }
  if (dims.length) parts.push(dims.join('×'))
  ;['materialType', 'color', 'circuitType', 'connectorType'].forEach((k) => {
    if (item[k]) parts.push(item[k])
  })
  const base = parts.join(' / ')
  const custom = item.customRequirements ? `\n备注:${item.customRequirements}` : ''
  return base + custom
}

const initData = async () => {
  if (!props.quotationId) return
  loading.value = true
  try {
    const res: any = await quotationApi.getInfo(props.quotationId)
    if (res.code === 200 && res.data) {
      info.value = res.data
    } else {
      ElMessage.error(res.msg || '加载报价单失败')
    }
  } catch {
    ElMessage.error('加载报价单失败')
  } finally {
    loading.value = false
  }
}

// 打印报价单：跳转到独立干净打印页（无弹窗/无侧边栏，A4Canvas 渲染）
const handlePrint = () => {
  if (!props.quotationId) return
  const url = `/print/quotation/${props.quotationId}`
  window.open(url, '_blank')
}

// 导出 Excel
const handleExportExcel = async () => {
  if (!props.quotationId) return
  const res: any = await quotationApi.exportExcel(props.quotationId)
  download(res, `${info.value?.quotationNo || props.quotationId}.xlsx`)
}

// 确认发送
const handleSend = async () => {
  if (!props.quotationId) return
  sending.value = true
  try {
    const res: any = await quotationApi.send(props.quotationId)
    if (res.code === 200) {
      ElMessage.success('发送成功')
      emit('success')
      dialogVisible.value = false
    } else {
      ElMessage.error(res.msg || '发送失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '发送失败')
  } finally {
    sending.value = false
  }
}
</script>

<style scoped lang="scss">
.send-wrapper {
  min-height: 300px;
}

.send-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;

  .toolbar-tip {
    margin-left: auto;
    font-size: 12px;
    color: #909399;
  }
}

/* 报价单信息预览 */
.quotation-preview {
  margin-bottom: 14px;
}

.quotation-items {
  margin-bottom: 14px;
}

.quotation-total {
  margin-bottom: 10px;
  max-width: 400px;
}

.quotation-final {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 400px;
  padding: 8px 12px;
  background: #2b5aa7;
  color: #fff;
  border-radius: 4px;
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 12px;
}

.quotation-remark {
  font-size: 13px;
  color: #606266;
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
}

@media print {
  .no-print {
    display: none !important;
  }
}
</style>
