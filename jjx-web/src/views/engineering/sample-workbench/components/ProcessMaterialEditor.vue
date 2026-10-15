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
                <el-select
                  v-model="row.materialId"
                  filterable
                  remote
                  :remote-method="(q: string) => onSearchMaterial(q, row)"
                  :loading="row.loading"
                  :popper-class="`material-popper-${row.uid}`"
                  placeholder="搜索物料档案"
                  style="width: 100%"
                  @change="(v: any) => $emit('material-selected', row, v)"
                  @visible-change="(v: boolean) => $emit('select-visible', row, v)"
                >
                  <el-option
                    v-for="opt in row.options"
                    :key="opt.materialId"
                    :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`"
                    :value="opt.materialId"
                  />
                  <el-option
                    v-if="row.loading"
                    value="__loading__"
                    disabled
                    style="text-align: center; color: #909399; font-size: 12px"
                    >加载中…</el-option
                  >
                  <el-option
                    v-else-if="row.options.length && row.total > row.options.length"
                    value="__more__"
                    disabled
                    style="text-align: center; color: #909399; font-size: 12px"
                    >下拉加载更多（还有 {{ row.total - row.options.length }} 条）</el-option
                  >
                  <el-option
                    v-else-if="row.options.length"
                    value="__all__"
                    disabled
                    style="text-align: center; color: #c0c4cc; font-size: 12px"
                    >已加载全部（共 {{ row.total }} 条）</el-option
                  >
                  <template #footer>
                    <el-button
                      v-if="matShowCreate(row)"
                      link
                      type="primary"
                      @click="matCreate(pc, row)"
                      >新建物料「{{ row._matKw }}」</el-button
                    >
                  </template>
                </el-select>
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
          <el-table-column label="用量" width="90">
            <template #default="{ row }">
              <el-input-number
                v-if="pc.editing"
                v-model="row.qty"
                :min="0"
                :precision="4"
                :controls="false"
                size="small"
                style="width: 100%"
              />
              <span v-else>{{ row.qty }}</span>
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
defineProps<{
  pc: any
  readonly?: boolean
  parseMaterials: (json?: string | null) => any[]
}>()
const emit = defineEmits<{
  (e: 'search-material', query: string, row: any): void
  (e: 'material-selected', row: any, value: any): void
  (e: 'select-visible', row: any, visible: boolean): void
  (e: 'add-material-row'): void
  (e: 'remove-material', index: number): void
  (e: 'create-material', row: any): void
}>()
function onSearchMaterial(query: string, row: any) {
  row._matKw = query || ''
  emit('search-material', query, row)
}
function matShowCreate(row: any): boolean {
  const keyword = (row._matKw || '').trim()
  return !!keyword && !row.loading && !(row.options || []).some((o: any) => (o.materialName || '').trim() === keyword)
}
function matCreate(_pc: any, row: any) {
  row.name = (row._matKw || '').trim()
  emit('create-material', row)
}
</script>
<style scoped>
.pc-row { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 8px; }
.pc-mat { flex: 1; min-width: 0; }
</style>
