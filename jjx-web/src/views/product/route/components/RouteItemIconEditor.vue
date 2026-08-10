<template>
  <div class="route-item-icon-editor">
    <el-divider content-position="left">工序明细（拖拽图标到表格添加工序）</el-divider>

    <!-- 上方：图标选择区（Tabs + 拖拽源） -->
    <div class="icon-selector-area">
      <div class="selector-toolbar">
        <el-radio-group v-model="groupMode" size="small">
          <el-radio-button value="category">按工序类别</el-radio-button>
          <el-radio-button value="type">按工序类型</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索工序名称/编码..."
          clearable
          size="small"
          style="width: 220px"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane
          v-for="group in groupedProcesses"
          :key="group.key"
          :label="group.label"
          :name="group.key"
        >
          <div class="icon-grid">
            <div
              v-for="process in group.options"
              :key="process.processId"
              class="icon-item"
              draggable="true"
              @dragstart="handleDragStart($event, process)"
              @click="addToNewGroup(process)"
            >
              <SvgIcon v-if="process.icon" :name="process.icon" :size="32" />
              <span class="icon-name">{{ process.processName }}</span>
            </div>
          </div>
          <el-empty
            v-if="group.options.length === 0"
            description="该分类下暂无工序"
            :image-size="60"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 下方：组合工序表格（拖拽目标）-->
    <div
      class="table-drop-zone"
      @dragover.prevent="handleDragOver"
      @dragleave="handleDragLeave"
      @drop="handleDropOnTable"
      :class="{ 'drag-over': isDragOverTable }"
    >
      <el-table
        :data="groups"
        border
        stripe
        style="width: 100%"
        max-height="500"
        @dragover.prevent="handleDragOver"
        @drop="handleDropOnTable"
      >
        <!-- 空数据时显示拖拽提示 -->
        <template #empty>
          <div class="drop-zone-empty" @dragover.prevent="handleDragOver" @drop="handleDropOnTable">
            <el-empty description="拖拽图标到此处创建工序组" :image-size="80" />
          </div>
        </template>
        <el-table-column label="序号" width="60" align="center">
          <template #default="scope">
            {{ scope.row.groupOrder }}
          </template>
        </el-table-column>

        <el-table-column label="组合工序" min-width="460">
          <template #default="scope">
            <div
              class="group-items"
              @dragover.prevent="handleDragOverGroup($event, scope.$index)"
              @drop="handleDropOnGroup($event, scope.$index)"
              :class="{ 'drag-over': dragOverGroupIndex === scope.$index }"
            >
              <div
                v-for="(item, itemIndex) in scope.row.items"
                :key="item.itemId ?? `${item.processId}_${itemIndex}`"
                class="group-item-row"
                draggable="true"
                @dragstart="handleItemDragStart($event, scope.$index, Number(itemIndex))"
                @dragover.prevent="handleItemDragOver($event, scope.$index, Number(itemIndex))"
                @drop="handleItemDrop($event, scope.$index, Number(itemIndex))"
              >
                <!-- 有下标（hasIndex=1）：IconStepBadge 显示图标+红底数字 -->
                <IconStepBadge
                  v-if="item.hasIndex === 1"
                  :icon="item.icon || ''"
                  :size="18"
                  :index="item.indexNumber ?? null"
                  @update:index="(n: number) => onUpdateIndex(scope.row, item, n)"
                />
                <!-- 无下标：只显示工序名称 -->
                <span v-else class="item-name">{{ item.processName }}</span>
                <el-icon class="item-close" @click="removeItemFromGroup(scope.$index, Number(itemIndex))">
                  <Close />
                </el-icon>
              </div>
              <span class="drop-hint">拖拽图标到此处加入组</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="工序类别" width="160">
          <template #default="scope">
            <el-select
              v-model="scope.row.processCategory"
              placeholder="请选择工序类别"
              size="small"
              clearable
              style="width: 140px"
              @change="(val: string) => handleProcessCategoryChange(scope.$index, val)"
            >
              <el-option
                v-for="item in ProcessCategoryEnum.items"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="总人工工时" width="120" align="center">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.totalLaborHours"
              :min="0"
              :precision="2"
              :step="0.1"
              size="small"
              controls-position="right"
              style="width: 100px"
            />
          </template>
        </el-table-column>

        <el-table-column label="总机器工时" width="120" align="center">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.totalMachineHours"
              :min="0"
              :precision="2"
              :step="0.1"
              size="small"
              controls-position="right"
              style="width: 100px"
            />
          </template>
        </el-table-column>

        <el-table-column label="组合备注" width="380">
          <template #default="scope">
            <el-input
              v-model="scope.row.remark"
              size="small"
              placeholder="组合备注（存到第一条工序的说明中）"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="Top"
              @click="moveGroupUp(scope.$index)"
              :disabled="scope.$index === 0"
            />
            <el-button
              link
              type="primary"
              icon="Bottom"
              @click="moveGroupDown(scope.$index)"
              :disabled="scope.$index === groups.length - 1"
            />
            <el-button link type="danger" icon="Delete" @click="removeGroup(scope.$index)" />
          </template>
        </el-table-column>
      </el-table>
      <div
        class="table-bottom-drop-zone"
        @dragover.prevent="handleDragOver"
        @dragleave="handleDragLeave"
        @drop="handleDropOnTable"
        :class="{ 'drag-over': isDragOverTable }"
      >
        <span class="drop-zone-text">+ 拖拽图标到此处新增组合</span>
      </div>
    </div>

    <!-- 下标工序：输入下标数字弹窗（has_index=1 的工序拖入时弹出） -->
    <el-dialog v-model="indexDialogVisible" title="输入下标数字" width="380px" append-to-body>
      <div style="font-size: 13px; color: #606266; margin-bottom: 12px">
        工序 <b>{{ indexDialogProcessName }}</b> 带下标，请输入下标数字（正整数）：
      </div>
      <el-input-number
        v-model="indexDialogValue"
        :min="1"
        :max="999"
        :precision="0"
        controls-position="right"
        style="width: 100%"
        placeholder="如 4 显示为 ④"
      />
      <template #footer>
        <el-button @click="indexDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!indexDialogValue" @click="confirmIndexDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Close } from '@element-plus/icons-vue'
import type { StandardProcessOption } from '@/types/product'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'
import { ProcessTypeEnum, ProcessCategoryEnum } from '@/enums/product'
import IconStepBadge from '@/components/IconStepBadge/index.vue'

// ==================== 类型定义 ====================

/** 组合工序 */
interface RouteItemGroup {
  groupOrder: number
  items: EngineeringRoutingItemVO[]
  totalLaborHours: number
  totalMachineHours: number
  remark: string
  /** 工序类别（字典表 process_category） */
  processCategory?: string
}

// ==================== Props & Emits ====================

const props = defineProps<{
  modelValue: EngineeringRoutingItemVO[]
  standardProcesses: StandardProcessOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [items: EngineeringRoutingItemVO[]]
}>()

// ==================== 状态 ====================

/** 分组维度：category=按工序类别 / type=按工序类型 */
const groupMode = ref<'category' | 'type'>('category')

/** 搜索关键字（按名称/编码过滤图标） */
const searchKeyword = ref('')

const activeTab = ref('')
const groups = ref<RouteItemGroup[]>([])
const isDragOverTable = ref(false)
const dragOverGroupIndex = ref<number | null>(null)

// 分组维度切换时重置选中tab
watch(groupMode, () => {
  activeTab.value = ''
})

// 拖拽数据
let draggedProcess: StandardProcessOption | null = null
let draggedItemInfo: { groupIndex: number; itemIndex: number } | null = null

// ==================== 下标工序弹窗（批次1） ====================
const indexDialogVisible = ref(false)
const indexDialogValue = ref<number | null>(null)
const indexDialogProcessName = ref('')
let pendingIndexItem: EngineeringRoutingItemVO | null = null

// 打开下标输入弹窗（has_index=1 的工序拖入/点击图标时）
const openIndexDialog = (item: EngineeringRoutingItemVO) => {
  pendingIndexItem = item
  indexDialogProcessName.value = item.processName || ''
  indexDialogValue.value = item.indexNumber ?? null
  indexDialogVisible.value = true
}

// 确认下标数字：写入 item.indexNumber
const confirmIndexDialog = () => {
  if (pendingIndexItem && indexDialogValue.value != null && indexDialogValue.value > 0) {
    // 注意：pendingIndexItem 是 push 前的原始对象引用，直接改属性不触发视图更新
    // 用 reactive() 取回 Vue 包装的响应式代理再改（已 push 进 groups 的对象在 reactiveMap 有缓存）
    const target = reactive(pendingIndexItem)
    target.indexNumber = Math.floor(indexDialogValue.value)
    syncToParent()
  }
  indexDialogVisible.value = false
  pendingIndexItem = null
}

// IconStepBadge 下标变更（点击工序图标改下标数字）
const onUpdateIndex = (group: RouteItemGroup, item: EngineeringRoutingItemVO, n: number) => {
  item.indexNumber = Math.floor(n)
  syncToParent()
}

// 临时 groupId 计数器（用于生成唯一的临时负数ID）
let tempGroupIdCounter = 0

const generateTempItemId = (): number => {
  tempGroupIdCounter++
  return -(Date.now() + tempGroupIdCounter + 100000)
}

// ==================== 计算属性 ====================

// 按所选维度分组的标准工序（类别或类型），支持关键字过滤
const groupedProcesses = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  const filtered = kw
    ? props.standardProcesses.filter((p) =>
        (p.processName || '').toLowerCase().includes(kw)
        || (p.processCode || '').toLowerCase().includes(kw))
    : props.standardProcesses
  const groups = new Map<string, StandardProcessOption[]>()
  filtered.forEach((p) => {
    const key = groupMode.value === 'category' ? p.processCategory : p.processType
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)!.push(p)
  })
  const result = Array.from(groups.entries()).map(([key, options]) => ({
    key,
    label: groupMode.value === 'category'
      ? ProcessCategoryEnum.getLabel(key)
      : ProcessTypeEnum.getLabel(key),
    options,
  }))
  // 默认选中第一个tab
  if (result.length > 0 && !activeTab.value) {
    activeTab.value = result[0].key
  }
  return result
})

// ==================== 监听 ====================

watch(
  () => props.modelValue,
  (val) => {
    if (val && val.length > 0 && groups.value.length === 0) {
      // 根据 groupId 重新组装为组合格式
      setItemsFromData(val)
    }
  },
  { immediate: true, deep: false }
)

// ==================== 数据组装 ====================

/**
 * 根据后端返回的 items（含 groupId）重新组装为 groups
 */
const setItemsFromData = (data: EngineeringRoutingItemVO[]) => {
  // 按 groupId 分组：有 groupId 的按组合分组，没有的各自独立
  const groupMap = new Map<string, EngineeringRoutingItemVO[]>()

  data.forEach((item) => {
    // 有 groupId 的按组合分组，没有的各自独立（用 itemId 或 index 作为key）
    const key = item.groupId
      ? `group_${item.groupId}`
      : `independent_${item.itemId || Math.random()}`
    if (!groupMap.has(key)) {
      groupMap.set(key, [])
    }
    groupMap.get(key)!.push(item)
  })

  // 转换为 groups，按 groupOrder 排序
  const sortedEntries = Array.from(groupMap.entries()).sort((a, b) => {
    const orderA = a[1][0].groupOrder || 0
    const orderB = b[1][0].groupOrder || 0
    return orderA - orderB
  })

  groups.value = sortedEntries.map(([, items], index) => ({
    groupOrder: items[0].groupOrder || index + 1,
    items: items,
    totalLaborHours: items.reduce(
      (sum, i) => sum + (i.customLaborHours || i.standardLaborHours || 0),
      0
    ),
    totalMachineHours: items.reduce(
      (sum, i) => sum + (i.customMachineHours || i.standardMachineHours || 0),
      0
    ),
    // 从第一条工序的 description 读取组合备注
    remark: items[0]?.description || '',
    // 从第一条工序的 processCategory 读取工序类别
    processCategory: items[0]?.processCategory || '',
  }))

  updateGroupOrder()
}

/**
 * 生成临时 groupId（负数，用于前端标识同一组合）
 */
const generateTempGroupId = (): number => {
  tempGroupIdCounter++
  return -(Date.now() + tempGroupIdCounter)
}

// ==================== 拖拽事件 ====================

// 开始拖拽图标
const handleDragStart = (event: DragEvent, process: StandardProcessOption) => {
  draggedProcess = process
  draggedItemInfo = null
  if (event.dataTransfer) {
    event.dataTransfer.setData('text/plain', String(process.processId))
    event.dataTransfer.effectAllowed = 'copy'
  }
}

// 开始拖拽组合内的工序
const handleItemDragStart = (event: DragEvent, groupIndex: number, itemIndex: number) => {
  draggedProcess = null
  draggedItemInfo = { groupIndex, itemIndex }
  if (event.dataTransfer) {
    event.dataTransfer.setData('text/plain', 'item')
    event.dataTransfer.effectAllowed = 'move'
  }
}

// 拖拽经过表格区域
const handleDragOver = (event: DragEvent) => {
  event.preventDefault()
  isDragOverTable.value = true
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
}

// 拖拽离开表格区域
const handleDragLeave = () => {
  isDragOverTable.value = false
}

// 拖拽经过组合区域
const handleDragOverGroup = (event: DragEvent, groupIndex: number) => {
  event.preventDefault()
  dragOverGroupIndex.value = groupIndex
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = draggedItemInfo ? 'move' : 'copy'
  }
}

// 拖拽经过组合内的工序标签
const handleItemDragOver = (event: DragEvent, groupIndex: number, itemIndex: number) => {
  event.preventDefault()
  dragOverGroupIndex.value = groupIndex
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = draggedItemInfo ? 'move' : 'copy'
  }
}

// 拖拽放置到表格空白区域（创建新组合）
const handleDropOnTable = (event: DragEvent) => {
  event.preventDefault()
  isDragOverTable.value = false
  dragOverGroupIndex.value = null

  if (draggedProcess) {
    addToNewGroup(draggedProcess)
    draggedProcess = null
  }
}

// 拖拽放置到组合区域（加入组合）
const handleDropOnGroup = (event: DragEvent, groupIndex: number) => {
  event.preventDefault()
  dragOverGroupIndex.value = null

  if (draggedProcess) {
    addToGroup(groupIndex, draggedProcess)
    draggedProcess = null
  } else if (draggedItemInfo) {
    // 移动组合内的工序到另一个组
    moveItemBetweenGroups(draggedItemInfo.groupIndex, draggedItemInfo.itemIndex, groupIndex)
    draggedItemInfo = null
  }
}

// 拖拽放置到组合内的工序标签上（排序）
const handleItemDrop = (event: DragEvent, targetGroupIndex: number, targetItemIndex: number) => {
  event.preventDefault()
  dragOverGroupIndex.value = null

  if (draggedProcess) {
    // 将新工序插入到目标位置
    addToGroupAtIndex(targetGroupIndex, draggedProcess, targetItemIndex)
    draggedProcess = null
  } else if (draggedItemInfo) {
    // 在组合内移动工序位置
    if (draggedItemInfo.groupIndex === targetGroupIndex) {
      moveItemWithinGroup(targetGroupIndex, draggedItemInfo.itemIndex, targetItemIndex)
    } else {
      moveItemBetweenGroups(draggedItemInfo.groupIndex, draggedItemInfo.itemIndex, targetGroupIndex)
    }
    draggedItemInfo = null
  }
}

// ==================== 操作方法 ====================

// 创建标准工序VO
const createItemVO = (process: StandardProcessOption): EngineeringRoutingItemVO => {
  return {
    itemId: generateTempItemId(),
    routingId: 0,
    processOrder: 0,
    customLaborHours: process.standardLaborHours || 0,
    customMachineHours: process.standardMachineHours || 0,
    customProcessParams: process.processParamTemplate || '',
    description: process.description || '',
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
    icon: process.icon,
    // 批次1：下标
    hasIndex: process.hasIndex || 0,
    indexNumber: null,
  }
}

// 添加工序后：若该工序带下标（has_index=1）则弹窗输入下标数字
const maybePromptIndex = (item: EngineeringRoutingItemVO) => {
  if (item.hasIndex === 1) {
    openIndexDialog(item)
  }
}

// 添加到新组合
const addToNewGroup = (process: StandardProcessOption) => {
  const newItem = createItemVO(process)
  groups.value.push({
    groupOrder: groups.value.length + 1,
    items: [newItem],
    totalLaborHours: process.standardLaborHours || 0,
    totalMachineHours: process.standardMachineHours || 0,
    remark: '',
  })
  updateGroupOrder()
  syncToParent()
  maybePromptIndex(newItem)
}

// 添加到已有组
const addToGroup = (groupIndex: number, process: StandardProcessOption) => {
  const newItem = createItemVO(process)
  groups.value[groupIndex].items.push(newItem)
  recalculateGroupHours(groupIndex)
  syncToParent()
  maybePromptIndex(newItem)
}

// 插入到组合的指定位置
const addToGroupAtIndex = (
  groupIndex: number,
  process: StandardProcessOption,
  itemIndex: number
) => {
  const newItem = createItemVO(process)
  groups.value[groupIndex].items.splice(itemIndex, 0, newItem)
  recalculateGroupHours(groupIndex)
  syncToParent()
  maybePromptIndex(newItem)
}

// 从组合中移除工序
const removeItemFromGroup = (groupIndex: number, itemIndex: number) => {
  groups.value[groupIndex].items.splice(itemIndex, 1)
  if (groups.value[groupIndex].items.length === 0) {
    // 如果组合为空，删除该组合
    groups.value.splice(groupIndex, 1)
    updateGroupOrder()
  } else {
    recalculateGroupHours(groupIndex)
  }
  syncToParent()
}

// 在组合内移动工序
const moveItemWithinGroup = (groupIndex: number, fromIndex: number, toIndex: number) => {
  const items = groups.value[groupIndex].items
  const [moved] = items.splice(fromIndex, 1)
  items.splice(toIndex, 0, moved)
  syncToParent()
}

// 在组合间移动工序
const moveItemBetweenGroups = (fromGroupIndex: number, itemIndex: number, toGroupIndex: number) => {
  const item = groups.value[fromGroupIndex].items[itemIndex]
  if (!item) return

  // 检查目标组合是否已有相同工序
  const exists = groups.value[toGroupIndex]?.items.some((i) => i.processId === item.processId)
  if (exists) {
    ElMessage.warning(`工序"${item.processName}"已在目标组合中`)
    return
  }

  groups.value[fromGroupIndex].items.splice(itemIndex, 1)
  groups.value[toGroupIndex].items.push(item)

  if (groups.value[fromGroupIndex].items.length === 0) {
    groups.value.splice(fromGroupIndex, 1)
  } else {
    recalculateGroupHours(fromGroupIndex)
  }
  recalculateGroupHours(toGroupIndex)
  updateGroupOrder()
  syncToParent()
}

// 上移组合
const moveGroupUp = (index: number) => {
  if (index <= 0) return
  const temp = groups.value[index]
  groups.value[index] = groups.value[index - 1]
  groups.value[index - 1] = temp
  updateGroupOrder()
  syncToParent()
}

// 下移组合
const moveGroupDown = (index: number) => {
  if (index >= groups.value.length - 1) return
  const temp = groups.value[index]
  groups.value[index] = groups.value[index + 1]
  groups.value[index + 1] = temp
  updateGroupOrder()
  syncToParent()
}

// 删除组合
const removeGroup = (index: number) => {
  groups.value.splice(index, 1)
  updateGroupOrder()
  syncToParent()
}

// 重新计算组合工时
const recalculateGroupHours = (groupIndex: number) => {
  const group = groups.value[groupIndex]
  if (!group) return
  group.totalLaborHours = group.items.reduce(
    (sum, item) => sum + (item.customLaborHours || item.standardLaborHours || 0),
    0
  )
  group.totalMachineHours = group.items.reduce(
    (sum, item) => sum + (item.customMachineHours || item.standardMachineHours || 0),
    0
  )
}

// 更新组合序号
const updateGroupOrder = () => {
  groups.value.forEach((group, index) => {
    group.groupOrder = index + 1
  })
}

// ==================== 工序类别变更 ====================

/**
 * 工序类别变更时，同步到组合内所有工序
 */
const handleProcessCategoryChange = (groupIndex: number, category: string) => {
  const group = groups.value[groupIndex]
  if (!group) return
  // 将类别同步到组合内所有工序
  group.items.forEach((item) => {
    item.processCategory = category
  })
  syncToParent()
}

// ==================== 同步数据 ====================

// 同步数据到父组件（保留组合结构）
const syncToParent = () => {
  const flatItems: EngineeringRoutingItemVO[] = []

  groups.value.forEach((group) => {
    // 生成一个临时 groupId（负数，同一组合的工序共享）
    const tempGroupId = generateTempGroupId()

    group.items.forEach((item, idx) => {
      // 深拷贝，避免引用问题
      const newItem = { ...item }
      newItem.processOrder = flatItems.length + 1
      newItem.groupId = tempGroupId // 同一组合的工序共享同一 groupId
      newItem.groupOrder = group.groupOrder
      newItem.groupName = `组合${group.groupOrder}`
      // 组合备注存到第一条工序的 description
      if (idx === 0 && group.remark) {
        newItem.description = group.remark
      }
      // 工序类别同步到所有工序
      if (group.processCategory) {
        newItem.processCategory = group.processCategory
      }
      flatItems.push(newItem)
    })
  })

  setTimeout(() => {
    emit('update:modelValue', JSON.parse(JSON.stringify(flatItems)))
  }, 0)
}

// 暴露方法给父组件
defineExpose({
  getGroups: () => JSON.parse(JSON.stringify(groups.value)),
  setGroups: (data: RouteItemGroup[]) => {
    groups.value = JSON.parse(JSON.stringify(data))
  },
  getItems: () => {
    const flatItems: EngineeringRoutingItemVO[] = []
    groups.value.forEach((group) => {
      const tempGroupId = generateTempGroupId()
      group.items.forEach((item, idx) => {
        const newItem = { ...item }
        newItem.processOrder = flatItems.length + 1
        newItem.groupId = tempGroupId
        newItem.groupOrder = group.groupOrder
        newItem.groupName = `组合${group.groupOrder}`
        // 组合备注存到第一条工序的 description
        if (idx === 0 && group.remark) {
          newItem.description = group.remark
        }
        // 工序类别同步到所有工序
        if (group.processCategory) {
          newItem.processCategory = group.processCategory
        }
        flatItems.push(newItem)
      })
    })
    return JSON.parse(JSON.stringify(flatItems))
  },
  setItems: (data: EngineeringRoutingItemVO[]) => {
    setItemsFromData(data)
  },
})
</script>

<style scoped>
.route-item-icon-editor {
  margin-top: 10px;
}

.icon-selector-area {
  margin-bottom: 16px;
}

.group-mode-switch {
  margin-bottom: 8px;
}

.selector-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  padding: 8px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 4px 8px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: grab;
  transition: all 0.2s ease;
  user-select: none;
}

.icon-item:hover {
  border-color: #409eff;
  background-color: #ecf5ff;
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.icon-item:active {
  cursor: grabbing;
}

.icon-name {
  margin-top: 6px;
  font-size: 11px;
  color: #606266;
  text-align: center;
  line-height: 1.3;
  word-break: break-all;
}

.table-drop-zone {
  border: 2px dashed #e4e7ed;
  border-radius: 6px;
  padding: 8px;
  transition: all 0.3s ease;
  min-height: 200px;
}

.table-drop-zone.drag-over {
  border-color: #409eff;
  background-color: rgba(64, 158, 255, 0.05);
}

.group-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px;
  min-height: 36px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.group-items.drag-over {
  background-color: rgba(64, 158, 255, 0.1);
  outline: 2px dashed #409eff;
}

.group-item-tag {
  cursor: grab;
  user-select: none;
}

.group-item-tag:active {
  cursor: grabbing;
}

/* ==================== 批次1：工序行（图标+下标） ==================== */
.group-item-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  margin-bottom: 4px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  cursor: grab;
  user-select: none;
  transition: all 0.2s ease;
}

.group-item-row:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

.group-item-row:active {
  cursor: grabbing;
}

.item-icon {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.item-emoji {
  font-size: 16px;
  line-height: 1;
}

.item-name {
  font-size: 12px;
  color: #303133;
  flex-shrink: 0;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-close {
  color: #c0c4cc;
  cursor: pointer;
  flex-shrink: 0;
  margin-left: auto;
}

.item-close:hover {
  color: #f56c6c;
}

.drop-hint {
  color: #c0c4cc;
  font-size: 12px;
  line-height: 28px;
  padding: 0 8px;
}

.empty-tip {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

/* 底部拖拽放置区 */
.table-bottom-drop-zone {
  margin-top: 12px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  cursor: default;
  transition: all 0.3s ease;
  background-color: #fafafa;
}

.table-bottom-drop-zone:hover {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.table-bottom-drop-zone.drag-over {
  border-color: #409eff;
  background-color: rgba(64, 158, 255, 0.08);
  transform: scale(1.02);
}

.drop-zone-text {
  color: #909399;
  font-size: 14px;
  user-select: none;
}

.table-bottom-drop-zone.drag-over .drop-zone-text {
  color: #409eff;
  font-weight: 500;
}
</style>
