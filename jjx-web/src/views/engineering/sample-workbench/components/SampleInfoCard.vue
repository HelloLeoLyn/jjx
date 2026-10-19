<template>
  <!-- 顶部信息区（含附件）占高较多：改折叠面板、默认收缩（dev-20261016-055）。
       title = 样品单信息 + Round N · 单号 + 成品编号；右侧动作按钮加 .stop，点按钮不会连带展开/收起。 -->
  <el-collapse v-model="activeNames" class="wb-card info-collapse">
    <el-collapse-item name="info">
      <template #title>
        <span class="info-title">样品单信息</span>
        <span class="desc"
          >Round {{ displayRound || card.sampleRound || 1 }} · {{ card.orderNo || '' }}</span
        >
        <span v-if="card.sampleProductCode" class="desc"
          >产品编号 {{ card.sampleProductCode }}</span
        >
        <span v-if="card.productName" class="desc">{{ card.productName }}</span>
        <el-tag size="small" type="warning">{{
          SampleOrderStatusEnum.getLabel(card.sampleStatus) || '工程打样中'
        }}</el-tag>
        <span class="info-actions">
          <el-button
            size="small"
            icon="CopyDocument"
            :disabled="readonly"
            @click.stop="!readonly && $emit('historyCopy')"
            >📋 复制打样方案</el-button
          >
          <el-button size="small" @click.stop="showFiles">查看图纸 / 资料</el-button>
          <el-dropdown trigger="click" @command="onMoreCommand" @click.stop>
            <el-button size="small" @click.stop>更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="transfer" :disabled="readonly"
                  >资料转移</el-dropdown-item
                >
                <el-dropdown-item command="clear" :disabled="readonly" divided
                  >清空当前轮内容</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button link type="primary" style="margin-left: 8px" @click.stop="$emit('back')"
            >← 返回打样平台</el-button
          >
        </span>
        <!-- 右上角可发现性提示（dev-20261016-055 增补）：明确「点这里能展开面板信息」；
             处于 header 内，点击即展开/收起，无需额外事件。 -->
        <span class="info-hint">{{ isOpen ? '点击收起' : '点击展开样品单信息' }}</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="单号">{{ card.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ card.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ card.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="轮次"
          >Round {{ displayRound || card.sampleRound || 1 }}</el-descriptions-item
        >
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
        <span style="margin-left: 12px; color: #909399; font-size: 12px"
          >接单后开始记录打样过程</span
        >
      </div>

      <!-- 样品单来源以类型和单号展示；报价来源可继续查看询价/报价摘要。 -->
      <div class="source-docs">
        <div class="source-docs-label">来源单据</div>
        <div class="source-docs-row">
          <span class="source-docs-item">
            <span class="source-docs-key">来源类型</span>
            {{
              card.sourceType === 'QUOTATION'
                ? '报价单'
                : card.sourceType === 'SALES_ORDER'
                  ? '销售订单'
                  : card.sourceType === 'SAMPLE_ORDER'
                    ? '样品单'
                    : '-'
            }}
          </span>
          <span class="source-docs-item">
            <span class="source-docs-key">来源单号</span>{{ card.sourceNo || '-' }}
          </span>
          <span v-if="card.sourceType === 'QUOTATION'" class="source-docs-item">
            <span class="source-docs-key">询价单</span>
            <template v-if="card.inquiryNo">
              {{ card.inquiryNo }}
              <el-button link type="primary" size="small" @click="$emit('viewInquiry')"
                >查看</el-button
              >
            </template>
            <span v-else>-</span>
          </span>
          <span v-if="card.sourceType === 'QUOTATION'" class="source-docs-item">
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

      <!-- 图纸 / 工艺文件（2026-10-14 dev-20261014-015：改走公共附件组件 refs） -->
      <div ref="filesRef" class="eng-files">
        <div class="eng-files-label">📐 图纸 / 工艺文件</div>
        <AttachmentCenter
          v-if="card?.orderId"
          source-type="sample"
          :source-id="card.orderId"
          :trace-id="card.traceId"
          :readonly="readonly"
          view-perm="sales:sample:view"
          edit-perm="sales:sample:edit"
          drop-zone
        />
      </div>

      <!-- 往来附件（客户/销售上传：退回修改/送样登记等；2026-09-05 轻量版只读展示） -->
      <div class="eng-files">
        <div class="eng-files-label">📎 往来附件（客户/销售上传）</div>
        <div v-if="customerFileList.length > 0">
          <div
            v-for="f in customerFileList"
            :key="f.id"
            style="
              padding: 4px 0;
              display: flex;
              align-items: center;
              gap: 8px;
              border-bottom: 1px solid #f0f0f0;
            "
          >
            <el-link :href="f.url" target="_blank" type="primary" underline="never"
              >📎 {{ f.name }}</el-link
            >
            <span style="color: #909399; font-size: 12px"
              >{{ f.createBy || '' }}{{ f.time ? ' · ' + f.time : '' }}</span
            >
          </div>
        </div>
        <div v-else style="color: #999; font-size: 12px; margin-top: 6px">
          客户退回/送样等上传的附件（暂空）
        </div>
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { computed, ref, nextTick } from 'vue'
import { SampleOrderStatusEnum } from '@/enums/sales'

/**
 * 样品单信息卡（dev-20260811-008 组件化）
 * 样品信息 + 接单/拒单 + 图纸上传（图纸=样品单级，挪至此；2026-10-14 改走公共附件组件 refs）
 * 2026-10-16（dev-20261016-055）：整卡改折叠面板、默认收缩，title 带 Round / 单号 / 成品编号，
 *   右上角给「点击展开样品单信息」提示，让用户知道点这里有内容。
 */
/** 折叠面板展开项：空数组 = 默认全部收起 */
const activeNames = ref<string[]>([])
/** 面板是否已展开（用于右上角提示文案切换） */
const isOpen = computed(() => activeNames.value.includes('info'))

const props = defineProps<{
  displayRound?: number
  card: any
  doneCount: number
  planCount: number
  summary: any
  saving: boolean
  customerFileList: any[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'clear'): void
  (e: 'transfer'): void
  (e: 'historyCopy'): void
  (e: 'back'): void
  // (e: 'accept'): void
  (e: 'reject'): void
  (e: 'viewInquiry'): void
  (e: 'viewQuotation'): void
}>()
function onMoreCommand(command: string) {
  if (props.readonly) return
  if (command === 'clear') emit('clear')
  if (command === 'transfer') emit('transfer')
}
async function showFiles() {
  activeNames.value = ['info']
  await nextTick()
  filesRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' })
}
const filesRef = ref<HTMLElement>()
defineExpose({ showFiles })
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

/* 折叠面板（dev-20261016-055）：外观保持原卡片观感，高度默认收起 */
.info-collapse {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: #fff;
  padding: 0 14px;
}
.info-collapse :deep(.el-collapse-item__header) {
  border-bottom: none;
  flex-wrap: wrap;
  height: auto;
  min-height: 48px;
  gap: 6px;
}
.info-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}
.info-title {
  font-weight: 600;
}
/* 右侧动作（复制打样方案 / 返回）推到最右，不随 title 一起换行 */
.info-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
/* 右上角可发现性提示（dev-20261016-055 增补）：在 header 内，点击即展开/收起 */
.info-hint {
  margin-left: 12px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-color-primary);
  white-space: nowrap;
}
/* 提示文字与自带箭头同色，强化「这里可点」 */
.info-collapse :deep(.el-collapse-item__arrow) {
  color: var(--el-color-primary);
  margin-left: 4px;
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
