<template>
  <div class="app-container">
    <!-- 统计/头部 -->
    <el-card shadow="never" class="head-card">
      <div class="head-row">
        <div>
          <span class="page-title">需求管理</span>
          <span class="page-desc">业务需求统一入口：变更(ECN) / 新增 / 改善 / 问题</span>
        </div>
        <div>
          <el-button type="primary" icon="Plus" v-hasPermi="['biz:requirement:add']" @click="handleAdd">
            新增需求
          </el-button>
        </div>
      </div>

      <!-- 类型 tab -->
      <el-tabs v-model="typeTab" @tab-change="handleSearch">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane v-for="t in typeOptions" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>

      <!-- 搜索 -->
      <el-form inline class="search-form">
        <el-form-item label="单号">
          <el-input v-model="queryParams.requirementNo" placeholder="RQ-xxx" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" placeholder="标题关键字" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.requirementStatus" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="单号" prop="requirementNo" width="130" fixed="left">
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click="handleView(row)">{{ row.requirementNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.requirementType)" size="small">{{ typeLabel(row.requirementType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="title" min-width="220" show-overflow-tooltip />
        <el-table-column label="变更类型" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.requirementType === 'CHANGE' && row.changeType">{{ changeTypeLabel(row.changeType) }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="关联业务" width="140">
          <template #default="{ row }">
            <span v-if="row.bizNo">{{ row.bizType }}:{{ row.bizNo }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="80" align="center">
          <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.requirementStatus)">{{ statusLabel(row.requirementStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请人" prop="applicantName" width="90" align="center" />
        <el-table-column label="创建时间" prop="createTime" width="160" align="center" />
        <el-table-column label="操作" width="290" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canEdit(row)" link type="primary" icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.requirementStatus === 1" link type="warning" icon="Upload" @click="handleSubmit(row)">提交</el-button>
            <el-button v-if="row.requirementStatus === 2" link type="warning" icon="Stamp" @click="openApproval(row)">会签</el-button>
            <el-button v-if="row.requirementStatus === 3" link type="primary" icon="CaretRight" @click="handleExecute(row)">执行</el-button>
            <el-button v-if="row.requirementStatus === 4" link type="info" icon="Finished" @click="handleClose(row)">关闭</el-button>
            <el-button v-if="canDelete(row)" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" append-to-body destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="需求类型" prop="requirementType">
              <el-select v-model="form.requirementType" style="width: 100%">
                <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源" prop="source">
              <el-select v-model="form.source" style="width: 100%">
                <el-option v-for="s in sourceOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="一句话描述需求/变更内容" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="需求描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="详细描述：变更前是什么、要改成什么、为什么" maxlength="2000" show-word-limit />
        </el-form-item>

        <!-- ECN 扩展（仅变更类型展示） -->
        <template v-if="form.requirementType === 'CHANGE'">
          <el-divider content-position="left">工程变更信息（ECN）</el-divider>
          <el-row>
            <el-col :span="12">
              <el-form-item label="变更类型">
                <el-select v-model="form.changeType" clearable style="width: 100%">
                  <el-option v-for="c in changeTypeOptions" :key="c.value" :label="c.label" :value="c.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="切入方式">
                <el-select v-model="form.cutoverMode" clearable style="width: 100%">
                  <el-option label="立即切入" value="IMMEDIATE" />
                  <el-option label="按批切换" value="BATCH" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12">
              <el-form-item label="是否重打样">
                <el-radio-group v-model="form.needResample">
                  <el-radio :value="1">是</el-radio>
                  <el-radio :value="0">否</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="关联产品">
                <el-select
                  v-model="form.bizId"
                  filterable
                  clearable
                  placeholder="选择变更影响的产品（升版 BOM/工艺用）"
                  style="width: 100%"
                  @change="onProductChange"
                >
                  <el-option v-for="p in productOptions" :key="p.productId" :label="p.productCode" :value="p.productId">
                    <span>{{ p.productCode }}</span>
                    <span style="float: right; color: #909399">{{ p.productName }}</span>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12">
              <el-form-item label="变更前版本">
                <el-input v-model="form.versionBefore" placeholder="如 V1.0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="变更后版本">
                <el-input v-model="form.versionAfter" placeholder="如 V2.0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-row>
          <el-col :span="12">
            <el-form-item label="紧急度">
              <el-select v-model="form.urgency" style="width: 100%">
                <el-option label="紧急" value="urgent" />
                <el-option label="高" value="high" />
                <el-option label="普通" value="normal" />
                <el-option label="低" value="low" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望完成">
              <el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="期望完成日期" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="handleSave">保 存</el-button>
        <el-button @click="dialogVisible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="需求详情" width="640px" append-to-body>
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="单号">{{ detail.requirementNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeLabel(detail.requirementType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.requirementStatus)" size="small">{{ statusLabel(detail.requirementStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源">{{ sourceLabel(detail.source) }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
        <template v-if="detail.requirementType === 'CHANGE'">
          <el-descriptions-item label="变更类型">{{ changeTypeLabel(detail.changeType) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="切入方式">{{ detail.cutoverMode === 'IMMEDIATE' ? '立即切入' : detail.cutoverMode === 'BATCH' ? '按批切换' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否重打样">{{ detail.needResample === 1 ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.versionBefore || '-' }} → {{ detail.versionAfter || '-' }}</el-descriptions-item>
        </template>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望完成">{{ detail.expectDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见" :span="2">{{ detail.reviewRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detail.executeBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始执行">{{ detail.executeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行结果" :span="2">{{ detail.executeResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关闭时间">{{ detail.closeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button v-if="detail?.requirementType === 'CHANGE'" type="primary" icon="Printer" @click="handlePrint(detail)">打印变更通知</el-button>
        <el-button
          v-if="canUpgrade(detail)"
          type="warning"
          icon="RefreshRight"
          @click="handleUpgrade(detail)"
          >升版 BOM/工艺</el-button
        >
        <el-button @click="detailVisible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 四部门会签弹窗 -->
    <ApprovalDialog v-model:visible="approvalVisible" :requirement="approvalRow" @signed="getList" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Requirement' })

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ApprovalDialog from './components/ApprovalDialog.vue'
import {
  pageRequirement, getRequirement, createRequirement, updateRequirement,
  removeRequirement, submitRequirement, executeRequirement, closeRequirement, upgradeRequirement,
  listRequirementTypes, listRequirementStatuses,
} from '@/api/biz/requirement'
import { listProduct } from '@/api/product'

// ===== 状态选项 =====
const typeOptions = ref<{ value: string; label: string }[]>([])
const statusOptions = ref<{ value: number; label: string }[]>([])
const typeTab = ref('')
const sourceOptions = [
  { value: 'CUSTOMER', label: '客户' },
  { value: 'SALES', label: '销售' },
  { value: 'QUALITY', label: '品质' },
  { value: 'ENGINEERING', label: '工程' },
  { value: 'PRODUCTION', label: '生产' },
  { value: 'MANAGEMENT', label: '管理' },
  { value: 'OTHER', label: '其他' },
]
const changeTypeOptions = [
  { value: 'DESIGN', label: '设计改版' },
  { value: 'PROCESS', label: '工艺调整' },
  { value: 'MATERIAL', label: '材料变更' },
  { value: 'DRAWING', label: '图纸更新' },
  { value: 'OTHER', label: '其他' },
]

type TagType = 'danger' | 'success' | 'warning' | 'info' | 'primary'
function typeLabel(v?: string) { return typeOptions.value.find(t => t.value === v)?.label || v || '-' }
function typeTag(v?: string): TagType {
  return ({ CHANGE: 'danger', ADD: 'success', IMPROVE: 'warning', ISSUE: 'info' } as Record<string, TagType>)[v || ''] || 'info'
}
function statusLabel(v?: number) { return statusOptions.value.find(s => s.value === v)?.label || '-' }
function statusTag(v?: number): TagType {
  return ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'primary', 5: 'info', 6: 'danger' } as Record<number, TagType>)[v || 0] || 'info'
}
function sourceLabel(v?: string) { return sourceOptions.find(s => s.value === v)?.label || v || '-' }
function changeTypeLabel(v?: string) { return changeTypeOptions.find(c => c.value === v)?.label || v || '-' }

// ===== 列表 =====
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1, pageSize: 10,
  requirementNo: '', title: '', requirementStatus: undefined as number | undefined,
})

async function getList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { ...queryParams }
    if (typeTab.value) params.requirementType = typeTab.value
    else delete params.requirementType
    const res: any = await pageRequirement(params)
    if (res.code === 200) {
      list.value = res.data?.records || []
      total.value = Number(res.data?.total || 0)
    }
  } finally {
    loading.value = false
  }
}
function handleSearch() { queryParams.pageNum = 1; getList() }
function handleReset() {
  Object.assign(queryParams, { pageNum: 1, pageSize: 10, requirementNo: '', title: '', requirementStatus: undefined })
  typeTab.value = ''
  getList()
}

// ===== 表单 =====
const dialogVisible = ref(false)
const dialogTitle = ref('新增需求')
const formRef = ref<FormInstance>()
const detailVisible = ref(false)
const detail = ref<any>(null)

const form = reactive<any>({
  requirementId: undefined, requirementType: 'CHANGE', title: '', description: '',
  source: 'ENGINEERING', urgency: 'normal', expectDate: '',
  changeType: '', cutoverMode: '', needResample: 0, versionBefore: '', versionAfter: '',
  bizType: '', bizId: undefined, bizNo: '', remark: '',
})

const rules: FormRules = {
  requirementType: [{ required: true, message: '请选择需求类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

function resetForm() {
  Object.assign(form, {
    requirementId: undefined, requirementType: 'CHANGE', title: '', description: '',
    source: 'ENGINEERING', urgency: 'normal', expectDate: '',
    changeType: '', cutoverMode: '', needResample: 0, versionBefore: '', versionAfter: '',
    bizType: '', bizId: undefined, bizNo: '', remark: '',
  })
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增需求'
  dialogVisible.value = true
}

function handleEdit(row: any) {
  Object.assign(form, {
    ...row,
    expectDate: row.expectDate || '',
  })
  dialogTitle.value = `编辑需求【${row.requirementNo}】`
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate().catch(() => Promise.reject())
  if (form.requirementType === 'CHANGE' && !form.changeType) {
    ElMessage.warning('变更类型需求请选择变更类型')
    return
  }
  try {
    if (form.requirementId) {
      await updateRequirement(form)
      ElMessage.success('保存成功')
    } else {
      await createRequirement(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

// ===== 详情 =====
async function handleView(row: any) {
  const res: any = await getRequirement(row.requirementId)
  detail.value = res.data
  detailVisible.value = true
}

// ===== 操作 =====
function canEdit(row: any) { return row.requirementStatus === 1 || row.requirementStatus === 6 }
function canDelete(row: any) { return row.requirementStatus === 1 }

function handleSubmit(row: any) {
  ElMessageBox.confirm(`确认提交需求「${row.requirementNo}」进入评审？`, '提交确认', { type: 'warning' })
    .then(async () => {
      await submitRequirement(row.requirementId)
      ElMessage.success('已提交评审，等待四部门会签')
      getList()
    }).catch(() => {})
}

// ===== 四部门会签 =====
const approvalVisible = ref(false)
const approvalRow = ref<any>(null)
function openApproval(row: any) {
  approvalRow.value = row
  approvalVisible.value = true
}

function handleExecute(row: any) {
  ElMessageBox.confirm(`确认开始执行「${row.requirementNo}」？`, '开始执行', { type: 'warning' })
    .then(() => executeRequirement(row.requirementId))
    .then(() => { ElMessage.success('已开始执行'); getList() })
    .catch(() => {})
}

function handleClose(row: any) {
  ElMessageBox.prompt('请输入执行结果（可留空）', '关闭需求单', {
    inputPlaceholder: '执行结果/完成情况',
    inputValidator: () => true,
  })
    .then(({ value }) => closeRequirement(row.requirementId, value || undefined))
    .then(() => { ElMessage.success('已关闭'); getList() })
    .catch(() => {})
}

function handleDelete(row: any) {
  ElMessageBox.confirm(`确认删除「${row.requirementNo}」？`, '警告', { type: 'warning' })
    .then(() => removeRequirement(row.requirementId))
    .then(() => { ElMessage.success('删除成功'); getList() })
    .catch(() => {})
}

// 打印变更通知（QR-030）
function handlePrint(row: any) {
  window.open(`/print/requirement/${row.requirementId}`, '_blank')
}

// ===== 关联产品（变更升版用） =====
const productOptions = ref<any[]>([])
async function loadProducts() {
  if (productOptions.value.length > 0) return
  try {
    const res: any = await listProduct({ pageNum: 1, pageSize: 500 })
    productOptions.value = Array.isArray(res?.data) ? res.data : []
  } catch {
    productOptions.value = []
  }
}
function onProductChange(id?: number) {
  const p = productOptions.value.find((x) => x.productId === id)
  if (p) {
    form.bizType = 'product'
    form.bizNo = p.productCode
  } else {
    form.bizType = ''
    form.bizNo = ''
  }
}

// ===== 变更升版（复制 BOM/工艺路线新版本） =====
function canUpgrade(d: any) {
  return (
    d?.requirementType === 'CHANGE' &&
    (d.requirementStatus === 3 || d.requirementStatus === 4) &&
    d.bizType === 'product' &&
    !!d.bizId
  )
}
function handleUpgrade(d: any) {
  ElMessageBox.prompt('请输入新版本号（如 V2.0，不能与现有版本重复）', '变更升版', {
    inputPlaceholder: '新版本号，如 V2.0',
    inputValidator: (v: string) => (v && v.trim() ? true : '版本号不能为空'),
  })
    .then(async ({ value }) => {
      const res: any = await upgradeRequirement(d.requirementId, value.trim())
      const logs: string[] = res?.data?.logs || []
      ElMessageBox.alert(
        `<div style="text-align:left;line-height:1.9">${logs.map((l) => '· ' + l).join('<br>')}</div>`,
        '升版结果',
        { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' },
      )
    })
    .catch(() => {})
}

onMounted(async () => {
  const [t, s] = await Promise.all([listRequirementTypes(), listRequirementStatuses()])
  typeOptions.value = (t as any).data || []
  statusOptions.value = (s as any).data || []
  loadProducts()
  getList()
})
</script>

<style scoped>
.head-card { margin-bottom: 12px; }
.head-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.page-title { font-size: 16px; font-weight: 600; }
.page-desc { margin-left: 12px; color: #909399; font-size: 12px; }
.search-form { margin-top: 4px; }
.table-card { margin-bottom: 16px; }
</style>
