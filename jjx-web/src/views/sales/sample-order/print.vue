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
      <PrintCompanyHeader variant="center" />

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
import { SampleOrderStatusEnum } from '@/enums/sales'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'

const route = useRoute()
const router = useRouter()

const info = ref<any>(null)
const loading = ref(false)

// 样品状态中文
const statusName = computed(() => {
  const s = info.value?.sampleStatus
  if (s === undefined || s === null) return '-'
  return SampleOrderStatusEnum.getLabel(Number(s)) || String(s)
})

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
  await loadData()
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
