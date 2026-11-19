<template>
  <div class="app-container">
    <el-form inline @submit.prevent="load">
      <el-form-item label="仓库">
        <el-select v-model="warehouseId" placeholder="请选择仓库" style="width: 220px" @change="load">
          <el-option v-for="w in warehouses" :key="w.warehouseId" :label="w.warehouseName" :value="Number(w.warehouseId)" />
        </el-select>
      </el-form-item>
      <el-form-item label="位置"><el-input v-model="keyword" clearable placeholder="名称、编码或完整路径" /></el-form-item>
      <el-form-item><el-button @click="load">刷新</el-button></el-form-item>
    </el-form>
    <div class="toolbar">
      <el-button v-hasPermi="['inventory:storage-location:add']" type="primary" :disabled="!warehouseId" @click="edit()">新增位置</el-button>
      <el-button v-hasPermi="['inventory:storage-location:add']" :disabled="!warehouseId" @click="importVisible = true">导入</el-button>
      <el-button :disabled="!warehouseId" @click="exportLocations">导出</el-button>
    </div>
    <el-alert title="位置名称与层级由你维护。库存存放在末级位置，上级按单位汇总；已有库存的位置请使用“细分并分配库存”。临时堆放也可按同样方式维护。" type="info" :closable="false" />
    <el-table v-loading="loading" :data="filtered" row-key="locationId" border default-expand-all style="margin-top: 12px" :tree-props="{ children: 'children' }" empty-text="当前仓库暂无位置">
      <el-table-column prop="locationName" label="位置名称" min-width="240" show-overflow-tooltip />
      <el-table-column prop="locationCode" label="编码" min-width="150" />
      <el-table-column label="收 / 发 / 结存" min-width="270">
        <template #default="{ row }">
          <div v-for="b in row.balances" :key="b.unit">{{ number(b.received) }} / {{ number(b.issued) }} / {{ number(b.quantity) }} {{ b.unit }}<span v-if="Number(b.reserved)">（预留 {{ number(b.reserved) }}）</span></div>
          <span v-if="!row.balances.length">暂无库存流水</span>
        </template>
      </el-table-column>
      <el-table-column label="容量管理" min-width="160">
        <template #default="{ row }"><span v-if="row.capacityEnabled">{{ number(row.usedCapacity) }} / {{ number(row.capacity) }} {{ row.capacityUnit }}</span><span v-else>未启用</span></template>
      </el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag v-bind="LocationStatusEnum.getTagProps(row.status)">{{ LocationStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" min-width="250" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['inventory:storage-location:edit']" link type="primary" @click="edit(row)">编辑</el-button>
          <el-button v-if="row.status === LocationStatusEnum.NORMAL.value" v-hasPermi="['inventory:storage-location:add']" link type="primary" :disabled="row.leaf && occupied(row) && !canSubdivide" title="有库存细分需同时具有库位编辑及移库新增、编辑、审核权限" @click="addChild(row)">{{ row.leaf && occupied(row) ? '细分并分配库存' : '新增下级' }}</el-button>
          <el-button v-hasPermi="['inventory:storage-location:edit']" link @click="toggle(row)">{{ row.status === LocationStatusEnum.NORMAL.value ? '停用' : '启用' }}</el-button>
          <el-button v-hasPermi="['inventory:storage-location:delete']" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.locationId ? '编辑位置' : '新增位置'" width="620px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" label-width="100px" :rules="rules">
        <el-form-item label="上级位置"><el-tree-select v-model="form.parentId" :data="parentOptions" node-key="locationId" :props="{ label: 'locationName', children: 'children' }" check-strictly clearable placeholder="留空为当前仓库的一级位置" style="width: 100%" /></el-form-item>
        <el-form-item label="位置名称" prop="locationName"><el-input v-model="form.locationName" maxlength="100" placeholder="名称由你决定，例如 I区、架二、顶层、格内分区或临时堆放" /></el-form-item>
        <el-form-item label="位置编码"><el-input v-model="form.locationCode" maxlength="50" placeholder="新增时留空自动生成，可自行修改" /></el-form-item>
        <el-form-item label="位置类型"><el-select v-model="form.locationType"><el-option v-for="item in LocationTypeEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="容量管理"><el-switch v-model="form.capacityEnabled" /><span class="hint">按库存记账单位限制容量</span></el-form-item>
        <template v-if="form.capacityEnabled">
          <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="0" :precision="4" /></el-form-item>
          <el-form-item label="容量单位"><el-select v-model="form.capacityUnit" placeholder="请选择库存记账单位"><el-option v-for="unit in units" :key="unit.value" :label="unit.label" :value="unit.value" /></el-select></el-form-item>
          <el-alert title="不同单位不会自动换算；上级含多种单位库存时，请关闭容量管理。" type="info" :closable="false" />
        </template>
        <el-form-item label="尺寸（cm）"><div class="dimensions"><el-input-number v-model="form.width" :min="0" placeholder="宽，可不填" /><el-input-number v-model="form.height" :min="0" placeholder="高，可不填" /><el-input-number v-model="form.depth" :min="0" placeholder="深，可不填" /></div></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio v-for="item in LocationStatusEnum.items" :key="item.value" :value="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <LocationSubdivisionDialog v-if="subdivision" :location="subdivision" @close="subdivision = undefined" @success="subdivision = undefined; load()" />
    <LocationImportDialog v-if="warehouseId" v-model:visible="importVisible" :warehouse-id="warehouseId" @success="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { warehouseApi } from '@/api/inventory/warehouse'
import { locationApi } from '@/api/inventory/location'
import { dictApi } from '@/api/system/dict'
import { hasPermi } from '@/directives'
import { LocationStatusEnum, LocationTypeEnum } from '@/enums/inventory/LocationEnum'
import type { LocationNode, StorageLocationUpdateDTO } from '@/types/inventory/location'
import type { InventoryWarehouse } from '@/types/inventory/warehouse'
import LocationImportDialog from '@/components/inventory/LocationImportDialog.vue'
import LocationSubdivisionDialog from '@/components/inventory/LocationSubdivisionDialog.vue'
defineOptions({ name: 'WarehouseLocation' })
const route = useRoute()
const warehouses = ref<InventoryWarehouse[]>([])
const warehouseId = ref<number>()
const nodes = ref<LocationNode[]>([])
const keyword = ref('')
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const importVisible = ref(false)
const subdivision = ref<LocationNode>()
const formRef = ref<FormInstance>()
const units = ref<{ value: string; label: string }[]>([])
const canSubdivide = computed(() => ['inventory:storage-location:add', 'inventory:storage-location:edit', 'inventory:transfer:add', 'inventory:transfer:edit', 'inventory:transfer:approve'].every(permission => hasPermi(permission)))
const blank = (): StorageLocationUpdateDTO => ({ locationId: undefined, warehouseId: warehouseId.value!, parentId: null, locationName: '', locationCode: '', locationType: 'normal', capacityEnabled: false, capacity: null, capacityUnit: null, width: null, height: null, depth: null, sortOrder: 0, status: LocationStatusEnum.NORMAL.value, remark: '', areaCode: '', shelfCode: '', levelCode: '', positionCode: '' })
const form = reactive(blank())
const rules = { locationName: [{ required: true, whitespace: true, message: '请填写位置名称', trigger: 'blur' }] }
const number = (value?: number | null) => value == null ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const occupied = (row: LocationNode) => row.balances.some(b => Number(b.quantity) !== 0 || Number(b.reserved) !== 0)
function filterTree(rows: LocationNode[], predicate: (node: LocationNode) => boolean): LocationNode[] {
  return rows.flatMap(row => { const children = filterTree(row.children, predicate); return predicate(row) || children.length ? [{ ...row, children }] : [] })
}
const filtered = computed(() => filterTree(nodes.value, row => !keyword.value.trim() || `${row.locationName} ${row.locationCode} ${row.path}`.toLowerCase().includes(keyword.value.trim().toLowerCase())))
const parentOptions = computed(() => {
  const prune = (rows: LocationNode[]): LocationNode[] => rows.filter(row => String(row.locationId) !== String(form.locationId) && row.status === LocationStatusEnum.NORMAL.value).map(row => ({ ...row, children: prune(row.children) }))
  return prune(nodes.value)
})
let requestId = 0
async function load() {
  const id = ++requestId
  nodes.value = []
  if (!warehouseId.value) return
  loading.value = true
  try { const response = await locationApi.tree(warehouseId.value); if (id === requestId) nodes.value = response.data || [] }
  finally { if (id === requestId) loading.value = false }
}
function edit(row?: LocationNode, parentId: number | null = null) {
  Object.assign(form, blank(), row || {}, { parentId: row?.parentId ?? parentId })
  visible.value = true
  formRef.value?.clearValidate()
}
function addChild(row: LocationNode) { if (row.leaf && occupied(row)) subdivision.value = row; else edit(undefined, row.locationId) }
async function save() {
  if (!await formRef.value?.validate()) return
  if (form.capacityEnabled && (!(Number(form.capacity) > 0) || !form.capacityUnit)) { ElMessage.warning('请填写大于0的容量并选择单位'); return }
  saving.value = true
  try {
    const payload = { ...form }
    if (payload.locationId) await locationApi.update(payload); else await locationApi.add(payload)
    visible.value = false; ElMessage.success('位置已保存'); await load()
  } finally { saving.value = false }
}
async function toggle(row: LocationNode) {
  const status = row.status === LocationStatusEnum.NORMAL.value ? LocationStatusEnum.DISABLED.value : LocationStatusEnum.NORMAL.value
  await ElMessageBox.confirm(`确认${LocationStatusEnum.getLabel(status)}“${row.path}”？存在库存或预留的位置无法停用。`, '修改位置状态')
  await locationApi.updateStatus(row.locationId, status); await load()
}
async function remove(row: LocationNode) {
  await ElMessageBox.confirm(`确认删除“${row.path}”？有下级或历史引用的位置需要保留，可在移空后停用。`, '删除位置', { type: 'warning' })
  await locationApi.delete(row.locationId); await load()
}
async function exportLocations() {
  const blob = await locationApi.export({ pageNum: 1, pageSize: 10, warehouseId: warehouseId.value })
  const url = URL.createObjectURL(new Blob([blob as unknown as BlobPart]))
  const a = document.createElement('a'); a.href = url; a.download = '库位列表.xlsx'; a.click(); URL.revokeObjectURL(url)
}
onMounted(async () => {
  warehouses.value = (await warehouseApi.getOptions()).data || []
  const requested = Number(route.query.warehouseId)
  const selected = warehouses.value.find(w => Number(w.warehouseId) === requested) || warehouses.value[0]
  warehouseId.value = selected ? Number(selected.warehouseId) : undefined
  await load()
  units.value = ((await dictApi.getItems('unit')).data || []).map(item => ({ value: item.itemValue, label: `${item.label} (${item.itemValue})` }))
})
</script>
<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.dimensions { display: flex; gap: 8px; flex-wrap: wrap; }
.dimensions :deep(.el-input-number) { width: 140px; }
.hint { margin-left: 12px; color: var(--el-text-color-secondary); }
</style>
