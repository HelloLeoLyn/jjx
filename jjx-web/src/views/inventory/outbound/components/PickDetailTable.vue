<template>
  <el-table :data="displayRows" border size="small" style="width: 100%">
    <el-table-column label="序号" width="70" align="center">
      <template #default="{ row }">
        <span>{{ row.sequence }}</span>
      </template>
    </el-table-column>
    <el-table-column label="材料编码" prop="materialCode" width="110">
      <template #default="{ row }">{{ row.source.materialCode || '-' }}</template>
    </el-table-column>
    <el-table-column label="材料名称" min-width="150" show-overflow-tooltip>
      <template #default="{ row }">
        <div class="pick-name" :style="{ paddingLeft: row.depth * 16 + 'px' }">
          <button
            v-if="row.hasChildren"
            type="button"
            class="pick-toggle"
            @click="toggle(row.parentKey)"
          >
            {{ collapsed.has(row.parentKey) ? '▸' : '▾' }}
          </button>
          <span v-else class="pick-spacer"></span>
          <span>{{ row.source.materialName || '-' }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="项目名称" width="110" show-overflow-tooltip>
      <template #default="{ row }">{{
        row.source.processName || row.source.projectName || '-'
      }}</template>
    </el-table-column>
    <el-table-column label="规格" min-width="120" show-overflow-tooltip>
      <template #default="{ row }">{{ row.source.specification || '-' }}</template>
    </el-table-column>
    <el-table-column label="单位" width="60" align="center">
      <template #default="{ row }">{{ row.source.unit || '-' }}</template>
    </el-table-column>
    <el-table-column label="跳距/模数/基数" width="120" align="center">
      <template #default="{ row }">{{ ratioText(row.source) }}</template>
    </el-table-column>
    <el-table-column label="实发数量" width="100" align="right">
      <template #default="{ row }">{{
        num(row.source.quantity ?? row.source.qtyPick ?? row.source.issuedQuantity)
      }}</template>
    </el-table-column>
    <el-table-column label="需求" width="90" align="right">
      <template #default="{ row }">{{ num(row.source.qtyNeeded ?? row.source.demand) }}</template>
    </el-table-column>
    <el-table-column label="库存数量" width="100" align="right">
      <template #default="{ row }">{{
        num(row.source.available ?? row.source.availableQuantity ?? row.source.stockQuantity)
      }}</template>
    </el-table-column>
    <el-table-column label="备注" min-width="100" show-overflow-tooltip>
      <template #default="{ row }">{{ row.source.remark || '-' }}</template>
    </el-table-column>
    <el-table-column v-if="showLocation" label="库位" width="110" show-overflow-tooltip>
      <template #default="{ row }">{{ row.source.locationName || '-' }}</template>
    </el-table-column>
    <template #empty>{{ emptyText }}</template>
  </el-table>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{ rows?: any[]; emptyText?: string; showLocation?: boolean }>(),
  { rows: () => [], emptyText: '无明细', showLocation: true }
)

const collapsed = ref(new Set<string>())

function toggle(key: string) {
  if (collapsed.value.has(key)) collapsed.value.delete(key)
  else collapsed.value.add(key)
}

const displayRows = computed(() => {
  const rows = props.rows || []
  const children = new Map<string, any[]>()
  const roots: any[] = []
  for (const row of rows) {
    const parentKey = row.parentRowKey ? String(row.parentRowKey) : ''
    if (parentKey) {
      if (!children.has(parentKey)) children.set(parentKey, [])
      children.get(parentKey)!.push(row)
    } else {
      roots.push(row)
    }
  }
  if (!roots.length) {
    return rows.map((row, index) => ({
      source: row,
      sequence: String(index + 1),
      depth: 0,
      hasChildren: false,
      parentKey: '',
    }))
  }
  const out: any[] = []
  roots.forEach((row, index) => {
    const key = String(row.rowKey || `root-${index}`)
    const kids = children.get(key) || []
    out.push({
      source: row,
      sequence: String(index + 1),
      depth: 0,
      hasChildren: kids.length > 0,
      parentKey: key,
    })
    if (!collapsed.value.has(key)) {
      kids.forEach((child, childIndex) => {
        out.push({
          source: child,
          sequence: `${index + 1}.${childIndex + 1}`,
          depth: 1,
          hasChildren: false,
          parentKey: key,
        })
      })
    }
  })
  return out
})

function num(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  const n = Number(value)
  return Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(2)) : '-'
}

function ratioText(row: any) {
  if (Number(row.level) === 2 && row.pitchMm != null) return `${num(row.pitchMm)}mm`
  return `${num(row.moduleQty ?? 1)}/${num(row.baseQty ?? 1)}`
}
</script>

<style scoped>
.pick-name {
  display: flex;
  align-items: center;
  gap: 4px;
}
.pick-toggle {
  width: 18px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  color: inherit;
}
.pick-spacer {
  width: 18px;
  flex-shrink: 0;
}
</style>
