<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model="queryParams.customerName" placeholder="请输入客户名称" clearable style="width: 200px" @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="样品状态" prop="sampleStatus">
          <el-select v-model="queryParams.sampleStatus" placeholder="请选择状态" clearable style="width: 200px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getList">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="operation-card" shadow="never">
      <el-button type="primary" plain icon="Plus" @click="showCreateDialog">新增样品单</el-button>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="sampleList" style="width: 100%">
        <el-table-column label="样品单号" prop="orderNo" width="180" />
        <el-table-column label="客户" prop="customerName" width="160" />
        <el-table-column label="样品状态" width="130">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.sampleStatus)" size="small">
              {{ statusLabel(scope.row.sampleStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="迭代轮次" width="90" align="center">
          <template #default="scope">Round {{ scope.row.sampleRound || 1 }}</template>
        </el-table-column>
        <el-table-column label="打样数量" width="90" align="center" prop="sampleQty" />
        <el-table-column label="快递单号" prop="sampleTrackingNo" min-width="140" />
        <el-table-column label="送样日期" width="110" prop="sampleSendDate" />
        <el-table-column label="确认人" prop="sampleClientName" width="100" />
        <el-table-column label="工程备注" min-width="160">
          <template #default="scope">
            <el-tooltip :content="scope.row.engineeringNote || '-'" placement="top">
              <span>{{ scope.row.engineeringNote ? scope.row.engineeringNote.substring(0, 20) + (scope.row.engineeringNote.length > 20 ? '...' : '') : '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="showDetail(scope.row)">详情</el-button>

            <template v-if="scope.row.sampleStatus === 1">
              <el-button link type="primary" size="small" @click="handleSubmitReview(scope.row)">提交审核</el-button>
            </template>
            <template v-else-if="scope.row.sampleStatus === 2">
              <el-button link type="success" size="small" @click="handleApprove(scope.row)">通过</el-button>
              <el-button link type="danger" size="small" @click="handleRejectReview(scope.row)">驳回</el-button>
            </template>
            <template v-else-if="scope.row.sampleStatus === 3">
              <el-button link type="primary" size="small" @click="handleMarkReady(scope.row)">样品完成</el-button>
            </template>
            <template v-else-if="scope.row.sampleStatus === 4">
              <el-button link type="primary" size="small" @click="handleSendSample(scope.row)">送样登记</el-button>
            </template>
            <template v-else-if="scope.row.sampleStatus === 5">
              <el-button link type="success" size="small" @click="handleConfirm(scope.row)">客户确认OK</el-button>
              <el-button link type="warning" size="small" @click="handleRejectSample(scope.row)">退回修改</el-button>
            </template>
            <template v-else-if="scope.row.sampleStatus === 6">
              <el-button link type="primary" size="small" @click="handleConvert(scope.row)">转量产</el-button>
            </template>
            <template v-else-if="scope.row.sampleStatus === 7">
              <el-tag size="small" type="success">已转量产</el-tag>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ===== 创建样品单弹窗 ===== -->
    <el-dialog title="新增样品单" v-model="createVisible" width="600px" append-to-body @close="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="120px">
        <el-form-item label="来源报价单" prop="quotationNo">
          <el-select v-model="createForm.quotationId" placeholder="请选择已确认的报价单" filterable clearable style="width:100%">
            <el-option v-for="q in quotationOptions" :key="q.quotationId" :label="`${q.quotationNo} - ${q.customerName} (${q.finalAmount}元)`" :value="q.quotationId" />
          </el-select>
        </el-form-item>
        <el-form-item label="打样数量" prop="sampleQty">
          <el-input-number v-model="createForm.sampleQty" :min="1" :max="1000" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="createForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- ===== 详情弹窗（含工程区） ===== -->
    <el-dialog title="样品单详情" v-model="detailVisible" width="820px" append-to-body @open="onDetailOpen">
      <template v-if="detailData">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="样品单号" :span="2">{{ detailData.orderNo }}</el-descriptions-item>
              <el-descriptions-item label="客户名称">{{ detailData.customerName }}</el-descriptions-item>
              <el-descriptions-item label="联系人">{{ detailData.contactPerson || '-' }}</el-descriptions-item>
              <el-descriptions-item label="样品状态">
                <el-tag :type="statusTagType(detailData.sampleStatus)" size="small">{{ statusLabel(detailData.sampleStatus) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="迭代轮次">Round {{ detailData.sampleRound || 1 }}</el-descriptions-item>
              <el-descriptions-item label="打样数量">{{ detailData.sampleQty || '-' }}</el-descriptions-item>
              <el-descriptions-item label="送样日期">{{ detailData.sampleSendDate || '-' }}</el-descriptions-item>
              <el-descriptions-item label="快递单号">{{ detailData.sampleTrackingNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="客户确认日期">{{ detailData.sampleConfirmDate || '-' }}</el-descriptions-item>
              <el-descriptions-item label="客户确认人">{{ detailData.sampleClientName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="转量产订单ID">{{ detailData.convertedOrderId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="转量产时间">{{ detailData.convertOrderTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
            </el-descriptions>

            <!-- 相关文档 -->
            <el-divider content-position="left">相关文档</el-divider>
            <AttachmentPanel
              v-if="detailData?.orderId"
              biz-type="sample"
              :biz-id="detailData.orderId"
            />
          </el-tab-pane>

          <!-- 🔧 工程区 -->
          <el-tab-pane label="🔧 工程区" name="engineering">
            <!-- 工程备注 / 工艺参数 -->
            <el-card shadow="never" style="margin-bottom:16px">
              <template #header><span style="font-weight:600">工程备注 / 工艺参数</span></template>
              <el-form label-width="100px">
                <el-form-item label="工艺备注">
                  <el-input v-model="engineeringForm.note" type="textarea" :rows="4"
                    :disabled="!isEngineeringStatus"
                    placeholder="填写工艺参数/材料规格/丝印要求/模切尺寸等"
                    maxlength="2000" show-word-limit />
                </el-form-item>
                <el-row v-if="isEngineeringStatus" :gutter="20">
                  <el-col>
                    <el-button type="primary" size="small" @click="saveEngineeringNote" :loading="savingEng">💾 保存工艺参数</el-button>
                    <el-button type="success" size="small" style="margin-left:12px" @click="handleDetailMarkReady">🎯 标记样品完成</el-button>
                  </el-col>
                </el-row>
              </el-form>
            </el-card>

            <!-- 图纸 / 工艺文件 -->
            <el-card shadow="never" style="margin-bottom:16px">
              <template #header><span style="font-weight:600">图纸 / 工艺文件</span></template>
              <el-upload ref="engUploadRef" :http-request="engUploadFile" :on-remove="engRemoveFile"
                :file-list="engFileList" :before-upload="engBeforeUpload"
                :disabled="!isEditableStatus" list-type="text" multiple>
                <el-button type="primary" size="small" :disabled="!isEditableStatus">📤 上传图纸/文件</el-button>
                <template #tip>
                  <div class="el-upload__tip" style="font-size:12px;color:#999;margin-top:6px">
                    菲林图 / 丝印图 / 模切图 / 规格书（PDF/DWG/DXF/图片/Word，单文件≤10MB）<br>
                    <span v-if="!isEditableStatus" style="color:#e6a23c">💡 工程打样中和送样阶段可上传文件</span>
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

            <!-- 退回记录（仅退回状态显示） -->
            <el-card shadow="never" v-if="detailData.sampleStatus === 9">
              <template #header><span style="font-weight:600;color:#e6a23c">🔄 客户退回记录</span></template>
              <div style="color:#666;font-size:13px;margin-bottom:8px">退回原因：{{ detailData.remark || '-' }}</div>
              <div style="color:#666;font-size:13px;margin-bottom:12px">当前轮次：Round {{ detailData.sampleRound || 1 }}</div>
              <el-button type="primary" size="small" @click="handleDetailRestartEngineering">🔄 重新开始打样</el-button>
            </el-card>
          </el-tab-pane>

          <!-- 迭代记录 -->
          <el-tab-pane label="📋 迭代记录" name="history">
            <el-card shadow="never" style="margin-bottom:16px">
              <template #header><span style="font-weight:600">样品迭代历程</span></template>
              <el-timeline>
                <el-timeline-item
                  v-for="(item, idx) in iterationHistory"
                  :key="idx"
                  :timestamp="item.time"
                  :type="item.type"
                  placement="top"
                >
                  <div style="font-weight:500">{{ item.action }}</div>
                  <div v-if="item.detail" style="color:#666;font-size:13px;margin-top:4px">{{ item.detail }}</div>
                </el-timeline-item>
              </el-timeline>
              <div v-if="iterationHistory.length === 0" style="color:#999;text-align:center;padding:20px">暂无迭代记录</div>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, UploadProps, UploadRawFile } from 'element-plus'
import request from '@/utils/request'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleOrderStatusEnum } from '@/enums/sales'

defineOptions({ name: 'SalesSampleOrder' })

// ==================== 数据 ====================
const loading = ref(false)
const creating = ref(false)
const sampleList = ref<any[]>([])
const statusOptions = ref<Array<{ value: number; label: string; description: string; terminal: boolean }>>([])
const quotationOptions = ref<any[]>([])

const queryParams = reactive({
  customerName: '',
  sampleStatus: undefined as number | undefined,
})

const createVisible = ref(false)
const detailVisible = ref(false)
const detailTab = ref('basic')
const detailData = ref<any>(null)

// ==================== 工程区数据 ====================
const engUploadRef = ref()
const engFileList = ref<any[]>([])
const engineeringForm = reactive({ note: '' })
const savingEng = ref(false)

// 工程区操作权限
const isEngineeringStatus = computed(() => detailData.value?.sampleStatus === 3)
const isRejectedStatus = computed(() => detailData.value?.sampleStatus === 9)
const isEditableStatus = computed(() => [3, 4, 5].includes(detailData.value?.sampleStatus))

// 迭代记录（基于现有字段动态生成）
const iterationHistory = computed(() => {
  const d = detailData.value
  if (!d) return []
  const history: Array<{ time: string; action: string; detail: string | null; type: string }> = []

  // 创建
  history.push({
    time: d.createTime || d.inquiryDate || '',
    action: `样品单创建（Round 1）`,
    detail: `样品单号: ${d.orderNo || ''}，打样数量: ${d.sampleQty || ''}`,
    type: 'primary',
  })

  // 审核
  if (d.sampleStatus >= 2) {
    history.push({ time: '', action: '提交审核', detail: null, type: 'info' })
  }
  if (d.sampleStatus >= 3) {
    history.push({ time: '', action: '审核通过，进入工程打样', detail: d.engineeringNote || null, type: 'success' })
  }
  if (d.sampleStatus >= 4) {
    history.push({ time: '', action: '样品完成，待送样', detail: d.sampleQty ? `打样数量: ${d.sampleQty}` : null, type: 'info' })
  }
  if (d.sampleStatus >= 5 && d.sampleSendDate) {
    history.push({ time: d.sampleSendDate, action: '已送样待客户确认', detail: d.sampleTrackingNo ? `快递单号: ${d.sampleTrackingNo}` : null, type: 'warning' })
  }

  // 退回记录（多轮迭代）
  if (d.sampleStatus === 9 || (d.sampleRound && d.sampleRound > 1)) {
    for (let r = 0; r < (d.sampleRound || 1); r++) {
      const roundNum = r + 1
      if (r > 0) {
        history.push({
          time: d.sampleConfirmDate || '',
          action: `Round ${roundNum} 客户退回要求修改`,
          detail: d.remark || '客户要求修改',
          type: 'danger',
        })
        history.push({
          time: '',
          action: `Round ${roundNum + 1} 重新打样`,
          detail: null,
          type: 'warning',
        })
      }
    }
  }

  // 客户确认
  if (d.sampleStatus === 6 && d.sampleConfirmDate) {
    history.push({ time: d.sampleConfirmDate, action: '✅ 客户确认样品OK', detail: d.sampleClientName ? `确认人: ${d.sampleClientName}` : null, type: 'success' })
  }

  // 转量产
  if (d.sampleStatus === 7 && d.convertOrderTime) {
    history.push({ time: d.convertOrderTime, action: '📦 已转量产', detail: d.convertedOrderId ? `标准订单ID: ${d.convertedOrderId}` : null, type: 'success' })
  }

  return history
})

// ==================== 创建表单 ====================
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  quotationId: undefined as number | undefined,
  sampleQty: 10,
  remark: '',
})
const createRules = {
  quotationId: [{ required: true, message: '请选择报价单', trigger: 'change' }],
  sampleQty: [{ required: true, message: '请输入打样数量', trigger: 'blur' }],
}

// ==================== 状态映射 ====================
// 使用统一枚举（对应后端 SampleOrderStatusEnum）
function statusLabel(status: number): string {
  const label = SampleOrderStatusEnum.getLabel(status)
  return label && label !== '未知' ? label : `未知(${status})`
}
function statusTagType(status: number): string {
  return (SampleOrderStatusEnum.getTagProps(status).type as string) || 'info'
}

// ==================== 接口 ====================
async function getList() {
  loading.value = true
  try {
    const res = await sampleOrderApi.list({ sampleStatus: queryParams.sampleStatus })
    sampleList.value = res.data || []
  } catch { sampleList.value = [] }
  finally { loading.value = false }
}

function resetQuery() {
  queryParams.customerName = ''
  queryParams.sampleStatus = undefined
  getList()
}

async function loadQuotationOptions() {
  try {
    const res = await request({
      url: '/sales/quotation/list', method: 'get',
      params: { pageNum: 1, pageSize: 50, quotationStatus: 'accepted' },
    })
    quotationOptions.value = res.data?.records || []
  } catch { quotationOptions.value = [] }
}

// ==================== 创建样品单 ====================
function showCreateDialog() {
  createVisible.value = true
  createForm.quotationId = undefined
  createForm.sampleQty = 10
  createForm.remark = ''
  loadQuotationOptions()
}
function resetCreateForm() { createFormRef.value?.resetFields() }

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const res = await sampleOrderApi.createFromQuotation(createForm.quotationId!, {
      sampleQty: createForm.sampleQty, remark: createForm.remark,
    })
    ElMessage.success(`样品单创建成功: ${res.data.orderNo}`)
    createVisible.value = false
    getList()
  } catch (e: any) { ElMessage.error(e.message || '创建失败') }
  finally { creating.value = false }
}

// ==================== 详情 / 工程区 ====================
async function showDetail(row: any) {
  detailData.value = row
  detailTab.value = 'basic'
  detailVisible.value = true

  // 初始化工程表单
  engineeringForm.note = row.engineeringNote || ''
  // 加载工程附件
  await loadEngFiles(row.orderId)
}

function onDetailOpen() {
  // 每次打开再次加载最新数据
  if (detailData.value?.orderId) {
    sampleOrderApi.getInfo(detailData.value.orderId).then(res => {
      detailData.value = res.data
      engineeringForm.note = res.data.engineeringNote || ''
    }).catch(() => {})
  }
}

// 工程附件
async function loadEngFiles(bizId: number) {
  try {
    const res: any = await request({
      url: '/system/attachment/list', method: 'get',
      params: { bizType: 'sample_order', bizId },
    })
    engFileList.value = res?.code === 200
      ? (res.data || []).map((a: any) => ({
          name: a.fileName,
          url: `/system/attachment/download/${a.id}`,
          response: a.id,
          status: 'success',
        }))
      : []
  } catch { engFileList.value = [] }
}

// 上传类型校验
function engBeforeUpload(file: UploadRawFile) {
  const maxSize = 10 * 1024 * 1024
  const allowed = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.jpg', '.jpeg', '.png', '.dwg', '.dxf', '.zip']
  const ext = '.' + (file.name.split('.').pop()?.toLowerCase() || '')
  if (!allowed.includes(ext)) { ElMessage.error('不支持的文件格式'); return false }
  if (file.size > maxSize) { ElMessage.error('文件大小不能超过10MB'); return false }
  return true
}

// 上传文件
const engUploadFile: UploadProps['httpRequest'] = async (options) => {
  const orderId = detailData.value?.orderId
  if (!orderId) { options.onError(new Error('无订单ID')); return }

  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('bizType', 'sample_order')
  fd.append('bizId', String(orderId))

  try {
    const res: any = await request({
      url: '/system/attachment/upload', method: 'post',
      data: fd, headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (res?.code === 200) {
      options.onSuccess(res.data)
      // 刷新文件列表
      await loadEngFiles(orderId)
    } else {
      options.onError(new Error(res?.msg || '上传失败'))
    }
  } catch (e: any) { options.onError(e) }
}

// 删除文件
async function engRemoveFile(file: any) {
  if (!file.response) return
  try {
    await request({ url: '/system/attachment/' + file.response, method: 'delete' })
    await loadEngFiles(detailData.value?.orderId)
  } catch { /* 静默 */ }
}

// 保存工程备注
async function saveEngineeringNote() {
  if (!detailData.value?.orderId) return
  savingEng.value = true
  try {
    await sampleOrderApi.startEngineering(detailData.value.orderId, engineeringForm.note)
    ElMessage.success('工艺参数已保存')
    detailData.value.engineeringNote = engineeringForm.note
  } catch { ElMessage.error('保存失败') }
  finally { savingEng.value = false }
}

// 标记样品完成（在工程区内操作）
async function handleDetailMarkReady() {
  if (!detailData.value?.orderId) return
  const { value } = await ElMessageBox.prompt('实际打样数量', '标记样品完成', {
    inputValue: String(detailData.value.sampleQty || 10),
    confirmButtonText: '样品完成',
  })
  const qty = parseInt(value || '0')
  if (qty <= 0) { ElMessage.warning('请输入有效数量'); return }
  await sampleOrderApi.markReady(detailData.value.orderId, qty)
  ElMessage.success('样品已完成，待送样')
  detailVisible.value = false
  getList()
}

// 退回后重新打样（在工程区内操作）
async function handleDetailRestartEngineering() {
  if (!detailData.value?.orderId) return
  await ElMessageBox.confirm('确认重新开始打样？将回到"工程打样中"状态。', '重新打样')
  // 调用审核通过接口让状态从REJECTED(9)回到ENGINEERING(3)
  await sampleOrderApi.approve(detailData.value.orderId, '重新打样')
  ElMessage.success('已回到工程打样阶段')
  detailVisible.value = false
  getList()
}

// ==================== 列表操作（Prompt弹窗方式） ====================
async function handleSubmitReview(row: any) {
  await ElMessageBox.confirm(`确定提交样品单 [${row.orderNo}] 审核？`, '确认')
  await sampleOrderApi.submitReview(row.orderId)
  ElMessage.success('已提交审核')
  getList()
}

async function handleApprove(row: any) {
  const { value } = await ElMessageBox.prompt('审核备注（可选）', '审核通过', { inputType: 'textarea' })
  await sampleOrderApi.approve(row.orderId, value || '')
  ElMessage.success('审核通过，已进入工程打样阶段')
  getList()
}

async function handleRejectReview(row: any) {
  const { value } = await ElMessageBox.prompt('驳回原因', '审核驳回', { inputType: 'textarea' })
  if (!value) { ElMessage.warning('请输入驳回原因'); return }
  await sampleOrderApi.rejectReview(row.orderId, value)
  ElMessage.success('已驳回')
  getList()
}

async function handleMarkReady(row: any) {
  const { value } = await ElMessageBox.prompt('实际打样数量', '样品完成', { inputValue: String(row.sampleQty || 10) })
  const qty = parseInt(value || '0')
  if (qty <= 0) { ElMessage.warning('请输入有效数量'); return }
  await sampleOrderApi.markReady(row.orderId, qty)
  ElMessage.success('样品已完成，待送样')
  getList()
}

async function handleSendSample(row: any) {
  const { value } = await ElMessageBox.prompt('快递单号（可选）', '送样登记')
  await sampleOrderApi.sendSample(row.orderId, value || '')
  ElMessage.success('送样登记成功')
  getList()
}

async function handleConfirm(row: any) {
  const { value } = await ElMessageBox.prompt('客户方确认人姓名', '客户确认样品OK', { inputValue: row.sampleClientName || '' })
  await sampleOrderApi.confirm(row.orderId, value || '客户确认')
  ElMessage.success('客户已确认样品OK')
  getList()
}

async function handleRejectSample(row: any) {
  const { value } = await ElMessageBox.prompt('退回原因/修改要求', '退回修改', {
    inputType: 'textarea', confirmButtonText: '退回',
  })
  if (!value) { ElMessage.warning('请输入退回原因'); return }
  await sampleOrderApi.rejectSample(row.orderId, value)
  ElMessage.success(`已退回要求修改（进入Round ${(row.sampleRound || 1) + 1}）`)
  getList()
}

async function handleConvert(row: any) {
  await ElMessageBox.confirm(
    `确定将样品单 [${row.orderNo}] 转量产？将自动生成标准订单。`,
    '转量产确认',
    { confirmButtonText: '确定转量产', cancelButtonText: '取消', type: 'warning' }
  )
  await sampleOrderApi.convertToProduction(row.orderId)
  ElMessage.success('转量产成功，已生成标准订单')
  getList()
}

// ==================== 初始化 ====================
onMounted(() => {
  getList()
  statusOptions.value = SampleOrderStatusEnum.items.map((item) => ({
    value: item.value,
    label: item.label,
    description: '',
    terminal: false,
  }))
})
</script>

<style scoped lang="scss">
.app-container { padding: 20px; }
.search-card, .operation-card, .table-card { margin-bottom: 16px; }
</style>
