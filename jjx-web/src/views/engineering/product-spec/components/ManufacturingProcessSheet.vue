<template>
  <div class="manufacturing-process-sheet" v-loading="loading">
    <el-alert
      v-if="errorMessage"
      class="sheet-error"
      type="error"
      :closable="false"
      show-icon
      :title="errorMessage"
    />

    <el-alert
      v-if="isPartial"
      class="partial-alert"
      type="warning"
      :closable="false"
      show-icon
      title="制造快照不完整（SNAPSHOT_PARTIAL）"
    >
      <div class="partial-tip">
        该工单缺少部分历史依据，以下内容按工单已持久化的快照展示，<b>不使用最新版本顶替</b>。
      </div>
      <ul class="warning-list">
        <li v-for="(warning, index) in warnings" :key="index">{{ warning }}</li>
      </ul>
    </el-alert>

    <template v-if="snapshot">
      <div class="sheet-head">
        <div class="head-line">
          <span class="head-label">工单</span>
          <span class="head-value">{{ snapshot.orderNo || '-' }}</span>
          <span class="head-label">产品</span>
          <span class="head-value">
            {{ snapshot.productName || '-' }}
            <span v-if="snapshot.productCode" class="head-sub">（{{ snapshot.productCode }}）</span>
          </span>
          <span class="head-label">计划数量</span>
          <span class="head-value">{{ formatNumber(snapshot.plannedQuantity) }}</span>
          <el-tag :type="isPartial ? 'warning' : 'success'" size="small" class="status-tag">
            {{ statusLabel }}
          </el-tag>
        </div>
        <div class="head-line">
          <span class="head-label">制造 BOM</span>
          <span class="head-value">{{ snapshot.bomCode || '未绑定' }}</span>
          <span class="head-label">工艺路线</span>
          <span class="head-value">
            {{ snapshot.routingCode || '未绑定' }}
            <span v-if="snapshot.routingVersion" class="head-sub">（{{ snapshot.routingVersion }}）</span>
          </span>
          <span class="head-label">作业规范版本</span>
          <span class="head-value head-version">{{ snapshot.workSpecVersionNo || '未绑定' }}</span>
        </div>
      </div>

      <div class="section">
        <div class="section-title">BOM 多层材料耗用</div>
        <el-table
          v-if="nodeRows.length"
          :data="nodeRows"
          border
          size="small"
          row-key="key"
          max-height="360"
        >
          <el-table-column label="层级 / 物料" min-width="260">
            <template #default="{ row }">
              <span class="node-indent" :style="{ paddingLeft: row.depth * 18 + 'px' }">
                <span class="node-level">L{{ row.depth + 1 }}</span>
                {{ row.node.materialName || '-' }}
                <span class="node-code">{{ row.node.materialCode || '' }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="用量" width="110" align="right">
            <template #default="{ row }">
              {{ formatNumber(row.node.quantity) }}
              <span class="node-unit">{{ row.node.unit || '' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单件用量" width="110" align="right">
            <template #default="{ row }">{{ formatNumber(row.node.moduleQty) }}</template>
          </el-table-column>
          <el-table-column label="损耗率(%)" width="100" align="right">
            <template #default="{ row }">{{ formatNumber(row.node.lossRate) }}</template>
          </el-table-column>
          <el-table-column label="工序" width="120">
            <template #default="{ row }">{{ row.node.processName || '-' }}</template>
          </el-table-column>
          <el-table-column label="数据来源" width="120" align="center">
            <template #default="{ row }">
              <el-tag
                v-if="row.node.snapshotSource === SNAPSHOT_SOURCE_SUB_BOM_MISSING"
                type="warning"
                size="small"
              >
                子BOM缺失
              </el-tag>
              <span v-else class="node-source">物料行快照</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="该工单快照没有 BOM 材料行" />
      </div>

      <div class="section">
        <div class="section-title">路线工序</div>
        <el-table
          v-if="routingSteps.length"
          :data="routingSteps"
          border
          size="small"
          max-height="300"
        >
          <el-table-column label="工序顺序" prop="workflowSeq" width="100" align="center" />
          <el-table-column label="工序号" prop="processOrder" width="100" align="center" />
          <el-table-column label="工序名称" prop="processName" min-width="160" />
          <el-table-column label="作业说明" prop="workInstruction" min-width="200">
            <template #default="{ row }">{{ row.workInstruction || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="该工单快照没有路线工序" />
      </div>

      <div class="section">
        <div class="section-title">工单实际工序</div>
        <el-table
          v-if="executionSteps.length"
          :data="executionSteps"
          border
          size="small"
          max-height="300"
        >
          <el-table-column label="工序号" prop="processOrder" width="100" align="center" />
          <el-table-column label="工序名称" prop="processName" min-width="160" />
          <el-table-column label="执行状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="executionStatusTag(row.executionStatus)" size="small">
                {{ executionStatusLabel(row.executionStatus) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="该工单还没有工序执行记录" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  MANUFACTURING_SNAPSHOT_COMPLETE,
  MANUFACTURING_SNAPSHOT_PARTIAL,
  getManufacturingSnapshot,
  type ManufacturingSnapshotExecutionStep,
  type ManufacturingSnapshotNodeVO,
  type ManufacturingSnapshotRoutingStep,
  type ManufacturingSnapshotVO,
} from '@/api/production/manufacturingSnapshot'
import { ExecutionStatusEnum } from '@/enums/production/WorkOrderEnum'
import type { TagProps } from 'element-plus'

/**
 * 作业规范 / 生产资料展示（dev-20261111-009 G2）。
 *
 * 只读：数据全部来自 G1 的工单制造历史快照接口；快照为 SNAPSHOT_PARTIAL 时
 * 只提示告警并展示已有部分，不拉最新 BOM / 路线 / 规范顶替，也不提供任何写操作。
 */
const props = defineProps<{
  /** 工单 ID（ProductionOrderVO.orderId 为字符串，此处统一再转数字） */
  orderId: number | string | null | undefined
  depth?: number
}>()

const SNAPSHOT_SOURCE_SUB_BOM_MISSING = 'SUB_BOM_MISSING'

const loading = ref(false)
const errorMessage = ref('')
const snapshot = ref<ManufacturingSnapshotVO | null>(null)

const isPartial = computed(
  () => snapshot.value?.snapshotStatus === MANUFACTURING_SNAPSHOT_PARTIAL,
)
const statusLabel = computed(() => {
  if (snapshot.value?.snapshotStatus === MANUFACTURING_SNAPSHOT_COMPLETE) return '快照完整'
  if (isPartial.value) return '快照不完整'
  return '未返回状态'
})
const warnings = computed(() => snapshot.value?.warnings ?? [])
const routingSteps = computed<ManufacturingSnapshotRoutingStep[]>(
  () => snapshot.value?.routingSteps ?? [],
)
const executionSteps = computed<ManufacturingSnapshotExecutionStep[]>(
  () => snapshot.value?.executionSteps ?? [],
)

interface FlatNodeRow {
  key: string
  depth: number
  node: ManufacturingSnapshotNodeVO
}

/** 后端返回嵌套 children；此处只做缩进扁平化，便于表格展示，不改动数据结构。 */
function flattenNodes(
  nodes: ManufacturingSnapshotNodeVO[] = [],
  depth = 0,
  prefix = '',
): FlatNodeRow[] {
  const rows: FlatNodeRow[] = []
  nodes.forEach((node, index) => {
    const key = prefix ? `${prefix}-${index}` : String(index)
    rows.push({ key, depth, node })
    if (node.children?.length) {
      rows.push(...flattenNodes(node.children, depth + 1, key))
    }
  })
  return rows
}

const nodeRows = computed(() => flattenNodes(snapshot.value?.nodes ?? []))

function formatNumber(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

function executionStatusLabel(status: number | null | undefined): string {
  if (status === null || status === undefined) return '-'
  return ExecutionStatusEnum.getLabel(Number(status))
}

function executionStatusTag(status: number | null | undefined): TagProps['type'] {
  if (status === null || status === undefined) return 'info'
  return ExecutionStatusEnum.getTagProps(Number(status))?.type || 'info'
}

async function loadSnapshot() {
  const orderId = props.orderId
  if (!orderId) {
    snapshot.value = null
    errorMessage.value = ''
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const res: any = await getManufacturingSnapshot(Number(orderId), props.depth ?? 32)
    snapshot.value = res?.data ?? null
    if (!snapshot.value) {
      errorMessage.value = '制造快照未返回数据'
    }
  } catch (error) {
    snapshot.value = null
    errorMessage.value = '制造快照加载失败'
    console.error('制造快照加载失败:', error)
  } finally {
    loading.value = false
  }
}

watch(() => [props.orderId, props.depth], loadSnapshot, { immediate: true })
</script>

<style scoped>
.manufacturing-process-sheet {
  min-height: 80px;
}
.sheet-error {
  margin-bottom: 12px;
}
.partial-alert {
  margin-bottom: 12px;
}
.partial-tip {
  font-size: 12px;
  color: #e6a23c;
}
.warning-list {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: #606266;
}
.sheet-head {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #303133;
}
.head-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  line-height: 24px;
}
.head-label {
  color: #909399;
}
.head-value {
  margin-right: 14px;
}
.head-sub {
  color: #909399;
}
.head-version {
  font-weight: 600;
  color: #409eff;
}
.status-tag {
  margin-left: auto;
}
.section {
  margin-bottom: 14px;
}
.section-title {
  margin-bottom: 6px;
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}
.node-indent {
  display: inline-block;
}
.node-level {
  margin-right: 6px;
  color: #909399;
  font-size: 12px;
}
.node-code {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}
.node-unit {
  color: #909399;
  font-size: 12px;
}
.node-source {
  color: #606266;
  font-size: 12px;
}
</style>
