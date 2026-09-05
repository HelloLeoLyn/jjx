<template>
  <div class="transfer-edit-page">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="page-title">
        <el-button link icon="ArrowLeft" @click="goBackLight">返回轻量版</el-button>
        <span style="font-weight: 600; font-size: 16px">
          打样转标准 · 对照编辑 · {{ store.orderNo || '' }}
        </span>
      </div>
      <div class="page-actions">
        <el-button icon="View" @click="previewVisible = true">预览作业指导书</el-button>
        <el-button type="primary" :disabled="!store.allMatched" :loading="store.confirming" @click="onConfirm">
          确认转移
        </el-button>
      </div>
    </div>

    <div v-loading="store.loading" class="page-body">
      <template v-if="!store.loading && store.preview">
        <!-- 未匹配提示 -->
        <el-alert
          v-if="!store.allMatched"
          type="warning"
          show-icon
          :closable="false"
          :title="`还有 ${store.unmatchedProcessCount} 道组合工序未选择标准项、${store.unmatchedMaterialCount} 项物料未选择标准项`"
          style="margin-bottom: 12px"
        />

        <!-- 左右对照：左=打样数据(只读) / 右=标准数据(可编辑) -->
        <div class="compare-row">
          <!-- 左列：打样数据（只读） -->
          <div class="col-sample">
            <div class="col-title">打样数据（只读）</div>

            <div class="block-title">工序（{{ store.sampleProcessCount }}）</div>
            <div v-for="group in store.sampleProcessGroups" :key="group.groupOrder" class="sample-item">
              <span class="idx">{{ group.groupOrder }}</span>
              <div class="sample-item-main">
                <div>
                  {{ group.processName }}
                  <el-tag v-if="group.itemCount > 1" size="small" type="info" style="margin-left: 6px">
                    组合 · {{ group.itemCount }} 项
                  </el-tag>
                  <el-tag v-if="group.hasCustomProcessParams" size="small" type="warning" style="margin-left: 6px">印刷</el-tag>
                </div>
                <template v-for="item in group.items" :key="item.processId">
                  <div v-if="printParamsText(item.customProcessParams)" class="sub print-params">
                    🖨️ {{ group.itemCount > 1 ? `${item.processName}：` : '' }}{{ printParamsText(item.customProcessParams) }}
                  </div>
                  <div v-if="item.processNote" class="sub">
                    {{ group.itemCount > 1 ? `${item.processName}：` : '' }}{{ item.processNote }}
                  </div>
                </template>
              </div>
              <div class="sample-item-tags">
                <el-tag
                  v-for="(category, ci) in [...new Set(group.items.map((item) => item.processCategory).filter(Boolean))]"
                  :key="ci"
                  size="small"
                  type="info"
                >
                  {{ categoryText(category as string) }}
                </el-tag>
              </div>
            </div>

            <div class="block-title">物料（{{ store.preview.sampleMaterials.length }}）</div>
            <div v-for="(m, i) in store.preview.sampleMaterials" :key="m.rowKey" class="sample-item">
              <span class="idx">{{ i + 1 }}</span>
              <div class="sample-item-main">
                <div>{{ m.name }}</div>
                <div class="sub">
                  {{ m.sourceProcessName }}{{ m.spec ? ' · ' + m.spec : '' }} · {{ m.qty }}{{ m.unit }}
                </div>
              </div>
            </div>
          </div>

          <!-- 右列：标准数据（可编辑） -->
          <div class="col-standard">
            <div class="col-title">
              标准数据（可编辑）
              <span class="sub-tip">组合工序保持整体，只能整体拖动排序</span>
            </div>

            <!-- 工序区域 -->
            <div class="block-title">
              工序映射
              <el-button size="small" type="primary" plain icon="Plus" style="margin-left: 8px" @click="addProcessVisible = true">
                新增工序
              </el-button>
            </div>
            <div class="group-list">
              <div
                v-for="(group, gi) in store.groupedProcesses"
                :key="gi"
                class="group-card"
                draggable="true"
                @dragstart="onGroupDragStart(gi, $event)"
                @dragover.prevent
                @drop="onGroupDrop(gi, $event)"
                @dragend="onGroupDragEnd"
              >
                <div class="group-head">
                  <span class="drag-handle">⠿</span>
                  <el-tag v-if="group.groupName" size="small" type="warning">{{ group.groupName }}</el-tag>
                  <span v-else class="sub">独立工序</span>
                  <span class="sub">第 {{ gi + 1 }} 组</span>
                  <div class="group-actions">
                    <el-button size="small" link type="primary" icon="Top" :disabled="gi === 0" @click="store.moveGroup(gi, gi - 1)" />
                    <el-button size="small" link type="primary" icon="Bottom" :disabled="gi === store.groupedProcesses.length - 1" @click="store.moveGroup(gi, gi + 1)" />
                    <el-button size="small" link type="danger" icon="Delete" @click="onRemoveGroup(group)" />
                  </div>
                </div>
                <div v-for="(item, ii) in group.items" :key="ii" class="process-row">
                  <!-- 有下标（hasIndex=1）：图标+红底数字 -->
                  <IconStepBadge
                    v-if="item.hasIndex === 1"
                    :icon="item.icon || ''"
                    :size="18"
                    :index="item.indexNumber ?? null"
                  />
                  <!-- 无下标：只显示工序名称（印刷工序带标识） -->
                  <span v-else class="row-label">
                    {{ item.processName }}
                    <el-tag v-if="item.customProcessParams" size="small" type="warning" style="margin-left: 4px">印刷</el-tag>
                  </span>
                  <el-select
                    v-model="item.stdProcessId"
                    filterable
                    size="small"
                    style="width: 100%"
                    :placeholder="item.customProcessParams ? '可不选（自定义工序）' : '请手动选择'"
                    :class="{ 'unmatched-select': item.stdProcessId == null && !item.customProcessParams }"
                    @change="(v: number) => onStdProcessChange(group, item, v)"
                  >
                    <el-option
                      v-for="opt in store.standardProcesses"
                      :key="opt.processId"
                      :label="opt.processName"
                      :value="opt.processId"
                    />
                  </el-select>
                </div>
              </div>
              <el-empty v-if="!store.processMappings.length" description="暂无工序" :image-size="60" />
            </div>

            <!-- 物料区域 -->
            <div class="block-title" style="margin-top: 16px">
              物料映射
              <el-button size="small" type="primary" plain icon="Plus" style="margin-left: 8px" @click="addMaterialVisible = true">
                新增物料
              </el-button>
            </div>
            <el-table :data="store.materialMappings" size="small" border stripe>
              <el-table-column label="来源工序" width="100">
                <template #default="scope">{{ scope.row.sourceProcessName }}</template>
              </el-table-column>
              <el-table-column label="标准物料" min-width="200">
                <template #default="scope">
                  <el-select
                    v-model="scope.row.materialId"
                    filterable
                    size="small"
                    style="width: 100%"
                    placeholder="请手动选择"
                    :class="{ 'unmatched-select': scope.row.materialId == null }"
                    @change="(v: number) => store.updateMaterialMapping(scope.row.rowKey, v)"
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
              <el-table-column label="规格" width="110">
                <template #default="scope">
                  <el-input v-model="scope.row.spec" size="small" placeholder="规格" />
                </template>
              </el-table-column>
              <el-table-column label="用量" width="110">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.qty"
                    :min="0"
                    :precision="4"
                    :controls="false"
                    size="small"
                    style="width: 100%"
                    @change="(v: number | undefined) => store.updateMaterialQty(scope.row.rowKey, v ?? 0)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="70">
                <template #default="scope">
                  <el-input v-model="scope.row.unit" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="60" align="center">
                <template #default="scope">
                  <el-button size="small" link type="danger" icon="Delete" @click="store.removeMaterial(scope.row.rowKey)" />
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!store.materialMappings.length" description="暂无物料" :image-size="60" />
          </div>
        </div>
      </template>
    </div>

    <!-- 新增工序弹窗 -->
    <el-dialog v-model="addProcessVisible" title="新增工序" width="420px" append-to-body>
      <el-select v-model="addProcessId" filterable size="large" style="width: 100%" placeholder="从标准工序库选择">
        <el-option
          v-for="opt in store.standardProcesses"
          :key="opt.processId"
          :label="opt.processName"
          :value="opt.processId"
        />
      </el-select>
      <template #footer>
        <el-button @click="addProcessVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!addProcessId" @click="onAddProcess">添加</el-button>
      </template>
    </el-dialog>

    <!-- 新增物料弹窗 -->
    <el-dialog v-model="addMaterialVisible" title="新增物料" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="来源工序">
          <el-select v-model="addMaterialProcessId" filterable size="small" style="width: 100%" placeholder="选择挂载的工序">
            <el-option
              v-for="p in store.processMappings"
              :key="p.sampleProcessId ?? p.processName + p.processOrder"
              :label="p.processName"
              :value="p.sampleProcessId ?? p.processName + p.processOrder"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标准物料">
          <el-select v-model="addMaterialId" filterable size="small" style="width: 100%" placeholder="从标准物料库选择">
            <el-option
              v-for="opt in store.standardMaterials"
              :key="opt.materialId"
              :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
              :value="opt.materialId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMaterialVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!addMaterialId || addMaterialProcessId == null" @click="onAddMaterial">添加</el-button>
      </template>
    </el-dialog>

    <!-- 预览作业指导书 -->
    <el-dialog v-model="previewVisible" title="作业指导书预览" width="720px" append-to-body>
      <div v-if="store.preview" class="guide-preview">
        <div class="guide-title">{{ store.orderNo }} · 打样转标准作业指导书（{{ store.transferResult?.version || '待确认' }}）</div>

        <div class="guide-section">
          <div class="guide-section-title">一、工序（{{ store.processMappings.length }} 道）</div>
          <el-table :data="store.processMappings" size="small" border>
            <el-table-column label="顺序" width="60" align="center">
              <template #default="scope">{{ scope.row.processOrder }}</template>
            </el-table-column>
            <el-table-column label="工序名称" min-width="120">
              <template #default="scope">{{ scope.row.processName }}</template>
            </el-table-column>
            <el-table-column label="组合" width="90" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.groupName" size="small" type="info">{{ scope.row.groupName }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="工艺说明" min-width="150">
              <template #default="scope">{{ scope.row.processNote || '-' }}</template>
            </el-table-column>
            <el-table-column label="参数" min-width="130">
              <template #default="scope">
                <span v-if="scope.row.customProcessParams" style="color: #e6a23c">🖨️ {{ printParamsText(scope.row.customProcessParams) }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="工时(h)" width="80" align="right">
              <template #default="scope">{{ (scope.row.durationMinutes ?? 0) / 60 }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="guide-section">
          <div class="guide-section-title">二、物料（{{ store.materialMappings.length }} 项）</div>
          <el-table :data="store.materialMappings" size="small" border>
            <el-table-column label="工序" width="100">
              <template #default="scope">{{ scope.row.sourceProcessName }}</template>
            </el-table-column>
            <el-table-column label="物料名称" min-width="150">
              <template #default="scope">{{ scope.row.materialName }}</template>
            </el-table-column>
            <el-table-column label="规格" width="100">
              <template #default="scope">{{ scope.row.spec || '-' }}</template>
            </el-table-column>
            <el-table-column label="用量" width="80" align="center">
              <template #default="scope">{{ scope.row.qty }}</template>
            </el-table-column>
            <el-table-column label="单位" width="70">
              <template #default="scope">{{ scope.row.unit || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSampleTransferStore } from '@/store/modules/sampleTransfer'
import type { ProcessMapping } from '@/api/sales/sampleTransfer'
import IconStepBadge from '@/components/IconStepBadge/index.vue'

defineOptions({ name: 'SampleTransferEdit' })

const route = useRoute()
const router = useRouter()
const store = useSampleTransferStore()

// 弹窗状态
const addProcessVisible = ref(false)
const addProcessId = ref<number | null>(null)
const addMaterialVisible = ref(false)
const addMaterialId = ref<number | null>(null)
const addMaterialProcessId = ref<string | number | null>(null)
const previewVisible = ref(false)

// 拖拽
let dragGroupIndex = -1

// 工序类别 → 中文
function categoryText(category: string): string {
  const map: Record<string, string> = {
    PANEL: '面板',
    UP_LINE: '上线',
    DOWN_LINE: '下线',
    OTHER: '其他',
  }
  return map[category] || category
}

// 印刷参数解析：customProcessParams JSON → 可读文本（2026-08-12）
function parsePrintParams(json?: string | null): Record<string, string> | null {
  if (!json || !json.trim()) return null
  try {
    const o = JSON.parse(json)
    if (o && typeof o === 'object' && (o.printName || o.colorNo || o.inkNo || o.screenNo)) return o
    return null
  } catch {
    return null
  }
}
function printParamsText(json?: string | null): string {
  const p = parsePrintParams(json)
  if (!p) return ''
  return [p.colorNo ? `色号:${p.colorNo}` : '', p.inkNo ? `油墨:${p.inkNo}` : '', p.screenNo ? `网框:${p.screenNo}` : ''].filter(Boolean).join(' ')
}

// 返回轻量版（数据保留在 store，弹窗打开不重新加载）
function goBackLight() {
  router.push({ path: '/sales/sample-order', query: { transferDialog: String(store.orderId || '') } })
}

// 确认转移
async function onConfirm() {
  const result = await store.confirmTransfer()
  if (!result) return
  ElMessage.success(
    `转移成功：${result.transferNo}（${result.version || ''}，BOM ${result.bomId || '-'} / 路线 ${result.routingId || '-'}）`
  )
  router.push({ path: '/sales/sample-order', query: { transferDone: String(store.orderId || '') } })
}

// 工序替换（组内某道工序改选标准工序）
function onStdProcessChange(group: { items: ProcessMapping[] }, item: ProcessMapping, stdProcessId: number) {
  const idx = store.processMappings.indexOf(item)
  if (idx >= 0) store.replaceProcess(idx, stdProcessId)
}

// 删除整组（组合工序保持完整删除）
function onRemoveGroup(group: { items: ProcessMapping[] }) {
  store.removeGroupItems(group.items)
}

// 拖拽排序
function onGroupDragStart(gi: number, e: DragEvent) {
  dragGroupIndex = gi
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
function onGroupDrop(targetGi: number, e: DragEvent) {
  e.preventDefault()
  if (dragGroupIndex >= 0 && dragGroupIndex !== targetGi) {
    store.moveGroup(dragGroupIndex, targetGi)
  }
  dragGroupIndex = -1
}
function onGroupDragEnd() {
  dragGroupIndex = -1
}

// 新增工序
function onAddProcess() {
  if (addProcessId.value) {
    store.addProcess(addProcessId.value)
    addProcessId.value = null
    addProcessVisible.value = false
  }
}

// 新增物料
function onAddMaterial() {
  if (!addMaterialId.value || addMaterialProcessId.value == null) return
  const pid = addMaterialProcessId.value
  const proc = store.processMappings.find(
    (p) => (p.sampleProcessId ?? (p.processName + p.processOrder)) === pid
  )
  store.addMaterial(proc?.sampleProcessId ?? 0, proc?.processName || '', addMaterialId.value)
  addMaterialId.value = null
  addMaterialProcessId.value = null
  addMaterialVisible.value = false
}

onMounted(async () => {
  const orderId = Number(route.query.orderId || 0)
  if (orderId) {
    if (store.orderId !== orderId || !store.preview) {
      await store.loadPreview(orderId)
    }
  } else if (!store.preview) {
    ElMessage.warning('缺少样品单参数')
    router.push('/sales/sample-order')
  }
})
</script>

<style scoped>
.transfer-edit-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-actions {
  display: flex;
  gap: 8px;
}

.compare-row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.col-sample {
  width: 360px;
  flex-shrink: 0;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  background: #fafafa;
  max-height: calc(100vh - 160px);
  overflow-y: auto;
}

.col-standard {
  flex: 1;
  min-width: 0;
}

.col-title {
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sub-tip {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.block-title {
  font-weight: 600;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
}

.sample-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
}

.idx {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.sample-item-main {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.sample-item-tags {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.group-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
  cursor: grab;
}

.group-card:active {
  cursor: grabbing;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.drag-handle {
  color: #c0c4cc;
  font-size: 14px;
}

.group-actions {
  margin-left: auto;
  display: flex;
  gap: 2px;
}

.process-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.row-label {
  width: 110px;
  flex-shrink: 0;
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unmatched-select :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}

.guide-preview {
  max-height: 60vh;
  overflow-y: auto;
}

.guide-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.guide-section {
  margin-bottom: 12px;
}

.guide-section-title {
  font-weight: 600;
  margin-bottom: 6px;
}
</style>
