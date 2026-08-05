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
          <el-button type="primary" plain @click="handleExportPdf">导出PDF</el-button>
          <el-button type="success" plain @click="handleExportExcel">导出Excel</el-button>
          <span class="toolbar-tip">发送前可打印或导出报价单给客户确认</span>
        </div>

        <!-- 报价单表单（打印区域） -->
        <div id="quotation-print-area" class="quotation-form">
          <div class="qf-title">报 价 单</div>
          <div class="qf-info">
            <div class="qf-info-item"><span class="qf-label">报价单号</span>{{ info.quotationNo }}</div>
            <div class="qf-info-item"><span class="qf-label">报价日期</span>{{ info.quotationDate || '-' }}</div>
            <div class="qf-info-item"><span class="qf-label">客户名称</span>{{ info.customerName || '-' }}</div>
            <div class="qf-info-item"><span class="qf-label">有效期至</span>{{ info.validUntil || '-' }}</div>
            <div class="qf-info-item"><span class="qf-label">联系人</span>{{ contactText }}</div>
            <div class="qf-info-item"><span class="qf-label">币种</span>{{ currencyText }}</div>
            <div class="qf-info-item"><span class="qf-label">来源询价</span>{{ info.inquiryNo || '-' }}</div>
            <div class="qf-info-item"><span class="qf-label">销售负责人</span>{{ info.salesPersonName || '-' }}</div>
          </div>
          <table class="qf-items">
            <thead>
              <tr>
                <th style="width: 40px">序号</th>
                <th style="width: 90px">产品编码</th>
                <th>产品名称/规格</th>
                <th style="width: 60px">数量</th>
                <th style="width: 50px">单位</th>
                <th style="width: 90px">单价</th>
                <th style="width: 100px">金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in itemsList" :key="idx">
                <td>{{ idx + 1 }}</td>
                <td>{{ item.productCode }}</td>
                <td class="qf-spec">{{ buildSpec(item) }}</td>
                <td class="qf-num">{{ item.quantity }}</td>
                <td>{{ item.unit || '' }}</td>
                <td class="qf-num">{{ fmt(item.unitPrice) }}</td>
                <td class="qf-num">{{ fmt(item.amount) }}</td>
              </tr>
              <tr v-if="!(info.items || []).length">
                <td colspan="7" class="qf-empty">无明细</td>
              </tr>
            </tbody>
          </table>
          <div class="qf-amounts">
            <div class="qf-amount-row"><span>小计</span><span>{{ fmt(info.subtotalAmount) }}</span></div>
            <div class="qf-amount-row"><span>税率(%)</span><span>{{ info.taxRate ?? '' }}</span></div>
            <div class="qf-amount-row"><span>税额</span><span>{{ fmt(info.taxAmount) }}</span></div>
            <div class="qf-amount-row"><span>折扣</span><span>{{ fmt(info.discountAmount) }}</span></div>
            <div class="qf-amount-row qf-total"><span>合计</span><span>{{ fmt(info.finalAmount) }}</span></div>
          </div>
          <div v-if="info.remark" class="qf-remark">备注：{{ info.remark }}</div>
          <div class="qf-signs">
            <div>销售负责人：{{ info.salesPersonName || '' }}</div>
            <div>客户确认：</div>
            <div>日期：</div>
          </div>
        </div>
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

// 打印报价单（浏览器打印 → 可另存为 PDF）
const handlePrint = () => {
  window.print()
}

// 导出 PDF
const handleExportPdf = async () => {
  if (!props.quotationId) return
  const res: any = await quotationApi.exportPdf(props.quotationId)
  download(res, `${info.value?.quotationNo || props.quotationId}.pdf`)
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

// ═══ 报价单表单（打印样式） ═══
.quotation-form {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 20px 24px;
  background: #fff;
  color: #333;

  .qf-title {
    text-align: center;
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 8px;
    margin-bottom: 16px;
  }

  .qf-info {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px 24px;
    margin-bottom: 14px;
    font-size: 13px;

    .qf-info-item {
      display: flex;
      gap: 6px;

      .qf-label {
        color: #909399;
        flex-shrink: 0;
      }
    }
  }

  .qf-items {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 14px;
    font-size: 13px;

    th,
    td {
      border: 1px solid #999;
      padding: 6px 8px;
      text-align: left;
    }

    th {
      background: #f0f0f0;
      font-weight: 600;
      text-align: center;
    }

    .qf-num {
      text-align: right;
    }

    .qf-spec {
      white-space: pre-line;
    }

    .qf-empty {
      text-align: center;
      color: #999;
    }
  }

  .qf-amounts {
    width: 320px;
    margin-left: auto;
    font-size: 13px;

    .qf-amount-row {
      display: flex;
      justify-content: space-between;
      padding: 5px 10px;
      border: 1px solid #999;
      border-top: none;

      &:first-child {
        border-top: 1px solid #999;
      }

      &.qf-total {
        font-weight: 700;
        background: #f5f7fa;
      }
    }
  }

  .qf-remark {
    margin-top: 12px;
    font-size: 13px;
    color: #555;
  }

  .qf-signs {
    margin-top: 36px;
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    color: #555;
  }
}

// ═══ 打印：只打印报价单表单 ═══
@media print {
  .no-print {
    display: none !important;
  }

  :deep(.el-dialog__header),
  :deep(.el-dialog__footer) {
    display: none !important;
  }

  :deep(.el-dialog) {
    box-shadow: none !important;
    border: none !important;
  }

  .quotation-form {
    border: none;
    padding: 0;
  }
}
</style>
