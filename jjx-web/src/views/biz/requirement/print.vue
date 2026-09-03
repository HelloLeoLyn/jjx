<template>
  <div class="ecn-print-page">
    <!-- 工具栏（打印时隐藏） -->
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.requirementNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" :disabled="!info" @click="handlePrint"
        >打印</el-button
      >
    </div>

    <!-- A4 画布 -->
    <A4Canvas v-if="info" :padding-mm="15">
      <PrintCompanyHeader variant="center" />

      <!-- 单据标题 -->
      <div class="doc-title">工 程 变 更 通 知</div>
      <div class="doc-subtitle">编号：JJX-QR-030（{{ info.requirementNo }}）</div>

      <!-- 信息区（单号已显示在标题副行，不重复；4列布局：变更日期/版本/品名料号/机种编号） -->
      <table class="doc-info">
        <tr>
          <td class="label">变更日期</td>
          <td>
            {{
              info.applyTime
                ? parseDate(info.applyTime)
                : info.createTime
                  ? parseDate(info.createTime)
                  : '-'
            }}
          </td>
          <td class="label">版 本</td>
          <td>{{ info.versionAfter || info.versionBefore || '-' }}</td>
        </tr>
        <tr>
          <td class="label">品名/料号</td>
          <td>{{ info.bizNo || info.title || '-' }}</td>

          <td class="label">机种编号</td>
          <td>
            {{ info.bizType ? info.bizType + ':' + (info.bizNo || '-') : info.title || '-' }}
          </td>
        </tr>
      </table>

      <!-- 变更内容 -->
      <div class="section-title">变更内容</div>
      <div class="content-box">{{ info.description || info.title }}</div>

      <!-- 变更前后对照 -->
      <table class="doc-items">
        <thead>
          <tr>
            <th style="width: 20%">项目</th>
            <th style="width: 40%">变更前</th>
            <th style="width: 40%">变更后</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td class="center">版本</td>
            <td class="center">{{ info.versionBefore || '-' }}</td>
            <td class="center">{{ info.versionAfter || '-' }}</td>
          </tr>
          <tr>
            <td class="center">变更类型</td>
            <td class="center" colspan="2">{{ changeTypeText }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 切入决策 -->
      <div class="decision-row">
        <span
          >切入方式：<b>{{
            info.cutoverMode === 'IMMEDIATE'
              ? '立即切入'
              : info.cutoverMode === 'BATCH'
                ? '按批切换'
                : '________'
          }}</b></span
        >
        <span style="margin-left: 40px"
          >是否重打样：<b>{{ info.needResample === 1 ? '是' : '否' }}</b></span
        >
      </div>

      <!-- 会签区 -->
      <div class="section-title">会签</div>
      <table class="doc-sign">
        <tr>
          <td>工程部</td>
          <td>制造部</td>
          <td>采购/仓库</td>
          <td>品管部</td>
        </tr>
        <tr class="sign-blank">
          <td></td>
          <td></td>
          <td></td>
          <td></td>
        </tr>
      </table>

      <!-- 制单/批准 -->
      <table class="doc-footer">
        <tr>
          <td>制单：{{ info.applicantName || '________' }}</td>
          <td>批准：{{ info.reviewerName || '________' }}</td>
          <td>日期：________</td>
        </tr>
      </table>
    </A4Canvas>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RequirementEcnPrint' })

import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRequirement } from '@/api/biz/requirement'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'

const route = useRoute()
const router = useRouter()
const info = ref<any>(null)

const changeTypeText = computed(() => {
  const map: Record<string, string> = {
    DESIGN: '设计改版',
    PROCESS: '工艺调整',
    MATERIAL: '材料变更',
    DRAWING: '图纸更新',
    OTHER: '其他',
  }
  return (info.value?.changeType && map[info.value.changeType]) || info.value?.changeType || '-'
})

function parseDate(v: string): string {
  if (!v) return '-'
  return String(v).slice(0, 10)
}

function handlePrint() {
  window.print()
}

onMounted(async () => {
  const id = route.params.id
  if (!id) return
  try {
    const res: any = await getRequirement(Number(id))
    info.value = res.data
  } catch {
    info.value = null
  }
})
</script>

<style scoped>
.print-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-tip {
  color: #909399;
  font-size: 13px;
}
.doc-title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 6px;
  margin: 6px 0 2px;
}
.doc-subtitle {
  text-align: center;
  color: #666;
  font-size: 11px;
  margin-bottom: 12px;
}
.doc-info {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 12px;
}
.doc-info td {
  border: 1px solid #999;
  padding: 5px 8px;
  font-size: 13px;
}
.doc-info .label {
  background: #f5f5f5;
  width: 120px;
  text-align: center;
  font-weight: 600;
}
.section-title {
  font-weight: 600;
  font-size: 13px;
  margin: 10px 0 6px;
  border-left: 3px solid #333;
  padding-left: 6px;
}
.content-box {
  border: 1px solid #999;
  min-height: 60px;
  padding: 8px;
  font-size: 13px;
}
.doc-items {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}
.doc-items th,
.doc-items td {
  border: 1px solid #999;
  padding: 5px 8px;
  font-size: 13px;
}
.doc-items th {
  background: #f5f5f5;
}
.center {
  text-align: center;
}
.decision-row {
  margin: 10px 0;
  font-size: 13px;
}
.doc-sign {
  width: 100%;
  border-collapse: collapse;
  margin: 6px 0;
}
.doc-sign td {
  border: 1px solid #999;
  width: 25%;
  text-align: center;
  padding: 5px;
  font-size: 13px;
}
.sign-blank {
  height: 70px;
}
.doc-footer {
  width: 100%;
  margin-top: 16px;
  font-size: 13px;
}
.doc-footer td {
  padding: 4px 0;
}
@media print {
  .no-print {
    display: none !important;
  }
  body {
    background: #fff;
  }
}
</style>
