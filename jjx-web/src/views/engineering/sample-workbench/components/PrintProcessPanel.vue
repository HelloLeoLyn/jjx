<template>
  <div class="print-panel">
    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">🖨️ 印刷工序（自定义工序，表格录入）</span>
        <span class="desc">无标准工序库，逐行录入；每行一道印刷，可排序/推进/挂材料</span>
        <el-button
          type="success" size="small" :loading="savingPlan" @click="savePlan"
          style="float: right; margin-top: -2px"
        >💾 保存工序计划</el-button>
        <el-button
          type="primary" size="small" icon="Plus" @click="addPrintRow(activeTab)"
          style="float: right; margin-top: -2px; margin-right: 8px"
        >＋ 添加印刷工序</el-button>
      </template>

      <!-- 子结构 tabs（面板/上线/下线/未分类，与冲型组装一致） -->
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane v-for="tab in tabs" :key="tab.value" :name="tab.value" :label="`${tab.label}（${filtered(tab.value).length}）`">
          <el-table :data="filtered(tab.value)" size="small" border stripe>
            <el-table-column label="#" width="44" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="印刷名称 *" min-width="140">
              <template #default="{ row }">
                <el-input v-model="row.printName" size="small" placeholder="如：丝印/移印/网印" :class="{ 'input-error': !(row.printName || '').trim() }" />
              </template>
            </el-table-column>
            <el-table-column label="色号" width="120">
              <template #default="{ row }">
                <el-input v-model="row.colorNo" size="small" placeholder="如 PANTONE 123C" />
              </template>
            </el-table-column>
            <el-table-column label="油墨编号" width="120">
              <template #default="{ row }">
                <el-input v-model="row.inkNo" size="small" placeholder="油墨编号" />
              </template>
            </el-table-column>
            <el-table-column label="网框编号" width="120">
              <template #default="{ row }">
                <el-input v-model="row.screenNo" size="small" placeholder="网框编号" />
              </template>
            </el-table-column>
            <el-table-column label="🧾 材料" min-width="180">
              <template #default="{ row }">
                <div class="mat-cell">
                  <el-tag v-for="(m, i) in parseMaterials(row.materials)" :key="i" size="small" type="info" closable @close="removeMaterial(row, i)" style="margin-right:4px;margin-bottom:2px">
                    {{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}
                  </el-tag>
                  <el-button size="small" link type="primary" icon="Plus" @click="openMaterialPicker(row)">材料</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="left">
              <template #default="{ row }">
                <el-tag :type="row.status === 2 ? 'success' : row.status === 1 ? 'warning' : 'info'" size="small">
                  {{ row.status === 2 ? '✓ 已完成' : row.status === 1 ? '⏳ 进行中' : '待做' }}
                </el-tag>
                <el-button
                  v-if="row.status !== 2 && !isEmptyRow(row)" size="small" link type="primary"
                  :loading="row.advancing" @click="advancePrint(row)"
                >{{ row.status === 1 ? '完成' : '开始' }}</el-button>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="200" align="center">
              <template #default="{ row }">
                <template v-if="!isEmptyRow(row)">
                  <el-button
                    v-if="row.saveState === 'dirty'"
                    size="small" link type="primary" :loading="savingPlan"
                    @click="handleRowSave(row)"
                  >保存</el-button>
                  <el-button v-else size="small" link disabled>✓ 已存</el-button>
                  <el-button size="small" link icon="Top" :disabled="isFirst(row)" @click="movePrintRow(row, -1)">上移</el-button>
                  <el-button size="small" link icon="Bottom" :disabled="isLast(row)" @click="movePrintRow(row, 1)">下移</el-button>
                  <el-button size="small" link type="danger" icon="Delete" @click="removePrintRow(row)">删</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!filtered(tab.value).length" style="text-align:center;color:#c0c4cc;padding:24px 0;font-size:13px">
            暂无印刷工序，点击右上角【＋ 添加印刷工序】录入
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 材料选择弹窗 -->
    <el-dialog v-model="pickerVisible" title="添加材料" width="480px" append-to-body>
      <el-select
        v-model="pickerMaterialId"
        filterable
        remote
        :remote-method="searchMaterial"
        :loading="pickerLoading"
        placeholder="搜索物料档案"
        style="width:100%"
      >
        <el-option
          v-for="opt in pickerOptions" :key="opt.materialId"
          :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
          :value="opt.materialId"
        />
      </el-select>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!pickerMaterialId" @click="confirmMaterial">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'

/**
 * 印刷工序面板（dev-20260811-009）
 * 一级大类=PRINT：无标准工序、全自定义工序，表格逐行录入
 * 子结构沿用面板/上线/下线/未分类；行可增删/上移下移/状态推进/挂材料
 */
const props = defineProps<{
  printList: any[]
  savingPlan: boolean
  parseMaterials: (json?: string | null) => any[]
  addPrintRow: (category?: string) => void
  removePrintRow: (row: any) => void
  movePrintRow: (row: any, dir: number) => void
  advancePrint: (row: any) => void
  savePlan: () => void
}>()

// 子结构 tabs（与冲型组装 planTabs 一致）
const tabs = [
  { value: 'PANEL', label: '面板' },
  { value: 'UP_LINE', label: '上线' },
  { value: 'DOWN_LINE', label: '下线' },
  { value: '', label: '未分类' },
]
const activeTab = ref('PANEL')

function filtered(value: string) {
  return props.printList.filter((r) => (r.category || '') === value)
}

// 空行判定：新建行(uid 以 new- 开头)且所有录入字段为空
function isEmptyRow(r: any) {
  return !!r
    && String(r.uid || '').startsWith('new-')
    && !(r.printName || '').trim()
    && !(r.colorNo || '').trim()
    && !(r.inkNo || '').trim()
    && !(r.screenNo || '').trim()
}

// 行级保存（2026-08-12）：编辑不再自动补行，点“保存”才提交并触发新行/材料/执行时间线

// 结构变化（加载/删除/保存重建）→ 确保当前 tab 末尾有一行空行；输入内容变化不触发
const uidSnapshot = ref('')
watch(
  () => props.printList.map((r) => String(r.uid)).join('|'),
  (uids) => {
    if (uids === uidSnapshot.value) return
    uidSnapshot.value = uids
    ensureEmptyRow()
  },
  { immediate: true },
)

// 切换子结构 tab → 目标 tab 也保证空行
watch(activeTab, () => ensureEmptyRow())

function ensureEmptyRow() {
  const rows = filtered(activeTab.value)
  const last = rows[rows.length - 1]
  if (!last || !isEmptyRow(last)) {
    props.addPrintRow(activeTab.value)
  }
}

// 行内容变化 → 标记 dirty（非空行，编辑后显示“保存”按钮）
watch(
  () => props.printList.map((r) =>
    `${r.uid}:${(r.printName || '').trim()}|${(r.colorNo || '').trim()}|${(r.inkNo || '').trim()}|${(r.screenNo || '').trim()}|${r.materials || ''}`
  ).join('|'),
  () => {
    props.printList.forEach((r) => {
      if (isEmptyRow(r)) return
      if (r.saveState === 'saving') return
      r.saveState = 'dirty'
    })
  },
)

// 行保存：提交整单（父组件 savePlan 内含 loadPlan+loadBom），成功后自动补空行/刷新时间线
async function handleRowSave(row: any) {
  if (props.savingPlan) return
  row.saveState = 'saving'
  try {
    await props.savePlan()
  } catch (e: any) {
    row.saveState = 'dirty'
    ElMessage.error(e?.message || '保存失败')
  }
}
function isFirst(row: any) {
  const arr = filtered(activeTab.value)
  return arr.indexOf(row) <= 0
}
function isLast(row: any) {
  const arr = filtered(activeTab.value)
  return arr.indexOf(row) >= arr.length - 1
}

// 材料选择弹窗
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerOptions = ref<any[]>([])
const pickerMaterialId = ref<number | null>(null)
const pickerTarget = ref<any>(null)

async function openMaterialPicker(row: any) {
  pickerTarget.value = row
  pickerMaterialId.value = null
  pickerOptions.value = []
  pickerVisible.value = true
  await searchMaterial('')
}

async function searchMaterial(query: string) {
  pickerLoading.value = true
  try {
    const params: any = { pageNum: 1, pageSize: 20 }
    if ((query || '').trim()) params.materialName = query.trim()
    const res: any = await materialApi.search(params)
    pickerOptions.value = res?.data?.records || res?.data || []
  } catch {
    pickerOptions.value = []
  } finally {
    pickerLoading.value = false
  }
}

function confirmMaterial() {
  const row = pickerTarget.value
  const opt = pickerOptions.value.find((o) => o.materialId === pickerMaterialId.value)
  if (!row || !opt) return
  const mats = props.parseMaterials(row.materials) || []
  mats.push({ name: opt.materialName, spec: opt.specification || '', qty: 1, unit: opt.unitName || '', materialId: opt.materialId, materialCode: opt.materialCode || '' })
  row.materials = JSON.stringify(mats)
  row.saveState = 'dirty'
  pickerVisible.value = false
  ElMessage.success(`已添加 ${opt.materialName} 到该印刷工序`)
}

function removeMaterial(row: any, idx: number) {
  const mats = props.parseMaterials(row.materials) || []
  mats.splice(idx, 1)
  row.materials = mats.length ? JSON.stringify(mats) : null
  row.saveState = 'dirty'
}
</script>

<style scoped>
.desc {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
.mat-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.input-error :deep(.el-input__inner) {
  border-color: #f56c6c;
}
</style>
