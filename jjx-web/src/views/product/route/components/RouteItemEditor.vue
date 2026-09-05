<template>
  <div class="route-item-editor">
    <el-divider content-position="left">工序明细（支持组合工序：一行工序 = 可含多个作业项）</el-divider>

    <!-- 添加工序区域 -->
    <div class="add-process-bar">
      <el-row :gutter="10">
        <el-col :span="5">
          <el-select
            v-model="selectedProcessId"
            placeholder="请选择标准工序"
            filterable
            clearable
            style="width: 100%"
            @change="handleProcessSelect"
          >
            <el-option-group v-for="group in groupedProcesses" :key="group.label" :label="group.label">
              <el-option
                v-for="item in group.options"
                :key="item.processId"
                :label="`${item.processCode} - ${item.processName}`"
                :value="item.processId"
              >
                <SvgIcon :name="item.icon"></SvgIcon
                ><span>{{ item.processCode }} - {{ item.processName }}</span>
              </el-option>
            </el-option-group>
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-input-number
            v-model="addLaborHours"
            :min="0"
            :precision="2"
            :step="0.1"
            placeholder="人工工时"
            style="width: 100%"
          />
        </el-col>
        <el-col :span="4">
          <el-input-number
            v-model="addMachineHours"
            :min="0"
            :precision="2"
            :step="0.1"
            placeholder="机器工时"
            style="width: 100%"
          />
        </el-col>
        <el-col :span="5">
          <el-input
            v-model="addDescription"
            placeholder="工序说明（可选）"
            clearable
            style="width: 100%"
          />
        </el-col>
        <el-col :span="6">
          <el-button type="primary" icon="Plus" @click="addItem" :disabled="!selectedProcessId">
            添加工序
          </el-button>
          <el-button type="success" icon="Files" @click="addComboItem">
            ＋ 组合工序
          </el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 工序明细表格（父子：父行=工序，expand 展开组合作业项） -->
    <el-table
      :data="items"
      border
      stripe
      style="width: 100%; margin-top: 10px"
      max-height="460"
      row-key="itemId"
      @expand-change="onExpandChange"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div v-if="!row.processId && row.majorCategory !== 'PRINT'" class="combo-jobs">
            <div class="combo-jobs-head">
              <span class="combo-jobs-title">🧩 组合工序「{{ row.processName || '未命名' }}」的作业项</span>
              <span style="flex: 1"></span>
              <el-select
                v-model="jobProcessId"
                placeholder="选择作业项（标准工序）"
                filterable
                size="small"
                style="width: 240px"
                @change="handleJobSelect"
              >
                <el-option-group v-for="group in groupedProcesses" :key="group.label" :label="group.label">
                  <el-option
                    v-for="item in group.options"
                    :key="item.processId"
                    :label="`${item.processCode} - ${item.processName}`"
                    :value="item.processId"
                  />
                </el-option-group>
              </el-select>
              <el-button type="primary" size="small" icon="Plus" :disabled="!jobProcessId" @click="addJob(row)">
                添加作业项
              </el-button>
            </div>
            <el-table v-if="row.children && row.children.length" :data="row.children" size="small" border>
              <el-table-column label="作业序号" type="index" width="70" align="center" />
              <el-table-column label="作业名称" prop="processName" min-width="160" />
              <el-table-column label="定制人工工时" width="140" align="center">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.customLaborHours"
                    :min="0"
                    :precision="2"
                    :step="0.1"
                    size="small"
                    controls-position="right"
                    style="width: 110px"
                  />
                </template>
              </el-table-column>
              <el-table-column label="定制机器工时" width="140" align="center">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.customMachineHours"
                    :min="0"
                    :precision="2"
                    :step="0.1"
                    size="small"
                    controls-position="right"
                    style="width: 110px"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="scope">
                  <el-button
                    link
                    type="danger"
                    icon="Delete"
                    @click="removeJob(row, scope.$index)"
                  />
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="combo-jobs-empty">还没有作业项，从上方选择标准工序后点「添加作业项」</div>
          </div>
          <div v-else-if="row.majorCategory === 'PRINT'" class="combo-jobs-empty">
            印刷工序（自定义参数在「工艺参数」列编辑）
          </div>
          <div v-else class="combo-jobs-empty">普通工序（单作业），如需组合请删除后添加「组合工序」再组合作业项</div>
        </template>
      </el-table-column>
      <el-table-column label="序号" type="index" width="55" align="center" />
      <el-table-column label="工序名称" min-width="190">
        <template #default="{ row }">
          <el-input
            v-if="!row.processId"
            v-model="row.processName"
            size="small"
            placeholder="组合工序名称（如：冲孔+贴合）"
            @change="syncToParent"
          />
          <span v-else>{{ row.processName }}</span>
          <el-tag v-if="row.children && row.children.length" size="small" type="warning" style="margin-left: 6px">
            组合·{{ row.children.length }}作业
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="定制人工工时" width="120" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.customLaborHours"
            :min="0"
            :precision="2"
            :step="0.1"
            size="small"
            controls-position="right"
            style="width: 100px"
          />
        </template>
      </el-table-column>
      <el-table-column label="定制机器工时" width="120" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.customMachineHours"
            :min="0"
            :precision="2"
            :step="0.1"
            size="small"
            controls-position="right"
            style="width: 100px"
          />
        </template>
      </el-table-column>
      <el-table-column label="工艺参数" width="170">
        <template #default="scope">
          <ProcessParamsEditor
            v-model="scope.row.customProcessParams"
            :template="scope.row.processParamTemplate"
          />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="120">
        <template #default="scope">
          <el-input v-model="scope.row.description" size="small" placeholder="工序说明" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Top"
            @click="moveUp(scope.$index)"
            :disabled="scope.$index === 0"
          />
          <el-button
            link
            type="primary"
            icon="Bottom"
            @click="moveDown(scope.$index)"
            :disabled="scope.$index === items.length - 1"
          />
          <el-button link type="danger" icon="Delete" @click="removeItem(scope.$index)" />
        </template>
      </el-table-column>
    </el-table>

    <div class="empty-tip" v-if="items.length === 0">
      <el-empty description="请从上方选择标准工序添加，或点「＋ 组合工序」添加组合工序" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { StandardProcessOption } from '@/types/product'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'
import ProcessParamsEditor from './ProcessParamsEditor.vue'
import { getDictLabel } from '@/utils/dict'
import { useDict } from '@/composables/useDict'

const props = defineProps<{
  modelValue: EngineeringRoutingItemVO[]
  standardProcesses: StandardProcessOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [items: EngineeringRoutingItemVO[]]
}>()

// 获取工序类别字典选项（带 Pinia 缓存）
const { options: processCategoryOptions } = useDict('process_category')

// 本地数据
const items = ref<EngineeringRoutingItemVO[]>([])
const selectedProcessId = ref<number | undefined>(undefined)
const addLaborHours = ref<number>(0)
const addMachineHours = ref<number>(0)
const addDescription = ref('')
// 组合作业项添加（作用于当前展开的组合行）
const expandedRow = ref<any>(null)
const jobProcessId = ref<number | undefined>(undefined)
const jobLaborHours = ref<number>(0)
const jobMachineHours = ref<number>(0)

// 按工序类别分组的标准工序
const groupedProcesses = computed(() => {
  const groups = new Map<string, StandardProcessOption[]>()
  props.standardProcesses.forEach((p) => {
    const key = p.processCategory
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)!.push(p)
  })
  return Array.from(groups.entries()).map(([key, options]) => ({
    label: getDictLabel(processCategoryOptions.value, key) || key,
    options,
  }))
})

// 监听外部数据变化，同步到本地（仅在本地数据为空时）
watch(
  () => props.modelValue,
  (val) => {
    if (val && val.length > 0 && items.value.length === 0) {
      items.value = JSON.parse(JSON.stringify(val))
    }
  },
  { immediate: true, deep: false }
)

// 选择标准工序时自动填工时
const handleProcessSelect = (processId: number) => {
  const process = props.standardProcesses.find((p) => p.processId === processId)
  if (process) {
    addLaborHours.value = process.standardLaborHours || 0
    addMachineHours.value = process.standardMachineHours || 0
  }
}

// 同步数据到父组件（使用 setTimeout 避免递归更新）
const syncToParent = () => {
  setTimeout(() => {
    emit('update:modelValue', JSON.parse(JSON.stringify(items.value)))
  }, 0)
}

function buildRowFromProcess(process: StandardProcessOption): any {
  return {
    itemId: 0,
    routingId: 0,
    processOrder: items.value.length + 1,
    customLaborHours: addLaborHours.value || process.standardLaborHours || 0,
    customMachineHours: addMachineHours.value || process.standardMachineHours || 0,
    customProcessParams: process.processParamTemplate || '',
    description: addDescription.value || process.description || '',
    createTime: '',
    updateTime: '',
    processId: process.processId,
    processCode: process.processCode,
    processName: process.processName,
    processType: process.processType,
    processTypeName: process.processTypeName,
    processTypeTagType: '',
    processCategory: process.processCategory || '',
    processCategoryName: process.processCategoryName || '',
    processCategoryTagType: '',
    standardLaborHours: process.standardLaborHours || 0,
    standardMachineHours: process.standardMachineHours || 0,
    processParamTemplate: process.processParamTemplate || '',
    skillRequirement: process.skillRequirement || '',
    equipmentType: process.equipmentType || '',
    qualityStandard: process.qualityStandard || '',
    isEnabled: process.isEnabled,
    isEnabledName: process.isEnabled === 1 ? '启用' : '停用',
    isEnabledTagType: process.isEnabled === 1 ? 'success' : 'info',
    displayOrder: process.displayOrder,
    children: [],
  }
}

// 添加工序（普通单作业）
const addItem = () => {
  if (!selectedProcessId.value) {
    ElMessage.warning('请先选择标准工序')
    return
  }
  const process = props.standardProcesses.find((p) => p.processId === selectedProcessId.value)
  if (!process) return
  items.value.push(buildRowFromProcess(process))
  resetAddBar()
  updateOrder()
  syncToParent()
}

// 添加组合工序（父行壳，名称手填，作业项在展开区添加）
const addComboItem = () => {
  items.value.push({
    itemId: 0,
    routingId: 0,
    processOrder: items.value.length + 1,
    customLaborHours: 0,
    customMachineHours: 0,
    customProcessParams: '',
    description: '',
    createTime: '',
    updateTime: '',
    processId: undefined as any,
    processCode: '',
    processName: '',
    processType: '',
    processTypeName: '',
    processTypeTagType: '',
    processCategory: '',
    processCategoryName: '',
    processCategoryTagType: '',
    standardLaborHours: 0,
    standardMachineHours: 0,
    processParamTemplate: '',
    skillRequirement: '',
    equipmentType: '',
    qualityStandard: '',
    isEnabled: 1,
    isEnabledName: '启用',
    isEnabledTagType: 'success',
    displayOrder: 0,
    children: [],
  } as any)
  updateOrder()
  syncToParent()
}

const resetAddBar = () => {
  selectedProcessId.value = undefined
  addLaborHours.value = 0
  addMachineHours.value = 0
  addDescription.value = ''
}

// ==================== 组合作业项 ====================

const onExpandChange = (row: any) => {
  expandedRow.value = row
  jobProcessId.value = undefined
  jobLaborHours.value = 0
  jobMachineHours.value = 0
}

const handleJobSelect = (processId: number) => {
  const process = props.standardProcesses.find((p) => p.processId === processId)
  if (process) {
    jobLaborHours.value = process.standardLaborHours || 0
    jobMachineHours.value = process.standardMachineHours || 0
  }
}

const addJob = (row: any) => {
  if (!jobProcessId.value) {
    ElMessage.warning('请先选择作业项')
    return
  }
  const process = props.standardProcesses.find((p) => p.processId === jobProcessId.value)
  if (!process) return
  if (!row.children) row.children = []
  row.children.push({
    itemId: 0,
    routingId: 0,
    processId: process.processId,
    processCode: process.processCode,
    processName: process.processName,
    customLaborHours: jobLaborHours.value || process.standardLaborHours || 0,
    customMachineHours: jobMachineHours.value || process.standardMachineHours || 0,
    customProcessParams: process.processParamTemplate || '',
    description: '',
    processCategory: process.processCategory || '',
    majorCategory: process.processCategory === 'PRINT' ? 'PRINT' : 'ASSEMBLY',
    standardLaborHours: process.standardLaborHours || 0,
    standardMachineHours: process.standardMachineHours || 0,
  } as any)
  // 组合父行工时 = 作业项工时合计
  recalcComboHours(row)
  jobProcessId.value = undefined
  jobLaborHours.value = 0
  jobMachineHours.value = 0
  syncToParent()
}

const removeJob = (row: any, index: number) => {
  row.children.splice(index, 1)
  recalcComboHours(row)
  syncToParent()
}

/** 组合工序父行工时 = Σ 作业项工时 */
const recalcComboHours = (row: any) => {
  if (!row.children || !row.children.length) return
  row.customLaborHours = row.children.reduce(
    (s: number, c: any) => s + Number(c.customLaborHours || 0),
    0
  )
  row.customMachineHours = row.children.reduce(
    (s: number, c: any) => s + Number(c.customMachineHours || 0),
    0
  )
}

// ==================== 行操作 ====================

const moveUp = (index: number) => {
  if (index <= 0) return
  const temp = items.value[index]
  items.value[index] = items.value[index - 1]
  items.value[index - 1] = temp
  updateOrder()
  syncToParent()
}

const moveDown = (index: number) => {
  if (index >= items.value.length - 1) return
  const temp = items.value[index]
  items.value[index] = items.value[index + 1]
  items.value[index + 1] = temp
  updateOrder()
  syncToParent()
}

const removeItem = (index: number) => {
  items.value.splice(index, 1)
  updateOrder()
  syncToParent()
}

const updateOrder = () => {
  items.value.forEach((item, index) => {
    item.processOrder = index + 1
  })
}

// 暴露方法给父组件
defineExpose({
  getItems: () => JSON.parse(JSON.stringify(items.value)),
  setItems: (data: EngineeringRoutingItemVO[]) => {
    items.value = JSON.parse(JSON.stringify(data))
  },
})
</script>

<style scoped>
.route-item-editor {
  margin-top: 10px;
}

.add-process-bar {
  margin-bottom: 10px;
}

.option-tag {
  float: right;
  color: #909399;
  font-size: 12px;
}

.empty-tip {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.combo-jobs {
  padding: 8px 16px 12px 56px;
}

.combo-jobs-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.combo-jobs-title {
  font-weight: 600;
  color: #b88230;
  font-size: 13px;
}

.combo-jobs-empty {
  padding: 6px 0 6px 56px;
  color: #909399;
  font-size: 12px;
}
</style>
