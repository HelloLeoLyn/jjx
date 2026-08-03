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

      <!-- 轮次切换（DEV-500） -->
      <el-tabs v-model="activeRound" style="margin-bottom:8px">
        <el-tab-pane
          v-for="r in roundList"
          :key="r.roundNo"
          :name="String(r.roundNo)"
          :label="`Round ${r.roundNo}${r.roundNo === (card.sampleRound || 1) ? '（当前）' : ''}`"
        />
      </el-tabs>

      <div v-if="isCurrentRound">
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
          <el-select v-model="form.process" placeholder="选择工序" style="width:170px">
            <el-option v-for="p in processOptions" :key="p" :label="p" :value="p" />
          </el-select>
          <el-input-number v-model="form.duration" :min="0" :precision="0" :controls="false" placeholder="耗时(分)" style="width:100px" />
          <span style="color:#909399;font-size:12px">分钟</span>
          <el-button type="primary" size="small" @click="saveProcess" :loading="saving">💾 保存工序</el-button>
          <span v-if="card.currentProcess" style="color:#909399;font-size:12px">当前：{{ card.currentProcess }}</span>
        </div>
        <!-- 该工序材料（工序单元：材料+工艺） -->
        <div style="margin-bottom:8px">
          <div style="font-size:12px;color:#909399;margin-bottom:4px">🧾 本工序材料</div>
          <div v-for="(m, idx) in form.materials" :key="idx" style="display:flex;gap:6px;margin-bottom:6px;align-items:center;flex-wrap:wrap">
            <el-select
              v-model="m.materialId"
              filterable
              remote
              :remote-method="(q: string) => searchMaterials(q, m)"
              :loading="m.loading"
              placeholder="搜索物料档案（必选）"
              style="width: 190px"
              @change="(v: any) => onMaterialSelected(m, v)"
            >
              <el-option v-for="opt in m.options" :key="opt.materialId" :label="`${opt.materialName}${opt.specification ? ' ' + opt.specification : ''} (${opt.materialCode || ''})`" :value="opt.materialId" />
            </el-select>
            <el-input v-model="m.spec" placeholder="规格" style="width:100px" :disabled="!!m.materialId" />
            <el-input-number v-model="m.qty" :min="0" :precision="4" :controls="false" placeholder="用量" style="width:90px" />
            <el-input v-model="m.unit" placeholder="单位" style="width:60px" :disabled="!!m.materialId" />
            <el-button type="primary" size="small" link @click="openMaterialCreate(m)">新建物料</el-button>
            <el-button type="danger" size="small" link @click="form.materials.splice(idx, 1)">删</el-button>
          </div>
          <el-button type="primary" size="small" plain icon="Plus" @click="addMaterialRow">添加材料</el-button>
          <span style="margin-left:8px;color:#909399;font-size:12px">材料必须从物料档案选择；档案中没有的先「新建物料」建档</span>
        </div>
        <!-- 工艺说明 -->
        <div style="margin-bottom:8px">
          <div style="font-size:12px;color:#909399;margin-bottom:4px">🔧 工艺说明（怎么做的）</div>
          <el-input v-model="form.processNote" type="textarea" :rows="2" placeholder="如：丝印机200目网版，刮刀压力3kg，室温干燥30分钟" />
        </div>
        <el-timeline v-if="processList.length > 0" style="padding-left:2px">
          <el-timeline-item v-for="(p, i) in processList" :key="p.processId" :timestamp="formatTime(p.startTime)" placement="top" :type="i === processList.length - 1 ? 'primary' : 'info'">
            <div style="font-size:13px">
              <span style="font-weight:600">{{ p.processName }}</span>
              <span v-if="p.durationMinutes" style="margin-left:8px;color:#606266;font-size:12px">⏱ {{ p.durationMinutes }}分钟</span>
              <span v-if="p.operator" style="margin-left:8px;color:#909399;font-size:12px">操作人：{{ p.operator }}</span>
              <div v-if="p.processNote" style="color:#606266;font-size:12px;margin-top:2px">🔧 {{ p.processNote }}</div>
              <div v-if="p.materials" style="margin-top:2px">
                <el-tag v-for="(m, mi) in parseMaterials(p.materials)" :key="mi" size="small" type="info" style="margin-right:4px">{{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}</el-tag>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <div v-else style="color:#999;font-size:13px;padding:8px 0">当前没有工序历史记录，录入工序后自动记录</div>
      </el-card>

<!-- 打样汇总（自动计算） -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">📊 打样汇总（自动）</span></template>
        <div style="display:flex;gap:24px;flex-wrap:wrap">
          <div style="text-align:center">
            <div style="font-size:22px;font-weight:700;color:#409eff">{{ summary.totalHours ?? '-' }}</div>
            <div style="font-size:12px;color:#909399;margin-top:2px">总工时(小时)</div>
          </div>
          <div style="text-align:center">
            <div style="font-size:22px;font-weight:700;color:#67c23a">¥{{ summary.materialCost ?? '-' }}</div>
            <div style="font-size:12px;color:#909399;margin-top:2px">材料成本(估算)</div>
          </div>
          <div style="text-align:center">
            <div style="font-size:22px;font-weight:700;color:#606266">{{ summary.processCount ?? '-' }}</div>
            <div style="font-size:12px;color:#909399;margin-top:2px">工序数</div>
          </div>
          <div style="text-align:center">
            <div style="font-size:22px;font-weight:700;color:#e6a23c">{{ summary.materialCount ?? '-' }}</div>
            <div style="font-size:12px;color:#909399;margin-top:2px">材料种类</div>
          </div>
        </div>
        <div style="font-size:12px;color:#999;margin-top:8px">由工序单元自动汇总：总工时=各工序耗时之和，材料成本=材料用量×标准单价</div>
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

      <!-- 物料汇总（从工序单元材料自动聚合，只读） -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">🧾 全单材料汇总</span></template>
        <el-table v-if="bomList.length > 0" :data="bomList" size="small" border style="width:100%">
          <el-table-column prop="process" label="工序" width="90" />
          <el-table-column prop="name" label="材料" min-width="140" />
          <el-table-column prop="spec" label="规格" min-width="120" />
          <el-table-column prop="qty" label="用量" width="90" />
          <el-table-column prop="unit" label="单位" width="70" />
        </el-table>
        <div v-else style="color:#999;font-size:13px;padding:8px 0">暂无材料（在工序录入中添加材料后自动汇总）</div>
      </el-card>

      <!-- 标记完成 -->
      <div style="text-align:center;margin-top:8px">
        <el-button type="success" size="large" @click="handleMarkReady" :loading="saving" style="width:200px">🎯 标记样品完成（送样）</el-button>
      </div>
      </div>

      <!-- 历史轮次（只读，DEV-500） -->
      <div v-else class="round-readonly">
        <el-alert
          type="info" :closable="false" show-icon style="margin-bottom:12px"
          title="历史轮次（只读）"
          description="该轮次已归档，如需调整请在当前轮次重新打样"
        />
        <div v-if="activeRoundData" style="margin-bottom:12px">
          <el-tag :type="activeRoundData.result === 'confirmed' ? 'success' : activeRoundData.result === 'rejected' ? 'danger' : 'info'">
            {{ activeRoundData.result === 'confirmed' ? '✅ 已确认' : activeRoundData.result === 'rejected' ? '⛔ 已退回' : '🔄 进行中' }}
          </el-tag>
          <span v-if="activeRoundData.rejectReason" style="margin-left:8px;color:#f56c6c;font-size:13px">
            退回原因：{{ activeRoundData.rejectReason }}
          </span>
          <span v-if="activeRoundData.engineeringNote" style="margin-left:12px;color:#606266;font-size:13px">
            工艺参数：{{ activeRoundData.engineeringNote }}
          </span>
        </div>
        <el-card shadow="never" style="margin-bottom:16px">
          <template #header><span style="font-weight:600">📜 工序快照</span></template>
          <el-timeline v-if="activeRoundProcesses.length" style="padding-left:2px">
            <el-timeline-item v-for="(p, i) in activeRoundProcesses" :key="i" :timestamp="formatTime(p.startTime)" placement="top" :type="i === activeRoundProcesses.length - 1 ? 'primary' : 'info'">
              <div style="font-size:13px">
                <span style="font-weight:600">{{ p.processName }}</span>
                <span v-if="p.durationMinutes" style="margin-left:8px;color:#606266;font-size:12px">⏱ {{ p.durationMinutes }}分钟</span>
                <span v-if="p.operator" style="margin-left:8px;color:#909399;font-size:12px">操作人：{{ p.operator }}</span>
                <div v-if="p.processNote" style="color:#606266;font-size:12px;margin-top:2px">🔧 {{ p.processNote }}</div>
                <div v-if="p.materials" style="margin-top:2px">
                  <el-tag v-for="(m, mi) in parseMaterials(p.materials)" :key="mi" size="small" type="info" style="margin-right:4px">{{ m.name }}{{ m.spec ? ' ' + m.spec : '' }}{{ m.qty ? ' ×' + m.qty : '' }}</el-tag>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <div v-else style="color:#999;font-size:13px">该轮次无工序快照</div>
        </el-card>
        <el-card shadow="never">
          <template #header><span style="font-weight:600">🧾 BOM 物料快照</span></template>
          <el-table v-if="activeRoundBom.length" :data="activeRoundBom" size="small" border style="width:100%">
            <el-table-column prop="process" label="工序" width="90" />
            <el-table-column prop="name" label="材料" min-width="140" />
            <el-table-column prop="spec" label="规格" min-width="120" />
            <el-table-column prop="qty" label="用量" width="90" />
            <el-table-column prop="unit" label="单位" width="70" />
          </el-table>
          <div v-else style="color:#999;font-size:13px">该轮次无物料快照</div>
        </el-card>
      </div>
    </div>
    <template #footer>
      <el-button @click="onClose(false)">关闭</el-button>
    </template>
    <!-- 物料建档弹窗（DEV-526：材料必须建档后选择） -->
    <MaterialFormDialog
      v-model="materialCreateVisible"
      :preset-data="materialPreset"
      @success="onMaterialCreated"
    />

  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps, UploadRawFile } from 'element-plus'
import request from '@/utils/request'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { materialApi } from '@/api/inventory/material'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
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
const form = reactive({
  note: '', process: '',
  materials: [] as any[], processNote: '', duration: undefined as number | undefined,
})
// 添加材料行（DEV-526：从物料档案选择，materialId 记录）
function addMaterialRow() {
  form.materials.push({ name: '', spec: '', qty: 1, unit: 'PCS', materialId: undefined as number | undefined, materialCode: '', options: [], loading: false })
}

// 远程搜索物料档案
async function searchMaterials(query: string, m: any) {
  if (!query || query.trim().length < 1) {
    m.options = []
    return
  }
  m.loading = true
  try {
    const res: any = await materialApi.search({ materialName: query.trim(), pageNum: 1, pageSize: 10 })
    m.options = res?.data?.records || res?.data || []
  } catch {
    m.options = []
  } finally {
    m.loading = false
  }
}

// 选中物料 → 自动填名称/规格/单位
function onMaterialSelected(m: any, materialId: number) {
  const mat = (m.options || []).find((o: any) => o.materialId === materialId)
  if (!mat) return
  m.name = mat.materialName
  m.spec = mat.specification || ''
  m.unit = mat.unit || 'PCS'
  m.materialCode = mat.materialCode || ''
}

// 建档弹窗
const materialCreateVisible = ref(false)
const materialPreset = ref<any>({})
function openMaterialCreate(m: any) {
  materialPreset.value = { materialName: m.name || '', specification: m.spec || '', unit: m.unit || 'PCS' }
  materialCreateVisible.value = true
}

// 建档成功 → 选中新建物料
function onMaterialCreated(mat: any) {
  const row = form.materials[form.materials.length - 1] || form.materials[0]
  if (row) {
    row.materialId = mat.materialId
    row.materialCode = mat.materialCode || ''
    row.name = mat.materialName
    row.spec = mat.specification || ''
    row.unit = mat.unit || 'PCS'
    row.options = [mat]
  }
}
// 解析材料JSON
function parseMaterials(json?: string) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
// ===== 轮次展示（DEV-500）=====
const roundList = ref<any[]>([])
const activeRound = ref('')
const isCurrentRound = computed(() => Number(activeRound.value) === (props.card?.sampleRound || 1))
const activeRoundData = computed(() => roundList.value.find((r) => String(r.roundNo) === activeRound.value) || null)
const activeRoundProcesses = computed(() => {
  const d = activeRoundData.value
  if (!d?.processSnapshot) return []
  try {
    const arr = JSON.parse(d.processSnapshot)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})
const activeRoundBom = computed(() => {
  const d = activeRoundData.value
  if (!d?.bomSnapshot) return []
  try {
    const arr = JSON.parse(d.bomSnapshot)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})
async function loadRounds() {
  if (!props.card?.orderId) return
  try {
    const res = await sampleOrderApi.getRounds(props.card.orderId)
    const rounds: any[] = res.data || []
    const current = props.card?.sampleRound || 1
    // 当前轮可能还没归档快照，补充占位
    if (!rounds.some((r) => r.roundNo === current)) {
      rounds.push({ roundNo: current, result: 'pending' })
    }
    roundList.value = rounds.sort((a, b) => a.roundNo - b.roundNo)
    activeRound.value = String(current)
  } catch {
    roundList.value = [{ roundNo: props.card?.sampleRound || 1, result: 'pending' }]
    activeRound.value = String(props.card?.sampleRound || 1)
  }
}
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
  await Promise.all([loadRounds(), loadProcesses(), loadBom(), loadEngFiles(), loadSummary()])
  form.note = props.card.engineeringNote || ''
  form.process = props.card.currentProcess || ''
  form.materials = []
  form.processNote = ''
  form.duration = undefined
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
    // 材料 → JSON
    const validMats = form.materials.filter((m: any) => m.name && m.name.trim())
    const materialsJson = validMats.length > 0 ? JSON.stringify(validMats) : null
    await sampleOrderApi.updateProcess(
      props.card.orderId,
      form.process,
      materialsJson,
      form.processNote || undefined,
      form.duration,
    )
    props.card.currentProcess = form.process
    await loadProcesses()
    await loadSummary()
    ElMessage.success(`已保存工序：${form.process}${validMats.length ? `（${validMats.length}种材料）` : ''}`)
    // 清空表单便于录下一道
    form.materials = []
    form.processNote = ''
    form.duration = undefined
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存工序失败')
  } finally {
    saving.value = false
  }
}

// 打样汇总
const summary = ref<any>({})
async function loadSummary() {
  if (!props.card?.orderId) return
  try {
    const res = await request.get(`/sales/sample-order/summary/${props.card.orderId}`)
    summary.value = res.data || {}
  } catch {
    summary.value = {}
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
  // DEV-500：当前轮次工序（历史轮走快照展示）
  try {
    const res = await sampleOrderApi.listProcesses(props.card.orderId, props.card?.sampleRound || undefined)
    processList.value = res.data || []
  } catch {
    processList.value = []
  }
}
async function loadBom() {
  // 从工序单元材料聚合（不再用 sales_sample_bom 表）
  if (!props.card?.orderId) return
  try {
    const res = await sampleOrderApi.listProcesses(props.card.orderId)
    const procs = res.data || []
    const agg: any[] = []
    for (const p of procs) {
      if (!p.materials) continue
      try {
        const mats = JSON.parse(p.materials)
        for (const m of mats) {
          agg.push({ process: p.processName, name: m.name, spec: m.spec, qty: m.qty, unit: m.unit })
        }
      } catch { /* ignore */ }
    }
    bomList.value = agg
  } catch { bomList.value = [] }
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
