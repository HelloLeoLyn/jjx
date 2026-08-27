<template>
  <div class="route-item-editor">
    <el-divider content-position="left">工序明细</el-divider>

    <!-- 添加工序区域 -->
    <div class="add-process-bar">
      <el-row :gutter="10">
        <el-col :span="6">
          <el-select
            v-model="selectedProcessId"
            placeholder="请选择标准工序"
            filterable
            clearable
            style="width: 100%"
            @change="handleProcessSelect"
          >
            <el-option-group
              v-for="group in groupedProcesses"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="item in group.options"
                :key="item.processId"
                :label="`${item.processCode} - ${item.processName}`"
                :value="item.processId"
              >
                <SvgIcon :name="item.icon"></SvgIcon
                ><span>{{ item.processCode }} - {{ item.processName }}</span>
                <!-- <span class="option-tag">{{ item.processTypeName }}</span> -->
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
        <el-col :span="6">
          <el-input
            v-model="addDescription"
            placeholder="工序说明（可选）"
            clearable
            style="width: 100%"
          />
        </el-col>
        <el-col :span="4">
          <el-button type="primary" icon="Plus" @click="addItem" :disabled="!selectedProcessId">
            添加工序
          </el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 工序明细表格 -->
    <el-table :data="items" border stripe style="width: 100%; margin-top: 10px" max-height="400">
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="工序编码" prop="processCode" width="120" />
      <el-table-column label="工序名称" prop="processName" width="150" />
      <!-- <el-table-column label="工序类型" prop="processTypeName" width="100" />
      <el-table-column label="工序类别" prop="processCategoryName" width="100" /> -->
      <el-table-column label="标准人工工时" prop="standardLaborHours" width="110" align="right" />
      <el-table-column label="标准机器工时" prop="standardMachineHours" width="110" align="right" />
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
      <el-table-column label="工艺参数" width="180">
        <template #default="scope">
          <ProcessParamsEditor
            v-model="scope.row.customProcessParams"
            :template="scope.row.processParamTemplate"
          />
        </template>
      </el-table-column>
      <el-table-column label="说明">
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
      <el-empty description="请从上方选择标准工序添加到路线中" :image-size="80" />
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

// 获取工序类别字典选项（带 Pinia 缓存�?
const { options: processCategoryOptions } = useDict('process_category')

// 本地数据
const items = ref<EngineeringRoutingItemVO[]>([])
const selectedProcessId = ref<number | undefined>(undefined)
const addLaborHours = ref<number>(0)
const addMachineHours = ref<number>(0)
const addDescription = ref('')

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

// 监听外部数据变化，同步到本地（仅在本地数据为空时�?
watch(
  () => props.modelValue,
  (val) => {
    if (val && val.length > 0 && items.value.length === 0) {
      items.value = JSON.parse(JSON.stringify(val))
    }
  },
  { immediate: true, deep: false }
)

// 选择标准工序时自动填�?
const handleProcessSelect = (processId: number) => {
  const process = props.standardProcesses.find((p) => p.processId === processId)
  if (process) {
    addLaborHours.value = process.standardLaborHours || 0
    addMachineHours.value = process.standardMachineHours || 0
  }
}

// 同步数据到父组件（使�?setTimeout 避免递归更新�?
const syncToParent = () => {
  setTimeout(() => {
    emit('update:modelValue', JSON.parse(JSON.stringify(items.value)))
  }, 0)
}

// 添加工序
const addItem = () => {
  if (!selectedProcessId.value) {
    ElMessage.warning('请先选择标准工序')
    return
  }

  const process = props.standardProcesses.find((p) => p.processId === selectedProcessId.value)
  if (!process) return

  // 检查是否已添加
  const exists = items.value.some((item) => item.processId === process.processId)
  if (exists) {
    ElMessage.warning('该工序已添加，请勿重复添加')
    return
  }

  const newItem: EngineeringRoutingItemVO = {
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
  }

  items.value.push(newItem)

  // 重置添加区域
  selectedProcessId.value = undefined
  addLaborHours.value = 0
  addMachineHours.value = 0
  addDescription.value = ''

  // 更新序号
  updateOrder()
  syncToParent()
}

// 上移
const moveUp = (index: number) => {
  if (index <= 0) return
  const temp = items.value[index]
  items.value[index] = items.value[index - 1]
  items.value[index - 1] = temp
  updateOrder()
  syncToParent()
}

// 下移
const moveDown = (index: number) => {
  if (index >= items.value.length - 1) return
  const temp = items.value[index]
  items.value[index] = items.value[index + 1]
  items.value[index + 1] = temp
  updateOrder()
  syncToParent()
}

// 删除工序
const removeItem = (index: number) => {
  items.value.splice(index, 1)
  updateOrder()
  syncToParent()
}

// 更新序号
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
</style>
