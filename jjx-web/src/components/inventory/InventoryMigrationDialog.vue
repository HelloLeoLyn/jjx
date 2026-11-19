<template>
  <el-dialog :model-value="modelValue" title="库存迁移数据维护" width="94%" destroy-on-close @update:model-value="emit('update:modelValue', $event)" @open="loadBatches">
    <el-alert title="选择迁移批次维护资料；成本按下表所示库存单位填写。缺失成本与零成本分别保留。" type="info" :closable="false" />
    <div class="migration-toolbar">
      <el-select v-model="batchId" placeholder="选择迁移批次" style="width: 390px" @change="loadRows">
        <el-option v-for="batch in batches" :key="batch.inboundId" :value="batch.inboundId" :label="`${batch.businessDate} · ${batch.batchCode}`" />
      </el-select>
      <el-select v-model="problem" style="width: 170px"><el-option label="成本缺失" value="missingCost" /><el-option label="全部成本记录" value="allCosts" /><el-option label="资料待核查" value="sourceIssues" /></el-select>
      <el-input v-model="keyword" clearable placeholder="按品名、编码或源行搜索" style="width: 240px" />
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </div>
    <p v-if="selectedBatch">盘点业务日期：{{ selectedBatch.businessDate }}；实际导入时间：{{ selectedBatch.importedAt }}。原记录 {{ selectedBatch.rows }} 行。</p>
    <el-table v-loading="loading" :data="pageRows" border max-height="440" @selection-change="selected = $event">
      <el-table-column v-if="problem !== 'sourceIssues'" type="selection" width="45" />
      <el-table-column prop="sourceRow" label="源行" width="75" />
      <el-table-column prop="materialCode" label="物料编码" width="125" />
      <el-table-column prop="materialName" label="品名" min-width="210" />
      <el-table-column prop="specification" label="规格" min-width="150" />
      <el-table-column prop="initialQuantity" label="迁移数量" width="100" />
      <el-table-column prop="receivedQuantity" label="累计收入" width="100" />
      <el-table-column prop="issuedQuantity" label="累计发出" width="100" />
      <el-table-column prop="remainingQuantity" label="当前结存" width="100" />
      <el-table-column prop="unit" label="库存单位" width="90" />
      <el-table-column label="单位成本" width="110"><template #default="{ row }">{{ row.unitCost == null ? '待补' : row.unitCost }}</template></el-table-column>
      <el-table-column label="成本记录" width="100"><template #default="{ row }"><el-button link type="primary" @click="loadHistory(row)">查看记录</el-button></template></el-table-column>
      <el-table-column v-if="problem === 'sourceIssues'" label="原始资料及核查事项" min-width="300"><template #default="{ row }"><div>{{ sourceOriginal(row.issues) }}</div><div>{{ sourceIssues(row.issues) }}</div></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="50" :total="filteredRows.length" layout="total, prev, pager, next" />
    <div v-if="problem !== 'sourceIssues'" class="migration-toolbar">
      <el-input-number v-model="cost" :min="0" :precision="4" :controls="false" placeholder="单位成本" />
      <el-input v-model="reason" maxlength="120" placeholder="补录依据，例如采购历史价格或客户确认" style="width: 370px" />
      <el-button v-hasPermi="['inventory:migration:cost']" type="primary" :loading="saving" :disabled="!selected.length || cost == null || !reason.trim()" @click="saveCosts">维护选中 {{ selected.length }} 行成本</el-button>
    </div>
    <p v-else>当前展示原始核查资料；请在物料档案维护已确认内容，修改数量通过盘点调整处理。</p>
    <el-divider />
    <div v-hasPermi="['inventory:migration:import']"><div v-hasPermi="['inventory:material:add']" class="migration-toolbar">
      <el-upload :auto-upload="false" :show-file-list="false" accept=".json" :on-change="readManifest"><el-button>选择迁移数据文件</el-button></el-upload>
      <span>{{ manifestName }}</span>
      <el-button :disabled="!manifest" :loading="checking" @click="checkImport">预检</el-button>
      <el-button type="primary" :disabled="!check || check.errors.length > 0 || check.alreadyImported" :loading="importing" @click="executeImport">导入当前批次</el-button>
    </div></div>
    <el-alert v-if="check" :type="check.errors.length ? 'error' : 'success'" :closable="false" :title="check.alreadyImported ? '此批次已导入，无需重复导入' : `预检 ${check.rows} 行，${check.zeroRows} 行零库存；${check.errors.length} 项错误`" />
    <div v-if="check?.errors.length" class="import-errors"><p v-for="error in check.errors.slice(0, 30)" :key="error">{{ error }}</p></div>
    <el-dialog v-model="historyVisible" title="成本维护记录" width="900px" append-to-body>
      <el-table v-loading="historyLoading" :data="history" border max-height="450">
        <el-table-column prop="time" label="时间" width="180" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column label="原单位成本" width="110"><template #default="{ row }">{{ row.beforeCost == null ? '未知' : row.beforeCost }}</template></el-table-column>
        <el-table-column prop="afterCost" label="维护后成本" width="110" />
        <el-table-column prop="amountDelta" label="金额差额" width="110" />
        <el-table-column prop="referenceNo" label="对应单据" min-width="160" />
        <el-table-column prop="reason" label="维护依据" min-width="180" />
      </el-table>
    </el-dialog>
    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { migrationApi, type MigrationBatch, type MigrationRow, type MigrationCheck, type MigrationCostAudit } from '@/api/inventory/migration'
defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const batches = ref<MigrationBatch[]>([]), rows = ref<MigrationRow[]>([]), selected = ref<MigrationRow[]>([])
const batchId = ref<number>(), problem = ref('missingCost'), keyword = ref(''), page = ref(1)
const loading = ref(false), saving = ref(false), checking = ref(false), importing = ref(false)
const cost = ref<number>(), reason = ref(''), manifest = ref<unknown>(), manifestName = ref(''), check = ref<MigrationCheck>()
const historyVisible = ref(false), historyLoading = ref(false), history = ref<MigrationCostAudit[]>([])
let historyRequest = 0
let requestId = 0
const selectedBatch = computed(() => batches.value.find(b => b.inboundId === batchId.value))
const filteredRows = computed(() => rows.value.filter(row => (problem.value !== 'missingCost' || row.unitCost == null) && (problem.value !== 'sourceIssues' || sourceIssues(row.issues).length > 0) && `${row.sourceRow} ${row.materialCode} ${row.materialName}`.includes(keyword.value)))
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * 50, page.value * 50))
watch([problem, keyword, batchId], () => { page.value = 1; selected.value = [] })
function sourceIssues(value: string) {
  try { const data = JSON.parse(value); return Array.isArray(data.issues) ? data.issues.join('；') : value } catch { return value }
}
function sourceOriginal(value: string) {
  try { const data = JSON.parse(value); return `原品名：${data.name || ''}；原规格：${data.spec || ''}` } catch { return '' }
}
async function loadBatches() {
  try { const response = await migrationApi.batches(); batches.value = response.data || []; if (!batches.value.some(b => b.inboundId === batchId.value)) batchId.value = batches.value[0]?.inboundId; await loadRows() } catch { ElMessage.error('迁移批次加载失败') }
}
async function loadRows() {
  const id = ++requestId; selected.value = []; rows.value = []; loading.value = true
  try { if (batchId.value != null) { const response = await migrationApi.rows(batchId.value); if (id === requestId) rows.value = response.data || [] } } catch { if (id === requestId) ElMessage.error('迁移明细加载失败') } finally { if (id === requestId) loading.value = false }
}
async function refresh() { await loadBatches() }
async function loadHistory(row: MigrationRow) {
  if (batchId.value == null) return
  const id = ++historyRequest; history.value = []; historyVisible.value = true; historyLoading.value = true
  try { const response = await migrationApi.history(batchId.value, row.lineId); if (id === historyRequest) history.value = response.data || [] }
  catch { if (id === historyRequest) ElMessage.error('成本记录加载失败') }
  finally { if (id === historyRequest) historyLoading.value = false }
}
async function saveCosts() {
  if (batchId.value == null || cost.value == null || !selected.value.length || saving.value || !reason.value.trim()) return
  const units = new Set(selected.value.map(r => r.unit))
  if (units.size !== 1) { ElMessage.warning('请按相同库存单位分批维护成本'); return }
  const requestedBatch = batchId.value
  const changes = selected.value.map(row => ({ lineId: row.lineId, expectedVersion: row.costVersion, unitCost: cost.value!, reason: reason.value.trim() }))
  try {
    await ElMessageBox.confirm(`按每${[...units][0]} ${cost.value} 元维护 ${changes.length} 行？库存数量保持原值。`, '确认成本维护')
    saving.value = true
    const response = await migrationApi.costs(requestedBatch, changes)
    if (!response.data) throw new Error('成本维护未返回结果')
    ElMessage.success(`已维护 ${response.data.changedRows} 行成本`); emit('changed'); await refresh()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error('成本维护失败，请刷新核对后重试') } finally { saving.value = false }
}
async function readManifest(file: UploadFile) {
  check.value = undefined; manifest.value = undefined; manifestName.value = ''
  if (!file.raw || file.raw.size > 10 * 1024 * 1024) { ElMessage.error('请选择不超过10MB的迁移JSON文件'); return }
  try { manifest.value = JSON.parse(await file.raw.text()); manifestName.value = file.name } catch { ElMessage.error('迁移文件无法解析') }
}
async function checkImport() {
  if (!manifest.value || checking.value) return
  checking.value = true; check.value = undefined
  try { check.value = (await migrationApi.preview(manifest.value)).data ?? undefined; if (!check.value) throw new Error('预检未返回结果') } catch { ElMessage.error('预检失败，请核对迁移文件') } finally { checking.value = false }
}
async function executeImport() {
  if (!manifest.value || !check.value || check.value.errors.length || check.value.alreadyImported || importing.value) return
  try {
    await ElMessageBox.confirm(`按当前迁移文件导入 ${check.value.rows} 行库存？`, '确认迁移')
    importing.value = true
    const result = (await migrationApi.import(manifest.value)).data
    if (!result) throw new Error('迁移未返回结果')
    ElMessage.success(result.alreadyImported ? '批次已导入，本次未重复入账' : `已导入 ${result.rows} 行`)
    check.value = undefined; emit('changed'); await refresh()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error('迁移未完成，请刷新预检结果核对') } finally { importing.value = false }
}
</script>
<style scoped>
.migration-toolbar { display: flex; gap: 12px; align-items: center; margin: 16px 0; flex-wrap: wrap; }
.import-errors { max-height: 160px; overflow: auto; color: var(--el-color-danger); }
</style>
