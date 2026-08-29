<template>
  <el-card class="wb-card" shadow="never">
    <template #header>
      <span style="font-weight: 600">样品单信息</span>
      <span class="desc">Round {{ card.sampleRound || 1 }} · {{ card.orderNo || '' }}</span>
      <span style="float: right">
        <el-button
          size="small"
          icon="CopyDocument"
          :disabled="readonly"
          @click="$emit('historyCopy')"
          >📋 从历史打样复制</el-button
        >
        <el-button link type="primary" style="margin-left: 8px" @click="$emit('back')"
          >← 返回打样平台</el-button
        >
      </span>
    </template>
    <el-descriptions :column="3" border size="small">
      <el-descriptions-item label="单号">{{ card.orderNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户">{{ card.customerName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="产品">{{ card.productName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="轮次">Round {{ card.sampleRound || 1 }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag size="small" type="warning">工程打样中</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="接单人">{{
        card.engineeringAcceptor || '-'
      }}</el-descriptions-item>
    </el-descriptions>
    <div class="summary-inline">
      <div class="summary-item">
        <div class="summary-num">{{ doneCount }} / {{ planCount }}</div>
        <div class="summary-label">工序完成</div>
      </div>
      <div class="summary-item">
        <div class="summary-num">{{ summary.totalHours ?? '-' }}</div>
        <div class="summary-label">总工时(小时)</div>
      </div>
      <div class="summary-item">
        <div class="summary-num">¥{{ summary.materialCost ?? '-' }}</div>
        <div class="summary-label">材料成本(估算)</div>
      </div>
      <div class="summary-tip">工时=已完成工序耗时之和；材料成本=材料用量×标准单价</div>
    </div>
    <!-- <div v-if="!card.engineeringAcceptor" class="accept-row">
      <el-button type="primary" @click="$emit('accept')" :loading="saving">✅ 工程接单</el-button>
      <el-button type="danger" plain style="margin-left:8px" @click="$emit('reject')">✋ 工程拒单</el-button>
    </div> -->
    <div class="accept-row">
      <el-tag type="success">接单人员：{{ card.engineeringAcceptor || '-' }}</el-tag>
      <span style="margin-left: 12px; color: #909399; font-size: 12px">接单后开始记录打样过程</span>
    </div>

    <!-- 来源单据（工作台第一步优化：询价单/报价单展示 + 查看入口，弹窗查看不离开工作台） -->
    <div class="source-docs">
      <div class="source-docs-label">来源单据</div>
      <div class="source-docs-row">
        <span class="source-docs-item">
          <span class="source-docs-key">询价单</span>
          <template v-if="card.inquiryNo">
            {{ card.inquiryNo }}
            <el-button link type="primary" size="small" @click="$emit('viewInquiry')"
              >查看</el-button
            >
          </template>
          <span v-else>-</span>
        </span>
        <span class="source-docs-item">
          <span class="source-docs-key">报价单</span>
          <template v-if="card.quotationNo">
            {{ card.quotationNo }}
            <el-button link type="primary" size="small" @click="$emit('viewQuotation')"
              >查看</el-button
            >
          </template>
          <span v-else>-</span>
        </span>
      </div>
    </div>

    <!-- 图纸 / 工艺文件（2026-08-11 挪到样品信息卡：图纸=样品单级资料） -->
    <div class="eng-files">
      <div class="eng-files-label">📐 图纸 / 工艺文件</div>
      <el-upload
        :disabled="readonly"
        :http-request="onUpload"
        :on-remove="onRemove"
        :file-list="engFileList"
        :before-upload="beforeUpload"
        list-type="text"
        multiple
      >
        <el-button type="primary" size="small" :disabled="readonly">📄 上传图纸/文件</el-button>
      </el-upload>
      <div v-if="engFileList.length > 0" style="margin-top: 8px">
        <div
          v-for="f in engFileList"
          :key="f.uid || f.name"
          style="
            padding: 4px 0;
            display: flex;
            align-items: center;
            gap: 8px;
            border-bottom: 1px solid #f0f0f0;
          "
        >
          <el-link v-if="f.url" :href="f.url" target="_blank" type="primary" underline="never"
            >📎 {{ f.name }}</el-link
          >
          <span v-else>{{ f.name }} <el-tag size="small" type="warning">待上传</el-tag></span>
        </div>
      </div>
      <div v-else style="color: #999; font-size: 12px; margin-top: 6px">
        菲林图 / 丝印图 / 模切图 / 规格书（≤10MB）
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { UploadProps } from 'element-plus'

/**
 * 样品单信息卡（dev-20260811-008 组件化）
 * 样品信息 + 接单/拒单 + 图纸上传（图纸=样品单级，挪至此）
 */
defineProps<{
  card: any
  doneCount: number
  planCount: number
  summary: any
  saving: boolean
  engFileList: any[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'historyCopy'): void
  (e: 'back'): void
  // (e: 'accept'): void
  (e: 'reject'): void
  (e: 'upload', options: any): void
  (e: 'remove', file: any): void
  (e: 'viewInquiry'): void
  (e: 'viewQuotation'): void
}>()

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (file.size > 10 * 1024 * 1024) {
    return false
  }
  return true
}
function onUpload(options: any): Promise<any> {
  emit('upload', options)
  return Promise.resolve()
}
function onRemove(file: any) {
  emit('remove', file)
}

defineExpose({ beforeUpload })
</script>

<style scoped>
.wb-card {
  margin-bottom: 14px;
}

.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}

.summary-inline {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
  display: flex;
  align-items: center;
  gap: 36px;
  flex-wrap: wrap;
}
.summary-item {
  text-align: center;
}
.summary-num {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}
.summary-item:nth-child(2) .summary-num {
  color: #67c23a;
}
.summary-item:nth-child(3) .summary-num {
  color: #e6a23c;
}
.summary-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.summary-tip {
  font-size: 12px;
  color: #999;
}

.accept-row {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
}

/* 来源单据（工作台第一步：辅助上下文，紧凑行式，不喧宾夺主） */
.source-docs {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}
.source-docs-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.source-docs-row {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #606266;
}
.source-docs-key {
  color: #909399;
  margin-right: 6px;
}

/* 图纸区（挪到样品信息卡） */
.eng-files {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}
.eng-files-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
</style>
