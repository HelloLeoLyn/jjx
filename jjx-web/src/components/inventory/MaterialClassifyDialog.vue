<template>
  <el-dialog v-model="visible" title="批量定分类" width="720px" append-to-body>
    <el-alert
      type="info"
      :closable="false"
      title="给勾选的物料指定末级分类；单次最多 200 条。未归类物料可先批量归类，再按模板补属性。"
      style="margin-bottom: 12px"
    />
    <el-form label-width="110px">
      <el-form-item label="已选物料">
        <span>{{ materialIds.length }} 条</span>
      </el-form-item>
      <el-form-item label="末级分类" required>
        <el-select v-model="categoryId" filterable placeholder="选择末级分类" style="width: 100%">
          <el-option
            v-for="category in leafCategories"
            :key="category.categoryId"
            :label="category.pathName"
            :value="category.categoryId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="保存为规则">
        <el-checkbox v-model="saveRule">成功后把关键词→分类保存为规则</el-checkbox>
        <div v-if="saveRule" class="rule-row">
          <el-input v-model="keyword" maxlength="200" placeholder="关键词，如 保护膜" style="width: 240px" />
          <el-select v-model="matchField" style="width: 160px">
            <el-option label="名称或规格（ANY）" value="ANY" />
            <el-option label="仅名称（NAME）" value="NAME" />
            <el-option label="仅规格（SPEC）" value="SPEC" />
          </el-select>
        </div>
        <div v-if="saveRule" class="hint">规则只影响后续导入/归类建议，命中后仍需人工确认。</div>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="message"
      :title="message"
      :type="failed ? 'error' : 'success'"
      :closable="false"
      style="margin-top: 8px"
    />
    <el-table v-if="failDetails.length" :data="failDetails" size="small" max-height="200" style="margin-top: 8px">
      <el-table-column label="物料ID" prop="materialId" width="110" />
      <el-table-column label="失败原因" prop="message" />
    </el-table>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="busy" :disabled="!canSubmit" @click="submit">确定归类</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialClassifyApi } from '@/api/inventory/classify'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { suggestRuleKeyword } from '@/enums/inventory/MaterialClassEnum'
import type { ClassifyItem, MaterialClassMatchField } from '@/types/inventory/classify'
import type { MaterialMasterCategory } from '@/types/inventory/materialMaster'

const props = withDefaults(
  defineProps<{ modelValue: boolean; materialIds: number[]; names?: Array<string | null | undefined> }>(),
  { modelValue: false, materialIds: () => [], names: () => [] }
)
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success'): void }>()

const MAX_BATCH = 200
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const categories = ref<MaterialMasterCategory[]>([])
const leafCategories = computed(() => categories.value.filter((item) => item.selectable))
const categoryId = ref<number>()
const saveRule = ref(false)
const keyword = ref('')
const matchField = ref<MaterialClassMatchField>('ANY')
const busy = ref(false)
const message = ref('')
const failed = ref(false)
const failDetails = ref<ClassifyItem[]>([])
const canSubmit = computed(
  () => !!categoryId.value && props.materialIds.length > 0 && !busy.value && (!saveRule.value || !!keyword.value.trim())
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    message.value = ''
    failed.value = false
    failDetails.value = []
    categoryId.value = undefined
    saveRule.value = false
    keyword.value = suggestRuleKeyword(props.names || [])
    matchField.value = 'ANY'
    if (!categories.value.length) {
      try {
        const response = await materialMasterApi.categories()
        categories.value = (response.data || []).filter((item) => item.selectable)
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '加载分类失败')
      }
    }
  },
  { immediate: true }
)

async function submit() {
  if (!canSubmit.value) return
  if (props.materialIds.length > MAX_BATCH) {
    ElMessage.warning(`单次最多 ${MAX_BATCH} 条，请减少勾选`)
    return
  }
  busy.value = true
  try {
    const response = await materialClassifyApi.batchAssign({
      materialIds: props.materialIds,
      categoryId: categoryId.value!,
    })
    const result = response.data
    failDetails.value = (result?.details || []).filter((item) => !item.success)
    failed.value = (result?.failCount || 0) > 0
    message.value = `成功 ${result?.successCount || 0} 条，失败 ${result?.failCount || 0} 条`
    if (saveRule.value && keyword.value.trim()) {
      const category = leafCategories.value.find((item) => item.categoryId === categoryId.value)
      if (category) {
        await materialClassifyApi.createRule({
          keyword: keyword.value.trim(),
          matchField: matchField.value,
          categoryCode: category.categoryCode,
        })
        message.value += '；规则已保存'
      }
    }
    emit('success')
  } catch (error) {
    failed.value = true
    message.value = error instanceof Error ? error.message : '批量定分类失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.rule-row {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
.hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
