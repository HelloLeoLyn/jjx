<template>
  <div class="stocktake-print-page">
    <!-- 工具栏（打印时隐藏） -->
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.stocktakeNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <!-- A4 画布（干净页面） -->
    <A4Canvas :padding-mm="15" v-if="info">
      <!-- 公司抬头 -->
      <div class="doc-header">
        <div class="company-name">{{ companyName }}</div>
        <div class="company-contact" v-if="companyContact">{{ companyContact }}</div>
      </div>

      <!-- 单据标题 -->
      <div class="doc-title">盘 点 单</div>

      <!-- 信息区 -->
      <div class="doc-info">
        <div class="info-item"><span class="info-label">盘点单号</span>{{ info.stocktakeNo }}</div>
        <div class="info-item"><span class="info-label">盘点类型</span>{{ typeName }}</div>
        <div class="info-item"><span class="info-label">仓库</span>{{ info.warehouseName || '-' }}</div>
        <div class="info-item"><span class="info-label">物料种类</span>{{ info.materialCount ?? itemsList.length }} 项</div>
        <div class="info-item"><span class="info-label">盘点人</span>{{ info.stocktakerName || '-' }}</div>
        <div class="info-item"><span class="info-label">监盘人</span>{{ info.supervisorName || '-' }}</div>
        <div class="info-item"><span class="info-label">计划开始</span>{{ info.planStartTime || '-' }}</div>
        <div class="info-item"><span class="info-label">计划结束</span>{{ info.planEndTime || '-' }}</div>
        <div class="info-item"><span class="info-label">单据状态</span>{{ statusName }}</div>
        <div class="info-item"><span class="info-label">创建时间</span>{{ info.createTime || '-' }}</div>
      </div>

      <!-- 汇总 -->
      <div class="doc-summary">
        <div class="summary-item">
          <span class="summary-label">账面总数量</span>
          <span class="summary-value">{{ fmtNum(info.totalSystemQuantity) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">实盘总数量</span>
          <span class="summary-value">{{ fmtNum(info.totalActualQuantity) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">差异总数量</span>
          <span class="summary-value diff">{{ fmtNum(info.totalDiffQuantity) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">差异金额</span>
          <span class="summary-value diff">{{ fmtMoney(info.totalDiffAmount) }}</span>
        </div>
      </div>

      <!-- 明细表格 -->
      <table class="doc-items">
        <thead>
          <tr>
            <th style="width: 5%">序号</th>
            <th style="width: 12%">物料编码</th>
            <th>物料名称</th>
            <th style="width: 9%">库位</th>
            <th style="width: 10%">账面数量</th>
            <th style="width: 10%">实盘数量</th>
            <th style="width: 10%">差异</th>
            <th style="width: 12%">处理</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in itemsList" :key="idx">
            <td class="col-center">{{ idx + 1 }}</td>
            <td>{{ item.materialCode }}</td>
            <td>{{ item.materialName }}</td>
            <td>{{ item.locationName || '-' }}</td>
            <td class="col-right">{{ fmtNum(item.systemQuantity) }}</td>
            <td class="col-right">{{ fmtNum(item.actualQuantity) }}</td>
            <td class="col-right" :class="{ 'diff-text': Number(item.diffQuantity) !== 0 }">{{ fmtNum(item.diffQuantity) }}</td>
            <td class="col-center">{{ adjustStatusText(item.adjustStatus) }}</td>
          </tr>
          <tr v-if="!itemsList.length">
            <td colspan="8" class="col-center">无明细</td>
          </tr>
        </tbody>
      </table>

      <!-- 备注 -->
      <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

      <!-- 签名区 -->
      <div class="doc-signs">
        <div class="sign-item">
          <div class="sign-line">盘点人：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">监盘人：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">日期：</div>
          <div class="sign-underline"></div>
        </div>
      </div>
    </A4Canvas>

    <div v-else v-loading="true" style="height: 400px"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { stocktakeApi } from '@/api/inventory/stocktake'
import { sysConfigApi } from '@/api/system/sysConfig'
import A4Canvas from '@/components/A4Canvas/index.vue'

const route = useRoute()
const router = useRouter()

const info = ref<any>(null)
const loading = ref(false)

// 公司抬头（后台配置）
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

const itemsList = computed<any[]>(() => info.value?.items || [])

const typeName = computed(() => {
  const map: Record<string, string> = { full: '全盘', partial: '抽盘', cycle: '循环盘点' }
  const t = info.value?.stocktakeType
  return t ? map[t] || t : '-'
})

const STATUS_NAMES: Record<number, string> = {
  0: '草稿', 4: '盘点中', 5: '已确认', 8: '已关闭', 9: '已取消', 11: '已处理',
}

const statusName = computed(() => {
  const s = info.value?.orderStatus
  return s !== undefined && s !== null ? STATUS_NAMES[Number(s)] || String(s) : '-'
})

const ADJUST_STATUS: Record<number, string> = {
  0: '待处理', 1: '已调整', 2: '已忽略',
}

function adjustStatusText(status?: number): string {
  if (status === undefined || status === null) return '-'
  return ADJUST_STATUS[Number(status)] || String(status)
}

const fmtNum = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN')
}

const fmtMoney = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

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

async function loadData() {
  const stocktakeId = route.params.id as string
  if (!stocktakeId) {
    ElMessage.error('缺少盘点单ID')
    return
  }
  loading.value = true
  try {
    const res: any = await stocktakeApi.getById(stocktakeId)
    if (res.code === 200 && res.data) {
      info.value = res.data
    } else {
      ElMessage.error(res.msg || '加载盘点单失败')
    }
  } catch {
    ElMessage.error('加载盘点单失败')
  } finally {
    loading.value = false
  }
}

function handlePrint() {
  window.print()
}

onMounted(async () => {
  await Promise.all([loadData(), loadCompanyConfig()])
})
</script>

<style scoped>
.stocktake-print-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}

.print-toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-tip {
  font-size: 14px;
  color: #606266;
}

/* 画布内容样式 */
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

.doc-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.summary-item {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 10px;
  text-align: center;
  background: #f7f9fc;
}

.summary-label {
  display: block;
  font-size: 10px;
  color: #888;
}

.summary-value {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #2b5aa7;
  margin-top: 2px;
}

.summary-value.diff {
  color: #e6a23c;
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

.diff-text {
  color: #e6a23c;
  font-weight: 700;
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

@media print {
  .no-print {
    display: none !important;
  }

  .stocktake-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
