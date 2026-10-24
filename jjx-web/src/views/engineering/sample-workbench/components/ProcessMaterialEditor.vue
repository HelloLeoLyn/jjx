<template>
  <!-- 行3：材料表格 -->
  <div class="pc-row">
    <div class="pc-mat">
      <el-table
        v-if="rows !== undefined || editable || tableRows.length"
        :data="tableRows"
        row-key="uid"
        size="small"
        border
        style="width: 100%"
      >
        <el-table-column label="材料" min-width="150">
          <template #default="{ row }">
            <template v-if="editable">
              <BomMaterialSelector
                :material-id="row.materialId"
                :material-name="row.materialId ? row.name : ''"
                :specification="row.spec"
                :material-code="row.materialCode"
                show-material-code
                browse-on-focus
                :keep-text-on-blur="false"
                :create-permissions="['engineering:sample:workbench']"
                :confirm-selection="true"
                @select="(item) => $emit('material-selected', row, item)"
                @clear="$emit('material-cleared', row)"
                @draft-change="(draft) => onMaterialDraft(row, draft)"
                @create="(keyword) => matCreate(row, keyword)"
              />
            </template>
            <template v-else>{{ row.name }}</template>
          </template>
        </el-table-column>
        <el-table-column label="规格" width="110">
          <template #default="{ row }">
            <el-input
              v-if="editable"
              v-model="row.spec"
              size="small"
              placeholder="规格"
              :disabled="!!row.materialId"
            />
            <span v-else>{{ row.spec || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模数" width="90">
          <template #default="{ row }">
            <el-input-number
              v-if="editable"
              v-model="row.moduleQty"
              :min="1"
              :precision="0"
              :controls="false"
              size="small"
              style="width: 100%"
            />
            <span v-else>{{ normalized(row).moduleQty ?? '待核对' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="基数" width="90">
          <template #default="{ row }">
            <el-input-number
              v-if="editable"
              v-model="row.baseQty"
              :min="1"
              :precision="0"
              :controls="false"
              size="small"
              style="width: 100%"
            />
            <span v-else>{{ normalized(row).baseQty ?? '待核对' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="跳距" width="120">
          <template #default="{ row }">
            <div v-if="editable" style="display: flex; align-items: center; gap: 4px">
              <el-input-number
                :model-value="splitNumber(row.split)"
                  aria-label="跳距（mm）"
                :min="0"
                :precision="0"
                :step="1"
                :controls="false"
                size="small"
                style="width: 78px"
                @update:model-value="(value) => setMaterialSplit(row, value)"
              /><span>mm</span>
            </div>
            <span v-else>{{ row.split || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="70">
          <template #default="{ row }">
            <el-input
              v-if="editable"
              v-model="row.unit"
              size="small"
              :disabled="!!row.materialId"
            />
            <span v-else>{{ row.unit || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="editable" label="操作" width="130" align="center">
          <template #header>
            <div class="material-operation-header">
              <span>操作</span>
              <el-button
                size="small"
                link
                type="primary"
                :aria-label="addLabel || '添加材料'"
                @click="$emit('add-material-row')"
                >＋ 添加材料</el-button
              >
            </div>
          </template>
          <template #default="{ $index }">
            <el-button size="small" link type="danger" @click="$emit('remove-material', $index)"
              >删</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <span v-else-if="!tableRows.length" style="color: #c0c4cc; font-size: 12px">无材料</span>
    </div>
  </div>
</template>
<script setup lang="ts">
import {
  normalizeMaterial as normalized,
  splitNumber,
  setMaterialSplit,
} from '../composables/sampleMaterials'
import { computed } from 'vue'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
const props = defineProps<{
  pc: any
  rows?: any[]
  addLabel?: string
  readonly?: boolean
  parseMaterials: (json?: string | null) => any[]
}>()
const editable = computed(() => !!props.pc.editing && !props.readonly)
const tableRows = computed(
  () =>
    props.rows ??
    (props.pc.editing ? props.pc.materialRows : props.parseMaterials(props.pc.materials))
)
const emit = defineEmits<{
  (e: 'material-selected', row: any, value: any): void
  (e: 'material-cleared', row: any): void
  (e: 'add-material-row'): void
  (e: 'remove-material', index: number): void
  (e: 'create-material', row: any): void
}>()


function onMaterialDraft(row: any, draft: { pending: boolean; text: string }) {
  row.__matDraftPending = !!draft.pending
  row.__matDraftText = draft.pending ? String(draft.text || '') : ''
}

function matCreate(row: any, keyword: string) {
  row._matKw = String(keyword || '').trim()
  emit('create-material', row)
}
</script>
<style scoped>
.material-operation-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.pc-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.pc-mat {
  flex: 1;
  min-width: 0;
}
</style>
