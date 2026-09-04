<template>
  <div class="print-panel">
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600">🖨️ 印刷工序（自定义工序，表格录入）</span>
        <span class="desc">无标准工序库，逐行录入；每行一道印刷，可排序/推进/挂材料</span>
        <el-button
          type="success"
          size="small"
          :loading="savingPlan"
          :disabled="readonly"
          @click="savePlan"
          style="float: right; margin-top: -2px"
          >💾 保存工序计划</el-button
        >
        <el-button
          type="primary"
          size="small"
          icon="Plus"
          :disabled="readonly"
          @click="addPrintRow(activeTab)"
          style="float: right; margin-top: -2px; margin-right: 8px"
          >＋ 添加印刷工序</el-button
        >
      </template>

      <!-- 子结构 tabs（面板/上线/下线/未分类，与冲型组装一致） -->
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.value"
          :name="tab.value"
          :label="`${tab.label}（${filtered(tab.value).length}）`"
        >
          <el-table :data="filtered(tab.value)" size="small" border stripe>
            <el-table-column label="#" width="44" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="印刷名称 *" min-width="140">
              <template #default="{ row }">
                <el-autocomplete
                  v-model="row.printName"
                  size="small"
                  :fetch-suggestions="(q, cb) => suggestFrom(q, cb, 'printNames')"
                  :trigger-on-focus="true"
                  clearable
                  placeholder="如：丝印/移印/网印"
                  :class="{ 'input-error': !(row.printName || '').trim() }"
                />
              </template>
            </el-table-column>
            <el-table-column label="色号" width="140">
              <template #default="{ row }">
                <el-autocomplete
                  v-model="row.colorNo"
                  size="small"
                  :fetch-suggestions="suggestColors"
                  :trigger-on-focus="true"
                  clearable
                  placeholder="选择或手输色号"
                />
              </template>
            </el-table-column>
            <el-table-column label="油墨" min-width="230">
              <template #default="{ row }">
                <el-autocomplete
                  v-model="row.inkNo"
                  size="small"
                  :fetch-suggestions="suggestInks"
                  :trigger-on-focus="true"
                  clearable
                  placeholder="选择 INK 物料，或直接手输"
                  @select="(item: any) => onInkSelect(row, item)"
                  @input="(val: string | number) => onInkInput(row, String(val ?? ''))"
                  @clear="() => onInkClear(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="网框编号" width="160">
              <template #default="{ row }">
                <el-autocomplete
                  v-model="row.screenNo"
                  size="small"
                  :fetch-suggestions="suggestScreen"
                  :trigger-on-focus="true"
                  clearable
                  placeholder="网框编号"
                />
              </template>
            </el-table-column>
            <el-table-column label="🧾 材料" min-width="180">
              <template #default="{ row }">
                <div class="mat-cell">
                  <el-tag
                    v-for="(m, i) in parseMaterials(row.materials)"
                    :key="i"
                    size="small"
                    type="info"
                    closable
                    @close="removeMaterial(row, i)"
                    style="margin-right: 4px; margin-bottom: 2px"
                  >
                    {{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}
                  </el-tag>
                  <el-button
                    size="small"
                    link
                    type="primary"
                    icon="Plus"
                    @click="openMaterialPicker(row)"
                    >材料</el-button
                  >
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="left">
              <template #default="{ row }">
                <el-tag
                  :type="
                    row.status === ProcessStatusEnum.DONE.value
                      ? 'success'
                      : row.status === ProcessStatusEnum.DOING.value
                        ? 'warning'
                        : 'info'
                  "
                  size="small"
                >
                  {{
                    row.status === ProcessStatusEnum.DONE.value
                      ? '✓ 已完成'
                      : row.status === ProcessStatusEnum.DOING.value
                        ? '⏳ 进行中'
                        : '待做'
                  }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="200" align="center">
              <template #default="{ row, $index }">
                <template v-if="!isEmptyRow(row)">
                  <el-button
                    v-if="row.saveState === 'dirty'"
                    size="small"
                    link
                    type="primary"
                    :loading="savingPlan"
                    @click="handleRowSave(row)"
                  ></el-button>
                  <!-- <el-button v-else size="small" link disabled>✓ 已存</el-button> -->
                  <el-button
                    size="small"
                    link
                    icon="Top"
                    :disabled="readonly || isFirst(row)"
                    @click="movePrintRow(row, -1)"
                  ></el-button>
                  <el-button
                    size="small"
                    link
                    icon="Bottom"
                    :disabled="readonly || isLast(row)"
                    @click="movePrintRow(row, 1)"
                  ></el-button>
                  <el-button
                    size="small"
                    link
                    type="danger"
                    icon="Delete"
                    :disabled="readonly"
                    @click="handleDeleteRow(row)"
                  ></el-button>
                </template>
                <!-- 空行（待录入占位行）也提供删除：防止手动新增多行空行后无法清理（2026-09-04） -->
                <el-button
                  v-else
                  size="small"
                  link
                  type="danger"
                  icon="Delete"
                  :disabled="readonly"
                  @click="handleDeleteRow(row, $index)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
          <div
            v-if="!filtered(tab.value).length"
            style="text-align: center; color: #c0c4cc; padding: 24px 0; font-size: 13px"
          >
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
        style="width: 100%"
      >
        <el-option
          v-for="opt in pickerOptions"
          :key="opt.materialId"
          :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
          :value="opt.materialId"
        />
      </el-select>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!pickerMaterialId" @click="confirmMaterial"
          >添加</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { getProcessHistory, suggestSampleColors, suggestSampleInks } from '@/api/sales/sampleOrder'
import { ProcessStatusEnum } from '@/enums/product/process'
import { suggestScreen as suggestScreenApi } from '@/api/engineering/screen'

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
  savePlan: () => void
  readonly?: boolean
}>()

// 子结构 tabs（与冲型组装 planTabs 一致）
const tabs = [
  { value: 'PANEL', label: '面板' },
  { value: 'UP_LINE', label: '上线' },
  { value: 'DOWN_LINE', label: '下线' },
  { value: '', label: '未分类' },
]
const activeTab = ref('PANEL')

// 印刷历史联想缓存（印刷名称仍沿用历史联想）
const historyCache = ref<Record<string, string[]>>({ printNames: [], colorNos: [], inkNos: [] })

// 色号联想（2026-09-04 搜索式下拉）：空输入 → 后端常用 TOP10；有输入 → 字典模糊搜
async function suggestColors(query: string, cb: (items: { value: string }[]) => void) {
  try {
    const res: any = await suggestSampleColors(query || undefined, 10)
    const list: string[] = res?.data || []
    cb(list.map((value) => ({ value })))
  } catch {
    cb([])
  }
}

// 油墨联想（2026-09-04 搜索式下拉，参考色号方案）：
// 空输入 → 后端常用 TOP10（历史 inkNo 频次 + INK 物料补足）；有输入 → INK 物料+历史模糊搜。
// 返回项带 materialId：点选物料自动关联；手输文本自动解除关联防错位。
async function suggestInks(query: string, cb: (items: any[]) => void) {
  try {
    const res: any = await suggestSampleInks(query || undefined, 10)
    const list: any[] = res?.data || []
    cb(list.map((x) => ({ value: x.text, materialId: x.materialId ?? null })))
  } catch {
    cb([])
  }
}

function onInkSelect(row: any, item: any) {
  // 点选联想项：文本 + 物料关联（若该文本命中 INK 物料）
  row._inkPickedText = item.value
  row.inkMaterialId = item.materialId ?? null
}

function onInkInput(row: any, val: string) {
  // 手输修改（不是刚点选的文本）→ 解除物料关联，防止“文本改了物料 id 还是旧的”错位
  if (row._inkPickedText !== val) {
    row.inkMaterialId = null
  }
}

function onInkClear(row: any) {
  row._inkPickedText = ''
  row.inkMaterialId = null
}

async function loadHistory() {
  try {
    const res: any = await getProcessHistory()
    if (res?.data) historyCache.value = res.data
  } catch {
    /* 联想失败不影响录入 */
  }
}
loadHistory()

// 历史联想：从缓存按关键字过滤，返回 [{value}]
function suggestFrom(query: string, cb: (items: { value: string }[]) => void, key: string) {
  const list: string[] = historyCache.value[key] || []
  const q = (query || '').trim().toLowerCase()
  const filtered = q ? list.filter((v) => v.toLowerCase().includes(q)) : list
  cb(filtered.slice(0, 20).map((value) => ({ value })))
}

// 网框联想：调网版主数据 suggest 接口（编号+内容显示）
async function suggestScreen(query: string, cb: (items: { value: string }[]) => void) {
  try {
    const res: any = await suggestScreenApi(query || undefined, 20)
    const list = res?.data || []
    cb(list.map((s: any) => ({ value: `${s.screenNo} ${s.content || ''}`.trim() })))
  } catch {
    cb([])
  }
}

function filtered(value: string) {
  return props.printList.filter((r) => (r.category || '') === value)
}

// 空行判定：新建行(uid 以 new- 开头)且所有录入字段为空
function isEmptyRow(r: any) {
  return (
    !!r &&
    String(r.uid || '').startsWith('new-') &&
    !(r.printName || '').trim() &&
    !(r.colorNo || '').trim() &&
    !(r.colorNoLabel || '').trim() &&
    r.inkMaterialId == null &&
    !(r.inkNo || '').trim() &&
    !(r.screenNo || '').trim()
  )
}

/** 行内容串（dirty 比对用，不含 uid） */
function rowContent(r: any) {
  return `${(r.printName || '').trim()}|${(r.colorNo || '').trim()}|${(r.colorNoLabel || '').trim()}|${r.inkMaterialId ?? ''}|${(r.inkNo || '').trim()}|${(r.screenNo || '').trim()}|${r.materials || ''}`
}

// 删除行（2026-09-04）：空行直接删除——若删后该分类无行则抑制一次自动补行（显示空态）；
// 内容行删除同父级语义（本地移除，保存后后端生效）
function handleDeleteRow(row: any, $index?: number) {
  console.log('handleDeleteRow', row, $index)
  if (props.readonly) return
  const isEmpty = isEmptyRow(row)
  const rows = filtered(activeTab.value)
  const willEmpty = isEmpty && rows.length === 1
  const i = props.printList.indexOf(row)
  if (i < 0) return
  props.printList.splice(i, 1)
  if (isEmpty) {
    if (willEmpty) suppressEnsureOnce = true
    ElMessage.success('已删除空行')
  } else {
    ElMessage.success('已删除该印刷工序（保存后生效）')
  }
}

// 行级保存（2026-08-12）：编辑不再自动补行，点“保存”才提交并触发新行/材料/执行时间线

// 结构变化（加载/删除/保存重建）→ 确保当前 tab 末尾有一行空行；输入内容变化不触发
// 2026-09-04：手动删除最后一个空行后抑制一次自动补行，允许分类显示空态（空态有“添加印刷工序”入口）
let suppressEnsureOnce = false
const uidSnapshot = ref('')
watch(
  () => props.printList.map((r) => String(r.uid)).join('|'),
  (uids) => {
    if (uids === uidSnapshot.value) return
    uidSnapshot.value = uids
    if (suppressEnsureOnce) {
      suppressEnsureOnce = false
      return
    }
    ensureEmptyRow()
  },
  { immediate: true }
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

// 行内容变化 → 标记 dirty：逐行比对 uid→内容快照，仅真正编辑过的行标 dirty
// （2026-09-04 修复：删除/自动补行属于结构变化，不应把其它行误标 dirty 而弹出“保存”按钮）
const rowContentSnapshot = ref<Record<string, string>>({})
watch(
  () => props.printList.map((r) => `${r.uid}::${rowContent(r)}`).join('|'),
  () => {
    const snap = rowContentSnapshot.value
    props.printList.forEach((r) => {
      if (isEmptyRow(r)) return
      if (r.saveState === 'saving') return
      const uid = String(r.uid)
      const content = rowContent(r)
      if (snap[uid] !== undefined && snap[uid] !== content) {
        r.saveState = 'dirty'
      }
    })
    const next: Record<string, string> = {}
    props.printList.forEach((r) => {
      next[String(r.uid)] = rowContent(r)
    })
    rowContentSnapshot.value = next
  },
  { immediate: true }
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
  mats.push({
    name: opt.materialName,
    spec: opt.specification || '',
    qty: 1,
    unit: opt.unitName || '',
    materialId: opt.materialId,
    materialCode: opt.materialCode || '',
  })
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
