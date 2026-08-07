<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="供应商编码" prop="supplierCode">
          <el-input
            v-model="queryParams.supplierCode"
            placeholder="请输入供应商编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="供应商名称" prop="supplierName">
          <el-input
            v-model="queryParams.supplierName"
            placeholder="请输入供应商名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="供应商类型" prop="supplierType">
          <el-select
            v-model="queryParams.supplierType"
            placeholder="请选择供应商类型"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in supplierTypeOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in statusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
            >删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Upload" @click="handleImport">导入</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button plain icon="Download" @click="handleDownloadTemplate">下载模板</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Star" :disabled="single" @click="handleEvaluation"
            >评估</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="supplierList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="供应商编码" align="center" prop="supplierCode" width="120" />
        <el-table-column label="供应商名称" align="center" prop="supplierName" width="180" />
        <el-table-column label="供应商类型" align="center" prop="supplierType" width="120">
          <template #default="scope">
            <span>{{ getSupplierTypeLabel(scope.row.supplierType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="联系人" align="center" prop="contactPerson" width="100" />
        <el-table-column label="联系电话" align="center" prop="phone" width="120" />
        <el-table-column label="邮箱" align="center" prop="email" width="180" />
        <el-table-column label="评估总分" align="center" prop="evaluationScore" width="100">
          <template #default="scope">
            <el-rate
              v-model="scope.row.evaluationScore"
              disabled
              :max="10"
              :allow-half="true"
              show-score
              text-color="#ff9900"
              score-template="{value}"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status" width="100">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          width="200"
        >
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="详情" placement="top">
              <el-button link type="info" icon="View" @click="handleView(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="评估" placement="top">
              <el-button
                link
                type="success"
                icon="Star"
                @click="handleEvaluation(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 添加或修改供应商对话框 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-form ref="supplierFormRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商编码" prop="supplierCode">
              <el-input v-model="form.supplierCode" placeholder="请输入供应商编码" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input
                v-model="form.supplierName"
                placeholder="请输入供应商名称"
                maxlength="100"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商类型" prop="supplierType">
              <el-select
                v-model="form.supplierType"
                placeholder="请选择供应商类型"
                style="width: 100%"
              >
                <el-option
                  v-for="dict in supplierTypeOptions"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="form.contactPerson" placeholder="请输入联系人" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="地址" prop="address">
              <el-input
                v-model="form.address"
                type="textarea"
                placeholder="请输入地址"
                :rows="2"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="付款条件" prop="paymentTerms">
              <el-input v-model="form.paymentTerms" placeholder="请输入付款条件" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行账户" prop="bankAccount">
              <el-input v-model="form.bankAccount" placeholder="请输入银行账户" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="税号" prop="taxNumber">
              <el-input v-model="form.taxNumber" placeholder="请输入税号" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio value="0">正常</el-radio>
                <el-radio value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                placeholder="请输入备注"
                :rows="3"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 供应商详情对话框 -->
    <el-dialog title="供应商详情" v-model="detailOpen" width="800px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="供应商编码">{{ detail.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="供应商名称">{{ detail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="供应商类型">
          {{ getSupplierTypeLabel(detail.supplierType) }}
        </el-descriptions-item>
        <el-descriptions-item label="联系人">{{
          detail.contactPerson || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ detail.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="付款条件">{{
          detail.paymentTerms || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="银行账户">{{
          detail.bankAccount || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="税号">{{ detail.taxNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评估总分">
          <el-rate
            :model-value="detail.evaluationScore || 0"
            disabled
            :max="10"
            :allow-half="true"
            show-score
            text-color="#ff9900"
            score-template="{value}"
          />
        </el-descriptions-item>
        <el-descriptions-item label="质量评分">
          {{ detail.qualityScore || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="交期评分">
          {{ detail.deliveryScore || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="价格评分">
          {{ detail.priceScore || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="最后评估时间">
          {{ detail.lastEvaluationDate ? parseTime(detail.lastEvaluationDate) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === '0' ? 'success' : 'danger'">
            {{ detail.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detail.remark || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 供应商评估对话框 -->
    <el-dialog title="供应商评估" v-model="evaluationOpen" width="600px" append-to-body>
      <el-form
        ref="evaluationFormRef"
        :model="evaluationForm"
        :rules="evaluationRules"
        label-width="120px"
      >
        <el-form-item label="质量评分" prop="qualityScore">
          <el-rate
            v-model="evaluationForm.qualityScore"
            :max="10"
            :allow-half="true"
            show-text
            :texts="['很差', '差', '一般', '好', '很好']"
          />
        </el-form-item>
        <el-form-item label="交期评分" prop="deliveryScore">
          <el-rate
            v-model="evaluationForm.deliveryScore"
            :max="10"
            :allow-half="true"
            show-text
            :texts="['很差', '差', '一般', '好', '很好']"
          />
        </el-form-item>
        <el-form-item label="价格评分" prop="priceScore">
          <el-rate
            v-model="evaluationForm.priceScore"
            :max="10"
            :allow-half="true"
            show-text
            :texts="['很差', '差', '一般', '好', '很好']"
          />
        </el-form-item>
        <el-form-item label="评估总分" prop="evaluationScore">
          <el-input-number
            v-model="evaluationForm.evaluationScore"
            :min="0"
            :max="10"
            :precision="1"
            :step="0.5"
            disabled
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="评估备注" prop="remark">
          <el-input
            v-model="evaluationForm.remark"
            type="textarea"
            placeholder="请输入评估备注"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitEvaluation">确 定</el-button>
          <el-button @click="cancelEvaluation">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'SupplierList',
})

import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listSupplier,
  getSupplier,
  addSupplier,
  updateSupplier,
  delSupplier,
  exportSupplier,
  changeSupplierStatus,
  updateSupplierEvaluation,
  importSuppliers,
  importTemplate,
  checkSupplierCodeUnique,
  checkSupplierNameUnique,
} from '@/api/purchase/supplier'
import { parseTime, download } from '@/utils/format'
import { SupplierTypeEnum, SupplierStatusEnum } from '@/enums/purchase'

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  supplierCode: undefined as string | undefined,
  supplierName: undefined as string | undefined,
  supplierType: undefined as string | undefined,
  status: undefined as string | undefined,
  orderByColumn: undefined as string | undefined,
  isAsc: undefined as 'asc' | 'desc' | undefined,
})

// 响应式数据
const loading = ref(false)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const evaluationOpen = ref(false)

// 表单数据
const form = reactive({
  supplierId: undefined as number | undefined,
  supplierCode: '',
  supplierName: '',
  supplierType: '',
  contactPerson: '',
  phone: '',
  email: '',
  address: '',
  paymentTerms: '',
  bankAccount: '',
  taxNumber: '',
  status: '0',
  remark: '',
})

// 详情数据
const detail = reactive({
  supplierId: undefined as number | undefined,
  supplierCode: '',
  supplierName: '',
  supplierType: '',
  contactPerson: '',
  phone: '',
  email: '',
  address: '',
  paymentTerms: '',
  bankAccount: '',
  taxNumber: '',
  evaluationScore: 0,
  qualityScore: 0,
  deliveryScore: 0,
  priceScore: 0,
  lastEvaluationDate: '',
  status: '0',
  remark: '',
})

// 评估表单数据
const evaluationForm = reactive({
  supplierId: undefined as number | undefined,
  qualityScore: 0,
  deliveryScore: 0,
  priceScore: 0,
  evaluationScore: 0,
  remark: '',
})

// 表单引用
const supplierFormRef = ref<FormInstance>()
const evaluationFormRef = ref<FormInstance>()

// 供应商编码唯一性校验
const validateSupplierCodeUnique = async (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback()
    return
  }
  try {
    const res = await checkSupplierCodeUnique(value)
    if (res.data === false) {
      callback(new Error('供应商编码已存在'))
    } else {
      callback()
    }
  } catch {
    callback()
  }
}

// 供应商名称唯一性校验
const validateSupplierNameUnique = async (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback()
    return
  }
  try {
    const res = await checkSupplierNameUnique(value)
    if (res.data === false) {
      callback(new Error('供应商名称已存在'))
    } else {
      callback()
    }
  } catch {
    callback()
  }
}

// 表单验证规则
const rules = reactive<FormRules>({
  supplierCode: [
    { required: true, message: '供应商编码不能为空', trigger: 'blur' },
    {
      min: 1,
      max: 50,
      message: '供应商编码长度必须在1到50个字符之间',
      trigger: 'blur',
    },
    {
      validator: validateSupplierCodeUnique,
      trigger: 'blur',
    },
  ],
  supplierName: [
    { required: true, message: '供应商名称不能为空', trigger: 'blur' },
    {
      min: 1,
      max: 100,
      message: '供应商名称长度必须在1到100个字符之间',
      trigger: 'blur',
    },
    {
      validator: validateSupplierNameUnique,
      trigger: 'blur',
    },
  ],
  supplierType: [{ required: true, message: '供应商类型不能为空', trigger: 'change' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
})

// 评估表单验证规则
const evaluationRules = reactive<FormRules>({
  qualityScore: [{ required: true, message: '质量评分不能为空', trigger: 'change' }],
  deliveryScore: [{ required: true, message: '交期评分不能为空', trigger: 'change' }],
  priceScore: [{ required: true, message: '价格评分不能为空', trigger: 'change' }],
})

// 表格数据
const supplierList = ref<any[]>([])

// 字典选项
const supplierTypeOptions = SupplierTypeEnum.items
const statusOptions = SupplierStatusEnum.items

// 获取供应商列表
const getList = async () => {
  loading.value = true
  try {
    const response = await listSupplier(queryParams)
    // 兼容后端返回数组（当前实现）或分页对象（后续升级）
    const data = response.data as any
    supplierList.value = data?.records || data?.list || (Array.isArray(data) ? data : []) || []
    total.value = data?.total ?? supplierList.value.length
  } catch (error) {
    console.error('获取供应商列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取供应商类型标签
const getSupplierTypeLabel = (type: string) => {
  const option = supplierTypeOptions.find(
    (opt: { value: string; label: string }) => opt.value === type
  )
  return option ? option.label : '未知'
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    supplierCode: undefined,
    supplierName: undefined,
    supplierType: undefined,
    status: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.supplierId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 排序触发
const handleSortChange = (column: any) => {
  if (column.prop && column.order) {
    queryParams.orderByColumn = column.prop
    queryParams.isAsc = column.order === 'ascending' ? 'asc' : 'desc'
  } else {
    queryParams.orderByColumn = undefined
    queryParams.isAsc = undefined
  }
  getList()
}

// 新增按钮操作
const handleAdd = () => {
  resetForm()
  open.value = true
  title.value = '新增供应商'
}

// 修改按钮操作
const handleUpdate = (row?: any) => {
  resetForm()
  const supplierId = row?.supplierId || ids.value[0]
  getSupplier(supplierId).then((response: any) => {
    Object.assign(form, response.data)
    open.value = true
    title.value = '修改供应商'
  })
}

// 删除按钮操作
const handleDelete = (row?: any) => {
  const supplierIds = row?.supplierId || ids.value
  ElMessageBox.confirm('是否确认删除供应商编号为"' + supplierIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return delSupplier(supplierIds)
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有供应商数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      const loading = ElLoading.service({ text: '导出中...', lock: true })
      return exportSupplier(queryParams)
        .then((response: any) => {
          download(response, '供应商列表.xlsx')
        })
        .finally(() => loading.close())
    })
    .catch(() => {})
}

// 导入按钮操作
const handleImport = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return

    const formData = new FormData()
    formData.append('file', file)

    try {
      await importSuppliers(formData)
      ElMessage.success('导入成功')
      getList()
    } catch (error) {
      console.error('导入失败:', error)
      ElMessage.error('导入失败')
    }
  }
  input.click()
}

// 下载导入模板
const handleDownloadTemplate = async () => {
  try {
    const res = await importTemplate()
    download(res, '供应商导入模板.xlsx')
  } catch (error) {
    console.error('下载模板失败:', error)
    ElMessage.error('下载模板失败')
  }
}

// 评估按钮操作
const handleEvaluation = (row?: any) => {
  const supplierId = row?.supplierId || ids.value[0]
  getSupplier(supplierId).then((response: any) => {
    Object.assign(evaluationForm, {
      supplierId: response.data.supplierId,
      qualityScore: response.data.qualityScore || 0,
      deliveryScore: response.data.deliveryScore || 0,
      priceScore: response.data.priceScore || 0,
      evaluationScore: response.data.evaluationScore || 0,
      remark: '',
    })
    evaluationOpen.value = true
  })
}

// 查看详情按钮操作
const handleView = (row: any) => {
  const supplierId = row.supplierId as number
  getSupplier(supplierId).then((response: any) => {
    Object.assign(detail, response.data)
    detailOpen.value = true
  })
}

// 状态改变处理
const handleStatusChange = (row: any) => {
  const text = row.status === 1 ? '启用' : '停用'
  ElMessageBox.confirm('确认要' + text + '"' + row.supplierName + '"供应商吗？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return changeSupplierStatus(row.supplierId, row.status)
    })
    .then(() => {
      ElMessage.success(text + '成功')
      getList()
    })
    .catch(() => {
      row.status = row.status === '0' ? '1' : '0'
    })
}

// 表单重置
const resetForm = () => {
  if (supplierFormRef.value) {
    supplierFormRef.value.resetFields()
  }
  Object.assign(form, {
    supplierId: undefined,
    supplierCode: '',
    supplierName: '',
    supplierType: '',
    contactPerson: '',
    phone: '',
    email: '',
    address: '',
    paymentTerms: '',
    bankAccount: '',
    taxNumber: '',
    status: '0',
    remark: '',
  })
}

// 提交表单
const submitForm = () => {
  if (!supplierFormRef.value) return

  supplierFormRef.value.validate((valid) => {
    if (valid) {
      if (form.supplierId !== undefined) {
        updateSupplier(form as any).then(() => {
          ElMessage.success('修改成功')
          open.value = false
          getList()
        })
      } else {
        addSupplier(form as any).then(() => {
          ElMessage.success('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

// 取消按钮
const cancel = () => {
  open.value = false
  resetForm()
}

// 提交评估
const submitEvaluation = () => {
  if (!evaluationFormRef.value) return

  evaluationFormRef.value.validate((valid) => {
    if (valid && evaluationForm.supplierId) {
      updateSupplierEvaluation(
        evaluationForm.supplierId,
        evaluationForm.evaluationScore,
        evaluationForm.qualityScore,
        evaluationForm.deliveryScore,
        evaluationForm.priceScore
      ).then(() => {
        ElMessage.success('评估成功')
        evaluationOpen.value = false
        getList()
      })
    }
  })
}

// 取消评估
const cancelEvaluation = () => {
  evaluationOpen.value = false
}

// 监听评估分数变化，计算总分
watch(
  () => [evaluationForm.qualityScore, evaluationForm.deliveryScore, evaluationForm.priceScore],
  () => {
    evaluationForm.evaluationScore = Number(
      (
        (evaluationForm.qualityScore + evaluationForm.deliveryScore + evaluationForm.priceScore) /
        3
      ).toFixed(1)
    )
  }
)

// 组件挂载时获取数据
onMounted(() => {
  getList()
})
</script>
