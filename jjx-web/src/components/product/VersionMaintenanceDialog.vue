<template>
  <el-dialog v-model="visible" :title="`${title} · 版本维护`" width="1280px" append-to-body>
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column label="版本号" width="125"
        ><template #default="{ row }">
          {{ row.version }} <el-tag v-if="row.isCurrent" type="success" size="small">当前</el-tag>
        </template></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="{ row }">{{ statusLabel(row) }}</template></el-table-column
      >
      <el-table-column v-if="supportsValidity" label="生效日期" width="115"
        ><template #default="{ row }">{{ date(row.effectiveDate) }}</template></el-table-column
      >
      <el-table-column v-if="supportsValidity" label="失效日期" width="115"
        ><template #default="{ row }">{{ date(row.expiryDate) }}</template></el-table-column
      >
      <el-table-column prop="createBy" label="创建人" width="100" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column v-if="supportsReviewAudit" prop="approveBy" label="审核人" width="100" />
      <el-table-column v-if="supportsReviewAudit" prop="approveTime" label="审核时间" width="170" />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <TableActionColumn
        :actions="actions"
        :max-visible="3"
        min-width="310"
        display="text"
        @action="handleAction"
      />
    </el-table>
    <el-dialog v-model="compareVisible" title="版本对比（差异）" width="1100px" append-to-body>
      <div class="compare-controls">
        <el-select v-model="oldId" placeholder="旧版本" style="width: 200px"
          ><el-option v-for="v in rows" :key="v.id" :value="v.id" :label="v.version"
        /></el-select>
        <span>→</span>
        <el-select v-model="newId" placeholder="新版本" style="width: 200px"
          ><el-option v-for="v in rows" :key="v.id" :value="v.id" :label="v.version"
        /></el-select>
        <el-button
          type="primary"
          :disabled="!oldId || !newId || oldId === newId"
          :loading="comparing"
          @click="compare"
          >对比</el-button
        >
      </div>
      <el-alert v-if="compareError" :title="compareError" type="error" :closable="false" />
      <el-table v-if="compared" :data="differences" max-height="500">
        <el-table-column prop="path" label="位置" min-width="180" />
        <el-table-column prop="field" label="变更项" width="140" />
        <el-table-column prop="before" label="旧版本" min-width="230" />
        <el-table-column prop="after" label="新版本" min-width="230" />
      </el-table>
      <el-empty v-if="compared && !differences.length" description="两个版本内容一致" />
    </el-dialog>
  </el-dialog>
</template>

<script lang="ts">
export interface VersionMaintenanceRow {
  id: number
  version: string
  approveStatus: number
  isCurrent: boolean
  canSetCurrent: boolean
  effectiveDate?: string
  expiryDate?: string
  createBy?: string
  createTime?: string
  approveBy?: string
  approveTime?: string
  remark?: string
}
export interface VersionDifference {
  path: string
  field: string
  before: string
  after: string
}

const contentFields: Record<string, string> = {
  bomName: 'BOM名称',
  routingName: '路线名称',
  effectiveDate: '生效日期',
  expiryDate: '失效日期',
  remark: '备注',
  description: '说明',
  materialCode: '物料编码',
  materialName: '物料名称',
  specification: '规格',
  processName: '工序名称',
  processCategory: '工序类别',
  processId: '工序',
  materialId: '关联物料',
  subBomId: '下层方案',
  unit: '单位',
  quantity: '单位用量',
  baseQty: '基数',
  moduleQty: '模数',
  lossRate: '损耗率',
  minIssueQty: '最低投料量',
  split: '跳距',
  pitchMm: '跳距毫米',
  pcsPerPitch: '每跳件数',
  widthMm: '宽度',
  lengthMm: '长度',
  layer: '层次',
  positionNo: '位号',
  sourceType: '来源',
  substituteJson: '替代料',
  unitFactor: '单位换算',
  unitFactorBasis: '换算口径',
  itemOrder: '物料顺序',
  processOrder: '工序顺序',
  groupOrder: '组合顺序',
  groupName: '组合名称',
  standardLaborHours: '标准人工工时',
  customLaborHours: '自定义人工工时',
  standardMachineHours: '标准机器工时',
  customMachineHours: '自定义机器工时',
  indexNumber: '下标',
  equipmentType: '设备类型',
  skillRequirement: '技能要求',
  icon: '图标',
  iconJson: '图标配置',
  workInstruction: '作业说明',
  workflowSeq: '组序号',
  customProcessParams: '自定义工艺参数',
  processType: '工序类型',
  majorCategory: '工序大类',
  qualityStandard: '质量标准',
  precondition: '前置条件',
  preconditionDisplay: '前置工序',
  isOptional: '可选工序',
  isCommon: '共用物料',
  unitFactorSource: '换算来源',
}
const printable = (value: unknown): string =>
  value == null || value === ''
    ? '—'
    : typeof value === 'object'
      ? JSON.stringify(value)
      : String(value)

/** 通过业务身份和父路径配对，复制后的数据库行号变化不算内容差异；重复物料保留出现次数。 */
export function diffVersionDetails(
  before: Record<string, any>,
  after: Record<string, any>
): VersionDifference[] {
  const flatten = (detail: Record<string, any>) => {
    const result = new Map<string, Record<string, any>>([['版本资料', detail]])
    const items: Record<string, any>[] = detail.items || []
    const byId = new Map(items.map((row) => [row.itemId, row]))
    const parentPath = (row: Record<string, any>, seen = new Set<unknown>()): string => {
      const parentId = row.parentMaterialId ?? row.parentId
      if (parentId == null || seen.has(parentId) || !byId.has(parentId)) return ''
      seen.add(parentId)
      const parent = byId.get(parentId)!
      return `${parentPath(parent, seen)}${identity(parent)}/`
    }
    const counts = new Map<string, number>()
    const walk = (rows: Record<string, any>[], prefix: string) =>
      rows.forEach((row) => {
        const base = `${prefix}${parentPath(row)}${identity(row)}`
        const count = (counts.get(base) || 0) + 1
        counts.set(base, count)
        const key = `${base} [${count}]`
        result.set(key, row)
        if (row.children?.length) walk(row.children, `${key}/`)
      })
    walk(items, '')
    return result
  }
  const oldRows = flatten(before)
  const newRows = flatten(after)
  const result: VersionDifference[] = []
  for (const key of new Set([...oldRows.keys(), ...newRows.keys()])) {
    const oldRow = oldRows.get(key)
    const newRow = newRows.get(key)
    if (!oldRow || !newRow) {
      const row = oldRow || newRow!
      const description = Object.entries(contentFields)
        .filter(([field]) => row[field] != null && row[field] !== '')
        .map(([field, label]) => `${label}：${printable(row[field])}`)
        .join('；')
      result.push({
        path: key,
        field: oldRow ? '删除' : '新增',
        before: oldRow ? description : '—',
        after: newRow ? description : '—',
      })
      continue
    }
    for (const [field, label] of Object.entries(contentFields)) {
      const a = printable(oldRow[field])
      const b = printable(newRow[field])
      if (a !== b) result.push({ path: key, field: label, before: a, after: b })
    }
  }
  return result
}
function identity(row: Record<string, any>): string {
  return String(
    row.materialCode ||
      (row.materialId != null ? `物料#${row.materialId}` : '') ||
      (row.processId != null ? `工序#${row.processId}` : '') ||
      row.processName ||
      row.materialName ||
      row.groupName ||
      '明细'
  )
}
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { BomStatusEnum, RouteStatusEnum } from '@/enums/product'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    domain: 'bom' | 'routing'
    rows: VersionMaintenanceRow[]
    loading?: boolean
    busy?: boolean
    supportsValidity?: boolean
    supportsReviewAudit?: boolean
    fetchDetail: (id: number) => Promise<Record<string, any>>
  }>(),
  { supportsValidity: true, supportsReviewAudit: true }
)
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  action: [key: string, row: VersionMaintenanceRow]
}>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const statusEnum = computed(() => (props.domain === 'bom' ? BomStatusEnum : RouteStatusEnum))
const statusLabel = (row: VersionMaintenanceRow) =>
  row.approveStatus === BomStatusEnum.VOIDED.value
    ? BomStatusEnum.VOIDED.label
    : statusEnum.value.getLabel(row.approveStatus)
const date = (value?: string) => (value ? value.slice(0, 10) : '—')
const allowed = (key: string, row: VersionMaintenanceRow) => {
  const state = statusEnum.value
  if (key === 'edit' || key === 'submit')
    return row.approveStatus === state.DRAFT.value || row.approveStatus === state.REJECTED.value
  if (key === 'delete') return row.approveStatus === state.DRAFT.value
  if (key === 'approve') return row.approveStatus === state.REVIEWING.value
  if (key === 'setCurrent') return row.canSetCurrent && !row.isCurrent
  if (key === 'void') return row.isCurrent && row.approveStatus === state.APPROVED.value
  if (key === 'compare') return props.rows.length > 1
  return true
}
const actions = computed<TableAction<VersionMaintenanceRow>[]>(() =>
  [
    { key: 'view', label: '查看', permission: `engineering:${props.domain}:view` },
    { key: 'edit', label: '编辑', permission: `engineering:${props.domain}:edit` },
    { key: 'copy', label: '复制为新版本', permission: `engineering:${props.domain}:add` },
    {
      key: 'submit',
      label: '提交审核',
      permission: `engineering:${props.domain}:${props.domain === 'bom' ? 'add' : 'edit'}`,
    },
    {
      key: 'approve',
      label: '审核通过/驳回',
      permission: [`engineering:${props.domain}:approve`, `engineering:${props.domain}:reject`],
    },
    { key: 'setCurrent', label: '设为当前', permission: `engineering:${props.domain}:edit` },
    { key: 'compare', label: '版本对比（差异）', permission: `engineering:${props.domain}:view` },
    {
      key: 'void',
      label: '作废',
      type: 'danger' as const,
      permission: `engineering:${props.domain}:edit`,
    },
    {
      key: 'delete',
      label: '删除',
      type: 'danger' as const,
      permission: `engineering:${props.domain}:delete`,
    },
  ].map((action) => ({
    ...action,
    visible: ({ row }: { row: VersionMaintenanceRow }) => allowed(action.key, row),
    disabled: props.busy,
  }))
)
const compareVisible = ref(false)
const oldId = ref<number>()
const newId = ref<number>()
const comparing = ref(false)
const compared = ref(false)
const compareError = ref('')
const differences = ref<VersionDifference[]>([])
let compareRequest = 0
const handleAction = (key: string, row: VersionMaintenanceRow) => {
  if (props.busy || !allowed(key, row)) return
  if (key === 'compare') {
    compareRequest++
    comparing.value = false
    oldId.value = row.id
    newId.value = props.rows.find((v) => v.id !== row.id)?.id
    compared.value = false
    compareError.value = ''
    compareVisible.value = true
    return
  }
  emit('action', key, row)
}
const compare = async () => {
  if (!oldId.value || !newId.value || oldId.value === newId.value) return
  const requestId = ++compareRequest
  comparing.value = true
  compared.value = false
  compareError.value = ''
  try {
    const [before, after] = await Promise.all([
      props.fetchDetail(oldId.value),
      props.fetchDetail(newId.value),
    ])
    if (requestId !== compareRequest) return
    differences.value = diffVersionDetails(before, after)
    compared.value = true
  } catch (error: any) {
    if (requestId === compareRequest)
      compareError.value = error?.message || '版本内容加载失败，请重试'
  } finally {
    if (requestId === compareRequest) comparing.value = false
  }
}
watch(compareVisible, (value) => {
  if (!value) {
    compareRequest++
    comparing.value = false
    compared.value = false
  }
})
watch([oldId, newId], () => {
  compareRequest++
  compared.value = false
  comparing.value = false
})
watch(
  () => props.modelValue,
  (value) => {
    if (!value) {
      compareVisible.value = false
      compareRequest++
      comparing.value = false
    }
  }
)
</script>
<style scoped>
.compare-controls {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
</style>
