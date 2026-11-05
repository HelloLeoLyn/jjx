<template>
  <el-dialog
    v-model="visible"
    title="物料导入 1A（一个模板 + 品类列）"
    width="700px"
    append-to-body
    :close-on-click-modal="false"
    :show-close="!dictionarySaving"
    :close-on-press-escape="!dictionarySaving"
  >
    <el-form label-width="100px">
      <el-form-item label="品类/子类（必选）">
        <el-select
          v-model="categoryId"
          filterable
          placeholder="选择要导入的品类或子类（有子类时必须选到子类）"
          style="width: 100%"
          :disabled="busy || dictionarySaving"
        >
          <el-option
            v-for="category in categories"
            :key="category.categoryId"
            :label="category.pathName"
            :value="category.categoryId"
          />
        </el-select>
        <div class="hint">模板按所选品类/子类只导出该分类的属性列；有子类的品类（如电子元件/辅料）必须选到子类。</div>
      </el-form-item>
      <el-form-item label="自动归类">
        <el-switch v-model="autoClassify" :disabled="busy || dictionarySaving || !!categoryId" />
        <span class="hint" style="margin-left: 8px">按已保存规则试判，仅建议、不写人工确认</span>
      </el-form-item>
      <el-form-item label="录入模板"
        ><el-button :disabled="!categoryId || busy || dictionarySaving" @click="download"
          >下载该品类模板</el-button
        ></el-form-item
      >
      <el-form-item label="Excel文件"
        ><input type="file" accept=".xlsx,.xls" :disabled="busy || dictionarySaving" @change="selectFile"
      /></el-form-item>
    </el-form>
    <el-alert
      title="先选品类/子类并下载对应模板。迁移现有物料时填写物料ID，保留原ID与编码；需要编辑权限。整表预检通过后导入，任一行失败不会部分写入。"
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
    <div v-for="(error, index) in errorRows" :key="index" class="import-error">
      <span>{{ error.message }}</span>
      <el-button
        v-if="canAddDictionary && error.dictionary"
        link
        type="primary"
        :disabled="busy || dictionarySaving"
        @click="openDictionary(error.dictionary)"
      >补进字典</el-button>
    </div>
    <el-dialog
      v-model="dictionaryVisible"
      title="补进字典"
      width="420px"
      append-to-body
      :close-on-click-modal="false"
      :show-close="!dictionarySaving"
      :close-on-press-escape="!dictionarySaving"
    >
      <el-form v-if="dictionaryTarget" label-width="70px" @submit.prevent="saveDictionary">
        <el-form-item label="字典">
          <el-input :model-value="dictionaryLabels[dictionaryTarget.dict]" readonly />
        </el-form-item>
        <el-form-item label="值">
          <el-input :model-value="dictionaryTarget.value" readonly />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="dictionaryName" :disabled="dictionarySaving" maxlength="50" />
        </el-form-item>
      </el-form>
      <el-alert v-if="dictionaryError" :title="dictionaryError" type="error" :closable="false" />
      <template #footer>
        <el-button :disabled="dictionarySaving" @click="dictionaryVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="dictionarySaving"
          :disabled="!canAddDictionary || !dictionaryName.trim()"
          @click="saveDictionary"
        >确认</el-button>
      </template>
    </el-dialog>
    <template #footer>
      <el-button :disabled="busy || dictionarySaving" @click="visible = false">关闭</el-button>
      <el-button :disabled="!file || dictionarySaving" :loading="busy" @click="run(true)"
        >预检</el-button
      >
      <el-button type="primary" :disabled="!passed || dictionarySaving" :loading="busy" @click="run(false)"
        >导入物料</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { dictApi } from '@/api/system/dict'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'
import { Permissions } from '@/enums/permissions'
import { useUserStore } from '@/store/modules/user'
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
const dictionaryLabels = { brand: '品牌', color: '颜色', form: '形态', unit: '单位' } as const
type MaterialDictionary = keyof typeof dictionaryLabels
type DictionaryTarget = { dict: MaterialDictionary; value: string }
const userStore = useUserStore()
const canAddDictionary = computed(() => userStore.hasPermission(Permissions.SYSTEM_DICT_ADD))
const dictionaryVisible = ref(false)
const dictionaryTarget = ref<DictionaryTarget>()
const dictionaryName = ref('')
const dictionarySaving = ref(false)
const dictionaryError = ref('')
const errorRows = computed(() => errors.value.map(message => ({
  message,
  dictionary: parseDictionaryError(message),
})))

function parseDictionaryError(error: string): DictionaryTarget | undefined {
  const match = /字典值不存在或名称不唯一：(.+)（([^（）]+)）$/.exec(error)
  if (!match || !match[1].trim() || !Object.prototype.hasOwnProperty.call(dictionaryLabels, match[2])) return
  return { dict: match[2] as MaterialDictionary, value: match[1].trim() }
}

function openDictionary(target: DictionaryTarget) {
  if (!canAddDictionary.value || busy.value || dictionarySaving.value) return
  dictionaryTarget.value = target
  dictionaryName.value = target.value
  dictionaryError.value = ''
  dictionaryVisible.value = true
}

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
    dictionaryVisible.value = false
    dictionaryTarget.value = undefined
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
async function saveDictionary() {
  const target = dictionaryTarget.value
  const label = dictionaryName.value.trim()
  if (!target || !dictionaryVisible.value || !canAddDictionary.value || dictionarySaving.value || busy.value || !label) return
  const itemKey = `master_${target.value.toUpperCase()}`
  if (itemKey.length > 50 || target.value.length > 100 || label.length > 50) {
    dictionaryError.value = '字典键不能超过50个字符，值不能超过100个字符，名称不能超过50个字符'
    return
  }
  const current = session
  let refresh = false
  dictionarySaving.value = true
  dictionaryError.value = ''
  try {
    // 现有接口仅返回启用项，查重与 sort_order 最大值都只按启用项计算。
    // 已停用项不参与查重，若同名停用项存在可能新增出同值项。
    const response = await dictApi.getItems(target.dict)
    if (current !== session) return
    const items = response.data || []
    const exists = items.some(item =>
      item.itemValue.toUpperCase() === target.value.toUpperCase() ||
      item.label?.toUpperCase() === target.value.toUpperCase() ||
      item.itemKey.toUpperCase() === itemKey.toUpperCase()
    )
    if (exists) {
      ElMessage.info('已存在')
    } else {
      await dictApi.addItem({
        dictCode: target.dict,
        itemKey,
        itemValue: target.value,
        label,
        sortOrder: (items.length ? Math.max(...items.map(item => item.sortOrder ?? 0)) : 0) + 1,
        isActive: CommonStatusEnum.NORMAL.value,
      })
      if (current !== session) return
      ElMessage.success('添加成功')
    }
    dictionaryVisible.value = false
    refresh = true
  } catch (error) {
    if (current === session)
      dictionaryError.value = error instanceof Error ? error.message : '添加字典失败'
  } finally {
    dictionarySaving.value = false
  }
  if (refresh && current === session) await run(true)
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
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
