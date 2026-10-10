<template>
  <div>
    <div class="print-actions">
      <span>印刷内容引用工艺路线；指导图统一在工程图集维护。</span>
      <div v-if="canEdit">
        <el-button :disabled="!data" @click="editRemarks">编辑整组备注</el-button>
        <el-button @click="openHistory">备注修改记录</el-button>
      </div>
    </div>
    <ProductSpecPreview ref="preview" :product-id="productId" paper-only :initial-sections="['print']" @loaded="data = $event" />

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
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { normalizeWorkSpec, productWorkSpecApi, type PrintRemarkHistory } from '@/api/product/workSpec'
import ProductSpecPreview from './ProductSpecPreview.vue'
import { flowGroups, type DocsetData } from './docset'

const props = defineProps<{ productId: number }>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const user = useUserStore()
const canEdit = computed(() => user.hasPermission('product:edit'))
const preview = ref<InstanceType<typeof ProductSpecPreview>>()
const data = shallowRef<DocsetData>()
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

function editRemarks() {
  if (!data.value) return
  draft.value = Object.fromEntries(flowGroups.map(group => [group.value, data.value!.workSpec.printRemarks[group.value] || '']))
  revision.value = data.value.workSpec.revision
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
  if (saving.value || !dirty.value) return
  saving.value = true
  try {
    const response = await productWorkSpecApi.savePrintRemarks(props.productId, revision.value, draft.value)
    if (disposed) return
    if (data.value) data.value = { ...data.value, workSpec: normalizeWorkSpec(response.data) }
    baseline.value = JSON.stringify(draft.value)
    editorVisible.value = false
    ElMessage.success('整组备注已保存，修改记录已保留')
    await preview.value?.reload()
    emit('updated')
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
  if (historyLoading.value) return
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
watch(() => editorVisible.value || saving.value, value => emit('busy', value))
onBeforeRouteLeave(allowLeave)
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { disposed = true; window.removeEventListener('beforeunload', beforeUnload); emit('busy', false) })
</script>

<style scoped>
.print-actions { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-bottom:12px; flex-wrap:wrap; }
.print-actions > span { font-size:13px; color:#697586; }
.remark-history { max-height:60vh; overflow:auto; min-height:120px; }
.history-item { padding:12px 0; border-bottom:1px solid #ebeef5; }
.history-heading { color:#697586; margin-bottom:10px; }
.history-field { margin-top:12px; }
.history-values { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:6px; }
.history-values > div { background:#f5f7fa; padding:10px; border-radius:4px; min-width:0; }
.history-values small { color:#909399; }
.history-values p { white-space:pre-wrap; overflow-wrap:anywhere; margin:6px 0 0; }
</style>
