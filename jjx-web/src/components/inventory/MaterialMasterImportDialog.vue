<template>
  <el-dialog
    v-model="visible"
    title="物料导入 1A（一个模板 + 品类列）"
    width="700px"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-form label-width="100px">
      <el-form-item label="品类（可选）">
        <el-select
          v-model="categoryId"
          filterable
          clearable
          placeholder="留空＝使用 1A 统一模板（按行【品类】列）"
          style="width: 100%"
          :disabled="busy"
        >
          <el-option
            v-for="category in categories"
            :key="category.categoryId"
            :label="category.pathName"
            :value="category.categoryId"
          />
        </el-select>
        <div class="hint">1A 模板按每行【品类】列校验属性；留空品类且开启自动归类时，未命中的行走未归类。</div>
      </el-form-item>
      <el-form-item label="自动归类">
        <el-switch v-model="autoClassify" :disabled="busy || !!categoryId" />
        <span class="hint" style="margin-left: 8px">按已保存规则试判，仅建议、不写人工确认</span>
      </el-form-item>
      <el-form-item label="录入模板"
        ><el-button :disabled="!categoryId || busy" @click="download"
          >下载该品类模板</el-button
        ></el-form-item
      >
      <el-form-item label="Excel文件"
        ><input type="file" accept=".xlsx,.xls" :disabled="busy" @change="selectFile"
      /></el-form-item>
    </el-form>
    <el-alert
      title="先下载对应品类模板。迁移现有物料时填写物料ID，保留原ID与编码；需要编辑权限。整表预检通过后导入，任一行失败不会部分写入。"
      type="info"
      :closable="false"
    />
    <el-alert
      v-if="message"
      :title="message"
      :type="passed ? 'success' : 'error'"
      :closable="false"
      style="margin-top: 12px"
    />
    <div v-for="error in errors" :key="error" class="import-error">{{ error }}</div>
    <template #footer>
      <el-button :disabled="busy" @click="visible = false">关闭</el-button>
      <el-button :disabled="!file" :loading="busy" @click="run(true)"
        >预检</el-button
      >
      <el-button type="primary" :disabled="!passed" :loading="busy" @click="run(false)"
        >导入物料</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import type { MaterialMasterCategory } from '@/types/inventory/materialMaster'
import { downloadMaterialBlob } from './materialMasterModel'
import { materialClassifyApi } from '@/api/inventory/classify'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success'): void }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const categories = ref<MaterialMasterCategory[]>([])
const categoryId = ref<number>()
const autoClassify = ref(false)
const file = ref<File>()
const busy = ref(false)
const passed = ref(false)
const errors = ref<string[]>([])
const message = ref('')
let session = 0
watch([categoryId, file], () => {
  passed.value = false
  errors.value = []
  message.value = ''
})
watch(
  () => props.modelValue,
  async (open) => {
    const current = ++session
    if (!open) return
    passed.value = false
    message.value = ''
    errors.value = []
    file.value = undefined
    autoClassify.value = false
    try {
      const response = await materialMasterApi.categories()
      if (current === session) categories.value = (response.data || []).filter((c) => c.selectable)
    } catch (error) {
      if (current === session)
        message.value = error instanceof Error ? error.message : '加载品类失败'
    }
  }
)
function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0]
}
async function download() {
  if (!categoryId.value) return
  busy.value = true
  try {
    downloadMaterialBlob(
      await materialMasterApi.downloadTemplate(categoryId.value),
      '物料导入模板.xlsx'
    )
  } catch (error) {
    message.value = error instanceof Error ? error.message : '模板下载失败'
  } finally {
    busy.value = false
  }
}
async function run(preview: boolean) {
  if (!file.value || busy.value || (!preview && !passed.value)) return
  const current = session
  busy.value = true
  try {
    const response = await materialMasterApi.importExcel(
      file.value,
      categoryId.value,
      preview,
      autoClassify.value && !categoryId.value
    )
    if (current !== session) return
    errors.value = response.data?.errors || []
    passed.value = !!response.data && !errors.value.length && preview
    let text = errors.value.length
      ? '预检失败，请修正文件后重试'
      : `${preview ? '预检通过' : '导入成功'}：${response.data?.successCount || 0}条`
    if (preview && !errors.value.length && autoClassify.value && !categoryId.value) {
      try {
        const dry = await materialClassifyApi.applyRules(false)
        text += `；自动归类将判定 ${dry.data?.changes?.length || 0} 条（需人工确认）`
      } catch {
        text += '；自动归类试算失败（不影响导入预检）'
      }
    }
    message.value = text
    if (!preview && !errors.value.length) {
      visible.value = false
      emit('success')
    }
  } catch (error) {
    if (current === session) {
      passed.value = false
      message.value = error instanceof Error ? error.message : '导入失败'
    }
  } finally {
    if (current === session) busy.value = false
  }
}
</script>

<style scoped>
.import-error {
  color: var(--el-color-danger);
  margin-top: 8px;
}
.hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
