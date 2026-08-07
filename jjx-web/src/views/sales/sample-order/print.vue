<template>
  <div class="sample-print-page">
    <!-- 工具栏（打印时隐藏） -->
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.orderNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <!-- A4 画布（干净页面） -->
    <A4Canvas :padding-mm="15" v-if="info">
      <!-- 公司抬头 -->
      <div class="doc-header">
        <div class="company-head-row">
          <img v-if="companyLogo" :src="companyLogo" class="company-logo" alt="logo" />
          <div class="company-head-text">
            <div class="company-name">{{ companyName }}</div>
            <div class="company-contact" v-if="companyContact">{{ companyContact }}</div>
          </div>
        </div>
        <div class="company-extra" v-if="companyTaxNo || companyBank || companyAccount || companyLegal">
          <span v-if="companyTaxNo">税号：{{ companyTaxNo }}</span>
          <span v-if="companyLegal">法人：{{ companyLegal }}</span>
          <span v-if="companyBank">开户行：{{ companyBank }}</span>
          <span v-if="companyAccount">账号：{{ companyAccount }}</span>
        </div>
      </div>

      <!-- 单据标题 -->
      <div class="doc-title">样 品 单</div>

      <!-- 信息区 -->
      <div class="doc-info">
        <div class="info-item"><span class="info-label">样品单号</span>{{ info.orderNo }}</div>
        <div class="info-item"><span class="info-label">样品状态</span>{{ statusName }}</div>
        <div class="info-item"><span class="info-label">客户名称</span>{{ info.customerName || '-' }}</div>
        <div class="info-item"><span class="info-label">联系人</span>{{ info.contactPerson || '-' }}</div>
        <div class="info-item"><span class="info-label">来源报价</span>{{ info.quotationNo || '-' }}</div>
        <div class="info-item"><span class="info-label">迭代轮次</span>Round {{ info.sampleRound || 1 }}</div>
        <div class="info-item"><span class="info-label">打样数量</span>{{ info.sampleQty || '-' }}</div>
        <div class="info-item"><span class="info-label">送样日期</span>{{ info.sampleSendDate || '-' }}</div>
        <div class="info-item"><span class="info-label">快递单号</span>{{ info.sampleTrackingNo || '-' }}</div>
        <div class="info-item"><span class="info-label">客户确认</span>{{ info.sampleConfirmDate ? info.sampleConfirmDate + (info.sampleClientName ? ' / ' + info.sampleClientName : '') : '-' }}</div>
      </div>

      <!-- 工程备注 -->
      <div v-if="info.engineeringNote" class="doc-engineering">
        <div class="eng-title">工艺参数 / 工程备注</div>
        <div class="eng-content">{{ info.engineeringNote }}</div>
      </div>

      <!-- 备注 -->
      <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

      <!-- 签名区 -->
      <div class="doc-signs">
        <div class="sign-item">
          <div class="sign-line">制单人：</div>
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

    <div v-else v-loading="true" style="height: 400px"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { sysConfigApi } from '@/api/system/sysConfig'
import { SampleOrderStatusEnum } from '@/enums/sales'
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
const companyTaxNo = ref('')
const companyBank = ref('')
const companyAccount = ref('')
const companyLegal = ref('')
const companyWebsite = ref('')
const companyLogo = ref('')
const companyContact = computed(() => {
  const parts: string[] = []
  if (companyAddress.value) parts.push(`地址：${companyAddress.value}`)
  if (companyPhone.value) parts.push(`电话：${companyPhone.value}`)
  if (companyEmail.value) parts.push(`邮箱：${companyEmail.value}`)
  if (companyWebsite.value) parts.push(`官网：${companyWebsite.value}`)
  return parts.join(' ｜ ')
})

// 样品状态中文
const statusName = computed(() => {
  const s = info.value?.sampleStatus
  if (s === undefined || s === null) return '-'
  return SampleOrderStatusEnum.getLabel(Number(s)) || String(s)
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
    companyTaxNo.value = map.company_tax_no || ''
    companyBank.value = map.company_bank || ''
    companyAccount.value = map.company_account || ''
    companyLegal.value = map.company_legal || ''
    companyWebsite.value = map.company_website || ''
    companyLogo.value = map.company_logo || ''
  } catch (e) {
    console.error('加载公司配置失败:', e)
  }
}

async function loadData() {
  const orderId = route.params.id as string
  if (!orderId) {
    ElMessage.error('缺少样品单ID')
    return
  }
  loading.value = true
  try {
    const res: any = await sampleOrderApi.getInfo(Number(orderId))
    if (res.code === 200 && res.data) {
      info.value = res.data
    } else {
      ElMessage.error(res.msg || '加载样品单失败')
    }
  } catch {
    ElMessage.error('加载样品单失败')
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
.sample-print-page {
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

.company-head-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.company-logo {
  max-height: 44px;
  max-width: 120px;
  object-fit: contain;
}

.company-head-text {
  text-align: center;
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

.company-extra {
  font-size: 9px;
  color: #777;
  margin-top: 3px;
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 4px 16px;
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

.doc-engineering {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  margin-bottom: 12px;
  background: #f7f9fc;
}

.eng-title {
  background: #2b5aa7;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 5px 10px;
}

.eng-content {
  font-size: 11px;
  color: #555;
  padding: 8px 10px;
  white-space: pre-wrap;
  line-height: 1.6;
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

  .sample-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
