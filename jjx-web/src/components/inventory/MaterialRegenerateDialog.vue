<template>
  <el-dialog v-model="visible" title="重算名称与规格" width="680px" append-to-body>
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="result">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="当前名称">{{ result.currentName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="生成名称">
          {{ result.generatedName || '—' }}
          <el-tag v-if="result.nameChanged" size="small" type="warning" class="diff-tag">有变化</el-tag>
          <el-tag v-else size="small" type="success" class="diff-tag">一致</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前规格">{{ result.currentSpec || '—' }}</el-descriptions-item>
        <el-descriptions-item label="生成规格">
          {{ result.generatedSpec || '—' }}
          <el-tag v-if="result.specChanged" size="small" type="warning" class="diff-tag">有变化</el-tag>
          <el-tag v-else size="small" type="success" class="diff-tag">一致</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="96px" class="regen-form">
        <el-form-item label="最终名称">
          <el-input v-model="finalName" maxlength="200" />
          <el-checkbox
            v-if="result.nameChanged"
            v-model="adoptGenerated"
            class="adopt-check"
          >
            采用生成名称（会替换当前人工确认的名称）
          </el-checkbox>
        </el-form-item>
        <el-form-item label="最终规格">
          <el-input v-model="finalSpec" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>

      <div class="evidence">
        <div class="evidence-title">依据</div>
        <div class="evidence-line">名称：材料 + 型号 + 颜色（品牌）</div>
        <div v-if="result.evidence.length" class="evidence-line">
          规格：{{ result.evidence.join('；') }}
        </div>
        <div v-else class="evidence-line">规格：该分类暂无属性值，生成值为空</div>
      </div>
    </template>
    <el-empty v-else description="未找到物料信息" />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">
        {{ canSave ? '保存名称与规格' : '无变化' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import {
  attributeEvidence,
  previewMasterName,
  previewMasterSpecification,
} from './materialMasterModel'
import {
  buildRegeneration,
  hasRegenerationChanges,
  resolveFinalName,
  type RegenerationResult,
} from './materialRegenerate'
import type { AttributeValue, MaterialDictionaries } from '@/types/inventory/materialMaster'

const props = withDefaults(
  defineProps<{ modelValue: boolean; materialId?: number | null }>(),
  { modelValue: false, materialId: null }
)
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success'): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const saving = ref(false)
const result = ref<RegenerationResult | null>(null)
const finalName = ref('')
const finalSpec = ref('')
const adoptGenerated = ref(false)

const canSave = computed(
  () =>
    !!result.value &&
    hasRegenerationChanges(result.value, finalName.value, finalSpec.value) &&
    !!finalName.value.trim() &&
    !saving.value
)

watch(adoptGenerated, () => {
  if (result.value) finalName.value = resolveFinalName(result.value, adoptGenerated.value)
})

async function load(materialId: number) {
  loading.value = true
  result.value = null
  adoptGenerated.value = false
  try {
    const [detailResponse, dictResponse] = await Promise.all([
      materialMasterApi.detail(materialId),
      materialMasterApi.dictionaries(),
    ])
    const detail = detailResponse.data
    if (!detail) {
      ElMessage.error('未找到物料信息')
      return
    }
    const dictionaries: MaterialDictionaries = dictResponse.data || {}
    const material = detail.material as unknown as Record<string, unknown>
    const generatedName = previewMasterName(
      {
        materialMeta: String(material.materialMeta ?? ''),
        model: String(material.model ?? ''),
        color: String(material.color ?? ''),
        brand: String(material.brand ?? ''),
      },
      dictionaries
    )
    const attributes = (detail.attributes || {}) as Record<string, AttributeValue>
    const generatedSpec = previewMasterSpecification(detail.configuration?.schema, attributes)
    const evidence = attributeEvidence(detail.configuration?.schema, attributes)
    result.value = buildRegeneration(detail.material, { name: generatedName, specification: generatedSpec }, evidence)
    finalName.value = result.value.currentName || result.value.generatedName
    finalSpec.value = result.value.generatedSpec
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载物料信息失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!result.value || !props.materialId || !canSave.value) return
  saving.value = true
  try {
    // 只改名称与规格，不动分类/属性/供应商/库存
    await materialMasterApi.updateNaming(props.materialId, {
      materialName: finalName.value.trim(),
      specification: finalSpec.value.trim(),
      reason: `名称：材料 + 型号 + 颜色（品牌）；规格：${result.value.evidence.join('；') || '该分类暂无属性值'}`,
    })
    ElMessage.success('名称与规格已更新')
    visible.value = false
    emit('success')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

watch(
  () => [props.modelValue, props.materialId] as const,
  ([open, id]) => {
    if (open && id) void load(id)
  },
  { immediate: true }
)
</script>

<style scoped>
.diff-tag {
  margin-left: 8px;
}
.regen-form {
  margin-top: 16px;
}
.adopt-check {
  margin-top: 6px;
  height: auto;
  white-space: normal;
  line-height: 1.4;
}
.evidence {
  margin-top: 4px;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 6px;
  font-size: 12px;
  color: #606266;
}
.evidence-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: #303133;
}
.evidence-line {
  line-height: 1.6;
}
</style>
