<template>
  <div class="work-spec-panel">
    <div class="work-actions">
      <div class="engineering-actions">
        <el-button v-if="canEdit" :disabled="!data" @click="edit('requirements')">工程要求</el-button>
        <el-button v-if="canEdit" :disabled="!data" @click="edit('die')">刀模位置</el-button>
        <el-button v-if="canEdit" :disabled="!data" @click="edit('structure')">结构图</el-button>
        <el-button v-if="canEdit" :disabled="!data" @click="edit('changes')">变更与发行</el-button>
      </div>
      <div class="source-actions">
        <el-button v-if="canEditBom" :disabled="!data?.bom.bomId" @click="bomVisible = true">修改 BOM</el-button>
        <el-button v-if="canEditRoute" :disabled="!data?.routing.routingId" @click="routeVisible = true">修改工艺路线</el-button>
        <el-button v-if="canConfirm" type="success" :disabled="!data || data.workSpec.approved" :loading="confirming" @click="confirmSpec">确认已批准</el-button>
      </div>
    </div>
    <ProductSpecPreview ref="preview" :product-id="productId" paper-only :initial-sections="['spec']" @loaded="onLoaded" />

    <el-dialog v-model="editorVisible" title="工程维护 · 产品作业规范" width="1060px" append-to-body destroy-on-close :close-on-click-modal="false" :before-close="closeEditor">
      <div class="editor-product">{{ productName }}　{{ productCode }}</div>
      <el-tabs v-model="editorTab">
        <el-tab-pane label="工程要求" name="requirements">
          <el-form label-width="130px">
            <div class="emboss-fields"><el-form-item v-for="field in embossFields" :key="field.key" :label="field.label"><el-input v-model="draft.emboss[field.key]" placeholder="选填" maxlength="20"><template #append>{{ field.unit }}</template></el-input></el-form-item></div>
            <el-form-item label="加工要求"><el-input v-model="draft.engineeringRequirements" type="textarea" :rows="7" maxlength="2000" show-word-limit placeholder="选填；每项要求可单独换行" /></el-form-item>
            <el-form-item label="文字颜色"><el-select v-model="draft.requirementsColor"><el-option v-for="color in workSpecColors" :key="color.value" :label="color.label" :value="color.value" /></el-select></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="刀模位置" name="die">
          <el-form label-width="100px"><el-form-item label="刀模位置"><el-input v-model="draft.dieLocation" type="textarea" :rows="8" maxlength="500" show-word-limit placeholder="工程独立填写位置，选填" /></el-form-item></el-form>
        </el-tab-pane>
        <el-tab-pane label="结构图" name="structure">
          <el-form label-width="100px"><el-form-item label="规范结构图"><el-select v-model="draft.structureFileId" clearable filterable placeholder="选填；选择这张规范单使用的图纸" style="width:100%"><el-option v-for="file in structureOptions" :key="file.id" :label="documentFileCaption(file)" :value="file.id" /></el-select></el-form-item></el-form>
          <EngineeringDrawingLibrary :product-code="productCode" :product-name="productName" :categories="['结构图']" :active="editorTab === 'structure'" @busy="drawingBusy = $event" @loaded="refreshDrawings" />
        </el-tab-pane>
        <el-tab-pane label="变更与发行" name="changes">
          <div class="change-heading"><strong>变更内容</strong><el-button :disabled="draft.changes.length >= 100" @click="addChange">新增记录</el-button></div>
          <div v-for="(change, i) in draft.changes" :key="i" class="change-row">
            <el-date-picker v-model="change.date" type="date" value-format="YYYY-MM-DD" placeholder="变更日期（选填）" />
            <el-input v-model="change.text" type="textarea" :rows="2" maxlength="1000" placeholder="变更说明（选填）" />
            <el-select v-model="change.color"><el-option v-for="color in workSpecColors" :key="color.value" :label="color.label" :value="color.value" /></el-select>
            <el-button link type="danger" @click="draft.changes.splice(i, 1)">删除</el-button>
          </div>
          <el-form label-width="100px" class="issue-form">
            <el-form-item label="发行单位"><el-input v-model="draft.issueUnit" maxlength="100" placeholder="选填" /></el-form-item>
            <el-form-item label="发行日期"><el-date-picker v-model="draft.issueDate" type="date" value-format="YYYY-MM-DD" placeholder="选填" /></el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer><el-button :disabled="saving || drawingBusy" @click="closeEditor()">关闭</el-button><el-button type="primary" :loading="saving" :disabled="drawingBusy || !dirty" @click="save">保存</el-button></template>
    </el-dialog>
    <BomFormDialog v-if="data?.bom.bomId" v-model="bomVisible" :bom-id="Number(data.bom.bomId)" @success="sourcesSaved" />
    <RouteFormDialog v-if="data?.routing.routingId" v-model="routeVisible" :routing-id="Number(data.routing.routingId)" @success="sourcesSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { normalizeWorkSpec, workSpecContent, productWorkSpecApi, embossFields, workSpecColors, type ProductWorkSpec } from '@/api/product/workSpec'
import ProductSpecPreview from './ProductSpecPreview.vue'
import EngineeringDrawingLibrary from '../../drawing/components/EngineeringDrawingLibrary.vue'
import BomFormDialog from '@/views/product/bom/components/BomFormDialog.vue'
import RouteFormDialog from '@/views/product/route/components/RouteFormDialog.vue'
import { documentFileCaption, type DocsetData } from './docset'

const props = defineProps<{ productId: number; productCode: string; productName: string }>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const user = useUserStore()
const canEdit = computed(() => user.hasPermission('product:edit'))
const canConfirm = computed(() => user.hasPermission('product:status:approve'))
const canEditBom = computed(() => user.hasPermission('engineering:bom:edit'))
const canEditRoute = computed(() => user.hasPermission('engineering:routing:edit'))
const preview = ref<InstanceType<typeof ProductSpecPreview>>()
const data = shallowRef<DocsetData>()
const editorVisible = ref(false)
const editorTab = ref('requirements')
const drawingBusy = ref(false)
const bomVisible = ref(false)
const routeVisible = ref(false)
const saving = ref(false)
const confirming = ref(false)
const draft = ref<ProductWorkSpec>(normalizeWorkSpec())
const baseline = ref('')
const dirty = computed(() => JSON.stringify(workSpecContent(draft.value)) !== baseline.value)
const structureOptions = computed(() => data.value?.files.filter(file => file.productFile && file.category === '结构图' && file.kind !== 'other') || [])
function onLoaded(value: DocsetData) { data.value = value }
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
  draft.value.changes.push({ date, text: '', color: '#ed00df' })
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
async function sourcesSaved() { await preview.value?.reload(); emit('updated') }
async function refreshDrawings() { await preview.value?.reload() }
watch(() => editorVisible.value || bomVisible.value || routeVisible.value || confirming.value, value => emit('busy', value))
onBeforeUnmount(() => emit('busy', false))
</script>

<style scoped>
.work-actions { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:12px; }
.engineering-actions,.source-actions { display:flex; gap:8px; flex-wrap:wrap; }
.work-actions .el-button + .el-button { margin-left:0; }
.editor-product { margin-bottom:12px; color:#697586; }
.emboss-fields { display:grid; grid-template-columns:1fr 1fr; gap:0 20px; }
.change-heading { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.change-row { display:grid; grid-template-columns:170px 1fr 90px 45px; gap:10px; margin-bottom:12px; align-items:start; }
.change-row :deep(.el-date-editor) { width:170px; }
.issue-form { margin-top:24px; border-top:1px solid #ebeef5; padding-top:20px; }
</style>
