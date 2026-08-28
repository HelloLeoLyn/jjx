<template>
  <div class="quality-print-page">
    <!-- 工具栏（打印时隐藏） -->
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.inspectionNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <!-- A4 画布（干净页面） -->
    <A4Canvas :padding-mm="15" v-if="info">
      <!-- 公司抬头 -->
      <PrintCompanyHeader variant="center" />

      <!-- 单据标题 -->
      <div class="doc-title">质 检 报 告</div>

      <!-- 信息区 -->
      <div class="doc-info">
        <div class="info-item"><span class="info-label">检验编号</span>{{ info.inspectionNo }}</div>
        <div class="info-item"><span class="info-label">检验类型</span>{{ info.inspectionTypeName || '-' }}</div>
        <div class="info-item"><span class="info-label">关联单号</span>{{ info.orderNo || '-' }}</div>
        <div class="info-item"><span class="info-label">产品/物料</span>{{ info.productName || info.materialName || '-' }}</div>
        <div class="info-item"><span class="info-label">检验员</span>{{ info.inspector || '-' }}</div>
        <div class="info-item"><span class="info-label">检验时间</span>{{ info.inspectTime || '-' }}</div>
        <div class="info-item"><span class="info-label">检验结果</span>{{ info.resultName || '-' }}</div>
        <div class="info-item"><span class="info-label">创建时间</span>{{ info.createTime || '-' }}</div>
      </div>

      <!-- 数量汇总 -->
      <div class="doc-summary">
        <div class="summary-item">
          <span class="summary-label">检验总数</span>
          <span class="summary-value">{{ fmtNum(info.totalQty) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">合格数</span>
          <span class="summary-value" style="color: #67c23a">{{ fmtNum(info.passQty) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">不合格数</span>
          <span class="summary-value" style="color: #f56c6c">{{ fmtNum(info.failQty) }}</span>
        </div>
      </div>

      <!-- 检验明细 -->
      <table class="doc-items" v-if="itemsList.length">
        <thead>
          <tr>
            <th style="width: 5%">序号</th>
            <th style="width: 20%">检验项目</th>
            <th style="width: 25%">标准</th>
            <th style="width: 20%">实测值</th>
            <th style="width: 10%">结果</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in itemsList" :key="idx">
            <td class="col-center">{{ idx + 1 }}</td>
            <td>{{ item.checkItem }}</td>
            <td>{{ item.standard || '-' }}</td>
            <td>{{ item.actualValue || '-' }}</td>
            <td class="col-center">{{ item.result || '-' }}</td>
            <td>{{ item.remark || '-' }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 缺陷描述 -->
      <div v-if="info.defectDesc" class="doc-defect">缺陷描述：{{ info.defectDesc }}</div>

      <!-- 备注 -->
      <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

      <!-- 签名区 -->
      <div class="doc-signs">
        <div class="sign-item">
          <div class="sign-line">检验员：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">审核：</div>
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
import { qualityApi } from '@/api/production/quality'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'

const route = useRoute()
const router = useRouter()

const info = ref<any>(null)
const loading = ref(false)

const itemsList = computed<any[]>(() => info.value?.items || [])

const fmtNum = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN')
}

async function loadData() {
  const inspectionId = route.params.id as string
  if (!inspectionId) {
    ElMessage.error('缺少检验ID')
    return
  }
  loading.value = true
  try {
    const res: any = await qualityApi.getById(Number(inspectionId))
    if (res.code === 200 && res.data) {
      info.value = res.data
    } else {
      ElMessage.error(res.msg || '加载质检报告失败')
    }
  } catch {
    ElMessage.error('加载质检报告失败')
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
.quality-print-page {
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

.doc-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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

.doc-defect {
  font-size: 11px;
  color: #f56c6c;
  padding: 6px 10px;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  background: #fef0f0;
  margin-bottom: 12px;
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

  .quality-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
