<template>
  <el-dialog
    :model-value="visible"
    title="🔧 工程打样工作台"
    width="1200px"
    append-to-body
    @update:model-value="onClose"
    @open="onOpen"
  >
    <div v-if="card" class="workbench-body">
      <!-- 单据信息 -->
      <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="样品单号">{{ card.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" type="warning">工程打样中</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户">{{ card.customerName }}</el-descriptions-item>
        <el-descriptions-item label="轮次">Round {{ card.sampleRound || 1 }}</el-descriptions-item>
        <el-descriptions-item label="当前工序">{{ card.currentProcess || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接单人">{{ card.engineeringAcceptor || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 接单/拒单 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">接单</span></template>
        <template v-if="!card.engineeringAcceptor">
          <el-button type="primary" @click="handleAccept" :loading="saving">✅ 工程接单</el-button>
          <el-button type="danger" plain style="margin-left:8px" @click="handleReject">✋ 工程拒单</el-button>
        </template>
        <template v-else>
          <el-tag type="success">已接单：{{ card.engineeringAcceptor }}</el-tag>
          <span style="margin-left:12px;color:#909399;font-size:12px">接单后开始记录打样过程</span>
        </template>
      </el-card>

      <!-- 工艺参数 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">工艺参数 / 工程备注</span></template>
        <el-input v-model="form.note" type="textarea" :rows="4"
          placeholder="填写工艺参数/材料规格/丝印要求/模切尺寸等"
          maxlength="2000" show-word-limit />
        <el-button type="primary" size="small" style="margin-top:8px" @click="saveNote" :loading="saving">💾 保存工艺参数</el-button>
      </el-card>

      <!-- 工序 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">工序进度</span></template>
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px">
          <el-select v-model="form.process" placeholder="选择当前工序" style="width:220px">
            <el-option v-for="p in processOptions" :key="p" :label="p" :value="p" />
          </el-select>
          <el-button type="primary" size="small" @click="saveProcess" :loading="saving">💾 保存工序</el-button>
          <span v-if="card.currentProcess" style="color:#909399;font-size:12px">当前：{{ card.currentProcess }}</span>
        </div>
        <el-timeline v-if="processList.length > 0" style="padding-left:2px">
          <el-timeline-item v-for="(p, i) in processList" :key="p.processId" :timestamp="formatTime(p.startTime)" placement="top" :type="i === processList.length - 1 ? 'primary' : 'info'">
            <div style="font-size:13px">
              <span style="font-weight:600">{{ p.processName }}</span>
              <span v-if="p.operator" style="margin-left:8px;color:#909399;font-size:12px">操作人：{{ p.operator }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
        <div v-else style="color:#999;font-size:13px;padding:8px 0">当前没有工序历史记录，选择工序后自动记录</div>
      </el-card>

      <!-- 成本/工时 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">成本 / 工时</span></template>
        <div style="display:flex;align-items:center;gap:8px">
          <el-input-number v-model="form.cost" :min="0" :precision="2" :controls="false" placeholder="成本" style="width:120px" />
          <span style="color:#909399">元</span>
          <el-input-number v-model="form.workHours" :min="0" :precision="1" :controls="false" placeholder="工时" style="width:120px" />
          <span style="color:#909399">小时</span>
          <el-button type="primary" size="small" @click="saveCost" :loading="saving">保存</el-button>
        </div>
        <div v-if="card.sampleCost" style="margin-top:8px;color:#606266;font-size:13px">已录：¥{{ card.sampleCost }} / {{ card.sampleWorkHours || 0 }}h</div>
      </el-card>

      <!-- 图纸上传 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">图纸 / 工艺文件</span></template>
        <el-upload ref="engUploadRef" :http-request="engUploadFile" :on-remove="engRemoveFile"
          :file-list="engFileList" :before-upload="engBeforeUpload"
          list-type="text" multiple>
          <el-button type="primary" size="small">📤 上传图纸/文件</el-button>
          <template #tip>
            <div class="el-upload__tip" style="font-size:12px;color:#999;margin-top:6px">
              菲林图 / 丝印图 / 模切图 / 规格书（PDF/DWG/DXF/图片/Word，单文件≤10MB）
            </div>
          </template>
        </el-upload>
        <el-divider />
        <div v-if="engFileList.length > 0" style="margin-top:4px">
          <div v-for="f in engFileList" :key="f.uid || f.name" style="padding:5px 0;display:flex;align-items:center;gap:8px;border-bottom:1px solid #f0f0f0">
            <el-link v-if="f.url" :href="f.url" target="_blank" type="primary" underline="never">📎 {{ f.name }}</el-link>
            <span v-else>{{ f.name }} <el-tag size="small" type="warning">待上传</el-tag></span>
          </div>
        </div>
        <div v-else style="color:#999;font-size:13px;padding:8px 0">暂无工程文件，请上传图纸或工艺文件</div>
      </el-card>

      <!-- 物料清单 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">🧾 打样物料清单（BOM）</span></template>
        <el-table :data="bomList" size="small" border style="width:100%">
          <el-table-column label="层结构" width="90" align="center">
            <template #default="{ row }">
              <el-select v-model="row.layerName" size="small" style="width:80px">
                <el-option v-for="l in bomLayerOptions" :key="l" :label="l" :value="l" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.materialName" size="small" placeholder="如PET面板膜" />
            </template>
          </el-table-column>
          <el-table-column label="规格" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.specification" size="small" placeholder="如0.25mm" />
            </template>
          </el-table-column>
          <el-table-column label="用量" width="105">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="4" size="small" style="width:85px" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="78" align="center">
            <template #default="{ row }">
              <el-input v-model="row.unit" size="small" placeholder="PCS" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index, row }">
              <el-button type="danger" size="small" link @click="removeBomRow($index, row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top:8px;display:flex;align-items:center;gap:8px">
          <el-button type="primary" size="small" @click="addBomRow">＋ 添加物料</el-button>
          <el-button type="success" size="small" @click="saveBomList" :loading="saving">💾 保存物料清单</el-button>
        </div>
      </el-card>

      <!-- 标记完成 -->
      <div style="text-align:center;margin-top:8px">
        <el-button type="success" size="large" @click="handleMarkReady" :loading="saving" style="width:200px">🎯 标记样品完成（送样）</el-button>
      </div>
    </div>
    <template #footer>
      <el-button @click="onClose(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps, UploadRawFile } from 'element-plus'
import request from '@/utils/request'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { useUserStore } from '@/store/modules/user'

const props = defineProps<{
  visible: boolean
  card: any
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const saving = ref(false)
const form = reactive({ note: '', process: '', cost: 0, workHours: 0 })
const processList = ref<any[]>([])
const bomList = ref<any[]>([])
const processOptions = ['印刷', '冲切', '贴合', 'SMT贴片', '装配', '测试', '包装']
const bomLayerOptions = ['面板', '线路', '间隔', '背胶', '连接器', '其他']
const engUploadRef = ref()
const engFileList = ref<any[]>([])

function onClose(val: boolean) {
  if (!val) emit('update:visible', false)
}

async function onOpen() {
  if (!props.card?.orderId) return
  await Promise.all([loadProcesses(), loadBom(), loadEngFiles()])
  form.note = props.card.engineeringNote || ''
  form.process = props.card.currentProcess || ''
  form.cost = props.card.sampleCost || 0
  form.workHours = props.card.sampleWorkHours || 0
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

// 接单
async function handleAccept() {
  if (!props.card?.orderId) return
  try {
    await ElMessageBox.confirm('确认接单开始打样？', '工程接单', { confirmButtonText: '确认接单', cancelButtonText: '取消', type: 'info' })
    const userStore = useUserStore()
    const name = userStore.nickName || '工程'
    await sampleOrderApi.acceptEngineering(props.card.orderId, name)
    ElMessage.success('接单成功')
    emit('saved')
    await refreshCard()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '接单失败')
  }
}

// 拒单
async function handleReject() {
  if (!props.card?.orderId) return
  try {
    const { value } = await ElMessageBox.prompt('请填写拒单原因', '工程拒单', {
      confirmButtonText: '确认拒单', cancelButtonText: '取消', type: 'warning',
      inputPlaceholder: '拒单原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '拒单原因不能为空'),
    })
    await sampleOrderApi.rejectEngineering(props.card.orderId, value.trim())
    ElMessage.success('已拒单，退回待审核')
    emit('saved')
    onClose(false)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '拒单失败')
  }
}

// 保存工艺参数
async function saveNote() {
  if (!props.card?.orderId) return
  saving.value = true
  try {
    await sampleOrderApi.startEngineering(props.card.orderId, form.note)
    props.card.engineeringNote = form.note
    ElMessage.success('工艺参数已保存')
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 保存工序（选择后点保存才生效）
async function saveProcess() {
  if (!props.card?.orderId) return
  if (!form.process) {
    ElMessage.warning('请先选择工序')
    return
  }
  saving.value = true
  try {
    await sampleOrderApi.updateProcess(props.card.orderId, form.process)
    props.card.currentProcess = form.process
    await loadProcesses()
    ElMessage.success(`已保存为：${form.process}`)
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存工序失败')
  } finally {
    saving.value = false
  }
}

// 成本
async function saveCost() {
  if (!props.card?.orderId) return
  saving.value = true
  try {
    await sampleOrderApi.recordCost(props.card.orderId, form.cost, form.workHours)
    props.card.sampleCost = form.cost
    props.card.sampleWorkHours = form.workHours
    ElMessage.success('成本/工时已保存')
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 图纸
const engBeforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件不能超过10MB')
    return false
  }
  return true
}
async function engUploadFile(options: any) {
  if (!props.card?.orderId) return
  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('bizType', 'sample')
  fd.append('bizId', String(props.card.orderId))
  if (props.card?.traceId) {
    fd.append('traceId', props.card.traceId)
  }
  try {
    const res = await request.post('/system/attachment/upload', fd)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('上传成功')
      await loadEngFiles()
      emit('saved')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  }
}
async function engRemoveFile(file: any) {
  if (file.id) {
    try {
      await request.delete(`/system/attachment/${file.id}`)
    } catch { /* ignore */ }
  }
  engFileList.value = engFileList.value.filter(f => f.uid !== file.uid)
}
async function loadEngFiles() {
  if (!props.card?.orderId) return
  try {
    const res = await request.get(`/system/attachment/list?bizType=sample&bizId=${props.card.orderId}`)
    engFileList.value = (res.data || []).map((a: any) => ({
      uid: a.id, name: a.fileName, url: a.filePath, id: a.id,
    }))
  } catch {
    engFileList.value = []
  }
}

// 物料清单
function addBomRow() {
  bomList.value.push({ layerName: '面板', materialName: '', specification: '', quantity: 1, unit: 'PCS' })
}
async function removeBomRow(index: number, row: any) {
  if (row.bomId) {
    try { await sampleOrderApi.deleteBomItem(row.bomId) } catch (e: any) { ElMessage.error(e?.message || '删除失败'); return }
  }
  bomList.value.splice(index, 1)
}
async function saveBomList() {
  if (!props.card?.orderId) return
  const valid = bomList.value.filter(i => i.materialName && i.materialName.trim())
  if (valid.length === 0) { ElMessage.warning('请至少填写一条物料名称'); return }
  saving.value = true
  try {
    const res = await sampleOrderApi.saveBom(props.card.orderId, valid)
    bomList.value = res.data || []
    ElMessage.success(`已保存 ${bomList.value.length} 条物料`)
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 标记完成
async function handleMarkReady() {
  if (!props.card?.orderId) return
  try {
    await ElMessageBox.confirm('确认样品制作完成？将进入待送样状态', '标记完成', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'success',
    })
    await sampleOrderApi.markReady(props.card.orderId)
    ElMessage.success('已标记完成，待送样')
    emit('saved')
    onClose(false)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}

// 数据加载
async function loadProcesses() {
  if (!props.card?.orderId) return
  try { const res = await sampleOrderApi.listProcesses(props.card.orderId); processList.value = res.data || [] } catch { processList.value = [] }
}
async function loadBom() {
  if (!props.card?.orderId) return
  try { const res = await sampleOrderApi.listBom(props.card.orderId); bomList.value = res.data || [] } catch { bomList.value = [] }
}
async function refreshCard() {
  if (!props.card?.orderId) return
  try { const res = await sampleOrderApi.getInfo(props.card.orderId); Object.assign(props.card, res.data) } catch { /* ignore */ }
}

watch(() => props.visible, (v) => { if (v) onOpen() })
</script>

<style scoped>
.workbench-body {
  height: 800px;
  overflow-y: auto;
  padding-right: 8px;
}

/* el-dialog 固定高度 800px（弹框整体） */
:deep(.el-dialog) {
  height: 800px;
  display: flex;
  flex-direction: column;
}
:deep(.el-dialog__body) {
  flex: 1;
  overflow: hidden;
}
</style>
