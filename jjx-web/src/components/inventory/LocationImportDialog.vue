<template>
  <el-dialog :model-value="visible" title="导入库位" width="85%" :close-on-click-modal="false" @update:model-value="emit('update:visible', $event)">
    <el-alert title="名称按原文保留，编码留空自动生成。不会自动拆分层级、推断区域或合并重名数据；尺寸可不填，导入后默认关闭容量管理。存在错误时整批不保存。" type="info" :closable="false" />
    <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="readFile" style="margin: 12px 0">
      <el-button :loading="reading">选择 Excel</el-button>
      <template #tip>支持“库位名称”“位置”“摆放/区域”等名称列，导入前可逐行修改。</template>
    </el-upload>
    <el-button link type="primary" @click="downloadTemplate">下载导入模板</el-button>
    <el-table :data="rows" max-height="450" border>
      <el-table-column type="index" width="55" />
      <el-table-column label="库位名称" min-width="220"><template #default="{ row }"><el-input v-model="row.locationName" maxlength="100" /></template></el-table-column>
      <el-table-column label="编码（可留空）" min-width="180"><template #default="{ row }"><el-input v-model="row.locationCode" maxlength="50" /></template></el-table-column>
      <el-table-column v-for="field in dimensions" :key="field.key" :label="field.label" width="155"><template #default="{ row }"><el-input-number v-model="row[field.key]" :min="0" :precision="2" style="width: 130px" /></template></el-table-column>
      <el-table-column label="备注" min-width="160"><template #default="{ row }"><el-input v-model="row.remark" maxlength="500" /></template></el-table-column>
      <el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" @click="rows.splice($index, 1)">移除</el-button></template></el-table-column>
    </el-table>
    <template #footer><span style="margin-right: 12px">共 {{ rows.length }} 条</span><el-button @click="emit('update:visible', false)">取消</el-button><el-button type="primary" :loading="saving" :disabled="!rows.length" @click="save">导入当前仓库</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import * as XLSX from 'xlsx'
import { locationApi } from '@/api/inventory/location'
const props = defineProps<{ visible: boolean; warehouseId: number }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'success'): void }>()
interface Row { locationName: string; locationCode: string; width?: number; height?: number; depth?: number; remark: string }
const rows = ref<Row[]>([])
const reading = ref(false)
const saving = ref(false)
const dimensions = [{ key: 'width', label: '宽度（cm）' }, { key: 'height', label: '高度（cm）' }, { key: 'depth', label: '深度（cm）' }] as const
watch(() => props.visible, value => { if (value) rows.value = [] })
function numeric(value: unknown): number | undefined { if (value == null || String(value).trim() === '') return undefined; const n = Number(value); if (!Number.isFinite(n) || n < 0) throw new Error('尺寸必须是非负数或留空'); return n }
async function readFile(file: UploadFile) {
  if (!file.raw) return
  reading.value = true
  rows.value = []
  try {
    const book = XLSX.read(await file.raw.arrayBuffer(), { type: 'array' })
    const data = XLSX.utils.sheet_to_json<Record<string, unknown>>(book.Sheets[book.SheetNames[0]], { defval: '' })
    if (data.length > 1000) throw new Error('每次最多导入1000条')
    const nameKeys = ['库位名称', '位置名称', '位置', '摆放/区域', '摆放 / 区域', '摆放区域', '仓库名称', '名称']
    const keys = data.length ? Object.keys(data[0]) : []
    const nameKey = nameKeys.find(key => keys.includes(key))
    if (!nameKey) throw new Error('未找到名称列，请使用导入模板或将表头设为“库位名称”')
    rows.value = data.map(row => ({ locationName: String(row[nameKey] ?? ''), locationCode: String(row['库位编码'] || ''), width: numeric(row['宽度'] ?? row['宽度(cm)']), height: numeric(row['高度'] ?? row['高度(cm)']), depth: numeric(row['深度'] ?? row['深度(cm)']), remark: String(row['备注'] || '') }))
    ElMessage.success(`已读取 ${rows.value.length} 条，请核对后导入`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '读取 Excel 失败') }
  finally { reading.value = false }
}
async function save() {
  if (rows.value.some(row => !row.locationName.trim())) { ElMessage.warning('有记录未填写名称，请填写或移除该行'); return }
  saving.value = true
  try {
    const data = rows.value.map((row, index) => ({ ...row, locationType: 'normal', sortOrder: String(index + 1), width: row.width == null ? '' : String(row.width), height: row.height == null ? '' : String(row.height), depth: row.depth == null ? '' : String(row.depth) }))
    ElMessage.success((await locationApi.importLocation(data, props.warehouseId)).data || '导入完成')
    emit('update:visible', false); emit('success')
  } finally { saving.value = false }
}
async function downloadTemplate() {
  const data = await locationApi.downloadImportTemplate()
  const url = URL.createObjectURL(new Blob([data as unknown as BlobPart]))
  const a = document.createElement('a'); a.href = url; a.download = '库位导入模板.xlsx'; a.click(); URL.revokeObjectURL(url)
}
</script>
