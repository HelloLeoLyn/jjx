<template>
    <!-- 行3：材料表格 -->
    <div class="pc-row">
      <div class="pc-mat">
        <el-table
          v-if="(pc.editing ? pc.materialRows : parseMaterials(pc.materials)).length"
          :data="pc.editing ? pc.materialRows : parseMaterials(pc.materials)"
          size="small"
          border
          style="width: 100%"
        >
          <el-table-column label="材料" min-width="150">
            <template #default="{ row }">
              <template v-if="pc.editing">
                <!-- dev-20261018-035：复用公共可编辑搜索框；输入文字与已选材料身份分离 -->
                <div class="mat-picker" @focusin="$emit('select-visible', row, true)">
                  <EditableSearchSelect
                    :model-value="row.materialId"
                    :display-value="displayOf(row)"
                    :options="row.options || []"
                    option-key="materialId"
                    :label-of="materialLabel"
                    :loading="!!row.loading"
                    :loading-more="!!row.loadingMore"
                    :total="row.total || 0"
                    :show-create="matShowCreate(row)"
                    :create-label="`新建物料「${keywordOf(row)}」`"
                    placeholder="搜索物料档案；选中后可继续改文字再搜"
                    size="small"
                    @search="(q: string) => onSearchMaterial(q, row)"
                    @select="(item: any) => $emit('material-selected', row, item)"
                    @clear="$emit('material-cleared', row)"
                    @load-more="$emit('load-more-material', row)"
                    @create="(kw: string) => matCreate(row, kw)"
                  >
                    <template #option="{ item }">{{ materialLabel(item) }}</template>
                    <template #footer>
                      <div v-if="row.searchFailed" class="mat-failed">
                        查询失败，<el-button link type="primary" size="small" @click="retry(row)">重试</el-button>
                      </div>
                    </template>
                  </EditableSearchSelect>
                </div>
              </template>
              <template v-else>{{ row.name }}</template>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="110">
            <template #default="{ row }">
              <el-input
                v-if="pc.editing"
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
              <el-input-number v-if="pc.editing" v-model="row.moduleQty" :min="1" :precision="0" :controls="false" size="small" style="width:100%" />
              <span v-else>{{ normalized(row).moduleQty ?? '待核对' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="跳距" width="120">
            <template #default="{ row }">
              <div v-if="pc.editing && !readonly" style="display: flex; align-items: center; gap: 4px">
                <el-input-number
                  :model-value="splitNumber(row.split)"
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
          <el-table-column label="基数" width="90">
            <template #default="{ row }">
              <el-input-number
                v-if="pc.editing"
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
          <el-table-column label="单位" width="70">
            <template #default="{ row }">
              <el-input
                v-if="pc.editing"
                v-model="row.unit"
                size="small"
                :disabled="!!row.materialId"
              />
              <span v-else>{{ row.unit || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!readonly && pc.editing" label="操作" width="60" align="center">
            <template #header>
              <el-button
                size="small"
                plain
                icon="Plus"
                @click="$emit('add-material-row')"
              ></el-button>
            </template>
            <template #default="{ $index }">
              <el-button size="small" link type="danger" @click="$emit('remove-material', $index)"
                >删</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!readonly && pc.editing" style="margin-top: 6px; display: flex; gap: 6px"></div>
        <span
          v-else-if="!parseMaterials(pc.materials).length"
          style="color: #c0c4cc; font-size: 12px"
          >无材料</span
        >
      </div>
    </div>
</template>
<script setup lang="ts">
import { normalizeMaterial as normalized, splitNumber, setMaterialSplit } from '../composables/sampleMaterials'
import EditableSearchSelect from '@/components/Selector/EditableSearchSelect.vue'
defineProps<{
  pc: any
  readonly?: boolean
  parseMaterials: (json?: string | null) => any[]
}>()
const emit = defineEmits<{
  (e: 'search-material', query: string, row: any): void
  (e: 'material-selected', row: any, value: any): void
  (e: 'material-cleared', row: any): void
  (e: 'load-more-material', row: any): void
  (e: 'select-visible', row: any, visible: boolean): void
  (e: 'add-material-row'): void
  (e: 'remove-material', index: number): void
  (e: 'create-material', row: any): void
}>()

/** 候选与已选行共用的显示文案：名称 + 规格 + 编码；历史行没有编码时不留空括号 */
function materialLabel(m: any): string {
  const name = String(m?.materialName ?? m?.name ?? '').trim()
  const spec = String(m?.specification ?? m?.spec ?? '').trim()
  const code = String(m?.materialCode ?? '').trim()
  return [name, spec].filter(Boolean).join(' ') + (code ? ` (${code})` : '')
}
/** 输入框显示值只取已选材料的档案快照；未选材料时留空，避免把自由文本当成已选 */
function displayOf(row: any): string {
  if (!row?.materialId) return ''
  return materialLabel(row)
}
function keywordOf(row: any): string {
  return String(row?._matKw || '').trim()
}
function onSearchMaterial(query: string, row: any) {
  row._matKw = query || ''
  emit('search-material', query, row)
}
function retry(row: any) {
  emit('search-material', row.lastQuery || '', row)
}
function matShowCreate(row: any): boolean {
  const keyword = keywordOf(row)
  if (row.searchFailed) return false // 查询失败时不给“新建”，避免把失败当零结果
  return !!keyword && !row.loading && !(row.options || []).some((o: any) => (o.materialName || '').trim() === keyword)
}
function matCreate(row: any, keyword: string) {
  row._matKw = String(keyword || '').trim()
  emit('create-material', row)
}
</script>
<style scoped>
.pc-row { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 8px; }
.pc-mat { flex: 1; min-width: 0; }
.mat-picker { width: 100%; }
.mat-failed { padding: 6px 10px; font-size: 12px; color: var(--el-color-danger); }
</style>
