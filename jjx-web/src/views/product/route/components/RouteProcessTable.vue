<template>
  <div class="route-process-table">
    <el-divider content-position="left">工序明细</el-divider>
    <el-tabs v-model="majorCategoryTab">
      <el-tab-pane :label="`🛠 冲型组装（${groups.length}）`" name="ASSEMBLY">
        <el-tabs v-model="assemblyActiveTab" type="border-card">
          <el-tab-pane
            v-for="tab in ROUTE_STRUCTURE_TABS"
            :key="tab.value"
            :name="tab.value"
            :label="`${tab.label}（${assemblyGroupsByTab(tab.value).length}）`"
          >
            <el-table
              :data="assemblyGroupsByTab(tab.value)"
              border
              stripe
              style="width: 100%"
              max-height="500"
            >
              <template #empty
                ><el-empty :description="`暂无${tab.label}冲型组装工序`" :image-size="80"
              /></template>
              <el-table-column label="序号" prop="groupOrder" width="70" align="center" />
              <el-table-column label="组合工序" min-width="460">
                <template #default="{ row }">
                  <div class="group-items">
                    <ProcessOperation :items="operationItems(row.items)" />
                  </div>
                  <div class="sub-items">
                    <span
                      v-for="(item, subIndex) in row.items"
                      :key="item.itemId || subIndex"
                      class="sub-item"
                    >
                      {{ row.items.length > 1 ? subSeq(row, subIndex) : '' }}
                      {{ item.processName || '-' }}
                      <span v-if="item.workInstruction" class="work-instruction"
                        >：{{ item.workInstruction }}</span
                      >
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="总人工工时(h)"
                prop="totalLaborHours"
                width="140"
                align="right"
              />
              <el-table-column
                label="总机器工时(h)"
                prop="totalMachineHours"
                width="140"
                align="right"
              />
              <el-table-column label="组合备注" min-width="180">
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
      <el-tab-pane :label="`🖨️ 印刷（${printRows.length}）`" name="PRINT">
        <el-tabs v-model="printActiveTab" type="border-card">
          <el-tab-pane
            v-for="tab in ROUTE_STRUCTURE_TABS"
            :key="tab.value"
            :name="tab.value"
            :label="`${tab.label}（${filteredPrintRows(tab.value).length}）`"
          >
            <el-table
              :data="filteredPrintRows(tab.value)"
              border
              stripe
              style="width: 100%"
              max-height="500"
            >
              <template #empty
                ><el-empty :description="`暂无${tab.label}印刷工序`" :image-size="80"
              /></template>
              <el-table-column label="序号" prop="processOrder" width="70" align="center" />
              <el-table-column label="印刷名称" min-width="180">
                <template #default="{ row }">
                  <div class="print-process">
                    <ProcessOperation v-if="row.icon" :items="operationItems([row])" />
                    <el-icon v-else class="print-icon" :size="24"><Printer /></el-icon>
                    <span>{{ row.processName || '-' }}</span>
                  </div>
                  <div v-if="row.workInstruction" class="work-instruction">
                    {{ row.workInstruction }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="色号" prop="colorNo" min-width="120" />
              <el-table-column label="油墨编号" prop="inkNo" min-width="120" />
              <el-table-column label="网框编号" prop="screenNo" min-width="120" />
              <el-table-column label="人工工时(h)" prop="laborHours" width="120" align="right" />
              <el-table-column label="机器工时(h)" prop="machineHours" width="120" align="right" />
              <el-table-column label="备注" min-width="180">
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Printer } from '@element-plus/icons-vue'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'
import { getDictLabel } from '@/utils/dict'
import { useDict } from '@/composables/useDict'
import ProcessOperation from '@/components/ProcessOperation/index.vue'
import type { ProcessOperationItem } from '@/components/ProcessOperation/types'
import { ROUTE_STRUCTURE_TABS, routeStructureTabValue } from './routeProcessTabs'

const props = defineProps<{ items: EngineeringRoutingItemVO[] }>()
const { options: categoryOptions } = useDict('process_category')
const majorCategoryTab = ref('ASSEMBLY')
const assemblyActiveTab = ref('PANEL')
const printActiveTab = ref('PANEL')

function operationItems(items: EngineeringRoutingItemVO[]): ProcessOperationItem[] {
  return items.map((item, index) => ({
    key: item.itemId || item.processId || index,
    icon: item.icon,
    processName: item.processName,
    hasIndex: item.hasIndex,
    indexNumber: item.hasIndex === 1 ? item.indexNumber : null,
    hasWorkInstruction: item.hasWorkInstruction,
    workInstruction: item.workInstruction,
  }))
}

function subSeq(row: { groupOrder: number }, index: number | string): string {
  return `${row.groupOrder}.${Number(index) + 1}`
}

interface GroupDisplay {
  groupOrder: number
  groupName: string
  items: EngineeringRoutingItemVO[]
  totalLaborHours: number
  totalMachineHours: number
  remark: string
  workflowSeq: number
  processCategory: string
  processCategoryName: string
}

const groups = computed(() =>
  buildGroups(props.items.filter((item) => item.majorCategory !== 'PRINT'))
)

function printParam(params: string | undefined, key: string): string {
  if (!params) return '-'
  try {
    const value = JSON.parse(params)?.[key]
    return typeof value === 'string' || typeof value === 'number' ? String(value) || '-' : '-'
  } catch {
    return '-'
  }
}

const printRows = computed(() =>
  props.items
    .filter((item) => item.majorCategory === 'PRINT')
    .map((item) => ({
      ...item,
      colorNo: printParam(item.customProcessParams, 'colorNo'),
      inkNo: printParam(item.customProcessParams, 'inkNo'),
      screenNo: printParam(item.customProcessParams, 'screenNo'),
      laborHours: item.customLaborHours ?? item.standardLaborHours ?? 0,
      machineHours: item.customMachineHours ?? item.standardMachineHours ?? 0,
    }))
    .sort((a, b) => (a.workflowSeq ?? 1) - (b.workflowSeq ?? 1) || a.processOrder - b.processOrder)
)

function assemblyGroupsByTab(value: string) {
  return groups.value.filter((group) => routeStructureTabValue(group.processCategory) === value)
}
function filteredPrintRows(value: string) {
  return printRows.value.filter((row) => routeStructureTabValue(row.processCategory) === value)
}

// 每次加载路线数据时选择第一个有数据的页签，空路线保留默认页签和空状态。
watch(
  () => props.items,
  () => {
    assemblyActiveTab.value =
      ROUTE_STRUCTURE_TABS.find((tab) => assemblyGroupsByTab(tab.value).length)?.value ?? 'PANEL'
    printActiveTab.value =
      ROUTE_STRUCTURE_TABS.find((tab) => filteredPrintRows(tab.value).length)?.value ?? 'PANEL'
    majorCategoryTab.value = groups.value.length || !printRows.value.length ? 'ASSEMBLY' : 'PRINT'
  },
  { immediate: true }
)

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
    items.forEach((item, index) => {
      const key = item.groupId ? 'group_' + item.groupId : 'independent_' + index
      if (!groupMap.has(key)) {
        groupMap.set(key, [])
      }
      groupMap.get(key)!.push(item)
    })
    const sortedEntries = Array.from(groupMap.entries()).sort((a, b) => {
      return (
        (a[1][0].workflowSeq ?? 1) - (b[1][0].workflowSeq ?? 1) ||
        (a[1][0].groupOrder || a[1][0].processOrder || 0) -
          (b[1][0].groupOrder || b[1][0].processOrder || 0)
      )
    })
    return sortedEntries.map(([, items]) => ({
      groupOrder: items[0].groupOrder || items[0].processOrder || 0,
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
.group-items,
.print-process {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
}
.group-items {
  overflow-x: auto;
}
.sub-items {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 4px 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.work-instruction {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.print-icon {
  color: var(--el-color-primary);
  flex-shrink: 0;
}
</style>
