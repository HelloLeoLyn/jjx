<template>
  <div class="work-spec-panel">
    <div class="work-actions">
      <strong>作业规范</strong>
      <div class="source-actions">
        <el-button :disabled="!data || loading || editing" @click="previewVisible = true">打印预览</el-button>
        <el-button v-if="canConfirm" type="success" :disabled="!data || loading || editing || data.workSpec.approved" :loading="confirming" @click="confirmSpec">确认已批准</el-button>
      </div>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" class="data-message">
      <el-button link type="primary" @click="loadData">重新加载</el-button>
    </el-alert>
    <el-alert v-if="data?.warnings.length" :title="data.warnings.join('；')" type="warning" :closable="false" class="data-message" />
    <div v-loading="loading" class="data-sections">
      <template v-if="data">
        <section class="spec-top">
          <div class="spec-top-col">
            <div class="section-heading"><h3>结构图</h3></div>
            <div class="structure-preview">
              <img v-if="structureImage" :src="structureImage" :alt="`${productName || productCode} 结构图`" />
              <span v-else class="empty-hint">未选择结构图</span>
            </div>
            <p v-if="structureCaption" class="structure-caption">{{ structureCaption }}</p>
          </div>
          <div class="spec-top-col">
            <div class="section-heading"><h3>凹凸条件</h3><el-button v-if="canEdit" :disabled="loading" @click="edit('requirements')">编辑</el-button></div>
            <el-descriptions :column="2" border>
              <el-descriptions-item v-for="field in embossFields" :key="field.key" :label="field.label">
                {{ data.workSpec.emboss[field.key] ? `${data.workSpec.emboss[field.key]} ${field.unit}` : '未填写' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="spec-top-col">
            <div class="section-heading"><h3>备注</h3><el-button v-if="canEdit" :disabled="loading" @click="edit('requirements')">编辑</el-button></div>
            <p class="multiline">{{ data.workSpec.engineeringRequirements || '未填写' }}</p>
          </div>
        </section>
        <section class="data-section">
          <div class="section-heading"><h3>BOM</h3><el-button v-if="canEditBom" :disabled="loading || !data.bom.bomId" @click="bomVisible = true">编辑</el-button></div>
          <el-table :data="data.bom.items || []" row-key="itemId" default-expand-all border size="small" empty-text="暂无 BOM 材料">
            <el-table-column prop="materialName" label="材料" min-width="180" />
            <el-table-column label="规格" min-width="180"><template #default="{ row }">{{ workSpecMaterialSpec(row) || '-' }}</template></el-table-column>
            <el-table-column label="用量" width="120"><template #default="{ row }">{{ calculateBomQuantity(row.baseQty, row.moduleQty) ?? row.quantity ?? '-' }}</template></el-table-column>
            <el-table-column prop="unit" label="单位" width="90" />
          </el-table>
        </section>
        <section class="data-section">
          <div class="section-heading"><h3>刀模位置</h3><el-button v-if="canEdit" :disabled="loading" @click="edit('die')">编辑</el-button></div>
          <p class="multiline">{{ data.workSpec.dieLocation || '未填写' }}</p>
        </section>
        <section class="data-section">
          <div class="section-heading"><h3>作业流程</h3><el-button v-if="canEditRoute" :disabled="loading || !data.routing.routingId" @click="routeVisible = true">编辑</el-button></div>
          <el-tabs v-model="workflowTab">
            <el-tab-pane v-for="group in workflowGroups" :key="group.value" :name="group.value" :label="group.label">
              <el-table :data="group.rows" border size="small" :empty-text="`暂无${group.label}冲型组装工序`">
                <el-table-column type="index" label="序号" width="70" />
                <el-table-column label="流程" min-width="220">
                  <template #default="{ row }">
                    <div class="workflow-operation">
                      <div class="workflow-symbols">
                        <template v-for="(item, i) in workflowOperations(row)" :key="item.itemId || i">
                          <span v-if="i">＋</span>
                          <span class="workflow-symbol">
                            <SvgIcon v-if="item.icon" :name="item.icon" :size="24" />
                            <span v-else>{{ item.processName || '-' }}</span>
                            <sub v-if="workSpecStepSubscript(item)">{{ workSpecStepSubscript(item) }}</sub>
                          </span>
                        </template>
                      </div>
                      <span class="workflow-remark">{{ workSpecOperationRemark(row) }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="耗时(h)" width="100"><template #default="{ row }">{{ workflowLabor(row) }}</template></el-table-column>
                <el-table-column label="工序名称" min-width="180"><template #default="{ row }">{{ workflowNames(row) }}</template></el-table-column>
                <el-table-column label="备注" min-width="180"><template #default="{ row }"><span class="multiline">{{ workSpecOperationRemark(row) || '-' }}</span></template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </section>
        <ProductPrintSpecPanel :product-id="productId" :work-spec="data.workSpec" :routing-items="data.routing.items || []" :loading="loading" @busy="printSpecBusy = $event" @updated="printRemarksSaved" />
      </template>
    </div>
    <el-dialog v-model="previewVisible" title="作业规范 · 打印预览" width="92%" append-to-body destroy-on-close>
      <ProductSpecPreview v-if="previewVisible" :product-id="productId" :initial-sections="['spec', 'print']" />
    </el-dialog>

    <el-dialog v-model="editorVisible" title="工程维护 · 产品作业规范" width="1060px" append-to-body destroy-on-close :close-on-click-modal="false" :before-close="closeEditor">
      <div class="editor-product">{{ productName }}　{{ productCode }}</div>
      <el-tabs v-model="editorTab">
        <el-tab-pane label="凹凸条件与备注" name="requirements">
          <el-form label-width="130px">
            <div class="emboss-fields"><el-form-item v-for="field in embossFields" :key="field.key" :label="field.label"><el-input v-model="draft.emboss[field.key]" placeholder="选填" maxlength="20"><template #append>{{ field.unit }}</template></el-input></el-form-item></div>
            <el-form-item label="备注"><el-input v-model="draft.engineeringRequirements" type="textarea" :rows="7" maxlength="2000" show-word-limit placeholder="选填；整份规范的备注，每项可单独换行" /></el-form-item>
            <el-form-item label="文字颜色"><el-select v-model="draft.requirementsColor"><el-option v-for="color in workSpecColors" :key="color.value" :label="color.label" :value="color.value" /></el-select></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="刀模位置" name="die">
          <el-form label-width="100px"><el-form-item label="刀模位置"><el-input v-model="draft.dieLocation" type="textarea" :rows="8" maxlength="500" show-word-limit placeholder="选填；每行填写一个位置，可留空行，与材料独立维护" /></el-form-item></el-form>
        </el-tab-pane>
        <el-tab-pane label="结构图" name="structure">
          <el-form label-width="100px"><el-form-item label="规范结构图"><el-select v-model="draft.structureFileId" clearable filterable placeholder="选填；选择这张规范单使用的图纸" style="width:100%"><el-option v-for="file in structureOptions" :key="file.id" :label="documentFileCaption(file)" :value="file.id" /></el-select></el-form-item></el-form>
          <EngineeringDrawingLibrary :product-code="productCode" :product-name="productName" :categories="['结构图']" :active="editorTab === 'structure'" @busy="drawingBusy = $event" @loaded="refreshDrawings" />
        </el-tab-pane>
        <el-tab-pane label="变更与发行" name="changes">
          <div class="change-heading"><strong>变更内容 · 已选打印 {{ draft.changes.filter(change => change.print).length }} 条</strong><div><el-button :disabled="draft.changes.length >= 100" @click="openChangeSources">引用工程变更</el-button><el-button :disabled="draft.changes.length >= 100" @click="addChange">手工新增</el-button></div></div>
          <p class="change-tip">勾选“打印”的内容会同时显示在纸张预览和打印单中；未勾选的记录仍保留。新记录默认不打印。</p>
          <div v-for="(change, i) in draft.changes" :key="i" class="change-card">
            <div class="change-row">
              <el-date-picker v-model="change.date" type="date" value-format="YYYY-MM-DD" placeholder="变更日期（选填）" />
              <el-input v-model="change.text" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="供生产阅读的变更说明（选填）" />
              <el-select v-model="change.color"><el-option v-for="color in workSpecColors" :key="color.value" :label="color.label" :value="color.value" /></el-select>
              <el-checkbox v-model="change.print">打印</el-checkbox>
              <el-button link type="danger" @click="draft.changes.splice(i, 1)">删除</el-button>
            </div>
            <el-input v-model="change.reason" maxlength="1000" placeholder="变更原因（选填，如客户要求、内部优化；不印在纸张上）" />
            <div class="change-source">来源：{{ change.sources?.length ? '工程变更引用' : '手工录入／历史补录' }}<span v-for="source in change.sources" :key="source.id">　{{ source.label }} · {{ source.date }}</span></div>
            <details v-if="change.sources?.length" class="source-details"><summary>查看原始变更内容</summary><p v-for="source in change.sources" :key="source.id">{{ source.label }}：{{ source.text }}</p></details>
          </div>
          <el-form label-width="100px" class="issue-form">
            <el-form-item label="发行单位"><el-input v-model="draft.issueUnit" maxlength="100" placeholder="选填" /></el-form-item>
            <el-form-item label="发行日期"><el-date-picker v-model="draft.issueDate" type="date" value-format="YYYY-MM-DD" placeholder="选填" /></el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer><el-button :disabled="saving || drawingBusy" @click="closeEditor()">关闭</el-button><el-button type="primary" :loading="saving" :disabled="drawingBusy || !dirty" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="changeSourcesVisible" title="引用已批准版本的工程变更" width="900px" append-to-body>
      <p class="change-tip">选择本次涉及的 BOM／工艺路线修改记录，合并为一条变更说明后可继续整理文字。</p>
      <el-checkbox-group v-model="selectedSourceIds" v-loading="changeSourcesLoading" class="source-options">
        <div v-for="source in changeSources" :key="source.id" class="source-option">
          <el-checkbox :value="source.id" :disabled="usedSourceIds.has(source.id)">{{ source.label }} · {{ source.date }}{{ usedSourceIds.has(source.id) ? '（已引用）' : '' }}</el-checkbox>
          <p>{{ source.text }}</p>
        </div>
      </el-checkbox-group>
      <el-empty v-if="!changeSourcesLoading && !changeSources.length" description="暂无可引用的已批准版本修改记录，可手工新增变更说明" />
      <el-button v-if="nextSourceBefore" :loading="changeSourcesLoading" @click="loadChangeSources(nextSourceBefore)">加载更早记录</el-button>
      <template #footer><el-button @click="changeSourcesVisible = false">关闭</el-button><el-button type="primary" :disabled="!selectedSourceIds.length || changeSourcesLoading" @click="importChangeSources">引用所选记录</el-button></template>
    </el-dialog>
    <BomFormDialog v-if="data?.bom.bomId" v-model="bomVisible" :bom-id="Number(data.bom.bomId)" @success="sourcesSaved" />
    <RouteFormDialog v-if="data?.routing.routingId" v-model="routeVisible" :routing-id="Number(data.routing.routingId)" @success="sourcesSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { normalizeWorkSpec, workSpecContent, productWorkSpecApi, embossFields, workSpecColors, type ProductWorkSpec, type WorkSpecChangeSource } from '@/api/product/workSpec'
import ProductSpecPreview from './ProductSpecPreview.vue'
import ProductPrintSpecPanel from './ProductPrintSpecPanel.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { processRowsForGroup, workflowOperations, workflowLabor, workflowNames } from './workSpecProcessRows'
import { calculateBomQuantity } from '@/utils/bomQuantity'
import EngineeringDrawingLibrary from '../../drawing/components/EngineeringDrawingLibrary.vue'
import BomFormDialog from '@/views/product/bom/components/BomFormDialog.vue'
import RouteFormDialog from '@/views/product/route/components/RouteFormDialog.vue'
import { documentFileCaption, loadDocset, workSpecMaterialSpec, flowGroups, workSpecStepSubscript, workSpecOperationRemark, renderFile, type DocsetData } from './docset'

const props = defineProps<{ productId: number; productCode: string; productName: string }>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const user = useUserStore()
const canEdit = computed(() => user.hasPermission('product:edit'))
const canConfirm = computed(() => user.hasPermission('product:status:approve'))
const canEditBom = computed(() => user.hasPermission('engineering:bom:edit'))
const canEditRoute = computed(() => user.hasPermission('engineering:routing:edit'))
const previewVisible = ref(false)
const loading = ref(false)
const loadError = ref('')
const printSpecBusy = ref(false)
let loadGeneration = 0
const data = shallowRef<DocsetData>()
const workflowTab = ref(flowGroups[0]?.value || '')
const workflowGroups = computed(() => flowGroups.map(group => ({
  ...group, rows: processRowsForGroup(data.value?.routing.items || [], 'ASSEMBLY', group.value),
})))
const editorVisible = ref(false)
const editorTab = ref('requirements')
const drawingBusy = ref(false)
const bomVisible = ref(false)
const routeVisible = ref(false)
const saving = ref(false)
const confirming = ref(false)
const draft = ref<ProductWorkSpec>(normalizeWorkSpec())
const baseline = ref('')
const changeSourcesVisible = ref(false)
const changeSourcesLoading = ref(false)
const changeSources = ref<WorkSpecChangeSource[]>([])
const selectedSourceIds = ref<number[]>([])
const nextSourceBefore = ref<number>()
const usedSourceIds = computed(() => new Set(draft.value.changes.flatMap(change => change.sourceLogIds)))
const dirty = computed(() => JSON.stringify(workSpecContent(draft.value)) !== baseline.value)
const structureOptions = computed(() => data.value?.files.filter(file => file.productFile && file.category === '结构图' && file.kind !== 'other') || [])
const structureFile = computed(() => data.value?.workSpec.structureFileId
  ? data.value.files.find(file => file.id === data.value?.workSpec.structureFileId && file.category === '结构图' && file.kind !== 'other')
  : undefined)
const structureImage = ref('')
const structureCaption = computed(() => structureFile.value ? documentFileCaption({ ...structureFile.value, name: '' }) : '')
async function loadStructureImage() {
  if (structureImage.value) { URL.revokeObjectURL(structureImage.value); structureImage.value = '' }
  const file = structureFile.value
  if (!file) return
  try { structureImage.value = (await renderFile(file))[0]?.url || '' }
  catch (e) { console.error('结构图加载失败', e) }
}
watch(structureFile, () => { void loadStructureImage() })
const editing = computed(() => editorVisible.value || bomVisible.value || routeVisible.value || printSpecBusy.value)
async function loadData() {
  const generation = ++loadGeneration
  loading.value = true
  loadError.value = ''
  try {
    const result = await loadDocset(props.productId)
    if (generation === loadGeneration) data.value = result
  } catch (error) {
    if (generation === loadGeneration) loadError.value = error instanceof Error ? error.message : '作业规范加载失败，请重试'
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}
function edit(tab: string) {
  if (!data.value) return
  draft.value = normalizeWorkSpec(JSON.parse(JSON.stringify(data.value.workSpec)))
  baseline.value = JSON.stringify(workSpecContent(draft.value))
  editorTab.value = tab
  editorVisible.value = true
}
async function closeEditor(done?: () => void) {
  if (saving.value || drawingBusy.value) { ElMessage.warning('请先完成保存或图纸维护'); return }
  if (dirty.value) {
    try { await ElMessageBox.confirm('工程规范有未保存修改，是否放弃？', '未保存修改', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' }) }
    catch { return }
  }
  if (done) done()
  else editorVisible.value = false
}
async function save() {
  if (saving.value || drawingBusy.value) return
  if (draft.value.changes.some(change => change.text.length > 1000)) { ElMessage.warning('请将变更说明整理到1000字以内，原始来源内容会保留'); return }
  for (const field of embossFields) {
    const value = (draft.value.emboss[field.key] || '').trim()
    if (value && !/^[+-]?\d{1,12}(\.\d{1,6})?$/.test(value)) { ElMessage.warning(field.label + '请填写数值，或留空'); return }
    draft.value.emboss[field.key] = value
  }
  if (draft.value.structureFileId && !structureOptions.value.some(file => file.id === draft.value.structureFileId)) { ElMessage.warning('结构图已不可用，请重新选择'); return }
  saving.value = true
  try {
    const response: any = await productWorkSpecApi.save(props.productId, draft.value)
    if (data.value) data.value = { ...data.value, workSpec: normalizeWorkSpec(response.data) }
    baseline.value = JSON.stringify(workSpecContent(draft.value))
    editorVisible.value = false
    ElMessage.success('工程规范已保存')
    await sourcesSaved()
  } catch (e) { console.error('保存工程规范失败', e) }
  finally { saving.value = false }
}
function addChange() {
  const now = new Date()
  const date = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0')
  draft.value.changes.push({ date, text: '', color: '#ed00df', print: false, reason: '', sourceLogIds: [], sources: [] })
}
async function openChangeSources() {
  changeSourcesVisible.value = true
  if (changeSourcesLoading.value) return
  changeSources.value = []
  selectedSourceIds.value = []
  nextSourceBefore.value = undefined
  await loadChangeSources()
}
async function loadChangeSources(before?: number) {
  if (changeSourcesLoading.value) return
  changeSourcesLoading.value = true
  try {
    const response = await productWorkSpecApi.changeSources(props.productId, before)
    changeSources.value.push(...(response.data?.items || []))
    nextSourceBefore.value = response.data?.nextBefore || undefined
  } catch (e) { console.error('读取工程变更来源失败', e); ElMessage.error('工程变更读取失败，请重试') }
  finally { changeSourcesLoading.value = false }
}
function importChangeSources() {
  if (draft.value.changes.length >= 100) return
  const sources = changeSources.value.filter(source => selectedSourceIds.value.includes(source.id) && !usedSourceIds.value.has(source.id))
  if (!sources.length) return
  if (sources.length > 100) { ElMessage.warning('一条变更最多引用100条来源，请分次整理'); return }
  addChange()
  const change = draft.value.changes[draft.value.changes.length - 1]!
  change.sourceLogIds = sources.map(source => source.id)
  change.sources = sources
  change.text = sources.map(source => source.text).join('；')
  changeSourcesVisible.value = false
  if (change.text.length > 1000) ElMessage.info('原始变更较多，请整理打印说明至1000字以内；来源原文会保留')
}
async function confirmSpec() {
  if (!data.value || confirming.value) return
  try {
    await ElMessageBox.confirm('确认当前材料、流程、结构图及工程要求已批准？', '工程确认', { type: 'warning', confirmButtonText: '确认已批准' })
    confirming.value = true
    await productWorkSpecApi.confirm(props.productId, data.value.workSpec.revision, data.value.workSpec.sourceRevision)
    ElMessage.success('已记录工程确认')
    await sourcesSaved()
  } catch (e) { if (e !== 'cancel' && e !== 'close') console.error('工程确认失败', e) }
  finally { confirming.value = false }
}
async function printRemarksSaved(workSpec: ProductWorkSpec) {
  if (data.value) data.value = { ...data.value, workSpec }
  await sourcesSaved()
}
async function sourcesSaved() { await loadData(); emit('updated') }
async function refreshDrawings() { await loadData() }
watch(() => editing.value || confirming.value, value => emit('busy', value))
watch(() => props.productId, () => { data.value = undefined; previewVisible.value = false; void loadData() }, { immediate: true })
onBeforeUnmount(() => { loadGeneration++; if (structureImage.value) URL.revokeObjectURL(structureImage.value); emit('busy', false) })
</script>

<style scoped>
.work-actions { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:12px; }
.source-actions { display:flex; gap:8px; flex-wrap:wrap; }
.work-actions .el-button + .el-button { margin-left:0; }
.data-sections { min-height:120px; }
.data-section { margin-bottom:20px; padding:16px; border:1px solid var(--el-border-color-light); border-radius:6px; }
.section-heading { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-bottom:12px; }
.section-heading h3 { margin:0; font-size:15px; }
.multiline { white-space:pre-wrap; overflow-wrap:anywhere; margin:0; line-height:1.7; }
.spec-top { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:16px; align-items:stretch; margin-bottom:20px; }
.spec-top-col { display:flex; flex-direction:column; min-width:0; padding:16px; border:1px solid var(--el-border-color-light); border-radius:6px; }
.spec-top-col .section-heading { margin-bottom:12px; }
.structure-preview { flex:1; min-height:200px; display:flex; align-items:center; justify-content:center; padding:8px; border:1px dashed var(--el-border-color); border-radius:6px; }
.structure-preview img { max-width:100%; max-height:320px; object-fit:contain; }
.empty-hint { color:var(--el-text-color-secondary); font-size:13px; }
.structure-caption { margin:8px 0 0; font-size:12px; color:#697586; text-align:center; }
.workflow-operation { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.workflow-symbols { display:flex; align-items:center; flex-wrap:wrap; gap:6px; }
.workflow-symbol { display:inline-flex; align-items:flex-end; }
.workflow-symbol sub { margin-left:2px; white-space:pre-wrap; }
.workflow-remark { white-space:pre-wrap; overflow-wrap:anywhere; }
.data-message { margin-bottom:12px; }
.editor-product { margin-bottom:12px; color:#697586; }
.emboss-fields { display:grid; grid-template-columns:1fr 1fr; gap:0 20px; }
.change-heading { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.change-row { display:grid; grid-template-columns:150px 1fr 90px 65px 45px; gap:10px; margin-bottom:10px; align-items:start; }
.change-row :deep(.el-date-editor) { width:150px; }
.change-card { border:1px solid #ebeef5; border-radius:6px; padding:12px; margin-bottom:12px; }
.change-tip,.change-source { font-size:12px; color:#697586; line-height:1.6; }
.change-source { margin-top:8px; }
.source-options { display:block; max-height:420px; overflow:auto; }
.source-option { padding:8px 0; border-bottom:1px solid #ebeef5; }
.source-option p,.source-details p { white-space:pre-wrap; overflow-wrap:anywhere; font-size:12px; margin:6px 0; }
.source-details { margin-top:6px; font-size:12px; color:#697586; }
.issue-form { margin-top:24px; border-top:1px solid #ebeef5; padding-top:20px; }
</style>
