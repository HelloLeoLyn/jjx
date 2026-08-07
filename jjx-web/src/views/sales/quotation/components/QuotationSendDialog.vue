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

        <!-- 报价单打印区（A4Canvas，方案B） -->
        <div class="a4-print-area no-print-margin">
          <A4Canvas :padding-mm="15">
            <!-- 公司抬头 -->
            <div class="doc-header">
              <div class="company-name">{{ companyName }}</div>
              <div class="company-contact" v-if="companyContact">{{ companyContact }}</div>
            </div>

            <!-- 单据标题 -->
            <div class="doc-title">报 价 单</div>

            <!-- 信息区 -->
            <div class="doc-info">
              <div class="info-item"><span class="info-label">报价单号</span>{{ info.quotationNo }}</div>
              <div class="info-item"><span class="info-label">报价日期</span>{{ info.quotationDate || '-' }}</div>
              <div class="info-item"><span class="info-label">客户名称</span>{{ info.customerName || '-' }}</div>
              <div class="info-item"><span class="info-label">有效期至</span>{{ info.validUntil || '-' }}</div>
              <div class="info-item"><span class="info-label">联系人</span>{{ contactText }}</div>
              <div class="info-item"><span class="info-label">币种</span>{{ currencyText }}</div>
              <div class="info-item"><span class="info-label">来源询价</span>{{ info.inquiryNo || '-' }}</div>
              <div class="info-item"><span class="info-label">销售负责人</span>{{ info.salesPersonName || '-' }}</div>
            </div>

            <!-- 明细表格 -->
            <table class="doc-items">
              <thead>
                <tr>
                  <th style="width: 6%">序号</th>
                  <th style="width: 13%">产品编码</th>
                  <th>产品名称 / 规格</th>
                  <th style="width: 8%">数量</th>
                  <th style="width: 6%">单位</th>
                  <th style="width: 12%">单价</th>
                  <th style="width: 13%">金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, idx) in itemsList" :key="idx">
                  <td class="col-center">{{ idx + 1 }}</td>
                  <td>{{ item.productCode }}</td>
                  <td class="col-spec">{{ buildSpec(item) }}</td>
                  <td class="col-right">{{ item.quantity }}</td>
                  <td class="col-center">{{ item.unit || '' }}</td>
                  <td class="col-right">{{ fmt(item.unitPrice) }}</td>
                  <td class="col-right">{{ fmt(item.amount) }}</td>
                </tr>
                <tr v-if="!itemsList.length">
                  <td colspan="7" class="col-center">无明细</td>
                </tr>
              </tbody>
            </table>

            <!-- 金额汇总 -->
            <div class="doc-amounts">
              <div class="amount-row"><span>小计</span><span>{{ fmt(info.subtotalAmount) }}</span></div>
              <div class="amount-row"><span>税率 (%)</span><span>{{ info.taxRate ?? '' }}</span></div>
              <div class="amount-row"><span>税额</span><span>{{ fmt(info.taxAmount) }}</span></div>
              <div class="amount-row"><span>折扣</span><span>{{ fmt(info.discountAmount) }}</span></div>
              <div class="amount-row amount-total"><span>合计</span><span>{{ fmt(info.finalAmount) }}</span></div>
            </div>

            <!-- 备注 -->
            <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

            <!-- 签名区 -->
            <div class="doc-signs">
              <div class="sign-item">
                <div class="sign-line">销售负责人：{{ info.salesPersonName || '' }}</div>
                <div class="sign-underline"></div>
              </div>
              <div class="sign-item">
                <div class="sign-line">客户确认：</div>
                <div class="sign-underline"></div>
              </div>
              <div class="sign-item">
                <div class="sign-line">日期：</div>
                <div class="sign-underline"></div>
              </div>
            </div>
          </A4Canvas>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Printer } from '@element-plus/icons-vue'
import { quotationApi } from '@/api/sales/quotation'
import { sysConfigApi } from '@/api/system/sysConfig'
import { download } from '@/utils/format'
import A4Canvas from '@/components/A4Canvas/index.vue'

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

// 公司抬头（读后台配置 pdf_template）
const companyName = ref('')
const companyAddress = ref('')
const companyPhone = ref('')
const companyEmail = ref('')
const companyContact = computed(() => {
  const parts: string[] = []
  if (companyAddress.value) parts.push(`地址：${companyAddress.value}`)
  if (companyPhone.value) parts.push(`电话：${companyPhone.value}`)
  if (companyEmail.value) parts.push(`邮箱：${companyEmail.value}`)
  return parts.join(' ｜ ')
})

async function loadCompanyConfig() {
  try {
    const res: any = await sysConfigApi.listByGroup('pdf_template')
    const list: any[] = res?.data || []
    const map: Record<string, string> = {}
    for (const item of list) map[item.configKey] = item.configValue || ''
    companyName.value = map.company_name || ''
    companyAddress.value = map.company_address || ''
    companyPhone.value = map.company_phone || ''
    companyEmail.value = map.company_email || ''
  } catch (e) {
    console.error('加载公司配置失败:', e)
  }
}

onMounted(() => {
  loadCompanyConfig()
})

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

/* 打印区（A4 画布内容样式） */
.a4-print-area {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
}

.doc-header {
  text-align: center;
  margin-bottom: 6px;
}

.company-name {
  font-size: 20px;
  font-weight: 700;
  color: #2b5aa7;
  letter-spacing: 2px;
}

.company-contact {
  font-size: 9px;
  color: #888;
  margin-top: 2px;
}

.doc-title {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 8px;
  margin: 14px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #2b5aa7;
}

.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 24px;
  margin-bottom: 12px;
  font-size: 11px;
}

.info-item {
  display: flex;
}

.info-label {
  width: 70px;
  color: #888;
  flex-shrink: 0;
}

.doc-items {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  margin-bottom: 10px;
}

.doc-items th {
  background: #2b5aa7;
  color: #fff;
  padding: 6px 4px;
  font-weight: 600;
  border: 1px solid #2b5aa7;
}

.doc-items td {
  border: 1px solid #dcdfe6;
  padding: 5px 4px;
}

.doc-items tr:nth-child(even) td {
  background: #f7f9fc;
}

.col-center {
  text-align: center;
}

.col-right {
  text-align: right;
}

.col-spec {
  font-size: 10px;
}

.doc-amounts {
  width: 45%;
  margin-left: auto;
  margin-bottom: 12px;
  font-size: 11px;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  padding: 3px 8px;
  border: 1px solid #dcdfe6;
}

.amount-row + .amount-row {
  border-top: none;
}

.amount-total {
  background: #2b5aa7;
  color: #fff;
  font-weight: 700;
  font-size: 13px;
}

.doc-remark {
  font-size: 10px;
  color: #555;
  margin-bottom: 20px;
}

.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  padding: 0 20px;
}

.sign-item {
  width: 30%;
  text-align: center;
  font-size: 11px;
}

.sign-line {
  padding-bottom: 4px;
}

.sign-underline {
  border-bottom: 1px solid #999;
}

// ═══ 打印：只打印报价单画布 ═══
@media print {
  .no-print {
    display: none !important;
  }

  /* 只打印报价单画布：隐藏页面其他一切（含弹窗遮罩/对话框外壳） */
  :global(body *:not(.a4-print-area):not(.a4-print-area *)) {
    visibility: hidden !important;
  }

  /* 画布区域恢复正常显示（visibility 方案保持布局，避免跳动） */
  :global(.a4-print-area),
  :global(.a4-print-area *) {
    visibility: visible !important;
  }

  /* 画布脱离弹窗滚动容器，铺满 A4 */
  :global(.el-overlay) {
    position: static !important;
    overflow: visible !important;
    background: none !important;
    padding: 0 !important;
  }

  :global(.el-dialog) {
    box-shadow: none !important;
    border: none !important;
    margin: 0 auto !important;
    max-height: none !important;
    overflow: visible !important;
    width: 210mm !important;
  }

  :global(.el-dialog__body) {
    overflow: visible !important;
    max-height: none !important;
    padding: 0 !important;
  }

  :global(.el-dialog__header),
  :global(.el-dialog__footer) {
    display: none !important;
  }

  .send-wrapper {
    min-height: 0 !important;
    background: #fff !important;
  }

  .a4-print-area {
    background: #fff !important;
    padding: 0 !important;
    overflow: visible !important;
  }
}
</style>
