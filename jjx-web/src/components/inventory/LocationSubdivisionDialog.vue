<template>
  <el-dialog :model-value="true" title="细分并分配库存" width="90%" :close-on-click-modal="false" @close="emit('close')">
    <p>原位置：{{ location.path }}。保存后成为上级位置，原有库存按下面的方案移入新建的末级位置。</p>
    <el-alert title="每批库存必须全部分配，物理卷只能整卷移动；有预留的批次须先处理占用。提交成功后生成移库凭证和收发流水，保留原批次及来源。" type="info" :closable="false" />
    <div v-for="(child, index) in children" :key="child.key" class="child">
      <span>新位置 {{ index + 1 }}</span>
      <el-input v-model="child.locationName" maxlength="100" placeholder="位置名称，由你决定" />
      <el-input v-model="child.locationCode" maxlength="50" placeholder="编码留空自动生成" />
      <el-button :disabled="children.length === 1" @click="children.splice(index, 1)">移除</el-button>
    </div>
    <el-button :disabled="children.length >= 50" @click="addChild">添加新位置</el-button>
    <el-table v-loading="loading" :data="paged" border style="margin-top: 12px" max-height="430">
      <el-table-column label="物料 / 批次" min-width="200"><template #default="{ row }">{{ row.material_code }} {{ row.material_name }}<br />{{ row.batch_no }}<span v-if="row.roll_no"> / 卷 {{ row.roll_no }}</span></template></el-table-column>
      <el-table-column label="现有库存" width="140"><template #default="{ row }">{{ row.quantity }} {{ row.unit }}<br /><span v-if="Number(row.reserved_quantity)" class="error">预留 {{ row.reserved_quantity }}</span></template></el-table-column>
      <el-table-column v-for="(child, index) in children" :key="child.key" :label="child.locationName || `新位置 ${index + 1}`" min-width="170">
        <template #default="{ row }">
          <el-input-number v-if="!row.roll_no" v-model="amounts[key(row, child)]" :min="0" :max="Number(row.quantity)" :precision="4" :disabled="!!Number(row.reserved_quantity) || saving" controls-position="right" style="width: 145px" />
          <span v-else>{{ amounts[key(row, child)] || 0 }} {{ row.unit }}</span>
          <el-button link type="primary" :disabled="!!Number(row.reserved_quantity) || saving" @click="moveAll(row, child)">整批移至此处</el-button>
        </template>
      </el-table-column>
      <el-table-column label="分配合计" width="150" fixed="right"><template #default="{ row }"><span :class="{ error: !balanced(row) }">{{ assigned(row) }} / {{ row.quantity }} {{ row.unit }}</span></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="10" :total="batches.length" layout="total, prev, pager, next" style="margin-top: 12px" />
    <p v-if="invalid" class="error">{{ invalid }}</p>
    <el-checkbox v-model="confirmed" :disabled="saving">我已核对全部 {{ batches.length }} 批库存及分配方案，确认立即细分并移库</el-checkbox>
    <template #footer><el-button :disabled="saving" @click="emit('close')">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || !!invalid || !confirmed" @click="submit">确认细分并移库</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { locationApi } from '@/api/inventory/location'
import type { LocationBatch, LocationNode } from '@/types/inventory/location'
const props = defineProps<{ location: LocationNode }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'success'): void }>()
let nextKey = 0
const children = ref<{ key: number; locationName: string; locationCode: string }[]>([])
function addChild() { children.value.push({ key: ++nextKey, locationName: '', locationCode: '' }) }
addChild(); addChild()
const batches = ref<LocationBatch[]>([])
const amounts = reactive<Record<string, number | undefined>>({})
const loading = ref(true)
const saving = ref(false)
const confirmed = ref(false)
const loadError = ref(false)
watch([children, amounts], () => { confirmed.value = false }, { deep: true })
const page = ref(1)
const paged = computed(() => batches.value.slice((page.value - 1) * 10, page.value * 10))
const key = (row: LocationBatch, child: { key: number }) => `${row.item_id}:${child.key}`
const assigned = (row: LocationBatch) => Math.round(children.value.reduce((sum, child) => sum + Number(amounts[key(row, child)] || 0), 0) * 10000) / 10000
const balanced = (row: LocationBatch) => Math.abs(assigned(row) - Number(row.quantity)) < 0.00001
function moveAll(row: LocationBatch, child: { key: number }) {
  children.value.forEach(c => { amounts[key(row, c)] = c.key === child.key ? Number(row.quantity) : 0 })
}
const invalid = computed(() => {
  if (loadError.value) return '库存读取失败，请关闭窗口后重试'
  if (children.value.some(child => !child.locationName.trim())) return '请填写每个新位置的名称'
  if (batches.value.some(row => Number(row.reserved_quantity) !== 0)) return '存在预留库存，请处理占用后重新打开此窗口'
  if (batches.value.some(row => !balanced(row))) return '仍有批次未全部分配，请逐批核对'
  return ''
})
async function submit() {
  if (invalid.value || !confirmed.value) return
  saving.value = true
  try {
    const allocations = batches.value.flatMap(row => children.value.flatMap((child, childIndex) => {
      const quantity = Number(amounts[key(row, child)] || 0)
      return quantity > 0 ? [{ stockItemId: row.item_id, childIndex, quantity }] : []
    }))
    const result = await locationApi.subdivide(props.location.locationId, { children: children.value.map(({ locationName, locationCode }) => ({ locationName, locationCode })), allocations, confirmed: true })
    ElMessage.success(result.data?.transferNo ? `已细分并移库，凭证 ${result.data.transferNo}` : '位置已细分')
    emit('success')
  } finally { saving.value = false }
}
onMounted(async () => { try { batches.value = (await locationApi.inventory(props.location.locationId)).data || [] } catch { loadError.value = true } finally { loading.value = false } })
</script>
<style scoped>
.child { display: flex; align-items: center; gap: 12px; margin: 12px 0; }
.child span { white-space: nowrap; }
.child .el-input { max-width: 300px; }
.error { color: var(--el-color-danger); }
</style>
