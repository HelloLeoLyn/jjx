<template>
  <div class="route-item-icon-editor">
    <el-divider content-position="left">工序明细（拖拽图标到表格添加工序）</el-divider>

    <!-- 大类 Tabs（2026-08-12：与打样平台一致，印刷工序独立表格） -->
    <el-tabs v-model="majorCategoryTab">
      <el-tab-pane label="🛠 冲型组装" name="ASSEMBLY">

    <!-- 上方：图标选择区（Tabs + 拖拽源） -->
    <div class="icon-selector-area">
      <div class="selector-toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索工序名称/编码..."
          clearable
          size="small"
          style="width: 240px"
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
                <span v-else class="item-name">
                  <el-tag v-if="item.customProcessParams" size="small" type="warning" style="margin-right: 4px">印刷</el-tag>
                  {{ item.processName }}
                </span>
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

        <el-table-column label="工艺参数" min-width="200">
          <template #default="scope">
            <el-input
              v-model="scope.row.customProcessParams"
              size="small"
              :placeholder="printParamsHint(scope.row)"
              @input="syncToParent"
            />
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

        <el-table-column label="操作" min-width="120" align="center" fixed="right">
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

      </el-tab-pane>

      <!-- 印刷 tab：独立印刷工序表格（2026-08-12，与打样平台一致：按结构 Tabs 分） -->
      <el-tab-pane label="🖨️ 印刷" name="PRINT">
        <div class="print-area">
          <div class="print-toolbar" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
            <span class="print-desc" style="color:#909399;font-size:12px">无标准工序库，逐行录入；每行一道印刷，按结构分（面板/上线/下线）</span>
            <el-button type="warning" size="small" icon="Plus" @click="addPrintRow">＋ 添加印刷工序</el-button>
          </div>
          <el-tabs v-model="printActiveTab" type="border-card">
            <el-tab-pane
              v-for="tab in PRINT_TABS"
              :key="tab.value"
              :name="tab.value"
              :label="`${tab.label}（${filteredPrintRows(tab.value).length}）`"
            >
              <el-table :data="filteredPrintRows(tab.value)" size="small" border stripe style="width: 100%">
                <el-table-column type="index" label="#" width="44" align="center" />
                <el-table-column label="印刷名称 *" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.processName" size="small" placeholder="如：丝印/移印/网印" />
                  </template>
                </el-table-column>
                <el-table-column label="色号" width="120">
                  <template #default="{ row }">
                    <el-input :model-value="getPrintParam(row, 'colorNo')" size="small" placeholder="如 PANTONE 123C" @input="(v: string) => setPrintParam(row, 'colorNo', v)" />
                  </template>
                </el-table-column>
                <el-table-column label="油墨编号" width="120">
                  <template #default="{ row }">
                    <el-input :model-value="getPrintParam(row, 'inkNo')" size="small" placeholder="油墨编号" @input="(v: string) => setPrintParam(row, 'inkNo', v)" />
                  </template>
                </el-table-column>
                <el-table-column label="网框编号" width="120">
                  <template #default="{ row }">
                    <el-input :model-value="getPrintParam(row, 'screenNo')" size="small" placeholder="网框编号" @input="(v: string) => setPrintParam(row, 'screenNo', v)" />
                  </template>
                </el-table-column>
                <el-table-column label="子结构" width="110">
                  <template #default="{ row }">
                    <el-select v-model="row.processCategory" size="small" style="width: 100px" @change="syncToParent">
                      <el-option v-for="o in ProcessCategoryEnum.items" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="人工工时(h)" width="100" align="center">
                  <template #default="{ row }">
                    <el-input-number v-model="row.customLaborHours" :min="0" :precision="2" :step="0.1" size="small" controls-position="right" style="width: 90px" />
                  </template>
                </el-table-column>
                <el-table-column label="机器工时(h)" width="100" align="center">
                  <template #default="{ row }">
                    <el-input-number v-model="row.customMachineHours" :min="0" :precision="2" :step="0.1" size="small" controls-position="right" style="width: 90px" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" min-width="150" align="center">
                  <template #default="{ row }">
                    <el-button size="small" link icon="Top" :disabled="isFirstPrint(row)" @click="movePrintRow(row, -1)">上移</el-button>
                    <el-button size="small" link icon="Bottom" :disabled="isLastPrint(row)" @click="movePrintRow(row, 1)">下移</el-button>
                    <el-button size="small" link type="danger" icon="Delete" @click="removePrintRow(row)">删</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!filteredPrintRows(tab.value).length" style="text-align:center;color:#c0c4cc;padding:16px 0;font-size:13px">暂无印刷工序，点击右上角【＋ 添加印刷工序】录入</div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-tab-pane>
    </el-tabs>

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
import { ProcessCategoryEnum } from '@/enums/product'
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

/** 搜索关键字（按名称/编码过滤图标） */
const searchKeyword = ref('')

// ==================== 印刷工序（2026-08-12：独立表格，与打样平台一致） ====================
/** 大类 Tab：ASSEMBLY冲型组装 / PRINT印刷 */
const majorCategoryTab = ref<'ASSEMBLY' | 'PRINT'>('ASSEMBLY')
/** 印刷工序行（majorCategory=PRINT，参数存 customProcessParams JSON） */
const printRows = ref<EngineeringRoutingItemVO[]>([])

/** 印刷子结构 Tabs（业务上印刷按结构分：面板/上线/下线/未分类，与打样一致，2026-08-12） */
const PRINT_TABS = [
  { value: 'PANEL', label: '面板' },
  { value: 'UP_LINE', label: '上线' },
  { value: 'DOWN_LINE', label: '下线' },
  { value: '', label: '未分类' },
]
const printActiveTab = ref('PANEL')

function filteredPrintRows(value: string) {
  // 2026-08-12：OTHER 归一显示到未分类（旧转移数据类别可能是 OTHER）
  return printRows.value.filter((r) => {
    const cat = r.processCategory || ''
    if (value === '') return cat === '' || cat === 'OTHER'
    return cat === value
  })
}

/** 新增印刷空行（归属当前子结构 tab） */
function addPrintRow() {
  const row: any = {
    itemId: generateTempItemId(),
    routingId: 0,
    processOrder: 0,
    customLaborHours: 0,
    customMachineHours: 0,
    customProcessParams: JSON.stringify({ printName: '' }),
    description: '',
    createTime: '',
    updateTime: '',
    processId: null,
    processCode: '',
    processName: '',
    processType: '',
    processTypeName: '',
    processTypeTagType: '',
    processCategory: printActiveTab.value,
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
    icon: '',
    hasIndex: 0,
    indexNumber: null,
  }
  printRows.value.push(row)
}

/** 印刷参数读取/写入（customProcessParams JSON） */
function getPrintParam(row: any, key: string): string {
  try {
    const o = JSON.parse(row.customProcessParams || '{}')
    return o[key] || ''
  } catch {
    return ''
  }
}
function setPrintParam(row: any, key: string, val: string) {
  try {
    const o = JSON.parse(row.customProcessParams || '{}')
    o[key] = val
    row.customProcessParams = JSON.stringify(o)
  } catch {
    row.customProcessParams = JSON.stringify({ [key]: val })
  }
}

function isFirstPrint(row: any) {
  return printRows.value.indexOf(row) <= 0
}
function isLastPrint(row: any) {
  return printRows.value.indexOf(row) >= printRows.value.length - 1
}
function movePrintRow(row: any, dir: number) {
  const i = printRows.value.indexOf(row)
  const j = i + dir
  if (i < 0 || j < 0 || j >= printRows.value.length) return
  const arr = printRows.value
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
  syncToParent()
}
function removePrintRow(row: any) {
  printRows.value.splice(printRows.value.indexOf(row), 1)
  syncToParent()
}

/** 工艺参数列提示 */
function printParamsHint(item: EngineeringRoutingItemVO): string {
  if (!item.customProcessParams) return '工艺参数 JSON（如 {"printName":"丝印","colorNo":"123C"}）'
  try {
    const o = JSON.parse(item.customProcessParams)
    const parts: string[] = []
    if (o.colorNo) parts.push(`色号:${o.colorNo}`)
    if (o.inkNo) parts.push(`油墨:${o.inkNo}`)
    if (o.screenNo) parts.push(`网框:${o.screenNo}`)
    return parts.length ? `🖨️ ${parts.join(' ')}` : item.customProcessParams
  } catch {
    return item.customProcessParams
  }
}

const activeTab = ref('')
const groups = ref<RouteItemGroup[]>([])
const isDragOverTable = ref(false)
const dragOverGroupIndex = ref<number | null>(null)

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

// 按工序类别分组的标准工序（固定维度，支持关键字过滤；2026-08-12 移除类型分组切换）
const groupedProcesses = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  const filtered = kw
    ? props.standardProcesses.filter((p) =>
        (p.processName || '').toLowerCase().includes(kw)
        || (p.processCode || '').toLowerCase().includes(kw))
    : props.standardProcesses
  const groups = new Map<string, StandardProcessOption[]>()
  filtered.forEach((p) => {
    const key = p.processCategory
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)!.push(p)
  })
  const result = Array.from(groups.entries()).map(([key, options]) => ({
    key,
    label: ProcessCategoryEnum.getLabel(key),
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
  // 2026-08-12：按大类拆分——PRINT 进印刷表格，其余组装组合
  const printItems = (data || []).filter((i) => i.majorCategory === 'PRINT')
  const assemblyItems = (data || []).filter((i) => i.majorCategory !== 'PRINT')

  // 印刷行直接进表格（保留参数 JSON）
  printRows.value = printItems.map((i) => ({ ...i }))

  // 按 groupId 分组：有 groupId 的按组合分组，没有的各自独立
  const groupMap = new Map<string, EngineeringRoutingItemVO[]>()

  assemblyItems.forEach((item) => {
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

// 同步数据到父组件（保留组合结构 + 印刷行，2026-08-12 合并两大类的工序）
const syncToParent = () => {
  const flatItems: EngineeringRoutingItemVO[] = []

  // 组装组合（ASSEMBLY）
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
      newItem.majorCategory = 'ASSEMBLY'
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

  // 印刷工序（PRINT，独立行）
  printRows.value.forEach((row) => {
    const newItem = { ...row }
    newItem.processOrder = flatItems.length + 1
    newItem.groupId = null
    newItem.groupOrder = null
    newItem.groupName = null
    newItem.majorCategory = 'PRINT'
    flatItems.push(newItem)
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
        newItem.majorCategory = 'ASSEMBLY'
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
    // 印刷工序（2026-08-12）
    printRows.value.forEach((row) => {
      const newItem = { ...row }
      newItem.processOrder = flatItems.length + 1
      newItem.groupId = null
      newItem.groupOrder = null
      newItem.groupName = null
      newItem.majorCategory = 'PRINT'
      flatItems.push(newItem)
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
