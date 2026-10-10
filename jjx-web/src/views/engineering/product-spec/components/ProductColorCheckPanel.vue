<template>
  <div>
    <div class="color-actions">
      <span>按参考样张分四块录入工程自查结论（正确／错误／不适用；未检查留空）；暂不强制，不影响产品、BOM、工艺路线与发货等流程。</span>
      <div v-if="canEdit">
        <el-button :disabled="!data" @click="editCheck">填写检查表</el-button>
      </div>
    </div>
    <ProductSpecPreview ref="preview" :product-id="productId" paper-only :initial-sections="['color']" @loaded="data = $event" />

    <el-dialog v-model="editorVisible" title="规范分色检查表 · 工程自查" width="960px" top="5vh" append-to-body destroy-on-close :close-on-click-modal="false" :before-close="closeEditor">
      <div class="color-editor">
        <section v-for="group in COLOR_CHECK_GROUPS" :key="group.key" class="editor-block">
          <div class="editor-block-title">{{ group.label }}</div>
          <div v-for="item in group.items" :key="item.key" class="editor-row">
            <div class="editor-line">
              <span class="editor-item-label">{{ item.label }}</span>
              <el-radio-group v-model="draft[item.key].result" :disabled="saving">
                <el-radio value="">未检查</el-radio>
                <el-radio v-for="option in ColorCheckResultEnum.items" :key="option.value" :value="option.value">{{ option.label }}</el-radio>
              </el-radio-group>
            </div>
            <el-input v-if="draft[item.key].result === ColorCheckResultEnum.INCORRECT.value" v-model="draft[item.key].reason" type="textarea" :rows="2" maxlength="1000" show-word-limit :disabled="saving" placeholder="请填写错误原因（必填）" class="editor-reason" />
          </div>
        </section>
      </div>
      <template #footer>
        <el-button :disabled="saving" @click="closeEditor()">关闭</el-button>
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { normalizeWorkSpec, productWorkSpecApi, type ColorCheckItemValue } from '@/api/product/workSpec'
import ProductSpecPreview from './ProductSpecPreview.vue'
import { COLOR_CHECK_GROUPS, ColorCheckResultEnum, colorCheckItemKeys } from '@/enums/product/ColorCheckEnum'
import type { DocsetData } from './docset'

interface ColorCheckDraft { result: string; reason: string }

const props = defineProps<{ productId: number }>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const user = useUserStore()
const canEdit = computed(() => user.hasPermission('product:edit'))
const preview = ref<InstanceType<typeof ProductSpecPreview>>()
const data = shallowRef<DocsetData>()
const editorVisible = ref(false)
const saving = ref(false)
const revision = ref('')
const draft = ref<Record<string, ColorCheckDraft>>(emptyDraft())
const baseline = ref('')
const dirty = computed(() => JSON.stringify(draft.value) !== baseline.value)
let disposed = false

function emptyDraft(): Record<string, ColorCheckDraft> {
  return Object.fromEntries(colorCheckItemKeys.map(key => [key, { result: '', reason: '' }]))
}
function editCheck() {
  if (!data.value) return
  const items = data.value.workSpec.colorCheck?.items || {}
  draft.value = Object.fromEntries(colorCheckItemKeys.map(key => {
    const item = items[key]
    return [key, { result: item?.result || '', reason: item?.reason || '' }]
  }))
  revision.value = data.value.workSpec.revision
  baseline.value = JSON.stringify(draft.value)
  editorVisible.value = true
}
async function allowLeave() {
  if (saving.value) { ElMessage.warning('检查表正在保存，请稍候'); return false }
  if (!editorVisible.value || !dirty.value) return true
  try {
    await ElMessageBox.confirm('分色检查表有未保存修改，是否放弃？', '未保存修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' })
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
  const missing = COLOR_CHECK_GROUPS.flatMap(group => group.items)
    .find(item => draft.value[item.key].result === ColorCheckResultEnum.INCORRECT.value && !draft.value[item.key].reason.trim())
  if (missing) { ElMessage.warning(`「${missing.label}」选择错误，请填写原因`); return }
  const items: Record<string, ColorCheckItemValue> = Object.fromEntries(
    colorCheckItemKeys.map(key => [key, { result: draft.value[key].result, reason: draft.value[key].reason }]))
  saving.value = true
  try {
    const response = await productWorkSpecApi.saveColorCheck(props.productId, revision.value, items)
    if (disposed) return
    if (data.value) data.value = { ...data.value, workSpec: normalizeWorkSpec(response.data) }
    baseline.value = JSON.stringify(draft.value)
    editorVisible.value = false
    ElMessage.success('分色检查表已保存')
    await preview.value?.reload()
    emit('updated')
  } catch (error) { console.error('保存分色检查表失败', error) }
  finally { saving.value = false }
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
.color-actions { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-bottom:12px; flex-wrap:wrap; }
.color-actions > span { font-size:13px; color:#697586; }
.color-editor { max-height:64vh; overflow:auto; }
.editor-block { margin-bottom:16px; }
.editor-block-title { font-weight:600; font-size:13px; color:#35445a; padding:6px 8px; background:#f4f7fc; border-radius:4px; margin-bottom:6px; }
.editor-row { padding:6px 8px; border-bottom:1px dashed #edf0f5; }
.editor-line { display:flex; align-items:center; gap:12px; }
.editor-item-label { width:190px; flex-shrink:0; font-size:13px; color:#485568; }
.editor-reason { margin-top:6px; margin-left:202px; }
</style>
