<template>
  <el-dialog
    v-model="visible"
    :title="`打样转标准 · ${store.orderNo || ''}`"
    width="860px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <!-- 加载中 -->
    <div v-loading="store.loading" style="min-height: 200px">
      <template v-if="!store.loading && store.preview">
        <!-- 重复转移提示（DEV-781 后续：方案A——记录已转，再转提醒） -->
        <el-alert
          v-if="transferredFlag"
          type="warning"
          show-icon
          :closable="false"
          title="该样品单已进行过资料转移，再次确认将生成新的 BOM/工艺路线版本（版本号+1），请确认是否必要"
          style="margin-bottom: 12px"
        />
        <!-- 未匹配提示 -->
        <el-alert
          v-if="!store.allMatched"
          type="warning"
          show-icon
          :closable="false"
          :title="`还有 ${store.unmatchedProcessCount} 道组合工序未选择标准项、${store.unmatchedMaterialCount} 项物料未选择标准项，请手动选择后再确认转移`"
          style="margin-bottom: 12px"
        />
        <el-alert
          v-else
          type="success"
          show-icon
          :closable="false"
          title="所有工序/物料均已匹配，可以确认转移"
          style="margin-bottom: 12px"
        />

        <!-- 工序映射列表（2026-08-12：按子结构 Tabs 分，与打样/路线一致） -->
        <div class="section-title">① 工序映射（{{ store.processMappings.length }} 项）</div>
        <el-tabs v-model="processTab" type="border-card" style="margin-bottom: 16px">
          <el-tab-pane
            v-for="tab in PROCESS_TABS"
            :key="tab.value"
            :name="tab.value"
            :label="`${tab.label}（${filteredProcessMappings(tab.value).length}）`"
          >
            <el-table :data="filteredProcessMappings(tab.value)" size="small" border stripe max-height="240">
              <el-table-column label="打样工序" min-width="150">
                <template #default="scope">
                  <span :class="{ 'unmatched-text': scope.row.stdProcessId == null }">
                    <el-tag v-if="scope.row.customProcessParams" size="small" type="warning" effect="plain" style="margin-right: 4px">印刷</el-tag>
                    {{ scope.row.processName }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="顺序" width="60" align="center">
                <template #default="scope">{{ scope.row.processOrder }}</template>
              </el-table-column>
              <el-table-column label="组合" width="90" align="center">
                <template #default="scope">
                  <el-tag v-if="scope.row.groupName" size="small" type="info">{{ scope.row.groupName }}</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="标准工序（可改选）" min-width="220">
                <template #default="scope">
                  <el-select
                    v-model="scope.row.stdProcessId"
                    filterable
                    size="small"
                    style="width: 100%"
                    :placeholder="scope.row.customProcessParams ? '可不选（自定义工序）' : '请手动选择'"
                    :class="{ 'unmatched-select': scope.row.stdProcessId == null && !scope.row.customProcessParams }"
                    @change="(v: number) => onProcessChange(scope.$index, v)"
                  >
                    <el-option
                      v-for="opt in store.standardProcesses"
                      :key="opt.processId"
                      :label="opt.processName"
                      :value="opt.processId"
                    />
                  </el-select>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>

        <!-- 物料映射列表 -->
        <div class="section-title">② 物料映射（{{ store.materialMappings.length }} 项）</div>
        <el-table :data="store.materialMappings" size="small" border stripe max-height="260">
          <el-table-column label="来源工序" width="110">
            <template #default="scope">{{ scope.row.sourceProcessName }}</template>
          </el-table-column>
          <el-table-column label="打样物料" min-width="150">
            <template #default="scope">
              <span :class="{ 'unmatched-text': scope.row.materialId == null }">
                {{ scope.row.materialName }}
              </span>
              <div v-if="scope.row.spec" style="font-size: 11px; color: #909399">{{ scope.row.spec }}</div>
            </template>
          </el-table-column>
          <el-table-column label="用量" width="80" align="center">
            <template #default="scope">{{ scope.row.qty }} {{ scope.row.unit }}</template>
          </el-table-column>
          <el-table-column label="标准物料（可改选）" min-width="220">
            <template #default="scope">
              <el-select
                v-model="scope.row.materialId"
                filterable
                size="small"
                style="width: 100%"
                placeholder="请手动选择"
                :class="{ 'unmatched-select': scope.row.materialId == null }"
                @change="(v: number) => onMaterialChange(scope.row.rowKey, v)"
              >
                <el-option
                  v-for="opt in store.standardMaterials"
                  :key="opt.materialId"
                  :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
                  :value="opt.materialId"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button @click="goStandardEdit">进入标准编辑</el-button>
      <el-button type="primary" :disabled="!store.allMatched" :loading="store.confirming" @click="onConfirm">
        确认转移
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSampleTransferStore } from '@/store/modules/sampleTransfer'

const props = defineProps<{
  modelValue: boolean
  orderId?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const router = useRouter()
const store = useSampleTransferStore()

// 工序映射子结构 Tabs（2026-08-12：与打样/路线一致，印刷工序按结构归属）
const PROCESS_TABS = [
  { value: 'PANEL', label: '面板' },
  { value: 'UP_LINE', label: '上线' },
  { value: 'DOWN_LINE', label: '下线' },
  { value: '', label: '未分类' },
]
const processTab = ref('PANEL')
function filteredProcessMappings(value: string) {
  // 2026-08-12：OTHER 归一显示到未分类（旧转移数据类别可能是 OTHER）
  return store.processMappings.filter((p) => {
    const cat = p.processCategory || ''
    if (value === '') return cat === '' || cat === 'OTHER'
    return cat === value
  })
}

// DEV-781 后续：已转移订单标记（localStorage 持久化，方案A）
const TRANSFER_FLAG_KEY = 'sample_transferred_orders'
const transferredFlag = ref(false)

function getTransferredIds(): number[] {
  try {
    const raw = localStorage.getItem(TRANSFER_FLAG_KEY)
    const arr = raw ? JSON.parse(raw) : []
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function markTransferred(orderId: number) {
  const arr = getTransferredIds()
  if (!arr.includes(orderId)) {
    arr.push(orderId)
    localStorage.setItem(TRANSFER_FLAG_KEY, JSON.stringify(arr))
  }
}

const visible = ref(props.modelValue)

watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
  }
)

watch(visible, (v) => {
  emit('update:modelValue', v)
})

// 打开弹窗时加载预览数据
async function onOpen() {
  if (!props.orderId) return
  transferredFlag.value = getTransferredIds().includes(props.orderId)
  if (store.orderId !== props.orderId || !store.preview) {
    await store.loadPreview(props.orderId)
  }
}

// 工序改选
function onProcessChange(index: number, stdProcessId: number) {
  store.replaceProcess(index, stdProcessId)
}

// 物料改选
function onMaterialChange(rowKey: string, materialId: number) {
  store.updateMaterialMapping(rowKey, materialId)
}

// 进入标准编辑（对照版全屏页，数据共享不丢失）
function goStandardEdit() {
  visible.value = false
  router.push({ path: '/sample/transfer/edit', query: { orderId: store.orderId } })
}

// 确认转移
async function onConfirm() {
  const result = await store.confirmTransfer()
  if (!result) return
  // 记录已转移（方案A：重复转移提醒）
  if (props.orderId) markTransferred(props.orderId)
  transferredFlag.value = true
  ElMessage.success(
    `转移成功：${result.transferNo}（${result.version || ''}，BOM ${result.bomId || '-'} / 路线 ${result.routingId || '-'}）`
  )
  visible.value = false
  emit('success')
}
</script>

<style scoped>
.section-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.unmatched-text {
  color: #f56c6c;
  font-weight: 600;
}

:deep(.unmatched-select .el-select__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
</style>
