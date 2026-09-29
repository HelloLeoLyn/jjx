<template>
  <el-drawer
    :model-value="visible"
    :title="`${lotNo || '检验批'} · 质量历史`"
    size="620px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="history-tip">
      检验批自身的事件流（录项 / 判定 / 审核 / 入库确认 / 处置变动 / 关闭 / 重开）；处置动作的凭证看「处置单历史」。
    </div>
    <el-timeline v-loading="loading">
      <el-timeline-item
        v-for="item in rows"
        :key="item.historyId"
        :timestamp="item.createTime || '-'"
      >
        <div class="history-event">{{ eventLabel(item.eventType) }}</div>
        <div class="muted">操作人：{{ item.operatorName || '-' }} · {{ item.remark || '无备注' }}</div>
        <el-collapse v-if="snapshotFields(item).length" class="history-snapshot">
          <el-collapse-item title="展开快照（当时的数量口径）">
            <div v-for="field in snapshotFields(item)" :key="field.label" class="snapshot-row">
              <span class="snapshot-label">{{ field.label }}</span>
              <span>{{ field.value }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-timeline-item>
      <el-empty v-if="!loading && !rows.length" description="暂无质量历史" />
    </el-timeline>
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 质量历史抽屉（dev-20260929-007）—— 区块 = 组件 = 文案同名。
 *
 * 形态：右侧抽屉（与「批次溯源」同款），不再在页面底部占用一整块卡片；需要排障/审计时随点随开。
 * 事件名对齐后端实际写入的 8 类（QualityLotServiceImpl.recordHistory）：
 *   ITEMS_SAVED / JUDGED / REVIEWED / INBOUND_CONFIRMED / DISPOSED / CLOSED / REOPENED / REINSPECTION_CREATED
 * 快照：后端把当时的批次全貌写进 snapshot_json（{lot:{...trace,...}}），这里展开关键数量。
 */
import type { QualityLotHistory } from '@/api/quality/lot'

defineProps<{
  visible: boolean
  lotNo?: string
  rows: QualityLotHistory[]
  loading?: boolean
}>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void }>()

/** 与后端 recordHistory 的 eventType 一一对应（禁止在这里出现后端不写的事件名）。 */
const EVENT_LABELS: Record<string, string> = {
  ITEMS_SAVED: '检验项保存',
  JUDGED: '检验批判定',
  REVIEWED: '检验批审核',
  INBOUND_CONFIRMED: '确认入库',
  DISPOSED: '处置数量变动',
  CLOSED: '检验批关闭',
  REOPENED: '检验批重开',
  REINSPECTION_CREATED: '生成复检批',
}
const eventLabel = (value?: string) => (value ? EVENT_LABELS[value] || value : '质量事件')

const num = (value: unknown) =>
  value == null || value === ''
    ? null
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })

/** 从 snapshot_json 里取关键数量（拿不到就返回空数组，界面不显示折叠）。 */
function snapshotFields(item: QualityLotHistory) {
  if (!item.snapshotJson) return []
  let lot: any
  try {
    lot = JSON.parse(item.snapshotJson)?.lot
  } catch {
    return []
  }
  if (!lot) return []
  const trace = lot.trace || {}
  const pairs: Array<[string, unknown]> = [
    ['批量', lot.lotQuantity],
    ['合格量', lot.passQuantity],
    ['不良量', lot.failQuantity],
    ['已入库', lot.storedQuantity],
    ['已处置', lot.disposedQuantity],
    ['剩余可处置', trace.remainingDispositionQuantity],
    ['来源单号', trace.sourceNo],
    ['复检关系', trace.relationshipMode],
    ['本批复检量', trace.reinspectionQuantity],
  ]
  return pairs
    .map(([label, value]) => ({ label, value: label === '来源单号' || label === '复检关系' ? (value ?? null) : num(value) }))
    .filter((field) => field.value !== null && field.value !== undefined && field.value !== '')
}
</script>

<style scoped>
.history-tip {
  margin-bottom: 12px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.history-event {
  font-weight: 600;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.history-snapshot {
  margin-top: 6px;
}
.snapshot-row {
  display: flex;
  gap: 8px;
  line-height: 1.8;
}
.snapshot-label {
  min-width: 84px;
  color: #909399;
}
</style>
