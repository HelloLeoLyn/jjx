<template>
  <div class="hr-employee">
    <div class="page-header">
      <h1 class="page-title">员工档案</h1>
      <div class="page-actions">
        <el-button
          type="primary"
          icon="Plus"
          v-hasPermi="['hr:employee:add']"
          @click="handleCreate"
        >
          新增员工
        </el-button>
        <el-button
          type="success"
          plain
          icon="Upload"
          v-hasPermi="['hr:employee:import']"
          @click="importDialogVisible = true"
        >
          导入
        </el-button>
        <el-button
          type="warning"
          plain
          icon="Download"
          v-hasPermi="['hr:employee:export']"
          @click="handleExport"
        >
          导出
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="姓名 / 工号 / 手机号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="query.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            clearable
            placeholder="全部部门（含子部门）"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="query.position" clearable placeholder="全部岗位" style="width: 160px">
            <el-option
              v-for="d in positionOptions"
              :key="d.itemKey"
              :label="d.itemValue || d.label"
              :value="d.itemKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="在职状态">
          <el-select v-model="query.employmentStatus" clearable placeholder="全部" style="width: 130px">
            <el-option
              v-for="d in EmploymentStatusEnum.items"
              :key="d.value"
              :label="d.label"
              :value="d.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="empNo" label="工号" width="110" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ row.sex ? SexEnum.getLabel(row.sex) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" width="130" />
        <el-table-column prop="position" label="岗位" width="140" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="进厂日期" width="120">
          <template #default="{ row }">{{ row.hireDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="学历" width="90">
          <template #default="{ row }">{{ educationLabel(row.education) }}</template>
        </el-table-column>
        <el-table-column label="在职状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.employmentStatus)" size="small">
              {{ statusLabel(row.employmentStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="系统账号" width="120">
          <template #default="{ row }">{{ row.userName || '未关联' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button
              link
              size="small"
              v-hasPermi="['hr:employee:edit']"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              size="small"
              type="danger"
              v-hasPermi="['hr:employee:delete']"
              @click="handleRemove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="form.empId ? '编辑员工' : '新增员工'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" prop="empNo">
              <el-input
                v-model="form.empNo"
                :placeholder="form.empId ? '' : '留空自动生成（如 JJX0001）'"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-radio-group v-model="form.sex">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="在职状态">
              <el-select v-model="form.employmentStatus" style="width: 100%">
                <el-option
                  v-for="d in EmploymentStatusEnum.items"
                  :key="d.value"
                  :label="d.label"
                  :value="d.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门">
              <el-tree-select
                v-model="form.deptId"
                :data="deptTree"
                :props="{ label: 'deptName', children: 'children' }"
                node-key="id"
                check-strictly
                :render-after-expand="false"
                clearable
                placeholder="请选择部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-select v-model="form.position" clearable placeholder="请选择岗位" style="width: 100%">
                <el-option
                  v-for="d in positionOptions"
                  :key="d.itemKey"
                  :label="d.itemValue || d.label"
                  :value="d.itemKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="11 位手机号" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进厂日期">
              <el-date-picker
                v-model="form.hireDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="离职日期">
              <el-date-picker
                v-model="form.leaveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证号">
              <el-input
                v-model="form.idCardNo"
                :placeholder="sensitiveHint"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证地址">
              <el-input
                v-model="form.idCardAddress"
                :placeholder="sensitiveHint"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学历">
              <el-select v-model="form.education" clearable placeholder="请选择" style="width: 100%">
                <el-option
                  v-for="d in educationOptions"
                  :key="d.itemKey"
                  :label="d.itemValue || d.label"
                  :value="d.itemKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="专业">
              <el-input v-model="form.major" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="现住址">
              <el-input v-model="form.currentAddress" :placeholder="sensitiveHint" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="个人履历">
              <el-input v-model="form.resume" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" clearable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="员工详情" width="720px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="工号">{{ detail.empNo }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="性别">
          {{ detail.sex ? SexEnum.getLabel(detail.sex) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="在职状态">
          {{ statusLabel(detail.employmentStatus) }}
        </el-descriptions-item>
        <el-descriptions-item label="部门">{{ detail.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detail.position || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="进厂日期">{{ detail.hireDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="离职日期">{{ detail.leaveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="学历">{{ educationLabel(detail.education) }}</el-descriptions-item>
        <el-descriptions-item label="专业">{{ detail.major || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detail.idCardNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="系统账号">{{ detail.userName || '未关联' }}</el-descriptions-item>
        <el-descriptions-item label="身份证地址" :span="2">
          {{ detail.idCardAddress || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="现住址" :span="2">{{ detail.currentAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="个人履历" :span="2">{{ detail.resume || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="!detail.sensitiveVisible"
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="敏感字段（身份证号/身份证地址/住址）已脱敏；如需查看明文，请申请 hr:employee:sensitive 权限。"
      />
    </el-dialog>

    <ExcelImportDialog
      :visible="importDialogVisible"
      @update:visible="importDialogVisible = $event"
      title="导入员工档案"
      tip="请按模板填写：部门需与映射表一致（工程部/业务部/仓库/资材部/品质部/制造部/冲型/印刷/组装/加工/刀模）；工号留空自动生成；身份证号入库即加密。"
      :import-api="hrEmployeeApi.importFile"
      :template-api="hrEmployeeApi.downloadTemplate"
      template-name="员工档案导入模板.xlsx"
      @success="handleImportSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import ExcelImportDialog from '@/components/ExcelImportDialog/index.vue'
import { deptApi } from '@/api/system/dept'
import { hrEmployeeApi } from '@/api/hr/employee'
import { useDict } from '@/composables/useDict'
import { download } from '@/utils/format'
import { EmploymentStatusEnum, SexEnum } from '@/enums/hr/EmployeeEnum'
import type { SysDept } from '@/types/system'
import type {
  HrEmployeeForm,
  HrEmployeeQuery,
  HrEmployeeVO,
  HrImportResult,
} from '@/types/hr/employee'

defineOptions({ name: 'HrEmployee' })

const loading = ref(false)
const saving = ref(false)
const list = ref<HrEmployeeVO[]>([])
const total = ref(0)
const query = reactive<HrEmployeeQuery>({ pageNum: 1, pageSize: 10 })

const deptTree = ref<SysDept[]>([])
const { options: educationOptions } = useDict('hr_education')
const { options: positionOptions } = useDict('hr_position')

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<HrEmployeeForm>({ name: '', employmentStatus: 2 })
const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

const detailVisible = ref(false)
const detail = ref<HrEmployeeVO>({} as HrEmployeeVO)
const importDialogVisible = ref(false)

const sensitiveHint = ref('')

/** 加载部门树（扁平列表 → 树） */
async function loadDeptTree() {
  const res = await deptApi.list({})
  const flat = res.data || []
  const map = new Map<number, SysDept>()
  flat.forEach((d) => {
    map.set(Number(d.id), { ...d, children: [] })
  })
  const roots: SysDept[] = []
  map.forEach((node) => {
    const pid = Number(node.parentId)
    if (pid && map.has(pid)) {
      map.get(pid)!.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  deptTree.value = roots
}

async function loadList() {
  loading.value = true
  try {
    const res = await hrEmployeeApi.page(query)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.keyword = undefined
  query.deptId = undefined
  query.position = undefined
  query.employmentStatus = undefined
  handleSearch()
}

function educationLabel(key?: string) {
  if (!key) return '-'
  const hit = educationOptions.value.find((d) => d.itemKey === key)
  return hit?.itemValue || hit?.label || key
}

function statusLabel(status?: number) {
  return status ? EmploymentStatusEnum.getLabel(status) : '-'
}

function statusTagType(status?: number) {
  return EmploymentStatusEnum.getTagProps(status ?? 0).type
}

function resetForm(data?: Partial<HrEmployeeForm>) {
  Object.keys(form).forEach((k) => delete (form as Record<string, unknown>)[k])
  Object.assign(form, { employmentStatus: 2 } as HrEmployeeForm, data || {})
}

async function handleCreate() {
  resetForm()
  sensitiveHint.value = '留空自动生成工号'
  formVisible.value = true
}

async function handleEdit(row: HrEmployeeVO) {
  const res = await hrEmployeeApi.detail(row.empId)
  const d = res.data
  if (!d) return
  resetForm({
    empId: d.empId,
    empNo: d.empNo,
    name: d.name,
    sex: d.sex,
    deptId: d.deptId,
    position: d.position,
    phone: d.phone,
    email: d.email,
    hireDate: d.hireDate,
    leaveDate: d.leaveDate,
    employmentStatus: d.employmentStatus,
    idCardNo: d.idCardNo,
    idCardAddress: d.idCardAddress,
    currentAddress: d.currentAddress,
    education: d.education,
    major: d.major,
    resume: d.resume,
    userId: d.userId,
    remark: d.remark,
  })
  sensitiveHint.value = d.sensitiveVisible
    ? ''
    : '无敏感字段权限：保持脱敏原值即不修改'
  formVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.empId) {
      await hrEmployeeApi.update(form)
      ElMessage.success('修改成功')
    } else {
      await hrEmployeeApi.create(form)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

function handleDetail(row: HrEmployeeVO) {
  hrEmployeeApi.detail(row.empId).then((res) => {
    const d = res.data
    if (!d) return
    detail.value = d
    detailVisible.value = true
  })
}

function handleRemove(row: HrEmployeeVO) {
  ElMessageBox.confirm(`确认删除员工「${row.name}（${row.empNo}）」？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await hrEmployeeApi.remove(row.empId)
      ElMessage.success('删除成功')
      loadList()
    })
    .catch(() => {})
}

function handleExport() {
  ElMessageBox.confirm('是否确认按当前筛选条件导出员工档案？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      const response = await hrEmployeeApi.exportList(query)
      download(response, '员工档案.xlsx')
    })
    .catch(() => {})
}

function handleImportSuccess(result?: HrImportResult) {
  if (result && result.failCount > 0) {
    ElMessage.warning(
      `导入完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条（详见接口返回明细）`,
    )
  } else {
    ElMessage.success('导入成功')
  }
  loadList()
}

onMounted(() => {
  loadDeptTree()
  loadList()
})
</script>

<style scoped>
.hr-employee {
  padding: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}
.filter-form {
  margin-bottom: 8px;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
