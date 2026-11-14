<template>
  <el-card class="bom-detail-card" shadow="never">
    <template #header>
      <ManufacturingBomPath
        v-if="ancestors.length"
        class="detail-path"
        :nodes="pathNodes"
        @navigate="emit('navigate', $event)"
      />
      <div class="card-header">
        <span>BOM明细</span>
        <div class="header-right">
          <span class="total-count">共 {{ items.length }} 项</span>
          <el-popover placement="bottom-end" :width="300" trigger="click">
            <template #reference>
              <el-button link type="primary" size="small" icon="Setting">列设置</el-button>
            </template>
            <div class="column-setting">
              <div class="column-setting-actions">
                <el-button link type="primary" size="small" @click="showAllColumns">全选</el-button>
                <el-button link type="primary" size="small" @click="resetColumns">恢复默认</el-button>
              </div>
              <el-checkbox-group v-model="visibleKeys" class="column-setting-list">
                <el-checkbox v-for="column in COLUMNS" :key="column.key" :value="column.key">
                  {{ column.label }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </el-popover>
        </div>
      </div>
    </template>
    <el-table
      :data="items"
      border
      style="width: 100%"
      :height="height"
      :stripe="stripe"
      :span-method="groupSpan"
      :row-class-name="rowClassName"
      :row-key="rowKey"
      :row-expandable="canExpand"
      :expand-row-keys="expandedKeys"
      @expand-change="onExpand"
    >
      <el-table-column v-if="ancestors.length" type="expand" width="48">
        <template #default="{ row }">
          <div v-loading="childOf(row)?.loading" class="sub-bom-detail">
            <el-alert
              v-if="childOf(row)?.error"
              :title="childOf(row)!.error"
              type="error"
              :closable="false"
            />
            <el-button
              v-if="childOf(row)?.error && !childOf(row)?.cycle"
              link
              type="primary"
              @click="loadChild(row)"
            >重试加载下层方案</el-button>
            <BomDetailTable
              v-if="childOf(row)?.detail"
              :items="childOf(row)!.detail!.items || []"
              :ancestors="[...ancestors, childOf(row)!.detail!]"
              height="auto"
              :stripe="stripe"
              @navigate="navigateFromChild(row, $event)"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column v-if="show('serial')" label="序号" width="60" align="center">
        <template #default="{ $index }">{{ groups[$index]?.displayNumber || '' }}</template>
      </el-table-column>
      <el-table-column v-if="show('reviewStatus')" label="行状态" width="105" align="center">
        <template #default="{ row }">
          <el-tag
            :type="
              BomItemReviewStatusEnum.getTagProps(
                row.reviewStatus ?? BomItemReviewStatusEnum.DRAFT.value
              ).type
            "
            size="small"
          >
            {{
              BomItemReviewStatusEnum.getLabel(
                row.reviewStatus ?? BomItemReviewStatusEnum.DRAFT.value
              )
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="show('materialCode')" label="物料编码" prop="materialCode" width="120" />
      <el-table-column
        v-if="show('materialName')"
        label="物料名称"
        prop="materialName"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column v-if="show('process')" label="项目" prop="processName" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <ProcessName :process-id="row.processId" :name="row.processName" />
        </template>
      </el-table-column>
      <el-table-column v-if="show('spec')" label="规格" prop="specification" width="140" show-overflow-tooltip />
      <!-- 档案规格：物料档案侧生成值，与本行规格不一致时标红，避免"看着对、数据对不上" -->
      <el-table-column v-if="show('archiveSpec')" label="档案规格" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="!archiveSpecOf(row)" class="muted">—</span>
          <el-tooltip v-else-if="specMismatch(row)" content="与本行规格不一致，请核对物料档案" placement="top">
            <span class="mismatch">{{ archiveSpecOf(row) }}</span>
          </el-tooltip>
          <span v-else>{{ archiveSpecOf(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="show('unit')" label="单位" prop="unit" width="80" />
      <el-table-column v-if="show('archiveUnit')" label="档案单位" width="90">
        <template #default="{ row }">
          <span v-if="!row.archiveUnit" class="muted">—</span>
          <el-tooltip v-else-if="unitMismatch(row)" content="与档案单位不一致，需人工确认" placement="top">
            <span class="mismatch">{{ row.archiveUnit }}</span>
          </el-tooltip>
          <span v-else>{{ row.archiveUnit }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="show('sourceType')" label="来源" width="110" align="center">
        <template #default="{ row }">
          <template v-if="row.sourceType">
            <el-tag :type="BomSourceTypeEnum.getTagProps(row.sourceType).type" size="small">
              {{ BomSourceTypeEnum.getLabel(row.sourceType) }}
            </el-tag>
            <!-- 自制行没指定下层方案时，领料/试算会算不出投入，这里直接提示 -->
            <el-tooltip v-if="isMakeWithoutScheme(row)" content="自制行未指定下层制造方案" placement="top">
              <el-tag type="warning" size="small" class="scheme-missing">缺方案</el-tag>
            </el-tooltip>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="show('subBom')" label="下层制造方案" width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.subBomCode">
            <span>{{ row.subBomCode }} {{ row.subBomVersion || '' }}</span>
            <el-tag
              v-if="row.subBomApproveStatus"
              :type="BomStatusEnum.getTagProps(row.subBomApproveStatus).type"
              size="small"
              class="scheme-status"
            >
              {{ BomStatusEnum.getLabel(row.subBomApproveStatus) }}
            </el-tag>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="show('moduleQty')" label="模数" prop="moduleQty" width="80" align="right">
        <template #default="{ row }">{{ num(row.moduleQty, 4) }}</template>
      </el-table-column>
      <el-table-column v-if="show('baseQty')" label="基数" prop="baseQty" width="80" align="right">
        <template #default="{ row }">{{ num(row.baseQty, 4) }}</template>
      </el-table-column>
      <el-table-column v-if="show('pitch')" label="跳距(mm)" prop="pitchMm" width="100" align="right">
        <template #default="{ row }">{{ num(row.pitchMm, 2) }}</template>
      </el-table-column>
      <el-table-column v-if="show('pcsPerPitch')" label="每跳片数" prop="pcsPerPitch" width="95" align="right">
        <template #default="{ row }">{{ num(row.pcsPerPitch, 0) }}</template>
      </el-table-column>
      <el-table-column v-if="show('lossRate')" label="损耗率(%)" prop="lossRate" width="100" align="right">
        <template #default="{ row }">{{ num(row.lossRate, 2) }}</template>
      </el-table-column>
      <!-- "数量"改回编辑弹窗的口径：它是一件的净用量（基数÷模数），不是整单需求 -->
      <el-table-column v-if="show('unitQty')" label="每件用量" prop="quantity" width="100" align="right">
        <template #default="{ row }">{{ num(row.quantity, 4) }}</template>
      </el-table-column>
      <el-table-column v-if="show('appliedQty')" label="应用料(含损耗)" prop="appliedQty" width="120" align="right">
        <template #default="{ row }">{{ num(row.appliedQty, 4) }}</template>
      </el-table-column>
      <el-table-column
        v-if="show('actualIssueQty')"
        label="实际投料"
        prop="actualIssueQty"
        width="100"
        align="right"
      >
        <template #default="{ row }">{{ num(row.actualIssueQty, 4) }}</template>
      </el-table-column>
      <el-table-column v-if="show('minIssueQty')" label="最低投料" prop="minIssueQty" width="100" align="right">
        <template #default="{ row }">{{ num(row.minIssueQty, 4) }}</template>
      </el-table-column>
      <el-table-column v-if="show('approvedBy')" label="行批准人" width="100">
        <template #default="{ row }">{{ row.approvedBy || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="show('approvedTime')" label="行批准时间" width="160">
        <template #default="{ row }">{{ parseTime(row.approvedTime) || '—' }}</template>
      </el-table-column>
      <el-table-column
        v-if="show('rejectReason')"
        label="驳回原因"
        prop="rejectReason"
        width="160"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ row.rejectReason || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="show('remark')" label="备注" prop="remark" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { bomGroupRows } from '@/utils/bomGroups'
import type { EngineeringBomItem } from '@/types/product/bom'
import ProcessName from '@/components/ProcessName.vue'
import { autoSpecOf } from '@/components/bomSpecMode'
import { unitMismatch } from '@/components/bom/consumptionMode'
import { parseTime } from '@/utils/format'
import { productBomApi } from '@/api/product/bom'
import type { EngineeringBom } from '@/types/product/bom'
import ManufacturingBomPath from './ManufacturingBomPath.vue'
import {
  BomItemReviewStatusEnum,
  BomSourceTypeEnum,
  BomStatusEnum,
} from '@/enums/product'

const props = withDefaults(
  defineProps<{
    items: EngineeringBomItem[]
    height?: number | string
    stripe?: boolean
    ancestors?: EngineeringBom[]
  }>(),
  { height: 300, stripe: false, ancestors: () => [] }
)
const emit = defineEmits<{ navigate: [index: number] }>()

type ChildDetail = EngineeringBom & { items?: EngineeringBomItem[] }
interface ChildState {
  loading: boolean
  error: string
  cycle?: boolean
  detail?: ChildDetail
}

// 展开、缓存和路径均只存在于前端，不修改父 BOM 或保存/审批链路。
const expandedKeys = ref<string[]>([])
const children = ref(new Map<number, ChildState>())
let generation = 0
watch([() => props.items, () => props.ancestors.map((bom) => bom.bomId).join('/')], () => {
  generation++
  expandedKeys.value = []
  children.value.clear()
})
const pathNodes = computed(() => props.ancestors.map((bom) => ({
  bomId: bom.bomId,
  label: [bom.bomCode, bom.bomVersion].filter(Boolean).join(' ') || bom.bomName || '当前层',
})))
const rowKey = (row: EngineeringBomItem) => `item:${row.itemId ?? props.items.indexOf(row)}`
const canExpand = (row: EngineeringBomItem) =>
  props.ancestors.length > 0 && row.sourceType === BomSourceTypeEnum.MAKE && Boolean(row.subBomId)
const childOf = (row: EngineeringBomItem) => children.value.get(row.subBomId!)

async function loadChild(row: EngineeringBomItem) {
  if (!canExpand(row)) return
  const bomId = row.subBomId!
  if (props.ancestors.some((bom) => bom.bomId === bomId)) {
    children.value.set(bomId, {
      loading: false,
      cycle: true,
      error: `循环引用：下层制造方案 ${row.subBomCode || '当前方案'} 已在祖先路径中，已拦截展开`,
    })
    return
  }
  if (childOf(row)?.loading || childOf(row)?.detail) return
  const state: ChildState = { loading: true, error: '' }
  children.value.set(bomId, state)
  const requestGeneration = generation
  try {
    const response = await productBomApi.getEngineeringBomInfo(bomId)
    if (requestGeneration !== generation) return
    children.value.set(bomId, { loading: false, error: '', detail: response.data })
  } catch {
    if (requestGeneration !== generation) return
    children.value.set(bomId, { loading: false, error: '下层制造方案加载失败，请确认访问权限与方案是否存在' })
  }
}

function onExpand(row: EngineeringBomItem, expandedRows: EngineeringBomItem[]) {
  expandedKeys.value = expandedRows.filter(canExpand).map(rowKey)
  if (expandedKeys.value.includes(rowKey(row))) void loadChild(row)
}
function navigateFromChild(row: EngineeringBomItem, index: number) {
  if (index === props.ancestors.length - 1) {
    expandedKeys.value = expandedKeys.value.filter((key) => key !== rowKey(row))
  } else {
    emit('navigate', index)
  }
}

/** 明细列清单：列设置的顺序与文案都以这里为准，加列时同时补 <el-table-column>。 */
const COLUMNS = [
  { key: 'serial', label: '序号' },
  { key: 'reviewStatus', label: '行状态' },
  { key: 'materialCode', label: '物料编码' },
  { key: 'materialName', label: '物料名称' },
  { key: 'process', label: '项目' },
  { key: 'spec', label: '规格' },
  { key: 'archiveSpec', label: '档案规格' },
  { key: 'unit', label: '单位' },
  { key: 'archiveUnit', label: '档案单位' },
  { key: 'sourceType', label: '来源' },
  { key: 'subBom', label: '下层制造方案' },
  { key: 'moduleQty', label: '模数' },
  { key: 'baseQty', label: '基数' },
  { key: 'pitch', label: '跳距(mm)' },
  { key: 'pcsPerPitch', label: '每跳片数' },
  { key: 'lossRate', label: '损耗率(%)' },
  { key: 'unitQty', label: '每件用量' },
  { key: 'appliedQty', label: '应用料(含损耗)' },
  { key: 'actualIssueQty', label: '实际投料' },
  { key: 'minIssueQty', label: '最低投料' },
  { key: 'approvedBy', label: '行批准人' },
  { key: 'approvedTime', label: '行批准时间' },
  { key: 'rejectReason', label: '驳回原因' },
  { key: 'remark', label: '备注' },
] as const

const ALL_KEYS = COLUMNS.map((column) => column.key)
const STORAGE_KEY = 'jjx.bomDetail.visibleColumns'

/** 只有用户主动隐藏过的列才记进 localStorage：默认全显示，恢复默认=清空记录。 */
const readStoredHidden = (): string[] => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((key) => ALL_KEYS.includes(key)) : []
  } catch {
    return []
  }
}

const hiddenKeys = ref<string[]>(readStoredHidden())
const visibleKeys = ref<string[]>(ALL_KEYS.filter((key) => !hiddenKeys.value.includes(key)))

watch(visibleKeys, (keys) => {
  hiddenKeys.value = ALL_KEYS.filter((key) => !keys.includes(key))
  try {
    if (hiddenKeys.value.length === 0) localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, JSON.stringify(hiddenKeys.value))
  } catch {
    // 隐私模式/容量不足时只影响记忆，不影响本次展示
  }
})

const show = (key: string) => visibleKeys.value.includes(key)
const showAllColumns = () => {
  visibleKeys.value = [...ALL_KEYS]
}
const resetColumns = () => {
  visibleKeys.value = [...ALL_KEYS]
}

const groups = computed(() => bomGroupRows(props.items, true))
const groupSpan = ({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }) => {
  // 序号列是合并组号的锚点；它被隐藏时不能按列序猜，直接不合并
  if (!show('serial') || columnIndex !== (props.ancestors.length ? 1 : 0)) return [1, 1]
  // 展开内容会插入物理行；此时重复展示组号，避免 rowspan 跨进子表导致列错位。
  if (expandedKeys.value.length) return [1, 1]
  const group = groups.value[rowIndex]
  if (!group) return [1, 1]
  if (group.span === 0) return [0, 0]
  if (group.span <= 1) return [1, 1]
  return rowIndex === group.index ? [group.span, 1] : [0, 0]
}

const rowClassName = ({ row }: { row: EngineeringBomItem }) =>
  [unitMismatch(row) ? 'bom-row-error' : '', canExpand(row) ? '' : 'no-sub-bom'].filter(Boolean).join(' ')

const archiveSpecOf = (row: EngineeringBomItem) => autoSpecOf(row).trim()
const specMismatch = (row: EngineeringBomItem) => {
  const archive = archiveSpecOf(row)
  const own = String(row.specification || '').trim()
  return Boolean(archive && own && archive !== own)
}
const isMakeWithoutScheme = (row: EngineeringBomItem) =>
  row.sourceType === BomSourceTypeEnum.MAKE && !row.subBomId

/** 数量列保留编辑弹窗的精度口径：模数/基数为 4 位小数，金额类不涉及。 */
const num = (value: number | string | null | undefined, digits: number) => {
  if (value === undefined || value === null || value === '') return '—'
  const parsed = typeof value === 'string' ? parseFloat(value) : Number(value)
  if (Number.isNaN(parsed)) return '—'
  return String(Number(parsed.toFixed(digits)))
}
</script>
<style scoped>
.detail-path {
  margin-bottom: 12px;
}
.detail-path :deep(.path-hint) {
  display: none;
}
.sub-bom-detail {
  min-height: 48px;
  padding: 12px;
}
:deep(.no-sub-bom > .el-table__expand-column .el-table__expand-icon) {
  display: none;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.total-count {
  font-size: 14px;
  color: #909399;
}
.column-setting-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.column-setting-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-height: 320px;
  overflow-y: auto;
}
.muted {
  color: #c0c4cc;
}
.mismatch {
  color: #f56c6c;
  font-weight: 600;
}
.scheme-missing,
.scheme-status {
  margin-left: 4px;
}
:deep(.bom-row-error) {
  background-color: #fef0f0;
}
</style>
