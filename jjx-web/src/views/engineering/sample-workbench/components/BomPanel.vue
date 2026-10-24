<template>
  <el-card class="col-bom" shadow="never">
    <template #header>
      <span style="font-weight: 600">BOM 物料清单</span>
      <span class="desc">卡片材料与独立材料共用同一份草稿</span>
    </template>
    <el-table v-if="bomList.length > 0" :data="bomList" row-key="uid" size="small" border style="width: 100%">
      <el-table-column label="材料" min-width="170">
        <template #default="{ row }">
          <BomMaterialSelector
            v-if="!readonly"
            :material-id="row.materialId"
            :material-name="row.name"
            :specification="row.spec"
            :material-code="row.materialCode"
            show-material-code
            browse-on-focus
            :keep-text-on-blur="false"
            :allow-create="false"
            @select="(material) => $emit('select', row, material)"
            @clear="$emit('clear', row)"
          />
          <span v-else>{{ row.name || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="项目" min-width="150">
        <template #default="{ row }">
          <el-select
            v-if="!readonly"
            v-model="row.projectIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            filterable
            placeholder="可选多个标准工序"
            size="small"
            style="width: 100%"
            @change="(v: any) => onProjectsChange(row, v)"
          >
            <el-option
              v-for="p in projects"
              :key="p.processId"
              :label="p.processName"
              :value="p.processId"
            />
          </el-select>
          <template v-else>
            <span v-if="projectNames(row).length">{{ projectNames(row).join('、') }}</span>
            <span v-else>-</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="规格" min-width="110">
        <template #default="{ row }">
          <el-input
            v-if="!readonly"
            v-model="row.spec"
            size="small"
            placeholder="规格"
            :disabled="!!row.materialId"
            @change="$emit('update', row)"
          />
          <span v-else>{{ row.spec || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="模数" width="90">
        <template #default="{ row }">
          <el-input-number
            v-if="!readonly"
            v-model="row.moduleQty"
            :min="1"
            :precision="0"
            :controls="false"
            size="small"
            style="width: 100%"
            @change="$emit('update', row)"
          />
          <span v-else>{{ row.moduleQty ?? '待核对' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="跳距" width="120">
        <template #default="{ row }">
          <div v-if="!readonly" style="display: flex; align-items: center; gap: 4px">
            <el-input-number
              :model-value="splitNumber(row.split)"
              :min="0"
              :precision="0"
              :step="1"
              :controls="false"
              size="small"
              style="width: 78px"
              @update:model-value="(value) => { setMaterialSplit(row, value); $emit('update', row) }"
            /><span>mm</span>
          </div>
          <span v-else>{{ row.split || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="基数" width="90">
        <template #default="{ row }">
          <el-input-number
            v-if="!readonly"
            v-model="row.baseQty"
            :min="1"
            :precision="0"
            :controls="false"
            size="small"
            style="width: 100%"
            @change="$emit('update', row)"
          />
          <span v-else>{{ row.baseQty ?? '待核对' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单位" width="80">
        <template #default="{ row }">
          <el-input
            v-if="!readonly"
            v-model="row.unit"
            size="small"
            :disabled="!!row.materialId"
            @change="$emit('update', row)"
          />
          <span v-else>{{ row.unit || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="60" align="center">
        <template #header>
          <el-button size="small" plain icon="Plus" @click="$emit('add')"></el-button>
        </template>
        <template #default="{ row }">
          <el-button size="small" link type="danger" @click="$emit('remove', row)">删</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-else style="color: #999; font-size: 13px">
      暂无材料（在工序中添加材料，或点右上角 + 直接新增）
    </div>
    <div v-if="showTransfer" class="transfer-zone" data-test="transfer-zone">
      <el-button type="success" size="small" :disabled="readonly" @click="$emit('transfer')"
        >📦 资料转移（建档产品/BOM/工艺路线）</el-button
      >
      <div class="desc">
        打样确认后，把本轮工序计划+材料建档为产品/BOM/工艺路线（可预览匹配/人工调整）
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import { splitNumber, setMaterialSplit } from '../composables/sampleMaterials'
/**
 * BOM 物料清单卡（dev-20260811-008 组件化；dev-20261015-017 支持直接编辑）
 * 列：材料｜项目｜规格｜模数｜跳距｜基数｜单位；与卡片材料共用同一份草稿。
 * dev-20261018-007：新增 showTransfer（默认 true），仅控制资料转移区显示，transfer 事件不变。
 */
const props = withDefaults(
  defineProps<{
    bomList: any[]
    projects?: any[]
    readonly?: boolean
    /** 是否显示「资料转移」区；默认 true 兼容既有调用方，false 时仅隐藏、不改 transfer 事件（dev-20261018-007） */
    showTransfer?: boolean
  }>(),
  { showTransfer: true }
)

const emit = defineEmits<{
  (e: 'update', row: any): void
  (e: 'remove', row: any): void
  (e: 'add'): void
  (e: 'select', row: any, material: any): void
  (e: 'clear', row: any): void
  (e: 'transfer'): void
}>()

function projectNames(row: any): string[] {
  const ids: number[] = row.projectIds || []
  if (!ids.length) return []
  return ids.map((id) => (props.projects || []).find((p: any) => p.processId === id)?.processName || String(id))
}

function onProjectsChange(row: any, value: number[]) {
  row.projectIds = value || []
  row.projectInherited = false
  emit('update', row)
}
</script>

<style scoped>
.col-bom {
  flex: 1;
  min-width: 0;
}
.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}
.transfer-zone {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}
.transfer-zone .desc {
  display: block;
  margin-left: 0;
  margin-top: 6px;
  line-height: 1.6;
}
</style>
