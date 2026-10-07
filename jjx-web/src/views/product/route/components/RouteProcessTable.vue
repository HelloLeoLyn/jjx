<template>
  <div>
    <el-divider content-position="left">工序明细</el-divider>
    <template v-for="block in blocks" :key="block.key">
      <el-divider content-position="left">
        {{ block.label }}（{{ block.groups.length }} 道）
      </el-divider>
      <el-table :data="block.groups" border stripe style="width: 100%">
        <el-table-column label="序号" width="70" align="center">
          <template #default="scope">{{ scope.row.groupOrder }}</template>
        </el-table-column>
        <el-table-column label="组合工序" min-width="300">
          <template #default="scope">
            <div class="group-items">
              <ProcessOperation
                :items="operationItems(scope.row.items)"
                :remark="scope.row.remark"
              />
            </div>
            <div v-if="scope.row.items.length > 1" class="sub-items">
              <span
                v-for="(item, subIndex) in scope.row.items"
                :key="item.itemId ?? subIndex"
                class="sub-item"
              >
                {{ subSeq(scope.row, subIndex) }} {{ item.processName }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="工序类别" width="120" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.processCategoryName" type="info" size="small">{{
              scope.row.processCategoryName
            }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="工艺参数" min-width="180">
          <template #default="scope">
            <span v-if="printParamsText(scope.row.items)" style="color: #e6a23c"
              >🖨️ {{ printParamsText(scope.row.items) }}</span
            >
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="总人工工时" width="120" align="right">
          <template #default="scope">{{ scope.row.totalLaborHours }}</template>
        </el-table-column>
        <el-table-column label="总机器工时" width="120" align="right">
          <template #default="scope">{{ scope.row.totalMachineHours }}</template>
        </el-table-column>
        <el-table-column label="组合备注" min-width="200">
          <template #default="scope"
            ><span>{{ scope.row.remark || '-' }}</span></template
          >
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'
import { ProcessCategoryEnum } from '@/enums/product'
import { getDictLabel } from '@/utils/dict'
import { useDict } from '@/composables/useDict'
import ProcessOperation from '@/components/ProcessOperation/index.vue'
import type { ProcessOperationItem } from '@/components/ProcessOperation/types'
const props = defineProps<{ items: EngineeringRoutingItemVO[] }>()
const { options: categoryOptions } = useDict('process_category')
/** 印刷参数友好文本（2026-08-12）：组内任一行有参数即展示 */
function printParamsText(items?: any[]): string {
  if (!items?.length) return ''
  const parts: string[] = []
  for (const it of items) {
    if (!it.customProcessParams) continue
    try {
      const o = JSON.parse(it.customProcessParams)
      if (o.colorNo) parts.push(`色号:${o.colorNo}`)
      if (o.inkNo) parts.push(`油墨:${o.inkNo}`)
      if (o.screenNo) parts.push(`网框:${o.screenNo}`)
    } catch {
      /* ignore */
    }
  }
  return parts.join(' ')
}

function operationItems(items: EngineeringRoutingItemVO[]): ProcessOperationItem[] {
  return (items || []).map((item, index) => ({
    key: item.itemId ?? item.processId ?? index,
    icon: item.icon,
    processName: item.processName,
    indexNumber: item.hasIndex === 1 ? item.indexNumber : null,
    hasWorkInstruction: item.hasWorkInstruction,
    workInstruction: item.workInstruction,
  }))
}

/** 子件号：父工序号 + "." + 子件序（如 4.1 / 4.2） */
function subSeq(row: { groupOrder?: number | string }, index: number | string): string {
  return `${row?.groupOrder ?? ''}.${Number(index) + 1}`
}

interface GroupDisplay {
  groupOrder: number
  groupName: string
  items: EngineeringRoutingItemVO[]
  totalLaborHours: number
  totalMachineHours: number
  remark: string
  /** 组（workflow）序号：同组内 groupOrder 从 1 递增（排序用） */
  workflowSeq: number
  /** 原始组标识（process_category：PANEL/UP_LINE/DOWN_LINE…） */
  processCategory: string
  processCategoryName: string
}

/** 组（workflow）块：同一组的工序序号各自从 1 开始，界面按组分块显示 */
interface BlockDisplay {
  key: string
  label: string
  groups: GroupDisplay[]
}

const groups = computed(() => buildGroups(props.items))

const blockLabel = (category: string): string =>
  ProcessCategoryEnum.items.find((item) => item.value === category)?.label || category || '未分组'

// 按组（process_category，按明细返回顺序的连续段）分块：面板 / 上线 / 下线 / 未分组
const blocks = computed<BlockDisplay[]>(() => {
  const built: BlockDisplay[] = []
  const cats: string[] = []
  groups.value.forEach((group) => {
    const category = group.processCategory || ''
    const last = built[built.length - 1]
    if (last && cats[cats.length - 1] === category) {
      last.groups.push(group)
      return
    }
    cats.push(category)
    built.push({
      key: `block-${built.length}-${category}`,
      label: blockLabel(category),
      groups: [group],
    })
  })
  return built
})

function buildGroups(items: EngineeringRoutingItemVO[]): GroupDisplay[] {
  if (!items || items.length === 0) {
    return []
  }
  const hasParentStruct = items.some((item) => Array.isArray(item.children))

  if (hasParentStruct) {
    return (
      items
        .map((parent) => {
          const groupItems = parent.children?.length
            ? (parent.children as EngineeringRoutingItemVO[])
            : [parent]
          return {
            groupOrder: parent.processOrder || 0,
            groupName: parent.processName || '',
            items: groupItems,
            totalLaborHours: groupItems.reduce(
              (sum, item) => sum + (item.customLaborHours || item.standardLaborHours || 0),
              0
            ),
            totalMachineHours: groupItems.reduce(
              (sum, item) => sum + (item.customMachineHours || item.standardMachineHours || 0),
              0
            ),
            remark: parent.remark || '',
            workflowSeq: parent.workflowSeq ?? 1,
            processCategory: parent.processCategory || '',
            processCategoryName:
              getDictLabel(categoryOptions.value, parent.processCategory) ||
              parent.processCategoryName ||
              parent.processCategory ||
              '',
          }
        })
        // 排序键与后端明细一致：先组（workflow_seq）、再组内序号
        .sort((a, b) => a.workflowSeq - b.workflowSeq || a.groupOrder - b.groupOrder)
    )
  } else {
    const groupMap = new Map<string, EngineeringRoutingItemVO[]>()
    items.forEach((item) => {
      const key = item.groupId
        ? 'group_' + item.groupId
        : 'independent_' + (item.itemId || Math.random())
      if (!groupMap.has(key)) {
        groupMap.set(key, [])
      }
      groupMap.get(key)!.push(item)
    })
    const sortedEntries = Array.from(groupMap.entries()).sort((a, b) => {
      return (
        (a[1][0].workflowSeq ?? 1) - (b[1][0].workflowSeq ?? 1) ||
        (a[1][0].groupOrder || 0) - (b[1][0].groupOrder || 0)
      )
    })
    return sortedEntries.map(([, items]) => ({
      groupOrder: items[0].groupOrder || 0,
      groupName: items[0].groupName || '组合' + (items[0].groupOrder || ''),
      items: items,
      totalLaborHours: items.reduce(
        (sum, i) => sum + (i.customLaborHours || i.standardLaborHours || 0),
        0
      ),
      totalMachineHours: items.reduce(
        (sum, i) => sum + (i.customMachineHours || i.standardMachineHours || 0),
        0
      ),
      remark: items[0]?.remark || '',
      workflowSeq: items[0]?.workflowSeq ?? 1,
      processCategory: items[0]?.processCategory || '',
      processCategoryName: getDictLabel(categoryOptions.value, items[0]?.processCategory) || '',
    }))
  }
}
</script>
<style scoped>
.group-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px;
}
/* 组合工序的子件：不单独占号，显示成 4.1 / 4.2（dev-20260929-027） */
.sub-items {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 4px 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.sub-item {
  white-space: nowrap;
}
</style>
