<template>
  <el-dialog :model-value="modelValue" title="预览并应用规则" width="min(1180px, 96vw)" append-to-body
    :close-on-click-modal="!applying" :close-on-press-escape="!applying" :show-close="!applying"
    @update:model-value="value => !applying && emit('update:modelValue', value)">
    <p>按当前品类已保存的规则生成，仅列出有变化或需要处理的物料。勾选支持跨页保留，每次最多应用500条。</p>
    <div class="toolbar">
      <el-checkbox v-model="options.applyName" :disabled="applying">更新名称</el-checkbox>
      <el-checkbox v-model="options.applySpecification" :disabled="applying">更新规格</el-checkbox>
      <el-checkbox v-model="options.applyModel" :disabled="applying">同时更新型号</el-checkbox>
    </div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="物料编码 / 名称" clearable maxlength="100" style="width: 260px"
        :disabled="applying" @keyup.enter="search" />
      <el-button :disabled="applying" :loading="loading" @click="search">查询 / 刷新预览</el-button>
      <el-button :disabled="applying || !selected.size" @click="selected.clear()">清空已选</el-button>
      <span>已选 {{ selected.size }} 条</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="records" row-key="materialId" border max-height="430" v-loading="loading">
      <el-table-column width="60" align="center">
        <template #header><el-checkbox :model-value="allPageSelected" :indeterminate="somePageSelected && !allPageSelected"
          :disabled="loading || applying || !selectableRows.length" aria-label="勾选本页可应用物料"
          @change="value => togglePage(Boolean(value))" /></template>
        <template #default="{ row }"><el-checkbox :model-value="selected.has(row.materialId)"
          :disabled="!row.selectable || loading || applying" :aria-label="`选择${row.materialCode}`"
          @change="value => toggle(row, Boolean(value))" /></template>
      </el-table-column>
      <el-table-column prop="materialCode" label="物料编码" width="135" />
      <el-table-column v-if="options.applyName" label="名称：当前 → 应用后" min-width="220">
        <template #default="{ row }"><div>{{ row.oldName || '—' }}</div><div :class="{ changed: row.oldName !== row.newName }">→ {{ row.newName || '—' }}</div></template>
      </el-table-column>
      <el-table-column v-if="options.applyModel" label="型号：当前 → 应用后" min-width="160">
        <template #default="{ row }"><div>{{ row.oldModel || '—' }}</div><div :class="{ changed: row.oldModel !== row.newModel }">→ {{ row.newModel || '—' }}</div></template>
      </el-table-column>
      <el-table-column v-if="options.applySpecification" label="规格：当前 → 应用后" min-width="200">
        <template #default="{ row }"><div>{{ row.oldSpecification || '—' }}</div><div :class="{ changed: row.oldSpecification !== row.newSpecification }">→ {{ row.newSpecification || '—' }}</div></template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
    </el-table>
    <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
      :page-sizes="[20, 50, 100]" :disabled="loading || applying" layout="total, sizes, prev, pager, next"
      @current-change="load" @size-change="search" />
    <el-alert v-if="result" :title="`已应用 ${result.success} 条，跳过 ${result.skipped} 条，失败 ${result.failed} 条`"
      :type="result.failed ? 'warning' : 'success'" :closable="false" />
    <el-table v-if="failures.length" :data="failures" border max-height="180">
      <el-table-column label="物料" width="150"><template #default="{ row }">{{ row.materialCode || row.materialId }}</template></el-table-column>
      <el-table-column prop="message" label="未应用原因" />
    </el-table>
    <template #footer>
      <el-button :disabled="applying" @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :loading="applying" :disabled="loading || !selected.size || !!error"
        @click="apply">应用到已选物料（{{ selected.size }}条）</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import type { MaterialRuleOptions, MaterialRuleChange, MaterialRuleSelection, MaterialRuleApplied } from '@/types/inventory/materialMaster'

const props = defineProps<{ modelValue: boolean; categoryId: number }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void; (event: 'applied'): void }>()
const options = reactive<MaterialRuleOptions>({ categoryId: props.categoryId, applyName: true, applySpecification: true, applyModel: false })
const keyword = ref(''), pageNum = ref(1), pageSize = ref(20), total = ref(0)
const records = ref<MaterialRuleChange[]>([]), selected = reactive(new Map<number, MaterialRuleSelection>())
const loading = ref(false), applying = ref(false), error = ref(''), result = ref<MaterialRuleApplied>()
const failures = computed(() => result.value?.records.filter(row => !row.success && !row.skipped) || [])
const selectableRows = computed(() => records.value.filter(row => row.selectable && row.previewToken))
const allPageSelected = computed(() => selectableRows.value.length > 0 && selectableRows.value.every(row => selected.has(row.materialId)))
const somePageSelected = computed(() => selectableRows.value.some(row => selected.has(row.materialId)))
let request = 0
async function load() {
  const generation = ++request
  error.value = ''
  if (!options.applyName && !options.applySpecification && !options.applyModel) {
    records.value = []; total.value = 0; loading.value = false; error.value = '请至少选择一项应用内容'; return
  }
  loading.value = true
  try {
    const response = await materialMasterApi.ruleChanges({ ...options, pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    if (generation !== request) return
    if (!response.data) throw new Error('预览结果为空，请刷新')
    records.value = response.data.records; total.value = response.data.total
    // 已选行资料变化时取消选择，必须重新审阅；不静默换成新的应用结果。
    for (const row of records.value) {
      if (selected.has(row.materialId) && (!row.selectable || selected.get(row.materialId)?.previewToken !== row.previewToken)) selected.delete(row.materialId)
    }
  } catch (e) {
    if (generation !== request) return
    records.value = []; error.value = e instanceof Error ? e.message : '预览失败'
  } finally { if (generation === request) loading.value = false }
}
function search() { pageNum.value = 1; void load() }
function toggle(row: MaterialRuleChange, checked: boolean) {
  if (!checked) { selected.delete(row.materialId); return }
  if (!row.selectable || !row.previewToken) return
  if (!selected.has(row.materialId) && selected.size >= 500) { ElMessage.warning('每次最多勾选500条'); return }
  selected.set(row.materialId, { materialId: row.materialId, previewToken: row.previewToken })
}
function togglePage(checked: boolean) { selectableRows.value.forEach(row => toggle(row, checked)) }
async function apply() {
  if (loading.value || applying.value || !selected.size || error.value) return
  applying.value = true; error.value = ''
  try {
    const response = await materialMasterApi.applyRules({ ...options }, [...selected.values()])
    if (!response.data) throw new Error('应用结果未返回，请刷新预览核对')
    result.value = response.data; selected.clear()
    if (result.value.success) emit('applied')
    await load()
  } catch (e) { error.value = e instanceof Error ? e.message : '应用失败，请刷新预览核对' }
  finally { applying.value = false }
}
watch(() => [props.modelValue, props.categoryId, options.applyName, options.applySpecification, options.applyModel], () => {
  ++request; selected.clear(); result.value = undefined; records.value = []; total.value = 0
  options.categoryId = props.categoryId; pageNum.value = 1
  if (props.modelValue) void load()
}, { immediate: true })
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 12px 0; }
.changed { color: var(--el-color-primary); font-weight: 600; }
.el-pagination, .el-alert { margin: 12px 0; }
</style>
