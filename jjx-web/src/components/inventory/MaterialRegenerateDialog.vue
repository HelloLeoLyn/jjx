<template>
  <el-dialog v-model="visible" title="重算名称与规格" width="860px" append-to-body>
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="result">
      <div class="diff-legend">差异标记：<del>删除</del> / <ins>新增</ins></div>
      <section
        v-for="row in comparisonRows"
        :key="row.label"
        class="comparison"
        :data-field="row.label"
      >
        <div class="comparison-title">
          {{ row.label }}
          <el-tag :type="row.diff.changed ? 'warning' : 'success'" size="small" class="diff-tag">
            {{ row.diff.changed ? '有变化' : '一致' }}
          </el-tag>
          <span v-if="!row.diff.changed" class="no-diff">无差异</span>
        </div>
        <div class="comparison-values">
          <div class="comparison-side" data-side="current">
            <div class="value-label">当前{{ row.label }}</div>
            <div class="diff-text">
              <template v-for="(part, index) in row.diff.current" :key="index"
                ><del v-if="part.kind === 'removed'">{{ part.text }}</del
                ><span v-else>{{ part.text }}</span></template
              ><span v-if="!row.diff.current.length">—</span>
            </div>
          </div>
          <span class="comparison-arrow" aria-label="重算为">→</span>
          <div class="comparison-side" data-side="generated">
            <div class="value-label">重算{{ row.label }}</div>
            <div class="diff-text">
              <template v-for="(part, index) in row.diff.generated" :key="index"
                ><ins v-if="part.kind === 'added'">{{ part.text }}</ins
                ><span v-else>{{ part.text }}</span></template
              ><span v-if="!row.diff.generated.length">—</span>
            </div>
          </div>
        </div>
        <template v-if="row.label === '规格'">
          <div v-if="!result.specGenerated" class="evidence-line">{{ specUnavailable }}</div>
          <div class="evidence-line">
            规格由后端按模板重算、不能手改。如需调整请回编辑改属性/厚度/尺寸。
          </div>
        </template>
      </section>

      <el-form label-width="96px" class="regen-form">
        <el-form-item label="最终名称">
          <el-input v-model="finalName" maxlength="200" placeholder="可确认或修改名称" />
          <el-checkbox v-if="result.nameChanged" v-model="adoptGenerated" class="adopt-check">
            采用生成名称（会替换当前人工确认的名称）
          </el-checkbox>
        </el-form-item>
      </el-form>

      <div class="evidence rules">
        <div class="evidence-title">生效规则原文</div>
        <template v-if="!configurationError">
          <div class="evidence-line rule-text">命名规则：{{ rules.name }}</div>
          <div v-if="rules.nameDetails" class="evidence-line">{{ rules.nameDetails }}</div>
          <div class="evidence-line rule-text">规格模板：{{ rules.spec }}</div>
          <div v-if="rules.specDetails" class="evidence-line">{{ rules.specDetails }}</div>
        </template>
        <div v-else class="missing-value">
          配置读取失败，暂无法核实生效规则与必填属性：{{ configurationError }}
        </div>
      </div>
      <div class="evidence">
        <div class="evidence-title">参与值清单（当前值，只读）</div>
        <dl class="participant-list">
          <div
            v-for="item in participants"
            :key="item.key"
            :data-value-key="item.key"
            :class="{ 'missing-value': item.empty }"
          >
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}<span v-if="item.empty">；该字段为空，生成结果会缺这一段</span></dd>
          </div>
        </dl>
      </div>
      <div class="evidence">
        <div class="evidence-title">依据参考</div>
        <div class="evidence-line">名称：{{ nameEvidence }}</div>
        <div class="evidence-line">规格：{{ result.evidence.join('；') }}</div>
        <div class="evidence-line">注：{{ result.remark }}</div>
      </div>
    </template>
    <el-empty v-else description="未找到物料信息" />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">
        {{ canSave ? '保存名称并按模板重算规格' : '无变化' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { previewMasterName } from './materialMasterModel'
import {
  buildRegeneration,
  describeRegenerationRules,
  diffCharacters,
  hasRegenerationChanges,
  regenerationValues,
  resolveFinalName,
  specificationUnavailableReason,
  type RegenerationValue,
  type RegenerationResult,
} from './materialRegenerate'
import type {
  MaterialDictionaries,
  MaterialMasterConfiguration,
} from '@/types/inventory/materialMaster'

const props = withDefaults(defineProps<{ modelValue: boolean; materialId?: number | null }>(), {
  modelValue: false,
  materialId: null,
})
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success'): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const saving = ref(false)
const result = ref<RegenerationResult | null>(null)
const finalName = ref('')
const adoptGenerated = ref(false)
const nameEvidence = ref('')
const rules = ref(describeRegenerationRules())
const participants = ref<RegenerationValue[]>([])
const specUnavailable = ref('')
const configurationError = ref('')
const comparisonRows = computed(() =>
  result.value
    ? [
        {
          label: '名称',
          diff: diffCharacters(result.value.currentName, result.value.generatedName),
        },
        {
          label: '规格',
          diff: diffCharacters(result.value.currentSpec, result.value.generatedSpec),
        },
      ]
    : []
)
let loadRequest = 0

const canSave = computed(
  () =>
    !!result.value &&
    hasRegenerationChanges(result.value, finalName.value) &&
    !!finalName.value.trim() &&
    !saving.value
)

watch(adoptGenerated, () => {
  if (result.value) finalName.value = resolveFinalName(result.value, adoptGenerated.value)
})

async function readConfiguration(categoryId?: number | null): Promise<{
  configuration: MaterialMasterConfiguration | null
  error: string
}> {
  if (!categoryId) return { configuration: null, error: '' }
  try {
    const response = await materialMasterApi.configuration(categoryId)
    return { configuration: response.data || null, error: '' }
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载品类配置失败'
    // 配置接口对无启用模板抛业务错误；其它失败不能伪装成“未配置”。
    return {
      configuration: null,
      error: message.includes('该品类尚未配置启用的属性模板') ? '' : message,
    }
  }
}

async function load(materialId: number) {
  const request = ++loadRequest
  loading.value = true
  result.value = null
  adoptGenerated.value = false
  try {
    const [detailResponse, dictResponse] = await Promise.all([
      materialMasterApi.detail(materialId),
      materialMasterApi.dictionaries(),
    ])
    if (request !== loadRequest) return
    const detail = detailResponse.data
    if (!detail) {
      ElMessage.error('未找到物料信息')
      return
    }
    const dictionaries: MaterialDictionaries = dictResponse.data || {}
    const material = detail.material
    const { sizeUnit: _legacySizeUnit, ...namingMaterial } = material
    const specification = detail.specificationGeneration
    if (!specification) throw new Error('规格生成结果加载失败，请稍后重试')
    // 与新增/编辑共用后端命名规则；仅无规则/无生成内容时沿用相同的旧名称回退。
    const [namingResponse, configurationResult] = await Promise.all([
      material.categoryId
        ? materialMasterApi.renderNaming(material.categoryId, {
            ...namingMaterial,
            attributeJson: detail.attributes || {},
          })
        : Promise.resolve(null),
      readConfiguration(material.categoryId),
    ])
    if (request !== loadRequest) return
    const schema = configurationResult.configuration?.schema
    configurationError.value = configurationResult.error
    rules.value = describeRegenerationRules(schema)
    participants.value = regenerationValues(material, detail.attributes || {}, schema, dictionaries)
    specUnavailable.value = specificationUnavailableReason(
      material.categoryId,
      schema,
      specification.message,
      configurationResult.error
    )
    const generatedName =
      namingResponse?.data ||
      previewMasterName(
        {
          materialMeta: String(material.materialMeta ?? ''),
          model: String(material.model ?? ''),
          color: String(material.color ?? ''),
          brand: String(material.brand ?? ''),
        },
        dictionaries
      )
    nameEvidence.value = namingResponse?.data
      ? '按该品类当前命名规则生成（与新增/编辑共用）'
      : '品类规则未生成名称，回退材料 + 型号 + 颜色（品牌）（与新增/编辑相同）'
    result.value = buildRegeneration(
      detail.material,
      {
        name: generatedName,
        specification: specification.specification,
        specGenerated: specification.generated,
      },
      [specification.message],
      detail.material.remark || undefined
    )
    finalName.value = result.value.currentName || result.value.generatedName
  } catch (error) {
    if (request !== loadRequest) return
    ElMessage.error(error instanceof Error ? error.message : '加载物料信息失败')
  } finally {
    if (request === loadRequest) loading.value = false
  }
}

async function save() {
  if (!result.value || !props.materialId || !canSave.value) return
  saving.value = true
  try {
    // 只改名称与规格，不动分类/属性/供应商/库存
    await materialMasterApi.updateNaming(props.materialId, {
      materialName: finalName.value.trim(),
      reason: `名称：${nameEvidence.value}；规格：${result.value.evidence.join('；')}`,
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
    else {
      loadRequest++
      result.value = null
      loading.value = false
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.comparison {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
.comparison-title {
  font-weight: 600;
}
.comparison-values {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 12px;
  margin: 10px 0;
}
.value-label,
.no-diff {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.no-diff {
  margin-left: 8px;
}
.diff-text,
.rule-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.comparison-arrow {
  align-self: center;
}
del {
  color: #a12622;
  background: #ffebe9;
  text-decoration: line-through;
}
ins {
  color: #116329;
  background: #dafbe1;
  text-decoration: underline;
  font-weight: 600;
}
.diff-legend {
  font-size: 12px;
}
.missing-value {
  color: #b42318;
}
.participant-list {
  margin: 0;
}
.participant-list > div {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 8px;
  padding: 4px 0;
}
.participant-list dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.rules {
  margin-bottom: 8px;
}
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
