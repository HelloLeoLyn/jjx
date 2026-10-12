<template>
  <section v-loading="loading || fetching" class="print-spec-section">
    <div class="print-actions">
      <h3>印刷规范</h3>
      <el-button v-if="canEdit" :disabled="loading || fetching" @click="editRemarks">编辑</el-button>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button link type="primary" @click="loadData">重新加载</el-button>
    </el-alert>
    <el-tabs v-model="printTab">
      <el-tab-pane v-for="group in printGroups" :key="group.value" :name="group.value" :label="group.label">
        <el-table :data="group.rows" border size="small" :empty-text="`暂无${group.label}印刷工序`">
          <el-table-column :label="`${group.label}印序`" min-width="180"><template #default="{ row, $index }">{{ $index + 1 }}. {{ row.processName ?? printParams(row).printName ?? '' }}<div v-if="row.workInstruction" class="print-instruction">{{ row.workInstruction }}</div></template></el-table-column>
          <el-table-column label="色号" min-width="100"><template #default="{ row }">{{ printParams(row).colorNo ?? '-' }}</template></el-table-column>
          <el-table-column label="油墨编号" min-width="120"><template #default="{ row }">{{ printParams(row).inkNo ?? '-' }}</template></el-table-column>
          <el-table-column v-if="showFilm" label="菲林" min-width="120"><template #default="{ row }">{{ printParams(row).filmNo ?? '-' }}</template></el-table-column>
          <el-table-column label="网框编号" min-width="120"><template #default="{ row }">{{ printParams(row).screenNo ?? '-' }}</template></el-table-column>
          <el-table-column label="网板编号" min-width="140">
            <template #header><el-tooltip :content="readOnly ? '历史版本只显示工序中记录的网板编号' : '按网框台账当前有效版面读取网板编号'" placement="top"><span>网板编号</span></el-tooltip></template>
            <template #default="{ row }">{{ plateNumber(row) }}</template>
          </el-table-column>
          <el-table-column label="人工工时(h)" width="120" align="right">
            <template #default="{ row }">{{ row.customLaborHours ?? row.standardLaborHours ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="机器工时(h)" width="120" align="right">
            <template #default="{ row }">{{ row.customMachineHours ?? row.standardMachineHours ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="180">
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
        <div v-if="currentWorkSpec" class="print-group"><strong>{{ group.label }}备注</strong><p>{{ printRemarks[group.value] || '未填写' }}</p></div>
      </el-tab-pane>
    </el-tabs>
    <el-button v-if="canEdit" link type="primary" @click="openHistory">备注修改记录</el-button>

    <el-dialog v-model="editorVisible" title="印刷规范 · 整组备注" width="720px" append-to-body destroy-on-close :close-on-click-modal="false" :before-close="closeEditor">
      <el-form label-position="top" :disabled="saving">
        <el-form-item v-for="group in flowGroups" :key="group.value" :label="group.label + '印刷备注'">
          <el-input v-model="draft[group.value]" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="选填；本组印刷共同要求，可换行" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="closeEditor()">关闭</el-button>
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="historyVisible" title="整组备注修改记录" width="900px" append-to-body>
      <div v-loading="historyLoading" class="remark-history">
        <el-alert v-if="historyError" :title="historyError" type="error" :closable="false" />
        <section v-for="item in history" :key="item.id" class="history-item">
          <div class="history-heading">{{ item.changedAt }}　{{ item.operatorName }}</div>
          <div v-for="field in item.fields" :key="field.field" class="history-field">
            <strong>{{ field.label }}</strong>
            <div class="history-values"><div><small>修改前</small><p>{{ field.before || '（空）' }}</p></div><div><small>修改后</small><p>{{ field.after || '（空）' }}</p></div></div>
          </div>
        </section>
        <el-empty v-if="!historyLoading && !historyError && !history.length" description="暂无备注修改记录" />
      </div>
      <template #footer><el-button v-if="historyError" :disabled="historyLoading" @click="loadHistory(nextBefore)">重试</el-button><el-button v-if="nextBefore" :loading="historyLoading" @click="loadHistory(nextBefore)">加载更早记录</el-button><el-button @click="historyVisible = false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { normalizeWorkSpec, productWorkSpecApi, type ProductWorkSpec, type PrintRemarkHistory } from '@/api/product/workSpec'
import { flowGroups, printParams } from './docset'
import { engineeringResourceApi } from '@/api/engineering/resource'
import { processRowsForGroup, currentPlateForFrame, type SpecProcessRow } from './workSpecProcessRows'

/** 传入 routingItems 时直接展示，可附带 workSpec；仅传 productId 时自主取数。 */
const props = defineProps<{ productId?: number; workSpec?: ProductWorkSpec; routingItems?: SpecProcessRow[]; loading?: boolean; readOnly?: boolean }>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [workSpec: ProductWorkSpec] }>()
const user = useUserStore()
const fetchedSpec = ref<ProductWorkSpec>()
const fetchedRoutingItems = ref<SpecProcessRow[]>([])
const fetching = ref(false)
const loadError = ref('')
let loadGeneration = 0
const currentWorkSpec = computed(() => props.workSpec ?? fetchedSpec.value)
const currentRoutingItems = computed(() => props.routingItems ?? fetchedRoutingItems.value)
const printRemarks = computed(() => currentWorkSpec.value?.printRemarks || {})
const canEdit = computed(() => !props.readOnly && !!props.productId && !!currentWorkSpec.value && user.hasPermission('product:edit'))
const editorVisible = ref(false)
const saving = ref(false)
const draft = ref<Record<string, string>>({})
const baseline = ref('')
const revision = ref('')
const dirty = computed(() => JSON.stringify(draft.value) !== baseline.value)
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyError = ref('')
const history = ref<PrintRemarkHistory[]>([])
const nextBefore = ref<number>()
let disposed = false
let plateGeneration = 0
const printTab = ref(flowGroups[0]?.value || '')
const routeCategories = [...flowGroups, { value: '', label: '未分类', symbol: '' }]
const normalizedRouteItems = computed(() => {
  const known = new Set(flowGroups.map((group) => group.value))
  const normalize = (rows: SpecProcessRow[]): SpecProcessRow[] =>
    rows.map((row) => ({
      ...row,
      processCategory: known.has(row.processCategory) ? row.processCategory : '',
      ...(row.children ? { children: normalize(row.children) } : {}),
    }))
  return normalize(currentRoutingItems.value)
})
const printGroups = computed(() => routeCategories.map(group => {
  const rows = processRowsForGroup(normalizedRouteItems.value, 'PRINT', group.value)
  rows.sort((a, b) => (a.workflowSeq ?? 1) - (b.workflowSeq ?? 1)
    || (a.processOrder || 0) - (b.processOrder || 0))
  return { ...group, rows }
}))
watch(
  printGroups,
  (groups) => {
    printTab.value = groups.find((group) => group.rows.length)?.value ?? flowGroups[0]?.value ?? ''
  },
  { immediate: true }
)
const printRows = computed(() => printGroups.value.flatMap(group => group.rows))
const showFilm = computed(() => printRows.value.some(row => String(printParams(row).filmNo ?? '').length > 0))
const frameNumbers = computed(() => [...new Set(printRows.value.map(row => String(printParams(row).screenNo || '').trim()).filter(Boolean))])
const canReadFrames = computed(() => user.hasPermission('engineering:screen-resource:view'))
const plateNumbers = ref<Record<string, string>>({})
function plateNumber(row: SpecProcessRow): string {
  const params = printParams(row)
  if (params.plateNo) return String(params.plateNo)
  if (props.readOnly) return '未记录'
  const frameNo = String(params.screenNo || '').trim()
  if (!frameNo) return '-'
  if (!canReadFrames.value) return '无台账查看权限'
  return plateNumbers.value[frameNo] ?? '读取中…'
}
async function loadPlates() {
  const generation = ++plateGeneration
  plateNumbers.value = {}
  if (props.readOnly || !canReadFrames.value) return
  // 按网框去重查询，避免同一网框在多组/多道印序中重复请求。
  for (const frameNo of frameNumbers.value) {
    if (generation !== plateGeneration || disposed) return
    try {
      const plate = await currentPlateForFrame(frameNo, async pageNum => {
        const response = await engineeringResourceApi.frames({ frameNo, pageNum, pageSize: 200 })
        return response.data || {}
      })
      if (generation === plateGeneration && !disposed) plateNumbers.value[frameNo] = plate || '-'
    } catch (error) {
      console.error('读取网板编号失败', frameNo, error)
      if (generation === plateGeneration && !disposed) plateNumbers.value[frameNo] = '读取失败'
    }
  }
}
watch(() => [JSON.stringify(frameNumbers.value), props.productId, props.readOnly, canReadFrames.value], loadPlates, { immediate: true })

function editRemarks() {
  const spec = currentWorkSpec.value
  if (!canEdit.value || props.loading || fetching.value || !spec) return
  draft.value = Object.fromEntries(flowGroups.map(group => [group.value, spec.printRemarks[group.value] || '']))
  revision.value = spec.revision
  baseline.value = JSON.stringify(draft.value)
  editorVisible.value = true
}
async function allowLeave() {
  if (saving.value) { ElMessage.warning('备注正在保存，请稍候'); return false }
  if (!editorVisible.value || !dirty.value) return true
  try {
    await ElMessageBox.confirm('整组备注有未保存修改，是否放弃？', '未保存修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' })
    return true
  } catch { return false }
}
async function closeEditor(done?: () => void) {
  if (!await allowLeave()) return
  if (done) done()
  else editorVisible.value = false
}
async function save() {
  if (saving.value || !dirty.value || !props.productId) return
  saving.value = true
  try {
    const response = await productWorkSpecApi.savePrintRemarks(props.productId, revision.value, draft.value)
    if (disposed) return
    baseline.value = JSON.stringify(draft.value)
    editorVisible.value = false
    ElMessage.success('整组备注已保存，修改记录已保留')
    const updated = normalizeWorkSpec(response.data)
    if (!props.workSpec) fetchedSpec.value = updated
    emit('updated', updated)
  } catch (error) { console.error('保存印刷整组备注失败', error) }
  finally { saving.value = false }
}
async function openHistory() {
  historyVisible.value = true
  if (historyLoading.value) return
  history.value = []
  nextBefore.value = undefined
  await loadHistory()
}
async function loadHistory(before?: number) {
  if (historyLoading.value || !props.productId) return
  historyLoading.value = true
  historyError.value = ''
  try {
    const response = await productWorkSpecApi.printRemarksHistory(props.productId, before)
    if (disposed) return
    history.value.push(...(response.data?.items || []))
    nextBefore.value = response.data?.nextBefore || undefined
  } catch (error) {
    console.error('读取印刷备注历史失败', error)
    historyError.value = '修改记录加载失败，请重试'
  } finally { historyLoading.value = false }
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (saving.value || (editorVisible.value && dirty.value)) { event.preventDefault(); event.returnValue = '' }
}
async function loadData() {
  const generation = ++loadGeneration
  if (props.routingItems !== undefined || !props.productId) {
    fetchedSpec.value = undefined
    fetchedRoutingItems.value = []
    loadError.value = ''
    fetching.value = false
    return
  }
  fetchedSpec.value = undefined
  fetchedRoutingItems.value = []
  fetching.value = true
  loadError.value = ''
  try {
    const response = await productWorkSpecApi.get(props.productId)
    if (generation !== loadGeneration || disposed) return
    if (!response.data?.sourceData?.product) throw new Error('未找到产品资料')
    fetchedSpec.value = normalizeWorkSpec(response.data)
    fetchedRoutingItems.value = response.data.sourceData.routing?.items || []
  } catch (error) {
    if (generation === loadGeneration && !disposed) {
      console.error('加载印刷规范失败', error)
      loadError.value = error instanceof Error ? error.message : '印刷规范加载失败，请重试'
    }
  } finally {
    if (generation === loadGeneration && !disposed) fetching.value = false
  }
}
watch(() => [props.productId, props.workSpec, props.routingItems], () => { void loadData() }, { immediate: true })
watch(() => editorVisible.value || saving.value, value => emit('busy', value))
onBeforeRouteLeave(allowLeave)
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { disposed = true; loadGeneration++; plateGeneration++; window.removeEventListener('beforeunload', beforeUnload); emit('busy', false) })
</script>

<style scoped>
.print-actions { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-bottom:12px; flex-wrap:wrap; }
.print-spec-section { padding:16px; border:1px solid var(--el-border-color-light); border-radius:6px; margin-bottom:20px; }
.print-actions h3 { margin:0; font-size:15px; }
.print-group { margin:16px 0; font-size:14px; }
.print-group p { white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.7; margin:8px 0 0; }
.print-instruction { color:var(--el-text-color-secondary); font-size:12px; margin-top:4px; }
.remark-history { max-height:60vh; overflow:auto; min-height:120px; }
.history-item { padding:12px 0; border-bottom:1px solid #ebeef5; }
.history-heading { color:#697586; margin-bottom:10px; }
.history-field { margin-top:12px; }
.history-values { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:6px; }
.history-values > div { background:#f5f7fa; padding:10px; border-radius:4px; min-width:0; }
.history-values small { color:#909399; }
.history-values p { white-space:pre-wrap; overflow-wrap:anywhere; margin:6px 0 0; }
</style>
