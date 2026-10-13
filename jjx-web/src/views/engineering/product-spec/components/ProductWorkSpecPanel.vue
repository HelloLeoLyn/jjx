<template>
  <div class="work-spec-panel">
    <div class="work-actions">
      <strong>作业规范</strong>
      <div class="source-actions">
        <el-button :disabled="!data || loading || editing" @click="previewVisible = true"
          >打印预览</el-button
        >
        <el-button
          v-if="canConfirm"
          type="success"
          :disabled="!data || loading || editing || data.workSpec.approved"
          :loading="confirming"
          @click="confirmSpec"
          >确认已批准</el-button
        >
      </div>
    </div>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      class="data-message"
    >
      <el-button link type="primary" @click="loadData">重新加载</el-button>
    </el-alert>
    <el-alert
      v-if="data?.warnings.length"
      :title="data.warnings.join('；')"
      type="warning"
      :closable="false"
      class="data-message"
    />
    <div v-loading="loading" class="data-sections">
      <template v-if="data">
        <section class="spec-top">
          <div class="spec-top-col">
            <div class="section-heading">
              <h3>结构图</h3>
              <div v-if="structureDraft === undefined">
                <el-button v-if="canEdit" :disabled="loading" @click="startStructureEdit">编辑</el-button>
              </div>
              <div v-else>
                <el-button :disabled="savingInline" @click="structureDraft = null">取消</el-button>
                <el-button type="primary" :loading="savingInline" @click="saveStructure">保存</el-button>
              </div>
            </div>
            <el-select
              v-if="structureDraft !== undefined"
              v-model="structureDraft"
              clearable
              filterable
              placeholder="选择这张规范单使用的图纸"
              style="width: 100%"
            >
              <el-option
                v-for="file in structureOptions"
                :key="file.id"
                :label="documentFileCaption(file)"
                :value="file.id"
              />
            </el-select>
            <template v-else>
              <div class="structure-preview">
                <img
                  v-if="structureImage"
                  :src="structureImage"
                  :alt="`${productName || productCode} 结构图`"
                />
                <span v-else class="empty-hint">未选择结构图</span>
              </div>
              <p v-if="structureCaption" class="structure-caption">{{ structureCaption }}</p>
            </template>
          </div>
          <div class="spec-top-col">
            <div class="section-heading">
              <h3>凹凸条件</h3>
            </div>
            <el-descriptions :column="1" border>
              <el-descriptions-item
                v-for="field in embossFields"
                :key="field.key"
                :label="field.label"
              >
                <el-input
                  v-if="canEdit"
                  size="small"
                  v-model="embossDraft[field.key]"
                  @change="saveEmboss"
                />
                <span v-else>{{ embossDraft[field.key] || '-' }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="spec-top-col">
            <div class="section-heading">
              <h3>备注</h3>
              <div v-if="noteDraft === null">
                <el-button v-if="canEdit" :disabled="loading" @click="startNoteEdit">编辑</el-button>
              </div>
              <div v-else>
                <el-button :disabled="savingInline" @click="noteDraft = null">取消</el-button>
                <el-button type="primary" :loading="savingInline" @click="saveNote">保存</el-button>
              </div>
            </div>
            <el-input
              v-if="noteDraft !== null"
              v-model="noteDraft"
              type="textarea"
              :rows="6"
              maxlength="2000"
              show-word-limit
              placeholder="选填；整份规范的备注，每项可单独换行"
            />
            <p v-else class="multiline">{{ data.workSpec.engineeringRequirements || '未填写' }}</p>
          </div>
        </section>
        <section class="data-section">
          <div class="section-heading">
            <h3>材料</h3>
            <el-button
              v-if="canEditBom"
              :disabled="loading || !data.bom.bomId"
              @click="bomVisible = true"
              >编辑</el-button
            >
          </div>
          <el-table :data="materialRows" border size="small" empty-text="暂无 BOM 材料">
            <el-table-column label="序号" width="80"
              ><template #default="{ row }"
                ><span :class="{ 'pending-row': row.pending }">{{
                  row.pending ? '待清理' : row.index
                }}</span></template
              ></el-table-column
            >
            <el-table-column label="项目" min-width="120"
              ><template #default="{ row }">{{
                row.pending ? '-' : plain(row.item?.processName) || '-'
              }}</template></el-table-column
            >
            <el-table-column label="材料" min-width="160"
              ><template #default="{ row }"
                ><span
                  :title="row.pending ? row.key : row.item?.materialName || row.item?.materialCode"
                  >{{
                    row.pending ? row.key : row.item?.materialName || row.item?.materialCode || '-'
                  }}</span
                ></template
              ></el-table-column
            >
            <el-table-column label="规格及模数" min-width="160"
              ><template #default="{ row }">{{
                row.pending ? '-' : workSpecMaterialSpec(row.item) || '-'
              }}</template></el-table-column
            >
            <el-table-column label="刀模位置" min-width="200"
              ><template #default="{ row }"
                ><el-input
                  v-model="dieDraft[row.key]"
                  size="small"
                  maxlength="100"
                  :placeholder="row.pending ? '待清理' : '未填写'"
                  :disabled="!canEdit || dieSaving"
                  @change="saveDieLocation(row.key, $event)" /></template
            ></el-table-column>
          </el-table>
          <el-alert
            v-if="hasLegacyDie"
            type="info"
            :closable="false"
            class="die-alert"
            title="检测到旧版刀模位置文本（未按 BOM 行），可迁移到 BOM 行"
          >
            <el-button link type="primary" :disabled="!canEdit" @click="migrateLegacyDie"
              >迁移旧文本</el-button
            >
          </el-alert>
        </section>
        <WorkSpecWorkflow
          :items="data.routing.items || []"
          :routing-id="Number(data.routing.routingId) || undefined"
          :editable="canEditRoute"
          :busy="loading"
          @edit="routeVisible = true"
        />
        <ProductPrintSpecPanel
          :product-id="productId"
          :work-spec="data.workSpec"
          :routing-items="data.routing.items || []"
          :loading="loading"
          @busy="printSpecBusy = $event"
          @updated="printRemarksSaved"
        />
        <section class="data-section">
          <div class="section-heading">
            <h3>变更内容 · 发行单位</h3>
            <div v-if="!issuanceDraft">
              <el-button v-if="canEdit" :disabled="loading" @click="startIssuanceEdit">编辑</el-button>
            </div>
            <div v-else>
              <el-button :disabled="savingIssuance" @click="cancelIssuance">取消</el-button>
              <el-button type="primary" :loading="savingIssuance" @click="saveIssuance">保存</el-button>
            </div>
          </div>
          <div class="issuance">
            <div class="issuance-col">
              <div class="issuance-head">
                <strong>变更内容</strong>
                <el-button
                  v-if="issuanceDraft"
                  link
                  type="primary"
                  :disabled="issuanceChanges.length >= 100"
                  @click="addIssuanceChange"
                  >添加</el-button
                >
              </div>
              <el-table
                :data="issuanceDraft ? issuanceChanges : data.workSpec.changes"
                border
                size="small"
                empty-text="暂无变更内容"
              >
                <el-table-column label="变更日期" width="140">
                  <template #default="{ row }">
                    <el-date-picker
                      v-if="issuanceDraft"
                      v-model="row.date"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="选填"
                      style="width: 120px"
                    />
                    <span v-else>{{ row.date || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="说明" min-width="240">
                  <template #default="{ row }">
                    <el-input
                      v-if="issuanceDraft"
                      v-model="row.text"
                      type="textarea"
                      :rows="2"
                      maxlength="1000"
                      show-word-limit
                      placeholder="供生产阅读的变更说明"
                    />
                    <span v-else class="multiline">{{ row.text || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="打印" width="70" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-if="issuanceDraft" v-model="row.print" />
                    <span v-else>{{ row.print ? '是' : '否' }}</span>
                  </template>
                </el-table-column>
                <el-table-column v-if="issuanceDraft" label="操作" width="70" align="center">
                  <template #default="{ $index }">
                    <el-button link type="danger" @click="removeIssuanceChange($index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div class="issuance-col">
              <div class="issuance-head"><strong>发行</strong></div>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="发行单位">{{ data?.workSpec.issueUnit || '工程部' }}</el-descriptions-item>
                <el-descriptions-item label="发布日期">{{ publishedAt || '—' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </section>
      </template>
    </div>
    <el-dialog
      v-model="previewVisible"
      title="作业规范 · 打印预览"
      width="92%"
      append-to-body
      destroy-on-close
    >
      <ProductSpecPreview
        v-if="previewVisible"
        :product-id="productId"
        :initial-sections="['spec', 'print']"
      />
    </el-dialog>

    <BomFormDialog
      v-if="data?.bom.bomId"
      v-model="bomVisible"
      :bom-id="Number(data.bom.bomId)"
      @success="sourcesSaved"
    />
    <RouteFormDialog
      v-if="data?.routing.routingId"
      v-model="routeVisible"
      :routing-id="Number(data.routing.routingId)"
      @success="sourcesSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import {
  normalizeWorkSpec,
  workSpecContent,
  productWorkSpecApi,
  embossFields,
  workSpecColors,
  type ProductWorkSpec,
} from '@/api/product/workSpec'
import ProductSpecPreview from './ProductSpecPreview.vue'
import ProductPrintSpecPanel from './ProductPrintSpecPanel.vue'
import WorkSpecWorkflow from './WorkSpecWorkflow.vue'
import EngineeringDrawingLibrary from '../../drawing/components/EngineeringDrawingLibrary.vue'
import BomFormDialog from '@/views/product/bom/components/BomFormDialog.vue'
import RouteFormDialog from '@/views/product/route/components/RouteFormDialog.vue'
import {
  documentFileCaption,
  loadDocset,
  workSpecMaterialSpec,
  plain,
  dieLocationKey,
  dieLocationLabel,
  flattenedBom,
  renderFile,
  type DocsetData,
} from './docset'

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
const editorVisible = ref(false)
const embossDraft = reactive<Record<string, string>>({})
const noteDraft = ref<string | null>(null)
const structureDraft = ref<number | null | undefined>(undefined)
const savingInline = ref(false)
const publishedAt = ref('')
const editorTab = ref('requirements')
const drawingBusy = ref(false)
const bomVisible = ref(false)
const routeVisible = ref(false)
const saving = ref(false)
const confirming = ref(false)
const draft = ref<ProductWorkSpec>(normalizeWorkSpec())
const baseline = ref('')
const savingIssuance = ref(false)
const issuanceDraft = ref<ProductWorkSpec | null>(null)
const issuanceChanges = computed(() => issuanceDraft.value?.changes || [])
const dirty = computed(() => JSON.stringify(workSpecContent(draft.value)) !== baseline.value)
const structureOptions = computed(
  () =>
    data.value?.files.filter(
      (file) => file.productFile && file.category === '结构图' && file.kind !== 'other'
    ) || []
)
const structureFile = computed(() =>
  data.value?.workSpec.structureFileId
    ? data.value.files.find(
        (file) =>
          file.id === data.value?.workSpec.structureFileId &&
          file.category === '结构图' &&
          file.kind !== 'other'
      )
    : undefined
)
const structureImage = ref('')
const structureCaption = computed(() =>
  structureFile.value ? documentFileCaption({ ...structureFile.value, name: '' }) : ''
)
async function loadStructureImage() {
  if (structureImage.value) {
    URL.revokeObjectURL(structureImage.value)
    structureImage.value = ''
  }
  const file = structureFile.value
  if (!file) return
  try {
    structureImage.value = (await renderFile(file))[0]?.url || ''
  } catch (e) {
    console.error('结构图加载失败', e)
  }
}
watch(structureFile, () => {
  void loadStructureImage()
})
const bomRows = computed(() => flattenedBom(data.value?.bom.items))
const dieRows = computed(() =>
  bomRows.value.map((row) => ({ key: dieLocationKey(row), label: dieLocationLabel(row) }))
)
const dieCurrentKeys = computed(() => new Set(bomRows.value.map((row) => dieLocationKey(row))))
const dieMap = computed<Record<string, string>>(() => data.value?.workSpec.dieLocation || {})
const dieUnmatched = computed(() =>
  Object.entries(dieMap.value)
    .filter(([key, value]) => !dieCurrentKeys.value.has(key) && String(value || '').trim())
    .map(([key, value]) => ({ key, value }))
)
const materialRows = computed(() => [
  ...bomRows.value.map((item, index) => ({
    key: dieLocationKey(item),
    index: index + 1,
    item,
    pending: false,
  })),
  ...dieUnmatched.value.map(({ key }) => ({
    key,
    index: 0,
    item: null as Record<string, any> | null,
    pending: true,
  })),
])
const dieDraft = ref<Record<string, string>>({})
const dieSaving = ref(false)
const hasLegacyDie = computed(
  () =>
    !!String(data.value?.workSpec.dieLocationText || '').trim() &&
    Object.keys(dieMap.value).length === 0
)
const draftDieUnmatched = computed(() =>
  Object.entries(draft.value.dieLocation || {})
    .filter(([key]) => !dieCurrentKeys.value.has(key))
    .map(([key, value]) => ({ key, value }))
)
function deleteDieKey(key: string) {
  delete draft.value.dieLocation[key]
}
function migrateLegacyDie() {
  if (!data.value || !canEdit.value) return
  const spec = normalizeWorkSpec(JSON.parse(JSON.stringify(data.value.workSpec)))
  const lines = String(spec.dieLocationText || '').split(/\r?\n/)
  const map: Record<string, string> = {}
  bomRows.value.forEach((row, index) => {
    const value = (lines[index] || '').trim()
    if (value) map[dieLocationKey(row)] = value
  })
  draft.value = { ...spec, dieLocation: map, dieLocationText: '' }
  baseline.value = JSON.stringify(workSpecContent(draft.value))
  editorTab.value = 'die'
  editorVisible.value = true
}
async function saveDieLocation(key: string, value: string) {
  const spec = data.value?.workSpec
  if (!spec || dieSaving.value || !canEdit.value) return
  const text = String(value ?? '').trim()
  if (text === String(spec.dieLocation[key] || '').trim()) return
  const next = normalizeWorkSpec(JSON.parse(JSON.stringify(spec)))
  if (text) next.dieLocation = { ...next.dieLocation, [key]: text }
  else {
    const copy = { ...next.dieLocation }
    delete copy[key]
    next.dieLocation = copy
  }
  dieSaving.value = true
  try {
    await productWorkSpecApi.save(props.productId, next)
    ElMessage.success('刀模位置已保存')
    await sourcesSaved()
  } catch (e) {
    console.error('保存刀模位置失败', e)
    ElMessage.error('刀模位置保存失败，请重试')
    dieDraft.value = { ...dieMap.value }
  } finally {
    dieSaving.value = false
  }
}
watch(
  dieMap,
  (value) => {
    dieDraft.value = { ...value }
  },
  { immediate: true }
)
const editing = computed(
  () =>
    editorVisible.value ||
    bomVisible.value ||
    routeVisible.value ||
    printSpecBusy.value ||
    dieSaving.value
)
async function loadData() {
  const generation = ++loadGeneration
  loading.value = true
  loadError.value = ''
  try {
    const result = await loadDocset(props.productId)
    if (generation === loadGeneration) {
      data.value = result
      syncInline()
    }
    void loadPublishedAt()
  } catch (error) {
    if (generation === loadGeneration)
      loadError.value = error instanceof Error ? error.message : '作业规范加载失败，请重试'
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
  if (saving.value || drawingBusy.value) {
    ElMessage.warning('请先完成保存或图纸维护')
    return
  }
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('工程规范有未保存修改，是否放弃？', '未保存修改', {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  if (done) done()
  else editorVisible.value = false
}
async function save() {
  if (saving.value || drawingBusy.value) return
  if (draft.value.changes.some((change) => change.text.length > 1000)) {
    ElMessage.warning('请将变更说明整理到1000字以内，原始来源内容会保留')
    return
  }
  for (const field of embossFields) {
    const value = (draft.value.emboss[field.key] || '').trim()
    if (value && !/^[+-]?\d{1,12}(\.\d{1,6})?$/.test(value)) {
      ElMessage.warning(field.label + '请填写数值，或留空')
      return
    }
    draft.value.emboss[field.key] = value
  }
  if (
    draft.value.structureFileId &&
    !structureOptions.value.some((file) => file.id === draft.value.structureFileId)
  ) {
    ElMessage.warning('结构图已不可用，请重新选择')
    return
  }
  saving.value = true
  try {
    const response: any = await productWorkSpecApi.save(props.productId, draft.value)
    if (data.value) data.value = { ...data.value, workSpec: normalizeWorkSpec(response.data) }
    baseline.value = JSON.stringify(workSpecContent(draft.value))
    editorVisible.value = false
    ElMessage.success('工程规范已保存')
    await sourcesSaved()
  } catch (e) {
    console.error('保存工程规范失败', e)
  } finally {
    saving.value = false
  }
}
function startIssuanceEdit() {
  if (!data.value || !canEdit.value) return
  issuanceDraft.value = normalizeWorkSpec(JSON.parse(JSON.stringify(data.value.workSpec)))
}
function cancelIssuance() {
  issuanceDraft.value = null
}
function addIssuanceChange() {
  const current = issuanceDraft.value
  if (!current || current.changes.length >= 100) return
  const now = new Date()
  const date =
    now.getFullYear() +
    '-' +
    String(now.getMonth() + 1).padStart(2, '0') +
    '-' +
    String(now.getDate()).padStart(2, '0')
  current.changes.push({
    date,
    text: '',
    color: '#ed00df',
    print: true,
    reason: '',
    sourceLogIds: [],
    sources: [],
  })
}
function removeIssuanceChange(index: number) {
  issuanceDraft.value?.changes.splice(index, 1)
}
async function saveIssuance() {
  const current = issuanceDraft.value
  if (!current || savingIssuance.value) return
  if (current.changes.some((change) => change.text.length > 1000)) {
    ElMessage.warning('请将变更说明整理到1000字以内')
    return
  }
  savingIssuance.value = true
  try {
    const response: any = await productWorkSpecApi.save(props.productId, current)
    if (data.value) data.value = { ...data.value, workSpec: normalizeWorkSpec(response.data) }
    issuanceDraft.value = null
    ElMessage.success('变更内容 · 发行单位已保存')
    await sourcesSaved()
  } catch (e) {
    console.error('保存变更与发行失败', e)
  } finally {
    savingIssuance.value = false
  }
}
async function confirmSpec() {
  if (!data.value || confirming.value) return
  try {
    await ElMessageBox.confirm('确认当前材料、流程、结构图及工程要求已批准？', '工程确认', {
      type: 'warning',
      confirmButtonText: '确认已批准',
    })
    confirming.value = true
    await productWorkSpecApi.confirm(
      props.productId,
      data.value.workSpec.revision,
      data.value.workSpec.sourceRevision
    )
    ElMessage.success('已记录工程确认')
    await sourcesSaved()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') console.error('工程确认失败', e)
  } finally {
    confirming.value = false
  }
}
async function printRemarksSaved(workSpec: ProductWorkSpec) {
  if (data.value) data.value = { ...data.value, workSpec }
  await sourcesSaved()
}
function syncInline() {
  const spec = data.value?.workSpec
  Object.keys(embossDraft).forEach((key) => delete embossDraft[key])
  Object.entries(spec?.emboss || {}).forEach(([key, value]) => {
    embossDraft[key] = value
  })
}
function currentSpec(): ProductWorkSpec {
  return normalizeWorkSpec(JSON.parse(JSON.stringify(data.value?.workSpec || {})))
}
async function persistSpec(next: ProductWorkSpec, message: string) {
  if (savingInline.value) return
  savingInline.value = true
  try {
    const response: any = await productWorkSpecApi.save(props.productId, next)
    if (data.value) data.value = { ...data.value, workSpec: normalizeWorkSpec(response.data) }
    ElMessage.success(message)
    await sourcesSaved()
  } catch (e) {
    console.error(message, e)
  } finally {
    savingInline.value = false
  }
}
async function saveEmboss() {
  if (!data.value) return
  await persistSpec(currentSpec(), '凹凸条件已保存')
}
function startNoteEdit() {
  if (!data.value || !canEdit.value) return
  noteDraft.value = data.value.workSpec.engineeringRequirements || ''
}
async function saveNote() {
  if (noteDraft.value === null) return
  const spec = currentSpec()
  spec.engineeringRequirements = noteDraft.value
  await persistSpec(spec, '备注已保存')
  noteDraft.value = null
}
function startStructureEdit() {
  if (!data.value || !canEdit.value) return
  structureDraft.value = data.value.workSpec.structureFileId ?? null
}
async function saveStructure() {
  const spec = currentSpec()
  spec.structureFileId = structureDraft.value ?? null
  await persistSpec(spec, '结构图已保存')
  structureDraft.value = undefined
}
async function loadPublishedAt() {
  try {
    const response: any = await productWorkSpecApi.listVersions(props.productId)
    const list: any[] = response?.data || []
    const latest = list
      .filter((item) => item?.publishedAt)
      .sort((a, b) => String(b.publishedAt).localeCompare(String(a.publishedAt)))[0]
    publishedAt.value = latest?.publishedAt || ''
  } catch {
    publishedAt.value = ''
  }
}
async function sourcesSaved() {
  await loadData()
  emit('updated')
}
async function refreshDrawings() {
  await loadData()
}
watch(
  () => editing.value || confirming.value,
  (value) => emit('busy', value)
)
watch(
  () => props.productId,
  () => {
    data.value = undefined
    previewVisible.value = false
    void loadData()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  loadGeneration++
  if (structureImage.value) URL.revokeObjectURL(structureImage.value)
  emit('busy', false)
})
</script>

<style scoped>
.work-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.source-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.work-actions .el-button + .el-button {
  margin-left: 0;
}
.data-sections {
  min-height: 120px;
}
.data-section {
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.section-heading h3 {
  margin: 0;
  font-size: 15px;
}
.pending-row {
  color: var(--el-color-warning);
  font-weight: 500;
}
.die-alert {
  margin-top: 12px;
}
.multiline {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 0;
  line-height: 1.7;
}
.spec-top {
  display: grid;
  grid-template-columns: 250px auto minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  margin-bottom: 20px;
}
.spec-top-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 321px;
  box-sizing: border-box;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}
.spec-top-col .section-heading {
  margin-bottom: 12px;
}
.structure-preview {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  box-sizing: border-box;
}
.structure-preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
.empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.structure-caption {
  margin: 8px 0 0;
  font-size: 12px;
  color: #697586;
  text-align: center;
}
.data-message {
  margin-bottom: 12px;
}
.editor-product {
  margin-bottom: 12px;
  color: #697586;
}
.emboss-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
}
.change-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.change-row {
  display: grid;
  grid-template-columns: 150px 1fr 90px 65px 45px;
  gap: 10px;
  margin-bottom: 10px;
  align-items: start;
}
.change-row :deep(.el-date-editor) {
  width: 150px;
}
.change-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}
.change-tip,
.change-source {
  font-size: 12px;
  color: #697586;
  line-height: 1.6;
}
.change-source {
  margin-top: 8px;
}
.source-options {
  display: block;
  max-height: 420px;
  overflow: auto;
}
.source-option {
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}
.source-option p,
.source-details p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
  margin: 6px 0;
}
.source-details {
  margin-top: 6px;
  font-size: 12px;
  color: #697586;
}
.issue-form {
  margin-top: 24px;
  border-top: 1px solid #ebeef5;
  padding-top: 20px;
}
.issuance {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}
.issuance-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
@media (max-width: 900px) {
  .issuance {
    grid-template-columns: 1fr;
  }
}
</style>
