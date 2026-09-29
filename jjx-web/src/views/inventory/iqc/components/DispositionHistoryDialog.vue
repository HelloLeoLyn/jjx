<template>
  <el-dialog
    :model-value="visible"
    :title="`${materialLabel} · 处置历史`"
    width="1000px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="dialog-tip">
      只列这一行材料的处置单（按来料明细维度，不与其他材料混合）；数量均为本次动作口径。
    </div>
    <el-table v-loading="loading" :data="rows" border size="small">
      <el-table-column prop="dispositionNo" label="处置单号" width="180" />
      <el-table-column label="类型" width="130">
        <template #default="{ row }">
          <el-tag :type="IqcQuarantineActionEnum.getTagProps(row.action).type">
            {{ IqcQuarantineActionEnum.getLabel(row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数量口径" width="130">
        <template #default="{ row }">本次处置量：{{ num(row.quantity) }}</template>
      </el-table-column>
      <el-table-column label="复检关系" min-width="200">
        <template #default="{ row }">
          <template v-if="row.action === 'REWORK'">
            <div>原批：{{ row.batchNo || '-' }}</div>
            <span class="muted">复检批：{{ row.childBatchNo || '待生成' }}</span>
            <div class="emphasis">该批只针对 {{ num(row.quantity) }} 件</div>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="IqcDispositionOrderStatusEnum.getTagProps(row.status).type">
            {{ IqcDispositionOrderStatusEnum.getLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="165" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <template
            v-if="row.action === 'SCRAP' && row.status === IqcDispositionOrderStatus.PENDING_APPROVAL"
          >
            <el-button v-if="canDispose" link type="primary" @click="emit('approve', row, true)"
              >通过</el-button
            >
            <el-button v-if="canDispose" link type="danger" @click="emit('approve', row, false)"
              >驳回</el-button
            >
            <span v-if="!canDispose" class="muted">待品质主管审批</span>
          </template>
          <template v-else-if="row.action === 'REWORK'">
            <el-button
              v-if="row.status === IqcReworkStatus.CREATED && canDispose"
              link
              type="primary"
              @click="emit('complete-rework', row)"
              >完成返工</el-button
            >
            <span v-else-if="row.status === IqcReworkStatus.CREATED" class="muted">待完成返工</span>
            <span v-else class="muted">待复检</span>
          </template>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="这一行材料暂无处置记录" />
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 处置历史弹窗（dev-20260929-007）—— 区块 = 组件 = 文案同名。
 *
 * 粒度：**一行材料**的处置单（按 inbound_item_id 过滤）。
 *   为什么不是 lot_id：返工单在「完成返工」时会把 lot_id 改写成复检子批，按 lot_id 过滤会漏掉已完成返工那一行；
 *   inbound_item_id 自登记起不变，是稳定键（数据源：工作台一次带回的 dispositions，无需新接口）。
 * 带操作：报废 通过/驳回（PENDING_APPROVAL）、完成返工（REWORK/CREATED）—— 动作由父组件执行，本组件只上抛。
 */
import {
  IqcDispositionOrderStatus,
  IqcDispositionOrderStatusEnum,
  IqcQuarantineActionEnum,
} from '@/enums/inventory/IqcQuarantineEnum'
import { IqcReworkStatus } from '@/enums/inventory/IqcReworkEnum'

defineProps<{
  visible: boolean
  /** 标题：物料编码 / 名称（可带检验批号） */
  materialLabel?: string
  rows: any[]
  canDispose: boolean
  loading?: boolean
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'approve', row: any, approved: boolean): void
  (e: 'complete-rework', row: any): void
}>()

const num = (value?: number | string | null) =>
  value == null || value === ''
    ? '-'
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
</script>

<style scoped>
.dialog-tip {
  margin-bottom: 12px;
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.emphasis {
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  font-weight: 600;
}
</style>
