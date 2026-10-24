<template>
  <el-dialog
    :model-value="modelValue"
    title="批量关联物料"
    width="1100px"
    append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="匹配结果只作推荐：请逐行确认候选；未选中行不提交，取消不写库。"
    />
    <el-table :data="rows" border height="420" style="margin-top: 10px">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="原材料名称" prop="originalName" width="180" show-overflow-tooltip />
      <el-table-column label="原规格型号" prop="originalSpec" width="140" show-overflow-tooltip />
      <el-table-column label="匹配" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="batchStatusTagType(row.status)" size="small">
            {{ batchStatusLabel(row.status) }} ({{ row.matchCount }})
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="选择候选档案" min-width="280">
        <template #default="{ row }">
          <el-select
            v-if="row.candidates.length"
            :model-value="row.selectedMaterialId"
            placeholder="请选择候选档案"
            style="width: 100%"
            @change="(v: number) => selectCandidate(row, v)"
          >
            <el-option
              v-for="c in row.candidates"
              :key="c.materialId"
              :label="`${c.materialName || ''} / ${c.materialCode || ''}`"
              :value="c.materialId"
            />
          </el-select>
          <span v-else class="muted">无候选（本卡只关联已有档案）</span>
        </template>
      </el-table-column>
      <el-table-column label="目标预览" min-width="320">
        <template #default="{ row }">
          <template v-if="selectedCandidate(row)">
            <div>{{ selectedCandidate(row)?.materialName }} / {{ selectedCandidate(row)?.materialCode }}</div>
            <div class="muted">
              规格 {{ selectedCandidate(row)?.specification || '—' }} ｜ 尺寸
              {{ selectedCandidate(row)?.size || '—' }} ｜ 单位
              {{ selectedCandidate(row)?.unit || '—' }}
            </div>
          </template>
          <span v-else class="muted">未选择</span>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <span class="summary">已选 {{ links.length }} / {{ rows.length }} 行</span>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!links.length || submitting"
        @click="emit('confirm', links)"
      >
        确认关联
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  batchStatusLabel,
  batchStatusTagType,
  buildBatchLinks,
  selectCandidate,
  selectedCandidate,
  type BatchRow,
} from './bomMaterialBatch'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    rows: BatchRow[]
    submitting?: boolean
  }>(),
  { rows: () => [], submitting: false }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', links: Array<{ itemId: number; materialId: number }>): void
}>()

const links = computed(() => buildBatchLinks(props.rows))
</script>

<style scoped>
.muted {
  color: #98a2af;
  font-size: 12px;
}
.summary {
  margin-right: 12px;
  color: #606266;
  font-size: 12px;
}
</style>
